package com.example.lmsnowplaying.network.logitechmediaserver

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.io.IOException

private const val TAG = "LMS"

// One entry in the upcoming playlist queue, used for the "next track"
// preview and the pick-a-different-track list.
data class QueueTrack(
    val absoluteIndex: Int,
    val title: String,
    val artist: String,
    val coverId: String,
    // Cover address of streaming tracks (they have no local cover id)
    val artworkUrl: String = "",
)

// The whole play queue and the position of the current track in it.
data class QueueList(
    val tracks: List<QueueTrack> = emptyList(),
    val currentIndex: Int = 0,
)

// A generic id/name pair, used for browsing artists and albums.
data class BrowseItem(
    val id: String,
    val name: String,
    // For albums: id of a track whose cover represents the album (used for
    // thumbnails); empty when unknown.
    val artworkId: String = "",
    // Second line in search results (artist); empty when unknown.
    val subtitle: String = "",
    // For albums: release year; empty when unknown.
    val year: String = "",
    // For playable plugin items (album, track) that also have items below them
    val openable: Boolean = false,
    // For albums of a streaming service: its name in lower case ("qobuz", "deezer"); empty for local albums.
    val service: String = "",
)

// The streaming service of an album, from the prefix of its external id ("qobuz:album:123" -> "qobuz").
fun serviceOfExtId(extId: String): String {
    val parts = extId.split(':')
    if (parts.size < 3 || parts[1] != "album") return ""
    val prefix = parts[0].lowercase()
    return if (prefix.length in 3..20 && prefix.all { it.isLetter() }) prefix else ""
}

// Repeat / shuffle / Don't Stop The Music state of a player.
// repeat: 0 off, 1 song, 2 queue; shuffle: 0 off, 1 songs, 2 albums;
// dstmProvider: empty when Don't Stop The Music is off.
data class PlayModes(
    val repeat: Int,
    val shuffle: Int,
    val dstmProvider: String,
)

// Plugins that can keep the queue filled ("Auto Mix"). Only one of them
// may be active per player; LMSNP enforces that when switching one on.
enum class MixPlugin(val label: String) {
    SUGARCUBE("SugarCube"),
    MIPSTER("MIPster"),
    RANDOMFLOW("Random Flow"),
    RANDOMPLAY("Random Mix"),
    DSTM("DSTM"),
}

enum class MixAction { START_MIX, START_BATCH, ADD_BATCH }

// Which of those plugins are installed (available) and which one is on.
data class MixState(
    val available: Set<MixPlugin> = emptySet(),
    val on: Set<MixPlugin> = emptySet(),
)

// An entry in the music folder tree: a sub folder or a track.
data class FolderEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
)

// An entry in the Lyrion favorites: a favorite (station, track...) or a folder of favorites.
data class FavoriteEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val icon: String = "",
    val isSearch: Boolean = false,
    // Plays by itself (a track, an album); may still have items below it
    val isAudio: Boolean = false,
    // Address of a favorite (empty for other entries)
    val url: String = "",
)

// An entry of Lyrion's Radio menu: the plugin's command and its display name.
data class RadioEntry(
    val cmd: String,
    val name: String,
    val isSearch: Boolean = false,
    // Address of the plugin's icon on the server (apps); empty when there is none
    val icon: String = "",
)

// Result of a library search: matching artists, albums and tracks.
data class SearchResults(
    val artists: List<BrowseItem> = emptyList(),
    val albums: List<BrowseItem> = emptyList(),
    val tracks: List<BrowseTrack> = emptyList(),
)

// A track found while browsing an album, used to pick a replacement for
// the upcoming ("next") track in the queue.
data class BrowseTrack(
    val id: String,
    val title: String,
    val trackNum: Int,
    // Cover id for thumbnails; empty when unknown.
    val coverId: String = "",
    // Second line in search results ("artist · album"); empty when unknown.
    val subtitle: String = "",
)

// A setting shown as a slider where every value can be reached (min..max, in
// steps of step), and the command that sets a value. fastStep is the step
// while a key is held down; with noLimitAtMax the top position means "no limit".
data class SliderSpec(
    val min: Int,
    val max: Int,
    val value: Int,
    val command: (Int) -> List<Any>,
    val fastStep: Int = 5,
    val noLimitAtMax: Boolean = false,
    val step: Int = 1,
)

// One setting of a plugin's Jive menu: its choices, the command that selects
// each choice, and which choice is current. Settings with a slider range
// carry a SliderSpec as well. A setting whose choices come from a list the
// plugin builds on request (Filter, Recipe, Mood) has the command that asks for
// that list in listCommand, and is filled in by loadPluginList.
data class PluginSetting(
    val text: String,
    val choices: List<String>,
    val commands: List<List<Any>>,
    val selected: Int,
    val slider: SliderSpec? = null,
    val listCommand: List<Any>? = null,
) {
    val display: String get() {
        val current = choices.getOrNull(selected)
        return if (current == null) text else "$text: $current"
    }
}

object LMS{

    // A getter, so a changed server address in Settings is used at once
    private val lmsApi get() = ApiClient.apiService

    var isPlaying: Boolean = false
    @Volatile var volume: Int = 0
    @Volatile var volumeMuted: Boolean = false
    @Volatile var volumeControl: Boolean = false
        private set

    var elapsedTime: Double = 0.0
        private set

    var trackDuration: Double = 0.0
        private set

    var playerMac: String = "00:00:00:00:00:00"
        private set

    var playerName: String = "No Player"
        private set

    // Observable copy of playerName: the screen collects this so the name
    // updates the moment another player is picked (also when that player has
    // nothing queued and no other value on screen changes).
    val playerNameFlow = kotlinx.coroutines.flow.MutableStateFlow("No Player")

    // Server id of the track now playing (set by update(); not a number for streams).
    @Volatile
    var currentTrackId: String = ""

    // One-line technical info of the stream as the player receives it, in Material's order
    // (bitrate, bit depth, sample rate, replay gain, type). Set by update(), shown by TrackInfoLine.
    val techLineFlow = kotlinx.coroutines.flow.MutableStateFlow("")
    private var maxBitrateKey = ""
    private var maxBitrate = 0

    @Volatile
    var players: MutableList<Pair<String, String>> = arrayListOf()
    // Players that are synchronized with each other: one set of MACs per group (2 or more).
    @Volatile
    var syncGroups: List<Set<String>> = emptyList()

    fun inSameSyncGroup(a: String, b: String): Boolean =
        a != b && syncGroups.any { a in it && b in it }

    // The groups as the server reports them ("syncgroups ?": sync_members = comma separated MACs).
    private suspend fun fetchSyncGroups(): List<Set<String>> {
        return safeCall("syncgroups", syncGroups) {
            val res = rpcPlayer("", listOf("syncgroups", "?"))
            val rawGroups = mutableListOf<String>()
            val loop = res.optJSONArray("syncgroups_loop")
            if (loop != null) {
                for (i in 0 until loop.length()) rawGroups.add(loop.getJSONObject(i).optString("sync_members", ""))
            } else if (res.has("sync_members")) {
                rawGroups.add(res.optString("sync_members", ""))
            }
            rawGroups
                .map { g -> g.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
                .filter { it.size >= 2 }
        }
    }

    // Adds [otherMac] to the group of [currentMac], or takes it out again when it is
    // already in that group ("sync <mac>" on the existing member joins; "sync -" leaves).
    suspend fun toggleSync(currentMac: String, otherMac: String) {
        safeCall("toggleSync", Unit) {
            if (inSameSyncGroup(currentMac, otherMac)) rpcPlayer(otherMac, listOf("sync", "-"))
            else rpcPlayer(currentMac, listOf("sync", otherMac))
            Unit
        }
    }

    // Per-player sync settings, same order as the sync-settings panel (all values: 0/1 or milliseconds).
    val syncPrefNames = listOf("syncVolume", "syncPower", "maintainSync", "startDelay", "playDelay", "minSyncAdjust", "packetLatency")

    suspend fun getSyncPrefs(mac: String): Map<String, Int> =
        safeCall("getSyncPrefs", emptyMap()) {
            syncPrefNames.associateWith { playerPref(mac, it).toIntOrNull() ?: 0 }
        }

    suspend fun setSyncPref(mac: String, name: String, value: Int) {
        safeCall("setSyncPref", Unit) {
            rpcPlayer(mac, listOf("playerpref", name, value.toString()))
            Unit
        }
    }

    // mac -> (model, model name) as reported by the server, for the player icons
    var playerModels: Map<String, Pair<String, String>> = emptyMap()
        private set

    // mac -> IP address (without port) the server sees the player on
    var playerIps: Map<String, String> = emptyMap()
    // Power state per player (true = on), as the server reports it in the "players" list
    var playerPower: Map<String, Boolean> = emptyMap()
        private set

    // Bumped whenever the visible player list changes, so the menu can redraw.
    val playersVersion = kotlinx.coroutines.flow.MutableStateFlow(0)

    // A player that just went to standby, or whose app just closed, is
    // often briefly unreachable (connection refused/timeout/EOF while it
    // wakes back up). None of our network calls should be able to crash
    // the whole app over that - we log it and let the caller treat it as
    // "didn't work this time" instead.
    private suspend fun <T> safeCall(what: String, default: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "$what failed (network): ${e.message}")
            default
        } catch (e: JSONException) {
            Log.w(TAG, "$what failed (bad response): ${e.message}")
            default
        } catch (e: Exception) {
            Log.w(TAG, "$what failed (${e.javaClass.simpleName}): ${e.message}")
            default
        }
    }

    // Runs one plugin check; any failure is logged and only affects that check.
    // Returns false when the check failed.
    private suspend fun checkPlugin(what: String, block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what check failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    suspend fun getPlayers() {
        safeCall("getPlayers", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"players\", \"0\", \"50\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.getPlayer(requestBody)

            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")

            val loop = getObject.getJSONArray("players_loop")
            // Build the new list first and swap it in one go: clearing the
            // shared list in place let the menu (main thread) see an empty
            // or half-filled list while this runs on the IO thread.
            val fresh: MutableList<Pair<String, String>> = arrayListOf()
            val freshModels = HashMap<String, Pair<String, String>>()
            val freshIps = HashMap<String, String>()
            val freshPower = HashMap<String, Boolean>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                val name = item.getString("name")
                val player_mac = item.getString("playerid")
                freshModels[player_mac] = Pair(item.optString("model", ""), item.optString("modelname", ""))
                freshIps[player_mac] = item.optString("ip", "").substringBeforeLast(':')
                freshPower[player_mac] = item.optInt("power", 1) == 1
                // The server also lists players that are offline (e.g. a player
                // app that is not running). Hide those.
                if (item.optInt("connected", 1) == 1) fresh.add(Pair(name, player_mac))
            }
            val freshGroups = fetchSyncGroups()
            val changed = fresh != players || freshGroups != syncGroups || freshPower != playerPower
            syncGroups = freshGroups
            players = fresh
            playerModels = freshModels
            playerIps = freshIps
            playerPower = freshPower
            if (changed) playersVersion.value = playersVersion.value + 1

            // The selected player went offline (e.g. a player app was stopped): it is
            // hidden from the list, so deselect it too. Only once it has been seen
            // online, so a player that is still connecting is not dropped.
            val onlyCfg = com.example.lmsnowplaying.settings.AppConfig.current
            if (onlyCfg.onlyControlDefault && onlyCfg.defaultPlayerMac.isNotBlank() && playerMac != onlyCfg.defaultPlayerMac) {
                // "Only control default": always the default player; none while it is offline.
                if (fresh.any { it.second == onlyCfg.defaultPlayerMac }) setPlayer(onlyCfg.defaultPlayerName, onlyCfg.defaultPlayerMac)
                else if (playerMac != "00:00:00:00:00:00") clearPlayer()
            } else if (playerMac != "00:00:00:00:00:00") {
                if (fresh.any { it.second == playerMac }) selectedSeenOnline = true
                else if (selectedSeenOnline || System.currentTimeMillis() - selectedAt > 60_000) clearPlayer()
            } else {
                // Nothing selected: back to the default player as soon as it is connected.
                val cfg = com.example.lmsnowplaying.settings.AppConfig.current
                if (cfg.defaultPlayerMac.isNotBlank() && fresh.any { it.second == cfg.defaultPlayerMac }) {
                    setPlayer(cfg.defaultPlayerName, cfg.defaultPlayerMac)
                } else if (cfg.defaultPlayerMac.isBlank() && com.example.lmsnowplaying.settings.AppConfig.lastPlayerMac.isNotBlank() &&
                    fresh.any { it.second == com.example.lmsnowplaying.settings.AppConfig.lastPlayerMac }) {
                    // No default player: the one used last
                    setPlayer(com.example.lmsnowplaying.settings.AppConfig.lastPlayerName, com.example.lmsnowplaying.settings.AppConfig.lastPlayerMac)
                }
            }

            println(players)
        }
    }

    // True once the selected player has been seen in the list of connected players.
    @Volatile
    private var selectedSeenOnline = false

    // When the current player was selected (ms): after a minute offline it is dropped even if never seen online.
    @Volatile
    private var selectedAt = 0L

    private fun clearPlayer() {
        playerMac = "00:00:00:00:00:00"
        playerName = "No Player"
        playerNameFlow.value = "No Player"
        selectedSeenOnline = false
    }

    fun setPlayer(name: String, mac: String){
        val previousMac = playerMac
        selectedSeenOnline = false
        selectedAt = System.currentTimeMillis()
        playerName = name
        playerNameFlow.value = name
        playerMac = mac
        if (mac != "00:00:00:00:00:00") com.example.lmsnowplaying.settings.AppConfig.rememberPlayer(name, mac)

        // Optional (Settings, Player settings): put the player we're switching away from into
        // standby, same as when the app is fully exited. Off by default, like Lyrion and Material:
        // the previous player keeps playing.
        // Not between players of one sync group: pausing or powering off one member
        // would stop the whole group (Lyrion's "power on/off in group"), including
        // the player we are switching to.
        if (com.example.lmsnowplaying.settings.AppConfig.current.powerOffOnSwitch &&
            previousMac != "00:00:00:00:00:00" && previousMac != mac && !inSameSyncGroup(previousMac, mac)) {
            CoroutineScope(Dispatchers.IO).launch {
                // Only power off: Lyrion pauses or stops by itself, as set for that player
                powerFor(previousMac, false)
            }
        }
    }

    // All artists of the playing track as "A, B" (Lyrion's tag "A": the names per
    // role, separated by ", "). Takes the role with the most names; "" if there is nothing.
    suspend fun getTrackArtists(playerMAC: String): String {
        return safeCall("getTrackArtists", "") {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMAC\", [\"status\", \"-\",1,\"tags:A\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.getCurrentSong(requestBody)
            val obj = JSONObject(res.body()?.string())
            val first = obj.getJSONObject("result").getJSONArray("playlist_loop").getJSONObject(0)
            listOf("trackartist", "artist")
                .map { first.optString(it, "") }
                .maxByOrNull { v -> v.split(", ").count { it.isNotBlank() } } ?: ""
        }
    }

    // Technical details of one library track (format, bit depth, sample rate, bitrate,
    // replay gain, lossless, size, tag version) as key -> value; only what the server knows.
    suspend fun getTrackTech(trackId: String): Map<String, String> {
        return safeCall("getTrackTech", emptyMap()) {
            val loop = rpcLoop(listOf("songinfo", 0, 100, "track_id:$trackId", "tags:oTIrYXQfvyg"), "songinfo_loop")
            val m = LinkedHashMap<String, String>()
            for (i in 0 until loop.length()) {
                val o = loop.getJSONObject(i)
                o.keys().forEach { k -> m[k] = o.optString(k) }
            }
            m
        }
    }

    suspend fun getSongInfo(playerMAC: String, songID: Int): JSONArray {
        return safeCall("getSongInfo", JSONArray()) {
            val reqString =
                "{\"method\": \"slim.request\", \"params\": [\"$playerMAC\", [\"songinfo\",0,100,\"track_id:$songID\",\"tags:acly4\"]]}"

            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.getCurrentSong(requestBody)

            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")

            getObject.getJSONArray("songinfo_loop")
        }
    }

    suspend fun playPause(){
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("playPause", Unit) {
            val reqString: String = if (isPlaying) {
                "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"pause\"]]}"
            } else {
                "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"play\"]]}"
            }
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playPause(requestBody)
        }
    }

    suspend fun playPause(play: Boolean){
        playPauseFor(playerMac, play)
        status()
    }

    private suspend fun playPauseFor(mac: String, play: Boolean){
        if (mac == "00:00:00:00:00:00") return
        safeCall("playPauseFor", Unit) {
            val playString: String = if (play) {
                "play"
            } else {
                "pause"
            }
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"$playString\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playPause(requestBody)
        }
    }

    suspend fun next(){
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("next", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlist\",\"index\",\"+1\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.next(requestBody)
        }
    }

    suspend fun prev(){
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("prev", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlist\",\"index\",\"-1\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.prev(requestBody)
        }
    }

    // Puts the player into standby (0) or wakes it (1). Needed for
    // self-powered players like the Boom - just pausing/stopping playback
    // isn't enough for those, they need an actual power-off command.
    suspend fun power(on: Boolean){
        powerFor(playerMac, on)
    }

    // Power switch of one player from the player list; the server applies the player's own
    // setting for pausing and resuming. The list is updated at once, the server follows.
    suspend fun setPlayerPower(mac: String, on: Boolean) {
        playerPower = playerPower + (mac to on)
        playersVersion.value = playersVersion.value + 1
        powerFor(mac, on)
    }

    private suspend fun powerFor(mac: String, on: Boolean){
        if (mac == "00:00:00:00:00:00") return
        safeCall("powerFor", Unit) {
            val powerValue = if (on) "1" else "0"
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"power\", \"$powerValue\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.power(requestBody)
        }
    }

    // Volume of the current player right now (one small request): negative when muted, null on failure.
    suspend fun fetchVolume(): Int? {
        if (playerMac == "00:00:00:00:00:00") return null
        return safeCall<Int?>("fetchVolume", null) {
            val res = rpcPlayer(playerMac, listOf("mixer", "volume", "?"))
            if (res.has("_volume")) res.optInt("_volume") else null
        }
    }

    // Changes the volume of the current player by delta percent (negative =
    // down). Plugins like Denon/Marantz AVR Control pick up this command too.
    suspend fun changeVolume(delta: Int) {
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("changeVolume", Unit) {
            rpcPlayer(playerMac, listOf("mixer", "volume", if (delta >= 0) "+$delta" else "$delta"))
        }
    }

    // Jumps to an absolute position (seconds) in the current track.
    suspend fun seekTo(seconds: Int) {
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("seekTo", Unit) {
            rpcPlayer(playerMac, listOf("time", seconds))
        }
    }

    suspend fun toggleMute() {
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("toggleMute", Unit) {
            rpcPlayer(playerMac, listOf("mixer", "muting", "toggle"))
        }
    }

    suspend fun status() {
        safeCall("status", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"status\", \"-\",1]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.status(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val mode = getObject.optString("mode", "")
            isPlaying = mode == "play"
            elapsedTime = getObject.optDouble("time", 0.0).let { if (it.isNaN()) 0.0 else it }
            trackDuration = getObject.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
            // Lyrion reports a muted player with a negative volume
            val vol = getObject.optInt("mixer volume", 100)
            volume = Math.abs(vol)
            volumeMuted = vol < 0
            volumeControl = getObject.optInt("use_volume_control", 1) == 1
        }
    }

    // Name of the streaming service a track URL belongs to, or "" (library files and plain web radio).
    // Known services get a proper name; any other plugin scheme (foo://...) shows its scheme.
    fun streamServiceName(url: String): String {
        val scheme = url.substringBefore(":", "").lowercase()
        return when (scheme) {
            "qobuz" -> "Qobuz"
            "tidal", "wimp" -> "Tidal"
            "spotify" -> "Spotify"
            "deezer", "deezerpodcast" -> "Deezer"
            "ytm", "ytmplaylist" -> "YouTube Music"
            "youtube" -> "YouTube"
            "soundcloud" -> "SoundCloud"
            // The Pyrrha plugin plays Pandora under its own scheme
            "pyrrha", "pandora" -> "Pandora"
            // Internal schemes of other plugins, and local/plain-web sources: no badge
            "randomflow", "", "file", "http", "https", "ftp", "mms", "rtmp", "rtsp" -> ""
            else -> if (scheme.length >= 2 && scheme.all { it.isLetterOrDigit() }) scheme.replaceFirstChar { it.uppercase() } else ""
        }
    }

    // True when the last status answer said the queue has no tracks at all (as opposed to a failed call).
    @Volatile var queueEmpty: Boolean = false

    suspend fun update(playerMAC: String): List<String> {
        queueEmpty = false
        return safeCall("update", listOf("", "", "", "", "", "", "", "")) {
            // "tags:acly4Ku" asks for artist/coverid/album/year plus "K"
            // (artwork_url) directly on the status call. artwork_url is how
            // remote sources - internet radio, Spotify/Tidal-style plugins -
            // carry their own cover art; those tracks have no local coverid
            // and no real library track_id to look up via songinfo at all.
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMAC\", [\"status\", \"-\",1,\"tags:bacly4KuorIT\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.getCurrentSong(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            queueEmpty = getObject.optInt("playlist_tracks", -1) == 0

            var title = ""
            var artist = ""
            var coverid = ""
            var portraitId = ""
            var album = ""
            var year = ""
            var artworkUrl = ""
            var trackUrl = ""

            try {
                val resultLoop = getObject.getJSONArray("playlist_loop")
                val firstResult = resultLoop.getJSONObject(0)
                title = firstResult.getString("title")
                artist = firstResult.optString("artist", "")
                coverid = firstResult.optString("coverid", "")
                album = firstResult.optString("album", "")
                year = firstResult.optString("year", "")
                artworkUrl = firstResult.optString("artwork_url", "")
                trackUrl = firstResult.optString("url", "")
                val id = firstResult.optString("id", "")
                currentTrackId = id
                techLineFlow.value = buildTechLine(playerMAC, getObject, firstResult, id)
                Log.w(TAG, "update: id=\"$id\" title=\"$title\" coverid=\"$coverid\" artwork_url=\"$artworkUrl\"")

                // The extra songinfo lookup is only meaningful (and only
                // valid to call) for a real library track - a plain,
                // non-negative numeric id. Remote/stream ids aren't real
                // track_ids and songinfo can't look them up; toIntOrNull()
                // safely skips those instead of throwing on id.toInt().
                val numericId = id.toIntOrNull()
                if (numericId != null && numericId >= 0) {
                    val res2 = getSongInfo(playerMAC, numericId)
                    Log.w(TAG, "update: getSongInfo returned ${res2.length()} entries: $res2")
                    for (i in 0 until res2.length()) {
                        val item = res2.getJSONObject(i)
                        if (artist == "" && item.has("artist")) {
                            artist = item.getString("artist")
                        }
                        if (coverid == "" && item.has("coverid")) {
                            coverid = item.getString("coverid")
                        }
                        if (portraitId == "" && item.has("portraitid")) {
                            portraitId = item.getString("portraitid")
                        }
                        if (album == "" && item.has("album")) {
                            album = item.getString("album")
                        }
                        if (year == "" && item.has("year")) {
                            year = item.getString("year")
                        }
                    }
                }

            } catch (e: JSONException) {
                techLineFlow.value = ""
                Log.w(TAG, "update: parsing playlist_loop/songinfo failed: ${e.message}")
                return@safeCall listOf(title, artist, coverid, portraitId, album, year, artworkUrl, trackUrl)
            }

            listOf(title, artist, coverid, portraitId, album, year, artworkUrl, trackUrl)
        }
    }

    // Tech info line, in Material's order. The base of the status response describes the stream the player
    // gets (after any transcoding), playlist_loop describes the source file. When transcoding, the line shows
    // source -> stream, and the stream part only uses base values, so source values (bit depth, bitrate)
    // never show up for the transcoded stream. The b tag makes Lyrion 9.2+ add the stream bitrate to the base;
    // without it (older Lyrion) the player's maxBitrate setting is used for mp3.
    // "981kb/s, 16bit, 44.1kHz, -6.88dB, flc": the technical line in Material's order.
    private fun techJoin(bitrate: String, size: String, rate: String, gain: String, type: String): String {
        val parts = mutableListOf<String>()
        val br = if (bitrate.isNotEmpty() && bitrate.all { it.isDigit() }) bitrate + "kb/s" else bitrate.replace("kbps", "kb/s").replace(Regex("^(\\d+)k$"), "$1kb/s")
        if (br.length > 1) parts.add(br)
        size.toIntOrNull()?.takeIf { it > 0 }?.let { parts.add("${it}bit") }
        rate.toDoubleOrNull()?.takeIf { it > 100 }?.let {
            val khz = it / 1000.0
            parts.add((if (khz % 1.0 == 0.0) khz.toInt().toString() else "%.1f".format(java.util.Locale.US, khz)) + "kHz")
        }
        gain.toDoubleOrNull()?.takeIf { it > 0.000001 || it < -0.000001 }
            ?.let { parts.add("%.2fdB".format(java.util.Locale.US, it)) }
        // "AAC (SoundCloud)" -> "aac": the service is already shown as the badge
        val shortType = type.replace(Regex("\\s*\\(.*\\)\\s*$"), "").let { if (it != type) it.lowercase() else it }
        if (shortType.isNotEmpty()) parts.add(shortType)
        return parts.joinToString(", ")
    }

    private suspend fun buildTechLine(mac: String, base: JSONObject, track: JSONObject, id: String): String {
        val transcoded = base.optString("is_transcoded", "").trim() == "1"
        // A 0 means unknown (remote tracks report 0 for a bitrate they do not know), so it counts as empty
        fun clean(v: String) = v.trim().let { if (it == "0") "" else it }
        fun field(key: String): String {
            val b = clean(base.optString(key, ""))
            return if (transcoded || b.isNotEmpty()) b else clean(track.optString(key, ""))
        }
        val type = field("type")
        var bitrate = field("bitrate")
        if (transcoded && bitrate.isEmpty() && type.equals("mp3", ignoreCase = true)) {
            val key = "$mac:$id"
            if (key != maxBitrateKey) {
                maxBitrate = runCatching { playerPref(mac, "maxBitrate").toIntOrNull() ?: 0 }.getOrDefault(0)
                maxBitrateKey = key
            }
            if (maxBitrate > 0) bitrate = "${maxBitrate}kb/s CBR"
        }
        val stream = techJoin(bitrate, field("samplesize"), field("samplerate"), base.optString("replay_gain", ""), type)
        if (!transcoded) return stream
        val source = techJoin(
            clean(track.optString("bitrate", "")), clean(track.optString("samplesize", "")),
            clean(track.optString("samplerate", "")), "", track.optString("type", "").trim()
        )
        return if (source.isEmpty()) stream else "$source \u2192 $stream"
    }

    // Fetches the upcoming queue starting at the currently playing track
    // (index 0 in the returned list = current track). Used both for the
    // "next track" preview and the pick-a-different-track list.
    suspend fun getUpcoming(mac: String, count: Int = 25): List<QueueTrack> {
        if (mac == "00:00:00:00:00:00") return emptyList()
        return safeCall("getUpcoming", emptyList()) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"status\", \"-\", $count, \"tags:ac\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.queue(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")

            val curIndex = getObject.optInt("playlist_cur_index", 0)
            val loop = getObject.optJSONArray("playlist_loop") ?: JSONArray()
            val list = mutableListOf<QueueTrack>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(
                    QueueTrack(
                        absoluteIndex = curIndex + i,
                        title = item.optString("title", ""),
                        artist = item.optString("artist", ""),
                        coverId = item.optString("coverid", ""),
                    )
                )
            }
            list
        }
    }

    // Jumps directly to an arbitrary (absolute) position in the playlist,
    // e.g. to play a track further down the queue than just "next".
    suspend fun jumpTo(mac: String, absoluteIndex: Int) {
        if (mac == "00:00:00:00:00:00") return
        safeCall("jumpTo", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlist\",\"index\",\"$absoluteIndex\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistIndex(requestBody)
        }
    }

    // Replaces whatever is currently queued up as the "next" track with a
    // newly chosen one: removes the old next-in-queue entry (if any), then
    // inserts the chosen track right after the currently playing one.
    // Plays or queues a whole album or a single track on the current player:
    //   cmd "load"   - replace the queue and start playing
    //   cmd "insert" - put it right after the current track
    //   cmd "add"    - append it to the end of the queue
    // Pass either albumId or trackId.
    suspend fun playlistControl(
        cmd: String,
        albumId: String? = null,
        trackId: String? = null,
        folderId: String? = null,
        playlistId: String? = null,
    ): Boolean {
        if (playerMac == "00:00:00:00:00:00") return false
        val target = when {
            albumId != null -> "album_id:$albumId"
            trackId != null -> "track_id:$trackId"
            folderId != null -> "folder_id:$folderId"
            playlistId != null -> "playlist_id:$playlistId"
            else -> return false
        }
        return safeCall("playlistControl($cmd)", false) {
            if (cmd == "load") power(true)
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlistcontrol\",\"cmd:$cmd\",\"$target\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistControl(requestBody)
            true
        }
    }

    suspend fun replaceNext(mac: String, newTrackId: String) {
        if (mac == "00:00:00:00:00:00") return
        safeCall("replaceNext", Unit) {
            val upcoming = getUpcoming(mac, 2)
            val hasNext = upcoming.size > 1
            if (hasNext) {
                val nextIndex = upcoming[1].absoluteIndex
                val delReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlist\",\"delete\",\"$nextIndex\"]]}"
                val delBody = delReq.toRequestBody("application/json".toMediaTypeOrNull())
                lmsApi.playlistDelete(delBody)
            }

            val insertReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlistcontrol\",\"cmd:insert\",\"track_id:$newTrackId\"]]}"
            val insertBody = insertReq.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistControl(insertBody)
        }
    }

    // Outcome of a "replace next track" attempt, used to give an accurate
    // toast without needlessly calling out a plugin to people who don't
    // use it.
    enum class ReplaceResult { SUGARCUBE, MIPSTER, RANDOMFLOW, DSTM, RANDOM, SKIPPED_QUEUE, NO_NEXT }

    // Outcome of a plugin's "replacenext" JSON-RPC call: whether the call
    // itself succeeded, and (only meaningful if it did) whether the next
    // track actually changed afterwards.
    private data class PluginReplaceAttempt(val ok: Boolean, val changed: Boolean)

    // Triggers SugarCube's or RandomFlow's own "replace next track" action
    // via its JSON-RPC command - both plugins expose the identical
    // ["<plugin>", "replacenext"] shape (only the plugin name differs), the
    // same action their own "Replace This Track" button fires, so we get
    // their music-similarity pick instead of a random track.
    //
    // Deliberately does NOT try to force the plugin's Chain/Auto Mix
    // setting on first: on both plugins, enabling that switches Don't Stop
    // The Music off as a side effect, and the app has no business silently
    // flipping a standing player setting (and losing the user's DSTM setup)
    // just because they tapped this button once. If the plugin silently
    // declines "replacenext" because Chain/Auto Mix happens to be off (or
    // the queue isn't in the exact shape it wants), that's simply reported
    // as "no change" below, and the caller falls through to DSTM/random
    // like any other no-op attempt.
    private suspend fun replaceNextViaPlugin(mac: String, pluginCommand: String): PluginReplaceAttempt {
        if (mac == "00:00:00:00:00:00") return PluginReplaceAttempt(ok = false, changed = false)

        return safeCall("replaceNextViaPlugin($pluginCommand)", PluginReplaceAttempt(ok = false, changed = false)) {
            val before = getUpcoming(mac, 2).getOrNull(1)

            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"$pluginCommand\", \"replacenext\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.pluginReplaceNext(requestBody)

            // The plugin may need several seconds (e.g. a large MusicIP/
            // SugarCube request), and the response returning doesn't
            // guarantee the playlist change has landed yet. Poll for the
            // change for up to ~8s; stop as soon as it shows up. If the
            // plugin silently declined, this costs the full wait before
            // falling through to the next tier.
            var changed = false
            for (i in 0 until 16) {
                kotlinx.coroutines.delay(500)
                val after = getUpcoming(mac, 2).getOrNull(1)
                if (after != null && (after.title != before?.title || after.coverId != before?.coverId)) {
                    changed = true
                    break
                }
            }
            PluginReplaceAttempt(ok = true, changed = changed)
        }
    }

    // Don't Stop The Music has no standalone "generate one now" command -
    // it only reacts to the server's own playlist events, and only adds
    // tracks (via whichever provider is configured for this player, e.g.
    // MusicIP/SugarCube, LastMix, Random Mix) once the queue is close to
    // running out. We can legitimately trigger that: if "next" is the only
    // track left in the queue, deleting it drops the remaining count to 0 -
    // safely below DSTM's default threshold - which fires its own
    // "delete" playlist notification and lets it fill the gap. Only
    // attempted when there's nothing else queued after "next", so this
    // never deletes tracks the user still wants further down the queue.
    private suspend fun replaceNextViaDstm(mac: String): Boolean {
        if (mac == "00:00:00:00:00:00") return false

        return safeCall("replaceNextViaDstm", false) {
            val queue = getUpcoming(mac, 3)
            val tailLength = queue.size - 1
            if (tailLength != 1) return@safeCall false

            // DSTM explicitly refuses to add anything while Lyrion's own Random
            // Mix (RandomPlay) plugin is active on this player - "disable" only
            // stops it from adding further tracks, it doesn't interrupt what's
            // already playing.
            val rpReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"randomplay\", \"disable\"]]}"
            val rpBody = rpReq.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.randomPlay(rpBody)

            val before = queue.getOrNull(1)
            val nextIndex = queue[1].absoluteIndex
            val delReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlist\",\"delete\",\"$nextIndex\"]]}"
            val delBody = delReq.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistDelete(delBody)

            // Give DSTM more of a chance to react to the "delete" notification
            // and (if configured) add a replacement via its provider - it can
            // delay its own reaction (e.g. until later in the current track),
            // so this is a best-effort wait, not a guarantee.
            kotlinx.coroutines.delay(5000)

            val after = getUpcoming(mac, 2).getOrNull(1)
            after != null && (after.title != before?.title || after.coverId != before?.coverId)
        }
    }

    // The current track's genre_id and artist_id (tags p and s), used to
    // keep the random fallback within the same genre/artist rather than
    // picking from the whole library.
    private suspend fun getCurrentGenreArtist(mac: String): Pair<String, String> {
        return safeCall("getCurrentGenreArtist", Pair("", "")) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"status\", \"-\", 1, \"tags:ps\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.status(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("playlist_loop") ?: JSONArray()
            if (loop.length() == 0) return@safeCall Pair("", "")
            val item = loop.getJSONObject(0)
            Pair(item.optString("genre_id", ""), item.optString("artist_id", ""))
        }
    }

    // Fetches up to [count] tracks matching the given filter (a tagged
    // param like "genre_id:7" or "artist_id:3", or null for the whole
    // library) and returns one random track id from that set, or null if
    // none were found.
    private suspend fun randomTrackId(filter: String?, count: Int = 300): String? {
        return safeCall("randomTrackId", null) {
            val filterParam = if (filter != null) ", \"$filter\"" else ""
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"tracks\", 0, $count$filterParam]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseTracks(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("titles_loop") ?: JSONArray()
            if (loop.length() == 0) return@safeCall null
            val randomItem = loop.getJSONObject((0 until loop.length()).random())
            randomItem.optString("id").takeIf { it.isNotEmpty() }
        }
    }

    // Native LMS fallback for when the chosen plugin isn't installed/
    // enabled, or didn't change anything: picks a random track, preferring
    // the current track's genre first, then its artist, then finally
    // anything in the library - and puts it in the "next" slot the same way
    // replaceNext() does.
    //
    // Only does this if "next" is the last track queued - same reasoning as
    // replaceNextViaDstm()'s tailLength check: if the user has an album or
    // a deliberate batch of tracks queued up, silently swapping out the
    // next one for something unrelated would break that sequence. SugarCube
    // and RandomFlow both already refuse to act in that situation; this
    // fallback needs the same guard, since it has no such judgment of its
    // own otherwise.
    private suspend fun replaceNextWithRandom(mac: String): Boolean {
        if (mac == "00:00:00:00:00:00") return false

        val queue = getUpcoming(mac, 3)
        val tailLength = queue.size - 1
        if (tailLength != 1) return false

        val (genreId, artistId) = getCurrentGenreArtist(mac)

        val trackId = (if (genreId.isNotEmpty()) randomTrackId("genre_id:$genreId") else null)
            ?: (if (artistId.isNotEmpty()) randomTrackId("artist_id:$artistId") else null)
            ?: randomTrackId(null)

        if (trackId != null) {
            replaceNext(mac, trackId)
            return true
        }
        return false
    }

    // Uses the Auto Mix plugin that is switched on for this player
    // (SugarCube, MIPster or Random Flow - see the Mix menu) for its
    // music-similarity pick, then gives DSTM a legitimate chance to fill the
    // gap via whichever provider is configured for this player, and only then
    // falls back to a genuinely random library track - so the button never
    // dead-ends, and people who don't use any of these never see them mentioned.
    // Name of the active plugin if it was tried but changed nothing during
    // the last replaceNextSmart() call, so the UI can tell the user to check
    // the plugin settings. Null otherwise.
    @Volatile
    var lastDeclinedPlugin: String? = null

    suspend fun replaceNextSmart(mac: String): ReplaceResult {
        lastDeclinedPlugin = null
        val active = getMixState().on.firstOrNull { it != MixPlugin.DSTM }
        when (active) {
            MixPlugin.SUGARCUBE -> {
                val attempt = replaceNextViaPlugin(mac, "sugarcube")
                if (attempt.changed) return ReplaceResult.SUGARCUBE
                if (attempt.ok) lastDeclinedPlugin = "SugarCube"
            }
            MixPlugin.MIPSTER -> {
                val attempt = replaceNextViaPlugin(mac, "mipster")
                if (attempt.changed) return ReplaceResult.MIPSTER
                if (attempt.ok) lastDeclinedPlugin = "MIPster"
            }
            MixPlugin.RANDOMFLOW -> {
                val attempt = replaceNextViaPlugin(mac, "randomflow")
                if (attempt.changed) return ReplaceResult.RANDOMFLOW
                if (attempt.ok) lastDeclinedPlugin = "Random Flow"
            }
            else -> { /* no plugin active: straight to DSTM */ }
        }

        if (replaceNextViaDstm(mac)) return ReplaceResult.DSTM

        if (replaceNextWithRandom(mac)) return ReplaceResult.RANDOM

        // Nothing replaced anything - most likely because there's an album
        // or a deliberate batch queued up (more than just "next" left to
        // play), which every tier above declines to disturb on purpose.
        if (getUpcoming(mac, 3).size <= 1) return ReplaceResult.NO_NEXT
        return ReplaceResult.SKIPPED_QUEUE
    }

    // --- Library browsing (artist -> album -> track), used to pick the
    // replacement for the "next" track. ---

    // Library-wide search (artists, albums, tracks matching the text).
    // Lyrion pages through one combined list (artists first, then albums,
    // then tracks), so a small count would be filled by artists alone -
    // ask for plenty so albums and tracks make it into the response too.
    suspend fun search(term: String, count: Int = 300): SearchResults {
        return safeCall("search", SearchResults()) {
            val params = JSONArray().put("").put(
                JSONArray().put("search").put(0).put(count).put("term:$term")
            )
            val reqString = JSONObject().put("method", "slim.request").put("params", params).toString()
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.search(requestBody)
            val jsonData: String? = res.body()?.string()
            val result = JSONObject(jsonData).getJSONObject("result")

            val artists = mutableListOf<BrowseItem>()
            val loopA = result.optJSONArray("contributors_loop") ?: JSONArray()
            for (i in 0 until loopA.length()) {
                val o = loopA.getJSONObject(i)
                artists.add(BrowseItem(o.optString("contributor_id"), o.optString("contributor")))
            }
            val albums = mutableListOf<BrowseItem>()
            val loopB = result.optJSONArray("albums_loop") ?: JSONArray()
            for (i in 0 until loopB.length()) {
                val o = loopB.getJSONObject(i)
                albums.add(BrowseItem(o.optString("album_id"), o.optString("album")))
            }
            val tracks = mutableListOf<BrowseTrack>()
            val loopC = result.optJSONArray("tracks_loop") ?: JSONArray()
            for (i in 0 until loopC.length()) {
                val o = loopC.getJSONObject(i)
                tracks.add(BrowseTrack(o.optString("track_id"), o.optString("track"), 0))
            }

            // Thumbnails: the search command carries no artwork info, so ask
            // for it separately with the same term and match by id. If either
            // lookup fails the rows simply have no picture.
            val albumInfo = try {
                val loop = rpcLoop(listOf("albums", 0, count, "search:$term", "tags:lja"), "albums_loop")
                (0 until loop.length()).associate {
                    val o = loop.getJSONObject(it)
                    o.optString("id") to Pair(o.optString("artwork_track_id"), o.optString("artist"))
                }
            } catch (e: Exception) { emptyMap() }
            val trackInfo = try {
                val loop = rpcLoop(listOf("titles", 0, count, "search:$term", "tags:caltem"), "titles_loop")
                (0 until loop.length()).associate {
                    val o = loop.getJSONObject(it)
                    val sub = listOf(o.optString("artist"), o.optString("album")).filter { s -> s.isNotEmpty() }
                    o.optString("id") to Triple(o.optString("coverid"), sub.joinToString(" · "), o.optInt("tracknum", 0))
                }
            } catch (e: Exception) { emptyMap() }

            // Albums that contain a matching track: show that track instead of
            // the artist, so the right album is recognised at a glance.
            val matchByAlbum = HashMap<String, MutableList<String>>()
            try {
                val loop = rpcLoop(listOf("titles", 0, count, "search:$term", "tags:caltem"), "titles_loop")
                for (k in 0 until loop.length()) {
                    val o = loop.getJSONObject(k)
                    val alb = o.optString("album_id")
                    if (alb.isEmpty()) continue
                    val n = o.optInt("tracknum", 0)
                    val line = (if (n > 0) "$n • " else "") + o.optString("title") +
                        o.optString("artist").let { a -> if (a.isNotEmpty()) " · $a" else "" }
                    matchByAlbum.getOrPut(alb) { mutableListOf() }.add(line)
                }
            } catch (e: Exception) { }
            val albumsOut = albums.map {
                val i = albumInfo[it.id]
                val m = matchByAlbum[it.id]
                val sub = if (m.isNullOrEmpty()) i?.second ?: ""
                    else m[0] + (if (m.size > 1) " (+${m.size - 1})" else "")
                it.copy(artworkId = i?.first ?: "", subtitle = sub)
            }
            val tracksOut = tracks.map {
                val i = trackInfo[it.id]
                it.copy(coverId = i?.first ?: "", subtitle = i?.second ?: "", trackNum = i?.third ?: 0)
            }
            SearchResults(artists, albumsOut, tracksOut)
        }
    }

    // --- Repeat / shuffle / Don't Stop The Music state of the current player ---

    // Last Don't Stop The Music provider seen on this player (so the repeat
    // button can switch it back on after switching it off).
    @Volatile
    var lastDstmProvider: String = ""

    const val RNP_CMD = "radionowplaying"

    private suspend fun rpcPlayer(mac: String, command: List<Any>): JSONObject {
        val cmd = JSONArray()
        command.forEach { cmd.put(it) }
        val params = JSONArray().put(mac).put(cmd)
        val reqString = JSONObject().put("method", "slim.request").put("params", params).toString()
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.rpc(requestBody)
        val jsonData: String? = res.body()?.string()
        return JSONObject(jsonData).optJSONObject("result") ?: JSONObject()
    }

    // Raw result of any plugin command for the current player (used by the
    // plugin menu screens). Null when no player is selected or the call fails.
    suspend fun pluginMenu(command: List<Any>): JSONObject? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return null
        return safeCall<JSONObject?>("pluginMenu", null) { rpcPlayer(mac, command) }
    }

    private data class SliderDef(
        val min: Int,
        val max: Int,
        val key: String?,
        val fastStep: Int = 5,
        val noLimitAtMax: Boolean = false,
        val step: Int = 1,
    )

    // Random Flow settings that get a slider with every value reachable (its
    // menu itself only offers steps of 10 and the like): set command -> range,
    // and the key holding the exact value in its "mixsettings" answer (null:
    // not in there, the menu's own value is used).
    private val randomFlowSliders = mapOf(
        "setwobble" to SliderDef(0, 100, "wobble"),
        "setartistcooldown" to SliderDef(0, 100, "artistCooldownTracks"),
        "setalbumcooldown" to SliderDef(0, 100, "albumCooldownTracks"),
        "setmaxplaycount" to SliderDef(0, 50, "maxPlaycount", noLimitAtMax = true),
        "setbatchsize" to SliderDef(10, 100, null),
        "setpoolsize" to SliderDef(10, 2000, null, fastStep = 20),
    )

    // MIPster settings with a slider (the ranges of its Live View): the name of
    // the setting -> range. The exact value is read from the player's prefs.
    private val mipsterSliders = mapOf(
        "chainSize" to SliderDef(10, 300, null, fastStep = 10),
        "chainRejectsize" to SliderDef(0, 30, null),
        "chainStyle" to SliderDef(0, 200, null, fastStep = 20, step = 20),
        "chainVariety" to SliderDef(0, 9, null),
    )

    // SugarCube settings with a slider (the ranges of its Live View), same
    // idea as MIPster's. Its Mix style moves in steps of 1.
    private val sugarCubeSliders = mapOf(
        "sugarcube_size" to SliderDef(10, 300, null, fastStep = 10),
        "sugarcube_rejectsize" to SliderDef(0, 30, null),
        "sugarcube_style" to SliderDef(0, 200, null, fastStep = 10),
        "sugarcube_variety" to SliderDef(0, 9, null),
    )

    // Random Mix types (command word, shown name); the chosen one is kept in the
    // app and used by "Start new mix".
    private val randomMixTypes = listOf(
        "tracks" to "Tracks", "albums" to "Albums", "contributors" to "Artists",
        "work" to "Works", "year" to "Years",
    )
    const val APP_MIX_TYPE = "app:mixtype"
    @Volatile
    private var randomMixType: String = "tracks"
    val currentRandomMixType: String get() = randomMixType

    // The settings of a mix plugin, read from its own Jive menu on the server.
    suspend fun pluginSettings(plugin: MixPlugin): List<PluginSetting> = when (plugin) {
        MixPlugin.RANDOMFLOW -> {
            val exact = pluginMenu(listOf("randomflow", "mixsettings"))
            jiveSettings(
                menuCommand = listOf("randomflow", "menu"),
                settingId = { cmd -> cmd.getOrNull(1) as? String },
                skip = setOf("setautomix"),
                defs = randomFlowSliders,
                exactValue = { _, def ->
                    val key = def.key
                    if (key != null && exact != null && exact.has(key)) {
                        // A null value means "no limit" (Max play count)
                        if (exact.isNull(key)) def.max else exact.optInt(key)
                    } else null
                },
                command = { cmd, _, def ->
                    { v: Int -> if (def.noLimitAtMax && v == def.max) cmd.take(2) + "none" else cmd.take(2) + v }
                },
            )
        }
        MixPlugin.SUGARCUBE -> {
            val mac = playerMac
            val base = jiveSettings(
                menuCommand = listOf("sugarcube", "menu"),
                // Its commands look like: sugarcube setting sugarcube_size:50
                settingId = { cmd -> (cmd.getOrNull(2) as? String)?.substringBefore(":") },
                skip = setOf("sugarcube_status", "sugarcube_sleep"),
                defs = sugarCubeSliders,
                exactValue = { id, _ -> playerPref(mac, "plugin.SugarCube:$id").toIntOrNull() },
                command = { _, id, _ -> { v: Int -> listOf("sugarcube", "setting", "$id:$v") } },
            )
            // Wobble is a player setting of SugarCube but not in its menu; set through the player pref
            if (base.isEmpty() || base.any { st -> st.commands.any { c -> c.any { it.toString().contains("sugarcube_wobble") } } }) base
            else {
                val labels = listOf("Disabled", "Tight (first 3)", "Medium (first 5)", "Loose (any)", "Floating (random)")
                val current = playerPref(mac, "plugin.SugarCube:sugarcube_wobble").toIntOrNull() ?: 0
                base + PluginSetting("Wobble", labels,
                    labels.indices.map { listOf<Any>("playerpref", "plugin.SugarCube:sugarcube_wobble", it) },
                    current.coerceIn(0, labels.size - 1))
            }
        }
        MixPlugin.MIPSTER -> {
            val mac = playerMac
            jiveSettings(
                menuCommand = listOf("mipster", "menu"),
                // Its commands look like: mipster setting chainSize:50
                settingId = { cmd -> (cmd.getOrNull(2) as? String)?.substringBefore(":") },
                skip = setOf("chainStatus", "sleepStatus"),
                defs = mipsterSliders,
                exactValue = { id, _ -> playerPref(mac, "plugin.mipster:$id").toIntOrNull() },
                command = { _, id, _ -> { v: Int -> listOf("mipster", "setting", "$id:$v") } },
            )
        }
        MixPlugin.RANDOMPLAY -> {
            // Random Mix has no menu of its own for these; they are server preferences
            val mac = playerMac
            val typeRow = PluginSetting("Mix type", randomMixTypes.map { it.second },
                randomMixTypes.map { listOf<Any>(APP_MIX_TYPE, it.first) },
                randomMixTypes.indexOfFirst { it.first == randomMixType }.coerceAtLeast(0))
            val libraryRow = PluginSetting("Library", emptyList(), emptyList(), -1, null,
                listOf("randomplaylibrarylist", 0, 100))
            listOf(typeRow) + listOf(Triple("newtracks", "New tracks", 1..100), Triple("oldtracks", "Old tracks", 0..100)).mapNotNull { (key, text, range) ->
                val value = rpcPlayer(mac, listOf("pref", "plugin.randomplay:$key", "?"))
                    .optString("_p2", "").toIntOrNull() ?: return@mapNotNull null
                PluginSetting(text, emptyList(), emptyList(), -1,
                    SliderSpec(range.first, range.last, value.coerceIn(range.first, range.last),
                        { v: Int -> listOf("pref", "plugin.randomplay:$key", v) }))
            } + libraryRow
        }
        else -> emptyList()
    }

    // The setting rows of a plugin's Jive menu. Rows without choices (the
    // action buttons and sub lists) and the Auto Mix switch are skipped.
    private suspend fun jiveSettings(
        menuCommand: List<Any>,
        settingId: (List<Any>) -> String?,
        skip: Set<String>,
        defs: Map<String, SliderDef>,
        exactValue: suspend (String, SliderDef) -> Int?,
        command: (List<Any>, String, SliderDef) -> ((Int) -> List<Any>),
    ): List<PluginSetting> {
        val loop = pluginMenu(menuCommand)?.optJSONArray("item_loop") ?: return emptyList()
        val settings = mutableListOf<PluginSetting>()
        for (i in 0 until loop.length()) {
            val item = loop.optJSONObject(i) ?: continue
            // A row that opens a list (Filter, Recipe, Mood); the list itself is loaded later
            val goCmd = item.optJSONObject("actions")?.optJSONObject("go")?.optJSONArray("cmd")
            if (goCmd != null && item.optJSONArray("choiceStrings") == null) {
                settings.add(PluginSetting(item.optString("text"), emptyList(), emptyList(), -1, null,
                    (0 until goCmd.length()).map { goCmd.get(it) }))
                continue
            }
            val choiceStrings = item.optJSONArray("choiceStrings") ?: continue
            val choiceCmds = item.optJSONObject("actions")?.optJSONObject("do")
                ?.optJSONArray("choices") ?: continue
            val commands = (0 until choiceCmds.length()).map { c ->
                val cmd = choiceCmds.optJSONObject(c)?.optJSONArray("cmd") ?: JSONArray()
                (0 until cmd.length()).map { cmd.get(it) }
            }
            val id = commands.firstOrNull()?.let(settingId)
            if (id != null && id in skip) continue
            val choices = (0 until choiceStrings.length()).map { choiceStrings.optString(it) }
            val selected = item.optInt("selectedIndex", 1) - 1
            val def = if (id != null) defs[id] else null
            val slider = if (def != null && id != null) {
                val exact = exactValue(id, def)
                val value = when {
                    exact != null -> exact
                    def.noLimitAtMax && selected <= 0 -> def.max
                    else -> choices.getOrNull(selected)?.toIntOrNull() ?: def.min
                }
                SliderSpec(def.min, def.max, value.coerceIn(def.min, def.max),
                    command(commands.first(), id, def), def.fastStep, def.noLimitAtMax, def.step)
            } else null
            settings.add(PluginSetting(item.optString("text"), choices, commands, selected, slider))
        }
        return settings
    }

    // Fills in a setting that opens a list (Filter, Recipe, Mood): asks the plugin
    // for the list, with the current choice marked "radio". Returns the setting
    // unchanged when the list can't be read.
    suspend fun loadPluginList(setting: PluginSetting): PluginSetting {
        val cmd = setting.listCommand ?: return setting
        val loop = pluginMenu(cmd)?.optJSONArray("item_loop") ?: return setting
        val choices = mutableListOf<String>()
        val commands = mutableListOf<List<Any>>()
        var selected = -1
        for (i in 0 until loop.length()) {
            val item = loop.optJSONObject(i) ?: continue
            val itemCmd = item.optJSONObject("actions")?.optJSONObject("do")
                ?.optJSONArray("cmd") ?: continue
            if (selected < 0 && item.optInt("radio", 0) == 1) selected = choices.size
            choices.add(item.optString("text"))
            commands.add((0 until itemCmd.length()).map { itemCmd.get(it) })
        }
        return setting.copy(choices = choices, commands = commands, selected = selected)
    }

    // Random Mix genres with their on/off state (a genre that is on is mixed).
    suspend fun loadGenres(): List<Pair<String, Boolean>> {
        val loop = pluginMenu(listOf("randomplaygenrelist", 0, 1000))?.optJSONArray("item_loop")
            ?: return emptyList()
        return (0 until loop.length()).mapNotNull { i ->
            val item = loop.optJSONObject(i) ?: return@mapNotNull null
            if (!item.has("checkbox")) null else item.optString("text") to (item.optInt("checkbox") == 1)
        }
    }

    suspend fun setGenre(genre: String, on: Boolean) =
        runPluginCommand(listOf("randomplaychoosegenre", genre, if (on) 1 else 0))

    suspend fun setAllGenres(on: Boolean) =
        runPluginCommand(listOf("randomplaygenreselectall", if (on) 1 else 0))

    // Runs one of a plugin menu's commands (a choice of a setting) for the current player.
    suspend fun runPluginCommand(command: List<Any>) {
        if (command.firstOrNull() == APP_MIX_TYPE) {
            randomMixType = command.getOrNull(1) as? String ?: "tracks"
            return
        }
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("runPluginCommand", Unit) { rpcPlayer(mac, command); Unit }
    }

    // Whether Don't Stop The Music is there. Lyrion drops the connection on a command of a plugin that is
    // switched off, so this asks "can" first; a failed check counts as there.
    private suspend fun dstmInstalled(): Boolean = hasCommand(listOf("dontstopthemusicsetting"))

    suspend fun getPlayModes(): PlayModes? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return null
        return safeCall("getPlayModes", null) {
            val status = rpcPlayer(mac, listOf("status", "-", 1))
            val dstm = rpcPlayer(mac, listOf("playerpref", "plugin.dontstopthemusic:provider", "?"))
            // The stored provider stays in Lyrion when the plugin is switched off, so it only counts while DSTM is there
            val provider = dstm.optString("_p2", "")
            val dstmActive = provider.isNotBlank() && provider != "0" && dstmInstalled()
            if (dstmActive) lastDstmProvider = provider
            PlayModes(
                repeat = status.optInt("playlist repeat", 0),
                shuffle = status.optInt("playlist shuffle", 0),
                dstmProvider = if (dstmActive) provider else "",
            )
        }
    }

    // mode: 0 = off, 1 = repeat the current song, 2 = repeat the whole queue
    suspend fun setRepeat(mode: Int) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setRepeat", Unit) { rpcPlayer(mac, listOf("playlist", "repeat", mode)); Unit }
    }

    // mode: 0 = off, 1 = shuffle songs, 2 = shuffle albums (releases)
    suspend fun setShuffle(mode: Int) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setShuffle", Unit) { rpcPlayer(mac, listOf("playlist", "shuffle", mode)); Unit }
    }

    // The Don't Stop The Music source to switch on: the last one seen on this
    // player, or else the first one the plugin offers (it lists its own
    // sources, including those of SugarCube / Random Flow, via the same
    // "dontstopthemusicsetting" command its settings menu uses). Empty if
    // there is none.
    suspend fun ensureDstmProvider(): String {
        if (lastDstmProvider.isNotBlank()) return lastDstmProvider
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return ""
        return safeCall("ensureDstmProvider", "") {
            val res = rpcPlayer(mac, listOf("dontstopthemusicsetting"))
            val loop = res.optJSONArray("item_loop") ?: JSONArray()
            for (i in 0 until loop.length()) {
                val cmd = loop.getJSONObject(i).optJSONObject("actions")
                    ?.optJSONObject("do")?.optJSONArray("cmd") ?: continue
                val token = cmd.optString(2, "")
                if (token.isNotBlank() && token != "0") {
                    lastDstmProvider = token
                    return@safeCall token
                }
            }
            ""
        }
    }

    // --- Auto Mix plugins (SugarCube, MIPster, Random Flow, Don't Stop The Music) ---

    // Random Flow keeps its "mix running" state on the sync-group master,
    // so commands/queries for it must use the master's id.
    private suspend fun masterMac(mac: String): String {
        val status = rpcPlayer(mac, listOf("status", "-", 1))
        return status.optString("sync_master", "").ifBlank { mac }
    }

    // The player (mac) for which this app started a Random Mix and has not
    // switched it off since.
    @Volatile
    private var randomMixMac: String? = null

    // Lyrion's "volume control" of a player: true = variable, false = fixed at 100%.
    suspend fun getDigitalVolumeControl(mac: String): Boolean? =
        safeCall<Boolean?>("getDigitalVolumeControl", null) {
            val v = playerPref(mac, "digitalVolumeControl")
            if (v.isEmpty()) null else v != "0"
        }

    suspend fun setDigitalVolumeControl(mac: String, variable: Boolean) {
        safeCall("setDigitalVolumeControl", Unit) {
            rpcPlayer(mac, listOf("playerpref", "digitalVolumeControl", if (variable) "1" else "0"))
            Unit
        }
    }

    private suspend fun playerPref(mac: String, name: String): String =
        rpcPlayer(mac, listOf("playerpref", name, "?")).optString("_p2", "")

    // Which Auto Mix plugins exist on the server and which one is on for
    // the current player. A plugin counts as available when its setting
    // can be read.
    suspend fun getMixState(): MixState = getMixStateOrNull() ?: MixState()

    // Null when every check failed (server not reachable): the caller keeps what it had,
    // while an empty state means the plugins really are not there.
    suspend fun getMixStateOrNull(): MixState? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return MixState()
        return safeCall<MixState?>("getMixState", null) {
            val available = mutableSetOf<MixPlugin>()
            val on = mutableSetOf<MixPlugin>()
            var checks = 0
            var failed = 0
            suspend fun runCheck(what: String, block: suspend () -> Unit) {
                checks++
                if (!checkPlugin(what, block)) failed++
            }

            // Each plugin is checked on its own: a failing check only drops that plugin.
            // "can" goes first: a removed plugin leaves its stored prefs behind, and Lyrion drops the
            // connection on a command of a plugin that is switched off.
            runCheck("SugarCube") {
                val sc = if (hasCommand(listOf("sugarcube", "setting"))) playerPref(mac, "plugin.SugarCube:sugarcube_status") else ""
                if (sc.isNotBlank()) { available.add(MixPlugin.SUGARCUBE); if (sc == "1") on.add(MixPlugin.SUGARCUBE) }
            }

            runCheck("MIPster") {
                val mip = if (hasCommand(listOf("mipster", "setting"))) playerPref(mac, "plugin.mipster:chainStatus") else ""
                if (mip.isNotBlank()) { available.add(MixPlugin.MIPSTER); if (mip == "1") on.add(MixPlugin.MIPSTER) }
            }

            runCheck("Random Flow") {
                val rf = if (hasCommand(listOf("randomflow", "mixsettings"))) rpcPlayer(masterMac(mac), listOf("randomflow", "mixsettings")) else JSONObject()
                if (rf.has("mixRunning")) {
                    available.add(MixPlugin.RANDOMFLOW)
                    if (rf.optString("mixRunning").lowercase() in listOf("1", "true")) on.add(MixPlugin.RANDOMFLOW)
                }
            }

            // Lyrion's own Random Mix: "randomplayisactive" answers with the
            // running mix type (empty when none); no answer = plugin missing.
            runCheck("Random Mix") {
                val rp = if (hasCommand(listOf("randomplayisactive"))) rpcPlayer(mac, listOf("randomplayisactive")) else JSONObject()
                if (rp.has("_randomplayisactive")) {
                    available.add(MixPlugin.RANDOMPLAY)
                    val type = rp.optString("_randomplayisactive", "")
                    if ((type.isNotBlank() && type != "0" && type != "null") || randomMixMac == mac) {
                        on.add(MixPlugin.RANDOMPLAY)
                    }
                }
            }

            runCheck("DSTM") {
                val dstmMenu = if (dstmInstalled()) rpcPlayer(mac, listOf("dontstopthemusicsetting")) else JSONObject()
                if ((dstmMenu.optJSONArray("item_loop")?.length() ?: 0) > 1) {
                    available.add(MixPlugin.DSTM)
                    val provider = playerPref(mac, "plugin.dontstopthemusic:provider")
                    if (provider.isNotBlank() && provider != "0") {
                        lastDstmProvider = provider
                        on.add(MixPlugin.DSTM)
                    }
                }
            }
            if (failed == checks) null else MixState(available, on)
        }
    }

    // The whole queue of the current player (for the Queue panel).
    suspend fun getQueue(count: Int = 500): QueueList {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return QueueList()
        return safeCall("getQueue", QueueList()) {
            val status = rpcPlayer(mac, listOf("status", 0, count, "tags:acK"))
            val loop = status.optJSONArray("playlist_loop") ?: JSONArray()
            val tracks = (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                QueueTrack(
                    absoluteIndex = o.optInt("playlist index", it),
                    title = o.optString("title", ""),
                    artist = o.optString("artist", ""),
                    coverId = o.optString("coverid", ""),
                    artworkUrl = o.optString("artwork_url", ""),
                )
            }
            QueueList(tracks, status.optInt("playlist_cur_index", 0))
        }
    }

    // Removes one track (by its position in the queue) from the queue.
    suspend fun removeFromQueue(index: Int) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("removeFromQueue", Unit) { rpcPlayer(mac, listOf("playlist", "delete", index)); Unit }
    }

    // Removes repeated tracks (same file, or same title + artist + album) from the queue; the first one stays,
    // the playing track is never removed. Returns how many were removed, -1 when it failed.
    suspend fun removeDuplicates(): Int {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return -1
        return safeCall("removeDuplicates", -1) {
            val status = rpcPlayer(mac, listOf("status", 0, 1000, "tags:alu"))
            val current = status.optInt("playlist_cur_index", -1)
            val loop = status.optJSONArray("playlist_loop") ?: JSONArray()
            val seen = HashSet<String>()
            val doomed = ArrayList<Int>()
            for (i in 0 until loop.length()) {
                val o = loop.getJSONObject(i)
                val index = o.optInt("playlist index", i)
                val url = o.optString("url", "")
                val key = if (url.isNotEmpty()) url
                else (o.optString("title", "") + "|" + o.optString("artist", "") + "|" + o.optString("album", "")).lowercase()
                if (!seen.add(key) && index != current) doomed.add(index)
            }
            // From the back, so the positions of the others stay valid
            doomed.sortedDescending().forEach { rpcPlayer(mac, listOf("playlist", "delete", it)) }
            doomed.size
        }
    }

    // Empties the whole queue (this also stops the playback).
    suspend fun clearQueue(): Boolean {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return false
        return safeCall("clearQueue", false) { rpcPlayer(mac, listOf("playlist", "clear")); true }
    }

    // Whether "replace next track" makes sense right now: not while a stream
    // plays, and not while a batch runs (the same rule the Auto Mix plugins
    // use: more than 2 tracks left after the current position = a batch).
    suspend fun canReplaceNext(): Boolean {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return false
        return safeCall("canReplaceNext", true) {
            val status = rpcPlayer(mac, listOf("status", "-", 1, "tags:"))
            val item = status.optJSONArray("playlist_loop")?.optJSONObject(0)
            val isStream = item != null && (item.optInt("remote", 0) == 1 || item.optLong("id", 0L) < 0L)
            val remaining = status.optInt("playlist_tracks", 0) - status.optInt("playlist_cur_index", 0)
            !isStream && remaining <= 2
        }
    }

    // The Auto Mix plugin that is switched on, but only while a stream
    // (internet radio etc.) is playing or the queue is empty - that is when a mix
    // has to be started by hand. Null otherwise (also when only DSTM is on).
    suspend fun getStreamMixPlugin(): MixPlugin? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return null
        return safeCall("getStreamMixPlugin", null) {
            val status = rpcPlayer(mac, listOf("status", "-", 1, "tags:u"))
            val item = status.optJSONArray("playlist_loop")?.optJSONObject(0)
            // An empty queue needs a mix started by hand as well. Tracks of a streaming service (Deezer, Qobuz, ...)
            // count as a stream too, even when Lyrion has them in its library.
            val isStream = item != null && (item.optInt("remote", 0) == 1 || item.optLong("id", 0L) < 0L ||
                streamServiceName(item.optString("url", "")).isNotEmpty())
            if (item != null && !isStream) return@safeCall null
            getMixState().on.firstOrNull { it != MixPlugin.DSTM && it != MixPlugin.RANDOMPLAY }
        }
    }

    private suspend fun applyMixPlugin(plugin: MixPlugin, enable: Boolean) {
        val mac = playerMac
        val flag = if (enable) "1" else "0"
        when (plugin) {
            MixPlugin.SUGARCUBE -> rpcPlayer(mac, listOf("sugarcube", "setting", "sugarcube_status:$flag"))
            MixPlugin.MIPSTER -> rpcPlayer(mac, listOf("mipster", "setting", "chainStatus:$flag"))
            MixPlugin.RANDOMFLOW -> rpcPlayer(masterMac(mac), listOf("randomflow", "setautomix", flag))
            // Random Mix has no "on but idle" state of its own, so switching it
            // on only marks it in the app; the mix starts with "Start new mix".
            MixPlugin.RANDOMPLAY -> {
                if (enable) rpcPlayer(mac, listOf("pref", "plugin.randomplay:continuous", "1"))
                else rpcPlayer(mac, listOf("randomplay", "disable"))
                randomMixMac = if (enable) mac else null
            }
            MixPlugin.DSTM -> {
                val provider = if (enable) ensureDstmProvider() else "0"
                if (provider.isNotBlank()) {
                    rpcPlayer(mac, listOf("playerpref", "plugin.dontstopthemusic:provider", provider))
                }
            }
        }
    }

    // Switches one plugin on or off. Switching a mix plugin on first switches
    // the other mix plugins that are on off, so only one keeps the queue
    // filled. Don't Stop The Music is left alone in both directions: Lyrion
    // has a hook (canConflictWithDSTM) that makes DSTM stand aside while a
    // mix plugin is mixing, so DSTM keeps its own setting.
    suspend fun setMixPlugin(plugin: MixPlugin, enable: Boolean, current: MixState) {
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("setMixPlugin", Unit) {
            if (enable && plugin != MixPlugin.DSTM) {
                for (other in current.on) {
                    if (other != plugin && other != MixPlugin.DSTM) applyMixPlugin(other, false)
                }
            }
            applyMixPlugin(plugin, enable)
        }
    }

    // Whether the server knows a command (an older plugin version may not).
    suspend fun hasCommand(command: List<String>): Boolean {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return true
        return safeCall("hasCommand", true) {
            rpcPlayer(mac, listOf("can") + command + "?").optInt("_can", 1) == 1
        }
    }

    // Starts a new mix / starts a batch / adds a batch with the given
    // plugin (not available for Don't Stop The Music).
    suspend fun mixAction(plugin: MixPlugin, action: MixAction) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("mixAction", Unit) {
            when (plugin) {
                MixPlugin.SUGARCUBE -> rpcPlayer(mac, listOf("sugarcube", when (action) {
                    // Older SugarCube versions call it "startmix" and have no batches
                    MixAction.START_MIX -> if (hasCommand(listOf("sugarcube", "startchain"))) "startchain" else "startmix"
                    MixAction.START_BATCH -> "startbatch"
                    MixAction.ADD_BATCH -> "addbatch"
                }))
                MixPlugin.MIPSTER -> rpcPlayer(mac, when (action) {
                    MixAction.START_MIX -> listOf("mipster", "setting", "startChain:1")
                    MixAction.START_BATCH -> listOf("mipster", "batch", "mood", "none", 0, "play")
                    MixAction.ADD_BATCH -> listOf("mipster", "batch", "mood", "none", 0, "add")
                })
                MixPlugin.RANDOMFLOW -> rpcPlayer(masterMac(mac), listOf("randomflow", when (action) {
                    MixAction.START_MIX -> "startmix"
                    MixAction.START_BATCH -> "startbatch"
                    MixAction.ADD_BATCH -> "topup"
                }))
                MixPlugin.RANDOMPLAY ->
                    if (action == MixAction.START_MIX) {
                        rpcPlayer(mac, listOf("randomplay", randomMixType))
                        randomMixMac = mac
                    }
                MixPlugin.DSTM -> Unit
            }
            Unit
        }
    }

    // Provider "0" switches Don't Stop The Music off for this player.
    suspend fun setDstmProvider(provider: String) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setDstmProvider", Unit) {
            rpcPlayer(mac, listOf("playerpref", "plugin.dontstopthemusic:provider", provider)); Unit
        }
    }

    // One level of the favorites. An empty parentId means the top level.
    suspend fun getFavorites(parentId: String, count: Int = 1000): List<FavoriteEntry> {
        return safeCall("getFavorites", emptyList()) {
            val cmd = mutableListOf<Any>("favorites", "items", 0, count, "want_url:1")
            if (parentId.isNotBlank()) cmd.add("item_id:$parentId")
            val loop = rpcLoop(cmd, "loop_loop")
            (0 until loop.length()).mapNotNull {
                val o = loop.getJSONObject(it)
                val id = o.optString("id")
                val name = o.optString("name")
                val icon = o.optString("image").ifBlank { o.optString("icon") }
                if (id.isBlank() || name.isBlank()) null
                else FavoriteEntry(id, name, o.optInt("hasitems", 0) > 0, icon, false, o.optInt("isaudio", 0) > 0 || o.optString("url").isNotBlank(), o.optString("url"))
            }
        }
    }

    // A favorite album of the library is only an address ("db:album.title=…&contributor.name=…") that Lyrion
    // cannot list itself. This looks the album up in the library and returns its id, or null.
    suspend fun albumIdFromFavoriteUrl(url: String): String? {
        if (!url.startsWith("db:album.title=")) return null
        return safeCall<String?>("albumIdFromFavoriteUrl", null) {
            val params = url.removePrefix("db:").split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i < 0) null else it.substring(0, i) to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }.toMap()
            // An album of a classical work is a subset of an album: not handled here
            if (params.containsKey("work.title")) return@safeCall null
            val title = params["album.title"].orEmpty()
            val artist = params["contributor.name"]
            if (title.isBlank()) return@safeCall null
            val loop = rpcLoop(listOf("albums", 0, 200, "search:$title", "tags:la"), "albums_loop")
            val matches = (0 until loop.length()).map { loop.getJSONObject(it) }.filter { it.optString("album") == title }
            (matches.firstOrNull { artist == null || it.optString("artist") == artist } ?: matches.firstOrNull())?.optString("id")
        }
    }

    // Plays or queues a favorite (or an item of another plugin, e.g. radio) on the current player:
    //   cmd "load" (replace the queue and play), "insert" (play next), "add" (at the end)
    suspend fun favoritesControl(cmd: String, itemId: String, plugin: String = "favorites"): Boolean {
        if (playerMac == "00:00:00:00:00:00") return false
        return safeCall("favoritesControl($cmd)", false) {
            if (cmd == "load") power(true)
            val lmsCmd = if (cmd == "load") "play" else cmd
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"$plugin\",\"playlist\",\"$lmsCmd\",\"item_id:$itemId\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.playlistControl(requestBody)
            // Lyrion answers an HTTP error or a JSON-RPC "error" when it refuses; an empty result is normal
            val answer = res.body()?.string().orEmpty()
            val refused = !res.isSuccessful || (answer.isNotBlank() && JSONObject(answer).has("error"))
            if (refused) Log.w(TAG, "favoritesControl($cmd, $plugin) item=$itemId refused: HTTP ${res.code()} ${answer.take(200)}")
            !refused
        }
    }

    // Gives a favorite (by its position id in the favorites) a new name; the original item is not touched.
    suspend fun renameFavorite(itemId: String, title: String): Boolean =
        safeCall("renameFavorite", false) {
            rpcPlayer("", listOf("favorites", "rename", "item_id:$itemId", "title:$title"))
            true
        }

    // What it takes to save something as a favorite: its address, its name and (optional) a picture.
    class FavTarget(val url: String, val title: String, val icon: String = "")

    // A plugin item (radio, app), from the plugin's menu answer (presetParams) of the level it sits in.
    private suspend fun pluginFavoriteOf(cmd: String, itemId: String): FavTarget? {
        val parentId = itemId.substringBeforeLast('.', "")
        val command = mutableListOf<Any>(cmd, "items", 0, 1000)
        if (parentId.isNotBlank()) command.add("item_id:$parentId")
        command.add("menu:$cmd")
        val loop = pluginMenu(command)?.optJSONArray("item_loop") ?: JSONArray()
        for (i in 0 until loop.length()) {
            val o = loop.getJSONObject(i)
            if (o.optJSONObject("params")?.optString("item_id") != itemId) continue
            val preset = o.optJSONObject("presetParams") ?: return null
            val url = preset.optString("favorites_url")
            val title = preset.optString("favorites_title").ifBlank { o.optString("text") }
            return if (url.isBlank() || title.isBlank()) null else FavTarget(url, title, preset.optString("icon"))
        }
        return null
    }

    suspend fun pluginFavTarget(cmd: String, itemId: String): FavTarget? {
        if (playerMac == "00:00:00:00:00:00") return null
        return safeCall<FavTarget?>("pluginFavTarget", null) { pluginFavoriteOf(cmd, itemId) }
    }

    // An album of the library: Lyrion gives its favorites address and name with every album.
    suspend fun albumFavTarget(albumId: String): FavTarget? = safeCall<FavTarget?>("albumFavTarget", null) {
        val o = rpcLoop(listOf("albums", 0, 1, "album_id:$albumId", "tags:l"), "albums_loop").optJSONObject(0)
        val url = o?.optString("favorites_url").orEmpty()
        val title = o?.optString("favorites_title").orEmpty()
        if (url.isBlank() || title.isBlank()) null else FavTarget(url, title)
    }

    // A track of the library: its own address and title.
    suspend fun trackFavTarget(trackId: String): FavTarget? = safeCall<FavTarget?>("trackFavTarget", null) {
        val o = rpcLoop(listOf("titles", 0, 1, "track_id:$trackId", "tags:tu"), "titles_loop").optJSONObject(0)
        val url = o?.optString("url").orEmpty()
        val title = o?.optString("title").orEmpty()
        if (url.isBlank() || title.isBlank()) null else FavTarget(url, title)
    }

    // The id in the Lyrion favorites of the entry with this address (also inside folders), or null.
    private suspend fun findFavoriteId(url: String, parent: String = "", depth: Int = 0): String? {
        val cmd = mutableListOf<Any>("favorites", "items", 0, 1000, "want_url:1")
        if (parent.isNotBlank()) cmd.add("item_id:$parent")
        val loop = rpcLoop(cmd, "loop_loop")
        for (i in 0 until loop.length()) {
            val o = loop.getJSONObject(i)
            if (o.optString("url") == url) return o.optString("id")
        }
        if (depth < 4) for (i in 0 until loop.length()) {
            val o = loop.getJSONObject(i)
            if (o.optInt("hasitems", 0) > 0) findFavoriteId(url, o.optString("id"), depth + 1)?.let { return it }
        }
        return null
    }

    // The favorites id of this target when it is a favorite already, otherwise null.
    suspend fun favoriteIdOf(target: FavTarget): String? =
        safeCall<String?>("favoriteIdOf", null) { findFavoriteId(target.url) }

    // Saves the target as a Lyrion favorite.
    suspend fun addFavorite(target: FavTarget): Boolean =
        safeCall("addFavorite", false) {
            val add = mutableListOf<Any>("favorites", "add", "url:${target.url}", "title:${target.title}")
            if (target.icon.isNotBlank()) add.add("icon:${target.icon}")
            rpcPlayer("", add)
            true
        }

    // Removes a favorite (by its id in the favorites) from the Lyrion favorites.
    suspend fun removeFavorite(favoriteId: String): Boolean =
        safeCall("removeFavorite", false) {
            rpcPlayer("", listOf("favorites", "delete", "item_id:$favoriteId"))
            true
        }

    // The Radio menu of the server, like the Material skin: by the weight the server gives each entry
    // (plugins first, then the TuneIn entries), alphabetical within the same weight. A plugin that its
    // own setting puts under Apps (Radio Now Playing, Pyrrha...) is not listed here.
    suspend fun getRadios(): List<RadioEntry> {
        return safeCall("getRadios", emptyList()) {
            val res = pluginMenu(listOf("radios", 0, 200))
            // The loop name differs per server version, so take the list whose entries have a "cmd"
            var loop = JSONArray()
            res?.keys()?.forEach { key ->
                val arr = res.optJSONArray(key)
                if (arr != null && arr.length() > 0 && arr.optJSONObject(0)?.has("cmd") == true) loop = arr
            }
            val listed = (0 until loop.length()).mapNotNull {
                val o = loop.getJSONObject(it)
                val cmd = o.optString("cmd")
                val name = o.optString("name")
                if (cmd.isBlank() || name.isBlank()) null
                // Only a plugin's one-colour icon ("..._svg.png") is taken over; the other entries keep the app's own icons
                else o.optInt("weight", 100) to RadioEntry(cmd, name, o.optString("type") == "xmlbrowser_search", o.optString("icon").takeIf { ic -> ic.endsWith("_svg.png") }.orEmpty())
            }
            listed.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() })).map { it.second }
        }
    }

    // The apps of the server (YouTube Music, Bandcamp, SoundCloud...): plugins that browse like radio.
    suspend fun getApps(): List<RadioEntry> = safeCall<List<RadioEntry>>("getApps", emptyList()) {
        val res = pluginMenu(listOf("apps", 0, 200))
        // The loop name differs ("appss_loop"), so take the list whose entries have a "cmd"
        var loop = JSONArray()
        res?.keys()?.forEach { key ->
            val arr = res.optJSONArray(key)
            if (arr != null && arr.length() > 0 && arr.optJSONObject(0)?.has("cmd") == true) loop = arr
        }
        (0 until loop.length()).mapNotNull {
            val o = loop.getJSONObject(it)
            val cmd = o.optString("cmd")
            val name = o.optString("name")
            if (cmd.isBlank() || name.isBlank()) null else RadioEntry(cmd, name, false, o.optString("icon"))
        }.sortedBy { it.name.lowercase() }
    }

    // One level of a radio plugin. An empty parentId means its top level.
    suspend fun getRadioItems(cmd: String, parentId: String, count: Int = 1000, search: String? = null): List<FavoriteEntry> {
        return safeCall("getRadioItems", emptyList()) {
            val command = mutableListOf<Any>(cmd, "items", 0, count)
            if (parentId.isNotBlank()) command.add("item_id:$parentId")
            if (search != null) command.add("search:$search")
            val loop = pluginMenu(command)?.optJSONArray("loop_loop") ?: JSONArray()
            (0 until loop.length()).mapNotNull {
                val o = loop.getJSONObject(it)
                val id = o.optString("id")
                // Some plugins (Podcast) give the text as "title" instead of "name"
                val name = o.optString("name").ifBlank { o.optString("title") }
                val icon = o.optString("image").ifBlank { o.optString("icon") }
                if (id.isBlank() || name.isBlank()) null
                else FavoriteEntry(id, name, o.optInt("hasitems", 0) > 0, icon, o.optString("type") == "search", o.optInt("isaudio", 0) > 0)
            }
        }
    }

    // The saved playlists of the server.
    suspend fun getPlaylists(count: Int = 1000): List<BrowseItem> {
        return safeCall("getPlaylists", emptyList()) {
            val loop = rpcLoop(listOf("playlists", 0, count), "playlists_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("playlist"))
            }
        }
    }

    // The tracks of one saved playlist, in playlist order.
    suspend fun getPlaylistTracks(playlistId: String, count: Int = 1000): List<Pair<BrowseTrack, String>> {
        return safeCall("getPlaylistTracks", emptyList()) {
            val loop = rpcLoop(listOf("playlists", "tracks", 0, count, "playlist_id:$playlistId", "tags:talc"), "playlisttracks_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                // The second part is the line below the title: artist and album
                BrowseTrack(o.optString("id"), o.optString("title"), it + 1, o.optString("coverid")) to
                    listOf(o.optString("artist"), o.optString("album")).filter { s -> s.isNotBlank() }.joinToString(" · ")
            }
        }
    }

    // MusicIP plugin (musicip moods / musicip mix): both need a player. The size of a mix
    // follows the player's own MusicIP settings. Empty when the plugin or MusicIP isn't there.
    suspend fun getMusicIpMoods(): List<String> {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return emptyList()
        return safeCall("getMusicIpMoods", emptyList()) {
            val loop = rpcPlayer(mac, listOf("musicip", "moods")).optJSONArray("titles_loop") ?: JSONArray()
            (0 until loop.length()).map { loop.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
        }
    }

    // The player's own MusicIP settings (plugin.musicip). type and rejectType: 0 tracks, 1 minutes, 2 MB.
    // filter is the name of the MusicIP filter ("" or "0" = none). Null when they cannot be read.
    data class MusicIpSettings(
        val size: Int, val type: Int, val style: Int, val variety: Int, val genre: Int,
        val rejectSize: Int, val rejectType: Int, val filter: String,
    )

    suspend fun getMusicIpSettings(): MusicIpSettings? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return null
        return safeCall<MusicIpSettings?>("getMusicIpSettings", null) {
            suspend fun pref(name: String) = playerPref(mac, "plugin.musicip:$name")
            val size = pref("mix_size")
            val type = pref("mix_type")
            if (size.isBlank() && type.isBlank()) null
            else MusicIpSettings(
                size = size.toIntOrNull() ?: 12,
                type = type.toIntOrNull() ?: 0,
                style = pref("mix_style").toIntOrNull() ?: 20,
                variety = pref("mix_variety").toIntOrNull() ?: 0,
                genre = pref("mix_genre").toIntOrNull() ?: 0,
                rejectSize = pref("reject_size").toIntOrNull() ?: 12,
                rejectType = pref("reject_type").toIntOrNull() ?: 0,
                filter = pref("mix_filter"),
            )
        }
    }

    // Genres MusicIP leaves out of a mix (a list in the player's prefs; an empty list is a real value, no fallback)
    suspend fun getMusicIpExcludedGenres(): List<String>? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return null
        return safeCall<List<String>?>("getMusicIpExcludedGenres", null) {
            val value = rpcPlayer(mac, listOf("playerpref", "plugin.musicip:mix_genre_filter", "?")).opt("_p2")
            when (value) {
                is JSONArray -> (0 until value.length()).map { value.optString(it) }
                is String -> if (value.isBlank()) emptyList() else listOf(value)
                else -> emptyList()
            }
        }
    }

    suspend fun setMusicIpExcludedGenres(genres: List<String>) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setMusicIpExcludedGenres", Unit) {
            rpcPlayer(mac, listOf("playerpref", "plugin.musicip:mix_genre_filter", JSONArray(genres))); Unit
        }
    }

    // The MusicIP filters, asked from MusicIP itself (its /api/filters), at the address in the plugin's
    // settings; localhost there means the Lyrion server. Null when MusicIP cannot be reached.
    var musicIpFiltersAddress: String = ""
    suspend fun getMusicIpFilters(): List<String>? = safeCall<List<String>?>("getMusicIpFilters", null) {
        val host0 = rpcPlayer("", listOf("pref", "plugin.musicip:host", "?")).optString("_p2", "").trim()
        val port = rpcPlayer("", listOf("pref", "plugin.musicip:port", "?")).optString("_p2", "").trim().ifBlank { "10002" }
        val lmsHost = com.example.lmsnowplaying.settings.AppConfig.current.lmsUrl.substringAfter("://").substringBefore("/").substringBefore(":")
        val host = if (host0.isBlank() || host0 == "localhost" || host0 == "127.0.0.1" || host0 == "::1") lmsHost else host0
        musicIpFiltersAddress = "$host:$port"
        val client = okhttp3.OkHttpClient.Builder().callTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
        val request = okhttp3.Request.Builder().url("http://$host:$port/api/filters").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@safeCall null
            response.body?.string().orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    suspend fun setMusicIpPref(name: String, value: Any) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setMusicIpPref($name)", Unit) { rpcPlayer(mac, listOf("playerpref", "plugin.musicip:$name", value)); Unit }
    }

    suspend fun getMusicIpMix(mood: String): List<Pair<BrowseTrack, String>> {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return emptyList()
        return safeCall("getMusicIpMix", emptyList()) {
            val loop = rpcPlayer(mac, listOf("musicip", "mix", "mood:$mood", "tags:alc")).optJSONArray("titles_loop") ?: JSONArray()
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseTrack(o.optString("id"), o.optString("title"), it + 1, o.optString("coverid")) to
                    listOf(o.optString("artist"), o.optString("album")).filter { s -> s.isNotBlank() }.joinToString(" · ")
            }
        }
    }

    // Plays, inserts or adds several tracks at once.
    suspend fun playTrackIds(cmd: String, ids: List<String>): Boolean {
        if (playerMac == "00:00:00:00:00:00" || ids.isEmpty()) return false
        return safeCall("playTrackIds($cmd)", false) {
            if (cmd == "load") power(true)
            rpcPlayer(playerMac, listOf("playlistcontrol", "cmd:$cmd", "track_id:${ids.joinToString(",")}"))
            true
        }
    }

    // Saves the player's current queue as a playlist on the server under the given name and checks that
    // it exists afterwards (Lyrion silently refuses to save while it scans). Lyrion may change characters
    // in the name (a colon becomes a space), so only letters and digits are compared.
    suspend fun saveQueueAsPlaylist(name: String): Boolean {
        if (playerMac == "00:00:00:00:00:00" || name.isBlank()) return false
        return safeCall("saveQueueAsPlaylist", false) {
            rpcPlayer(playerMac, listOf("playlist", "save", name))
            val wanted = name.filter { it.isLetterOrDigit() }.lowercase()
            getPlaylists().any { it.name.filter { c -> c.isLetterOrDigit() }.lowercase() == wanted }
        }
    }

    suspend fun getGenres(count: Int = 2000): List<BrowseItem> {
        return safeCall("getGenres", emptyList()) {
            val loop = rpcLoop(listOf("genres", 0, count), "genres_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("genre"))
            }
        }
    }

    suspend fun getArtistsByGenre(genreId: String, count: Int = 5000): List<BrowseItem> {
        return safeCall("getArtistsByGenre", emptyList()) {
            val loop = rpcLoop(listOf("artists", 0, count, "genre_id:$genreId"), "artists_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("artist"))
            }
        }
    }

    // One level of the music folder tree. An empty folderId means the top level.
    suspend fun getFolder(folderId: String, count: Int = 2000): List<FolderEntry> {
        return safeCall("getFolder", emptyList()) {
            val cmd = mutableListOf<Any>("musicfolder", 0, count)
            if (folderId.isNotBlank()) cmd.add("folder_id:$folderId")
            val loop = rpcLoop(cmd, "folder_loop")
            (0 until loop.length()).mapNotNull {
                val o = loop.getJSONObject(it)
                val type = o.optString("type")
                if (type == "folder" || type == "track") {
                    FolderEntry(o.optString("id"), o.optString("filename"), type == "folder")
                } else null
            }
        }
    }

    // Sends one server-wide (no player) JSON-RPC query and returns the named result loop.
    private suspend fun rpcLoop(command: List<Any>, loopName: String): JSONArray {
        val cmd = JSONArray()
        command.forEach { cmd.put(it) }
        val params = JSONArray().put("").put(cmd)
        val reqString = JSONObject().put("method", "slim.request").put("params", params).toString()
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.search(requestBody)
        val jsonData: String? = res.body()?.string()
        return JSONObject(jsonData).getJSONObject("result").optJSONArray(loopName) ?: JSONArray()
    }

    // role: Lyrion role token(s) to restrict to (e.g. ALBUMARTIST); empty = the normal list.
    suspend fun getArtists(count: Int = 500, role: String = ""): List<BrowseItem> {
        return safeCall("getArtists", emptyList()) {
            val roleParam = if (role.isNotEmpty()) ", \"role_id:$role\"" else ""
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"artists\", 0, $count$roleParam]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseArtists(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("artists_loop") ?: JSONArray()
            val list = mutableListOf<BrowseItem>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(BrowseItem(item.optString("id"), item.optString("artist")))
            }
            list
        }
    }

    // role: restrict to albums where the artist has this role (e.g. COMPOSER); empty = normal.
    suspend fun getAlbums(artistId: String, count: Int = 500, role: String = ""): List<BrowseItem> {
        return safeCall("getAlbums", emptyList()) {
            val roleParam = if (role.isNotEmpty()) ", \"role_id:$role\"" else ""
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"albums\", 0, $count, \"artist_id:$artistId\"$roleParam, \"sort:yearalbum\", \"tags:lj\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseAlbums(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("albums_loop") ?: JSONArray()
            val list = mutableListOf<BrowseItem>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(BrowseItem(item.optString("id"), item.optString("album"), item.optString("artwork_track_id")))
            }
            list
        }
    }

    // Years that have albums, newest first (year 0 = no year tag is left out).
    suspend fun getYears(): List<BrowseItem> {
        return safeCall("getYears", emptyList()) {
            val loop = rpcLoop(listOf("years", 0, 1000), "years_loop")
            (0 until loop.length())
                .map { loop.getJSONObject(it).optString("year") }
                .filter { it.isNotEmpty() && it != "0" }
                .sortedDescending()
                .map { BrowseItem(it, it) }
        }
    }

    // Albums of one year, with the artist as second line.
    suspend fun getAlbumsByYear(year: String, count: Int = 1000): List<BrowseItem> {
        return safeCall("getAlbumsByYear", emptyList()) {
            val loop = rpcLoop(listOf("albums", 0, count, "year:$year", "sort:album", "tags:lja"), "albums_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("album"), o.optString("artwork_track_id"), o.optString("artist"))
            }
        }
    }

    // All albums in the given Lyrion sort order (like Material's Releases).
    suspend fun getReleases(sort: String = "artistalbum", count: Int = 10000): List<BrowseItem> {
        return safeCall("getReleases", emptyList()) {
            val loop = rpcLoop(listOf("albums", 0, count, "sort:$sort", "tags:ljayE"), "albums_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("album"), o.optString("artwork_track_id"), o.optString("artist"), o.optString("year"), service = serviceOfExtId(o.optString("extid")))
            }
        }
    }

    // Newest albums first (Lyrion sort:new), with the artist as second line.
    suspend fun getNewAlbums(count: Int = 200): List<BrowseItem> {
        return safeCall("getNewAlbums", emptyList()) {
            val loop = rpcLoop(listOf("albums", 0, count, "sort:new", "tags:ljaE"), "albums_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("album"), o.optString("artwork_track_id"), o.optString("artist"), service = serviceOfExtId(o.optString("extid")))
            }
        }
    }

    // Music and Artist Information plugin: the texts the Material skin shows in its info view.
    // Each function returns empty values when the plugin has nothing (or the call fails).
    data class ArtistInfo(val bio: String, val portraitId: String)

    // Like the Material skin, the album text is asked by album id when known: a name lookup finds
    // nothing for compilations and for tracks whose artist differs from the album artist.
    suspend fun getArtistInfo(artist: String): ArtistInfo = safeCall("getArtistInfo", ArtistInfo("", "")) {
        val r = rpcPlayer("", listOf("musicartistinfo", "biography", "artist:$artist"))
        ArtistInfo(r.optString("biography", "").trim(), r.optString("portraitid", ""))
    }

    suspend fun getAlbumReview(artist: String, album: String, albumId: String = ""): String = safeCall("getAlbumReview", "") {
        val cmd = if (albumId.isNotBlank()) listOf("musicartistinfo", "albumreview", "album_id:$albumId")
            else listOf("musicartistinfo", "albumreview", "album:$album", "artist:$artist")
        rpcPlayer("", cmd).optString("albumreview", "").trim()
    }

    // Albums of the artist with exactly this name (the name is all we know of the playing artist).
    suspend fun getArtistAlbums(artist: String): List<BrowseItem> = safeCall<List<BrowseItem>>("getArtistAlbums", emptyList()) {
        val found = rpcPlayer("", listOf("artists", 0, 20, "search:$artist")).optJSONArray("artists_loop") ?: JSONArray()
        var id = ""
        for (i in 0 until found.length()) {
            val o = found.getJSONObject(i)
            if (o.optString("artist").equals(artist, ignoreCase = true)) { id = o.optString("id"); break }
        }
        if (id.isBlank()) return@safeCall emptyList<BrowseItem>()
        val loop = rpcPlayer("", listOf("albums", 0, 200, "artist_id:$id", "sort:yearalbum", "tags:lyj")).optJSONArray("albums_loop") ?: JSONArray()
        val list = mutableListOf<BrowseItem>()
        for (i in 0 until loop.length()) {
            val o = loop.getJSONObject(i)
            list.add(BrowseItem(o.optString("id"), o.optString("album"), o.optString("artwork_track_id"), year = o.optString("year", "")))
        }
        list
    }

    suspend fun getLyrics(artist: String, title: String): String = safeCall("getLyrics", "") {
        rpcPlayer("", listOf("musicartistinfo", "lyrics", "artist:$artist", "title:$title"))
            .optString("lyrics", "").trim()
    }

    // Album id of a library track (tag "e"). Do not add the artist id tag "s" here: on this
    // server the connection drops on it.
    suspend fun getAlbumIdOfTrack(trackId: String): String = safeCall("getAlbumIdOfTrack", "") {
        rpcPlayer("", listOf("titles", 0, 1, "track_id:$trackId", "tags:e"))
            .optJSONArray("titles_loop")?.optJSONObject(0)?.optString("album_id", "") ?: ""
    }

    // Moves the queue of the selected player to another player with the Material skin plugin's
    // "transferqueue" command (it syncs the two players, which copies the queue and the play
    // position, then clears and switches off the source). The plugin gives no answer we can read,
    // so success is checked on the target: it must have tracks afterwards.
    suspend fun transferQueue(toMac: String): Boolean = safeCall("transferQueue", false) {
        if (playerMac == "00:00:00:00:00:00") return@safeCall false
        rpcPlayer("", listOf("material-skin", "transferqueue", "from:$playerMac", "to:$toMac", "mode:move"))
        kotlinx.coroutines.delay(2000)
        rpcPlayer(toMac, listOf("status", "-", 1)).optInt("playlist_tracks", 0) > 0
    }

    // A track of the album shown in the info view: artist, length and the technical line.
    data class AlbumTrack(val id: String, val title: String, val trackNum: Int, val artist: String, val seconds: Double, val tech: String)

    suspend fun getAlbumTracks(albumId: String, count: Int = 200): List<AlbumTrack> = safeCall<List<AlbumTrack>>("getAlbumTracks", emptyList()) {
        val loop = rpcPlayer("", listOf("titles", 0, count, "album_id:$albumId", "sort:tracknum", "tags:tadrTIoY"))
            .optJSONArray("titles_loop") ?: JSONArray()
        val list = mutableListOf<AlbumTrack>()
        for (i in 0 until loop.length()) {
            val o = loop.getJSONObject(i)
            list.add(AlbumTrack(
                o.optString("id"), o.optString("title"), o.optInt("tracknum", 0), o.optString("artist"),
                o.optDouble("duration", 0.0),
                techJoin(o.optString("bitrate"), o.optString("samplesize"), o.optString("samplerate"), o.optString("replay_gain"), o.optString("type"))
            ))
        }
        list
    }

    suspend fun getTracks(albumId: String, count: Int = 200): List<BrowseTrack> {
        return safeCall("getTracks", emptyList()) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"titles\", 0, $count, \"album_id:$albumId\", \"sort:tracknum\", \"tags:ta\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseTracks(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("titles_loop") ?: JSONArray()
            val list = mutableListOf<BrowseTrack>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(BrowseTrack(item.optString("id"), item.optString("title"), item.optInt("tracknum", 0), subtitle = item.optString("artist")))
            }
            list
        }
    }

}
