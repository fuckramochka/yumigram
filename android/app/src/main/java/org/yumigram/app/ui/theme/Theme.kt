package org.yumigram.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Pixel OS Material 3 Expressive Soft & Round Shapes
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
    val isAmoledPref by ThemeManager.isAmoled.collectAsState()
    val dynamicColor by ThemeManager.dynamicColor.collectAsState()
    val accentPalette by ThemeManager.accentPalette.collectAsState()

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }

    val useAmoled = isDark && (themeMode == ThemeMode.AMOLED || isAmoledPref)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (useAmoled) {
                base.copy(
                    background = AmoledSurfaceLowest,
                    surface = AmoledSurface,
                    surfaceContainerLowest = AmoledSurfaceLowest,
                    surfaceContainerLow = AmoledSurfaceLow,
                    surfaceContainer = AmoledSurface,
                    surfaceContainerHigh = AmoledSurfaceHigh,
                    surfaceContainerHighest = AmoledSurfaceHighest
                )
            } else {
                base
            }
        }
        useAmoled -> darkColorScheme(
            primary = accentPalette.primary,
            onPrimary = Color.White,
            primaryContainer = accentPalette.container.copy(alpha = 0.35f),
            onPrimaryContainer = accentPalette.primary,
            background = AmoledSurfaceLowest,
            onBackground = AmoledOnSurface,
            surface = AmoledSurface,
            onSurface = AmoledOnSurface,
            surfaceVariant = AmoledSurfaceHigh,
            onSurfaceVariant = AmoledOnSurfaceVariant,
            surfaceContainerLowest = AmoledSurfaceLowest,
            surfaceContainerLow = AmoledSurfaceLow,
            surfaceContainer = AmoledSurface,
            surfaceContainerHigh = AmoledSurfaceHigh,
            surfaceContainerHighest = AmoledSurfaceHighest
        )
        isDark -> darkColorScheme(
            primary = accentPalette.primary,
            onPrimary = Color.White,
            primaryContainer = accentPalette.container.copy(alpha = 0.35f),
            onPrimaryContainer = accentPalette.primary,
            background = PixelDarkSurfaceLowest,
            onBackground = PixelDarkOnSurface,
            surface = PixelDarkSurface,
            onSurface = PixelDarkOnSurface,
            surfaceVariant = PixelDarkSurfaceHigh,
            onSurfaceVariant = PixelDarkOnSurfaceVariant,
            surfaceContainerLowest = PixelDarkSurfaceLowest,
            surfaceContainerLow = PixelDarkSurfaceLow,
            surfaceContainer = PixelDarkSurface,
            surfaceContainerHigh = PixelDarkSurfaceHigh,
            surfaceContainerHighest = PixelDarkSurfaceHighest,
            outline = PixelDarkOutline,
            outlineVariant = PixelDarkOutlineVariant
        )
        else -> lightColorScheme(
            primary = accentPalette.primary,
            onPrimary = Color.White,
            primaryContainer = accentPalette.container,
            onPrimaryContainer = accentPalette.primary,
            background = PixelLightSurfaceLowest,
            onBackground = PixelLightOnSurface,
            surface = PixelLightSurface,
            onSurface = PixelLightOnSurface,
            surfaceVariant = PixelLightSurfaceHigh,
            onSurfaceVariant = PixelLightOnSurfaceVariant,
            surfaceContainerLowest = PixelLightSurfaceLowest,
            surfaceContainerLow = PixelLightSurfaceLow,
            surfaceContainer = PixelLightSurface,
            surfaceContainerHigh = PixelLightSurfaceHigh,
            surfaceContainerHighest = PixelLightSurfaceHighest,
            outline = PixelLightOutline,
            outlineVariant = PixelLightOutlineVariant
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PixelShapes,
        content = content
    )
}
