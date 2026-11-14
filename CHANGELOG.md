# 📝 CHANGELOG — YUMIGRAM

All notable changes to this project will be documented in this file.

## [0.2.0] — 2026-11-14

### Added
- **Real Telegram MTProto Auth Flow**:
  - `AuthScreen.kt` with phone number input (international format), code verification, and 2FA cloud password support.
  - Linked to `YumigramBridge.requestCode`, `YumigramBridge.signIn`, and `YumigramBridge.checkPassword`.
- **Live MTProto Chat & Message Operations**:
  - Rust Core JNI bridge functions `nativeFetchLiveChats`, `nativeFetchLiveMessages`, and `nativeSendMessage`.
  - Automatic fallback to SQLite cache and reactive UI update on MTProto response.
  - Pull-to-refresh / manual sync in `ChatListScreen.kt`.
- **Customizable Read Indicators**:
  - `IndicatorStyle` enum supporting Dots (`•`/`••`), Checkmarks (`✓`/`✓✓`), Hearts (`♡`/`♥`), and Slashes (`/`/`//`).
  - Integrated into `SettingsScreen.kt`, `ChatListScreen.kt`, and `ChatScreen.kt`.
- **Full Navigation & Screens**:
  - `SettingsScreen.kt`: Granular Ghost Mode toggles (hide read receipt, hide typing, hide online), Anti-Delete, Anti-Edit, read indicator picker, and Telegram account sign out.
  - `AgentScreen.kt`: Interactive AI Agent hub with preset actions (summarize dialogs, update bio, smart reply), prompt runner, and live execution history log.
  - `ContactsScreen.kt`: Pixel OS search pill, contact avatars, initials, and direct open.
  - `ProfileScreen.kt`: Telegram session info, MTProto AES-256 encryption status, quick settings shortcuts.
- **NDK & Build Optimization**:
  - Configured `ndkVersion = "27.2.12479018"` in `app/build.gradle.kts`.
  - Fixed all Compose icon deprecations (using `AutoMirrored.Rounded`).
  - Compiled and verified ARM64-v8a debug APK: `app-debug.apk` (20.35 MB).

## [0.1.0] — 2026-11-14

### Added
- Initial project architecture: Rust Core (`grammers` MTProto + SQLite + Tokio + JNI) + Jetpack Compose Android UI.
- Pixel OS Material You MD3 Expressive theme with Monet dynamic colors.
- Ghost Mode privacy interceptor (`ghost.rs`).
- Anti-Delete and Anti-Edit SQLite engine with `is_deleted` and `original_text` tracking.
- Updates Engine stream loop for real-time Telegram events.
- Unit test suite: 6 automated tests in `tests/db_tests.rs` and `tests/ghost_agent_tests.rs`.
