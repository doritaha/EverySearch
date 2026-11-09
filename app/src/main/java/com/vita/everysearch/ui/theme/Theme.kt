package com.vita.everysearch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFF8B8CFF),
    onPrimary = Color(0xFF14123A),
    primaryContainer = Color(0xFF2E2C7A),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Color(0xFF4DD8E6),
    secondaryContainer = Color(0xFF0E4A52),
    onSecondaryContainer = Color(0xFFCFF7FB),
    background = Color(0xFF0F1220),
    onBackground = Color(0xFFE6E7F2),
    surface = Color(0xFF0F1220),
    onSurface = Color(0xFFE6E7F2),
    surfaceVariant = Color(0xFF242842),
    onSurfaceVariant = Color(0xFFB4B7CC),
    surfaceContainerLowest = Color(0xFF0A0C17),
    surfaceContainerLow = Color(0xFF151829),
    surfaceContainer = Color(0xFF1A1D30),
    surfaceContainerHigh = Color(0xFF212539),
    surfaceContainerHighest = Color(0xFF292D43),
    outline = Color(0xFF4A4F6E),
    outlineVariant = Color(0xFF2E3250),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF16124F),
    secondary = Color(0xFF0891B2),
    secondaryContainer = Color(0xFFCCF3FA),
    onSecondaryContainer = Color(0xFF00363F),
    background = Color(0xFFF7F7FC),
    surface = Color(0xFFF7F7FC),
    surfaceVariant = Color(0xFFE4E4F0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F1F9),
    surfaceContainer = Color(0xFFEBEBF5),
    surfaceContainerHigh = Color(0xFFE5E5F0),
    surfaceContainerHighest = Color(0xFFDFDFEB),
)

@Composable
fun EverySearchTheme(dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
