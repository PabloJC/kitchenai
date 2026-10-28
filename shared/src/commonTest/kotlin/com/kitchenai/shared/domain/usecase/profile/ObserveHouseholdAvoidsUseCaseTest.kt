package com.kitchenai.shared.domain.usecase.profile

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.usecase.kitchen.FakeKitchenRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.kitchen
import com.kitchenai.shared.domain.usecase.kitchen.otherUser
import com.kitchenai.shared.domain.usecase.kitchen.user
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveHouseholdAvoidsUseCaseTest {
    private val termA = termRef("taxonomy-1", "term-a")
    private val termB = termRef("taxonomy-1", "term-b")

    @Test
    fun `pools every member's Avoid set into one`() =
        runTest {
            val held =
                kitchen(memberIds = setOf(user, otherUser)).copy(
                    memberAvoids =
                        mapOf(
                            user.value to HouseholdAvoids(setOf(termA)),
                            otherUser.value to HouseholdAvoids(setOf(termA, termB)),
                        ),
                )

            ObserveHouseholdAvoidsUseCase(FakeKitchenRepositoryContract(held))(user).test {
                assertEquals(HouseholdAvoids(setOf(termA, termB)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a household where nobody avoids anything is empty`() =
        runTest {
            ObserveHouseholdAvoidsUseCase(FakeKitchenRepositoryContract(kitchen()))(user).test {
                assertEquals(HouseholdAvoids(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the kitchen listener's failures come out on the error stream`() =
        runTest {
            val useCase = ObserveHouseholdAvoidsUseCase(FakeKitchenRepositoryContract(readError = AppError.Network()))

            useCase.errors(user).test {
                assertEquals(AppError.Network(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
