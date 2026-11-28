package org.telegram.messenger.yumigram.ai;

import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Process;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue2;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local on-device AI Manager for Yumigram (Юмиграм).
 * Downloads and manages lightweight GGUF models directly from Hugging Face.
 */
public class YumiLocalAiManager {

    private static final String TAG = "YumiLocalAiManager";
    private static final long MAX_MODEL_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final long DISK_SPACE_RESERVE_BYTES = 128L * 1024L * 1024L;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final DispatchQueue2 modelDownloadQueue = new DispatchQueue2("yumiModelDownload");
    private static final ConcurrentHashMap<String, Boolean> activeDownloads = new ConcurrentHashMap<>();

    public static class LocalModelInfo {
        public final String id;
        public final String name;
        public final String size;
        public final String hfUrl;
        public final String fileName;
        public final long expectedBytes;
        public final String sha256;

        public LocalModelInfo(String id, String name, String size, String hfUrl, String fileName, long expectedBytes, String sha256) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.hfUrl = hfUrl;
            this.fileName = fileName;
            this.expectedBytes = expectedBytes;
            this.sha256 = sha256;
        }
    }

    public static final List<LocalModelInfo> AVAILABLE_LOCAL_MODELS = new ArrayList<LocalModelInfo>() {{
        add(new LocalModelInfo(
                "qwen2.5-0.5b",
                "Qwen 2.5 0.5B Instruct (Ultra-light)",
                "470 MiB",
                "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                491400032L,
                "0663b826e09042345d6e2069d9655cd3ae84812d8f39a5894bed7b24fc0e86cc"
        ));
        add(new LocalModelInfo(
                "qwen2.5-1.5b",
                "Qwen 2.5 1.5B Instruct (Balanced)",
                "1.1 GiB",
                "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                1117320736L,
                "6ca5463cf24c16cd56d7ad7461524d813b07b3f29889b2fbdbb8286a7e97a14a"
        ));
        add(new LocalModelInfo(
                "llama3.2-1b",
                "Llama 3.2 1B Instruct (Meta)",
                "770 MiB",
                "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/067b946cf014b7c697f3654f621d577a3e3afd1c/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                807694464L,
                "7314cd624de8068beee86215e529a23665ff09e458977e32f30b8149764e7be1"
        ));
        add(new LocalModelInfo(
                "gemma2-2b",
                "Gemma 2 2B Instruct (Google)",
                "1.6 GiB",
                "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/855f67caed130e1befc571b52bd181be2e858883/gemma-2-2b-it-Q4_K_M.gguf",
                "gemma-2-2b-it-Q4_K_M.gguf",
                1708582752L,
                "90f9c2316393fb452b47988ffa7a411f0891e2c1a7178ae868ac4f70f96f7c8d"
        ));
    }};

    public interface DownloadProgressCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onSuccess(File modelFile);
        void onError(String error);
    }

    public interface DeleteModelCallback {
        void onSuccess();
        void onError(String error);
    }

    public static File getModelsDir() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "yumi_models");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** The bundled llama.cpp AAR currently ships native code for arm64-v8a only. */
    public static boolean isLocalInferenceSupported() {
        if (Build.VERSION.SDK_INT < 23 || !Process.is64Bit()) return false;
        return Build.SUPPORTED_64_BIT_ABIS.length > 0 && "arm64-v8a".equals(Build.SUPPORTED_64_BIT_ABIS[0]);
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
        if (!file.isFile() || file.length() != info.expectedBytes) return false;
        try (FileInputStream input = new FileInputStream(file)) {
            if (input.read() != 'G' || input.read() != 'G' || input.read() != 'U' || input.read() != 'F') return false;
            File checksumFile = new File(file.getAbsolutePath() + ".sha256");
            if (!checksumFile.isFile()) return false;
            byte[] checksum = new byte[64];
            try (FileInputStream checksumInput = new FileInputStream(checksumFile)) {
                int offset = 0;
                while (offset < checksum.length) {
                    int count = checksumInput.read(checksum, offset, checksum.length - offset);
                    if (count < 0) break;
                    offset += count;
                }
                return offset == checksum.length && info.sha256.equals(new String(checksum, java.nio.charset.StandardCharsets.US_ASCII));
            }
        } catch (Exception e) {
            FileLog.e(TAG, e);
            return false;
        }
    }

    public static File getModelFile(String modelId) {
        LocalModelInfo info = getModelInfo(modelId);
        return new File(getModelsDir(), info.fileName);
    }

    public static void deleteModel(String modelId, DeleteModelCallback callback) {
        File modelFile = getModelFile(modelId);
        try {
            YumiLocalInference.deleteModel(modelFile.getAbsolutePath(), (deleted, error) -> {
                if (callback == null) return;
                if (!Boolean.TRUE.equals(deleted)) callback.onError(error == null ? "Не удалось удалить локальную модель." : error);
                else callback.onSuccess();
            });
        } catch (Throwable e) {
            FileLog.e(TAG, e);
            if (callback != null) mainHandler.post(() -> callback.onError("Не удалось выгрузить модель: " + e.getLocalizedMessage()));
        }
    }

    /**
     * Downloads a selected quantized model directly from Hugging Face.
     */
    public static void downloadModel(String modelId, DownloadProgressCallback callback) {
        if (!isLocalInferenceSupported()) {
            if (callback != null) mainHandler.post(() -> callback.onError("Локальная модель работает только в 64-битной arm64-сборке Yumigram."));
            return;
        }
        LocalModelInfo info = getModelInfo(modelId);
        File destination = new File(getModelsDir(), info.fileName);

        modelDownloadQueue.postRunnable(() -> {
            if (activeDownloads.putIfAbsent(info.id, true) != null) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Эта модель уже загружается.");
                });
                return;
            }
            HttpURLConnection conn = null;
            File tempFile = new File(getModelsDir(), info.fileName + ".download");
            File checksumTemp = new File(getModelsDir(), info.fileName + ".sha256.download");
            boolean downloadedCompletely = false;
            try {
                URL url = new URL(info.hfUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "Yumigram-Android/1.0");
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("Hugging Face вернул HTTP " + code);
                }

                long totalSize = conn.getContentLengthLong();
                if (totalSize != info.expectedBytes) {
                    throw new IllegalStateException("Размер файла модели не совпадает с закреплённой версией");
                }
                if (totalSize > MAX_MODEL_BYTES) {
                    throw new IllegalStateException("Файл модели превышает ограничение 8 ГБ");
                }
                long neededSpace = totalSize > 0 ? totalSize : DISK_SPACE_RESERVE_BYTES;
                if (getModelsDir().getUsableSpace() < neededSpace + DISK_SPACE_RESERVE_BYTES) {
                    throw new IllegalStateException("Недостаточно свободного места для модели");
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[65536];
                    long downloaded = 0;
                    int bytesRead;
                    int lastPercent = -1;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        digest.update(buffer, 0, bytesRead);
                        downloaded += bytesRead;
                        if (downloaded > MAX_MODEL_BYTES || (totalSize > 0 && downloaded > totalSize)) {
                            throw new IllegalStateException("Размер загруженной модели не совпадает с ответом сервера");
                        }
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
                    out.getFD().sync();
                    if (downloaded <= 1024L * 1024L || (totalSize > 0 && downloaded != totalSize)) {
                        throw new IllegalStateException("Загрузка модели завершилась не полностью");
                    }
                }

                String actualSha256 = toHex(digest.digest());
                if (!info.sha256.equals(actualSha256)) {
                    throw new IllegalStateException("SHA-256 модели не совпал. Загрузка удалена для защиты устройства.");
                }

                try (FileOutputStream checksumOutput = new FileOutputStream(checksumTemp)) {
                    checksumOutput.write(info.sha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    checksumOutput.getFD().sync();
                }
                if (destination.exists() && !destination.delete()) {
                    throw new IllegalStateException("Не удалось заменить повреждённый файл модели");
                }
                if (!tempFile.renameTo(destination)) {
                    throw new IllegalStateException("Не удалось сохранить скачанную модель");
                }
                File checksumDestination = new File(destination.getAbsolutePath() + ".sha256");
                if (checksumDestination.exists() && !checksumDestination.delete()) {
                    destination.delete();
                    throw new IllegalStateException("Не удалось обновить проверку целостности модели");
                }
                if (!checksumTemp.renameTo(checksumDestination)) {
                    destination.delete();
                    throw new IllegalStateException("Не удалось сохранить проверку целостности модели");
                }
                downloadedCompletely = true;
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(destination);
                });
            } catch (Exception e) {
                FileLog.e(TAG, e);
                String error = e.getLocalizedMessage() == null ? "неизвестная ошибка" : e.getLocalizedMessage();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Не удалось загрузить модель: " + error);
                });
            } finally {
                if (conn != null) conn.disconnect();
                if (checksumTemp.exists()) checksumTemp.delete();
                if (!downloadedCompletely && tempFile.exists() && !tempFile.delete()) {
                    FileLog.e(TAG + ": failed to remove incomplete model download " + tempFile.getName());
                }
                activeDownloads.remove(info.id);
            }
        });
    }

    /**
     * Runs prompt inference locally on device.
     */
    public static void inferLocal(String modelId, String prompt, YumiAiManager.AiCallback callback) {
        inferLocal(modelId, prompt, "You are Yumi Copilot, an assistant inside the Yumigram Telegram client. Follow the user request and return a concise, useful answer.", callback);
    }

    public static void inferLocal(String modelId, String prompt, String systemPrompt, YumiAiManager.AiCallback callback) {
        if (!isLocalInferenceSupported()) {
            if (callback != null) mainHandler.post(() -> callback.onError("Локальная модель недоступна в этой ABI-сборке. Используй облачный режим или установи arm64-вариант."));
            return;
        }
        if (!isModelDownloaded(modelId)) {
            if (callback != null) callback.onError("Локальна модель ще не завантажена з Hugging Face");
            return;
        }
        File modelFile = getModelFile(modelId);
        FileLog.d(TAG + ": Running on-device inference with " + modelFile.getName());
        try {
            YumiLocalInference.infer(modelFile.getAbsolutePath(), prompt, systemPrompt, (result, error) -> {
                if (callback == null) return;
                if (error != null) callback.onError("Помилка локального ШІ: " + error);
                else callback.onSuccess(result == null ? "" : result.trim());
            });
        } catch (Throwable e) {
            FileLog.e(TAG, e);
            if (callback != null) mainHandler.post(() -> callback.onError("Не вдалося запустити локальну модель: " + e.getLocalizedMessage()));
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }
}
