package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.common.FlakyPantryPort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.defaultKitchenId
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_not_found
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModelTest : RecipeDetailViewModelHarness() {
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
            assertTrue(viewModel.state.value.cooksAnyway)
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
            // The cook reads the pantry once, for its plan, and the tap and the refresh its write
            // triggers share one flight, so a superseded match never reaches the pantry. A fifth
            // read would be two matches running unmanaged, whichever answered last winning.
            assertEquals(4, pantry.reads)
        }

    @Test
    fun `saving keeps the dish and fills the heart`() =
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
    fun `the heart toggles between saving and unsaving from what is on screen`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(catalogue = listOf(dish))
            val viewModel = started(pantry = emptyList(), recipePort = recipes)
            advanceUntilIdle()

            viewModel.toggleSaved()
            advanceUntilIdle()
            assertEquals(listOf(dish.id), recipes.saved.map { it.id })
            assertTrue(viewModel.state.value.isSaved)

            viewModel.toggleSaved()
            advanceUntilIdle()
            assertTrue(recipes.saved.isEmpty())
            assertFalse(viewModel.state.value.isSaved)
        }

    @Test
    fun `a dish already kept opens with the heart filled`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(catalogue = listOf(dish))
            recipes.saveRecipe(defaultKitchenId, dish)
            val viewModel = started(pantry = emptyList(), recipePort = recipes)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSaved)
        }

    @Test
    fun `unsaving removes the dish and offers an undo that keeps it again`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(catalogue = listOf(dish))
            recipes.saveRecipe(defaultKitchenId, dish)
            val viewModel = started(pantry = emptyList(), recipePort = recipes)
            advanceUntilIdle()
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.unsave()
            advanceUntilIdle()
            assertTrue(recipes.saved.isEmpty())
            assertFalse(viewModel.state.value.isSaved)

            viewModel.save()
            advanceUntilIdle()
            collector.cancel()

            assertEquals(listOf(dish.id), recipes.saved.map { it.id })
            assertTrue(viewModel.state.value.isSaved)
            assertEquals(listOf(RecipeDetailEvent.Unsaved, RecipeDetailEvent.Saved), seen)
        }

    @Test
    fun `a dish removed elsewhere empties the heart here`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(catalogue = listOf(dish))
            recipes.saveRecipe(defaultKitchenId, dish)
            val viewModel = started(pantry = emptyList(), recipePort = recipes)
            advanceUntilIdle()

            recipes.removeSavedRecipe(defaultKitchenId, dish.id)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSaved)
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
    fun `a tag resolves to its label even though its taxonomy is not the units one`() =
        runTest(dispatcher) {
            val cuisine = TermRef(TaxonomyId.of("cuisines").orFail(), TermId.of("italian").orFail())
            val tagged = dish.copy(id = RecipeId.of("recipe-2").orFail(), tags = listOf(cuisine))
            val viewModel =
                started(
                    pantry = emptyList(),
                    recipePort = FakeRecipePort(catalogue = listOf(tagged)),
                    recipeId = tagged.id,
                )
            advanceUntilIdle()
            // Before the catalogue answers: the same honest fallback an ingredient gets.
            assertEquals(listOf("italian"), viewModel.state.value.tags)

            taxonomies.taxonomies.emit(listOf(cuisinesTaxonomy(default = "en")))
            taxonomies.terms.emit(listOf(term(cuisine, mapOf("en" to "Italian"))))
            advanceUntilIdle()

            // A tag's taxonomy carries no `purpose` — watching only UNITS-purpose taxonomies,
            // as this screen once did, would leave this stuck on "italian" forever.
            assertEquals(listOf("Italian"), viewModel.state.value.tags)
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
            assertEquals(UiText.of(Res.string.error_not_found), viewModel.state.value.error)
            assertTrue(viewModel.state.value.held.isEmpty())
        }

    @Test
    fun `retrying a recipe that failed to load reads it again and clears the error`() =
        runTest(dispatcher) {
            val port = FailingRecipePort(FakeRecipePort(catalogue = listOf(dish)), AppError.Network())
            val viewModel = started(pantry = emptyList(), recipePort = port)
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)
            assertEquals("", viewModel.state.value.title)

            port.error = null
            viewModel.retry()
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertEquals("Dish", viewModel.state.value.title)
        }

    @Test
    fun `a free-text holding that plausibly names an unverifiable line is offered as a candidate`() =
        runTest(dispatcher) {
            val salt = freeTextHolding("item-2", "salt")
            val held = listOf(holding(200.0), salt)
            val viewModel = started(pantry = held, pantryPort = FakePantryPort(held))
            advanceUntilIdle()

            val line = viewModel.state.value.unverifiable.single()
            assertEquals(listOf("salt"), line.candidates.map { it.label })
            assertFalse(line.candidates.single().confirmed)
        }

    @Test
    fun `confirming a candidate only flips its own chip and matches the pantry unchanged`() =
        runTest(dispatcher) {
            val salt = freeTextHolding("item-2", "salt")
            val pantryPort = FakePantryPort(listOf(salt))
            val viewModel = started(pantry = listOf(salt), pantryPort = pantryPort)
            advanceUntilIdle()
            val candidateId = viewModel.state.value.unverifiable.single().candidates.single().id

            viewModel.confirmCandidate(candidateId)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.unverifiable.single().candidates.single().confirmed)
            // Still unverifiable, never quietly promoted to held: PantryMatcher's own answer for
            // this line is untouched by a confirmation (#163).
            assertTrue(viewModel.state.value.held.none { it.name == "a pinch of salt" })
            // And nothing was written: confirming is purely local, never a pantry write.
            assertEquals(listOf(salt), pantryPort.held)

            viewModel.confirmCandidate(candidateId)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.unverifiable.single().candidates.single().confirmed)
        }

    @Test
    fun `an unrelated free-text holding is never offered as a candidate`() =
        runTest(dispatcher) {
            val oil = freeTextHolding("item-2", "olive oil")
            val viewModel = started(pantry = listOf(oil), pantryPort = FakePantryPort(listOf(oil)))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.unverifiable.single().candidates.isEmpty())
        }

    @Test
    fun `cook anyway turns back into cook once the pantry covers every ingredient without reopening the dish`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel =
                started(
                    pantry = emptyList(),
                    pantryPort = pantry,
                    recipePort = FakeRecipePort(catalogue = emptyList(), stored = listOf(dish)),
                )
            advanceUntilIdle()
            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
            assertTrue(viewModel.state.value.cooksAnyway)

            // Shopping happens on another tab while this detail sits on the back stack.
            pantry.upsert(defaultKitchenId, holding(200.0))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.missing.isEmpty())
            assertEquals(listOf("rice"), viewModel.state.value.held.map { it.name })
            assertFalse(viewModel.state.value.cooksAnyway)
        }

    @Test
    fun `a re-match that fails after a pantry change leaves the screen as it was`() =
        runTest(dispatcher) {
            val pantry = FlakyPantryPort()
            val viewModel = started(pantry = emptyList(), pantryPort = pantry)
            advanceUntilIdle()
            val before = viewModel.state.value
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            pantry.failReads = true
            pantry.upsert(defaultKitchenId, holding(200.0))
            advanceUntilIdle()
            collector.cancel()

            assertEquals(before, viewModel.state.value)
            assertTrue(seen.isEmpty())
        }

    @Test
    fun `a pantry re-match that lands after a stepper tap does not overwrite it`() =
        runTest(dispatcher) {
            val pantry = FlakyPantryPort()
            val viewModel = started(pantry = emptyList(), pantryPort = pantry)
            advanceUntilIdle()

            // Enough for two servings and not for four; the re-match for two is held mid-read.
            val late = CompletableDeferred<Unit>()
            pantry.holdNextRead = late
            pantry.upsert(defaultKitchenId, holding(300.0))
            dispatcher.scheduler.runCurrent()
            viewModel.setServings(4)
            advanceUntilIdle()
            late.complete(Unit)
            advanceUntilIdle()

            assertEquals(4, viewModel.state.value.servings)
            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
        }

    @Test
    fun `a dish with a key gets its own photograph once it is on screen`() =
        runTest(dispatcher) {
            val keyed = dish.copy(imageKey = IMAGE_KEY)
            val port = FakeRecipePort(catalogue = listOf(keyed))
            val viewModel = started(pantry = emptyList(), recipePort = port)
            advanceUntilIdle()

            assertEquals("https://images.test/recipe.jpg", viewModel.state.value.imageUrl)
            assertEquals(1, port.imageRequests.size)
            // Published for the card behind this screen.
            assertEquals("https://images.test/recipe.jpg", pictures.all.value[dish.id])
        }

    @Test
    fun `a failed photograph leaves the dish-type one and shows no error`() =
        runTest(dispatcher) {
            val port =
                FakeRecipePort(
                    catalogue = listOf(dish.copy(imageKey = IMAGE_KEY)),
                    imageResult = AppResult.Failure(AppError.Network(null)),
                )
            val viewModel = started(pantry = emptyList(), recipePort = port)
            advanceUntilIdle()

            assertNull(viewModel.state.value.imageUrl)
            assertNull(viewModel.state.value.error)
            assertEquals("Dish", viewModel.state.value.title)
        }

    @Test
    fun `a dish that already has a photograph is not asked for one`() =
        runTest(dispatcher) {
            val pictured = dish.copy(imageKey = IMAGE_KEY, imageUrl = "https://images.test/old.jpg")
            val port = FakeRecipePort(catalogue = listOf(pictured))
            val viewModel = started(pantry = emptyList(), recipePort = port)
            advanceUntilIdle()

            assertEquals("https://images.test/old.jpg", viewModel.state.value.imageUrl)
            assertTrue(port.imageRequests.isEmpty())
        }
}

/** Refuses every read of one recipe while [error] is set, then answers like the port it wraps. */
private class FailingRecipePort(
    private val real: RecipeRepositoryContract,
    var error: AppError?,
) : RecipeRepositoryContract by real {
    override suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe> =
        error?.let { AppResult.Failure(it) } ?: real.getRecipe(recipeId)
}

private val IMAGE_KEY = "a".repeat(64)
