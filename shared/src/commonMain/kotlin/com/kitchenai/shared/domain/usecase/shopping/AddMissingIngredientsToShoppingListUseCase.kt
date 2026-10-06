package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.AddedToListSummary
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.ceilToWhole
import com.kitchenai.shared.domain.model.scaledTo
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Puts everything a recipe needs and the pantry does not cover onto a shopping list.
 *
 * Every read is one-shot: a list built from the first emission of a listener would hang for
 * good once that listener had failed.
 */
class AddMissingIngredientsToShoppingListUseCase(
    private val recipes: RecipeRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val ingredients: IngredientRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        recipeId: RecipeId,
        servings: Int,
    ): AppResult<AddedToListSummary> =
        when (val found = recipes.getRecipe(recipeId)) {
            is AppResult.Failure -> found
            is AppResult.Success -> invoke(kitchenId, listId, found.data, servings)
        }

    /**
     * For a recipe the caller already holds. A generated dish lives nowhere a repository can be
     * asked about, so re-reading it by id would fail for the only kind this app suggests.
     */
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        recipe: Recipe,
        servings: Int,
    ): AppResult<AddedToListSummary> {
        val scaled = recipe.scaledTo(servings)
        if (scaled is AppResult.Failure) return scaled
        val held = pantry.getPantry(kitchenId)
        if (held is AppResult.Failure) return held
        val current = shoppingItems.getItems(kitchenId, listId)
        if (current is AppResult.Failure) return current
        return add(
            kitchenId,
            listId,
            (scaled as AppResult.Success).data,
            (held as AppResult.Success).data,
            (current as AppResult.Success).data,
        )
    }

    private suspend fun add(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        recipe: Recipe,
        held: List<PantryItem>,
        current: List<ShoppingItem>,
    ): AppResult<AddedToListSummary> =
        when (val converter = units()) {
            is AppResult.Failure -> converter
            is AppResult.Success -> addWith(kitchenId, listId, recipe, held, current, converter.data)
        }

    private suspend fun addWith(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        recipe: Recipe,
        held: List<PantryItem>,
        current: List<ShoppingItem>,
        converter: UnitConverter,
    ): AppResult<AddedToListSummary> {
        val wanted = PantryMatcher.match(recipe, held, time.now(), converter).wanted(recipe.id, converter)
        val summary = AddedToListSummary(added = wanted.size, skipped = recipe.ingredients.size - wanted.size)
        if (wanted.isEmpty()) return AppResult.Success(summary)
        return when (val drafted = draft(current, wanted, converter)) {
            is AppResult.Failure -> drafted
            is AppResult.Success -> shoppingItems.upsertItems(kitchenId, listId, drafted.data).map { summary }
        }
    }

    /**
     * Unverifiable lines are wanted too: being unable to check the pantry is not evidence the
     * user has it, and a line missing from the list is worse than a redundant one. Optional
     * ones are left out — nobody shops for a garnish they did not ask for.
     */
    private suspend fun PantryMatch.wanted(
        recipeId: RecipeId,
        converter: UnitConverter,
    ): List<ShoppingLine> {
        val missingWanted = missing.filterNot { it.ingredient.optional }
        val uncheckedWanted = unverifiable.filterNot { it.optional }
        val catalogue =
            catalogueOf(
                missingWanted.mapNotNull { it.ingredient.ingredient } + uncheckedWanted.mapNotNull { it.ingredient },
            )
        // The shortfall is what is left to buy; without one the whole amount is.
        val short =
            missingWanted.map {
                it.ingredient.asLine(it.shortfall ?: it.ingredient.quantity, recipeId, catalogue, converter)
            }
        val unchecked = uncheckedWanted.map { it.asLine(it.quantity, recipeId, catalogue, converter) }
        return short + unchecked
    }

    /** One read per distinct catalogue id, in parallel: a recipe's ingredients are independent lookups. */
    private suspend fun catalogueOf(ids: List<IngredientId>): Map<IngredientId, Ingredient> =
        coroutineScope {
            ids.distinct()
                .map { id -> async { ingredients.getIngredient(id) } }
                .awaitAll()
                .mapNotNull { (it as? AppResult.Success)?.data }
                .associateBy { it.id }
        }

    // Free text stays free text and a catalogue id stays an id: the client never writes prose,
    // so an unverifiable catalogue line cannot be turned into words here.
    private fun RecipeIngredient.asLine(
        wantedQuantity: Quantity?,
        recipeId: RecipeId,
        catalogue: Map<IngredientId, Ingredient>,
        converter: UnitConverter,
    ): ShoppingLine {
        val rounded = wantedQuantity?.let { roundedToBuyable(ingredient?.let(catalogue::get), it, converter) }
        return ShoppingLine(ingredient, freeText, rounded, recipeId)
    }

    /**
     * A whole-item ingredient is rounded up in its own unit, so an amount in another unit is
     * converted first: 500 g of something sold by the kilo is one kilo, not half of one. An amount
     * that cannot be converted into that unit is left as it is.
     */
    private fun roundedToBuyable(
        catalogued: Ingredient?,
        quantity: Quantity,
        converter: UnitConverter,
    ): Quantity {
        val whole = catalogued?.takeIf { it.purchasedWhole } ?: return quantity
        val inWholeUnit = converter.amountIn(quantity, whole.defaultUnit) ?: return quantity
        return Quantity(inWholeUnit.ceilToWhole(), whole.defaultUnit)
    }

    /** One write, so the drafts are folded against each other before any of them leaves. */
    private fun draft(
        current: List<ShoppingItem>,
        wanted: List<ShoppingLine>,
        converter: UnitConverter,
    ): AppResult<List<ShoppingItem>> {
        val working = current.toMutableList()
        val touched = LinkedHashMap<ShoppingItemId, ShoppingItem>()
        for (line in wanted) {
            val item =
                when (val drafted = draftShoppingLine(working, line, ids, time, converter)) {
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
