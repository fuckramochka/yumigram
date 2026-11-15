use crate::db::LocalDatabase;
use crate::models::{ChatItem, MessageItem};
use grammers_client::client::UpdateStream;
use grammers_client::media::Media;
use grammers_client::update::Update;
use std::sync::Arc;
use tokio::sync::broadcast;

pub struct UpdatesEngine {
    db: Arc<LocalDatabase>,
    tx_event: broadcast::Sender<String>,
}

impl UpdatesEngine {
    pub fn new(db: Arc<LocalDatabase>, tx_event: broadcast::Sender<String>) -> Self {
        Self { db, tx_event }
    }

    /// Background loop that processes real-time Telegram updates
    pub async fn run_loop(&self, mut stream: UpdateStream) {
        log::info!("UpdatesEngine background loop started");
        while let Ok(update) = stream.next().await {
            match update {
                Update::NewMessage(msg) => {
                    let chat_id = msg.peer_id().bot_api_dialog_id_unchecked();
                    let sender_id = msg.sender_id().map(|p| p.bot_api_dialog_id_unchecked()).unwrap_or(0);
                    let sender_name = msg.sender().and_then(|p| p.name()).unwrap_or("Unknown").to_string();

                    let (media_type, duration) = match msg.media() {
                        Some(Media::Photo(_)) => (Some("photo".to_string()), None),
                        Some(Media::Sticker(_)) => (Some("sticker".to_string()), None),
                        Some(Media::Poll(_)) => (Some("poll".to_string()), None),
                        Some(Media::Document(ref doc)) => {
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
                        sender_id,
                        sender_name,
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

                    let _ = self.db.save_message(&item);

                    // Update chat list row
                    let chat_title = msg.peer().and_then(|p| p.name()).unwrap_or("Chat").to_string();
                    let chat_item = ChatItem {
                        id: chat_id,
                        title: chat_title,
                        username: None,
                        chat_type: "dialog".into(),
                        unread_count: 0,
                        last_message_text: Some(msg.text().to_string()),
                        last_message_date: item.date,
                        last_message_outgoing: item.is_outgoing,
                        last_message_read: false,
                        avatar_path: None,
                    };
                    let _ = self.db.save_chat(&chat_item);

                    // Notify UI
                    let _ = self.tx_event.send(format!("new_message:{}", chat_id));
                }
                Update::MessageEdited(msg) => {
                    let chat_id = msg.peer_id().bot_api_dialog_id_unchecked();
                    let _ = self.db.mark_message_edited(chat_id, msg.id(), msg.text());
                    let _ = self.tx_event.send(format!("edit_message:{}:{}", chat_id, msg.id()));
                }
                Update::MessageDeleted(del) => {
                    // Anti-Delete in action: we do NOT erase, we mark deleted!
                    let chat_id = del.channel_id().unwrap_or(0);
                    for &msg_id in del.messages() {
                        let _ = self.db.mark_message_deleted(chat_id, msg_id);
                        let _ = self.tx_event.send(format!("delete_message:{}:{}", chat_id, msg_id));
                    }
                }
                _ => {}
            }
        }
    }
}
