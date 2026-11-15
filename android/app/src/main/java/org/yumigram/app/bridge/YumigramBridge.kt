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

/// Mirror of core GhostConfig (Rust): {"hide_read": bool, "hide_typing": bool, "hide_online": bool}
@Serializable
data class GhostConfig(
    val hide_read: Boolean = false,
    val hide_typing: Boolean = false,
    val hide_online: Boolean = false
)

/// Mirror of core ActionRequest (Rust): only real MTProto/SQLite-backed actions.
@Serializable
data class ActionRequest(
    val action_type: String,
    val chat_id: Long? = null,
    val text: String? = null,
    val bio: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val query: String? = null,
    val limit: Int? = null
)

/// Mirror of core ActionResult (Rust).
@Serializable
data class ActionResult(
    val success: Boolean,
    val message: String,
    val data: String? = null
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val status: String? = null,
    val message: String? = null,
    val name: String? = null,
    val recaptcha_required: Boolean = false,
    val sitekey: String? = null
)

@Serializable
data class UserModel(
    val id: Long,
    val first_name: String = "",
    val username: String? = null
)

@Serializable
data class StickerModel(
    val document_id: Long,
    val access_hash: Long,
    val file_reference_hex: String,
    val emoji: String,
    val mime_type: String,
    val width: Int = 512,
    val height: Int = 512,
    val thumb_path: String? = null
)

@Serializable
data class UpdateEvent(
    val type: String, // "new_message", "edit_message", "delete_message"
    val chat_id: Long,
    val message: MessageModel? = null,
    val message_id: Int? = null,
    val msg_id: Int? = null,
    val text: String? = null
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
    external fun nativeSetApiCredentials(apiId: Int, apiHash: String): Boolean
    external fun nativeFetchLiveChats(limit: Int): String
    external fun nativeFetchLiveMessages(chatId: Long, limit: Int): String
    external fun nativeSendMessage(chatId: Long, text: String, replyToId: Int): String
    external fun nativeSendMedia(chatId: Long, filePath: String, mediaType: String, caption: String, duration: Int, replyToId: Int): String
    external fun nativeForwardMessages(fromChatId: Long, toChatId: Long, messageIdsJson: String): String
    external fun nativeGetStickers(cacheDir: String): String
    external fun nativeSendSticker(chatId: Long, docId: Long, accessHash: Long, fileRefHex: String, replyToId: Int): String
    external fun nativeSendReaction(chatId: Long, messageId: Int, emoji: String): Boolean
    external fun nativePinMessage(chatId: Long, messageId: Int, unpin: Boolean): Boolean
    external fun nativeGetChats(): String
    external fun nativeGetMessages(chatId: Long, limit: Int, offset: Int): String
    external fun nativeSaveChat(chatJson: String): Boolean
    external fun nativeMarkDeleted(chatId: Long, messageId: Int): Boolean
    external fun nativeSetGhostConfig(hideRead: Boolean, hideTyping: Boolean, hideOnline: Boolean)
    external fun nativeGetGhostConfig(): String
    external fun nativeExecuteAction(actionJson: String): String
    external fun nativeDownloadMedia(chatId: Long, messageId: Int, targetPath: String): String
    external fun nativeDownloadChatAvatar(chatId: Long, targetPath: String): String
    external fun nativeGetMe(): String
    external fun nativePollEvents(): String

    suspend fun isAuthorized(): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        nativeIsAuthorized()
    }

    suspend fun setApiCredentials(apiId: Int, apiHash: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        nativeSetApiCredentials(apiId, apiHash)
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

    suspend fun sendMessage(chatId: Long, text: String, replyToId: Int? = null): MessageModel? = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext null
        val raw = nativeSendMessage(chatId, text, replyToId ?: 0)
        try {
            json.decodeFromString<MessageModel>(raw)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendMedia(
        chatId: Long,
        filePath: String,
        mediaType: String,
        caption: String = "",
        duration: Int = 0,
        replyToId: Int? = null
    ): MessageModel? = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext null
        try {
            val raw = nativeSendMedia(chatId, filePath, mediaType, caption, duration, replyToId ?: 0)
            json.decodeFromString<MessageModel>(raw)
        } catch (e: Exception) {
            android.util.Log.e("YumigramBridge", "sendMedia error", e)
            null
        }
    }

    suspend fun forwardMessages(fromChatId: Long, toChatId: Long, messageIds: List<Int>): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        try {
            val idsJson = json.encodeToString(messageIds)
            val raw = nativeForwardMessages(fromChatId, toChatId, idsJson)
            val list = json.decodeFromString<List<MessageModel>>(raw)
            list.isNotEmpty()
        } catch (e: Exception) {
            android.util.Log.e("YumigramBridge", "forwardMessages error", e)
            false
        }
    }

    suspend fun getStickers(cacheDir: String): List<StickerModel> = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext emptyList()
        try {
            val raw = nativeGetStickers(cacheDir)
            json.decodeFromString<List<StickerModel>>(raw)
        } catch (e: Exception) {
            android.util.Log.e("YumigramBridge", "getStickers error", e)
            emptyList()
        }
    }

    suspend fun sendSticker(
        chatId: Long,
        sticker: StickerModel,
        replyToId: Int? = null
    ): MessageModel? = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext null
        try {
            val raw = nativeSendSticker(
                chatId = chatId,
                docId = sticker.document_id,
                accessHash = sticker.access_hash,
                fileRefHex = sticker.file_reference_hex,
                replyToId = replyToId ?: 0
            )
            json.decodeFromString<MessageModel>(raw)
        } catch (e: Exception) {
            android.util.Log.e("YumigramBridge", "sendSticker error", e)
            null
        }
    }

    suspend fun sendReaction(chatId: Long, messageId: Int, emoji: String?): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        try {
            nativeSendReaction(chatId, messageId, emoji ?: "")
        } catch (e: Exception) {
            android.util.Log.e("YumigramBridge", "sendReaction error", e)
            false
        }
    }

    suspend fun pinMessage(chatId: Long, messageId: Int, unpin: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        try {
            nativePinMessage(chatId, messageId, unpin)
        } catch (e: Exception) {
            android.util.Log.e("YumigramBridge", "pinMessage error", e)
            false
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

    suspend fun getGhostConfig(): GhostConfig = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext GhostConfig()
        try {
            json.decodeFromString<GhostConfig>(nativeGetGhostConfig())
        } catch (_: Exception) {
            GhostConfig()
        }
    }

    suspend fun executeAction(action: ActionRequest): ActionResult = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext ActionResult(false, "Native library not loaded")
        val reqJson = json.encodeToString(action)
        val raw = nativeExecuteAction(reqJson)
        try {
            json.decodeFromString<ActionResult>(raw)
        } catch (e: Exception) {
            ActionResult(false, "Parse error: ${e.message}")
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

    suspend fun searchMessages(query: String): List<MessageModel> = withContext(Dispatchers.IO) {
        val res = executeAction(ActionRequest(action_type = "search_messages", query = query))
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

    suspend fun updateProfile(firstName: String?, lastName: String?, bio: String?): ActionResult = withContext(Dispatchers.IO) {
        executeAction(
            ActionRequest(
                action_type = "update_profile",
                first_name = firstName,
                last_name = lastName,
                bio = bio
            )
        )
    }

    suspend fun getMe(): UserModel? = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext null
        val raw = nativeGetMe()
        if (raw.isBlank() || raw == "{}") return@withContext null
        try {
            json.decodeFromString<UserModel>(raw)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pollEvents(): List<UpdateEvent> = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext emptyList()
        val raw = nativePollEvents()
        if (raw.isBlank() || raw == "[]") return@withContext emptyList()
        try {
            json.decodeFromString<List<UpdateEvent>>(raw)
        } catch (e: Exception) {
            android.util.Log.e("YumigramBridge", "pollEvents error: ${e.message}, raw: $raw")
            emptyList()
        }
    }
}
