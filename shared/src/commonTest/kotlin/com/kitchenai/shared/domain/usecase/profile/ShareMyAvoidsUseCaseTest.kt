package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.usecase.kitchen.FakeKitchenRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.kitchen
import com.kitchenai.shared.domain.usecase.kitchen.otherUser
import com.kitchenai.shared.domain.usecase.kitchen.stranger
import com.kitchenai.shared.domain.usecase.kitchen.user
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ShareMyAvoidsUseCaseTest {
    private val avoided = termRef("taxonomy-1", "term-a")
    private val preferred = termRef("taxonomy-1", "term-b")
    private val ingredient = (IngredientId.of("ing-1") as AppResult.Success).data
    private val profile =
        UserProfile.newFor(user, listOf("en"), Instant.fromEpochSeconds(0)).copy(
            constraints =
                listOf(
                    DietaryConstraint(avoided, ConstraintStrength.AVOID),
                    DietaryConstraint(preferred, ConstraintStrength.PREFER),
                ),
            avoidedIngredients = listOf(ingredient),
        )
    private val shared = kitchen(memberIds = setOf(user, otherUser))

    @Test
    fun `writes the member's Avoid terms and ingredients and never their Prefers`() =
        runTest {
            val port = FakeKitchenRepositoryContract(shared)

            assertEquals(AppResult.Success(Unit), ShareMyAvoidsUseCase(port)(shared, profile))

            assertEquals(listOf(HouseholdAvoids(setOf(avoided), setOf(ingredient))), port.updatedAvoids)
        }

    @Test
    fun `a kitchen that already holds the entry is not written again so the echo stops`() =
        runTest {
            val port = FakeKitchenRepositoryContract(shared)
            val echoed = shared.copy(memberAvoids = mapOf(user.value to HouseholdAvoids.of(profile)))

            ShareMyAvoidsUseCase(port)(echoed, profile)

            assertTrue(port.updatedAvoids.isEmpty())
        }

    @Test
    fun `clearing every Avoid clears the shared entry too`() =
        runTest {
            val port = FakeKitchenRepositoryContract(shared)
            val held = shared.copy(memberAvoids = mapOf(user.value to HouseholdAvoids(setOf(avoided))))

            ShareMyAvoidsUseCase(port)(held, profile.copy(constraints = emptyList(), avoidedIngredients = emptyList()))

            assertEquals(listOf(HouseholdAvoids()), port.updatedAvoids)
        }

    @Test
    fun `nothing is written to a kitchen the member is not in`() =
        runTest {
            val port = FakeKitchenRepositoryContract(shared)

            ShareMyAvoidsUseCase(port)(kitchen(ownerId = stranger), profile)

            assertTrue(port.updatedAvoids.isEmpty())
        }

    @Test
    fun `past the rules' cap the entry is cut to it rather than refused whole`() =
        runTest {
            val port = FakeKitchenRepositoryContract(shared)
            val many =
                (1..ShareMyAvoidsUseCase.MAX_SHARED + 5).map {
                    DietaryConstraint(termRef("taxonomy-1", "t-$it"), ConstraintStrength.AVOID)
                }

            ShareMyAvoidsUseCase(port)(shared, profile.copy(constraints = many))

            assertEquals(ShareMyAvoidsUseCase.MAX_SHARED, port.updatedAvoids.single().terms.size)
        }

    @Test
    fun `a refused write is passed on`() =
        runTest {
            val port =
                FakeKitchenRepositoryContract(
                    shared,
                ).apply { updateMyAvoidsResult = AppResult.Failure(AppError.Network()) }

            assertEquals(AppResult.Failure(AppError.Network()), ShareMyAvoidsUseCase(port)(shared, profile))
        }
}
