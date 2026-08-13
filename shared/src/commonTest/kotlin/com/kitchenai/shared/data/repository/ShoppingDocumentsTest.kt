package com.kitchenai.shared.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule a delete has to obey before it reaches Firestore: it never exceeds what one commit
 * accepts. Dropping a corrupt document is the other rule, and it lives in [DecodedDocumentsTest].
 */
class ShoppingDocumentsTest {
    @Test
    fun `chunks a delete larger than the batch limit`() {
        val documents = List(FIRESTORE_BATCH_LIMIT + 1) { it }

        val chunks = documents.chunkedForBatch()

        assertEquals(listOf(FIRESTORE_BATCH_LIMIT, 1), chunks.map { it.size })
        assertEquals(documents, chunks.flatten())
    }

    @Test
    fun `commits a delete of exactly the batch limit as one batch`() {
        val chunks = List(FIRESTORE_BATCH_LIMIT) { it }.chunkedForBatch()

        assertEquals(1, chunks.size)
    }

    @Test
    fun `has nothing to commit for an empty delete`() {
        assertEquals(emptyList(), emptyList<Int>().chunkedForBatch())
    }
}
