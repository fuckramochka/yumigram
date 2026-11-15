package org.yumigram.app.ui.screens.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.YumigramBridge

enum class AuthStep {
    PHONE,
    CODE,
    PASSWORD
}

enum class ApiProfile(val label: String, val description: String, val apiId: Int, val apiHash: String) {
    DESKTOP(
        label = "Telegram Desktop",
        description = "App ID: 2040 · Швидкий вхід без перевірок Google Play",
        apiId = 2040,
        apiHash = "b18441a1ff607e10a989891a5462e627"
    ),
    ANDROID(
        label = "Telegram Android",
        description = "App ID: 6 · Офіційний мобільний клієнт",
        apiId = 6,
        apiHash = "eb06d4abfb49dc3eeb1aeb98ae0f581e"
    ),
    CUSTOM(
        label = "Власний API ID",
        description = "Ключі розробника з my.telegram.org",
        apiId = 0,
        apiHash = ""
    )
}

/**
 * Formats a phone number with clean spacing after the country code and between digit groups.
 * e.g. "380681940505" -> "+380 68 194 05 05"
 */
fun formatPhoneNumber(digits: String): String {
    if (digits.isEmpty()) return "+"

    // Ukraine (+380): +380 XX XXX XX XX
    if (digits.startsWith("380")) {
        val b = StringBuilder("+380")
        val rest = digits.substring(3)
        if (rest.isNotEmpty()) {
            b.append(" ").append(rest.take(2))
        }
        if (rest.length > 2) {
            b.append(" ").append(rest.substring(2).take(3))
        }
        if (rest.length > 5) {
            b.append(" ").append(rest.substring(5).take(2))
        }
        if (rest.length > 7) {
            b.append(" ").append(rest.substring(7).take(2))
        }
        return b.toString()
    }

    // USA/Canada (+1): +1 XXX XXX XXXX
    if (digits.startsWith("1")) {
        val b = StringBuilder("+1")
        val rest = digits.substring(1)
        if (rest.isNotEmpty()) b.append(" ").append(rest.take(3))
        if (rest.length > 3) b.append(" ").append(rest.substring(3).take(3))
        if (rest.length > 6) b.append(" ").append(rest.substring(6).take(4))
        return b.toString()
    }

    // Common 2-digit country codes: +XX XXX XXX XXX
    val twoDigitPrefixes = setOf("44", "48", "49", "33", "34", "39", "40", "41", "43", "45", "46", "47", "90", "91")
    if (digits.length >= 2 && twoDigitPrefixes.contains(digits.take(2))) {
        val b = StringBuilder("+" + digits.take(2))
        val rest = digits.substring(2)
        if (rest.isNotEmpty()) b.append(" ").append(rest.take(3))
        if (rest.length > 3) b.append(" ").append(rest.substring(3).take(3))
        if (rest.length > 6) b.append(" ").append(rest.substring(6).take(3))
        return b.toString()
    }

    // Generic fallback: +XXX followed by groups of 3
    val prefixLen = if (digits.length >= 3) 3 else digits.length
    val b = StringBuilder("+" + digits.take(prefixLen))
    val rest = digits.substring(prefixLen)
    var i = 0
    while (i < rest.length) {
        b.append(" ")
        b.append(rest.substring(i, minOf(i + 3, rest.length)))
        i += 3
    }
    return b.toString()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RecaptchaDialog(
    sitekey: String,
    onSuccess: (token: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Підтвердження безпеки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Пройдіть перевірку Google reCAPTCHA від Telegram",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                val html = remember(sitekey) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                        <script src="https://www.google.com/recaptcha/api.js" async defer></script>
                        <style>
                            body {
                                background-color: #1e1f22;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                                margin: 0;
                            }
                        </style>
                        <script type="text/javascript">
                            function onCaptchaSuccess(token) {
                                window.AndroidBridge.postMessage(token);
                            }
                        </script>
                    </head>
                    <body>
                        <div class="g-recaptcha" data-sitekey="$sitekey" data-callback="onCaptchaSuccess"></div>
                    </body>
                    </html>
                    """.trimIndent()
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun postMessage(token: String) {
                                    onSuccess(token)
                                }
                            }, "AndroidBridge")
                            loadDataWithBaseURL("https://telegram.org", html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Закрити")
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(AuthStep.PHONE) }
    var phoneNumber by remember { mutableStateOf("+380 ") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var selectedProfile by remember { mutableStateOf(ApiProfile.DESKTOP) }
    var showApiSettings by remember { mutableStateOf(false) }
    var customApiId by remember { mutableStateOf("") }
    var customApiHash by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recaptchaSitekey by remember { mutableStateOf<String?>(null) }
    var showRecaptchaDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Recaptcha Dialog
    if (showRecaptchaDialog && recaptchaSitekey != null) {
        RecaptchaDialog(
            sitekey = recaptchaSitekey!!,
            onSuccess = { _ ->
                showRecaptchaDialog = false
                errorMessage = null
                // With token verified, re-trigger request code
                val rawDigits = phoneNumber.filter { it.isDigit() }
                if (rawDigits.length >= 7) {
                    isLoading = true
                    scope.launch {
                        val res = YumigramBridge.requestCode("+$rawDigits")
                        isLoading = false
                        if (res.success) {
                            step = AuthStep.CODE
                        } else {
                            errorMessage = res.message ?: "Помилка відправки коду"
                        }
                    }
                }
            },
            onDismiss = { showRecaptchaDialog = false }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Branding Icon Header
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

            Spacer(modifier = Modifier.height(20.dp))

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
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )

            // Error & Advisory Card
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
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )

                            // Quick Action Buttons if reCAPTCHA or Profile issue
                            if (recaptchaSitekey != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                YumigramBridge.setApiCredentials(
                                                    ApiProfile.DESKTOP.apiId,
                                                    ApiProfile.DESKTOP.apiHash
                                                )
                                                selectedProfile = ApiProfile.DESKTOP
                                                val rawDigits = phoneNumber.filter { it.isDigit() }
                                                val res = YumigramBridge.requestCode("+$rawDigits")
                                                isLoading = false
                                                if (res.success) {
                                                    errorMessage = null
                                                    recaptchaSitekey = null
                                                    step = AuthStep.CODE
                                                } else {
                                                    errorMessage = res.message
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("⚡ Desktop API", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showRecaptchaDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("reCAPTCHA", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
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
                                onValueChange = { input ->
                                    var raw = input
                                    if (input.length < phoneNumber.length && phoneNumber.endsWith(" ")) {
                                        val clean = phoneNumber.filter { it.isDigit() }
                                        raw = if (clean.isNotEmpty()) clean.dropLast(1) else ""
                                    }
                                    val cleanDigits = raw.filter { it.isDigit() }
                                    phoneNumber = if (cleanDigits.isEmpty()) {
                                        if (raw.startsWith("+")) "+" else ""
                                    } else {
                                        formatPhoneNumber(cleanDigits)
                                    }
                                    errorMessage = null
                                    recaptchaSitekey = null
                                },
                                label = { Text("Номер телефону") },
                                placeholder = { Text("+380 68 194 05 05") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val rawDigits = phoneNumber.filter { it.isDigit() }
                                        if (rawDigits.length >= 7 && !isLoading) {
                                            isLoading = true
                                            scope.launch {
                                                val res = YumigramBridge.requestCode("+$rawDigits")
                                                isLoading = false
                                                if (res.success) {
                                                    step = AuthStep.CODE
                                                } else {
                                                    errorMessage = res.message ?: "Помилка відправки коду"
                                                    if (res.recaptcha_required) {
                                                        recaptchaSitekey = res.sitekey
                                                    }
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
                                    val rawDigits = phoneNumber.filter { it.isDigit() }
                                    if (rawDigits.length >= 7) {
                                        isLoading = true
                                        errorMessage = null
                                        recaptchaSitekey = null
                                        scope.launch {
                                            val res = YumigramBridge.requestCode("+$rawDigits")
                                            isLoading = false
                                            if (res.success) {
                                                step = AuthStep.CODE
                                            } else {
                                                errorMessage = res.message ?: "Помилка відправки коду"
                                                if (res.recaptcha_required) {
                                                    recaptchaSitekey = res.sitekey
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isLoading && phoneNumber.filter { it.isDigit() }.length >= 7,
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
                                    recaptchaSitekey = null
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

            Spacer(modifier = Modifier.height(20.dp))

            // MTProto Profile & Advanced API Settings
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Профіль MTProto:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedProfile.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(
                            onClick = { showApiSettings = !showApiSettings },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (showApiSettings) Icons.Rounded.ExpandLess else Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showApiSettings) "Приховати" else "Налаштувати",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    AnimatedVisibility(visible = showApiSettings) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                            ApiProfile.values().forEach { profile ->
                                val isSelected = selectedProfile == profile
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedProfile = profile
                                            if (profile != ApiProfile.CUSTOM) {
                                                scope.launch {
                                                    YumigramBridge.setApiCredentials(profile.apiId, profile.apiHash)
                                                }
                                            }
                                        }
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = profile.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = profile.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (selectedProfile == ApiProfile.CUSTOM) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customApiId,
                                    onValueChange = { customApiId = it },
                                    label = { Text("Custom API ID") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customApiHash,
                                    onValueChange = { customApiHash = it },
                                    label = { Text("Custom API Hash") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val id = customApiId.toIntOrNull() ?: 0
                                        if (id > 0 && customApiHash.isNotEmpty()) {
                                            scope.launch {
                                                YumigramBridge.setApiCredentials(id, customApiHash)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Застосувати власні ключі")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
