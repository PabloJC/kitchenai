package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.KitchenDto
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId

fun Kitchen.toDto(): KitchenDto =
    KitchenDto(
        ownerId = ownerId.value,
        memberIds = memberIds.map { it.value },
        joinCode = joinCode.value,
        memberDisplayNames = memberDisplayNames,
    )

/** The document id is the kitchen id. */
fun KitchenDto.toDomain(documentId: String): AppResult<Kitchen> {
    val owner = ownerId ?: return missing("ownerId")
    val code = joinCode ?: return missing("joinCode")
    return KitchenId.of(documentId).flatMap { id ->
        UserId.of(owner).flatMap { ownerUserId ->
            memberIds.mapAll(UserId::of).flatMap { members ->
                KitchenJoinCode.of(code).map { joinCodeValue ->
                    Kitchen(
                        id = id,
                        ownerId = ownerUserId,
                        memberIds = members.toSet(),
                        joinCode = joinCodeValue,
                        memberDisplayNames = memberDisplayNames,
                    )
                }
            }
        }
    }
}

private fun missing(field: String): AppResult.Failure = AppResult.Failure(AppError.Validation(field, "is missing"))
