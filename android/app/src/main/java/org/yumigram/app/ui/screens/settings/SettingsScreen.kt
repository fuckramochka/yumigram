package org.yumigram.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yumigram.app.bridge.YumigramBridge

enum class IndicatorStyle(val title: String, val sent: String, val read: String) {
    DOTS("Крапки (Telegram)", "•", "••"),
    CHECKS("Галочки (WhatsApp style)", "✓", "✓✓"),
    HEARTS("Сердечка (Ayu style)", "♡", "♥"),
    SLASHES("Рисочки", "/", "//")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentIndicator: IndicatorStyle,
    onIndicatorChanged: (IndicatorStyle) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ghostEnabled by remember { mutableStateOf(true) }
    var hideRead by remember { mutableStateOf(true) }
    var hideTyping by remember { mutableStateOf(true) }
    var hideOnline by remember { mutableStateOf(true) }

    var antiDeleteEnabled by remember { mutableStateOf(true) }
    var antiEditEnabled by remember { mutableStateOf(true) }

    var showIndicatorDialog by remember { mutableStateOf(false) }

    fun updateGhost() {
        if (ghostEnabled) {
            YumigramBridge.setGhostConfig(hideRead, hideTyping, hideOnline)
        } else {
            YumigramBridge.setGhostConfig(false, false, false)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Налаштування",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // GHOST MODE SECTION
            item {
                Text(
                    text = "GHOST MODE (ПРИВИД)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
                        SettingsToggleRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "Ghost Mode активовано",
                            subtitle = "Комплексний режим невидимості у мережі",
                            checked = ghostEnabled,
                            onCheckedChange = {
                                ghostEnabled = it
                                updateGhost()
                            }
                        )

                        if (ghostEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingsToggleRow(
                                icon = Icons.Rounded.MarkChatRead,
                                title = "Приховувати прочитання",
                                subtitle = "Не надсилати звіт про прочитання повідомлень",
                                checked = hideRead,
                                onCheckedChange = {
                                    hideRead = it
                                    updateGhost()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingsToggleRow(
                                icon = Icons.Rounded.EditNote,
                                title = "Приховувати статус друку",
                                subtitle = "Співрозмовник не бачить, коли ви пишете",
                                checked = hideTyping,
                                onCheckedChange = {
                                    hideTyping = it
                                    updateGhost()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingsToggleRow(
                                icon = Icons.Rounded.PersonOff,
                                title = "Приховувати онлайн статус",
                                subtitle = "Не оновлювати останній час відвідування",
                                checked = hideOnline,
                                onCheckedChange = {
                                    hideOnline = it
                                    updateGhost()
                                }
                            )
                        }
                    }
                }
            }

            // ANTI-DELETE & ANTI-EDIT SECTION
            item {
                Text(
                    text = "БЕЗПЕКА ТА ЗБЕРЕЖЕННЯ (AYUGRAM STYLE)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
                        SettingsToggleRow(
                            icon = Icons.Rounded.DeleteForever,
                            title = "Anti-Delete",
                            subtitle = "Зберігати видалені співрозмовником повідомлення у локальній базі",
                            checked = antiDeleteEnabled,
                            onCheckedChange = { antiDeleteEnabled = it }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        SettingsToggleRow(
                            icon = Icons.Rounded.HistoryEdu,
                            title = "Anti-Edit",
                            subtitle = "Фіксувати початковий текст до редагування",
                            checked = antiEditEnabled,
                            onCheckedChange = { antiEditEnabled = it }
                        )
                    }
                }
            }

            // CUSTOMIZATION SECTION
            item {
                Text(
                    text = "КАСТОМІЗАЦІЯ ІНТЕРФЕЙСУ (PIXEL MD3)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showIndicatorDialog = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Значок прочитання повідомлень",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${currentIndicator.title} (${currentIndicator.sent} / ${currentIndicator.read})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // APP INFO & LOGOUT
            item {
                Text(
                    text = "СИСТЕМА ТА АКАУНТ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ядро Yumigram Core",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Rust grammers MTProto v0.12.0 + SQLite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Вийти з акаунта Telegram", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showIndicatorDialog) {
        AlertDialog(
            onDismissRequest = { showIndicatorDialog = false },
            title = { Text("Виберіть стиль значка прочитання") },
            text = {
                Column {
                    IndicatorStyle.values().forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onIndicatorChanged(style)
                                    showIndicatorDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentIndicator == style),
                                onClick = {
                                    onIndicatorChanged(style)
                                    showIndicatorDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = style.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Відправлено: ${style.sent}  •  Прочитано: ${style.read}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIndicatorDialog = false }) {
                    Text("Закрити")
                }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
