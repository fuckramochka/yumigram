package org.yumigram.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.YumigramBridge
import org.yumigram.app.ui.theme.*
import org.yumigram.app.updater.AppUpdateManager
import org.yumigram.app.updater.UpdateInfo

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
    onOpenPlugins: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ghostEnabled by remember { mutableStateOf(true) }
    var hideRead by remember { mutableStateOf(true) }
    var hideTyping by remember { mutableStateOf(true) }
    var hideOnline by remember { mutableStateOf(true) }

    var antiDeleteEnabled by remember { mutableStateOf(true) }
    var antiEditEnabled by remember { mutableStateOf(true) }

    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    val currentDynamicColor by ThemeManager.dynamicColor.collectAsState()
    val currentAccent by ThemeManager.accentPalette.collectAsState()
    val currentWallpaper by ThemeManager.chatWallpaper.collectAsState()

    var showIndicatorDialog by remember { mutableStateOf(false) }

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // THEMES & APPEARANCE (MATERIAL YOU)
            item {
                Text(
                    text = "ЗОВНІШНІЙ ВИГЛЯД ТА ТЕМИ (MATERIAL YOU)",
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
                        // Theme mode selector
                        Text(
                            text = "Режим теми інтерфейсу",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.values().forEach { mode ->
                                val isSelected = currentThemeMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { ThemeManager.setThemeMode(context, mode) },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                ThemeMode.SYSTEM -> "Система"
                                                ThemeMode.DARK -> "Темна"
                                                ThemeMode.LIGHT -> "Світла"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Dynamic Color Monet Toggle
                        SettingsToggleRow(
                            icon = Icons.Rounded.Palette,
                            title = "Pixel Dynamic Color (Monet)",
                            subtitle = "Адаптація кольорів додатку під шпалери робочого столу Android",
                            checked = currentDynamicColor,
                            onCheckedChange = { ThemeManager.setDynamicColor(context, it) }
                        )

                        // Custom Accent Palette (if dynamic color is off)
                        if (!currentDynamicColor) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = "Акцентний колір додатку",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AccentPalette.values().forEach { palette ->
                                    val isSelected = currentAccent == palette
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { ThemeManager.setAccentPalette(context, palette) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(palette.primary)
                                            )
                                        },
                                        label = { Text(palette.title, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Chat Wallpaper Selector
                        Text(
                            text = "Шпалери та фон чату",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChatWallpaper.values().forEach { wallpaper ->
                                val isSelected = currentWallpaper == wallpaper
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { ThemeManager.setChatWallpaper(context, wallpaper) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(wallpaper.backgroundColor)
                                        )
                                    },
                                    label = { Text(wallpaper.title, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Indicator style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showIndicatorDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Стиль значка прочитання",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${currentIndicator.title} (${currentIndicator.sent} / ${currentIndicator.read})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // GHOST MODE SECTION
            item {
                Text(
                    text = "GHOST MODE (РЕЖИМ ПРИВИДА)",
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
                        SettingsToggleRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "Ghost Mode активовано",
                            subtitle = "Комплексний режим невидимості у мережі Telegram",
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
                                subtitle = "Не надсилати звіт про перегляд повідомлень",
                                checked = hideRead,
                                onCheckedChange = {
                                    hideRead = it
                                    updateGhost()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingsToggleRow(
                                icon = Icons.Rounded.EditNote,
                                title = "Приховувати набір тексту",
                                subtitle = "Співрозмовник не бачить, коли ви друкуєте",
                                checked = hideTyping,
                                onCheckedChange = {
                                    hideTyping = it
                                    updateGhost()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            SettingsToggleRow(
                                icon = Icons.Rounded.PersonOff,
                                title = "Приховувати статус онлайн",
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
                    text = "БЕЗПЕКА ТА ЗБЕРЕЖЕННЯ (ANTI-DELETE)",
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

            // IN-APP AUTO-UPDATE SECTION
            item {
                Text(
                    text = "ОНОВЛЕННЯ ПРОГРАМИ",
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
                            Column {
                                Text(
                                    text = "Версія Yumigram",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Поточна встановлена: ${AppUpdateManager.CURRENT_VERSION}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    isCheckingUpdates = true
                                    scope.launch {
                                        val info = AppUpdateManager.checkForUpdates()
                                        isCheckingUpdates = false
                                        if (info != null) {
                                            updateInfo = info
                                        } else {
                                            Toast.makeText(context, "Встановлено найновішу версію!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdates && !isDownloading,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Перевірити")
                                }
                            }
                        }

                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Завантаження APK: ${(downloadProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // PLUGINS
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPlugins() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Менеджер розширень (Plugins)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Керування активними плагінами та ШІ хуками",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // LOGOUT BUTTON
            item {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Вийти з акаунта Telegram",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Indicator Style Selection Dialog
    if (showIndicatorDialog) {
        AlertDialog(
            onDismissRequest = { showIndicatorDialog = false },
            title = { Text("Оберіть стиль прочитання") },
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
                                .padding(vertical = 10.dp),
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
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Відправлено: ${style.sent}  ·  Прочитано: ${style.read}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIndicatorDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    // In-app Update Dialog
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(text = "Доступне оновлення ${info.tagName}")
            },
            text = {
                Column {
                    Text(
                        text = info.releaseTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentInfo = updateInfo
                        updateInfo = null
                        if (currentInfo != null) {
                            isDownloading = true
                            downloadProgress = 0f
                            scope.launch {
                                AppUpdateManager.downloadAndInstallApk(
                                    context = context,
                                    downloadUrl = currentInfo.downloadUrl,
                                    onProgress = { progress -> downloadProgress = progress },
                                    onError = { err ->
                                        isDownloading = false
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                                isDownloading = false
                            }
                        }
                    }
                ) {
                    Text("Оновити зараз")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("Пізніше")
                }
            }
        )
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
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
