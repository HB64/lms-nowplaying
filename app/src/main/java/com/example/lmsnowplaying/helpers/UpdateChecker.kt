package com.example.lmsnowplaying.helpers

import android.content.Context
import com.example.lmsnowplaying.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Looks up the latest GitHub release (never a pre-release or draft) and tells
// whether it is newer than the installed version.
object UpdateChecker {

    private const val LATEST_URL = "https://api.github.com/repos/HB64/lms-nowplaying/releases/latest"

    data class Release(val version: String, val notes: String, val pageUrl: String, val apkUrl: String)

    // One check per app start (not when coming back from the background).
    @Volatile private var checkedThisRun = false

    // Returns the new release to announce, or null (up to date, already checked
    // today, or GitHub not reachable). At most one successful check per day.
    suspend fun checkOnStart(context: Context): Release? {
        if (checkedThisRun) return null
        checkedThisRun = true
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        if (SettingsRepository.lastUpdateCheck(context) == today) return null
        val release = withContext(Dispatchers.IO) { fetchLatest() } ?: return null
        SettingsRepository.markUpdateChecked(context, today)
        return if (isNewer(release.version, currentVersion(context))) release else null
    }

    fun currentVersion(context: Context): String =
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }

    private fun fetchLatest(): Release? {
        return try {
            val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "LMS-NowPlaying")
                if (conn.responseCode != 200) return null
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
                var apk = ""
                val assets = o.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                    }
                }
                Release(o.optString("tag_name").trimStart('v', 'V'), o.optString("body"), o.optString("html_url"), apk)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parts(v: String): List<Int> =
        v.trimStart('v', 'V').split('.', '-').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // Release notes as plain text for the dialog: no markdown marks, shortened.
    fun notesPreview(body: String, max: Int = 700): String {
        val text = body.lines()
            .map { it.trim().trimStart('#', ' ').replace("**", "").replace("`", "") }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return if (text.length > max) text.take(max).trimEnd() + "…" else text
    }
}
