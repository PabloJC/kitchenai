package com.kitchenai.admin.requests

import kotlinx.serialization.Serializable

/** The `migrateFreeText` payload. */
@Serializable
data class ConvertItemsRequest(
    val requestId: String,
    val dryRun: Boolean,
)
