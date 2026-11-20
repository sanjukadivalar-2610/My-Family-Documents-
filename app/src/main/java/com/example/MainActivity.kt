package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.security.SecurityPreferences
import com.example.data.security.SecuritySettings
import com.example.ui.MyFamilyDocApp
import com.example.ui.theme.MyFamilyDocTheme

class MainActivity : FragmentActivity() {

    private var pendingVaultLinkUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingVaultLinkUri = intent?.data
        setContent {
            val securityPreferences = remember(applicationContext) {
                SecurityPreferences(applicationContext)
            }
            val securitySettings by securityPreferences.settingsFlow
                .collectAsStateWithLifecycle(initialValue = SecuritySettings())

            MyFamilyDocTheme(themeMode = securitySettings.themeMode) {
                MyFamilyDocApp(
                    securityPreferences = securityPreferences,
                    securitySettings = securitySettings,
                    pendingVaultLinkUri = pendingVaultLinkUri,
                    onConsumeVaultLink = { pendingVaultLinkUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingVaultLinkUri = intent.data
    }
}
