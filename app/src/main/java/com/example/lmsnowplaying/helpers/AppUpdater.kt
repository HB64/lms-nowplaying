package com.example.lmsnowplaying.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.lmsnowplaying.settings.NavState
import com.example.lmsnowplaying.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Downloads the APK of a release and hands it to the Android installer.
// Android always asks for confirmation itself; nothing is installed silently.
object AppUpdater {

    private fun dir(context: Context) = File(context.cacheDir, "updates")

    private fun isValidApk(context: Context, file: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        return info.packageName == context.packageName
    }

    // Returns the downloaded APK (an earlier complete download is reused), or null on failure.
    suspend fun download(context: Context, release: UpdateChecker.Release, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val dir = dir(context).apply { mkdirs() }
            val target = File(dir, "lms-nowplaying-${release.version}.apk")
            val part = File(dir, target.name + ".part")
            try {
                if (target.exists() && isValidApk(context, target)) { onProgress(100); return@withContext target }
                dir.listFiles()?.forEach { it.delete() }
                val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "LMS-NowPlaying")
                    if (conn.responseCode != 200) return@withContext null
                    val total = conn.contentLengthLong
                    var done = 0L
                    var last = -1
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                ensureActive()
                                if (total > 0) {
                                    val p = (done * 100 / total).toInt()
                                    if (p != last) { last = p; onProgress(p) }
                                }
                            }
                        }
                    }
                    if (total > 0 && done != total) return@withContext null
                } finally {
                    conn.disconnect()
                }
                if (!part.renameTo(target) || !isValidApk(context, target)) { target.delete(); return@withContext null }
                target
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } finally {
                part.delete()
            }
        }

    // Android 8+ asks per app for permission to install apps ("unknown sources").
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context): Boolean = try {
        NavState.leavingForInstall = true
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        // Next start announces the update again (the install was not finished)
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { SettingsRepository.markUpdateChecked(appContext, "") }
        true
    } catch (_: Exception) {
        NavState.leavingForInstall = false
        false
    }

    fun install(context: Context, file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        NavState.leavingForInstall = true
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        NavState.leavingForInstall = false
        false
    }
}
