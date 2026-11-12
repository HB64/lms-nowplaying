package com.example.lmsnowplaying.composable

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.sin
import kotlin.random.Random

private data class Star(
    val xFraction: Float,
    val yFraction: Float,
    val radius: Float,
    val phase: Float,
    val speed: Float,
    val baseAlpha: Float,
)

// A lightweight, non-audio-reactive "screensaver" style backdrop: a few
// hundred gently twinkling stars over a dark gradient sky, similar in spirit
// to the Apple TV screensavers but without the cost of decoding video (or
// needing access to the actual audio, which we don't have - playback
// happens on the Lyrion player, not in this app).
@Composable
fun Starfield(modifier: Modifier = Modifier, starCount: Int = 220) {
    // A fixed seed keeps the star layout stable across recompositions
    // (e.g. when the track changes) instead of reshuffling every time.
    val stars = remember(starCount) {
        val random = Random(seed = 42)
        List(starCount) {
            Star(
                xFraction = random.nextFloat(),
                yFraction = random.nextFloat(),
                radius = random.nextFloat() * 1.6f + 0.6f,
                phase = random.nextFloat() * (2f * Math.PI.toFloat()),
                speed = random.nextFloat() * 0.6f + 0.4f,
                baseAlpha = random.nextFloat() * 0.5f + 0.35f,
            )
        }
    }

    // Fine noise tile laid over the dark gradient: the gradient has only a
    // few distinct colour steps, which show as wide bands up close. The noise
    // (white at 0-4/255 opacity) breaks those bands up.
    val noiseBrush = remember {
        val tile = 128
        val random = Random(seed = 7)
        val pixels = IntArray(tile * tile) { (random.nextInt(0, 5) shl 24) or 0x00FFFFFF }
        val bitmap = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, tile, 0, 0, tile, tile)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starfield")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starfieldTime"
    )

    Canvas(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF000010), Color(0xFF05051A), Color(0xFF000000))
            )
        )
    ) {
        val w = size.width
        val h = size.height
        drawRect(brush = noiseBrush)
        stars.forEach { star ->
            val twinkle = (sin(time * star.speed + star.phase) + 1f) / 2f
            val alpha = (star.baseAlpha * (0.5f + 0.5f * twinkle)).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.radius * 2.5f,
                center = Offset(star.xFraction * w, star.yFraction * h)
            )
        }
    }
}
