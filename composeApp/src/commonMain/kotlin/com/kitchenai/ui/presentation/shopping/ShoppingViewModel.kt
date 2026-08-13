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
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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
    private val _state = MutableStateFlow(ShoppingUiState())
    val state: StateFlow<ShoppingUiState> = _state.asStateFlow()

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
    private val resolver = MutableStateFlow(LabelResolver())

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
        resolver.value = LabelResolver(languageTags = languageTags)
        _state.update { it.copy(listName = defaultListName) }
        watchCatalogue(languageTags)
        viewModelScope.launch(dispatchers.default) {
            val labels = languageTags.take(1).associateWith { defaultListName }
            when (val list = ensureDefaultShoppingList(userId, labels)) {
                is AppResult.Failure -> fail(list.error)
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
            if (result is AppResult.Success) _events.send(ShoppingEvent.ItemRemoved(label(item), item))
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
        val count = _state.value.checked.size
        edit { user, list ->
            val result = writes.clearChecked(user, list)
            if (result is AppResult.Success) _events.send(ShoppingEvent.CheckedCleared(count))
            result
        }
    }

    /** Typing anywhere drops the pick: the word on screen would otherwise stop matching the identifier. */
    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = ShoppingDraftUi(text = text, suggestions = suggest(text))) }
    }

    fun onPick(suggestion: IngredientSuggestion) {
        _state.update { it.copy(draft = ShoppingDraftUi(text = suggestion.label, picked = suggestion)) }
    }

    fun add() {
        val draft = _state.value.draft
        val text = draft.text.trim()
        val picked = draft.picked
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
        if (dispatched) _state.update { it.copy(draft = ShoppingDraftUi()) }
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
                render()
            }
        }
        viewModelScope.launch(dispatchers.default) {
            reads.items.errors(userId, listId).collect { error -> fail(error) }
        }
    }

    private fun watchCatalogue(languageTags: List<String>) {
        viewModelScope.launch(dispatchers.default) {
            reads.ingredients().collect { loaded ->
                catalogue.value = loaded
                resolver.value = LabelResolver(ingredients = loaded, languageTags = languageTags)
                render()
            }
        }
        viewModelScope.launch(dispatchers.default) {
            reads.ingredients.errors().collect { error -> fail(error) }
        }
    }

    private fun render() {
        val loaded = items.value ?: return
        val lines = loaded.map(::toUi)
        _state.update {
            it.copy(
                unchecked = lines.filterNot(ShoppingItemUi::checked),
                checked = lines.filter(ShoppingItemUi::checked),
                isLoading = false,
                // A listener that emits again has recovered: the banner it raised goes with it.
                error = null,
            )
        }
    }

    private fun toUi(item: ShoppingItem): ShoppingItemUi =
        ShoppingItemUi(
            id = item.id,
            label = label(item),
            quantity = item.quantity?.let(::formatQuantity),
            // The recipe catalogue is not read here, so this is the identifier until a screen reads it.
            sourceRecipe = item.sourceRecipe?.value,
            fromCatalogue = item.ingredient != null,
            checked = item.checked,
        )

    /** A catalogue miss renders the identifier: ugly and honest beats a placeholder that hides it. */
    private fun label(item: ShoppingItem): String =
        item.freeText ?: item.ingredient?.let { id -> resolver.value.label(id) ?: id.value }.orEmpty()

    private fun formatQuantity(quantity: Quantity): String {
        val whole = quantity.amount.toLong()
        val amount = if (quantity.amount == whole.toDouble()) whole.toString() else quantity.amount.toString()
        val unit = quantity.unit?.let { ref -> resolver.value.label(ref) ?: ref.term.value }
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
            val result = block(user, list)
            if (result is AppResult.Failure) fail(result.error)
        }
        return true
    }

    // The lines already on screen stay there: a broken listener is not an emptied list.
    private fun fail(error: AppError) {
        _state.update { it.copy(isLoading = false, error = error.describe()) }
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
private fun AppError.describe(): String =
    when (this) {
        is AppError.Network -> "No connection"
        is AppError.Unauthorized -> "This account is not allowed to read this list"
        is AppError.NotFound -> "Cannot find $resource"
        is AppError.Validation -> "Invalid $field: $reason"
        is AppError.Unknown -> "Something went wrong"
    }
