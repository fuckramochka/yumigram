package org.telegram.messenger.yumigram;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/**
 * Yumigram (Юмиграм) central configuration and preferences manager.
 * Stores and manages settings for:
 * 1. Custom UI / Theming
 * 2. Ghost Mode & Anti-Delete
 * 3. App Icons
 * 4. Built-in AI Assistant
 * 5. Enhanced Media Player
 */
public class YumiConfig {

    private static final String PREFS_NAME = "yumigram_config";
    private static volatile SharedPreferences preferences;

    // --- 1. Custom UI ---
    public static final String KEY_HIDE_SPONSORED = "yumi_hide_sponsored";
    public static final String KEY_CHAT_BUBBLE_RADIUS = "yumi_chat_bubble_radius";
    public static final String KEY_BOTTOM_NAV = "yumi_bottom_nav";
    public static final String KEY_AVATAR_RADIUS = "yumi_avatar_radius";

    // --- 2. Ghost Mode & Anti-Delete ---
    public static final String KEY_GHOST_MODE = "yumi_ghost_mode";
    public static final String KEY_HIDE_READ = "yumi_hide_read";
    public static final String KEY_HIDE_TYPING = "yumi_hide_typing";
    public static final String KEY_HIDE_STORIES_SEEN = "yumi_hide_stories_seen";
    public static final String KEY_HIDE_ONLINE = "yumi_hide_online";
    public static final String KEY_ANTI_DELETE = "yumi_anti_delete";
    public static final String KEY_SAVE_DELETED_STORIES = "yumi_save_deleted_stories";

    // --- 3. App Icons ---
    public static final String KEY_SELECTED_ICON = "yumi_selected_icon";

    // --- 4. Built-in AI Assistant ---
    public static final String KEY_AI_ENABLED = "yumi_ai_enabled";
    public static final String KEY_AI_PROVIDER = "yumi_ai_provider"; // "gemini", "openai", "custom"
    public static final String KEY_AI_API_KEY = "yumi_ai_api_key";
    public static final String KEY_AI_MODEL = "yumi_ai_model";
    public static final String KEY_AI_SUMMARIZE_LIMIT = "yumi_ai_summarize_limit";

    // --- 5. Enhanced Media Player ---
    public static final String KEY_ENHANCED_PLAYER = "yumi_enhanced_player";
    public static final String KEY_EQUALIZER_ENABLED = "yumi_equalizer_enabled";
    public static final String KEY_CUSTOM_AUDIO_SPEED = "yumi_custom_audio_speed";
    public static final String KEY_VIDEO_SWIPE_GESTURES = "yumi_video_swipe_gestures";

    private static SharedPreferences getPrefs() {
        if (preferences == null) {
            synchronized (YumiConfig.class) {
                if (preferences == null && ApplicationLoader.applicationContext != null) {
                    preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                }
            }
        }
        return preferences;
    }

    // --- Ghost Mode Getters & Setters ---

    public static boolean isGhostModeEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_GHOST_MODE, false);
    }

    public static void setGhostModeEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_GHOST_MODE, enabled).apply();
        }
    }

    public static boolean isHideReadEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && (isGhostModeEnabled() || p.getBoolean(KEY_HIDE_READ, false));
    }

    public static void setHideReadEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_HIDE_READ, enabled).apply();
        }
    }

    public static boolean isHideTypingEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && (isGhostModeEnabled() || p.getBoolean(KEY_HIDE_TYPING, false));
    }

    public static void setHideTypingEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_HIDE_TYPING, enabled).apply();
        }
    }

    public static boolean isHideStoriesSeenEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && (isGhostModeEnabled() || p.getBoolean(KEY_HIDE_STORIES_SEEN, false));
    }

    public static void setHideStoriesSeenEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_HIDE_STORIES_SEEN, enabled).apply();
        }
    }

    // --- Anti-Delete Getters & Setters ---

    public static boolean isAntiDeleteEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_ANTI_DELETE, true); // Enabled by default for Yumigram
    }

    public static void setAntiDeleteEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_ANTI_DELETE, enabled).apply();
        }
    }

    // --- UI Getters & Setters ---

    public static boolean isHideSponsoredEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_HIDE_SPONSORED, true);
    }

    public static void setHideSponsoredEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_HIDE_SPONSORED, enabled).apply();
        }
    }

    // --- AI Assistant Getters & Setters ---

    public static boolean isAiEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_AI_ENABLED, true);
    }

    public static void setAiEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_AI_ENABLED, enabled).apply();
        }
    }

    public static String getAiProvider() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_AI_PROVIDER, "gemini") : "gemini";
    }

    public static void setAiProvider(String provider) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putString(KEY_AI_PROVIDER, provider).apply();
        }
    }

    public static String getAiApiKey() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_AI_API_KEY, "") : "";
    }

    public static void setAiApiKey(String apiKey) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putString(KEY_AI_API_KEY, apiKey).apply();
        }
    }

    // --- Player Getters & Setters ---

    public static boolean isEnhancedPlayerEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_ENHANCED_PLAYER, true);
    }

    public static void setEnhancedPlayerEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_ENHANCED_PLAYER, enabled).apply();
        }
    }

    public static boolean isEqualizerEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_EQUALIZER_ENABLED, false);
    }

    public static void setEqualizerEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_EQUALIZER_ENABLED, enabled).apply();
        }
    }
}
