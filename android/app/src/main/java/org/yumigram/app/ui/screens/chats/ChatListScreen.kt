package org.yumigram.app.ui.screens.chats

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.screens.settings.IndicatorStyle
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
    onRefresh: () -> Unit,
    onChatClick: (ChatModel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ChatFilter.ALL) }

    val filteredChats = remember(searchQuery, selectedFilter, chats) {
        var list = if (searchQuery.isBlank()) chats else {
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
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Refresh button
                        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Оновити чати",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Ghost Mode Badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar (Pixel OS rounded pill shape)
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Пошук повідомлень або контактів...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Пошук",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Очистити",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Category Chips (Pixel OS horizontal scroll)
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
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
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
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Нічого не знайдено" else "Немає активних чатів",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Натисніть кнопку оновлення зверху для завантаження",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (selectedFilter == ChatFilter.ALL && searchQuery.isBlank()) {
                    item {
                        SavedMessagesRow(
                            onClick = {
                                val savedChat = chats.find { it.title == "Збережене" || it.title == "Saved Messages" }
                                    ?: ChatModel(
                                        id = 0L,
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
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 82.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }
                }

                items(filteredChats, key = { it.id }) { chat ->
                    ChatItemRow(
                        chat = chat,
                        indicatorStyle = indicatorStyle,
                        onClick = { onChatClick(chat) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 82.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
fun ChatItemRow(
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with Coil AsyncImage and Material You Gradient Initials Fallback
        if (avatarFile != null && avatarFile!!.exists() && avatarFile!!.length() > 0) {
            AsyncImage(
                model = avatarFile,
                contentDescription = chat.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
            )
        } else {
            val gradientColors = remember(chat.id) {
                val palettes = listOf(
                    listOf(Color(0xFF1A73E8), Color(0xFF4285F4)),
                    listOf(Color(0xFF00897B), Color(0xFF26A69A)),
                    listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)),
                    listOf(Color(0xFFFF5722), Color(0xFFFF8A65)),
                    listOf(Color(0xFFE91E63), Color(0xFFF06292)),
                    listOf(Color(0xFF00ACC1), Color(0xFF4DD0E1))
                )
                palettes[kotlin.math.abs(chat.id.hashCode()) % palettes.size]
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                val initial = chat.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Outgoing indicator
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

                // Unread Count Badge in Pixel OS style
                if (chat.unread_count > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                text = "${chat.unread_count}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedMessagesRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
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
