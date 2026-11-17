package com.example.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class DocumentCategory(
    val storageKey: String,
    val labelEn: String,
    val labelKn: String,
    val icon: ImageVector,
    val tintColor: Color
) {
    AADHAAR(
        storageKey = "Aadhaar Card",
        labelEn = "Aadhaar Card",
        labelKn = "ಆಧಾರ್ ಕಾರ್ಡ್",
        icon = Icons.Rounded.Badge,
        tintColor = Color(0xFF2563EB)
    ),
    PAN(
        storageKey = "PAN Card",
        labelEn = "PAN Card",
        labelKn = "ಪ್ಯಾನ್ ಕಾರ್ಡ್",
        icon = Icons.Rounded.CreditCard,
        tintColor = Color(0xFF0284C7)
    ),
    VOTER_ID(
        storageKey = "Voter ID",
        labelEn = "Voter ID",
        labelKn = "ಮತದಾರರ ಗುರುತಿನ ಚೀಟಿ",
        icon = Icons.Rounded.HowToVote,
        tintColor = Color(0xFF4F46E5)
    ),
    RATION_CARD(
        storageKey = "Ration Card",
        labelEn = "Ration Card",
        labelKn = "ರೇಷನ್ ಕಾರ್ಡ್",
        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
        tintColor = Color(0xFF0D9488)
    ),
    DRIVING_LICENCE(
        storageKey = "Driving Licence",
        labelEn = "Driving Licence",
        labelKn = "ಚಾಲನಾ ಪರವಾನಗಿ",
        icon = Icons.Rounded.DirectionsCar,
        tintColor = Color(0xFFD97706)
    ),
    PASSPORT(
        storageKey = "Passport",
        labelEn = "Passport",
        labelKn = "ಪಾಸ್‌ಪೋರ್ಟ್",
        icon = Icons.Rounded.Flight,
        tintColor = Color(0xFF1D4ED8)
    ),
    EDUCATION(
        storageKey = "Education Documents",
        labelEn = "Education Documents",
        labelKn = "ಶೈಕ್ಷಣಿಕ ದಾಖಲೆಗಳು",
        icon = Icons.Rounded.School,
        tintColor = Color(0xFF7C3AED)
    ),
    BANK(
        storageKey = "Bank Documents",
        labelEn = "Bank Documents",
        labelKn = "ಬ್ಯಾಂಕ್ ದಾಖಲೆಗಳು",
        icon = Icons.Rounded.AccountBalance,
        tintColor = Color(0xFF059669)
    ),
    PROPERTY(
        storageKey = "Property Documents",
        labelEn = "Property Documents",
        labelKn = "ಆಸ್ತಿ ದಾಖಲೆಗಳು",
        icon = Icons.Rounded.HomeWork,
        tintColor = Color(0xFFB45309)
    ),
    MEDICAL(
        storageKey = "Medical Documents",
        labelEn = "Medical Documents",
        labelKn = "ವೈದ್ಯಕೀಯ ದಾಖಲೆಗಳು",
        icon = Icons.Rounded.LocalHospital,
        tintColor = Color(0xFFDC2626)
    ),
    CERTIFICATES(
        storageKey = "Certificates",
        labelEn = "Certificates",
        labelKn = "ಪ್ರಮಾಣಪತ್ರಗಳು",
        icon = Icons.Rounded.WorkspacePremium,
        tintColor = Color(0xFF0891B2)
    ),
    OTHER(
        storageKey = "Other Documents",
        labelEn = "Other Documents",
        labelKn = "ಇತರೆ ದಾಖಲೆಗಳು",
        icon = Icons.Rounded.FolderOpen,
        tintColor = Color(0xFF475569)
    );

    fun localizedLabel(languageCode: String): String =
        if (languageCode == "kn") labelKn else labelEn

    companion object {
        fun fromKey(key: String): DocumentCategory =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: OTHER
    }
}
