package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.yumigram.ai.YumiAiManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Interactive AI Copilot Chat for Yumigram (Юмиграм).
 * Can chat, summarize dialogues, read chats, and draft/send messages on user request.
 */
public class YumiAiCopilotActivity extends BaseFragment {

    public static class ChatEntry {
        public final boolean isAi;
        public final String text;

        public ChatEntry(boolean isAi, String text) {
            this.isAi = isAi;
            this.text = text;
        }
    }

    private final ArrayList<ChatEntry> messages = new ArrayList<>();
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText inputEditText;
    private ImageView sendButton;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Yumi Copilot ✨");
        actionBar.setSubtitle(LocaleController.getString(R.string.YumiSectionAI));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        recyclerView = new RecyclerView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 60));

        // Bottom Input Container
        LinearLayout inputBar = new LinearLayout(context);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        inputBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        inputBar.setGravity(Gravity.CENTER_VERTICAL);

        inputEditText = new EditText(context);
        inputEditText.setHint(LocaleController.getString(R.string.YumiAiInputHint));
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

        root.addView(inputBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.BOTTOM));

        // Welcome greeting
        if (messages.isEmpty()) {
            messages.add(new ChatEntry(true, LocaleController.getString(R.string.YumiAiGreeting)));
            adapter.notifyDataSetChanged();
        }

        return fragmentView;
    }

    private void sendMessage() {
        String text = inputEditText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        inputEditText.setText("");

        messages.add(new ChatEntry(false, text));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);

        processUserQuery(text);
    }

    private void processUserQuery(String query) {
        String lower = query.toLowerCase();

        // Check if query is a request to send a message (UK/EN/RU)
        if (lower.startsWith("напиши ") || lower.startsWith("відправ ") || lower.startsWith("send ") || lower.startsWith("write ") || lower.startsWith("отправь ")) {
            if (!org.telegram.messenger.yumigram.YumiConfig.isAiAgentSendMessagesEnabled()) {
                messages.add(new ChatEntry(true, "⚠️ AI Agent message sending is disabled in Yumigram AI Settings."));
                adapter.notifyItemInserted(messages.size() - 1);
                return;
            }
            handleSendRequest(query);
            return;
        }

        // Check if query is a request to read a chat (UK/EN/RU)
        if (lower.startsWith("прочитай ") || lower.startsWith("що в чаті ") || lower.startsWith("read ") || lower.startsWith("summarize ") || lower.startsWith("что в чате ")) {
            if (!org.telegram.messenger.yumigram.YumiConfig.isAiAgentReadChatEnabled()) {
                messages.add(new ChatEntry(true, "⚠️ AI Agent chat reading is disabled in Yumigram AI Settings."));
                adapter.notifyItemInserted(messages.size() - 1);
                return;
            }
            handleReadRequest(query);
            return;
        }

        // Standard AI generation
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("You are Yumigram's advanced AI Copilot Agent in Telegram. Be helpful, concise, friendly, and act with full awareness of your assistant capabilities.\n\nUser asks: ").append(query);

        YumiAiManager.requestAi(contextBuilder.toString(), new YumiAiManager.AiCallback() {
            @Override
            public void onSuccess(String result) {
                messages.add(new ChatEntry(true, result));
                adapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            }

            @Override
            public void onError(String errorMessage) {
                messages.add(new ChatEntry(true, "⚠️ Error: " + errorMessage));
                adapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        });
    }

    private void handleSendRequest(String query) {
        // e.g.: "напиши Тарасу: привіт" or "write to John: hello"
        String[] parts = query.split(":", 2);
        if (parts.length < 2) {
            messages.add(new ChatEntry(true, "Please specify recipient and message separated by a colon, e.g.:\n«Write to [Name]: [Message]» or «Напиши [Ім'я]: [Текст]»"));
            adapter.notifyItemInserted(messages.size() - 1);
            return;
        }

        String targetName = parts[0].replaceFirst("(?i)(напиши|відправ|send|write to|write|отправь)\\s*", "").trim();
        String messageBody = parts[1].trim();

        TLRPC.User foundUser = findUserByName(targetName);
        if (foundUser != null) {
            SendMessagesHelper.getInstance(currentAccount).sendMessage(
                    SendMessagesHelper.SendMessageParams.of(messageBody, foundUser.id)
            );
            messages.add(new ChatEntry(true, "✅ Повідомлення успішно надіслано для " + foundUser.first_name + ":\n\"" + messageBody + "\""));
        } else {
            messages.add(new ChatEntry(true, "Не вдалося знайти користувача з іменем «" + targetName + "» у ваших чатах. Спробуйте уточнити точне ім'я."));
        }
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    private void handleReadRequest(String query) {
        String targetName = query.replaceFirst("(?i)(прочитай|що в чаті|read|summarize|что в чате)\\s*", "").trim();
        TLRPC.User foundUser = findUserByName(targetName);

        if (foundUser != null) {
            messages.add(new ChatEntry(true, "📖 Читаю останні повідомлення з чату " + foundUser.first_name + "...\n(Аналізую вміст за допомогою ШІ)"));
            adapter.notifyItemInserted(messages.size() - 1);

            ArrayList<MessageObject> msgList = MessagesController.getInstance(currentAccount).dialogMessage.get(foundUser.id);
            if (msgList != null && !msgList.isEmpty()) {
                YumiAiManager.summarizeMessages(msgList, new YumiAiManager.AiCallback() {
                    @Override
                    public void onSuccess(String result) {
                        AndroidUtilities.runOnUIThread(() -> {
                            messages.add(new ChatEntry(true, "📖 Стислий зміст чату " + foundUser.first_name + ":\n\n" + result));
                            adapter.notifyItemInserted(messages.size() - 1);
                            recyclerView.scrollToPosition(messages.size() - 1);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        AndroidUtilities.runOnUIThread(() -> {
                            messages.add(new ChatEntry(true, "⚠️ Помилка аналізу повідомлень: " + errorMessage));
                            adapter.notifyItemInserted(messages.size() - 1);
                            recyclerView.scrollToPosition(messages.size() - 1);
                        });
                    }
                });
            } else {
                messages.add(new ChatEntry(true, "Не знайдено завантажених повідомлень у чаті з " + foundUser.first_name + ". Відкрийте цей чат, щоб синхронізувати його."));
                adapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        } else {
            messages.add(new ChatEntry(true, "Чат з «" + targetName + "» не знайдено серед ваших діалогів."));
            adapter.notifyItemInserted(messages.size() - 1);
        }
    }

    private TLRPC.User findUserByName(String name) {
        if (TextUtils.isEmpty(name)) return null;
        String search = name.toLowerCase();
        for (int a = 0; a < MessagesController.getInstance(currentAccount).dialogs_dict.size(); a++) {
            long did = MessagesController.getInstance(currentAccount).dialogs_dict.keyAt(a);
            if (did > 0) {
                TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(did);
                if (u != null && u.first_name != null && u.first_name.toLowerCase().contains(search)) {
                    return u;
                }
            }
        }
        return null;
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {
        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            frame.setPadding(dp(12), dp(4), dp(12), dp(4));

            TextView textView = new TextView(parent.getContext());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
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
            } else {
                lp.gravity = Gravity.RIGHT;
                holder.textView.setTextColor(Theme.getColor(Theme.key_chat_messageTextOut));
                shape.setColor(Theme.getColor(Theme.key_chat_outBubble));
            }
            holder.textView.setBackground(shape);
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
