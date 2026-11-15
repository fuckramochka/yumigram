use crate::db::LocalDatabase;
use crate::models::{ChatItem, MessageItem, UpdateEvent};
use grammers_client::client::UpdateStream;
use grammers_client::media::Media;
use grammers_client::update::Update;
use grammers_tl_types as tl;
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
                        Some(Media::Contact(_)) => (Some("contact".to_string()), None),
                        Some(Media::Geo(_))
                        | Some(Media::GeoLive(_))
                        | Some(Media::Venue(_)) => (Some("location".to_string()), None),
                        Some(Media::Document(ref doc)) => {
                            use tl::enums::Document as D;
                            let mut is_voice = false;
                            let mut is_round_video = false;
                            let mut is_video = false;
                            let mut is_audio = false;
                            let mut is_sticker = false;
                            let mut dur: Option<i32> = None;

                            if let Some(D::Document(d)) = doc.raw.document.as_ref() {
                                for attr in &d.attributes {
                                    match attr {
                                        tl::enums::DocumentAttribute::Audio(a) => {
                                            dur = Some(a.duration);
                                            if a.voice {
                                                is_voice = true;
                                            } else {
                                                is_audio = true;
                                            }
                                        }
                                        tl::enums::DocumentAttribute::Video(v) => {
                                            dur = Some(v.duration as i32);
                                            if v.round_message {
                                                is_round_video = true;
                                            } else {
                                                is_video = true;
                                            }
                                        }
                                        tl::enums::DocumentAttribute::Sticker(_) => {
                                            is_sticker = true;
                                        }
                                        _ => {}
                                    }
                                }
                            }

                            let mime = doc.mime_type().unwrap_or("");
                            if is_round_video {
                                (Some("video_note".to_string()), dur)
                            } else if is_voice || mime.starts_with("audio/ogg") {
                                (Some("voice".to_string()), dur)
                            } else if is_video || mime.starts_with("video/") {
                                (Some("video".to_string()), dur)
                            } else if is_audio || mime.starts_with("audio/") {
                                (Some("audio".to_string()), dur)
                            } else if is_sticker
                                || mime == "image/webp"
                                || mime.contains("tgsticker")
                                || mime.contains("sticker")
                            {
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
                        reply_to_id: msg.reply_to_message_id(),
                        is_deleted: false,
                        original_text: None,
                        media_type,
                        media_path: None,
                        duration,
                    };

                    let _ = self.db.save_message(&item);

                    // Update chat list row
                    let is_self = match msg.peer() {
                        Some(grammers_client::peer::Peer::User(u)) => u.is_self(),
                        _ => false,
                    };
                    let chat_title = if is_self {
                        "Збережене".to_string()
                    } else {
                        msg.peer().and_then(|p| p.name()).unwrap_or("Chat").to_string()
                    };
                    let chat_item = ChatItem {
                        id: chat_id,
                        title: chat_title,
                        username: None,
                        chat_type: if is_self { "user".into() } else { "dialog".into() },
                        unread_count: 0,
                        last_message_text: Some(msg.text().to_string()),
                        last_message_date: item.date,
                        last_message_outgoing: item.is_outgoing,
                        last_message_read: false,
                        avatar_path: None,
                    };
                    let _ = self.db.save_chat(&chat_item);

                    // Notify UI with structured event
                    let ev = UpdateEvent {
                        r#type: "new_message".to_string(),
                        chat_id,
                        message: Some(item),
                        message_id: Some(msg.id()),
                        msg_id: Some(msg.id()),
                        text: Some(msg.text().to_string()),
                    };
                    if let Ok(json_str) = serde_json::to_string(&ev) {
                        let _ = self.tx_event.send(json_str);
                    }
                }
                Update::MessageEdited(msg) => {
                    let chat_id = msg.peer_id().bot_api_dialog_id_unchecked();
                    let _ = self.db.mark_message_edited(chat_id, msg.id(), msg.text());
                    let ev = UpdateEvent {
                        r#type: "edit_message".to_string(),
                        chat_id,
                        message: None,
                        message_id: Some(msg.id()),
                        msg_id: Some(msg.id()),
                        text: Some(msg.text().to_string()),
                    };
                    if let Ok(json_str) = serde_json::to_string(&ev) {
                        let _ = self.tx_event.send(json_str);
                    }
                }
                Update::MessageDeleted(del) => {
                    // Anti-Delete in action: we do NOT erase, we mark deleted!
                    let channel_chat_id = del.channel_id();
                    for &msg_id in del.messages() {
                        let chat_id = channel_chat_id.unwrap_or_else(|| {
                            self.db.get_message_chat_id(msg_id).ok().flatten().unwrap_or(0)
                        });
                        let _ = self.db.mark_message_deleted(chat_id, msg_id);
                        let ev = UpdateEvent {
                            r#type: "delete_message".to_string(),
                            chat_id,
                            message: None,
                            message_id: Some(msg_id),
                            msg_id: Some(msg_id),
                            text: None,
                        };
                        if let Ok(json_str) = serde_json::to_string(&ev) {
                            let _ = self.tx_event.send(json_str);
                        }
                    }
                }
                _ => {}
            }
        }
    }
}
