package com.example.domain.model

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.NavyPrimary
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class CloudCategoryItem(
    val key: String = "",
    val labelEn: String = "",
    val labelKn: String = ""
)

@Keep
@IgnoreExtraProperties
data class CloudAppConfig(
    val configVersion: Long = 1L,
    val noticeActive: Boolean = false,
    val noticeTitleEn: String = "",
    val noticeTitleKn: String = "",
    val noticeBodyEn: String = "",
    val noticeBodyKn: String = "",
    val noticeLevel: String = "info",
    val homeBannerLabelEn: String = "",
    val homeBannerLabelKn: String = "",
    val extraCategories: List<Map<String, String>> = emptyList(),
    val expiryWarningDays: Long = 30L,
    val showSampleDocs: Boolean = true,
    val minRecommendedVersionCode: Long = 1L
)

@Keep
@IgnoreExtraProperties
data class FamilyCloudConfig(
    val familyId: String = "",
    val adminUid: String = "",
    val noticeActive: Boolean = false,
    val noticeTitleEn: String = "",
    val noticeTitleKn: String = "",
    val noticeBodyEn: String = "",
    val noticeBodyKn: String = "",
    val noticeLevel: String = "info",
    val homeBannerLabelEn: String = "",
    val homeBannerLabelKn: String = "",
    val extraCategories: List<Map<String, String>> = emptyList(),
    val expiryWarningDays: Long = 30L,
    val showSampleDocs: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class CategoryOption(
    val storageKey: String,
    val labelEn: String,
    val labelKn: String,
    val icon: ImageVector,
    val tintColor: Color,
    val isCloudSynced: Boolean = false
) {
    fun localizedLabel(languageCode: String): String =
        if (languageCode == "kn" && labelKn.isNotBlank()) labelKn
        else labelEn.ifBlank { storageKey }
}

data class EffectiveCloudConfig(
    val isSyncedFromCloud: Boolean = false,
    val configVersion: Long = 1L,
    val noticeActive: Boolean = false,
    val noticeTitleEn: String = "",
    val noticeTitleKn: String = "",
    val noticeBodyEn: String = "",
    val noticeBodyKn: String = "",
    val noticeLevel: String = "info",
    val homeBannerLabelEn: String = "",
    val homeBannerLabelKn: String = "",
    val extraCategories: List<CloudCategoryItem> = emptyList(),
    val expiryWarningDays: Long = 30L,
    val showSampleDocs: Boolean = true,
    val minRecommendedVersionCode: Long = 1L
) {
    fun allCategoryOptions(): List<CategoryOption> {
        val builtIn = DocumentCategory.entries.map {
            CategoryOption(
                storageKey = it.storageKey,
                labelEn = it.labelEn,
                labelKn = it.labelKn,
                icon = it.icon,
                tintColor = it.tintColor,
                isCloudSynced = false
            )
        }
        val builtInKeys = builtIn.map { it.storageKey.lowercase() }.toSet()
        val cloudAdded = extraCategories
            .filter { it.key.isNotBlank() && it.key.lowercase() !in builtInKeys }
            .distinctBy { it.key.lowercase() }
            .map { item ->
                CategoryOption(
                    storageKey = item.key,
                    labelEn = item.labelEn.ifBlank { item.key },
                    labelKn = item.labelKn.ifBlank { item.labelEn.ifBlank { item.key } },
                    icon = Icons.Rounded.BookmarkAdded,
                    tintColor = NavyPrimary,
                    isCloudSynced = true
                )
            }
        return builtIn + cloudAdded
    }

    fun resolveCategoryOption(categoryKey: String): CategoryOption {
        return allCategoryOptions().firstOrNull {
            it.storageKey.equals(categoryKey, ignoreCase = true)
        } ?: DocumentCategory.fromKey(categoryKey).let {
            CategoryOption(
                storageKey = categoryKey.ifBlank { it.storageKey },
                labelEn = categoryKey.ifBlank { it.labelEn },
                labelKn = if (categoryKey.isBlank()) it.labelKn else categoryKey,
                icon = it.icon,
                tintColor = it.tintColor,
                isCloudSynced = false
            )
        }
    }

    fun localizedNoticeTitle(languageCode: String): String =
        if (languageCode == "kn") noticeTitleKn.ifBlank { noticeTitleEn } else noticeTitleEn.ifBlank { noticeTitleKn }

    fun localizedNoticeBody(languageCode: String): String =
        if (languageCode == "kn") noticeBodyKn.ifBlank { noticeBodyEn } else noticeBodyEn.ifBlank { noticeBodyKn }

    fun localizedHomeBannerLabel(languageCode: String): String =
        if (languageCode == "kn") homeBannerLabelKn.ifBlank { homeBannerLabelEn } else homeBannerLabelEn.ifBlank { homeBannerLabelKn }

    companion object {
        fun merge(globalConfig: CloudAppConfig?, familyConfig: FamilyCloudConfig?): EffectiveCloudConfig {
            val globalCats = parseCategories(globalConfig?.extraCategories.orEmpty())
            val familyCats = parseCategories(familyConfig?.extraCategories.orEmpty())
            val mergedCats = (globalCats + familyCats).distinctBy { it.key.lowercase() }.take(15)

            val useFamilyNotice = familyConfig?.noticeActive == true &&
                (familyConfig.noticeBodyEn.isNotBlank() || familyConfig.noticeBodyKn.isNotBlank())
            val useGlobalNotice = globalConfig?.noticeActive == true &&
                (globalConfig.noticeBodyEn.isNotBlank() || globalConfig.noticeBodyKn.isNotBlank())

            val noticeActive = useFamilyNotice || useGlobalNotice
            val noticeTitleEn = if (useFamilyNotice) familyConfig!!.noticeTitleEn else globalConfig?.noticeTitleEn.orEmpty()
            val noticeTitleKn = if (useFamilyNotice) familyConfig!!.noticeTitleKn else globalConfig?.noticeTitleKn.orEmpty()
            val noticeBodyEn = if (useFamilyNotice) familyConfig!!.noticeBodyEn else globalConfig?.noticeBodyEn.orEmpty()
            val noticeBodyKn = if (useFamilyNotice) familyConfig!!.noticeBodyKn else globalConfig?.noticeBodyKn.orEmpty()
            val noticeLevel = if (useFamilyNotice) familyConfig!!.noticeLevel else globalConfig?.noticeLevel ?: "info"

            val bannerEn = familyConfig?.homeBannerLabelEn?.takeIf { it.isNotBlank() }
                ?: globalConfig?.homeBannerLabelEn.orEmpty()
            val bannerKn = familyConfig?.homeBannerLabelKn?.takeIf { it.isNotBlank() }
                ?: globalConfig?.homeBannerLabelKn.orEmpty()

            val warningDays = (familyConfig?.expiryWarningDays ?: globalConfig?.expiryWarningDays ?: 30L)
                .coerceIn(1L, 365L)

            val showSamples = when {
                familyConfig != null -> familyConfig.showSampleDocs
                globalConfig != null -> globalConfig.showSampleDocs
                else -> true
            }

            return EffectiveCloudConfig(
                isSyncedFromCloud = globalConfig != null || familyConfig != null,
                configVersion = globalConfig?.configVersion ?: 1L,
                noticeActive = noticeActive,
                noticeTitleEn = noticeTitleEn,
                noticeTitleKn = noticeTitleKn,
                noticeBodyEn = noticeBodyEn,
                noticeBodyKn = noticeBodyKn,
                noticeLevel = noticeLevel,
                homeBannerLabelEn = bannerEn,
                homeBannerLabelKn = bannerKn,
                extraCategories = mergedCats,
                expiryWarningDays = warningDays,
                showSampleDocs = showSamples,
                minRecommendedVersionCode = globalConfig?.minRecommendedVersionCode ?: 1L
            )
        }

        private fun parseCategories(rawList: List<Map<String, String>>): List<CloudCategoryItem> {
            return rawList.mapNotNull { map ->
                val key = map["key"]?.trim().orEmpty()
                if (key.length < 2 || key.length > 60) null
                else CloudCategoryItem(
                    key = key,
                    labelEn = map["labelEn"]?.trim().orEmpty().ifBlank { key }.take(60),
                    labelKn = map["labelKn"]?.trim().orEmpty().ifBlank { key }.take(60)
                )
            }
        }
    }
}
