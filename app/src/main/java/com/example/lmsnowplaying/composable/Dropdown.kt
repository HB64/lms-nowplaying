package com.example.lmsnowplaying.composable

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.LocalPlayer
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import com.example.lmsnowplaying.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Player rows have a fixed height so the list always shows whole rows.
private val PLAYER_ROW_HEIGHT = 44.dp
private const val PLAYER_ROWS_VISIBLE = 6

@Preview
@Composable
fun MyUI() {

    val data = remember { mutableStateListOf<Pair<String, String>>() }
    // Redraw when the refreshed player list differs (see the loop below).
    @Suppress("UNUSED_VARIABLE")
    val playersVersion = LMS.playersVersion.collectAsState().value
    data.clear()
    data.addAll(LMS.players.filter { it.second !in AppConfig.current.hiddenPlayers })

    // Keep the player list fresh: players come and go (e.g. a player app that is still
    // connecting) and their power state changes elsewhere. Every 2 s while the menu is open
    // and for a minute after a player app start, otherwise every 10 s.
    LaunchedEffect(Unit) {
        while (true) {
            val fast = System.currentTimeMillis() < LocalPlayer.fastRefreshUntil || NavState.menuOpen
            kotlinx.coroutines.delay(if (fast) 2000L else 10000L)
            withContext(Dispatchers.IO) {
                LMS.getPlayers()
                LocalPlayer.applyFixedVolume()
            }
        }
    }


    val contextForToast = LocalContext.current.applicationContext

    // state of the menu
    // Coming back from Settings (opened from this menu): open the menu again.
    // The flag stays set until the menu is closed again, because the screen
    // can rebuild this composable (artwork swap) right after returning.
    val returningFromSettings = remember { NavState.reopenMenu }
    var expanded by remember {
        mutableStateOf(returningFromSettings)
    }
    val menuButtonFocusRequester = remember { FocusRequester() }
    var wasExpanded by remember { mutableStateOf(false) }
    var menuButtonHasFocus by remember { mutableStateOf(false) }
    // Fresh list the moment the menu opens
    LaunchedEffect(expanded) {
        if (expanded) withContext(Dispatchers.IO) { LMS.getPlayers() }
    }
    // NavState.menuClosedAt: when the menu closed last. The Enter press that
    // closed it must not also "click" the ⋮ button that gets focus back and
    // reopen the menu.
    LaunchedEffect(expanded) {
        NavState.menuOpen = expanded
        if (wasExpanded && !expanded) NavState.menuClosedAt = System.currentTimeMillis()
        if (wasExpanded && !expanded && !NavState.showSettings) NavState.reopenMenu = false
        // Menu just closed: put focus back on the ⋮ button instead of
        // leaving it nowhere (it stays there until the idle timeout or
        // until the user moves to another button).
        // The focus the removed menu leaves behind can be handed to another
        // element a moment later, so ask a few times until the button has it.
        if (wasExpanded && !expanded && !NavState.showSettings) {
            repeat(8) {
                if (!menuButtonHasFocus) {
                    try { menuButtonFocusRequester.requestFocus() } catch (_: Exception) { }
                }
                kotlinx.coroutines.delay(100)
            }
        }
        wasExpanded = expanded
    }

    Box(
        contentAlignment = Alignment.TopEnd
    ) {
        // 3 vertical dots icon

        // Give this button focus as soon as the screen appears, so the
        // remote is immediately ready to open the settings/options menu
        // without needing to navigate there first - unless a default
        // player was already auto-selected, in which case the play button
        // takes initial focus instead (see MainActivity/NavState).
        LaunchedEffect(Unit) {
            // This button was (re)created right after the menu closed - the
            // screen swaps layout when the artwork changes, which throws the
            // old button away together with its focus. Take focus back.
            if (System.currentTimeMillis() - NavState.menuClosedAt < 3000 && !NavState.showSettings) {
                repeat(10) {
                    if (!menuButtonHasFocus) {
                        try { menuButtonFocusRequester.requestFocus() } catch (_: Exception) { }
                    }
                    kotlinx.coroutines.delay(100)
                }
                return@LaunchedEffect
            }
            if (!NavState.focusPlayButtonOnStart && !NavState.startFocusDone) {
                NavState.startFocusDone = true
                menuButtonFocusRequester.requestFocus()
            }
        }

        FocusIconButton(
            onClick = {
                if (System.currentTimeMillis() - NavState.menuClosedAt > 600) expanded = true
            },
            size = 30.dp,
            modifier = Modifier
                .padding(top = 2.dp)
                .focusRequester(menuButtonFocusRequester)
                .onFocusChanged { menuButtonHasFocus = it.isFocused; NavState.menuFocused = it.isFocused }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Open Options",
                tint = Color.LightGray,
                modifier = Modifier.size(15.dp)

            )
        }

        // Menu rendered directly in the same composition/window (not a
        // separate Popup), with D-pad navigation handled manually instead of
        // relying on Compose's ambient focus system - neither the Popup-based
        // DropdownMenu nor plain focusable() reliably showed a focus
        // highlight on this TV setup, so we track/drive it ourselves,
        // mirroring how the media-key handling already works reliably.
        if (expanded) {
            // Menu entry to start/stop the local player (SB Player or
            // Squeezelite on this device); only when one is chosen in Settings.
            val localApp = AppConfig.current.localPlayerApp
            // Not with auto-start on: LMSNP then starts the player itself (also on
            // every return to the app), so a manual start/stop would be undone.
            val hasLocal = localApp != SettingsRepository.LOCAL_PLAYER_NONE && !AppConfig.current.localPlayerAutoStart
            // "Running" = the server lists a connected player of the model that
            // belongs to that app AND whose IP address is one of this device's
            // own (so a Squeezelite on another device does not count). If the
            // server sees this device under another address (NAT, VPN), this
            // shows "Start" even though the player runs.
            val localModel = if (localApp == SettingsRepository.LOCAL_PLAYER_SQUEEZELITE) "squeezelite" else "squeezeplay"
            val ownIps = remember {
                try {
                    java.net.NetworkInterface.getNetworkInterfaces().toList()
                        .flatMap { it.inetAddresses.toList() }
                        .mapNotNull { it.hostAddress?.substringBefore('%') }
                        .toSet()
                } catch (_: Exception) { emptySet<String>() }
            }
            val localRunning = hasLocal && data.any {
                LMS.playerModels[it.second]?.first == localModel && LMS.playerIps[it.second] in ownIps
            }

            // "Only control default": no player list, only the other rows.
            val onlyDefault = AppConfig.current.onlyControlDefault && AppConfig.current.defaultPlayerMac.isNotBlank()
            val listCount = if (onlyDefault) 0 else data.size

            // Top level: "Players…" (opens the player list), Standby, local player
            // (if any), Settings. The long player list is only shown after OK on the
            // first row, so it never sits between the other rows.
            val hasPlayersRow = data.isNotEmpty()
            // Default player and "only control default": two rows below the player
            // list (not between the players), same behaviour as in Settings.
            val defaultMac = AppConfig.current.defaultPlayerMac
            val hasDefaultRow = data.isNotEmpty() || defaultMac.isNotBlank()
            val hasOnlyRow = defaultMac.isNotBlank()
            var rowCursor = 0
            val playersRowIndex = if (hasPlayersRow) rowCursor++ else -1
            val standbyRowIndex = rowCursor++
            val localRowIndex = if (hasLocal) rowCursor++ else -1
            val settingsRowIndex = rowCursor++
            val totalItems = rowCursor
            var playersMode by remember { mutableStateOf(false) }
            // true: the list is the choice of the default player (with "None" on top)
            var pickDefault by remember { mutableStateOf(false) }
            var playerIndex by remember { mutableStateOf(0) }
            // true: the power icon of the highlighted player has the focus (Right), OK switches it
            var powerFocus by remember { mutableStateOf(false) }
            // Same for the power icon on the "Players" row of the menu (the active player)
            var menuPowerFocus by remember { mutableStateOf(false) }
            // Alarms are only read while the player list is open: now, then every 30 s
            LaunchedEffect(playersMode) {
                while (playersMode) {
                    withContext(Dispatchers.IO) { LMS.refreshAlarms() }
                    kotlinx.coroutines.delay(30_000L)
                }
            }
            var highlightedIndex by remember {
                mutableStateOf(if (returningFromSettings) settingsRowIndex else 0)
            }
            var menuHasFocus by remember { mutableStateOf(false) }
            val noneLabel = stringResource(R.string.default_player_last_used)
            val shownList: List<Pair<String, String>> =
                if (pickDefault) listOf(noneLabel to "") + data.toList() else if (onlyDefault) emptyList() else data.toList()
            val listSize = shownList.size
            val menuFocusRequester = remember { FocusRequester() }
            // The player list scrolls inside a limited height.
            val playerListState = rememberLazyListState()
            LaunchedEffect(playerIndex, playersMode) {
                if (playersMode && playerIndex in 0 until listSize) {
                    val info = playerListState.layoutInfo
                    val first = playerListState.firstVisibleItemIndex
                    val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
                    if (playerIndex < first) playerListState.scrollToItem(playerIndex)
                    else if (playerIndex > first + visible - 1) playerListState.scrollToItem(playerIndex - visible + 1)
                }
            }

            // Quick toggle for whether the active player is put into standby
            // when the app closes - same reasoning as the background-style
            // toggle above.
            var standbyOnExit by remember { mutableStateOf(AppConfig.current.standbyOnExit) }

            // The first requestFocus can happen before the menu is ready for
            // it, which left the menu open without key focus (D-pad did
            // nothing). Keep asking until the menu really has focus.
            // Close by itself after 15 s without a key press, like the other panels.
            var lastMenuKey by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    if (System.currentTimeMillis() - lastMenuKey > 15_000) {
                        expanded = false
                        return@LaunchedEffect
                    }
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    // Not while a tip or notice is open on top: both would fight for the focus
                    if (!menuHasFocus && !NavState.tipOpen) {
                        try { menuFocusRequester.requestFocus() } catch (_: Exception) { }
                    }
                    kotlinx.coroutines.delay(150)
                }
            }

            Column(
                modifier = Modifier
                    // The menu floats over the screen: it must not add its
                    // height to this Box, or the whole layout shifts down
                    // while the menu is open.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout(placeable.width, 0) { placeable.place(0, 0) }
                    }
                    .offset(y = 28.dp)
                    .width(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xE6222222))
                    .padding(vertical = 8.dp)
                    .focusRequester(menuFocusRequester)
                    .onFocusChanged { menuHasFocus = it.isFocused }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        lastMenuKey = System.currentTimeMillis()
                        if (playersMode) {
                            val listN = shownList.size
                            // The rows below the list: default player, only control default
                            val extras = if (pickDefault) 0 else (if (hasDefaultRow) 1 else 0) + (if (hasOnlyRow) 1 else 0)
                            val n = listN + extras
                            if (n == 0) { playersMode = false; pickDefault = false; return@onKeyEvent true }
                            val onPlayerRow = !pickDefault && playerIndex in 0 until listN
                            if (!onPlayerRow) powerFocus = false
                            if (onPlayerRow && !powerFocus && event.key == Key.DirectionRight) {
                                powerFocus = true
                                return@onKeyEvent true
                            }
                            if (onPlayerRow && powerFocus) {
                                when (event.key) {
                                    Key.DirectionLeft, Key.Back -> { powerFocus = false; return@onKeyEvent true }
                                    Key.DirectionRight -> return@onKeyEvent true
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        val mac = shownList[playerIndex].second
                                        val newOn = !(LMS.playerPower[mac] ?: true)
                                        CoroutineScope(Dispatchers.IO).launch { LMS.setPlayerPower(mac, newOn) }
                                        return@onKeyEvent true
                                    }
                                    else -> {}
                                }
                            }
                            when (event.key) {
                                Key.DirectionDown -> playerIndex = (playerIndex + 1) % n
                                Key.DirectionUp -> playerIndex = (playerIndex - 1 + n) % n
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    val itemValue = shownList.getOrNull(playerIndex)
                                    if (playerIndex >= listN) {
                                        if (playerIndex == listN) {
                                            pickDefault = true
                                            playerIndex = if (defaultMac.isBlank()) 0 else (data.indexOfFirst { it.second == defaultMac } + 1).coerceAtLeast(0)
                                        } else {
                                            val newValue = !AppConfig.current.onlyControlDefault
                                            Toast.makeText(contextForToast, "${contextForToast.getString(R.string.menu_only_control_default)}: ${contextForToast.getString(if (newValue) R.string.menu_standby_on else R.string.menu_standby_off)}", Toast.LENGTH_SHORT).show()
                                            // Effective at once (so the menu shows it), the save follows
                                            AppConfig.update(AppConfig.current.copy(onlyControlDefault = newValue))
                                            CoroutineScope(Dispatchers.IO).launch {
                                                val current = SettingsRepository.get(contextForToast)
                                                SettingsRepository.save(contextForToast, current.copy(onlyControlDefault = newValue))
                                            }
                                            // The menu stays open; the highlight stays on this row
                                            playerIndex = (if (newValue) 0 else data.size) + 1
                                        }
                                    } else if (itemValue != null && pickDefault) {
                                        val (name, mac) = itemValue
                                        Toast.makeText(contextForToast, "${contextForToast.getString(R.string.menu_default_player_prefix)}: ${name}", Toast.LENGTH_SHORT).show()
                                        val newName = if (mac.isEmpty()) "" else name
                                        val onlyNow = AppConfig.current.onlyControlDefault && mac.isNotEmpty()
                                        AppConfig.update(AppConfig.current.copy(defaultPlayerName = newName, defaultPlayerMac = mac, onlyControlDefault = onlyNow))
                                        CoroutineScope(Dispatchers.IO).launch {
                                            val current = SettingsRepository.get(contextForToast)
                                            SettingsRepository.save(contextForToast, current.copy(
                                                defaultPlayerName = newName,
                                                defaultPlayerMac = mac,
                                                onlyControlDefault = onlyNow,
                                            ))
                                        }
                                        // Back to the player list, on the "Default player" row
                                        pickDefault = false
                                        playerIndex = (if (onlyNow) 0 else data.size)
                                    } else if (itemValue != null) {
                                        Toast.makeText(contextForToast, " Connected to: ${itemValue.first}", Toast.LENGTH_SHORT).show()
                                        LMS.setPlayer(itemValue.first, itemValue.second)
                                        // The list stays open: Left or Back closes it
                                    }
                                }
                                Key.Back, Key.DirectionLeft, Key.DirectionRight ->
                                    if (pickDefault) { pickDefault = false; playerIndex = 0 } else playersMode = false
                                else -> {}
                            }
                            return@onKeyEvent true
                        }
                        if (highlightedIndex != playersRowIndex) menuPowerFocus = false
                        if (highlightedIndex == playersRowIndex && LMS.playerMac != "00:00:00:00:00:00") {
                            if (!menuPowerFocus && event.key == Key.DirectionRight) {
                                menuPowerFocus = true
                                return@onKeyEvent true
                            }
                            if (menuPowerFocus) {
                                when (event.key) {
                                    Key.DirectionLeft, Key.Back -> { menuPowerFocus = false; return@onKeyEvent true }
                                    Key.DirectionRight -> return@onKeyEvent true
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        val mac = LMS.playerMac
                                        val newOn = !(LMS.playerPower[mac] ?: true)
                                        CoroutineScope(Dispatchers.IO).launch { LMS.setPlayerPower(mac, newOn) }
                                        return@onKeyEvent true
                                    }
                                    Key.DirectionUp, Key.DirectionDown -> menuPowerFocus = false
                                    else -> {}
                                }
                            }
                        }
                        when (event.key) {
                            Key.DirectionDown -> {
                                highlightedIndex = (highlightedIndex + 1) % totalItems
                                true
                            }
                            Key.DirectionUp -> {
                                highlightedIndex = (highlightedIndex - 1 + totalItems) % totalItems
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (highlightedIndex == playersRowIndex) {
                                    playerIndex = data.indexOfFirst { it.second == LMS.playerMac }.coerceAtLeast(0)
                                    playersMode = true
                                } else if (highlightedIndex == standbyRowIndex) {
                                    val newValue = !standbyOnExit
                                    standbyOnExit = newValue
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val current = SettingsRepository.get(contextForToast)
                                        SettingsRepository.save(contextForToast, current.copy(standbyOnExit = newValue))
                                        AppConfig.load(contextForToast)
                                    }
                                    // Leave the menu open, same as the background toggle.
                                } else if (highlightedIndex == localRowIndex) {
                                    // Menu stays open: the label flips by itself once the player
                                    // (dis)connects (the list refreshes every 2 s meanwhile).
                                    if (localRunning) LocalPlayer.stop(contextForToast, localApp)
                                    else LocalPlayer.start(contextForToast, localApp)
                                } else {
                                    NavState.reopenMenu = true
                                    NavState.showSettings = true
                                    expanded = false
                                }
                                true
                            }
                            Key.Back, Key.DirectionLeft, Key.DirectionRight -> {
                                expanded = false
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                if (playersMode) {
                // Only when the list scrolls: a header showing where you are in it,
                // and a line below it, so it is clear the list scrolls on its own.
                val listScrolls = listSize > PLAYER_ROWS_VISIBLE
                if (listScrolls) {
                    val position = if (playerIndex in 0 until listSize) "${playerIndex + 1}/$listSize" else "$listSize"
                    Text(
                        text = "${stringResource(if (pickDefault) R.string.menu_default_player_prefix else R.string.menu_players_header)} ($position)",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(
                    state = playerListState,
                    modifier = Modifier
                        .heightIn(max = PLAYER_ROW_HEIGHT * PLAYER_ROWS_VISIBLE)
                        // Thin scrollbar at the right edge, only when the list is longer
                        // than what fits, so it is clear that there are more players.
                        .drawWithContent {
                            drawContent()
                            val info = playerListState.layoutInfo
                            val total = info.totalItemsCount
                            val shown = info.visibleItemsInfo.size
                            if (total > shown && shown > 0) {
                                val thumbH = size.height * shown / total
                                val thumbTop = size.height * playerListState.firstVisibleItemIndex / total
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.85f),
                                    topLeft = Offset(size.width - 6.dp.toPx(), thumbTop),
                                    size = Size(4.dp.toPx(), thumbH),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                ) {
                    itemsIndexed(shownList) { itemIndex, itemValue ->
                        val models = LMS.playerModels[itemValue.second]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PLAYER_ROW_HEIGHT)
                                .background(if (playerIndex == itemIndex) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = playerIcon(models?.first ?: "", models?.second ?: ""),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            // Name, and under it the next alarm of the player (display only, like Material)
                            val alarmAt = if (pickDefault) null else LMS.nextAlarm[itemValue.second]
                            Column(
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = if (pickDefault) (if (itemValue.second == defaultMac) "●  " else "○  ") + itemValue.first else itemValue.first,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = if (alarmAt != null) 14.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                                    lineHeight = if (alarmAt != null) 18.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                                )
                                if (alarmAt != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = AlarmIcon,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(end = 4.dp).size(12.dp)
                                        )
                                        Text(
                                            text = alarmLabel(alarmAt),
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            // Players that are synchronized with another one
                            if (LMS.syncGroups.any { itemValue.second in it }) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Synced",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Power state: bright when on, dim when off; Right moves the focus here, OK switches it
                            if (!pickDefault) {
                                val powerOn = LMS.playerPower[itemValue.second] ?: true
                                val iconFocused = powerFocus && playerIndex == itemIndex
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(28.dp)
                                        .background(if (iconFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = PowerIcon,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = if (powerOn) 0.95f else 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (listScrolls) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)))
                }
                if (!pickDefault) {
                    if (hasDefaultRow) {
                        MenuRow(
                            text = "${stringResource(R.string.menu_default_player_prefix)}: ${AppConfig.current.defaultPlayerName.ifBlank { noneLabel }}",
                            icon = null,
                            highlighted = playerIndex == listSize
                        )
                    }
                    if (hasOnlyRow) {
                        MenuRow(
                            text = "${stringResource(R.string.menu_only_control_default)}: ${stringResource(if (AppConfig.current.onlyControlDefault) R.string.menu_standby_on else R.string.menu_standby_off)}",
                            icon = null,
                            highlighted = playerIndex == listSize + 1,
                            maxLines = 2
                        )
                    }
                }
                return@Column
                }

                if (hasPlayersRow) {
                    val currentModel = LMS.playerModels[LMS.playerMac]
                    MenuRow(
                        text = stringResource(R.string.menu_players_header) +
                            if (LMS.playerName.isNotBlank()) ": ${LMS.playerName}" else "",
                        icon = playerIcon(currentModel?.first ?: "", currentModel?.second ?: ""),
                        highlighted = highlightedIndex == playersRowIndex,
                        trailing = {
                            // Power of the active player: Right moves the focus here, OK switches it
                            if (LMS.playerMac != "00:00:00:00:00:00") {
                                val powerOn = LMS.playerPower[LMS.playerMac] ?: true
                                val iconFocused = menuPowerFocus && highlightedIndex == playersRowIndex
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(if (iconFocused) Color.White.copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = PowerIcon,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = if (powerOn) 0.95f else 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )
                }

                val standbyLabel = if (standbyOnExit)
                    stringResource(R.string.menu_standby_on)
                else
                    stringResource(R.string.menu_standby_off)
                MenuRow(
                    text = "${stringResource(R.string.menu_standby_prefix)}: $standbyLabel",
                    icon = StandbyMenuIcon,
                    highlighted = highlightedIndex == standbyRowIndex
                )

                if (hasLocal) {
                    MenuRow(
                        text = stringResource(
                            if (localRunning) R.string.menu_local_player_stop_now else R.string.menu_local_player_start_now
                        ),
                        icon = null,
                        highlighted = highlightedIndex == localRowIndex
                    )
                }

                MenuRow(
                    text = stringResource(R.string.menu_settings),
                    icon = SettingsMenuIcon,
                    highlighted = highlightedIndex == settingsRowIndex
                )
            }
        }
    }
}

// Day, date and time of the alarm, in the language of the device: "ma 30 nov 06:00".
private fun alarmLabel(at: Long): String =
    java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))

// A row in the ⋮ menu: icon, then text; highlighted while the D-pad is on it.
@Composable
private fun MenuRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, highlighted: Boolean, trailing: (@Composable () -> Unit)? = null, maxLines: Int = 1) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) Color.White.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        } else {
            Box(Modifier.size(18.dp))
        }
        Text(
            text = text, color = Color.White, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            modifier = if (trailing != null) Modifier.weight(1f).padding(start = 10.dp) else Modifier.padding(start = 10.dp)
        )
        trailing?.invoke()
    }
}
