package com.kitchenai.shared.data.repository

import com.kitchenai.shared.data.mapper.shoppingItemDocument
import com.kitchenai.shared.data.mapper.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two rules a snapshot has to obey before it reaches the domain: a delete never exceeds what
 * Firestore will commit, and a corrupt document costs itself rather than the list around it.
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

    @Test
    fun `drops the document it cannot map and keeps its siblings`() {
        val decoded =
            listOf(
                shoppingItemDocument().toDomain("item-1"),
                shoppingItemDocument(freeText = "text-1").toDomain("item-2"),
                shoppingItemDocument().toDomain("item-3"),
            )

        val kept = decoded.decodedOrDropped()

        assertEquals(listOf("item-1", "item-3"), kept.map { it.id.value })
    }
}
