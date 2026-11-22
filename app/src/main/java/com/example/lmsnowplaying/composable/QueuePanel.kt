package com.example.lmsnowplaying.composable

import com.example.lmsnowplaying.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.network.logitechmediaserver.QueueList
import com.example.lmsnowplaying.network.logitechmediaserver.QueueTrack
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext

// The play queue as a panel on top of Now Playing: choose a track to play it
// or remove it. Back / D-pad left goes one step back, and finally closes it.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QueuePanel() {
    var queue by remember { mutableStateOf<QueueList?>(null) }
    var selected by remember { mutableStateOf<QueueTrack?>(null) }
    // The track whose menu was opened last: the list comes back on it (otherwise on the playing track)
    var lastOpened by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    val moveFocus = remember { FocusRequester() }
    // With an empty queue nothing else can hold the focus; without it the keys leave the panel
    val emptyFocus = remember { FocusRequester() }
    // True while the list of players to move the queue to is shown
    var pickPlayer by remember { mutableStateOf(false) }
    // True while the name for saving the queue is asked, and the name offered first
    var naming by remember { mutableStateOf(false) }
    var defaultName by remember { mutableStateOf("") }
    val context = LocalContext.current.applicationContext

    // Read the queue now and keep it fresh while no track is selected
    LaunchedEffect(Unit) {
        while (true) {
            if (selected == null && !pickPlayer && !naming) queue = withContext(Dispatchers.IO) { LMS.getQueue() }
            delay(3000)
        }
    }

    // First time the list is there: scroll to the current track and focus it
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(queue != null) {
        val q = queue ?: return@LaunchedEffect
        if (positioned) return@LaunchedEffect
        positioned = true
        val cur = q.tracks.indexOfFirst { it.absoluteIndex == q.currentIndex }.coerceAtLeast(0)
        listState.scrollToItem((cur - 1).coerceAtLeast(0))
        delay(60)
        try { currentFocus.requestFocus() } catch (_: Exception) { }
    }

    // Back from the action menu to the list: focus the current track again
    LaunchedEffect(selected, pickPlayer, naming) {
        if (naming) {
            // the name field takes the focus itself
        } else if (selected == null && !pickPlayer) {
            delay(60)
            val q = queue
            val idx = q?.tracks?.indexOfFirst { it.absoluteIndex == (lastOpened ?: q.currentIndex) } ?: -1
            if (idx >= 0) {
                val first = listState.firstVisibleItemIndex
                val shown = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                if (idx < first || idx > first + shown - 1) listState.scrollToItem((idx - 1).coerceAtLeast(0))
                delay(60)
            }
            try { currentFocus.requestFocus() } catch (_: Exception) { }
        } else {
            delay(60)
            try { actionFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    val goBack: () -> Unit = {
        if (naming) naming = false else if (pickPlayer) pickPlayer = false else if (selected != null) selected = null else NavState.showQueue = false
    }
    // After a mouse click cleared the focus, a D-pad key comes back here (see NowPlaying)
    DisposableEffect(Unit) {
        NavState.panelRefocus = {
            try {
                when {
                    naming -> false
                    selected != null || pickPlayer -> { actionFocus.requestFocus(); true }
                    queue?.tracks?.isNotEmpty() == true -> { currentFocus.requestFocus(); true }
                    else -> { emptyFocus.requestFocus(); true }
                }
            } catch (_: Exception) { false }
        }
        onDispose { NavState.panelRefocus = null }
    }
    BackHandler(enabled = true) { goBack() }

    Box(
        modifier = Modifier
            .width(320.dp)
            .heightIn(max = 300.dp)
            // Keep D-pad focus inside the panel while it is open
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    goBack()
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight &&
                    selected == null && !pickPlayer && queue?.tracks?.isNotEmpty() == true) {
                    // Right on a track: to the "move to" button next to the title
                    try { moveFocus.requestFocus() } catch (_: Exception) { }
                    true
                } else false
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2181818))
            .padding(8.dp)
    ) {
        Column {
            val q = queue
            val sel = selected
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (naming) stringResource(R.string.queue_save) else if (pickPlayer) stringResource(R.string.queue_move_title) else sel?.title ?: if (q == null) stringResource(R.string.queue_title) else stringResource(R.string.queue_title_count, q.tracks.size),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp).weight(1f)
                )
                if (!pickPlayer && !naming && sel == null && q != null && q.tracks.isNotEmpty()) {
                    // Icons for the whole queue; Left and Down go back to the list (Left would close the panel)
                    IconActionRow(
                        listOf(
                            Triple(ActionMoveIcon, stringResource(R.string.queue_move_short), { pickPlayer = true; Unit }),
                            Triple(ActionSaveQueueIcon, stringResource(R.string.queue_save), {
                                defaultName = context.getString(R.string.queue_saved_name, SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date()))
                                naming = true
                                Unit
                            }),
                            Triple(ActionDuplicateIcon, stringResource(R.string.queue_dedupe), {
                                scope.launch {
                                    val removed = withContext(Dispatchers.IO) { LMS.removeDuplicates() }
                                    queue = withContext(Dispatchers.IO) { LMS.getQueue() }
                                    val text = if (removed <= 0) context.getString(R.string.queue_dedupe_none)
                                    else context.getString(R.string.queue_dedupe_done, removed)
                                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                }
                                Unit
                            }),
                            Triple(ActionClearQueueIcon, stringResource(R.string.queue_clear), {
                                scope.launch {
                                    withContext(Dispatchers.IO) { LMS.clearQueue() }
                                    // Nothing left to do in an empty queue: the panel closes
                                    NavState.showQueue = false
                                }
                                Unit
                            }),
                        ),
                        moveFocus,
                        modifier = Modifier.padding(bottom = 4.dp).onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionDown)) {
                                try { currentFocus.requestFocus() } catch (_: Exception) { }
                                true
                            } else false
                        },
                        wrapLeft = false,
                    )
                }
            }
            if (naming) {
                Popup(
                    popupPositionProvider = topCenterPopup,
                    onDismissRequest = { naming = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        modifier = Modifier
                            .width(560.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xF2181818))
                            .padding(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.queue_save), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                        SaveQueueField(defaultName) { name ->
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { LMS.saveQueueAsPlaylist(name) }
                                val text = if (ok) context.getString(R.string.queue_saved_server, name) else context.getString(R.string.queue_save_server_failed)
                                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                                naming = false
                            }
                        }
                    }
                }
            } else if (pickPlayer) {
                // Players hidden in the main menu are left out here too
                val others = LMS.players.filter { it.second != LMS.playerMac && it.second !in AppConfig.current.hiddenPlayers }
                if (others.isEmpty()) {
                    Text(stringResource(R.string.queue_move_none), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                }
                // Scrolls when there are more players than fit
                HintLazyColumn {
                    itemsIndexed(others) { i, p ->
                        QueueRow(p.first, false, if (i == 0) Modifier.focusRequester(actionFocus) else Modifier) {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { LMS.transferQueue(p.second) }
                                Toast.makeText(
                                    context,
                                    if (ok) context.getString(R.string.queue_moved, p.first) else context.getString(R.string.queue_move_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                                if (ok) {
                                    // The queue lives on that player now: follow it
                                    LMS.setPlayer(p.first, p.second)
                                    NavState.showQueue = false
                                } else pickPlayer = false
                            }
                        }
                    }
                }
            } else if (sel != null) {
                // Icons side by side: play this track now, remove it from the queue
                IconActionRow(
                    listOf(
                        Triple(ActionPlayIcon, stringResource(R.string.action_play_now), {
                            lastOpened = null
                            scope.launch {
                                withContext(Dispatchers.IO) { LMS.jumpTo(LMS.playerMac, sel.absoluteIndex) }
                                selected = null
                                queue = withContext(Dispatchers.IO) { LMS.getQueue() }
                            }
                            Unit
                        }),
                        Triple(ActionDeleteIcon, stringResource(R.string.queue_remove), {
                            lastOpened = null
                            scope.launch {
                                withContext(Dispatchers.IO) { LMS.removeFromQueue(sel.absoluteIndex) }
                                selected = null
                                queue = withContext(Dispatchers.IO) { LMS.getQueue() }
                            }
                            Unit
                        }),
                    ),
                    actionFocus,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    wrapLeft = false,
                )
            } else if (q == null) {
                Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            } else if (q.tracks.isEmpty()) {
                LaunchedEffect(Unit) {
                    delay(60)
                    try { emptyFocus.requestFocus() } catch (_: Exception) { }
                }
                Text(stringResource(R.string.queue_empty), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp).focusRequester(emptyFocus).focusable())
            } else {
                val imageLoader = rememberLmsImageLoader()
                HintLazyColumn(state = listState) {
                    itemsIndexed(q.tracks) { _, track ->
                        val isCurrent = track.absoluteIndex == q.currentIndex
                        val label = if (track.artist.isNotBlank()) "${track.title} – ${track.artist}" else track.title
                        // Streams have a made-up numeric cover id with no picture behind it
                        val realCover = track.coverId.isNotBlank() && track.coverId.toLongOrNull() == null
                        ThumbRow(
                            text = (if (isCurrent) "▶ " else "") + label,
                            imageUrl = when {
                                realCover -> coverUrl(track.coverId)
                                track.artworkUrl.startsWith("http") -> track.artworkUrl
                                track.artworkUrl.isNotBlank() -> "${AppConfig.current.lmsUrl}/" + track.artworkUrl.trimStart('/')
                                else -> null
                            },
                            placeholder = Icons.Outlined.MusicNote,
                            round = false,
                            imageLoader = imageLoader,
                            onClick = { lastOpened = track.absoluteIndex; selected = track },
                            modifier = if (track.absoluteIndex == (lastOpened ?: q.currentIndex)) Modifier.focusRequester(currentFocus) else Modifier,
                            bold = isCurrent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(text: String, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Text(
        text = text,
        color = if (current) Color.White else Color.White.copy(alpha = 0.85f),
        fontSize = 14.sp,
        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

// "Move to…" next to the queue title; Down and Left go back to the list (Left would close the panel).
@Composable
private fun QueueMoveButton(text: String, modifier: Modifier, onBack: () -> Unit, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionDown)) {
                    onBack()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
        Text(text = text, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

// Text field for the name of the queue to save, with the offered name selected so typing replaces it.
// OK opens the keyboard, Done saves (an empty name gives the offered one).
@Composable
private fun SaveQueueField(offered: String, onDone: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    var value by remember(offered) { mutableStateOf(TextFieldValue(offered, TextRange(0, offered.length))) }
    LaunchedEffect(offered) {
        delay(60)
        try { fieldFocus.requestFocus() } catch (_: Exception) { }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it; NavState.lastTyped = System.currentTimeMillis() },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                onDone(value.text.trim().ifEmpty { offered })
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                        keyboard?.show()
                        true
                    } else false
                }
        )
    }
}

// Places a popup at the top centre of the screen (the on-screen keyboard fills the lower part).
private val topCenterPopup = object : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((windowSize.width - popupContentSize.width) / 2, 40)
}
