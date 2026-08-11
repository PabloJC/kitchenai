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
