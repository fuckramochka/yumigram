package org.yumigram.app.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.yumigram.app.ui.screens.settings.TelegramStarsAndBusinessScreen
import org.yumigram.app.updater.AppUpdateManager
import org.yumigram.app.updater.UpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPrefs = remember { context.getSharedPreferences("yumigram_user_profile", Context.MODE_PRIVATE) }

    var firstName by remember { mutableStateOf(userPrefs.getString("first_name", "Користувач") ?: "Користувач") }
    var lastName by remember { mutableStateOf(userPrefs.getString("last_name", "Telegram") ?: "Telegram") }
    var bio by remember { mutableStateOf(userPrefs.getString("bio", "🇺🇦 Yumigram Pixel OS Experience") ?: "🇺🇦 Yumigram Pixel OS Experience") }
    var phone by remember { mutableStateOf(userPrefs.getString("phone", "+380 68 194 05 05") ?: "+380 68 194 05 05") }
    var username by remember { mutableStateOf(userPrefs.getString("username", "yumigram_user") ?: "yumigram_user") }
    var birthday by remember { mutableStateOf(userPrefs.getString("birthday", "15 листопада 🎂") ?: "15 листопада 🎂") }
    var channel by remember { mutableStateOf(userPrefs.getString("channel", "https://t.me/yumigram_official") ?: "https://t.me/yumigram_official") }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showStarsDialog by remember { mutableStateOf(false) }

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Мій профіль",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { showEditProfileDialog = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Редагувати профіль")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Налаштування")
                    }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Avatar & Name Header (Pixel OS Style)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .border(
                            3.dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFE040FB),
                                    Color(0xFF2979FF),
                                    Color(0xFF00E676),
                                    Color(0xFFFF9100),
                                    Color(0xFFE040FB)
                                )
                            ),
                            CircleShape
                        )
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showEditProfileDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = firstName.take(1).uppercase(),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$firstName $lastName",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "@$username",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                // Online Status Pill with Green Dot
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Офіційний MTProto 2.0 онлайн",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // TELEGRAM STARS & BUSINESS BANNER CARD
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStarsDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFFD54F), Color(0xFFFFA000))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⭐", fontSize = 24.sp)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Telegram Stars & Premium", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFA000).copy(alpha = 0.2f)
                                ) {
                                    Text("350 ⭐", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                                }
                            }
                            Text("Подарунки, валюта GRAM, Toncoin та бізнес-функції", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // PERSONAL INFO DETAILS CARD
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
                                text = "ОСОБИСТІ ДАНІ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { showEditProfileDialog = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Редагувати", modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.Phone,
                            title = "Номер телефону",
                            value = phone
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.AlternateEmail,
                            title = "Ім'я користувача (@юз)",
                            value = "@$username"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.Info,
                            title = "Про себе (Біографія)",
                            value = bio
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.Cake,
                            title = "День народження",
                            value = birthday
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.Campaign,
                            title = "Персональний канал (ТГК)",
                            value = channel
                        )
                    }
                }
            }

            // ACCOUNT CLIENT & SECURITY DETAILS
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ОБЛІКОВИЙ ЗАПИС ТА СЕСІЯ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.PhoneAndroid,
                            title = "Офіційний клієнт",
                            value = "App ID: 6 (Telegram Android)"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.Fingerprint,
                            title = "Сесія",
                            value = "Авторизовано через захищений токен"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        ProfileInfoRow(
                            icon = Icons.Rounded.Shield,
                            title = "Захист конфіденційності",
                            value = "Ghost Mode & Anti-Delete активовано"
                        )
                    }
                }
            }

            // APP DETAILS & IN-APP AUTO-UPDATE CARD
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
                                text = "ОНОВЛЕННЯ ПРОГРАМИ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = AppUpdateManager.CURRENT_VERSION,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Yumigram Pixel OS Edition",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Отримуйте останні оновлення безпеки, нові стікери та покращення інтерфейсу безпосередньо з офіційного репозиторію.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        if (updateStatusMessage != null) {
                            Text(
                                text = updateStatusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        if (isDownloading) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Завантаження APK: ${(downloadProgress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    isCheckingUpdates = true
                                    updateStatusMessage = null
                                    scope.launch {
                                        val info = AppUpdateManager.checkForUpdates()
                                        isCheckingUpdates = false
                                        if (info != null) {
                                            updateInfo = info
                                        } else {
                                            Toast.makeText(context, "Встановлено найновішу версію!", Toast.LENGTH_SHORT).show()
                                            updateStatusMessage = "Встановлено найновішу версію Yumigram."
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdates,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Перевірка...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Перевірити наявність оновлень")
                                }
                            }
                        }
                    }
                }
            }

            // Quick Shortcut to Settings
            item {
                OutlinedButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Розширені налаштування (Ghost Mode, Теми)")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        EditProfileDialog(
            initialFirstName = firstName,
            initialLastName = lastName,
            initialBio = bio,
            initialPhone = phone,
            initialUsername = username,
            initialBirthday = birthday,
            initialChannel = channel,
            onDismiss = { showEditProfileDialog = false },
            onSaved = { fn, ln, b, ph, u, bd, ch ->
                firstName = fn
                lastName = ln
                bio = b
                phone = ph
                username = u
                birthday = bd
                channel = ch
            }
        )
    }

    // Telegram Stars & Business Screen
    if (showStarsDialog) {
        TelegramStarsAndBusinessScreen(
            onDismiss = { showStarsDialog = false }
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Доступне оновлення!",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Нова версія: ${info.versionName}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Розмір: ${info.formattedSize()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Опис змін:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Text(
                        text = info.changelog.ifBlank { "Покращення стабільності та нові функції." },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDownload = updateInfo ?: return@Button
                        updateInfo = null
                        isDownloading = true
                        downloadProgress = 0f
                        scope.launch {
                            val apkFile = AppUpdateManager.downloadApk(toDownload) { progress ->
                                downloadProgress = progress
                            }
                            isDownloading = false
                            if (apkFile != null) {
                                AppUpdateManager.installApk(context, apkFile)
                            } else {
                                Toast.makeText(context, "Помилка завантаження файлу оновлення", Toast.LENGTH_SHORT).show()
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
fun ProfileInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(title, value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Скопійовано: $value", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Скопіювати",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
