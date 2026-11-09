package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.ui.presentation.common.FakeRecipePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsImagesTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun keyed(id: String): RecipeSuggestion =
        suggestion(id).let { it.copy(recipe = it.recipe.copy(imageKey = id.padEnd(64, 'a'))) }

    @Test
    fun `a new set's cards get their own photos without opening them`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            agent.answer = AppResult.Success(listOf(keyed("new-1"), keyed("new-2")))
            val viewModel = started(recipes)
            advanceUntilIdle()

            assertEquals(listOf("new-1", "new-2"), recipes.imageRequests.map { it.id.value })
            assertTrue(viewModel.state.value.suggestions.all { it.imageUrl == "https://images.test/recipe.jpg" })
        }

    @Test
    fun `the stored set is pictured on launch, earlier sets are not`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(stored = listOf(keyed("stored-1").recipe))
            agent.answer = AppResult.Failure(AppError.DailyLimit)
            started(recipes)
            advanceUntilIdle()

            assertEquals(listOf("stored-1"), recipes.imageRequests.map { it.id.value })
        }

    @Test
    fun `a set already pictured or with no keys makes no call`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            val pictured =
                keyed("new-1").let { it.copy(recipe = it.recipe.copy(imageUrl = "https://images.test/a.jpg")) }
            agent.answer = AppResult.Success(listOf(pictured, suggestion("plain")))
            started(recipes)
            advanceUntilIdle()

            assertTrue(recipes.imageRequests.isEmpty())
        }

    @Test
    fun `offline, nothing is asked for`() =
        runTest(dispatcher) {
            network.setOnline(false)
            val recipes = FakeRecipePort(stored = listOf(keyed("stored-1").recipe))
            started(recipes)
            advanceUntilIdle()

            assertTrue(recipes.imageRequests.isEmpty())
        }

    @Test
    fun `a failed photo keeps the dish-type photo and shows no error`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(imageResult = AppResult.Failure(AppError.Network(null)))
            agent.answer = AppResult.Success(listOf(keyed("new-1")))
            val viewModel = started(recipes)
            advanceUntilIdle()

            assertNull(viewModel.state.value.suggestions.single().imageUrl)
            assertNull(viewModel.state.value.error)
        }
}
