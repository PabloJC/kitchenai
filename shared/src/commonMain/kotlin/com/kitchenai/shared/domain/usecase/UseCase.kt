package com.kitchenai.shared.domain.usecase

import com.kitchenai.shared.core.AppResult

/** Un caso de uso = una operación. Sin dependencias de framework. */
fun interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): AppResult<R>
}

object NoParams
