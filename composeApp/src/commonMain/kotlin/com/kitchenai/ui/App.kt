package com.kitchenai.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.KitchenAiTheme
import com.kitchenai.ui.presentation.health.HealthScreen

@Composable
fun App() {
    KitchenAiTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            HealthScreen()
        }
    }
}
