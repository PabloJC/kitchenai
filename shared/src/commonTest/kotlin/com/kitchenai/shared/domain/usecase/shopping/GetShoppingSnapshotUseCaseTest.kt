package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.ShoppingSnapshot
import com.kitchenai.shared.domain.usecase.kitchen.FakeKitchenRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.kitchen
import com.kitchenai.shared.domain.usecase.kitchen.user
import com.kitchenai.shared.domain.usecase.session.FakeSessionPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GetShoppingSnapshotUseCaseTest {
    private val signedIn = FakeSessionPort(Session.SignedIn(user, isAnonymous = true))
    private val kitchens = FakeKitchenRepositoryContract(kitchen(id = "kitchen"))
    private val lists = FakeShoppingListRepositoryContract()
    private val items = FakeShoppingItemRepositoryContract()

    private fun useCase(
        session: FakeSessionPort = signedIn,
        kitchenPort: FakeKitchenRepositoryContract = kitchens,
        listPort: FakeShoppingListRepositoryContract = lists,
        itemPort: FakeShoppingItemRepositoryContract = items,
    ) = GetShoppingSnapshotUseCase(session, kitchenPort, listPort, itemPort)

    private suspend fun seedList(
        id: String,
        seconds: Long,
    ) {
        lists.upsertList(kitchenId(), ShoppingList(listId(id), kitchenId(), emptyMap(), instant(seconds)))
    }

    @Test
    fun `returns the oldest list's unchecked lines in the order the list screen shows them`() =
        runTest {
            seedList("newer", seconds = 20)
            seedList("list", seconds = 10)
            items.seed(
                listId(),
                shoppingItem("bread", seconds = 30),
                shoppingItem("milk", seconds = 10),
                shoppingItem("eggs", seconds = 5).copy(checked = true),
            )

            val snapshot = assertIs<ShoppingSnapshot>(assertIs<AppResult.Success<*>>(useCase()()).data)

            assertEquals(listOf("milk", "bread"), snapshot.pending.map { it.id.value })
            assertEquals(listId(), snapshot.listId)
        }

    @Test
    fun `signed out fails with Unauthorized`() =
        runTest {
            val result = useCase(session = FakeSessionPort(Session.SignedOut))()

            assertIs<AppError.Unauthorized>(assertIs<AppResult.Failure>(result).error)
        }

    @Test
    fun `no kitchen yet is nothing to show and not an error`() =
        runTest {
            val result = useCase(kitchenPort = FakeKitchenRepositoryContract())()

            assertNull(assertIs<AppResult.Success<*>>(result).data)
        }

    @Test
    fun `no list yet is nothing to show and creates none`() =
        runTest {
            val result = useCase()()

            assertNull(assertIs<AppResult.Success<*>>(result).data)
            assertEquals(0, lists.upsertListCalls)
        }

    @Test
    fun `a failed kitchen read is passed on`() =
        runTest {
            val error = AppError.Network()

            val result = useCase(kitchenPort = FakeKitchenRepositoryContract(readError = error))()

            assertEquals(AppResult.Failure(error), result)
        }

    @Test
    fun `a failed list read is passed on`() =
        runTest {
            val error = AppError.Network()

            val result = useCase(listPort = FakeShoppingListRepositoryContract(failure = error))()

            assertEquals(AppResult.Failure(error), result)
        }

    @Test
    fun `a failed item read is passed on`() =
        runTest {
            val error = AppError.Network()
            seedList("list", seconds = 10)

            val result = useCase(itemPort = FakeShoppingItemRepositoryContract(failure = error))()

            assertEquals(AppResult.Failure(error), result)
        }
}
