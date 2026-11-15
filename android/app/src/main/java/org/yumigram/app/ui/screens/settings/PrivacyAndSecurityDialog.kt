package org.yumigram.app.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class PrivacyOption(val label: String) {
    EVERYBODY("Всі"),
    MY_CONTACTS("Мої контакти"),
    NOBODY("Ніхто"),
    PREMIUM_ONLY("Лише з Преміум ⭐")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndSecurityDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yumigram_privacy_prefs", Context.MODE_PRIVATE) }

    // Privacy States
    var phonePrivacy by remember { mutableStateOf(prefs.getString("privacy_phone", PrivacyOption.MY_CONTACTS.name) ?: PrivacyOption.MY_CONTACTS.name) }
    var lastSeenPrivacy by remember { mutableStateOf(prefs.getString("privacy_last_seen", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var photoPrivacy by remember { mutableStateOf(prefs.getString("privacy_photo", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var forwardsPrivacy by remember { mutableStateOf(prefs.getString("privacy_forwards", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var callsPrivacy by remember { mutableStateOf(prefs.getString("privacy_calls", PrivacyOption.MY_CONTACTS.name) ?: PrivacyOption.MY_CONTACTS.name) }
    var voiceMessagesPrivacy by remember { mutableStateOf(prefs.getString("privacy_voice", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var messagesPrivacy by remember { mutableStateOf(prefs.getString("privacy_messages", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var birthdayPrivacy by remember { mutableStateOf(prefs.getString("privacy_birthday", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var bioPrivacy by remember { mutableStateOf(prefs.getString("privacy_bio", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var musicPrivacy by remember { mutableStateOf(prefs.getString("privacy_music", PrivacyOption.EVERYBODY.name) ?: PrivacyOption.EVERYBODY.name) }
    var groupsPrivacy by remember { mutableStateOf(prefs.getString("privacy_groups", PrivacyOption.MY_CONTACTS.name) ?: PrivacyOption.MY_CONTACTS.name) }

    // Security States
    var twoFactorEnabled by remember { mutableStateOf(prefs.getBoolean("two_factor_enabled", true)) }
    var passcodeLockEnabled by remember { mutableStateOf(prefs.getBoolean("passcode_lock_enabled", false)) }
    var passkeyEnabled by remember { mutableStateOf(prefs.getBoolean("passkey_enabled", false)) }
    var deleteAccountMonths by remember { mutableIntStateOf(prefs.getInt("delete_account_months", 6)) }

    // Dialog state for option selection
    var selectedItemToEdit by remember { mutableStateOf<String?>(null) }
    var currentOptionsList by remember { mutableStateOf<List<PrivacyOption>>(emptyList()) }
    var onOptionSelectedCallback by remember { mutableStateOf<(PrivacyOption) -> Unit>({}) }

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
                    title = { Text("Приватність і безпека", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрити")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECURITY SECTION
                    item {
                        Text(
                            text = "БЕЗПЕКА ТА ВХІД",
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
                                // 2FA Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Password, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Двоетапна автентифікація (2FA)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(if (twoFactorEnabled) "Увімкнено (хмарний пароль активний)" else "Вимкнено", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = twoFactorEnabled,
                                        onCheckedChange = {
                                            twoFactorEnabled = it
                                            prefs.edit().putBoolean("two_factor_enabled", it).apply()
                                            Toast.makeText(context, if (it) "2FA активовано" else "2FA деактивовано", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Passcode Lock (Код доступу)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Код доступу (Passcode Lock)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Блокування додатку PIN-кодом або відбитком", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = passcodeLockEnabled,
                                        onCheckedChange = {
                                            passcodeLockEnabled = it
                                            prefs.edit().putBoolean("passcode_lock_enabled", it).apply()
                                            Toast.makeText(context, if (it) "Код доступу встановлено" else "Блокування вимкнено", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Passkey (Ключ доступу)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Ключ доступу (Passkey FIDO2)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Вхід за біометрією без введення паролю", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = passkeyEnabled,
                                        onCheckedChange = {
                                            passkeyEnabled = it
                                            prefs.edit().putBoolean("passkey_enabled", it).apply()
                                            Toast.makeText(context, if (it) "Passkey створено" else "Passkey вимкнено", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Account Self-Destruct Timer
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedItemToEdit = "Видалити мій акаунт, якщо я не онлайн"
                                            currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Видалення акаунту при неактивності", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Якщо відсутній у мережі: $deleteAccountMonths місяців", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("$deleteAccountMonths міс.", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // ACTIVE SESSIONS & DEVICES (Пристрої на акаунті)
                    item {
                        Text(
                            text = "АКТИВНІ СЕСІЇ ТА ПРИСТРОЇ",
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
                                // Current Device
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = Color(0xFF4CAF50))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Google Pixel 8 Pro (Цей пристрій)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Yumigram v1.0.11 · Київ, Україна · Онлайн", fontSize = 11.sp, color = Color(0xFF4CAF50))
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Other Session 1
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Laptop, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Telegram Desktop Windows 11", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Остання активність: Сьогодні, 09:14 · 178.62.204.81", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Other Session 2
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("MacBook Pro 16\" (macOS Sonoma)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Остання активність: Вчора, 22:30", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedButton(
                                    onClick = {
                                        Toast.makeText(context, "Всі інші сесії успішно завершено!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Завершити всі інші сесії")
                                }
                            }
                        }
                    }

                    // BLOCKED USERS
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Заблоковані користувачі", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("0 користувачів у чорному списку", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { Toast.makeText(context, "Чорний список порожній", Toast.LENGTH_SHORT).show() }) {
                                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }

                    // WHO CAN SEE / PRIVACY MATRIX
                    item {
                        Text(
                            text = "КОНФІДЕНЦІЙНІСТЬ (ХТО МОЖЕ БАЧИТИ ТА ВЗАЄМОДІЯТИ)",
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
                                // Phone
                                PrivacySettingRow("Номер телефону", phonePrivacy) {
                                    selectedItemToEdit = "Номер телефону"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        phonePrivacy = opt.name
                                        prefs.edit().putString("privacy_phone", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Last seen
                                PrivacySettingRow("Остання активність та онлайн", lastSeenPrivacy) {
                                    selectedItemToEdit = "Остання активність та онлайн"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        lastSeenPrivacy = opt.name
                                        prefs.edit().putString("privacy_last_seen", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Photo
                                PrivacySettingRow("Фотографії профілю", photoPrivacy) {
                                    selectedItemToEdit = "Фотографії профілю"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        photoPrivacy = opt.name
                                        prefs.edit().putString("privacy_photo", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Forwards
                                PrivacySettingRow("Пересилання повідомлень", forwardsPrivacy) {
                                    selectedItemToEdit = "Пересилання повідомлень"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        forwardsPrivacy = opt.name
                                        prefs.edit().putString("privacy_forwards", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Calls
                                PrivacySettingRow("Дзвінки", callsPrivacy) {
                                    selectedItemToEdit = "Дзвінки"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        callsPrivacy = opt.name
                                        prefs.edit().putString("privacy_calls", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Voice messages (Who can send me voice/video notes)
                                PrivacySettingRow("Голосові повідомлення (хто може надсилати)", voiceMessagesPrivacy) {
                                    selectedItemToEdit = "Голосові повідомлення"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.PREMIUM_ONLY, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        voiceMessagesPrivacy = opt.name
                                        prefs.edit().putString("privacy_voice", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Messages (Who can message me)
                                PrivacySettingRow("Особисті повідомлення", messagesPrivacy) {
                                    selectedItemToEdit = "Особисті повідомлення"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.PREMIUM_ONLY)
                                    onOptionSelectedCallback = { opt ->
                                        messagesPrivacy = opt.name
                                        prefs.edit().putString("privacy_messages", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Birthday
                                PrivacySettingRow("День народження", birthdayPrivacy) {
                                    selectedItemToEdit = "День народження"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        birthdayPrivacy = opt.name
                                        prefs.edit().putString("privacy_birthday", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Bio
                                PrivacySettingRow("Про себе (Біографія)", bioPrivacy) {
                                    selectedItemToEdit = "Біографія"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        bioPrivacy = opt.name
                                        prefs.edit().putString("privacy_bio", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Profile music
                                PrivacySettingRow("Музика в профілі", musicPrivacy) {
                                    selectedItemToEdit = "Музика в профілі"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        musicPrivacy = opt.name
                                        prefs.edit().putString("privacy_music", opt.name).apply()
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                // Groups & channels
                                PrivacySettingRow("Додавання до груп та каналів", groupsPrivacy) {
                                    selectedItemToEdit = "Додавання до груп та каналів"
                                    currentOptionsList = listOf(PrivacyOption.EVERYBODY, PrivacyOption.MY_CONTACTS, PrivacyOption.NOBODY)
                                    onOptionSelectedCallback = { opt ->
                                        groupsPrivacy = opt.name
                                        prefs.edit().putString("privacy_groups", opt.name).apply()
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

    // Option Picker Sub-Dialog
    if (selectedItemToEdit != null) {
        AlertDialog(
            onDismissRequest = { selectedItemToEdit = null },
            title = { Text(selectedItemToEdit!!) },
            text = {
                Column {
                    currentOptionsList.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelectedCallback(option)
                                    selectedItemToEdit = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemToEdit = null }) {
                    Text("Закрити")
                }
            }
        )
    }
}

@Composable
private fun PrivacySettingRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    val displayLabel = remember(value) {
        try {
            PrivacyOption.valueOf(value).label
        } catch (_: Exception) {
            value
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(text = displayLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}
