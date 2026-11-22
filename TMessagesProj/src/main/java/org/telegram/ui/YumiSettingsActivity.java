package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.yumigram.ai.YumiLocalAiManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;

/**
 * Dedicated settings screen for Yumigram (Юмиграм) modifications.
 */
public class YumiSettingsActivity extends UniversalFragment {

    private static final int ID_GHOST_MODE = 1;
    private static final int ID_HIDE_READ = 2;
    private static final int ID_HIDE_TYPING = 3;
    private static final int ID_HIDE_STORIES_SEEN = 4;
    private static final int ID_HIDE_VOICE_LISTEN = 5;
    private static final int ID_STAY_OFFLINE = 6;

    private static final int ID_ANTI_DELETE = 10;
    private static final int ID_SAVE_DELETED_MEDIA = 11;

    private static final int ID_SAVE_SECRET_MEDIA = 15;
    private static final int ID_SEND_SECRET_MEDIA_READ = 16;
    private static final int ID_ALLOW_SECRET_SCREENSHOTS = 17;

    private static final int ID_AI_ENABLED = 20;
    private static final int ID_AI_OPEN_COPILOT = 21;
    private static final int ID_AI_MODE_CLOUD = 22;
    private static final int ID_AI_MODE_LOCAL = 23;
    private static final int ID_AI_API_KEY = 24;
    private static final int ID_AI_PRIMARY_MODEL = 25;
    private static final int ID_AI_FALLBACK_MODEL = 26;
    private static final int ID_AI_LOCAL_MODEL = 27;
    private static final int ID_AI_DOWNLOAD_LOCAL = 28;

    private static final int ID_ENHANCED_PLAYER = 30;
    private static final int ID_EQUALIZER = 31;

    private static final int ID_HIDE_SPONSORED = 40;

    private String downloadStatus = null;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YumiSettings);
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        fragmentView = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        // --- Ghost Mode ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionGhost)));
        items.add(UItem.asCheck(ID_GHOST_MODE, getString(R.string.YumiGhostMode)).setChecked(YumiConfig.isGhostModeEnabled()));
        items.add(UItem.asCheck(ID_HIDE_READ, getString(R.string.YumiHideRead)).setChecked(YumiConfig.isHideReadEnabled()));
        items.add(UItem.asCheck(ID_HIDE_TYPING, getString(R.string.YumiHideTyping)).setChecked(YumiConfig.isHideTypingEnabled()));
        items.add(UItem.asCheck(ID_HIDE_STORIES_SEEN, getString(R.string.YumiHideStoriesSeen)).setChecked(YumiConfig.isHideStoriesSeenEnabled()));
        items.add(UItem.asCheck(ID_HIDE_VOICE_LISTEN, getString(R.string.YumiHideVoiceListen)).setChecked(YumiConfig.isHideVoiceListenEnabled()));
        items.add(UItem.asCheck(ID_STAY_OFFLINE, getString(R.string.YumiStayOffline)).setChecked(YumiConfig.isStayOfflineEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiGhostModeInfo)));

        // --- Anti-Delete ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionAntiDelete)));
        items.add(UItem.asCheck(ID_ANTI_DELETE, getString(R.string.YumiAntiDelete)).setChecked(YumiConfig.isAntiDeleteEnabled()));
        items.add(UItem.asCheck(ID_SAVE_DELETED_MEDIA, getString(R.string.YumiSaveDeletedMedia)).setChecked(YumiConfig.isSaveDeletedMediaEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiSaveDeletedMediaInfo)));

        // --- View-Once Media ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionSecretMedia)));
        items.add(UItem.asCheck(ID_SAVE_SECRET_MEDIA, getString(R.string.YumiSaveSecretMedia)).setChecked(YumiConfig.isSaveSecretMediaEnabled()));
        items.add(UItem.asCheck(ID_SEND_SECRET_MEDIA_READ, getString(R.string.YumiSendSecretMediaRead)).setChecked(YumiConfig.shouldSendSecretMediaRead()));
        items.add(UItem.asCheck(ID_ALLOW_SECRET_SCREENSHOTS, getString(R.string.YumiAllowSecretScreenshots)).setChecked(YumiConfig.isAllowScreenshotsInSecretMedia()));
        items.add(UItem.asShadow(getString(R.string.YumiSendSecretMediaReadInfo)));

        // --- AI Assistant ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionAI)));
        items.add(UItem.asCheck(ID_AI_ENABLED, getString(R.string.YumiAiEnabled)).setChecked(YumiConfig.isAiEnabled()));

        if (YumiConfig.isAiEnabled()) {
            items.add(UItem.asButton(ID_AI_OPEN_COPILOT, R.drawable.msg_bot, "✨ Відкрити чат з ШІ (Yumi Copilot)"));

            boolean isLocal = YumiConfig.isLocalAi();
            items.add(UItem.asRadio(ID_AI_MODE_CLOUD, "Хмарний ШІ (Google Gemini API)").setChecked(!isLocal));
            items.add(UItem.asRadio(ID_AI_MODE_LOCAL, "Локальний ШІ (On-Device / Hugging Face)").setChecked(isLocal));

            if (!isLocal) {
                // Cloud Gemini settings
                String key = YumiConfig.getAiApiKey();
                String keyText = TextUtils.isEmpty(key) ? "Не налаштовано (натисніть, щоб додати)" : "••••••••" + key.substring(Math.max(0, key.length() - 4));
                items.add(UItem.asButton(ID_AI_API_KEY, "API-ключ Gemini: " + keyText));
                items.add(UItem.asButton(ID_AI_PRIMARY_MODEL, "Основна модель: " + YumiConfig.getAiPrimaryModel()));
                items.add(UItem.asButton(ID_AI_FALLBACK_MODEL, "Резервна модель (Fallback): " + YumiConfig.getAiFallbackModel()));
                items.add(UItem.asShadow("Хмарний режим: передові моделі Gemini 3.5/3.1 з автоматичним перемиканням при перевантаженні"));
            } else {
                // Local Hugging Face settings
                YumiLocalAiManager.LocalModelInfo modelInfo = YumiLocalAiManager.getModelInfo(YumiConfig.getAiLocalModelId());
                boolean downloaded = YumiLocalAiManager.isModelDownloaded(modelInfo.id);

                items.add(UItem.asButton(ID_AI_LOCAL_MODEL, "Модель: " + modelInfo.name + " (" + modelInfo.size + ")"));
                String btnText = downloaded ? "✅ Модель завантажена (готова до роботи)" : (downloadStatus != null ? downloadStatus : "📥 Завантажити модель з Hugging Face");
                items.add(UItem.asButton(ID_AI_DOWNLOAD_LOCAL, btnText));
                items.add(UItem.asShadow("Локальний режим працює повністю на вашому пристрої offline без відправки даних у хмару."));
            }
        } else {
            items.add(UItem.asShadow(getString(R.string.YumiAiEnabledInfo)));
        }

        // --- Media Player ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionPlayer)));
        items.add(UItem.asCheck(ID_ENHANCED_PLAYER, getString(R.string.YumiEnhancedPlayer)).setChecked(YumiConfig.isEnhancedPlayerEnabled()));
        items.add(UItem.asCheck(ID_EQUALIZER, getString(R.string.YumiEqualizer)).setChecked(YumiConfig.isEqualizerEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiEnhancedPlayerInfo)));

        // --- Interface ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionUI)));
        items.add(UItem.asCheck(ID_HIDE_SPONSORED, getString(R.string.YumiHideSponsored)).setChecked(YumiConfig.isHideSponsoredEnabled()));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_GHOST_MODE:
                YumiConfig.setGhostModeEnabled(!YumiConfig.isGhostModeEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_READ:
                YumiConfig.setHideReadEnabled(!YumiConfig.isHideReadEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_TYPING:
                YumiConfig.setHideTypingEnabled(!YumiConfig.isHideTypingEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_STORIES_SEEN:
                YumiConfig.setHideStoriesSeenEnabled(!YumiConfig.isHideStoriesSeenEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_VOICE_LISTEN:
                YumiConfig.setHideVoiceListenEnabled(!YumiConfig.isHideVoiceListenEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_STAY_OFFLINE:
                YumiConfig.setStayOfflineEnabled(!YumiConfig.isStayOfflineEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_ANTI_DELETE:
                YumiConfig.setAntiDeleteEnabled(!YumiConfig.isAntiDeleteEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_SAVE_DELETED_MEDIA:
                YumiConfig.setSaveDeletedMediaEnabled(!YumiConfig.isSaveDeletedMediaEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_SAVE_SECRET_MEDIA:
                YumiConfig.setSaveSecretMediaEnabled(!YumiConfig.isSaveSecretMediaEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_SEND_SECRET_MEDIA_READ:
                YumiConfig.setSendSecretMediaRead(!YumiConfig.shouldSendSecretMediaRead());
                if (adapter != null) adapter.update(true);
                break;
            case ID_ALLOW_SECRET_SCREENSHOTS:
                YumiConfig.setAllowScreenshotsInSecretMedia(!YumiConfig.isAllowScreenshotsInSecretMedia());
                if (adapter != null) adapter.update(true);
                break;
            case ID_AI_ENABLED:
                YumiConfig.setAiEnabled(!YumiConfig.isAiEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_AI_OPEN_COPILOT:
                presentFragment(new YumiAiCopilotActivity());
                break;
            case ID_AI_MODE_CLOUD:
                YumiConfig.setAiMode("cloud");
                if (adapter != null) adapter.update(true);
                break;
            case ID_AI_MODE_LOCAL:
                YumiConfig.setAiMode("local");
                if (adapter != null) adapter.update(true);
                break;
            case ID_AI_API_KEY:
                showApiKeyDialog();
                break;
            case ID_AI_PRIMARY_MODEL:
                showModelSelectDialog(true);
                break;
            case ID_AI_FALLBACK_MODEL:
                showModelSelectDialog(false);
                break;
            case ID_AI_LOCAL_MODEL:
                showLocalModelSelectDialog();
                break;
            case ID_AI_DOWNLOAD_LOCAL:
                startDownloadLocalModel();
                break;
            case ID_ENHANCED_PLAYER:
                YumiConfig.setEnhancedPlayerEnabled(!YumiConfig.isEnhancedPlayerEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_EQUALIZER:
                YumiConfig.setEqualizerEnabled(!YumiConfig.isEqualizerEnabled());
                if (adapter != null) adapter.update(true);
                break;
            case ID_HIDE_SPONSORED:
                YumiConfig.setHideSponsoredEnabled(!YumiConfig.isHideSponsoredEnabled());
                if (adapter != null) adapter.update(true);
                break;
        }
    }

    private void showApiKeyDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("API-ключ Google Gemini");
        builder.setMessage("Введіть безкоштовний API-ключ з aistudio.google.com");

        final EditText input = new EditText(getParentActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(YumiConfig.getAiApiKey());
        builder.setView(input);

        builder.setPositiveButton("Зберегти", (dialog, which) -> {
            YumiConfig.setAiApiKey(input.getText().toString().trim());
            if (adapter != null) adapter.update(true);
        });
        builder.setNegativeButton("Скасувати", null);
        builder.show();
    }

    private void showModelSelectDialog(boolean isPrimary) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(isPrimary ? "Виберіть основну модель" : "Виберіть резервну модель (Fallback)");

        String[] models = YumiConfig.CLOUD_MODELS;
        builder.setItems(models, (dialog, which) -> {
            String selected = models[which];
            if (isPrimary) {
                YumiConfig.setAiPrimaryModel(selected);
            } else {
                YumiConfig.setAiFallbackModel(selected);
            }
            if (adapter != null) adapter.update(true);
        });
        builder.show();
    }

    private void showLocalModelSelectDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Виберіть локальну модель (Hugging Face)");

        CharSequence[] names = new CharSequence[YumiLocalAiManager.AVAILABLE_LOCAL_MODELS.size()];
        for (int i = 0; i < names.length; i++) {
            YumiLocalAiManager.LocalModelInfo info = YumiLocalAiManager.AVAILABLE_LOCAL_MODELS.get(i);
            names[i] = info.name + " (" + info.size + ")";
        }

        builder.setItems(names, (dialog, which) -> {
            YumiConfig.setAiLocalModelId(YumiLocalAiManager.AVAILABLE_LOCAL_MODELS.get(which).id);
            if (adapter != null) adapter.update(true);
        });
        builder.show();
    }

    private void startDownloadLocalModel() {
        String modelId = YumiConfig.getAiLocalModelId();
        if (YumiLocalAiManager.isModelDownloaded(modelId)) {
            Toast.makeText(getParentActivity(), "Модель вже завантажена!", Toast.LENGTH_SHORT).show();
            return;
        }

        downloadStatus = "⏳ Завантаження: 0%...";
        if (adapter != null) adapter.update(true);

        YumiLocalAiManager.downloadModel(modelId, new YumiLocalAiManager.DownloadProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                downloadStatus = "⏳ Завантаження: " + percent + "% (" + (downloadedBytes / (1024 * 1024)) + " MB)...";
                if (adapter != null) adapter.update(true);
            }

            @Override
            public void onSuccess(File modelFile) {
                downloadStatus = null;
                if (adapter != null) adapter.update(true);
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), "Модель успішно завантажена з Hugging Face!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String error) {
                downloadStatus = "❌ Помилка: " + error;
                if (adapter != null) adapter.update(true);
            }
        });
    }
}
