package org.yumigram.app.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.yumigram.app.ui.theme.ThemeManager

val PREVIEW_NAME_COLORS = listOf(
    Pair("Pixel Blue", Color(0xFF1A73E8)),
    Pair("Neon Cyan", Color(0xFF00E5FF)),
    Pair("Purple", Color(0xFF7C4DFF)),
    Pair("Emerald", Color(0xFF00C853)),
    Pair("Sunset Orange", Color(0xFFFF6D00)),
    Pair("Ruby Red", Color(0xFFD50000)),
    Pair("Hot Pink", Color(0xFFFF4081)),
    Pair("Rainbow", Color(0xFF9C27B0))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAndAppearanceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val currentFontSize by ThemeManager.fontSize.collectAsState()
    val currentNameColorIdx by ThemeManager.nameColorIndex.collectAsState()
    val inAppBrowser by ThemeManager.inAppBrowserEnabled.collectAsState()
    val bubbleRadius by ThemeManager.bubbleCornerRadius.collectAsState()
    val autoReplyEnabled by ThemeManager.autoReplyEnabled.collectAsState()
    val autoReplyMsg by ThemeManager.autoReplyMessage.collectAsState()

    var tempFontSize by remember { mutableFloatStateOf(currentFontSize) }
    var tempRadius by remember { mutableIntStateOf(bubbleRadius) }
    var tempAutoReplyMsg by remember { mutableStateOf(autoReplyMsg) }

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
                TopAppBar(
                    title = { Text("Налаштування чатів та вигляд", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрити")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            ThemeManager.setFontSize(context, tempFontSize)
                            ThemeManager.setBubbleCornerRadius(context, tempRadius)
                            ThemeManager.setAutoReplyMessage(context, tempAutoReplyMsg)
                            Toast.makeText(context, "Налаштування збережено!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }) {
                            Text("Зберегти", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // FONT SIZE SLIDER
                    item {
                        Text(
                            text = "РОЗМІР ШРИФТУ ПОВІДОМЛЕНЬ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Розмір тексту в чаті:", fontWeight = FontWeight.SemiBold)
                                    Text("${tempFontSize.toInt()} sp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }

                                Slider(
                                    value = tempFontSize,
                                    onValueChange = { tempFontSize = it },
                                    valueRange = 12f..24f,
                                    steps = 11,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Live text preview
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Привіт! Так виглядатиме текст повідомлення у чаті Yumigram.",
                                        fontSize = tempFontSize.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // NAME COLOR (TELEGRAM PREMIUM)
                    item {
                        Text(
                            text = "КОЛІР ІМЕНІ КОРИСТУВАЧА (PREMIUM)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Оберіть персональний стиль імені та цитат:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PREVIEW_NAME_COLORS.forEachIndexed { idx, (name, color) ->
                                        val isSelected = currentNameColorIdx == idx
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                ThemeManager.setNameColorIndex(context, idx)
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        if (isSelected) 3.dp else 0.dp,
                                                        MaterialTheme.colorScheme.onSurface,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // BUBBLE CORNER RADIUS
                    item {
                        Text(
                            text = "КУТИ ТА ФОРМА БУЛЬБАШОК",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Заокруглення кутів:", fontWeight = FontWeight.SemiBold)
                                    Text("$tempRadius dp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }

                                Slider(
                                    value = tempRadius.toFloat(),
                                    onValueChange = { tempRadius = it.toInt() },
                                    valueRange = 4f..28f,
                                    steps = 11,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Bubble preview
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(tempRadius.dp, tempRadius.dp, 4.dp, tempRadius.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Text(
                                            text = "Форма вашої бульбашки повідомлення",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // IN-APP BROWSER
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Вбудований браузер (In-App Browser)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Відкривати зовнішні посилання всередині Yumigram без виходу з програми", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = inAppBrowser,
                                        onCheckedChange = { ThemeManager.setInAppBrowserEnabled(context, it) }
                                    )
                                }
                            }
                        }
                    }

                    // CHAT AUTOMATION (BOT / AUTO-REPLY)
                    item {
                        Text(
                            text = "АВТОМАТИЗАЦІЯ ЧАТУ (ЧАТ-БОТ АСИСТЕНТ)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Автоматичні відповіді (Чат-бот)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Автоматично відповідати співрозмовникам за шаблоном", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = autoReplyEnabled,
                                        onCheckedChange = { ThemeManager.setAutoReplyEnabled(context, it) }
                                    )
                                }

                                if (autoReplyEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = tempAutoReplyMsg,
                                        onValueChange = { tempAutoReplyMsg = it },
                                        label = { Text("Шаблон автовідповіді бота") },
                                        minLines = 2,
                                        maxLines = 4,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}
