package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.TermRef
import kotlin.time.Instant

/** What a blank amount means: the sheet is for "I have some", not for counting salt. */
internal const val DEFAULT_AMOUNT = 1.0

/**
 * What the sheet can save right now, or null when it cannot: a malformed amount is never
 * guessed at, and a blank one is [DEFAULT_AMOUNT]. [text] is only a source when nothing is
 * [picked]; [unit] is the one the sheet shows, see [shownUnit].
 */
internal fun draftOf(
    picked: IngredientOption?,
    text: String,
    amount: Double?,
    malformed: Boolean,
    unit: TermRef?,
    location: TermRef?,
    expiresAt: Instant?,
): PantryItemDraft? {
    if (malformed) return null
    val typed = text.trim().takeIf { it.isNotEmpty() }
    if (picked == null && typed == null) return null
    return PantryItemDraft(
        ingredient = picked?.id,
        freeText = if (picked == null) typed else null,
        amount = amount ?: DEFAULT_AMOUNT,
        unit = unit,
        location = location,
        expiresAt = expiresAt,
    )
}

/**
 * The unit the sheet shows: the one reported, else the ingredient's own, else the first on offer.
 * What is saved is what is shown, never a unit the user could not see.
 */
internal fun shownUnit(
    reportedId: String?,
    picked: IngredientOption?,
    units: List<Pair<TermRef, String>>,
): TermRef? =
    units.firstOrNull { (ref, _) -> ref.term.value == reportedId }?.first
        ?: picked?.defaultUnit?.takeIf { own -> units.any { (ref, _) -> ref == own } }
        ?: units.firstOrNull()?.first

/**
 * The option a saved id stands for. A holding whose ingredient the catalogue no longer lists
 * still resolves, under the name its row showed, so editing it does not turn it into free text.
 */
internal fun pickedOption(
    pickedId: String?,
    catalogue: List<IngredientOption>,
    editing: PantryItemUi?,
): IngredientOption? {
    if (pickedId == null) return null
    catalogue.firstOrNull { option -> option.id.value == pickedId }?.let { found -> return found }
    val held = editing?.ingredient?.takeIf { id -> id.value == pickedId } ?: return null
    return IngredientOption(held, editing.name, null)
}
