package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.theme.Dimens

/** The one spinner in the app. It carries no text: a screen that needs wording uses [EmptyState]. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(Dimens.large),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
