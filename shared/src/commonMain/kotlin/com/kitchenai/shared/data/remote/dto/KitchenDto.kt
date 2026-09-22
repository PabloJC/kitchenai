package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `kitchens/{kitchenId}` document. The identifier is the document id.
 *
 * [memberDisplayNames] is keyed by plain member uid, not wrapped: it travels straight to iOS
 * through [com.kitchenai.shared.domain.model.Kitchen]'s own `Flow`, and a value-class key would
 * have made this the first exception to how every other public map is keyed.
 *
 * [removedMemberIds] has no domain-model counterpart: nothing above the Firestore layer reads
 * it. It exists purely for `firestore.rules`' `isSelfJoin` to reject a rejoin from a uid the
 * owner already removed — the join code alone cannot revoke someone who still remembers the
 * kitchen id, which every former member does by construction.
 */
@Serializable
data class KitchenDto(
    val ownerId: String? = null,
    val memberIds: List<String> = emptyList(),
    val joinCode: String? = null,
    val memberDisplayNames: Map<String, String> = emptyMap(),
    val removedMemberIds: List<String> = emptyList(),
)
