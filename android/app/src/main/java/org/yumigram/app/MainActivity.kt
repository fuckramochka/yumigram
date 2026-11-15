package org.yumigram.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.MessageModel
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.navigation.Screen
import org.yumigram.app.ui.screens.agent.AgentScreen
import org.yumigram.app.ui.screens.auth.AuthScreen
import org.yumigram.app.ui.screens.chat.ChatScreen
import org.yumigram.app.ui.screens.chats.ChatListScreen
import org.yumigram.app.ui.screens.contacts.ContactsScreen
import org.yumigram.app.ui.screens.plugins.PluginsScreen
import org.yumigram.app.ui.screens.profile.ProfileScreen
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
    var isViewingPlugins by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

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
        refreshChats()
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

    if (isViewingPlugins) {
        PluginsScreen(
            onBack = { isViewingPlugins = false }
        )
    } else if (selectedChat != null) {
        ChatScreen(
            chat = selectedChat!!,
            messages = messages,
            indicatorStyle = indicatorStyle,
            onBack = {
                selectedChat = null
                refreshChats()
            },
            onSendMessage = { text ->
                scope.launch {
                    val sentMsg = YumigramBridge.sendMessage(selectedChat!!.id, text)
                    if (sentMsg != null) {
                        messages = listOf(sentMsg) + messages
                    }
                }
            },
            onSendRichMessage = { text, mediaType, mediaPath, duration ->
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
                    media_type = mediaType,
                    media_path = mediaPath,
                    duration = duration
                )
                messages = listOf(richMsg) + messages
                scope.launch {
                    val payload = when (mediaType) {
                        "voice" -> "🎙️ [Голосове повідомлення $duration сек]"
                        "video_note", "round_video" -> "⭕ [Відео-кружечок $duration сек]"
                        "photo" -> "🖼️ [Фотографія]"
                        "video" -> "🎥 [Відео]"
                        "audio" -> "🎵 $text"
                        "file" -> "📁 $text"
                        else -> text
                    }
                    YumigramBridge.sendMessage(selectedChat!!.id, payload)
                }
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val navItems = listOf(
                        Screen.Chats,
                        Screen.Contacts,
                        Screen.Agent,
                        Screen.Profile,
                        Screen.Settings
                    )
                    navItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = MaterialTheme.colorScheme.background
            ) {
                Crossfade(targetState = currentScreen, label = "screen_crossfade") { screen ->
                    when (screen) {
                        Screen.Chats -> ChatListScreen(
                            chats = chats,
                            indicatorStyle = indicatorStyle,
                            isRefreshing = isRefreshing,
                            onRefresh = { refreshChats() },
                            onChatClick = { chat -> selectedChat = chat }
                        )
                        Screen.Contacts -> ContactsScreen(
                            contacts = chats,
                            onContactClick = { chat -> selectedChat = chat }
                        )
                        Screen.Agent -> AgentScreen()
                        Screen.Profile -> ProfileScreen(
                            onNavigateToSettings = { currentScreen = Screen.Settings }
                        )
                        Screen.Settings -> SettingsScreen(
                            currentIndicator = indicatorStyle,
                            onIndicatorChanged = { indicatorStyle = it },
                            onOpenPlugins = { isViewingPlugins = true },
                            onLogout = onLogout
                        )
                    }
                }
            }
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
