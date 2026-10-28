package com.kitchenai.ui.presentation.quickadd

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.GetShoppingSnapshotUseCase
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakeShoppingItemPort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.common.noUnits
import com.kitchenai.ui.presentation.session.FakeSessionPort
import com.kitchenai.ui.presentation.session.userId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class QuickAddToListTest {
    private val session =
        FakeSessionPort().apply { sessionChanges.value = Session.SignedIn(userId, isAnonymous = true) }
    private val kitchens = FakeKitchenPort()
    private val items = FakeShoppingItemPort()
    private val fixedTime = TimeProvider { Instant.fromEpochSeconds(5) }

    private fun quickAdd(lists: FakeShoppingListPort = FakeShoppingListPort()) =
        QuickAddToList(
            GetShoppingSnapshotUseCase(session, kitchens, lists, items),
            AddShoppingItemUseCase(items, IdGenerator { "added" }, fixedTime, noUnits()),
        )

    @Test
    fun `what was said goes in trimmed as a free-text line`() =
        runTest {
            assertEquals(QuickAddOutcome.ADDED, quickAdd().add("  word-a "))

            assertEquals("word-a", items.upserts.single().single().freeText)
        }

    @Test
    fun `nothing said writes nothing`() =
        runTest {
            assertEquals(QuickAddOutcome.NOTHING_TO_ADD, quickAdd().add("   "))
            assertTrue(items.upserts.isEmpty())
        }

    @Test
    fun `signed out and no list yet both ask for the app and write nothing`() =
        runTest {
            assertEquals(QuickAddOutcome.NO_LIST, quickAdd(FakeShoppingListPort(existing = false)).add("word-a"))

            session.sessionChanges.value = Session.SignedOut
            assertEquals(QuickAddOutcome.NO_LIST, quickAdd().add("word-a"))
            assertTrue(items.upserts.isEmpty())
        }

    @Test
    fun `a failed read or write is a failure`() =
        runTest {
            kitchens.readError = AppError.Network()
            assertEquals(QuickAddOutcome.FAILED, quickAdd().add("word-a"))

            kitchens.readError = null
            items.upsertResult = AppResult.Failure(AppError.Network())
            assertEquals(QuickAddOutcome.FAILED, quickAdd().add("word-a"))
        }
}
