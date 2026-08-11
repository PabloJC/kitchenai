package com.kitchenai.shared.core

/**
 * Returns the value on success, or the result of [fallback] otherwise.
 *
 * Exists so that consuming an [AppResult] does not require a `when` when all you need is a
 * default value.
 */
inline fun <T> AppResult<T>.getOrElse(fallback: (AppError) -> T): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> fallback(error)
    }
