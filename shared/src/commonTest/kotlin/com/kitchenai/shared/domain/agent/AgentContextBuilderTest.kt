package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.freeTextPantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.ingredientId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class AgentContextBuilderTest {
    private val now = Instant.fromEpochSeconds(1_000_000)
    private val unit = termRef("term-1")
    private val options = SuggestionOptions()
    private val languageTags = listOf("xx")

    @Test
    fun `an expired holding never reaches the context`() {
        val pantry =
            listOf(
                pantryItem("item-1", "ing-1", Quantity(1.0, unit), expiresAt = now - 1.days),
                pantryItem("item-2", "ing-2", Quantity(1.0, unit), expiresAt = now + 30.days),
            )

        val context = AgentContextBuilder.build(profile(), pantry, options, languageTags, now)

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

        val context = AgentContextBuilder.build(profile(), pantry, options, languageTags, now)

        assertEquals(listOf(true, false, false), context.pantry.map { it.expiring })
    }

    @Test
    fun `the expiring flag counts calendar days in the device zone whatever the hour`() {
        for (zone in listOf(TimeZone.of("Asia/Tokyo"), TimeZone.of("America/Los_Angeles"))) {
            val lateEvening = LocalDateTime(2026, 10, 14, 23, 30).toInstant(zone)
            val one = Quantity(1.0, unit)
            val pantry =
                listOf(
                    // The day after tomorrow: the last of the three soon days.
                    pantryItem("item-1", "ing-1", one, expiresAt = endOf(LocalDate(2026, 10, 16), zone)),
                    // Three calendar days ahead: fresh, as the pantry shows it.
                    pantryItem("item-2", "ing-2", one, expiresAt = endOf(LocalDate(2026, 10, 17), zone)),
                    // Today: still stock until midnight, so it reaches the context flagged.
                    pantryItem("item-3", "ing-3", one, expiresAt = endOf(LocalDate(2026, 10, 14), zone)),
                )

            val context = AgentContextBuilder.build(profile(), pantry, options, languageTags, lateEvening, zone)

            assertEquals(
                listOf(ingredientId("ing-3") to true, ingredientId("ing-1") to true, ingredientId("ing-2") to false),
                context.pantry.map { it.ingredient to it.expiring },
                zone.id,
            )
        }
    }

    private fun endOf(
        date: LocalDate,
        zone: TimeZone,
    ): Instant = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.milliseconds

    @Test
    fun `a free-text holding never reaches the context and costs no capped slot either`() {
        val pantry =
            listOf(
                freeTextPantryItem("item-1", "the good bread", Quantity(1.0, unit)),
                pantryItem("item-2", "ing-2", Quantity(1.0, unit)),
            )

        val capped = options.copy(maxPantryEntries = 1)
        val context = AgentContextBuilder.build(profile(), pantry, capped, languageTags, now)

        assertEquals(listOf(ingredientId("ing-2")), context.pantry.map { it.ingredient })
    }

    @Test
    fun `the pantry cap keeps the most urgent holdings`() {
        val pantry =
            listOf(
                pantryItem("item-1", "ing-1", Quantity(1.0, unit)),
                pantryItem("item-2", "ing-2", Quantity(1.0, unit), expiresAt = now + 30.days),
                pantryItem("item-3", "ing-3", Quantity(1.0, unit), expiresAt = now + 2.days),
            )

        val capped = options.copy(maxPantryEntries = 2)
        val context = AgentContextBuilder.build(profile(), pantry, capped, languageTags, now)

        assertEquals(listOf(ingredientId("ing-3"), ingredientId("ing-2")), context.pantry.map { it.ingredient })
    }

    @Test
    fun `profile references travel to the context unchanged`() {
        val constraint = DietaryConstraint(termRef("term-2"), ConstraintStrength.AVOID)
        val stored = profile(constraints = listOf(constraint), servings = 4)

        val context = AgentContextBuilder.build(stored, emptyList(), options, languageTags, now)

        assertEquals(4, context.servings)
        assertEquals(listOf(constraint), context.constraints)
        assertTrue(context.pantry.isEmpty())
    }

    /** #131: the profile's own tags are a fact about the person, captured once on first launch. */
    @Test
    fun `the language answered in is the caller's rather than the profile's stored one`() {
        val stored = profile()

        val context = AgentContextBuilder.build(stored, emptyList(), options, listOf("es"), now)

        assertEquals(listOf("xx"), stored.languageTags)
        assertEquals(listOf("es"), context.languageTags)
    }
}
