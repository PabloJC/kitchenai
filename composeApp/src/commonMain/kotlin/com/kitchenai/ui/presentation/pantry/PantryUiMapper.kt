package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.freshnessAt
import com.kitchenai.ui.designsystem.format.formatQuantity
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.wordFor
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * How long before its expiry date a holding counts as urgent. A product setting rather than a
 * domain rule, which is why `freshnessAt` takes it instead of knowing it.
 */
internal val ExpiringSoonWindow = 3.days

internal fun PantryItemUi.applied(
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

internal fun PantryItem.toUi(
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

internal fun List<Term>.optionsIn(
    taxonomies: Set<TaxonomyId>,
    resolver: LabelResolver,
): List<Pair<TermRef, String>> =
    filter { term -> term.ref.taxonomy in taxonomies }
        .map { term -> term.ref to resolver.wordFor(term.ref) }

/** The vocabularies the catalogue itself measures ingredients in. */
internal fun List<Ingredient>.unitTaxonomies(): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { ingredient -> ingredient.defaultUnit?.taxonomy }

/** The vocabularies the catalogue declares a use for, whatever the user happens to hold. */
internal fun List<Taxonomy>.purposeful(): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { taxonomy -> taxonomy.id.takeIf { taxonomy.purpose != null } }

internal fun List<Taxonomy>.of(purpose: TaxonomyPurpose): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { taxonomy -> taxonomy.id.takeIf { taxonomy.purpose == purpose } }

/** The vocabularies the pantry itself already uses, for amounts and for storage places. */
internal fun List<PantryItem>.termTaxonomies(): Set<TaxonomyId> =
    flatMapTo(mutableSetOf()) { item -> listOfNotNull(item.quantity.unit?.taxonomy, item.location?.taxonomy) }

internal fun List<PantryItem>.locationTaxonomies(): Set<TaxonomyId> =
    mapNotNullTo(mutableSetOf()) { item -> item.location?.taxonomy }
