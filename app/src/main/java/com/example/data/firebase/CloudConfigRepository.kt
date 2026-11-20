package com.example.data.firebase

import android.content.Context
import com.example.domain.model.CloudAppConfig
import com.example.domain.model.CloudCategoryItem
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.FamilyCloudConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Observes Spark-plan Firestore content & configuration documents (/app_config/live and
 * /families/{familyId}/config/live) so notices, categories, configurable labels, and supported
 * settings sync immediately across devices without requiring an APK update.
 */
class CloudConfigRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FamilyVaultRepository.getConfiguredFirestore(context.applicationContext),
        auth = Firebase.auth
    )

    fun observeGlobalCloudConfig(): Flow<CloudAppConfig?> = flow {
        if (auth.currentUser?.uid.isNullOrBlank()) {
            emit(null)
            return@flow
        }
        val path = "app_config/live"
        emitAll(
            db.collection("app_config").document("live")
                .snapshots()
                .map { snap -> parseGlobalConfig(snap) }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val exception = fsError ?: (error as? Exception)
                    if (exception != null && auth.currentUser != null) {
                        handleFirestoreError(exception, OperationType.GET, path, auth)
                    }
                    emit(null)
                }
        )
    }

    fun observeFamilyCloudConfig(familyId: String): Flow<FamilyCloudConfig?> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || familyId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "families/$familyId/config/live"
        emitAll(
            db.collection("families").document(familyId)
                .collection("config").document("live")
                .snapshots()
                .map { snap -> parseFamilyConfig(snap) }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val exception = fsError ?: (error as? Exception)
                    if (exception != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exception, OperationType.GET, path, auth)
                    }
                    emit(null)
                }
        )
    }

    fun observeEffectiveCloudConfig(familyId: String): Flow<EffectiveCloudConfig> {
        val globalFlow = observeGlobalCloudConfig()
        val familyFlow = if (familyId.isBlank()) flowOf(null) else observeFamilyCloudConfig(familyId)
        return combine(globalFlow, familyFlow) { globalCfg, familyCfg ->
            EffectiveCloudConfig.merge(globalCfg, familyCfg)
        }
    }

    suspend fun saveFamilyCloudConfig(
        familyId: String,
        noticeActive: Boolean,
        noticeTitleEn: String,
        noticeTitleKn: String,
        noticeBodyEn: String,
        noticeBodyKn: String,
        noticeLevel: String,
        homeBannerLabelEn: String,
        homeBannerLabelKn: String,
        extraCategories: List<CloudCategoryItem>,
        expiryWarningDays: Long,
        showSampleDocs: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in."))
        val configRef = db.collection("families").document(familyId)
            .collection("config").document("live")
        return try {
            val cleanLevel = when (noticeLevel) {
                "warning", "maintenance" -> noticeLevel
                else -> "info"
            }
            val cleanCats = extraCategories
                .filter { it.key.trim().length in 2..60 }
                .distinctBy { it.key.trim().lowercase() }
                .take(15)
                .map { cat ->
                    val k = cat.key.trim().take(60)
                    mapOf(
                        "key" to k,
                        "labelEn" to cat.labelEn.trim().ifBlank { k }.take(60),
                        "labelKn" to cat.labelKn.trim().ifBlank { k }.take(60)
                    )
                }

            val existingSnap = configRef.get().await()
            if (existingSnap.exists()) {
                configRef.update(
                    mapOf(
                        "noticeActive" to noticeActive,
                        "noticeTitleEn" to noticeTitleEn.trim().take(120),
                        "noticeTitleKn" to noticeTitleKn.trim().take(120),
                        "noticeBodyEn" to noticeBodyEn.trim().take(500),
                        "noticeBodyKn" to noticeBodyKn.trim().take(500),
                        "noticeLevel" to cleanLevel,
                        "homeBannerLabelEn" to homeBannerLabelEn.trim().take(160),
                        "homeBannerLabelKn" to homeBannerLabelKn.trim().take(160),
                        "extraCategories" to cleanCats,
                        "expiryWarningDays" to expiryWarningDays.coerceIn(1L, 365L),
                        "showSampleDocs" to showSampleDocs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                configRef.set(
                    mapOf(
                        "familyId" to familyId,
                        "adminUid" to uid,
                        "noticeActive" to noticeActive,
                        "noticeTitleEn" to noticeTitleEn.trim().take(120),
                        "noticeTitleKn" to noticeTitleKn.trim().take(120),
                        "noticeBodyEn" to noticeBodyEn.trim().take(500),
                        "noticeBodyKn" to noticeBodyKn.trim().take(500),
                        "noticeLevel" to cleanLevel,
                        "homeBannerLabelEn" to homeBannerLabelEn.trim().take(160),
                        "homeBannerLabelKn" to homeBannerLabelKn.trim().take(160),
                        "extraCategories" to cleanCats,
                        "expiryWarningDays" to expiryWarningDays.coerceIn(1L, 365L),
                        "showSampleDocs" to showSampleDocs,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, configRef.path, auth)
            Result.failure(e)
        }
    }

    private fun parseGlobalConfig(snap: DocumentSnapshot): CloudAppConfig? {
        if (!snap.exists()) return null
        val data = snap.data ?: return null
        return CloudAppConfig(
            configVersion = (data["configVersion"] as? Number)?.toLong() ?: 1L,
            noticeActive = data["noticeActive"] as? Boolean ?: false,
            noticeTitleEn = (data["noticeTitleEn"] as? String).orEmpty(),
            noticeTitleKn = (data["noticeTitleKn"] as? String).orEmpty(),
            noticeBodyEn = (data["noticeBodyEn"] as? String).orEmpty(),
            noticeBodyKn = (data["noticeBodyKn"] as? String).orEmpty(),
            noticeLevel = (data["noticeLevel"] as? String) ?: "info",
            homeBannerLabelEn = (data["homeBannerLabelEn"] as? String).orEmpty(),
            homeBannerLabelKn = (data["homeBannerLabelKn"] as? String).orEmpty(),
            extraCategories = parseRawCategoryMaps(data["extraCategories"]),
            expiryWarningDays = (data["expiryWarningDays"] as? Number)?.toLong() ?: 30L,
            showSampleDocs = data["showSampleDocs"] as? Boolean ?: true,
            minRecommendedVersionCode = (data["minRecommendedVersionCode"] as? Number)?.toLong() ?: 1L
        )
    }

    private fun parseFamilyConfig(snap: DocumentSnapshot): FamilyCloudConfig? {
        if (!snap.exists()) return null
        val data = snap.data ?: return null
        return FamilyCloudConfig(
            familyId = (data["familyId"] as? String).orEmpty(),
            adminUid = (data["adminUid"] as? String).orEmpty(),
            noticeActive = data["noticeActive"] as? Boolean ?: false,
            noticeTitleEn = (data["noticeTitleEn"] as? String).orEmpty(),
            noticeTitleKn = (data["noticeTitleKn"] as? String).orEmpty(),
            noticeBodyEn = (data["noticeBodyEn"] as? String).orEmpty(),
            noticeBodyKn = (data["noticeBodyKn"] as? String).orEmpty(),
            noticeLevel = (data["noticeLevel"] as? String) ?: "info",
            homeBannerLabelEn = (data["homeBannerLabelEn"] as? String).orEmpty(),
            homeBannerLabelKn = (data["homeBannerLabelKn"] as? String).orEmpty(),
            extraCategories = parseRawCategoryMaps(data["extraCategories"]),
            expiryWarningDays = (data["expiryWarningDays"] as? Number)?.toLong() ?: 30L,
            showSampleDocs = data["showSampleDocs"] as? Boolean ?: true,
            createdAt = snap.getTimestamp("createdAt"),
            updatedAt = snap.getTimestamp("updatedAt")
        )
    }

    private fun parseRawCategoryMaps(raw: Any?): List<Map<String, String>> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val key = (map["key"] as? String)?.trim().orEmpty()
            if (key.isBlank()) return@mapNotNull null
            mapOf(
                "key" to key,
                "labelEn" to ((map["labelEn"] as? String)?.trim().orEmpty().ifBlank { key }),
                "labelKn" to ((map["labelKn"] as? String)?.trim().orEmpty().ifBlank { key })
            )
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
