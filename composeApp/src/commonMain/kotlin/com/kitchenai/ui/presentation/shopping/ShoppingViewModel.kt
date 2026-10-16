package com.kitchenai.ui.presentation.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.presentation.common.foldForSearch
import com.kitchenai.ui.presentation.common.wordFor
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_list
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
 * The caches below are `MutableStateFlow` rather than plain fields, but not for thread safety:
 * every coroutine this class starts runs confined to Main, same as the public methods that read
 * them. They stay flows so `.value` reads are uniform across the class; see #147 for whether
 * that uniformity is worth keeping.
 */
class ShoppingViewModel(
    private val ensureDefaultShoppingList: EnsureDefaultShoppingListUseCase,
    private val observeKitchen: ObserveKitchenUseCase,
    private val reads: ShoppingReadsDelegate,
    private val writes: ShoppingWritesDelegate,
) : ViewModel() {
    // Buffered: an undo offer emitted while the screen is recomposing must wait, not disappear.
    private val _events = Channel<ShoppingEvent>(Channel.BUFFERED)
    val events: Flow<ShoppingEvent> = _events.receiveAsFlow()

    private var started = false

    // Written by the bootstrap coroutine and read by every public method, so not plain fields.
    // The kitchen can change while this screen is open (joining another one), so it is a
    // listener, not a value captured once — every default-list bootstrap below re-runs for it.
    private val kitchenId = MutableStateFlow<KitchenId?>(null)

    // Set together, only once a list has actually resolved for that exact kitchen: kitchenId
    // updates as soon as the kitchen listener emits, but the matching list id only arrives once
    // ensureDefaultShoppingList resolves for it. Reading them as two independent flows in edit()
    // let a kitchen change pair the new kitchen with the previous one's list id.
    private val activeList = MutableStateFlow<Pair<KitchenId, ShoppingListId>?>(null)

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

    // Lines ticked a moment ago, with the row they held: they stay there in "To buy" until the
    // delay ends, because the stream itself already sorts a ticked line to the bottom.
    private val settling = MutableStateFlow<Map<ShoppingItemId, Int>>(emptyMap())
    private val settleJobs = mutableMapOf<ShoppingItemId, Job>()

    // User-owned, so they belong to the screen rather than to a listener.
    private val draft = MutableStateFlow(ShoppingDraftUi())
    private val listName = MutableStateFlow("")

    /** A rejected write outlives the next emission: the listener echo must not wipe the reason. */
    private val writeFailure = MutableStateFlow<UiText?>(null)

    private val lines =
        combine(items, itemsError, itemsAnswered, resolver, units) { loaded, error, answered, labels, unitTerms ->
            LinesState(
                lines = loaded.orEmpty().map { item -> item.toUi(labels) },
                units = unitTerms.map { term -> term.ref to labels.wordFor(term.ref) },
                error = error,
                isLoading = !answered,
                failedToLoad = error != null && loaded == null,
            )
        }.combine(settling) { loaded, settled -> loaded.copy(settling = settled) }

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
        this.languageTags = languageTags
        rebuildResolver()
        listName.value = defaultListName
        watchCatalogue()
        watchUnits()
        watchKitchen(userId)
        watchDefaultList(defaultListName)
    }

    private fun watchKitchen(userId: UserId) {
        viewModelScope.launch {
            observeKitchen(userId).collect { kitchen -> kitchenId.value = kitchen.id }
        }
        viewModelScope.launch {
            observeKitchen.errors(userId).collect { error ->
                itemsError.value = error.describe(Res.string.error_unauthorized_list)
                itemsAnswered.value = true
            }
        }
    }

    /**
     * Re-runs the whole bootstrap — ensuring the default list, then its items — for every kitchen
     * the user is in over the life of the screen. [collectLatest] cancels the previous kitchen's
     * item listeners (started inside the same [coroutineScope]) before this runs for the new one.
     */
    private fun watchDefaultList(defaultListName: String) {
        viewModelScope.launch {
            kitchenId.filterNotNull().collectLatest { kitchen ->
                val labels = languageTags.take(1).associateWith { defaultListName }
                when (val list = ensureDefaultShoppingList(kitchen, labels)) {
                    is AppResult.Failure -> {
                        itemsError.value = list.error.describe(Res.string.error_unauthorized_list)
                        itemsAnswered.value = true
                    }
                    is AppResult.Success -> {
                        activeList.value = kitchen to list.data
                        coroutineScope {
                            launch {
                                reads.items(kitchen, list.data).collect { loaded ->
                                    items.value = loaded
                                    itemsError.value = null
                                    itemsAnswered.value = true
                                }
                            }
                            launch {
                                reads.items.errors(kitchen, list.data).collect { error ->
                                    itemsError.value = error.describe(Res.string.error_unauthorized_list)
                                    itemsAnswered.value = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Assigns, never toggles: the value on screen is the value written, on both devices. */
    fun setChecked(
        itemId: ShoppingItemId,
        checked: Boolean,
    ) {
        val row = settling.value[itemId] ?: state.value.unchecked.indexOfFirst { it.id == itemId }
        val dispatched = edit { kitchen, list -> writes.setChecked(kitchen, list, itemId, checked) }
        if (!dispatched) return
        settleJobs.remove(itemId)?.cancel()
        if (!checked || row < 0) {
            settling.value = settling.value - itemId
            return
        }
        settling.value = settling.value + (itemId to row)
        settleJobs[itemId] =
            viewModelScope.launch {
                delay(SETTLE_MILLIS)
                settling.value = settling.value - itemId
                settleJobs.remove(itemId)
            }
    }

    /** Replaces the quantity of a line, which is what lets a line typed by hand reach the pantry. */
    fun setQuantity(
        itemId: ShoppingItemId,
        amount: Double,
        unit: TermRef?,
    ) {
        edit { kitchen, list -> writes.setQuantity(kitchen, list, itemId, Quantity(amount, unit)) }
    }

    fun remove(itemId: ShoppingItemId) {
        val item = items.value?.firstOrNull { it.id == itemId } ?: return
        edit { kitchen, list ->
            val result = writes.remove(kitchen, list, itemId)
            if (result is AppResult.Success) _events.send(ShoppingEvent.ItemRemoved(item.label(resolver.value), item))
            result
        }
    }

    /** Adds the line back rather than resurrecting it: the removed document is gone on every device. */
    fun undoRemove(item: ShoppingItem) {
        edit { kitchen, list ->
            writes.add(
                kitchenId = kitchen,
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
        edit { kitchen, list ->
            val result = writes.clearChecked(kitchen, list)
            if (result is AppResult.Success) _events.send(ShoppingEvent.CheckedCleared(count))
            result
        }
    }

    fun moveCheckedToPantry() {
        edit { kitchen, list ->
            val result = writes.moveCheckedToPantry(kitchen, list)
            if (result is AppResult.Success) {
                _events.send(ShoppingEvent.MovedToPantry(result.data.moved, result.data.skipped))
            }
            result
        }
    }

    /**
     * Typing anywhere drops the pick: the word on screen would otherwise stop matching the
     * identifier. Suggestions look at the name alone, so "2 kg pota" still offers potatoes.
     */
    fun onDraftChange(text: String) {
        draft.value = draft.value.copy(text = text, picked = null, suggestions = suggest(parsed(text).name))
    }

    /** A quantity typed before the pick ("2 kg pota") is kept, unless one was set by hand. */
    fun onPick(suggestion: IngredientSuggestion) {
        val current = draft.value
        val typed = parsed(current.text)
        val keepsTyped = current.amount == null && typed.amount != null
        draft.value =
            current.copy(
                text = suggestion.label,
                picked = suggestion,
                suggestions = emptyList(),
                amount = if (keepsTyped) typed.amount else current.amount,
                unit = if (keepsTyped) typed.unit else current.unit,
            )
    }

    /** Null amount is an empty or half-typed field: the line is then added without a quantity. */
    fun onQuantityChange(
        amount: Double?,
        unit: TermRef?,
    ) {
        draft.value = draft.value.copy(amount = amount, unit = unit)
    }

    fun add() {
        val current = draft.value
        val picked = current.picked
        // "2 kg potatoes" in the one field; an amount set by hand in the quantity row wins.
        val typed = if (picked == null) parsed(current.text) else QuickAddText(current.text.trim())
        val text = typed.name
        if (picked == null && text.isEmpty()) return
        val amount = current.amount ?: typed.amount
        val unit = if (current.amount != null) current.unit else typed.unit
        // Zero or less is not an amount: it would be refused downstream, so the line goes in bare.
        val quantity = amount?.takeIf { it > 0.0 }?.let { held -> Quantity(held, unit) }
        val dispatched =
            edit { kitchen, list ->
                writes.add(
                    kitchenId = kitchen,
                    listId = list,
                    ingredient = picked?.id,
                    freeText = if (picked == null) text else null,
                    quantity = quantity,
                )
            }
        // Only once the write is on its way: clearing first loses the line the user typed while
        // the default list was still resolving.
        if (dispatched) draft.value = ShoppingDraftUi(revision = current.revision + 1)
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            reads.ingredients().collect { loaded ->
                catalogue.value = loaded
                rebuildResolver()
                catalogueError.value = null
            }
        }
        viewModelScope.launch {
            reads.ingredients.errors().collect { error ->
                catalogueError.value = error.describe(Res.string.error_unauthorized_list)
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
            unchecked = lines.lines.filterNot(ShoppingItemUi::checked).withSettling(lines),
            checked = lines.lines.filter { line -> line.checked && line.id !in lines.settling },
            draft = draft,
            units = lines.units,
            isLoading = lines.isLoading,
            failedToLoad = lines.failedToLoad,
            // A write that was rejected is the newest thing that happened, so it speaks first.
            error = writeFailure ?: lines.error ?: catalogueError,
        )

    private fun suggest(query: String): List<IngredientSuggestion> {
        val needle = foldForSearch(query).trim()
        if (needle.isEmpty()) return emptyList()
        val onList =
            items.value.orEmpty().filterNot {
                    line ->
                line.checked
            }.mapNotNullTo(mutableSetOf()) { it.ingredient }
        return catalogue.value
            .mapNotNull { ingredient ->
                resolver.value.label(ingredient.id)?.let { label ->
                    IngredientSuggestion(ingredient.id, label, onList = ingredient.id in onList)
                }
            }.filter { suggestion -> foldForSearch(suggestion.label).contains(needle) }
            .take(SUGGESTION_LIMIT)
    }

    /** Every spelling of every unit, singular and plural in any language, for the one-line parse. */
    private fun parsed(text: String): QuickAddText =
        parseQuickAdd(
            text,
            units.value.map {
                    term ->
                term.ref to (term.labels.values + term.pluralLabels.values).toSet()
            },
        )

    /**
     * Every write goes through here, so no action can forget to report its failure. Returns
     * whether it dispatched at all: until the default list resolves there is nothing to write
     * to, and a caller that has taken something from the user needs to know that.
     */
    private fun edit(block: suspend (KitchenId, ShoppingListId) -> AppResult<*>): Boolean {
        val (kitchen, list) = activeList.value ?: return false
        viewModelScope.launch {
            // A write that lands clears the last one that did not: the banner belongs to the
            // most recent attempt, not to the first that ever failed.
            when (val result = block(kitchen, list)) {
                is AppResult.Failure -> writeFailure.value = result.error.describe(Res.string.error_unauthorized_list)
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

    data class MovedToPantry(val moved: Int, val skipped: Int) : ShoppingEvent
}

private const val SUGGESTION_LIMIT = 6

/** How long a ticked line stays in place before it drops to the cart: long enough to see it land. */
internal const val SETTLE_MILLIS = 600L

/** What the item listener has produced so far, as one value rather than four fields. */
private data class LinesState(
    val lines: List<ShoppingItemUi> = emptyList(),
    val units: List<Pair<TermRef, String>> = emptyList(),
    val error: UiText? = null,
    val isLoading: Boolean = true,
    val failedToLoad: Boolean = false,
    val settling: Map<ShoppingItemId, Int> = emptyMap(),
)

/** Puts the just-ticked lines back at the row they held, in the order they held it. */
private fun List<ShoppingItemUi>.withSettling(lines: LinesState): List<ShoppingItemUi> {
    val held = lines.lines.filter { line -> line.checked && line.id in lines.settling }
    return held.sortedBy { line -> lines.settling.getValue(line.id) }.fold(this) { rows, line ->
        rows.toMutableList().apply { add(lines.settling.getValue(line.id).coerceIn(0, rows.size), line) }
    }
}
