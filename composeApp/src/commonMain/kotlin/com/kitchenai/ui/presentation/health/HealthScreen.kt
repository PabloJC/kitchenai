package com.kitchenai.ui.presentation.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.core.currentPlatform
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HealthScreen(
    modifier: Modifier = Modifier,
    viewModel: HealthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("KitchenAI", style = MaterialTheme.typography.headlineMedium)
        Text(currentPlatform().name, style = MaterialTheme.typography.bodyMedium)

        when (val actual = state) {
            HealthUiState.Loading -> CircularProgressIndicator()

            is HealthUiState.Ready ->
                Text(
                    text = actual.projectId,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

            is HealthUiState.Error ->
                Text(
                    text = actual.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
        }
    }
}
