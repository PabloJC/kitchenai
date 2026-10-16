package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.defaultKitchenId
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.snack_unsaved
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsSavedViewTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    fun `the saved view lists what can be cooked now first and keeps the listener order within each`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            recipes.saveRecipe(defaultKitchenId, shortOfRice().recipe)
            recipes.saveRecipe(defaultKitchenId, dish("recipe-2"))
            recipes.saveRecipe(defaultKitchenId, dish("recipe-3"))
            agent.answer = AppResult.Success(emptyList())

            val viewModel = started(recipes = recipes)
            advanceUntilIdle()

            assertEquals(
                listOf("recipe-2", "recipe-3", "recipe-1"),
                viewModel.state.value.visibleSaved.map { it.id.value },
            )
        }

    @Test
    fun `switching to the saved view and searching it`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            recipes.saveRecipe(defaultKitchenId, dish("recipe-1").copy(title = "Lentil stew"))
            recipes.saveRecipe(defaultKitchenId, dish("recipe-2").copy(title = "Tomato soup"))
            // Past the threshold, so the search field is offered at all.
            (3..9).forEach { n -> recipes.saveRecipe(defaultKitchenId, dish("recipe-$n").copy(title = "Other $n")) }
            agent.answer = AppResult.Success(emptyList())
            val viewModel = started(recipes = recipes)
            advanceUntilIdle()

            viewModel.showSaved(true)
            viewModel.searchSaved("SOUP")
            assertTrue(viewModel.state.value.showingSaved)
            assertEquals(listOf("recipe-2"), viewModel.state.value.visibleSaved.map { it.id.value })

            viewModel.searchSaved("")
            assertEquals(9, viewModel.state.value.visibleSaved.size)
            viewModel.showSaved(false)
            assertFalse(viewModel.state.value.showingSaved)
        }

    @Test
    fun `a search stops filtering once its field is no longer offered`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            (1..9).forEach { n -> recipes.saveRecipe(defaultKitchenId, dish("recipe-$n").copy(title = "Dish $n")) }
            agent.answer = AppResult.Success(emptyList())
            val viewModel = started(recipes = recipes)
            advanceUntilIdle()
            viewModel.searchSaved("Dish 9")
            assertEquals(listOf("recipe-9"), viewModel.state.value.visibleSaved.map { it.id.value })

            // Eight left: the field goes away, and so must the filter it held.
            viewModel.unsave(RecipeId.of("recipe-9").orFail())
            advanceUntilIdle()

            assertEquals("", viewModel.state.value.savedQuery)
            assertEquals(8, viewModel.state.value.visibleSaved.size)
        }

    @Test
    fun `unsaving from a card removes it and an undo saves it again`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            recipes.saveRecipe(defaultKitchenId, dish("recipe-1"))
            agent.answer = AppResult.Success(emptyList())
            val viewModel = started(recipes = recipes)
            advanceUntilIdle()
            val seen = mutableListOf<SuggestionsEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.unsave(RecipeId.of("recipe-1").orFail())
            advanceUntilIdle()
            assertTrue(recipes.saved.isEmpty())
            assertTrue(viewModel.state.value.savedRecipes.isEmpty())

            viewModel.undoUnsave((seen.single() as SuggestionsEvent.Unsaved).restore)
            advanceUntilIdle()
            collector.cancel()

            assertEquals(listOf("recipe-1"), recipes.saved.map { it.id.value })
            assertEquals(listOf("recipe-1"), viewModel.state.value.savedRecipes.map { it.id.value })
        }

    @Test
    fun `an unsave reads as removed from saved`() {
        assertEquals(UiText.of(Res.string.snack_unsaved), SuggestionsEvent.Unsaved(dish("recipe-1")).sentence())
    }
}
