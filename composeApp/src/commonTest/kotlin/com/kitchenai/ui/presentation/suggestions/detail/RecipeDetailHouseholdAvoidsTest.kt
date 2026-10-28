package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.ui.presentation.common.kitchen
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailHouseholdAvoidsTest : RecipeDetailViewModelHarness() {
    private val rice = (IngredientId.of("rice") as AppResult.Success).data

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an ingredient someone in the kitchen avoids is named but nobody is`() =
        runTest(dispatcher) {
            kitchens.emit(kitchen().copy(memberAvoids = mapOf("user-2" to HouseholdAvoids(ingredients = setOf(rice)))))

            val viewModel = started(pantry = emptyList())
            advanceUntilIdle()

            assertEquals(listOf("rice"), viewModel.state.value.avoidedByHousehold)
        }

    @Test
    fun `a dish nobody avoids carries no warning`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = emptyList())
            advanceUntilIdle()

            assertTrue(viewModel.state.value.avoidedByHousehold.isEmpty())
        }

    @Test
    fun `an Avoid added while the dish is open warns at once`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = emptyList())
            advanceUntilIdle()

            kitchens.emit(kitchen().copy(memberAvoids = mapOf("user-2" to HouseholdAvoids(ingredients = setOf(rice)))))
            advanceUntilIdle()

            assertEquals(listOf("rice"), viewModel.state.value.avoidedByHousehold)
        }
}
