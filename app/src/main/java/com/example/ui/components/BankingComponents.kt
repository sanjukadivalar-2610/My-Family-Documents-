package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.DocumentCategory
import com.example.domain.model.EffectiveCloudConfig
import com.example.domain.model.ExpiryStatus
import com.example.domain.model.FamilyDocument
import com.example.domain.model.SyncState
import com.example.localization.LocalAppStrings
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonSoft
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.NavyElevated
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateMillis(millis: Long): String {
    if (millis <= 0L) return "—"
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    }
}

@Composable
fun LanguageSwitcherPill(
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isKannada = currentLanguage == "kn"
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("language_switcher_pill"),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = "Language switcher",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 6.dp, end = 4.dp)
                    .size(16.dp)
            )
            LanguageChipOption(
                label = "EN",
                selected = !isKannada,
                onClick = { onSelectLanguage("en") },
                tag = "lang_option_en"
            )
            Spacer(modifier = Modifier.width(4.dp))
            LanguageChipOption(
                label = "ಕನ್ನಡ",
                selected = isKannada,
                onClick = { onSelectLanguage("kn") },
                tag = "lang_option_kn"
            )
        }
    }
}

@Composable
private fun LanguageChipOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val bg by animateColorAsState(
        targetValue = if (selected) NavyPrimary else Color.Transparent,
        label = "langBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "langFg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun BankingHeroGradientBrush(): Brush = Brush.linearGradient(
    colors = listOf(NavyPrimary, NavyElevated)
)

@Composable
fun BankingStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SyncStatusBadge(
    syncState: SyncState,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val (label, icon, bg, fg) = when (syncState) {
        SyncState.SYNCED -> Quad(strings.syncedStatus, Icons.Rounded.CloudDone, EmeraldSoft, Color(0xFF065F46))
        SyncState.SYNCING -> Quad(strings.syncingStatus, Icons.Rounded.CloudSync, MaterialTheme.colorScheme.primaryContainer, NavyPrimary)
        SyncState.OFFLINE -> Quad(strings.offlineStatus, Icons.Rounded.CloudOff, AmberSoft, Color(0xFF92400E))
        SyncState.ATTENTION_NEEDED -> Quad(strings.attentionStatus, Icons.Rounded.CloudQueue, CrimsonSoft, Color(0xFF991B1B))
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ExpiryStatusPill(
    document: FamilyDocument,
    warningDays: Long = 30L
) {
    val strings = LocalAppStrings.current
    val status = document.expiryStatus(warningDays = warningDays)
    if (status == ExpiryStatus.NO_EXPIRY) return
    val (label, bg, fg, icon) = when (status) {
        ExpiryStatus.EXPIRED -> Quad(strings.expiredBadge, CrimsonSoft, CrimsonAlert, Icons.Rounded.WarningAmber)
        ExpiryStatus.EXPIRING_SOON -> Quad(strings.expiringSoonBadge, AmberSoft, Color(0xFFB45309), Icons.Rounded.Schedule)
        ExpiryStatus.VALID -> Quad(strings.validBadge, EmeraldSoft, Color(0xFF047857), Icons.Rounded.VerifiedUser)
        ExpiryStatus.NO_EXPIRY -> return
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCardItem(
    document: FamilyDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cloudConfig: EffectiveCloudConfig = EffectiveCloudConfig(),
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val category = cloudConfig.resolveCategoryOption(document.category)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode && onToggleSelect != null) onToggleSelect() else onClick()
                },
                onLongClick = onLongClick
            )
            .testTag("document_card_${document.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SkyAccent.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) NavyPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect?.invoke() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("doc_card_checkbox_${document.id}")
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(category.tintColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.localizedLabel(strings.languageCode),
                    tint = category.tintColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (document.isSample) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SkyAccent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = strings.sampleDocBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${category.localizedLabel(strings.languageCode)} • ${document.memberName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = strings.encryptedBadge,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${document.fileType.uppercase()} • ${formatFileSize(document.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ExpiryStatusPill(
                        document = document,
                        warningDays = cloudConfig.expiryWarningDays
                    )
                    Icon(
                        imageVector = if (document.isBackedUp) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                        contentDescription = if (document.isBackedUp) strings.cloudSyncedBadge else strings.localOnlyBadge,
                        tint = if (document.isBackedUp) EmeraldVerified else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (!selectionMode && (onShareClick != null || onDownloadClick != null)) {
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (onShareClick != null) {
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("doc_card_share_${document.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = strings.docActions.shareAction,
                                tint = NavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onDownloadClick != null) {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("doc_card_download_${document.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = strings.docActions.downloadAction,
                                tint = NavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MemberAvatar(
    name: String,
    photoBase64: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, photoBase64) {
        value = if (photoBase64.isNullOrBlank()) null
        else withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(NavyPrimary, SkyAccent))
            ),
        contentAlignment = Alignment.Center
    ) {
        val decodedBitmap = bitmap
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            val initials = name.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
            if (initials.isNotEmpty()) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = name,
                    tint = PureWhite
                )
            }
        }
    }
}
