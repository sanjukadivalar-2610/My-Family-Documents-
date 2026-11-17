package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.security.SecurityPreferences
import com.example.data.security.SecuritySettings
import com.example.ui.MyFamilyDocApp
import com.example.ui.theme.MyFamilyDocTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val securityPreferences = remember(applicationContext) {
                SecurityPreferences(applicationContext)
            }
            val securitySettings by securityPreferences.settingsFlow
                .collectAsStateWithLifecycle(initialValue = SecuritySettings())

            MyFamilyDocTheme(themeMode = securitySettings.themeMode) {
                MyFamilyDocApp(
                    securityPreferences = securityPreferences,
                    securitySettings = securitySettings
                )
            }
        }
    }
}
