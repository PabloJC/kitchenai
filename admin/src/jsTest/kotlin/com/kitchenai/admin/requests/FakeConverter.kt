package com.kitchenai.admin.requests

/** Answers every call with [counts]; [refusal] fails the real run only, [dryRunRefusal] the count. */
class FakeConverter(
    var counts: ConversionCounts = ConversionCounts(pantryItems = 2, shoppingItems = 1, dryRun = false),
) : ItemConverter {
    val calls = mutableListOf<Pair<String, Boolean>>()
    var refusal: String? = null
    var dryRunRefusal: String? = null

    override suspend fun convert(
        requestId: String,
        dryRun: Boolean,
    ): Result<ConversionCounts> {
        calls += requestId to dryRun
        val refused = if (dryRun) dryRunRefusal else refusal
        if (refused != null) return Result.failure(IllegalStateException(refused))
        return Result.success(counts.copy(dryRun = dryRun))
    }
}
