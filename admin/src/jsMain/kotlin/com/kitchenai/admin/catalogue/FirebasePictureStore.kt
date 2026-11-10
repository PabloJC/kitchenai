package com.kitchenai.admin.catalogue

import com.kitchenai.admin.core.catchingFailures
import com.kitchenai.shared.domain.port.TimeProvider
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata
import org.w3c.files.Blob
import org.w3c.files.FilePropertyBag

class FirebasePictureStore(
    private val storage: FirebaseStorage,
    private val time: TimeProvider,
) : PictureStore {
    override suspend fun uploadIngredientPicture(
        ingredientId: String,
        picture: Blob,
    ): Result<String> =
        catchingFailures {
            val path = ingredientPicturePath(ingredientId, time.now().toEpochMilliseconds())
            val reference = storage.reference(path)
            val file = File(arrayOf(picture), path.substringAfterLast('/'), FilePropertyBag(type = WEBP))
            reference.putFile(file, storageMetadata { contentType = WEBP })
            reference.getDownloadUrl()
        }

    override suspend fun delete(url: String): Result<Unit> =
        if (isIngredientPictureUrl(url)) catchingFailures { storage.reference(url).delete() } else Result.success(Unit)

    private companion object {
        const val WEBP = "image/webp"
    }
}
