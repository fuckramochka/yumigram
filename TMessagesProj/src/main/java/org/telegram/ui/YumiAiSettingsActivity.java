package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.yumigram.ai.YumiLocalAiManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.util.ArrayList;

/**
 * Dedicated settings screen for Yumi AI Agent (ШІ Агент Юмі).
 * Configures autonomous agent capabilities, cloud/local engine, models and voice transcription.
 */
public class YumiAiSettingsActivity extends UniversalFragment {

    private static final int ID_AI_OPEN_COPILOT = 1;
    private static final int ID_AI_ENABLED = 2;

    private static final int ID_AI_AGENT_READ_CHAT = 10;
    private static final int ID_AI_AGENT_SEND_MESSAGES = 11;
    private static final int ID_AI_AGENT_SMART_REPLIES = 12;
    private static final int ID_AI_VOICE_TRANSCRIPTION = 13;

    private static final int ID_AI_MODE_CLOUD = 20;
    private static final int ID_AI_MODE_LOCAL = 21;
    private static final int ID_AI_API_KEY = 22;
    private static final int ID_AI_PRIMARY_MODEL = 23;
    private static final int ID_AI_FALLBACK_MODEL = 24;
    private static final int ID_AI_LOCAL_MODEL = 25;
    private static final int ID_AI_DOWNLOAD_LOCAL = 26;

    private String downloadStatus = null;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YumiMenuAI);
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
        // --- 1. Quick Launch AI Agent ---
        items.add(UItem.asButton(ID_AI_OPEN_COPILOT, R.drawable.msg_bot, getString(R.string.YumiAiOpenCopilot)));

        // --- 2. Master Toggle ---
        items.add(UItem.asHeader(getString(R.string.YumiSectionAI)));
        items.add(UItem.asCheck(ID_AI_ENABLED, getString(R.string.YumiAiEnabled)).setChecked(YumiConfig.isAiEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiAiEnabledInfo)));

        if (!YumiConfig.isAiEnabled()) {
            return;
        }

        // --- 3. Agent Capabilities & Telegram Access ---
        items.add(UItem.asHeader(getString(R.string.YumiAiAgentPermissions)));
        items.add(UItem.asCheck(ID_AI_AGENT_READ_CHAT, getString(R.string.YumiAiAgentReadChat)).setChecked(YumiConfig.isAiAgentReadChatEnabled()));
        items.add(UItem.asCheck(ID_AI_AGENT_SEND_MESSAGES, getString(R.string.YumiAiAgentSendMessages)).setChecked(YumiConfig.isAiAgentSendMessagesEnabled()));
        items.add(UItem.asCheck(ID_AI_AGENT_SMART_REPLIES, getString(R.string.YumiAiAgentSmartReplies)).setChecked(YumiConfig.isAiAgentSmartRepliesEnabled()));
        items.add(UItem.asCheck(ID_AI_VOICE_TRANSCRIPTION, getString(R.string.YumiAiVoiceTranscription)).setChecked(YumiConfig.isAiVoiceTranscriptionEnabled()));
        items.add(UItem.asShadow(getString(R.string.YumiAiVoiceTranscriptionInfo)));

        // --- 4. Engine & Model Configuration ---
        items.add(UItem.asHeader(getString(R.string.YumiAiMode)));
        boolean isLocal = YumiConfig.isLocalAi();
        items.add(UItem.asRadio(ID_AI_MODE_CLOUD, getString(R.string.YumiAiModeCloud)).setChecked(!isLocal));
        items.add(UItem.asRadio(ID_AI_MODE_LOCAL, getString(R.string.YumiAiModeLocal)).setChecked(isLocal));

        if (!isLocal) {
            // Cloud Gemini Settings
            String key = YumiConfig.getAiApiKey();
            String keyMasked = TextUtils.isEmpty(key) ? getString(R.string.YumiCloudApiKeyNotSet) : "••••••••" + key.substring(Math.max(0, key.length() - 4));
            items.add(UItem.asButton(ID_AI_API_KEY, String.format(getString(R.string.YumiAiApiKey), keyMasked)));
            items.add(UItem.asButton(ID_AI_PRIMARY_MODEL, String.format(getString(R.string.YumiAiPrimaryModel), YumiConfig.getAiPrimaryModel())));
            items.add(UItem.asButton(ID_AI_FALLBACK_MODEL, String.format(getString(R.string.YumiAiFallbackModel), YumiConfig.getAiFallbackModel())));
            items.add(UItem.asShadow(getString(R.string.YumiAiCloudModeDesc)));
        } else {
            // Local Hugging Face Settings
            YumiLocalAiManager.LocalModelInfo modelInfo = YumiLocalAiManager.getModelInfo(YumiConfig.getAiLocalModelId());
            boolean downloaded = YumiLocalAiManager.isModelDownloaded(modelInfo.id);

            items.add(UItem.asButton(ID_AI_LOCAL_MODEL, String.format(getString(R.string.YumiAiLocalModel), modelInfo.name, modelInfo.size)));
            String btnText = downloaded ? getString(R.string.YumiAiModelDownloaded) : (downloadStatus != null ? downloadStatus : getString(R.string.YumiAiDownloadLocal));
            items.add(UItem.asButton(ID_AI_DOWNLOAD_LOCAL, btnText));
            items.add(UItem.asShadow(getString(R.string.YumiAiLocalModeDesc)));
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
            case ID_AI_MODE_CLOUD:
                YumiConfig.setAiMode("cloud");
                updateList();
                break;
            case ID_AI_MODE_LOCAL:
                YumiConfig.setAiMode("local");
                updateList();
                break;
            case ID_AI_API_KEY:
                showApiKeyDialog();
                break;
            case ID_AI_PRIMARY_MODEL:
                showModelSelectionDialog(true);
                break;
            case ID_AI_FALLBACK_MODEL:
                showModelSelectionDialog(false);
                break;
            case ID_AI_LOCAL_MODEL:
                showLocalModelSelectionDialog();
                break;
            case ID_AI_DOWNLOAD_LOCAL:
                startLocalModelDownload();
                break;
        }
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.YumiAiApiKey));

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("AIzaSy...");
        input.setText(YumiConfig.getAiApiKey());
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String key = input.getText().toString().trim();
            YumiConfig.setAiApiKey(key);
            updateList();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showModelSelectionDialog(boolean isPrimary) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(isPrimary ? getString(R.string.YumiAiPrimaryModel) : getString(R.string.YumiAiFallbackModel));

        final String[] models = YumiConfig.CLOUD_MODELS;
        String current = isPrimary ? YumiConfig.getAiPrimaryModel() : YumiConfig.getAiFallbackModel();
        int selectedIndex = 0;
        for (int i = 0; i < models.length; i++) {
            if (models[i].equalsIgnoreCase(current)) {
                selectedIndex = i;
                break;
            }
        }

        builder.setItems(models, (dialog, which) -> {
            if (isPrimary) {
                YumiConfig.setAiPrimaryModel(models[which]);
            } else {
                YumiConfig.setAiFallbackModel(models[which]);
            }
            dialog.dismiss();
            updateList();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showLocalModelSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.YumiAiLocalModel));

        final java.util.List<YumiLocalAiManager.LocalModelInfo> models = YumiLocalAiManager.AVAILABLE_LOCAL_MODELS;
        CharSequence[] modelNames = new CharSequence[models.size()];
        String currentId = YumiConfig.getAiLocalModelId();

        for (int i = 0; i < models.size(); i++) {
            YumiLocalAiManager.LocalModelInfo m = models.get(i);
            boolean dl = YumiLocalAiManager.isModelDownloaded(m.id);
            modelNames[i] = m.name + " (" + m.size + ")" + (dl ? " ✅" : "");
        }

        builder.setItems(modelNames, (dialog, which) -> {
            YumiConfig.setAiLocalModelId(models.get(which).id);
            downloadStatus = null;
            dialog.dismiss();
            updateList();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void startLocalModelDownload() {
        YumiLocalAiManager.LocalModelInfo info = YumiLocalAiManager.getModelInfo(YumiConfig.getAiLocalModelId());
        if (YumiLocalAiManager.isModelDownloaded(info.id)) {
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_bot, getString(R.string.YumiAiModelDownloaded)).show();
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
                BulletinFactory.of(YumiAiSettingsActivity.this).createSimpleBulletin(R.drawable.msg_bot, getString(R.string.YumiAiModelDownloaded)).show();
            }

            @Override
            public void onError(String error) {
                downloadStatus = "⚠️ " + error;
                updateList();
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
