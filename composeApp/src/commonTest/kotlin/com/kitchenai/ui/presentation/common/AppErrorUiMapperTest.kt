package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
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
    fun `not found carries the resource it was missing from`() {
        val expected = UiText.of(Res.string.error_not_found, "recipe")
        assertEquals(expected, AppError.NotFound("recipe").describe(UNAUTHORIZED))
    }

    @Test
    fun `validation reads the field and the reason by default`() {
        val error = AppError.Validation("field-1", "reason-1")

        assertEquals(UiText.of(Res.string.error_invalid_field, "field-1", "reason-1"), error.describe(UNAUTHORIZED))
    }

    @Test
    fun `a screen's own words for a validation failure replace the generic one`() {
        val error = AppError.Validation("field-1", "reason-1")

        val described = error.describe(UNAUTHORIZED) { UiText.Raw(it.reason) }

        assertEquals(UiText.Raw("reason-1"), described)
    }

    @Test
    fun `unknown reads as unknown`() {
        assertEquals(UiText.of(Res.string.error_unknown), AppError.Unknown().describe(UNAUTHORIZED))
    }

    private companion object {
        val UNAUTHORIZED = Res.string.error_unauthorized_own_data
    }
}
