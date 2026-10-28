package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class HouseholdAvoidsTest {
    private val termA = ref("term-a")
    private val termB = ref("term-b")
    private val rice = id("rice")
    private val salt = id("salt")

    @Test
    fun `a member's share is their Avoids and avoided ingredients and never their Prefers`() {
        val profile =
            UserProfile.newFor(uid("user-1"), listOf("en"), Instant.fromEpochSeconds(0)).copy(
                constraints =
                    listOf(
                        DietaryConstraint(termA, ConstraintStrength.AVOID),
                        DietaryConstraint(termB, ConstraintStrength.PREFER),
                    ),
                avoidedIngredients = listOf(rice),
            )

        assertEquals(HouseholdAvoids(setOf(termA), setOf(rice)), HouseholdAvoids.of(profile))
    }

    @Test
    fun `a kitchen pools every member's set`() {
        val kitchen =
            Kitchen(
                id = (KitchenId.of("kitchen-1") as AppResult.Success).data,
                ownerId = uid("user-1"),
                memberIds = setOf(uid("user-1"), uid("user-2")),
                joinCode = (KitchenJoinCode.of("code-1") as AppResult.Success).data,
                memberDisplayNames = emptyMap(),
                memberAvoids =
                    mapOf(
                        "user-1" to HouseholdAvoids(setOf(termA)),
                        "user-2" to HouseholdAvoids(ingredients = setOf(rice)),
                    ),
            )

        assertEquals(HouseholdAvoids(setOf(termA), setOf(rice)), kitchen.householdAvoids())
    }

    @Test
    fun `a recipe's hits are the tags and catalogue lines the set rules out`() {
        val recipe =
            Recipe(
                id = (RecipeId.of("recipe-1") as AppResult.Success).data,
                title = "t",
                summary = null,
                servings = 2,
                totalMinutes = null,
                ingredients =
                    listOf(
                        RecipeIngredient(rice, null, null, false),
                        RecipeIngredient(salt, null, null, false),
                        RecipeIngredient(null, "free text", null, false),
                    ),
                steps = emptyList(),
                tags = listOf(termA, termB),
                source = RecipeSource.Catalogue,
            )

        val hits = HouseholdAvoids(setOf(termB), setOf(rice)).hitsIn(recipe)

        assertEquals(AvoidHits(listOf(termB), listOf(rice)), hits)
        assertTrue(HouseholdAvoids().hitsIn(recipe).isEmpty)
    }
}

private fun ref(term: String): TermRef =
    TermRef((TaxonomyId.of("taxonomy-1") as AppResult.Success).data, (TermId.of(term) as AppResult.Success).data)

private fun id(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

private fun uid(raw: String): UserId = (UserId.of(raw) as AppResult.Success).data
