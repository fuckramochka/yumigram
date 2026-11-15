use crate::client::TelegramManager;
use crate::db::LocalDatabase;
use crate::models::{ActionRequest, ActionResult};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatSummary {
    pub id: i64,
    pub title: String,
    pub unread_count: i32,
    pub last_message: Option<String>,
    pub chat_type: String,
}

/// Executes repository-level actions backed by real MTProto / SQLite.
/// No simulated or hardcoded results are ever returned here.
pub struct ActionExecutor {
    db: Arc<LocalDatabase>,
    telegram: Option<Arc<TelegramManager>>,
}

impl ActionExecutor {
    pub fn new(db: Arc<LocalDatabase>, telegram: Option<Arc<TelegramManager>>) -> Self {
        Self { db, telegram }
    }

    pub async fn execute_action(&self, request: ActionRequest) -> ActionResult {
        match request.action_type.as_str() {
            "list_chats" => self.handle_list_chats().await,
            "read_chat" => self.handle_read_chat(request.chat_id, request.limit).await,
            "send_message" => self.handle_send_message(request.chat_id, request.text).await,
            "update_bio" | "update_profile" => {
                self.handle_update_profile(request.first_name, request.last_name, request.bio.or(request.text))
                    .await
            }
            "search_messages" => self.handle_search_messages(request.query.or(request.text), request.limit).await,
            unknown => ActionResult {
                success: false,
                message: format!("Невідома дія: '{}'", unknown),
                data: None,
            },
        }
    }

    async fn handle_list_chats(&self) -> ActionResult {
        match self.db.get_chats() {
            Ok(chats) => {
                let summaries: Vec<ChatSummary> = chats
                    .into_iter()
                    .map(|c| ChatSummary {
                        id: c.id,
                        title: c.title,
                        unread_count: c.unread_count,
                        last_message: c.last_message_text,
                        chat_type: c.chat_type,
                    })
                    .collect();

                let json = serde_json::to_string(&summaries).unwrap_or_default();
                ActionResult {
                    success: true,
                    message: format!("Знайдено {} активних діалогів", summaries.len()),
                    data: Some(json),
                }
            }
            Err(e) => ActionResult {
                success: false,
                message: format!("Помилка отримання чатів: {}", e),
                data: None,
            },
        }
    }

    async fn handle_read_chat(&self, chat_id: Option<i64>, limit: Option<i32>) -> ActionResult {
        let chat_id = match chat_id {
            Some(id) => id,
            None => {
                return ActionResult {
                    success: false,
                    message: "chat_id обов'язковий для читання чату".into(),
                    data: None,
                };
            }
        };

        let fetch_limit = limit.unwrap_or(20);

        // If MTProto is connected, pull fresh messages from Telegram
        if let Some(tg) = self.telegram.as_ref() {
            let _ = tg.fetch_messages(&self.db, chat_id, fetch_limit as usize).await;
        }

        match self.db.get_messages(chat_id, fetch_limit, 0) {
            Ok(msgs) => {
                let json = serde_json::to_string(&msgs).unwrap_or_default();
                ActionResult {
                    success: true,
                    message: format!("Отримано {} останніх повідомлень", msgs.len()),
                    data: Some(json),
                }
            }
            Err(e) => ActionResult {
                success: false,
                message: format!("Помилка читання чату: {}", e),
                data: None,
            },
        }
    }

    async fn handle_send_message(&self, chat_id: Option<i64>, text: Option<String>) -> ActionResult {
        let (chat_id, text) = match (chat_id, text) {
            (Some(cid), Some(t)) if !t.trim().is_empty() => (cid, t.trim().to_string()),
            _ => {
                return ActionResult {
                    success: false,
                    message: "chat_id та непорожній text обов'язкові для відправки повідомлення".into(),
                    data: None,
                };
            }
        };

        if let Some(tg) = self.telegram.as_ref() {
            match tg.send_message(&self.db, chat_id, &text, None).await {
                Ok(msg) => ActionResult {
                    success: true,
                    message: format!("Повідомлення успішно відправлено в чат {} (ID: {})", chat_id, msg.id),
                    data: Some(serde_json::to_string(&msg).unwrap_or_default()),
                },
                Err(e) => ActionResult {
                    success: false,
                    message: format!("Не вдалося відправити повідомлення через MTProto: {}", e),
                    data: None,
                },
            }
        } else {
            ActionResult {
                success: false,
                message: "Клієнт Telegram не підключений".into(),
                data: None,
            }
        }
    }

    async fn handle_update_profile(
        &self,
        first_name: Option<String>,
        last_name: Option<String>,
        bio: Option<String>,
    ) -> ActionResult {
        if first_name.is_none() && last_name.is_none() && bio.is_none() {
            return ActionResult {
                success: false,
                message: "Необхідно вказати хоча б одне поле для оновлення (first_name, last_name, або bio)".into(),
                data: None,
            };
        }

        // Persist to local settings
        if let Some(ref fn_val) = first_name {
            let _ = self.db.set_setting("profile_first_name", fn_val);
        }
        if let Some(ref ln_val) = last_name {
            let _ = self.db.set_setting("profile_last_name", ln_val);
        }
        if let Some(ref b_val) = bio {
            let _ = self.db.set_setting("profile_bio", b_val);
        }

        // Send to real Telegram servers via MTProto
        if let Some(tg) = self.telegram.as_ref() {
            match tg.update_profile(first_name.clone(), last_name.clone(), bio.clone()).await {
                Ok(()) => ActionResult {
                    success: true,
                    message: "Профіль Telegram успішно синхронізовано з сервером".into(),
                    data: bio,
                },
                Err(e) => ActionResult {
                    success: false,
                    message: format!("Профіль збережено локально, але синхронізація MTProto не вдалася: {}", e),
                    data: bio,
                },
            }
        } else {
            ActionResult {
                success: false,
                message: "Клієнт Telegram не підключений".into(),
                data: bio,
            }
        }
    }

    async fn handle_search_messages(&self, query: Option<String>, limit: Option<i32>) -> ActionResult {
        let query = match query {
            Some(q) if !q.trim().is_empty() => q.trim().to_string(),
            _ => {
                return ActionResult {
                    success: false,
                    message: "query обов'язковий для пошуку повідомлень".into(),
                    data: None,
                };
            }
        };

        let search_limit = limit.unwrap_or(20);
        match self.db.search_messages(&query, search_limit) {
            Ok(matches) => {
                let json = serde_json::to_string(&matches).unwrap_or_default();
                ActionResult {
                    success: true,
                    message: format!("Знайдено {} повідомлень за запитом '{}'", matches.len(), query),
                    data: Some(json),
                }
            }
            Err(e) => ActionResult {
                success: false,
                message: format!("Помилка пошуку повідомлень: {}", e),
                data: None,
            },
        }
    }
}
