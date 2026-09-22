package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
import com.kitchenai.ui.resources.error_unauthorized_action
import com.kitchenai.ui.resources.kitchen_invalid_code
import com.kitchenai.ui.resources.kitchen_leave_disabled_owner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KitchenUiMapperTest {
    private val owner = userId("owner")
    private val member = userId("member")

    @Test
    fun `a solo owner can leave and carries no reason not to`() {
        val ui = kitchen(ownerId = owner, memberIds = setOf(owner)).toUi(owner)

        assertTrue(ui.isOwner)
        assertTrue(ui.canLeave)
        assertNull(ui.leaveDisabledReason)
    }

    @Test
    fun `an owner with other members cannot leave and is told why`() {
        val ui = kitchen(ownerId = owner, memberIds = setOf(owner, member)).toUi(owner)

        assertTrue(ui.isOwner)
        assertEquals(false, ui.canLeave)
        assertEquals(UiText.of(Res.string.kitchen_leave_disabled_owner), ui.leaveDisabledReason)
    }

    @Test
    fun `a non-owner member can always leave`() {
        val ui = kitchen(ownerId = owner, memberIds = setOf(owner, member)).toUi(member)

        assertEquals(false, ui.isOwner)
        assertTrue(ui.canLeave)
        assertNull(ui.leaveDisabledReason)
    }

    @Test
    fun `the join code is always carried for the copy affordance regardless of who is viewing`() {
        val loaded = kitchen(ownerId = owner, memberIds = setOf(owner, member), joinCode = "abc-123")

        assertEquals("abc-123", loaded.toUi(owner).joinCode)
        assertEquals("abc-123", loaded.toUi(member).joinCode)
    }

    @Test
    fun `only the owner can remove another member and never themself`() {
        val loaded = kitchen(ownerId = owner, memberIds = setOf(owner, member))

        val asOwner = loaded.toUi(owner).members.associateBy { it.id }
        assertEquals(false, asOwner.getValue(owner).canRemove)
        assertTrue(asOwner.getValue(member).canRemove)

        val asMember = loaded.toUi(member).members.associateBy { it.id }
        assertEquals(false, asMember.getValue(owner).canRemove)
        assertEquals(false, asMember.getValue(member).canRemove)
    }

    @Test
    fun `a member without a display name falls back to their identifier`() {
        val loaded = kitchen(ownerId = owner, memberIds = setOf(owner))

        assertEquals(owner.value, loaded.toUi(owner).members.single().name)
    }

    @Test
    fun `a member's own display name is used when the kitchen carries one`() {
        val names = mapOf(member.value to "Ada")
        val loaded = kitchen(ownerId = owner, memberIds = setOf(owner, member), memberDisplayNames = names)

        assertEquals("Ada", loaded.toUi(owner).members.first { it.id == member }.name)
    }

    @Test
    fun `a generic error is described with the kitchen screen's own unauthorized wording`() {
        assertEquals(UiText.of(Res.string.error_unauthorized_action), AppError.Unauthorized().describeKitchenError())
    }

    @Test
    fun `a join failure is described as an invalid code rather than the resource that went missing`() {
        val message = AppError.NotFound("kitchenInvite").describeJoinError()
        assertEquals(UiText.of(Res.string.kitchen_invalid_code), message)
    }

    @Test
    fun `a join failure that is not a missing resource keeps the generic wording`() {
        assertEquals(UiText.of(Res.string.error_unauthorized_action), AppError.Unauthorized().describeJoinError())
    }

    @Test
    fun `joining while owning a kitchen with other members reuses the proactive leave-disabled wording`() {
        // JoinKitchenUseCase leaves the caller's current kitchen first, so LeaveKitchenUseCase's own
        // owner guard is what actually fails here — reachable, not just theoretical (review finding).
        val error = AppError.Validation("kitchen", "owner cannot leave a kitchen with other members")

        assertEquals(UiText.of(Res.string.kitchen_leave_disabled_owner), error.describeJoinError())
        assertEquals(UiText.of(Res.string.kitchen_leave_disabled_owner), error.describeKitchenError())
    }

    @Test
    fun `a validation error for an unrelated field keeps the generic invalid-field wording`() {
        val error = AppError.Validation("joinCode", "must not be blank")
        val expected = UiText.of(Res.string.error_invalid_field, "joinCode", "must not be blank")

        assertEquals(expected, error.describeKitchenError())
    }

    private fun userId(raw: String): UserId = (UserId.of(raw) as AppResult.Success).data

    private fun kitchen(
        ownerId: UserId,
        memberIds: Set<UserId>,
        joinCode: String = "code-1",
        memberDisplayNames: Map<String, String> = emptyMap(),
    ): Kitchen =
        Kitchen(
            id = (KitchenId.of("kitchen-1") as AppResult.Success).data,
            ownerId = ownerId,
            memberIds = memberIds,
            joinCode = (KitchenJoinCode.of(joinCode) as AppResult.Success).data,
            memberDisplayNames = memberDisplayNames,
        )
}
