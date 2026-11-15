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

/// A single repository-level action requested from the Kotlin layer.
/// Only real, MTProto/SQLite-backed operations are allowed here.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionRequest {
    pub action_type: String, // "list_chats", "read_chat", "send_message", "update_profile", "search_messages"
    pub chat_id: Option<i64>,
    pub text: Option<String>,
    pub bio: Option<String>,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub query: Option<String>,
    pub limit: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActionResult {
    pub success: bool,
    pub message: String,
    pub data: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserModel {
    pub id: i64,
    pub first_name: String,
    pub username: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StickerModel {
    pub document_id: i64,
    pub access_hash: i64,
    pub file_reference_hex: String,
    pub emoji: String,
    pub mime_type: String,
    pub width: i32,
    pub height: i32,
    pub thumb_path: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateEvent {
    pub r#type: String, // "new_message", "edit_message", "delete_message"
    pub chat_id: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<MessageItem>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message_id: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub msg_id: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub text: Option<String>,
}
