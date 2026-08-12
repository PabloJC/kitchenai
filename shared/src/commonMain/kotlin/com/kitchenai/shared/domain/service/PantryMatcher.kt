package com.kitchenai.shared.domain.service

import com.kitchenai.shared.domain.model.CoveredIngredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.MissingIngredient
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeIngredient
import kotlin.time.Instant

/**
 * Compares a recipe with a pantry snapshot. The agent proposes; this verifies, which is why a
 * `covered` claim in a model response is never displayed.
 *
 * Pure, synchronous and total: [now] is a parameter rather than a clock, so the same inputs
 * always give the same match and every rule below is testable without a scheduler.
 */
object PantryMatcher {
    fun match(
        recipe: Recipe,
        pantry: List<PantryItem>,
        now: Instant,
    ): PantryMatch {
        val held = pantry.filterNot { it.hasExpiredAt(now) }.groupBy { it.ingredient }
        val covered = mutableListOf<CoveredIngredient>()
        val missing = mutableListOf<MissingIngredient>()
        val unverifiable = mutableListOf<RecipeIngredient>()
        for (line in recipe.ingredients) {
            when (val verdict = classify(line, held)) {
                is Verdict.Covered -> covered += CoveredIngredient(line, verdict.by)
                is Verdict.Missing -> missing += MissingIngredient(line, verdict.shortfall)
                Verdict.Unverifiable -> unverifiable += line
            }
        }
        return PantryMatch(recipe.id, covered, missing, unverifiable)
    }

    private fun classify(
        line: RecipeIngredient,
        held: Map<IngredientId, List<PantryItem>>,
    ): Verdict {
        // No catalogue id means a free-text line, and there is nothing to compare it against.
        val ingredient = line.ingredient ?: return Verdict.Unverifiable
        val holdings = held[ingredient].orEmpty()
        val required = line.quantity
        if (required == null) {
            // The recipe asks for no amount, so holding any of it at all is enough.
            return if (holdings.isEmpty()) Verdict.Missing(null) else Verdict.Covered(holdings.ids())
        }
        val comparable = holdings.filter { it.quantity.canCombineWith(required) }
        val available = comparable.sumOf { it.quantity.amount }
        return when {
            available >= required.amount -> Verdict.Covered(comparable.ids())
            // A holding in another unit could close the gap and nothing here converts units.
            comparable.size < holdings.size -> Verdict.Unverifiable
            else -> Verdict.Missing(Quantity(required.amount - available, required.unit))
        }
    }

    private fun List<PantryItem>.ids(): List<PantryItemId> = map { it.id }
}

// Expired stock is not stock. `freshnessAt` is not used here: it needs a product-defined
// window that the matcher has no business knowing about.
private fun PantryItem.hasExpiredAt(now: Instant): Boolean = expiresAt?.let { it <= now } == true

/** The three answers a single line can get, kept private: callers read [PantryMatch] instead. */
private sealed interface Verdict {
    data class Covered(val by: List<PantryItemId>) : Verdict

    data class Missing(val shortfall: Quantity?) : Verdict

    data object Unverifiable : Verdict
}
