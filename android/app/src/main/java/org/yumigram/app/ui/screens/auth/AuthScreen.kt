package org.yumigram.app.ui.screens.auth

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.YumigramBridge

enum class AuthStep {
    PHONE,
    CODE,
    PASSWORD
}

enum class ApiProfile(
    val label: String,
    val shortLabel: String,
    val badge: String,
    val description: String,
    val apiId: Int,
    val apiHash: String
) {
    DESKTOP(
        label = "Telegram Desktop",
        shortLabel = "Desktop",
        badge = "ID: 2040 · Офіційний клієнт",
        description = "Найбільш стабільний. Вхід без Play Integrity / reCAPTCHA. Код приходить у відкритий Telegram на іншому пристрої або SMS.",
        apiId = 2040,
        apiHash = "b18441a1ff607e10a989891a5462e627"
    ),
    MACOS(
        label = "Telegram macOS",
        shortLabel = "macOS",
        badge = "ID: 2834 · Apple Swift",
        description = "Офіційний native клієнт для macOS. Високий пріоритет та швидка доставка кодів Telegram.",
        apiId = 2834,
        apiHash = "68875f756c9b437a8b916ca3de215815"
    ),
    WEB(
        label = "Telegram Web (WebK)",
        shortLabel = "WebK",
        badge = "ID: 2496 · Браузерний",
        description = "Офіційний веб-клієнт web.telegram.org/k. Працює як браузерна сесія без блокувань Play Market.",
        apiId = 2496,
        apiHash = "8da85b0d5bfe62527e5b244c209159c3"
    ),
    IOS(
        label = "Telegram iOS",
        shortLabel = "iOS",
        badge = "ID: 10840 · Apple iPhone",
        description = "Офіційний мобільний клієнт для iOS. Швидка мобільна автентифікація в екосистемі Telegram.",
        apiId = 10840,
        apiHash = "33c45224029d59cb3ad0c16134215aeb"
    ),
    ANDROID_X(
        label = "Telegram Android X",
        shortLabel = "Android X",
        badge = "ID: 21724 · TDLib",
        description = "Офіційний експериментальний Android клієнт на базі TDLib.",
        apiId = 21724,
        apiHash = "3e0cb5efcd52300aec5994fdfc5bdc16"
    ),
    ANDROID(
        label = "Telegram Android",
        shortLabel = "Android",
        badge = "ID: 6 · Офіційний Android",
        description = "Стандартний Google Play клієнт. Може вимагати Play Integrity або проходження reCAPTCHA.",
        apiId = 6,
        apiHash = "eb06d4abfb49dc3eeb1aeb98ae0f581e"
    ),
    CUSTOM(
        label = "Власний API ID",
        shortLabel = "Свій ID",
        badge = "my.telegram.org",
        description = "Ваш персональний ID та Hash з порталу my.telegram.org (100% гарантія доставки коду).",
        apiId = 0,
        apiHash = ""
    )
}

/**
 * VisualTransformation for phone numbers that displays visual spaces
 * (e.g. "+380 68 194 05 05") WITHOUT modifying the raw text in state.
 * This guarantees the cursor never jumps backwards and numbers never get inverted!
 */
class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val out = StringBuilder()
        val originalToTransformed = IntArray(raw.length + 1)
        val transformedToOriginal = ArrayList<Int>()

        var visualPos = 0

        fun appendChar(c: Char, rawIdx: Int) {
            originalToTransformed[rawIdx] = visualPos
            out.append(c)
            transformedToOriginal.add(rawIdx)
            visualPos++
        }

        fun appendSpace() {
            out.append(' ')
            val currentRaw = minOf(transformedToOriginal.size, raw.length)
            transformedToOriginal.add(currentRaw)
            visualPos++
        }

        if (raw.startsWith("+380") || raw.startsWith("380")) {
            val hasPlus = raw.startsWith("+")
            val codeLen = if (hasPlus) 4 else 3

            for (i in 0 until raw.length) {
                if (i == codeLen && i > 0) appendSpace()
                else if (i == codeLen + 2) appendSpace()
                else if (i == codeLen + 5) appendSpace()
                else if (i == codeLen + 7) appendSpace()

                appendChar(raw[i], i)
            }
            if (raw.length == codeLen) {
                appendSpace()
            }
        } else {
            val codeLen = if (raw.startsWith("+")) minOf(4, raw.length) else minOf(3, raw.length)
            for (i in 0 until raw.length) {
                if (i == codeLen && i > 0) appendSpace()
                else if (i > codeLen && (i - codeLen) % 3 == 0) appendSpace()

                appendChar(raw[i], i)
            }
            if (raw.length == codeLen && raw.length > 0) {
                appendSpace()
            }
        }

        originalToTransformed[raw.length] = visualPos
        transformedToOriginal.add(raw.length)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return originalToTransformed[offset.coerceIn(0, raw.length)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transformedToOriginal[offset.coerceIn(0, transformedToOriginal.size - 1)]
            }
        }

        return TransformedText(AnnotatedString(out.toString()), offsetMapping)
    }
}

@Composable
fun ApiProfileIcon(
    profile: ApiProfile,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val icon = when (profile) {
        ApiProfile.DESKTOP -> Icons.Rounded.Computer
        ApiProfile.MACOS -> Icons.Rounded.Laptop
        ApiProfile.WEB -> Icons.Rounded.Public
        ApiProfile.IOS -> Icons.Rounded.Smartphone
        ApiProfile.ANDROID_X -> Icons.Rounded.Bolt
        ApiProfile.ANDROID -> Icons.Rounded.Android
        ApiProfile.CUSTOM -> Icons.Rounded.Tune
    }
    Icon(imageVector = icon, contentDescription = profile.label, modifier = modifier, tint = tint)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Підтвердження безпеки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Пройдіть перевірку reCAPTCHA для входу через Android API",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                val html = remember(sitekey) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <script src="https://www.google.com/recaptcha/api.js" async defer></script>
                        <style>
                            body {
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                                margin: 0;
                                background-color: #f7f9fa;
                            }
                        </style>
                    </head>
                    <body>
                        <form action="javascript:void(0);">
                            <div class="g-recaptcha" data-sitekey="$sitekey" data-callback="onRecaptchaSuccess"></div>
                        </form>
                        <script>
                            function onRecaptchaSuccess(token) {
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.postMessage(token);
                                }
                            }
                        </script>
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
fun ProfileSelectionDialog(
    currentProfile: ApiProfile,
    customApiId: String,
    customApiHash: String,
    onSelectProfile: (ApiProfile) -> Unit,
    onSaveCustom: (id: String, hash: String) -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var editingCustom by remember { mutableStateOf(currentProfile == ApiProfile.CUSTOM) }
    var tempCustomId by remember { mutableStateOf(customApiId) }
    var tempCustomHash by remember { mutableStateOf(customApiHash) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Вибір MTProto API",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Оберіть клієнт для отримання коду",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрити")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ApiProfile.values().forEach { profile ->
                        val isSelected = currentProfile == profile
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (profile == ApiProfile.CUSTOM) {
                                        editingCustom = true
                                        onSelectProfile(profile)
                                    } else {
                                        editingCustom = false
                                        onSelectProfile(profile)
                                        onDismiss()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ApiProfileIcon(
                                    profile = profile,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = profile.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = profile.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (profile == ApiProfile.CUSTOM) {
                                            editingCustom = true
                                            onSelectProfile(profile)
                                        } else {
                                            editingCustom = false
                                            onSelectProfile(profile)
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (editingCustom || currentProfile == ApiProfile.CUSTOM) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Налаштування власного API ID",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Особисті ключі з порталу розробників Telegram гарантують 100% отримання коду без блокувань.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                )
                                OutlinedTextField(
                                    value = tempCustomId,
                                    onValueChange = { tempCustomId = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("App API ID (наприклад, 1234567)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = tempCustomHash,
                                    onValueChange = { tempCustomHash = it.trim() },
                                    label = { Text("App API Hash (32 символи)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            uriHandler.openUri("https://my.telegram.org")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("my.telegram.org", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            onSaveCustom(tempCustomId, tempCustomHash)
                                            onDismiss()
                                        },
                                        enabled = tempCustomId.isNotEmpty() && tempCustomHash.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Застосувати", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Готово")
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val prefs = remember {
        context.getSharedPreferences("yumigram_auth_prefs", Context.MODE_PRIVATE)
    }

    var step by remember { mutableStateOf(AuthStep.PHONE) }
    var phoneNumber by remember { mutableStateOf("+380") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val savedProfileName = prefs.getString("selected_api_profile", ApiProfile.DESKTOP.name)
    var selectedProfile by remember {
        mutableStateOf(
            try {
                ApiProfile.valueOf(savedProfileName ?: ApiProfile.DESKTOP.name)
            } catch (_: Exception) {
                ApiProfile.DESKTOP
            }
        )
    }
    var customApiId by remember {
        mutableStateOf(prefs.getString("custom_api_id", "") ?: "")
    }
    var customApiHash by remember {
        mutableStateOf(prefs.getString("custom_api_hash", "") ?: "")
    }

    var showProfileDialog by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recaptchaSitekey by remember { mutableStateOf<String?>(null) }
    var showRecaptchaDialog by remember { mutableStateOf(false) }

    var resendCooldown by remember { mutableStateOf(60) }

    // Helper to apply MTProto credentials and save preference
    fun applyProfile(profile: ApiProfile, customId: String? = null, customHash: String? = null) {
        selectedProfile = profile
        prefs.edit().putString("selected_api_profile", profile.name).apply()
        errorMessage = null
        recaptchaSitekey = null

        val id: Int
        val hash: String

        if (profile == ApiProfile.CUSTOM) {
            val effId = customId ?: customApiId
            val effHash = customHash ?: customApiHash
            id = effId.toIntOrNull() ?: 0
            hash = effHash
            prefs.edit()
                .putString("custom_api_id", effId)
                .putString("custom_api_hash", effHash)
                .apply()
        } else {
            id = profile.apiId
            hash = profile.apiHash
        }

        if (id > 0 && hash.isNotEmpty()) {
            scope.launch {
                YumigramBridge.setApiCredentials(id, hash)
            }
        }
    }

    // Apply saved profile on initial composition
    LaunchedEffect(Unit) {
        val id = if (selectedProfile == ApiProfile.CUSTOM) {
            customApiId.toIntOrNull() ?: ApiProfile.DESKTOP.apiId
        } else {
            selectedProfile.apiId
        }
        val hash = if (selectedProfile == ApiProfile.CUSTOM) {
            customApiHash.ifEmpty { ApiProfile.DESKTOP.apiHash }
        } else {
            selectedProfile.apiHash
        }
        YumigramBridge.setApiCredentials(id, hash)
    }

    // Countdown timer for code resend
    LaunchedEffect(step) {
        if (step == AuthStep.CODE) {
            resendCooldown = 60
            while (resendCooldown > 0) {
                delay(1000)
                resendCooldown--
            }
        }
    }

    // Recaptcha Dialog
    if (showRecaptchaDialog && recaptchaSitekey != null) {
        RecaptchaDialog(
            sitekey = recaptchaSitekey!!,
            onSuccess = { _ ->
                showRecaptchaDialog = false
                errorMessage = null
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

    // Full Profile Selection Dialog
    if (showProfileDialog) {
        ProfileSelectionDialog(
            currentProfile = selectedProfile,
            customApiId = customApiId,
            customApiHash = customApiHash,
            onSelectProfile = { profile ->
                applyProfile(profile)
            },
            onSaveCustom = { id, hash ->
                customApiId = id
                customApiHash = hash
                applyProfile(ApiProfile.CUSTOM, id, hash)
            },
            onDismiss = { showProfileDialog = false }
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
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Branding Icon Header
            Box(
                modifier = Modifier
                    .size(76.dp)
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
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            // MTProto Profile Selector Card (Prominent & Easy 1-tap switcher)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ApiProfileIcon(
                                    profile = selectedProfile,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = selectedProfile.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = selectedProfile.badge,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        TextButton(
                            onClick = { showProfileDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Всі варіанти", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Quick-Switch Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ApiProfile.values().forEach { profile ->
                            val isSelected = selectedProfile == profile
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (profile == ApiProfile.CUSTOM) {
                                        showProfileDialog = true
                                    } else {
                                        applyProfile(profile)
                                    }
                                },
                                label = {
                                    Text(
                                        text = profile.shortLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

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
                                            applyProfile(ApiProfile.DESKTOP)
                                            val rawDigits = phoneNumber.filter { it.isDigit() }
                                            if (rawDigits.length >= 7) {
                                                isLoading = true
                                                scope.launch {
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
                                    val clean = if (input.startsWith("+")) {
                                        "+" + input.substring(1).filter { it.isDigit() }
                                    } else {
                                        input.filter { it.isDigit() }
                                    }
                                    if (clean.filter { it.isDigit() }.length <= 15) {
                                        phoneNumber = clean
                                        errorMessage = null
                                        recaptchaSitekey = null
                                    }
                                },
                                visualTransformation = remember { PhoneVisualTransformation() },
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
                            // Show verified recipient phone number badge
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Код надіслано на: $phoneNumber",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Text(
                                text = "💬 Перевірте додаток Telegram на іншому телефоні чи ПК — код приходить у системний чат 'Telegram' (із синьою галочкою)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

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

                            Spacer(modifier = Modifier.height(10.dp))

                            // Resend Code / SMS Countdown Button
                            FilledTonalButton(
                                onClick = {
                                    if (resendCooldown == 0 && !isLoading) {
                                        isLoading = true
                                        scope.launch {
                                            val rawDigits = phoneNumber.filter { it.isDigit() }
                                            val res = YumigramBridge.requestCode("+$rawDigits")
                                            isLoading = false
                                            if (res.success) {
                                                errorMessage = null
                                                resendCooldown = 60
                                            } else {
                                                errorMessage = res.message ?: "Помилка повторного запиту коду"
                                            }
                                        }
                                    }
                                },
                                enabled = resendCooldown == 0 && !isLoading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (resendCooldown > 0) {
                                    Text("Повторний запит через ${resendCooldown}с", fontSize = 13.sp)
                                } else {
                                    Text("📩 Надіслати код повторно (SMS / Telegram)", fontSize = 13.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick Profile Switcher Rescue Card on Step CODE
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.HelpOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Не надходить код?",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Поточний клієнт: ${selectedProfile.label}. Спробуйте перемкнути API та надіслати код повторно в 1 клік:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            ApiProfile.MACOS,
                                            ApiProfile.WEB,
                                            ApiProfile.DESKTOP,
                                            ApiProfile.IOS,
                                            ApiProfile.ANDROID_X
                                        ).forEach { altProfile ->
                                            if (altProfile != selectedProfile) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        applyProfile(altProfile)
                                                        val rawDigits = phoneNumber.filter { it.isDigit() }
                                                        if (rawDigits.length >= 7 && !isLoading) {
                                                            isLoading = true
                                                            errorMessage = null
                                                            scope.launch {
                                                                val res = YumigramBridge.requestCode("+$rawDigits")
                                                                isLoading = false
                                                                if (res.success) {
                                                                    resendCooldown = 60
                                                                    code = ""
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Запит надіслано через ${altProfile.shortLabel}! Перевірте Telegram.",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                } else {
                                                                    errorMessage = res.message ?: "Помилка відправки коду"
                                                                }
                                                            }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("⚡ ${altProfile.shortLabel}", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
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
                                Text("Змінити номер телефону")
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
        }
    }
}
