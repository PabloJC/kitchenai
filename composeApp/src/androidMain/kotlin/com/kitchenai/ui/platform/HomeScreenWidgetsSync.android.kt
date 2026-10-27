package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kitchenai.ui.widget.ShoppingListWidget
import kotlinx.coroutines.launch

@Composable
actual fun HomeScreenWidgetsSync() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // On stop, not on every write: nobody looks at the home screen while the app is in front.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        scope.launch { ShoppingListWidget().updateAll(context) }
    }
}
