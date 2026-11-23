package com.astrix.badge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri

// Put your GitHub details here
const val GITHUB_OWNER = "astrixjeiko"
const val GITHUB_REPO = "Badge"
const val CREDITS_NAME = "Astrix"

class UpdateFileProvider : FileProvider()

class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

object Updates {
    var available by mutableStateOf<UpdateInfo?>(null)
    var checking by mutableStateOf(false)
    var progress by mutableStateOf<Float?>(null) // null means not downloading
    var message by mutableStateOf<String?>(null)

    fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: 0 }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // The "What's new" bullets of a release description, without the markdown marks
    fun whatsNew(body: String): String {
        val lines = body.lines()
        val start = lines.indexOfFirst { it.trim().startsWith("### What") }
        val section = if (start >= 0) {
            lines.drop(start + 1).takeWhile { !it.trim().startsWith("###") }
        } else {
            lines.filter { !it.startsWith("#") }
        }
        return section.joinToString("\n") {
            it.replace("**", "").replace(Regex("^\\s*[-*] "), "• ")
        }.trim().take(1200)
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Badge-app")
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode !in 200..299) throw IOException("GitHub error " + c.responseCode)
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.getString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (apkUrl == null) null
            else UpdateInfo(json.getString("tag_name"), json.optString("body"), apkUrl, size)
        } finally {
            c.disconnect()
        }
    }

    suspend fun check(context: Context, manual: Boolean) {
        if (checking) return
        checking = true
        if (manual) message = null
        try {
            val info = fetchLatest()
            context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
                .edit { putLong("last_update_check", System.currentTimeMillis()) }
            if (info != null && isNewer(info.version, currentVersion(context))) {
                available = info
            } else {
                available = null
                if (manual) message = "This version is up to date (v" + currentVersion(context) + ")."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BadgeUpdate", "Update check failed", e)
            if (manual) message = "Couldn't check for updates. Check your connection and try again."
        } finally {
            checking = false
        }
    }

    // Once a day at most, and only if the toggle in Settings is on
    suspend fun autoCheck(context: Context) {
        val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_update_check", true)) return
        val last = prefs.getLong("last_update_check", 0L)
        if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) return
        check(context, manual = false)
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo) {
        if (progress != null) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            message = "Allow Badge to install updates, then come back and tap Update again."
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    ("package:" + context.packageName).toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        message = null
        progress = 0f
        try {
            val file = withContext(Dispatchers.IO) { download(context, info) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BadgeUpdate", "Update failed", e)
            message = "The download failed. Please try again."
        } finally {
            progress = null
        }
    }

    private fun download(context: Context, info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Badge-update.apk")
        val c = URL(info.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        try {
            if (c.responseCode !in 200..299) throw IOException("Download error " + c.responseCode)
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            c.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) progress = (done.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (info.size > 0 && file.length() != info.size) throw IOException("Incomplete download")
        return file
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, onLater: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = Updates.progress
    val notes = remember(info) { Updates.whatsNew(info.notes) }

    AlertDialog(
        onDismissRequest = { if (progress == null) { Updates.message = null; onLater() } },
        title = { Text("Update available") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Badge " + info.version + " is ready. You have v" + Updates.currentVersion(context) + ".",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("What's new", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                Updates.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = progress == null,
                onClick = { scope.launch { Updates.downloadAndInstall(context, info) } }
            ) { Text(if (progress == null) "Update" else "Downloading…") }
        },
        dismissButton = {
            TextButton(
                enabled = progress == null,
                onClick = { Updates.message = null; onLater() }
            ) { Text("Later") }
        }
    )
}