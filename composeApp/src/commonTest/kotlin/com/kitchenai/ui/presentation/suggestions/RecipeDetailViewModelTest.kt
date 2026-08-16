package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItems
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy
import com.kitchenai.shared.domain.usecase.recipe.CookRecipe
import com.kitchenai.shared.domain.usecase.recipe.GetRecipeById
import com.kitchenai.shared.domain.usecase.recipe.GetStoredRecipe
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantry
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipe
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingList
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.FakeShoppingItemPort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.TestDispatcherProvider
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_missing_ingredients
import com.kitchenai.ui.resources.error_not_found
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.fromEpochSeconds(1_000)
    private val user = UserId.of("user-1").orFail()
    private val gram = TermRef(TaxonomyId.of("units").orFail(), TermId.of("gram").orFail())

    /** One catalogue line the pantry can check, and one free-text line it never can. */
    private val dish =
        Recipe(
            id = RecipeId.of("recipe-1").orFail(),
            title = "Dish",
            summary = "A dish",
            servings = 2,
            totalMinutes = 20,
            ingredients =
                listOf(
                    RecipeIngredient(IngredientId.of("rice").orFail(), null, Quantity(200.0, gram), false),
                    RecipeIngredient(null, "a pinch of salt", null, false),
                ),
            steps = listOf("Cook it"),
            tags = emptyList(),
            source = RecipeSource.Catalogue,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the three buckets stay three and free text is never called missing`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = listOf(holding(200.0)))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("rice"), state.held.map { it.name })
            assertTrue(state.missing.isEmpty())
            // The line the pantry cannot check is its own bucket, not a shortfall.
            assertEquals(listOf("a pinch of salt"), state.unverifiable.map { it.name })
        }

    @Test
    fun `a shortfall lands in missing rather than in unverifiable`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = emptyList())
            advanceUntilIdle()

            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
            assertTrue(viewModel.state.value.held.isEmpty())
        }

    @Test
    fun `moving the servings stepper re-matches rather than only re-scaling`() =
        runTest(dispatcher) {
            // Exactly enough for two servings, and so not enough for four.
            val viewModel = started(pantry = listOf(holding(200.0)))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.missing.isEmpty())

            viewModel.setServings(4)
            advanceUntilIdle()

            assertEquals(4, viewModel.state.value.servings)
            // The buckets moved with the numbers. This is the whole reason the domain changed.
            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
            assertFalse(viewModel.state.value.canCook)
        }

    @Test
    fun `a second stepper tap supersedes the first rather than racing it`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(200.0)))
            val viewModel = started(pantry = listOf(holding(200.0)), pantryPort = pantry)
            advanceUntilIdle()
            assertEquals(1, pantry.reads)

            // Two taps before either load can run. The first must never reach the pantry: if it
            // did, its answer for four could land after six and put the buckets back.
            viewModel.setServings(4)
            viewModel.setServings(6)
            advanceUntilIdle()

            assertEquals(2, pantry.reads)
            assertEquals(6, viewModel.state.value.servings)
            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
        }

    @Test
    fun `cooking is refused while anything is missing and says so`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(emptyList())
            val viewModel = started(pantry = emptyList(), pantryPort = pantry)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.canCook)
            // The refusal is a one-shot event, not a banner: the screen already lists which
            // ingredients are short, so a permanent message would repeat what is on screen.
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.cook()
            advanceUntilIdle()
            collector.cancel()

            val failure = seen.filterIsInstance<RecipeDetailEvent.Failed>().single()
            // The cook's own sentence, not the generic validation one.
            assertEquals(UiText.of(Res.string.error_missing_ingredients), failure.message)
            assertTrue(pantry.held.isEmpty())
        }

    @Test
    fun `cooking takes the ingredients out of the pantry`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(500.0)))
            val viewModel = started(pantry = listOf(holding(500.0)), pantryPort = pantry)
            advanceUntilIdle()

            viewModel.cook()
            advanceUntilIdle()

            assertEquals(300.0, pantry.held.single().quantity.amount)
        }

    @Test
    fun `a cook that worked is never followed by a failure`() =
        runTest(dispatcher) {
            // Enough for one cook and not for the next, so the buckets have to move afterwards.
            val recipes = FakeRecipePort(catalogue = emptyList(), stored = listOf(dish))
            val pantry = FakePantryPort(listOf(holding(350.0)))
            val viewModel = started(pantry = listOf(holding(350.0)), pantryPort = pantry, recipePort = recipes)
            advanceUntilIdle()
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            // A new generation replaces the stored one while this screen is still open, so the
            // dish this ViewModel is showing exists nowhere any longer.
            recipes.replaceAll(emptyList())
            viewModel.cook()
            advanceUntilIdle()
            collector.cancel()

            assertEquals(listOf<RecipeDetailEvent>(RecipeDetailEvent.Cooked), seen)
            assertEquals(150.0, pantry.held.single().quantity.amount)
            // And the buckets still refreshed: what is left no longer covers another serving.
            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
        }

    @Test
    fun `a stepper tap during a cook is answered for rather than overwritten`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(500.0)))
            val viewModel =
                started(
                    pantry = listOf(holding(500.0)),
                    pantryPort = pantry,
                    recipePort = FakeRecipePort(catalogue = emptyList(), stored = listOf(dish)),
                )
            advanceUntilIdle()

            // The stepper is not gated while a write runs, so the tap and the post-cook refresh
            // are genuinely in flight together.
            viewModel.cook()
            viewModel.setServings(4)
            advanceUntilIdle()

            // 300 left after cooking two servings, and four servings need 400.
            assertEquals(4, viewModel.state.value.servings)
            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
            // The refresh and the tap share one flight, so the superseded match never reaches
            // the pantry. Outside it there is a fifth read: two matches running unmanaged, with
            // whichever answered last deciding what the screen shows.
            assertEquals(4, pantry.reads)
        }

    @Test
    fun `saving reports itself once and disables the button`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(catalogue = listOf(dish))
            val viewModel = started(pantry = emptyList(), recipePort = recipes)
            advanceUntilIdle()

            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf(dish.id), recipes.saved.map { it.id })
            assertTrue(viewModel.state.value.isSaved)
        }

    @Test
    fun `a generated dish opens from the last generation since it exists nowhere else`() =
        runTest(dispatcher) {
            // No catalogue at all: exactly a dish the model invented a moment ago.
            val recipePort = FakeRecipePort(catalogue = emptyList(), stored = listOf(dish))
            val viewModel = started(pantry = emptyList(), recipePort = recipePort)
            advanceUntilIdle()

            assertEquals("Dish", viewModel.state.value.title)
            assertEquals(null, viewModel.state.value.error)
        }

    @Test
    fun `cooking a generated dish empties the pantry without saving it first`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(500.0)))
            val viewModel =
                started(
                    pantry = listOf(holding(500.0)),
                    pantryPort = pantry,
                    recipePort = FakeRecipePort(catalogue = emptyList(), stored = listOf(dish)),
                )
            advanceUntilIdle()

            viewModel.cook()
            advanceUntilIdle()

            assertEquals(300.0, pantry.held.single().quantity.amount)
        }

    @Test
    fun `adding what is missing works on a generated dish without saving it first`() =
        runTest(dispatcher) {
            val recipePort = FakeRecipePort(catalogue = emptyList(), stored = listOf(dish))
            val viewModel = started(pantry = emptyList(), recipePort = recipePort)
            advanceUntilIdle()
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.addMissingToList()
            advanceUntilIdle()
            collector.cancel()

            // Both lines are wanted: the shortfall, and the one the pantry cannot check.
            assertEquals(RecipeDetailEvent.AddedToList(added = 2, skipped = 0), seen.single())
            assertEquals(2, items.upserts.single().size)
        }

    @Test
    fun `a list this screen has to create is not left without a name`() =
        runTest(dispatcher) {
            // The session gate normally gets there first. If it ever does not, the list it
            // creates here must still be called something.
            lists = FakeShoppingListPort(existing = false)
            val viewModel = started(pantry = emptyList())
            advanceUntilIdle()

            viewModel.addMissingToList()
            advanceUntilIdle()

            assertEquals(mapOf("en" to "List"), lists.created.single().labels)
        }

    @Test
    fun `an ingredient and its unit are shown as words once the catalogue answers`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = listOf(holding(200.0)))
            advanceUntilIdle()
            // Before the catalogue answers the screen shows the identifier, which is the honest
            // fallback and the state every other test in this file leaves it in.
            assertEquals(listOf("rice"), viewModel.state.value.held.map { it.name })

            catalogue.emit(listOf(ingredient("rice", mapOf("en" to "Rice"))))
            taxonomies.taxonomies.emit(listOf(unitsTaxonomy(default = "en")))
            taxonomies.terms.emit(listOf(term(gram, mapOf("en" to "g"))))
            advanceUntilIdle()

            val line = viewModel.state.value.held.single()
            assertEquals("Rice", line.name)
            assertEquals("200 g", line.quantity)
        }

    @Test
    fun `a term the user cannot read falls back to the taxonomy default rather than its id`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = listOf(holding(200.0)))
            advanceUntilIdle()

            // The user reads English; the term is labelled only in the taxonomy's own language.
            catalogue.emit(listOf(ingredient("rice", mapOf("en" to "Rice"))))
            taxonomies.taxonomies.emit(listOf(unitsTaxonomy(default = "es")))
            taxonomies.terms.emit(listOf(term(gram, mapOf("es" to "gr"))))
            advanceUntilIdle()

            assertEquals("200 gr", viewModel.state.value.held.single().quantity)
        }

    @Test
    fun `an empty screen does not report that nothing is missing`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = emptyList(), recipePort = FakeRecipePort(catalogue = emptyList()))
            advanceUntilIdle()

            // Nothing loaded, so `missing` is empty — which is not the same as having everything.
            assertTrue(viewModel.state.value.missing.isEmpty())
            assertFalse(viewModel.state.value.canCook)
        }

    @Test
    fun `a recipe that cannot be read reports rather than rendering an empty dish`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = emptyList(), recipePort = FakeRecipePort(catalogue = emptyList()))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(UiText.of(Res.string.error_not_found, "recipe"), viewModel.state.value.error)
            assertTrue(viewModel.state.value.held.isEmpty())
        }

    private fun holding(amount: Double): PantryItem =
        PantryItem(
            id = PantryItemId.of("item-1").orFail(),
            ingredient = IngredientId.of("rice").orFail(),
            quantity = Quantity(amount, gram),
            location = null,
            expiresAt = null,
            updatedAt = now,
        )

    private val taxonomies = FakeTaxonomyPort()
    private val catalogue = FakeIngredientPort()
    private val items = FakeShoppingItemPort()
    private var lists = FakeShoppingListPort()

    private fun started(
        pantry: List<PantryItem>,
        pantryPort: FakePantryPort = FakePantryPort(pantry),
        recipePort: FakeRecipePort = FakeRecipePort(catalogue = listOf(dish)),
    ): RecipeDetailViewModel {
        val time = TimeProvider { now }
        val reads =
            RecipeDetailReads(
                recipe = GetRecipeById(recipePort),
                storedRecipe = GetStoredRecipe(recipePort),
                match = MatchRecipeAgainstPantry(recipePort, pantryPort, time),
                ingredients = ObserveIngredients(catalogue),
                taxonomies = ObserveTaxonomies(taxonomies),
                taxonomy = ObserveTaxonomy(taxonomies),
            )
        val writes =
            RecipeDetailWrites(
                save = SaveRecipe(recipePort),
                cook = CookRecipe(recipePort, pantryPort, ConsumePantryItems(pantryPort, time), time),
                addMissing =
                    AddMissingIngredientsToShoppingList(
                        recipePort,
                        pantryPort,
                        items,
                        sequentialIds(),
                        time,
                    ),
                defaultList = EnsureDefaultShoppingList(lists, IdGenerator { "list-1" }, time),
            )
        return RecipeDetailViewModel(reads, writes, TestDispatcherProvider(dispatcher))
            .also { it.start(user, dish.id, listOf("en"), "List") }
    }

    private fun ingredient(
        id: String,
        labels: Map<String, String>,
    ): Ingredient = Ingredient(IngredientId.of(id).orFail(), labels, null, emptyList())

    private fun term(
        ref: TermRef,
        labels: Map<String, String>,
    ): Term = Term(ref, labels, null, 0)

    private fun unitsTaxonomy(default: String): Taxonomy =
        Taxonomy(gram.taxonomy, mapOf("en" to "Units"), default, TaxonomyPurpose.UNITS)

    /** Distinct ids: a constant one would fold two drafted lines into a single item. */
    private fun sequentialIds(): IdGenerator {
        var next = 0
        return IdGenerator { "shopping-${++next}" }
    }
}

private fun <T> AppResult<T>.orFail(): T = (this as AppResult.Success).data
