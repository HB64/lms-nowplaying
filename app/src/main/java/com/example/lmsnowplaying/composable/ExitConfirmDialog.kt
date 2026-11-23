package com.example.lmsnowplaying.composable

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.delay

// "Exit LMSNP?" while music is playing: Cancel has the focus first, Back also cancels.
@Composable
fun ExitConfirmDialog(onCancel: () -> Unit, onExit: () -> Unit) {
    val cancelSource = remember { MutableInteractionSource() }
    val exitSource = remember { MutableInteractionSource() }
    val cancelFocused by cancelSource.collectIsFocusedAsState()
    val exitFocused by exitSource.collectIsFocusedAsState()
    val cancelRequester = remember { FocusRequester() }
    // The app icon (an adaptive icon, so drawn into a bitmap)
    val context = LocalContext.current
    val icon = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.let { drawable ->
            val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 192, 192)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        }
    }

    // Keep asking for the focus until one of the two buttons has it (the screen underneath can grab it back)
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!hasFocus) {
                try { cancelRequester.requestFocus() } catch (_: Exception) { }
            }
            delay(150)
        }
    }

    // While this question is open the screen underneath leaves the focus alone
    DisposableEffect(Unit) {
        NavState.tipOpen = true
        onDispose { NavState.tipOpen = false }
    }

    BackHandler(enabled = true) { onCancel() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xEE222222))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                Text(
                    stringResource(R.string.exit_confirm_body),
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = if (icon != null) 16.dp else 0.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 20.dp)) {
                TextButton(
                    onClick = onCancel,
                    interactionSource = cancelSource,
                    modifier = Modifier
                        .focusRequester(cancelRequester)
                        .onFocusChanged { if (it.isFocused) hasFocus = true else if (!exitFocused) hasFocus = false }
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (cancelFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                ) { Text(stringResource(R.string.exit_confirm_cancel), color = Color.White, fontWeight = FontWeight.SemiBold) }
                TextButton(
                    onClick = onExit,
                    interactionSource = exitSource,
                    modifier = Modifier
                        .onFocusChanged { if (it.isFocused) hasFocus = true else if (!cancelFocused) hasFocus = false }
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (exitFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                ) { Text(stringResource(R.string.exit_confirm_yes), color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
