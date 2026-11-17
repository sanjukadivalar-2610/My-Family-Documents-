package com.example.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "my_family_doc_prefs")

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

data class SecuritySettings(
    val languageCode: String = "en",
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val pinEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val backupIntervalDays: Int = 1,
    val expiryRemindersEnabled: Boolean = true,
    val lastLocalBackupMillis: Long = 0L
)

class SecurityPreferences(private val context: Context) {

    val settingsFlow: Flow<SecuritySettings> = context.vaultDataStore.data.map { prefs ->
        SecuritySettings(
            languageCode = prefs[KEY_LANGUAGE] ?: "en",
            themeMode = ThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            pinEnabled = prefs[KEY_PIN_ENABLED] ?: false,
            pinHash = prefs[KEY_PIN_HASH] ?: "",
            pinSalt = prefs[KEY_PIN_SALT] ?: "",
            biometricEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false,
            autoBackupEnabled = prefs[KEY_AUTO_BACKUP] ?: true,
            backupIntervalDays = prefs[KEY_BACKUP_INTERVAL_DAYS] ?: 1,
            expiryRemindersEnabled = prefs[KEY_EXPIRY_REMINDERS] ?: true,
            lastLocalBackupMillis = prefs[KEY_LAST_BACKUP_MILLIS] ?: 0L
        )
    }

    suspend fun getOrCreateDeviceInstallationId(): String {
        val current = context.vaultDataStore.data.first()[KEY_DEVICE_INSTALLATION_ID]
        if (!current.isNullOrBlank()) return current
        val created = "dev_${UUID.randomUUID().toString().replace("-", "")}"
        context.vaultDataStore.edit { it[KEY_DEVICE_INSTALLATION_ID] = created }
        return created
    }

    suspend fun setLanguage(code: String) {
        context.vaultDataStore.edit { it[KEY_LANGUAGE] = if (code == "kn") "kn" else "en" }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.vaultDataStore.edit { it[KEY_THEME_MODE] = mode.key }
    }

    suspend fun setPin(pin: String) {
        val salt = DocumentCryptoManager.generateSalt()
        val hash = DocumentCryptoManager.hashPin(pin, salt)
        context.vaultDataStore.edit {
            it[KEY_PIN_SALT] = salt
            it[KEY_PIN_HASH] = hash
            it[KEY_PIN_ENABLED] = true
        }
    }

    suspend fun disablePin() {
        context.vaultDataStore.edit {
            it[KEY_PIN_ENABLED] = false
            it[KEY_PIN_HASH] = ""
            it[KEY_PIN_SALT] = ""
            it[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val current = settingsFlow.first()
        if (!current.pinEnabled || current.pinHash.isBlank()) return true
        return DocumentCryptoManager.hashPin(pin, current.pinSalt) == current.pinHash
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { it[KEY_AUTO_BACKUP] = enabled }
    }

    suspend fun setBackupIntervalDays(days: Int) {
        context.vaultDataStore.edit { it[KEY_BACKUP_INTERVAL_DAYS] = days.coerceIn(1, 30) }
    }

    suspend fun setExpiryRemindersEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { it[KEY_EXPIRY_REMINDERS] = enabled }
    }

    suspend fun updateLastBackupMillis(millis: Long) {
        context.vaultDataStore.edit { it[KEY_LAST_BACKUP_MILLIS] = millis }
    }

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language_code")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_AUTO_BACKUP = booleanPreferencesKey("auto_backup_enabled")
        val KEY_BACKUP_INTERVAL_DAYS = intPreferencesKey("backup_interval_days")
        val KEY_EXPIRY_REMINDERS = booleanPreferencesKey("expiry_reminders_enabled")
        val KEY_LAST_BACKUP_MILLIS = longPreferencesKey("last_backup_millis")
        val KEY_DEVICE_INSTALLATION_ID = stringPreferencesKey("device_installation_id")
    }
}
