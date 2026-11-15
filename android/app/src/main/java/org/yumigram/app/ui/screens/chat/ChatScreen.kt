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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

private val POPULAR_REACTIONS = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "👏", "🎉")

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
    // In reverseLayout = true:
    // Older message (chronologically before, visually above) is at currentIndex + 1
    val prevOlder = messages.getOrNull(currentIndex + 1)
    // Newer message (chronologically after, visually below) is at currentIndex - 1
    val nextNewer = messages.getOrNull(currentIndex - 1)

    fun isSameSeries(a: MessageModel, b: MessageModel?): Boolean {
        if (b == null) return false
        if (a.is_outgoing != b.is_outgoing) return false
        if (!a.is_outgoing && a.sender_id != b.sender_id) return false
        // Disallow grouping across major time gap (> 5 minutes = 300 seconds)
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
        // Outgoing: Author is on RIGHT side (topEnd & bottomEnd)
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
        // Incoming: Author is on LEFT side (topStart & bottomStart)
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

    // 5. Predictive Back Gesture Handler (Android 14+ / Pixel OS Expressive)
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

    // Auto-scroll to newly sent or received message
    LaunchedEffect(messages.firstOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Predictive back card morphing values
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = MaterialTheme.colorScheme.onSurface
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
                                // Chat Avatar with Generative Monet Fallback
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
                                                color = monetPalette.onColor
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
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (chat.chat_type) {
                                            "user", "dialog" -> "Особистий чат"
                                            "group", "supergroup" -> "Група"
                                            "channel" -> "Канал"
                                            else -> "Чат"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Меню",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("👤 Профіль контакту") },
                                    onClick = {
                                        showMenu = false
                                        showChatProfile = true
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("🖼️ Шпалери з галереї") },
                                    onClick = {
                                        showMenu = false
                                        galleryLauncher.launch("image/*")
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) }
                                )
                                if (customWallpaperUri != null) {
                                    DropdownMenuItem(
                                        text = { Text("❌ Скинути шпалери галереї") },
                                        onClick = {
                                            showMenu = false
                                            ThemeManager.setCustomWallpaperUri(context, null)
                                            Toast.makeText(context, "Шпалери скинуто на стандартні", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )

                    // Pinned Message Bar (No hard lines)
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
                                color = MaterialTheme.colorScheme.surfaceContainerLow
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
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = "Pinned",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Закріплене повідомлення",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = pinned.text.ifBlank { "[Вкладення]" },
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    // Channels: Broadcast mode bottom bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
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
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                    // 4. The Input Pill bottom panel (Material 3 Expressive)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Emoji / Stickers Toggle
                                IconButton(
                                    onClick = { showEmojiStickerTray = !showEmojiStickerTray },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showEmojiStickerTray) Icons.Rounded.Keyboard else Icons.Rounded.Mood,
                                        contentDescription = "Емодзі та стікери",
                                        tint = if (showEmojiStickerTray) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Attach Button (📎)
                                IconButton(
                                    onClick = { showAttachmentMenu = true },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AttachFile,
                                        contentDescription = "Вкладення",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // The Input Pill: RoundedCornerShape(26.dp) with surfaceContainerHighest
                                TextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = {
                                        Text(
                                            "Повідомлення...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Morphing Action Button (Mic ⟷ Send) with Spring Physics
                                AnimatedContent(
                                    targetState = inputText.isNotBlank(),
                                    transitionSpec = {
                                        (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                fadeIn() +
                                                rotateIn(initialAngle = -45f))
                                            .togetherWith(
                                                scaleOut() + fadeOut() + rotateOut(targetAngle = 45f)
                                            )
                                    },
                                    label = "ActionMorph"
                                ) { hasText ->
                                    if (hasText) {
                                        FilledIconButton(
                                            onClick = {
                                                dispatchMessage(inputText)
                                                inputText = ""
                                            },
                                            modifier = Modifier.size(48.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
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
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Mic,
                                                    contentDescription = "Голосове повідомлення",
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            FilledTonalIconButton(
                                                onClick = { showRecordVideoNote = true },
                                                modifier = Modifier.size(42.dp),
                                                shape = CircleShape,
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.RadioButtonChecked,
                                                    contentDescription = "Кружечок",
                                                    modifier = Modifier.size(22.dp)
                                                )
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
                MaterialTheme.colorScheme.surfaceContainerLowest
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
                    // Gentle dark tint overlay for readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }

                // 2. Smart Bubbles with sequence chaining
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

                        // Clustered spacing: closer (3.dp) within same series, wider (10.dp) between bursts
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
    }

    // Context Menu Bottom Sheet on Long Press
    if (selectedMessageForMenu != null) {
        val targetMsg = selectedMessageForMenu!!
        val isPinned = pinnedMessage?.id == targetMsg.id

        ModalBottomSheet(
            onDismissRequest = { selectedMessageForMenu = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Quick Reactions Row
                Text(
                    text = "Швидка реакція",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items(POPULAR_REACTIONS.size) { rIdx ->
                        val reaction = POPULAR_REACTIONS[rIdx]
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    scope.launch {
                                        YumigramBridge.sendReaction(chat.id, targetMsg.id, reaction)
                                    }
                                    selectedMessageForMenu = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = reaction, fontSize = 22.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 💬 Відповісти (Reply)
                ListItem(
                    headlineContent = { Text("Відповісти", fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Reply,
                            contentDescription = "Відповісти",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            replyingTo = targetMsg
                            selectedMessageForMenu = null
                        }
                )

                // ➡️ Переслати (Forward)
                ListItem(
                    headlineContent = { Text("Переслати", fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Переслати",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            messageToForward = targetMsg
                            selectedMessageForMenu = null
                        }
                )

                // 📌 Закріпити / Відкріпити (Pin/Unpin)
                ListItem(
                    headlineContent = {
                        Text(if (isPinned) "Відкріпити" else "Закріпити", fontWeight = FontWeight.Medium)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = if (isPinned) "Відкріпити" else "Закріпити",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val willUnpin = isPinned
                            pinnedMessage = if (willUnpin) null else targetMsg
                            scope.launch {
                                YumigramBridge.pinMessage(chat.id, targetMsg.id, unpin = willUnpin)
                            }
                            Toast.makeText(
                                context,
                                if (willUnpin) "Повідомлення відкріплено" else "Повідомлення закріплено",
                                Toast.LENGTH_SHORT
                            ).show()
                            selectedMessageForMenu = null
                        }
                )

                // 📋 Копіювати текст (Copy)
                ListItem(
                    headlineContent = { Text("Копіювати текст", fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Копіювати",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Message Text", targetMsg.text.ifBlank { targetMsg.media_type ?: "" })
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Текст скопійовано", Toast.LENGTH_SHORT).show()
                            selectedMessageForMenu = null
                        }
                )

                // 🗑️ Видалити (Delete)
                ListItem(
                    headlineContent = { Text("Видалити", fontWeight = FontWeight.Medium, color = DeletedRed) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Видалити",
                            tint = DeletedRed
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            scope.launch {
                                YumigramBridge.nativeMarkDeleted(chat.id, targetMsg.id)
                            }
                            Toast.makeText(context, "Повідомлення видалено", Toast.LENGTH_SHORT).show()
                            selectedMessageForMenu = null
                        }
                )
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

    // Round Video Note Camera Dialog (Кружечки)
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

// 2. Custom Layout: Inline Text with Embedded Timestamp & Read Indicators
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

    // 2. Spring Physics Swipe-To-Reply Animatable
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

    // Download media if needed
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

    // Tonal Colors for Bubbles
    val bubbleColor = if (hasCustomWallpaper) {
        if (message.is_outgoing) wallpaper.bubbleOutgoing else wallpaper.bubbleIncoming
    } else {
        if (message.is_outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val textColor = if (hasCustomWallpaper) {
        Color.White
    } else {
        if (message.is_outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    }

    val metaColor = if (hasCustomWallpaper) {
        Color.White.copy(alpha = 0.7f)
    } else {
        if (message.is_outgoing) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }

    // Dynamic Corner Geometry based on MessageSequence
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
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = "Відповісти",
                        tint = if (dragOffsetAnim.value >= thresholdPx)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isRoundVideo) {
                // Telegram Video Circle (Кружечки) rendered WITHOUT rectangular bubble
                VideoNoteBubble(
                    mediaPath = localMediaPath,
                    durationSeconds = message.duration ?: 15,
                    isOutgoing = message.is_outgoing
                )
            } else if (isSticker) {
                // Telegram Stickers rendered WITHOUT colored bubble background
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
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                    }

                    // Floating timestamp pill over transparent sticker
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
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
                                val indicator = if (message.is_read) indicatorStyle.read else indicatorStyle.sent
                                Text(
                                    text = indicator,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64B5F6)
                                )
                            }
                        }
                    }
                }
            } else {
                // Regular Smart Message Bubble with Dynamic Rounded Corners
                Surface(
                    shape = bubbleShape,
                    color = bubbleColor,
                    shadowElevation = 1.dp,
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

                        // Quoted message inside bubble if reply_to_id is present
                        if (message.reply_to_id != null) {
                            ReplyQuoteBadge(
                                replyToMessage = repliedMessage,
                                replyToId = message.reply_to_id,
                                isOutgoing = message.is_outgoing,
                                onClick = { onReplyQuoteClick(message.reply_to_id) },
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Anti-delete notification
                        if (message.is_deleted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Видалено",
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
                                            color = MaterialTheme.colorScheme.primary,
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
                                Text(
                                    text = formattedTime,
                                    fontSize = 11.sp,
                                    color = metaColor
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
                                    val indicator = if (message.is_read) indicatorStyle.read else indicatorStyle.sent
                                    Text(
                                        text = indicator,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasCustomWallpaper) Color(0xFF64B5F6) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // 2. Inline Timestamp Layout: Embedded in the last line if there's space
                        val isRichSpecial = message.media_type in listOf("voice", "audio", "video") || isPoll || isLocation || isContact
                        if (!isRichSpecial && message.text.isNotBlank()) {
                            InlineMessageTextWithTime(
                                text = message.text,
                                fontSize = chatFontSize,
                                textColor = textColor,
                                timeContent = timeRow
                            )
                        } else {
                            // Pure media or special card: time row aligned to end
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
