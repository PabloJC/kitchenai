package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/** One member's entry in `kitchens/{id}.memberAvoids`: their Avoid terms and avoided ingredient ids. */
@Serializable
data class MemberAvoidsDto(
    val terms: List<TermRefDto> = emptyList(),
    val ingredients: List<String> = emptyList(),
)
