package com.kitchenai.admin.catalogue

import com.kitchenai.admin.core.catchingFailures
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.IngredientDto
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.domain.model.IngredientId
import dev.gitlive.firebase.functions.FirebaseFunctions

class FirebaseCatalogueWriter(
    private val functions: FirebaseFunctions,
    private val paths: FirestorePaths,
) : CatalogueWriter {
    override suspend fun writeIngredient(
        id: String,
        document: IngredientDto,
    ): Result<Unit> =
        catchingFailures {
            functions
                .httpsCallable(WRITE_CATALOGUE)
                // Reified: on JS the overload taking a serializer re-encodes its output as `Any` and throws.
                .invoke(WriteCatalogueRequest(INGREDIENT, id, document))
            Unit
        }.recoverCatching { failure ->
            // GitLive prefixes the callable's own message with itself: show the callable's words once.
            throw IllegalStateException(failure.cause?.message ?: failure.message, failure)
        }

    // Straight to Firestore: the callable only writes, and #410's rules let the admin claim delete.
    override suspend fun deleteIngredient(id: String): Result<Unit> =
        catchingFailures { paths.ingredient(ingredientId(id)).delete() }

    override suspend fun recipesReferencing(ingredientId: String): Result<List<String>> =
        catchingFailures {
            val recipes =
                paths.recipes().get().documents.associate {
                        doc ->
                    doc.id to doc.data(RecipeDto.serializer())
                }
            recipesReferencing(ingredientId, recipes)
        }

    private fun ingredientId(raw: String): IngredientId =
        when (val id = IngredientId.of(raw)) {
            is AppResult.Success -> id.data
            is AppResult.Failure -> throw IllegalArgumentException("Not an ingredient id")
        }

    private companion object {
        const val WRITE_CATALOGUE = "writeCatalogue"
        const val INGREDIENT = "ingredient"
    }
}
