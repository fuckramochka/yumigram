package org.yumigram.app.ui.screens.profile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.YumigramBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    initialFirstName: String = "Користувач",
    initialLastName: String = "Telegram",
    initialBio: String = "🇺🇦 Yumigram Pixel OS Experience",
    initialPhone: String = "+380 68 194 05 05",
    initialUsername: String = "yumigram_user",
    initialBirthday: String = "15 листопада 🎂",
    initialChannel: String = "https://t.me/yumigram_official",
    onDismiss: () -> Unit,
    onSaved: (firstName: String, lastName: String, bio: String, phone: String, username: String, birthday: String, channel: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    var bio by remember { mutableStateOf(initialBio) }
    var phone by remember { mutableStateOf(initialPhone) }
    var username by remember { mutableStateOf(initialUsername) }
    var birthday by remember { mutableStateOf(initialBirthday) }
    var channel by remember { mutableStateOf(initialChannel) }
    var isSaving by remember { mutableStateOf(false) }

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
                // Top App Bar
                TopAppBar(
                    title = { Text("Налаштування акаунта", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Скасувати")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    YumigramBridge.updateProfile(firstName, lastName, bio)
                                    // Save locally
                                    context.getSharedPreferences("yumigram_user_profile", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("first_name", firstName)
                                        .putString("last_name", lastName)
                                        .putString("bio", bio)
                                        .putString("phone", phone)
                                        .putString("username", username)
                                        .putString("birthday", birthday)
                                        .putString("channel", channel)
                                        .apply()

                                    isSaving = false
                                    Toast.makeText(context, "Профіль успішно оновлено!", Toast.LENGTH_SHORT).show()
                                    onSaved(firstName, lastName, bio, phone, username, birthday, channel)
                                    onDismiss()
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Зберегти", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar Picker Button
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                Toast.makeText(context, "Оберіть нове фото з галереї", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddAPhoto,
                            contentDescription = "Змінити фото",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Змінити фотографію профілю",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Fields
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ОСОБИСТІ ДАНІ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("Ім'я") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text("Прізвище") },
                                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Про себе (Біографія)") },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "КОНТАКТИ ТА ПРИВ'ЯЗКИ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Ім'я користувача (@юз)") },
                                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                                prefix = { Text("@") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Номер телефону") },
                                leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = birthday,
                                onValueChange = { birthday = it },
                                label = { Text("День народження") },
                                leadingIcon = { Icon(Icons.Rounded.Cake, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = channel,
                                onValueChange = { channel = it },
                                label = { Text("Персональний канал (ТГК)") },
                                leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
