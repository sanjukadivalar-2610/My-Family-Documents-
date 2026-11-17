package com.example.data.firebase

import com.example.BuildConfig
import com.example.data.security.DocumentCryptoManager
import com.example.domain.model.FamilyCredentialsOverview
import com.example.domain.model.FamilyLoginResult
import com.example.domain.model.MemberCredentialSummary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class FamilyAuthHttpResponse(
    val statusCode: Int,
    val body: String
)

fun interface FamilyAuthHttpTransport {
    suspend fun postJson(url: String, bearerToken: String?, bodyJson: String): FamilyAuthHttpResponse
}

class FamilyAuthException(
    val code: String,
    override val message: String
) : Exception(message)

class UrlConnectionFamilyAuthTransport : FamilyAuthHttpTransport {
    override suspend fun postJson(
        url: String,
        bearerToken: String?,
        bodyJson: String
    ): FamilyAuthHttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
        }
        try {
            connection.outputStream.use { os ->
                os.write(bodyJson.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream?.use { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).readText()
            }.orEmpty()
            FamilyAuthHttpResponse(statusCode = statusCode, body = responseText)
        } finally {
            connection.disconnect()
        }
    }
}

class FamilyAuthRepository(
    private val auth: FirebaseAuth? = null,
    private val endpointUrl: String = BuildConfig.FAMILY_AUTH_FUNCTION_URL,
    private val transport: FamilyAuthHttpTransport = UrlConnectionFamilyAuthTransport(),
    private val idTokenProvider: (suspend () -> String)? = null,
    private val customTokenSigner: (suspend (String) -> Unit)? = null
) {

    private fun resolveAuth(): FirebaseAuth = auth ?: Firebase.auth

    suspend fun loginWithFamilyUsername(
        username: String,
        password: String,
        deviceId: String,
        memberName: String,
        cryptoManager: DocumentCryptoManager
    ): Result<FamilyLoginResult> {
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.length < 3 || password.isEmpty()) {
            return Result.failure(
                FamilyAuthException("MISSING_FIELDS", "Enter a valid Family Username and Password.")
            )
        }
        return try {
            val payload = JSONObject().apply {
                put("action", "loginWithFamilyUsername")
                put("username", cleanUsername)
                put("password", password)
                put("deviceId", deviceId)
                if (memberName.trim().isNotEmpty()) {
                    put("memberName", memberName.trim().take(80))
                }
            }
            val response = transport.postJson(endpointUrl, null, payload.toString())
            val json = parseSuccessOrThrow(response)
            val result = FamilyLoginResult(
                customToken = json.getString("customToken"),
                uid = json.getString("uid"),
                familyId = json.getString("familyId"),
                familyName = json.optString("familyName", ""),
                displayName = json.optString("displayName", "Family Member"),
                username = json.optString("username", cleanUsername),
                authMethod = json.optString("authMethod", "family_username"),
                vaultKeyBase64 = json.optString("vaultKeyBase64", "")
            )
            if (result.vaultKeyBase64.isNotBlank()) {
                cryptoManager.storeFamilyVaultKeyBase64(result.familyId, result.vaultKeyBase64)
            }
            val signer = customTokenSigner
            if (signer != null) {
                signer(result.customToken)
            } else {
                resolveAuth().signInWithCustomToken(result.customToken).await()
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapNetworkOrServerException(e))
        }
    }

    suspend fun upsertFamilyCredentials(
        familyId: String,
        username: String,
        password: String
    ): Result<String> {
        return try {
            val idToken = requireAdminIdToken()
            val payload = JSONObject().apply {
                put("action", "upsertFamilyCredentials")
                put("familyId", familyId)
                put("username", username.trim().lowercase())
                put("password", password)
            }
            val response = transport.postJson(endpointUrl, idToken, payload.toString())
            val json = parseSuccessOrThrow(response)
            Result.success(json.optString("familyUsername", username.trim().lowercase()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapNetworkOrServerException(e))
        }
    }

    suspend fun resetFamilyPassword(
        familyId: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val idToken = requireAdminIdToken()
            val payload = JSONObject().apply {
                put("action", "resetFamilyPassword")
                put("familyId", familyId)
                put("newPassword", newPassword)
            }
            val response = transport.postJson(endpointUrl, idToken, payload.toString())
            parseSuccessOrThrow(response)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapNetworkOrServerException(e))
        }
    }

    suspend fun createMemberCredential(
        familyId: String,
        memberName: String,
        relation: String,
        memberUsername: String,
        password: String,
        existingMemberId: String? = null
    ): Result<MemberCredentialSummary> {
        return try {
            val idToken = requireAdminIdToken()
            val payload = JSONObject().apply {
                put("action", "createMemberCredential")
                put("familyId", familyId)
                put("memberName", memberName.trim().take(80))
                put("relation", relation.trim().ifBlank { "Member" }.take(40))
                put("memberUsername", memberUsername.trim().lowercase())
                put("password", password)
                if (!existingMemberId.isNullOrBlank()) {
                    put("existingMemberId", existingMemberId)
                }
            }
            val response = transport.postJson(endpointUrl, idToken, payload.toString())
            val json = parseSuccessOrThrow(response)
            Result.success(
                MemberCredentialSummary(
                    username = json.optString("memberUsername", memberUsername.trim().lowercase()),
                    memberId = json.optString("memberId", ""),
                    memberName = memberName.trim(),
                    relation = relation.trim().ifBlank { "Member" },
                    linkedUid = json.optString("memberUid", ""),
                    active = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapNetworkOrServerException(e))
        }
    }

    suspend fun resetMemberPassword(
        familyId: String,
        memberUsername: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val idToken = requireAdminIdToken()
            val payload = JSONObject().apply {
                put("action", "resetMemberPassword")
                put("familyId", familyId)
                put("memberUsername", memberUsername.trim().lowercase())
                put("newPassword", newPassword)
            }
            val response = transport.postJson(endpointUrl, idToken, payload.toString())
            parseSuccessOrThrow(response)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapNetworkOrServerException(e))
        }
    }

    suspend fun revokeMemberAccess(
        familyId: String,
        memberUsername: String?,
        memberUid: String?
    ): Result<Unit> {
        return try {
            val idToken = requireAdminIdToken()
            val payload = JSONObject().apply {
                put("action", "revokeMemberAccess")
                put("familyId", familyId)
                if (!memberUsername.isNullOrBlank()) put("memberUsername", memberUsername.trim().lowercase())
                if (!memberUid.isNullOrBlank()) put("memberUid", memberUid)
            }
            val response = transport.postJson(endpointUrl, idToken, payload.toString())
            parseSuccessOrThrow(response)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapNetworkOrServerException(e))
        }
    }

    suspend fun listFamilyCredentials(familyId: String): Result<FamilyCredentialsOverview> {
        return try {
            val idToken = requireAdminIdToken()
            val payload = JSONObject().apply {
                put("action", "listFamilyCredentials")
                put("familyId", familyId)
            }
            val response = transport.postJson(endpointUrl, idToken, payload.toString())
            val json = parseSuccessOrThrow(response)
            val arr = json.optJSONArray("memberAccounts")
            val members = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(
                            MemberCredentialSummary(
                                username = obj.optString("username", ""),
                                memberId = obj.optString("memberId", ""),
                                memberName = obj.optString("memberName", ""),
                                relation = obj.optString("relation", "Member"),
                                linkedUid = obj.optString("linkedUid", ""),
                                active = obj.optBoolean("active", false)
                            )
                        )
                    }
                }
            }
            Result.success(
                FamilyCredentialsOverview(
                    familyId = json.optString("familyId", familyId),
                    familyUsername = json.optString("familyUsername", ""),
                    hasFamilyPassword = json.optBoolean("hasFamilyPassword", false),
                    memberAccounts = members
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapNetworkOrServerException(e))
        }
    }

    private suspend fun requireAdminIdToken(): String {
        idTokenProvider?.let { return it() }
        val user = resolveAuth().currentUser
            ?: throw FamilyAuthException("UNAUTHENTICATED", "Sign in with Google Admin account first.")
        return user.getIdToken(false).await()?.token
            ?: throw FamilyAuthException("UNAUTHENTICATED", "Unable to verify Admin session token.")
    }

    private fun parseSuccessOrThrow(response: FamilyAuthHttpResponse): JSONObject {
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        if (response.statusCode in 200..299 && json != null) {
            return json
        }
        if (response.statusCode == 404) {
            throw FamilyAuthException(
                "BACKEND_NOT_DEPLOYED",
                "Family Auth Cloud Function is not deployed yet. Deploy `functions/` via Firebase CLI (`firebase deploy --only functions`)."
            )
        }
        val code = json?.optString("code").orEmpty().ifBlank { "HTTP_${response.statusCode}" }
        val message = json?.optString("error").orEmpty().ifBlank {
            "Authentication service returned status ${response.statusCode}."
        }
        throw FamilyAuthException(code, message)
    }

    private fun mapNetworkOrServerException(e: Exception): FamilyAuthException {
        if (e is FamilyAuthException) return e
        val msg = e.localizedMessage ?: "Unable to connect to authentication service."
        return FamilyAuthException("NETWORK_ERROR", msg)
    }
}
