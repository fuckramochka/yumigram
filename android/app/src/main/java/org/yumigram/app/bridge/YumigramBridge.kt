package org.yumigram.app.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val bio: String? = null
)

@Serializable
data class AgentResult(
    val success: Boolean,
    val message: String,
    val data: String? = null
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

    fun isNativeLoaded(): Boolean = isLoaded

    // JNI Native Declarations
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

    suspend fun isAuthorized(): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        try {
            nativeIsAuthorized()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun requestCode(phone: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext AuthResponse(false, message = "Native library not loaded")
        val raw = nativeRequestCode(phone)
        try {
            json.decodeFromString<AuthResponse>(raw)
        } catch (e: Exception) {
            AuthResponse(false, message = "Error: ${e.message} (raw: $raw)")
        }
    }

    suspend fun signIn(code: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext AuthResponse(false, message = "Native library not loaded")
        val raw = nativeSignIn(code)
        try {
            json.decodeFromString<AuthResponse>(raw)
        } catch (e: Exception) {
            AuthResponse(false, message = "Error: ${e.message} (raw: $raw)")
        }
    }

    suspend fun checkPassword(password: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext AuthResponse(false, message = "Native library not loaded")
        val raw = nativeCheckPassword(password)
        try {
            json.decodeFromString<AuthResponse>(raw)
        } catch (e: Exception) {
            AuthResponse(false, message = "Error: ${e.message} (raw: $raw)")
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
}
