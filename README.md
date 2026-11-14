# 🌌 Yumigram

> **Next-Generation Autonomous Telegram Client for Android**  
> *Engineered with Rust, Jetpack Compose, Material 3 Expressive, and Edge AI Intelligence.*

---

## 🚀 Key Features

### 👻 Ghost Mode & Anti-Features
* **Granular Ghost Mode**: Completely silent online presence. Block typing indicators, read receipts, and online status individually or in combination.
* **Anti-Delete Engine**: Intercepts Telegram `Update::MessageDeleted` and flags messages with `[Видалено]`, preserving original text and local media in SQLite database.
* **Anti-Edit History**: Preserves the original message text when an edited update is received (`Update::MessageEdited`), displaying the edit delta and timestamp.
* **Custom Read Indicators**: Choose from 4 visual styles in Settings:
  * Dots (`•` / `••`)
  * Checkmarks (`✓` / `✓✓`)
  * Hearts (`♡` / `♥`)
  * Slashes (`/` / `//`)

### 🤖 AI Agent Connector & Privacy Guard
* **Privacy Guard**: Regex-powered PII sanitizer running inside Rust Core before any data leaves the device:
  * Automatically masks phone numbers, emails, credit card numbers, authentication tokens, passwords, and participant names.
* **Google Gemini & Local Edge AI**:
  * **Google Gemini API**: Structured JSON analysis via secure Android HTTPS bridge.
  * **Local Semantic Extractor**: 100% offline edge processing for summarizing discussions, extracting action items, and detecting unresolved questions without network access.
* **Smart Summaries**: Structured chat summaries with topic tags, checkbox action items, unanswered questions, and executive summaries.
* **AI Draft Reply**: Generates contextual replies with mandatory **User Confirmation Dialog** before sending.

### 🧩 Plugin SDK Architecture
* **Permission Isolation**: Plugins declare capabilities in `PluginManifest` (`ReadMessages`, `ModifyOutgoing`, `SendSilent`, `AccessLocalAi`).
* **Message Transformation Pipeline**: Safe `on_outgoing_message` hooks allow extending message functionality without touching the core protocol code.
* **Dynamic Management**: Dedicated in-app UI to enable, inspect, or disable plugins on the fly.

### 🎨 Multimedia & UI/UX
* **Pixel Material 3 Expressive**: Modern typography, rounded cards, dynamic colors, and fluid animations.
* **Coil 3 Async Loading**: Efficient caching and lazy loading of user avatars and chat photo attachments.
* **Media3 ExoPlayer Voice Player**: Voice message playback with interactive timeline, speed controls (**`1x` / `1.5x` / `2x`**), and waveform visualization.
* **Media Viewer**: Fullscreen viewer with swipe-to-dismiss gesture and Anti-Delete media indicators.

---

## 🛠️ Architecture

Yumigram uses a hybrid high-performance architecture:
```
┌────────────────────────────────────────────────────────┐
│             Android UI (Jetpack Compose)               │
│  Pixel MD3 Expressive • Coil 3 • Media3 ExoPlayer      │
└──────────────────────────┬─────────────────────────────┘
                           │ JNI (Java Native Interface)
┌──────────────────────────▼─────────────────────────────┐
│                 Rust Core Engine                       │
│  ├─ MTProto Network (grammers-client & mtsender)       │
│  ├─ SQLite Local Storage (rusqlite)                    │
│  ├─ Ghost Privacy Interceptor                          │
│  ├─ AI Agent & Privacy Guard Sanitizer                 │
│  └─ Plugin SDK & Hooks Registry                        │
└────────────────────────────────────────────────────────┘
```

---

## 📦 Building and Running

### Prerequisites
* **Android SDK** (API Level 26+)
* **JDK 17**
* **Rust** 1.80+ with `aarch64-linux-android` target
* **Android NDK** 27.x

### 1. Build Rust Core
```bash
# Run unit tests on host
cargo test --manifest-path core/Cargo.toml

# Build native library for Android arm64-v8a
cargo build --manifest-path core/Cargo.toml --target aarch64-linux-android --release

# Copy to Android jniLibs
cp core/target/aarch64-linux-android/release/libyumigram_core.so android/app/src/main/jniLibs/arm64-v8a/
```

### 2. Build Android APK
```bash
cd android
./gradlew assembleDebug
```
The output APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## 🧪 Automated Testing

Yumigram includes a comprehensive test suite in Rust covering:
* **Database tests** (`core/tests/db_tests.rs`): anti-delete, anti-edit, transaction isolation, media caching.
* **Ghost & AI tests** (`core/tests/ghost_agent_tests.rs`): privacy guard PII sanitization, Gemini payload builder/parser, agent executor.
* **Plugin tests** (`core/tests/plugin_tests.rs`): plugin registration, message modification hooks, permission enforcement.

Run all tests:
```bash
cargo test --manifest-path core/Cargo.toml
```

---

## 📄 License

GPL-3.0 License.
