package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.TaxonomyDto
import com.kitchenai.shared.data.remote.dto.TermDto
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Labels are placeholders and identifiers are opaque: a fixture that named a real cuisine would
// be the hardcoded vocabulary the catalogue exists to replace.
class TaxonomyMapperTest {
    private val taxonomy = (TaxonomyId.of("t-1") as AppResult.Success).data

    @Test
    fun `a catalogue document keeps its labels and its declared default language`() {
        val dto = TaxonomyDto(labels = mapOf("xx" to "Label"), defaultLanguageTag = "xx")

        assertEquals(AppResult.Success(Taxonomy(taxonomy, dto.labels, "xx")), dto.toDomain("t-1"))
    }

    @Test
    fun `a catalogue document with a blank id fails`() {
        assertTrue(TaxonomyDto().toDomain("") is AppResult.Failure)
    }

    @Test
    fun `a term with no labels maps to an empty label map rather than to a failure`() {
        val expected = Term(TermRef(taxonomy, termId("a")), emptyMap(), parent = null, order = 0)

        assertEquals(AppResult.Success(expected), TermDto().toDomain(taxonomy, "a"))
    }

    @Test
    fun `a term keeps its parent and the order the catalogue gave it`() {
        val dto = TermDto(labels = mapOf("xx" to "Label"), parent = "b", order = 7)
        val expected = Term(TermRef(taxonomy, termId("a")), dto.labels, termId("b"), order = 7)

        assertEquals(AppResult.Success(expected), dto.toDomain(taxonomy, "a"))
    }

    @Test
    fun `a term with a blank parent fails instead of losing the relation`() {
        assertTrue(TermDto(parent = " ").toDomain(taxonomy, "a") is AppResult.Failure)
    }

    @Test
    fun `a term with a blank id fails`() {
        assertTrue(TermDto().toDomain(taxonomy, "") is AppResult.Failure)
    }

    private fun termId(raw: String): TermId = (TermId.of(raw) as AppResult.Success).data
}
