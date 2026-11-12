package com.example.lmsnowplaying.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.DeviceVolume
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Volume slider under the player buttons. Invisible (but reachable with
// D-pad down) until the volume is changed or it has focus; left/right change
// the volume by the chosen step, OK mutes.
@Composable
fun VolumeSlider() {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    val visible = focused || now - NavState.volumeShownAt < 4000
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "volumeAlpha")
    val muted = NavState.volumeMuted
    val volume = NavState.volume

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = AppConfig.current.volumeStep
                when (event.key) {
                    Key.DirectionLeft -> {
                        DeviceVolume.adjust(-step)
                        true
                    }
                    Key.DirectionRight -> {
                        DeviceVolume.adjust(step)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        DeviceVolume.mute()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = source)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (muted) Icons.Filled.VolumeOff else VolumeIcon,
            contentDescription = "Volume",
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        val accent = NavState.accentColor
        Canvas(modifier = Modifier.width(260.dp).height(16.dp)) {
            val barHeight = 4.dp.toPx()
            val dotRadius = 3.5.dp.toPx()
            val centerY = size.height / 2f
            val radius = CornerRadius(barHeight / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(0f, centerY - barHeight / 2f),
                size = Size(size.width, barHeight),
                cornerRadius = radius,
            )
            val filled = size.width * (volume / 100f)
            if (filled > 0f) {
                drawRoundRect(
                    color = if (muted) accent.copy(alpha = 0.4f) else accent,
                    topLeft = Offset(0f, centerY - barHeight / 2f),
                    size = Size(filled, barHeight),
                    cornerRadius = radius,
                )
            }
            val dotX = filled.coerceIn(dotRadius, (size.width - dotRadius).coerceAtLeast(dotRadius))
            drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = dotRadius + 1.dp.toPx(), center = Offset(dotX, centerY))
            drawCircle(color = Color.White, radius = dotRadius, center = Offset(dotX, centerY))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (muted) "-" else "$volume%",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.width(44.dp).padding(end = 0.dp)
        )
    }
}
