package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
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
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
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
    ShoppingListId.of(documentId).andThen { id ->
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
    ShoppingItemId.of(documentId).andThen { id ->
        pointers().andThen { (ingredient, recipe) ->
            quantityOrNull().andThen { quantity ->
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
    ingredientId.optional(IngredientId::of).andThen { ingredient ->
        sourceRecipeId.optional(RecipeId::of).map { recipe -> ingredient to recipe }
    }

/** A unit with no amount is corruption, not an absent quantity: dropping it would hide that. */
private fun ShoppingItemDto.quantityOrNull(): AppResult<Quantity?> =
    when {
        amount == null && unitTaxonomy == null && unitTerm == null -> AppResult.Success(null)
        amount == null -> AppResult.Failure(AppError.Validation("amount", "a unit without an amount"))
        else -> unitOrNull().map { unit -> Quantity(amount, unit) }
    }

/** Absent is null; half-specified is corruption. A taxonomy without its term decodes to neither. */
private fun ShoppingItemDto.unitOrNull(): AppResult<TermRef?> =
    when {
        unitTaxonomy == null && unitTerm == null -> AppResult.Success(null)
        unitTaxonomy == null || unitTerm == null ->
            AppResult.Failure(AppError.Validation("unit", "incomplete term reference"))
        else -> TaxonomyId.of(unitTaxonomy).andThen { taxonomy -> TermId.of(unitTerm).map { TermRef(taxonomy, it) } }
    }

private inline fun <T> String?.optional(of: (String) -> AppResult<T>): AppResult<T?> =
    if (this == null) AppResult.Success(null) else of(this)

/**
 * `map` for a transform that can itself fail. Kept private to this file: the pantry mapper of
 * the sibling branch declares its own, and two identical package-level helpers would collide.
 */
private inline fun <T, R> AppResult<T>.andThen(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(data)
        is AppResult.Failure -> this
    }
