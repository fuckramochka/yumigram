pub mod models;
pub mod db;
pub mod ghost;
pub mod agent;
pub mod ai;
pub mod client;
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
use std::sync::{Arc, Mutex, OnceLock};

struct AppState {
    db: Arc<LocalDatabase>,
    ghost: GhostManager,
    agent: AgentExecutor,
    plugins: Arc<PluginManager>,
    tg: Arc<TelegramManager>,
    rt: tokio::runtime::Runtime,
}

static STATE: OnceLock<Mutex<AppState>> = OnceLock::new();

fn get_state() -> Option<std::sync::MutexGuard<'static, AppState>> {
    STATE.get().map(|m| m.lock().unwrap())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeInit(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
    session_path: JString,
) -> jboolean {
    #[cfg(target_os = "android")]
    {
        let _ = android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("YumigramCore"),
        );
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
            let ghost = GhostManager::new(GhostConfig::default());
            let agent = AgentExecutor::new(db.clone(), Some(tg.clone()));
            let plugins = Arc::new(PluginManager::new(db.clone()));

            let rt = match tokio::runtime::Builder::new_multi_thread()
                .enable_all()
                .build()
            {
                Ok(r) => r,
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
            let _ = STATE.set(Mutex::new(app_state));
            1
        }
        Err(_) => 0,
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeIsAuthorized(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if let Some(state) = get_state() {
        let tg = state.tg.clone();
        if state.rt.block_on(tg.is_authorized()) { 1 } else { 0 }
    } else {
        0
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeRequestCode(
    mut env: JNIEnv,
    _class: JClass,
    phone: JString,
) -> jstring {
    let phone_str: String = match env.get_string(&phone) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"success\":false,\"message\":\"Invalid phone string\"}").unwrap().into_raw(),
    };

    let result = if let Some(state) = get_state() {
        let tg = state.tg.clone();
        match state.rt.block_on(tg.request_login_code(&phone_str)) {
            Ok(_) => "{\"success\":true,\"message\":\"Code sent\"}".to_string(),
            Err(e) => format!("{{\"success\":false,\"message\":\"{}\"}}", e),
        }
    } else {
        "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
    };

    env.new_string(result).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSignIn(
    mut env: JNIEnv,
    _class: JClass,
    code: JString,
) -> jstring {
    let code_str: String = match env.get_string(&code) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"success\":false,\"message\":\"Invalid code string\"}").unwrap().into_raw(),
    };

    let result = if let Some(state) = get_state() {
        let tg = state.tg.clone();
        let db = state.db.clone();
        match state.rt.block_on(tg.sign_in(&code_str)) {
            Ok(status) => {
                if status == "2FA_REQUIRED" {
                    "{\"success\":true,\"status\":\"2FA_REQUIRED\"}".to_string()
                } else {
                    // Start updates engine stream on successful login
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
    };

    env.new_string(result).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeCheckPassword(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
) -> jstring {
    let pwd_str: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{\"success\":false,\"message\":\"Invalid password string\"}").unwrap().into_raw(),
    };

    let result = if let Some(state) = get_state() {
        let tg = state.tg.clone();
        let db = state.db.clone();
        match state.rt.block_on(tg.check_password(&pwd_str)) {
            Ok(_) => {
                // Start updates stream on successful 2FA auth
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
    };

    env.new_string(result).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeFetchLiveChats(
    env: JNIEnv,
    _class: JClass,
    limit: jint,
) -> jstring {
    let result = if let Some(state) = get_state() {
        let tg = state.tg.clone();
        let db = state.db.clone();
        match state.rt.block_on(tg.fetch_dialogs(&db, limit as usize)) {
            Ok(chats) => serde_json::to_string(&chats).unwrap_or_else(|_| "[]".into()),
            Err(_) => {
                // Fallback to SQLite cache
                state.db.get_chats().map(|c| serde_json::to_string(&c).unwrap_or_else(|_| "[]".into())).unwrap_or_else(|_| "[]".into())
            }
        }
    } else {
        "[]".to_string()
    };

    env.new_string(result).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeFetchLiveMessages(
    env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    limit: jint,
) -> jstring {
    let result = if let Some(state) = get_state() {
        let tg = state.tg.clone();
        let db = state.db.clone();
        match state.rt.block_on(tg.fetch_messages(&db, chat_id, limit as usize)) {
            Ok(msgs) => serde_json::to_string(&msgs).unwrap_or_else(|_| "[]".into()),
            Err(_) => "[]".into(),
        }
    } else {
        "[]".to_string()
    };

    env.new_string(result).unwrap().into_raw()
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
        Err(_) => return env.new_string("{\"success\":false}").unwrap().into_raw(),
    };

    let result = if let Some(state) = get_state() {
        // Pipeline outgoing message through active plugin hooks!
        let transformed_text = state.plugins.process_outgoing_message(chat_id, raw_text);
        let tg = state.tg.clone();
        let db = state.db.clone();
        match state.rt.block_on(tg.send_message(&db, chat_id, &transformed_text)) {
            Ok(msg) => serde_json::to_string(&msg).unwrap_or_else(|_| "{\"success\":false}".into()),
            Err(e) => format!("{{\"success\":false,\"message\":\"{}\"}}", e),
        }
    } else {
        "{\"success\":false,\"message\":\"State not initialized\"}".to_string()
    };

    env.new_string(result).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeGetChats(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let chats_json = if let Some(state) = get_state() {
        match state.db.get_chats() {
            Ok(chats) => serde_json::to_string(&chats).unwrap_or_else(|_| "[]".into()),
            Err(_) => "[]".into(),
        }
    } else {
        "[]".into()
    };

    env.new_string(chats_json).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeGetMessages(
    env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    limit: jint,
    offset: jint,
) -> jstring {
    let msgs_json = if let Some(state) = get_state() {
        match state.db.get_messages(chat_id, limit, offset) {
            Ok(msgs) => serde_json::to_string(&msgs).unwrap_or_else(|_| "[]".into()),
            Err(_) => "[]".into(),
        }
    } else {
        "[]".into()
    };

    env.new_string(msgs_json).unwrap().into_raw()
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

    if let (Ok(chat), Some(state)) = (serde_json::from_str::<ChatItem>(&json_str), get_state()) {
        return if state.db.save_chat(&chat).is_ok() { 1 } else { 0 };
    }
    0
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeMarkDeleted(
    _env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    message_id: jint,
) -> jboolean {
    if let Some(state) = get_state() {
        return if state.db.mark_message_deleted(chat_id, message_id).is_ok() { 1 } else { 0 };
    }
    0
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeSetGhostConfig(
    _env: JNIEnv,
    _class: JClass,
    hide_read: jboolean,
    hide_typing: jboolean,
    hide_online: jboolean,
) {
    if let Some(state) = get_state() {
        state.ghost.update_config(GhostConfig {
            hide_read: hide_read != 0,
            hide_typing: hide_typing != 0,
            hide_online: hide_online != 0,
        });
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeExecuteAgentAction(
    mut env: JNIEnv,
    _class: JClass,
    action_json: JString,
) -> jstring {
    let json_str: String = match env.get_string(&action_json) {
        Ok(s) => s.into(),
        Err(_) => {
            return env.new_string("{\"success\":false,\"message\":\"Invalid JSON string\"}").unwrap().into_raw();
        }
    };

    let result = if let Ok(request) = serde_json::from_str::<AgentActionRequest>(&json_str) {
        if let Some(state) = get_state() {
            let res = state.rt.block_on(state.agent.execute_action(request));
            serde_json::to_string(&res).unwrap_or_else(|_| "{\"success\":false}".into())
        } else {
            "{\"success\":false,\"message\":\"State not initialized\"}".into()
        }
    } else {
        "{\"success\":false,\"message\":\"Failed to parse ActionRequest\"}".into()
    };

    env.new_string(result).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_yumigram_app_bridge_YumigramBridge_nativeListPlugins(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = if let Some(state) = get_state() {
        let plugins = state.plugins.list_plugins();
        serde_json::to_string(&plugins).unwrap_or_else(|_| "[]".into())
    } else {
        "[]".into()
    };

    env.new_string(result).unwrap().into_raw()
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

    if let Some(state) = get_state() {
        if state.plugins.set_plugin_enabled(&id_str, enabled != 0).is_ok() {
            1
        } else {
            0
        }
    } else {
        0
    }
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
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let result = if let Some(state) = get_state() {
        match state.rt.block_on(state.tg.download_message_media(&state.db, chat_id, message_id, &path_str)) {
            Ok(downloaded_path) => downloaded_path,
            Err(e) => {
                log::error!("nativeDownloadMedia failed: {}", e);
                String::new()
            }
        }
    } else {
        String::new()
    };

    env.new_string(result).unwrap().into_raw()
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
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let result = if let Some(state) = get_state() {
        match state.rt.block_on(state.tg.download_chat_avatar(&state.db, chat_id, &path_str)) {
            Ok(avatar_path) => avatar_path,
            Err(e) => {
                log::error!("nativeDownloadChatAvatar failed: {}", e);
                String::new()
            }
        }
    } else {
        String::new()
    };

    env.new_string(result).unwrap().into_raw()
}
