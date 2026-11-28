package org.telegram.messenger.yumigram.ai

import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.function.BiConsumer

/** Runs GGUF text generation on-device and releases native model memory after an idle window. */
object YumiLocalInference {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var loadedModel: LlamaModel? = null
    private var loadedPath: String? = null
    private var idleRelease: Job? = null

    @JvmStatic
    fun infer(modelPath: String, prompt: String, callback: BiConsumer<String?, String?>) {
        infer(modelPath, prompt, "You are Yumi Copilot, an assistant inside the Yumigram Telegram client. Follow the user request and return a concise, useful answer.", callback)
    }

    @JvmStatic
    fun infer(modelPath: String, prompt: String, systemPrompt: String, callback: BiConsumer<String?, String?>) {
        scope.launch {
            try {
                val answer = mutex.withLock {
                    idleRelease?.cancel()
                    idleRelease = null
                    val model = if (loadedModel == null || !loadedModel!!.isLoaded || loadedPath != modelPath) {
                        releaseLoadedModel()
                        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                        Llama.loadModel(
                            modelPath = modelPath,
                            config = LlamaConfig(contextSize = 4096, threads = threads)
                        ).also {
                            loadedModel = it
                            loadedPath = modelPath
                        }
                    } else {
                        loadedModel!!
                    }
                    val result = Llama.complete(
                        model = model,
                        prompt = prompt,
                        systemPrompt = systemPrompt,
                        maxTokens = 512
                    )
                    scheduleIdleRelease()
                    result.text
                }
                withContext(Dispatchers.Main) { callback.accept(answer, null) }
            } catch (error: Throwable) {
                mutex.withLock { scheduleIdleRelease() }
                withContext(Dispatchers.Main) {
                    callback.accept(null, error.localizedMessage ?: "Local model inference failed")
                }
            }
        }
    }

    @JvmStatic
    fun deleteModel(modelPath: String, callback: BiConsumer<Boolean, String?>) {
        scope.launch {
            try {
                val deleted = mutex.withLock {
                    if (loadedPath == modelPath) releaseLoadedModel()
                    val modelFile = File(modelPath)
                    if (modelFile.exists() && !modelFile.delete()) return@withLock false
                    val checksumFile = File(modelPath + ".sha256")
                    !checksumFile.exists() || checksumFile.delete()
                }
                withContext(Dispatchers.Main) {
                    callback.accept(deleted, if (deleted) null else "Не удалось удалить файлы модели")
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    callback.accept(false, error.localizedMessage ?: "Не удалось выгрузить локальную модель")
                }
            }
        }
    }

    private fun releaseLoadedModel() {
        idleRelease?.cancel()
        idleRelease = null
        loadedModel?.let { Llama.releaseModel(it) }
        loadedModel = null
        loadedPath = null
    }

    private fun scheduleIdleRelease() {
        idleRelease?.cancel()
        idleRelease = scope.launch {
            delay(IDLE_RELEASE_DELAY_MS)
            mutex.withLock { releaseLoadedModel() }
        }
    }

    private const val IDLE_RELEASE_DELAY_MS = 120_000L
}
