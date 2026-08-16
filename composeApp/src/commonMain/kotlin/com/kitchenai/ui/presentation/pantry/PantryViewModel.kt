package com.kitchenai.ui.presentation.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.freshnessAt
import com.kitchenai.ui.designsystem.format.formatQuantity
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_not_found
import com.kitchenai.ui.resources.error_timeout
import com.kitchenai.ui.resources.error_unauthorized_own_data
import com.kitchenai.ui.resources.error_unknown
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

    // Without these the resolver has no per-taxonomy default language, so a label the device's
    // own tags do not cover renders as its identifier even when the catalogue has a good one.
    private val vocabularies = MutableStateFlow<List<Taxonomy>>(emptyList())

    // One per listener. A single field meant a catalogue that recovered cleared a pantry that
    // had not, and clearing only the pantry's left the other three banners up for good.
    private val errors = MutableStateFlow<Map<Source, UiText>>(emptyMap())

    private var started = false
    private var user: UserId? = null
    private var languageTags: List<String> = emptyList()

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
        viewModelScope.launch {
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
        viewModelScope.launch {
            // The row is captured before the write: undo restores it under its own id rather than
            // adding a second holding of the same ingredient.
            val original = held.value?.firstOrNull { candidate -> candidate.id == item.id } ?: return@launch
            when (val result = writes.remove(userId, item.id)) {
                is AppResult.Failure -> result.reportFailure()
                is AppResult.Success -> _events.send(PantryEvent.ItemRemoved(item.name, original))
            }
        }
    }

    /** Takes the row to restore rather than remembering one: two quick removals do not race. */
    fun undoRemove(item: PantryItem) {
        val userId = user ?: return
        viewModelScope.launch {
            writes.update(userId, item).reportFailure()
        }
    }

    private fun watchPantry(userId: UserId) {
        viewModelScope.launch {
            reads.pantry(userId).collect { items ->
                held.value = items
                // Clearing belongs here rather than in render(): re-emitting an unchanged list
                // leaves the combine silent, and a listener that recovered still recovered.
                recovered(Source.PANTRY)
            }
        }
        viewModelScope.launch {
            reads.pantry.errors(userId).collect { error -> fail(Source.PANTRY, error) }
        }
    }

    private fun watchCatalogue() {
        viewModelScope.launch {
            reads.ingredients().collect { ingredients ->
                catalogue.value = ingredients
                recovered(Source.CATALOGUE)
            }
        }
        viewModelScope.launch {
            reads.ingredients.errors().collect { error -> fail(Source.CATALOGUE, error) }
        }
    }

    /**
     * The vocabularies watched are the ones the data already points at. Nothing in the catalogue
     * says which taxonomy holds units and which holds storage places, and naming one here would
     * be the contextual constant this project does not allow.
     */
    private fun watchVocabulary() {
        val ids =
            combine(held, catalogue, vocabularies) { items, ingredients, declared ->
                // The catalogue declaring what a vocabulary is for is the only thing that lets a
                // fresh pantry offer a storage location: derived from the user's own rows, the
                // first one could never get one and none would ever be discovered.
                declared.purposeful() + ingredients.unitTaxonomies() + items.orEmpty().termTaxonomies()
            }.distinctUntilChanged()

        viewModelScope.launch {
            ids.flatMapLatest(::termsOf).collect { terms ->
                vocabulary.value = terms
                recovered(Source.TERMS)
            }
        }
        viewModelScope.launch {
            reads.taxonomies().collect { loaded ->
                vocabularies.value = loaded
                recovered(Source.TAXONOMIES)
            }
        }
        viewModelScope.launch {
            reads.taxonomies.errors().collect { error -> fail(Source.TAXONOMIES, error) }
        }
        viewModelScope.launch {
            ids.flatMapLatest { watched ->
                watched.map { id -> reads.taxonomy.errors(id) }.merge()
            }.collect { error -> fail(Source.TERMS, error) }
        }
    }

    private fun termsOf(ids: Set<TaxonomyId>): Flow<List<Term>> =
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ids.map { id -> reads.taxonomy(id) }) { loaded -> loaded.toList().flatten() }
        }

    private fun watchProjection() {
        viewModelScope.launch {
            combine(held, catalogue, vocabulary, vocabularies, errors, ::Projection)
                .collect { projection -> render(projection) }
        }
    }

    private fun render(projection: Projection) {
        val resolver =
            LabelResolver(
                terms = projection.terms,
                ingredients = projection.ingredients,
                taxonomies = projection.taxonomies,
                languageTags = languageTags,
            )
        val items = projection.items
        val ingredients = projection.ingredients
        val terms = projection.terms
        val taxonomies = projection.taxonomies
        // A stable order, so the banner does not flicker between two broken listeners.
        val message = Source.entries.firstNotNullOfOrNull { source -> projection.errors[source] }
        val now = writes.time.now()
        _state.update { current ->
            current.copy(
                items = items.orEmpty().map { item -> item.toUi(resolver, now) },
                // Answered means emitted or failed. Only the pantry listener decides this: a
                // broken catalogue leaves the rows loading, it does not replace them.
                isLoading = items == null && Source.PANTRY !in projection.errors,
                error = message,
                ingredients = ingredients.map { ingredient -> ingredient.id to resolver.nameOf(ingredient) },
                units = terms.optionsIn(taxonomies.of(TaxonomyPurpose.UNITS) + ingredients.unitTaxonomies(), resolver),
                locations =
                    terms.optionsIn(
                        taxonomies.of(TaxonomyPurpose.STORAGE_LOCATIONS) + items.orEmpty().locationTaxonomies(),
                        resolver,
                    ),
            )
        }
    }

    /**
     * `update` rather than a read and a write: four collectors on a real pool touch this map,
     * and `value = value + …` loses whichever lands second. The projection reads it from here.
     */
    private fun fail(
        source: Source,
        error: AppError,
    ) = errors.update { open -> open + (source to error.describe()) }

    /** That listener spoke again, so whatever it was complaining about is over. */
    private fun recovered(source: Source) = errors.update { open -> open - source }

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
        // From the draft, not from the row: the sheet lets the ingredient be changed, and
        // keeping the old one silently discarded that edit.
        ingredient = draft.ingredient,
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
        quantityLabel = formatQuantity(quantity.amount, unitLabel),
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

/** The vocabularies the catalogue declares a use for, whatever the user happens to hold. */
private fun List<Taxonomy>.purposeful(): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { taxonomy -> taxonomy.id.takeIf { taxonomy.purpose != null } }

private fun List<Taxonomy>.of(purpose: TaxonomyPurpose): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { taxonomy -> taxonomy.id.takeIf { taxonomy.purpose == purpose } }

/** The vocabularies the pantry itself already uses, for amounts and for storage places. */
private fun List<PantryItem>.termTaxonomies(): Set<TaxonomyId> =
    flatMapTo(mutableSetOf()) { item -> listOfNotNull(item.quantity.unit?.taxonomy, item.location?.taxonomy) }

private fun List<PantryItem>.locationTaxonomies(): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { item -> item.location?.taxonomy }

/** The cause is dropped on purpose: it can carry paths and identifiers, and this ends up on screen. */
private fun AppError.describe(): UiText =
    when (this) {
        is AppError.Network -> UiText.of(Res.string.error_no_connection)
        is AppError.Timeout -> UiText.of(Res.string.error_timeout)
        is AppError.Unauthorized -> UiText.of(Res.string.error_unauthorized_own_data)
        is AppError.NotFound -> UiText.of(Res.string.error_not_found, resource)
        is AppError.Validation -> UiText.of(Res.string.error_invalid_field, field, reason)
        is AppError.Unknown -> UiText.of(Res.string.error_unknown)
    }

/** The four sources a rendered pantry needs, so the combine stays one value rather than four. */
private data class Projection(
    val items: List<PantryItem>?,
    val ingredients: List<Ingredient>,
    val terms: List<Term>,
    val taxonomies: List<Taxonomy>,
    // In the projection, not written to the state on the side: a listener that fails before its
    // first emission changes nothing else, and the screen would spin for ever waiting for it.
    val errors: Map<Source, UiText>,
)

/** The listeners this screen keeps open, each owning its own message. */
private enum class Source {
    PANTRY,
    CATALOGUE,
    TERMS,
    TAXONOMIES,
}
