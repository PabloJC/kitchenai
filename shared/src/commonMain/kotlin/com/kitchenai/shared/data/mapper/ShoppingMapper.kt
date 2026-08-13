package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.ShoppingItemDto
import com.kitchenai.shared.data.remote.dto.ShoppingListDto
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import kotlin.time.Instant

fun ShoppingList.toDto(): ShoppingListDto =
    ShoppingListDto(
        ownerId = ownerId.value,
        labels = labels,
        updatedAtMillis = updatedAt.toEpochMilliseconds(),
    )

/** The document id is the identifier: the payload never repeats it. */
fun ShoppingListDto.toDomain(documentId: String): AppResult<ShoppingList> =
    ShoppingListId.of(documentId).flatMap { id ->
        UserId.of(ownerId).map { owner ->
            ShoppingList(id, owner, labels, Instant.fromEpochMilliseconds(updatedAtMillis))
        }
    }

fun ShoppingItem.toDto(): ShoppingItemDto =
    ShoppingItemDto(
        ingredientId = ingredient?.value,
        freeText = freeText,
        amount = quantity?.amount,
        unitTaxonomy = quantity?.unit?.taxonomy?.value,
        unitTerm = quantity?.unit?.term?.value,
        checked = checked,
        sourceRecipeId = sourceRecipe?.value,
        updatedAtMillis = updatedAt.toEpochMilliseconds(),
    )

/**
 * Decoding goes through [ShoppingItem.create], so a document holding both an ingredient and a
 * free-text line — or neither — fails here instead of entering the domain. The factory only
 * builds unchecked lines, hence the copy.
 */
fun ShoppingItemDto.toDomain(documentId: String): AppResult<ShoppingItem> =
    ShoppingItemId.of(documentId).flatMap { id ->
        pointers().flatMap { (ingredient, recipe) ->
            quantityOrNull().flatMap { quantity ->
                ShoppingItem
                    .create(
                        id = id,
                        updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
                        ingredient = ingredient,
                        freeText = freeText,
                        quantity = quantity,
                        sourceRecipe = recipe,
                    ).map { item -> item.copy(checked = checked) }
            }
        }
    }

/** The two optional identifiers a line can carry, paired so the chain above stays flat. */
private fun ShoppingItemDto.pointers(): AppResult<Pair<IngredientId?, RecipeId?>> =
    ingredientId.optional(IngredientId::of).flatMap { ingredient ->
        sourceRecipeId.optional(RecipeId::of).map { recipe -> ingredient to recipe }
    }

/** A unit with no amount is corruption, not an absent quantity: dropping it would hide that. */
private fun ShoppingItemDto.quantityOrNull(): AppResult<Quantity?> =
    when {
        amount == null && unitTaxonomy == null && unitTerm == null -> AppResult.Success(null)
        amount == null -> AppResult.Failure(AppError.Validation("amount", "a unit without an amount"))
        else -> termRefOrNull(unitTaxonomy, unitTerm, "unit").map { unit -> Quantity(amount, unit) }
    }

private inline fun <T> String?.optional(of: (String) -> AppResult<T>): AppResult<T?> =
    if (this == null) AppResult.Success(null) else of(this)
