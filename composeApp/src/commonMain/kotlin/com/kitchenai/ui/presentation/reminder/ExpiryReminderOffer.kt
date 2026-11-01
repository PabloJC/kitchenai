package com.kitchenai.ui.presentation.reminder

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.platform.platformFormatTime
import com.kitchenai.ui.platform.rememberNotificationPermissionRequest
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.reminder_offer_body
import com.kitchenai.ui.resources.reminder_offer_no
import com.kitchenai.ui.resources.reminder_offer_title
import com.kitchenai.ui.resources.reminder_offer_yes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Keeps reminders in step for [userId] and asks, once, whether to have them. The question comes
 * when there is something to remind about, never at launch, and the system prompt only after a yes.
 */
@Composable
fun ExpiryReminders(
    userId: UserId,
    viewModel: ExpiryReminderViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest()
    LaunchedEffect(userId) { viewModel.start(userId) }

    if (!state.offering) return
    AlertDialog(
        // Tapping outside is not an answer: it postpones the question rather than refusing for good.
        onDismissRequest = viewModel::later,
        title = { Text(stringResource(Res.string.reminder_offer_title)) },
        text = { Text(stringResource(Res.string.reminder_offer_body, platformFormatTime(state.hour, state.minute))) },
        confirmButton = {
            Button(onClick = { requestPermission(viewModel::answered) }) {
                Text(stringResource(Res.string.reminder_offer_yes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::decline,
            ) { Text(stringResource(Res.string.reminder_offer_no)) }
        },
    )
}
