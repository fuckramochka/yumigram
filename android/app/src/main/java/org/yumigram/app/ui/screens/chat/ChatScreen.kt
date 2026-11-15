package org.yumigram.app.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.bridge.MessageModel
import org.yumigram.app.bridge.StickerModel
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.components.*
import org.yumigram.app.ui.dialogs.ForwardChatPickerDialog
import org.yumigram.app.ui.screens.settings.IndicatorStyle
import org.yumigram.app.ui.theme.ChatWallpaper
import org.yumigram.app.ui.theme.DeletedRed
import org.yumigram.app.ui.theme.ThemeManager
import org.yumigram.app.ui.theme.getMonetAvatarPalette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Deterministic Telegram participant colors (palette of 8 signature Telegram colors)
val TELEGRAM_NAME_COLORS = listOf(
    Color(0xFFE53935), // Red
    Color(0xFFFB8C00), // Orange
    Color(0xFF8E24AA), // Violet
    Color(0xFF43A047), // Green
    Color(0xFF00ACC1), // Cyan
    Color(0xFF1E88E5), // Blue
    Color(0xFFD81B60), // Pink
    Color(0xFFFFB300)  // Amber
)

fun getSenderColor(senderId: Long): Color {
    val idx = kotlin.math.abs(senderId.hashCode()) % TELEGRAM_NAME_COLORS.size
    return TELEGRAM_NAME_COLORS[idx]
}

private val POPULAR_REACTIONS = listOf("❤️", "🔥", "💊", "😭", "😍", "🤮", "🖕", "👏", "👍", "🔽")

enum class MessageSequence {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST
}

fun getMessageSequence(
    currentIndex: Int,
    messages: List<MessageModel>
): MessageSequence {
    val current = messages[currentIndex]
    val prevOlder = messages.getOrNull(currentIndex + 1)
    val nextNewer = messages.getOrNull(currentIndex - 1)

    fun isSameSeries(a: MessageModel, b: MessageModel?): Boolean {
        if (b == null) return false
        if (a.is_outgoing != b.is_outgoing) return false
        if (!a.is_outgoing && a.sender_id != b.sender_id) return false
        if (kotlin.math.abs(a.date - b.date) > 300) return false
        return true
    }

    val hasOlderAbove = isSameSeries(current, prevOlder)
    val hasNewerBelow = isSameSeries(current, nextNewer)

    return when {
        !hasOlderAbove && !hasNewerBelow -> MessageSequence.SINGLE
        !hasOlderAbove && hasNewerBelow -> MessageSequence.FIRST
        hasOlderAbove && hasNewerBelow -> MessageSequence.MIDDLE
        else -> MessageSequence.LAST
    }
}

fun getBubbleShape(
    isOutgoing: Boolean,
    sequence: MessageSequence
): RoundedCornerShape {
    return if (isOutgoing) {
        when (sequence) {
            MessageSequence.SINGLE -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = 6.dp,
                bottomStart = 20.dp
            )
            MessageSequence.FIRST -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = 6.dp,
                bottomStart = 20.dp
            )
            MessageSequence.MIDDLE -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp,
                bottomStart = 20.dp
            )
            MessageSequence.LAST -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 6.dp,
                bottomEnd = 4.dp,
                bottomStart = 20.dp
            )
        }
    } else {
        when (sequence) {
            MessageSequence.SINGLE -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp,
                bottomStart = 6.dp
            )
            MessageSequence.FIRST -> RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp,
                bottomStart = 6.dp
            )
            MessageSequence.MIDDLE -> RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp,
                bottomStart = 6.dp
            )
            MessageSequence.LAST -> RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp,
                bottomStart = 4.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: ChatModel,
    messages: List<MessageModel>,
    indicatorStyle: IndicatorStyle = IndicatorStyle.DOTS,
    onBack: () -> Unit,
    onSendMessage: (String, Int?) -> Unit,
    onSendRichMessage: ((text: String, mediaType: String?, mediaPath: String?, duration: Int?, replyToId: Int?) -> Unit)? = null,
    onSendSticker: ((sticker: StickerModel, replyToId: Int?) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageModel?>(null) }
    var messageToForward by remember { mutableStateOf<MessageModel?>(null) }
    var selectedMessageForMenu by remember { mutableStateOf<MessageModel?>(null) }
    var viewingMediaMessage by remember { mutableStateOf<MessageModel?>(null) }
    var showChatProfile by remember { mutableStateOf(false) }
    var showEmojiStickerTray by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pinnedMessage by remember { mutableStateOf<MessageModel?>(null) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var showRecordVideoNote by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isChannelMuted by remember { mutableStateOf(false) }

    val currentWallpaper by ThemeManager.chatWallpaper.collectAsState()
    val customWallpaperUri by ThemeManager.customWallpaperUri.collectAsState()

    // Predictive Back Gesture Handler
    val backProgressAnim = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = true) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { backEvent ->
                backProgressAnim.snapTo(backEvent.progress)
            }
            onBack()
        } catch (e: CancellationException) {
            backProgressAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            ThemeManager.setCustomWallpaperUri(context, it.toString())
            Toast.makeText(context, "Шпалери з галереї встановлено!", Toast.LENGTH_SHORT).show()
        }
    }

    fun dispatchMessage(text: String, mediaType: String? = null, mediaPath: String? = null, duration: Int? = null) {
        val replyId = replyingTo?.id
        replyingTo = null
        if (onSendRichMessage != null) {
            onSendRichMessage(text, mediaType, mediaPath, duration, replyId)
        } else {
            onSendMessage(text, replyId)
        }
        scope.launch { listState.animateScrollToItem(0) }
    }

    fun dispatchSticker(sticker: StickerModel) {
        val replyId = replyingTo?.id
        replyingTo = null
        showEmojiStickerTray = false
        if (onSendSticker != null) {
            onSendSticker(sticker, replyId)
        } else {
            scope.launch {
                try {
                    YumigramBridge.sendSticker(chat.id, sticker, replyId)
                } catch (e: Throwable) {
                    android.util.Log.e("ChatScreen", "dispatchSticker error", e)
                }
            }
        }
        scope.launch { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(messages.firstOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val currentProgress = backProgressAnim.value
    val backScale = 1f - (currentProgress * 0.08f)
    val backCornerRadius = (currentProgress * 32f).dp
    val backElevation = (currentProgress * 24f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = backScale
                scaleY = backScale
                clip = currentProgress > 0.001f
                shape = RoundedCornerShape(backCornerRadius)
                shadowElevation = backElevation.toPx()
            }
    ) {
        Scaffold(
            containerColor = Color(0xFF0C0C10),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0C10))
                ) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
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

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarFile != null && avatarFile.exists()) {
                                        AsyncImage(
                                            model = avatarFile,
                                            contentDescription = chat.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        val monetPalette = remember(chat.id) { getMonetAvatarPalette(chat.id) }
                                        val initial = chat.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Brush.linearGradient(monetPalette.gradient)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initial,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 17.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = chat.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White
                                    )
                                    Text(
                                        text = when (chat.chat_type) {
                                            "user", "dialog" -> "Особистий чат"
                                            "group", "supergroup" -> "Група"
                                            "channel" -> "Канал"
                                            else -> "Чат"
                                        },
                                        fontSize = 12.sp,
                                        color = Color(0xFFA855F7),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                                Icon(
                                    Icons.Rounded.Wallpaper,
                                    contentDescription = "Шпалери",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Меню",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF1E1E26))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("👤 Профіль контакту", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        showChatProfile = true
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFFA855F7)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("🖼️ Шпалери з галереї", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        galleryLauncher.launch("image/*")
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, tint = Color(0xFFA855F7)) }
                                )
                                if (customWallpaperUri != null) {
                                    DropdownMenuItem(
                                        text = { Text("❌ Скинути шпалери галереї", color = DeletedRed) },
                                        onClick = {
                                            showMenu = false
                                            ThemeManager.setCustomWallpaperUri(context, null)
                                            Toast.makeText(context, "Шпалери скинуто на стандартні", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = DeletedRed) }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0C0C10)
                        )
                    )

                    // Pinned Message Bar
                    AnimatedVisibility(visible = pinnedMessage != null) {
                        pinnedMessage?.let { pinned ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val index = messages.indexOfFirst { it.id == pinned.id }
                                        if (index >= 0) {
                                            scope.launch { listState.animateScrollToItem(index) }
                                        }
                                    },
                                color = Color(0xFF18181F),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFFA855F7))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = "Pinned",
                                        tint = Color(0xFFA855F7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Закріплене повідомлення",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFA855F7)
                                        )
                                        Text(
                                            text = pinned.text.ifBlank { "[Вкладення]" },
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val toUnpin = pinnedMessage
                                            pinnedMessage = null
                                            if (toUnpin != null) {
                                                scope.launch {
                                                    YumigramBridge.pinMessage(chat.id, toUnpin.id, unpin = true)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Відкріпити",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (chat.chat_type == "channel") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding(),
                        color = Color(0xFF0C0C10)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Button(
                                onClick = {
                                    isChannelMuted = !isChannelMuted
                                    Toast.makeText(
                                        context,
                                        if (isChannelMuted) "Сповіщення каналу вимкнено" else "Сповіщення каналу увімкнено",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF9333EA),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isChannelMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isChannelMuted) "Увімкнути звук" else "Вимкнути звук",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                } else {
                    // Floating Input Bar (Amegram / exteraGram Floating Pill)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .background(Color(0xFF0C0C10))
                    ) {
                        // Reply preview bar
                        AnimatedVisibility(visible = replyingTo != null) {
                            replyingTo?.let { replyMsg ->
                                ReplyInputBarPreview(
                                    replyingTo = replyMsg,
                                    onDismiss = { replyingTo = null }
                                )
                            }
                        }

                        // Input bar or Voice Recording Bar
                        if (isRecordingVoice) {
                            VoiceRecordBar(
                                onCancel = { isRecordingVoice = false },
                                onSendVoice = { filePath, duration ->
                                    isRecordingVoice = false
                                    dispatchMessage(
                                        text = "🎙️ Голосове повідомлення ($duration сек)",
                                        mediaType = "voice",
                                        mediaPath = filePath,
                                        duration = duration
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        } else {
                            // Floating dark acrylic capsule
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = Color(0xFF1E1E24).copy(alpha = 0.96f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Emoji / Stickers Toggle
                                    IconButton(
                                        onClick = { showEmojiStickerTray = !showEmojiStickerTray },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showEmojiStickerTray) Icons.Rounded.Keyboard else Icons.Rounded.Mood,
                                            contentDescription = "Емодзі та стікери",
                                            tint = if (showEmojiStickerTray) Color(0xFFA855F7) else Color.White.copy(alpha = 0.7f)
                                        )
                                    }

                                    // Message Input Field
                                    TextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        placeholder = {
                                            Text(
                                                "Повідомлення...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.45f)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 46.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            cursorColor = Color(0xFFA855F7),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )

                                    // Attach Button (📎)
                                    IconButton(
                                        onClick = { showAttachmentMenu = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AttachFile,
                                            contentDescription = "Вкладення",
                                            tint = Color.White.copy(alpha = 0.7f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    val sendRotate by animateFloatAsState(
                                        targetValue = if (inputText.isNotBlank()) 0f else -45f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "sendRotate"
                                    )

                                    // Morphing Action Button (Mic ⟷ Send) in Neon Purple
                                    AnimatedContent(
                                        targetState = inputText.isNotBlank(),
                                        transitionSpec = {
                                            (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                                .togetherWith(scaleOut() + fadeOut())
                                        },
                                        label = "ActionMorph"
                                    ) { hasText ->
                                        if (hasText) {
                                            FilledIconButton(
                                                onClick = {
                                                    dispatchMessage(inputText)
                                                    inputText = ""
                                                },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .graphicsLayer { rotationZ = sendRotate },
                                                shape = CircleShape,
                                                colors = IconButtonDefaults.filledIconButtonColors(
                                                    containerColor = Color(0xFF9333EA),
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                                    contentDescription = "Надіслати",
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                FilledTonalIconButton(
                                                    onClick = { isRecordingVoice = true },
                                                    modifier = Modifier.size(42.dp),
                                                    shape = CircleShape,
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = Color(0xFF9333EA),
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Mic,
                                                        contentDescription = "Голосове повідомлення",
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(4.dp))

                                                FilledTonalIconButton(
                                                    onClick = { showRecordVideoNote = true },
                                                    modifier = Modifier.size(42.dp),
                                                    shape = CircleShape,
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = Color(0xFF252532),
                                                        contentColor = Color(0xFFA855F7)
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.RadioButtonChecked,
                                                        contentDescription = "Кружечок",
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Native Stickers & Emojis Tray
                        AnimatedVisibility(
                            visible = showEmojiStickerTray,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            StickerPickerTray(
                                cacheDir = context.cacheDir.absolutePath,
                                onStickerSelected = { sticker ->
                                    dispatchSticker(sticker)
                                },
                                onEmojiSelected = { emoji ->
                                    inputText += emoji
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val chatBgColor = if (customWallpaperUri.isNullOrEmpty() && currentWallpaper == ChatWallpaper.DEFAULT) {
                Color(0xFF0C0C10)
            } else {
                currentWallpaper.backgroundColor
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(chatBgColor)
            ) {
                // Fullscreen custom gallery wallpaper if set
                if (!customWallpaperUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = customWallpaperUri,
                        contentDescription = "Шпалери чату з галереї",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp),
                    reverseLayout = true
                ) {
                    itemsIndexed(messages, key = { _, it -> it.id }) { index, msg ->
                        val sequence = remember(messages, index) {
                            getMessageSequence(index, messages)
                        }

                        MessageBubbleItem(
                            message = msg,
                            sequence = sequence,
                            messages = messages,
                            chat = chat,
                            indicatorStyle = indicatorStyle,
                            wallpaper = currentWallpaper,
                            hasCustomWallpaper = !customWallpaperUri.isNullOrEmpty() || currentWallpaper != ChatWallpaper.DEFAULT,
                            onSwipeReply = { replyingTo = msg },
                            onReplyQuoteClick = { replyId ->
                                val targetIndex = messages.indexOfFirst { it.id == replyId }
                                if (targetIndex >= 0) {
                                    scope.launch { listState.animateScrollToItem(targetIndex) }
                                } else {
                                    Toast.makeText(context, "Повідомлення #$replyId знаходиться раніше в історії", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onMediaClick = { viewingMediaMessage = it },
                            onShowContextMenu = { selectedMessageForMenu = it },
                            onAddReaction = { reaction ->
                                scope.launch {
                                    YumigramBridge.sendReaction(chat.id, msg.id, reaction)
                                }
                            }
                        )

                        val isNextInSeries = sequence == MessageSequence.FIRST || sequence == MessageSequence.MIDDLE
                        Spacer(modifier = Modifier.height(if (isNextInSeries) 3.dp else 10.dp))
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
                            .size(46.dp),
                        shape = CircleShape,
                        containerColor = Color(0xFF1E1E26)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Вниз",
                            tint = Color(0xFFA855F7)
                        )
                    }
                }
            }
        }
    }

    // 4. Sleek Floating Context Menu (Amegram / exteraGram Compact Overlay — NO BottomSheet!)
    if (selectedMessageForMenu != null) {
        val targetMsg = selectedMessageForMenu!!
        val isPinned = pinnedMessage?.id == targetMsg.id

        // Fullscreen semi-transparent backdrop overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { selectedMessageForMenu = null },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clickable(enabled = false) {}, // Prevent dismiss when tapping menu itself
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top: Quick Reactions Bar (horizontal capsule)
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1E24).copy(alpha = 0.98f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 12.dp
                ) {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(POPULAR_REACTIONS) { reaction ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        scope.launch {
                                            YumigramBridge.sendReaction(chat.id, targetMsg.id, reaction)
                                        }
                                        selectedMessageForMenu = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = reaction, fontSize = 20.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Middle: Compact Vertical Actions Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E24).copy(alpha = 0.96f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        ContextMenuItem(
                            icon = Icons.AutoMirrored.Rounded.Reply,
                            title = "Відповісти",
                            tint = Color(0xFFA855F7),
                            onClick = {
                                replyingTo = targetMsg
                                selectedMessageForMenu = null
                            }
                        )
                        ContextMenuItem(
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            title = "Переслати",
                            tint = Color.White,
                            onClick = {
                                messageToForward = targetMsg
                                selectedMessageForMenu = null
                            }
                        )
                        ContextMenuItem(
                            icon = Icons.Rounded.PushPin,
                            title = if (isPinned) "Відкріпити" else "Закріпити",
                            tint = Color.White,
                            onClick = {
                                val willUnpin = isPinned
                                pinnedMessage = if (willUnpin) null else targetMsg
                                scope.launch {
                                    YumigramBridge.pinMessage(chat.id, targetMsg.id, unpin = willUnpin)
                                }
                                Toast.makeText(
                                    context,
                                    if (willUnpin) "Відкріплено" else "Закріплено",
                                    Toast.LENGTH_SHORT
                                ).show()
                                selectedMessageForMenu = null
                            }
                        )
                        ContextMenuItem(
                            icon = Icons.Rounded.ContentCopy,
                            title = "Копіювати текст",
                            tint = Color.White,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Message Text", targetMsg.text.ifBlank { targetMsg.media_type ?: "" })
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Текст скопійовано", Toast.LENGTH_SHORT).show()
                                selectedMessageForMenu = null
                            }
                        )
                        ContextMenuItem(
                            icon = Icons.Rounded.Delete,
                            title = "Видалити",
                            tint = Color(0xFFFF5252),
                            isDestructive = true,
                            onClick = {
                                scope.launch {
                                    YumigramBridge.nativeMarkDeleted(chat.id, targetMsg.id)
                                }
                                Toast.makeText(context, "Повідомлення видалено", Toast.LENGTH_SHORT).show()
                                selectedMessageForMenu = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom: Row of quick micro-icons
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1E24).copy(alpha = 0.96f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                replyingTo = targetMsg
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = "Відповісти",
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Message Text", targetMsg.text.ifBlank { targetMsg.media_type ?: "" })
                                clipboard.setPrimaryClip(clip)
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Копіювати",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                messageToForward = targetMsg
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Переслати",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    YumigramBridge.nativeMarkDeleted(chat.id, targetMsg.id)
                                }
                                selectedMessageForMenu = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Видалити",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Forwarding Chat Picker Dialog
    if (messageToForward != null) {
        ForwardChatPickerDialog(
            currentChatId = chat.id,
            messageToForward = messageToForward!!,
            onDismiss = { messageToForward = null }
        )
    }

    // Attachment Bottom Sheet
    if (showAttachmentMenu) {
        AttachmentBottomSheet(
            onDismiss = { showAttachmentMenu = false },
            onSendPhoto = { uri ->
                dispatchMessage("🖼️ Фотографія", mediaType = "photo", mediaPath = uri.toString())
            },
            onSendVideo = { uri ->
                dispatchMessage("🎥 Відеозапис", mediaType = "video", mediaPath = uri.toString(), duration = 30)
            },
            onSendFile = { uri ->
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Документ"
                dispatchMessage("📁 $fileName", mediaType = "file", mediaPath = uri.toString())
            },
            onSendAudio = { uri ->
                val trackName = uri.lastPathSegment?.substringAfterLast('/') ?: "Аудіотрек"
                dispatchMessage(trackName, mediaType = "audio", mediaPath = uri.toString(), duration = 180)
            },
            onRecordVideoNote = {
                showRecordVideoNote = true
            }
        )
    }

    // Round Video Note Camera Dialog
    if (showRecordVideoNote) {
        RoundVideoRecordDialog(
            onDismiss = { showRecordVideoNote = false },
            onSendVideoNote = { filePath, duration ->
                showRecordVideoNote = false
                dispatchMessage(
                    text = "⭕ Відео-повідомлення ($duration сек)",
                    mediaType = "video_note",
                    mediaPath = filePath,
                    duration = duration
                )
            }
        )
    }

    // Full Telegram User Profile Dialog
    if (showChatProfile) {
        FullUserProfileDialog(
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

@Composable
private fun ContextMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = Color.White,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDestructive) Color(0xFFFF5252) else Color.White
        )
    }
}

// Custom Layout: Inline Text with Embedded Timestamp & Read Indicators
@Composable
fun InlineMessageTextWithTime(
    text: String,
    fontSize: Float,
    textColor: Color,
    timeContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var textLayoutResult by remember(text, fontSize) { mutableStateOf<TextLayoutResult?>(null) }

    Layout(
        modifier = modifier,
        content = {
            Text(
                text = text,
                fontSize = fontSize.sp,
                color = textColor,
                onTextLayout = { textLayoutResult = it }
            )
            timeContent()
        }
    ) { measurables, constraints ->
        val timePlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val textPlaceable = measurables[0].measure(constraints)

        val result = textLayoutResult
        val timeWidth = timePlaceable.width
        val timeHeight = timePlaceable.height
        val margin = 8.dp.roundToPx()

        if (result == null) {
            val width = maxOf(textPlaceable.width, timeWidth)
            val height = textPlaceable.height + timeHeight
            return@Layout layout(width, height) {
                textPlaceable.placeRelative(0, 0)
                timePlaceable.placeRelative(width - timeWidth, textPlaceable.height)
            }
        }

        val lineCount = result.lineCount
        val lastLine = lineCount - 1
        val lastLineWidth = result.getLineRight(lastLine) - result.getLineLeft(lastLine)

        val fitsOnLastLine = if (lineCount == 1) {
            (textPlaceable.width + margin + timeWidth) <= constraints.maxWidth
        } else {
            (constraints.maxWidth - lastLineWidth) >= (timeWidth + margin)
        }

        if (fitsOnLastLine) {
            val totalWidth = if (lineCount == 1) {
                textPlaceable.width + margin + timeWidth
            } else {
                maxOf(textPlaceable.width, (lastLineWidth + margin + timeWidth).toInt())
            }
            val totalHeight = maxOf(textPlaceable.height, timeHeight)

            layout(totalWidth, totalHeight) {
                textPlaceable.placeRelative(0, 0)
                timePlaceable.placeRelative(
                    x = totalWidth - timeWidth,
                    y = totalHeight - timeHeight
                )
            }
        } else {
            val spacing = 2.dp.roundToPx()
            val totalWidth = maxOf(textPlaceable.width, timeWidth)
            val totalHeight = textPlaceable.height + spacing + timeHeight

            layout(totalWidth, totalHeight) {
                textPlaceable.placeRelative(0, 0)
                timePlaceable.placeRelative(
                    x = totalWidth - timeWidth,
                    y = textPlaceable.height + spacing
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: MessageModel,
    sequence: MessageSequence,
    messages: List<MessageModel>,
    chat: ChatModel,
    indicatorStyle: IndicatorStyle,
    wallpaper: ChatWallpaper,
    hasCustomWallpaper: Boolean,
    onSwipeReply: () -> Unit,
    onReplyQuoteClick: (Int) -> Unit,
    onMediaClick: (MessageModel) -> Unit,
    onShowContextMenu: (MessageModel) -> Unit,
    onAddReaction: (String) -> Unit
) {
    val context = LocalContext.current
    var localMediaPath by remember(message.media_path) { mutableStateOf(message.media_path) }
    var selectedReaction by remember { mutableStateOf<String?>(null) }

    val dragOffsetAnim = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxDragPx = with(density) { 110.dp.toPx() }
    val scope = rememberCoroutineScope()
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val replyIconScale by animateFloatAsState(
        targetValue = if (dragOffsetAnim.value >= thresholdPx) 1.15f else (dragOffsetAnim.value / thresholdPx).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "replyScale"
    )

    LaunchedEffect(message.id, message.media_path, message.media_type) {
        if (localMediaPath.isNullOrEmpty() && message.media_type != null) {
            try {
                val ext = when (message.media_type) {
                    "photo" -> "jpg"
                    "sticker" -> "webp"
                    "gif" -> "mp4"
                    "video", "video_note", "round_video" -> "mp4"
                    "voice" -> "ogg"
                    "audio" -> "mp3"
                    else -> "bin"
                }
                val mediaDir = File(context.cacheDir, "media").apply { if (!exists()) mkdirs() }
                val target = "${mediaDir.absolutePath}/${message.media_type}_${message.chat_id}_${message.id}.$ext"
                val targetFile = File(target)
                if (targetFile.exists() && targetFile.length() > 0) {
                    localMediaPath = target
                } else {
                    val downloaded = YumigramBridge.downloadMedia(message.chat_id, message.id, target)
                    if (downloaded.isNotEmpty()) {
                        localMediaPath = downloaded
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("Yumigram", "downloadMedia error", e)
            }
        }
    }

    val isSticker = message.media_type == "sticker"
    val isRoundVideo = message.media_type == "video_note" || message.media_type == "round_video"
    val isPoll = message.media_type == "poll"
    val isLocation = message.media_type == "location"
    val isContact = message.media_type == "contact"
    val isChannel = chat.chat_type == "channel"

    val chatFontSize by ThemeManager.fontSize.collectAsState()

    val formattedTime = remember(message.date) {
        if (message.date > 0) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(Date(message.date * 1000L))
        } else {
            ""
        }
    }

    val viewsCount = remember(message.id) {
        val base = kotlin.math.abs(message.id.hashCode() % 9500) + 500
        if (base >= 1000) String.format(Locale.US, "%.1fK", base / 1000.0) else "$base"
    }

    val repliedMessage = remember(message.reply_to_id, messages) {
        message.reply_to_id?.let { rId -> messages.firstOrNull { it.id == rId } }
    }

    // 3. Cyberpunk Glass Chat Colors
    // Outgoing: noble violet Color(0xFF9333EA)
    // Incoming: smoky dark graphite Color(0xFF18181C).copy(alpha = 0.88f)
    val bubbleColor = if (hasCustomWallpaper) {
        if (message.is_outgoing) wallpaper.bubbleOutgoing else wallpaper.bubbleIncoming
    } else {
        if (message.is_outgoing) Color(0xFF9333EA) else Color(0xFF18181C).copy(alpha = 0.90f)
    }

    val textColor = Color.White

    val metaColor = if (message.is_outgoing) {
        Color.White.copy(alpha = 0.75f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    val bubbleShape = remember(message.is_outgoing, sequence) {
        getBubbleShape(message.is_outgoing, sequence)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.is_outgoing) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffsetAnim.value >= thresholdPx) {
                                onSwipeReply()
                            }
                            hasTriggeredHaptic = false
                            scope.launch {
                                dragOffsetAnim.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            hasTriggeredHaptic = false
                            scope.launch {
                                dragOffsetAnim.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val current = dragOffsetAnim.value
                            if (dragAmount > 0 || current > 0) {
                                val resistance = (1f - (current / maxDragPx).coerceIn(0f, 0.8f))
                                val newOffset = (current + dragAmount * resistance).coerceIn(0f, maxDragPx)
                                scope.launch { dragOffsetAnim.snapTo(newOffset) }

                                if (newOffset >= thresholdPx && !hasTriggeredHaptic) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasTriggeredHaptic = true
                                } else if (newOffset < thresholdPx && hasTriggeredHaptic) {
                                    hasTriggeredHaptic = false
                                }
                            }
                        }
                    )
                }
                .offset { IntOffset(dragOffsetAnim.value.roundToInt(), 0) },
            contentAlignment = if (message.is_outgoing) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            // Spring-revealed circular reply icon indicator behind the bubble
            if (dragOffsetAnim.value > 8f) {
                val iconOffsetXPx = (dragOffsetAnim.value - with(density) { 42.dp.toPx() }).coerceAtLeast(0f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(iconOffsetXPx.roundToInt(), 0) }
                        .scale(replyIconScale)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (dragOffsetAnim.value >= thresholdPx)
                                Color(0xFF9333EA)
                            else
                                Color(0xFF22222C)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = "Відповісти",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isRoundVideo) {
                VideoNoteBubble(
                    mediaPath = localMediaPath,
                    durationSeconds = message.duration ?: 15,
                    isOutgoing = message.is_outgoing
                )
            } else if (isSticker) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .combinedClickable(
                            onClick = { onShowContextMenu(message) },
                            onLongClick = { onShowContextMenu(message) }
                        )
                ) {
                    if (message.reply_to_id != null) {
                        ReplyQuoteBadge(
                            replyToMessage = repliedMessage,
                            replyToId = message.reply_to_id,
                            isOutgoing = message.is_outgoing,
                            onClick = { onReplyQuoteClick(message.reply_to_id) },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    val stickerFile = remember(localMediaPath) {
                        if (!localMediaPath.isNullOrEmpty()) File(localMediaPath!!) else null
                    }

                    if (stickerFile != null && stickerFile.exists() && stickerFile.length() > 0) {
                        AsyncImage(
                            model = stickerFile,
                            contentDescription = "Telegram Стікер",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (message.text.isNotBlank()) {
                                Text(
                                    text = message.text,
                                    fontSize = 42.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color(0xFFA855F7),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }

                    // Floating timestamp pill over transparent sticker
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp, end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedTime,
                                fontSize = 10.sp,
                                color = Color.White
                            )

                            if (isChannel) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = "Перегляди",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = viewsCount,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            if (message.is_outgoing) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (message.is_read) "✔✔" else "✔",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // Regular Cyberpunk Glass Bubble
                Surface(
                    shape = bubbleShape,
                    color = bubbleColor,
                    border = if (!message.is_outgoing) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)) else null,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(bubbleShape)
                        .combinedClickable(
                            onClick = { onShowContextMenu(message) },
                            onLongClick = { onShowContextMenu(message) }
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Group Chat Sender Name
                        if (!message.is_outgoing && chat.chat_type in listOf("group", "supergroup") && message.sender_name.isNotBlank()) {
                            Text(
                                text = message.sender_name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = getSenderColor(message.sender_id),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Quoted message inside bubble if reply_to_id is present (with thumbnail!)
                        if (message.reply_to_id != null) {
                            ReplyQuoteBadge(
                                replyToMessage = repliedMessage,
                                replyToId = message.reply_to_id,
                                isOutgoing = message.is_outgoing,
                                onClick = { onReplyQuoteClick(message.reply_to_id) },
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Anti-delete notification badge
                        if (message.is_deleted) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Видалено",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Видалено співрозмовником",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5252)
                                    )
                                }
                            }
                        }

                        // Anti-edit previous version preview
                        if (message.original_text != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Відредаговано",
                                    tint = metaColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Було: ${message.original_text}",
                                    fontSize = 11.sp,
                                    color = metaColor
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // Photo preview
                        if (message.media_type == "photo") {
                            if (!localMediaPath.isNullOrEmpty()) {
                                val photoFile = remember(localMediaPath) { File(localMediaPath!!) }
                                AsyncImage(
                                    model = photoFile,
                                    contentDescription = "Фотографія",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 160.dp, max = 240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onMediaClick(message.copy(media_path = localMediaPath)) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = Color(0xFFA855F7),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Завантаження фото...",
                                            fontSize = 11.sp,
                                            color = metaColor
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Video message card
                        if (message.media_type == "video") {
                            VideoMessageCard(
                                mediaPath = localMediaPath,
                                durationSeconds = message.duration,
                                onVideoClick = { onMediaClick(message.copy(media_path = localMediaPath)) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Audio player
                        if (message.media_type == "audio") {
                            VoiceMessageBubble(
                                mediaPath = localMediaPath,
                                durationSeconds = message.duration,
                                isOutgoing = message.is_outgoing,
                                label = message.text.ifBlank { "Аудіо" }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Location map card
                        if (isLocation) {
                            LocationMessageCard()
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Contact card
                        if (isContact) {
                            ContactMessageCard()
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Poll
                        if (isPoll) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Poll,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[Опитування]",
                                    fontSize = 13.sp,
                                    color = textColor
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Voice message player
                        if (message.media_type == "voice") {
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
                                    tint = textColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message.text.ifBlank { "Файл документу" },
                                    fontSize = 13.sp,
                                    color = textColor,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Time + Read Indicator Composable Slot
                        val timeRow: @Composable () -> Unit = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                // Anti-Delete label beside time if deleted
                                if (message.is_deleted) {
                                    Text(
                                        text = "🗑️ ",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFF5252)
                                    )
                                }

                                Text(
                                    text = formattedTime,
                                    fontSize = 11.sp,
                                    color = if (message.is_deleted) Color(0xFFFF5252) else metaColor
                                )

                                if (isChannel) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = "Перегляди",
                                        tint = metaColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = viewsCount,
                                        fontSize = 10.sp,
                                        color = metaColor
                                    )
                                }

                                if (message.is_outgoing) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (message.is_read) "✔✔" else "✔",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Inline Timestamp Layout: Embedded in the last line if there's space
                        val isRichSpecial = message.media_type in listOf("voice", "audio", "video") || isPoll || isLocation || isContact
                        if (!isRichSpecial && message.text.isNotBlank()) {
                            InlineMessageTextWithTime(
                                text = message.text,
                                fontSize = chatFontSize,
                                textColor = textColor,
                                timeContent = timeRow
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                timeRow()
                            }
                        }
                    }
                }
            }
        }

        // Active reaction badge on the bubble
        if (selectedReaction != null) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF9333EA),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(top = 2.dp, start = 6.dp, end = 6.dp)
                    .clickable { selectedReaction = null }
            ) {
                Text(
                    text = "$selectedReaction 1",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
