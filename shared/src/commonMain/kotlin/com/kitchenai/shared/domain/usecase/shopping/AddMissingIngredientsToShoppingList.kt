package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.AddedToListSummary
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.scaledTo
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher

/**
 * Puts everything a recipe needs and the pantry does not cover onto a shopping list.
 *
 * Every read is one-shot: a list built from the first emission of a listener would hang for
 * good once that listener had failed.
 */
class AddMissingIngredientsToShoppingList(
    private val recipes: RecipeRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
        recipeId: RecipeId,
        servings: Int,
    ): AppResult<AddedToListSummary> =
        when (val found = recipes.getRecipe(recipeId)) {
            is AppResult.Failure -> found
            is AppResult.Success -> invoke(userId, listId, found.data, servings)
        }

    /**
     * For a recipe the caller already holds. A generated dish lives nowhere a repository can be
     * asked about, so re-reading it by id would fail for the only kind this app suggests.
     */
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
        recipe: Recipe,
        servings: Int,
    ): AppResult<AddedToListSummary> {
        val scaled = recipe.scaledTo(servings)
        if (scaled is AppResult.Failure) return scaled
        val held = pantry.getPantry(userId)
        if (held is AppResult.Failure) return held
        val current = shoppingItems.getItems(userId, listId)
        if (current is AppResult.Failure) return current
        return add(
            userId,
            listId,
            (scaled as AppResult.Success).data,
            (held as AppResult.Success).data,
            (current as AppResult.Success).data,
        )
    }

    private suspend fun add(
        userId: UserId,
        listId: ShoppingListId,
        recipe: Recipe,
        held: List<PantryItem>,
        current: List<ShoppingItem>,
    ): AppResult<AddedToListSummary> {
        val wanted = PantryMatcher.match(recipe, held, time.now()).wanted(recipe.id)
        val summary = AddedToListSummary(added = wanted.size, skipped = recipe.ingredients.size - wanted.size)
        if (wanted.isEmpty()) return AppResult.Success(summary)
        return when (val drafted = draft(current, wanted)) {
            is AppResult.Failure -> drafted
            is AppResult.Success -> shoppingItems.upsertItems(userId, listId, drafted.data).map { summary }
        }
    }

    /**
     * Unverifiable lines are wanted too: being unable to check the pantry is not evidence the
     * user has it, and a line missing from the list is worse than a redundant one. Optional
     * ones are left out — nobody shops for a garnish they did not ask for.
     */
    private fun PantryMatch.wanted(recipeId: RecipeId): List<ShoppingLine> {
        val short =
            missing.filterNot { it.ingredient.optional }
                // The shortfall is what is left to buy; without one the whole amount is.
                .map { it.ingredient.asLine(it.shortfall ?: it.ingredient.quantity, recipeId) }
        val unchecked = unverifiable.filterNot { it.optional }.map { it.asLine(it.quantity, recipeId) }
        return short + unchecked
    }

    // Free text stays free text and a catalogue id stays an id: the client never writes prose,
    // so an unverifiable catalogue line cannot be turned into words here.
    private fun RecipeIngredient.asLine(
        wantedQuantity: Quantity?,
        recipeId: RecipeId,
    ): ShoppingLine = ShoppingLine(ingredient, freeText, wantedQuantity, recipeId)

    /** One write, so the drafts are folded against each other before any of them leaves. */
    private fun draft(
        current: List<ShoppingItem>,
        wanted: List<ShoppingLine>,
    ): AppResult<List<ShoppingItem>> {
        val working = current.toMutableList()
        val touched = LinkedHashMap<ShoppingItemId, ShoppingItem>()
        for (line in wanted) {
            val item =
                when (val drafted = draftShoppingLine(working, line, ids, time)) {
                    is AppResult.Failure -> return drafted
                    is AppResult.Success -> drafted.data
                }
            working.removeAll { it.id == item.id }
            working += item
            touched[item.id] = item
        }
        return AppResult.Success(touched.values.toList())
    }
}
