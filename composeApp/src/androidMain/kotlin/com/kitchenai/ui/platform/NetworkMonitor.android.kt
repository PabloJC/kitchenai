package com.kitchenai.ui.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.scope.Scope

actual fun Scope.platformNetworkMonitor(): NetworkMonitor = ConnectivityManagerNetworkMonitor(get<Context>())

private class ConnectivityManagerNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: Flow<Boolean> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    // VALIDATED tells a captive portal from a link; capabilities follow every onAvailable.
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        trySend(capabilities.isUsable())
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }
            trySend(manager?.currentlyUsable() ?: true)
            // A refused registration (missing permission, too many callbacks) must not take the app down.
            val registered = runCatching { manager?.registerDefaultNetworkCallback(callback) }.isSuccess
            awaitClose { if (registered) runCatching { manager?.unregisterNetworkCallback(callback) } }
        }.distinctUntilChanged()

    // Unknown reads as online: a banner shown by mistake is worse than one missed.
    private fun ConnectivityManager.currentlyUsable(): Boolean =
        runCatching {
            val network = activeNetwork ?: return@runCatching false
            getNetworkCapabilities(network)?.isUsable() == true
        }.getOrDefault(true)

    private fun NetworkCapabilities.isUsable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
