package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.IngredientDto
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TermRef

/**
 * One direction only: the catalogue is read-only for the client, so an encoder here would be
 * public API nothing calls.
 */
fun IngredientDto.toDomain(documentId: String): AppResult<Ingredient> =
    IngredientId.of(documentId).flatMap { id ->
        termRefOrNull(defaultUnitTaxonomy, defaultUnitTerm, "defaultUnit").flatMap { unit ->
            tags.toTermRefs().map { refs -> Ingredient(id, labels, unit, refs) }
        }
    }

/** The first unusable tag fails the document: a partly decoded catalogue entry is worse than none. */
private fun Map<String, List<String>>.toTermRefs(): AppResult<List<TermRef>> {
    val seed: AppResult<List<TermRef>> = AppResult.Success(emptyList())
    return entries
        .flatMap { (taxonomy, terms) -> terms.map { term -> taxonomy to term } }
        .fold(seed) { acc, (taxonomy, term) ->
            acc.flatMap { refs -> termRef(taxonomy, term).map { refs + it } }
        }
}
