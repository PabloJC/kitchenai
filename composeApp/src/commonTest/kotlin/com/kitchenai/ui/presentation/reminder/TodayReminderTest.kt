package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.reminder.ObserveReminderSettingsUseCase
import com.kitchenai.shared.domain.usecase.reminder.PlanExpiryDigestsUseCase
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeReminderSettingsPort
import com.kitchenai.ui.presentation.session.FakeSessionPort
import com.kitchenai.ui.presentation.session.userId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TodayReminderTest {
    private val zone = TimeZone.of("Europe/Madrid")
    private val now = LocalDateTime(2026, 10, 20, 18, 0).toInstant(zone)
    private val tomorrow = LocalDateTime(2026, 10, 21, 12, 0).toInstant(zone)
    private val session =
        FakeSessionPort().apply { sessionChanges.value = Session.SignedIn(userId, isAnonymous = true) }
    private val settings = FakeReminderSettingsPort().apply { current.value = ReminderSettings(offered = true) }
    private val catalogue = FakeIngredientPort()

    private fun today(pantry: FakePantryPort) =
        TodayReminder(
            session = ObserveSessionUseCase(session),
            reads =
                ExpiryReminderReadsDelegate(
                    kitchen = ObserveKitchenUseCase(FakeKitchenPort()),
                    pantry = ObservePantryUseCase(pantry),
                    settings = ObserveReminderSettingsUseCase(settings),
                    plan = PlanExpiryDigestsUseCase(),
                    ingredients = ObserveIngredientsUseCase(catalogue),
                ),
            time = TimeProvider { now },
            wording = {
                    digest,
                    nameOf,
                ->
                ReminderText("t", digest.expiring.joinToString(transform = nameOf), "p", "c")
            },
            zone = { zone },
        )

    @Test
    fun `what is in the pantry now is named by its catalogue words`() =
        runTest {
            catalogue.emit(listOf(Ingredient(ingredient("a"), mapOf("en" to "word-a"), null, emptyList())))

            val text = today(FakePantryPort(listOf(item("a", tomorrow), item("b", null)))).compose(listOf("en"))

            assertEquals("word-a", text?.body)
        }

    @Test
    fun `an item used up since the last launch is not announced`() =
        runTest {
            assertNull(today(FakePantryPort(listOf(item("b", null)))).compose(listOf("en")))
        }

    @Test
    fun `nothing is said before the person agreed to reminders`() =
        runTest {
            settings.current.value = ReminderSettings(offered = false)

            assertNull(today(FakePantryPort(listOf(item("a", tomorrow)))).compose(listOf("en")))
        }

    @Test
    fun `signed out says nothing`() =
        runTest {
            session.sessionChanges.value = Session.SignedOut

            assertNull(today(FakePantryPort(listOf(item("a", tomorrow)))).compose(listOf("en")))
        }

    private fun ingredient(raw: String): IngredientId = (IngredientId.of("ing-$raw") as AppResult.Success).data

    private fun item(
        id: String,
        expiresAt: Instant?,
    ): PantryItem =
        PantryItem(
            id = (PantryItemId.of(id) as AppResult.Success).data,
            ingredient = ingredient(id),
            freeText = null,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = expiresAt,
            updatedAt = now,
        )
}
