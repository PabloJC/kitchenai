package com.kitchenai.shared.core

/**
 * Explicit result of a domain operation.
 * No layer propagates exceptions across its boundaries.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed class AppError(open val cause: Throwable? = null) {
    data class Network(override val cause: Throwable? = null) : AppError(cause)

    /**
     * The call was reachable and did not answer in time. Separate from [Network] because the
     * user's connection is not at fault and retrying is the fix — the one thing "no connection"
     * does not tell them.
     */
    data class Timeout(override val cause: Throwable? = null) : AppError(cause)

    data class Unauthorized(override val cause: Throwable? = null) : AppError(cause)

    data class NotFound(val resource: String) : AppError()

    data class Validation(val field: String, val reason: String) : AppError()

    data class Unknown(override val cause: Throwable? = null) : AppError(cause)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Failure -> this
    }

/** [map] for a transform that can itself fail. It belongs next to [map]: chaining is not a data concern. */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(data)
        is AppResult.Failure -> this
    }
