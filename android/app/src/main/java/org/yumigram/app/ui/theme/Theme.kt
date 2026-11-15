package org.yumigram.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Pixel OS Expressive Soft & Round Shapes
val PixelShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun YumigramTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsState()
    val dynamicColor by ThemeManager.dynamicColor.collectAsState()
    val accentPalette by ThemeManager.accentPalette.collectAsState()

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = accentPalette.primary,
            primaryContainer = accentPalette.container.copy(alpha = 0.3f),
            onPrimaryContainer = accentPalette.primary,
            surface = PixelDarkSurface,
            onSurface = PixelDarkOnSurface,
            surfaceVariant = PixelDarkSurfaceVariant,
            onSurfaceVariant = PixelDarkOnSurfaceVariant
        )
        else -> lightColorScheme(
            primary = accentPalette.primary,
            primaryContainer = accentPalette.container,
            onPrimaryContainer = accentPalette.primary,
            surface = PixelLightSurface,
            onSurface = PixelLightOnSurface,
            surfaceVariant = PixelLightSurfaceVariant
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PixelShapes,
        content = content
    )
}
