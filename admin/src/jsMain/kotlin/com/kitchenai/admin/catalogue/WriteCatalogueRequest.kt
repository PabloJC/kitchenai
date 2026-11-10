package com.kitchenai.admin.catalogue

import com.kitchenai.shared.data.remote.dto.IngredientDto
import kotlinx.serialization.Serializable

/** The `writeCatalogue` payload for an ingredient; [kind] is the only one this admin sends yet. */
@Serializable
data class WriteCatalogueRequest(
    val kind: String,
    val id: String,
    val data: IngredientDto,
)
