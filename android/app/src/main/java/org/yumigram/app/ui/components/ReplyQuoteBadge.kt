package org.yumigram.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yumigram.app.bridge.MessageModel

/**
 * Renders an elegant quote badge inside a MessageBubble pointing to the replied message.
 * Clicking triggers a smooth animated scroll to the original message.
 */
@Composable
fun ReplyQuoteBadge(
    replyToMessage: MessageModel?,
    replyToId: Int,
    isOutgoing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = if (isOutgoing) Color(0xFF64B5F6) else MaterialTheme.colorScheme.primary
    val backgroundColor = if (isOutgoing) {
        Color.Black.copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    val authorName = remember(replyToMessage, replyToId) {
        replyToMessage?.sender_name?.takeIf { it.isNotBlank() } ?: "Повідомлення #$replyToId"
    }

    val previewText = remember(replyToMessage) {
        if (replyToMessage == null) {
            "Перейти до оригіналу"
        } else if (replyToMessage.text.isNotBlank()) {
            replyToMessage.text
        } else {
            when (replyToMessage.media_type) {
                "photo" -> "📷 Фотографія"
                "sticker" -> "⭐ Стікер"
                "voice" -> "🎤 Голосове повідомлення"
                "video_note", "round_video" -> "📹 Відеоповідомлення"
                "video" -> "🎥 Відео"
                "audio" -> "🎵 Музика"
                "file" -> "📁 Документ"
                else -> "Вкладення"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vertical colored line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = authorName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = previewText,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    val barColor = MaterialTheme.colorScheme.primary

    val snippetText = remember(replyingTo) {
        if (replyingTo.text.isNotBlank()) {
            replyingTo.text
        } else {
            when (replyingTo.media_type) {
                "photo" -> "📷 Фотографія"
                "sticker" -> "⭐ Стікер"
                "voice" -> "🎤 Голосове повідомлення"
                "video_note", "round_video" -> "📹 Відеоповідомлення"
                "video" -> "🎥 Відео"
                "audio" -> "🎵 Музика"
                "file" -> "📁 Документ"
                else -> "Вкладення"
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = replyingTo.sender_name.takeIf { it.isNotBlank() } ?: "Співрозмовник",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = snippetText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Скасувати відповідь",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
