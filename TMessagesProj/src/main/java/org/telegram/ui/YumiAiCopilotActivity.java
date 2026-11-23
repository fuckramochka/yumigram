package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.yumigram.ai.YumiAgentEngine;
import org.telegram.messenger.yumigram.util.YumiLocale;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
    private static final int MENU_CLEAR = 1;

    public static class ChatEntry {
        public final boolean isAi;
        public String text;
        public boolean isThinking;

        public ChatEntry(boolean isAi, String text) {
            this(isAi, text, false);
        }

        public ChatEntry(boolean isAi, String text, boolean isThinking) {
            this.isAi = isAi;
            this.text = text;
            this.isThinking = isThinking;
        }
    }

    private final ArrayList<ChatEntry> messages = new ArrayList<>();
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText inputEditText;
    private ImageView sendButton;
    private boolean isAgentWorking = false;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Yumi Agent ✨");
        actionBar.setSubtitle("Autonomous Telegram AI");

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_CLEAR, R.drawable.msg_delete);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR) {
                    clearChatHistory();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        // Load saved chat history
        loadChatHistory();

        // Chat RecyclerView
        recyclerView = new RecyclerView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 104));

        // Bottom panel container (Chips + Input)
        LinearLayout bottomPanel = new LinearLayout(context);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        // Quick Suggestion Chips
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsLayout = new LinearLayout(context);
        chipsLayout.setOrientation(LinearLayout.HORIZONTAL);
        chipsLayout.setPadding(dp(10), dp(6), dp(10), dp(4));

        addChip(chipsLayout, "✨ Підсумувати чати", "Підсумуй мої останні активні чати");
        addChip(chipsLayout, "✍️ Скласти повідомлення", "Напиши ввічливу відповідь");
        addChip(chipsLayout, "🔍 Знайти в повідомленнях", "Знайди важливі повідомлення за сьогодні");
        addChip(chipsLayout, "🌐 Перекласти", "Переклади текст українською:");

        chipsScroll.addView(chipsLayout);
        bottomPanel.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Input bar
        LinearLayout inputBar = new LinearLayout(context);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setPadding(dp(12), dp(4), dp(12), dp(8));
        inputBar.setGravity(Gravity.CENTER_VERTICAL);

        inputEditText = new EditText(context);
        inputEditText.setHint(YumiLocale.getString(R.string.YumiAiInputHint));
        inputEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        inputEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        inputEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        inputEditText.setBackground(null);
        inputBar.addView(inputEditText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        sendButton = new ImageView(context);
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend));
        sendButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        sendButton.setOnClickListener(v -> sendMessage());
        inputBar.addView(sendButton, LayoutHelper.createLinear(40, 40));

        bottomPanel.addView(inputBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        root.addView(bottomPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        // Welcome greeting if fresh
        if (messages.isEmpty()) {
            messages.add(new ChatEntry(true, YumiLocale.getString(R.string.YumiAiGreeting)));
            saveChatHistory();
            adapter.notifyDataSetChanged();
        }

        return fragmentView;
    }

    private void addChip(LinearLayout parent, String title, String prompt) {
        TextView chip = new TextView(parent.getContext());
        chip.setText(title);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        chip.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        bg.setStroke(dp(1), Theme.getColor(Theme.key_divider));
        chip.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            inputEditText.setText(prompt);
            inputEditText.setSelection(prompt.length());
        });

        parent.addView(chip);
    }

    private void sendMessage() {
        if (isAgentWorking) return;

        String text = inputEditText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        inputEditText.setText("");

        messages.add(new ChatEntry(false, text));
        int userPos = messages.size() - 1;
        adapter.notifyItemInserted(userPos);

        // Add thinking placeholder
        ChatEntry thinkingEntry = new ChatEntry(true, "✨ Yumi Agent аналізує...", true);
        messages.add(thinkingEntry);
        int thinkingPos = messages.size() - 1;
        adapter.notifyItemInserted(thinkingPos);
        recyclerView.scrollToPosition(thinkingPos);

        isAgentWorking = true;

        YumiAgentEngine.executePrompt(text, new YumiAgentEngine.AgentCallback() {
            @Override
            public void onResponse(String agentResponse) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    thinkingEntry.text = agentResponse;
                    thinkingEntry.isThinking = false;
                    adapter.notifyItemChanged(thinkingPos);
                    saveChatHistory();
                    recyclerView.scrollToPosition(messages.size() - 1);
                });
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    isAgentWorking = false;
                    thinkingEntry.text = "⚠️ Помилка агента: " + error;
                    thinkingEntry.isThinking = false;
                    adapter.notifyItemChanged(thinkingPos);
                    saveChatHistory();
                    recyclerView.scrollToPosition(messages.size() - 1);
                });
            }
        });
    }

    private void clearChatHistory() {
        messages.clear();
        messages.add(new ChatEntry(true, YumiLocale.getString(R.string.YumiAiGreeting)));
        adapter.notifyDataSetChanged();
        saveChatHistory();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, "Історію чату очищено").show();
    }

    private void loadChatHistory() {
        messages.clear();
        File file = new File(ApplicationLoader.applicationContext.getFilesDir(), HISTORY_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                messages.add(new ChatEntry(obj.optBoolean("isAi"), obj.optString("text")));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveChatHistory() {
        try {
            JSONArray arr = new JSONArray();
            // Store up to recent 60 messages
            int start = Math.max(0, messages.size() - 60);
            for (int i = start; i < messages.size(); i++) {
                ChatEntry e = messages.get(i);
                if (e.isThinking) continue;
                JSONObject obj = new JSONObject();
                obj.put("isAi", e.isAi);
                obj.put("text", e.text);
                arr.put(obj);
            }

            File file = new File(ApplicationLoader.applicationContext.getFilesDir(), HISTORY_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {
        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            frame.setPadding(dp(14), dp(4), dp(14), dp(4));

            TextView textView = new TextView(parent.getContext());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setLineSpacing(dp(2), 1.0f);
            textView.setPadding(dp(14), dp(10), dp(14), dp(10));
            frame.addView(textView);

            return new ChatViewHolder(frame, textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            ChatEntry entry = messages.get(position);
            holder.textView.setText(entry.text);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) holder.textView.getLayoutParams();
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(16));

            if (entry.isAi) {
                lp.gravity = Gravity.LEFT;
                holder.textView.setTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
                shape.setColor(Theme.getColor(Theme.key_chat_inBubble));
                if (entry.isThinking) {
                    shape.setStroke(dp(1), Theme.getColor(Theme.key_featuredStickers_addButton));
                }
            } else {
                lp.gravity = Gravity.RIGHT;
                holder.textView.setTextColor(Theme.getColor(Theme.key_chat_messageTextOut));
                shape.setColor(Theme.getColor(Theme.key_chat_outBubble));
            }
            holder.textView.setBackground(shape);

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

    private static class ChatViewHolder extends RecyclerView.ViewHolder {
        public final TextView textView;
        public ChatViewHolder(View itemView, TextView textView) {
            super(itemView);
            this.textView = textView;
        }
    }
}
