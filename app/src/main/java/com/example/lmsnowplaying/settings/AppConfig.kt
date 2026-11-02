package com.example.lmsnowplaying.settings

import android.content.Context

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

    suspend fun load(context: Context): SettingsRepository.Settings {
        current = SettingsRepository.get(context)
        return current
    }
}
