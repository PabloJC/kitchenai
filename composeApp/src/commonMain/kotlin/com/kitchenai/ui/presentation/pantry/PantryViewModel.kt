package com.kitchenai.ui.presentation.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.freshnessAt
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The pantry projected for the screen: holdings resolved into words, plus the four actions that
 * change them.
 *
 * It computes nothing it could ask for. Merging quantities belongs to [AddPantryItem] and the
 * order of the list to [ObservePantry]; if this class ever compares two amounts, the logic has
 * moved into the wrong layer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModel(
    private val reads: PantryReads,
    private val writes: PantryWrites,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(PantryUiState())
    val state: StateFlow<PantryUiState> = _state.asStateFlow()

    private val _events = Channel<PantryEvent>(Channel.BUFFERED)
    val events: Flow<PantryEvent> = _events.receiveAsFlow()

    // Null means the pantry listener has not emitted yet, which is what loading is. An empty
    // list from a listener is an empty pantry, and the two must never render the same.
    private val held = MutableStateFlow<List<PantryItem>?>(null)
    private val catalogue = MutableStateFlow<List<Ingredient>>(emptyList())
    private val vocabulary = MutableStateFlow<List<Term>>(emptyList())

    private var started = false
    private var user: UserId? = null
    private var languageTags: List<String> = emptyList()
    private var lastRemoved: PantryItem? = null

    /** Idempotent: a configuration change composes the screen again, not a second set of listeners. */
    fun start(
        userId: UserId,
        languageTags: List<String>,
    ) {
        if (started) return
        started = true
        user = userId
        this.languageTags = languageTags
        watchPantry(userId)
        watchCatalogue()
        watchVocabulary()
        watchProjection()
    }

    fun openEditor(item: PantryItemUi?) {
        _state.update { current -> current.copy(editing = item, isEditorOpen = true) }
    }

    fun closeEditor() {
        _state.update { current -> current.copy(editing = null, isEditorOpen = false) }
    }

    /**
     * Adds or overwrites, depending on the row the sheet was opened on. Neither write waits for
     * the network, which is what makes the list move while offline.
     */
    fun save(draft: PantryItemDraft) {
        val userId = user ?: return
        val editing = _state.value.editing
        closeEditor()
        viewModelScope.launch(dispatchers.default) {
            val quantity = Quantity(draft.amount, draft.unit)
            val result =
                if (editing == null) {
                    writes.add(userId, draft.ingredient, quantity, draft.location, draft.expiresAt)
                } else {
                    writes.update(userId, editing.applied(draft, writes.time.now()))
                }
            result.reportFailure()
        }
    }

    fun remove(item: PantryItemUi) {
        val userId = user ?: return
        viewModelScope.launch(dispatchers.default) {
            // The row is captured before the write: undo restores it under its own id rather than
            // adding a second holding of the same ingredient.
            val original = held.value?.firstOrNull { candidate -> candidate.id == item.id }
            when (val result = writes.remove(userId, item.id)) {
                is AppResult.Failure -> result.reportFailure()
                is AppResult.Success -> {
                    lastRemoved = original
                    _events.send(PantryEvent.ItemRemoved(item.name))
                }
            }
        }
    }

    fun undoRemove() {
        val userId = user ?: return
        val original = lastRemoved ?: return
        lastRemoved = null
        viewModelScope.launch(dispatchers.default) {
            writes.update(userId, original).reportFailure()
        }
    }

    private fun watchPantry(userId: UserId) {
        viewModelScope.launch(dispatchers.default) {
            reads.pantry(userId).collect { items -> held.value = items }
        }
        viewModelScope.launch(dispatchers.default) {
            reads.pantry.errors(userId).collect(::fail)
        }
    }

    private fun watchCatalogue() {
        viewModelScope.launch(dispatchers.default) {
            reads.ingredients().collect { ingredients -> catalogue.value = ingredients }
        }
        viewModelScope.launch(dispatchers.default) {
            reads.ingredients.errors().collect(::fail)
        }
    }

    /**
     * The vocabularies watched are the ones the data already points at. Nothing in the catalogue
     * says which taxonomy holds units and which holds storage places, and naming one here would
     * be the contextual constant this project does not allow.
     */
    private fun watchVocabulary() {
        val ids =
            combine(held, catalogue) { items, ingredients ->
                ingredients.unitTaxonomies() + items.orEmpty().termTaxonomies()
            }.distinctUntilChanged()

        viewModelScope.launch(dispatchers.default) {
            ids.flatMapLatest(::termsOf).collect { terms -> vocabulary.value = terms }
        }
        viewModelScope.launch(dispatchers.default) {
            ids.flatMapLatest { watched ->
                watched.map { id -> reads.taxonomy.errors(id) }.merge()
            }.collect(::fail)
        }
    }

    private fun termsOf(ids: Set<TaxonomyId>): Flow<List<Term>> =
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ids.map { id -> reads.taxonomy(id) }) { loaded -> loaded.toList().flatten() }
        }

    private fun watchProjection() {
        viewModelScope.launch(dispatchers.default) {
            combine(held, catalogue, vocabulary) { items, ingredients, terms ->
                Triple(items, ingredients, terms)
            }.collect { (items, ingredients, terms) -> render(items, ingredients, terms) }
        }
    }

    private fun render(
        items: List<PantryItem>?,
        ingredients: List<Ingredient>,
        terms: List<Term>,
    ) {
        val resolver = LabelResolver(terms = terms, ingredients = ingredients, languageTags = languageTags)
        val now = writes.time.now()
        _state.update { current ->
            current.copy(
                items = items.orEmpty().map { item -> item.toUi(resolver, now) },
                isLoading = items == null,
                ingredients = ingredients.map { ingredient -> ingredient.id to resolver.nameOf(ingredient) },
                units = terms.optionsIn(ingredients.unitTaxonomies(), resolver),
                locations = terms.optionsIn(items.orEmpty().locationTaxonomies(), resolver),
            )
        }
    }

    private fun fail(error: AppError) {
        _state.update { current -> current.copy(error = error.describe(), isLoading = false) }
    }

    private suspend fun AppResult<Any>.reportFailure() {
        if (this is AppResult.Failure) _events.send(PantryEvent.SaveFailed(error.describe()))
    }
}

/**
 * How long before its expiry date a holding counts as urgent. A product setting rather than a
 * domain rule, which is why `freshnessAt` takes it instead of knowing it.
 */
private val ExpiringSoonWindow = 3.days

private fun PantryItemUi.applied(
    draft: PantryItemDraft,
    now: Instant,
): PantryItem =
    PantryItem(
        id = id,
        ingredient = ingredient,
        quantity = Quantity(draft.amount, draft.unit),
        location = draft.location,
        expiresAt = draft.expiresAt,
        updatedAt = now,
    )

private fun PantryItem.toUi(
    resolver: LabelResolver,
    now: Instant,
): PantryItemUi {
    val unitLabel = quantity.unit?.let { unit -> resolver.wordFor(unit) }
    return PantryItemUi(
        id = id,
        ingredient = ingredient,
        name = resolver.label(ingredient) ?: ingredient.value,
        quantityLabel = listOfNotNull(quantity.amount.trimmed(), unitLabel).joinToString(" "),
        amount = quantity.amount,
        unit = quantity.unit,
        location = location,
        locationLabel = location?.let { place -> resolver.wordFor(place) },
        expiresAt = expiresAt,
        freshness = freshnessAt(now, ExpiringSoonWindow),
    )
}

/** A miss renders the identifier: ugly and honest beats a placeholder hiding a missing label. */
private fun LabelResolver.wordFor(ref: TermRef): String = label(ref) ?: ref.term.value

private fun LabelResolver.nameOf(ingredient: Ingredient): String = label(ingredient.id) ?: ingredient.id.value

private fun List<Term>.optionsIn(
    taxonomies: Set<TaxonomyId>,
    resolver: LabelResolver,
): List<Pair<TermRef, String>> =
    filter { term -> term.ref.taxonomy in taxonomies }
        .map { term -> term.ref to resolver.wordFor(term.ref) }

/** The vocabularies the catalogue itself measures ingredients in. */
private fun List<Ingredient>.unitTaxonomies(): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { ingredient -> ingredient.defaultUnit?.taxonomy }

/** The vocabularies the pantry itself already uses, for amounts and for storage places. */
private fun List<PantryItem>.termTaxonomies(): Set<TaxonomyId> =
    flatMapTo(mutableSetOf()) { item -> listOfNotNull(item.quantity.unit?.taxonomy, item.location?.taxonomy) }

private fun List<PantryItem>.locationTaxonomies(): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { item -> item.location?.taxonomy }

/** A whole number keeps no fraction: nobody writes "3.0 onions". */
private fun Double.trimmed(): String = if (this == toLong().toDouble()) toLong().toString() else toString()

/** The cause is dropped on purpose: it can carry paths and identifiers, and this ends up on screen. */
private fun AppError.describe(): String =
    when (this) {
        is AppError.Network -> "No connection"
        is AppError.Unauthorized -> "This account is not allowed to read its own data"
        is AppError.NotFound -> "Cannot find $resource"
        is AppError.Validation -> "Invalid $field: $reason"
        is AppError.Unknown -> "Something went wrong"
    }
