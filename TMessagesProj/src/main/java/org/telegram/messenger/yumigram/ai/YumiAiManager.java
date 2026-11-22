package org.telegram.messenger.yumigram.ai;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.yumigram.YumiConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Built-in AI Assistant engine for Yumigram (Юмиграм).
 * Supports cloud models (Gemini with automatic fallback) and on-device local models.
 */
public class YumiAiManager {

    private static final String TAG = "YumiAiManager";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AiCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }

    /**
     * Transcribes an incoming/outgoing voice or video note using AI multimodal capabilities.
     */
    public static void transcribeVoice(MessageObject messageObject, AiCallback callback) {
        if (messageObject == null) {
            notifyError(callback, "Message is null");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            File audioFile = null;
            if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
                File f = new File(messageObject.messageOwner.attachPath);
                if (f.exists()) {
                    audioFile = f;
                }
            }
            if (audioFile == null && messageObject.messageOwner != null) {
                audioFile = FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner);
            }

            if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
                notifyError(callback, "Файл голосового повідомлення ще не завантажено");
                return;
            }

            try {
                byte[] bytes = new byte[(int) audioFile.length()];
                try (FileInputStream fis = new FileInputStream(audioFile)) {
                    int read = 0;
                    while (read < bytes.length) {
                        int r = fis.read(bytes, read, bytes.length - read);
                        if (r == -1) break;
                        read += r;
                    }
                }

                String base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP);
                String prompt = "Transcribe this voice message word for word in its original spoken language with exact punctuation and capitalization. Return ONLY the transcribed text without extra comments.";

                requestAiInternal(prompt, base64Audio, "audio/ogg", callback);
            } catch (Exception e) {
                FileLog.e(TAG, e);
                notifyError(callback, "Помилка читання аудіо: " + e.getLocalizedMessage());
            }
        });
    }

    /**
     * Generates a concise summary for recent chat messages.
     */
    public static void summarizeMessages(ArrayList<MessageObject> messages, AiCallback callback) {
        if (messages == null || messages.isEmpty()) {
            notifyError(callback, "Немає повідомлень для переказу");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Зроби стислий і зрозумілий підсумок (3-5 головних пунктів) наступної переписки в Telegram:\n\n");
        int count = 0;
        for (int i = Math.max(0, messages.size() - 35); i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && msg.messageText != null && msg.messageText.length() > 0) {
                String sender = msg.isOutOwner() ? "Я" : "Співрозмовник";
                sb.append(sender).append(": ").append(msg.messageText).append("\n");
                count++;
            }
        }

        if (count == 0) {
            notifyError(callback, "Текстових повідомлень не знайдено");
            return;
        }

        requestAi(sb.toString(), callback);
    }

    /**
     * Generates a smart context-aware reply.
     */
    public static void generateQuickReply(String messageText, AiCallback callback) {
        if (TextUtils.isEmpty(messageText)) {
            notifyError(callback, "Порожній вхідний текст");
            return;
        }
        String prompt = "Запропонуй 3 коротких, влучних і ввічливих варіанти відповіді на наступне повідомлення в Telegram:\n\"" + messageText + "\"";
        requestAi(prompt, callback);
    }

    /**
     * Rewrites text to improve clarity, fix typos, or change tone.
     */
    public static void rewriteText(String text, String style, AiCallback callback) {
        if (TextUtils.isEmpty(text)) {
            notifyError(callback, "Порожній текст для покращення");
            return;
        }
        String prompt = "Перепиши наступний текст у стилі (" + (TextUtils.isEmpty(style) ? "грамотно, ввічливо та чітко" : style) + "):\n\"" + text + "\"";
        requestAi(prompt, callback);
    }

    /**
     * Translates text into target language using AI.
     */
    public static void translateText(String text, String targetLang, AiCallback callback) {
        if (TextUtils.isEmpty(text)) {
            notifyError(callback, "Порожній текст для перекладу");
            return;
        }
        String prompt = "Переклади наступний текст мовою (" + (TextUtils.isEmpty(targetLang) ? "українська" : targetLang) + "). Виведи ТІЛЬКИ готовий переклад без лапок і коментарів:\n\"" + text + "\"";
        requestAi(prompt, callback);
    }

    public static void requestAi(String prompt, AiCallback callback) {
        if (YumiConfig.isLocalAi()) {
            YumiLocalAiManager.inferLocal(YumiConfig.getAiLocalModelId(), prompt, callback);
        } else {
            requestAiInternal(prompt, null, null, callback);
        }
    }

    private static void requestAiInternal(String prompt, String base64Data, String mimeType, AiCallback callback) {
        Utilities.stageQueue.postRunnable(() -> {
            String apiKey = YumiConfig.getAiApiKey();
            if (TextUtils.isEmpty(apiKey)) {
                notifyError(callback, "Вкажіть API-ключ Gemini у Налаштуваннях Юмиграм");
                return;
            }

            String primaryModel = YumiConfig.getAiPrimaryModel();
            String fallbackModel = YumiConfig.getAiFallbackModel();

            // Try primary model first
            boolean success = tryModel(primaryModel, apiKey, prompt, base64Data, mimeType, callback, false);
            if (!success && !TextUtils.isEmpty(fallbackModel) && !fallbackModel.equals(primaryModel)) {
                FileLog.d(TAG + ": Primary model " + primaryModel + " failed. Falling back to " + fallbackModel);
                tryModel(fallbackModel, apiKey, prompt, base64Data, mimeType, callback, true);
            }
        });
    }

    private static boolean tryModel(String model, String apiKey, String prompt, String base64Data, String mimeType, AiCallback callback, boolean isFinalAttempt) {
        try {
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setDoOutput(true);

            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentItem = new JSONObject();
            JSONArray parts = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);

            if (!TextUtils.isEmpty(base64Data) && !TextUtils.isEmpty(mimeType)) {
                JSONObject inlineDataPart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", mimeType);
                inlineData.put("data", base64Data);
                inlineDataPart.put("inline_data", inlineData);
                parts.put(inlineDataPart);
            }

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
                            return true;
                        }
                    }
                }
                if (isFinalAttempt) {
                    notifyError(callback, "Порожня відповідь від моделі " + model);
                }
                return false;
            } else {
                FileLog.e(TAG + ": Model " + model + " returned HTTP " + code);
                if (isFinalAttempt) {
                    notifyError(callback, "Помилка ШІ (" + model + "): HTTP " + code);
                }
                return false;
            }
        } catch (Exception e) {
            FileLog.e(TAG, e);
            if (isFinalAttempt) {
                notifyError(callback, "Помилка зв'язку (" + model + "): " + e.getLocalizedMessage());
            }
            return false;
        }
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
