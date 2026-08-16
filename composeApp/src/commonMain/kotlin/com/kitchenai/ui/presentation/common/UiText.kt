package com.kitchenai.ui.presentation.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * A sentence a ViewModel chose and a screen renders, named rather than written.
 *
 * A `String` in state is already translated, and a ViewModel has no language to translate into:
 * it knows which sentence applies, not what that sentence says. This carries the choice and
 * leaves the words to the screen, which is where the reader's language is known.
 *
 * [args] exist because a third of these messages carry a value — a field, a resource, a count —
 * and a message that cannot interpolate says less exactly where it is trying to help.
 */
sealed interface UiText {
    data class Resource(
        val id: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiText

    /**
     * Words that arrive already written, from the domain rather than from a table — a validation
     * reason naming what a value must be. There is nothing to look up: the sentence exists and
     * this module did not author it.
     */
    data class Raw(val value: String) : UiText

    companion object {
        fun of(
            id: StringResource,
            vararg args: Any,
        ): Resource = Resource(id, args.toList())
    }
}

/**
 * Resolved where the language is: inside composition, against the reader's locale.
 *
 * The spread is the resource API's shape rather than a choice: it takes varargs, [UiText] holds
 * a list, and copying two arguments is not the cost detekt is warning about.
 */
@Suppress("SpreadOperator")
@Composable
fun UiText.resolve(): String =
    when (this) {
        is UiText.Resource ->
            if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
        is UiText.Raw -> value
    }

/**
 * The same sentence for a caller that is not composing — a snackbar is shown from a coroutine,
 * where `stringResource` cannot be called at all.
 */
@Suppress("SpreadOperator")
suspend fun UiText.text(): String =
    when (this) {
        is UiText.Resource ->
            if (args.isEmpty()) getString(id) else getString(id, *args.toTypedArray())
        is UiText.Raw -> value
    }
