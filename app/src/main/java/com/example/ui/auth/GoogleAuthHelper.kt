package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
    forceAccountChooser: Boolean = false
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Activity context required for Google Sign-In")

            if (forceAccountChooser) {
                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            }

            val result = try {
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val primaryRequest = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
                credentialManager.getCredential(activity, primaryRequest)
            } catch (primaryError: GetCredentialException) {
                if (primaryError is GetCredentialCancellationException) {
                    throw primaryError
                }
                Log.w("Auth", "Primary GetSignInWithGoogleOption failed, trying GetGoogleIdOption fallback", primaryError)
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder().addCredentialOption(fallbackOption).build()
                credentialManager.getCredential(activity, fallbackRequest)
            }

            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type: ${credential.type}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            val rawMsg = e.errorMessage?.toString()?.trim().orEmpty()
            Log.w("Auth", "Google Sign-In flow cancelled/dismissed: $rawMsg", e)
            val looksLikeSystemBlockOrError = rawMsg.contains("blocked", ignoreCase = true) ||
                rawMsg.contains("too many", ignoreCase = true) ||
                rawMsg.contains("developer", ignoreCase = true) ||
                rawMsg.contains("config", ignoreCase = true) ||
                rawMsg.contains("error", ignoreCase = true) ||
                rawMsg.contains("fail", ignoreCase = true) ||
                rawMsg.contains("28444") ||
                rawMsg.contains("10:") ||
                rawMsg.contains("16:")
            val isExplicitUserDismiss = !looksLikeSystemBlockOrError && (
                rawMsg.isBlank() ||
                    rawMsg.contains("user", ignoreCase = true) ||
                    rawMsg.contains("canceled", ignoreCase = true) ||
                    rawMsg.contains("cancelled", ignoreCase = true)
                )
            if (isExplicitUserDismiss) {
                onAuthCancelled()
            } else {
                val sha1 = getAppSha1Fingerprint(context)
                onAuthError("Google Sign-In stopped: $rawMsg\nApp SHA-1: $sha1")
            }
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Google Sign-In credential error: ${e.type}", e)
            val sha1 = getAppSha1Fingerprint(context)
            val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: e.localizedMessage ?: e.type
            onAuthError("Google Sign-In failed: $detail\nApp SHA-1: $sha1")
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            val sha1 = getAppSha1Fingerprint(context)
            onAuthError("${e.localizedMessage ?: "Sign in failed"}\nApp SHA-1: $sha1")
        }
    }
}

@Suppress("DEPRECATION")
private fun getAppSha1Fingerprint(context: Context): String {
    return try {
        val pkg = context.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            if (signingInfo == null) emptyArray()
            else if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
        }
        val certBytes = signatures.firstOrNull()?.toByteArray() ?: return "unknown"
        val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
        digest.joinToString(":") { "%02X".format(it) }
    } catch (_: Exception) {
        "unknown"
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
