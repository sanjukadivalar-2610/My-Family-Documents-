package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.FamilyAuthException
import com.example.data.firebase.FamilyAuthHttpResponse
import com.example.data.firebase.FamilyAuthHttpTransport
import com.example.data.firebase.FamilyAuthRepository
import com.example.data.security.DocumentCryptoManager
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FamilyCredentialAuthTest {

    private fun isolatedDeviceContext(base: Context, deviceName: String): Context {
        val deviceRoot = File(base.filesDir, "cred_sim_$deviceName").apply {
            deleteRecursively()
            mkdirs()
        }
        val cacheRoot = File(base.cacheDir, "cred_sim_${deviceName}_cache").apply {
            deleteRecursively()
            mkdirs()
        }
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = deviceRoot
            override fun getCacheDir(): File = cacheRoot
        }
    }

    @Test
    fun `admin credential setup sends verified bearer token and normalized username`() = runTest {
        var capturedBearer: String? = null
        var capturedPayload: JSONObject? = null
        val transport = FamilyAuthHttpTransport { _, bearerToken, bodyJson ->
            capturedBearer = bearerToken
            capturedPayload = JSONObject(bodyJson)
            FamilyAuthHttpResponse(
                statusCode = 200,
                body = JSONObject()
                    .put("ok", true)
                    .put("familyId", "fam_sharma_01")
                    .put("familyUsername", "sharma.family")
                    .toString()
            )
        }
        val repo = FamilyAuthRepository(
            transport = transport,
            idTokenProvider = { "admin_firebase_id_token_xyz" }
        )

        val result = repo.upsertFamilyCredentials(
            familyId = "fam_sharma_01",
            username = "  Sharma.Family  ",
            password = "VaultPass99"
        )

        assertTrue(result.isSuccess)
        assertEquals("sharma.family", result.getOrThrow())
        assertEquals("admin_firebase_id_token_xyz", capturedBearer)
        assertEquals("upsertFamilyCredentials", capturedPayload?.getString("action"))
        assertEquals("sharma.family", capturedPayload?.getString("username"))
    }

    @Test
    fun `family member login signs custom token and caches 256-bit vault key for two-device decrypt`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val adminCrypto = DocumentCryptoManager(isolatedDeviceContext(baseContext, "admin_phone_a"))
        val memberCrypto = DocumentCryptoManager(isolatedDeviceContext(baseContext, "member_phone_b"))

        val familyId = "fam_two_device_sync"
        val adminUid = "uid_google_admin"
        val sharedKeyBytes = ByteArray(32) { (it + 7).toByte() }
        val sharedKeyBase64 = Base64.getEncoder().encodeToString(sharedKeyBytes)

        adminCrypto.storeFamilyVaultKeyBase64(familyId, sharedKeyBase64)
        val secretDocBytes = "SENSITIVE_FAMILY_DEED_PDF".toByteArray(Charsets.UTF_8)
        adminCrypto.saveEncryptedDocumentForFamily("doc_deed_1", secretDocBytes, familyId, adminUid)
        val encryptedBlob = adminCrypto.readEncryptedPayloadBytes("doc_deed_1")!!

        var signedToken: String? = null
        val transport = FamilyAuthHttpTransport { _, _, bodyJson ->
            val req = JSONObject(bodyJson)
            assertEquals("loginWithFamilyUsername", req.getString("action"))
            assertEquals("priya.sharma", req.getString("username"))
            FamilyAuthHttpResponse(
                statusCode = 200,
                body = JSONObject()
                    .put("ok", true)
                    .put("customToken", "firebase_custom_token_priya")
                    .put("uid", "fmem_priya_001")
                    .put("familyId", familyId)
                    .put("familyName", "Sharma Family")
                    .put("displayName", "Priya Sharma")
                    .put("username", "priya.sharma")
                    .put("authMethod", "member_username")
                    .put("vaultKeyBase64", sharedKeyBase64)
                    .toString()
            )
        }

        val repo = FamilyAuthRepository(
            transport = transport,
            customTokenSigner = { token -> signedToken = token }
        )

        val loginRes = repo.loginWithFamilyUsername(
            username = "Priya.Sharma",
            password = "MemberPass42",
            deviceId = "device_phone_b",
            memberName = "Priya",
            cryptoManager = memberCrypto
        )

        assertTrue(loginRes.isSuccess)
        assertEquals("firebase_custom_token_priya", signedToken)
        assertEquals("fmem_priya_001", loginRes.getOrThrow().uid)
        assertTrue(memberCrypto.hasFamilyVaultKey(familyId))

        memberCrypto.writeRawEncryptedPayload("doc_deed_1", encryptedBlob)
        val decryptedOnPhoneB = memberCrypto.readDecryptedBytes("doc_deed_1", familyId, adminUid)
        assertArrayEquals(secretDocBytes, decryptedOnPhoneB)
    }

    @Test
    fun `wrong password and brute-force lockout surface clear authentication errors`() = runTest {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val crypto = DocumentCryptoManager(isolatedDeviceContext(baseContext, "wrong_pwd_phone"))

        val wrongPwdRepo = FamilyAuthRepository(
            transport = { _, _, _ ->
                FamilyAuthHttpResponse(
                    statusCode = 401,
                    body = JSONObject()
                        .put("ok", false)
                        .put("code", "INVALID_CREDENTIALS")
                        .put("error", "Invalid username or password.")
                        .toString()
                )
            }
        )
        val wrongRes = wrongPwdRepo.loginWithFamilyUsername("sharma.family", "WrongPass1", "dev1", "", crypto)
        assertTrue(wrongRes.isFailure)
        val wrongErr = wrongRes.exceptionOrNull() as FamilyAuthException
        assertEquals("INVALID_CREDENTIALS", wrongErr.code)

        val lockoutRepo = FamilyAuthRepository(
            transport = { _, _, _ ->
                FamilyAuthHttpResponse(
                    statusCode = 429,
                    body = JSONObject()
                        .put("ok", false)
                        .put("code", "TOO_MANY_ATTEMPTS")
                        .put("error", "Too many failed login attempts. Try again in 15 minutes.")
                        .toString()
                )
            }
        )
        val lockRes = lockoutRepo.loginWithFamilyUsername("sharma.family", "WrongPass1", "dev1", "", crypto)
        assertTrue(lockRes.isFailure)
        val lockErr = lockRes.exceptionOrNull() as FamilyAuthException
        assertEquals("TOO_MANY_ATTEMPTS", lockErr.code)
    }

    @Test
    fun `non-admin caller and undeployed backend errors are surfaced cleanly`() = runTest {
        val forbiddenRepo = FamilyAuthRepository(
            transport = { _, _, _ ->
                FamilyAuthHttpResponse(
                    statusCode = 403,
                    body = JSONObject()
                        .put("ok", false)
                        .put("code", "FORBIDDEN")
                        .put("error", "Only the Family Admin can perform this action.")
                        .toString()
                )
            },
            idTokenProvider = { "member_id_token" }
        )
        val forbidRes = forbiddenRepo.revokeMemberAccess("fam_01", "priya.sharma", "fmem_priya")
        assertTrue(forbidRes.isFailure)
        assertEquals("FORBIDDEN", (forbidRes.exceptionOrNull() as FamilyAuthException).code)

        val notDeployedRepo = FamilyAuthRepository(
            transport = { _, _, _ ->
                FamilyAuthHttpResponse(statusCode = 404, body = "<html>Not Found</html>")
            },
            idTokenProvider = { "admin_id_token" }
        )
        val listRes = notDeployedRepo.listFamilyCredentials("fam_01")
        assertTrue(listRes.isFailure)
        assertEquals("BACKEND_NOT_DEPLOYED", (listRes.exceptionOrNull() as FamilyAuthException).code)
    }

    @Test
    fun `listFamilyCredentials parses active and revoked member accounts`() = runTest {
        val repo = FamilyAuthRepository(
            transport = { _, _, _ ->
                val members = JSONArray()
                    .put(
                        JSONObject()
                            .put("username", "priya.sharma")
                            .put("memberId", "mem_1")
                            .put("memberName", "Priya")
                            .put("relation", "Spouse")
                            .put("linkedUid", "fmem_1")
                            .put("active", true)
                    )
                    .put(
                        JSONObject()
                            .put("username", "rohan.sharma")
                            .put("memberId", "mem_2")
                            .put("memberName", "Rohan")
                            .put("relation", "Child")
                            .put("linkedUid", "fmem_2")
                            .put("active", false)
                    )
                FamilyAuthHttpResponse(
                    statusCode = 200,
                    body = JSONObject()
                        .put("ok", true)
                        .put("familyId", "fam_01")
                        .put("familyUsername", "sharma.family")
                        .put("hasFamilyPassword", true)
                        .put("memberAccounts", members)
                        .toString()
                )
            },
            idTokenProvider = { "admin_id_token" }
        )

        val overview = repo.listFamilyCredentials("fam_01").getOrThrow()
        assertEquals("sharma.family", overview.familyUsername)
        assertTrue(overview.hasFamilyPassword)
        assertEquals(2, overview.memberAccounts.size)
        assertTrue(overview.memberAccounts[0].active)
        assertEquals(false, overview.memberAccounts[1].active)
    }
}
