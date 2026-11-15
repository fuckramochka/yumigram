package org.yumigram.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.yumigram.app.bridge.MessageModel
import java.io.File

/**
 * Amegram / exteraGram style Quote Badge with thin vertical violet/pink line,
 * author name, quote mark ❞, and a miniature thumbnail on the right if media is quoted.
 */
@Composable
fun ReplyQuoteBadge(
    replyToMessage: MessageModel?,
    replyToId: Int,
    isOutgoing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = if (isOutgoing) Color(0xFFE879F9) else Color(0xFFA855F7)
    val backgroundColor = if (isOutgoing) {
        Color.Black.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    val authorName = remember(replyToMessage, replyToId) {
        replyToMessage?.sender_name?.takeIf { it.isNotBlank() } ?: "Повідомлення #$replyToId"
    }

    val hasMedia = replyToMessage?.media_type != null
    val mediaFile = remember(replyToMessage?.media_path) {
        if (!replyToMessage?.media_path.isNullOrEmpty()) File(replyToMessage.media_path!!) else null
    }

    val previewText = remember(replyToMessage) {
        if (replyToMessage == null) {
            "Перейти до оригіналу"
        } else if (replyToMessage.text.isNotBlank()) {
            replyToMessage.text
        } else {
            when (replyToMessage.media_type) {
                "photo" -> "Фотографія"
                "sticker" -> "Стікер"
                "voice" -> "Голосове повідомлення"
                "video_note", "round_video" -> "Відеоповідомлення"
                "video" -> "Відео"
                "audio" -> "Музика"
                "file" -> "Документ"
                else -> "Вкладення"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thin vertical violet/pink line
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = authorName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "❞",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor.copy(alpha = 0.8f)
                    )
                }

                Text(
                    text = previewText,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Miniature Thumbnail Preview on the right side if replying to media
            if (hasMedia) {
                Spacer(modifier = Modifier.width(8.dp))
                if (mediaFile != null && mediaFile.exists() && mediaFile.length() > 0) {
                    AsyncImage(
                        model = mediaFile,
                        contentDescription = "Прев'ю медіа",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(barColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (replyToMessage?.media_type) {
                            "photo" -> Icons.Rounded.Image
                            "video" -> Icons.Rounded.Videocam
                            "video_note", "round_video" -> Icons.Rounded.RadioButtonChecked
                            "sticker" -> Icons.Rounded.Star
                            "voice" -> Icons.Rounded.Mic
                            "audio" -> Icons.Rounded.MusicNote
                            else -> Icons.Rounded.AttachFile
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = barColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quote preview panel rendered right above the text input bar when replying to a message.
 */
@Composable
fun ReplyInputBarPreview(
    replyingTo: MessageModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = Color(0xFFA855F7)
    val mediaFile = remember(replyingTo.media_path) {
        if (!replyingTo.media_path.isNullOrEmpty()) File(replyingTo.media_path!!) else null
    }

    val snippetText = remember(replyingTo) {
        if (replyingTo.text.isNotBlank()) {
            replyingTo.text
        } else {
            when (replyingTo.media_type) {
                "photo" -> "Фотографія"
                "sticker" -> "Стікер"
                "voice" -> "Голосове повідомлення"
                "video_note", "round_video" -> "Відеоповідомлення"
                "video" -> "Відео"
                "audio" -> "Музика"
                "file" -> "Документ"
                else -> "Вкладення"
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E24).copy(alpha = 0.95f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Reply,
            contentDescription = "Відповідь",
            tint = barColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = replyingTo.sender_name.takeIf { it.isNotBlank() } ?: "Співрозмовник",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "❞",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor.copy(alpha = 0.8f)
                )
            }
            Text(
                text = snippetText,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Thumbnail on the right if media is present
        if (replyingTo.media_type != null) {
            Spacer(modifier = Modifier.width(6.dp))
            if (mediaFile != null && mediaFile.exists() && mediaFile.length() > 0) {
                AsyncImage(
                    model = mediaFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(barColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (replyingTo.media_type) {
                        "photo" -> Icons.Rounded.Image
                        "video" -> Icons.Rounded.Videocam
                        "video_note", "round_video" -> Icons.Rounded.RadioButtonChecked
                        "sticker" -> Icons.Rounded.Star
                        else -> Icons.Rounded.AttachFile
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = barColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Скасувати відповідь",
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
