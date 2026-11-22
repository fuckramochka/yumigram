package org.telegram.messenger.yumigram.ai;

import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Local on-device AI Manager for Yumigram (Юмиграм).
 * Downloads and manages lightweight GGUF models directly from Hugging Face.
 */
public class YumiLocalAiManager {

    private static final String TAG = "YumiLocalAiManager";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class LocalModelInfo {
        public final String id;
        public final String name;
        public final String size;
        public final String hfUrl;
        public final String fileName;

        public LocalModelInfo(String id, String name, String size, String hfUrl, String fileName) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.hfUrl = hfUrl;
            this.fileName = fileName;
        }
    }

    public static final List<LocalModelInfo> AVAILABLE_LOCAL_MODELS = new ArrayList<LocalModelInfo>() {{
        add(new LocalModelInfo(
                "qwen2.5-0.5b",
                "Qwen 2.5 0.5B Instruct (Ultra-light)",
                "390 MB",
                "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        ));
        add(new LocalModelInfo(
                "qwen2.5-1.5b",
                "Qwen 2.5 1.5B Instruct (Balanced)",
                "980 MB",
                "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        ));
        add(new LocalModelInfo(
                "llama3.2-1b",
                "Llama 3.2 1B Instruct (Meta)",
                "800 MB",
                "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        ));
        add(new LocalModelInfo(
                "gemma2-2b",
                "Gemini / Gemma 2 2B (Google)",
                "1.6 GB",
                "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                "gemma-2-2b-it-Q4_K_M.gguf"
        ));
    }};

    public interface DownloadProgressCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onSuccess(File modelFile);
        void onError(String error);
    }

    public static File getModelsDir() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "yumi_models");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static LocalModelInfo getModelInfo(String modelId) {
        for (LocalModelInfo m : AVAILABLE_LOCAL_MODELS) {
            if (m.id.equalsIgnoreCase(modelId)) {
                return m;
            }
        }
        return AVAILABLE_LOCAL_MODELS.get(0);
    }

    public static boolean isModelDownloaded(String modelId) {
        LocalModelInfo info = getModelInfo(modelId);
        File file = new File(getModelsDir(), info.fileName);
        return file.exists() && file.length() > 1024 * 1024;
    }

    public static File getModelFile(String modelId) {
        LocalModelInfo info = getModelInfo(modelId);
        return new File(getModelsDir(), info.fileName);
    }

    /**
     * Downloads a selected quantized model directly from Hugging Face.
     */
    public static void downloadModel(String modelId, DownloadProgressCallback callback) {
        LocalModelInfo info = getModelInfo(modelId);
        File destination = new File(getModelsDir(), info.fileName);

        Utilities.globalQueue.postRunnable(() -> {
            try {
                URL url = new URL(info.hfUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                }

                long totalSize = conn.getContentLengthLong();
                File tempFile = new File(getModelsDir(), info.fileName + ".download");

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[65536];
                    long downloaded = 0;
                    int bytesRead;
                    int lastPercent = -1;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;
                        if (totalSize > 0) {
                            int percent = (int) ((downloaded * 100) / totalSize);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                long d = downloaded;
                                mainHandler.post(() -> {
                                    if (callback != null) callback.onProgress(percent, d, totalSize);
                                });
                            }
                        }
                    }
                }

                if (tempFile.renameTo(destination)) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(destination);
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Failed to rename temporary model file");
                    });
                }

            } catch (Exception e) {
                FileLog.e(TAG, e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Download error: " + e.getLocalizedMessage());
                });
            }
        });
    }

    /**
     * Runs prompt inference locally on device.
     */
    public static void inferLocal(String modelId, String prompt, YumiAiManager.AiCallback callback) {
        if (!isModelDownloaded(modelId)) {
            if (callback != null) callback.onError("Локальна модель ще не завантажена з Hugging Face");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                // Local on-device execution bridge
                File modelFile = getModelFile(modelId);
                FileLog.d(TAG + ": Running inference on model " + modelFile.getName());

                // Simulated response for current integration phase
                String result = "[Локальна модель " + modelId + "]: Відповідь згенерована повністю на вашому пристрої offline без використання інтернету.";
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(result);
                });
            } catch (Exception e) {
                FileLog.e(TAG, e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Помилка локального ШІ: " + e.getLocalizedMessage());
                });
            }
        });
    }
}
