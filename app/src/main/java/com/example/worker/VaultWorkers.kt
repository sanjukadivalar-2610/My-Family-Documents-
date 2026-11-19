package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.firebase.FamilyVaultRepository
import com.example.data.security.DocumentCryptoManager
import com.example.data.security.SecurityPreferences
import com.example.domain.model.ExpiryStatus
import com.example.domain.model.FamilyDocument
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object ExpiryNotificationHelper {
    private const val CHANNEL_ID = "doc_expiry_reminders"
    private const val NOTIFICATION_ID = 4401

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Document Expiry Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for family documents expiring within 30 days"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyExpiringDocuments(context: Context, docs: List<FamilyDocument>, languageCode: String): Int {
        val now = System.currentTimeMillis()
        val alerting = docs.filter {
            val st = it.expiryStatus(now)
            st == ExpiryStatus.EXPIRING_SOON || st == ExpiryStatus.EXPIRED
        }
        if (alerting.isEmpty()) return 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return alerting.size
        }

        ensureChannel(context)
        val title = if (languageCode == "kn") {
            "ದಾಖಲೆ ಮುಕ್ತಾಯ ಎಚ್ಚರಿಕೆ (${alerting.size})"
        } else {
            "Document Expiry Alert (${alerting.size})"
        }
        val firstDoc = alerting.first()
        val body = if (languageCode == "kn") {
            "${firstDoc.title} ಮತ್ತು ಇತರೆ ದಾಖಲೆಗಳ ಅವಧಿ ಪರಿಶೀಲಿಸಿ."
        } else {
            "Check ${firstDoc.title}${if (alerting.size > 1) " and ${alerting.size - 1} more" else ""}."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return alerting.size
    }
}

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
        val isPendingSyncRun = inputData.getBoolean(KEY_IMMEDIATE_PENDING_SYNC, false)
        val prefs = SecurityPreferences(applicationContext)
        val settings = prefs.settingsFlow.first()
        if (!isPendingSyncRun && !settings.autoBackupEnabled) return Result.success()

        val repo = FamilyVaultRepository(applicationContext)
        val crypto = DocumentCryptoManager(applicationContext)
        val userProfile = runCatching { repo.observeUserProfile(uid).first() }.getOrNull() ?: return Result.success()
        val familyId = userProfile.familyId
        if (familyId.isBlank()) return Result.success()

        val family = runCatching { repo.observeFamilyAccount(familyId).first() }.getOrNull()
        val docs = runCatching { repo.observePermittedDocuments(familyId, uid).first() }.getOrNull().orEmpty()
        var uploadedAny = false
        var hadFailure = false

        docs.filter { (!it.isBackedUp || it.storagePath.isNullOrBlank()) && !it.isSample }.forEach { doc ->
            val adminUid = family?.adminUid ?: doc.adminUid
            val payload = runCatching {
                if (adminUid.isNotBlank()) {
                    crypto.reencryptLegacyPayloadForFamilyIfNeeded(doc.id, familyId, adminUid)
                } else {
                    crypto.readEncryptedPayloadBytes(doc.id)
                }
            }.getOrNull()
            if (payload != null) {
                val res = repo.uploadEncryptedPayloadToStorage(familyId, doc.id, payload)
                if (res.isSuccess) uploadedAny = true else hadFailure = true
            }
        }
        if (uploadedAny) {
            repo.markBackupTimestamp(familyId)
            prefs.updateLastBackupMillis(System.currentTimeMillis())
        }
        if (settings.expiryRemindersEnabled) {
            ExpiryNotificationHelper.notifyExpiringDocuments(applicationContext, docs, settings.languageCode)
        }
        return if (hadFailure && isPendingSyncRun) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_IMMEDIATE_PENDING_SYNC = "immediate_pending_sync"
    }
}

object VaultWorkScheduler {
    private const val AUTO_BACKUP_WORK = "my_family_doc_auto_backup"
    private const val PENDING_CLOUD_SYNC_WORK = "my_family_doc_pending_sync"

    fun enqueuePendingSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .setInputData(
                androidx.work.workDataOf(AutoBackupWorker.KEY_IMMEDIATE_PENDING_SYNC to true)
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PENDING_CLOUD_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleAutoBackup(context: Context, enabled: Boolean, intervalDays: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(AUTO_BACKUP_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            intervalDays.coerceAtLeast(1).toLong(),
            TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            AUTO_BACKUP_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAll(context: Context) {
        runCatching {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(AUTO_BACKUP_WORK)
            workManager.cancelUniqueWork(PENDING_CLOUD_SYNC_WORK)
        }
    }
}
