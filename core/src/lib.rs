pub mod models;
pub mod db;
pub mod ghost;
pub mod agent;
pub mod ai;
pub mod client;
pub mod session;
pub mod updates;
pub mod plugins;

use db::LocalDatabase;
use ghost::GhostManager;
use agent::AgentExecutor;
use client::TelegramManager;
use plugins::PluginManager;
use models::{ChatItem, GhostConfig, AgentActionRequest};

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::JNIEnv;
use std::sync::{Arc, OnceLock};

struct AppState {
    db: Arc<LocalDatabase>,
    ghost: Arc<GhostManager>,
    agent: Arc<AgentExecutor>,
    plugins: Arc<PluginManager>,
    tg: Arc<TelegramManager>,
    rt: Arc<tokio::runtime::Runtime>,
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
                let ghost = Arc::new(GhostManager::new(GhostConfig::default()));
                let agent = Arc::new(AgentExecutor::new(db.clone(), Some(tg.clone())));
                let plugins = Arc::new(PluginManager::new(db.clone()));

                let rt = match tokio::runtime::Builder::new_multi_thread()
                    .enable_all()
                    .build()
                {
                    Ok(r) => Arc::new(r),
                    Err(_) => return 0,
                };

                // Connect in background runtime and start updates if already authorized
                let tg_clone = tg.clone();
                let db_clone = db.clone();
                rt.spawn(async move {
                    if tg_clone.connect().await.is_ok() && tg_clone.is_authorized().await {
                        let (tx, _rx) = tokio::sync::broadcast::channel(100);
                        let _ = tg_clone.start_updates_stream(db_clone, tx).await;
                    }
                });

                let app_state = AppState { db, ghost, agent, plugins, tg, rt };
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
                        let tg_stream = tg.clone();
                        state.rt.spawn(async move {
                            let (tx, _rx) = tokio::sync::broadcast::channel(100);
                            let _ = tg_stream.start_updates_stream(db, tx).await;
                        });
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
                    let tg_stream = tg.clone();
                    state.rt.spawn(async move {
                        let (tx, _rx) = tokio::sync::broadcast::channel(100);
                        let _ = tg_stream.start_updates_stream(db, tx).await;
                    });
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
) -> jstring {
    let raw_text: String = match env.get_string(&text) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false}"),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let transformed_text = state.plugins.process_outgoing_message(chat_id, raw_text);
            let tg = state.tg.clone();
            let db = state.db.clone();
            match state.rt.block_on(tg.send_message(&db, chat_id, &transformed_text)) {
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
            state.ghost.update_config(GhostConfig {
                hide_read: hide_read != 0,
                hide_typing: hide_typing != 0,
                hide_online: hide_online != 0,
            });
        }
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeExecuteAgentAction(
    mut env: JNIEnv,
    _class: JClass,
    action_json: JString,
) -> jstring {
    let json_str: String = match env.get_string(&action_json) {
        Ok(s) => s.into(),
        Err(_) => return safe_jstring(&mut env, "{\"success\":false,\"message\":\"Invalid JSON string\"}"),
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Ok(request) = serde_json::from_str::<AgentActionRequest>(&json_str) {
            if let Some(state) = get_state() {
                let r = state.rt.block_on(state.agent.execute_action(request));
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
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeListPlugins(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            let plugins = state.plugins.list_plugins();
            serde_json::to_string(&plugins).unwrap_or_else(|_| "[]".into())
        } else {
            "[]".into()
        }
    }));

    let result = res.unwrap_or_else(|_| "[]".to_string());
    safe_jstring(&mut env, &result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSetPluginEnabled(
    mut env: JNIEnv,
    _class: JClass,
    plugin_id: JString,
    enabled: jboolean,
) -> jboolean {
    let id_str: String = match env.get_string(&plugin_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if let Some(state) = get_state() {
            if state.plugins.set_plugin_enabled(&id_str, enabled != 0).is_ok() { 1 } else { 0 }
        } else {
            0
        }
    }));
    res.unwrap_or(0)
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
