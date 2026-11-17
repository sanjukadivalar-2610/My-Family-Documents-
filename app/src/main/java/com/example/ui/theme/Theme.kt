package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.security.ThemeMode

private val LightColors = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = PureWhite,
    primaryContainer = SkySoft,
    onPrimaryContainer = NavyPrimary,
    secondary = SkyAccent,
    onSecondary = NavyDeep,
    secondaryContainer = ColorTokens.SkyTintContainer,
    onSecondaryContainer = NavyPrimary,
    tertiary = EmeraldVerified,
    onTertiary = PureWhite,
    background = AlabasterBackground,
    onBackground = SlateTextPrimary,
    surface = PureWhite,
    onSurface = SlateTextPrimary,
    surfaceVariant = ColorTokens.LightSurfaceVariant,
    onSurfaceVariant = SlateTextMuted,
    outline = SlateBorder,
    error = CrimsonAlert,
    onError = PureWhite,
    errorContainer = CrimsonSoft,
    onErrorContainer = CrimsonAlert
)

private val DarkColors = darkColorScheme(
    primary = SkyAccent,
    onPrimary = NavyDeep,
    primaryContainer = NavyElevated,
    onPrimaryContainer = PureWhite,
    secondary = SkyAccent,
    onSecondary = NavyDeep,
    secondaryContainer = SkyDarkTint,
    onSecondaryContainer = SkySoft,
    tertiary = EmeraldVerified,
    onTertiary = NavyDeep,
    background = ObsidianBackground,
    onBackground = LightTextPrimary,
    surface = ObsidianSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = LightTextMuted,
    outline = ObsidianBorder,
    error = CrimsonAlert,
    onError = PureWhite,
    errorContainer = ColorTokens.DarkErrorContainer,
    onErrorContainer = CrimsonSoft
)

private object ColorTokens {
    val SkyTintContainer = androidx.compose.ui.graphics.Color(0xFFBAE6FD)
    val LightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFF1F5F9)
    val DarkErrorContainer = androidx.compose.ui.graphics.Color(0xFF450A0A)
}

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyFamilyDocTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
