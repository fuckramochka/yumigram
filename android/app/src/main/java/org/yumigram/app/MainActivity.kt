package org.yumigram.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.MessageModel
import org.yumigram.app.bridge.UserModel
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.navigation.Screen
import org.yumigram.app.ui.screens.auth.AuthScreen
import org.yumigram.app.ui.screens.chat.ChatScreen
import org.yumigram.app.ui.screens.chats.ChatListScreen
import org.yumigram.app.ui.screens.contacts.ContactsScreen
import org.yumigram.app.ui.screens.settings.IndicatorStyle
import org.yumigram.app.ui.screens.settings.SettingsScreen
import org.yumigram.app.ui.theme.YumigramTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("YumigramCrash", "Fatal exception in thread ${thread.name}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText(throwable.stackTraceToString())
            } catch (_: Exception) {}
        }

        try {
            // Initialize Theme Manager with persisted preferences
            org.yumigram.app.ui.theme.ThemeManager.init(this)

            // Initialize Rust core database and MTProto session in app's internal files directory
            val dbPath = "${filesDir.absolutePath}/yumigram.db"
            val sessionPath = "${filesDir.absolutePath}/yumigram_session.session"
            YumigramBridge.nativeInit(dbPath, sessionPath)
        } catch (e: Throwable) {
            android.util.Log.e("Yumigram", "nativeInit failed", e)
        }

        setContent {
            YumigramTheme {
                RootNavigationContainer()
            }
        }
    }
}

fun resolveContentUriToCacheFile(context: Context, uri: Uri, mediaType: String?): String {
    return try {
        val ext = when (mediaType) {
            "photo" -> "jpg"
            "video" -> "mp4"
            "audio" -> "mp3"
            "voice" -> "m4a"
            "video_note", "round_video" -> "mp4"
            else -> "bin"
        }
        val uploadDir = File(context.cacheDir, "media_upload").apply { if (!exists()) mkdirs() }
        val targetFile = File(uploadDir, "upload_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        targetFile.absolutePath
    } catch (e: Throwable) {
        android.util.Log.e("MainActivity", "resolveContentUriToCacheFile error", e)
        ""
    }
}

@Composable
fun RootNavigationContainer() {
    val context = LocalContext.current
    val crashFile = remember { java.io.File(context.filesDir, "last_crash.txt") }
    var crashReport by remember {
        mutableStateOf(if (crashFile.exists()) {
            try { crashFile.readText() } catch (_: Exception) { null }
        } else null)
    }

    var isAuthorized by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    if (crashReport != null) {
        CrashScreen(
            report = crashReport!!,
            onDismiss = {
                try { crashFile.delete() } catch (_: Exception) {}
                crashReport = null
            }
        )
        return
    }

    LaunchedEffect(Unit) {
        try {
            val authorized = YumigramBridge.isAuthorized()
            isAuthorized = authorized
        } catch (e: Throwable) {
            android.util.Log.e("Yumigram", "isAuthorized check failed", e)
            isAuthorized = false
        }
    }

    when (isAuthorized) {
        null -> {
            // Splash / checking auth state
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Yumigram",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
        false -> {
            AuthScreen(
                onAuthSuccess = {
                    isAuthorized = true
                }
            )
        }
        true -> {
            MainAppContainer(
                onLogout = {
                    isAuthorized = false
                }
            )
        }
    }
}

@Composable
fun MainAppContainer(
    onLogout: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chats) }
    var selectedChat by remember { mutableStateOf<ChatModel?>(null) }
    var indicatorStyle by remember { mutableStateOf(IndicatorStyle.DOTS) }

    var chats by remember { mutableStateOf<List<ChatModel>>(emptyList()) }
    var messages by remember { mutableStateOf<List<MessageModel>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<UserModel?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Load cached chats from local SQLite and fetch live from Telegram
    fun refreshChats() {
        isRefreshing = true
        scope.launch {
            try {
                // First load from local SQLite cache
                val cached = YumigramBridge.getChats()
                if (cached.isNotEmpty()) {
                    chats = cached
                }
                // Then fetch live from Telegram MTProto
                val live = YumigramBridge.fetchLiveChats(30)
                if (live.isNotEmpty()) {
                    chats = live
                }
            } catch (e: Throwable) {
                android.util.Log.e("Yumigram", "refreshChats error", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                currentUser = YumigramBridge.getMe()
            } catch (e: Throwable) {
                android.util.Log.e("Yumigram", "getMe error", e)
            }
        }
        refreshChats()
    }

    // Real-time event polling loop from Rust UpdatesEngine
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(400)
            try {
                val events = YumigramBridge.pollEvents()
                if (events.isNotEmpty()) {
                    for (event in events) {
                        when (event.type) {
                            "new_message" -> {
                                val newMsg = event.message
                                if (newMsg != null) {
                                    if (selectedChat?.id == event.chat_id) {
                                        if (messages.none { it.id == newMsg.id }) {
                                            messages = listOf(newMsg) + messages
                                        }
                                    }
                                    var chatFound = false
                                    chats = chats.map { chat ->
                                        if (chat.id == event.chat_id) {
                                            chatFound = true
                                            val isCurrentChatOpen = (selectedChat?.id == event.chat_id)
                                            val newUnread = if (isCurrentChatOpen || newMsg.is_outgoing) {
                                                chat.unread_count
                                            } else {
                                                chat.unread_count + 1
                                            }
                                            chat.copy(
                                                last_message_text = newMsg.text.ifBlank {
                                                    when (newMsg.media_type) {
                                                        "photo" -> "📷 Фотографія"
                                                        "voice" -> "🎤 Голосове повідомлення"
                                                        "video_note", "round_video" -> "📹 Відеоповідомлення"
                                                        "video" -> "🎥 Відео"
                                                        "audio", "music" -> "🎵 Музика"
                                                        "file" -> "📁 Файл"
                                                        else -> newMsg.text
                                                    }
                                                },
                                                last_message_date = newMsg.date,
                                                last_message_outgoing = newMsg.is_outgoing,
                                                unread_count = newUnread
                                            )
                                        } else {
                                            chat
                                        }
                                    }
                                    if (!chatFound) {
                                        refreshChats()
                                    }
                                }
                            }
                            "delete_message" -> {
                                val targetId = event.message_id ?: event.msg_id ?: event.message?.id
                                if (targetId != null) {
                                    if (selectedChat?.id == event.chat_id || event.chat_id == 0L) {
                                        messages = messages.map { msg ->
                                            if (msg.id == targetId) {
                                                msg.copy(is_deleted = true)
                                            } else {
                                                msg
                                            }
                                        }
                                    }
                                }
                            }
                            "edit_message" -> {
                                val targetId = event.message_id ?: event.msg_id ?: event.message?.id
                                val newText = event.text ?: event.message?.text
                                if (targetId != null && newText != null) {
                                    if (selectedChat?.id == event.chat_id || event.chat_id == 0L) {
                                        messages = messages.map { msg ->
                                            if (msg.id == targetId) {
                                                msg.copy(text = newText)
                                            } else {
                                                msg
                                            }
                                        }
                                    }
                                    chats = chats.map { chat ->
                                        if (chat.id == event.chat_id) {
                                            chat.copy(last_message_text = newText)
                                        } else {
                                            chat
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "pollEvents error", e)
            }
        }
    }

    // When chat is selected, load messages
    LaunchedEffect(selectedChat?.id) {
        selectedChat?.let { chat ->
            try {
                // Load from SQLite cache
                val cachedMsgs = YumigramBridge.getMessages(chat.id, 50, 0)
                messages = cachedMsgs

                // Concurrently fetch live messages from MTProto
                val liveMsgs = YumigramBridge.fetchLiveMessages(chat.id, 50)
                if (liveMsgs.isNotEmpty()) {
                    messages = liveMsgs
                }
            } catch (e: Throwable) {
                android.util.Log.e("Yumigram", "loadMessages error", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Crossfade(targetState = currentScreen, label = "screen_crossfade") { screen ->
                when (screen) {
                    Screen.Chats -> ChatListScreen(
                        chats = chats,
                        indicatorStyle = indicatorStyle,
                        isRefreshing = isRefreshing,
                        currentUserId = currentUser?.id,
                        onRefresh = { refreshChats() },
                        onChatClick = { chat -> selectedChat = chat }
                    )
                    Screen.Contacts -> ContactsScreen(
                        contacts = chats,
                        onContactClick = { chat -> selectedChat = chat }
                    )
                    Screen.Settings -> SettingsScreen(
                        currentIndicator = indicatorStyle,
                        onIndicatorChanged = { indicatorStyle = it },
                        onLogout = onLogout
                    )
                }
            }
        }

        // 1. Floating Island Navigation Bar (Only visible when chat is not open)
        if (selectedChat == null) {
            FloatingIslandNavBar(
                currentScreen = currentScreen,
                onSelectScreen = { currentScreen = it },
                currentUser = currentUser,
                onSearchClick = {
                    currentScreen = Screen.Chats
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, horizontal = 16.dp)
            )
        }

        if (selectedChat != null) {
            ChatScreen(
                chat = selectedChat!!,
                messages = messages,
                indicatorStyle = indicatorStyle,
                onBack = {
                    selectedChat = null
                    refreshChats()
                },
                onSendMessage = { text, replyToId ->
                    scope.launch {
                        val sentMsg = YumigramBridge.sendMessage(selectedChat!!.id, text, replyToId)
                        if (sentMsg != null) {
                            messages = listOf(sentMsg) + messages
                        }
                    }
                },
                onSendRichMessage = { text, mediaType, mediaPath, duration, replyToId ->
                    val localId = (System.currentTimeMillis() % 1000000).toInt()
                    val richMsg = MessageModel(
                        id = localId,
                        chat_id = selectedChat!!.id,
                        sender_id = 0,
                        sender_name = "Я",
                        text = text,
                        date = System.currentTimeMillis() / 1000,
                        is_outgoing = true,
                        is_read = false,
                        reply_to_id = replyToId,
                        media_type = mediaType,
                        media_path = mediaPath,
                        duration = duration
                    )
                    messages = listOf(richMsg) + messages
                    scope.launch {
                        try {
                            val currentChatId = selectedChat?.id ?: return@launch
                            val realPath = if (mediaPath != null && mediaPath.startsWith("content://")) {
                                resolveContentUriToCacheFile(context, Uri.parse(mediaPath), mediaType)
                            } else {
                                mediaPath ?: ""
                            }

                            val sentMsg = if (mediaType != null && realPath.isNotBlank()) {
                                val cleanType = when (mediaType) {
                                    "round_video" -> "video_note"
                                    else -> mediaType
                                }
                                val captionText = when (mediaType) {
                                    "photo", "video", "file", "audio" -> {
                                        if (text.startsWith("🖼️") || text.startsWith("🎥") || text.startsWith("📁")) "" else text
                                    }
                                    else -> ""
                                }
                                YumigramBridge.sendMedia(
                                    chatId = currentChatId,
                                    filePath = realPath,
                                    mediaType = cleanType,
                                    caption = captionText,
                                    duration = duration ?: 0,
                                    replyToId = replyToId
                                )
                            } else {
                                YumigramBridge.sendMessage(currentChatId, text, replyToId)
                            }

                            if (sentMsg != null) {
                                messages = messages.map { if (it.id == localId) sentMsg else it }
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("MainActivity", "Failed to send real media", e)
                        }
                    }
                },
                onSendSticker = { sticker, replyToId ->
                    scope.launch {
                        try {
                            val currentChatId = selectedChat?.id ?: return@launch
                            val sentMsg = YumigramBridge.sendSticker(currentChatId, sticker, replyToId)
                            if (sentMsg != null) {
                                messages = listOf(sentMsg) + messages
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("MainActivity", "Failed to send real sticker", e)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun CrashScreen(
    report: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Виявлено помилку збою",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Yumigram зафіксував звіт про останній інцидент:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 280.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(12.dp)) {
                    item {
                        Text(
                            text = report,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Yumigram Crash", report)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Звіт скопійовано", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Скопіювати")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Продовжити")
                }
            }
        }
    }
}

/**
 * 1. Amegram / exteraGram Floating Island Navigation Bar
 * Floating horizontal capsule with dark acrylic glass, miniature user avatar,
 * soft tab pill, section icons, and separate circular search FAB.
 */
@Composable
fun FloatingIslandNavBar(
    currentScreen: Screen,
    onSelectScreen: (Screen) -> Unit,
    currentUser: UserModel?,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Floating horizontal capsule
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF1E1E24).copy(alpha = 0.94f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Miniature user avatar (size = 36.dp)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C36))
                        .clickable { onSelectScreen(Screen.Settings) },
                    contentAlignment = Alignment.Center
                ) {
                    val initial = currentUser?.first_name?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Nav items: Chats, Contacts, Settings
                val navItems = listOf(Screen.Chats, Screen.Contacts, Screen.Settings)
                navItems.forEach { screen ->
                    val isSelected = currentScreen == screen
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF9333EA),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { onSelectScreen(screen) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = screen.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { onSelectScreen(screen) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Circular Floating Action Button (FAB) (size = 52.dp, accent color)
        Surface(
            shape = CircleShape,
            color = Color(0xFF9333EA),
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable { onSearchClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Пошук",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
