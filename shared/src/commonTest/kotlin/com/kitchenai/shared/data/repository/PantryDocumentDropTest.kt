package com.kitchenai.shared.data.repository

import com.kitchenai.shared.data.mapper.document
import com.kitchenai.shared.data.mapper.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule a corrupt document must obey: it costs itself, never the list around it. Emptying a
 * user's pantry because one row will not decode is the failure this guards against.
 */
class PantryDocumentDropTest {
    @Test
    fun `drops the document it cannot map and keeps its siblings`() {
        val decoded =
            listOf(
                document().toDomain("item-1"),
                document(unitTaxonomy = "taxonomy-1").toDomain("item-2"),
                document().toDomain("item-3"),
            )

        val mapped = decoded.mapped()

        assertEquals(listOf("item-1", "item-3"), mapped.items.map { it.id.value })
    }

    @Test
    fun `counts every document it dropped`() {
        val decoded =
            listOf(
                document().toDomain(" "),
                document(ingredientId = "").toDomain("item-2"),
                document().toDomain("item-3"),
            )

        val mapped = decoded.mapped()

        assertEquals(2, mapped.dropped)
        assertEquals(1, mapped.items.size)
    }
}
