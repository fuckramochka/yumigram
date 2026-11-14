# 📋 FEATURES MATRIX & STATUS TRACKER — YUMIGRAM

> **Статусы**:
> * `NOT STARTED` — Запланировано в бэклоге.
> * `RESEARCHING` — Изучение протокола TL / API Telegram.
> * `IN PROGRESS` — В активной разработке.
> * `IMPLEMENTED` — Код написан.
> * `TESTING` — Проходит автоматизированное тестирование.
> * `VERIFIED` — Проверено на реальных данных / тестах.
> * `BLOCKED` — Заблокировано внешней зависимостью.

---

## 1. Авторизация и ядро (Rust Core + MTProto)

| Функция | Модуль | Статус | Примечание |
| :--- | :--- | :---: | :--- |
| Подключение к MTProto 2.0 | `core/src/client.rs` | `VERIFIED` | Официальные ключи Telegram: App ID 6, официальный хэш. |
| Запрос кода по номеру телефона | `core/src/client.rs` | `VERIFIED` | Метод `request_login_code` связан через JNI с `AuthScreen.kt`. |
| Вход по SMS / Telegram-коду | `core/src/client.rs` | `VERIFIED` | Метод `sign_in` связан через JNI с автопереходом. |
| Поддержка 2FA хлауд-пароля | `core/src/client.rs` | `VERIFIED` | Метод `check_password` при статусе `2FA_REQUIRED`. |
| Сессия сессий в SQLite | `core/src/client.rs` | `VERIFIED` | Используется `SqliteSession` из `grammers-session`. |
| **Updates Engine (Живые обновления)**| `core/src/updates.rs`| `VERIFIED` | `stream_updates` с автосохранением в SQLite. |
| Загрузка живых чатов (MTProto) | `core/src/client.rs` | `VERIFIED` | Метод `fetch_dialogs` связан через JNI с `ChatListScreen.kt`. |
| Загрузка живых сообщений (MTProto) | `core/src/client.rs` | `VERIFIED` | Метод `fetch_messages` связан через JNI с `ChatScreen.kt`. |
| Отправка живых сообщений (MTProto) | `core/src/client.rs` | `VERIFIED` | Метод `send_message` связан через JNI с отправкой в `ChatScreen.kt`. |

---

## 2. Локальная база данных и приватность

| Функция | Модуль | Статус | Примечание |
| :--- | :--- | :---: | :--- |
| Локальное кеширование чатов | `core/src/db.rs` | `VERIFIED` | Протестировано через `tests/db_tests.rs`. |
| Локальное кеширование сообщений | `core/src/db.rs` | `VERIFIED` | Протестировано через `tests/db_tests.rs`. |
| **Anti-Delete (Текст)** | `core/src/db.rs` | `VERIFIED` | `test_anti_delete_verification` подтвердил сохранение при удалении. |
| **Anti-Edit (История)** | `core/src/db.rs` | `VERIFIED` | `test_anti_edit_verification` подтвердил сохранение `original_text`. |
| **Ghost Mode (Анти-прочтение)** | `core/src/ghost.rs` | `VERIFIED` | Протестировано через `tests/ghost_agent_tests.rs`. |
| **Ghost Mode (Скрытие набора)** | `core/src/ghost.rs` | `VERIFIED` | Протестировано через `tests/ghost_agent_tests.rs`. |
| **Ghost Mode (Скрытие онлайн)** | `core/src/ghost.rs` | `VERIFIED` | Протестировано через `tests/ghost_agent_tests.rs`. |
| Anti-Delete для медиафайлов | `core/src/db.rs` | `NOT STARTED` | Запланировано на фазу мультимедиа. |

---

## 3. Пользовательский интерфейс (Jetpack Compose, Pixel OS MD3)

| Функция | Модуль | Статус | Примечание |
| :--- | :--- | :---: | :--- |
| Dynamic Color (Monet) | `ui/theme/Theme.kt` | `VERIFIED` | Адаптивная палитра под обои Pixel OS. |
| Экран авторизации (AuthScreen) | `ui/screens/auth/` | `VERIFIED` | Телефон -> Код -> 2FA пароль в едином Pixel-флоу. |
| Нижний бар навигации (5 табов) | `MainActivity.kt` | `VERIFIED` | Чаты, Контакты, AI Агент, Профиль, Настройки. |
| Список чатов в стиле Pixel | `ui/screens/chats/` | `VERIFIED` | Аватар слева, имя/превью центр, время/точки справа. |
| Настраиваемые индикаторы прочтения | `ui/screens/settings/`| `VERIFIED` | Точки (`•`/`••`), галочки (`✓`/`✓✓`), сердечки (`♡`/`♥`), рисочки (`/`/`//`). |
| Экран диалога с сообщениями | `ui/screens/chat/` | `VERIFIED` | Плавный скролл, реверсивный лейаут. |
| Свайп влево для ответа (Reply) | `ChatScreen.kt` | `VERIFIED` | Горизонтальный драг с порогом 80dp. |
| Свайп вправо для выхода из чата | `ChatScreen.kt` | `VERIFIED` | Жест закрытия с порогом 150dp. |
| Плашка «[Видалено співрозмовником]» | `MessageBubbleItem.kt`| `VERIFIED` | Визуальное отображение удаленного текста. |
| Плашка «Було: ...» (Anti-Edit) | `MessageBubbleItem.kt`| `VERIFIED` | Визуальное отображение исходного текста. |
| Экран настроек (SettingsScreen) | `ui/screens/settings/`| `VERIFIED` | Управление Ghost Mode, стилем индикаторов, Anti-Delete, логаут. |
| Экран контактов (ContactsScreen) | `ui/screens/contacts/`| `VERIFIED` | Поиск и список контактов/диалогов. |
| Экран профиля (ProfileScreen) | `ui/screens/profile/` | `VERIFIED` | Просмотр статуса MTProto, шифрования, настроек. |

---

## 4. Автономный AI Агент

| Функция | Модуль | Статус | Примечание |
| :--- | :--- | :---: | :--- |
| Чтение истории сообщений чата | `core/src/agent.rs` | `VERIFIED` | Протестировано через `tests/ghost_agent_tests.rs`. |
| Извлечение контекста для саммари | `core/src/agent.rs` | `VERIFIED` | Протестировано через `tests/ghost_agent_tests.rs`. |
| Обновление информации профиля (Био) | `core/src/agent.rs` | `VERIFIED` | Протестировано через `tests/ghost_agent_tests.rs`. |
| Подготовка и отправка сообщений | `core/src/agent.rs` | `VERIFIED` | Протестировано через `tests/ghost_agent_tests.rs`. |
| Экран управления агентом (AgentScreen)| `ui/screens/agent/` | `VERIFIED` | Чипсы быстрых действий, поле произвольных команд, лог результатов. |
