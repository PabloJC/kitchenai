package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.data.remote.dto.RecipeDto

/**
 * A Firestore document decoded into [RecipeDto], paired with the id Firestore keys it by — the
 * DTO itself carries none.
 */
data class RecipeDocument(
    val id: String,
    val dto: RecipeDto,
)
