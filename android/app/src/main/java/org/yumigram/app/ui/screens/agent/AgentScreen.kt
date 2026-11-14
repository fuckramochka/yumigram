package org.yumigram.app.ui.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.AgentAction
import org.yumigram.app.bridge.AgentStructuredSummary
import org.yumigram.app.bridge.AiSettingsModel
import org.yumigram.app.bridge.YumigramBridge

data class AgentLogItem(
    val title: String,
    val details: String,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    modifier: Modifier = Modifier
) {
    var promptText by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var aiSettings by remember { mutableStateOf(AiSettingsModel()) }
    var showSettingsCard by remember { mutableStateOf(false) }

    var latestSummary by remember { mutableStateOf<AgentStructuredSummary?>(null) }
    var draftToConfirm by remember { mutableStateOf<String?>(null) }
    var targetChatIdForDraft by remember { mutableStateOf<Long?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var logHistory by remember {
        mutableStateOf(
            listOf(
                AgentLogItem(
                    title = "AI Коннектор активовано",
                    details = "Підключено модуль інтелектуального аналізу з апаратним захистом конфіденційності (PrivacyGuard).",
                    isSuccess = true
                )
            )
        )
    }

    val scope = rememberCoroutineScope()

    // Load saved AI settings on launch
    LaunchedEffect(Unit) {
        val s = YumigramBridge.getAiSettings()
        aiSettings = s
        apiKeyInput = s.gemini_api_key
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Yumigram AI Agent",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsCard = !showSettingsCard }) {
                        Icon(
                            imageVector = if (showSettingsCard) Icons.Rounded.Close else Icons.Rounded.Tune,
                            contentDescription = "AI Settings"
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Status Card
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (aiSettings.provider == "gemini") "Google Gemini 1.5" else "Local ONNX / Offline",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Privacy Guard",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Розумні саммарі, генерація чернеток та виявлення задач без витоку особистих даних.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Expandable AI Settings Card (Gemini API Key & Privacy controls)
            if (showSettingsCard) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Налаштування моделі AI",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Provider Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = aiSettings.provider == "local",
                                    onClick = {
                                        aiSettings = aiSettings.copy(provider = "local")
                                        scope.launch {
                                            YumigramBridge.saveAiSettings("local", aiSettings.gemini_api_key, aiSettings.strict_privacy)
                                        }
                                    },
                                    label = { Text("Local ONNX") },
                                    leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null) }
                                )

                                FilterChip(
                                    selected = aiSettings.provider == "gemini",
                                    onClick = {
                                        aiSettings = aiSettings.copy(provider = "gemini")
                                        scope.launch {
                                            YumigramBridge.saveAiSettings("gemini", aiSettings.gemini_api_key, aiSettings.strict_privacy)
                                        }
                                    },
                                    label = { Text("Google Gemini") },
                                    leadingIcon = { Icon(Icons.Rounded.Cloud, contentDescription = null) }
                                )
                            }

                            if (aiSettings.provider == "gemini") {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("Gemini API Key") },
                                    placeholder = { Text("AIzaSy...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showApiKey = !showApiKey }) {
                                            Icon(
                                                imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        aiSettings = aiSettings.copy(gemini_api_key = apiKeyInput)
                                        scope.launch {
                                            val ok = YumigramBridge.saveAiSettings(
                                                aiSettings.provider,
                                                apiKeyInput,
                                                aiSettings.strict_privacy
                                            )
                                            logHistory = listOf(
                                                AgentLogItem(
                                                    title = "Ключ Gemini збережено",
                                                    details = if (ok) "Ключ успішно записано у захищену локальну БД" else "Помилка збереження",
                                                    isSuccess = ok
                                                )
                                            ) + logHistory
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Зберегти ключ")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Повна санітизація PII (Privacy Guard)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Автоматично маскує номери телефонів, картки, e-mail та паролі перед обробкою",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = aiSettings.strict_privacy,
                                    onCheckedChange = {
                                        aiSettings = aiSettings.copy(strict_privacy = it)
                                        scope.launch {
                                            YumigramBridge.saveAiSettings(
                                                aiSettings.provider,
                                                aiSettings.gemini_api_key,
                                                it
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Action Pills
            item {
                Text(
                    text = "Дії розумного асистента",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                val summary = YumigramBridge.summarizeChatSmart(null)
                                latestSummary = summary
                                isLoading = false
                                logHistory = listOf(
                                    AgentLogItem(
                                        title = "Розумне саммарі (${summary.provider_used})",
                                        details = summary.summary_text,
                                        isSuccess = true
                                    )
                                ) + logHistory
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Скласти саммарі")
                    }

                    FilledTonalButton(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                val chats = YumigramBridge.getChats()
                                val topChat = chats.firstOrNull()
                                if (topChat != null) {
                                    val res = YumigramBridge.draftReply(topChat.id, "погодитися та подякувати")
                                    isLoading = false
                                    if (res.success && res.data != null) {
                                        draftToConfirm = res.data
                                        targetChatIdForDraft = topChat.id
                                    }
                                } else {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Чернетка")
                    }
                }
            }

            // Structured Summary Results Card (Rich Presentation)
            if (latestSummary != null) {
                item {
                    val summary = latestSummary!!
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📊 Підсумок: ${summary.chat_title}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "${summary.total_messages} пов.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = summary.summary_text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            // Key Topics Chips
                            if (summary.key_topics.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ключові теми:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(summary.key_topics) { topic ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = topic,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Action Items
                            if (summary.action_items.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Домовленості та завдання:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                summary.action_items.forEach { action ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = action,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            // Unanswered questions
                            if (summary.unanswered_questions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Відкриті питання:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                summary.unanswered_questions.forEach { q ->
                                    Text(
                                        text = "• $q",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Custom prompt box
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            label = { Text("Команда агенту...") },
                            placeholder = { Text("Наприклад: напиши повідомлення, що я запізнююся на 15 хвилин") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (promptText.isNotBlank()) {
                                    val userCommand = promptText
                                    promptText = ""
                                    isLoading = true
                                    scope.launch {
                                        val chats = YumigramBridge.getChats()
                                        val targetId = chats.firstOrNull()?.id ?: 0L
                                        val res = YumigramBridge.draftReply(targetId, userCommand)
                                        isLoading = false
                                        if (res.success && res.data != null) {
                                            draftToConfirm = res.data
                                            targetChatIdForDraft = targetId
                                        }
                                        logHistory = listOf(
                                            AgentLogItem(
                                                title = "Команда: $userCommand",
                                                details = res.data ?: res.message,
                                                isSuccess = res.success
                                            )
                                        ) + logHistory
                                    }
                                }
                            },
                            enabled = !isLoading && promptText.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Виконати")
                            }
                        }
                    }
                }
            }

            // Results Log Section
            item {
                Text(
                    text = "Журнал автономних дій",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(logHistory) { log ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (log.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                contentDescription = null,
                                tint = if (log.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = log.title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = log.details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Confirmation Dialog before sending an AI-drafted message
    if (draftToConfirm != null && targetChatIdForDraft != null) {
        AlertDialog(
            onDismissRequest = { draftToConfirm = null },
            icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
            title = { Text("Підтвердження відправки") },
            text = {
                Column {
                    Text(
                        text = "AI Асистент підготував чернетку для відправки:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = draftToConfirm!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val textToSend = draftToConfirm!!
                        val chatId = targetChatIdForDraft!!
                        draftToConfirm = null
                        targetChatIdForDraft = null
                        scope.launch {
                            val res = YumigramBridge.sendAgentMessage(chatId, textToSend)
                            logHistory = listOf(
                                AgentLogItem(
                                    title = "Повідомлення відправлено",
                                    details = res.message,
                                    isSuccess = res.success
                                )
                            ) + logHistory
                        }
                    }
                ) {
                    Text("Відправити зараз")
                }
            },
            dismissButton = {
                TextButton(onClick = { draftToConfirm = null }) {
                    Text("Скасувати")
                }
            }
        )
    }
}
