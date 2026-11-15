package org.yumigram.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.yumigram.app.bridge.ChatModel
import java.io.File

private val STORY_PREVIEWS = listOf(
    "Сьогодні, 10:45",
    "Вчора, 18:20",
    "2 дні тому"
)

private val GIFTS = listOf(
    Pair("🧸", "Плюшевий ведмедик"),
    Pair("🌹", "Червона троянда"),
    Pair("🚀", "Космічна ракета"),
    Pair("🎁", "Святковий подарунок"),
    Pair("💎", "Діамант"),
    Pair("👑", "Золота корона")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullUserProfileDialog(
    chat: ChatModel,
    onDismiss: () -> Unit,
    onCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var selectedMediaTab by remember { mutableStateOf(0) }
    var isPlayingMusic by remember { mutableStateOf(false) }
    var userNote by remember { mutableStateOf("") }
    var isEditingNote by remember { mutableStateOf(false) }
    var viewingStory by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрити")
                    }
                    Text(
                        text = if (chat.chat_type == "user") "Профіль контакту" else "Інформація про ${chat.chat_type}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Telegram Info", "ID: ${chat.id} · @${chat.username ?: chat.title}"))
                        Toast.makeText(context, "Дані контакту скопійовано!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Поділитись")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Card with Avatar & Story Ring
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val storyBrush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFFE040FB),
                                    Color(0xFF2979FF),
                                    Color(0xFF00E676),
                                    Color(0xFFFF9100),
                                    Color(0xFFE040FB)
                                )
                            )

                            // Avatar with Telegram Stories Ring
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .border(3.dp, storyBrush, CircleShape)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .clickable { viewingStory = "Історія користувача ${chat.title}" },
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarFile = remember(chat.avatar_path) {
                                    if (!chat.avatar_path.isNullOrEmpty()) File(chat.avatar_path) else null
                                }

                                if (avatarFile != null && avatarFile.exists() && avatarFile.length() > 0) {
                                    AsyncImage(
                                        model = avatarFile,
                                        contentDescription = chat.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = chat.title.take(1).uppercase(),
                                            fontSize = 42.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = chat.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Text(
                                text = if (chat.chat_type == "user") "в мережі" else "${chat.chat_type.uppercase()} · ${chat.unread_count + 12} учасників",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Stories Pills
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                STORY_PREVIEWS.forEachIndexed { idx, time ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { viewingStory = "Історія #$idx (${chat.title})" }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Історія $time",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons Row (Call, Video, Mute, Search)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileActionButton(
                                icon = Icons.Rounded.Call,
                                title = "Дзвінок",
                                onClick = onCallClick
                            )
                            ProfileActionButton(
                                icon = Icons.Rounded.Videocam,
                                title = "Відео",
                                onClick = onVideoCallClick
                            )
                            ProfileActionButton(
                                icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                title = if (isMuted) "Вимкнено" else "Звук",
                                onClick = {
                                    isMuted = !isMuted
                                    Toast.makeText(context, if (isMuted) "Сповіщення вимкнено" else "Сповіщення увімкнено", Toast.LENGTH_SHORT).show()
                                }
                            )
                            ProfileActionButton(
                                icon = Icons.Rounded.Search,
                                title = "Пошук",
                                onClick = onSearchClick
                            )
                        }
                    }

                    // DETAILED INFO SECTION (Pixel OS Material You)
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ІНФОРМАЦІЯ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Username
                                DetailTile(
                                    icon = Icons.Rounded.AlternateEmail,
                                    label = "Ім'я користувача",
                                    value = if (!chat.username.isNullOrEmpty()) "@${chat.username}" else "@id_${kotlin.math.abs(chat.id)}",
                                    onCopy = {
                                        val u = if (!chat.username.isNullOrEmpty()) "@${chat.username}" else "@id_${kotlin.math.abs(chat.id)}"
                                        copyToClipboard(context, "Юзернейм", u)
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                // TG ID
                                DetailTile(
                                    icon = Icons.Rounded.Fingerprint,
                                    label = "Telegram ID",
                                    value = "${kotlin.math.abs(chat.id)}",
                                    onCopy = { copyToClipboard(context, "Telegram ID", "${kotlin.math.abs(chat.id)}") }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                // Phone Number
                                DetailTile(
                                    icon = Icons.Rounded.Phone,
                                    label = "Номер телефону",
                                    value = "+380 68 194 05 05",
                                    onCopy = { copyToClipboard(context, "Номер телефону", "+380 68 194 05 05") }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                // Bio / About
                                DetailTile(
                                    icon = Icons.Rounded.Info,
                                    label = "Про себе (Bio)",
                                    value = "🇺🇦 Офіційний контакт · Yumigram Pixel OS Experience",
                                    onCopy = { copyToClipboard(context, "Bio", "🇺🇦 Офіційний контакт · Yumigram Pixel OS Experience") }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                // Birthday
                                DetailTile(
                                    icon = Icons.Rounded.Cake,
                                    label = "День народження",
                                    value = "15 листопада 🎂",
                                    onCopy = { copyToClipboard(context, "День народження", "15 листопада") }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                // Personal Channel
                                DetailTile(
                                    icon = Icons.Rounded.Campaign,
                                    label = "Персональний канал",
                                    value = "📢 Yumigram Insider (14.8k підписників)",
                                    onCopy = { copyToClipboard(context, "Канал", "https://t.me/yumigram_official") }
                                )
                            }
                        }
                    }

                    // PROFILE MUSIC & PLAYER (Музика в профілі)
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "МУЗИКА В ПРОФІЛІ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(onClick = { isPlayingMusic = !isPlayingMusic }) {
                                            Icon(
                                                imageVector = if (isPlayingMusic) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = "Грати музику",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Океан Ельзи — Без бою",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (isPlayingMusic) "Відтворюється зараз · 01:42 / 04:19" else "Закріплений трек користувача",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // GIFTS SECTION (Подарунки з Telegram)
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ПОДАРУНКИ (STAR GIFTS)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${GIFTS.size} отримано",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    GIFTS.forEach { (emoji, title) ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.clickable {
                                                Toast.makeText(context, "Подарунок: $title", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(text = emoji, fontSize = 32.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // USER NOTE SECTION (Примітка)
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ОСОБИСТА ПРИМІТКА",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (isEditingNote) {
                                    OutlinedTextField(
                                        value = userNote,
                                        onValueChange = { userNote = it },
                                        placeholder = { Text("Введіть примітку (видима лише вам)...") },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { isEditingNote = false },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Зберегти")
                                    }
                                } else {
                                    Text(
                                        text = userNote.ifBlank { "Торкніться, щоб додати особисту примітку до цього контакту" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (userNote.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isEditingNote = true }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // SHARED MEDIA TABS SECTION
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val tabs = listOf("Медіа (24)", "Файли (8)", "Посилання (15)", "Музика (12)", "Опитування (3)")

                                TabRow(
                                    selectedTabIndex = selectedMediaTab,
                                    containerColor = Color.Transparent
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = selectedMediaTab == index,
                                            onClick = { selectedMediaTab = index },
                                            text = { Text(title, fontSize = 11.sp, maxLines = 1) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                when (selectedMediaTab) {
                                    0 -> { // Media Photos
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            (1..6).forEach { num ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(76.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                    1 -> { // Files
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("Document_Project.pdf (2.4 MB)", "Archive_Assets.zip (14.8 MB)", "Release_Notes.docx (120 KB)").forEach { file ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(text = file, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                    2 -> { // Links
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("https://github.com/fuckramochka/yumigram", "https://t.me/telegram", "https://my.telegram.org").forEach { link ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(text = link, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                    3 -> { // Music
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("Track 01 - Intro.mp3 (3:20)", "Live Acoustic Performance.flac (4:50)", "Voice Memo #14.ogg (0:45)").forEach { audio ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(text = audio, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                    4 -> { // Polls
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(text = "📊 Опитування: 'Яку функцію додати наступною?'", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = "1. Спільні папки (78% голосів)", fontSize = 12.sp)
                                            Text(text = "2. Відеодзвінки (22% голосів)", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Story View Dialog
    if (viewingStory != null) {
        Dialog(onDismissRequest = { viewingStory = null }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewingStory!!,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { viewingStory = null }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Закрити", tint = Color.White)
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✨ Перегляд історії в стилі Telegram Stories ✨",
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = { viewingStory = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Закрити історію")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DetailTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Копіювати",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label скопійовано!", Toast.LENGTH_SHORT).show()
}
