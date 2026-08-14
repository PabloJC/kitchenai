package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Every dispatcher pointed at the one the test controls. One copy: this had been written four
 * times, once per screen, which is how a repository ends up with four answers to one question.
 *
 * [defaultDispatcher] is separate because the session test needs `default` to run on a
 * different scheduler than `main` to reproduce a race; every other test leaves them the same.
 */
class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher = dispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = defaultDispatcher
}
