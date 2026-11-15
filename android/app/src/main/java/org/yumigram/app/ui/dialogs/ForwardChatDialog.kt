package org.yumigram.app.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.MessageModel
import org.yumigram.app.bridge.YumigramBridge
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardChatPickerDialog(
    currentChatId: Long,
    messageToForward: MessageModel,
    onDismiss: () -> Unit,
    onMessageForwarded: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var chats by remember { mutableStateOf<List<ChatModel>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val cached = YumigramBridge.getChats()
            if (cached.isNotEmpty()) {
                chats = cached
            }
            val live = YumigramBridge.fetchLiveChats(40)
            if (live.isNotEmpty()) {
                chats = live
            }
        } catch (e: Throwable) {
            android.util.Log.e("ForwardChatDialog", "load chats error", e)
        } finally {
            isLoading = false
        }
    }

    val filteredChats = remember(chats, searchQuery, currentChatId) {
        chats.filter { chat ->
            val matchQuery = searchQuery.isBlank() ||
                    chat.title.contains(searchQuery, ignoreCase = true) ||
                    (chat.username?.contains(searchQuery, ignoreCase = true) == true)
            matchQuery
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Переслати повідомлення",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Оберіть отримувача",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрити",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Пошук чату...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Пошук",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Content list
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                } else if (filteredChats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Чати не знайдено" else "Список чатів порожній",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredChats, key = { it.id }) { chat ->
                            ForwardChatRow(
                                chat = chat,
                                isCurrent = chat.id == currentChatId,
                                isSending = isSending,
                                onClick = {
                                    if (isSending) return@ForwardChatRow
                                    isSending = true
                                    scope.launch {
                                        try {
                                            val success = YumigramBridge.forwardMessages(
                                                fromChatId = currentChatId,
                                                toChatId = chat.id,
                                                messageIds = listOf(messageToForward.id)
                                            )
                                            if (success) {
                                                Toast.makeText(
                                                    context,
                                                    "Повідомлення переслано в «${chat.title}»",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onMessageForwarded()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Помилка пересилання повідомлення",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onDismiss()
                                            }
                                        } catch (e: Throwable) {
                                            android.util.Log.e("ForwardChatDialog", "forward error", e)
                                            Toast.makeText(
                                                context,
                                                "Помилка: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onDismiss()
                                        } finally {
                                            isSending = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardChatRow(
    chat: ChatModel,
    isCurrent: Boolean,
    isSending: Boolean,
    onClick: () -> Unit
) {
    val avatarColors = remember(chat.id) {
        val colors = listOf(
            Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC),
            Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF42A5F5),
            Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFA726)
        )
        colors[kotlin.math.abs(chat.id.hashCode()) % colors.size]
    }

    val initial = chat.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarFile = remember(chat.avatar_path) {
        if (!chat.avatar_path.isNullOrEmpty()) File(chat.avatar_path) else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isSending) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(avatarColors),
            contentAlignment = Alignment.Center
        ) {
            if (avatarFile != null && avatarFile.exists()) {
                AsyncImage(
                    model = avatarFile,
                    contentDescription = chat.title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initial,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subText = when {
                isCurrent -> "Поточний діалог"
                chat.chat_type == "channel" -> "Канал"
                chat.chat_type == "group" -> "Група"
                chat.username != null -> "@${chat.username}"
                else -> "Користувач"
            }
            Text(
                text = subText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "Переслати сюди",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
