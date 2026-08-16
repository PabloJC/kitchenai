package com.kitchenai.ui.presentation.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_not_found
import com.kitchenai.ui.resources.error_timeout
import com.kitchenai.ui.resources.error_unauthorized_list
import com.kitchenai.ui.resources.error_unknown
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Renders one shopping list from its stream and writes every edit straight through.
 *
 * No action touches the items in the state: the domain writes, Firestore's local cache echoes the
 * write back within the frame, and a second device's edit arrives the same way. Keeping a local
 * copy would look like an optimistic update and behave like a divergence.
 *
 * Every coroutine it starts runs on the injected dispatcher, so the caches below are
 * `MutableStateFlow` rather than plain fields: two collectors on a real pool write them and the
 * public methods read them, and a `var` gives no visibility guarantee between the two.
 */
class ShoppingViewModel(
    private val ensureDefaultShoppingList: EnsureDefaultShoppingList,
    private val reads: ShoppingReads,
    private val writes: ShoppingWrites,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    // Buffered: an undo offer emitted while the screen is recomposing must wait, not disappear.
    private val _events = Channel<ShoppingEvent>(Channel.BUFFERED)
    val events: Flow<ShoppingEvent> = _events.receiveAsFlow()

    private var started = false

    // Written by the bootstrap coroutine and read by every public method, so not plain fields.
    private val userId = MutableStateFlow<UserId?>(null)
    private val listId = MutableStateFlow<ShoppingListId?>(null)

    // Null until the first emission. An empty list and a list nobody has sent yet look the same
    // on screen otherwise, and one of them is still loading.
    // Null means the listener has not emitted yet, which is what loading is; an empty list is an
    // empty list.
    private val items = MutableStateFlow<List<ShoppingItem>?>(null)
    private val catalogue = MutableStateFlow<List<Ingredient>>(emptyList())

    // Units are terms and their fallback language lives on the taxonomy, so the resolver needs
    // all three alongside the catalogue. Without them a quantity renders its bare term id.
    private val units = MutableStateFlow<List<Term>>(emptyList())
    private val vocabularies = MutableStateFlow<List<Taxonomy>>(emptyList())
    private var languageTags: List<String> = emptyList()
    private val resolver = MutableStateFlow(LabelResolver())

    // One per source: a catalogue that recovers must not clear a still-broken item listener,
    // and the item listener answering — with data or with a failure — is what ends loading.
    private val itemsError = MutableStateFlow<UiText?>(null)
    private val catalogueError = MutableStateFlow<UiText?>(null)
    private val itemsAnswered = MutableStateFlow(false)

    // User-owned, so they belong to the screen rather than to a listener.
    private val draft = MutableStateFlow(ShoppingDraftUi())
    private val listName = MutableStateFlow("")

    /** A rejected write outlives the next emission: the listener echo must not wipe the reason. */
    private val writeFailure = MutableStateFlow<UiText?>(null)

    private val lines =
        combine(items, itemsError, itemsAnswered, resolver) { loaded, error, answered, labels ->
            LinesState(
                lines = loaded.orEmpty().map { item -> toUi(item, labels) },
                error = error,
                isLoading = !answered,
                failedToLoad = error != null && loaded == null,
            )
        }

    /**
     * Derived, never assembled: four collectors used to each call a render() that read five
     * fields and wrote the state, so whichever finished last could publish a combination that
     * never held. Here every source is a flow and the composition happens in one place.
     *
     * Declared after its sources on purpose — a property cannot combine what is not built yet —
     * and eager, so the screen has a state before anything collects it.
     */
    val state: StateFlow<ShoppingUiState> =
        combine(lines, catalogueError, draft, listName, writeFailure, ::project)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ShoppingUiState())

    /**
     * Idempotent: a configuration change composes the screen again and must not open a second pair
     * of listeners. [defaultListName] only reaches Firestore when there is no list yet.
     */
    fun start(
        userId: UserId,
        languageTags: List<String>,
        defaultListName: String,
    ) {
        if (started) return
        started = true
        this.userId.value = userId
        this.languageTags = languageTags
        rebuildResolver()
        listName.value = defaultListName
        watchCatalogue()
        watchUnits()
        viewModelScope.launch(dispatchers.default) {
            val labels = languageTags.take(1).associateWith { defaultListName }
            when (val list = ensureDefaultShoppingList(userId, labels)) {
                is AppResult.Failure -> {
                    itemsError.value = list.error.describe()
                    itemsAnswered.value = true
                }
                is AppResult.Success -> {
                    listId.value = list.data
                    watchItems(userId, list.data)
                }
            }
        }
    }

    /** Assigns, never toggles: the value on screen is the value written, on both devices. */
    fun setChecked(
        itemId: ShoppingItemId,
        checked: Boolean,
    ) {
        edit { user, list -> writes.setChecked(user, list, itemId, checked) }
    }

    fun remove(itemId: ShoppingItemId) {
        val item = items.value?.firstOrNull { it.id == itemId } ?: return
        edit { user, list ->
            val result = writes.remove(user, list, itemId)
            if (result is AppResult.Success) _events.send(ShoppingEvent.ItemRemoved(label(item, resolver.value), item))
            result
        }
    }

    /** Adds the line back rather than resurrecting it: the removed document is gone on every device. */
    fun undoRemove(item: ShoppingItem) {
        edit { user, list ->
            writes.add(
                userId = user,
                listId = list,
                ingredient = item.ingredient,
                freeText = item.freeText,
                quantity = item.quantity,
                sourceRecipe = item.sourceRecipe,
            )
        }
    }

    fun clearChecked() {
        val count = state.value.checked.size
        edit { user, list ->
            val result = writes.clearChecked(user, list)
            if (result is AppResult.Success) _events.send(ShoppingEvent.CheckedCleared(count))
            result
        }
    }

    /** Typing anywhere drops the pick: the word on screen would otherwise stop matching the identifier. */
    fun onDraftChange(text: String) {
        draft.value = ShoppingDraftUi(text = text, suggestions = suggest(text))
    }

    fun onPick(suggestion: IngredientSuggestion) {
        draft.value = ShoppingDraftUi(text = suggestion.label, picked = suggestion)
    }

    fun add() {
        val current = draft.value
        val text = current.text.trim()
        val picked = current.picked
        if (picked == null && text.isEmpty()) return
        val dispatched =
            edit { user, list ->
                writes.add(
                    userId = user,
                    listId = list,
                    ingredient = picked?.id,
                    freeText = if (picked == null) text else null,
                )
            }
        // Only once the write is on its way: clearing first loses the line the user typed while
        // the default list was still resolving.
        if (dispatched) draft.value = ShoppingDraftUi()
    }

    /**
     * Both streams of the list, as the port's contract requires: the data one goes quiet when the
     * listener fails, so an empty screen without this second collector would read as an empty list.
     */
    private fun watchItems(
        userId: UserId,
        listId: ShoppingListId,
    ) {
        viewModelScope.launch(dispatchers.default) {
            reads.items(userId, listId).collect { loaded ->
                items.value = loaded
                itemsError.value = null
                itemsAnswered.value = true
            }
        }
        viewModelScope.launch(dispatchers.default) {
            reads.items.errors(userId, listId).collect { error ->
                itemsError.value = error.describe()
                itemsAnswered.value = true
            }
        }
    }

    /** Rebuilt from every source at once: a resolver missing one of them answers with an id. */
    private fun rebuildResolver() {
        resolver.value =
            LabelResolver(
                terms = units.value,
                ingredients = catalogue.value,
                taxonomies = vocabularies.value,
                languageTags = languageTags,
            )
    }

    /** The unit vocabulary, as the detail screen watches it: a quantity is not a bare number. */
    private fun watchUnits() {
        viewModelScope.launch(dispatchers.default) {
            // collectLatest and coroutineScope: a re-emission replaces the per-taxonomy
            // collectors rather than adding a second one for the same taxonomy.
            reads.taxonomies().collectLatest { published ->
                vocabularies.value = published
                rebuildResolver()
                coroutineScope {
                    published.filter { it.purpose == TaxonomyPurpose.UNITS }.forEach { unit ->
                        launch {
                            reads.taxonomy(unit.id).collect { terms ->
                                units.value = terms
                                rebuildResolver()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun watchCatalogue() {
        viewModelScope.launch(dispatchers.default) {
            reads.ingredients().collect { loaded ->
                catalogue.value = loaded
                rebuildResolver()
                catalogueError.value = null
            }
        }
        viewModelScope.launch(dispatchers.default) {
            reads.ingredients.errors().collect { error ->
                catalogueError.value = error.describe()
            }
        }
    }

    /** Pure: it takes what every source says and produces the one state that follows from it. */
    private fun project(
        lines: LinesState,
        catalogueError: UiText?,
        draft: ShoppingDraftUi,
        listName: String,
        writeFailure: UiText?,
    ): ShoppingUiState =
        ShoppingUiState(
            listName = listName,
            unchecked = lines.lines.filterNot(ShoppingItemUi::checked),
            checked = lines.lines.filter(ShoppingItemUi::checked),
            draft = draft,
            isLoading = lines.isLoading,
            failedToLoad = lines.failedToLoad,
            // A write that was rejected is the newest thing that happened, so it speaks first.
            error = writeFailure ?: lines.error ?: catalogueError,
        )

    private fun toUi(
        item: ShoppingItem,
        labels: LabelResolver,
    ): ShoppingItemUi =
        ShoppingItemUi(
            id = item.id,
            label = label(item, labels),
            quantity = item.quantity?.let { amount -> formatQuantity(amount, labels) },
            fromCatalogue = item.ingredient != null,
            checked = item.checked,
        )

    /** A catalogue miss renders the identifier: ugly and honest beats a placeholder that hides it. */
    private fun label(
        item: ShoppingItem,
        labels: LabelResolver,
    ): String = item.freeText ?: item.ingredient?.let { id -> labels.label(id) ?: id.value }.orEmpty()

    private fun formatQuantity(
        quantity: Quantity,
        labels: LabelResolver,
    ): String {
        val whole = quantity.amount.toLong()
        val amount = if (quantity.amount == whole.toDouble()) whole.toString() else quantity.amount.toString()
        val unit = quantity.unit?.let { ref -> labels.label(ref) ?: ref.term.value }
        return listOfNotNull(amount, unit).joinToString(" ")
    }

    private fun suggest(query: String): List<IngredientSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return catalogue.value
            .mapNotNull { ingredient ->
                resolver.value.label(ingredient.id)?.let { label -> IngredientSuggestion(ingredient.id, label) }
            }.filter { suggestion -> suggestion.label.contains(trimmed, ignoreCase = true) }
            .take(SUGGESTION_LIMIT)
    }

    /**
     * Every write goes through here, so no action can forget to report its failure. Returns
     * whether it dispatched at all: until the default list resolves there is nothing to write
     * to, and a caller that has taken something from the user needs to know that.
     */
    private fun edit(block: suspend (UserId, ShoppingListId) -> AppResult<*>): Boolean {
        val user = userId.value ?: return false
        val list = listId.value ?: return false
        viewModelScope.launch(dispatchers.default) {
            // A write that lands clears the last one that did not: the banner belongs to the
            // most recent attempt, not to the first that ever failed.
            when (val result = block(user, list)) {
                is AppResult.Failure -> writeFailure.value = result.error.describe()
                is AppResult.Success -> writeFailure.value = null
            }
        }
        return true
    }
}

/** One-shot announcements. They never live in the state: a snackbar shown twice is a bug. */
sealed interface ShoppingEvent {
    /** [restore] travels with the offer so a second removal cannot steal the first one's undo. */
    data class ItemRemoved(
        val label: String,
        val restore: ShoppingItem,
    ) : ShoppingEvent

    data class CheckedCleared(val count: Int) : ShoppingEvent
}

private const val SUGGESTION_LIMIT = 6

/** The cause is dropped on purpose: it can carry paths and identifiers, and this ends up on screen. */
private fun AppError.describe(): UiText =
    when (this) {
        is AppError.Network -> UiText.of(Res.string.error_no_connection)
        is AppError.Timeout -> UiText.of(Res.string.error_timeout)
        is AppError.Unauthorized -> UiText.of(Res.string.error_unauthorized_list)
        is AppError.NotFound -> UiText.of(Res.string.error_not_found, resource)
        is AppError.Validation -> UiText.of(Res.string.error_invalid_field, field, reason)
        is AppError.Unknown -> UiText.of(Res.string.error_unknown)
    }

/** What the item listener has produced so far, as one value rather than four fields. */
private data class LinesState(
    val lines: List<ShoppingItemUi> = emptyList(),
    val error: UiText? = null,
    val isLoading: Boolean = true,
    val failedToLoad: Boolean = false,
)
