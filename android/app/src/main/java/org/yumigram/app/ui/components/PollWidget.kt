package org.yumigram.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PollWidget(
    question: String,
    options: List<String> = listOf(
        "🔥 Оновлення дизайну Pixel OS",
        "🐱 Telegram Стікери та WebP",
        "🖼️ Шпалери чату з власної галереї",
        "🎵 Музичний плеєр та історія в профілі"
    ),
    isOutgoing: Boolean = false,
    onVoteOption: (Int) -> Unit = {}
) {
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var totalVotes by remember { mutableIntStateOf(142) }

    // Simulated distribution percentages
    val basePercentages = remember { listOf(45, 28, 17, 10) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(12.dp)
    ) {
        // Poll Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Poll,
                contentDescription = "Poll",
                tint = if (isOutgoing) Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = question.ifBlank { "Яка фіча Yumigram вам найбільше подобається?" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Анонімне опитування",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Options List
        options.forEachIndexed { index, optionText ->
            val isSelected = selectedOption == index
            val hasVoted = selectedOption != null
            val rawPercentage = if (hasVoted) {
                if (isSelected) (basePercentages.getOrElse(index) { 20 } + 3).coerceAtMost(100)
                else basePercentages.getOrElse(index) { 20 }
            } else 0

            val animatedProgress by animateFloatAsState(
                targetValue = if (hasVoted) rawPercentage / 100f else 0f,
                animationSpec = tween(durationMillis = 500),
                label = "progress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (selectedOption == null) {
                            selectedOption = index
                            totalVotes += 1
                            onVoteOption(index)
                        }
                    }
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                // Percentage fill bar
                if (hasVoted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .matchParentSize()
                            .background(
                                if (isSelected) Color(0xFF42A5F5).copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.15f)
                            )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Checkbox / Radio
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(
                                    2.dp,
                                    if (isSelected) Color(0xFF42A5F5) else Color.White.copy(alpha = 0.6f),
                                    CircleShape
                                )
                                .background(if (isSelected) Color(0xFF42A5F5) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = optionText,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    if (hasVoted) {
                        Text(
                            text = "$rawPercentage%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF90CAF9) else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Total votes count
        Text(
            text = "$totalVotes голосів",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.End)
        )
    }
}
