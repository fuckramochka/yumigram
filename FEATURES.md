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

## 3. Модульна система плагінів (Plugin System)

| Функція | Модуль | Статус | Примітка |
| :--- | :--- | :---: | :--- |
| Маніфест плагіна (`PluginManifest`) | `core/src/plugins.rs` | `VERIFIED` | ID, назва, версія, автор, опис, перелік дозволів. |
| Система дозволів (`PluginPermission`)| `core/src/plugins.rs` | `VERIFIED` | `ModifyOutgoing`, `ReadMessages`, `SendMessages`, `AccessMedia`. |
| Хуки вихідних повідомлень | `core/src/plugins.rs` | `VERIFIED` | Перехоплення та трансформація тексту перед відправкою MTProto. |
| Збереження стану плагінів | `core/src/plugins.rs` | `VERIFIED` | Збереження активності плагінів у таблиці `settings` SQLite. |
| UI Менеджер плагінів | `PluginsScreen.kt` | `VERIFIED` | Список модулів, перемикачі увімкнення/вимкнення, деталі дозволів. |

---

## 4. Мультимедіа та контент

| Функція | Модуль | Статус | Примітка |
| :--- | :--- | :---: | :--- |
| Асинхронне завантаження аватарок | `ChatListScreen.kt` | `VERIFIED` | Інтеграція Coil `AsyncImage` із fallback на літери. |
| Відображення фото-вкладень | `ChatScreen.kt` | `VERIFIED` | Заокруглені прев'ю-картки в бульбашках повідомлень. |
| Повноекранний переглядач медіа | `MediaViewerDialog.kt`| `VERIFIED` | Повний екран із жестом свайпу вниз для закриття та Anti-Delete плашкою. |
| Плеєр голосових повідомлень | `VoicePlayer.kt` | `VERIFIED` | Media3 ExoPlayer із прогрес-баром та кнопкою Play/Pause. |
| Регулювання швидкості аудіо | `VoicePlayer.kt` | `VERIFIED` | Перемикач швидкості `1x` / `1.5x` / `2x`. |
| Документи та файли | `ChatScreen.kt` | `VERIFIED` | Картка файлу з назвою та іконкою документа. |
