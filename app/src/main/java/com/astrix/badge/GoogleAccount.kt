package com.astrix.badge

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

const val GOOGLE_WEB_CLIENT_ID = "747737033958-67m8hdmqjsda19hptdusil3slps43lfa.apps.googleusercontent.com"

data class LinkedAccount(val name: String, val email: String)

// The linked account
object GoogleAccountStore {
    var account by mutableStateOf<LinkedAccount?>(null)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("google_email", null)
        account = if (email.isNullOrBlank()) {
            null
        } else {
            LinkedAccount(prefs.getString("google_name", "") ?: "", email)
        }
    }

    fun save(context: Context, linked: LinkedAccount) {
        context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE).edit {
            putString("google_name", linked.name)
            putString("google_email", linked.email)
        }
        account = linked
    }

    fun clear(context: Context) {
        context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE).edit {
            remove("google_name")
            remove("google_email")
        }
        account = null
    }
}

// Opens Google's account chooser
suspend fun linkGoogleAccount(activityContext: Context): Result<LinkedAccount> {
    val option = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            // google.id is the account's email address
            Result.success(LinkedAccount(google.displayName ?: "", google.id))
        } else {
            Result.failure(IllegalStateException("Unexpected credential type"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("BadgeGoogle", "Linking the Google account failed", e)
        Result.failure(e)
    }
}

suspend fun unlinkGoogleAccount(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("BadgeGoogle", "Clearing the Google sign-in state failed", e)
    }
    GoogleAccountStore.clear(context)
}

// A message for the user
fun googleErrorMessage(error: Throwable): String? = when (error) {
    is GetCredentialCancellationException -> null
    is NoCredentialException ->
        "No Google account found on this device. Add one in the device's settings and try again."
    is GetCredentialProviderConfigurationException ->
        "Google sign-in isn't available on this device. Check that Google Play services are installed and up to date."
    else -> "Couldn't link the account. Please try again."
}