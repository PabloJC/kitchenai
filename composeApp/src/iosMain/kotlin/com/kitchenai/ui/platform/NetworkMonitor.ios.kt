package com.kitchenai.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.scope.Scope
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

actual fun Scope.platformNetworkMonitor(): NetworkMonitor = PathNetworkMonitor()

/** The C API behind `NWPathMonitor`, which Kotlin cannot reach; it reports the current path as soon as it starts. */
@OptIn(ExperimentalForeignApi::class)
private class PathNetworkMonitor : NetworkMonitor {
    override val isOnline: Flow<Boolean> =
        callbackFlow {
            val monitor = nw_path_monitor_create()
            nw_path_monitor_set_update_handler(monitor) { path ->
                trySend(path != null && nw_path_get_status(path) == nw_path_status_satisfied)
            }
            nw_path_monitor_set_queue(monitor, dispatch_queue_create("kitchenai.network.path", null))
            nw_path_monitor_start(monitor)
            awaitClose { nw_path_monitor_cancel(monitor) }
        }.distinctUntilChanged()
}
