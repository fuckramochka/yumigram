package org.yumigram.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.yumigram.app.bridge.StickerModel
import org.yumigram.app.bridge.YumigramBridge
import java.io.File

private val TRAY_EMOJIS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "😊", "😇",
    "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😚", "😋", "😛", "😜",
    "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🫡", "🤐", "🤨", "😐", "😑",
    "😶", "🫥", "😏", "😒", "🙄", "😬", "😮‍💨", "🤥", "😌", "😔", "😪", "🤤",
    "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "😵‍💫",
    "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮",
    "😯", "😲", "😳", "🥺", "🥹", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
    "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹",
    "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "✨", "🔥", "💥", "💯",
    "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "⭐", "🌟", "💫", "👍", "👎", "👏",
    "🙌", "👐", "🤲", "🤝", "🙏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉"
)

enum class PickerTab(val title: String) {
    EMOJIS("Емоджі"),
    GIFS("GIF"),
    STICKERS("Наліпки")
}

@Composable
fun StickerPickerTray(
    cacheDir: String,
    onStickerSelected: (StickerModel) -> Unit,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(PickerTab.STICKERS) }
    var stickers by remember { mutableStateOf<List<StickerModel>>(emptyList()) }
    var isLoadingStickers by remember { mutableStateOf(true) }
    var selectedPackIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val list = YumigramBridge.getStickers(cacheDir)
            stickers = list
        } catch (e: Throwable) {
            android.util.Log.e("StickerPickerTray", "Error loading stickers", e)
        } finally {
            isLoadingStickers = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .background(Color(0xFF141418))
    ) {
        // 1. Top bar: horizontal bar of sticker pack icons (Recent, Favorites, installed packs)
        if (selectedTab == PickerTab.STICKERS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Recent icon
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (selectedPackIndex == 0) Color(0xFF9333EA) else Color.Transparent)
                        .clickable { selectedPackIndex = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = "Нещодавні",
                        tint = if (selectedPackIndex == 0) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Favorites icon
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (selectedPackIndex == 1) Color(0xFF9333EA) else Color.Transparent)
                        .clickable { selectedPackIndex = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Улюблені",
                        tint = if (selectedPackIndex == 1) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Installed packs sample icons
                for (i in 2..5) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (selectedPackIndex == i) Color(0xFF9333EA) else Color.White.copy(alpha = 0.05f))
                        .clickable { selectedPackIndex = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (i) {
                                2 -> "🐸"
                                3 -> "🐱"
                                4 -> "🔥"
                                else -> "✨"
                            },
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // 2. Main content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                PickerTab.STICKERS -> {
                    if (isLoadingStickers) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color(0xFFA855F7),
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else if (stickers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🎨 Стікери відсутні",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        // Render transparently directly on dark surface without frames or cards!
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(stickers, key = { it.document_id }) { sticker ->
                                val localFile = remember(sticker.thumb_path) {
                                    if (!sticker.thumb_path.isNullOrEmpty()) File(sticker.thumb_path) else null
                                }

                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clickable { onStickerSelected(sticker) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (localFile != null && localFile.exists() && localFile.length() > 0) {
                                        AsyncImage(
                                            model = localFile,
                                            contentDescription = sticker.emoji,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = sticker.emoji.ifBlank { "⭐" },
                                            fontSize = 38.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PickerTab.EMOJIS -> {
                    // Standard dense grid (8 emojis per row without frames or button borders)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(TRAY_EMOJIS) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable { onEmojiSelected(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 26.sp)
                            }
                        }
                    }
                }

                PickerTab.GIFS -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Gif,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Пошук популярних GIF через Tenor...",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        // 3. Bottom tabs: Перемикач «Емоджі | GIF | Наліпки» з іконкою лупи для швидкого пошуку
        Surface(
            color = Color(0xFF1A1A20),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Switcher tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PickerTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF9333EA) else Color.Transparent,
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { selectedTab = tab }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = tab.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Search magnifying glass icon
                IconButton(
                    onClick = { /* Search tray */ },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Швидкий пошук",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
