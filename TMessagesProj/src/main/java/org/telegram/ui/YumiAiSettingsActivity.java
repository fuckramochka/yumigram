package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import org.telegram.ui.Components.YumiAgentTheme;
import org.telegram.ui.ActionBar.AlertDialog;

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
    private String switchToLocalAfterDownloadModelId = null;

    private View createAgentHero(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(org.telegram.messenger.AndroidUtilities.dp(18), org.telegram.messenger.AndroidUtilities.dp(16), org.telegram.messenger.AndroidUtilities.dp(18), org.telegram.messenger.AndroidUtilities.dp(16));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{YumiAgentTheme.colors().accentSoft, YumiAgentTheme.colors().surface, YumiAgentTheme.colors().background});
        background.setCornerRadius(org.telegram.messenger.AndroidUtilities.dp(22));
        background.setStroke(org.telegram.messenger.AndroidUtilities.dp(1), YumiAgentTheme.colors().outline);
        card.setBackground(background);

        TextView mark = new TextView(context);
        mark.setText("✦");
        mark.setTextSize(25);
        mark.setTextColor(Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable orb = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{YumiAgentTheme.colors().accent, YumiAgentTheme.colors().accentSoft});
        orb.setShape(GradientDrawable.OVAL);
        mark.setBackground(orb);
        card.addView(mark, org.telegram.ui.Components.LayoutHelper.createLinear(52, 52));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(org.telegram.messenger.AndroidUtilities.dp(14), 0, 0, 0);
        TextView title = new TextView(context);
        title.setText(YumiLocale.getString(R.string.YumiAiWorkspaceTitle));
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setTextColor(YumiAgentTheme.colors().primaryText);
        labels.addView(title, org.telegram.ui.Components.LayoutHelper.createLinear(-1, -2));
        TextView subtitle = new TextView(context);
        subtitle.setText(YumiLocale.getString(R.string.YumiAiSettingsHeroSubtitle));
        subtitle.setTextSize(13);
        subtitle.setTextColor(YumiAgentTheme.colors().secondaryText);
        subtitle.setMaxLines(2);
        labels.addView(subtitle, org.telegram.ui.Components.LayoutHelper.createLinear(-1, -2, 0, 4, 0, 0));
        card.addView(labels, org.telegram.ui.Components.LayoutHelper.createLinear(0, -2, 1));
        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setTextColor(YumiAgentTheme.colors().accent);
        card.addView(arrow, org.telegram.ui.Components.LayoutHelper.createLinear(28, 42));
        card.setOnClickListener(v -> presentFragment(new YumiAiCopilotActivity()));
        return card;
    }

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
        // 1. One visual entry point to the AI workspace.
        Context context = getParentActivity();
        if (context != null) items.add(UItem.asCustomShadow(createAgentHero(context), 92));
        else items.add(UItem.asButton(ID_AI_OPEN_COPILOT, R.drawable.msg_bot, YumiLocale.getString(R.string.YumiAiOpenCopilot)));

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
            if (!YumiLocalAiManager.isLocalInferenceSupported()) {
                items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiAiLocalUnsupportedInfo)));
                if (downloaded) items.add(UItem.asButton(ID_AI_DOWNLOAD_LOCAL, YumiLocale.getString(R.string.YumiAiRemoveLocalModel)));
            } else {
                items.add(UItem.asButton(ID_AI_LOCAL_MODEL, String.format(YumiLocale.getString(R.string.YumiAiLocalModel), modelInfo.name, modelInfo.size)));
                String btnText = downloadStatus != null ? downloadStatus : (downloaded ? YumiLocale.getString(R.string.YumiAiRemoveLocalModel) : YumiLocale.getString(R.string.YumiAiDownloadLocal));
                items.add(UItem.asButton(ID_AI_DOWNLOAD_LOCAL, btnText));
                items.add(UItem.asShadow(YumiLocale.getString(R.string.YumiAiLocalModeDesc)));
            }
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
                    switchToLocalAfterDownloadModelId = null;
                } else {
                    if (!YumiLocalAiManager.isLocalInferenceSupported()) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, YumiLocale.getString(R.string.YumiAiLocalUnsupportedInfo)).show();
                        break;
                    }
                    if (downloadStatus != null) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, YumiLocale.getString(R.string.YumiAiLocalDownloadInProgress)).show();
                        break;
                    }
                    YumiLocalAiManager.LocalModelInfo model = YumiLocalAiManager.getModelInfo(YumiConfig.getAiLocalModelId());
                    if (!YumiLocalAiManager.isModelDownloaded(model.id)) {
                        new AlertDialog.Builder(getParentActivity())
                                .setTitle(YumiLocale.getString(R.string.YumiAiSwitchLocalRequiresDownloadTitle))
                                .setMessage(String.format(YumiLocale.getString(R.string.YumiAiSwitchLocalRequiresDownloadMessage), model.name, model.size))
                                .setNegativeButton(YumiLocale.getString(R.string.Cancel), null)
                                .setPositiveButton(YumiLocale.getString(R.string.YumiAiSwitchLocalDownloadAction), (dialog, which) -> {
                                    if (YumiLocalAiManager.isModelDownloaded(model.id)) {
                                        YumiConfig.setAiLocalModelId(model.id);
                                        YumiConfig.setAiMode("local");
                                        updateList();
                                        return;
                                    }
                                    switchToLocalAfterDownloadModelId = model.id;
                                    startLocalModelDownload();
                                }).show();
                        break;
                    }
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
                if (downloadStatus != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, YumiLocale.getString(R.string.YumiAiLocalDownloadInProgress)).show();
                } else {
                    showLocalModelWheelPicker();
                }
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
                YumiLocalAiManager.LocalModelInfo selected = list.get(index);
                if (!YumiLocalAiManager.isModelDownloaded(selected.id)) {
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle(YumiLocale.getString(R.string.YumiAiSwitchLocalRequiresDownloadTitle))
                            .setMessage(String.format(YumiLocale.getString(R.string.YumiAiSwitchLocalRequiresDownloadMessage), selected.name, selected.size))
                            .setNegativeButton(YumiLocale.getString(R.string.Cancel), null)
                            .setPositiveButton(YumiLocale.getString(R.string.YumiAiSwitchLocalDownloadAction), (dialog, which) -> {
                                if (YumiLocalAiManager.isModelDownloaded(selected.id)) {
                                    YumiConfig.setAiLocalModelId(selected.id);
                                    updateList();
                                    return;
                                }
                                switchToLocalAfterDownloadModelId = selected.id;
                                startLocalModelDownload(selected.id);
                            }).show();
                    return;
                }
                YumiConfig.setAiLocalModelId(selected.id);
                updateList();
            }
        }).show();
    }

    private void startLocalModelDownload() {
        startLocalModelDownload(YumiConfig.getAiLocalModelId());
    }

    private void startLocalModelDownload(String modelId) {
        if (downloadStatus != null) return;
        YumiLocalAiManager.LocalModelInfo info = YumiLocalAiManager.getModelInfo(modelId);
        if (YumiLocalAiManager.isModelDownloaded(info.id)) {
            if (switchToLocalAfterDownloadModelId != null && switchToLocalAfterDownloadModelId.equals(info.id)) {
                YumiConfig.setAiLocalModelId(info.id);
                YumiConfig.setAiMode("local");
                switchToLocalAfterDownloadModelId = null;
                updateList();
                return;
            }
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(YumiLocale.getString(R.string.YumiAiDeleteLocalModelTitle))
                    .setMessage(String.format(YumiLocale.getString(R.string.YumiAiDeleteLocalModelMessage), info.name, info.size))
                    .setNegativeButton(YumiLocale.getString(R.string.YumiAiDeleteLocalModelCancel), null)
                    .setPositiveButton(YumiLocale.getString(R.string.YumiAiDeleteLocalModelAction), (dialog, which) -> {
                        downloadStatus = YumiLocale.getString(R.string.YumiAiRemovingLocalModel);
                        updateList();
                        YumiLocalAiManager.deleteModel(info.id, new YumiLocalAiManager.DeleteModelCallback() {
                            @Override public void onSuccess() {
                                downloadStatus = null;
                                if (YumiConfig.isLocalAi() && YumiConfig.getAiLocalModelId().equals(info.id)) {
                                    YumiConfig.setAiMode("cloud");
                                }
                                updateList();
                                BulletinFactory.of(YumiAiSettingsActivity.this).createSimpleBulletin(R.raw.chats_infotip, YumiLocale.getString(R.string.YumiAiLocalModelRemoved)).show();
                            }
                            @Override public void onError(String error) {
                                downloadStatus = null;
                                updateList();
                                BulletinFactory.of(YumiAiSettingsActivity.this).createSimpleBulletin(R.raw.error, error).show();
                            }
                        });
                    }).show();
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
                if (switchToLocalAfterDownloadModelId != null && info.id.equals(switchToLocalAfterDownloadModelId)) {
                    YumiConfig.setAiLocalModelId(info.id);
                    YumiConfig.setAiMode("local");
                    switchToLocalAfterDownloadModelId = null;
                }
                updateList();
                BulletinFactory.of(YumiAiSettingsActivity.this).createSimpleBulletin(R.raw.chats_infotip,
                        YumiConfig.isLocalAi() ? YumiLocale.getString(R.string.YumiAiLocalModeEnabledAfterDownload) : YumiLocale.getString(R.string.YumiAiModelDownloaded)).show();
            }

            @Override
            public void onError(String error) {
                downloadStatus = null;
                switchToLocalAfterDownloadModelId = null;
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
