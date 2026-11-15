package org.yumigram.app.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class StarGiftItem(
    val emoji: String,
    val title: String,
    val starsPrice: Int
)

private val STAR_GIFTS_CATALOG = listOf(
    StarGiftItem("🧸", "Плюшевий ведмедик", 15),
    StarGiftItem("🌹", "Червона троянда", 25),
    StarGiftItem("🎂", "Святковий торт", 30),
    StarGiftItem("🍾", "Шампанське", 45),
    StarGiftItem("🚀", "Космічна ракета", 50),
    StarGiftItem("🎁", "Преміум бокс", 75),
    StarGiftItem("💎", "Діамант TON", 100),
    StarGiftItem("👑", "Золота корона", 250)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramStarsAndBusinessScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Stars & Crypto, 1 = Premium, 2 = Business, 3 = Send Gift

    var starsBalance by remember { mutableIntStateOf(350) }
    var tonBalance by remember { mutableStateOf("14.50") }
    var gramBalance by remember { mutableStateOf("1,250") }

    // Business states
    var greetingEnabled by remember { mutableStateOf(true) }
    var awayMessageEnabled by remember { mutableStateOf(false) }
    var quickRepliesCount by remember { mutableIntStateOf(5) }

    // Send Gift Dialog State
    var selectedGiftToSend by remember { mutableStateOf<StarGiftItem?>(null) }

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
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Зірки, Преміум та Бізнес", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрити")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                // Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Зірки ⭐", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Преміум 👑", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Бізнес 💼", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Подарунки 🎁", fontSize = 12.sp) }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // TELEGRAM STARS & CRYPTO / GRAM
                            item {
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFFFD54F), Color(0xFFFFA000))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("⭐", fontSize = 34.sp)
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = "$starsBalance Telegram Stars",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text = "Офіційна внутрішня валюта для подарунків, ботів та підписок",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    starsBalance += 100
                                                    Toast.makeText(context, "+100 Telegram Stars додано до балансу!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.Black)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Купити зірки", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = { selectedTab = 3 },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("Витратити")
                                            }
                                        }
                                    }
                                }
                            }

                            // GRAM & TONCOIN WALLET CARD
                            item {
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "ГАМАНЕЦЬ TON SPACE & ВАЛЮТА GRAM",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF0098EA)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF0098EA).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "UQ...3Fa9",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0098EA),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Баланс Toncoin:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$tonBalance TON", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0098EA))
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Валюта GRAM:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$gramBalance GRAM", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C4DFF))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        FilledTonalButton(
                                            onClick = {
                                                Toast.makeText(context, "Гаманець TON Space синхронізовано", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Керувати гаманцем у Telegram")
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // TELEGRAM PREMIUM
                            item {
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF7C4DFF), Color(0xFF00B0FF))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = "Telegram Premium Активний",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            text = "Підписка активована · Зірковий бейдж ⭐ біля імені",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            PremiumFeatureItem("Подвоєні ліміти", "До 4 ГБ завантаження файлів, до 1000 каналів, 20 папок")
                                            PremiumFeatureItem("Розпізнавання голосу", "Перетворення голосових та кружечків у текст одним дотиком")
                                            PremiumFeatureItem("Преміум стікери та емодзі", "Повний доступ до всіх анімованих реакцій та стікерпаків")
                                            PremiumFeatureItem("Швидкість завантаження х10", "Максимальна швидкість завантаження без обмежень серверу")
                                            PremiumFeatureItem("Відсутність реклами", "Жодної реклами в публічних каналах")
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // TELEGRAM BUSINESS
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
                                            text = "ФУНКЦІЇ TELEGRAM BUSINESS",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Greeting
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Вітальне повідомлення", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text("Надсилати при першому зверненні клієнта", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = greetingEnabled, onCheckedChange = { greetingEnabled = it })
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                        // Away message
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Повідомлення «Не на місці»", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text("Автовідповідь у неробочі години", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(checked = awayMessageEnabled, onCheckedChange = { awayMessageEnabled = it })
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                        // Quick replies
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    Toast.makeText(context, "Налаштування швидких відповідей (/info, /price)", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Швидкі відповіді", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text("Шаблони повідомлень за командами", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("$quickRepliesCount шаблонів", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                                        // Working hours
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    Toast.makeText(context, "Робочі години: Пн-Пт 09:00 - 19:00", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Години роботи", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                Text("Пн-Пт 09:00 - 19:00", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // STAR GIFTS CATALOG (Подарунки)
                            item {
                                Text(
                                    text = "КАТАЛОГ ПОДАРУНКІВ TELEGRAM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            item {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp)
                                ) {
                                    items(STAR_GIFTS_CATALOG) { gift ->
                                        ElevatedCard(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.elevatedCardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedGiftToSend = gift }
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(gift.emoji, fontSize = 38.sp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = gift.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFFFFA000).copy(alpha = 0.2f)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("⭐", fontSize = 11.sp)
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "${gift.starsPrice}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFFFB300)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Send Gift Confirmation Dialog
    selectedGiftToSend?.let { gift ->
        var recipient by remember { mutableStateOf("@yumigram_friend") }
        var giftComment by remember { mutableStateOf("З найкращими побажаннями! 🎉") }
        var isAnonymous by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { selectedGiftToSend = null },
            icon = { Text(gift.emoji, fontSize = 42.sp) },
            title = { Text("Надіслати ${gift.title}", textAlign = TextAlign.Center) },
            text = {
                Column {
                    Text(
                        text = "Вартість: ⭐ ${gift.starsPrice} Stars (Ваш баланс: $starsBalance)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("Отримувач (@username)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = giftComment,
                        onValueChange = { giftComment = it },
                        label = { Text("Коментар до подарунка") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Приховати моє ім'я (Анонімний подарунок)", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (starsBalance >= gift.starsPrice) {
                            starsBalance -= gift.starsPrice
                            Toast.makeText(context, "${gift.title} успішно надіслано користувачу $recipient!", Toast.LENGTH_LONG).show()
                            selectedGiftToSend = null
                        } else {
                            Toast.makeText(context, "Недостатньо зірок! Поповніть баланс.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Надіслати (⭐ ${gift.starsPrice})")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedGiftToSend = null }) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@Composable
private fun PremiumFeatureItem(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
