package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.DietaryConstraintDto
import com.kitchenai.shared.data.remote.dto.HouseholdDto
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

// Identifiers here are opaque on purpose: naming a diet or an allergen in a fixture would be the
// hardcoded vocabulary the taxonomies exist to avoid.
class UserProfileMapperTest {
    private val documentId = "user-1"
    private val profile =
        UserProfile(
            userId = (UserId.of(documentId) as AppResult.Success).data,
            displayName = "Name",
            languageTags = listOf("xx", "yy-ZZ"),
            household = HouseholdContext(servings = 2, weeklyBudget = 40.0, defaultCookingMinutes = 25),
            constraints = listOf(DietaryConstraint(ref("t-1", "a"), ConstraintStrength.EXCLUDE)),
            preferences = listOf(ref("t-2", "b"), ref("t-2", "c")),
            avoidedIngredients = listOf((IngredientId.of("i-1") as AppResult.Success).data),
            updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
        )

    @Test
    fun `a profile survives the round trip through the document shape`() {
        assertEquals(AppResult.Success(profile), profile.toDto().toDomain(documentId))
    }

    @Test
    fun `a profile with nothing set survives the round trip too`() {
        val empty = UserProfile.newFor(profile.userId, emptyList(), Instant.fromEpochMilliseconds(0))

        assertEquals(AppResult.Success(empty), empty.toDto().toDomain(documentId))
    }

    @Test
    fun `an absent timestamp fails naming the field`() {
        val dto = profile.toDto().copy(updatedAtMillis = null)

        assertEquals(validation("updatedAt"), dto.toDomain(documentId))
    }

    @Test
    fun `an absent household fails naming the field`() {
        val dto = profile.toDto().copy(household = null)

        assertEquals(validation("household"), dto.toDomain(documentId))
    }

    @Test
    fun `a household without servings fails naming the field`() {
        val dto = profile.toDto().copy(household = HouseholdDto())

        assertEquals(validation("household.servings"), dto.toDomain(documentId))
    }

    @Test
    fun `an unknown strength fails instead of falling back to a weaker one`() {
        val dto = profile.toDto().copy(constraints = listOf(DietaryConstraintDto("t-1", "a", "SOMETHING_ELSE")))

        val result = dto.toDomain(documentId)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.Validation("constraints.strength", "is not a known strength"), result.error)
    }

    @Test
    fun `a constraint that names no strength at all fails as well`() {
        val dto = profile.toDto().copy(constraints = listOf(DietaryConstraintDto("t-1", "a")))

        assertTrue(dto.toDomain(documentId) is AppResult.Failure)
    }

    @Test
    fun `a constraint missing its term fails naming the field`() {
        val dto = profile.toDto().copy(constraints = listOf(DietaryConstraintDto(taxonomy = "t-1")))

        assertEquals(validation("constraints.term"), dto.toDomain(documentId))
    }

    @Test
    fun `a blank identifier fails rather than decoding into an unusable reference`() {
        val dto = profile.toDto().copy(preferences = mapOf("" to listOf("b")))

        assertTrue(dto.toDomain(documentId) is AppResult.Failure)
    }

    @Test
    fun `a blank document id fails rather than producing a profile nobody owns`() {
        assertTrue(profile.toDto().toDomain(" ") is AppResult.Failure)
    }

    private fun validation(field: String): AppResult<UserProfile> =
        AppResult.Failure(AppError.Validation(field, "is missing"))

    private companion object {
        fun ref(
            taxonomy: String,
            term: String,
        ): TermRef =
            TermRef(
                (TaxonomyId.of(taxonomy) as AppResult.Success).data,
                (TermId.of(term) as AppResult.Success).data,
            )
    }
}
