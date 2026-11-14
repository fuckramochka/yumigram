# 📋 FEATURES MATRIX & STATUS TRACKER — YUMIGRAM

> **Статуси**:
> * `PLANNED` — Заплановано в беклозі.
> * `IN_PROGRESS` — В активній розробці.
> * `IMPLEMENTED` — Код написано.
> * `TESTED` — Проходить автоматизоване тестування.
> * `VERIFIED` — Підтверджено реальними тестами / збіркою.

---

## 1. Авторизація та ядро (Rust Core + MTProto)

| Функція | Модуль | Статус | Примітка |
| :--- | :--- | :---: | :--- |
| Підключення до MTProto 2.0 | `core/src/client.rs` | `VERIFIED` | Офіційні ключі: App ID 6, офіційний хеш Telegram. |
| Запуск фонового мережевого I/O | `core/src/client.rs` | `VERIFIED` | Фоновий таск `tokio::spawn(runner.run())` для обробки TCP фреймів. |
| Запит коду за номером телефону | `core/src/client.rs` | `VERIFIED` | Метод `request_login_code` зв'язаний через JNI з `AuthScreen.kt`. |
| Вхід за SMS / Telegram кодом | `core/src/client.rs` | `VERIFIED` | Метод `sign_in` з автоматичним запуском оновлень. |
| Підтримка 2FA хмарного пароля | `core/src/client.rs` | `VERIFIED` | Метод `check_password` при статусі `2FA_REQUIRED`. |
| Сесія у SQLite | `core/src/client.rs` | `VERIFIED` | Використовується `SqliteSession` із `grammers-session`. |
| **Updates Engine (Живі оновлення)**| `core/src/updates.rs`| `VERIFIED` | `stream_updates` із розбором повідомлень, фото, аудіо та видалень. |
| Завантаження живих чатів (MTProto) | `core/src/client.rs` | `VERIFIED` | Метод `fetch_dialogs` із збереженням у SQLite. |
| Завантаження живих повідомлень | `core/src/client.rs` | `VERIFIED` | Метод `fetch_messages` із розпізнаванням медіатипу. |
| Відправка повідомлень (MTProto) | `core/src/client.rs` | `VERIFIED` | Метод `send_message` із збереженням у SQLite. |

---

## 2. Локальна база даних, приватність та медіа

| Функція | Модуль | Статус | Примітка |
| :--- | :--- | :---: | :--- |
| Локальне кешування чатів | `core/src/db.rs` | `VERIFIED` | Протестовано через `tests/db_tests.rs`. |
| Локальне кешування повідомлень | `core/src/db.rs` | `VERIFIED` | Протестовано через `tests/db_tests.rs`. |
| **Anti-Delete (Текст)** | `core/src/db.rs` | `VERIFIED` | `test_anti_delete_verification` підтвердив збереження при видаленні. |
| **Anti-Delete (Медіа)** | `core/src/db.rs` | `VERIFIED` | `test_media_anti_delete_storage` підтвердив збереження `media_path`. |
| **Anti-Edit (Історія)** | `core/src/db.rs` | `VERIFIED` | `test_anti_edit_verification` підтвердив збереження `original_text`. |
| **Ghost Mode (Анти-прочитання)**| `core/src/ghost.rs` | `VERIFIED` | Протестовано через `tests/ghost_agent_tests.rs`. |
| **Ghost Mode (Приховування набору)**| `core/src/ghost.rs` | `VERIFIED` | Протестовано через `tests/ghost_agent_tests.rs`. |
| **Ghost Mode (Приховування онлайну)**| `core/src/ghost.rs` | `VERIFIED` | Протестовано через `tests/ghost_agent_tests.rs`. |

---

## 3. Мультимедіа та контент (Фаза 3)

| Функція | Модуль | Статус | Примітка |
| :--- | :--- | :---: | :--- |
| Асинхронне завантаження аватарок | `ChatListScreen.kt` | `VERIFIED` | Інтеграція Coil `AsyncImage` із fallback на літери. |
| Відображення фото-вкладень | `ChatScreen.kt` | `VERIFIED` | Заокруглені прев'ю-картки в бульбашках повідомлень. |
| Повноекранний переглядач медіа | `MediaViewerDialog.kt`| `VERIFIED` | Повний екран із жестом свайпу вниз для закриття та Anti-Delete плашкою. |
| Плеєр голосових повідомлень | `VoicePlayer.kt` | `VERIFIED` | Media3 ExoPlayer із прогрес-баром та кнопкою Play/Pause. |
| Регулювання швидкості аудіо | `VoicePlayer.kt` | `VERIFIED` | Перемикач швидкості `1x` / `1.5x` / `2x`. |
| Документи та файли | `ChatScreen.kt` | `VERIFIED` | Картка файлу з назвою та іконкою документа. |

---

## 4. Користувацький інтерфейс (Jetpack Compose MD3 Expressive)

| Функція | Модуль | Статус | Примітка |
| :--- | :--- | :---: | :--- |
| Dynamic Color (Monet) | `ui/theme/Theme.kt` | `VERIFIED` | Адаптація під системні шпалери Android. |
| Свайп вліво для відповіді (Reply) | `ChatScreen.kt` | `VERIFIED` | Драг-жест із прев'ю-плашкою над полем введення. |
| Свайп вправо для закриття чату | `ChatScreen.kt` | `VERIFIED` | Горизонтальний жест закриття з порогом 150dp. |
| Кастомні значки прочитання | `SettingsScreen.kt` | `VERIFIED` | Крапки (`•`/`••`), галочки (`✓`/`✓✓`), сердечка (`♡`/`♥`), рисочки (`/`/`//`). |
| Нижня панель навігації (5 розділів)| `MainActivity.kt` | `VERIFIED` | Чати, Контакти, AI Агент, Профіль, Налаштування. |
