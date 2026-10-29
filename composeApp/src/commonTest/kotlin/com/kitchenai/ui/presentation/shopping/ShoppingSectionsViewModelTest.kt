package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.defaultKitchenId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The list in the shop's order, and which recipe a line is for. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingSectionsViewModelTest : ShoppingViewModelHarness() {
    @Test
    fun `with a sections vocabulary the lines group in its order with free text under the leftovers`() =
        runTest(dispatcher) {
            val viewModel = started()
            taxonomies.taxonomies.emit(listOf(sections))
            taxonomies.terms.emit(listOf(term(fruit, "Fruit", 1), term(dairy, "Dairy", 2)))
            catalogue.emit(listOf(Ingredient(ingredientId, mapOf("en" to "Milk"), null, listOf(dairy))))
            items.emit(listOf(line("item-1", freeText = "written by hand"), line("item-2")))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.grouped)
            assertEquals(listOf("Dairy", null), state.toBuy.map { it.title })
            assertEquals(
                listOf(listOf("item-2"), listOf("item-1")),
                state.toBuy.map {
                        g ->
                    g.lines.map { it.id.value }
                },
            )
        }

    @Test
    fun `with no shop sections in the catalogue the list stays one group`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1"), line("item-2")))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.grouped)
            assertEquals(listOf(2), viewModel.state.value.toBuy.map { it.lines.size })
        }

    @Test
    fun `a line from a saved recipe says which recipe it is for`() =
        runTest(dispatcher) {
            recipes.saveRecipe(defaultKitchenId, savedDish)
            val viewModel = started()
            items.emit(listOf(line("item-1", sourceRecipe = savedDish.id)))
            advanceUntilIdle()

            assertEquals("Pasta alla Norma", viewModel.state.value.unchecked.single().forRecipe)
        }

    @Test
    fun `a line from a dish nobody kept is indistinguishable from one added by hand`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1", sourceRecipe = recipeId), line("item-2")))
            advanceUntilIdle()

            // A recipe neither saved nor stored has no title anywhere, so nothing about it may reach
            // the row. Compared whole: any future field carrying the id would fail here.
            val (fromDish, byHand) = viewModel.state.value.unchecked
            assertEquals(byHand, fromDish.copy(id = byHand.id))
        }

    private fun term(
        ref: TermRef,
        label: String,
        order: Int,
    ) = Term(ref, mapOf("en" to label), null, order)
}

private val sectionsId = unwrap(TaxonomyId.of("food-groups"))
private val sections = Taxonomy(sectionsId, mapOf("en" to "Food group"), "en", TaxonomyPurpose.SHOPPING_SECTIONS)
private val fruit = TermRef(sectionsId, unwrap(TermId.of("fruit")))
private val dairy = TermRef(sectionsId, unwrap(TermId.of("dairy")))

private val savedDish =
    Recipe(
        id = unwrap(RecipeId.of("recipe-saved")),
        title = "Pasta alla Norma",
        summary = null,
        servings = 2,
        totalMinutes = null,
        ingredients = emptyList(),
        steps = emptyList(),
        tags = emptyList(),
        source = RecipeSource.Catalogue,
    )
