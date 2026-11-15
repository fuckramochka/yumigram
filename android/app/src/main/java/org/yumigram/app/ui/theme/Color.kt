package org.yumigram.app.ui.theme

import androidx.compose.ui.graphics.Color

// Pixel OS Material 3 Expressive Primary Palette (Fallback when dynamic Monet is not active)
val PixelBluePrimary = Color(0xFFA8C7FA)
val PixelBlueOnPrimary = Color(0xFF003062)
val PixelBlueContainer = Color(0xFF00468C)
val PixelBlueOnContainer = Color(0xFFD6E3FF)

// Pixel Dark Monet Palette (Tonal Elevation layers)
val PixelDarkSurfaceLowest = Color(0xFF0E1014)
val PixelDarkSurfaceLow = Color(0xFF14171D)
val PixelDarkSurface = Color(0xFF1A1D24)
val PixelDarkSurfaceHigh = Color(0xFF232730)
val PixelDarkSurfaceHighest = Color(0xFF2E323D)
val PixelDarkOnSurface = Color(0xFFE2E2E9)
val PixelDarkOnSurfaceVariant = Color(0xFFC4C6D0)
val PixelDarkOutline = Color(0xFF8E9099)
val PixelDarkOutlineVariant = Color(0xFF44474E)

// True AMOLED Pure Black Palette (#000000)
val AmoledSurfaceLowest = Color(0xFF000000)
val AmoledSurfaceLow = Color(0xFF0B0C0F)
val AmoledSurface = Color(0xFF000000)
val AmoledSurfaceHigh = Color(0xFF16181E)
val AmoledSurfaceHighest = Color(0xFF22242C)
val AmoledOnSurface = Color(0xFFE8E8EE)
val AmoledOnSurfaceVariant = Color(0xFFC4C6D0)

// Pixel Light Expressive Palette
val PixelLightSurfaceLowest = Color(0xFFFFFFFF)
val PixelLightSurfaceLow = Color(0xFFF6F7FB)
val PixelLightSurface = Color(0xFFF0F3F9)
val PixelLightSurfaceHigh = Color(0xFFE7EAF1)
val PixelLightSurfaceHighest = Color(0xFFDDE2EA)
val PixelLightOnSurface = Color(0xFF191C20)
val PixelLightOnSurfaceVariant = Color(0xFF44474E)
val PixelLightOutline = Color(0xFF74777F)
val PixelLightOutlineVariant = Color(0xFFC4C6D0)

// Fallback constants for backward compatibility
val PixelDarkCard = PixelDarkSurfaceHigh
val PixelLightSurfaceVariant = PixelLightSurfaceHighest
val PixelDarkSurfaceVariant = PixelDarkSurfaceHigh

// Chat bubble accents
val OutgoingBubbleColor = Color(0xFF2B5278)
val IncomingBubbleColor = Color(0xFF1E232A)
val ReadIndicatorBlue = Color(0xFF4FA3FF)
val DeletedRed = Color(0xFFFF5252)

// Generative Pastel Monet Avatar Palettes (Mint, Coral, Lavender, Powder, Ocean, Amber, Sage, Sky)
data class MonetAvatarPalette(
    val gradient: List<Color>,
    val onColor: Color
)

val MONET_AVATAR_PALETTES = listOf(
    // 1. Mint (М'ята)
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFA8E6CF), Color(0xFF56C596)),
        onColor = Color(0xFF00382B)
    ),
    // 2. Coral (Корал)
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFFFD3B6), Color(0xFFFF8B94)),
        onColor = Color(0xFF5C1D00)
    ),
    // 3. Lavender (Лаванда)
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFE1BEE7), Color(0xFFBA68C8)),
        onColor = Color(0xFF38006B)
    ),
    // 4. Powder / Rose (Пудра)
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFFFCDD2), Color(0xFFF48FB1)),
        onColor = Color(0xFF4C0027)
    ),
    // 5. Ocean (Океан)
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFB3E5FC), Color(0xFF4FC3F7)),
        onColor = Color(0xFF003662)
    ),
    // 6. Amber / Honey
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F)),
        onColor = Color(0xFF402D00)
    ),
    // 7. Sage (Шавлія)
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFDCEDC8), Color(0xFFAED581)),
        onColor = Color(0xFF1B3A00)
    ),
    // 8. Sky (Небо)
    MonetAvatarPalette(
        gradient = listOf(Color(0xFFC5CAE9), Color(0xFF7986CB)),
        onColor = Color(0xFF1A237E)
    )
)

fun getMonetAvatarPalette(id: Long): MonetAvatarPalette {
    val idx = kotlin.math.abs(id.hashCode()) % MONET_AVATAR_PALETTES.size
    return MONET_AVATAR_PALETTES[idx]
}
