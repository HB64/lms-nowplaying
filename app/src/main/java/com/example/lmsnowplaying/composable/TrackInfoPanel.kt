package com.example.lmsnowplaying.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.JoinLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

// One line under the album line with the technical info of the stream (see LMS.buildTechLine).
// The height is reserved, so the rows below do not jump while it loads or when a track has no info.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackInfoLine(trackKey: String) {
    val line by LMS.techLineFlow.collectAsState()
    // Source -> stream when transcoding: the arrow in the text becomes Material's transcode icon (join_left)
    val arrow = line.indexOf('\u2192')
    val shown = buildAnnotatedString {
        if (arrow < 0) append(line) else {
            append(line.substring(0, arrow))
            appendInlineContent("transcode", "\u2192")
            append(line.substring(arrow + 1))
        }
    }
    val inline = mapOf(
        "transcode" to InlineTextContent(
            Placeholder(width = 14.sp, height = 14.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
        ) {
            Icon(Icons.Filled.JoinLeft, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.fillMaxSize())
        }
    )
    Spacer(modifier = Modifier.height(4.dp))
    Box(modifier = Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        if (line.isNotEmpty()) {
            Text(
                shown,
                inlineContent = inline,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    }
}
