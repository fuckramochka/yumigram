package org.yumigram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import org.yumigram.app.ui.screens.profile.ProfileScreen
import org.yumigram.app.ui.screens.settings.IndicatorStyle
import org.yumigram.app.ui.screens.settings.SettingsScreen
import org.yumigram.app.ui.theme.YumigramTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Rust core database in app's internal files directory
        val dbPath = "${filesDir.absolutePath}/yumigram.db"
        YumigramBridge.nativeInit(dbPath)

        setContent {
            YumigramTheme {
                RootNavigationContainer()
            }
        }
    }
}

@Composable
fun RootNavigationContainer() {
    var isAuthorized by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val authorized = YumigramBridge.isAuthorized()
        isAuthorized = authorized
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

    val scope = rememberCoroutineScope()

    // Load cached chats from local SQLite and fetch live from Telegram
    fun refreshChats() {
        isRefreshing = true
        scope.launch {
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
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshChats()
    }

    // When chat is selected, load messages
    LaunchedEffect(selectedChat?.id) {
        selectedChat?.let { chat ->
            // Load from SQLite cache
            val cachedMsgs = YumigramBridge.getMessages(chat.id, 50, 0)
            messages = cachedMsgs

            // Concurrently fetch live messages from MTProto
            val liveMsgs = YumigramBridge.fetchLiveMessages(chat.id, 50)
            if (liveMsgs.isNotEmpty()) {
                messages = liveMsgs
            }
        }
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
            onSendMessage = { text ->
                scope.launch {
                    val sentMsg = YumigramBridge.sendMessage(selectedChat!!.id, text)
                    if (sentMsg != null) {
                        messages = listOf(sentMsg) + messages
                    }
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
                            onLogout = onLogout
                        )
                    }
                }
            }
        }
    }
}
