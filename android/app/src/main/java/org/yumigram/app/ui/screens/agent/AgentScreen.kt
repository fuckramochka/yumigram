package org.yumigram.app.ui.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.yumigram.app.bridge.AgentAction
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
    var isLoading by remember { mutableStateOf(false) }
    var logHistory by remember {
        mutableStateOf(
            listOf(
                AgentLogItem(
                    title = "Система ініціалізована",
                    details = "AI Агент готовий до автономних дій: читання чатів, відповіді, аналіз та керування профілем.",
                    isSuccess = true
                )
            )
        )
    }

    val scope = rememberCoroutineScope()

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Hero card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Автономний асистент",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Виконує дії як реальний користувач: читає чати, аналізує діалоги, відповідає та оновлює статус.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Quick Pills
            Text(
                text = "Швидкі дії асистента",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val res = YumigramBridge.executeAgent(
                                AgentAction(action_type = "summarize", text = "Аналіз останніх важливих подій у чатах")
                            )
                            isLoading = false
                            logHistory = listOf(
                                AgentLogItem(
                                    title = "Аналіз останніх повідомлень",
                                    details = res.data ?: res.message,
                                    isSuccess = res.success
                                )
                            ) + logHistory
                        }
                    },
                    label = { Text("Підсумок чатів") },
                    leadingIcon = { Icon(Icons.Rounded.Summarize, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                AssistChip(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val res = YumigramBridge.executeAgent(
                                AgentAction(action_type = "update_bio", bio = "Powered by Yumigram 🚀 Pixel OS Edition")
                            )
                            isLoading = false
                            logHistory = listOf(
                                AgentLogItem(
                                    title = "Оновлення Bio акаунта",
                                    details = res.message,
                                    isSuccess = res.success
                                )
                            ) + logHistory
                        }
                    },
                    label = { Text("Оновити Bio") },
                    leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom prompt box
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Довільна команда агенту...") },
                        placeholder = { Text("Наприклад: проаналізуй останні повідомлення та склади відповідь") },
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
                                    val res = YumigramBridge.executeAgent(
                                        AgentAction(action_type = "summarize", text = userCommand)
                                    )
                                    isLoading = false
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Журнал автономних дій",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Results Log
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
            }
        }
    }
}
