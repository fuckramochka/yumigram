use grammers_client::client::{LoginToken, PasswordToken, SignInError};
use grammers_client::Client;
use grammers_mtsender::SenderPool;
use grammers_session::storages::SqliteSession;
use grammers_session::types::PeerRef;
use crate::db::LocalDatabase;
use crate::models::{ChatItem, MessageItem};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::Mutex;

pub const OFFICIAL_TELEGRAM_APP_ID: i32 = 6;
pub const OFFICIAL_TELEGRAM_APP_HASH: &str = "eb06d4abfb49dc3eeb1aeb98ae0f581e";

pub struct TelegramManager {
    api_id: i32,
    api_hash: String,
    session_file: String,
    client: Arc<Mutex<Option<Client>>>,
    pending_login_token: Arc<Mutex<Option<LoginToken>>>,
    pending_password_token: Arc<Mutex<Option<PasswordToken>>>,
    peer_cache: Arc<Mutex<HashMap<i64, PeerRef>>>,
}

impl TelegramManager {
    pub fn new(session_file: String) -> Self {
        Self {
            api_id: OFFICIAL_TELEGRAM_APP_ID,
            api_hash: OFFICIAL_TELEGRAM_APP_HASH.to_string(),
            session_file,
            client: Arc::new(Mutex::new(None)),
            pending_login_token: Arc::new(Mutex::new(None)),
            pending_password_token: Arc::new(Mutex::new(None)),
            peer_cache: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Connects to real Telegram MTProto servers using official credentials
    pub async fn connect(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let session = Arc::new(SqliteSession::open(&self.session_file).await?);
        let pool = SenderPool::new(session, self.api_id);
        let client = Client::new(pool.handle);

        let mut lock = self.client.lock().await;
        *lock = Some(client);
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

    /// Requests login code from Telegram servers for a phone number
    pub async fn request_login_code(
        &self,
        phone: &str,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let lock = self.client.lock().await;
        if let Some(client) = lock.as_ref() {
            let token = client.request_login_code(phone, &self.api_hash).await?;
            let mut pending = self.pending_login_token.lock().await;
            *pending = Some(token);
            Ok(())
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

                if let Ok(Some(peer_ref)) = dialog.peer.to_ref().await {
                    cache.insert(chat_id, peer_ref);
                }

                let last_msg_text = dialog.last_message.as_ref().map(|m| m.text().to_string());
                let last_msg_date = dialog.last_message.as_ref().map(|m| m.date().as_second()).unwrap_or(0);
                let last_msg_outgoing = dialog.last_message.as_ref().map(|m| m.outgoing()).unwrap_or(false);

                let item = ChatItem {
                    id: chat_id,
                    title,
                    username: None,
                    chat_type: "dialog".into(),
                    unread_count: 0,
                    last_message_text: last_msg_text,
                    last_message_date: last_msg_date,
                    last_message_outgoing: last_msg_outgoing,
                    last_message_read: false,
                    avatar_path: None,
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
                    media_type: None,
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
            };
            let _ = db.save_message(&item);
            Ok(item)
        } else {
            Err("Не знайдено дескриптор чату для відправки".into())
        }
    }
}
