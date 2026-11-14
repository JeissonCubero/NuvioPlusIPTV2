package com.nuvio.tv.livetv.only

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Live TV only build: updates from this app's GitHub releases.
 *
 * Every build of the `livetv-only` branch is published as a release tagged `build-N`, with one
 * APK per processor type. The app compares N with its own build number (versionCode − 1,000,000),
 * downloads the APK that fits this TV and hands it to Android's installer, which installs it as an
 * update over the current app (same ID, same signing key: settings and lists are kept).
 */
object AppUpdater {

    private const val REPO = "JeissonCubero/NuvioPlusIPTV2"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CODE_BASE = 1_000_000

    data class Update(val build: Int, val name: String, val apkUrl: String, val sizeBytes: Long, val notes: String)

    /** This app's own build number (0 for builds made outside the release workflow). */
    val currentBuild: Int get() = (BuildConfig.VERSION_CODE - CODE_BASE).coerceAtLeast(0)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** The newest release if it's newer than this app, else null. Throws when GitHub can't be reached. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LiveTV-Updater")
            .build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("GitHub answered ${r.code}")
            r.body?.string().orEmpty()
        }
        val json = JSONObject(body)
        val build = json.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
        if (build <= currentBuild) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val byName = (0 until assets.length()).map { assets.getJSONObject(it) }
            .associateBy { it.optString("name") }
        val abis = Build.SUPPORTED_ABIS.orEmpty().toList()
        val pick = listOf("arm64-v8a", "armeabi-v7a")
            .filter { it in abis }
            .sortedBy { abis.indexOf(it) }
            .map { "app-$it-release.apk" }
            .plus("app-universal-release.apk")
            .firstNotNullOfOrNull { byName[it] }
            ?: return@withContext null
        Update(
            build = build,
            name = json.optString("name").ifBlank { "Build $build" },
            apkUrl = pick.optString("browser_download_url"),
            sizeBytes = pick.optLong("size"),
            notes = json.optString("body")
        )
    }

    /** Downloads the APK into the app's own space, reporting progress 0..1. */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "live-tv-${update.build}.apk")
        val req = Request.Builder().url(update.apkUrl).header("User-Agent", "LiveTV-Updater").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Download failed (${r.code})")
            val body = r.body ?: throw IllegalStateException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        onProgress(1f)
        out
    }

    /** True when Android lets this app install updates (the "Install unknown apps" switch). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for this app. False when the TV has no such screen. */
    fun openInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent); true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Hands the downloaded APK to Android's installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
