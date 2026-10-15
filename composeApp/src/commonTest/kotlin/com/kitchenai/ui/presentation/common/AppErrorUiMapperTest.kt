package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_conflict
import com.kitchenai.ui.resources.error_invalid_input
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_not_found
import com.kitchenai.ui.resources.error_timeout
import com.kitchenai.ui.resources.error_unauthorized_own_data
import com.kitchenai.ui.resources.error_unknown
import kotlin.test.Test
import kotlin.test.assertEquals

class AppErrorUiMapperTest {
    @Test
    fun `network reads as no connection`() {
        assertEquals(UiText.of(Res.string.error_no_connection), AppError.Network().describe(UNAUTHORIZED))
    }

    @Test
    fun `timeout reads as its own sentence rather than the connection one`() {
        assertEquals(UiText.of(Res.string.error_timeout), AppError.Timeout().describe(UNAUTHORIZED))
    }

    @Test
    fun `unauthorized reads as whatever the caller says this screen means by it`() {
        assertEquals(UiText.of(UNAUTHORIZED), AppError.Unauthorized().describe(UNAUTHORIZED))
    }

    @Test
    fun `not found never names the internal resource it was missing from`() {
        val described = AppError.NotFound("recipe").describe(UNAUTHORIZED)

        assertEquals(UiText.of(Res.string.error_not_found), described)
    }

    @Test
    fun `validation reads as a generic sentence without the field or the reason`() {
        val error = AppError.Validation("field-1", "reason-1")

        assertEquals(UiText.of(Res.string.error_invalid_input), error.describe(UNAUTHORIZED))
    }

    @Test
    fun `a screen's own words for a validation failure replace the generic one`() {
        val error = AppError.Validation("field-1", "reason-1")

        val described = error.describe(UNAUTHORIZED) { UiText.Raw(it.reason) }

        assertEquals(UiText.Raw("reason-1"), described)
    }

    @Test
    fun `a screen that has nothing to say about a validation failure falls back to the generic sentence`() {
        val error = AppError.Validation("field-1", "reason-1")

        assertEquals(UiText.of(Res.string.error_invalid_input), error.describe(UNAUTHORIZED) { null })
    }

    @Test
    fun `unknown reads as unknown`() {
        assertEquals(UiText.of(Res.string.error_unknown), AppError.Unknown().describe(UNAUTHORIZED))
    }

    @Test
    fun `conflict reads as already in use`() {
        assertEquals(UiText.of(Res.string.error_conflict), AppError.Conflict().describe(UNAUTHORIZED))
    }

    private companion object {
        val UNAUTHORIZED = Res.string.error_unauthorized_own_data
    }
}
