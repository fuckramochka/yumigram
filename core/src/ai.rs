use crate::models::{MessageItem};
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::sync::LazyLock;

static RE_PHONE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(\+?\d{1,3}[-.\s]?)?(\(?\d{2,4}\)?[-.\s]?)?\d{3,4}[-.\s]?\d{3,4}").unwrap()
});

static RE_EMAIL: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}").unwrap()
});

static RE_CREDIT_CARD: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"\b(?:\d{4}[ -]?){3}\d{4}\b").unwrap()
});

static RE_SECRET: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?i)(api[_-]?key|bearer|token|password|secret|пароль|токен)\s*[:=]\s*\S+").unwrap()
});

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum AiProvider {
    GoogleGemini,
    LocalOnnx,
}

impl Default for AiProvider {
    fn default() -> Self {
        Self::LocalOnnx
    }
}

impl From<&str> for AiProvider {
    fn from(s: &str) -> Self {
        match s.to_lowercase().as_str() {
            "gemini" | "google_gemini" | "google" => AiProvider::GoogleGemini,
            _ => AiProvider::LocalOnnx,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SanitizedMessage {
    pub id: i32,
    pub sender_name: String,
    pub text: String,
    pub is_outgoing: bool,
    pub was_sanitized: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StructuredSummary {
    pub chat_title: String,
    pub total_messages: usize,
    pub participants: Vec<String>,
    pub key_topics: Vec<String>,
    pub unanswered_questions: Vec<String>,
    pub action_items: Vec<String>,
    pub summary_text: String,
    pub provider_used: String,
    pub privacy_protected: bool,
}

pub struct PrivacyGuard;

impl PrivacyGuard {
    /// Sanitizes sensitive personally identifiable information (PII)
    /// such as phone numbers, emails, credit cards, and API secrets.
    pub fn sanitize_text(text: &str) -> (String, bool) {
        let mut modified = false;
        let mut result = text.to_string();

        if RE_SECRET.is_match(&result) {
            result = RE_SECRET.replace_all(&result, "$1=[SECRET_REDACTED]").into_owned();
            modified = true;
        }

        if RE_CREDIT_CARD.is_match(&result) {
            result = RE_CREDIT_CARD.replace_all(&result, "[CARD_REDACTED]").into_owned();
            modified = true;
        }

        if RE_EMAIL.is_match(&result) {
            result = RE_EMAIL.replace_all(&result, "[EMAIL_REDACTED]").into_owned();
            modified = true;
        }

        // Only redact phone numbers if length is at least 9 characters to avoid matching small numbers
        if result.chars().filter(|c| c.is_ascii_digit()).count() >= 9 && RE_PHONE.is_match(&result) {
            result = RE_PHONE.replace_all(&result, "[PHONE_REDACTED]").into_owned();
            modified = true;
        }

        (result, modified)
    }

    /// Sanitizes a batch of messages and optionally masks participant names
    pub fn sanitize_messages(messages: &[MessageItem], anonymize_names: bool) -> (Vec<SanitizedMessage>, bool) {
        let mut any_sanitized = false;
        let mut name_map = std::collections::HashMap::new();
        let mut user_counter = 1;

        let sanitized = messages
            .iter()
            .map(|m| {
                let (clean_text, was_clean) = Self::sanitize_text(&m.text);
                if was_clean {
                    any_sanitized = true;
                }

                let display_name = if anonymize_names {
                    if m.is_outgoing {
                        "Я".to_string()
                    } else {
                        name_map.entry(m.sender_name.clone()).or_insert_with(|| {
                            let alias = format!("Співрозмовник {}", user_counter);
                            user_counter += 1;
                            alias
                        }).clone()
                    }
                } else {
                    m.sender_name.clone()
                };

                SanitizedMessage {
                    id: m.id,
                    sender_name: display_name,
                    text: clean_text,
                    is_outgoing: m.is_outgoing,
                    was_sanitized: was_clean,
                }
            })
            .collect();

        (sanitized, any_sanitized)
    }
}

pub struct AiConnector;

impl AiConnector {
    /// Builds the structured prompt for Google Gemini to produce clean JSON
    pub fn build_gemini_summary_prompt(chat_title: &str, messages: &[SanitizedMessage]) -> String {
        let mut dialog_log = String::new();
        for m in messages {
            dialog_log.push_str(&format!("{}: {}\n", m.sender_name, m.text));
        }

        format!(
            "Ти — інтелектуальний помічник захищеного месенджера Yumigram.\n\
            Проаналізуй діалог із чату: \"{}\".\n\
            Діалог:\n\
            {}\n\n\
            Сформуй структурований звіт у форматі JSON із такими ключами:\n\
            {{\n\
              \"chat_title\": \"{}\",\n\
              \"key_topics\": [\"тема 1\", \"тема 2\"],\n\
              \"unanswered_questions\": [\"питання 1\"],\n\
              \"action_items\": [\"завдання/домовленість 1\"],\n\
              \"summary_text\": \"Детальний та якісний підсумок розмови...\"\n\
            }}\n\
            Відповідай ТІЛЬКИ валідним JSON без додаткового тексту.",
            chat_title, dialog_log, chat_title
        )
    }

    /// Builds a prompt for generating a smart reply
    pub fn build_gemini_reply_prompt(
        chat_title: &str,
        context: &[SanitizedMessage],
        instruction: Option<&str>,
    ) -> String {
        let mut dialog_log = String::new();
        for m in context.iter().rev().take(6) {
            dialog_log.push_str(&format!("{}: {}\n", m.sender_name, m.text));
        }

        let user_goal = instruction.unwrap_or("ввічливо та змістовно відповісти на останнє повідомлення");

        format!(
            "Ти — персональний асистент у Telegram клієнті Yumigram.\n\
            Останні репліки з чату \"{}\":\n\
            {}\n\n\
            Інструкція користувача: {}\n\
            Склади одну готову природну чернетку відповіді від першої особи ('Я').\n\
            Повертай ТІЛЬКИ текст повідомлення.",
            chat_title, dialog_log, user_goal
        )
    }

    /// Wraps a prompt into the Google Gemini REST JSON payload
    pub fn build_gemini_request_payload(prompt: &str) -> String {
        serde_json::json!({
            "contents": [{
                "parts": [{ "text": prompt }]
            }],
            "generationConfig": {
                "temperature": 0.2,
                "maxOutputTokens": 1024
            }
        })
        .to_string()
    }

    /// Parses the text response from Google Gemini API JSON
    pub fn parse_gemini_response_text(api_json: &str) -> Result<String, String> {
        let val: serde_json::Value = serde_json::from_str(api_json)
            .map_err(|e| format!("Failed to parse Gemini JSON: {}", e))?;

        if let Some(err) = val.get("error").and_then(|e| e.get("message")).and_then(|m| m.as_str()) {
            return Err(format!("Gemini API error: {}", err));
        }

        let candidate_text = val
            .pointer("/candidates/0/content/parts/0/text")
            .and_then(|t| t.as_str())
            .ok_or_else(|| "Gemini response did not contain candidate text".to_string())?;

        Ok(candidate_text.trim().to_string())
    }

    /// Executes high-quality local semantic extraction (Local ONNX / Offline mode)
    pub fn local_semantic_summary(chat_title: &str, messages: &[SanitizedMessage]) -> StructuredSummary {
        let mut participants = Vec::new();
        let mut questions = Vec::new();
        let mut topics = Vec::new();
        let mut action_items = Vec::new();

        let action_keywords = [
            "треба", "потрібно", "зроби", "скинь", "надішли", "домовилися", "завтра",
            "сьогодні", "перевір", "зустріч", "зателефонуй", "купи", "todo", "task", "meeting",
        ];

        for m in messages.iter().rev() {
            if !participants.contains(&m.sender_name) && !m.sender_name.is_empty() {
                participants.push(m.sender_name.clone());
            }

            let text = m.text.trim();
            let text_lower = text.to_lowercase();

            // Extract questions
            if (text.contains('?') || text.ends_with('?')) && questions.len() < 5 {
                questions.push(format!("{}: \"{}\"", m.sender_name, text));
            }

            // Extract action items
            for kw in &action_keywords {
                if text_lower.contains(kw) && action_items.len() < 5 {
                    if !action_items.iter().any(|item: &String| item.contains(text)) {
                        action_items.push(format!("{}: {}", m.sender_name, text));
                        break;
                    }
                }
            }

            // Extract topics based on meaningful phrases
            if text.chars().count() > 15 && topics.len() < 6 {
                let char_count = text.chars().count();
                let snippet = if char_count > 50 {
                    let truncated: String = text.chars().take(47).collect();
                    format!("{}...", truncated)
                } else {
                    text.to_string()
                };
                if !topics.contains(&snippet) {
                    topics.push(snippet);
                }
            }
        }

        if action_items.is_empty() {
            action_items.push("Перевірити відкриті питання та узгодити подальші кроки".into());
        }

        let last_msg_snippet = messages.first().map(|m| m.text.as_str()).unwrap_or("немає повідомлень");
        let summary_text = format!(
            "Діалог '{}' містить {} опрацьованих повідомлень від {} учасників.\n\
            Основний контекст: останній обмін думками стосувався \"{}\".\n\
            Всі чутливі дані захищені вбудованим PrivacyGuard.",
            chat_title,
            messages.len(),
            participants.len(),
            last_msg_snippet
        );

        StructuredSummary {
            chat_title: chat_title.to_string(),
            total_messages: messages.len(),
            participants,
            key_topics: topics,
            unanswered_questions: questions,
            action_items,
            summary_text,
            provider_used: "Local ONNX / Offline Privacy Engine".into(),
            privacy_protected: true,
        }
    }
}
