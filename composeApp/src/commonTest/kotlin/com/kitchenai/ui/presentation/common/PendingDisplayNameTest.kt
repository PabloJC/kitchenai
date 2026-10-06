package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingDisplayNameTest {
    private val pending = PendingDisplayName()

    @Test
    fun `the first uid bound wins and a later one is ignored`() {
        pending.expect("Ada")
        pending.bind(first)
        pending.bind(second)

        assertEquals(PendingDisplayName.Entry("Ada", first), pending.current.value)
    }

    @Test
    fun `binding with nothing offered leaves nothing`() {
        pending.bind(first)

        assertNull(pending.current.value)
    }

    @Test
    fun `a failed sign-in drops an unbound name but not a bound one`() {
        pending.expect("Ada")
        pending.dropUnbound()
        assertNull(pending.current.value)

        pending.expect("Ada")
        pending.bind(first)
        pending.dropUnbound()
        assertEquals(PendingDisplayName.Entry("Ada", first), pending.current.value)
    }

    @Test
    fun `another uid becoming active drops a bound name and keeps an unbound or matching one`() {
        pending.expect("Ada")
        pending.dropUnless(second)
        assertEquals(PendingDisplayName.Entry("Ada"), pending.current.value)

        pending.bind(first)
        pending.dropUnless(first)
        assertEquals(PendingDisplayName.Entry("Ada", first), pending.current.value)

        pending.dropUnless(second)
        assertNull(pending.current.value)
    }

    @Test
    fun `consuming an applied entry leaves a newer offer in place`() {
        pending.expect("Ada")
        pending.bind(first)
        val applied = pending.current.value!!

        pending.expect("Grace")
        pending.consume(applied)

        assertEquals(PendingDisplayName.Entry("Grace"), pending.current.value)
    }
}

private val first = (UserId.of("user-1") as AppResult.Success).data
private val second = (UserId.of("user-2") as AppResult.Success).data
