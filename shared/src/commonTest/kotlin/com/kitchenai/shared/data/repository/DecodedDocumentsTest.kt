package com.kitchenai.shared.data.repository

import com.kitchenai.shared.data.mapper.document
import com.kitchenai.shared.data.mapper.shoppingItemDocument
import com.kitchenai.shared.data.mapper.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule a corrupt document must obey: it costs itself, never the list around it. Emptying a
 * user's pantry or shopping list because one row will not decode is the failure this guards against.
 */
class DecodedDocumentsTest {
    @Test
    fun `drops the pantry document it cannot map and keeps its siblings`() {
        val decoded =
            listOf(
                document().toDomain("item-1"),
                document(unitTaxonomy = "taxonomy-1").toDomain("item-2"),
                document().toDomain("item-3"),
            )

        val kept = decoded.decodedOrDropped()

        assertEquals(listOf("item-1", "item-3"), kept.map { it.id.value })
    }

    @Test
    fun `drops the shopping document it cannot map and keeps its siblings`() {
        val decoded =
            listOf(
                shoppingItemDocument().toDomain("item-1"),
                shoppingItemDocument(freeText = "text-1").toDomain("item-2"),
                shoppingItemDocument().toDomain("item-3"),
            )

        val kept = decoded.decodedOrDropped()

        assertEquals(listOf("item-1", "item-3"), kept.map { it.id.value })
    }

    @Test
    fun `drops every document that fails whatever made it fail`() {
        val decoded =
            listOf(
                document().toDomain(" "),
                document(ingredientId = "").toDomain("item-2"),
                document().toDomain("item-3"),
            )

        val kept = decoded.decodedOrDropped()

        assertEquals(listOf("item-3"), kept.map { it.id.value })
    }
}
