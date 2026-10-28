package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.MoveToPantryOptions
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.profile.FakeTaxonomyRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import com.kitchenai.shared.domain.usecase.profile.metricUnitTerms
import com.kitchenai.shared.domain.usecase.profile.metricUnits
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MoveCheckedItemsToPantryUseCaseTest {
    private val list = listId()
    private val time = fixedTime(1_000)
    private val unit = termRef("units", "gram")
    private val kilograms = termRef("units", "kilogram")

    private fun useCase(
        items: FakeShoppingItemRepositoryContract,
        pantry: FakePantryRepositoryContract = FakePantryRepositoryContract(),
        units: GetUnitConverterUseCase = noUnits(),
        catalogue: FakeIngredientRepositoryContract = FakeIngredientRepositoryContract(),
    ) = MoveCheckedItemsToPantryUseCase(items, pantry, sequentialIds(), time, units, catalogue)

    @Test
    fun `the pantry write goes through the confirmed path rather than the optimistic one`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true))
            val pantry = FakePantryRepositoryContract()

            useCase(items, pantry)(kitchenId(), list)

            // Not upsertAll: an optimistic write always "succeeds" before the server has
            // answered, which would let removeItems run on a pantry write that never landed.
            assertEquals(1, pantry.upsertAllConfirmedCalls)
            assertEquals(0, pantry.upsertAllCalls)
        }

    @Test
    fun `a checked catalogue line with a quantity moves into the pantry and leaves the list`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true))
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(1, result.data.moved)
            assertEquals(0, result.data.skipped)
            assertEquals(listOf(ingredientId("rice")), pantry.items.map { it.ingredient })
            assertTrue(items.itemsOf(list).isEmpty())
        }

    @Test
    fun `a checked free-text line moves into the pantry as free text`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("bread", ingredient = null, freeText = "the good bread", quantity = Quantity(1.0))
                    .copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(1, result.data.moved)
            assertEquals("the good bread", pantry.items.single().freeText)
        }

    @Test
    fun `a checked line with no quantity stays on the list and is reported as skipped`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("milk", quantity = null).copy(checked = true))
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(0, result.data.moved)
            assertEquals(1, result.data.skipped)
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("milk"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `a checked line with a zero amount fails rather than creating an empty holding`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(0.0, unit)).copy(checked = true))
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Failure)
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("rice"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `an unchecked line never moves`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)))
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(0, result.data.moved)
            assertEquals(0, result.data.skipped)
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("rice"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `two checked lines for the same ingredient in the same unit merge with each other`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("rice-1", "rice", quantity = Quantity(100.0, unit)).copy(checked = true),
                shoppingItem("rice-2", "rice", quantity = Quantity(150.0, unit)).copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(2, result.data.moved)
            val holding = pantry.items.single()
            assertEquals(Quantity(250.0, unit), holding.quantity)
        }

    @Test
    fun `nothing is written to the pantry or removed from the list when a later line fails`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                // Would succeed on its own — the point is that it must not be written just
                // because it was processed before the line that fails.
                shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true),
                // Both an ingredient and free text: rejected by draftPantryHolding by way of
                // PantryItem.create's own invariant.
                shoppingItem("broken", quantity = Quantity(1.0))
                    .copy(checked = true, freeText = "not allowed alongside an ingredient"),
            )
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Failure)
            // The earlier, individually-valid line must not have been committed on its own.
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("rice", "broken"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `a pantry write failure leaves the list exactly as it was`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true))
            val pantry = FakePantryRepositoryContract(writeError = AppError.Network())

            val result = useCase(items, pantry)(kitchenId(), list)

            assertTrue(result is AppResult.Failure)
            assertEquals(listOf("rice"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `the read failure travels back as it is`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract(AppError.Network())

            val result = useCase(items)(kitchenId(), list)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Network)
        }

    @Test
    fun `a checked line merges into a holding in another unit of the same dimension`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(1.0, kilograms)).copy(checked = true))
            val pantry = FakePantryRepositoryContract(listOf(heldRice(Quantity(500.0, unit))))

            val result = useCase(items, pantry, metricUnits("units"))(kitchenId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(Quantity(1500.0, unit)), pantry.items.map { it.quantity })
            assertTrue(items.itemsOf(list).isEmpty())
        }

    @Test
    fun `two checked lines in convertible units merge into the first one's unit`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("rice-1", "rice", quantity = Quantity(250.0, unit)).copy(checked = true),
                shoppingItem("rice-2", "rice", quantity = Quantity(0.25, kilograms)).copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract()

            useCase(items, pantry, metricUnits("units"))(kitchenId(), list)

            assertEquals(listOf(Quantity(500.0, unit)), pantry.items.map { it.quantity })
        }

    @Test
    fun `a converter that cannot be read leaves the pantry and the list as they were`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true))
            val pantry = FakePantryRepositoryContract()
            val broken =
                GetUnitConverterUseCase(
                    FakeTaxonomyRepositoryContract(metricUnitTerms("units"), taxonomiesError = AppError.Network()),
                )

            val result = useCase(items, pantry, broken)(kitchenId(), list)

            assertTrue(result is AppResult.Failure)
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("rice"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `a dated line moves with its date and an undated one without`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true),
                shoppingItem("pasta", quantity = Quantity(500.0, unit)).copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract()
            val options = MoveToPantryOptions(expiresAt = mapOf(itemId("rice") to instant(9_000)))

            useCase(items, pantry)(kitchenId(), list, options)

            val dates = pantry.items.associate { it.ingredient to it.expiresAt }
            val expected =
                mapOf<IngredientId?, Instant?>(ingredientId("rice") to instant(9_000), ingredientId("pasta") to null)
            assertEquals(expected, dates)
        }

    @Test
    fun `a dated line merging into a dated holding keeps the earlier date`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true))
            val pantry =
                FakePantryRepositoryContract(
                    listOf(pantryItem("held-rice", "rice", Quantity(500.0, unit), instant(5_000))),
                )
            val options = MoveToPantryOptions(expiresAt = mapOf(itemId("rice") to instant(9_000)))

            useCase(items, pantry)(kitchenId(), list, options)

            val holding = pantry.items.single()
            assertEquals(Quantity(700.0, unit), holding.quantity)
            assertEquals(instant(5_000), holding.expiresAt)
        }

    @Test
    fun `with the flag on a bare catalogue line moves as one in its default unit`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("milk", quantity = null).copy(checked = true))
            val pantry = FakePantryRepositoryContract()
            val litre = termRef("units", "litre")
            val catalogue = FakeIngredientRepositoryContract(listOf(ingredient("milk", defaultUnit = litre)))

            val result =
                useCase(items, pantry, catalogue = catalogue)(
                    kitchenId(),
                    list,
                    MoveToPantryOptions(defaultAmountForBare = true),
                )

            assertTrue(result is AppResult.Success)
            assertEquals(1, result.data.moved)
            assertEquals(0, result.data.skipped)
            assertEquals(Quantity(1.0, litre), pantry.items.single().quantity)
            assertTrue(items.itemsOf(list).isEmpty())
        }

    @Test
    fun `with the flag on a bare line takes the amount typed for it`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("bread", ingredient = null, freeText = "bread", quantity = null).copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract()
            val options = MoveToPantryOptions(defaultAmountForBare = true, bareAmounts = mapOf(itemId("bread") to 2.0))

            useCase(items, pantry)(kitchenId(), list, options)

            assertEquals(Quantity(2.0), pantry.items.single().quantity)
        }

    @Test
    fun `with the flag on an ingredient the catalogue no longer lists moves as a plain count`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("milk", quantity = null).copy(checked = true))
            val pantry = FakePantryRepositoryContract()

            useCase(items, pantry)(kitchenId(), list, MoveToPantryOptions(defaultAmountForBare = true))

            assertEquals(Quantity(1.0), pantry.items.single().quantity)
        }

    @Test
    fun `with the flag on a catalogue that cannot be read moves nothing`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("milk", quantity = null).copy(checked = true))
            val pantry = FakePantryRepositoryContract()
            val broken = FakeIngredientRepositoryContract(error = AppError.Network())

            val result =
                useCase(items, pantry, catalogue = broken)(
                    kitchenId(),
                    list,
                    MoveToPantryOptions(defaultAmountForBare = true),
                )

            assertTrue(result is AppResult.Failure)
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("milk"), items.itemsOf(list).map { it.id.value })
        }

    private fun heldRice(quantity: Quantity) = pantryItem("held-rice", "rice", quantity)
}
