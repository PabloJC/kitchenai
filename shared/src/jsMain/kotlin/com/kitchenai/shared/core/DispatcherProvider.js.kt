package com.kitchenai.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// The browser has one thread and no blocking IO: Default is the only dispatcher that exists.
@Suppress("InjectDispatcher")
internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.Default
