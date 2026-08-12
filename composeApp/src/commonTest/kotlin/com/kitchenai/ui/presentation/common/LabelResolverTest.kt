package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelResolverTest {
    @Test
    fun `an exact language tag beats every other candidate`() {
        val resolver = resolverFor(labels = mapOf("en" to "generic", "en-GB" to "exact"), tags = listOf("en-GB"))

        assertEquals("exact", resolver.label(termRef))
    }

    @Test
    fun `a regional tag falls back to its primary subtag`() {
        val resolver = resolverFor(labels = mapOf("en" to "generic"), tags = listOf("en-GB"))

        assertEquals("generic", resolver.label(termRef))
    }

    @Test
    fun `a language nobody asked for falls back to the taxonomy default`() {
        val resolver = resolverFor(labels = mapOf("zz" to "declared"), tags = listOf("en"), defaultTag = "zz")

        assertEquals("declared", resolver.label(termRef))
    }

    @Test
    fun `a term outside the snapshot resolves to null so the caller can render the id`() {
        val resolver = LabelResolver(languageTags = listOf("en"))

        assertNull(resolver.label(termRef))
    }

    @Test
    fun `a term whose labels speak no known language resolves to null`() {
        val resolver = resolverFor(labels = mapOf("zz" to "unreadable"), tags = listOf("en"))

        assertNull(resolver.label(termRef))
    }

    @Test
    fun `an ingredient resolves against its own labels`() {
        val ingredient = Ingredient(ingredientId, mapOf("en" to "catalogued"), null, emptyList())
        val resolver = LabelResolver(ingredients = listOf(ingredient), languageTags = listOf("en"))

        assertEquals("catalogued", resolver.label(ingredientId))
    }

    @Test
    fun `an ingredient outside the snapshot resolves to null`() {
        val resolver = LabelResolver(languageTags = listOf("en"))

        assertNull(resolver.label(ingredientId))
    }

    private fun resolverFor(
        labels: Map<String, String>,
        tags: List<String>,
        defaultTag: String? = null,
    ): LabelResolver =
        LabelResolver(
            terms = listOf(Term(termRef, labels, parent = null, order = 0)),
            taxonomies = listOf(Taxonomy(termRef.taxonomy, emptyMap(), defaultTag)),
            languageTags = tags,
        )
}

// Opaque identifiers on purpose: naming a real term or ingredient here would be the contextual
// constant the domain refuses to hold.
private val termRef = TermRef(unwrap(TaxonomyId.of("tx-1")), unwrap(TermId.of("tm-1")))
private val ingredientId = unwrap(IngredientId.of("ing-1"))

private fun <T> unwrap(result: AppResult<T>): T = (result as AppResult.Success).data
