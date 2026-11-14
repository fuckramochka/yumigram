package org.yumigram.app.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.MessageModel
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.components.MediaViewerDialog
import org.yumigram.app.ui.components.VoiceMessageBubble
import org.yumigram.app.ui.screens.settings.IndicatorStyle
import org.yumigram.app.ui.theme.DeletedRed
import org.yumigram.app.ui.theme.IncomingBubbleColor
import org.yumigram.app.ui.theme.OutgoingBubbleColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: ChatModel,
    messages: List<MessageModel>,
    indicatorStyle: IndicatorStyle = IndicatorStyle.DOTS,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageModel?>(null) }
    var viewingMediaMessage by remember { mutableStateOf<MessageModel?>(null) }

    // Screen-level horizontal drag: swipe right to close chat
    var totalDragX by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (totalDragX > 150f) {
                        onBack()
                    }
                    totalDragX = 0f
                },
                onHorizontalDrag = { _, dragAmount ->
                    totalDragX += dragAmount
                }
            )
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!chat.avatar_path.isNullOrEmpty()) {
                            AsyncImage(
                                model = chat.avatar_path,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.title.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = chat.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "в мережі",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Reply preview bar
                AnimatedVisibility(visible = replyingTo != null) {
                    replyingTo?.let { replyMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = "Reply",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = replyMsg.sender_name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = replyMsg.text,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { replyingTo = null }) {
                                Text("✕", fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Input bar in Pixel OS pill shape
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Повідомлення...") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                                replyingTo = null
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubbleItem(
                    message = msg,
                    indicatorStyle = indicatorStyle,
                    onSwipeReply = { replyingTo = msg },
                    onMediaClick = { viewingMediaMessage = it }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    // Fullscreen Media Viewer
    if (viewingMediaMessage != null) {
        MediaViewerDialog(
            message = viewingMediaMessage,
            onDismiss = { viewingMediaMessage = null }
        )
    }
}

@Composable
fun MessageBubbleItem(
    message: MessageModel,
    indicatorStyle: IndicatorStyle,
    onSwipeReply: () -> Unit,
    onMediaClick: (MessageModel) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var localMediaPath by remember(message.media_path) { mutableStateOf(message.media_path) }

    LaunchedEffect(message.id, message.media_path) {
        if (localMediaPath.isNullOrEmpty() && (message.media_type == "photo" || message.media_type == "voice" || message.media_type == "audio")) {
            val ext = if (message.media_type == "photo") "jpg" else "ogg"
            val target = "${context.cacheDir.absolutePath}/media/${message.media_type}_${message.chat_id}_${message.id}.$ext"
            val downloaded = YumigramBridge.downloadMedia(message.chat_id, message.id, target)
            if (downloaded.isNotEmpty()) {
                localMediaPath = downloaded
            }
        }
    }

    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -80f) {
                            onSwipeReply()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        // Only allow dragging left for reply
                        if (dragAmount < 0 || offsetX < 0) {
                            offsetX = (offsetX + dragAmount).coerceIn(-120f, 0f)
                        }
                    }
                )
            }
            .offset(x = offsetX.dp),
        contentAlignment = if (message.is_outgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        val bubbleColor = if (message.is_outgoing) OutgoingBubbleColor else IncomingBubbleColor
        val shape = if (message.is_outgoing) {
            RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        } else {
            RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        }

        Surface(
            shape = shape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Anti-delete notification
                if (message.is_deleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Deleted",
                            tint = DeletedRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "[Видалено співрозмовником]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeletedRed
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Anti-edit previous version preview
                if (message.original_text != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edited",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Було: ${message.original_text}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Photo preview
                if (message.media_type == "photo" && !localMediaPath.isNullOrEmpty()) {
                    AsyncImage(
                        model = localMediaPath,
                        contentDescription = "Photo Attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onMediaClick(message.copy(media_path = localMediaPath)) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Voice / Audio message player
                if (message.media_type == "voice" || message.media_type == "audio") {
                    VoiceMessageBubble(
                        mediaPath = localMediaPath,
                        durationSeconds = message.duration,
                        isOutgoing = message.is_outgoing
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Generic file attachment
                if (message.media_type == "file") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.15f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = "Document",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.text.ifBlank { "Файл документу" },
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Main message text (if not voice or file placeholder)
                if (message.media_type != "voice" && message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Bottom row: time + read indicator
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val formattedTime = remember(message.date) {
                        if (message.date > 0) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(Date(message.date * 1000L))
                        } else {
                            ""
                        }
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    if (message.is_outgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val indicator = if (message.is_read) indicatorStyle.read else indicatorStyle.sent
                        Text(
                            text = indicator,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64B5F6)
                        )
                    }
                }
            }
        }
    }
}
