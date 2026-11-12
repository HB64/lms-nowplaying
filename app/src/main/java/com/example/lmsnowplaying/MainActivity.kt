package com.example.lmsnowplaying

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.lmsnowplaying.composable.PluginTipDialog
import com.example.lmsnowplaying.network.jellyfin.JellyfinApi
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import com.example.lmsnowplaying.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {

    // Volume keys control the selected player (step from settings, mute on
    // the mute key). Without a selected player they keep their normal job.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Not before a server address is set: touching LMS then crashes.
        if (AppConfig.current.lmsUrl.isNotBlank() && LMS.playerMac != "00:00:00:00:00:00" && NavState.volumeAdjustable) {
            val step = AppConfig.current.volumeStep
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    DeviceVolume.adjust(step)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    DeviceVolume.adjust(-step)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    if (event.repeatCount == 0) DeviceVolume.mute()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        DeviceVolume.init(applicationContext)


        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Blocking on purpose: this is a deliberate app-exit
                    // action, and we need the standby command to actually
                    // reach the player (e.g. a Boom) before the process
                    // dies - a fire-and-forget coroutine could get killed
                    // mid-request by the exitProcess() call right after.
                    if (AppConfig.current.standbyOnExit && AppConfig.current.lmsUrl.isNotBlank()) {
                        runBlocking(Dispatchers.IO) {
                            LMS.playPause(false)
                            LMS.power(false)
                        }
                    }
                    // The player app on this device stops together with LMSNP
                    if (AppConfig.current.localPlayerStopOnExit) LocalPlayer.stop(this@MainActivity, AppConfig.current.localPlayerApp)
                    // Remove the task first and only then end the process: killing the
                    // process while the activity is still in the task makes Android
                    // restart the app (it then comes back with "No Player").
                    this@MainActivity.finishAndRemoveTask()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 500)
                }
            }
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // "loading" while checking stored settings, then "setup" or "ready"
            var screenState by remember { mutableStateOf("loading") }

            LaunchedEffect(Unit) {
                val resolved = AppConfig.load(context)
                if (resolved.localPlayerAutoStart) LocalPlayer.start(context, resolved.localPlayerApp)
                screenState = if (resolved.lmsUrl.isNotBlank()) "ready" else "setup"
            }

            when (screenState) {
                "setup" -> {
                    SettingsScreen(onSaved = {
                        scope.launch {
                            AppConfig.load(context)
                            screenState = "ready"
                        }
                    })
                }
                "ready" -> {
                    if (NavState.showSettings) {
                        // Reached via the options (⋮) menu on the Now Playing
                        // screen, so a Back/Exit press here should return to
                        // Now Playing rather than closing the app - without
                        // this, it falls through to the top-level back
                        // handler above, which exits the whole app.
                        BackHandler(enabled = true) {
                            NavState.showSettings = false
                        }
                        SettingsScreen(
                            initial = AppConfig.current,
                            onSaved = {
                                scope.launch {
                                    AppConfig.load(context)
                                    NavState.showSettings = false
                                }
                            },
                            onCancel = { NavState.showSettings = false }
                        )
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            LMS.getPlayers()
                            // Auto-select the remembered default player on a
                            // fresh start (e.g. launched via a Harmony
                            // activity), so no manual pick is needed.
                            if (LMS.playerMac == "00:00:00:00:00:00") {
                                val defaultMac = AppConfig.current.defaultPlayerMac
                                val defaultName = AppConfig.current.defaultPlayerName
                                // Only when it is connected: an offline default player
                                // (e.g. a player app that is stopped) stays unselected
                                // until it connects (LMS.getPlayers then picks it).
                                if (defaultMac.isNotBlank() && LMS.players.any { it.second == defaultMac }) {
                                    LMS.setPlayer(defaultName, defaultMac)
                                }
                            }
                        }

                        JellyfinApi.SetAPIToken(context)

                        // If a default player was already remembered, the
                        // player screen will auto-connect without any input
                        // needed - so start focus on the play button rather
                        // than the options (⋮) menu.
                        NavState.focusPlayButtonOnStart = AppConfig.current.defaultPlayerMac.isNotBlank()
                        NavState.startFocusDone = false

                        var showPluginTip by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            if (!SettingsRepository.hasShownPluginTip(context)) {
                                showPluginTip = true
                            }
                        }

                        // Volume slider tip: once, the first time a player with
                        // adjustable volume is shown (after the plugin tip).
                        var showVolumeTip by remember { mutableStateOf(false) }
                        LaunchedEffect(NavState.volumeAdjustable, showPluginTip) {
                            if (NavState.volumeAdjustable && !showPluginTip && !showVolumeTip &&
                                !SettingsRepository.hasShownVolumeTip(context)) {
                                showVolumeTip = true
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            SecondaryScreen()
                            if (showVolumeTip) {
                                PluginTipDialog(
                                    onDismiss = {
                                        showVolumeTip = false
                                        scope.launch { SettingsRepository.markVolumeTipShown(context) }
                                    },
                                    titleRes = R.string.volume_tip_title,
                                    bodyRes = R.string.volume_tip_body,
                                )
                            }
                            if (showPluginTip) {
                                PluginTipDialog(onDismiss = {
                                    showPluginTip = false
                                    scope.launch { SettingsRepository.markPluginTipShown(context) }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    // Covers leaving the app *without* an explicit Back press - e.g. a
    // Harmony activity's standby button turning off the screen/device
    // directly, which never goes through onBackPressedDispatcher at all.
    // Blocking on purpose, same reasoning as the Back-press handler: a
    // fire-and-forget coroutine risks getting cut off if the system
    // proceeds to sleep/suspend the process right after onStop() returns,
    // which is likely why it was unreliable.
    // Old Compose versions can crash with "The ACTION_HOVER_EXIT event was
    // not cleared" when a mouse/pointer hover reaches them. This is a TV
    // app driven by a D-pad, so hover events are simply swallowed.
    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_HOVER_ENTER,
            android.view.MotionEvent.ACTION_HOVER_MOVE,
            android.view.MotionEvent.ACTION_HOVER_EXIT -> return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    // The first start is handled when the settings are loaded (onCreate).
    // Every later return to LMSNP starts the player app again (auto-start on):
    // it may have been stopped by us, or have quit itself on the standby
    // command. Starting one that is already running does no harm.
    private var firstStart = true

    override fun onStart() {
        super.onStart()
        if (firstStart) {
            firstStart = false
            return
        }
        val cfg = AppConfig.current
        if (cfg.localPlayerAutoStart) LocalPlayer.start(this, cfg.localPlayerApp)
    }

    override fun onStop() {
        super.onStop()
        val app = AppConfig.current.localPlayerApp
        if (app != SettingsRepository.LOCAL_PLAYER_NONE && AppConfig.current.localPlayerStopOnExit) {
            LocalPlayer.stop(this, app)
        }
        if (AppConfig.current.standbyOnExit && AppConfig.current.lmsUrl.isNotBlank()) {
            runBlocking(Dispatchers.IO) {
                LMS.playPause(false)
                LMS.power(false)
            }
        }
    }

}
