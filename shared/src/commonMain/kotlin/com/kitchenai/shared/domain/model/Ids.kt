package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import kotlin.jvm.JvmInline

// Typed identifiers: one concept, one file. Constructors are private and the only way in is
// `of`, because an `init { require(...) }` would throw an exception across a layer boundary.

@JvmInline
value class UserId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<UserId> = nonBlank("UserId", raw).map { UserId(it) }
    }
}

@JvmInline
value class IngredientId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<IngredientId> = nonBlank("IngredientId", raw).map { IngredientId(it) }
    }
}

@JvmInline
value class PantryItemId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<PantryItemId> = nonBlank("PantryItemId", raw).map { PantryItemId(it) }
    }
}

@JvmInline
value class ShoppingListId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<ShoppingListId> = nonBlank("ShoppingListId", raw).map { ShoppingListId(it) }
    }
}

@JvmInline
value class ShoppingItemId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<ShoppingItemId> = nonBlank("ShoppingItemId", raw).map { ShoppingItemId(it) }
    }
}

@JvmInline
value class RecipeId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<RecipeId> = nonBlank("RecipeId", raw).map { RecipeId(it) }
    }
}

@JvmInline
value class AgentId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<AgentId> = nonBlank("AgentId", raw).map { AgentId(it) }
    }
}

@JvmInline
value class TaxonomyId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<TaxonomyId> = nonBlank("TaxonomyId", raw).map { TaxonomyId(it) }
    }
}

@JvmInline
value class TermId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<TermId> = nonBlank("TermId", raw).map { TermId(it) }
    }
}

@JvmInline
value class KitchenId private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<KitchenId> = nonBlank("KitchenId", raw).map { KitchenId(it) }
    }
}

/** The code a `Kitchen` is joined by. Not a secret: it identifies a group to join, not a user. */
@JvmInline
value class KitchenJoinCode private constructor(val value: String) {
    companion object {
        fun of(raw: String): AppResult<KitchenJoinCode> = nonBlank("KitchenJoinCode", raw).map { KitchenJoinCode(it) }
    }
}

/** The single rule every identifier shares. Values are stored verbatim: no trimming, no casing. */
private fun nonBlank(
    field: String,
    raw: String,
): AppResult<String> =
    if (raw.isBlank()) {
        AppResult.Failure(AppError.Validation(field, "must not be blank"))
    } else {
        AppResult.Success(raw)
    }
