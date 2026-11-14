package com.example.lmsnowplaying.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Technical details of the playing track, opened with the info button next to the title.
// trackKey only makes the panel reload when the track changes.
@Composable
fun TrackInfoPanel(trackKey: String) {
    val trackId = LMS.currentTrackId
    var info by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(trackId, trackKey) {
        info = null
        // Streams and radio have no library track id to look up
        info = if ((trackId.toIntOrNull() ?: -1) >= 0) withContext(Dispatchers.IO) { LMS.getTrackTech(trackId) } else emptyMap()
    }

    val yes = stringResource(R.string.info_yes)
    val no = stringResource(R.string.info_no)
    val m = info
    val rows = mutableListOf<Pair<String, String>>()
    if (m != null) {
        fun value(key: String) = m[key]?.trim().orEmpty()
        value("type").takeIf { it.isNotEmpty() }?.let { rows.add(stringResource(R.string.info_format) to formatName(it)) }
        value("samplesize").takeIf { it.isNotEmpty() && it != "0" }?.let { rows.add(stringResource(R.string.info_bit_depth) to "$it-bit") }
        value("samplerate").toDoubleOrNull()?.takeIf { it > 0 }?.let { rows.add(stringResource(R.string.info_sample_rate) to sampleRateText(it)) }
        value("bitrate").takeIf { it.isNotEmpty() }?.let { rows.add(stringResource(R.string.info_bitrate) to it) }
        value("lossless").takeIf { it.isNotEmpty() }?.let { rows.add(stringResource(R.string.info_lossless) to if (it == "1") yes else no) }
        value("replay_gain").takeIf { it.isNotEmpty() }?.let { rows.add(stringResource(R.string.info_gain_track) to dbText(it)) }
        value("album_replay_gain").takeIf { it.isNotEmpty() }?.let { rows.add(stringResource(R.string.info_gain_album) to dbText(it)) }
        value("filesize").toLongOrNull()?.takeIf { it > 0 }?.let { rows.add(stringResource(R.string.info_file_size) to "%.1f MB".format(it / 1048576.0)) }
        value("tagversion").takeIf { it.isNotEmpty() }?.let { rows.add(stringResource(R.string.info_tag_version) to it) }
    }

    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2181818))
            .padding(12.dp)
    ) {
        Text(stringResource(R.string.info_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp))
        when {
            m == null -> Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            rows.isEmpty() -> Text(stringResource(R.string.info_none), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            else -> rows.forEach { (label, v) ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    // Fixed label width so all values line up in one column
                    Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp, modifier = Modifier.width(160.dp))
                    Text(v, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

// One line under the album line: format, bit depth, sample rate, bitrate.
// The height is reserved, so the rows below do not jump while it loads or when a track has no info.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackInfoLine(trackKey: String) {
    val trackId = LMS.currentTrackId
    var info by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(trackId, trackKey) {
        info = null
        info = if ((trackId.toIntOrNull() ?: -1) >= 0) withContext(Dispatchers.IO) { LMS.getTrackTech(trackId) } else emptyMap()
    }
    val m = info
    val parts = if (m == null) emptyList() else {
        fun value(key: String) = m[key]?.trim().orEmpty()
        listOfNotNull(
            value("type").takeIf { it.isNotEmpty() }?.let { formatName(it) },
            value("samplesize").takeIf { it.isNotEmpty() && it != "0" }?.let { "$it-bit" },
            value("samplerate").toDoubleOrNull()?.takeIf { it > 0 }?.let { sampleRateText(it) },
            value("bitrate").takeIf { it.isNotEmpty() },
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Box(modifier = Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    }
}

// Lyrion gives the content type as a short code (flc, mp3, ...).
private fun formatName(type: String): String = when (type.lowercase()) {
    "flc" -> "FLAC"
    "mp3" -> "MP3"
    "aac" -> "AAC"
    "mp4" -> "AAC (MP4)"
    "ogg", "ogf" -> "Ogg Vorbis"
    "alc" -> "Apple Lossless"
    "aif", "aiff" -> "AIFF"
    "wav", "pcm" -> "WAV"
    else -> type.uppercase()
}

// Documented as kHz, but accept Hz as well.
private fun sampleRateText(v: Double): String {
    val khz = if (v > 1000) v / 1000.0 else v
    return (if (khz % 1.0 == 0.0) khz.toInt().toString() else "%.1f".format(khz)) + " kHz"
}

private fun dbText(v: String): String = if (v.contains("dB", ignoreCase = true)) v else "$v dB"
