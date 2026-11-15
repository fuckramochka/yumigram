package org.yumigram.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/// Only screens with real, working functionality are exposed.
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Chats : Screen("chats", "Чати", Icons.Rounded.ChatBubble)
    object Contacts : Screen("contacts", "Контакти", Icons.Rounded.People)
    object Settings : Screen("settings", "Налаштування", Icons.Rounded.Settings)
}
