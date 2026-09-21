package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `kitchenInvites/{joinCode}` document: only enough to resolve a code to a kitchen. The
 * identifier is the join code itself, never repeated inside the payload.
 */
@Serializable
data class KitchenInviteDto(
    val kitchenId: String? = null,
)
