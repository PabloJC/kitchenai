package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.DietaryConstraintDto
import com.kitchenai.shared.data.remote.dto.HouseholdDto
import com.kitchenai.shared.data.remote.dto.TermRefDto
import com.kitchenai.shared.data.remote.dto.UserProfileDto
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import kotlin.time.Instant

fun UserProfile.toDto(): UserProfileDto =
    UserProfileDto(
        displayName = displayName,
        languageTags = languageTags,
        household = household.toDto(),
        constraints = constraints.map { it.toDto() },
        preferences = preferences.map { TermRefDto(it.taxonomy.value, it.term.value) },
        avoidedIngredients = avoidedIngredients.map { it.value },
        updatedAtMillis = updatedAt.toEpochMilliseconds(),
    )

/**
 * The document id is the user id. Decoding returns [AppResult] because a document written by an
 * older client, or by hand in the console, is an expected failure rather than a bug.
 */
fun UserProfileDto.toDomain(documentId: String): AppResult<UserProfile> {
    val millis = updatedAtMillis ?: return missing("updatedAt")
    return UserId.of(documentId).flatMap { id ->
        household.toDomain().flatMap { context ->
            decodeReferences().map { refs ->
                UserProfile(
                    userId = id,
                    displayName = displayName,
                    languageTags = languageTags,
                    household = context,
                    constraints = refs.constraints,
                    preferences = refs.preferences,
                    avoidedIngredients = refs.avoided,
                    updatedAt = Instant.fromEpochMilliseconds(millis),
                )
            }
        }
    }
}

private fun HouseholdContext.toDto(): HouseholdDto = HouseholdDto(servings, weeklyBudget, defaultCookingMinutes)

private fun HouseholdDto?.toDomain(): AppResult<HouseholdContext> =
    when {
        this == null -> missing("household")
        servings == null -> missing("household.servings")
        else -> AppResult.Success(HouseholdContext(servings, weeklyBudget, defaultCookingMinutes))
    }

private fun DietaryConstraint.toDto(): DietaryConstraintDto =
    DietaryConstraintDto(term.taxonomy.value, term.term.value, strength.name)

private fun DietaryConstraintDto.toDomain(): AppResult<DietaryConstraint> {
    val taxonomy = taxonomy ?: return missing("constraints.taxonomy")
    val term = term ?: return missing("constraints.term")
    return termRef(taxonomy, term).flatMap { ref -> strength.toStrength().map { DietaryConstraint(ref, it) } }
}

/**
 * An unreadable strength fails the document. Falling back to a weaker one would turn a hard
 * exclusion into a preference, which is a safety bug and not a parsing detail.
 */
private fun String?.toStrength(): AppResult<ConstraintStrength> =
    ConstraintStrength.entries.firstOrNull { it.name == this }?.let { AppResult.Success(it) }
        ?: AppResult.Failure(AppError.Validation("constraints.strength", "is not a known strength"))

/** The three reference lists, decoded together so [toDomain] stays one chain rather than six. */
private class References(
    val constraints: List<DietaryConstraint>,
    val preferences: List<TermRef>,
    val avoided: List<IngredientId>,
)

private fun UserProfileDto.decodeReferences(): AppResult<References> {
    val terms = constraints.mapAll(DietaryConstraintDto::toDomain).getOrElse { return AppResult.Failure(it) }
    val liked = preferences.toTermRefs().getOrElse { return AppResult.Failure(it) }
    val avoided = avoidedIngredients.mapAll(IngredientId::of).getOrElse { return AppResult.Failure(it) }
    return AppResult.Success(References(terms, liked, avoided))
}

/** Order is preserved: the domain type is a `List` and it is the order the user set. */
private fun List<TermRefDto>.toTermRefs(): AppResult<List<TermRef>> =
    mapAll { reference -> termRef(reference.taxonomy.orEmpty(), reference.term.orEmpty()) }

/** The first failure wins: a half-decoded document would silently drop what the user actually set. */
internal fun <T, R> List<T>.mapAll(transform: (T) -> AppResult<R>): AppResult<List<R>> {
    val mapped = ArrayList<R>(size)
    forEach { element -> mapped += transform(element).getOrElse { return AppResult.Failure(it) } }
    return AppResult.Success(mapped)
}

private fun missing(field: String): AppResult.Failure = AppResult.Failure(AppError.Validation(field, "is missing"))
