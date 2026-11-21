package com.astrix.badge

import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

// Small helpers
fun lastBackupTime(context: Context): Long =
    context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE).getLong("last_backup", 0L)

fun saveLastBackupTime(context: Context, millis: Long) {
    context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE).edit { putLong("last_backup", millis) }
}

fun formatBackupTime(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

fun parseDriveTime(text: String): Long =
    runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)

fun driveErrorMessage(e: Throwable): String = when (e) {
    is UnknownHostException, is SocketTimeoutException -> "No internet connection. Try again when you're online."
    is IOException if e.message?.contains("Drive error 403") == true ->
        "Google Drive refused the request. Check that the Drive API is turned on in Google Cloud."

    is IOException if e.message?.contains("Drive error 401") == true ->
        "Google needs you to allow access again. Please try once more."

    is IllegalArgumentException -> e.message ?: "That backup couldn't be read."
    else -> "Something went wrong. Please try again."
}

// Talking to Google Drive (plain web requests, no extra libraries)
object DriveBackup {
    private const val FILE_NAME = "badge-backup.json"
    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"

    class RemoteBackup(val id: String, val modifiedTime: String)

    private fun open(url: String, method: String, token: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        return connection
    }

    private fun HttpURLConnection.readBody(): String {
        val ok = responseCode in 200..299
        val text = (if (ok) inputStream else errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (!ok) throw IOException("Drive error $responseCode: $text")
        return text
    }

    suspend fun find(token: String): RemoteBackup? = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val fields = URLEncoder.encode("files(id,modifiedTime)", "UTF-8")
        val url = "$FILES_URL?spaces=appDataFolder&q=$query&orderBy=modifiedTime%20desc&pageSize=10&fields=$fields"
        val files = JSONObject(open(url, "GET", token).readBody()).getJSONArray("files")
        if (files.length() == 0) {
            null
        } else {
            val file = files.getJSONObject(0)
            RemoteBackup(file.getString("id"), file.optString("modifiedTime"))
        }
    }

    suspend fun upload(token: String, json: String): RemoteBackup = withContext(Dispatchers.IO) {
        val boundary = "badge_" + System.currentTimeMillis()
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("parents", JSONArray().put("appDataFolder"))
            .toString()
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n--$boundary--"
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,modifiedTime"
        val connection = open(url, "POST", token)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val response = JSONObject(connection.readBody())
        RemoteBackup(response.getString("id"), response.optString("modifiedTime"))
    }

    suspend fun download(token: String, id: String): String = withContext(Dispatchers.IO) {
        open("$FILES_URL/$id?alt=media", "GET", token).readBody()
    }

    suspend fun delete(token: String, id: String) {
        withContext(Dispatchers.IO) {
            val connection = open("$FILES_URL/$id", "DELETE", token)
            connection.responseCode   // a failed delete only leaves an old copy behind
            connection.disconnect()
        }
    }
}

// Asking Google for permission
private suspend fun authorizeDrive(context: Context, email: String): AuthorizationResult =
    suspendCancellableCoroutine { continuation ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .setAccount(android.accounts.Account(email, "com.google"))
            .build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

@Composable
fun rememberDriveRunner(
    email: String,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit
): (suspend (String) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pending
        pending = null
        if (action == null) {
            onBusy(false)
        } else {
            scope.launch {
                try {
                    val auth = Identity.getAuthorizationClient(context)
                        .getAuthorizationResultFromIntent(result.data)
                    val token = auth.accessToken
                    if (token == null) onError("Badge needs your permission to use its private backup space in Google Drive.")
                    else action(token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("BadgeBackup", "Drive action failed", e)
                    onError(driveErrorMessage(e))
                } finally {
                    onBusy(false)
                }
            }
        }
    }

    return { action ->
        scope.launch {
            onBusy(true)
            try {
                val auth = authorizeDrive(context, email)
                if (auth.hasResolution()) {
                    // First time: Google shows its permission screen
                    pending = action
                    launcher.launch(IntentSenderRequest.Builder(auth.pendingIntent!!.intentSender).build())
                } else {
                    val token = auth.accessToken
                    if (token == null) onError("Couldn't get permission to use Google Drive.") else action(token)
                    onBusy(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BadgeBackup", "Drive action failed", e)
                onError(driveErrorMessage(e))
                onBusy(false)
            }
        }
    }
}