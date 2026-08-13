package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.TaxonomyDto
import com.kitchenai.shared.data.remote.dto.TermDto
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef

// One direction only: the catalogue is read-only for the client, so an encoder here would be
// public API nothing calls. Seeding the documents is out of scope.

fun TaxonomyDto.toDomain(documentId: String): AppResult<Taxonomy> =
    TaxonomyId.of(documentId).map { id -> Taxonomy(id, labels, defaultLanguageTag, purpose.toPurpose()) }

/**
 * An unrecognised purpose is null, never a failure: a catalogue written by a newer client would
 * otherwise break an older app, and a vocabulary this version cannot place is one it can still
 * display.
 */
private fun String?.toPurpose(): TaxonomyPurpose? = TaxonomyPurpose.entries.firstOrNull { it.name == this }

/**
 * A term with no labels maps to an empty label map rather than to a failure: the term exists and
 * the UI can fall back to its id, while dropping it would hide a choice the catalogue offers.
 */
fun TermDto.toDomain(
    taxonomy: TaxonomyId,
    documentId: String,
): AppResult<Term> {
    val id = TermId.of(documentId).getOrElse { return AppResult.Failure(it) }
    val parentId = parent?.let { raw -> TermId.of(raw).getOrElse { failure -> return AppResult.Failure(failure) } }
    return AppResult.Success(Term(TermRef(taxonomy, id), labels, parentId, order))
}
