package com.kitchenai.admin.requests

/** A conversion awaiting confirmation: [preview] is the dry run's count of what it will change. */
data class ConversionState(
    val request: IngredientRequest,
    val preview: ConversionCounts,
    val isBusy: Boolean = false,
    val message: String? = null,
)
