package org.yumigram.app.ui.screens.chats

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
    ALL("Всі"),
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
            ChatFilter.PERSONAL -> list.filter { it.chat_type == "user" || it.chat_type == "dialog" }
            ChatFilter.GROUPS -> list.filter { it.chat_type == "group" || it.chat_type == "supergroup" }
            ChatFilter.CHANNELS -> list.filter { it.chat_type == "channel" }
            ChatFilter.BOTS -> list.filter { it.chat_type == "bot" || it.username?.endsWith("bot", ignoreCase = true) == true }
            ChatFilter.UNREAD -> list.filter { it.unread_count > 0 }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Refresh button
                        FilledTonalIconButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing,
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
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

                        // Ghost Mode Badge (Material 3 Expressive pill)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
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
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ghost",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Flagship The Pill Search Bar (Material 3 Expressive, shape = CircleShape, height = 52.dp)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Пошук чатів або повідомлень...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Очистити",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Expressive Filter Chips Row (No scrollbars, smooth pill layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = filter.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = null
                        )
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
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Нічого не знайдено" else "Немає активних чатів",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Оновіть або почніть нову бесіду",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Tonal surface separation instead of hard Dividers
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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

    // Pulsating online indicator animation
    val isUserChat = chat.chat_type == "user" || chat.chat_type == "dialog"
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

    // Tonal surface card (surfaceContainerLow, 20.dp rounded shape)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
            // Avatar Box with optional Pulsating Online Indicator
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
                    // Generative Pastel Monet Avatar
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
                            color = monetPalette.onColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Pulsating Online Indicator for direct user chats
                if (isUserChat) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 1.dp, y = 1.dp)
                    ) {
                        // Soft pulsing halo
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.35f * pulseAlpha))
                        )
                        // Solid green dot with 2.dp border matching card background
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
                    Text(
                        text = chat.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = chat.last_message_text ?: "Немає повідомлень",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    // Unread Count Badge in Pixel MD3 Pill Style
                    if (chat.unread_count > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${chat.unread_count}",
                                    color = MaterialTheme.colorScheme.onPrimary,
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                            listOf(Color(0xFF2979FF), Color(0xFF00B0FF))
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Хмарне сховище для ваших заміток та медіа",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
