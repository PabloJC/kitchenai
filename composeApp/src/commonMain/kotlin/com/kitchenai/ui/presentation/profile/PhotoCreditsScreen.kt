package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.navigation.DetailTopBarState
import com.kitchenai.ui.platform.rememberOpenUrl
import com.kitchenai.ui.presentation.common.PublishDetailTitle
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.credits_by
import com.kitchenai.ui.resources.credits_failed
import com.kitchenai.ui.resources.credits_intro
import com.kitchenai.ui.resources.profile_photo_credits
import org.jetbrains.compose.resources.stringResource

/**
 * The attribution CC BY and CC BY-SA ask of anyone showing the photographs: title, author,
 * licence and source, each source and licence one tap away.
 */
@Composable
fun PhotoCreditsScreen(
    detailTopBar: DetailTopBarState,
    modifier: Modifier = Modifier,
) {
    PublishDetailTitle(stringResource(Res.string.profile_photo_credits), detailTopBar)
    // Loaded once from the bundle; an empty list stands for a file that failed to parse.
    val credits by produceState<List<PhotoCredit>?>(null) {
        value = parsePhotoCredits(Res.readBytes(PHOTO_CREDITS_FILE).decodeToString()).orEmpty()
    }
    val loaded = credits
    when {
        loaded == null -> LoadingState(modifier)
        loaded.isEmpty() -> ErrorState(message = stringResource(Res.string.credits_failed), modifier = modifier)
        else -> PhotoCreditList(loaded, modifier)
    }
}

@Composable
private fun PhotoCreditList(
    credits: List<PhotoCredit>,
    modifier: Modifier = Modifier,
) {
    val openUrl = rememberOpenUrl()
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(Res.string.credits_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimens.large),
            )
        }
        items(credits, key = { credit -> credit.image }) { credit ->
            ListItem(
                headlineContent = { Text(credit.title) },
                supportingContent = { Text(stringResource(Res.string.credits_by, credit.author)) },
                trailingContent = { LicenceLabel(credit, openUrl) },
                modifier = Modifier.clickable(role = Role.Button) { openUrl(credit.source) },
            )
        }
    }
}

/** The licence's name, a link to its deed when it has one (public domain has none). */
@Composable
private fun LicenceLabel(
    credit: PhotoCredit,
    openUrl: (String) -> Unit,
) {
    val deed = credit.licenceUrl
    if (deed == null) {
        Text(credit.licence, style = MaterialTheme.typography.labelLarge)
    } else {
        TextButton(onClick = { openUrl(deed) }) { Text(credit.licence) }
    }
}

private const val PHOTO_CREDITS_FILE = "files/photo_credits.json"
