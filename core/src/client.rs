use grammers_client::client::{LoginToken, PasswordToken, SignInError};
use grammers_client::Client;
use grammers_mtsender::{SenderPool, UpdatesConfiguration};
use crate::session::FileSession;
use grammers_session::types::PeerRef;
use grammers_session::updates::UpdatesLike;
use crate::db::LocalDatabase;
use crate::models::{ChatItem, MessageItem};
use crate::updates::UpdatesEngine;
use std::collections::HashMap;
use std::path::Path;
use std::sync::Arc;
use tokio::sync::{broadcast, mpsc, Mutex};

pub const OFFICIAL_TELEGRAM_APP_ID: i32 = 6;
pub const OFFICIAL_TELEGRAM_APP_HASH: &str = "eb06d4abfb49dc3eeb1aeb98ae0f581e";

pub const TELEGRAM_DESKTOP_APP_ID: i32 = 2040;
pub const TELEGRAM_DESKTOP_APP_HASH: &str = "b18441a1ff607e10a989891a5462e627";

pub const TELEGRAM_MACOS_APP_ID: i32 = 2834;
pub const TELEGRAM_MACOS_APP_HASH: &str = "68875f756c9b437a8b916ca3de215815";

pub const TELEGRAM_WEB_APP_ID: i32 = 2496;
pub const TELEGRAM_WEB_APP_HASH: &str = "8da85b0d5bfe62527e5b244c209159c3";

pub const TELEGRAM_IOS_APP_ID: i32 = 10840;
pub const TELEGRAM_IOS_APP_HASH: &str = "33c45224029d59cb3ad0c16134215aeb";

pub const TELEGRAM_ANDROID_X_APP_ID: i32 = 21724;
pub const TELEGRAM_ANDROID_X_APP_HASH: &str = "3e0cb5efcd52300aec5994fdfc5bdc16";

pub struct TelegramManager {
    api_id: Arc<tokio::sync::RwLock<i32>>,
    api_hash: Arc<tokio::sync::RwLock<String>>,
    session_file: String,
    client: Arc<Mutex<Option<Client>>>,
    updates_rx: Arc<Mutex<Option<mpsc::Receiver<UpdatesLike>>>>,
    pending_login_token: Arc<Mutex<Option<LoginToken>>>,
    pending_password_token: Arc<Mutex<Option<PasswordToken>>>,
    peer_cache: Arc<Mutex<HashMap<i64, PeerRef>>>,
}

impl TelegramManager {
    pub fn new(session_file: String) -> Self {
        Self {
            api_id: Arc::new(tokio::sync::RwLock::new(OFFICIAL_TELEGRAM_APP_ID)),
            api_hash: Arc::new(tokio::sync::RwLock::new(OFFICIAL_TELEGRAM_APP_HASH.to_string())),
            session_file,
            client: Arc::new(Mutex::new(None)),
            updates_rx: Arc::new(Mutex::new(None)),
            pending_login_token: Arc::new(Mutex::new(None)),
            pending_password_token: Arc::new(Mutex::new(None)),
            peer_cache: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Sets MTProto API credentials and re-establishes connection
    pub async fn set_api_credentials(
        &self,
        api_id: i32,
        api_hash: String,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        {
            let mut id_lock = self.api_id.write().await;
            *id_lock = api_id;
            let mut hash_lock = self.api_hash.write().await;
            *hash_lock = api_hash;
        }
        {
            let mut pending = self.pending_login_token.lock().await;
            *pending = None;
            let mut pwd = self.pending_password_token.lock().await;
            *pwd = None;
        }
        self.connect().await
    }

    /// Connects to real Telegram MTProto servers using official credentials
    /// and actively drives the MTProto network I/O runner in a background task.
    pub async fn connect(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let api_id = *self.api_id.read().await;
        let mut connection_params = grammers_mtsender::ConnectionParams::default();
        match api_id {
            TELEGRAM_DESKTOP_APP_ID => {
                connection_params.device_model = "Telegram Desktop".to_string();
                connection_params.system_version = "Linux 64-bit".to_string();
                connection_params.app_version = "5.5.5".to_string();
            }
            TELEGRAM_MACOS_APP_ID => {
                connection_params.device_model = "MacBook Pro".to_string();
                connection_params.system_version = "macOS 14.5".to_string();
                connection_params.app_version = "10.14.0".to_string();
            }
            TELEGRAM_WEB_APP_ID => {
                connection_params.device_model = "Telegram Web".to_string();
                connection_params.system_version = "Chrome / Linux".to_string();
                connection_params.app_version = "2.1.0".to_string();
            }
            TELEGRAM_IOS_APP_ID => {
                connection_params.device_model = "iPhone 15 Pro".to_string();
                connection_params.system_version = "iOS 17.5".to_string();
                connection_params.app_version = "10.14.0".to_string();
            }
            TELEGRAM_ANDROID_X_APP_ID => {
                connection_params.device_model = "Samsung SM-S928B".to_string();
                connection_params.system_version = "Android 14 (SDK 34)".to_string();
                connection_params.app_version = "0.26.8".to_string();
            }
            OFFICIAL_TELEGRAM_APP_ID => {
                connection_params.device_model = "Google Pixel 8".to_string();
                connection_params.system_version = "Android 14".to_string();
                connection_params.app_version = "10.14.5".to_string();
            }
            _ => {
                connection_params.device_model = "Yumigram Mobile".to_string();
                connection_params.system_version = "Android 14".to_string();
                connection_params.app_version = "1.0.8".to_string();
            }
        }

        let session = Arc::new(FileSession::load_or_create(&self.session_file));
        let SenderPool {
            runner,
            handle,
            updates,
        } = SenderPool::with_configuration(session, api_id, connection_params);
        let client = Client::new(handle);

        // Spawn runner in background tokio task to drive socket I/O & frame encryption
        tokio::spawn(async move {
            runner.run().await;
        });

        let mut client_lock = self.client.lock().await;
        *client_lock = Some(client);

        let mut updates_lock = self.updates_rx.lock().await;
        *updates_lock = Some(updates);

        Ok(())
    }

    pub async fn is_authorized(&self) -> bool {
        let lock = self.client.lock().await;
        if let Some(client) = lock.as_ref() {
            client.is_authorized().await.unwrap_or(false)
        } else {
            false
        }
    }

    /// Starts real-time updates engine loop when authorized
    pub async fn start_updates_stream(
        &self,
        db: Arc<LocalDatabase>,
        tx_event: broadcast::Sender<String>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let client = {
            let lock = self.client.lock().await;
            lock.as_ref().cloned().ok_or("Client not connected")?
        };

        let updates = {
            let mut lock = self.updates_rx.lock().await;
            lock.take()
        };

        if let Some(updates_chan) = updates {
            let stream = client
                .stream_updates(updates_chan, UpdatesConfiguration { catch_up: true })
                .await?;
            let engine = UpdatesEngine::new(db, tx_event);

            tokio::spawn(async move {
                engine.run_loop(stream).await;
            });
        }

        Ok(())
    }

    /// Requests login code from Telegram servers for a phone number
    pub async fn request_login_code(
        &self,
        phone: &str,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let lock = self.client.lock().await;
        if let Some(client) = lock.as_ref() {
            let api_hash = self.api_hash.read().await.clone();
            match client.request_login_code(phone, &api_hash).await {
                Ok(token) => {
                    let mut pending = self.pending_login_token.lock().await;
                    *pending = Some(token);
                    Ok("OK".to_string())
                }
                Err(grammers_mtsender::InvocationError::Rpc(err)) => {
                    let err_name = &err.name;
                    if err_name.contains("RECAPTCHA_CHECK") {
                        let sitekey = if let Some(v) = err.value {
                            if let Some(pos) = err_name.find("RECAPTCHA_CHECK_") {
                                let after = &err_name[pos + 16..];
                                if let Some(key_part) = after.split('_').last() {
                                    format!("{}{}", v, key_part)
                                } else {
                                    format!("{}{}", v, after)
                                }
                            } else {
                                format!("{}", v)
                            }
                        } else {
                            err_name.split('_').last().unwrap_or(err_name).to_string()
                        };
                        Ok(format!("RECAPTCHA_REQUIRED:{}", sitekey))
                    } else if err_name.contains("PHONE_NUMBER_INVALID") {
                        Err("Невірний формат номера телефону. Перевірте код країни та цифри.".into())
                    } else if err_name.contains("PHONE_NUMBER_BANNED") {
                        Err("Цей номер телефону заблоковано в Telegram.".into())
                    } else if err_name.contains("PHONE_NUMBER_FLOOD") {
                        Err("Забагато запитів для цього номера. Зачекайте деякий час або спробуйте інший API профіль.".into())
                    } else if err_name.contains("API_ID_PUBLISHED_FLOOD") {
                        Err("Telegram тимчасово обмежив цей API ID. Будь ласка, оберіть інший профіль (наприклад, macOS, WebK або Власний ID).".into())
                    } else if err_name.contains("API_ID_INVALID") {
                        Err("Вказано недійсний API ID або Hash. Перевірте введені дані.".into())
                    } else {
                        Err(format!("{}", err).into())
                    }
                }
                Err(e) => Err(format!("{}", e).into()),
            }
        } else {
            Err("Client not connected to Telegram servers".into())
        }
    }

    /// Verifies received SMS / Telegram login code
    pub async fn sign_in(
        &self,
        code: &str,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let client_lock = self.client.lock().await;
        let mut token_lock = self.pending_login_token.lock().await;

        if let (Some(client), Some(token)) = (client_lock.as_ref(), token_lock.as_ref()) {
            match client.sign_in(token, code).await {
                Ok(user) => {
                    let user_name = user.first_name().unwrap_or("Користувач").to_string();
                    *token_lock = None;
                    Ok(format!("SUCCESS:{}", user_name))
                }
                Err(SignInError::PasswordRequired(pwd_token)) => {
                    let mut pwd_lock = self.pending_password_token.lock().await;
                    *pwd_lock = Some(pwd_token);
                    Ok("2FA_REQUIRED".to_string())
                }
                Err(SignInError::InvalidCode) => Err("Невірний код підтвердження".into()),
                Err(SignInError::SignUpRequired) => Err("Необхідна реєстрація в офіційному клієнті".into()),
                Err(e) => Err(format!("Помилка входу: {:?}", e).into()),
            }
        } else {
            Err("Немає активного запиту авторизації".into())
        }
    }

    /// Submits 2FA Cloud Password
    pub async fn check_password(
        &self,
        password: &str,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let client_lock = self.client.lock().await;
        let mut pwd_lock = self.pending_password_token.lock().await;

        if let (Some(client), Some(pwd_token)) = (client_lock.as_ref(), pwd_lock.take()) {
            client.check_password(pwd_token, password).await?;
            Ok(())
        } else {
            Err("Не знайдено токен 2FA пароля".into())
        }
    }

    /// Fetches real dialogs from Telegram servers, caches in SQLite, returns list
    pub async fn fetch_dialogs(
        &self,
        db: &LocalDatabase,
        limit: usize,
    ) -> Result<Vec<ChatItem>, Box<dyn std::error::Error + Send + Sync>> {
        let client_lock = self.client.lock().await;
        if let Some(client) = client_lock.as_ref() {
            let mut dialogs = client.iter_dialogs();
            let mut result = Vec::new();
            let mut cache = self.peer_cache.lock().await;

            while let Some(dialog) = dialogs.next().await? {
                let chat_id = dialog.peer.id().bot_api_dialog_id_unchecked();
                let title = dialog.peer.name().unwrap_or("Без назви").to_string();
                let username = dialog.peer.username().map(|u| u.to_string());
                let chat_type = match &dialog.peer {
                    grammers_client::peer::Peer::User(_) => "user",
                    grammers_client::peer::Peer::Group(_) => "group",
                    grammers_client::peer::Peer::Channel(_) => "channel",
                    grammers_client::peer::Peer::Community(_) => "channel",
                };

                if let Ok(Some(peer_ref)) = dialog.peer.to_ref().await {
                    cache.insert(chat_id, peer_ref);
                }

                let last_msg_text = dialog.last_message.as_ref().map(|m| m.text().to_string());
                let last_msg_date = dialog.last_message.as_ref().map(|m| m.date().as_second()).unwrap_or(0);
                let last_msg_outgoing = dialog.last_message.as_ref().map(|m| m.outgoing()).unwrap_or(false);
                let existing_avatar = db.get_chat_avatar_path(chat_id).unwrap_or(None);

                let item = ChatItem {
                    id: chat_id,
                    title,
                    username,
                    chat_type: chat_type.into(),
                    unread_count: 0,
                    last_message_text: last_msg_text,
                    last_message_date: last_msg_date,
                    last_message_outgoing: last_msg_outgoing,
                    last_message_read: false,
                    avatar_path: existing_avatar,
                };

                let _ = db.save_chat(&item);
                result.push(item);

                if result.len() >= limit {
                    break;
                }
            }
            Ok(result)
        } else {
            Err("Клієнт не підключений".into())
        }
    }

    /// Fetches real messages from Telegram servers for a chat, caches in SQLite
    pub async fn fetch_messages(
        &self,
        db: &LocalDatabase,
        chat_id: i64,
        limit: usize,
    ) -> Result<Vec<MessageItem>, Box<dyn std::error::Error + Send + Sync>> {
        let client_lock = self.client.lock().await;
        let cache = self.peer_cache.lock().await;

        if let (Some(client), Some(&peer_ref)) = (client_lock.as_ref(), cache.get(&chat_id)) {
            let mut iter = client.iter_messages(peer_ref);
            let mut result = Vec::new();

            while let Some(msg) = iter.next().await? {
                let (media_type, duration) = match msg.media() {
                    Some(grammers_client::media::Media::Photo(_)) => (Some("photo".to_string()), None),
                    Some(grammers_client::media::Media::Sticker(_)) => (Some("sticker".to_string()), None),
                    Some(grammers_client::media::Media::Poll(_)) => (Some("poll".to_string()), None),
                    Some(grammers_client::media::Media::Document(ref doc)) => {
                        let mime = doc.mime_type().unwrap_or("");
                        if mime.starts_with("audio/") || mime.contains("ogg") {
                            (Some("voice".to_string()), None)
                        } else if mime.starts_with("video/") {
                            (Some("video".to_string()), None)
                        } else if mime == "image/webp" || mime.contains("tgsticker") || mime.contains("sticker") {
                            (Some("sticker".to_string()), None)
                        } else if mime.contains("gif") {
                            (Some("gif".to_string()), None)
                        } else {
                            (Some("file".to_string()), None)
                        }
                    }
                    _ => (None, None),
                };

                let item = MessageItem {
                    id: msg.id(),
                    chat_id,
                    sender_id: msg.sender_id().map(|p| p.bot_api_dialog_id_unchecked()).unwrap_or(0),
                    sender_name: msg.sender().and_then(|p| p.name()).unwrap_or("Співрозмовник").to_string(),
                    text: msg.text().to_string(),
                    date: msg.date().as_second(),
                    is_outgoing: msg.outgoing(),
                    is_read: false,
                    reply_to_id: None,
                    is_deleted: false,
                    original_text: None,
                    media_type,
                    media_path: None,
                    duration,
                };

                let _ = db.save_message(&item);
                result.push(item);

                if result.len() >= limit {
                    break;
                }
            }
            Ok(result)
        } else {
            // Fallback to local SQLite cache if offline
            Ok(db.get_messages(chat_id, limit as i32, 0)?)
        }
    }

    /// Sends real message through Telegram MTProto servers
    pub async fn send_message(
        &self,
        db: &LocalDatabase,
        chat_id: i64,
        text: &str,
    ) -> Result<MessageItem, Box<dyn std::error::Error + Send + Sync>> {
        let client_lock = self.client.lock().await;
        let cache = self.peer_cache.lock().await;

        if let (Some(client), Some(&peer_ref)) = (client_lock.as_ref(), cache.get(&chat_id)) {
            let sent = client.send_message(peer_ref, text).await?;
            let item = MessageItem {
                id: sent.id(),
                chat_id,
                sender_id: 0,
                sender_name: "Я".into(),
                text: sent.text().to_string(),
                date: sent.date().as_second(),
                is_outgoing: true,
                is_read: false,
                reply_to_id: None,
                is_deleted: false,
                original_text: None,
                media_type: None,
                media_path: None,
                duration: None,
            };
            let _ = db.save_message(&item);
            Ok(item)
        } else {
            Err("Не знайдено дескриптор чату для відправки".into())
        }
    }

    /// Downloads media attached to a message and stores it at target_path
    pub async fn download_message_media(
        &self,
        db: &LocalDatabase,
        chat_id: i64,
        message_id: i32,
        target_path: &str,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        if Path::new(target_path).exists() {
            if let Ok(meta) = std::fs::metadata(target_path) {
                if meta.len() > 0 {
                    let _ = db.update_message_media_path(chat_id, message_id, target_path);
                    return Ok(target_path.to_string());
                }
            }
        }

        let client_lock = self.client.lock().await;
        let cache = self.peer_cache.lock().await;

        if let (Some(client), Some(&peer_ref)) = (client_lock.as_ref(), cache.get(&chat_id)) {
            let messages = client.get_messages_by_id(peer_ref, &[message_id]).await?;
            if let Some(Some(msg)) = messages.into_iter().next() {
                if let Some(media) = msg.media() {
                    if let Some(parent) = Path::new(target_path).parent() {
                        let _ = tokio::fs::create_dir_all(parent).await;
                    }
                    client.download_media(&media, target_path).await?;
                    let _ = db.update_message_media_path(chat_id, message_id, target_path);
                    Ok(target_path.to_string())
                } else {
                    Err("Повідомлення не містить завантажуваного медіа".into())
                }
            } else {
                Err("Повідомлення не знайдено на серверах Telegram".into())
            }
        } else {
            Err("Не знайдено дескриптор чату в кеші".into())
        }
    }

    /// Downloads chat avatar (profile photo) and stores it at target_path
    pub async fn download_chat_avatar(
        &self,
        db: &LocalDatabase,
        chat_id: i64,
        target_path: &str,
    ) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        if Path::new(target_path).exists() {
            if let Ok(meta) = std::fs::metadata(target_path) {
                if meta.len() > 0 {
                    let _ = db.update_chat_avatar_path(chat_id, target_path);
                    return Ok(target_path.to_string());
                }
            }
        }

        let client_lock = self.client.lock().await;
        let cache = self.peer_cache.lock().await;

        if let (Some(client), Some(&peer_ref)) = (client_lock.as_ref(), cache.get(&chat_id)) {
            let peer = client.resolve_peer(peer_ref).await?;
            if let Some(photo) = peer.photo(false).await? {
                if let Some(parent) = Path::new(target_path).parent() {
                    let _ = tokio::fs::create_dir_all(parent).await;
                }
                client.download_media(&photo, target_path).await?;
                let _ = db.update_chat_avatar_path(chat_id, target_path);
                Ok(target_path.to_string())
            } else {
                Err("У чату немає фотографії профілю".into())
            }
        } else {
            Err("Не знайдено дескриптор чату в кеші".into())
        }
    }

    /// Updates current user profile on Telegram MTProto servers
    pub async fn update_profile(
        &self,
        first_name: Option<String>,
        last_name: Option<String>,
        about: Option<String>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let client_lock = self.client.lock().await;
        if let Some(client) = client_lock.as_ref() {
            client
                .invoke(&grammers_tl_types::functions::account::UpdateProfile {
                    first_name,
                    last_name,
                    about,
                })
                .await?;
            Ok(())
        } else {
            Err("Клієнт не підключений до серверів Telegram".into())
        }
    }
}
