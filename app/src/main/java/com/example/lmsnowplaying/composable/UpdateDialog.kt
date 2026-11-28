package com.example.lmsnowplaying.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.helpers.AppUpdater
import com.example.lmsnowplaying.helpers.UpdateChecker
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// "New version available": Install downloads the APK and opens the Android
// installer, Later (or Back) closes the notice.
@Composable
fun UpdateDialog(release: UpdateChecker.Release, currentVersion: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasApk = release.apkUrl.isNotBlank()

    // 0 idle, 1 downloading, 2 failed, 3 waiting for the install permission
    var phase by remember { mutableStateOf(0) }
    var percent by remember { mutableStateOf(-1) }
    var job by remember { mutableStateOf<Job?>(null) }

    val installSource = remember { MutableInteractionSource() }
    val laterSource = remember { MutableInteractionSource() }
    val installFocused by installSource.collectIsFocusedAsState()
    val laterFocused by laterSource.collectIsFocusedAsState()
    val installRequester = remember { FocusRequester() }
    val laterRequester = remember { FocusRequester() }

    // Keep the focus on one of the two buttons: the screen underneath can grab it back.
    var installHas by remember { mutableStateOf(false) }
    var laterHas by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!installHas && !laterHas) {
                try { (if (hasApk) installRequester else laterRequester).requestFocus() } catch (_: Exception) { }
            }
            delay(150)
        }
    }

    // While this notice is open the screen underneath leaves the focus alone.
    DisposableEffect(Unit) {
        NavState.tipOpen = true
        onDispose { NavState.tipOpen = false }
    }

    val close: () -> Unit = { job?.cancel(); onDismiss() }
    BackHandler(enabled = true) { close() }

    val startInstall: () -> Unit = {
        if (phase != 1) {
            job = scope.launch {
                phase = 1
                percent = -1
                val file = AppUpdater.download(context, release) { percent = it }
                if (file == null) { phase = 2; return@launch }
                if (!AppUpdater.canInstall(context)) {
                    phase = if (AppUpdater.openInstallPermissionSettings(context)) 3 else 2
                    return@launch
                }
                phase = if (AppUpdater.install(context, file)) 0 else 2
            }
        }
    }

    val notes = UpdateChecker.notesPreview(release.notes, release.version)
    val status = when (phase) {
        1 -> if (percent >= 0) stringResource(R.string.update_downloading, percent) else stringResource(R.string.update_downloading_plain)
        2 -> stringResource(R.string.update_failed)
        3 -> stringResource(R.string.update_allow_hint)
        else -> if (hasApk) "" else stringResource(R.string.update_manual_hint)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xEE222222))
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.update_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.update_body, release.version, currentVersion, notes),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 20.dp)) {
                if (hasApk) {
                    TextButton(
                        onClick = startInstall,
                        interactionSource = installSource,
                        modifier = Modifier
                            .focusRequester(installRequester)
                            .onFocusChanged { installHas = it.isFocused }
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (installFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                    ) {
                        Text(stringResource(R.string.update_install), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(
                    onClick = close,
                    interactionSource = laterSource,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .focusRequester(laterRequester)
                        .onFocusChanged { laterHas = it.isFocused }
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (laterFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    Text(stringResource(R.string.update_later), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
