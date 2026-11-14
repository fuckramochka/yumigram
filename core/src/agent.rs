use crate::ai::{AiConnector, AiProvider, PrivacyGuard, StructuredSummary};
use crate::client::TelegramManager;
use crate::db::LocalDatabase;
use crate::models::{AgentActionRequest, AgentActionResult, MessageItem};
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

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AiSettingsModel {
    pub provider: String,
    pub gemini_api_key: String,
    pub strict_privacy: bool,
}

pub struct AgentExecutor {
    db: Arc<LocalDatabase>,
    telegram: Option<Arc<TelegramManager>>,
}

impl AgentExecutor {
    pub fn new(db: Arc<LocalDatabase>, telegram: Option<Arc<TelegramManager>>) -> Self {
        Self { db, telegram }
    }

    /// Executes an autonomous action requested by the in-app AI Agent or user
    pub async fn execute_action(&self, request: AgentActionRequest) -> AgentActionResult {
        match request.action_type.as_str() {
            "list_chats" => self.handle_list_chats().await,
            "read_chat" => self.handle_read_chat(request.chat_id, request.limit).await,
            "send_message" => self.handle_send_message(request.chat_id, request.text).await,
            "update_bio" | "update_profile" => {
                self.handle_update_profile(request.first_name, request.last_name, request.bio.or(request.text))
                    .await
            }
            "summarize" => {
                self.handle_summarize(
                    request.chat_id,
                    request.provider.as_deref(),
                    request.api_key.as_deref(),
                    request.strict_privacy,
                    request.raw_ai_response.as_deref(),
                )
                .await
            }
            "prepare_ai_summary" => {
                self.handle_prepare_ai_summary(
                    request.chat_id,
                    request.strict_privacy.unwrap_or(true),
                )
                .await
            }
            "parse_ai_summary" => {
                self.handle_parse_ai_summary(
                    request.chat_id,
                    request.raw_ai_response.as_deref().unwrap_or(""),
                )
                .await
            }
            "draft_reply" => {
                self.handle_draft_reply(
                    request.chat_id,
                    request.text,
                    request.raw_ai_response.as_deref(),
                )
                .await
            }
            "search_messages" => self.handle_search_messages(request.query.or(request.text), request.limit).await,
            "get_ghost_mode" => self.handle_get_ghost_mode().await,
            "toggle_ghost_mode" => {
                self.handle_toggle_ghost_mode(request.text.as_deref().unwrap_or(""), true).await
            }
            "get_ai_settings" => self.handle_get_ai_settings().await,
            "set_ai_settings" => {
                self.handle_set_ai_settings(
                    request.provider.as_deref(),
                    request.api_key.as_deref(),
                    request.strict_privacy,
                )
                .await
            }
            unknown => AgentActionResult {
                success: false,
                message: format!("Невідома дія AI Агента: '{}'", unknown),
                data: None,
            },
        }
    }

    async fn handle_list_chats(&self) -> AgentActionResult {
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
                AgentActionResult {
                    success: true,
                    message: format!("Знайдено {} активних діалогів", summaries.len()),
                    data: Some(json),
                }
            }
            Err(e) => AgentActionResult {
                success: false,
                message: format!("Помилка отримання чатів: {}", e),
                data: None,
            },
        }
    }

    async fn handle_read_chat(&self, chat_id: Option<i64>, limit: Option<i32>) -> AgentActionResult {
        let chat_id = match chat_id {
            Some(id) => id,
            None => {
                return AgentActionResult {
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
                AgentActionResult {
                    success: true,
                    message: format!("Отримано {} останніх повідомлень", msgs.len()),
                    data: Some(json),
                }
            }
            Err(e) => AgentActionResult {
                success: false,
                message: format!("Помилка читання чату: {}", e),
                data: None,
            },
        }
    }

    async fn handle_send_message(&self, chat_id: Option<i64>, text: Option<String>) -> AgentActionResult {
        let (chat_id, text) = match (chat_id, text) {
            (Some(cid), Some(t)) if !t.trim().is_empty() => (cid, t.trim().to_string()),
            _ => {
                return AgentActionResult {
                    success: false,
                    message: "chat_id та непорожній text обов'язкові для відправки повідомлення".into(),
                    data: None,
                };
            }
        };

        if let Some(tg) = self.telegram.as_ref() {
            match tg.send_message(&self.db, chat_id, &text).await {
                Ok(msg) => AgentActionResult {
                    success: true,
                    message: format!("Повідомлення успішно відправлено в чат {} (ID: {})", chat_id, msg.id),
                    data: Some(serde_json::to_string(&msg).unwrap_or_default()),
                },
                Err(e) => AgentActionResult {
                    success: false,
                    message: format!("Не вдалося відправити повідомлення через MTProto: {}", e),
                    data: None,
                },
            }
        } else {
            AgentActionResult {
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
    ) -> AgentActionResult {
        if first_name.is_none() && last_name.is_none() && bio.is_none() {
            return AgentActionResult {
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
                Ok(()) => AgentActionResult {
                    success: true,
                    message: "Профіль Telegram успішно синхронізовано з сервером".into(),
                    data: bio,
                },
                Err(e) => AgentActionResult {
                    success: true,
                    message: format!("Профіль збережено локально, але синхронізація MTProto не вдалася: {}", e),
                    data: bio,
                },
            }
        } else {
            AgentActionResult {
                success: true,
                message: "Профіль оновлено в локальній базі даних".into(),
                data: bio,
            }
        }
    }

    /// Intelligent structured summarization with privacy sanitization
    async fn handle_summarize(
        &self,
        chat_id: Option<i64>,
        requested_provider: Option<&str>,
        requested_key: Option<&str>,
        strict_privacy: Option<bool>,
        raw_ai_response: Option<&str>,
    ) -> AgentActionResult {
        let (chat_title, msgs) = self.get_dialog_messages(chat_id).await;

        if msgs.is_empty() {
            return AgentActionResult {
                success: true,
                message: format!("Немає повідомлень для підсумку в '{}'", chat_title),
                data: Some("Діалог порожній або повідомлення ще не завантажені.".into()),
            };
        }

        let strict = strict_privacy.unwrap_or_else(|| {
            self.db.get_setting("ai_strict_privacy").unwrap_or_default().as_deref() == Some("true")
        });

        // 1. Always sanitize sensitive information (PII protection)
        let (sanitized_msgs, was_sanitized) = PrivacyGuard::sanitize_messages(&msgs, strict);

        // 2. Check if a raw response from external Gemini was provided
        if let Some(raw_resp) = raw_ai_response {
            if !raw_resp.trim().is_empty() {
                if let Ok(clean_text) = AiConnector::parse_gemini_response_text(raw_resp) {
                    // Try parsing JSON from candidate text
                    if let Ok(mut summary) = serde_json::from_str::<StructuredSummary>(&clean_text) {
                        summary.provider_used = "Google Gemini 1.5 Flash (Cloud)".into();
                        summary.privacy_protected = true;
                        let json = serde_json::to_string_pretty(&summary).unwrap_or_default();
                        return AgentActionResult {
                            success: true,
                            message: format!("Розумний підсумок від Google Gemini успішно отримано (PII захист: {})", was_sanitized),
                            data: Some(json),
                        };
                    }
                }
            }
        }

        // 3. Determine provider
        let provider_str = requested_provider
            .map(|s| s.to_string())
            .or_else(|| self.db.get_setting("ai_provider").ok().flatten())
            .unwrap_or_else(|| "local".into());

        let api_key = requested_key
            .map(|s| s.to_string())
            .or_else(|| self.db.get_setting("ai_gemini_api_key").ok().flatten());

        let provider = AiProvider::from(provider_str.as_str());

        // If Gemini is selected and API key is absent, fallback gracefully to Local ONNX
        let summary = match (provider, api_key) {
            (AiProvider::GoogleGemini, Some(_key)) => {
                // If caller didn't provide raw_ai_response yet, prepare prompt payload
                let prompt = AiConnector::build_gemini_summary_prompt(&chat_title, &sanitized_msgs);
                let payload = AiConnector::build_gemini_request_payload(&prompt);
                return AgentActionResult {
                    success: true,
                    message: "PROMPT_PREPARED".into(),
                    data: Some(payload),
                };
            }
            _ => {
                // High-quality local semantic analysis with 100% on-device privacy guarantee
                AiConnector::local_semantic_summary(&chat_title, &sanitized_msgs)
            }
        };

        let json = serde_json::to_string_pretty(&summary).unwrap_or_default();
        AgentActionResult {
            success: true,
            message: format!("Підсумок для '{}' успішно складено ({})", chat_title, summary.provider_used),
            data: Some(json),
        }
    }

    async fn handle_prepare_ai_summary(&self, chat_id: Option<i64>, strict_privacy: bool) -> AgentActionResult {
        let (chat_title, msgs) = self.get_dialog_messages(chat_id).await;
        if msgs.is_empty() {
            return AgentActionResult {
                success: false,
                message: "Немає повідомлень для формування запиту".into(),
                data: None,
            };
        }

        let (sanitized_msgs, was_sanitized) = PrivacyGuard::sanitize_messages(&msgs, strict_privacy);
        let prompt = AiConnector::build_gemini_summary_prompt(&chat_title, &sanitized_msgs);
        let payload = AiConnector::build_gemini_request_payload(&prompt);

        AgentActionResult {
            success: true,
            message: format!("Запит для Gemini успішно підготовлено (PII захист: {})", was_sanitized),
            data: Some(payload),
        }
    }

    async fn handle_parse_ai_summary(&self, chat_id: Option<i64>, raw_ai_response: &str) -> AgentActionResult {
        let (chat_title, msgs) = self.get_dialog_messages(chat_id).await;

        match AiConnector::parse_gemini_response_text(raw_ai_response) {
            Ok(clean_text) => {
                // Clean potential ```json and ``` fences
                let json_candidate = clean_text
                    .trim()
                    .trim_start_matches("```json")
                    .trim_start_matches("```")
                    .trim_end_matches("```")
                    .trim();

                match serde_json::from_str::<serde_json::Value>(json_candidate) {
                    Ok(val) => {
                        let key_topics = val.get("key_topics")
                            .and_then(|v| v.as_array())
                            .map(|arr| arr.iter().filter_map(|x| x.as_str().map(|s| s.to_string())).collect())
                            .unwrap_or_default();

                        let unanswered_questions = val.get("unanswered_questions")
                            .and_then(|v| v.as_array())
                            .map(|arr| arr.iter().filter_map(|x| x.as_str().map(|s| s.to_string())).collect())
                            .unwrap_or_default();

                        let action_items = val.get("action_items")
                            .and_then(|v| v.as_array())
                            .map(|arr| arr.iter().filter_map(|x| x.as_str().map(|s| s.to_string())).collect())
                            .unwrap_or_default();

                        let summary_text = val.get("summary_text")
                            .and_then(|v| v.as_str())
                            .unwrap_or(&clean_text)
                            .to_string();

                        let participants: Vec<String> = msgs.iter()
                            .map(|m| m.sender_name.clone())
                            .filter(|n| !n.is_empty())
                            .collect::<std::collections::HashSet<_>>()
                            .into_iter()
                            .collect();

                        let summary = StructuredSummary {
                            chat_title,
                            total_messages: msgs.len(),
                            participants,
                            key_topics,
                            unanswered_questions,
                            action_items,
                            summary_text,
                            provider_used: "Google Gemini (Gemini 1.5 Flash)".into(),
                            privacy_protected: true,
                        };

                        let json = serde_json::to_string_pretty(&summary).unwrap_or_default();
                        AgentActionResult {
                            success: true,
                            message: "Структурований підсумок від Gemini успішно розпізнано".into(),
                            data: Some(json),
                        }
                    }
                    Err(_) => {
                        // Return text directly in fallback summary
                        let summary = StructuredSummary {
                            chat_title,
                            total_messages: msgs.len(),
                            participants: vec!["Співрозмовник".into(), "Я".into()],
                            key_topics: vec!["Аналіз бесіди від Gemini".into()],
                            unanswered_questions: Vec::new(),
                            action_items: vec!["Ознайомитися з висновками".into()],
                            summary_text: clean_text,
                            provider_used: "Google Gemini 1.5 Flash".into(),
                            privacy_protected: true,
                        };
                        let json = serde_json::to_string_pretty(&summary).unwrap_or_default();
                        AgentActionResult {
                            success: true,
                            message: "Підсумок Gemini збережено".into(),
                            data: Some(json),
                        }
                    }
                }
            }
            Err(e) => AgentActionResult {
                success: false,
                message: format!("Помилка обробки відповіді від Gemini: {}", e),
                data: None,
            },
        }
    }

    async fn handle_draft_reply(
        &self,
        chat_id: Option<i64>,
        instruction: Option<String>,
        raw_ai_response: Option<&str>,
    ) -> AgentActionResult {
        let chat_id = match chat_id {
            Some(id) => id,
            None => {
                return AgentActionResult {
                    success: false,
                    message: "chat_id обов'язковий для складання відповіді".into(),
                    data: None,
                };
            }
        };

        if let Some(raw) = raw_ai_response {
            if !raw.trim().is_empty() {
                if let Ok(clean) = AiConnector::parse_gemini_response_text(raw) {
                    return AgentActionResult {
                        success: true,
                        message: "Чернетку відповіді складено через Google Gemini".into(),
                        data: Some(clean),
                    };
                }
            }
        }

        let msgs = self.db.get_messages(chat_id, 10, 0).unwrap_or_default();
        let last_incoming = msgs.iter().find(|m| !m.is_outgoing);

        let draft = match (last_incoming, instruction.as_deref()) {
            (Some(inc), Some(instr)) => {
                let instr_lower = instr.to_lowercase();
                if instr_lower.contains("погод") || instr_lower.contains("так") || instr_lower.contains("agree") {
                    format!("Домовилися! Щодо '{}' — все влаштовує, робимо так 👍", inc.text)
                } else if instr_lower.contains("відмов") || instr_lower.contains("ні") || instr_lower.contains("busy") {
                    format!("Дякую за повідомлення! На жаль, зараз не маю можливості щодо '{}', повернемося пізніше.", inc.text)
                } else if instr_lower.contains("запізн") || instr_lower.contains("late") {
                    "Привіт! Я трішки запізнююся, буду хвилин через 15-20. Перепрошую за затримку!".into()
                } else {
                    format!("{} (у відповідь на: \"{}\")", instr, inc.text)
                }
            }
            (Some(inc), None) => {
                if inc.text.contains('?') {
                    format!("Привіт! Щодо твого запитання (\"{}\") — я перевіряю інформацію і скоро відповім.", inc.text)
                } else {
                    format!("Зрозумів тебе! Прийняв до відома: \"{}\"", inc.text)
                }
            }
            (None, Some(instr)) => instr.to_string(),
            (None, None) => "Привіт! Як справи?".into(),
        };

        AgentActionResult {
            success: true,
            message: "Чернетку відповіді згенеровано".into(),
            data: Some(draft),
        }
    }

    async fn handle_search_messages(&self, query: Option<String>, limit: Option<i32>) -> AgentActionResult {
        let query = match query {
            Some(q) if !q.trim().is_empty() => q.trim().to_string(),
            _ => {
                return AgentActionResult {
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
                AgentActionResult {
                    success: true,
                    message: format!("Знайдено {} повідомлень за запитом '{}'", matches.len(), query),
                    data: Some(json),
                }
            }
            Err(e) => AgentActionResult {
                success: false,
                message: format!("Помилка пошуку повідомлень: {}", e),
                data: None,
            },
        }
    }

    async fn handle_get_ghost_mode(&self) -> AgentActionResult {
        let hide_read = self.db.get_setting("ghost_hide_read").unwrap_or_default().as_deref() == Some("1");
        let hide_typing = self.db.get_setting("ghost_hide_typing").unwrap_or_default().as_deref() == Some("1");
        let hide_online = self.db.get_setting("ghost_hide_online").unwrap_or_default().as_deref() == Some("1");

        let status = format!(
            "Ghost Mode:\n- Приховувати прочитання: {}\n- Приховувати набір тексту: {}\n- Приховувати онлайн: {}",
            if hide_read { "Увімкнено" } else { "Вимкнено" },
            if hide_typing { "Увімкнено" } else { "Вимкнено" },
            if hide_online { "Увімкнено" } else { "Вимкнено" }
        );

        AgentActionResult {
            success: true,
            message: "Статус Ghost Mode отримано".into(),
            data: Some(status),
        }
    }

    async fn handle_toggle_ghost_mode(&self, setting: &str, enabled: bool) -> AgentActionResult {
        let key = match setting {
            "hide_read" | "read" => "ghost_hide_read",
            "hide_typing" | "typing" => "ghost_hide_typing",
            "hide_online" | "online" => "ghost_hide_online",
            _ => "ghost_hide_read",
        };

        let val = if enabled { "1" } else { "0" };
        let _ = self.db.set_setting(key, val);

        AgentActionResult {
            success: true,
            message: format!("Налаштування Ghost Mode '{}' встановлено в {}", key, val),
            data: Some(val.to_string()),
        }
    }

    async fn handle_get_ai_settings(&self) -> AgentActionResult {
        let provider = self.db.get_setting("ai_provider").unwrap_or_default().unwrap_or_else(|| "local".into());
        let gemini_api_key = self.db.get_setting("ai_gemini_api_key").unwrap_or_default().unwrap_or_default();
        let strict_privacy = self.db.get_setting("ai_strict_privacy").unwrap_or_default().as_deref() == Some("true");

        let settings = AiSettingsModel {
            provider,
            gemini_api_key,
            strict_privacy,
        };

        AgentActionResult {
            success: true,
            message: "Налаштування AI отримано".into(),
            data: Some(serde_json::to_string(&settings).unwrap_or_default()),
        }
    }

    async fn handle_set_ai_settings(
        &self,
        provider: Option<&str>,
        api_key: Option<&str>,
        strict_privacy: Option<bool>,
    ) -> AgentActionResult {
        if let Some(p) = provider {
            let _ = self.db.set_setting("ai_provider", p);
        }
        if let Some(k) = api_key {
            let _ = self.db.set_setting("ai_gemini_api_key", k);
        }
        if let Some(sp) = strict_privacy {
            let _ = self.db.set_setting("ai_strict_privacy", if sp { "true" } else { "false" });
        }

        AgentActionResult {
            success: true,
            message: "Налаштування AI успішно збережено в базі даних".into(),
            data: None,
        }
    }

    async fn get_dialog_messages(&self, chat_id: Option<i64>) -> (String, Vec<MessageItem>) {
        match chat_id {
            Some(cid) => {
                let title = self.db
                    .get_chat_by_id(cid)
                    .ok()
                    .flatten()
                    .map(|c| c.title)
                    .unwrap_or_else(|| format!("Чат {}", cid));

                if let Some(tg) = self.telegram.as_ref() {
                    let _ = tg.fetch_messages(&self.db, cid, 30).await;
                }

                let msgs = self.db.get_messages(cid, 35, 0).unwrap_or_default();
                (title, msgs)
            }
            None => {
                let chats = self.db.get_chats().unwrap_or_default();
                let mut combined_msgs = Vec::new();
                for c in chats.iter().take(3) {
                    if let Ok(m) = self.db.get_messages(c.id, 10, 0) {
                        combined_msgs.extend(m);
                    }
                }
                ("Усі недавні чати".to_string(), combined_msgs)
            }
        }
    }
}
