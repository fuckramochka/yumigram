# 📝 CHANGELOG — YUMIGRAM

All notable changes to this project will be documented in this file.

## [0.4.0] — 2026-11-14

### Added
- **AI Agent Connector & Privacy Guard (Phase 5)**:
  - Implemented `PrivacyGuard` in `core/src/ai.rs` providing regex sanitization of phone numbers, emails, credit cards, bearer tokens, secrets, and participants before transmission.
  - Implemented Google Gemini REST API client and JSON response parser in Rust & Kotlin bridge.
  - Added deterministic Local Semantic Extractor for 100% offline edge summarization (action items, topics, unresolved questions).
  - Redesigned `AgentScreen.kt` with Material 3 Expressive UI, topics chips, action checkboxes, provider selector (Gemini vs Local), API key management, and AI Reply drafting with User Confirmation Dialog.
  - Added SQLite `ai_settings` table for persisting AI provider, API keys, and privacy flags.
- **Plugin SDK Architecture (Phase 4)**:
  - Built `PluginManifest`, `PluginPermission` isolation, and `PluginRegistry` in `core/src/plugin.rs`.
  - Added `on_outgoing_message` hooks pipeline before MTProto packet transmission.
  - Implemented `PluginsScreen.kt` with active plugin toggles and permission chips.
  - Created reference `AutoSignaturePlugin` and verified in unit tests.
- **Multimedia & Core Hardening (Phase 3)**:
  - Connected Coil 3 for async avatar and attachment loading with disk caching.
  - Connected AndroidX Media3 ExoPlayer for voice message playback (`1x` / `1.5x` / `2x`).
  - Fixed MTProto private chat `is_deleted = 1` resolution in SQLite database.
  - Compiled optimized `aarch64-linux-android` release library (`libyumigram_core.so`, 5.1 MB).
  - Added ProGuard rules (`proguard-rules.pro`) for JNI, Coil, and ExoPlayer.
  - Configured GitHub Actions CI/CD workflow (`.github/workflows/build.yml`) for Rust tests and Android APK assembly.
  - Full test suite passing: 12/12 Rust unit tests (100%).

## [0.3.0] — 2026-11-14

### Added
- **Multimedia Engine (Media & Content)**:
  - Integrated `androidx.media3:media3-exoplayer` for in-app voice/audio message playback.
  - Implemented `VoiceMessageBubble` component with interactive Play/Pause, playback timeline, duration, and speed controls (`1x` / `1.5x` / `2x`).
  - Integrated `io.coil-kt:coil-compose` for asynchronous image caching and rendering.
  - Implemented `MediaViewerDialog` supporting fullscreen image inspection, swipe-down gesture to dismiss, and Anti-Delete media notice.
  - Added photo preview thumbnails in `MessageBubbleItem` and document file cards.
  - Added dynamic avatar rendering in `ChatListScreen` and `ContactsScreen` with letter initials fallback.
- **Database & Media Schema**:
  - Added `media_path` and `duration` columns to SQLite `messages` table with safe migrations.
  - Added `update_message_media_path` and `update_chat_avatar_path` to `LocalDatabase`.
  - Added `test_media_anti_delete_storage` in unit test suite.
- **Engine Reliability Fixes**:
  - Verified `SenderPoolRunner` background task execution.
  - Connected `UpdatesEngine::run_loop` via `stream_updates`.
  - Verified 7/7 Rust unit tests passing.
  - Assembled installable APK: `app-debug.apk` (25.46 MB).

## [0.2.0] — 2026-11-14

### Added
- Real Telegram MTProto Auth Flow (`AuthScreen.kt`): phone, code, 2FA cloud password.
- Live MTProto chat & message fetching and sending.
- Customizable read status indicators: Dots (`•`/`••`), Checkmarks (`✓`/`✓✓`), Hearts (`♡`/`♥`), Slashes (`/`/`//`).
- SettingsScreen with Ghost Mode toggles, AgentScreen with prompt execution console, ContactsScreen, ProfileScreen.

## [0.1.0] — 2026-11-14

### Added
- Initial Rust Core (`grammers` MTProto + SQLite + Tokio + JNI).
- Pixel OS Material You MD3 Expressive theme with Monet dynamic colors.
- Ghost Mode privacy interceptor and Anti-Delete/Anti-Edit SQLite engine.
