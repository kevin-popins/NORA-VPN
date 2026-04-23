package com.privatevpn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AppAccent,
    onPrimary = AppSurface,
    primaryContainer = AppAccentPressed,
    secondary = AppAccentPressed,
    tertiary = AppAccent,
    background = AppBackground,
    onBackground = AppTextPrimary,
    surface = AppSurface,
    onSurface = AppTextPrimary,
    surfaceVariant = AppSurfaceSecondary,
    onSurfaceVariant = AppTextSecondary,
    outline = AppBorder,
    outlineVariant = AppBorder,
    error = AppDanger,
    onError = AppSurface
)

@Composable
fun PrivateVpnTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
