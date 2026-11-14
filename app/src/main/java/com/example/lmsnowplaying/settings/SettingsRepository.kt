package com.example.lmsnowplaying.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lyrion_settings")

/**
 * Runtime-configurable settings, replacing the old compile-time BuildConfig values
 * (local.properties). Lets a plain APK be configured from within the app instead
 * of requiring a rebuild via Android Studio.
 */
object SettingsRepository {

    private val LMS_URL = stringPreferencesKey("lms_url")
    private val LMS_USERNAME = stringPreferencesKey("lms_username")
    private val LMS_PASSWORD = stringPreferencesKey("lms_password")
    private val JELLYFIN_URL = stringPreferencesKey("jellyfin_url")
    private val JELLYFIN_USERNAME = stringPreferencesKey("jellyfin_username")
    private val JELLYFIN_PASSWORD = stringPreferencesKey("jellyfin_password")
    private val JELLYFIN_API_KEY = stringPreferencesKey("jellyfin_api_key")
    private val DEFAULT_PLAYER_NAME = stringPreferencesKey("default_player_name")
    private val DEFAULT_PLAYER_MAC = stringPreferencesKey("default_player_mac")
    private val BACKGROUND_STYLE = stringPreferencesKey("background_style")
    private val STANDBY_ON_EXIT = booleanPreferencesKey("standby_on_exit")
    private val SMART_REPLACE_PROVIDER = stringPreferencesKey("smart_replace_provider")
    private val VOLUME_STEP = intPreferencesKey("volume_step")
    private val SEEK_STEP = intPreferencesKey("seek_step")
    private val LOCAL_PLAYER_APP = stringPreferencesKey("local_player_app")
    private val LOCAL_PLAYER_STOP_ON_EXIT = booleanPreferencesKey("local_player_stop_on_exit")
    private val LOCAL_PLAYER_AUTOSTART = booleanPreferencesKey("local_player_autostart")
    private val SHOW_VOLUME_SLIDER = booleanPreferencesKey("show_volume_slider")
    private val SHOW_TRACK_INFO_BUTTON = booleanPreferencesKey("show_track_info_button")
    private val SHOW_TRACK_INFO_LINE = booleanPreferencesKey("show_track_info_line")
    private val ONLY_CONTROL_DEFAULT = booleanPreferencesKey("only_control_default")
    private val HIDDEN_PLAYERS = stringSetPreferencesKey("hidden_players")
    private val HIDDEN_MY_MUSIC = stringSetPreferencesKey("hidden_my_music")
    private val LOCAL_PLAYER_DEVICE_VOLUME = booleanPreferencesKey("local_player_device_volume")
    private val LOCAL_PLAYER_FIXED_VOLUME = intPreferencesKey("local_player_fixed_volume")

    // Valid values for Settings.backgroundStyle.
    const val BACKGROUND_STYLE_ARTIST = "artist"
    const val BACKGROUND_STYLE_ALBUM = "album"
    const val BACKGROUND_STYLE_STARFIELD = "starfield"
    const val BACKGROUND_STYLE_ARTIST_GRAYSCALE = "artist_grayscale"

    // Default for Settings.smartReplaceProvider. The replace button now
    // follows whichever mix plugin is active; the setting is only kept so
    // existing stored preferences stay readable.
    const val SMART_REPLACE_NONE = "none"

    // Valid values for Settings.localPlayerApp.
    const val LOCAL_PLAYER_NONE = "none"
    // My Music items that are off until the user switches them on (like Material).
    val DEFAULT_HIDDEN_MY_MUSIC: Set<String> = setOf("albumartists", "composers", "years")
    const val LOCAL_PLAYER_SBPLAYER = "sbplayer"
    const val LOCAL_PLAYER_SQUEEZELITE = "squeezelite"

    data class Settings(
        val lmsUrl: String = "",
        val lmsUsername: String = "",
        val lmsPassword: String = "",
        val jellyfinUrl: String = "",
        val jellyfinUsername: String = "",
        val jellyfinPassword: String = "",
        val jellyfinApiKey: String = "",
        // Remembers the last-selected player so it's auto-selected again on
        // the next app start, instead of requiring a manual pick every time
        // (useful when the app is launched fresh via e.g. a Harmony activity).
        val defaultPlayerName: String = "",
        val defaultPlayerMac: String = "",
        // Whether the Now Playing backdrop should try an artist photo
        // (BACKGROUND_STYLE_ARTIST, the default) or always just use the
        // blurred album cover (BACKGROUND_STYLE_ALBUM) - some artist photos
        // are ugly or, for multi-artist tracks, end up being the album cover
        // anyway (Lyrion/Jellyfin fall back to that when no real photo is
        // found), which can get repetitive.
        val backgroundStyle: String = BACKGROUND_STYLE_ARTIST,
        // Whether the active player should be put into standby (paused +
        // powered off) when the app closes or is left. Defaults to on,
        // matching the app's original behavior.
        val standbyOnExit: Boolean = true,
        // Which plugin the "replace next track" button prefers, since it
        // can't reliably tell on its own whether SugarCube or RandomFlow is
        // the one installed (or neither). Defaults to SugarCube, matching
        // the app's original behavior.
        val smartReplaceProvider: String = SMART_REPLACE_NONE,
        // Percent per press of the volume keys (1, 2, 5 or 10).
        val volumeStep: Int = 5,
        // Seconds per press when seeking with the progress bar (1, 2, 5, 10 or 30).
        val seekStep: Int = 10,
        // Software player app on this device (SB Player / Squeezelite) and
        // whether to start it when LMSNP starts.
        val localPlayerApp: String = LOCAL_PLAYER_NONE,
        val localPlayerAutoStart: Boolean = false,
        // Stop the player app when LMSNP is closed or left.
        val localPlayerStopOnExit: Boolean = false,
        // Show the volume slider (only applies when SB Player is the local player).
        val showVolumeSlider: Boolean = true,
        // The ⓘ button next to the title (technical info of the track); off by default.
        val showTrackInfoButton: Boolean = false,
        // One line with the main technical data under the album line; off by default.
        val showTrackInfoLine: Boolean = false,
        val onlyControlDefault: Boolean = false,
        // MAC addresses of players hidden from the ⋮ menu list.
        val hiddenPlayers: Set<String> = emptySet(),
        // Items of the Browse > My Music list that are switched off (artists, genres, folders, favorites, playlists).
        val hiddenMyMusic: Set<String> = DEFAULT_HIDDEN_MY_MUSIC,
        // Slider and volume keys control this device's volume when the selected player is the local player.
        val localPlayerDeviceVolume: Boolean = false,
        // Squeezelite in Lyrion: 1 = fixed at 100%, 0 = variable (default). Sent whenever the player connects.
        val localPlayerFixedVolume: Int = 0,
    )

    fun flow(context: Context): Flow<Settings> =
        context.dataStore.data.map { prefs ->
            Settings(
                lmsUrl = prefs[LMS_URL] ?: "",
                lmsUsername = prefs[LMS_USERNAME] ?: "",
                lmsPassword = prefs[LMS_PASSWORD] ?: "",
                jellyfinUrl = prefs[JELLYFIN_URL] ?: "",
                jellyfinUsername = prefs[JELLYFIN_USERNAME] ?: "",
                jellyfinPassword = prefs[JELLYFIN_PASSWORD] ?: "",
                jellyfinApiKey = prefs[JELLYFIN_API_KEY] ?: "",
                defaultPlayerName = prefs[DEFAULT_PLAYER_NAME] ?: "",
                defaultPlayerMac = prefs[DEFAULT_PLAYER_MAC] ?: "",
                backgroundStyle = prefs[BACKGROUND_STYLE] ?: BACKGROUND_STYLE_ARTIST,
                standbyOnExit = prefs[STANDBY_ON_EXIT] ?: true,
                smartReplaceProvider = prefs[SMART_REPLACE_PROVIDER] ?: SMART_REPLACE_NONE,
                volumeStep = prefs[VOLUME_STEP] ?: 5,
                seekStep = prefs[SEEK_STEP] ?: 10,
                localPlayerApp = prefs[LOCAL_PLAYER_APP] ?: LOCAL_PLAYER_NONE,
                localPlayerAutoStart = prefs[LOCAL_PLAYER_AUTOSTART] ?: false,
                localPlayerStopOnExit = prefs[LOCAL_PLAYER_STOP_ON_EXIT] ?: false,
                showVolumeSlider = prefs[SHOW_VOLUME_SLIDER] ?: true,
                showTrackInfoButton = prefs[SHOW_TRACK_INFO_BUTTON] ?: false,
                showTrackInfoLine = prefs[SHOW_TRACK_INFO_LINE] ?: false,
                onlyControlDefault = prefs[ONLY_CONTROL_DEFAULT] ?: false,
                hiddenPlayers = prefs[HIDDEN_PLAYERS] ?: emptySet(),
                hiddenMyMusic = prefs[HIDDEN_MY_MUSIC] ?: DEFAULT_HIDDEN_MY_MUSIC,
                localPlayerDeviceVolume = prefs[LOCAL_PLAYER_DEVICE_VOLUME] ?: false,
                localPlayerFixedVolume = prefs[LOCAL_PLAYER_FIXED_VOLUME] ?: 0,
            )
        }

    suspend fun get(context: Context): Settings = flow(context).first()

    suspend fun save(context: Context, settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[LMS_URL] = settings.lmsUrl
            prefs[LMS_USERNAME] = settings.lmsUsername
            prefs[LMS_PASSWORD] = settings.lmsPassword
            prefs[JELLYFIN_URL] = settings.jellyfinUrl
            prefs[JELLYFIN_USERNAME] = settings.jellyfinUsername
            prefs[JELLYFIN_PASSWORD] = settings.jellyfinPassword
            prefs[JELLYFIN_API_KEY] = settings.jellyfinApiKey
            prefs[DEFAULT_PLAYER_NAME] = settings.defaultPlayerName
            prefs[DEFAULT_PLAYER_MAC] = settings.defaultPlayerMac
            prefs[BACKGROUND_STYLE] = settings.backgroundStyle
            prefs[STANDBY_ON_EXIT] = settings.standbyOnExit
            prefs[SMART_REPLACE_PROVIDER] = settings.smartReplaceProvider
            prefs[VOLUME_STEP] = settings.volumeStep
            prefs[SEEK_STEP] = settings.seekStep
            prefs[LOCAL_PLAYER_APP] = settings.localPlayerApp
            prefs[LOCAL_PLAYER_AUTOSTART] = settings.localPlayerAutoStart
            prefs[LOCAL_PLAYER_STOP_ON_EXIT] = settings.localPlayerStopOnExit
            prefs[SHOW_VOLUME_SLIDER] = settings.showVolumeSlider
            prefs[SHOW_TRACK_INFO_BUTTON] = settings.showTrackInfoButton
            prefs[SHOW_TRACK_INFO_LINE] = settings.showTrackInfoLine
            prefs[ONLY_CONTROL_DEFAULT] = settings.onlyControlDefault
            prefs[HIDDEN_PLAYERS] = settings.hiddenPlayers
            prefs[HIDDEN_MY_MUSIC] = settings.hiddenMyMusic
            prefs[LOCAL_PLAYER_DEVICE_VOLUME] = settings.localPlayerDeviceVolume
            prefs[LOCAL_PLAYER_FIXED_VOLUME] = settings.localPlayerFixedVolume
        }
    }

    // Updates just the remembered default player, without touching (or
    // needing to know) the rest of the settings.
    suspend fun saveDefaultPlayer(context: Context, name: String, mac: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_PLAYER_NAME] = name
            prefs[DEFAULT_PLAYER_MAC] = mac
        }
    }

    // One-time "this app works best with SugarCube + Don't Stop The Music"
    // tip, shown once on first launch and then never again.
    private val PLUGIN_TIP_SHOWN = booleanPreferencesKey("plugin_tip_shown")

    suspend fun hasShownPluginTip(context: Context): Boolean =
        context.dataStore.data.map { it[PLUGIN_TIP_SHOWN] ?: false }.first()

    suspend fun markPluginTipShown(context: Context) {
        context.dataStore.edit { prefs -> prefs[PLUGIN_TIP_SHOWN] = true }
    }

    // One-time tip about the volume slider, shown the first time a player
    // with adjustable volume is on screen.
    private val VOLUME_TIP_SHOWN = booleanPreferencesKey("volume_tip_shown")

    suspend fun hasShownVolumeTip(context: Context): Boolean =
        context.dataStore.data.map { it[VOLUME_TIP_SHOWN] ?: false }.first()

    suspend fun markVolumeTipShown(context: Context) {
        context.dataStore.edit { prefs -> prefs[VOLUME_TIP_SHOWN] = true }
    }
}
