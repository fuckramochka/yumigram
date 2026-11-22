package org.telegram.messenger.yumigram;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/**
 * Yumigram (Юмиграм) central configuration and preferences manager.
 * Stores and manages settings for:
 * 1. Custom UI / Theming
 * 2. Ghost Mode & Anti-Delete
 * 3. View-Once (Secret) Media handling
 * 4. App Icons
 * 5. Built-in AI Assistant
 * 6. Enhanced Media Player
 */
public class YumiConfig {

    private static final String PREFS_NAME = "yumigram_config";
    private static volatile SharedPreferences preferences;

    // --- 1. Custom UI ---
    public static final String KEY_HIDE_SPONSORED = "yumi_hide_sponsored";
    public static final String KEY_CHAT_BUBBLE_RADIUS = "yumi_chat_bubble_radius";
    public static final String KEY_BOTTOM_NAV = "yumi_bottom_nav";
    public static final String KEY_AVATAR_RADIUS = "yumi_avatar_radius";

    // --- 2. Ghost Mode ---
    public static final String KEY_GHOST_MODE = "yumi_ghost_mode";
    public static final String KEY_HIDE_READ = "yumi_hide_read";
    public static final String KEY_HIDE_TYPING = "yumi_hide_typing";
    public static final String KEY_HIDE_STORIES_SEEN = "yumi_hide_stories_seen";
    public static final String KEY_HIDE_VOICE_LISTEN = "yumi_hide_voice_listen";
    public static final String KEY_STAY_OFFLINE = "yumi_stay_offline";

    // --- 3. Anti-Delete ---
    public static final String KEY_ANTI_DELETE = "yumi_anti_delete";
    public static final String KEY_SAVE_DELETED_MEDIA = "yumi_save_deleted_media";
    public static final String KEY_SAVE_DELETED_STORIES = "yumi_save_deleted_stories";

    // --- 4. View-Once (Secret) Media ---
    public static final String KEY_SAVE_SECRET_MEDIA = "yumi_save_secret_media";
    public static final String KEY_SEND_SECRET_MEDIA_READ = "yumi_send_secret_media_read";
    public static final String KEY_ALLOW_SECRET_SCREENSHOTS = "yumi_allow_secret_screenshots";

    // --- 5. App Icons ---
    public static final String KEY_SELECTED_ICON = "yumi_selected_icon";

    // --- 6. Built-in AI Assistant ---
    public static final String KEY_AI_ENABLED = "yumi_ai_enabled";
    public static final String KEY_AI_MODE = "yumi_ai_mode"; // "cloud", "local"
    public static final String KEY_AI_PROVIDER = "yumi_ai_provider"; // "gemini", "openai", "custom"
    public static final String KEY_AI_API_KEY = "yumi_ai_api_key";
    public static final String KEY_AI_PRIMARY_MODEL = "yumi_ai_primary_model";
    public static final String KEY_AI_FALLBACK_MODEL = "yumi_ai_fallback_model";
    public static final String KEY_AI_LOCAL_MODEL_ID = "yumi_ai_local_model_id";
    public static final String KEY_AI_SUMMARIZE_LIMIT = "yumi_ai_summarize_limit";

    public static final String DEFAULT_PRIMARY_MODEL = "gemini-3.5-flash-lite";
    public static final String DEFAULT_FALLBACK_MODEL = "gemini-3.1-flash-lite";

    public static final String[] CLOUD_MODELS = new String[] {
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.0-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
    };

    // --- 7. Enhanced Media Player ---
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

    public static boolean isHideVoiceListenEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && (isGhostModeEnabled() || p.getBoolean(KEY_HIDE_VOICE_LISTEN, false));
    }

    public static void setHideVoiceListenEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_HIDE_VOICE_LISTEN, enabled).apply();
        }
    }

    public static boolean isStayOfflineEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_STAY_OFFLINE, false);
    }

    public static void setStayOfflineEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_STAY_OFFLINE, enabled).apply();
        }
    }

    // --- Anti-Delete Getters & Setters ---

    public static boolean isAntiDeleteEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_ANTI_DELETE, true);
    }

    public static void setAntiDeleteEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_ANTI_DELETE, enabled).apply();
        }
    }

    public static boolean isSaveDeletedMediaEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_SAVE_DELETED_MEDIA, true);
    }

    public static void setSaveDeletedMediaEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_SAVE_DELETED_MEDIA, enabled).apply();
        }
    }

    // --- View-Once (Secret) Media Getters & Setters ---

    public static boolean isSaveSecretMediaEnabled() {
        SharedPreferences p = getPrefs();
        return p != null && p.getBoolean(KEY_SAVE_SECRET_MEDIA, true);
    }

    public static void setSaveSecretMediaEnabled(boolean enabled) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_SAVE_SECRET_MEDIA, enabled).apply();
        }
    }

    public static boolean shouldSendSecretMediaRead() {
        SharedPreferences p = getPrefs();
        return p == null || p.getBoolean(KEY_SEND_SECRET_MEDIA_READ, true);
    }

    public static void setSendSecretMediaRead(boolean sendRead) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_SEND_SECRET_MEDIA_READ, sendRead).apply();
        }
    }

    public static boolean isAllowScreenshotsInSecretMedia() {
        SharedPreferences p = getPrefs();
        return p == null || p.getBoolean(KEY_ALLOW_SECRET_SCREENSHOTS, true);
    }

    public static void setAllowScreenshotsInSecretMedia(boolean allow) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putBoolean(KEY_ALLOW_SECRET_SCREENSHOTS, allow).apply();
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

    public static String getAiMode() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_AI_MODE, "cloud") : "cloud";
    }

    public static void setAiMode(String mode) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putString(KEY_AI_MODE, mode).apply();
        }
    }

    public static boolean isLocalAi() {
        return "local".equalsIgnoreCase(getAiMode());
    }

    public static String getAiPrimaryModel() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_AI_PRIMARY_MODEL, DEFAULT_PRIMARY_MODEL) : DEFAULT_PRIMARY_MODEL;
    }

    public static void setAiPrimaryModel(String model) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putString(KEY_AI_PRIMARY_MODEL, model).apply();
        }
    }

    public static String getAiFallbackModel() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_AI_FALLBACK_MODEL, DEFAULT_FALLBACK_MODEL) : DEFAULT_FALLBACK_MODEL;
    }

    public static void setAiFallbackModel(String model) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putString(KEY_AI_FALLBACK_MODEL, model).apply();
        }
    }

    public static String getAiLocalModelId() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_AI_LOCAL_MODEL_ID, "qwen2.5-0.5b") : "qwen2.5-0.5b";
    }

    public static void setAiLocalModelId(String modelId) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit().putString(KEY_AI_LOCAL_MODEL_ID, modelId).apply();
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
