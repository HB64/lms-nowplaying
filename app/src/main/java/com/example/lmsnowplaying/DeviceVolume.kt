package com.example.lmsnowplaying

import android.content.Context
import android.media.AudioManager
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Volume of this device (Android music volume). Used instead of the player
 * volume in Lyrion when the selected player is the local player on this
 * device and the setting "Control device volume" is on.
 */
object DeviceVolume {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun audio(): AudioManager? =
        appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // True when the slider and the volume keys should control this device.
    // Not on a device whose volume is fixed (e.g. HDMI/CEC): Android then
    // ignores volume changes.
    fun active(): Boolean {
        if (AppConfig.current.lmsUrl.isBlank()) return false
        if (!AppConfig.current.localPlayerDeviceVolume) return false
        if (!LocalPlayer.selectedIsLocal()) return false
        val a = audio() ?: return false
        return try { !a.isVolumeFixed } catch (_: Exception) { false }
    }

    fun percent(): Int {
        val a = audio() ?: return 0
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (a.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()
    }

    fun isMuted(): Boolean =
        try { audio()?.isStreamMute(AudioManager.STREAM_MUSIC) == true } catch (_: Exception) { false }

    private fun change(deltaPercent: Int) {
        val a = audio() ?: return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        // The device volume has only `max` notches (about 7% each on 15): a step
        // is a fixed number of notches, at least one.
        val notches = (kotlin.math.abs(deltaPercent) * max / 100f).roundToInt().coerceAtLeast(1)
        val idx = if (deltaPercent > 0) cur + notches else cur - notches
        a.setStreamVolume(AudioManager.STREAM_MUSIC, idx.coerceIn(0, max), 0)
    }

    // Volume keys and slider: the device, or the player in Lyrion.
    fun adjust(deltaPercent: Int) {
        if (NavState.deviceVolumeMode) {
            try { change(deltaPercent) } catch (_: Exception) { }
            NavState.volume = percent()
            NavState.volumeShownAt = System.currentTimeMillis()
        } else {
            NavState.bumpVolume(deltaPercent)
            CoroutineScope(Dispatchers.IO).launch { LMS.changeVolume(deltaPercent) }
        }
    }

    fun mute() {
        if (NavState.deviceVolumeMode) {
            try { audio()?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0) } catch (_: Exception) { }
            NavState.volumeMuted = isMuted()
            NavState.volumeShownAt = System.currentTimeMillis()
        } else {
            NavState.toggleMuteLocal()
            CoroutineScope(Dispatchers.IO).launch { LMS.toggleMute() }
        }
    }
}
