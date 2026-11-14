package org.yumigram.app.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ChatModel(
    val id: Long,
    val title: String,
    val username: String? = null,
    val chat_type: String = "user",
    val unread_count: Int = 0,
    val last_message_text: String? = null,
    val last_message_date: Long = 0,
    val last_message_outgoing: Boolean = false,
    val last_message_read: Boolean = false,
    val avatar_path: String? = null
)

@Serializable
data class MessageModel(
    val id: Int,
    val chat_id: Long,
    val sender_id: Long,
    val sender_name: String,
    val text: String,
    val date: Long,
    val is_outgoing: Boolean,
    val is_read: Boolean,
    val reply_to_id: Int? = null,
    val is_deleted: Boolean = false,
    val original_text: String? = null,
    val media_type: String? = null,
    val media_path: String? = null,
    val duration: Int? = null
)

@Serializable
data class AgentAction(
    val action_type: String,
    val chat_id: Long? = null,
    val text: String? = null,
    val bio: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val query: String? = null,
    val limit: Int? = null,
    val api_key: String? = null,
    val provider: String? = null,
    val strict_privacy: Boolean? = null,
    val raw_ai_response: String? = null
)

@Serializable
data class AgentResult(
    val success: Boolean,
    val message: String,
    val data: String? = null
)

@Serializable
data class AgentChatSummary(
    val id: Long,
    val title: String,
    val unread_count: Int,
    val last_message: String? = null,
    val chat_type: String = "user"
)

@Serializable
data class AgentStructuredSummary(
    val chat_title: String,
    val total_messages: Int,
    val participants: List<String> = emptyList(),
    val key_topics: List<String> = emptyList(),
    val unanswered_questions: List<String> = emptyList(),
    val action_items: List<String> = emptyList(),
    val summary_text: String,
    val provider_used: String = "Local ONNX / Offline Privacy Engine",
    val privacy_protected: Boolean = true
)

@Serializable
data class AiSettingsModel(
    val provider: String = "local",
    val gemini_api_key: String = "",
    val strict_privacy: Boolean = true
)

@Serializable
data class PluginManifestModel(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val min_core_version: String,
    val permissions: List<String>
)

@Serializable
data class PluginStateModel(
    val manifest: PluginManifestModel,
    val is_enabled: Boolean
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val status: String? = null,
    val message: String? = null,
    val name: String? = null
)

object YumigramBridge {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var isLoaded = false

    init {
        try {
            System.loadLibrary("yumigram_core")
            isLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isLoaded = false
        }
    }

    external fun nativeInit(dbPath: String, sessionPath: String): Boolean
    external fun nativeIsAuthorized(): Boolean
    external fun nativeRequestCode(phone: String): String
    external fun nativeSignIn(code: String): String
    external fun nativeCheckPassword(password: String): String
    external fun nativeFetchLiveChats(limit: Int): String
    external fun nativeFetchLiveMessages(chatId: Long, limit: Int): String
    external fun nativeSendMessage(chatId: Long, text: String): String
    external fun nativeGetChats(): String
    external fun nativeGetMessages(chatId: Long, limit: Int, offset: Int): String
    external fun nativeSaveChat(chatJson: String): Boolean
    external fun nativeMarkDeleted(chatId: Long, messageId: Int): Boolean
    external fun nativeSetGhostConfig(hideRead: Boolean, hideTyping: Boolean, hideOnline: Boolean)
    external fun nativeExecuteAgentAction(actionJson: String): String
    external fun nativeListPlugins(): String
    external fun nativeSetPluginEnabled(pluginId: String, enabled: Boolean): Boolean
    external fun nativeDownloadMedia(chatId: Long, messageId: Int, targetPath: String): String
    external fun nativeDownloadChatAvatar(chatId: Long, targetPath: String): String

    suspend fun listPlugins(): List<PluginStateModel> = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext emptyList()
        val raw = nativeListPlugins()
        try {
            json.decodeFromString<List<PluginStateModel>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        nativeSetPluginEnabled(pluginId, enabled)
    }

    suspend fun isAuthorized(): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        nativeIsAuthorized()
    }

    suspend fun requestCode(phone: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext AuthResponse(false, message = "Native library not loaded")
        val raw = nativeRequestCode(phone)
        try {
            json.decodeFromString<AuthResponse>(raw)
        } catch (_: Exception) {
            AuthResponse(false, message = raw)
        }
    }

    suspend fun signIn(code: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext AuthResponse(false, message = "Native library not loaded")
        val raw = nativeSignIn(code)
        try {
            json.decodeFromString<AuthResponse>(raw)
        } catch (_: Exception) {
            AuthResponse(false, message = raw)
        }
    }

    suspend fun checkPassword(password: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext AuthResponse(false, message = "Native library not loaded")
        val raw = nativeCheckPassword(password)
        try {
            json.decodeFromString<AuthResponse>(raw)
        } catch (_: Exception) {
            AuthResponse(false, message = raw)
        }
    }

    suspend fun fetchLiveChats(limit: Int = 30): List<ChatModel> = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext emptyList()
        val raw = nativeFetchLiveChats(limit)
        try {
            json.decodeFromString<List<ChatModel>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchLiveMessages(chatId: Long, limit: Int = 50): List<MessageModel> = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext emptyList()
        val raw = nativeFetchLiveMessages(chatId, limit)
        try {
            json.decodeFromString<List<MessageModel>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun sendMessage(chatId: Long, text: String): MessageModel? = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext null
        val raw = nativeSendMessage(chatId, text)
        try {
            json.decodeFromString<MessageModel>(raw)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getChats(): List<ChatModel> = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext emptyList()
        val raw = nativeGetChats()
        try {
            json.decodeFromString<List<ChatModel>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getMessages(chatId: Long, limit: Int = 50, offset: Int = 0): List<MessageModel> = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext emptyList()
        val raw = nativeGetMessages(chatId, limit, offset)
        try {
            json.decodeFromString<List<MessageModel>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setGhostConfig(hideRead: Boolean, hideTyping: Boolean, hideOnline: Boolean) {
        if (!isLoaded) return
        nativeSetGhostConfig(hideRead, hideTyping, hideOnline)
    }

    suspend fun executeAgent(action: AgentAction): AgentResult = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext AgentResult(false, "Native library not loaded")
        val reqJson = json.encodeToString(action)
        val raw = nativeExecuteAgentAction(reqJson)
        try {
            json.decodeFromString<AgentResult>(raw)
        } catch (e: Exception) {
            AgentResult(false, "Parse error: ${e.message}")
        }
    }

    suspend fun downloadMedia(chatId: Long, messageId: Int, targetPath: String): String = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext ""
        try {
            nativeDownloadMedia(chatId, messageId, targetPath)
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun downloadChatAvatar(chatId: Long, targetPath: String): String = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext ""
        try {
            nativeDownloadChatAvatar(chatId, targetPath)
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun listChatsForAgent(): List<AgentChatSummary> = withContext(Dispatchers.IO) {
        val res = executeAgent(AgentAction(action_type = "list_chats"))
        if (res.success && res.data != null) {
            try {
                json.decodeFromString<List<AgentChatSummary>>(res.data)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun getAiSettings(): AiSettingsModel = withContext(Dispatchers.IO) {
        val res = executeAgent(AgentAction(action_type = "get_ai_settings"))
        if (res.success && res.data != null) {
            try {
                json.decodeFromString<AiSettingsModel>(res.data)
            } catch (_: Exception) {
                AiSettingsModel()
            }
        } else {
            AiSettingsModel()
        }
    }

    suspend fun saveAiSettings(provider: String, apiKey: String, strictPrivacy: Boolean): Boolean = withContext(Dispatchers.IO) {
        val res = executeAgent(
            AgentAction(
                action_type = "set_ai_settings",
                provider = provider,
                api_key = apiKey,
                strict_privacy = strictPrivacy
            )
        )
        res.success
    }

    suspend fun callGeminiApi(apiKey: String, payloadJson: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 15000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(payloadJson.toByteArray(Charsets.UTF_8))
            }
            val respCode = conn.responseCode
            val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }
            responseText
        } catch (e: Exception) {
            """{"error":{"message":"${e.message ?: "Network error"}"}}"""
        }
    }

    suspend fun summarizeChatSmart(chatId: Long?): AgentStructuredSummary = withContext(Dispatchers.IO) {
        val settings = getAiSettings()

        // 1. If Google Gemini is configured, use it with privacy sanitization
        if (settings.provider == "gemini" && settings.gemini_api_key.isNotBlank()) {
            val prep = executeAgent(
                AgentAction(
                    action_type = "prepare_ai_summary",
                    chat_id = chatId,
                    strict_privacy = settings.strict_privacy
                )
            )
            if (prep.success && !prep.data.isNullOrBlank()) {
                val rawResponse = callGeminiApi(settings.gemini_api_key, prep.data)
                val parsed = executeAgent(
                    AgentAction(
                        action_type = "parse_ai_summary",
                        chat_id = chatId,
                        raw_ai_response = rawResponse
                    )
                )
                if (parsed.success && !parsed.data.isNullOrBlank()) {
                    try {
                        return@withContext json.decodeFromString<AgentStructuredSummary>(parsed.data)
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Local ONNX / Offline Privacy Engine fallback (100% on-device)
        val localRes = executeAgent(
            AgentAction(
                action_type = "summarize",
                chat_id = chatId,
                provider = "local_onnx",
                strict_privacy = settings.strict_privacy
            )
        )
        if (localRes.success && !localRes.data.isNullOrBlank()) {
            try {
                return@withContext json.decodeFromString<AgentStructuredSummary>(localRes.data)
            } catch (_: Exception) {}
        }

        AgentStructuredSummary(
            chat_title = "Діалог",
            total_messages = 0,
            summary_text = localRes.message
        )
    }

    suspend fun draftReply(chatId: Long, instruction: String? = null): AgentResult = withContext(Dispatchers.IO) {
        val settings = getAiSettings()
        if (settings.provider == "gemini" && settings.gemini_api_key.isNotBlank()) {
            // Contextual draft with Gemini
            val res = executeAgent(
                AgentAction(
                    action_type = "draft_reply",
                    chat_id = chatId,
                    text = instruction,
                    provider = "gemini",
                    api_key = settings.gemini_api_key
                )
            )
            return@withContext res
        }
        executeAgent(AgentAction(action_type = "draft_reply", chat_id = chatId, text = instruction))
    }

    suspend fun searchMessages(query: String): List<MessageModel> = withContext(Dispatchers.IO) {
        val res = executeAgent(AgentAction(action_type = "search_messages", query = query))
        if (res.success && res.data != null) {
            try {
                json.decodeFromString<List<MessageModel>>(res.data)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun updateProfile(firstName: String?, lastName: String?, bio: String?): AgentResult = withContext(Dispatchers.IO) {
        executeAgent(
            AgentAction(
                action_type = "update_profile",
                first_name = firstName,
                last_name = lastName,
                bio = bio
            )
        )
    }

    suspend fun sendAgentMessage(chatId: Long, text: String): AgentResult = withContext(Dispatchers.IO) {
        executeAgent(AgentAction(action_type = "send_message", chat_id = chatId, text = text))
    }
}
