package com.kitchenai.admin.catalogue

import org.w3c.files.Blob

/** Catalogue pictures in Storage. Failures carry a message meant to be shown as is. */
interface PictureStore {
    /** Uploads a WebP for [ingredientId] and returns its download URL. */
    suspend fun uploadIngredientPicture(
        ingredientId: String,
        picture: Blob,
    ): Result<String>

    /** Removes a picture this admin uploaded; any other URL is left alone and counts as done. */
    suspend fun delete(url: String): Result<Unit>
}
