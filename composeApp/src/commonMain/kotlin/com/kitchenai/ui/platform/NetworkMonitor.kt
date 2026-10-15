package com.kitchenai.ui.platform

import kotlinx.coroutines.flow.Flow
import org.koin.core.scope.Scope

/**
 * Whether the device can reach the internet right now. Firestore keeps working offline, so this
 * is only there to tell the user; nothing in the data layer depends on it.
 */
interface NetworkMonitor {
    /** Emits the current state on collection, then every change. Never throws; unknown reads as online. */
    val isOnline: Flow<Boolean>
}

/**
 * The platform's monitor, built from Koin because Android needs the application `Context` and
 * iOS needs nothing: a plain `expect class` constructor could not express both.
 */
expect fun Scope.platformNetworkMonitor(): NetworkMonitor
