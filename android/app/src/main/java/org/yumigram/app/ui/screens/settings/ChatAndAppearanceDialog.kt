package org.yumigram.app.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.yumigram.app.ui.theme.ThemeManager

/**
 * Реальні налаштування вигляду чатів: розмір шрифту та кути бульбашок.
 * Обидва параметри зберігаються у SharedPreferences та застосовуються у MessageBubble.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAndAppearanceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val currentFontSize by ThemeManager.fontSize.collectAsState()
    val bubbleRadius by ThemeManager.bubbleCornerRadius.collectAsState()

    var tempFontSize by remember { mutableFloatStateOf(currentFontSize) }
    var tempRadius by remember { mutableIntStateOf(bubbleRadius) }

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

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}
