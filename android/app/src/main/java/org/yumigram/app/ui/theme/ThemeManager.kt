package org.yumigram.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val title: String) {
    SYSTEM("Системна (як у Pixel OS)"),
    DARK("Pixel Dark Monet"),
    AMOLED("True AMOLED Pure Black"),
    LIGHT("Світла")
}

enum class AccentPalette(val title: String, val primary: Color, val container: Color) {
    CYBERPUNK("Amegram Purple", Color(0xFF9333EA), Color(0xFFA855F7)),
    PIXEL_BLUE("Pixel Blue", Color(0xFF1A73E8), Color(0xFFD2E3FC)),
    EMERALD("Смарагдовий", Color(0xFF00897B), Color(0xFFB2DFDB)),
    LAVENDER("Лавандовий", Color(0xFF7C4DFF), Color(0xFFD1C4E9)),
    CORAL("Кораловий", Color(0xFFFF5722), Color(0xFFFFCCBC)),
    OBSIDIAN("Обсидіан", Color(0xFF455A64), Color(0xFFCFD8DC))
}

enum class ChatWallpaper(
    val title: String,
    val backgroundColor: Color,
    val bubbleIncoming: Color,
    val bubbleOutgoing: Color
) {
    DEFAULT("Amegram Cyberpunk", Color(0xFF0C0C10), Color(0xFF18181C), Color(0xFF9333EA)),
    SLATE("Графіт AMOLED", Color(0xFF000000), Color(0xFF16181E), Color(0xFF2E7D32)),
    OCEAN("Океанський бриз", Color(0xFF0B192C), Color(0xFF1E3E62), Color(0xFF00ADB5)),
    VELVET("Оксамит", Color(0xFF1A0B2E), Color(0xFF2E1752), Color(0xFF9C27B0)),
    LIGHT_MINIMAL("Світлий папір", Color(0xFFF6F7FB), Color(0xFFFFFFFF), Color(0xFF1A73E8))
}

object ThemeManager {
    private const val PREFS_NAME = "yumigram_theme_prefs"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private val _isAmoled = MutableStateFlow(false)
    val isAmoled: StateFlow<Boolean> = _isAmoled

    private val _accentPalette = MutableStateFlow(AccentPalette.PIXEL_BLUE)
    val accentPalette: StateFlow<AccentPalette> = _accentPalette

    private val _chatWallpaper = MutableStateFlow(ChatWallpaper.DEFAULT)
    val chatWallpaper: StateFlow<ChatWallpaper> = _chatWallpaper

    private val _customWallpaperUri = MutableStateFlow<String?>(null)
    val customWallpaperUri: StateFlow<String?> = _customWallpaperUri

    private val _fontSize = MutableStateFlow(15f)
    val fontSize: StateFlow<Float> = _fontSize

    private val _bubbleCornerRadius = MutableStateFlow(20)
    val bubbleCornerRadius: StateFlow<Int> = _bubbleCornerRadius

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _themeMode.value = try { ThemeMode.valueOf(modeStr) } catch (_: Exception) { ThemeMode.SYSTEM }

        _isAmoled.value = prefs.getBoolean("is_amoled", false) || _themeMode.value == ThemeMode.AMOLED

        _dynamicColor.value = prefs.getBoolean("dynamic_color", true)

        val accentStr = prefs.getString("accent_palette", AccentPalette.PIXEL_BLUE.name) ?: AccentPalette.PIXEL_BLUE.name
        _accentPalette.value = try { AccentPalette.valueOf(accentStr) } catch (_: Exception) { AccentPalette.PIXEL_BLUE }

        val wallpaperStr = prefs.getString("chat_wallpaper", ChatWallpaper.DEFAULT.name) ?: ChatWallpaper.DEFAULT.name
        _chatWallpaper.value = try { ChatWallpaper.valueOf(wallpaperStr) } catch (_: Exception) { ChatWallpaper.DEFAULT }

        _customWallpaperUri.value = prefs.getString("custom_wallpaper_uri", null)
        _fontSize.value = prefs.getFloat("font_size", 15f)
        _bubbleCornerRadius.value = prefs.getInt("bubble_corner_radius", 20)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        if (mode == ThemeMode.AMOLED) {
            _isAmoled.value = true
        } else if (mode == ThemeMode.LIGHT) {
            _isAmoled.value = false
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", mode.name)
            .putBoolean("is_amoled", _isAmoled.value)
            .apply()
    }

    fun setAmoled(context: Context, enabled: Boolean) {
        _isAmoled.value = enabled
        if (enabled && _themeMode.value != ThemeMode.DARK) {
            _themeMode.value = ThemeMode.AMOLED
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_amoled", enabled)
            .putString("theme_mode", _themeMode.value.name)
            .apply()
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        _dynamicColor.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("dynamic_color", enabled).apply()
    }

    fun setAccentPalette(context: Context, palette: AccentPalette) {
        _accentPalette.value = palette
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("accent_palette", palette.name).apply()
    }

    fun setChatWallpaper(context: Context, wallpaper: ChatWallpaper) {
        _chatWallpaper.value = wallpaper
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("chat_wallpaper", wallpaper.name).apply()
    }

    fun setCustomWallpaperUri(context: Context, uri: String?) {
        _customWallpaperUri.value = uri
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("custom_wallpaper_uri", uri).apply()
    }

    fun setFontSize(context: Context, size: Float) {
        _fontSize.value = size
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("font_size", size).apply()
    }

    fun setBubbleCornerRadius(context: Context, radius: Int) {
        _bubbleCornerRadius.value = radius
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt("bubble_corner_radius", radius).apply()
    }
}
