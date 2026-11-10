package com.kitchenai.admin.catalogue

import com.kitchenai.shared.data.remote.dto.IngredientDto

/** The admin's catalogue writes. Failures carry a message meant to be shown as is. */
interface CatalogueWriter {
    /** Creates or replaces the whole document, through `writeCatalogue`'s validator. */
    suspend fun writeIngredient(
        id: String,
        document: IngredientDto,
    ): Result<Unit>

    suspend fun deleteIngredient(id: String): Result<Unit>

    /** Ids of the catalogue recipes that name the ingredient; a hard delete is refused while any do. */
    suspend fun recipesReferencing(ingredientId: String): Result<List<String>>
}
