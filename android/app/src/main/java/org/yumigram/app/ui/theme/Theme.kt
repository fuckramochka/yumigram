package org.yumigram.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PixelBluePrimary,
    onPrimary = PixelBlueOnPrimary,
    primaryContainer = PixelBlueContainer,
    onPrimaryContainer = PixelBlueOnContainer,
    surface = PixelDarkSurface,
    onSurface = PixelDarkOnSurface,
    surfaceVariant = PixelDarkSurfaceVariant,
    onSurfaceVariant = PixelDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = PixelBlueOnPrimary,
    onPrimary = PixelBluePrimary,
    surface = PixelLightSurface,
    onSurface = PixelLightOnSurface,
    surfaceVariant = PixelLightSurfaceVariant
)

// Pixel OS Expressive Soft & Round Shapes
val PixelShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun YumigramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Pixel OS Dynamic Color (Monet)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PixelShapes,
        content = content
    )
}
