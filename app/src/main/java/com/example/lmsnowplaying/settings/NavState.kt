package com.example.lmsnowplaying.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tiny app-wide navigation flag so the player dropdown (deep in the
 * composable tree) can ask MainActivity to show the Settings screen again,
 * without needing a NavHost for a single extra screen.
 */
object NavState {
    var showSettings by mutableStateOf(false)

    // Set once by MainActivity right before showing the player screen: true
    // when a default player was already auto-selected, so initial D-pad
    // focus should land on the play button instead of the options (⋮) menu
    // - there's no need to open the menu when a player is already picked.
    var focusPlayButtonOnStart by mutableStateOf(false)

    // Set once the initial focus (play button or ⋮ button) has been given, so
    // a later recomposition of the screen can't steal focus from an open menu.
    var startFocusDone = false

    // True while the ⋮ options menu is open, so the idle "return focus to
    // the play button" timer never pulls focus out from under it.
    var menuOpen by mutableStateOf(false)
    // A tip dialog is open: the screen underneath must not pull the focus away from it.
    var tipOpen by mutableStateOf(false)

    // When the ⋮ menu was last closed (ms). The ⋮ button can be recreated
    // right after (the screen switches layout when the artwork changes) and
    // then uses this to take its focus back.
    var menuClosedAt = 0L

    // Set when Settings was opened from the ⋮ menu, so the menu opens again
    // when Settings is left.
    var reopenMenu = false

    // True while the library browse screen (opened from the top-left button
    // on Now Playing) is showing.
    var showBrowse by mutableStateOf(false)

    // When something was last typed on the on-screen keyboard (ms); it does not
    // arrive as a remote key press, but it must keep the panels open.
    var lastTyped = 0L

    // The part of the browse panel that was open last, so it opens there again
    // (kept while the app runs).
    var lastBrowse = ""

    // The row last focused in each browse list (key = list name), so a list
    // opens on that row again after the panel was closed.
    val listFocus = HashMap<String, Int>()

    // True while the Queue panel (button at the bottom right) is showing.
    var showQueue by mutableStateOf(false)

    // The color of the progress bar (taken from the album cover), so other
    // panels can use the same color.
    var accentColor by mutableStateOf(Color.White)

    // Name of the mix plugin that is on right now, shown next to the "Mix"
    // title of the browse panel (empty when none is on).
    var activeMixLabel by mutableStateOf("")
    var activeMixIcon by mutableStateOf<ImageVector?>(null)

    // True after a long stretch without remote input: the focus ring is
    // hidden (focus itself stays put) until the next key press.
    var focusRingHidden by mutableStateOf(false)

    // Volume of the current player (0-100), whether it is muted, and whether
    // the player accepts volume changes at all (use_volume_control).
    var volume by mutableStateOf(0)
    var volumeMuted by mutableStateOf(false)
    var volumeAdjustable by mutableStateOf(false)
    // True when slider and volume keys control this device's own volume (see DeviceVolume).
    var deviceVolumeMode by mutableStateOf(false)

    // When the volume was last changed here (ms): the slider shows for a few
    // seconds after, and the polled value is ignored briefly so it doesn't jump back.
    var volumeShownAt by mutableStateOf(0L)

    fun bumpVolume(delta: Int) {
        volume = (volume + delta).coerceIn(0, 100)
        volumeShownAt = System.currentTimeMillis()
    }

    fun toggleMuteLocal() {
        volumeMuted = !volumeMuted
        volumeShownAt = System.currentTimeMillis()
    }
}
