# 🏛️ ARCHITECTURE SPECIFICATION — YUMIGRAM

> **Архитектурный принцип**: «Строгая модульность, безопасность памяти в Rust, нулевые утечки в Android UI, отсутствие монолитного спагетти-кода».

---

## 1. Общая диаграмма системы

```mermaid
flowchart TD
    subgraph Android_UI["Android Layer (Kotlin + Jetpack Compose)"]
        UI_Screens["Экраны (Chats, Chat, Settings, AI, Profile)"]
        UI_Theme["Pixel MD3 Expressive (Dynamic Colors, Shapes, Animations)"]
        UI_Nav["Navigation Graph & Predictive Back"]
        UI_VM["ViewModels & StateFlow"]
    end

    subgraph FFI_Bridge["FFI / JNI Layer"]
        Bridge_KT["YumigramBridge.kt (Kotlin Coroutines / Flow)"]
        JNI_Native["libyumigram_core.so (JNI C-ABI)"]
    end

    subgraph Rust_Core["Rust Core Engine"]
        Core_Client["Telegram Client Manager (grammers-client & mtsender)"]
        Core_Session["Session Manager (SqliteSession)"]
        Core_DB["Local Database (rusqlite / SQLite)"]
        Core_Ghost["Ghost Privacy Interceptor (Anti-read, Anti-typing)"]
        Core_AntiDel["Anti-Delete & Anti-Edit Archive"]
        Core_Agent["Autonomous AI Agent Executor"]
    end

    subgraph Telegram_Cloud["Telegram Servers (MTProto 2.0)"]
        DC1["DC 1 (Miami)"]
        DC2["DC 2 (Amsterdam - Master)"]
        DC4["DC 4 (Amsterdam)"]
        DC5["DC 5 (Singapore)"]
    end

    UI_Screens --> UI_VM
    UI_VM --> Bridge_KT
    Bridge_KT <--> JNI_Native
    JNI_Native <--> Core_Client
    JNI_Native <--> Core_DB
    JNI_Native <--> Core_Ghost
    JNI_Native <--> Core_Agent

    Core_Client <--> Core_Session
    Core_Client <--> Core_Ghost
    Core_Ghost <--> Core_DB
    Core_AntiDel <--> Core_DB

    Core_Client <===>|Encrypted MTProto 2.0 TCP| Telegram_Cloud
```

---

## 2. Разделение ответственности модулей

### Модуль 1: Rust Core (`core/`)
* **`models.rs`**: Строгие структуры данных (`ChatItem`, `MessageItem`, `GhostConfig`, `AgentAction`). Никаких нетипизированных `HashMap`.
* **`db.rs`**: SQLite-движок с поддержкой:
  * Кеширования чатов и сообщений для мгновенного холодного старта (<100ms).
  * Транзакционного сохранения истории.
  * **Anti-Delete**: При получении серверного события `DeleteMessages`, запись в БД **не удаляется**, а получает флаг `is_deleted = 1`.
  * **Anti-Edit**: При получении серверного события `EditMessage`, исходный текст архивируется в поле `original_text`.
* **`ghost.rs`**: Потокобезопасный диспетчер приватности (`Arc<RwLock<GhostConfig>>`):
  * Перехватывает вызовы `readHistory`: не отправляет серверу подтверждение прочтения при включенном режиме.
  * Блокирует отправку статуса набора текста `setTyping`.
* **`client.rs`**: Управление сессиями и пулом сетевых отправителей `SenderPool`. Асинхронное подключение к дата-центрам Telegram.
* **`agent.rs`**: Исполнитель команд автономного ИИ-агента с поддержкой чтения контекста чатов, подготовки черновиков и изменения настроек профиля.

### Модуль 2: FFI / JNI Bridge (`android/.../bridge/`)
* **Асинхронность**: Все вызовы к Rust-ядру выполняются на пуле `Dispatchers.IO`. Никаких блокировок главного UI-потока (Main Thread).
* **Сериализация**: Использование безопасной сериализации Kotlinx.serialization / serde_json.
* **Graceful Degradation**: При падении или отсутствии нативной библиотеки приложение не крашится, а логирует ошибку и переходит в безопасный режим.

### Модуль 3: Android UI (`android/.../ui/`)
* **Pixel MD3 Expressive**:
  * Динамические палитры Monet (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
  * Системные жесты Predictive Back (Android 14/15).
  * Обработка экранных отступов (`WindowInsets`, `imePadding`, `navigationBarsPadding`).
* **Кастомные компоненты**:
  * Адаптивные пузыри сообщений (выбор между хвостиками, скругленными плашками и плоским стилем).
  * Индикаторы прочтения (точки `•` / `••`, галочки `✓` / `✓✓`, сердечки `♡` / `♥`).

---

## 3. Требования к безопасности и надежности
1. **Безопасность памяти**: Вся криптография и работа с сетевыми пакетами изолированы в Rust (защита от buffer overflow, use-after-free).
2. **Сессии**: Хранятся в изолированном защищенном каталоге приложения (`getFilesDir()`).
3. **Ghost Mode Integrity**: Логика перехвата работает на уровне ядра, исключая случайную утечку статуса прочтения при скролле.
