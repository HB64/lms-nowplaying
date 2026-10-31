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

    // When the ⋮ menu was last closed (ms). The ⋮ button can be recreated
    // right after (the screen switches layout when the artwork changes) and
    // then uses this to take its focus back.
    var menuClosedAt = 0L

    // True while the library browse screen (opened from the top-left button
    // on Now Playing) is showing.
    var showBrowse by mutableStateOf(false)

    // When something was last typed on the on-screen keyboard (ms); it does not
    // arrive as a remote key press, but it must keep the panels open.
    var lastTyped = 0L

    // The part of the browse panel that was open last, so it opens there again
    // (kept while the app runs).
    var lastBrowse = ""

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
}
