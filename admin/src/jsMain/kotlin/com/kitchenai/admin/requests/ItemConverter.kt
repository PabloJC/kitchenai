package com.kitchenai.admin.requests

/** Moves the items still holding a closed request's text onto its ingredient. Failures carry a message to show. */
interface ItemConverter {
    /** With [dryRun], counts what a real run would change and writes nothing. */
    suspend fun convert(
        requestId: String,
        dryRun: Boolean,
    ): Result<ConversionCounts>
}
