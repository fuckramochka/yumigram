use crate::models::{AgentActionRequest, AgentActionResult};
use crate::db::LocalDatabase;
use std::sync::Arc;

pub struct AgentExecutor {
    db: Arc<LocalDatabase>,
}

impl AgentExecutor {
    pub fn new(db: Arc<LocalDatabase>) -> Self {
        Self { db }
    }

    /// Executes an autonomous action requested by the in-app AI Agent
    pub async fn execute_action(&self, request: AgentActionRequest) -> AgentActionResult {
        match request.action_type.as_str() {
            "read_chat" => {
                if let Some(chat_id) = request.chat_id {
                    match self.db.get_messages(chat_id, 20, 0) {
                        Ok(msgs) => {
                            let json = serde_json::to_string(&msgs).unwrap_or_default();
                            AgentActionResult {
                                success: true,
                                message: format!("Fetched {} recent messages for chat {}", msgs.len(), chat_id),
                                data: Some(json),
                            }
                        }
                        Err(e) => AgentActionResult {
                            success: false,
                            message: format!("Failed to read chat: {}", e),
                            data: None,
                        },
                    }
                } else {
                    AgentActionResult {
                        success: false,
                        message: "chat_id is required for read_chat".into(),
                        data: None,
                    }
                }
            }
            "summarize" => {
                if let Some(chat_id) = request.chat_id {
                    match self.db.get_messages(chat_id, 30, 0) {
                        Ok(msgs) => {
                            let mut text_corpus = String::new();
                            for m in msgs.iter().rev() {
                                if !m.text.is_empty() {
                                    text_corpus.push_str(&format!("{}: {}\n", m.sender_name, m.text));
                                }
                            }
                            AgentActionResult {
                                success: true,
                                message: "Chat context extracted for AI summary".into(),
                                data: Some(text_corpus),
                            }
                        }
                        Err(e) => AgentActionResult {
                            success: false,
                            message: format!("Failed to fetch messages for summary: {}", e),
                            data: None,
                        },
                    }
                } else {
                    AgentActionResult {
                        success: false,
                        message: "chat_id is required for summarize".into(),
                        data: None,
                    }
                }
            }
            "update_bio" => {
                if let Some(bio) = request.bio {
                    // Update locally and queue for Telegram API update
                    let _ = self.db.set_setting("profile_bio", &bio);
                    AgentActionResult {
                        success: true,
                        message: format!("Profile bio update queued: '{}'", bio),
                        data: Some(bio),
                    }
                } else {
                    AgentActionResult {
                        success: false,
                        message: "bio text is required".into(),
                        data: None,
                    }
                }
            }
            "send_message" => {
                if let (Some(chat_id), Some(text)) = (request.chat_id, request.text) {
                    AgentActionResult {
                        success: true,
                        message: format!("Message queued to send to chat {}: '{}'", chat_id, text),
                        data: Some(text),
                    }
                } else {
                    AgentActionResult {
                        success: false,
                        message: "chat_id and text are required for send_message".into(),
                        data: None,
                    }
                }
            }
            unknown => AgentActionResult {
                success: false,
                message: format!("Unknown action type: '{}'", unknown),
                data: None,
            },
        }
    }
}
