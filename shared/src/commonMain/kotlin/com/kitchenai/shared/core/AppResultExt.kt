package com.kitchenai.shared.core

/**
 * Devuelve el valor si la operación fue bien, o el resultado de [fallback] si no.
 *
 * Existe para que quien consume un [AppResult] no tenga que escribir un `when`
 * cuando lo único que necesita es un valor por defecto.
 */
inline fun <T> AppResult<T>.getOrElse(fallback: (AppError) -> T): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> fallback(error)
    }
