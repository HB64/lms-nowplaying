package com.example.lmsnowplaying.composable

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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

@Preview
@Composable
fun MyUI() {

    val data = remember { mutableStateListOf<Pair<String, String>>() }
    // Redraw when the refreshed player list differs (see the loop below).
    @Suppress("UNUSED_VARIABLE")
    val playersVersion = LMS.playersVersion.collectAsState().value
    data.clear()
    data.addAll(LMS.players)

    // Keep the player list fresh: players come and go (e.g. a player app
    // that is still connecting). 10 s normally, 2 s after a player app start.
    LaunchedEffect(Unit) {
        while (true) {
            val fast = System.currentTimeMillis() < LocalPlayer.fastRefreshUntil
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
                .onFocusChanged { menuButtonHasFocus = it.isFocused }
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

            // Start on the player that is currently selected
            var highlightedIndex by remember {
                mutableStateOf(
                    if (returningFromSettings) listCount + 1 + (if (hasLocal) 1 else 0)
                    else if (onlyDefault) 0 else data.indexOfFirst { it.second == LMS.playerMac }.coerceAtLeast(0)
                )
            }
            var menuHasFocus by remember { mutableStateOf(false) }
            // +1 for the standby-on-exit row, +1 for the local player row
            // (if any), +1 for the "Settings" entry.
            val totalItems = listCount + 2 + (if (hasLocal) 1 else 0)
            val standbyRowIndex = listCount
            val localRowIndex = if (hasLocal) listCount + 1 else -1
            val settingsRowIndex = totalItems - 1
            val menuFocusRequester = remember { FocusRequester() }

            // Quick toggle for whether the active player is put into standby
            // when the app closes - same reasoning as the background-style
            // toggle above.
            var standbyOnExit by remember { mutableStateOf(AppConfig.current.standbyOnExit) }

            // The first requestFocus can happen before the menu is ready for
            // it, which left the menu open without key focus (D-pad did
            // nothing). Keep asking until the menu really has focus.
            LaunchedEffect(Unit) {
                while (true) {
                    if (!menuHasFocus) {
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
                                if (highlightedIndex < listCount) {
                                    val itemValue = data[highlightedIndex]
                                    Toast.makeText(contextForToast, " Connected to: ${itemValue.first}", Toast.LENGTH_SHORT).show()
                                    LMS.setPlayer(itemValue.first, itemValue.second)
                                    expanded = false
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
                (if (onlyDefault) emptyList() else data.toList()).forEachIndexed { itemIndex, itemValue ->
                    val models = LMS.playerModels[itemValue.second]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (highlightedIndex == itemIndex) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = playerIcon(models?.first ?: "", models?.second ?: ""),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = itemValue.first,
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                    }
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

// A row in the ⋮ menu: icon, then text; highlighted while the D-pad is on it.
@Composable
private fun MenuRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, highlighted: Boolean) {
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
        Text(text = text, color = Color.White, modifier = Modifier.padding(start = 10.dp))
    }
}
