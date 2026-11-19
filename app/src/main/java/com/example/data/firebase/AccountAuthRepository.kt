package com.example.data.firebase

import android.util.Patterns
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class AccountAuthException(
    val code: String,
    override val message: String
) : Exception(message)

sealed interface GoogleSignInOutcome {
    data class Authenticated(
        val user: FirebaseUser,
        val uid: String,
        val needsPasswordCreation: Boolean
    ) : GoogleSignInOutcome

    data class NeedsPasswordVerificationToLink(
        val email: String,
        val pendingGoogleCredential: AuthCredential,
        val message: String
    ) : GoogleSignInOutcome
}

class AccountAuthRepository(
    private val auth: FirebaseAuth = Firebase.auth
) {

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun hasEmailPasswordProvider(user: FirebaseUser? = auth.currentUser): Boolean {
        return user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true
    }

    fun hasGoogleProvider(user: FirebaseUser? = auth.currentUser): Boolean {
        return user?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true
    }

    suspend fun refreshPasswordLinkedState(): Boolean {
        val user = auth.currentUser ?: return false
        runCatching { user.reload().await() }
        return hasEmailPasswordProvider(auth.currentUser ?: user)
    }

    suspend fun completeGoogleSignIn(
        credential: AuthCredential,
        googleEmail: String? = null
    ): Result<GoogleSignInOutcome> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val signedInUser = auth.currentUser ?: result.user
                ?: return Result.failure(AccountAuthException("AUTH_FAILED", "Google Sign-In returned no user."))
            runCatching { signedInUser.reload().await() }
            val refreshed = auth.currentUser ?: signedInUser
            val needsPassword = !hasEmailPasswordProvider(refreshed)
            Result.success(
                GoogleSignInOutcome.Authenticated(
                    user = refreshed,
                    uid = refreshed.uid,
                    needsPasswordCreation = needsPassword
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            val conflictEmail = (e.email?.takeIf { it.isNotBlank() } ?: googleEmail)?.trim().orEmpty()
            if (e.errorCode == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" && conflictEmail.isNotBlank()) {
                Result.success(
                    GoogleSignInOutcome.NeedsPasswordVerificationToLink(
                        email = conflictEmail,
                        pendingGoogleCredential = credential,
                        message = "An existing account uses $conflictEmail. Enter your password to verify ownership and link Google Sign-In to the same account."
                    )
                )
            } else {
                Result.failure(
                    AccountAuthException(
                        code = "CREDENTIAL_CONFLICT",
                        message = e.localizedMessage
                            ?: "This Google account is already linked to another account."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException(
                    code = "GOOGLE_SIGN_IN_FAILED",
                    message = e.localizedMessage ?: "Google Sign-In failed."
                )
            )
        }
    }

    suspend fun linkPasswordToCurrentGoogleUser(password: String): Result<FirebaseUser> {
        val user = auth.currentUser
            ?: return Result.failure(AccountAuthException("UNAUTHENTICATED", "Sign in with Google first."))
        if (hasEmailPasswordProvider(user)) {
            return Result.success(user)
        }
        val email = user.email?.trim()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(AccountAuthException("MISSING_EMAIL", "Signed-in account does not have an email address."))
        if (!isStrongPassword(password)) {
            return Result.failure(
                AccountAuthException(
                    "WEAK_PASSWORD",
                    "Password must be at least 8 characters and include at least 1 letter and 1 number."
                )
            )
        }

        return try {
            val emailCred = EmailAuthProvider.getCredential(email, password)
            try {
                user.linkWithCredential(emailCred).await()
            } catch (collision: FirebaseAuthUserCollisionException) {
                val ownedEmail = auth.currentUser?.email?.trim()
                val sameEmailOwnedBySignedInGoogleUser =
                    collision.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" &&
                        ownedEmail != null &&
                        ownedEmail.equals(email, ignoreCase = true) &&
                        hasGoogleProvider(user) &&
                        !hasEmailPasswordProvider(user)
                if (sameEmailOwnedBySignedInGoogleUser) {
                    user.updatePassword(password).await()
                } else {
                    throw collision
                }
            }
            runCatching { user.reload().await() }
            val updated = auth.currentUser ?: user
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(
                AccountAuthException(
                    "REQUIRES_RECENT_LOGIN",
                    "Please re-verify with Google once before linking your password."
                )
            )
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(
                AccountAuthException(
                    "ACCOUNT_CONFLICT",
                    e.localizedMessage ?: "An account with this email credential already exists."
                )
            )
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(
                AccountAuthException(
                    "WEAK_PASSWORD",
                    e.localizedMessage ?: "Choose a stronger password."
                )
            )
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException(
                    "LINK_FAILED",
                    e.localizedMessage ?: "Unable to link password to your Google account."
                )
            )
        }
    }

    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        displayName: String? = null
    ): Result<FirebaseUser> {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(AccountAuthException("INVALID_EMAIL", "Enter a valid email address."))
        }
        if (!isStrongPassword(password)) {
            return Result.failure(
                AccountAuthException(
                    "WEAK_PASSWORD",
                    "Password must be at least 8 characters and include at least 1 letter and 1 number."
                )
            )
        }

        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user
                ?: return Result.failure(AccountAuthException("SIGN_UP_FAILED", "Account creation returned no user."))
            if (!displayName.isNullOrBlank()) {
                runCatching {
                    user.updateProfile(
                        userProfileChangeRequest { this.displayName = displayName.trim().take(80) }
                    ).await()
                }
            }
            runCatching { user.sendEmailVerification().await() }
            Result.success(auth.currentUser ?: user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(
                AccountAuthException(
                    "EMAIL_ALREADY_IN_USE",
                    "An account already exists with $cleanEmail. Sign in with your password or Google to access your existing account."
                )
            )
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(
                AccountAuthException("WEAK_PASSWORD", e.localizedMessage ?: "Password is too weak.")
            )
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException("SIGN_UP_FAILED", e.localizedMessage ?: "Unable to create account.")
            )
        }
    }

    suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail) || password.isEmpty()) {
            return Result.failure(AccountAuthException("MISSING_FIELDS", "Enter your email address and password."))
        }

        return try {
            val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user
                ?: return Result.failure(AccountAuthException("LOGIN_FAILED", "Authentication returned no user."))
            runCatching { user.reload().await() }
            Result.success(auth.currentUser ?: user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(AccountAuthException("INVALID_CREDENTIALS", "Incorrect email or password."))
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(AccountAuthException("USER_NOT_FOUND", "No account found with this email."))
        } catch (e: Exception) {
            Result.failure(AccountAuthException("LOGIN_FAILED", e.localizedMessage ?: "Unable to sign in."))
        }
    }

    suspend fun verifyPasswordAndLinkGoogleCredential(
        email: String,
        password: String,
        pendingGoogleCredential: AuthCredential
    ): Result<FirebaseUser> {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail) || password.isEmpty()) {
            return Result.failure(AccountAuthException("MISSING_FIELDS", "Enter your account password to verify ownership."))
        }

        return try {
            // Step 1: Verify ownership of the existing Email/Password account
            val signInResult = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val existingUser = signInResult.user
                ?: return Result.failure(AccountAuthException("LOGIN_FAILED", "Unable to verify existing account."))

            // Step 2: Link the previously verified Google credential to the SAME Firebase UID
            existingUser.linkWithCredential(pendingGoogleCredential).await()
            runCatching { existingUser.reload().await() }
            Result.success(auth.currentUser ?: existingUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(AccountAuthException("INVALID_CREDENTIALS", "Incorrect password. Account linking was not performed."))
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(
                AccountAuthException(
                    "CREDENTIAL_ALREADY_IN_USE",
                    "This Google account is already linked to a different account. No duplicate account or data transfer was performed."
                )
            )
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException("LINK_FAILED", e.localizedMessage ?: "Unable to verify and link Google Sign-In.")
            )
        }
    }

    suspend fun linkGoogleCredentialToSignedInUser(
        googleCredential: AuthCredential,
        googleEmail: String? = null
    ): Result<FirebaseUser> {
        val user = auth.currentUser
            ?: return Result.failure(AccountAuthException("UNAUTHENTICATED", "Sign in first before linking Google."))
        val currentEmail = user.email?.trim()
        val chosenEmail = googleEmail?.trim()
        if (!currentEmail.isNullOrBlank() && !chosenEmail.isNullOrBlank() &&
            !currentEmail.equals(chosenEmail, ignoreCase = true)
        ) {
            return Result.failure(
                AccountAuthException(
                    "EMAIL_MISMATCH",
                    "Select the same Google email ($currentEmail) to link to your existing account."
                )
            )
        }
        if (hasGoogleProvider(user)) {
            return Result.success(user)
        }
        return try {
            val res = user.linkWithCredential(googleCredential).await()
            val updated = res.user ?: user
            runCatching { updated.reload().await() }
            Result.success(auth.currentUser ?: updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(
                AccountAuthException(
                    "CREDENTIAL_ALREADY_IN_USE",
                    "This Google account is already associated with a different Firebase user."
                )
            )
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(
                AccountAuthException(
                    "REQUIRES_RECENT_LOGIN",
                    "Log out and sign back in with your password, then link Google again."
                )
            )
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException("LINK_FAILED", e.localizedMessage ?: "Unable to link Google account.")
            )
        }
    }

    suspend fun reauthenticateCurrentUserWithGoogle(googleCredential: AuthCredential): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(AccountAuthException("UNAUTHENTICATED", "User is not signed in."))
        return try {
            user.reauthenticate(googleCredential).await()
            runCatching { user.reload().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException("REAUTH_FAILED", e.localizedMessage ?: "Google re-verification failed.")
            )
        }
    }

    suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(AccountAuthException("UNAUTHENTICATED", "User is not signed in."))
        val email = user.email?.trim()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(AccountAuthException("MISSING_EMAIL", "Signed-in account does not have an email address."))
        if (password.isEmpty()) {
            return Result.failure(AccountAuthException("MISSING_PASSWORD", "Enter your current password to continue."))
        }
        return try {
            val cred = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(cred).await()
            runCatching { user.reload().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(AccountAuthException("INVALID_CREDENTIALS", "Incorrect password. Verification failed."))
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException("REAUTH_FAILED", e.localizedMessage ?: "Password verification failed.")
            )
        }
    }

    fun isRecentLoginFresh(maxAgeMillis: Long = DEFAULT_RECENT_LOGIN_MAX_AGE_MS): Boolean {
        val user = auth.currentUser ?: return false
        val lastSignInMs = user.metadata?.lastSignInTimestamp ?: 0L
        if (lastSignInMs <= 0L) return false
        val age = System.currentTimeMillis() - lastSignInMs
        return age in 0L..maxAgeMillis
    }

    suspend fun deleteCurrentFirebaseAuthUser(): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(AccountAuthException("UNAUTHENTICATED", "No signed-in Firebase account."))
        return try {
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(
                AccountAuthException(
                    "REQUIRES_RECENT_LOGIN",
                    "Please re-authenticate to confirm permanent account deletion."
                )
            )
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException(
                    "AUTH_DELETE_FAILED",
                    e.localizedMessage ?: "Failed to delete Firebase Authentication account."
                )
            )
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(AccountAuthException("INVALID_EMAIL", "Enter a valid email address."))
        }
        return try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                AccountAuthException("RESET_FAILED", e.localizedMessage ?: "Unable to send password reset email.")
            )
        }
    }

    companion object {
        const val DEFAULT_RECENT_LOGIN_MAX_AGE_MS = 4 * 60 * 1000L

        fun isValidEmail(email: String): Boolean {
            val clean = email.trim()
            return clean.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(clean).matches()
        }

        fun isStrongPassword(password: String): Boolean =
            password.length in 8..128 &&
                password.any { it.isLetter() } &&
                password.any { it.isDigit() }
    }
}
