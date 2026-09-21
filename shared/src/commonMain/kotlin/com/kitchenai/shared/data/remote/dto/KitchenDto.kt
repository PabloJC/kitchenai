package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `kitchens/{kitchenId}` document. The identifier is the document id.
 *
 * [memberDisplayNames] is keyed by plain member uid, not wrapped: it travels straight to iOS
 * through [com.kitchenai.shared.domain.model.Kitchen]'s own `Flow`, and a value-class key would
 * have made this the first exception to how every other public map is keyed.
 */
@Serializable
data class KitchenDto(
    val ownerId: String? = null,
    val memberIds: List<String> = emptyList(),
    val joinCode: String? = null,
    val memberDisplayNames: Map<String, String> = emptyMap(),
)
