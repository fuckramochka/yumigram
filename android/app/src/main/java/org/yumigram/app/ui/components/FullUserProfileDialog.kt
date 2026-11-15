package org.yumigram.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.yumigram.app.bridge.ChatModel
import org.yumigram.app.ui.theme.getMonetAvatarPalette
import java.io.File

enum class SharedMediaType(val title: String) {
    PHOTOS("Фото"),
    VIDEOS("Відео"),
    FILES("Файли"),
    VOICE("Голосові"),
    GIFS("GIF"),
    GROUPS("Групи")
}

@Composable
fun FullUserProfileDialog(
    chat: ChatModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var selectedMediaTab by remember { mutableStateOf(SharedMediaType.PHOTOS) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color(0xFF0C0C10)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Профіль",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row {
                        IconButton(onClick = {
                            copyToClipboard(context, "Дані контакту", "@${chat.username ?: chat.title} · ID: ${chat.id}")
                        }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Поділитись", tint = Color.White)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Big circular avatar in the center with name and status
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(CircleShape),
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
                                    val monetPalette = remember(chat.id) { getMonetAvatarPalette(chat.id) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Brush.linearGradient(monetPalette.gradient)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = chat.title.take(1).uppercase(),
                                            fontSize = 46.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = monetPalette.onColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Status: «активність — нещодавно» for users
                            Text(
                                text = when (chat.chat_type) {
                                    "user", "dialog" -> "активність — нещодавно"
                                    "bot" -> "бот Telegram"
                                    "channel" -> "публічний канал"
                                    "group", "supergroup" -> "груповий чат"
                                    else -> "активність — нещодавно"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA855F7),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 2. Quick action buttons row: «Написати», «Без звуку», «Виклик», «Відео»
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileActionButton(
                                icon = Icons.Rounded.ChatBubble,
                                label = "Написати",
                                containerColor = Color(0xFF9333EA),
                                onClick = onDismiss
                            )
                            ProfileActionButton(
                                icon = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                label = if (isMuted) "Звук вимк" else "Без звуку",
                                containerColor = Color(0xFF1E1E26),
                                onClick = {
                                    isMuted = !isMuted
                                    Toast.makeText(context, if (isMuted) "Сповіщення вимкнено" else "Сповіщення увімкнено", Toast.LENGTH_SHORT).show()
                                }
                            )
                            ProfileActionButton(
                                icon = Icons.Rounded.Call,
                                label = "Виклик",
                                containerColor = Color(0xFF1E1E26),
                                onClick = {
                                    Toast.makeText(context, "Голосовий виклик Telegram...", Toast.LENGTH_SHORT).show()
                                }
                            )
                            ProfileActionButton(
                                icon = Icons.Rounded.Videocam,
                                label = "Відео",
                                containerColor = Color(0xFF1E1E26),
                                onClick = {
                                    Toast.makeText(context, "Відеодзвінок Telegram...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // 3. Information Block: Username @username with QR code, Telegram ID, Bio
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF16161C),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ІНФОРМАЦІЯ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFA855F7)
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Username with QR code
                                if (!chat.username.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { copyToClipboard(context, "Юзернейм", "@${chat.username}") }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF22222C)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AlternateEmail,
                                                contentDescription = null,
                                                tint = Color(0xFFA855F7),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Ім'я користувача", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                            Text(text = "@${chat.username}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        }
                                        IconButton(onClick = {
                                            Toast.makeText(context, "QR-код для @${chat.username}", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Rounded.QrCode, contentDescription = "QR-код", tint = Color(0xFFA855F7))
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                                }

                                // Telegram ID
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { copyToClipboard(context, "Telegram ID", "${chat.id}") }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22222C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Fingerprint,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Telegram ID", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                        Text(text = "${chat.id}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Копіювати", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

                                // Description / Bio
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22222C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Про себе / Опис", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                        Text(
                                            text = "Користувач Yumigram · Привіт, я використовую Telegram!",
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Shared Media Section: horizontal tabs («Фото», «Відео», «Файли», «Голосові», «GIF», «Групи»)
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "СПІЛЬНІ МЕДІА",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFA855F7),
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SharedMediaType.values().forEach { mediaTab ->
                                    val isSelected = selectedMediaTab == mediaTab
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) Color(0xFF9333EA) else Color(0xFF1E1E26),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clickable { selectedMediaTab = mediaTab }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                            Text(
                                                text = mediaTab.title,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Shared media content placeholder
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF16161C),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = when (selectedMediaTab) {
                                                SharedMediaType.PHOTOS -> Icons.Rounded.Image
                                                SharedMediaType.VIDEOS -> Icons.Rounded.Videocam
                                                SharedMediaType.FILES -> Icons.Rounded.InsertDriveFile
                                                SharedMediaType.VOICE -> Icons.Rounded.Mic
                                                SharedMediaType.GIFS -> Icons.Rounded.Gif
                                                SharedMediaType.GROUPS -> Icons.Rounded.Group
                                            },
                                            contentDescription = null,
                                            tint = Color(0xFFA855F7).copy(alpha = 0.5f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Немає збережених ${selectedMediaTab.title.lowercase()} у цьому діалозі",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
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
}

@Composable
private fun ProfileActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable { onClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label скопійовано!", Toast.LENGTH_SHORT).show()
}
