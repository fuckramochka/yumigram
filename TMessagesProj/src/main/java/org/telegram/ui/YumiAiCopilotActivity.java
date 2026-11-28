package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.text.TextUtils;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.util.Linkify;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.yumigram.ai.YumiAgentEngine;
import org.telegram.messenger.yumigram.ai.YumiAgentConversationStore;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.YumiAgentConfirmationSheet;
import org.telegram.ui.Components.YumiAgentTheme;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern AI Agent Copilot Chat for Yumigram.
 * - Powered by autonomous YumiAgentEngine (Telegram tools, reasoning, smart actions)
 * - Persistent chat history saved to disk
 * - Telegram-native bubble styling with copy/share
 * - Quick prompt suggestion chips
 * - Clear history action
 */
public class YumiAiCopilotActivity extends BaseFragment {

    private static final String HISTORY_FILE = "yumi_agent_chat.json";
    private static final int MENU_HISTORY = 1;
    private static final int REQUEST_VOICE_INPUT = 7412;
    private static final int REQUEST_AVATAR_IMAGE = 7413;
    private static final int REQUEST_SEND_FILE = 7414;

    public static class ChatEntry {
        public final String id;
        public final boolean isAi;
        public String text;
        public boolean isThinking;
        public String attachmentName;
        public String attachmentMime;

        public ChatEntry(boolean isAi, String text) {
            this(UUID.randomUUID().toString(), isAi, text, false);
        }

        public ChatEntry(boolean isAi, String text, boolean isThinking) {
            this(UUID.randomUUID().toString(), isAi, text, isThinking);
        }

        public ChatEntry(String id, boolean isAi, String text, boolean isThinking) {
            this.id = id == null ? UUID.randomUUID().toString() : id;
            this.isAi = isAi;
            this.text = text;
            this.isThinking = isThinking;
        }
    }

    private final ArrayList<ChatEntry> messages = new ArrayList<>();
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private RecyclerView conversationsRecyclerView;
    private ConversationAdapter conversationAdapter;
    private EditText historySearch;
    private TextView historyEmptyView;
    private final ArrayList<YumiAgentConversationStore.Conversation> conversationItems = new ArrayList<>();
    private FrameLayout rootView;
    private FrameLayout drawerOverlay;
    private FrameLayout drawerPanel;
    private LinearLayout welcomePanel;
    private LinearLayout composerCard;
    private EditText inputEditText;
    private ImageView sendButton;
    private TextView modelStatusView;
    private String conversationId;
    private int conversationAccount = -1;
    private boolean conversationReady;
    private boolean isAgentWorking = false;
    private int pendingAvatarAccount = -1;
    private long pendingFileDialogId;
    private int pendingFileAccount = -1;
    private String pendingFileCaption;
    private String pendingFileRecipient;
    private ChatEntry pendingFileThinkingEntry;
    private int pendingFileThinkingPosition = -1;
    private int conversationSearchGeneration;
    private ChatEntry composerFileEntry;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(YumiLocale.getString(R.string.YumiAiWorkspaceTitle));
        actionBar.setSubtitle(YumiLocale.getString(YumiConfig.isLocalAi() ? R.string.YumiAiWorkspaceSubtitleLocal : R.string.YumiAiWorkspaceSubtitleCloud));

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_HISTORY, R.drawable.msg_recent);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (drawerPanel != null && drawerPanel.getVisibility() == View.VISIBLE) hideConversationDrawer();
                    else finishFragment();
                } else if (id == MENU_HISTORY) {
                    showConversationDrawer();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(YumiAgentTheme.colors().background);
        fragmentView = root;
        rootView = root;

        recyclerView = new RecyclerView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, dp(8), 0, dp(12));
        root.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 96));

        welcomePanel = buildWelcomePanel(context);
        root.addView(welcomePanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 92));

        LinearLayout bottomPanel = new LinearLayout(context);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        bottomPanel.setBackgroundColor(YumiAgentTheme.colors().background);

        composerCard = new LinearLayout(context);
        composerCard.setOrientation(LinearLayout.VERTICAL);
        composerCard.setPadding(dp(8), dp(4), dp(8), dp(4));
        composerCard.setBackground(agentSurface(YumiAgentTheme.colors().surface, dp(24), YumiAgentTheme.colors().outline));

        LinearLayout inputBar = new LinearLayout(context);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setPadding(dp(2), dp(2), dp(2), dp(2));
        inputBar.setGravity(Gravity.CENTER_VERTICAL);

        inputEditText = new EditText(context);
        inputEditText.setHint(YumiLocale.getString(R.string.YumiAiWorkspaceInputHint));
        inputEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        inputEditText.setTextColor(YumiAgentTheme.colors().primaryText);
        inputEditText.setHintTextColor(YumiAgentTheme.colors().secondaryText);
        inputEditText.setBackground(null);
        inputEditText.setMinLines(1);
        inputEditText.setMaxLines(5);
        inputEditText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        inputBar.addView(inputEditText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        ImageView attachButton = createComposerButton(context, R.drawable.msg_add, "Отправить файл в Telegram");
        attachButton.setOnClickListener(v -> openRecipientPickerForFile());
        inputBar.addView(attachButton, LayoutHelper.createLinear(40, 40));

        ImageView voiceButton = createComposerButton(context, R.drawable.msg_voicechat, "Голосовой ввод");
        voiceButton.setContentDescription("Голосовой ввод");
        voiceButton.setOnClickListener(v -> startVoiceInput());
        inputBar.addView(voiceButton, LayoutHelper.createLinear(40, 40));

        sendButton = new ImageView(context);
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setColorFilter(YumiAgentTheme.colors().accent);
        sendButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        sendButton.setOnClickListener(v -> sendMessage());
        sendButton.setContentDescription("Отправить запрос AI");
        inputBar.addView(sendButton, LayoutHelper.createLinear(40, 40));
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateComposerState(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        composerCard.addView(inputBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        modelStatusView = new TextView(context);
        modelStatusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        modelStatusView.setTextColor(YumiAgentTheme.colors().secondaryText);
        modelStatusView.setGravity(Gravity.CENTER);
        modelStatusView.setText(YumiLocale.getString(YumiConfig.isLocalAi() ? R.string.YumiAiWorkspaceLocalNotice : R.string.YumiAiWorkspaceCloudNotice));
        composerCard.addView(modelStatusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 24));
        bottomPanel.addView(composerCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(bottomPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        drawerOverlay = new FrameLayout(context);
        drawerOverlay.setBackgroundColor(0x99000000);
        drawerOverlay.setVisibility(View.GONE);
        drawerOverlay.setOnClickListener(v -> hideConversationDrawer());
        root.addView(drawerOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        drawerPanel = buildConversationDrawer(context);
        drawerPanel.setVisibility(View.GONE);
        int drawerWidth = Math.max(dp(240), Math.min(dp(360), context.getResources().getDisplayMetrics().widthPixels - dp(40)));
        root.addView(drawerPanel, LayoutHelper.createFrame(drawerWidth, LayoutHelper.MATCH_PARENT, Gravity.START));

        updateWelcomeVisibility();
        updateComposerState();
        loadChatHistory();

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        int selectedAccount = org.telegram.messenger.UserConfig.selectedAccount;
        if (conversationAccount >= 0 && selectedAccount != conversationAccount && adapter != null) {
            messages.clear();
            adapter.notifyDataSetChanged();
            conversationId = null;
            loadChatHistory();
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (drawerPanel != null && drawerPanel.getVisibility() == View.VISIBLE) {
            if (invoked) hideConversationDrawer();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private GradientDrawable agentSurface(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private ImageView createComposerButton(Context context, int icon, String description) {
        ImageView button = new ImageView(context);
        button.setImageResource(icon);
        button.setColorFilter(YumiAgentTheme.colors().accent);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setContentDescription(description);
        return button;
    }

    private LinearLayout buildWelcomePanel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(8), dp(12), dp(8), dp(12));

        TextView eyebrow = new TextView(context);
        eyebrow.setText("YUMI  ·  AI AGENT");
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        eyebrow.setLetterSpacing(.08f);
        eyebrow.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        eyebrow.setTextColor(YumiAgentTheme.colors().accent);
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setPadding(dp(13), dp(7), dp(13), dp(7));
        eyebrow.setBackground(agentSurface(YumiAgentTheme.colors().accentSoft, dp(18), YumiAgentTheme.colors().outline));
        panel.addView(eyebrow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 17));

        FrameLayout orb = new FrameLayout(context);
        GradientDrawable orbBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{YumiAgentTheme.colors().accent, YumiAgentTheme.colors().accentSoft, YumiAgentTheme.colors().surface});
        orbBackground.setShape(GradientDrawable.OVAL);
        orb.setBackground(orbBackground);
        orb.setElevation(dp(7));
        TextView glyph = new TextView(context);
        glyph.setText("✦");
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        glyph.setTextColor(Color.WHITE);
        glyph.setGravity(Gravity.CENTER);
        orb.addView(glyph, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        panel.addView(orb, LayoutHelper.createLinear(84, 84, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));

        TextView title = new TextView(context);
        title.setText(YumiLocale.getString(R.string.YumiAiWelcomeTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 27);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setTextColor(YumiAgentTheme.colors().primaryText);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        TextView subtitle = new TextView(context);
        subtitle.setText(YumiLocale.getString(R.string.YumiAiWelcomeDescription));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setTextColor(YumiAgentTheme.colors().secondaryText);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxWidth(dp(340));
        panel.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 18));

        String[] prompts = {YumiLocale.getString(R.string.YumiAiStarterSummary), YumiLocale.getString(R.string.YumiAiStarterSearch), YumiLocale.getString(R.string.YumiAiStarterFile)};
        for (String prompt : prompts) {
            TextView suggestion = new TextView(context);
            suggestion.setText(prompt + "   ›");
            suggestion.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            suggestion.setTextColor(YumiAgentTheme.colors().primaryText);
            suggestion.setPadding(dp(14), dp(11), dp(14), dp(11));
            suggestion.setBackground(agentSurface(YumiAgentTheme.colors().surface, dp(15), YumiAgentTheme.colors().outline));
            LinearLayout.LayoutParams suggestionLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 5, 0, 5);
            panel.addView(suggestion, suggestionLp);
            suggestion.setOnClickListener(v -> {
                inputEditText.setText(prompt);
                inputEditText.setSelection(inputEditText.length());
                inputEditText.requestFocus();
            });
        }
        return panel;
    }

    private FrameLayout buildConversationDrawer(Context context) {
        FrameLayout panel = new FrameLayout(context);
        panel.setBackgroundColor(YumiAgentTheme.colors().surface);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(28), dp(12), dp(12));
        panel.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(YumiLocale.getString(R.string.YumiAiHistoryTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setTextColor(YumiAgentTheme.colors().primaryText);
        header.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1));
        TextView newChat = new TextView(context);
        newChat.setText("＋");
        newChat.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        newChat.setTextColor(YumiAgentTheme.colors().accent);
        newChat.setGravity(Gravity.CENTER);
        newChat.setBackground(agentSurface(YumiAgentTheme.colors().accentSoft, dp(14), YumiAgentTheme.colors().outline));
        newChat.setContentDescription(YumiLocale.getString(R.string.YumiAiNewChat));
        header.addView(newChat, LayoutHelper.createLinear(44, 44));
        newChat.setOnClickListener(v -> startNewConversation());
        content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        historySearch = new EditText(context);
        historySearch.setSingleLine(true);
        historySearch.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        historySearch.setHint(YumiLocale.getString(R.string.YumiAiHistorySearch));
        historySearch.setHintTextColor(YumiAgentTheme.colors().secondaryText);
        historySearch.setTextColor(YumiAgentTheme.colors().primaryText);
        historySearch.setPadding(dp(14), 0, dp(14), 0);
        historySearch.setBackground(agentSurface(YumiAgentTheme.colors().background, dp(14), Color.TRANSPARENT));
        content.addView(historySearch, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 10, 0, 12));

        conversationsRecyclerView = new RecyclerView(context);
        conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        conversationAdapter = new ConversationAdapter();
        conversationsRecyclerView.setAdapter(conversationAdapter);
        FrameLayout historyContainer = new FrameLayout(context);
        historyContainer.addView(conversationsRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        historyEmptyView = new TextView(context);
        historyEmptyView.setText(YumiLocale.getString(R.string.YumiAiHistoryEmpty));
        historyEmptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        historyEmptyView.setTextColor(YumiAgentTheme.colors().secondaryText);
        historyEmptyView.setGravity(Gravity.CENTER);
        historyEmptyView.setVisibility(View.GONE);
        historyContainer.addView(historyEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        content.addView(historyContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1));
        historySearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { searchConversationList(s == null ? "" : s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        return panel;
    }

    private void showConversationDrawer() {
        if (drawerOverlay == null || drawerPanel == null) return;
        refreshConversationList();
        drawerOverlay.setVisibility(View.VISIBLE);
        drawerPanel.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(drawerHiddenTranslation());
        drawerPanel.animate().translationX(0).setDuration(190).start();
    }

    private void hideConversationDrawer() {
        if (drawerOverlay == null || drawerPanel == null) return;
        drawerPanel.animate().translationX(drawerHiddenTranslation()).setDuration(160).withEndAction(() -> {
            if (drawerPanel != null) drawerPanel.setVisibility(View.GONE);
            if (drawerOverlay != null) drawerOverlay.setVisibility(View.GONE);
        }).start();
    }

    private float drawerHiddenTranslation() {
        float sign = org.telegram.messenger.LocaleController.isRTL ? 1f : -1f;
        return sign * dp(360);
    }

    private void refreshConversationList() {
        if (conversationAccount < 0) return;
        int account = conversationAccount;
        YumiAgentConversationStore.listConversations(account, new YumiAgentConversationStore.Callback<List<YumiAgentConversationStore.Conversation>>() {
            @Override public void onResult(List<YumiAgentConversationStore.Conversation> rows) {
                if (account != conversationAccount || conversationAdapter == null) return;
                conversationItems.clear();
                if (rows != null) conversationItems.addAll(rows);
                searchConversationList(historySearch == null ? "" : historySearch.getText().toString());
            }
            @Override public void onError(String error) { FileLog.e("Yumi AI: " + error); }
        });
    }

    private void searchConversationList(String query) {
        if (conversationAdapter == null || conversationAccount < 0) return;
        int account = conversationAccount;
        int generation = ++conversationSearchGeneration;
        YumiAgentConversationStore.searchConversations(account, query, new YumiAgentConversationStore.Callback<List<YumiAgentConversationStore.Conversation>>() {
            @Override public void onResult(List<YumiAgentConversationStore.Conversation> rows) {
                if (account != conversationAccount || generation != conversationSearchGeneration || conversationAdapter == null) return;
                conversationAdapter.showRows(rows, query);
            }
            @Override public void onError(String error) { if (generation == conversationSearchGeneration) showHistoryError(error); }
        });
    }

    private void loadChatHistory() {
        messages.clear();
        conversationReady = false;
        conversationAccount = org.telegram.messenger.UserConfig.selectedAccount;
        int account = conversationAccount;
        String legacy = readLegacyHistory();
        YumiAgentConversationStore.importLegacyHistory(account, legacy, new YumiAgentConversationStore.Callback<String>() {
            @Override public void onResult(String importedConversationId) {
                if (account != org.telegram.messenger.UserConfig.selectedAccount || account != conversationAccount) return;
                YumiAgentConversationStore.listConversations(account, new YumiAgentConversationStore.Callback<List<YumiAgentConversationStore.Conversation>>() {
                    @Override public void onResult(List<YumiAgentConversationStore.Conversation> rows) {
                        if (account != org.telegram.messenger.UserConfig.selectedAccount || account != conversationAccount) return;
                        if (rows == null || rows.isEmpty()) {
                            createInitialConversation(account);
                            return;
                        }
                        String id = !TextUtils.isEmpty(importedConversationId) ? importedConversationId : rows.get(0).id;
                        openConversation(account, id);
                    }
                    @Override public void onError(String error) { showHistoryError(error); }
                });
            }
            @Override public void onError(String error) { showHistoryError(error); }
        });
    }

    private String readLegacyHistory() {
        File file = new File(ApplicationLoader.applicationContext.getFilesDir(), HISTORY_FILE);
        if (!file.exists()) return "[]";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder value = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
            return value.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "[]";
        }
    }

    private void createInitialConversation(int account) {
        YumiAgentConversationStore.createConversation(account, new YumiAgentConversationStore.Callback<YumiAgentConversationStore.Conversation>() {
            @Override public void onResult(YumiAgentConversationStore.Conversation conversation) { if (account == conversationAccount) openConversation(account, conversation.id); }
            @Override public void onError(String error) { showHistoryError(error); }
        });
    }

    private void openConversation(int account, String id) {
        conversationId = id;
        conversationReady = false;
        messages.clear();
        adapter.notifyDataSetChanged();
        updateWelcomeVisibility();
        updateComposerState();
        YumiAgentConversationStore.loadMessages(account, id, new YumiAgentConversationStore.Callback<List<YumiAgentConversationStore.AgentMessage>>() {
            @Override public void onResult(List<YumiAgentConversationStore.AgentMessage> rows) {
                if (account != conversationAccount || !TextUtils.equals(id, conversationId)) return;
                messages.clear();
                if (rows != null) for (YumiAgentConversationStore.AgentMessage row : rows) {
                    ChatEntry entry = new ChatEntry(row.id, row.role == YumiAgentConversationStore.ROLE_ASSISTANT, row.content, false);
                    entry.attachmentName = row.attachmentName;
                    entry.attachmentMime = row.attachmentMime;
                    messages.add(entry);
                }
                conversationReady = true;
                adapter.notifyDataSetChanged();
                updateWelcomeVisibility();
                updateComposerState();
                if (!messages.isEmpty()) recyclerView.scrollToPosition(messages.size() - 1);
                refreshConversationList();
            }
            @Override public void onError(String error) { showHistoryError(error); }
        });
    }

    private void startNewConversation() {
        if (isAgentWorking || conversationAccount != org.telegram.messenger.UserConfig.selectedAccount) return;
        int account = conversationAccount;
        YumiAgentConversationStore.createConversation(account, new YumiAgentConversationStore.Callback<YumiAgentConversationStore.Conversation>() {
            @Override public void onResult(YumiAgentConversationStore.Conversation conversation) {
                if (account != conversationAccount || account != org.telegram.messenger.UserConfig.selectedAccount) return;
                openConversation(account, conversation.id);
                hideConversationDrawer();
            }
            @Override public void onError(String error) { showHistoryError(error); }
        });
    }

    private void showConversationActions(YumiAgentConversationStore.Conversation conversation) {
        if (getParentActivity() == null || conversation == null) return;
        CharSequence[] actions = {"Переименовать", "Очистить сообщения", "Удалить диалог"};
        new org.telegram.ui.ActionBar.BottomSheet.Builder(getParentActivity())
                .setTitle(flattenConfirmationText(getConversationTitle(conversation)))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showRenameConversation(conversation);
                    else if (which == 1) confirmClearConversation(conversation);
                    else if (which == 2) confirmDeleteConversation(conversation);
                }).show();
    }

    private void showRenameConversation(YumiAgentConversationStore.Conversation conversation) {
        if (getParentActivity() == null) return;
        EditText edit = new EditText(getParentActivity());
        edit.setSingleLine(true);
        edit.setText(getConversationTitle(conversation));
        edit.setSelection(edit.length());
        edit.setPadding(dp(18), dp(12), dp(18), dp(12));
        new org.telegram.ui.ActionBar.BottomSheet.Builder(getParentActivity())
                .setTitle("Переименовать AI-чат", true)
                .setCustomView(edit)
                .setItems(new CharSequence[]{"Сохранить", "Отмена"}, (sheet, which) -> {
                    if (which != 0) return;
                    int account = conversationAccount;
                    String title = edit.getText().toString().trim();
                    YumiAgentConversationStore.renameConversation(account, conversation.id, title, new YumiAgentConversationStore.Callback<Boolean>() {
                        @Override public void onResult(Boolean result) { refreshConversationList(); }
                        @Override public void onError(String error) { showHistoryError(error); }
                    });
                }).show();
    }

    private void confirmClearConversation(YumiAgentConversationStore.Conversation conversation) {
        YumiAgentConfirmationSheet.show(getParentActivity(), "Очистить диалог?",
                "Все сообщения в этом AI-чате будут удалены. Сам диалог останется в истории.", "Отмена", "Очистить", true,
                null, () -> {
                    int account = conversationAccount;
                    YumiAgentConversationStore.clearConversation(account, conversation.id, new YumiAgentConversationStore.Callback<Boolean>() {
                        @Override public void onResult(Boolean result) {
                            if (TextUtils.equals(conversationId, conversation.id)) {
                                messages.clear();
                                adapter.notifyDataSetChanged();
                                updateWelcomeVisibility();
                            }
                            refreshConversationList();
                        }
                        @Override public void onError(String error) { showHistoryError(error); }
                    });
                });
    }

    private void confirmDeleteConversation(YumiAgentConversationStore.Conversation conversation) {
        YumiAgentConfirmationSheet.show(getParentActivity(), "Удалить AI-диалог?",
                "«" + flattenConfirmationText(getConversationTitle(conversation)) + "» и все его сообщения будут удалены с этого устройства.", "Оставить", "Удалить диалог", true,
                null, () -> {
                    int account = conversationAccount;
                    YumiAgentConversationStore.deleteConversation(account, conversation.id, new YumiAgentConversationStore.Callback<Boolean>() {
                        @Override public void onResult(Boolean result) {
                            if (TextUtils.equals(conversationId, conversation.id)) {
                                conversationId = null;
                                conversationReady = false;
                                messages.clear();
                                adapter.notifyDataSetChanged();
                                updateWelcomeVisibility();
                                updateComposerState();
                                createInitialConversation(account);
                            }
                            refreshConversationList();
                        }
                        @Override public void onError(String error) { showHistoryError(error); }
                    });
                });
    }

    private void showHistoryError(String error) {
        if (getParentActivity() == null) return;
        conversationReady = false;
        updateComposerState();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, error).show();
    }

    private void updateWelcomeVisibility() {
        if (welcomePanel == null || recyclerView == null) return;
        boolean show = messages.isEmpty();
        welcomePanel.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateComposerState() {
        if (sendButton == null || inputEditText == null) return;
        boolean hasText = !TextUtils.isEmpty(inputEditText.getText());
        sendButton.setAlpha(conversationReady && !isAgentWorking && hasText ? 1f : .42f);
        sendButton.setEnabled(conversationReady && !isAgentWorking && hasText);
    }

    private void persistEntry(ChatEntry entry) {
        if (entry == null || !conversationReady || conversationAccount != org.telegram.messenger.UserConfig.selectedAccount || TextUtils.isEmpty(entry.text)) return;
        YumiAgentConversationStore.saveMessage(conversationAccount, conversationId, entry.id,
                entry.isAi ? YumiAgentConversationStore.ROLE_ASSISTANT : YumiAgentConversationStore.ROLE_USER,
                entry.text, entry.attachmentName, entry.attachmentMime,
                new YumiAgentConversationStore.Callback<Boolean>() {
                    @Override public void onResult(Boolean saved) { refreshConversationList(); }
                    @Override public void onError(String error) { FileLog.e("Yumi AI history: " + error); }
                });
    }

    private void startVoiceInput() {
        if (getParentActivity() == null) return;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите запрос для Юми");
            startActivityForResult(intent, REQUEST_VOICE_INPUT);
        } catch (Exception e) {
            Toast.makeText(getParentActivity(), "Голосовой ввод недоступен на устройстве", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode == REQUEST_SEND_FILE) {
            handleSendFilePickerResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_AVATAR_IMAGE) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
                if (pendingAvatarAccount != org.telegram.messenger.UserConfig.selectedAccount) {
                    pendingAvatarAccount = -1;
                    appendAgentNotice("Аккаунт изменился. Фото профиля не изменено.");
                } else {
                    uploadAvatarFromUri(data.getData());
                }
            } else {
                pendingAvatarAccount = -1;
            }
            return;
        }
        if (requestCode == REQUEST_VOICE_INPUT && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && inputEditText != null) {
                inputEditText.setText(results.get(0));
                inputEditText.setSelection(inputEditText.length());
            }
        }
    }

    private void handleSendFilePickerResult(int resultCode, Intent data) {
        Uri uri = resultCode == android.app.Activity.RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            finishPendingFileAction("Выбор файла отменён.");
            return;
        }
        if (!"content".equalsIgnoreCase(uri.getScheme())) {
            finishPendingFileAction("Системный выборщик вернул неподдерживаемый тип URI. Выбери файл ещё раз.");
            return;
        }
        android.app.Activity activity = getParentActivity();
        if (activity == null || pendingFileAccount != org.telegram.messenger.UserConfig.selectedAccount) {
            finishPendingFileAction("Аккаунт изменился. Файл не отправлен.");
            return;
        }
        String mimeType = activity.getContentResolver().getType(uri);
        if (TextUtils.isEmpty(mimeType)) mimeType = "application/octet-stream";
        String displayName = getUriDisplayName(uri);
        if (TextUtils.isEmpty(displayName)) displayName = "Файл";
        displayName = flattenConfirmationText(displayName);
        if (displayName.length() > 160) displayName = displayName.substring(0, 157) + "…";

        final String selectedName = displayName;
        final String selectedMimeType = mimeType;
        final Uri selectedUri = uri;
        if (pendingFileThinkingEntry != null) {
            pendingFileThinkingEntry.attachmentName = selectedName;
            pendingFileThinkingEntry.attachmentMime = selectedMimeType;
            pendingFileThinkingEntry.text = "Файл · " + selectedName + (TextUtils.isEmpty(pendingFileCaption) ? "" : "\n" + pendingFileCaption);
            int pendingPosition = messages.indexOf(pendingFileThinkingEntry);
            if (pendingPosition >= 0) adapter.notifyItemChanged(pendingPosition);
        }
        String caption = TextUtils.isEmpty(pendingFileCaption) ? "Без подписи" : flattenConfirmationText(pendingFileCaption);
        String safeRecipient = flattenConfirmationText(pendingFileRecipient);
        org.telegram.ui.Components.YumiAgentConfirmationSheet.show(activity, "Отправить файл?",
                "Кому: " + safeRecipient + "\nФайл: " + selectedName + "\nТип: " + flattenConfirmationText(selectedMimeType) + "\n\nПодпись: " + caption,
                "Отмена", "Отправить", false,
                () -> finishPendingFileAction("Отправка файла отменена."), () -> {
                    if (pendingFileAccount != org.telegram.messenger.UserConfig.selectedAccount) {
                        finishPendingFileAction("Аккаунт изменился. Файл не отправлен.");
                        return;
                    }
                    if (!YumiConfig.isAiEnabled() || !YumiConfig.isAiAgentSendMessagesEnabled()) {
                        finishPendingFileAction("Отправка файлов выключена в настройках AI.");
                        return;
                    }
                    try {
                        org.telegram.messenger.SendMessagesHelper.prepareSendingDocument(
                                org.telegram.messenger.AccountInstance.getInstance(pendingFileAccount),
                                null, null, selectedUri, pendingFileCaption, selectedMimeType,
                                pendingFileDialogId, null, null, null, null, null,
                                true, 0, null, null, false);
                        finishPendingFileAction("Файл передан Telegram в очередь отправки. Это ещё не подтверждает доставку.");
                    } catch (Exception e) {
                        FileLog.e(e);
                        finishPendingFileAction("Не удалось передать файл в очередь отправки.");
                    }
                });
    }

    private String getUriDisplayName(Uri uri) {
        android.app.Activity activity = getParentActivity();
        if (activity == null || uri == null) return null;
        try (android.database.Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return uri.getLastPathSegment();
    }

    private String flattenConfirmationText(String value) {
        return flattenConfirmationText(value, 1200);
    }

    private String flattenConfirmationText(String value, int maxLength) {
        if (value == null) return "";
        StringBuilder safe = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || Character.isISOControl(c)) {
                safe.append(' ');
            } else if (c == '\u202A' || c == '\u202B' || c == '\u202D' || c == '\u202E' || c == '\u202C' ||
                    c == '\u2066' || c == '\u2067' || c == '\u2068' || c == '\u2069') {
                continue;
            } else {
                safe.append(c);
            }
            if (safe.length() >= maxLength) break;
        }
        return safe.toString().trim();
    }

    private void finishPendingFileAction(String result) {
        isAgentWorking = false;
        completeThinkingEntry(pendingFileThinkingEntry, pendingFileThinkingPosition, result);
        pendingFileDialogId = 0;
        pendingFileAccount = -1;
        pendingFileCaption = null;
        pendingFileRecipient = null;
        pendingFileThinkingEntry = null;
        pendingFileThinkingPosition = -1;
        composerFileEntry = null;
        updateComposerState();
    }

    private void startSendFilePicker(String recipient, String caption, long dialogId, ChatEntry entry, int position) {
        android.app.Activity activity = getParentActivity();
        if (activity == null || dialogId == 0) {
            isAgentWorking = false;
            completeThinkingEntry(entry, position, "Не удалось определить чат для отправки файла.");
            return;
        }
        int captionLimit = org.telegram.messenger.MessagesController.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getCaptionMaxLengthLimit();
        if (caption != null && caption.length() > captionLimit) {
            isAgentWorking = false;
            completeThinkingEntry(entry, position, "Подпись длиннее текущего лимита Telegram (" + captionLimit + " символов). Файл не выбран.");
            return;
        }
        pendingFileDialogId = dialogId;
        pendingFileAccount = org.telegram.messenger.UserConfig.selectedAccount;
        pendingFileCaption = caption == null ? "" : caption;
        pendingFileRecipient = getDialogDisplayName(pendingFileAccount, dialogId, recipient);
        pendingFileThinkingEntry = entry;
        pendingFileThinkingPosition = position;
        completeThinkingEntry(entry, position, "Выбери файл для «" + recipient + "». Перед отправкой приложение покажет его имя и подпись для подтверждения.");
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_SEND_FILE);
        } catch (Exception e) {
            FileLog.e(e);
            finishPendingFileAction("Не удалось открыть системный выбор файлов.");
        }
    }

    private void openRecipientPickerForFile() {
        if (!conversationReady || isAgentWorking || !YumiConfig.isAiEnabled() || !YumiConfig.isAiAgentSendMessagesEnabled()) {
            appendAgentNotice("Для отправки файлов включи AI и разрешение отправки сообщений в настройках.");
            return;
        }
        int account = conversationAccount;
        composerFileEntry = new ChatEntry(true, "Выбери чат, куда отправить файл…", true);
        messages.add(composerFileEntry);
        adapter.notifyItemInserted(messages.size() - 1);
        int entryPosition = messages.size() - 1;
        pendingFileAccount = account;
        pendingFileThinkingEntry = composerFileEntry;
        pendingFileThinkingPosition = entryPosition;
        pendingFileCaption = inputEditText == null ? "" : inputEditText.getText().toString().trim();
        pendingFileRecipient = "Telegram чат";
        isAgentWorking = true;
        updateComposerState();
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
        final boolean[] selected = {false};
        DialogsActivity picker = new DialogsActivity(args) {
            @Override
            public void onFragmentDestroy() {
                super.onFragmentDestroy();
                if (!selected[0] && composerFileEntry != null) {
                    AndroidUtilities.runOnUIThread(() -> finishPendingFileAction("Выбор чата отменён. Файл не отправлен."));
                }
            }
        };
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) return false;
            long dialogId = dids.get(0).dialogId;
            if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                finishPendingFileAction("Аккаунт изменился. Файл не отправлен.");
                selected[0] = true;
                return true;
            }
            selected[0] = true;
            pendingFileDialogId = dialogId;
            pendingFileAccount = account;
            pendingFileRecipient = getDialogDisplayName(account, dialogId, "Telegram чат");
            fragment.finishFragment();
            AndroidUtilities.runOnUIThread(() -> launchComposerFilePicker(account));
            return true;
        });
        presentFragment(picker);
    }

    private void launchComposerFilePicker(int account) {
        if (account != org.telegram.messenger.UserConfig.selectedAccount || getParentActivity() == null) {
            finishPendingFileAction("Аккаунт изменился. Файл не отправлен.");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_SEND_FILE);
        } catch (Exception e) {
            FileLog.e(e);
            finishPendingFileAction("Не удалось открыть системный выбор файлов.");
        }
    }

    private String getDialogDisplayName(int account, long dialogId, String fallback) {
        try {
            if (dialogId > 0) {
                TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(account).getUser(dialogId);
                if (user != null) return org.telegram.messenger.UserObject.getUserName(user);
            } else if (dialogId < 0) {
                TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(account).getChat(-dialogId);
                if (chat != null && !TextUtils.isEmpty(chat.title)) return chat.title;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return TextUtils.isEmpty(fallback) ? "Telegram чат" : fallback;
    }

    private void chooseAvatarImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Выберите новое фото профиля"), REQUEST_AVATAR_IMAGE);
        } catch (Exception e) {
            FileLog.e(e);
            pendingAvatarAccount = -1;
            if (getParentActivity() != null) appendAgentNotice("Не удалось открыть выбор фото профиля.");
        }
    }

    private void uploadAvatarFromUri(Uri uri) {
        android.app.Activity activity = getParentActivity();
        if (activity == null || uri == null) {
            pendingAvatarAccount = -1;
            return;
        }
        final int avatarAccount = pendingAvatarAccount;
        ContentResolver contentResolver = activity.getContentResolver();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            File avatarFile = new File(ApplicationLoader.applicationContext.getCacheDir(), "yumi_avatar_" + System.currentTimeMillis() + ".jpg");
            try (InputStream input = contentResolver.openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(avatarFile)) {
                if (input == null) throw new IllegalStateException("Не удалось открыть изображение");
                byte[] buffer = new byte[8192];
                int read;
                long totalBytes = 0;
                while ((read = input.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > 20L * 1024L * 1024L) throw new IllegalStateException("Изображение больше 20 МБ");
                    output.write(buffer, 0, read);
                }
                org.telegram.messenger.MediaController.PhotoEntry entry = new org.telegram.messenger.MediaController.PhotoEntry(0, 0, 0, avatarFile.getAbsolutePath(), 0, false, 0, 0, avatarFile.length());
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() == null || avatarAccount != org.telegram.messenger.UserConfig.selectedAccount) {
                        avatarFile.delete();
                        pendingAvatarAccount = -1;
                        return;
                    }
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Установить это фото профиля?",
                            "Фото будет загружено в Telegram как аватар аккаунта.", "Отмена", "Установить", false, () -> {
                                avatarFile.delete();
                                pendingAvatarAccount = -1;
                            }, () -> {
                                if (avatarAccount != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                    avatarFile.delete();
                                    pendingAvatarAccount = -1;
                                    appendAgentNotice("Аккаунт изменился или AI отключён. Фото профиля не изменено.");
                                    return;
                                }
                                org.telegram.messenger.utils.PhotoUtilities.setImageAsAvatar(entry, YumiAiCopilotActivity.this, () -> {
                                    avatarFile.delete();
                                    pendingAvatarAccount = -1;
                                    appendAgentNotice("Фото профиля обновлено.");
                                });
                            });
                });
            } catch (Exception e) {
                FileLog.e(e);
                avatarFile.delete();
                AndroidUtilities.runOnUIThread(() -> {
                    pendingAvatarAccount = -1;
                    if (getParentActivity() != null) appendAgentNotice("Не удалось открыть фото: " + e.getLocalizedMessage());
                });
            }
        });
    }

    private String getConversationTitle(YumiAgentConversationStore.Conversation conversation) {
        if (conversation == null || TextUtils.equals(conversation.title, YumiAgentConversationStore.DEFAULT_TITLE)) {
            return YumiLocale.getString(R.string.YumiAiNewChat);
        }
        return conversation.title;
    }

    private void sendMessage() {
        if (isAgentWorking || !conversationReady) return;
        if (!YumiConfig.isAiEnabled()) {
            appendAgentNotice("ИИ отключён в настройках Yumigram.");
            return;
        }

        String text = inputEditText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        inputEditText.setText("");

        ChatEntry userEntry = new ChatEntry(false, text);
        messages.add(userEntry);
        persistEntry(userEntry);
        int userPos = messages.size() - 1;
        adapter.notifyItemInserted(userPos);
        updateWelcomeVisibility();

        // Add thinking placeholder
        ChatEntry thinkingEntry = new ChatEntry(true, "✨ Yumi Agent аналізує...", true);
        messages.add(thinkingEntry);
        int thinkingPos = messages.size() - 1;
        adapter.notifyItemInserted(thinkingPos);
        recyclerView.scrollToPosition(thinkingPos);

        isAgentWorking = true;
        updateComposerState();

        YumiAgentEngine.executePromptWithContext(text, buildConversationContext(), new YumiAgentEngine.AgentCallback() {
            @Override
            public void onResponse(String agentResponse) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, agentResponse);
                });
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "⚠️ Помилка агента: " + error);
                });
            }

            @Override
            public void onSendMessageDraft(String recipient, String draftText) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    if (!completeThinkingEntry(thinkingEntry, thinkingPos, "Черновик сообщения · " + recipient + "\n\n" + draftText)) return;
                    if (getParentActivity() != null) {
                        final int targetAccount = org.telegram.messenger.UserConfig.selectedAccount;
                        TLRPC.Dialog target = YumiAgentEngine.findDialog(recipient);
                        String confirmedRecipient = target != null
                                ? getDialogDisplayName(targetAccount, target.id, recipient)
                                : recipient;
                        org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Отправить сообщение?",
                                "Кому: " + flattenConfirmationText(confirmedRecipient) + "\n\nТекст: " + flattenConfirmationText(draftText, 5000),
                                "Оставить черновик", "Отправить", false,
                                () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Отправка отменена. Черновик остался в переписке."), () -> {
                                    if (targetAccount != org.telegram.messenger.UserConfig.selectedAccount) {
                                        completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Сообщение не отправлено.");
                                        return;
                                    }
                                    YumiAgentEngine.confirmSendMessage(recipient, draftText, new YumiAgentEngine.AgentCallback() {
                                        @Override
                                        public void onResponse(String result) {
                                            AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result));
                                        }

                                        @Override
                                        public void onError(String error) {
                                            AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, "Не отправлено: " + error));
                                        }
                                    });
                                });
                    }
                });
            }

            @Override
            public void onSendFileDraft(String recipient, String caption) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!YumiConfig.isAiEnabled() || !YumiConfig.isAiAgentSendMessagesEnabled()) {
                        isAgentWorking = false;
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Отправка файлов выключена в настройках AI.");
                        return;
                    }
                    String normalizedRecipient = recipient == null ? "" : recipient.trim();
                    if (normalizedRecipient.isEmpty()) {
                        isAgentWorking = false;
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Укажи чат, куда отправить файл.");
                        return;
                    }
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(normalizedRecipient);
                    if (target != null) {
                        startSendFilePicker(normalizedRecipient, caption, target.id, thinkingEntry, thinkingPos);
                        return;
                    }
                    if (normalizedRecipient.startsWith("@") && normalizedRecipient.length() > 1) {
                        int account = org.telegram.messenger.UserConfig.selectedAccount;
                        String username = normalizedRecipient.substring(1);
                        org.telegram.messenger.MessagesController.getInstance(account).getUserNameResolver().resolve(username, resolvedDialogId -> {
                            if (resolvedDialogId == null || resolvedDialogId == 0 || resolvedDialogId == Long.MAX_VALUE) {
                                isAgentWorking = false;
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось найти @" + username + ". Файл не отправлен.");
                                return;
                            }
                            if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                isAgentWorking = false;
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Файл не отправлен.");
                                return;
                            }
                            startSendFilePicker(normalizedRecipient, caption, resolvedDialogId, thinkingEntry, thinkingPos);
                        });
                        return;
                    }
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось найти чат «" + normalizedRecipient + "». Файл не отправлен.");
                });
            }

            @Override
            public void onSetUserBlocked(String user, boolean blocked) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    String normalizedUser = user == null ? "" : user.trim();
                    if (normalizedUser.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Укажи пользователя.");
                        return;
                    }
                    int targetAccount = org.telegram.messenger.UserConfig.selectedAccount;
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(normalizedUser);
                    String displayName = target != null && target.id > 0
                            ? getDialogDisplayName(targetAccount, target.id, normalizedUser)
                            : normalizedUser;
                    if (getParentActivity() == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось открыть подтверждение.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения: " + (blocked ? "заблокировать " : "разблокировать ") + displayName + ".");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(),
                            blocked ? "Заблокировать пользователя?" : "Разблокировать пользователя?",
                            flattenConfirmationText(displayName), "Отмена", blocked ? "Заблокировать" : "Разблокировать", true,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Изменение блокировки отменено."), () -> {
                                if (targetAccount != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Ничего не изменено.");
                                    return;
                                }
                                YumiAgentEngine.setUserBlocked(normalizedUser, blocked, new YumiAgentEngine.AgentCallback() {
                                    @Override public void onResponse(String result) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result));
                                    }
                                    @Override public void onError(String error) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось изменить блокировку: " + error));
                                    }
                                });
                            });
                });
            }

            @Override
            public void onOpenChat(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog dialog = YumiAgentEngine.findDialog(chatName);
                    if (dialog == null) {
                        String normalizedName = chatName == null ? "" : chatName.trim();
                        if (normalizedName.isEmpty()) {
                            completeThinkingEntry(thinkingEntry, thinkingPos, "Уточни, какой чат открыть.");
                            return;
                        }
                        if (normalizedName.startsWith("@") && normalizedName.length() > 1) {
                            int account = org.telegram.messenger.UserConfig.selectedAccount;
                            String username = normalizedName.substring(1);
                            org.telegram.messenger.MessagesController.getInstance(account).getUserNameResolver().resolve(username, resolvedDialogId -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Чат не открыт.");
                                    return;
                                }
                                if (resolvedDialogId == null || resolvedDialogId == 0 || resolvedDialogId == Long.MAX_VALUE) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось найти @" + username + ". Проверь имя пользователя.");
                                    return;
                                }
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю @" + username + ".");
                                Bundle args = new Bundle();
                                if (resolvedDialogId > 0) args.putLong("user_id", resolvedDialogId);
                                else args.putLong("chat_id", -resolvedDialogId);
                                presentFragment(new ChatActivity(args));
                            });
                            return;
                        }
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю поиск чатов по запросу «" + chatName + "».");
                        DialogsActivity dialogsActivity = new DialogsActivity(new Bundle());
                        dialogsActivity.setInitialSearchString(normalizedName);
                        presentFragment(dialogsActivity);
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю чат «" + chatName + "».");
                    Bundle args = new Bundle();
                    if (dialog.id > 0) args.putLong("user_id", dialog.id);
                    else args.putLong("chat_id", -dialog.id);
                    presentFragment(new ChatActivity(args));
                });
            }

            @Override
            public void onOpenProfile(String userName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    String normalizedName = userName == null ? "" : userName.trim();
                    if (normalizedName.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Уточни, чей профиль открыть.");
                        return;
                    }
                    TLRPC.Dialog dialog = YumiAgentEngine.findDialog(normalizedName);
                    if (dialog != null) {
                        openProfile(dialog.id, normalizedName, thinkingEntry, thinkingPos);
                        return;
                    }
                    if (normalizedName.startsWith("@") && normalizedName.length() > 1) {
                        int account = org.telegram.messenger.UserConfig.selectedAccount;
                        String username = normalizedName.substring(1);
                        org.telegram.messenger.MessagesController.getInstance(account).getUserNameResolver().resolve(username, resolvedDialogId -> {
                            if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Профиль не открыт.");
                                return;
                            }
                            if (resolvedDialogId == null || resolvedDialogId == 0 || resolvedDialogId == Long.MAX_VALUE) {
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось найти @" + username + ".");
                                return;
                            }
                            openProfile(resolvedDialogId, normalizedName, thinkingEntry, thinkingPos);
                        });
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю поиск по запросу «" + normalizedName + "».");
                    DialogsActivity dialogsActivity = new DialogsActivity(new Bundle());
                    dialogsActivity.setInitialSearchString(normalizedName);
                    presentFragment(dialogsActivity);
                });
            }

            @Override
            public void onOpenSettings(String section) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю настройки: " + section + ".");
                    String normalized = section == null ? "" : section.toLowerCase(Locale.ROOT);
                    if (normalized.contains("ai") || normalized.contains("ии") || ((normalized.contains("voice") || normalized.contains("transcription") || normalized.contains("голос") || normalized.contains("транск")) && !normalized.contains("call") && !normalized.contains("звон"))) presentFragment(new YumiAiSettingsActivity());
                    else if (normalized.contains("ghost") || normalized.contains("невид")) presentFragment(new YumiGhostSettingsActivity());
                    else if (normalized.contains("privacy") || normalized.contains("приват")) presentFragment(new PrivacySettingsActivity());
                    else if (normalized.contains("notification") || normalized.contains("уведом")) presentFragment(new NotificationsSettingsActivity());
                    else if (normalized.contains("data") || normalized.contains("storage") || normalized.contains("данн") || normalized.contains("памят")) presentFragment(new DataSettingsActivity());
                    else if (normalized.contains("device") || normalized.contains("session") || normalized.contains("устрой") || normalized.contains("сеанс") || normalized.contains("активн")) presentFragment(new SessionsActivity(SessionsActivity.TYPE_DEVICES));
                    else if (normalized.contains("folder") || normalized.contains("filter") || normalized.contains("папк") || normalized.contains("фильтр")) presentFragment(new FiltersSetupActivity());
                    else if (normalized.contains("archive") || normalized.contains("архив")) presentFragment(new ArchiveSettingsActivity());
                    else if (normalized.contains("language") || normalized.contains("язык")) presentFragment(new LanguageSelectActivity());
                    else if (normalized.contains("theme") || normalized.contains("тема") || normalized.contains("темы") || normalized.contains("темн")) presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                    else if (normalized.contains("interface") || normalized.contains("appearance") || normalized.contains("интерфейс") || normalized.contains("внешн") || normalized.contains("оформлен")) presentFragment(new YumiInterfaceSettingsActivity());
                    else if (normalized.contains("lite") || normalized.contains("performance") || normalized.contains("анимац") || normalized.contains("производитель")) presentFragment(new LiteModeSettingsActivity());
                    else if (normalized.contains("sticker") || normalized.contains("emoji") || normalized.contains("стикер") || normalized.contains("эмодзи")) presentFragment(new StickersActivity(org.telegram.messenger.MediaDataController.TYPE_IMAGE, null));
                    else if (normalized.contains("proxy") || normalized.contains("прокс")) presentFragment(new ProxyListActivity());
                    else if (normalized.contains("call") || normalized.contains("звон")) presentFragment(new CallLogActivity());
                    else if (normalized.contains("profile") || normalized.contains("account") || normalized.contains("username") || normalized.contains("avatar") || normalized.contains("nickname") || normalized.contains("профил") || normalized.contains("аккаунт") || normalized.contains("обліков") || normalized.contains("аватар") || normalized.contains("нік") || normalized.contains("ник") || normalized.contains("біо") || normalized.contains("bio")) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", org.telegram.messenger.UserConfig.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getClientUserId());
                        presentFragment(new ProfileActivity(args));
                    } else if (normalized.contains("chat") || normalized.contains("dialog") || normalized.contains("чат") || normalized.contains("діалог")) {
                        presentFragment(new DialogsActivity(new Bundle()));
                    } else presentFragment(new SettingsActivity());
                });
            }

            @Override
            public void onOpenContacts() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю контакты.");
                    presentFragment(new ContactsActivity(new Bundle()));
                });
            }

            @Override
            public void onCreateContact() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    if (getParentActivity() == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось открыть форму нового контакта.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю форму нового контакта.");
                    new NewContactBottomSheet(YumiAiCopilotActivity.this, getParentActivity()).show();
                });
            }

            @Override
            public void onOpenCallLog() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю журнал звонков.");
                    presentFragment(new CallLogActivity());
                });
            }

            @Override
            public void onOpenSavedMessages() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю Избранное.");
                    Bundle args = new Bundle();
                    args.putLong("user_id", org.telegram.messenger.UserConfig.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getClientUserId());
                    presentFragment(new ChatActivity(args));
                });
            }

            @Override
            public void onOpenArchive() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю архив чатов.");
                    Bundle args = new Bundle();
                    args.putInt("folderId", 1);
                    presentFragment(new DialogsActivity(args));
                });
            }

            @Override
            public void onCreateGroup() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    if (org.telegram.messenger.MessagesController.getInstance(account).isFrozen()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Telegram временно ограничил создание групп для этого аккаунта.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю создание группы. Добавь участников и заверши создание в Telegram.");
                    presentFragment(new GroupCreateActivity(new Bundle()));
                });
            }

            @Override
            public void onCreateChannel() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    if (org.telegram.messenger.MessagesController.getInstance(account).isFrozen()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Telegram временно ограничил создание каналов для этого аккаунта.");
                        return;
                    }
                    Bundle args = new Bundle();
                    args.putInt("step", 0);
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю создание канала. Укажи название и заверши создание в Telegram.");
                    presentFragment(new ChannelCreateActivity(args));
                });
            }

            @Override
            public void onSuggestReply(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (TextUtils.isEmpty(chatName) || TextUtils.isEmpty(chatName.trim())) {
                        isAgentWorking = false;
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Укажи чат для подготовки ответа.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Читаю последнее входящее сообщение и готовлю варианты ответа…");
                    YumiAgentEngine.generateSmartReplySuggestions(chatName, new YumiAgentEngine.AgentCallback() {
                        @Override public void onResponse(String result) {
                            AndroidUtilities.runOnUIThread(() -> {
                                isAgentWorking = false;
                                completeThinkingEntry(thinkingEntry, thinkingPos, result);
                            });
                        }
                        @Override public void onError(String error) {
                            AndroidUtilities.runOnUIThread(() -> {
                                isAgentWorking = false;
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось подготовить варианты ответа: " + error);
                            });
                        }
                    });
                });
            }

            @Override
            public void onSearchWeb(String query) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    String trimmed = query == null ? "" : query.trim();
                    if (trimmed.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Укажи, что найти в интернете.");
                        return;
                    }
                    String url = "https://www.google.com/search?q=" + Uri.encode(trimmed);
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения поиска в интернете: «" + trimmed + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Искать в интернете?",
                            flattenConfirmationText(trimmed), "Отмена", "Открыть поиск", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Поиск отменён."), () -> {
                                Browser.openUrl(getParentActivity(), Uri.parse(url));
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Открыла результаты поиска в браузере.");
                            });
                });
            }

            @Override
            public void onOpenUrl(String rawUrl) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    String value = rawUrl == null ? "" : rawUrl.trim();
                    Uri uri = Uri.parse(value);
                    String scheme = uri.getScheme();
                    if (TextUtils.isEmpty(value) || TextUtils.isEmpty(uri.getHost()) ||
                            !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось открыть ссылку: поддерживаются только полные HTTP/HTTPS-адреса.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения открытия ссылки.");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Открыть ссылку?",
                            flattenConfirmationText(uri.getHost()) + "\n" + flattenConfirmationText(uri.toString()), "Отмена", "Открыть", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Открытие ссылки отменено."), () -> {
                                Browser.openUrl(getParentActivity(), uri);
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Открыла ссылку в браузере.");
                            });
                });
            }

            @Override
            public void onStartCall(String userQuery, boolean video) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    String targetName = userQuery == null ? "" : userQuery.trim();
                    if (targetName.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Укажи, кому позвонить.");
                        return;
                    }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    TLRPC.Dialog dialog = YumiAgentEngine.findDialog(targetName);
                    Runnable confirmCall = () -> {
                        if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                            completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Звонок не начат.");
                            return;
                        }
                        TLRPC.User user = dialog == null ? null : org.telegram.messenger.MessagesController.getInstance(account).getUser(dialog.id);
                        if (user == null && targetName.startsWith("@")) {
                            org.telegram.messenger.MessagesController.getInstance(account).getUserNameResolver().resolve(targetName.substring(1), resolvedDialogId -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Звонок не начат.");
                                    return;
                                }
                                if (resolvedDialogId == null || resolvedDialogId <= 0 || resolvedDialogId == Long.MAX_VALUE) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось найти пользователя для звонка.");
                                    return;
                                }
                                startConfirmedCall(account, resolvedDialogId, targetName, video, thinkingEntry, thinkingPos);
                            });
                            return;
                        }
                        if (user == null || user.id == org.telegram.messenger.UserConfig.getInstance(account).getClientUserId()) {
                            completeThinkingEntry(thinkingEntry, thinkingPos, "Звонок доступен только другому Telegram-пользователю.");
                            return;
                        }
                        startConfirmedCall(account, user.id, targetName, video, thinkingEntry, thinkingPos);
                    };
                    String callType = video ? "видеозвонок" : "голосовой звонок";
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения: " + callType + ".");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Позвонить в Telegram?",
                            flattenConfirmationText(targetName) + "\n\nБудет начат " + callType + ".", "Отмена", "Позвонить", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Звонок отменён."), confirmCall);
                });
            }

            private void startConfirmedCall(int account, long userId, String targetName, boolean video, ChatEntry thinkingEntry, int thinkingPos) {
                TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(account).getUser(userId);
                if (user == null) {
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось загрузить пользователя для звонка.");
                    return;
                }
                TLRPC.UserFull userFull = org.telegram.messenger.MessagesController.getInstance(account).getUserFull(userId);
                completeThinkingEntry(thinkingEntry, thinkingPos, "Запускаю звонок для «" + targetName + "».");
                org.telegram.ui.Components.voip.VoIPHelper.startCall(user, video, userFull != null && userFull.video_calls_available,
                        getParentActivity(), userFull, org.telegram.messenger.AccountInstance.getInstance(account));
            }

            @Override
            public void onRenameChat(String chatName, String newTitle) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog dialog = YumiAgentEngine.findDialog(chatName);
                    if (dialog == null || dialog.id == 0 || org.telegram.messenger.DialogObject.isEncryptedDialog(dialog.id) || TextUtils.isEmpty(newTitle)) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось определить чат или новое название.");
                        return;
                    }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    if (dialog.id > 0) {
                        TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(account).getUser(dialog.id);
                        if (user == null || !user.contact) {
                            completeThinkingEntry(thinkingEntry, thinkingPos, "Переименовать можно только сохранённый контакт.");
                            return;
                        }
                    }
                    long dialogId = dialog.id;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения переименования «" + chatName + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(),
                            dialogId > 0 ? "Переименовать контакт?" : "Переименовать группу или канал?",
                            flattenConfirmationText(chatName) + " → " + flattenConfirmationText(newTitle), "Отмена", "Переименовать", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Переименование отменено."), () -> YumiAgentEngine.confirmRenameChat(account, dialogId, newTitle, new YumiAgentEngine.AgentCallback() {
                                @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result)); }
                                @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, error)); }
                            }));
                });
            }

            @Override
            public void onUpdateProfile(String firstName, String lastName, String bio) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    StringBuilder changes = new StringBuilder();
                    if (!TextUtils.isEmpty(firstName)) changes.append("Имя: ").append(flattenConfirmationText(firstName)).append('\n');
                    if (!TextUtils.isEmpty(lastName)) changes.append("Фамилия: ").append(flattenConfirmationText(lastName)).append('\n');
                    if (!TextUtils.isEmpty(bio)) changes.append("О себе: ").append(flattenConfirmationText(bio));
                    if (changes.length() == 0) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Уточни, какое поле профиля изменить.");
                        return;
                    }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения изменения профиля.");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Изменить профиль?",
                            changes.toString(), "Отмена", "Сохранить", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Изменение профиля отменено."), () -> YumiAgentEngine.confirmProfileUpdate(account, firstName, lastName, bio, new YumiAgentEngine.AgentCallback() {
                                @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result)); }
                                @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, error)); }
                            }));
                });
            }

            @Override
            public void onSearchMessages(String query, String chat) {
                AndroidUtilities.runOnUIThread(() -> YumiAgentEngine.searchMessages(query, chat, this));
            }

            @Override
            public void onReactToMessage(String query, String chat, String reaction) {
                AndroidUtilities.runOnUIThread(() -> YumiAgentEngine.searchMessagesForReaction(query, chat, reaction, this));
            }

            @Override
            public void onForwardMessage(String query, String chat, String recipient) {
                AndroidUtilities.runOnUIThread(() -> YumiAgentEngine.searchMessagesForForward(query, chat, recipient, this));
            }

            @Override
            public void onReplyToMessage(String query, String chat, String text) {
                AndroidUtilities.runOnUIThread(() -> YumiAgentEngine.searchMessagesForReply(query, chat, text, this));
            }

            @Override
            public void onTogglePinMessage(String query, String chat) {
                AndroidUtilities.runOnUIThread(() -> YumiAgentEngine.searchMessagesForPin(query, chat, this));
            }

            @Override
            public void onSearchResults(int account, String chat, ArrayList<TLRPC.Message> results) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Результаты поиска закрыты.");
                        return;
                    }
                    if (getParentActivity() == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Экран уже закрыт. Результаты поиска недоступны.");
                        return;
                    }
                    if (results == null || results.isEmpty()) { completeThinkingEntry(thinkingEntry, thinkingPos, "Ничего не найдено."); return; }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Найдено сообщений: " + results.size() + ".");
                    CharSequence[] labels = new CharSequence[Math.min(20, results.size())];
                    for (int i = 0; i < labels.length; i++) {
                        TLRPC.Message message = results.get(i);
                        String text = TextUtils.isEmpty(message.message) ? "Сообщение" : flattenConfirmationText(message.message);
                        if (text.length() > 80) text = text.substring(0, 77) + "…";
                        labels[i] = flattenConfirmationText(getMessageChatTitle(message, account) + ": " + text);
                    }
                    new AlertDialog.Builder(getParentActivity()).setTitle("Результаты поиска")
                            .setItems(labels, (d, which) -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Результаты поиска закрыты.");
                                    return;
                                }
                                if (which < 0 || which >= results.size()) return;
                                TLRPC.Message message = results.get(which);
                                long dialogId = message.dialog_id;
                                if (dialogId == 0 && message.peer_id != null) dialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
                                if (dialogId == 0 && !TextUtils.isEmpty(chat)) {
                                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chat);
                                    if (target != null) dialogId = target.id;
                                }
                                Bundle args = new Bundle();
                                if (dialogId > 0) args.putLong("user_id", dialogId);
                                else if (dialogId < 0) args.putLong("chat_id", -dialogId);
                                args.putInt("message_id", message.id);
                                presentFragment(new ChatActivity(args));
                            }).setNegativeButton("Закрыть", null).show();
                });
            }

            @Override
            public void onReactionMessageResults(int account, String chat, String reaction, ArrayList<TLRPC.Message> results) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Реакция не добавлена.");
                        return;
                    }
                    if (getParentActivity() == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Экран уже закрыт. Реакция не добавлена.");
                        return;
                    }
                    if (results == null || results.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Сообщения по запросу не найдены.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Нашла сообщения: " + results.size() + ". Выбери сообщение для реакции " + reaction + ".");
                    CharSequence[] labels = new CharSequence[Math.min(20, results.size())];
                    for (int i = 0; i < labels.length; i++) {
                        TLRPC.Message message = results.get(i);
                        String body = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
                        if (body.length() > 90) body = body.substring(0, 87) + "…";
                        labels[i] = getMessageChatTitle(message) + " · " + body;
                    }
                    new AlertDialog.Builder(getParentActivity()).setTitle("Выбери сообщение")
                            .setItems(labels, (dialog, which) -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Результаты поиска закрыты.");
                                    return;
                                }
                                if (which < 0 || which >= labels.length) return;
                                TLRPC.Message message = results.get(which);
                                long dialogId = message.dialog_id;
                                if (dialogId == 0 && message.peer_id != null) dialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
                                if (dialogId == 0 && !TextUtils.isEmpty(chat)) {
                                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chat);
                                    if (target != null) dialogId = target.id;
                                }
                                if (dialogId == 0) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось определить чат этого сообщения.");
                                    return;
                                }
                                long targetDialogId = dialogId;
                                String messageText = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : message.message;
                                String preview = flattenConfirmationText(messageText);
                                if (preview.length() > 300) preview = preview.substring(0, 297) + "…";
                                String recipient = getDialogDisplayName(account, targetDialogId, chat);
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения реакции " + reaction + ".");
                                org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Добавить реакцию?",
                                        "Чат: " + flattenConfirmationText(recipient) + "\nСообщение: " + preview + "\n\nРеакция: " + reaction,
                                        "Отмена", "Добавить", false,
                                        () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Добавление реакции отменено."), () -> {
                                            if (account != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                                completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился или AI отключён. Реакция не добавлена.");
                                                return;
                                            }
                                            YumiAgentEngine.confirmReactToMessage(account, targetDialogId, message, reaction, new YumiAgentEngine.AgentCallback() {
                                                @Override public void onResponse(String result) {
                                                    AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result));
                                                }
                                                @Override public void onError(String error) {
                                                    AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось добавить реакцию: " + error));
                                                }
                                            });
                                        });
                            }).setNegativeButton("Закрыть", null).show();
                });
            }

            @Override
            public void onForwardMessageResults(int account, String sourceChat, String recipient, ArrayList<TLRPC.Message> results) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Сообщение не переслано.");
                        return;
                    }
                    if (getParentActivity() == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Экран уже закрыт. Сообщение не переслано.");
                        return;
                    }
                    if (results == null || results.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Сообщения по запросу не найдены.");
                        return;
                    }
                    TLRPC.Dialog localTarget = YumiAgentEngine.findDialog(recipient);
                    if (localTarget != null) {
                        showForwardMessagePicker(account, sourceChat, recipient, getDialogDisplayName(account, localTarget.id, recipient), localTarget.id, results, thinkingEntry, thinkingPos);
                        return;
                    }
                    if (recipient != null && recipient.trim().startsWith("@") && recipient.trim().length() > 1) {
                        String username = recipient.trim().substring(1);
                        org.telegram.messenger.MessagesController.getInstance(account).getUserNameResolver().resolve(username, targetDialogId -> {
                            if (targetDialogId == null || targetDialogId == 0 || targetDialogId == Long.MAX_VALUE) {
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось найти чат @" + username + ". Сообщение не переслано.");
                                return;
                            }
                            if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Сообщение не переслано.");
                                return;
                            }
                            showForwardMessagePicker(account, sourceChat, recipient, getDialogDisplayName(account, targetDialogId, recipient), targetDialogId, results, thinkingEntry, thinkingPos);
                        });
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось однозначно найти чат-получатель. Укажи его точное название или @username.");
                });
            }

            @Override
            public void onReplyMessageResults(int account, String chat, String text, ArrayList<TLRPC.Message> results) {
                AndroidUtilities.runOnUIThread(() -> showReplyMessagePicker(account, chat, text, results, thinkingEntry, thinkingPos));
            }

            @Override
            public void onPinMessageResults(int account, String chat, ArrayList<TLRPC.Message> results) {
                AndroidUtilities.runOnUIThread(() -> showPinMessagePicker(account, chat, results, thinkingEntry, thinkingPos));
            }

            @Override
            public void onDeleteMessage(String query, String chat) {
                AndroidUtilities.runOnUIThread(() -> YumiAgentEngine.searchMessagesForDeletion(query, chat, this));
            }

            @Override
            public void onDeleteMessageResults(int account, String chat, ArrayList<TLRPC.Message> results) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Результаты поиска закрыты.");
                        return;
                    }
                    if (getParentActivity() == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Экран уже закрыт. Результаты поиска недоступны.");
                        return;
                    }
                    if (results == null || results.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Сообщения по запросу не найдены.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Нашла сообщения: " + results.size() + ". Выбери нужное для удаления.");
                    CharSequence[] labels = new CharSequence[Math.min(20, results.size())];
                    for (int i = 0; i < labels.length; i++) {
                        TLRPC.Message message = results.get(i);
                        String text = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
                        if (text.length() > 90) text = text.substring(0, 87) + "…";
                        labels[i] = flattenConfirmationText(getMessageChatTitle(message, account) + " · " + (message.out ? "Ты: " : "") + text);
                    }
                    new AlertDialog.Builder(getParentActivity()).setTitle("Выбери сообщение")
                            .setItems(labels, (dialog, which) -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Результаты поиска закрыты.");
                                    return;
                                }
                                if (which < 0 || which >= labels.length) return;
                                TLRPC.Message message = results.get(which);
                                long dialogId = message.dialog_id;
                                if (dialogId == 0 && message.peer_id != null) dialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
                                if (dialogId == 0 && !TextUtils.isEmpty(chat)) {
                                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chat);
                                    if (target != null) dialogId = target.id;
                                }
                                if (dialogId == 0) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось определить чат этого сообщения.");
                                    return;
                                }
                                long targetDialogId = dialogId;
                                String scope = message.out ? "у всех" : "в твоём аккаунте";
                                String deletePreview = flattenConfirmationText(TextUtils.isEmpty(message.message) ? "Сообщение без текста" : message.message);
                                if (deletePreview.length() > 700) deletePreview = deletePreview.substring(0, 697) + "…";
                                org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Удалить это сообщение?",
                                        deletePreview + "\n\nОно будет удалено " + scope + ".", "Отмена", "Удалить", true,
                                        () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Удаление сообщения отменено."), () -> {
                                            if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                                completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился. Сообщение не удалено.");
                                                return;
                                            }
                                            completeThinkingEntry(thinkingEntry, thinkingPos, "Удаляю сообщение…");
                                            YumiAgentEngine.confirmDeleteMessage(account, targetDialogId, message.id, message.out, new YumiAgentEngine.AgentCallback() {
                                                @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result)); }
                                                @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, error)); }
                                            });
                                        });
                            }).setNegativeButton("Закрыть", null).show();
                });
            }

            @Override
            public void onEditMessage(String query, String chat, String newText) {
                if (TextUtils.isEmpty(newText)) {
                    isAgentWorking = false;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Укажи новый текст сообщения.");
                    return;
                }
                YumiAgentEngine.searchMessages(query, chat, new YumiAgentEngine.AgentCallback() {
                    @Override public void onResponse(String text) { AndroidUtilities.runOnUIThread(() -> { isAgentWorking = false; completeThinkingEntry(thinkingEntry, thinkingPos, text); }); }
                    @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> { isAgentWorking = false; completeThinkingEntry(thinkingEntry, thinkingPos, error); }); }
                    @Override public void onSearchResults(int resultAccount, String resultChat, ArrayList<TLRPC.Message> results) {
                        AndroidUtilities.runOnUIThread(() -> showEditMessagePicker(resultAccount, resultChat, results, newText, thinkingEntry, thinkingPos));
                    }
                });
            }

            @Override
            public void onToggleMute(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chatName);
                    if (target == null) { completeThinkingEntry(thinkingEntry, thinkingPos, "Не нашла чат «" + chatName + "."); return; }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    boolean currentlyMuted = org.telegram.messenger.MessagesController.getInstance(account).isDialogMuted(target.id, 0);
                    String action = currentlyMuted ? "Включить уведомления" : "Отключить уведомления";
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения: " + action.toLowerCase(Locale.ROOT) + " для «" + chatName + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), action + "?",
                            flattenConfirmationText(chatName), "Отмена", action, false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Изменение уведомлений отменено."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился или AI отключён. Уведомления не изменены.");
                                    return;
                                }
                                org.telegram.messenger.NotificationsController.getInstance(account).muteDialog(target.id, 0, !currentlyMuted);
                                completeThinkingEntry(thinkingEntry, thinkingPos, currentlyMuted ? "Уведомления включены." : "Уведомления отключены.");
                            });
                });
            }

            @Override
            public void onSetAvatar() {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    pendingAvatarAccount = org.telegram.messenger.UserConfig.selectedAccount;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Открываю выбор фото профиля.");
                    chooseAvatarImage();
                });
            }

            @Override
            public void onTogglePin(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chatName);
                    if (target == null) { completeThinkingEntry(thinkingEntry, thinkingPos, "Не нашла чат «" + chatName + "."); return; }
                    boolean pin = !target.pinned;
                    String action = pin ? "Закрепить" : "Открепить";
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения: " + action.toLowerCase(Locale.ROOT) + " чат «" + chatName + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), action + " чат?",
                            flattenConfirmationText(chatName), "Отмена", action, false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Изменение закрепления отменено."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился или AI отключён. Закрепление не изменено.");
                                    return;
                                }
                                boolean updated = org.telegram.messenger.MessagesController.getInstance(account)
                                        .pinDialog(target.id, pin, org.telegram.messenger.MessagesController.getInstance(account).getInputPeer(target.id), 0);
                                completeThinkingEntry(thinkingEntry, thinkingPos, updated ? (pin ? "Запрос на закрепление отправлен." : "Запрос на открепление отправлен.") : "Не удалось изменить закрепление.");
                            });
                });
            }

            @Override
            public void onArchiveChat(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chatName);
                    if (target == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не нашла чат «" + chatName + ".");
                        return;
                    }
                    boolean archive = target.folder_id != 1;
                    String action = archive ? "Переместить в архив" : "Вернуть из архива";
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения: " + action.toLowerCase(Locale.ROOT) + " «" + chatName + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), action + "?",
                            flattenConfirmationText(chatName), "Отмена", action, false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Изменение архива отменено."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился или AI отключён. Папка чата не изменена.");
                                    return;
                                }
                                int folderId = archive ? 1 : 0;
                                int result = org.telegram.messenger.MessagesController.getInstance(account)
                                        .addDialogToFolder(target.id, folderId, 0, 0);
                                completeThinkingEntry(thinkingEntry, thinkingPos,
                                        result > 0 ? (archive ? "Запрос на перемещение чата в архив отправлен Telegram." : "Запрос на возврат чата из архива отправлен Telegram.") : "Не удалось изменить папку чата.");
                            });
                });
            }

            @Override
            public void onMarkChatRead(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chatName);
                    if (target == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не нашла чат «" + chatName + ".");
                        return;
                    }
                    if (org.telegram.messenger.DialogObject.isEncryptedDialog(target.id)) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Отметка секретного чата прочитанным через Copilot не поддерживается.");
                        return;
                    }
                    if (org.telegram.messenger.yumigram.YumiGhostController.shouldSuppressRead(target.id)) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Ghost Mode скрывает отметки о прочтении. Отключи скрытие прочтения и повтори запрос.");
                        return;
                    }
                    if (target.top_message <= 0) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "В этом чате нет сообщения для отметки о прочтении.");
                        return;
                    }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения отметки «" + chatName + "» прочитанным.");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Отметить чат прочитанным?",
                            flattenConfirmationText(chatName) + "\n\nTelegram отправит отметку о прочтении.", "Отмена", "Отметить", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Действие отменено."), () -> YumiAgentEngine.confirmMarkChatRead(account, target.id, target.top_message, new YumiAgentEngine.AgentCallback() {
                                @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result)); }
                                @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, error)); }
                            }));
                });
            }

            @Override
            public void onMarkChatUnread(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chatName);
                    if (target == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не нашла чат «" + chatName + ".");
                        return;
                    }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения отметки «" + chatName + "» непрочитанным.");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Отметить чат непрочитанным?",
                            flattenConfirmationText(chatName), "Отмена", "Отметить", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Действие отменено."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился или AI отключён. Чат не изменён.");
                                    return;
                                }
                                org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
                                controller.markDialogAsUnread(target.id, controller.getInputPeer(target.id), 0);
                                completeThinkingEntry(thinkingEntry, thinkingPos, "Запрос на отметку чата непрочитанным отправлен Telegram.");
                            });
                });
            }

            @Override
            public void onSetUsername(String username) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения изменения имени пользователя.");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Изменить имя пользователя?",
                            "@" + flattenConfirmationText(username.replace("@", "")), "Отмена", "Изменить", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Изменение имени пользователя отменено."), () -> YumiAgentEngine.confirmUsername(account, username, new YumiAgentEngine.AgentCallback() {
                                @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result)); }
                                @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, error)); }
                            }));
                });
            }

            @Override
            public void onSetAppSetting(String setting, boolean enabled) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    String label = setting.replace('_', ' ');
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения настройки «" + label + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Изменить настройку?",
                            flattenConfirmationText(label) + " → " + (enabled ? "включено" : "выключено"), "Отмена", "Применить", false,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Изменение настройки отменено."), () -> {
                                if (YumiConfig.setAiManagedToggle(setting, enabled)) completeThinkingEntry(thinkingEntry, thinkingPos, "Настройка «" + label + "» обновлена.");
                                else completeThinkingEntry(thinkingEntry, thinkingPos, "Такой настройки нет в списке доступных.");
                            });
                });
            }

            @Override
            public void onFindChats(String query) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    if (TextUtils.isEmpty(query) || TextUtils.isEmpty(query.trim())) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Уточни имя или название чата для поиска.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ищу чаты по запросу «" + query.trim() + "».");
                    DialogsActivity dialogsActivity = new DialogsActivity(new Bundle());
                    dialogsActivity.setInitialSearchString(query.trim());
                    presentFragment(dialogsActivity);
                });
            }

            @Override
            public void onDeleteChat(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(chatName);
                    if (target == null) { completeThinkingEntry(thinkingEntry, thinkingPos, "Не нашла чат «" + chatName + "."); return; }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    String effect = target.id < 0
                            ? "Yumigram выйдет из группы или канала и уберёт его из списка чатов."
                            : "История личного чата будет удалена с этого аккаунта. Это действие нельзя отменить.";
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения удаления чата «" + chatName + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Удалить переписку?",
                            "«" + flattenConfirmationText(chatName) + "»\n\n" + effect, "Отмена", "Удалить", true,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Удаление чата отменено."), () -> {
                                YumiAgentEngine.confirmDeleteDialog(account, target.id, new YumiAgentEngine.AgentCallback() {
                                    @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result)); }
                                    @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, error)); }
                                });
                            });
                });
            }

            @Override
            public void onClearChatHistory(String chatName) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    String normalizedChat = chatName == null ? "" : chatName.trim();
                    if (normalizedChat.isEmpty()) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Укажи чат, историю которого очистить.");
                        return;
                    }
                    TLRPC.Dialog target = YumiAgentEngine.findDialog(normalizedChat);
                    if (target == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не нашла чат «" + flattenConfirmationText(normalizedChat) + "».");
                        return;
                    }
                    int account = org.telegram.messenger.UserConfig.selectedAccount;
                    if (org.telegram.messenger.DialogObject.isEncryptedDialog(target.id)) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Очистка истории секретного чата через Copilot не поддерживается.");
                        return;
                    }
                    if (getParentActivity() == null) {
                        completeThinkingEntry(thinkingEntry, thinkingPos, "Не удалось открыть подтверждение очистки истории.");
                        return;
                    }
                    completeThinkingEntry(thinkingEntry, thinkingPos, "Ожидаю подтверждения очистки истории чата «" + flattenConfirmationText(normalizedChat) + "».");
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Очистить историю чата?",
                            "История сообщений будет очищена в этом аккаунте. Сам чат останется в списке.", "Отмена", "Очистить историю", true,
                            () -> completeThinkingEntry(thinkingEntry, thinkingPos, "Очистка истории отменена."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                    completeThinkingEntry(thinkingEntry, thinkingPos, "Аккаунт изменился или AI отключён. История не очищена.");
                                    return;
                                }
                                YumiAgentEngine.confirmClearChatHistory(account, target.id, new YumiAgentEngine.AgentCallback() {
                                    @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, result)); }
                                    @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(thinkingEntry, thinkingPos, error)); }
                                });
                            });
                });
            }
        });
    }

    private String buildConversationContext() {
        StringBuilder context = new StringBuilder();
        // The last entry is the new user prompt, which is sent separately to the model.
        int end = Math.max(0, messages.size() - 1);
        int start = Math.max(0, end - 10);
        for (int i = start; i < end; i++) {
            ChatEntry entry = messages.get(i);
            if (entry.isThinking || TextUtils.isEmpty(entry.text)) continue;
            String text = entry.text.length() > 700 ? entry.text.substring(0, 697) + "…" : entry.text;
            context.append(entry.isAi ? "Yumi: " : "User: ").append(text).append('\n');
            if (context.length() >= 3600) break;
        }
        return context.toString();
    }

    private String getMessageChatTitle(TLRPC.Message message) {
        return getMessageChatTitle(message, org.telegram.messenger.UserConfig.selectedAccount);
    }

    private String getMessageChatTitle(TLRPC.Message message, int account) {
        if (message == null) return "Чат";
        long dialogId = message.dialog_id;
        if (dialogId == 0 && message.peer_id != null) dialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
        if (dialogId > 0) {
            TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(account).getUser(dialogId);
            return user == null ? "Личный чат" : org.telegram.messenger.UserObject.getUserName(user);
        }
        if (dialogId < 0) {
            TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(account).getChat(-dialogId);
            return chat == null ? "Группа или канал" : chat.title;
        }
        return "Чат";
    }

    private void openProfile(long dialogId, String displayName, ChatEntry entry, int position) {
        Bundle args = new Bundle();
        if (dialogId > 0) args.putLong("user_id", dialogId);
        else if (dialogId < 0) args.putLong("chat_id", -dialogId);
        else {
            completeThinkingEntry(entry, position, "Не удалось определить профиль.");
            return;
        }
        completeThinkingEntry(entry, position, "Открываю профиль «" + displayName + "».");
        presentFragment(new ProfileActivity(args));
    }

    private void showForwardMessagePicker(int account, String sourceChat, String recipient, String recipientDisplayName, long targetDialogId, ArrayList<TLRPC.Message> results, ChatEntry entry, int position) {
        if (getParentActivity() == null) {
            completeThinkingEntry(entry, position, "Экран уже закрыт. Сообщение не переслано.");
            return;
        }
        if (results == null || results.isEmpty()) {
            completeThinkingEntry(entry, position, "Сообщения по запросу не найдены.");
            return;
        }
        completeThinkingEntry(entry, position, "Нашла сообщения: " + results.size() + ". Выбери, что переслать в «" + recipientDisplayName + "».");
        CharSequence[] labels = new CharSequence[Math.min(20, results.size())];
        for (int i = 0; i < labels.length; i++) {
            TLRPC.Message message = results.get(i);
            String body = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
            if (body.length() > 90) body = body.substring(0, 87) + "…";
            labels[i] = getMessageChatTitle(message) + " · " + body;
        }
        new AlertDialog.Builder(getParentActivity()).setTitle("Выбери сообщение")
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= labels.length) return;
                    TLRPC.Message message = results.get(which);
                    long resolvedSourceDialogId = message.dialog_id;
                    if (resolvedSourceDialogId == 0 && message.peer_id != null) resolvedSourceDialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
                    if (resolvedSourceDialogId == 0 && !TextUtils.isEmpty(sourceChat)) {
                        TLRPC.Dialog source = YumiAgentEngine.findDialog(sourceChat);
                        if (source != null) resolvedSourceDialogId = source.id;
                    }
                    if (resolvedSourceDialogId == 0) {
                        completeThinkingEntry(entry, position, "Не удалось определить исходный чат сообщения.");
                        return;
                    }
                    final long sourceDialogId = resolvedSourceDialogId;
                    if (org.telegram.messenger.DialogObject.isEncryptedDialog(sourceDialogId) || org.telegram.messenger.DialogObject.isEncryptedDialog(targetDialogId)) {
                        completeThinkingEntry(entry, position, "Пересылка из секретных чатов или в них не поддерживается.");
                        return;
                    }
                    String body = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
                    if (body.length() > 500) body = body.substring(0, 497) + "…";
                    String sourceName = getDialogDisplayName(account, sourceDialogId, sourceChat);
                    String targetName = flattenConfirmationText(recipientDisplayName);
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Переслать это сообщение?",
                            "Откуда: " + flattenConfirmationText(sourceName) + "\nКому: " + targetName + "\n\n" + body,
                            "Отмена", "Переслать", false,
                            () -> completeThinkingEntry(entry, position, "Пересылка отменена."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(entry, position, "Аккаунт изменился. Сообщение не переслано.");
                                    return;
                                }
                                if (!YumiConfig.isAiEnabled() || !YumiConfig.isAiAgentSendMessagesEnabled()) {
                                    completeThinkingEntry(entry, position, "Пересылка сообщений выключена в настройках AI.");
                                    return;
                                }
                                YumiAgentEngine.forwardMessage(account, sourceDialogId, message, targetDialogId, new YumiAgentEngine.AgentCallback() {
                                    @Override public void onResponse(String result) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, result));
                                    }
                                    @Override public void onError(String error) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, "Не удалось переслать сообщение: " + error));
                                    }
                                });
                            });
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showPinMessagePicker(int account, String chat, ArrayList<TLRPC.Message> results, ChatEntry entry, int position) {
        isAgentWorking = false;
        if (account != org.telegram.messenger.UserConfig.selectedAccount) {
            completeThinkingEntry(entry, position, "Аккаунт изменился. Результаты поиска закрыты.");
            return;
        }
        if (getParentActivity() == null) {
            completeThinkingEntry(entry, position, "Экран уже закрыт. Сообщение не закреплено.");
            return;
        }
        if (results == null || results.isEmpty()) {
            completeThinkingEntry(entry, position, "Сообщения по запросу не найдены.");
            return;
        }
        completeThinkingEntry(entry, position, "Нашла сообщения: " + results.size() + ". Выбери, какое закрепить или открепить.");
        CharSequence[] labels = new CharSequence[Math.min(20, results.size())];
        for (int i = 0; i < labels.length; i++) {
            TLRPC.Message message = results.get(i);
            String body = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
            if (body.length() > 90) body = body.substring(0, 87) + "…";
            labels[i] = flattenConfirmationText(getMessageChatTitle(message, account) + (message.pinned ? " · Закреплено · " : " · ") + body);
        }
        new AlertDialog.Builder(getParentActivity()).setTitle("Выбери сообщение")
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= labels.length) return;
                    if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                        completeThinkingEntry(entry, position, "Аккаунт изменился. Сообщение не изменено.");
                        return;
                    }
                    TLRPC.Message message = results.get(which);
                    long dialogId = message.dialog_id;
                    if (dialogId == 0 && message.peer_id != null) dialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
                    if (dialogId == 0 && !TextUtils.isEmpty(chat)) {
                        TLRPC.Dialog target = YumiAgentEngine.findDialog(chat);
                        if (target != null) dialogId = target.id;
                    }
                    if (dialogId == 0 || org.telegram.messenger.DialogObject.isEncryptedDialog(dialogId)) {
                        completeThinkingEntry(entry, position, "Не удалось определить обычный чат для сообщения.");
                        return;
                    }
                    final long targetDialogId = dialogId;
                    boolean unpin = message.pinned;
                    String body = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
                    if (body.length() > 500) body = body.substring(0, 497) + "…";
                    String action = unpin ? "открепить" : "закрепить";
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Подтвердить действие",
                            "Чат: " + flattenConfirmationText(getDialogDisplayName(account, targetDialogId, chat)) +
                                    "\nДействие: " + action + " сообщение\n\n" + body,
                            "Отмена", unpin ? "Открепить" : "Закрепить", false,
                            () -> completeThinkingEntry(entry, position, "Изменение закрепления отменено."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount || !YumiConfig.isAiEnabled()) {
                                    completeThinkingEntry(entry, position, "Аккаунт или настройки AI изменились. Действие не выполнено.");
                                    return;
                                }
                                completeThinkingEntry(entry, position, unpin ? "Открепляю сообщение…" : "Закрепляю сообщение…");
                                YumiAgentEngine.confirmPinMessage(account, targetDialogId, message, unpin, new YumiAgentEngine.AgentCallback() {
                                    @Override public void onResponse(String result) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, result));
                                    }
                                    @Override public void onError(String error) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, error));
                                    }
                                });
                            });
                }).setNegativeButton("Закрыть", null).show();
    }

    private void showReplyMessagePicker(int account, String chat, String replyText, ArrayList<TLRPC.Message> results, ChatEntry entry, int position) {
        isAgentWorking = false;
        if (account != org.telegram.messenger.UserConfig.selectedAccount) {
            completeThinkingEntry(entry, position, "Аккаунт изменился. Результаты поиска закрыты.");
            return;
        }
        if (getParentActivity() == null) {
            completeThinkingEntry(entry, position, "Экран уже закрыт. Ответ не отправлен.");
            return;
        }
        if (results == null || results.isEmpty()) {
            completeThinkingEntry(entry, position, "Сообщения по запросу не найдены.");
            return;
        }
        completeThinkingEntry(entry, position, "Нашла сообщения: " + results.size() + ". Выбери сообщение, на которое ответить.");
        CharSequence[] labels = new CharSequence[Math.min(20, results.size())];
        for (int i = 0; i < labels.length; i++) {
            TLRPC.Message message = results.get(i);
            String body = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
            if (body.length() > 90) body = body.substring(0, 87) + "…";
            labels[i] = flattenConfirmationText(getMessageChatTitle(message, account) + " · " + body);
        }
        new AlertDialog.Builder(getParentActivity()).setTitle("Выбери сообщение")
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= labels.length) return;
                    if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                        completeThinkingEntry(entry, position, "Аккаунт изменился. Ответ не отправлен.");
                        return;
                    }
                    TLRPC.Message message = results.get(which);
                    long dialogId = message.dialog_id;
                    if (dialogId == 0 && message.peer_id != null) dialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
                    if (dialogId == 0 && !TextUtils.isEmpty(chat)) {
                        TLRPC.Dialog target = YumiAgentEngine.findDialog(chat);
                        if (target != null) dialogId = target.id;
                    }
                    if (dialogId == 0) {
                        completeThinkingEntry(entry, position, "Не удалось определить чат этого сообщения.");
                        return;
                    }
                    if (org.telegram.messenger.DialogObject.isEncryptedDialog(dialogId)) {
                        completeThinkingEntry(entry, position, "Ответы через AI в секретных чатах не поддерживаются.");
                        return;
                    }
                    final long targetDialogId = dialogId;
                    String sourceText = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
                    if (sourceText.length() > 500) sourceText = sourceText.substring(0, 497) + "…";
                    String replyPreview = flattenConfirmationText(replyText, 5000);
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Отправить ответ?",
                            "Чат: " + flattenConfirmationText(getDialogDisplayName(account, targetDialogId, chat)) +
                                    "\n\nОтвет на: " + sourceText + "\n\nТекст ответа: " + replyPreview,
                            "Отмена", "Отправить ответ", false,
                            () -> completeThinkingEntry(entry, position, "Ответ отменён."), () -> {
                                if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                                    completeThinkingEntry(entry, position, "Аккаунт изменился. Ответ не отправлен.");
                                    return;
                                }
                                if (!YumiConfig.isAiEnabled() || !YumiConfig.isAiAgentSendMessagesEnabled()) {
                                    completeThinkingEntry(entry, position, "Ответы выключены в настройках AI.");
                                    return;
                                }
                                completeThinkingEntry(entry, position, "Отправляю ответ…");
                                YumiAgentEngine.replyToMessage(account, targetDialogId, message, replyText, new YumiAgentEngine.AgentCallback() {
                                    @Override public void onResponse(String result) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, result));
                                    }
                                    @Override public void onError(String error) {
                                        AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, "Не удалось отправить ответ: " + error));
                                    }
                                });
                            });
                }).setNegativeButton("Закрыть", null).show();
    }

    private void showEditMessagePicker(int account, String chat, ArrayList<TLRPC.Message> results, String newText, ChatEntry entry, int position) {
        isAgentWorking = false;
        if (account != org.telegram.messenger.UserConfig.selectedAccount) {
            completeThinkingEntry(entry, position, "Аккаунт изменился. Результаты поиска закрыты.");
            return;
        }
        if (getParentActivity() == null) {
            completeThinkingEntry(entry, position, "Экран уже закрыт. Результаты поиска недоступны.");
            return;
        }
        if (results == null || results.isEmpty()) {
            completeThinkingEntry(entry, position, "Сообщения по запросу не найдены.");
            return;
        }
        ArrayList<TLRPC.Message> editableResults = new ArrayList<>();
        for (TLRPC.Message message : results) if (message.out) editableResults.add(message);
        if (editableResults.isEmpty()) {
            completeThinkingEntry(entry, position, "Среди результатов нет твоих сообщений, которые можно изменить.");
            return;
        }
        completeThinkingEntry(entry, position, "Нашла отправленные сообщения: " + editableResults.size() + ". Выбери сообщение для изменения.");
        CharSequence[] labels = new CharSequence[Math.min(20, editableResults.size())];
        for (int i = 0; i < labels.length; i++) {
            TLRPC.Message message = editableResults.get(i);
            String body = TextUtils.isEmpty(message.message) ? "Сообщение без текста" : flattenConfirmationText(message.message);
            if (body.length() > 90) body = body.substring(0, 87) + "…";
            labels[i] = flattenConfirmationText(getMessageChatTitle(message, account) + " · " + (message.out ? "Ты: " : "") + body);
        }
        new AlertDialog.Builder(getParentActivity()).setTitle("Выбери сообщение")
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= labels.length) return;
                    if (account != org.telegram.messenger.UserConfig.selectedAccount) {
                        completeThinkingEntry(entry, position, "Аккаунт изменился. Сообщение не изменено.");
                        return;
                    }
                    TLRPC.Message message = editableResults.get(which);
                    long dialogId = message.dialog_id;
                    if (dialogId == 0 && message.peer_id != null) dialogId = org.telegram.messenger.DialogObject.getPeerDialogId(message.peer_id);
                    if (dialogId == 0 && !TextUtils.isEmpty(chat)) {
                        TLRPC.Dialog target = YumiAgentEngine.findDialog(chat);
                        if (target != null) dialogId = target.id;
                    }
                    if (dialogId == 0) {
                        completeThinkingEntry(entry, position, "Не удалось определить чат этого сообщения.");
                        return;
                    }
                    long targetDialogId = dialogId;
                    String oldText = flattenConfirmationText(TextUtils.isEmpty(message.message) ? "Сообщение без текста" : message.message);
                    if (oldText.length() > 500) oldText = oldText.substring(0, 497) + "…";
                    String newTextPreview = flattenConfirmationText(newText);
                    if (newTextPreview.length() > 500) newTextPreview = newTextPreview.substring(0, 497) + "…";
                    org.telegram.ui.Components.YumiAgentConfirmationSheet.show(getParentActivity(), "Изменить сообщение?",
                            "Было: " + oldText + "\n\nСтанет: " + newTextPreview, "Отмена", "Сохранить", false,
                            () -> completeThinkingEntry(entry, position, "Изменение сообщения отменено."), () -> {
                                completeThinkingEntry(entry, position, "Изменяю сообщение…");
                                YumiAgentEngine.confirmEditMessage(account, targetDialogId, message.id, newText, new YumiAgentEngine.AgentCallback() {
                                    @Override public void onResponse(String result) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, result)); }
                                    @Override public void onError(String error) { AndroidUtilities.runOnUIThread(() -> completeThinkingEntry(entry, position, error)); }
                                });
                            });
                }).setNegativeButton("Закрыть", null).show();
    }

    private boolean completeThinkingEntry(ChatEntry entry, int position, String text) {
        if (entry == null || position < 0 || position >= messages.size() || messages.get(position) != entry) return false;
        entry.text = text;
        entry.isThinking = false;
        adapter.notifyItemChanged(position);
        persistEntry(entry);
        updateWelcomeVisibility();
        updateComposerState();
        if (recyclerView != null) recyclerView.scrollToPosition(Math.max(0, messages.size() - 1));
        return true;
    }

    private void appendAgentNotice(String text) {
        ChatEntry entry = new ChatEntry(true, text);
        messages.add(entry);
        adapter.notifyItemInserted(messages.size() - 1);
        persistEntry(entry);
        updateWelcomeVisibility();
        if (recyclerView != null) recyclerView.scrollToPosition(messages.size() - 1);
    }

    private CharSequence renderAgentText(String value) {
        SpannableStringBuilder text = new SpannableStringBuilder(value == null ? "" : value);
        replaceMarkdown(text, Pattern.compile("```(?:[a-zA-Z0-9_+-]+)?\\s*([\\s\\S]*?)```"), true);
        replaceMarkdown(text, Pattern.compile("`([^`\\n]+)`"), true);
        replaceMarkdown(text, Pattern.compile("\\*\\*(.+?)\\*\\*"), false);
        Matcher headings = Pattern.compile("(?m)^#{1,3}\\s+.+$").matcher(text);
        while (headings.find()) text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), headings.start(), headings.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Linkify.addLinks(text, Linkify.WEB_URLS);
        return text;
    }

    private void replaceMarkdown(SpannableStringBuilder text, Pattern pattern, boolean code) {
        Matcher matcher = pattern.matcher(text);
        ArrayList<int[]> ranges = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
            values.add(matcher.group(1));
        }
        for (int i = ranges.size() - 1; i >= 0; i--) {
            int start = ranges.get(i)[0];
            int end = ranges.get(i)[1];
            String inner = values.get(i);
            text.replace(start, end, inner);
            text.setSpan(code ? new TypefaceSpan("monospace") : new StyleSpan(android.graphics.Typeface.BOLD),
                    start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (code) text.setSpan(new BackgroundColorSpan(YumiAgentTheme.colors().background), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {
        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            frame.setPadding(dp(18), dp(5), dp(18), dp(5));
            LinearLayout column = new LinearLayout(parent.getContext());
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout labelRow = new LinearLayout(parent.getContext());
            labelRow.setOrientation(LinearLayout.HORIZONTAL);
            labelRow.setGravity(Gravity.CENTER_VERTICAL);
            labelRow.setPadding(dp(2), 0, 0, dp(5));
            TextView textView = new TextView(parent.getContext());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLineSpacing(dp(3), 1.02f);
            textView.setPadding(dp(15), dp(11), dp(15), dp(11));
            TextView label = new TextView(parent.getContext());
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            label.setTextColor(YumiAgentTheme.colors().accent);
            label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            ProgressBar progress = new ProgressBar(parent.getContext(), null, android.R.attr.progressBarStyleSmall);
            progress.setIndeterminate(true);
            progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(YumiAgentTheme.colors().accent));
            labelRow.addView(progress, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 7, 0));
            labelRow.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            column.addView(labelRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            column.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            frame.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return new ChatViewHolder(frame, textView, label, progress, column);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            ChatEntry entry = messages.get(position);
            String displayText = !TextUtils.isEmpty(entry.attachmentName)
                    ? "📎  " + entry.attachmentName + "\n" + entry.text
                    : entry.text;
            holder.textView.setText(entry.isAi ? renderAgentText(displayText) : displayText, TextView.BufferType.SPANNABLE);
            holder.textView.setMovementMethod(entry.isAi ? LinkMovementMethod.getInstance() : null);
            holder.textView.setLinksClickable(entry.isAi);
            YumiAgentTheme.Colors colors = YumiAgentTheme.colors();
            LinearLayout.LayoutParams textLp = (LinearLayout.LayoutParams) holder.textView.getLayoutParams();
            FrameLayout.LayoutParams columnLp = (FrameLayout.LayoutParams) holder.column.getLayoutParams();
            if (entry.isAi) {
                columnLp.gravity = Gravity.LEFT;
                holder.label.setVisibility(View.VISIBLE);
                holder.progress.setVisibility(entry.isThinking ? View.VISIBLE : View.GONE);
                holder.label.setText("✦  " + YumiLocale.getString(entry.isThinking ? R.string.YumiAiAssistantWorking : R.string.YumiAiAssistantLabel));
                holder.textView.setTextColor(colors.primaryText);
                holder.textView.setBackground(agentSurface(colors.surface, dp(17), entry.isThinking ? colors.accentSoft : colors.outline));
                holder.textView.setPadding(dp(14), dp(12), dp(14), dp(12));
                textLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                holder.column.setPadding(dp(2), dp(2), dp(20), dp(2));
            } else {
                columnLp.gravity = Gravity.RIGHT;
                holder.label.setVisibility(View.GONE);
                holder.progress.setVisibility(View.GONE);
                holder.textView.setTextColor(colors.userText);
                holder.textView.setBackground(agentSurface(colors.userBubble, dp(20), Color.TRANSPARENT));
                holder.textView.setPadding(dp(15), dp(10), dp(15), dp(10));
                Context context = getParentActivity() != null ? getParentActivity() : ApplicationLoader.applicationContext;
                int screenWidth = context != null ? context.getResources().getDisplayMetrics().widthPixels : dp(420);
                textLp.width = Math.min((int) (screenWidth * .82f), dp(420));
                holder.column.setPadding(dp(30), dp(2), dp(2), dp(2));
            }
            holder.column.setLayoutParams(columnLp);
            holder.textView.setLayoutParams(textLp);

            // Copy on long-click
            holder.textView.setOnLongClickListener(v -> {
                Context ctx = getParentActivity() != null ? getParentActivity() : ApplicationLoader.applicationContext;
                if (ctx != null) {
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Yumi AI", entry.text));
                        BulletinFactory.of(YumiAiCopilotActivity.this).createSimpleBulletin(R.raw.chats_infotip, "Текст скопійовано").show();
                    }
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    private class ConversationAdapter extends RecyclerView.Adapter<ConversationViewHolder> {
        private final ArrayList<YumiAgentConversationStore.Conversation> filtered = new ArrayList<>();

        void showRows(List<YumiAgentConversationStore.Conversation> rows, String query) {
            filtered.clear();
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (rows != null) filtered.addAll(rows);
            notifyDataSetChanged();
            if (historyEmptyView != null) {
                historyEmptyView.setText(YumiLocale.getString(needle.isEmpty() ? R.string.YumiAiHistoryEmpty : R.string.YumiAiHistoryNoResults));
                historyEmptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }

        @NonNull
        @Override
        public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(13), dp(10), dp(13), dp(10));
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(parent.getContext());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            title.setTextColor(YumiAgentTheme.colors().primaryText);
            title.setMaxLines(1);
            row.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            TextView preview = new TextView(parent.getContext());
            preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            preview.setTextColor(YumiAgentTheme.colors().secondaryText);
            preview.setMaxLines(1);
            row.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            return new ConversationViewHolder(row, title, preview);
        }

        @Override
        public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
            YumiAgentConversationStore.Conversation item = filtered.get(position);
            holder.title.setText(getConversationTitle(item));
            holder.preview.setText(TextUtils.isEmpty(item.preview) ? YumiLocale.getString(R.string.YumiAiNewChat) : item.preview);
            boolean selected = TextUtils.equals(item.id, conversationId);
            holder.itemView.setBackground(agentSurface(selected ? YumiAgentTheme.colors().accentSoft : Color.TRANSPARENT, dp(14), Color.TRANSPARENT));
            holder.itemView.setOnClickListener(v -> {
                if (conversationAccount == org.telegram.messenger.UserConfig.selectedAccount) {
                    if (isAgentWorking && !TextUtils.equals(item.id, conversationId)) {
                        BulletinFactory.of(YumiAiCopilotActivity.this).createSimpleBulletin(R.raw.chats_infotip, "Дождись завершения действия AI перед переключением чата.").show();
                        return;
                    }
                    hideConversationDrawer();
                    openConversation(conversationAccount, item.id);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                showConversationActions(item);
                return true;
            });
        }

        @Override public int getItemCount() { return filtered.size(); }
    }

    private static class ConversationViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView preview;
        ConversationViewHolder(View itemView, TextView title, TextView preview) {
            super(itemView);
            this.title = title;
            this.preview = preview;
        }
    }

    private static class ChatViewHolder extends RecyclerView.ViewHolder {
        public final TextView textView;
        public final TextView label;
        public final ProgressBar progress;
        public final LinearLayout column;
        public ChatViewHolder(View itemView, TextView textView, TextView label, ProgressBar progress, LinearLayout column) {
            super(itemView);
            this.textView = textView;
            this.label = label;
            this.progress = progress;
            this.column = column;
        }
    }
}
