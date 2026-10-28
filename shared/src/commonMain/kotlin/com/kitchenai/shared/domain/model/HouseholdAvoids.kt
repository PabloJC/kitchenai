package com.kitchenai.shared.domain.model

/**
 * What a kitchen's members have marked Avoid, pooled and nameless: who avoids what is not
 * something another member needs, only that a dish must not contain it.
 */
data class HouseholdAvoids(
    val terms: Set<TermRef> = emptySet(),
    val ingredients: Set<IngredientId> = emptySet(),
) {
    val isEmpty: Boolean get() = terms.isEmpty() && ingredients.isEmpty()

    operator fun plus(other: HouseholdAvoids): HouseholdAvoids =
        HouseholdAvoids(terms + other.terms, ingredients + other.ingredients)

    /** The terms and catalogue ingredients of [recipe] this set rules out, in the recipe's order. */
    fun hitsIn(recipe: Recipe): AvoidHits =
        AvoidHits(
            terms = recipe.tags.filter { it in terms }.distinct(),
            ingredients = recipe.ingredients.mapNotNull { it.ingredient }.filter { it in ingredients }.distinct(),
        )

    companion object {
        /** One member's share: their Avoid constraints and avoided ingredients; Prefer stays theirs. */
        fun of(profile: UserProfile): HouseholdAvoids =
            HouseholdAvoids(
                terms = profile.constraints.filter { it.strength == ConstraintStrength.AVOID }.map { it.term }.toSet(),
                ingredients = profile.avoidedIngredients.toSet(),
            )
    }
}
