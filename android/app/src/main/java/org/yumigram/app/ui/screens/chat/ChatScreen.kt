package org.yumigram.app.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.MessageModel
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.components.MediaViewerDialog
import org.yumigram.app.ui.components.VoiceMessageBubble
import org.yumigram.app.ui.screens.settings.IndicatorStyle
import org.yumigram.app.ui.theme.ChatWallpaper
import org.yumigram.app.ui.theme.DeletedRed
import org.yumigram.app.ui.theme.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val POPULAR_REACTIONS = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "👏", "🎉")

private val POPULAR_EMOJIS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "😊", "😇",
    "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😚", "😋", "😛", "😜",
    "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🫡", "🤐", "🤨", "😐", "😑",
    "😶", "🫥", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥", "😌", "😔", "😪", "🤤",
    "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "😵‍💫",
    "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮",
    "😯", "😲", "😳", "🥺", "🥹", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
    "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹",
    "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "✨", "🔥", "💥", "💯",
    "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "⭐", "🌟", "💫", "👍", "👎", "👏",
    "🙌", "👐", "🤲", "🤝", "🙏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉"
)

private val POPULAR_STICKERS = listOf(
    "🐱 Meow!", "🚀 To the Moon!", "🔥 Fire!", "☕ Coffee Time", "🍕 Pizza Lover",
    "🎉 Party Time!", "💤 Sleepy", "👀 I see you", "❤️ Sending Love", "💯 100% True",
    "⭐ Superstar", "⚡ Shocking!", "🦄 Magic!", "🐸 Kermit Chill", "🐶 Good Boy"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: ChatModel,
    messages: List<MessageModel>,
    indicatorStyle: IndicatorStyle = IndicatorStyle.DOTS,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageModel?>(null) }
    var viewingMediaMessage by remember { mutableStateOf<MessageModel?>(null) }
    var showChatProfile by remember { mutableStateOf(false) }
    var showEmojiStickerTray by remember { mutableStateOf(false) }
    var selectedTrayTab by remember { mutableStateOf(0) } // 0 = Emojis, 1 = Stickers

    val currentWallpaper by ThemeManager.chatWallpaper.collectAsState()

    // Screen-level horizontal drag: swipe right to close chat
    var totalDragX by remember { mutableFloatStateOf(0f) }

    // Auto-scroll to newly sent or received message (index 0 because reverseLayout = true)
    LaunchedEffect(messages.firstOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChatProfile = true }
                    ) {
                        val avatarFile = remember(chat.avatar_path) {
                            if (!chat.avatar_path.isNullOrEmpty()) File(chat.avatar_path) else null
                        }

                        if (avatarFile != null && avatarFile.exists() && avatarFile.length() > 0) {
                            AsyncImage(
                                model = avatarFile,
                                contentDescription = chat.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chat.title.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chat.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (chat.chat_type == "user") "в мережі" else "${chat.chat_type.uppercase()} · Торкніться для деталей",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showChatProfile = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Інфо про чат")
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
                                    text = replyMsg.text.ifBlank { "[Вкладення]" },
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { replyingTo = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Скасувати", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Input bar in Pixel OS pill shape with emoji toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showEmojiStickerTray = !showEmojiStickerTray },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (showEmojiStickerTray) Icons.Rounded.Keyboard else Icons.Rounded.Mood,
                            contentDescription = "Емодзі та стікери",
                            tint = if (showEmojiStickerTray) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

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
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
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

                // Emoji and Sticker Tray
                AnimatedVisibility(
                    visible = showEmojiStickerTray,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTrayTab,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Tab(
                                selected = selectedTrayTab == 0,
                                onClick = { selectedTrayTab = 0 },
                                text = { Text("Емодзі 😊") }
                            )
                            Tab(
                                selected = selectedTrayTab == 1,
                                onClick = { selectedTrayTab = 1 },
                                text = { Text("Стікери 🐱") }
                            )
                        }

                        if (selectedTrayTab == 0) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 42.dp),
                                contentPadding = PaddingValues(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(POPULAR_EMOJIS) { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { inputText += emoji },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 24.sp)
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                contentPadding = PaddingValues(10.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(POPULAR_STICKERS) { sticker ->
                                    ElevatedCard(
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .clickable {
                                                onSendMessage(sticker)
                                                showEmojiStickerTray = false
                                                scope.launch { listState.animateScrollToItem(0) }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = sticker,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentWallpaper.backgroundColor)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                reverseLayout = true
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubbleItem(
                        message = msg,
                        chat = chat,
                        indicatorStyle = indicatorStyle,
                        wallpaper = currentWallpaper,
                        onSwipeReply = { replyingTo = msg },
                        onMediaClick = { viewingMediaMessage = it },
                        onAddReaction = { reaction ->
                            // Send reaction as quick message confirmation
                            scope.launch {
                                YumigramBridge.sendMessage(chat.id, "$reaction на повідомлення #${msg.id}")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Scroll-to-bottom FAB
            if (listState.firstVisibleItemIndex > 2) {
                FloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(padding)
                        .padding(bottom = 16.dp, end = 16.dp)
                        .size(44.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Вниз",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Chat / User Profile Dialog
    if (showChatProfile) {
        ChatProfileDialog(
            chat = chat,
            onDismiss = { showChatProfile = false }
        )
    }

    // Fullscreen Media Viewer
    if (viewingMediaMessage != null) {
        MediaViewerDialog(
            message = viewingMediaMessage,
            onDismiss = { viewingMediaMessage = null }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: MessageModel,
    chat: ChatModel,
    indicatorStyle: IndicatorStyle,
    wallpaper: ChatWallpaper,
    onSwipeReply: () -> Unit,
    onMediaClick: (MessageModel) -> Unit,
    onAddReaction: (String) -> Unit
) {
    val context = LocalContext.current
    var localMediaPath by remember(message.media_path) { mutableStateOf(message.media_path) }
    var selectedReaction by remember { mutableStateOf<String?>(null) }
    var showReactionMenu by remember { mutableStateOf(false) }

    LaunchedEffect(message.id, message.media_path) {
        if (localMediaPath.isNullOrEmpty() && (message.media_type == "photo" || message.media_type == "voice" || message.media_type == "audio")) {
            try {
                val ext = if (message.media_type == "photo") "jpg" else "ogg"
                val mediaDir = File(context.cacheDir, "media").apply { if (!exists()) mkdirs() }
                val target = "${mediaDir.absolutePath}/${message.media_type}_${message.chat_id}_${message.id}.$ext"
                val downloaded = YumigramBridge.downloadMedia(message.chat_id, message.id, target)
                if (downloaded.isNotEmpty()) {
                    localMediaPath = downloaded
                }
            } catch (e: Throwable) {
                android.util.Log.e("Yumigram", "downloadMedia error", e)
            }
        }
    }

    var offsetX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.is_outgoing) Alignment.End else Alignment.Start
    ) {
        // Floating reaction picker bar
        AnimatedVisibility(
            visible = showReactionMenu,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(POPULAR_REACTIONS) { reaction ->
                        Text(
                            text = reaction,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    selectedReaction = reaction
                                    showReactionMenu = false
                                    onAddReaction(reaction)
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -70f) {
                                onSwipeReply()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount < 0 || offsetX < 0) {
                                offsetX = (offsetX + dragAmount).coerceIn(-100f, 0f)
                            }
                        }
                    )
                }
                .offset(x = offsetX.dp),
            contentAlignment = if (message.is_outgoing) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            val bubbleColor = if (message.is_outgoing) wallpaper.bubbleOutgoing else wallpaper.bubbleIncoming
            val shape = if (message.is_outgoing) {
                RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
            } else {
                RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
            }

            Surface(
                shape = shape,
                color = bubbleColor,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = { showReactionMenu = !showReactionMenu },
                        onLongClick = { showReactionMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Group Chat Sender Name (who wrote what)
                    if (!message.is_outgoing && chat.chat_type != "user" && message.sender_name.isNotBlank()) {
                        val nameColor = remember(message.sender_id) {
                            val colors = listOf(
                                Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC),
                                Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF42A5F5),
                                Color(0xFF29B6F6), Color(0xFF26A69A), Color(0xFF66BB6A),
                                Color(0xFF9CCC65), Color(0xFFFFA726), Color(0xFFFF7043)
                            )
                            colors[kotlin.math.abs(message.sender_id.hashCode()) % colors.size]
                        }
                        Text(
                            text = message.sender_name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = nameColor,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

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
                                tint = Color.LightGray,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Було: ${message.original_text}",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Photo preview
                    if (message.media_type == "photo" && !localMediaPath.isNullOrEmpty()) {
                        val photoFile = remember(localMediaPath) { File(localMediaPath!!) }
                        AsyncImage(
                            model = photoFile,
                            contentDescription = "Фотографія",
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
                                .background(Color.Black.copy(alpha = 0.2f))
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
                            color = Color.White.copy(alpha = 0.7f)
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

        // Active reaction badge on the bubble
        if (selectedReaction != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(top = 2.dp, start = 6.dp, end = 6.dp)
                    .clickable { selectedReaction = null }
            ) {
                Text(
                    text = "$selectedReaction 1",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ChatProfileDialog(
    chat: ChatModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрити")
                    }
                }

                // Big Avatar
                val avatarFile = remember(chat.avatar_path) {
                    if (!chat.avatar_path.isNullOrEmpty()) File(chat.avatar_path) else null
                }

                if (avatarFile != null && avatarFile.exists() && avatarFile.length() > 0) {
                    AsyncImage(
                        model = avatarFile,
                        contentDescription = chat.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.title.take(1).uppercase(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (!chat.username.isNullOrEmpty()) {
                    Text(
                        text = "@${chat.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detail tiles
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Тип чату: ${chat.chat_type.uppercase()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Захищено MTProto 2.0 шифруванням", fontSize = 13.sp)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Ghost Mode & Anti-Delete активні", fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Готово")
                }
            }
        }
    }
}
