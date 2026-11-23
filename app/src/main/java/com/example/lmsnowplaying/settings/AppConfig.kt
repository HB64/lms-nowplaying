package com.example.lmsnowplaying.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Effective, resolved configuration used throughout the app. Prefers the
 * runtime settings entered in SettingsScreen (no build-time defaults, so
 * no server details can end up in a published APK). Must be populated once via load() before RetrofitClient
 * or JellyfinApi are touched (MainActivity does this before showing the
 * "ready" screen).
 */
object AppConfig {

    var current: SettingsRepository.Settings = SettingsRepository.Settings()
        private set

    // Makes new settings effective at once (the save to disk follows separately).
    fun update(settings: SettingsRepository.Settings) {
        current = settings
    }

    // The player chosen last; used at start when no default player is set. Kept apart from the settings
    // so that saving the settings never overwrites it.
    var lastPlayerName = ""
        private set
    var lastPlayerMac = ""
        private set
    private var appContext: Context? = null

    fun rememberPlayer(name: String, mac: String) {
        if (name == lastPlayerName && mac == lastPlayerMac) return
        lastPlayerName = name
        lastPlayerMac = mac
        val context = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch { SettingsRepository.saveLastPlayer(context, name, mac) }
    }

    suspend fun load(context: Context): SettingsRepository.Settings {
        current = SettingsRepository.get(context)
        appContext = context.applicationContext
        val last = SettingsRepository.getLastPlayer(context)
        lastPlayerName = last.first
        lastPlayerMac = last.second
        return current
    }
}
