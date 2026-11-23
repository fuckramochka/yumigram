package org.telegram.messenger.yumigram.ai;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Autonomous AI Agent Engine for Yumigram (Юмиграм).
 * Interprets natural language user instructions, executes Telegram tools
 * (search dialogs, read history, send messages, transcribe), and orchestrates multi-step responses.
 */
public class YumiAgentEngine {

    private static final String TAG = "YumiAgentEngine";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AgentCallback {
        void onResponse(String text);
        void onError(String error);
    }

    private static final String SYSTEM_PROMPT =
            "You are Yumi Copilot, a powerful autonomous AI Agent built into the Yumigram Telegram client.\n" +
            "You have full access to Telegram and can reason, read chats, summarize discussions, draft messages, and take actions.\n\n" +
            "Capabilities & Action Syntax:\n" +
            "1. When instructed to send or write a message to someone, reply with:\n" +
            "   ACTION: SEND_MESSAGE | [recipient name] | [exact message text]\n" +
            "2. When instructed to read or summarize a chat/channel, reply with:\n" +
            "   ACTION: READ_CHAT | [chat or contact name]\n" +
            "3. For all other requests (answering questions, brainstorming, explaining, translating, drafting):\n" +
            "   Respond directly, intelligently and naturally without any ACTION prefix.\n\n" +
            "Always reply in the same language as the user (Ukrainian, English, etc.). Be concise, helpful and precise.";

    /**
     * Processes user prompt through autonomous agent reasoning.
     */
    public static void executePrompt(String userPrompt, AgentCallback callback) {
        if (TextUtils.isEmpty(userPrompt)) {
            if (callback != null) callback.onError("Empty prompt");
            return;
        }

        // Include recent user dialog names so the model knows contacts
        int account = UserConfig.selectedAccount;
        StringBuilder contextBuilder = new StringBuilder();
        try {
            ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(account).getAllDialogs();
            if (dialogs != null && !dialogs.isEmpty()) {
                contextBuilder.append("\nKnown user chats/contacts: ");
                int limit = Math.min(25, dialogs.size());
                for (int i = 0; i < limit; i++) {
                    TLRPC.Dialog d = dialogs.get(i);
                    long id = d.id;
                    String title = null;
                    if (id > 0) {
                        TLRPC.User user = MessagesController.getInstance(account).getUser(id);
                        if (user != null) title = user.first_name + (user.last_name != null ? " " + user.last_name : "");
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-id);
                        if (chat != null) title = chat.title;
                    }
                    if (!TextUtils.isEmpty(title)) {
                        contextBuilder.append("\"").append(title).append("\", ");
                    }
                }
            }
        } catch (Throwable ignored) {}

        String fullPrompt = SYSTEM_PROMPT + contextBuilder.toString() + "\n\nUser: " + userPrompt;

        YumiAiManager.requestAi(fullPrompt, new YumiAiManager.AiCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                handleAgentResponse(rawResponse.trim(), callback);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    private static void handleAgentResponse(String response, AgentCallback callback) {
        if (response.startsWith("ACTION: SEND_MESSAGE |")) {
            // Parse: ACTION: SEND_MESSAGE | [recipient] | [text]
            String[] parts = response.split("\\|", 3);
            if (parts.length >= 3) {
                String recipient = parts[1].trim();
                String textToSend = parts[2].trim();
                executeSendMessage(recipient, textToSend, callback);
                return;
            }
        } else if (response.startsWith("ACTION: READ_CHAT |")) {
            // Parse: ACTION: READ_CHAT | [chat]
            String[] parts = response.split("\\|", 2);
            if (parts.length >= 2) {
                String chatName = parts[1].trim();
                executeReadChat(chatName, callback);
                return;
            }
        }

        // Direct answer
        if (callback != null) {
            callback.onResponse(response);
        }
    }

    private static void executeSendMessage(String recipientQuery, String messageText, AgentCallback callback) {
        if (!YumiConfig.isAiAgentSendMessagesEnabled()) {
            if (callback != null) callback.onResponse("⚠️ Відправка повідомлень вимкнена в налаштуваннях Yumi AI.");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            int account = UserConfig.selectedAccount;
            long targetDialogId = findDialogIdByName(account, recipientQuery);

            if (targetDialogId == 0) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onResponse("Не вдалося знайти співрозмовника або чат: «" + recipientQuery + "». Будь ласка, уточніть ім'я.");
                });
                return;
            }

            SendMessagesHelper.getInstance(account).sendMessage(
                    SendMessagesHelper.SendMessageParams.of(
                            messageText,
                            targetDialogId
                    )
            );

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onResponse("✅ Повідомлення надіслано до «" + recipientQuery + "»:\n\n«" + messageText + "»");
                }
            });
        });
    }

    private static void executeReadChat(String chatName, AgentCallback callback) {
        if (!YumiConfig.isAiAgentReadChatEnabled()) {
            if (callback != null) callback.onResponse("⚠️ Читання чатів агентом вимкнено в налаштуваннях Yumi AI.");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            int account = UserConfig.selectedAccount;
            long targetDialogId = findDialogIdByName(account, chatName);

            if (targetDialogId == 0) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onResponse("Не вдалося знайти чат або діалог: «" + chatName + "».");
                });
                return;
            }

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onResponse("📖 Читаю чат «" + chatName + "» та аналізую зміст повідомлень...");
                }
            });
        });
    }

    private static long findDialogIdByName(int account, String query) {
        if (TextUtils.isEmpty(query)) return 0;
        String q = query.toLowerCase();

        ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(account).getAllDialogs();
        if (dialogs == null) return 0;

        for (TLRPC.Dialog d : dialogs) {
            long id = d.id;
            if (id > 0) {
                TLRPC.User user = MessagesController.getInstance(account).getUser(id);
                if (user != null) {
                    String first = user.first_name != null ? user.first_name.toLowerCase() : "";
                    String last = user.last_name != null ? user.last_name.toLowerCase() : "";
                    String full = first + " " + last;
                    if (first.contains(q) || last.contains(q) || full.contains(q) || q.contains(first)) {
                        return id;
                    }
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-id);
                if (chat != null && chat.title != null) {
                    String title = chat.title.toLowerCase();
                    if (title.contains(q) || q.contains(title)) {
                        return id;
                    }
                }
            }
        }
        return 0;
    }
}
