package org.yumigram.app.ui.screens.chats

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.screens.settings.IndicatorStyle
import org.yumigram.app.ui.theme.getMonetAvatarPalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChatFilter(val title: String) {
    ALL("Усі чати"),
    PERSONAL("Особисті"),
    GROUPS("Групи"),
    CHANNELS("Канали"),
    BOTS("Боти"),
    UNREAD("Непрочитані")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatModel>,
    indicatorStyle: IndicatorStyle,
    isRefreshing: Boolean = false,
    currentUserId: Long? = null,
    onRefresh: () -> Unit,
    onChatClick: (ChatModel) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var myUserId by remember(currentUserId) { mutableStateOf(currentUserId) }

    LaunchedEffect(Unit) {
        if (myUserId == null) {
            myUserId = YumigramBridge.getMe()?.id
        }
    }

    val filteredChats = remember(searchQuery, selectedFilter, chats) {
        val list = if (searchQuery.isBlank()) chats else {
            chats.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        (it.last_message_text?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        when (selectedFilter) {
            ChatFilter.ALL -> list
            ChatFilter.PERSONAL -> list.filter {
                (it.chat_type == "user" || it.chat_type == "dialog") &&
                        (it.username?.endsWith("bot", ignoreCase = true) != true) &&
                        it.chat_type != "bot"
            }
            ChatFilter.GROUPS -> list.filter { it.chat_type == "group" || it.chat_type == "supergroup" }
            ChatFilter.CHANNELS -> list.filter { it.chat_type == "channel" }
            ChatFilter.BOTS -> list.filter { it.chat_type == "bot" || it.username?.endsWith("bot", ignoreCase = true) == true }
            ChatFilter.UNREAD -> list.filter { it.unread_count > 0 }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0C0C10),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C10))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Повідомлення",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Refresh button
                        FilledTonalIconButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing,
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF1E1E26),
                                contentColor = Color(0xFFA855F7)
                            )
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFA855F7)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Оновити чати",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Ghost Mode Badge (Amegram purple pill)
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF9333EA).copy(alpha = 0.25f),
                            border = BorderStroke(0.5.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = "Ghost Mode",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFFA855F7)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ghost",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // The Pill Search Bar (Amegram dark acrylic glass, CircleShape)
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF18181F),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Пошук",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Пошук чатів або повідомлень...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFFA855F7),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Очистити",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Chat Folders / Tabs: Elegant dark capsules (Zero toxic orange!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF9333EA) else Color(0xFF18181F),
                            border = BorderStroke(
                                0.5.dp,
                                if (isSelected) Color(0xFFA855F7) else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .clickable { selectedFilter = filter }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = filter.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (filteredChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF18181F),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = Color(0xFFA855F7)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Нічого не знайдено" else "Немає активних чатів",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Оновіть або почніть нову бесіду",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Amegram dark graphite cards with bottom padding for Floating Island NavBar
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedFilter == ChatFilter.ALL && searchQuery.isBlank()) {
                    item {
                        SavedMessagesCard(
                            onClick = {
                                scope.launch {
                                    val realId = myUserId ?: currentUserId ?: YumigramBridge.getMe()?.id ?: 0L
                                    val savedChat = chats.find {
                                        (realId != 0L && it.id == realId) ||
                                                it.title == "Збережене" ||
                                                it.title == "Saved Messages"
                                    }?.copy(title = "Збережене")
                                        ?: ChatModel(
                                            id = realId,
                                            title = "Збережене",
                                            username = null,
                                            chat_type = "user",
                                            unread_count = 0,
                                            last_message_text = "Хмарне сховище ваших повідомлень",
                                            last_message_date = System.currentTimeMillis() / 1000,
                                            avatar_path = null
                                        )
                                    onChatClick(savedChat)
                                }
                            }
                        )
                    }
                }

                items(filteredChats, key = { it.id }) { chat ->
                    ChatCardItem(
                        chat = chat,
                        indicatorStyle = indicatorStyle,
                        onClick = { onChatClick(chat) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatCardItem(
    chat: ChatModel,
    indicatorStyle: IndicatorStyle,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // Cache directory for downloaded avatars
    val avatarsDir = remember { File(context.cacheDir, "avatars").apply { if (!exists()) mkdirs() } }
    val targetFile = remember(chat.id) { File(avatarsDir, "chat_${chat.id}.jpg") }

    var avatarFile by remember(chat.id, chat.avatar_path) {
        mutableStateOf(
            if (!chat.avatar_path.isNullOrEmpty() && File(chat.avatar_path).exists() && File(chat.avatar_path).length() > 0) {
                File(chat.avatar_path)
            } else if (targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else null
        )
    }

    LaunchedEffect(chat.id) {
        if (avatarFile == null) {
            try {
                val downloaded = YumigramBridge.downloadChatAvatar(chat.id, targetFile.absolutePath)
                if (downloaded.isNotEmpty() && targetFile.exists() && targetFile.length() > 0) {
                    avatarFile = targetFile
                }
            } catch (e: Throwable) {
                android.util.Log.e("YumigramAvatar", "download avatar error for chat ${chat.id}", e)
            }
        }
    }

    // STRICT ONLINE CHECK: Only real direct users (NOT channels, NOT groups, NOT bots)
    val isBot = chat.chat_type == "bot" || chat.username?.endsWith("bot", ignoreCase = true) == true
    val isChannelOrGroup = chat.chat_type == "channel" || chat.chat_type == "group" || chat.chat_type == "supergroup"
    val isRealDirectUser = (chat.chat_type == "user" || chat.chat_type == "dialog") && !isBot && !isChannelOrGroup

    // Pulsating online indicator animation ONLY for real direct users
    val isUserOnline = isRealDirectUser && (chat.last_message_date > 0 && (System.currentTimeMillis() / 1000 - chat.last_message_date) < 600)

    val infiniteTransition = rememberInfiniteTransition(label = "online_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Amegram dark glass card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF16161C),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Box with STRICT real-user online indicator
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                if (avatarFile != null && avatarFile!!.exists() && avatarFile!!.length() > 0) {
                    AsyncImage(
                        model = avatarFile,
                        contentDescription = chat.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    // Generative Pastel Monet Avatar with Crisp White Letter
                    val monetPalette = remember(chat.id) { getMonetAvatarPalette(chat.id) }
                    val initial = chat.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Brush.linearGradient(monetPalette.gradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Green dot appears ONLY for verified real direct users! CATEGORICALLY absent on channels/groups/bots!
                if (isUserOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 1.dp, y = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.35f * pulseAlpha))
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF16161C))
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Title + Last Message
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = chat.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        // Muted / Notification Off icon
                        if (chat.chat_type == "channel") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.NotificationsOff,
                                contentDescription = "Без звуку",
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Date/Time
                    val formattedTime = remember(chat.last_message_date) {
                        if (chat.last_message_date > 0) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(Date(chat.last_message_date * 1000L))
                        } else ""
                    }

                    if (formattedTime.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Outgoing read/sent indicator
                    if (chat.last_message_outgoing) {
                        val indicator = if (chat.last_message_read) indicatorStyle.read else indicatorStyle.sent
                        Text(
                            text = "$indicator ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA855F7)
                        )
                    }

                    Text(
                        text = chat.last_message_text ?: "Немає повідомлень",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )

                    // Sleek Unread Count Badge (Amegram purple pill)
                    if (chat.unread_count > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF9333EA),
                            modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${chat.unread_count}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedMessagesCard(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF16161C),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF9333EA), Color(0xFFA855F7))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = "Saved Messages",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Збережене",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Хмарне сховище для ваших заміток та медіа",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}
