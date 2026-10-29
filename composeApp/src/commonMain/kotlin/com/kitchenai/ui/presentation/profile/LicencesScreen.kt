package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.navigation.DetailTopBarState
import com.kitchenai.ui.presentation.common.PublishDetailTitle
import com.kitchenai.ui.presentation.common.readBundledText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.licences_close
import com.kitchenai.ui.resources.licences_failed
import com.kitchenai.ui.resources.profile_licences
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import org.jetbrains.compose.resources.stringResource

/**
 * Every library the app ships with and its licence. The list is generated from the build's own
 * dependencies (`exportLibraryDefinitions` in composeApp/build.gradle.kts), never written by hand.
 */
@Composable
fun LicencesScreen(
    detailTopBar: DetailTopBarState,
    modifier: Modifier = Modifier,
) {
    PublishDetailTitle(stringResource(Res.string.profile_licences), detailTopBar)
    // Read here rather than inside produceLibraries, whose null means "still loading" and would spin forever.
    val json by produceState<String?>(null) { value = readBundledText(LICENCES_FILE).orEmpty() }
    val loaded = json
    when {
        loaded == null -> LoadingState(modifier)
        loaded.isEmpty() -> ErrorState(message = stringResource(Res.string.licences_failed), modifier = modifier)
        else -> LicenceList(loaded, modifier)
    }
}

@Composable
private fun LicenceList(
    json: String,
    modifier: Modifier = Modifier,
) {
    val libraries by produceLibraries(json)
    LibrariesContainer(
        libraries = libraries,
        modifier = modifier.fillMaxSize(),
        licenseDialogConfirmText = stringResource(Res.string.licences_close),
    )
}

private const val LICENCES_FILE = "files/aboutlibraries.json"
