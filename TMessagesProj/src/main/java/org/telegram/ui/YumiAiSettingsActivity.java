package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.yumigram.ai.YumiLocalAiManager;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Components.YumiApiKeyBottomSheet;
import org.telegram.ui.Components.YumiWheelPickerDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dedicated settings screen for Yumi AI Agent.
 * Features:
 * - Single switchable Cloud vs Local mode selector
 * - 3D Wheel Picker dialog for Primary and Fallback model selection
 * - Beautiful Gemini API key sheet with clipboard paste and Get Key shortcut
 * - Full AI Agent permissions & voice transcription
 */
public class YumiAiSettingsActivity extends UniversalFragment {

    private static final int ID_AI_OPEN_COPILOT = 1;
    private static final int ID_AI_ENABLED = 2;

    private static final int ID_AI_AGENT_READ_CHAT = 10;
    private static final int ID_AI_AGENT_SEND_MESSAGES = 11;
    private static final int ID_AI_AGENT_SMART_REPLIES = 12;
    private static final int ID_AI_VOICE_TRANSCRIPTION = 13;

    private static final int ID_AI_MODE_TOGGLE = 20;
    private static final int ID_AI_GET_KEY = 21;
    private static final int ID_AI_API_KEY = 22;
    private static final int ID_AI_PRIMARY_MODEL = 23;
    private static final int ID_AI_FALLBACK_MODEL = 24;
    private static final int ID_AI_LOCAL_MODEL = 25;
    private static final int ID_AI_DOWNLOAD_LOCAL = 26;

    private String downloadStatus = null;

    @Override
    protected CharSequence getTitle() {
        return YumiLocale.getString(R.string.YumiMenuAI);
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
        // 1. Quick Launch AI Agent
        items.add(UItem.asButton(ID_AI_OPEN_COPILOT, R.drawable.msg_bot, YumiLocale.getString(R.string.YumiAiOpenCopilot)));

        // 2. Master AI Agent Switch
        boolean aiEnabled = YumiConfig.isAiEnabled();
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiSectionAI)));
        items.add(UItem.asCheck(ID_AI_ENABLED, YumiLocale.getString(R.string.YumiAiEnabled)).setChecked(aiEnabled));
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiAiEnabledInfo)));

        if (!aiEnabled) {
            return;
        }

        // 3. Agent Capabilities & Telegram Access
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiAiAgentPermissions)));
        items.add(UItem.asCheck(ID_AI_AGENT_READ_CHAT, YumiLocale.getString(R.string.YumiAiAgentReadChat)).setChecked(YumiConfig.isAiAgentReadChatEnabled()));
        items.add(UItem.asCheck(ID_AI_AGENT_SEND_MESSAGES, YumiLocale.getString(R.string.YumiAiAgentSendMessages)).setChecked(YumiConfig.isAiAgentSendMessagesEnabled()));
        items.add(UItem.asCheck(ID_AI_AGENT_SMART_REPLIES, YumiLocale.getString(R.string.YumiAiAgentSmartReplies)).setChecked(YumiConfig.isAiAgentSmartRepliesEnabled()));
        items.add(UItem.asCheck(ID_AI_VOICE_TRANSCRIPTION, YumiLocale.getString(R.string.YumiAiVoiceTranscription)).setChecked(YumiConfig.isAiVoiceTranscriptionEnabled()));
        items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiAiVoiceTranscriptionInfo)));

        // 4. Engine & Model Configuration (Single Switcher)
        items.add(UItem.asHeader(YumiLocale.getString(R.string.YumiAiMode)));
        boolean isLocal = YumiConfig.isLocalAi();
        String modeText = isLocal ? "💻 " + YumiLocale.getString(R.string.YumiAiModeLocal) : "☁️ " + YumiLocale.getString(R.string.YumiAiModeCloud);
        items.add(UItem.asButton(ID_AI_MODE_TOGGLE, R.drawable.player_new_repeatall, modeText, "Tap to switch"));

        if (!isLocal) {
            // Cloud Gemini Settings
            String key = YumiConfig.getAiApiKey();
            String keyMasked = TextUtils.isEmpty(key) ? YumiLocale.getString(R.string.YumiCloudApiKeyNotSet) : "••••••••" + key.substring(Math.max(0, key.length() - 4));

            items.add(UItem.asButton(ID_AI_GET_KEY, R.drawable.msg_link, "🔑 Get Free Gemini API Key", "aistudio.google.com"));
            items.add(UItem.asButton(ID_AI_API_KEY, String.format(YumiLocale.getString(R.string.YumiAiApiKey), keyMasked)));
            items.add(UItem.asButton(ID_AI_PRIMARY_MODEL, String.format(YumiLocale.getString(R.string.YumiAiPrimaryModel), YumiConfig.getAiPrimaryModel())));
            items.add(UItem.asButton(ID_AI_FALLBACK_MODEL, String.format(YumiLocale.getString(R.string.YumiAiFallbackModel), YumiConfig.getAiFallbackModel())));
            items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiAiCloudModeDesc)));
        } else {
            // Local Hugging Face Settings
            YumiLocalAiManager.LocalModelInfo modelInfo = YumiLocalAiManager.getModelInfo(YumiConfig.getAiLocalModelId());
            boolean downloaded = YumiLocalAiManager.isModelDownloaded(modelInfo.id);

            items.add(UItem.asButton(ID_AI_LOCAL_MODEL, String.format(YumiLocale.getString(R.string.YumiAiLocalModel), modelInfo.name, modelInfo.size)));
            String btnText = downloaded ? YumiLocale.getString(R.string.YumiAiModelDownloaded) : (downloadStatus != null ? downloadStatus : YumiLocale.getString(R.string.YumiAiDownloadLocal));
            items.add(UItem.asButton(ID_AI_DOWNLOAD_LOCAL, btnText));
            items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiAiLocalModeDesc)));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_AI_OPEN_COPILOT:
                presentFragment(new YumiAiCopilotActivity());
                break;
            case ID_AI_ENABLED:
                YumiConfig.setAiEnabled(!YumiConfig.isAiEnabled());
                updateList();
                break;
            case ID_AI_AGENT_READ_CHAT:
                YumiConfig.setAiAgentReadChatEnabled(!YumiConfig.isAiAgentReadChatEnabled());
                updateList();
                break;
            case ID_AI_AGENT_SEND_MESSAGES:
                YumiConfig.setAiAgentSendMessagesEnabled(!YumiConfig.isAiAgentSendMessagesEnabled());
                updateList();
                break;
            case ID_AI_AGENT_SMART_REPLIES:
                YumiConfig.setAiAgentSmartRepliesEnabled(!YumiConfig.isAiAgentSmartRepliesEnabled());
                updateList();
                break;
            case ID_AI_VOICE_TRANSCRIPTION:
                YumiConfig.setAiVoiceTranscriptionEnabled(!YumiConfig.isAiVoiceTranscriptionEnabled());
                updateList();
                break;
            case ID_AI_MODE_TOGGLE:
                // Single button toggles mode cleanly
                if (YumiConfig.isLocalAi()) {
                    YumiConfig.setAiMode("cloud");
                } else {
                    YumiConfig.setAiMode("local");
                }
                updateList();
                break;
            case ID_AI_GET_KEY:
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"));
                    getParentActivity().startActivity(browserIntent);
                } catch (Exception ignored) {}
                break;
            case ID_AI_API_KEY:
                showApiKeySheet();
                break;
            case ID_AI_PRIMARY_MODEL:
                showModelWheelPicker(true);
                break;
            case ID_AI_FALLBACK_MODEL:
                showModelWheelPicker(false);
                break;
            case ID_AI_LOCAL_MODEL:
                showLocalModelWheelPicker();
                break;
            case ID_AI_DOWNLOAD_LOCAL:
                startLocalModelDownload();
                break;
        }
    }

    private void showApiKeySheet() {
        if (getParentActivity() == null) return;
        new YumiApiKeyBottomSheet(getParentActivity(), YumiConfig.getAiApiKey(), key -> {
            updateList();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, "Gemini API key saved!").show();
        }).show();
    }

    private void showModelWheelPicker(boolean isPrimary) {
        if (getParentActivity() == null) return;
        List<String> models = Arrays.asList(YumiConfig.CLOUD_MODELS);
        String current = isPrimary ? YumiConfig.getAiPrimaryModel() : YumiConfig.getAiFallbackModel();
        int initialIndex = Math.max(0, models.indexOf(current));

        String title = isPrimary ? "Primary AI Model" : "Fallback AI Model";
        new YumiWheelPickerDialog(getParentActivity(), title, models, initialIndex, (index, value) -> {
            if (isPrimary) {
                YumiConfig.setAiPrimaryModel(value);
            } else {
                YumiConfig.setAiFallbackModel(value);
            }
            updateList();
        }).show();
    }

    private void showLocalModelWheelPicker() {
        if (getParentActivity() == null) return;
        List<YumiLocalAiManager.LocalModelInfo> list = YumiLocalAiManager.AVAILABLE_LOCAL_MODELS;
        List<String> names = new ArrayList<>();
        String currentId = YumiConfig.getAiLocalModelId();
        int initialIndex = 0;

        for (int i = 0; i < list.size(); i++) {
            YumiLocalAiManager.LocalModelInfo m = list.get(i);
            boolean dl = YumiLocalAiManager.isModelDownloaded(m.id);
            names.add(m.name + " (" + m.size + ")" + (dl ? " ✅" : ""));
            if (m.id.equals(currentId)) {
                initialIndex = i;
            }
        }

        new YumiWheelPickerDialog(getParentActivity(), "Select Local Model", names, initialIndex, (index, value) -> {
            if (index >= 0 && index < list.size()) {
                YumiConfig.setAiLocalModelId(list.get(index).id);
                downloadStatus = null;
                updateList();
            }
        }).show();
    }

    private void startLocalModelDownload() {
        YumiLocalAiManager.LocalModelInfo info = YumiLocalAiManager.getModelInfo(YumiConfig.getAiLocalModelId());
        if (YumiLocalAiManager.isModelDownloaded(info.id)) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, YumiLocale.getString(R.string.YumiAiModelDownloaded)).show();
            return;
        }

        downloadStatus = "⏳ 0%...";
        updateList();

        YumiLocalAiManager.downloadModel(info.id, new YumiLocalAiManager.DownloadProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                downloadStatus = "⏳ " + percent + "% (" + (downloadedBytes / (1024 * 1024)) + "/" + (totalBytes / (1024 * 1024)) + " MB)";
                updateList();
            }

            @Override
            public void onSuccess(File modelFile) {
                downloadStatus = null;
                updateList();
                BulletinFactory.of(YumiAiSettingsActivity.this).createSimpleBulletin(R.raw.chats_infotip, YumiLocale.getString(R.string.YumiAiModelDownloaded)).show();
            }

            @Override
            public void onError(String error) {
                downloadStatus = null;
                updateList();
                BulletinFactory.of(YumiAiSettingsActivity.this).createSimpleBulletin(R.raw.error, "Download error: " + error).show();
            }
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void updateList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
