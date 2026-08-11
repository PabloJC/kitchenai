package com.kitchenai.shared.domain.usecase

import com.kitchenai.shared.core.AppResult

/** One use case = one operation. No framework dependencies. */
fun interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): AppResult<R>
}

object NoParams
