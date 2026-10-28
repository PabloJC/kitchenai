package com.kitchenai.ui.presentation.shoppingwidget

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.shopping.GetShoppingSnapshotUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemCheckedUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakeShoppingItemPort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.session.FakeSessionPort
import com.kitchenai.ui.presentation.session.userId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ShoppingWidgetLoaderTest {
    private val session =
        FakeSessionPort().apply { sessionChanges.value = Session.SignedIn(userId, isAnonymous = true) }
    private val kitchens = FakeKitchenPort()
    private val items = FakeShoppingItemPort()
    private val catalogue = FakeIngredientPort()

    private fun loader(lists: FakeShoppingListPort = FakeShoppingListPort()) =
        ShoppingWidgetLoader(
            GetShoppingSnapshotUseCase(session, kitchens, lists, items),
            ObserveIngredientsUseCase(catalogue),
            SetShoppingItemCheckedUseCase(items, TimeProvider { Instant.fromEpochSeconds(5) }),
        )

    @Test
    fun `unchecked lines are drawn in words and ticked ones are left out`() =
        runTest {
            catalogue.emit(listOf(Ingredient(ingredientId, mapOf("en" to "word-a"), null, emptyList())))
            items.emit(listOf(line("item-1"), line("item-2", freeText = "word-b"), line("item-3", checked = true)))

            val state = assertIs<ShoppingWidgetState.Lines>(loader().load(listOf("en")))

            assertEquals(listOf("word-a", "word-b"), state.lines.map { it.label })
        }

    @Test
    fun `a catalogue that never answers leaves the names as their ids instead of hanging`() =
        runTest {
            items.emit(listOf(line("item-1")))

            val state = assertIs<ShoppingWidgetState.Lines>(loader().load(listOf("en")))

            assertEquals(listOf(ingredientId.value), state.lines.map { it.label })
        }

    @Test
    fun `signed out and no list yet both offer only the way into the app`() =
        runTest {
            val noList = loader(FakeShoppingListPort(existing = false))
            assertEquals(ShoppingWidgetState.NothingYet, noList.load(listOf("en")))

            session.sessionChanges.value = Session.SignedOut
            assertEquals(ShoppingWidgetState.NothingYet, loader().load(listOf("en")))
        }

    @Test
    fun `a failed read is drawn as a failure`() =
        runTest {
            kitchens.readError = AppError.Network()

            assertEquals(ShoppingWidgetState.Failed, loader().load(listOf("en")))
        }

    @Test
    fun `a tick writes the line checked`() =
        runTest {
            items.emit(listOf(line("item-1")))

            val result = loader().check("item-1")

            assertEquals(AppResult.Success(Unit), result)
            assertTrue(items.upserts.single().single().checked)
        }

    @Test
    fun `a tick on a line that is gone writes nothing`() =
        runTest {
            items.emit(listOf(line("item-1")))

            val result = loader().check("item-9")

            assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(result).error)
            assertTrue(items.upserts.isEmpty())
        }

    @Test
    fun `a tick with no list fails and writes nothing`() =
        runTest {
            val result = loader(FakeShoppingListPort(existing = false)).check("item-1")

            assertIs<AppResult.Failure>(result)
            assertTrue(items.upserts.isEmpty())
        }
}

private val ingredientId = (IngredientId.of("ing-1") as AppResult.Success).data

/** Identifiers and opaque words only: a real product name here would be the constant the domain refuses. */
private fun line(
    raw: String,
    checked: Boolean = false,
    freeText: String? = null,
): ShoppingItem =
    ShoppingItem(
        id = (ShoppingItemId.of(raw) as AppResult.Success).data,
        ingredient = if (freeText == null) ingredientId else null,
        freeText = freeText,
        quantity = null,
        checked = checked,
        sourceRecipe = null,
        updatedAt = Instant.fromEpochSeconds(0),
    )
