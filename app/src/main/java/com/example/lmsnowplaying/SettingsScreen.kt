package com.example.lmsnowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.composable.BackgroundMenuIcon
import com.example.lmsnowplaying.composable.FilmstripIcon
import com.example.lmsnowplaying.composable.ServerIcon
import com.example.lmsnowplaying.composable.SettingsMenuIcon
import com.example.lmsnowplaying.composable.StandbyMenuIcon
import com.example.lmsnowplaying.composable.VolumeIcon
import com.example.lmsnowplaying.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import com.example.lmsnowplaying.network.logitechmediaserver.LMS

// Saves run one after the other so an older snapshot can never overwrite a newer one.
private val saveLock = Mutex()

/**
 * Adds "http://" if the user left the scheme off (e.g. typed just an IP or
 * hostname), and strips a trailing slash so URL concatenation elsewhere
 * (e.g. "$lmsUrl/music/...") doesn't end up with a double slash.
 */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return trimmed
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    return withScheme.trimEnd('/')
}

/**
 * Settings as a list in the same style as the ⋮ menu: rows driven by the
 * D-pad, choice lists for the options, and separate fill-in screens for the
 * server details (the only place where typing is needed).
 */
// Default-player list: fixed row height, so it always shows whole rows.
private val DEF_ROW_HEIGHT = 44.dp
private const val DEF_ROWS_VISIBLE = 6

@Preview(heightDp = 540, widthDp = 960)
@Composable
fun SettingsScreen(
    initial: SettingsRepository.Settings = SettingsRepository.Settings(),
    onSaved: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lmsUrl by remember { mutableStateOf(initial.lmsUrl) }
    var lmsUsername by remember { mutableStateOf(initial.lmsUsername) }
    var lmsPassword by remember { mutableStateOf(initial.lmsPassword) }
    var jellyfinUrl by remember { mutableStateOf(initial.jellyfinUrl) }
    var jellyfinUsername by remember { mutableStateOf(initial.jellyfinUsername) }
    var jellyfinPassword by remember { mutableStateOf(initial.jellyfinPassword) }
    var jellyfinApiKey by remember { mutableStateOf(initial.jellyfinApiKey) }
    var backgroundStyle by remember { mutableStateOf(initial.backgroundStyle) }
    var standbyOnExit by remember { mutableStateOf(initial.standbyOnExit) }
    var volumeStep by remember { mutableStateOf(initial.volumeStep) }
    var seekStep by remember { mutableStateOf(initial.seekStep) }
    var localPlayerApp by remember { mutableStateOf(initial.localPlayerApp) }
    var localPlayerAutoStart by remember { mutableStateOf(initial.localPlayerAutoStart) }
    var localPlayerStopOnExit by remember { mutableStateOf(initial.localPlayerStopOnExit) }
    var showVolumeSlider by remember { mutableStateOf(initial.showVolumeSlider) }
    var showTrackInfoButton by remember { mutableStateOf(initial.showTrackInfoButton) }
    var showTrackInfoLine by remember { mutableStateOf(initial.showTrackInfoLine) }
    var localPlayerDeviceVolume by remember { mutableStateOf(initial.localPlayerDeviceVolume) }
    var localPlayerFixedVolume by remember { mutableStateOf(initial.localPlayerFixedVolume) }
    // Is this device's Squeezelite connected to Lyrion right now?
    var slRunning by remember { mutableStateOf(false) }
    var defaultPlayerName by remember { mutableStateOf(initial.defaultPlayerName) }
    var defaultPlayerMac by remember { mutableStateOf(initial.defaultPlayerMac) }
    var onlyControlDefault by remember { mutableStateOf(initial.onlyControlDefault) }
    var hiddenPlayers by remember { mutableStateOf(initial.hiddenPlayers) }
    var hiddenMyMusic by remember { mutableStateOf(initial.hiddenMyMusic) }
    // Connected players, captured when the Default player list is opened.
    var defPlayers by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    // Sync (for the selected player): the other players, and its 7 sync settings.
    var syncCands by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var syncTick by remember { mutableStateOf(0) }
    var syncPrefs by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val syncListState = rememberLazyListState()

    // "list", "lms" or "jellyfin"; popup is "", "bg", "vol" or "player".
    var screen by remember { mutableStateOf("list") }
    var popup by remember { mutableStateOf("") }
    var highlighted by remember { mutableStateOf(0) }
    var popupIndex by remember { mutableStateOf(0) }
    // Default-player menu: -1 = the ⓘ button next to the title; the text shows after OK on it.
    var defInfoShown by remember { mutableStateOf(false) }

    val bgValues = listOf(
        SettingsRepository.BACKGROUND_STYLE_ARTIST,
        SettingsRepository.BACKGROUND_STYLE_ARTIST_GRAYSCALE,
        SettingsRepository.BACKGROUND_STYLE_ALBUM,
        SettingsRepository.BACKGROUND_STYLE_STARFIELD,
    )
    val bgLabels = listOf(
        stringResource(R.string.settings_background_artist),
        stringResource(R.string.settings_background_artist_grayscale),
        stringResource(R.string.settings_background_album),
        stringResource(R.string.settings_background_starfield),
    )
    val volValues = listOf(1, 2, 5, 10)
    val seekValues = listOf(1, 2, 5, 10, 30)
    val playerApps = listOf(
        SettingsRepository.LOCAL_PLAYER_NONE,
        SettingsRepository.LOCAL_PLAYER_SBPLAYER,
        SettingsRepository.LOCAL_PLAYER_SQUEEZELITE,
    )
    val noneLabel = stringResource(R.string.menu_local_player_none)
    val playerLabels = listOf(noneLabel, "SB Player", "Squeezelite")
    val notSet = stringResource(R.string.settings_not_set)
    val onLabel = stringResource(R.string.menu_standby_on)
    val offLabel = stringResource(R.string.menu_standby_off)

    // Opened from the ⋮ menu: every change is saved at once, no Save row.
    // First start (no settings yet): "Save and start".
    val hasCancel = onCancel != null
    val saveRow = 7
    // Row 7: Default player (from the ⋮ menu) or Save and start (first start).
    // Row 8 (only from the ⋮ menu): which items My Music shows.
    // Row 9 (only from the ⋮ menu): the track info button on/off.
    // Row 10 (only from the ⋮ menu): Synchronize… (couple players / sync settings of the selected player).
    val rowCount = if (hasCancel) 11 else 8
    val rp = if (hasCancel) 6.dp else 10.dp
    val playerOk: () -> Boolean = { LMS.playerMac.isNotBlank() && LMS.playerMac != "00:00:00:00:00:00" }
    val syncChange: (String, Int) -> Unit = { name, v ->
        syncPrefs = syncPrefs + (name to v)
        val mac = LMS.playerMac
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { LMS.setSyncPref(mac, name, v) }
    }
    val myMusicIds = listOf("albumartists", "artists", "composers", "genres", "years", "releases", "newmusic", "folders", "favorites", "playlists")
    val myMusicLabels = listOf(
        stringResource(R.string.browse_album_artists), stringResource(R.string.browse_artists), stringResource(R.string.browse_composers), stringResource(R.string.browse_genres),
        stringResource(R.string.browse_years), stringResource(R.string.browse_releases), stringResource(R.string.browse_new_music), stringResource(R.string.browse_music_folder), stringResource(R.string.browse_favorites),
        stringResource(R.string.browse_playlists)
    )

    val buildSettings: () -> SettingsRepository.Settings = {
        SettingsRepository.Settings(
            lmsUrl = normalizeUrl(lmsUrl),
            lmsUsername = lmsUsername.trim(),
            lmsPassword = lmsPassword,
            jellyfinUrl = normalizeUrl(jellyfinUrl),
            jellyfinUsername = jellyfinUsername.trim(),
            jellyfinPassword = jellyfinPassword,
            jellyfinApiKey = jellyfinApiKey.trim(),
            defaultPlayerName = defaultPlayerName,
            defaultPlayerMac = defaultPlayerMac,
            onlyControlDefault = onlyControlDefault && defaultPlayerMac.isNotEmpty(),
            hiddenPlayers = hiddenPlayers,
            hiddenMyMusic = hiddenMyMusic,
            backgroundStyle = backgroundStyle,
            standbyOnExit = standbyOnExit,
            volumeStep = volumeStep,
            seekStep = seekStep,
            localPlayerApp = localPlayerApp,
            localPlayerStopOnExit = localPlayerStopOnExit,
            localPlayerAutoStart = localPlayerAutoStart && localPlayerApp != SettingsRepository.LOCAL_PLAYER_NONE,
            showVolumeSlider = showVolumeSlider,
            showTrackInfoButton = showTrackInfoButton,
            showTrackInfoLine = showTrackInfoLine,
            localPlayerDeviceVolume = localPlayerDeviceVolume && localPlayerApp != SettingsRepository.LOCAL_PLAYER_NONE,
            localPlayerFixedVolume = localPlayerFixedVolume,
        )
    }

    val doSave: () -> Unit = {
        val toSave = buildSettings()
        scope.launch {
            withContext(Dispatchers.IO) { SettingsRepository.save(context, toSave) }
            onSaved()
        }
    }

    // Autosave: effective at once; the write runs outside this screen's scope
    // so leaving the screen right after a change cannot cancel it.
    val persist: () -> Unit = {
        if (hasCancel) {
            val toSave = buildSettings()
            AppConfig.update(toSave)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                saveLock.withLock { SettingsRepository.save(context, toSave) }
            }
        }
    }

    // Server details: saved when a field is left or the fill-in screen is closed.
    val commitServers: () -> Unit = {
        lmsUrl = normalizeUrl(lmsUrl)
        jellyfinUrl = normalizeUrl(jellyfinUrl)
        persist()
    }

    // Re-read the player list now, so "Only control default" takes effect at once.
    val refreshPlayersNow: () -> Unit = {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            if (AppConfig.current.lmsUrl.isNotBlank()) LMS.getPlayers()
        }
    }

    // Local player changes apply at once: stop the old app, start the new one.
    val applyLocal: (String, Boolean, Boolean) -> Unit = { app, autoIn, stopIn ->
        val oldApp = localPlayerApp
        val oldAuto = localPlayerAutoStart
        val none = app == SettingsRepository.LOCAL_PLAYER_NONE
        val auto = autoIn && !none
        localPlayerApp = app
        localPlayerAutoStart = auto
        localPlayerStopOnExit = stopIn && !none
        localPlayerDeviceVolume = localPlayerDeviceVolume && !none
        persist()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            if (oldApp != app) {
                LocalPlayer.stop(context, oldApp)
                LocalPlayer.start(context, app)
            } else if (auto && !oldAuto) {
                LocalPlayer.start(context, app)
            }
            LocalPlayer.refreshPlayersSoon()
            // No server address yet (first start): LMS must not be touched.
            if (AppConfig.current.lmsUrl.isNotBlank()) {
                com.example.lmsnowplaying.network.logitechmediaserver.LMS.getPlayers()
            }
        }
    }

    // Is this device's Squeezelite connected right now? (shown next to its volume row)
    LaunchedEffect(popup, localPlayerApp) {
        if (popup == "player" && localPlayerApp == SettingsRepository.LOCAL_PLAYER_SQUEEZELITE) {
            slRunning = withContext(Dispatchers.IO) { LocalPlayer.localPlayerMac() != null }
        }
    }
    val fixedOnText = stringResource(R.string.menu_local_player_fixed_on)
    val fixedOffText = stringResource(R.string.menu_local_player_fixed_off)
    val fixedFailedText = stringResource(R.string.menu_local_player_fixed_failed)
    val fixedLaterText = stringResource(R.string.menu_local_player_fixed_later)
    // The choice is stored and sent to Lyrion as soon as Squeezelite is connected
    // (at once if it is already). A message tells which of the two happened.
    val toggleFixed: () -> Unit = {
        val newValue = if (localPlayerFixedVolume == 1) 0 else 1
        localPlayerFixedVolume = newValue
        persist()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            LocalPlayer.resetFixedApply()
            val mac = LocalPlayer.localPlayerMac()
            var message = fixedLaterText
            if (mac != null) {
                LocalPlayer.applyFixedVolume()
                val variableNow = LMS.getDigitalVolumeControl(mac)
                message = if (variableNow != null && variableNow == (newValue == 0)) {
                    if (newValue == 1) fixedOnText else fixedOffText
                } else fixedFailedText
            }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Rows of the Local player list; some only exist for certain apps.
    val playerRows = buildList {
        add("app0"); add("app1"); add("app2"); add("auto"); add("stop")
        if (localPlayerApp != SettingsRepository.LOCAL_PLAYER_NONE) add("dev")
        if (localPlayerApp == SettingsRepository.LOCAL_PLAYER_SBPLAYER) add("slider")
        if (localPlayerApp == SettingsRepository.LOCAL_PLAYER_SQUEEZELITE) add("fixed")
        add("done")
    }

    // Back closes a choice list or a fill-in screen first.
    BackHandler(enabled = popup.isNotEmpty() || screen != "list") {
        if (popup == "syncpick") { popup = "syncmenu"; popupIndex = 0 }
        else if (popup == "syncset") { popup = "syncmenu"; popupIndex = 1 }
        else if (popup.isNotEmpty()) popup = "" else { commitServers(); screen = "list" }
    }

    // Keep the highlighted row of the default-player list in view.
    val defListState = rememberLazyListState()
    LaunchedEffect(popupIndex, popup) {
        if (popup == "defplayer") {
            val n = defPlayers.size + 1
            if (popupIndex == -1) defListState.scrollToItem(0)
            else if (popupIndex in 0 until n) {
                val first = defListState.firstVisibleItemIndex
                val visible = defListState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                if (popupIndex < first) defListState.scrollToItem(popupIndex)
                else if (popupIndex > first + visible - 1) defListState.scrollToItem(popupIndex - visible + 1)
            }
        }
    }

    LaunchedEffect(popup) {
        if (popup == "syncset") syncPrefs = withContext(Dispatchers.IO) { LMS.getSyncPrefs(LMS.playerMac) }
    }
    LaunchedEffect(popupIndex, popup) {
        if (popup == "syncpick" && popupIndex in syncCands.indices) {
            val first = syncListState.firstVisibleItemIndex
            val visible = syncListState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            if (popupIndex < first) syncListState.scrollToItem(popupIndex)
            else if (popupIndex > first + visible - 1) syncListState.scrollToItem(popupIndex - visible + 1)
        }
    }

    val rootFocus = remember { FocusRequester() }
    var rootHasFocus by remember { mutableStateOf(false) }
    // Keep asking until the list really has key focus (see the ⋮ menu).
    LaunchedEffect(screen) {
        if (screen == "list") {
            while (true) {
                if (!rootHasFocus) {
                    try { rootFocus.requestFocus() } catch (_: Exception) { }
                }
                delay(150)
            }
        }
    }

    // Opened from the ⋮ menu: close by itself after 15 s without a key press or
    // typing (not on first start). Everything is autosaved; a field still being
    // edited is committed first, like Back does.
    var lastSettingsKey by remember { mutableStateOf(System.currentTimeMillis()) }
    if (hasCancel) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                val idle = System.currentTimeMillis() - maxOf(lastSettingsKey, NavState.lastTyped)
                if (idle > 15_000) {
                    if (screen != "list") commitServers()
                    // Idle close: back to Now Playing, not into the ⋮ menu.
                    NavState.reopenMenu = false
                    onCancel?.invoke()
                    return@LaunchedEffect
                }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { lastSettingsKey = System.currentTimeMillis(); false }) {
            if (screen == "list") {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(560.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE6222222))
                        .padding(vertical = 8.dp)
                        .focusRequester(rootFocus)
                        .onFocusChanged { rootHasFocus = it.isFocused }
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val isOk = event.key == Key.DirectionCenter ||
                                event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (popup.isNotEmpty()) {
                                val count = when (popup) {
                                    "bg" -> bgValues.size
                                    "vol" -> volValues.size
                                    "seek" -> seekValues.size
                                    "defplayer" -> defPlayers.size + 2
                                    "mymusic" -> myMusicIds.size
                                    "syncmenu" -> 2
                                    "syncpick" -> syncCands.size.coerceAtLeast(1)
                                    "syncset" -> LMS.syncPrefNames.size
                                    else -> playerRows.size
                                }
                                when {
                                    // Default-player menu: up from the first row goes to the ⓘ button.
                                    popup == "defplayer" && event.key == Key.DirectionDown ->
                                        popupIndex = if (popupIndex == -1) 0 else (popupIndex + 1) % count
                                    popup == "defplayer" && event.key == Key.DirectionUp ->
                                        popupIndex = if (popupIndex == 0) -1 else if (popupIndex == -1) count - 1 else popupIndex - 1
                                    event.key == Key.DirectionDown -> popupIndex = (popupIndex + 1) % count
                                    event.key == Key.DirectionUp -> popupIndex = (popupIndex - 1 + count) % count
                                    // Default-player list: D-pad right shows/hides that player in the ⋮ menu.
                                    event.key == Key.DirectionRight && popup == "defplayer" && popupIndex in 1..defPlayers.size -> {
                                        val mac = defPlayers[popupIndex - 1].second
                                        hiddenPlayers = if (mac in hiddenPlayers) hiddenPlayers - mac else hiddenPlayers + mac
                                        persist()
                                    }
                                    event.key == Key.DirectionRight && popup == "syncset" && popupIndex >= 3 -> {
                                        val n = LMS.syncPrefNames[popupIndex]
                                        val big = popupIndex < 5
                                        val cur = syncPrefs[n] ?: 0
                                        val nv = (cur + if (big) 10 else 1).coerceAtMost(if (big) 1000 else 100)
                                        if (nv != cur) syncChange(n, nv)
                                    }
                                    event.key == Key.DirectionLeft && popup == "syncset" -> {
                                        if (popupIndex < 3) { popup = "syncmenu"; popupIndex = 1 } else {
                                            val n = LMS.syncPrefNames[popupIndex]
                                            val cur = syncPrefs[n] ?: 0
                                            val nv = (cur - if (popupIndex < 5) 10 else 1).coerceAtLeast(0)
                                            if (nv != cur) syncChange(n, nv)
                                        }
                                    }
                                    event.key == Key.DirectionLeft && popup == "syncpick" -> { popup = "syncmenu"; popupIndex = 0 }
                                    event.key == Key.DirectionLeft && popup == "syncmenu" -> popup = ""
                                    isOk -> when (popup) {
                                        "bg" -> { backgroundStyle = bgValues[popupIndex]; popup = ""; persist() }
                                        "vol" -> { volumeStep = volValues[popupIndex]; popup = ""; persist() }
                                        "seek" -> { seekStep = seekValues[popupIndex]; popup = ""; persist() }
                                        "mymusic" -> {
                                            val id = myMusicIds[popupIndex]
                                            hiddenMyMusic = if (id in hiddenMyMusic) hiddenMyMusic - id else hiddenMyMusic + id
                                            persist()
                                        }
                                        "defplayer" -> {
                                            if (popupIndex == -1) {
                                                defInfoShown = !defInfoShown
                                            } else if (popupIndex == defPlayers.size + 1) {
                                                // Switch: only possible with a default player chosen.
                                                if (defaultPlayerMac.isNotEmpty()) {
                                                    onlyControlDefault = !onlyControlDefault
                                                    persist()
                                                    refreshPlayersNow()
                                                }
                                            } else {
                                                val p = defPlayers.getOrNull(popupIndex - 1)
                                                defaultPlayerName = p?.first ?: ""
                                                defaultPlayerMac = p?.second ?: ""
                                                if (defaultPlayerMac.isEmpty()) onlyControlDefault = false
                                                popup = ""
                                                persist()
                                                refreshPlayersNow()
                                            }
                                        }
                                        "syncmenu" -> if (popupIndex == 0) {
                                            syncCands = LMS.players.filter { it.second != LMS.playerMac && it.second !in hiddenPlayers }
                                            if (syncCands.isNotEmpty()) { popupIndex = 0; popup = "syncpick" }
                                        } else { popupIndex = 0; popup = "syncset" }
                                        "syncpick" -> {
                                            val other = syncCands.getOrNull(popupIndex)
                                            if (other != null) {
                                                val cur = LMS.playerMac
                                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                                    LMS.toggleSync(cur, other.second)
                                                    LMS.getPlayers()
                                                    withContext(Dispatchers.Main) { syncTick++ }
                                                }
                                            }
                                        }
                                        "syncset" -> if (popupIndex < 3) {
                                            val n = LMS.syncPrefNames[popupIndex]
                                            syncChange(n, if ((syncPrefs[n] ?: 0) == 1) 0 else 1)
                                        }
                                        else -> {
                                            val none = localPlayerApp == SettingsRepository.LOCAL_PLAYER_NONE
                                            val row = playerRows.getOrNull(popupIndex)
                                            when {
                                                popupIndex < 3 -> applyLocal(playerApps[popupIndex], localPlayerAutoStart, localPlayerStopOnExit)
                                                row == "auto" -> if (!none) applyLocal(localPlayerApp, !localPlayerAutoStart, localPlayerStopOnExit)
                                                row == "stop" -> if (!none) applyLocal(localPlayerApp, localPlayerAutoStart, !localPlayerStopOnExit)
                                                row == "dev" -> { localPlayerDeviceVolume = !localPlayerDeviceVolume; persist() }
                                                row == "slider" -> { showVolumeSlider = !showVolumeSlider; persist() }
                                                row == "fixed" -> toggleFixed()
                                                else -> popup = ""
                                            }
                                        }
                                    }
                                    else -> return@onKeyEvent false
                                }
                                return@onKeyEvent true
                            }
                            when {
                                event.key == Key.DirectionDown -> highlighted = (highlighted + 1) % rowCount
                                event.key == Key.DirectionUp -> highlighted = (highlighted - 1 + rowCount) % rowCount
                                isOk -> when (highlighted) {
                                    0 -> screen = "lms"
                                    1 -> screen = "jellyfin"
                                    2 -> { popupIndex = bgValues.indexOf(backgroundStyle).coerceAtLeast(0); popup = "bg" }
                                    3 -> { standbyOnExit = !standbyOnExit; persist() }
                                    4 -> { popupIndex = volValues.indexOf(volumeStep).coerceAtLeast(0); popup = "vol" }
                                    5 -> { popupIndex = seekValues.indexOf(seekStep).coerceAtLeast(0); popup = "seek" }
                                    6 -> {
                                        popupIndex = playerApps.indexOf(localPlayerApp).coerceAtLeast(0)
                                        popup = "player"
                                    }
                                    7 -> if (!hasCancel) doSave() else if (AppConfig.current.lmsUrl.isNotBlank()) {
                                        defPlayers = LMS.players.toList()
                                        popupIndex = defPlayers.indexOfFirst { it.second == defaultPlayerMac } + 1
                                        defInfoShown = false
                                        popup = "defplayer"
                                    }
                                    8 -> if (hasCancel) { popupIndex = 0; popup = "mymusic" }
                                    9 -> if (hasCancel) {
                                        // Off -> button -> line -> button and line -> off
                                        val next = (when { showTrackInfoButton && showTrackInfoLine -> 3; showTrackInfoLine -> 2; showTrackInfoButton -> 1; else -> 0 } + 1) % 4
                                        showTrackInfoButton = next == 1 || next == 3
                                        showTrackInfoLine = next == 2 || next == 3
                                        persist()
                                    }
                                    10 -> if (hasCancel && playerOk()) { popupIndex = 0; popup = "syncmenu" }
                                }
                                else -> return@onKeyEvent false
                            }
                            true
                        }
                ) {
                    when (popup) {
                        "bg" -> {
                            SRow(stringResource(R.string.menu_background_prefix), BackgroundMenuIcon, false, bold = true)
                            bgLabels.forEachIndexed { i, l ->
                                SRow((if (backgroundStyle == bgValues[i]) "●  " else "○  ") + l, null, popupIndex == i)
                            }
                        }
                        "vol" -> {
                            SRow(stringResource(R.string.settings_volume_step_section), VolumeIcon, false, bold = true)
                            volValues.forEachIndexed { i, v ->
                                SRow((if (volumeStep == v) "●  " else "○  ") + "$v%", null, popupIndex == i)
                            }
                        }
                        "seek" -> {
                            SRow(stringResource(R.string.menu_seek_step_prefix), Icons.Filled.FastForward, false, bold = true)
                            seekValues.forEachIndexed { i, v ->
                                SRow((if (seekStep == v) "●  " else "○  ") + "$v s", null, popupIndex == i)
                            }
                        }
                        "mymusic" -> {
                            SRow(stringResource(R.string.settings_mymusic_items), null, false, bold = true)
                            myMusicIds.forEachIndexed { i, id ->
                                SRow((if (id in hiddenMyMusic) "☐  " else "☑  ") + myMusicLabels[i], null, popupIndex == i)
                            }
                        }
                        "defplayer" -> {
                            val listCount = defPlayers.size + 1
                            val listScrolls = listCount > DEF_ROWS_VISIBLE
                            val position = if (popupIndex in 0 until listCount) "${popupIndex + 1}/$listCount" else "$listCount"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(
                                    text = stringResource(R.string.menu_default_player_prefix) + if (listScrolls) " ($position)" else "",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (popupIndex == -1) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "Info",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (defInfoShown) {
                                Text(
                                    stringResource(R.string.default_player_info),
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                            }
                            LazyColumn(
                                state = defListState,
                                modifier = Modifier
                                    .heightIn(max = DEF_ROW_HEIGHT * DEF_ROWS_VISIBLE)
                                    .drawWithContent {
                                        drawContent()
                                        val info = defListState.layoutInfo
                                        val total = info.totalItemsCount
                                        val shown = info.visibleItemsInfo.size
                                        if (total > shown && shown > 0) {
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = 0.85f),
                                                topLeft = Offset(size.width - 6.dp.toPx(), size.height * defListState.firstVisibleItemIndex / total),
                                                size = Size(4.dp.toPx(), size.height * shown / total),
                                                cornerRadius = CornerRadius(2.dp.toPx())
                                            )
                                        }
                                    }
                            ) {
                                items(listCount) { i ->
                                    Box(Modifier.fillMaxWidth().height(DEF_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                                        if (i == 0) {
                                            SRow((if (defaultPlayerMac.isEmpty()) "●  " else "○  ") + noneLabel, null, popupIndex == 0)
                                        } else {
                                            val p = defPlayers[i - 1]
                                            SRow(
                                                (if (defaultPlayerMac == p.second) "●  " else "○  ") + p.first, null, popupIndex == i,
                                                trailing = { Text(if (p.second in hiddenPlayers) "☐" else "☑", color = Color.White) }
                                            )
                                        }
                                    }
                                }
                            }
                            if (listScrolls) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)))
                            }
                            SRow(
                                stringResource(R.string.menu_only_control_default), null, popupIndex == defPlayers.size + 1,
                                dim = defaultPlayerMac.isEmpty(),
                                trailing = { Switch(checked = onlyControlDefault && defaultPlayerMac.isNotEmpty(), onCheckedChange = null) }
                            )
                        }
                        "syncmenu" -> {
                            SRow(stringResource(R.string.menu_sync) + " " + LMS.playerName, Icons.Outlined.Link, false, bold = true)
                            val anyOther = LMS.players.any { it.second != LMS.playerMac && it.second !in hiddenPlayers }
                            SRow(stringResource(R.string.menu_sync_with) + "…", Icons.Outlined.Link, popupIndex == 0, dim = !anyOther)
                            SRow(stringResource(R.string.menu_sync_settings), Icons.Outlined.Tune, popupIndex == 1)
                        }
                        "syncpick" -> {
                            val tick = syncTick
                            val total = syncCands.size
                            SRow(
                                stringResource(R.string.menu_sync_with) + " " + LMS.playerName +
                                    if (total > DEF_ROWS_VISIBLE) " (${popupIndex + 1}/$total)" else "",
                                Icons.Outlined.Link, false, bold = true
                            )
                            LazyColumn(
                                state = syncListState,
                                modifier = Modifier
                                    .heightIn(max = DEF_ROW_HEIGHT * DEF_ROWS_VISIBLE)
                                    .drawWithContent {
                                        drawContent()
                                        val info = syncListState.layoutInfo
                                        val t = info.totalItemsCount
                                        val shown = info.visibleItemsInfo.size
                                        if (t > shown && shown > 0) {
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = 0.85f),
                                                topLeft = Offset(size.width - 6.dp.toPx(), size.height * syncListState.firstVisibleItemIndex / t),
                                                size = Size(4.dp.toPx(), size.height * shown / t),
                                                cornerRadius = CornerRadius(2.dp.toPx())
                                            )
                                        }
                                    }
                            ) {
                                items(total) { i ->
                                    val p = syncCands[i]
                                    Box(Modifier.fillMaxWidth().height(DEF_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                                        SRow(
                                            p.first, null, popupIndex == i,
                                            trailing = { Text(if (tick >= 0 && LMS.inSameSyncGroup(LMS.playerMac, p.second)) "☑" else "☐", color = Color.White) }
                                        )
                                    }
                                }
                            }
                            if (total > DEF_ROWS_VISIBLE) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)))
                            }
                        }
                        "syncset" -> {
                            SRow(stringResource(R.string.menu_sync_settings_title) + " " + LMS.playerName, Icons.Outlined.Tune, false, bold = true)
                            val labels = listOf(
                                R.string.sync_pref_volume, R.string.sync_pref_power, R.string.sync_pref_maintain,
                                R.string.sync_pref_start_delay, R.string.sync_pref_audio_delay,
                                R.string.sync_pref_min_adjust, R.string.sync_pref_packet
                            )
                            LMS.syncPrefNames.forEachIndexed { i, name ->
                                val v = syncPrefs[name]
                                SRow(
                                    stringResource(labels[i]), null, popupIndex == i,
                                    trailing = {
                                        Text(
                                            when {
                                                v == null -> "…"
                                                i < 3 -> if (v == 1) "☑" else "☐"
                                                else -> "‹ $v ›"
                                            },
                                            color = Color.White
                                        )
                                    }
                                )
                            }
                        }
                        "player" -> {
                            SRow(stringResource(R.string.menu_local_player_prefix), Icons.Filled.PlayArrow, false, bold = true)
                            playerRows.forEachIndexed { i, id ->
                                when {
                                    i < 3 -> SRow((if (localPlayerApp == playerApps[i]) "●  " else "○  ") + playerLabels[i], null, popupIndex == i)
                                    id == "auto" -> SRow((if (localPlayerAutoStart) "☑  " else "☐  ") + stringResource(R.string.menu_local_player_auto), null, popupIndex == i)
                                    id == "stop" -> SRow((if (localPlayerStopOnExit) "☑  " else "☐  ") + stringResource(R.string.menu_local_player_stop), null, popupIndex == i)
                                    id == "dev" -> SRow((if (localPlayerDeviceVolume) "☑  " else "☐  ") + stringResource(R.string.menu_local_player_device_volume), null, popupIndex == i)
                                    id == "slider" -> SRow((if (showVolumeSlider) "☑  " else "☐  ") + stringResource(R.string.menu_volume_slider_prefix), null, popupIndex == i)
                                    id == "fixed" -> SRow(
                                        (if (localPlayerFixedVolume == 1) "☑  " else "☐  ") + stringResource(R.string.menu_local_player_fixed_volume) +
                                            (if (!slRunning) " (" + stringResource(R.string.menu_local_player_fixed_pending) + ")" else ""),
                                        null, popupIndex == i
                                    )
                                    else -> SRow(stringResource(R.string.settings_done), null, popupIndex == i, bold = true)
                                }
                            }
                        }
                        else -> {
                            SRow(stringResource(R.string.settings_title), SettingsMenuIcon, false, bold = true)
                            SRow("Lyrion: ${lmsUrl.ifBlank { notSet }}", ServerIcon, highlighted == 0, vPad = rp)
                            SRow("Jellyfin: ${jellyfinUrl.ifBlank { notSet }}", FilmstripIcon, highlighted == 1, vPad = rp)
                            SRow("${stringResource(R.string.menu_background_prefix)}: ${bgLabels[bgValues.indexOf(backgroundStyle).coerceAtLeast(0)]}", BackgroundMenuIcon, highlighted == 2, vPad = rp)
                            SRow("${stringResource(R.string.menu_standby_prefix)}: ${if (standbyOnExit) onLabel else offLabel}", StandbyMenuIcon, highlighted == 3, vPad = rp)
                            SRow("${stringResource(R.string.menu_volume_step_prefix)}: $volumeStep%", VolumeIcon, highlighted == 4, vPad = rp)
                            SRow("${stringResource(R.string.menu_seek_step_prefix)}: $seekStep s", Icons.Filled.FastForward, highlighted == 5, vPad = rp)
                            SRow(
                                "${stringResource(R.string.menu_local_player_prefix)}: ${playerLabels[playerApps.indexOf(localPlayerApp).coerceAtLeast(0)]}" +
                                    if (localPlayerAutoStart && localPlayerApp != SettingsRepository.LOCAL_PLAYER_NONE) " (${stringResource(R.string.menu_local_player_auto)})" else "",
                                Icons.Filled.PlayArrow, highlighted == 6, vPad = rp
                            )
                            if (hasCancel) {
                                SRow("${stringResource(R.string.menu_default_player_prefix)}: ${defaultPlayerName.ifBlank { noneLabel }}", Icons.Filled.Check, highlighted == 7, vPad = rp)
                                SRow(stringResource(R.string.settings_mymusic_items), null, highlighted == 8, vPad = rp)
                                SRow("${stringResource(R.string.settings_track_info)}: " + when {
                                    showTrackInfoButton && showTrackInfoLine -> stringResource(R.string.info_mode_both)
                                    showTrackInfoButton -> stringResource(R.string.info_mode_button)
                                    showTrackInfoLine -> stringResource(R.string.info_mode_line)
                                    else -> offLabel
                                }, Icons.Outlined.Info, highlighted == 9, vPad = rp)
                                SRow(stringResource(R.string.menu_sync), Icons.Outlined.Link, highlighted == 10, dim = !playerOk(), vPad = rp)
                            } else {
                                SRow(stringResource(R.string.settings_save_and_start), null, highlighted == saveRow, vPad = rp, bold = true)
                            }
                        }
                    }
                }
            } else {
                // Fill-in screen for one server (the only place with typing).
                val isLms = screen == "lms"
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(560.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE6222222))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLms) {
                        SectionTitle(stringResource(R.string.settings_lms_section), ServerIcon, MaterialTheme.typography.titleMedium, FontWeight.Bold, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        SettingField(stringResource(R.string.settings_lms_url_label), lmsUrl, { lmsUrl = it }, commitServers)
                        SettingField(stringResource(R.string.settings_lms_username_label), lmsUsername, { lmsUsername = it }, commitServers)
                        SettingField(stringResource(R.string.settings_lms_password_label), lmsPassword, { lmsPassword = it }, commitServers)
                    } else {
                        SectionTitle(stringResource(R.string.settings_jellyfin_section), FilmstripIcon, MaterialTheme.typography.titleMedium, FontWeight.Bold, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        SettingField(stringResource(R.string.settings_jellyfin_url_label), jellyfinUrl, { jellyfinUrl = it }, commitServers)
                        SettingField(stringResource(R.string.settings_jellyfin_username_label), jellyfinUsername, { jellyfinUsername = it }, commitServers)
                        SettingField(stringResource(R.string.settings_jellyfin_password_label), jellyfinPassword, { jellyfinPassword = it }, commitServers)
                        SettingField(stringResource(R.string.settings_jellyfin_apikey_label), jellyfinApiKey, { jellyfinApiKey = it }, commitServers)
                    }
                    FocusRow(stringResource(R.string.settings_done)) { commitServers(); screen = "list" }
                }
            }
        }
    }
}

// A row in the settings list: optional icon, then text; highlighted while the D-pad is on it.
@Composable
private fun SRow(
    text: String, icon: ImageVector?, highlighted: Boolean, bold: Boolean = false,
    dim: Boolean = false, trailing: (@Composable () -> Unit)? = null,
    vPad: androidx.compose.ui.unit.Dp = 10.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) Color.White.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = vPad)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        } else {
            Box(Modifier.size(18.dp))
        }
        Text(
            text = text,
            color = if (dim) Color.White.copy(alpha = 0.4f) else Color.White,
            fontWeight = if (bold) FontWeight.Bold else null,
            modifier = (if (trailing != null) Modifier.weight(1f) else Modifier).padding(start = 10.dp)
        )
        if (trailing != null) trailing()
    }
}

// Text field that only becomes editable (and opens the keyboard) after OK,
// so moving over it with the D-pad doesn't pop up the keyboard.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SettingField(label: String, value: String, onValueChange: (String) -> Unit, onCommit: () -> Unit = {}) {
    var editing by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) {
            delay(50)
            keyboard?.show()
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { dirty = true; NavState.lastTyped = System.currentTimeMillis(); onValueChange(it) },
        readOnly = !editing,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            editing = false
            keyboard?.hide()
        }),
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onFocusChanged {
                if (!it.isFocused) {
                    editing = false
                    // Edited and left: save now (autosave).
                    if (dirty) { dirty = false; onCommit() }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionCenter && !editing) {
                    if (event.type == KeyEventType.KeyDown) editing = true
                    true
                } else false
            }
    )
}

// Focusable row (D-pad OK = click) in the same look as the list rows.
@Composable
private fun FocusRow(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// Section heading with an icon in front of it.
@Composable
private fun SectionTitle(
    text: String,
    icon: ImageVector,
    style: androidx.compose.ui.text.TextStyle,
    weight: FontWeight? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(text = text, color = Color.White, fontWeight = weight, style = style)
    }
}
