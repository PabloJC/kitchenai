package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef

/**
 * Every document that points at a vocabulary stores the pair as two nullable strings, so every
 * mapper decodes it the same way. Here rather than in one of them, since none owns the shape.
 */
internal fun termRef(
    taxonomy: String,
    term: String,
): AppResult<TermRef> = TaxonomyId.of(taxonomy).flatMap { id -> TermId.of(term).map { TermRef(id, it) } }

/**
 * Absent is null; half-specified is corruption. A taxonomy without its term would otherwise
 * decode into a silent null and lose the fact that the document is wrong.
 */
internal fun termRefOrNull(
    taxonomy: String?,
    term: String?,
    field: String,
): AppResult<TermRef?> =
    when {
        taxonomy == null && term == null -> AppResult.Success(null)
        taxonomy == null || term == null -> AppResult.Failure(AppError.Validation(field, "incomplete term reference"))
        else -> termRef(taxonomy, term)
    }
