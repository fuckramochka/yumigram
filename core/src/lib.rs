pub mod models;
pub mod db;
pub mod ghost;
pub mod actions;
pub mod client;
pub mod session;
pub mod updates;

use db::LocalDatabase;
use ghost::GhostManager;
use actions::ActionExecutor;
use client::TelegramManager;
use models::{ChatItem, GhostConfig, ActionRequest};

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::collections::VecDeque;
use std::sync::{Arc, Mutex as StdMutex, OnceLock};

struct AppState {
    db: Arc<LocalDatabase>,
    ghost: Arc<GhostManager>,
    actions: Arc<ActionExecutor>,
    tg: Arc<TelegramManager>,
    rt: Arc<tokio::runtime::Runtime>,
    event_queue: Arc<StdMutex<VecDeque<String>>>,
}

static STATE: OnceLock<AppState> = OnceLock::new();

fn get_state() -> Option<&'static AppState> {
    STATE.get()
}

fn safe_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn spawn_updates_stream(
    tg: Arc<TelegramManager>,
    db: Arc<LocalDatabase>,
    event_queue: Arc<StdMutex<VecDeque<String>>>,
) {
    let (tx, mut rx) = tokio::sync::broadcast::channel(200);
    let q = event_queue.clone();
    tokio::spawn(async move {
        while let Ok(event_json) = rx.recv().await {
            let mut queue = q.lock().unwrap();
            if queue.len() >= 200 {
                queue.pop_front();
            }
            queue.push_back(event_json);
        }
    });
    tokio::spawn(async move {
        let _ = tg.get_me().await;
        let _ = tg.start_updates_stream(db, tx).await;
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeInit(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
    session_path: JString,
) -> jboolean {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        #[cfg(target_os = "android")]
        {
            let _ = android_logger::init_once(
                android_logger::Config::default()
                    .with_max_level(log::LevelFilter::Debug)
                    .with_tag("YumigramCore"),
            );
        }

        if STATE.get().is_some() {
            return 1;
        }

        let db_p: String = match env.get_string(&db_path) {
            Ok(s) => s.into(),
            Err(_) => return 0,
        };

        let session_p: String = match env.get_string(&session_path) {
            Ok(s) => s.into(),
            Err(_) => return 0,
        };

        match LocalDatabase::new(&db_p) {
            Ok(db_inst) => {
                let db = Arc::new(db_inst);
                let tg = Arc::new(TelegramManager::new(session_p));

                // Restore persisted Ghost Mode settings from SQLite so the
                // in-memory manager matches what the user actually configured.
                let ghost_cfg = GhostConfig {
                    hide_read: db.get_setting("ghost_hide_read").ok().flatten().map(|v| v == "1").unwrap_or(false),
                    hide_typing: db.get_setting("ghost_hide_typing").ok().flatten().map(|v| v == "1").unwrap_or(false),
                    hide_online: db.get_setting("ghost_hide_online").ok().flatten().map(|v| v == "1").unwrap_or(false),
                };
                let ghost = Arc::new(GhostManager::new(ghost_cfg));
                let actions = Arc::new(ActionExecutor::new(db.clone(), Some(tg.clone())));

                let rt = match tokio::runtime::Builder::new_multi_thread()
                    .enable_all()
                    .build()
                {
                    Ok(r) => Arc::new(r),
                    Err(_) => return 0,
                };

                let event_queue = Arc::new(StdMutex::new(VecDeque::with_capacity(200)));

                // Connect in background runtime and start updates if already authorized
                let tg_clone = tg.clone();
                let db_clone = db.clone();
                let q_clone = event_queue.clone();
                rt.spawn(async move {
                    if tg_clone.connect().await.is_ok() && tg_clone.is_authorized().await {
                        spawn_updates_stream(tg_clone, db_clone, q_clone);
                    }
                });

                let app_state = AppState { db, ghost, actions, tg, rt, event_queue };
                let _ = STATE.set(app_state);
                1
            }
            Err(_) => 0,
        }
    }));
    res.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeIsAuthorized(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            if state.rt.block_on(tg.is_authorized()) { 1 } else { 0 }
        } else {
            0
        }
    }));
    res.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSetApiCredentials(
    mut env: JNIEnv,
    _class: JClass,
    api_id: jint,
    api_hash: JString,
) -> jboolean {
    let hash_str: String = match env.get_string(&api_hash) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            match state.rt.block_on(tg.set_api_credentials(api_id, hash_str)) {
                Ok(_) => 1,
                Err(_) => 0,
            }
        } else {
            0
        }
    }));
    res.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeRequestCode(
    mut env: JNIEnv,
    _class: JClass,
    phone: JString,
) -> jstring {
    let phone_str: String = match env.get_string(&phone) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false,\"message\":\"Invalid phone string\"}"),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            match state.rt.block_on(tg.request_login_code(&phone_str)) {
                Ok(status) => {
                    if status.starts_with("RECAPTCHA_REQUIRED:") {
                        let sitekey = status.strip_prefix("RECAPTCHA_REQUIRED:").unwrap_or("");
                        format!(
                            "{{\"success\":false,\"recaptcha_required\":true,\"sitekey\":\"{}\",\"message\":\"Telegram вимагає підтвердження reCAPTCHA або використання ключів Telegram Desktop API (2040)\"}}",
                            sitekey
                        )
                    } else {
                        "{\"success\":true,\"message\":\"Code sent\"}".to_string()
                    }
                }
                Err(e) => {
                    let msg = e.to_string().replace('"', "\\\"");
                    format!("{{\"success\":false,\"message\":\"{}\"}}", msg)
                }
            }
        } else {
            "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{\"success\":false,\"message\":\"Internal panic\"}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSignIn(
    mut env: JNIEnv,
    _class: JClass,
    code: JString,
) -> jstring {
    let code_str: String = match env.get_string(&code) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false,\"message\":\"Invalid code string\"}"),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.sign_in(&code_str)) {
                Ok(status) => {
                    if status == "2FA_REQUIRED" {
                        "{\"success\":true,\"status\":\"2FA_REQUIRED\"}".to_string()
                    } else {
                        spawn_updates_stream(tg.clone(), db.clone(), state.event_queue.clone());
                        format!("{{\"success\":true,\"status\":\"SUCCESS\",\"name\":\"{}\"}}", status)
                    }
                }
                Err(e) => format!("{{\"success\":false,\"message\":\"{}\"}}", e),
            }
        } else {
            "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{\"success\":false,\"message\":\"Internal panic\"}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeCheckPassword(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
) -> jstring {
    let pwd_str: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false,\"message\":\"Invalid password string\"}"),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.check_password(&pwd_str)) {
                Ok(_) => {
                    spawn_updates_stream(tg.clone(), db.clone(), state.event_queue.clone());
                    "{\"success\":true}".to_string()
                }
                Err(e) => format!("{{\"success\":false,\"message\":\"{}\"}}", e),
            }
        } else {
            "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{\"success\":false,\"message\":\"Internal panic\"}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeFetchLiveChats(
    mut env: JNIEnv,
    _class: JClass,
    limit: jint,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.fetch_dialogs(&db, limit as usize)) {
                Ok(chats) => serde_json::to_string(&chats).unwrap_or_else(|_| "[]".into()),
                Err(_) => {
                    state.db.get_chats().map(|c| serde_json::to_string(&c).unwrap_or_else(|_| "[]".into())).unwrap_or_else(|_| "[]".into())
                }
            }
        } else {
            "[]".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeFetchLiveMessages(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    limit: jint,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.fetch_messages(&db, chat_id, limit as usize)) {
                Ok(msgs) => serde_json::to_string(&msgs).unwrap_or_else(|_| "[]".into()),
                Err(_) => "[]".into(),
            }
        } else {
            "[]".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSendMessage(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    text: JString,
    reply_to_id: jint,
) -> jstring {
    let raw_text: String = match env.get_string(&text) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false}"),
    };

    let reply_id = if reply_to_id > 0 { Some(reply_to_id as i32) } else { None };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.send_message(&db, chat_id, &raw_text, reply_id)) {
                Ok(msg) => serde_json::to_string(&msg).unwrap_or_else(|_| "{\"success\":false}".into()),
                Err(e) => format!("{{\"success\":false,\"message\":\"{}\"}}", e),
            }
        } else {
            "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{\"success\":false,\"message\":\"Internal panic\"}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSendMedia(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    file_path: JString,
    media_type: JString,
    caption: JString,
    duration: jint,
    reply_to_id: jint,
) -> jstring {
    let raw_file_path: String = match env.get_string(&file_path) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false,\"message\":\"Invalid file path\"}"),
    };
    let raw_media_type: String = match env.get_string(&media_type) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false,\"message\":\"Invalid media type\"}"),
    };
    let raw_caption: String = match env.get_string(&caption) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let reply_id = if reply_to_id > 0 { Some(reply_to_id as i32) } else { None };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.send_media_file(
                &db,
                chat_id,
                &raw_file_path,
                &raw_media_type,
                &raw_caption,
                duration as i32,
                reply_id,
            )) {
                Ok(msg) => serde_json::to_string(&msg).unwrap_or_else(|_| "{\"success\":false}".into()),
                Err(e) => format!("{{\"success\":false,\"message\":\"{}\"}}", e),
            }
        } else {
            "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{\"success\":false,\"message\":\"Internal panic\"}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeForwardMessages(
    mut env: JNIEnv,
    _class: JClass,
    from_chat_id: jlong,
    to_chat_id: jlong,
    message_ids_json: JString,
) -> jstring {
    let raw_ids_json: String = match env.get_string(&message_ids_json) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "[]"),
    };

    let ids: Vec<i32> = serde_json::from_str(&raw_ids_json).unwrap_or_default();
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.forward_messages(&db, from_chat_id, to_chat_id, ids)) {
                Ok(msgs) => serde_json::to_string(&msgs).unwrap_or_else(|_| "[]".into()),
                Err(e) => {
                    log::error!("nativeForwardMessages failed: {}", e);
                    "[]".to_string()
                }
            }
        } else {
            "[]".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeGetStickers(
    mut env: JNIEnv,
    _class: JClass,
    cache_dir: JString,
) -> jstring {
    let raw_cache_dir: String = match env.get_string(&cache_dir) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "[]"),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            match state.rt.block_on(tg.get_installed_stickers(&raw_cache_dir)) {
                Ok(stickers) => serde_json::to_string(&stickers).unwrap_or_else(|_| "[]".into()),
                Err(e) => {
                    log::error!("nativeGetStickers failed: {}", e);
                    "[]".to_string()
                }
            }
        } else {
            "[]".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSendSticker(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    doc_id: jlong,
    access_hash: jlong,
    file_ref_hex: JString,
    reply_to_id: jint,
) -> jstring {
    let raw_file_ref_hex: String = match env.get_string(&file_ref_hex) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false}"),
    };

    let file_ref = client::hex_to_bytes(&raw_file_ref_hex);
    let reply_id = if reply_to_id > 0 { Some(reply_to_id as i32) } else { None };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.send_sticker(
                &db,
                chat_id,
                doc_id,
                access_hash,
                &file_ref,
                reply_id,
            )) {
                Ok(msg) => serde_json::to_string(&msg).unwrap_or_else(|_| "{\"success\":false}".into()),
                Err(e) => format!("{{\"success\":false,\"message\":\"{}\"}}", e),
            }
        } else {
            "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{\"success\":false,\"message\":\"Internal panic\"}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSendReaction(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    message_id: jint,
    emoji: JString,
) -> jboolean {
    let raw_emoji: Option<String> = match env.get_string(&emoji) {
        Ok(s) => {
            let string: String = s.into();
            if string.is_empty() {
                None
            } else {
                Some(string)
            }
        }
        Err(_) => None,
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            match state
                .rt
                .block_on(tg.send_reaction(chat_id, message_id, raw_emoji.as_deref()))
            {
                Ok(_) => 1,
                Err(_) => 0,
            }
        } else {
            0
        }
    }));

    res.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativePinMessage(
    _env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    message_id: jint,
    unpin: jboolean,
) -> jboolean {
    let is_unpin = unpin != 0;
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            match state.rt.block_on(tg.pin_message(chat_id, message_id, is_unpin)) {
                Ok(_) => 1,
                Err(_) => 0,
            }
        } else {
            0
        }
    }));

    res.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeGetChats(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            match state.db.get_chats() {
                Ok(chats) => serde_json::to_string(&chats).unwrap_or_else(|_| "[]".into()),
                Err(_) => "[]".into(),
            }
        } else {
            "[]".into()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeGetMessages(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    limit: jint,
    offset: jint,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            match state.db.get_messages(chat_id, limit, offset) {
                Ok(msgs) => serde_json::to_string(&msgs).unwrap_or_else(|_| "[]".into()),
                Err(_) => "[]".into(),
            }
        } else {
            "[]".into()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSaveChat(
    mut env: JNIEnv,
    _class: JClass,
    chat_json: JString,
) -> jboolean {
    let json_str: String = match env.get_string(&chat_json) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let (Ok(chat), Some(state)) = (serde_json::from_str::<ChatItem>(&json_str), get_state()) {
            if state.db.save_chat(&chat).is_ok() { 1 } else { 0 }
        } else {
            0
        }
    }));
    res.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeMarkDeleted(
    _env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    message_id: jint,
) -> jboolean {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            if state.db.mark_message_deleted(chat_id, message_id).is_ok() { 1 } else { 0 }
        } else {
            0
        }
    }));
    res.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSetGhostConfig(
    _env: JNIEnv,
    _class: JClass,
    hide_read: jboolean,
    hide_typing: jboolean,
    hide_online: jboolean,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let config = GhostConfig {
                hide_read: hide_read != 0,
                hide_typing: hide_typing != 0,
                hide_online: hide_online != 0,
            };
            // Persist to SQLite so the setting survives an app restart.
            let _ = state.db.set_setting("ghost_hide_read", if config.hide_read { "1" } else { "0" });
            let _ = state.db.set_setting("ghost_hide_typing", if config.hide_typing { "1" } else { "0" });
            let _ = state.db.set_setting("ghost_hide_online", if config.hide_online { "1" } else { "0" });
            state.ghost.update_config(config);
        }
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeGetGhostConfig(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let cfg = state.ghost.get_config();
            serde_json::to_string(&cfg).unwrap_or_else(|_| "{}".into())
        } else {
            "{}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeExecuteAction(
    mut env: JNIEnv,
    _class: JClass,
    action_json: JString,
) -> jstring {
    let json_str: String = match env.get_string(&action_json) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false,\"message\":\"Invalid JSON string\"}"),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Ok(request) = serde_json::from_str::<ActionRequest>(&json_str) {
            if let Some(state) = get_state() {
                let r = state.rt.block_on(state.actions.execute_action(request));
                serde_json::to_string(&r).unwrap_or_else(|_| "{\"success\":false}".into())
            } else {
                "{\"success\":false,\"message\":\"State not initialized\"}".into()
            }
        } else {
            "{\"success\":false,\"message\":\"Failed to parse ActionRequest\"}".into()
        }
    }));

    let result = res.unwrap_or_else(|_| "{\"success\":false,\"message\":\"Internal panic\"}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeDownloadMedia(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    message_id: jint,
    target_path: JString,
) -> jstring {
    let path_str: String = match env.get_string(&target_path) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, ""),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            match state.rt.block_on(state.tg.download_message_media(&state.db, chat_id, message_id, &path_str)) {
                Ok(downloaded_path) => downloaded_path,
                Err(e) => {
                    log::error!("nativeDownloadMedia failed: {}", e);
                    String::new()
                }
            }
        } else {
            String::new()
        }
    }));

    let result = res.unwrap_or_default();
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeDownloadChatAvatar(
    mut env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    target_path: JString,
) -> jstring {
    let path_str: String = match env.get_string(&target_path) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, ""),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            match state.rt.block_on(state.tg.download_chat_avatar(&state.db, chat_id, &path_str)) {
                Ok(avatar_path) => avatar_path,
                Err(e) => {
                    log::error!("nativeDownloadChatAvatar failed: {}", e);
                    String::new()
                }
            }
        } else {
            String::new()
        }
    }));

    let result = res.unwrap_or_default();
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeGetMe(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let tg = state.tg.clone();
            match state.rt.block_on(tg.get_me()) {
                Ok(user) => {
                    let user_id = user.id().bot_api_dialog_id_unchecked();
                    let first_name = user.first_name().unwrap_or("").to_string();
                    let username = user.username().map(|s| s.to_string());
                    let u = crate::models::UserModel {
                        id: user_id,
                        first_name,
                        username,
                    };
                    serde_json::to_string(&u).unwrap_or_else(|_| "{}".into())
                }
                Err(e) => {
                    log::error!("nativeGetMe error: {}", e);
                    "{}".to_string()
                }
            }
        } else {
            "{}".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "{}".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativePollEvents(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let mut queue = state.event_queue.lock().unwrap();
            if queue.is_empty() {
                "[]".to_string()
            } else {
                let events: Vec<String> = queue.drain(..).collect();
                format!("[{}]", events.join(","))
            }
        } else {
            "[]".to_string()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}
