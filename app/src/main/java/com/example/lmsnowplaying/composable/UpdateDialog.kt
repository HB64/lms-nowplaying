package com.example.lmsnowplaying.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.helpers.UpdateChecker

// "New version available" notice, shown once after a fresh start.
@Composable
fun UpdateDialog(release: UpdateChecker.Release, currentVersion: String, onDismiss: () -> Unit) {
    PluginTipDialog(
        onDismiss = onDismiss,
        titleText = stringResource(R.string.update_title),
        bodyText = stringResource(
            R.string.update_body, release.version, currentVersion, UpdateChecker.notesPreview(release.notes)
        ),
        okRes = R.string.update_ok,
    )
}
