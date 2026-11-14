package org.yumigram.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.YumigramBridge

enum class AuthStep {
    PHONE,
    CODE,
    PASSWORD
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(AuthStep.PHONE) }
    var phoneNumber by remember { mutableStateOf("+") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pixel OS Branding Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (step) {
                        AuthStep.PHONE -> Icons.Rounded.Phone
                        AuthStep.CODE -> Icons.Rounded.Security
                        AuthStep.PASSWORD -> Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Yumigram",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = when (step) {
                    AuthStep.PHONE -> "Введіть номер телефону для входу в офіційний Telegram"
                    AuthStep.CODE -> "Код підтвердження надіслано на ваш Telegram або SMS"
                    AuthStep.PASSWORD -> "Хмарний пароль (Двохетапна автентифікація)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // Error Card
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Input Fields Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        AuthStep.PHONE -> {
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = {
                                    phoneNumber = it
                                    errorMessage = null
                                },
                                label = { Text("Номер телефону") },
                                placeholder = { Text("+380...") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (phoneNumber.length > 5 && !isLoading) {
                                            isLoading = true
                                            scope.launch {
                                                val res = YumigramBridge.requestCode(phoneNumber.trim())
                                                isLoading = false
                                                if (res.success) {
                                                    step = AuthStep.CODE
                                                } else {
                                                    errorMessage = res.message ?: "Помилка відправки коду"
                                                }
                                            }
                                        }
                                    }
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    if (phoneNumber.length > 5) {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            val res = YumigramBridge.requestCode(phoneNumber.trim())
                                            isLoading = false
                                            if (res.success) {
                                                step = AuthStep.CODE
                                            } else {
                                                errorMessage = res.message ?: "Помилка відправки коду"
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading && phoneNumber.length > 5,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Отримати код", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        AuthStep.CODE -> {
                            OutlinedTextField(
                                value = code,
                                onValueChange = {
                                    code = it.filter { ch -> ch.isDigit() }
                                    errorMessage = null
                                    if (code.length == 5 && !isLoading) {
                                        isLoading = true
                                        scope.launch {
                                            val res = YumigramBridge.signIn(code.trim())
                                            isLoading = false
                                            if (res.success) {
                                                if (res.status == "2FA_REQUIRED") {
                                                    step = AuthStep.PASSWORD
                                                } else {
                                                    onAuthSuccess()
                                                }
                                            } else {
                                                errorMessage = res.message ?: "Невірний код"
                                            }
                                        }
                                    }
                                },
                                label = { Text("Код підтвердження") },
                                placeholder = { Text("12345") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (code.isNotEmpty()) {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            val res = YumigramBridge.signIn(code.trim())
                                            isLoading = false
                                            if (res.success) {
                                                if (res.status == "2FA_REQUIRED") {
                                                    step = AuthStep.PASSWORD
                                                } else {
                                                    onAuthSuccess()
                                                }
                                            } else {
                                                errorMessage = res.message ?: "Невірний код"
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading && code.isNotEmpty(),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Увійти", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = {
                                    step = AuthStep.PHONE
                                    code = ""
                                    errorMessage = null
                                }
                            ) {
                                Text("Змінити номер")
                            }
                        }

                        AuthStep.PASSWORD -> {
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    errorMessage = null
                                },
                                label = { Text("Пароль 2FA") },
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    if (password.isNotEmpty()) {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            val res = YumigramBridge.checkPassword(password)
                                            isLoading = false
                                            if (res.success) {
                                                onAuthSuccess()
                                            } else {
                                                errorMessage = res.message ?: "Невірний пароль 2FA"
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading && password.isNotEmpty(),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Підтвердити пароль", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Офіційні MTProto ключі Telegram (App ID: 6)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
