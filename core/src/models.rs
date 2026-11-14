use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatItem {
    pub id: i64,
    pub title: String,
    pub username: Option<String>,
    pub chat_type: String, // "user", "group", "channel"
    pub unread_count: i32,
    pub last_message_text: Option<String>,
    pub last_message_date: i64,
    pub last_message_outgoing: bool,
    pub last_message_read: bool,
    pub avatar_path: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageItem {
    pub id: i32,
    pub chat_id: i64,
    pub sender_id: i64,
    pub sender_name: String,
    pub text: String,
    pub date: i64,
    pub is_outgoing: bool,
    pub is_read: bool,
    pub reply_to_id: Option<i32>,
    pub is_deleted: bool,
    pub original_text: Option<String>,
    pub media_type: Option<String>, // "photo", "voice", "audio", "video", "file"
    pub media_path: Option<String>,
    pub duration: Option<i32>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct GhostConfig {
    pub hide_read: bool,
    pub hide_typing: bool,
    pub hide_online: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ReadIndicatorStyle {
    Dots,       // • / ••
    Checkmarks, // ✓ / ✓✓
    Hearts,     // ♡ / ♥
    Slashes,    // / / //
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum BubbleStyle {
    ClassicWithTail,
    RoundedWithoutTail,
    FlatMinimal,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentActionRequest {
    pub action_type: String, // "send_message", "read_chat", "update_bio", "update_profile", "summarize", "draft_reply", "search_messages", "list_chats", "get_ai_settings", "set_ai_settings", "prepare_ai_summary", "parse_ai_summary"
    pub chat_id: Option<i64>,
    pub text: Option<String>,
    pub bio: Option<String>,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub query: Option<String>,
    pub limit: Option<i32>,
    pub api_key: Option<String>,
    pub provider: Option<String>,
    pub strict_privacy: Option<bool>,
    pub raw_ai_response: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentActionResult {
    pub success: bool,
    pub message: String,
    pub data: Option<String>,
}
