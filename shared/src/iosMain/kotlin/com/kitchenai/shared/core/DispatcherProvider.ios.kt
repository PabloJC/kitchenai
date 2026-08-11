package com.kitchenai.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// The actual of the port every other file must inject: naming a dispatcher here is the point.
@Suppress("InjectDispatcher")
internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.Default
