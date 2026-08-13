package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.ingredientId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class AgentContextBuilderTest {
    private val now = Instant.fromEpochSeconds(1_000_000)
    private val unit = termRef("term-1")
    private val options = SuggestionOptions()

    @Test
    fun `an expired holding never reaches the context`() {
        val pantry =
            listOf(
                pantryItem("item-1", "ing-1", Quantity(1.0, unit), expiresAt = now - 1.days),
                pantryItem("item-2", "ing-2", Quantity(1.0, unit), expiresAt = now + 30.days),
            )

        val context = AgentContextBuilder.build(profile(), pantry, options, now)

        assertEquals(listOf(ingredientId("ing-2")), context.pantry.map { it.ingredient })
    }

    @Test
    fun `a holding inside the window is flagged as expiring`() {
        val pantry =
            listOf(
                pantryItem("item-1", "ing-1", Quantity(1.0, unit), expiresAt = now + 1.days),
                pantryItem("item-2", "ing-2", Quantity(1.0, unit), expiresAt = now + 30.days),
                pantryItem("item-3", "ing-3", Quantity(1.0, unit)),
            )

        val context = AgentContextBuilder.build(profile(), pantry, options, now)

        assertEquals(listOf(true, false, false), context.pantry.map { it.expiring })
    }

    @Test
    fun `the pantry cap keeps the most urgent holdings`() {
        val pantry =
            listOf(
                pantryItem("item-1", "ing-1", Quantity(1.0, unit)),
                pantryItem("item-2", "ing-2", Quantity(1.0, unit), expiresAt = now + 30.days),
                pantryItem("item-3", "ing-3", Quantity(1.0, unit), expiresAt = now + 2.days),
            )

        val context = AgentContextBuilder.build(profile(), pantry, options.copy(maxPantryEntries = 2), now)

        assertEquals(listOf(ingredientId("ing-3"), ingredientId("ing-2")), context.pantry.map { it.ingredient })
    }

    @Test
    fun `profile references travel to the context unchanged`() {
        val constraint = DietaryConstraint(termRef("term-2"), ConstraintStrength.EXCLUDE)
        val stored = profile(constraints = listOf(constraint), servings = 4)

        val context = AgentContextBuilder.build(stored, emptyList(), options, now)

        assertEquals(4, context.servings)
        assertEquals(listOf(constraint), context.constraints)
        assertEquals(stored.languageTags, context.languageTags)
        assertTrue(context.pantry.isEmpty())
    }
}
