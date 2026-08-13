package com.kitchenai.shared.data.repository

/**
 * Firestore commits at most 500 writes per batch and rejects the whole commit past that, so a
 * user who ticks 600 lines would otherwise delete none of them.
 */
internal const val FIRESTORE_BATCH_LIMIT = 500

/** Pure on purpose: the rule above is worth a test, and a test of it must not need a Firestore. */
internal fun <T> List<T>.chunkedForBatch(): List<List<T>> =
    if (isEmpty()) emptyList() else chunked(FIRESTORE_BATCH_LIMIT)
