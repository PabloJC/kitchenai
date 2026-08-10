package com.kitchenai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitchenai.shared.core.currentPlatform
import com.kitchenai.ui.designsystem.theme.KitchenAiTheme

@Composable
fun App() {
    KitchenAiTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("KitchenAI", style = MaterialTheme.typography.headlineMedium)
                Text(currentPlatform().name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
