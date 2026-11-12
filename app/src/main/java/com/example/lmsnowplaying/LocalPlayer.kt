package com.example.lmsnowplaying

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.SettingsRepository

/**
 * Starts/stops a software player app (SB Player or Squeezelite) on this
 * device, using the same intents as the LMS Material app.
 */
object LocalPlayer {
    private const val TAG = "LocalPlayer"
    private const val SB_PLAYER_PKG = "com.angrygoat.android.sbplayer"
    private const val SQUEEZELITE_PKG = "org.lyrion.squeezelite"

    // Until this time the player list is refreshed every 2 s instead of every
    // 10 s (a player app that was just started takes a while to connect).
    // Kept here, not in LMS: touching LMS before a server address is set crashes.
    @Volatile
    var fastRefreshUntil = 0L

    fun refreshPlayersSoon() {
        fastRefreshUntil = System.currentTimeMillis() + 60_000
    }

    fun start(context: Context, app: String): Boolean {
        refreshPlayersSoon()
        return control(context, app, true)
    }

    // IP addresses of this device (to tell its player from the same kind of
    // player on another device).
    private fun ownIps(): Set<String> = try {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .toSet()
    } catch (_: Exception) { emptySet() }

    private fun modelOf(app: String): String? = when (app) {
        SettingsRepository.LOCAL_PLAYER_SQUEEZELITE -> "squeezelite"
        SettingsRepository.LOCAL_PLAYER_SBPLAYER -> "squeezeplay"
        else -> null
    }

    // The connected player of the chosen local player app on this device, if any.
    // (Never touches LMS before a server address is set.)
    fun localPlayerMac(): String? {
        if (AppConfig.current.lmsUrl.isBlank()) return null
        val model = modelOf(AppConfig.current.localPlayerApp) ?: return null
        val own = ownIps()
        return LMS.players.firstOrNull {
            LMS.playerModels[it.second]?.first == model && LMS.playerIps[it.second] in own
        }?.second
    }

    // Is the player selected in the app the local player on this device?
    fun selectedIsLocal(): Boolean {
        if (AppConfig.current.lmsUrl.isBlank()) return false
        val model = modelOf(AppConfig.current.localPlayerApp) ?: return false
        val mac = LMS.playerMac
        return LMS.playerModels[mac]?.first == model && LMS.playerIps[mac] in ownIps()
    }

    // The Squeezelite player for which the fixed-volume choice was last sent.
    @Volatile
    private var fixedAppliedMac: String? = null

    fun resetFixedApply() {
        fixedAppliedMac = null
    }

    // Sends the chosen fixed/variable volume to Lyrion once per connection of
    // this device's Squeezelite (called after every refresh of the player list).
    suspend fun applyFixedVolume() {
        val wanted = AppConfig.current.localPlayerFixedVolume
        if (wanted < 0 || AppConfig.current.localPlayerApp != SettingsRepository.LOCAL_PLAYER_SQUEEZELITE) return
        val mac = localPlayerMac()
        if (mac == null) {
            fixedAppliedMac = null
            return
        }
        if (mac == fixedAppliedMac) return
        fixedAppliedMac = mac
        val variableNow = LMS.getDigitalVolumeControl(mac) ?: return
        val wantVariable = wanted == 0
        if (variableNow != wantVariable) LMS.setDigitalVolumeControl(mac, wantVariable)
    }

    fun stop(context: Context, app: String): Boolean {
        refreshPlayersSoon()
        return control(context, app, false)
    }

    private fun control(context: Context, app: String, start: Boolean): Boolean {
        val pkg = when (app) {
            SettingsRepository.LOCAL_PLAYER_SBPLAYER -> SB_PLAYER_PKG
            SettingsRepository.LOCAL_PLAYER_SQUEEZELITE -> SQUEEZELITE_PKG
            else -> return false
        }
        if (!isInstalled(context, pkg)) {
            Log.w(TAG, "$pkg is not installed")
            return false
        }
        return try {
            if (pkg == SB_PLAYER_PKG) {
                val intent = Intent()
                intent.setClassName(pkg, "$pkg.SBPlayerReceiver")
                intent.action = "$pkg." + if (start) "LAUNCH" else "EXIT"
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                context.sendBroadcast(intent)
            } else {
                val intent = Intent()
                intent.setClassName(pkg, "$pkg.PlayerService")
                if (start) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                    else context.startService(intent)
                } else {
                    context.stopService(intent)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ${if (start) "start" else "stop"} $pkg: ${e.message}")
            false
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
}
