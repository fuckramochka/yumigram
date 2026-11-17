package org.telegram.messenger.yumigram.ai;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.yumigram.YumiConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Built-in AI Assistant engine for Yumigram (Юмиграм).
 * Powered by Gemini API / custom LLM endpoints.
 */
public class YumiAiManager {

    private static final String TAG = "YumiAiManager";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AiCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }

    /**
     * Generates a concise summary for recent chat messages.
     */
    public static void summarizeMessages(ArrayList<MessageObject> messages, AiCallback callback) {
        if (messages == null || messages.isEmpty()) {
            notifyError(callback, "No messages to summarize");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Суммаризируй кратко (3-5 пунктов) следующую переписку в Telegram:\n\n");
        int count = 0;
        for (int i = Math.max(0, messages.size() - 30); i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && msg.messageText != null && msg.messageText.length() > 0) {
                String sender = msg.isOutOwner() ? "Я" : "Собеседник";
                sb.append(sender).append(": ").append(msg.messageText).append("\n");
                count++;
            }
        }

        if (count == 0) {
            notifyError(callback, "No text messages found");
            return;
        }

        requestAi(sb.toString(), callback);
    }

    /**
     * Generates a smart context-aware reply.
     */
    public static void generateQuickReply(String messageText, AiCallback callback) {
        if (TextUtils.isEmpty(messageText)) {
            notifyError(callback, "Empty input text");
            return;
        }
        String prompt = "Предложи 3 кратких, вежливых варианта ответа на следующее сообщение в Telegram:\n\"" + messageText + "\"";
        requestAi(prompt, callback);
    }

    /**
     * Rewrites text to improve clarity, fix typos, or change tone.
     */
    public static void rewriteText(String text, String style, AiCallback callback) {
        if (TextUtils.isEmpty(text)) {
            notifyError(callback, "Empty text to rewrite");
            return;
        }
        String prompt = "Перепиши следующий текст в стиле (" + (TextUtils.isEmpty(style) ? "грамотно, вежливо и четко" : style) + "):\n\"" + text + "\"";
        requestAi(prompt, callback);
    }

    /**
     * Translates text into target language using AI.
     */
    public static void translateText(String text, String targetLang, AiCallback callback) {
        if (TextUtils.isEmpty(text)) {
            notifyError(callback, "Empty text to translate");
            return;
        }
        String prompt = "Переведи следующий текст на язык (" + (TextUtils.isEmpty(targetLang) ? "русский" : targetLang) + "). Выведи только готовый перевод без пояснений:\n\"" + text + "\"";
        requestAi(prompt, callback);
    }

    private static void requestAi(String prompt, AiCallback callback) {
        Utilities.stageQueue.postRunnable(() -> {
            String apiKey = YumiConfig.getAiApiKey();
            if (TextUtils.isEmpty(apiKey)) {
                notifyError(callback, "AI API Key is not set in Yumigram Settings");
                return;
            }

            try {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject contentItem = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject partItem = new JSONObject();
                partItem.put("text", prompt);
                parts.put(partItem);
                contentItem.put("parts", parts);
                contents.put(contentItem);
                payload.put("contents", contents);

                byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                    }

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray candidates = jsonResponse.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject candidate = candidates.getJSONObject(0);
                        JSONObject content = candidate.optJSONObject("content");
                        if (content != null) {
                            JSONArray responseParts = content.optJSONArray("parts");
                            if (responseParts != null && responseParts.length() > 0) {
                                String resultText = responseParts.getJSONObject(0).optString("text");
                                notifySuccess(callback, resultText.trim());
                                return;
                            }
                        }
                    }
                    notifyError(callback, "Empty response from Gemini AI");
                } else {
                    notifyError(callback, "AI request failed with HTTP " + code);
                }
            } catch (Exception e) {
                FileLog.e(TAG, e);
                notifyError(callback, "Error: " + e.getLocalizedMessage());
            }
        });
    }

    private static void notifySuccess(AiCallback callback, String text) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onSuccess(text));
    }

    private static void notifyError(AiCallback callback, String error) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onError(error));
    }
}
