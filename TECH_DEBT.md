# ⚠️ TECHNICAL DEBT & CODE HEALTH — YUMIGRAM

> **Політика нульового приховування проблем**: «Будь-який недолік, недоробка чи вузьке місце повинні бути негайно задокументовані та усунені».

---

## 1. Виправлений технічний борг (Resolved Debt)

### [RESOLVED] Розсинхронізація сигнатури `nativeInit` між JNI та Kotlin
- **Статус**: `RESOLVED` (v0.2.1)
- **Проблема**: У `lib.rs` функція приймала `db_path` та `session_path`, тоді як у `YumigramBridge.kt` був лише один параметр `dbPath`. Це призводило до помилки лінкування JNI під час запуску.
- **Рішення**: Оновлено `YumigramBridge.kt` та `MainActivity.kt` для передачі обох шляхів: `yumigram.db` та `yumigram_session.session`.

### [RESOLVED] Не запущений `SenderPoolRunner` та відсутність реального I/O
- **Статус**: `RESOLVED` (v0.2.1)
- **Проблема**: `SenderPool::new` повертав `runner`, але він не запускався у фоновому таску `tokio::spawn(runner.run())`. Через це всі спроби звернення до MTProto серверів переривалися з помилкою `SenderPoolRunner was dropped at send`.
- **Рішення**: У `TelegramManager::connect` додано фоновий таск `tokio::spawn(async move { runner.run().await; })`.

### [RESOLVED] Непідключений потік живих оновлень `UpdatesEngine`
- **Статус**: `RESOLVED` (v0.2.1)
- **Проблема**: Канал `pool.updates` не оброблявся через `stream_updates`, і `UpdatesEngine` залишався відключеним.
- **Рішення**: Реалізовано метод `start_updates_stream(db, tx_event)`, який запускає `stream_updates` та фоновий цикл обробки `UpdatesEngine::run_loop` одразу після успішної авторизації.

---

## 2. Активний технічний борг (Backlog)

### [TD-001] Передача списків чатів та повідомлень через JSON-рядки в JNI
- **Пріоритет**: Середній
- **Модулі**: `core/src/lib.rs`, `android/.../bridge/YumigramBridge.kt`
- **Проблема**: Наразі списки об'єктів серіалізуються в JSON перед поверненням у Java/Kotlin. Для тисяч повідомлень це створює накладні витрати на парсинг.
- **Рішення**: На етапі оптимізації Фази 5 перевести передачу на бінарні DirectByteBuffer або FlatBuffers.

### [TD-002] Кешування PeerRef тільки в оперативній пам'яті
- **Пріоритет**: Середній
- **Модулі**: `core/src/client.rs`
- **Проблема**: `peer_cache` зберігається в `HashMap<i64, PeerRef>`. При перезапуску додатку до першого виклику `fetch_dialogs` кеш порожній.
- **Рішення**: Зберігати `access_hash` безпосередньо в таблиці `chats` у локальній базі даних SQLite.

### [TD-003] Відсутність завантаження та кешування медіафайлів
- **Пріоритет**: Високий (Фаза 3)
- **Модулі**: `core/src/client.rs`, `android/app/`
- **Проблема**: Картинки та голосові повідомлення ще не завантажуються з Telegram MTProto.
- **Рішення**: Додати метод `download_file` у Rust Core та підключити Coil + Media3 ExoPlayer на Android.
