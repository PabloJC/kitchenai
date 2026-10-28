package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.KitchenDto
import com.kitchenai.shared.data.remote.dto.MemberAvoidsDto
import com.kitchenai.shared.data.remote.dto.TermRefDto
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.model.IngredientId
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
        memberAvoids = memberAvoids.mapValues { (_, avoids) -> avoids.toDto() },
    )

fun HouseholdAvoids.toDto(): MemberAvoidsDto =
    MemberAvoidsDto(
        terms = terms.map { TermRefDto(it.taxonomy.value, it.term.value) },
        ingredients = ingredients.map { it.value },
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
                        memberAvoids = memberAvoids.mapValues { (_, avoids) -> avoids.toDomain() },
                    )
                }
            }
        }
    }
}

private fun missing(field: String): AppResult.Failure = AppResult.Failure(AppError.Validation(field, "is missing"))

/**
 * A reference that does not decode is dropped rather than failing the kitchen: it could never
 * equal a recipe's valid tag, so nothing it would have ruled out slips through.
 */
private fun MemberAvoidsDto.toDomain(): HouseholdAvoids =
    HouseholdAvoids(
        terms =
            terms
                .mapNotNull { ref ->
                    val taxonomy = ref.taxonomy ?: return@mapNotNull null
                    val term = ref.term ?: return@mapNotNull null
                    (termRef(taxonomy, term) as? AppResult.Success)?.data
                }.toSet(),
        ingredients = ingredients.mapNotNull { (IngredientId.of(it) as? AppResult.Success)?.data }.toSet(),
    )
