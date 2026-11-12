package com.kitchenai.admin.requests

import com.kitchenai.admin.core.catchingFailures
import dev.gitlive.firebase.functions.FirebaseFunctions

class FirebaseItemConverter(
    private val functions: FirebaseFunctions,
) : ItemConverter {
    override suspend fun convert(
        requestId: String,
        dryRun: Boolean,
    ): Result<ConversionCounts> =
        catchingFailures {
            functions
                .httpsCallable(MIGRATE_FREE_TEXT)
                // Reified, as in FirebaseCatalogueWriter: the serializer overload throws on JS.
                .invoke(ConvertItemsRequest(requestId, dryRun))
                .data<ConversionCounts>()
        }.recoverCatching { failure ->
            // GitLive prefixes the callable's own message with itself: show the callable's words once.
            throw IllegalStateException(failure.cause?.message ?: failure.message, failure)
        }

    private companion object {
        const val MIGRATE_FREE_TEXT = "migrateFreeText"
    }
}
