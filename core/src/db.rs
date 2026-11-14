use crate::models::{ChatItem, MessageItem};
use rusqlite::{params, Connection, Result};
use std::sync::{Arc, Mutex};

pub struct LocalDatabase {
    conn: Arc<Mutex<Connection>>,
}

impl LocalDatabase {
    pub fn new(db_path: &str) -> Result<Self> {
        let conn = Connection::open(db_path)?;
        let db = Self {
            conn: Arc::new(Mutex::new(conn)),
        };
        db.create_tables()?;
        Ok(db)
    }

    fn create_tables(&self) -> Result<()> {
        let conn = self.conn.lock().unwrap();

        // Chats table
        conn.execute(
            "CREATE TABLE IF NOT EXISTS chats (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                username TEXT,
                chat_type TEXT NOT NULL,
                unread_count INTEGER DEFAULT 0,
                last_message_text TEXT,
                last_message_date INTEGER DEFAULT 0,
                last_message_outgoing INTEGER DEFAULT 0,
                last_message_read INTEGER DEFAULT 0,
                avatar_path TEXT
            )",
            [],
        )?;

        // Messages table with anti-delete, anti-edit, and media support!
        conn.execute(
            "CREATE TABLE IF NOT EXISTS messages (
                id INTEGER NOT NULL,
                chat_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                sender_name TEXT NOT NULL,
                text TEXT NOT NULL,
                date INTEGER NOT NULL,
                is_outgoing INTEGER DEFAULT 0,
                is_read INTEGER DEFAULT 0,
                reply_to_id INTEGER,
                is_deleted INTEGER DEFAULT 0,
                original_text TEXT,
                media_type TEXT,
                media_path TEXT,
                duration INTEGER,
                PRIMARY KEY (chat_id, id)
            )",
            [],
        )?;

        // Safe migrations for columns
        let _ = conn.execute("ALTER TABLE messages ADD COLUMN media_path TEXT", []);
        let _ = conn.execute("ALTER TABLE messages ADD COLUMN duration INTEGER", []);

        // Settings table for Ghost mode and preferences
        conn.execute(
            "CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )",
            [],
        )?;

        Ok(())
    }

    pub fn save_chat(&self, chat: &ChatItem) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO chats (id, title, username, chat_type, unread_count, 
                                last_message_text, last_message_date, last_message_outgoing, 
                                last_message_read, avatar_path)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
             ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                username = excluded.username,
                chat_type = excluded.chat_type,
                unread_count = excluded.unread_count,
                last_message_text = excluded.last_message_text,
                last_message_date = excluded.last_message_date,
                last_message_outgoing = excluded.last_message_outgoing,
                last_message_read = excluded.last_message_read,
                avatar_path = COALESCE(excluded.avatar_path, avatar_path)",
            params![
                chat.id,
                chat.title,
                chat.username,
                chat.chat_type,
                chat.unread_count,
                chat.last_message_text,
                chat.last_message_date,
                chat.last_message_outgoing as i32,
                chat.last_message_read as i32,
                chat.avatar_path,
            ],
        )?;
        Ok(())
    }

    pub fn get_chats(&self) -> Result<Vec<ChatItem>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, title, username, chat_type, unread_count, 
                    last_message_text, last_message_date, last_message_outgoing, 
                    last_message_read, avatar_path
             FROM chats ORDER BY last_message_date DESC",
        )?;

        let chat_iter = stmt.query_map([], |row| {
            Ok(ChatItem {
                id: row.get(0)?,
                title: row.get(1)?,
                username: row.get(2)?,
                chat_type: row.get(3)?,
                unread_count: row.get(4)?,
                last_message_text: row.get(5)?,
                last_message_date: row.get(6)?,
                last_message_outgoing: row.get::<_, i32>(7)? != 0,
                last_message_read: row.get::<_, i32>(8)? != 0,
                avatar_path: row.get(9)?,
            })
        })?;

        let mut chats = Vec::new();
        for chat in chat_iter {
            chats.push(chat?);
        }
        Ok(chats)
    }

    pub fn save_message(&self, msg: &MessageItem) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO messages (id, chat_id, sender_id, sender_name, text, date,
                                   is_outgoing, is_read, reply_to_id, is_deleted, original_text,
                                   media_type, media_path, duration)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
             ON CONFLICT(chat_id, id) DO UPDATE SET
                text = excluded.text,
                is_read = excluded.is_read,
                media_path = COALESCE(excluded.media_path, media_path)",
            params![
                msg.id,
                msg.chat_id,
                msg.sender_id,
                msg.sender_name,
                msg.text,
                msg.date,
                msg.is_outgoing as i32,
                msg.is_read as i32,
                msg.reply_to_id,
                msg.is_deleted as i32,
                msg.original_text,
                msg.media_type,
                msg.media_path,
                msg.duration,
            ],
        )?;
        Ok(())
    }

    pub fn update_message_media_path(&self, chat_id: i64, message_id: i32, media_path: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE messages SET media_path = ?1 WHERE chat_id = ?2 AND id = ?3",
            params![media_path, chat_id, message_id],
        )?;
        Ok(())
    }

    pub fn update_chat_avatar_path(&self, chat_id: i64, avatar_path: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE chats SET avatar_path = ?1 WHERE id = ?2",
            params![avatar_path, chat_id],
        )?;
        Ok(())
    }

    /// Anti-Delete feature: when Telegram sends a delete event, we DO NOT remove the row!
    /// We mark `is_deleted = 1` so the user can still see what was deleted!
    pub fn mark_message_deleted(&self, chat_id: i64, message_id: i32) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        if chat_id != 0 {
            conn.execute(
                "UPDATE messages SET is_deleted = 1 WHERE chat_id = ?1 AND id = ?2",
                params![chat_id, message_id],
            )?;
        } else {
            conn.execute(
                "UPDATE messages SET is_deleted = 1 WHERE id = ?1",
                params![message_id],
            )?;
        }
        Ok(())
    }

    /// Anti-Edit feature: stores the original text if it was modified
    pub fn mark_message_edited(&self, chat_id: i64, message_id: i32, new_text: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "UPDATE messages 
             SET original_text = CASE WHEN original_text IS NULL THEN text ELSE original_text END,
                 text = ?3
             WHERE chat_id = ?1 AND id = ?2",
            params![chat_id, message_id, new_text],
        )?;
        Ok(())
    }

    pub fn get_messages(&self, chat_id: i64, limit: i32, offset: i32) -> Result<Vec<MessageItem>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, chat_id, sender_id, sender_name, text, date,
                    is_outgoing, is_read, reply_to_id, is_deleted, original_text,
                    media_type, media_path, duration
             FROM messages 
             WHERE chat_id = ?1 
             ORDER BY date DESC, id DESC 
             LIMIT ?2 OFFSET ?3",
        )?;

        let msg_iter = stmt.query_map(params![chat_id, limit, offset], |row| {
            Ok(MessageItem {
                id: row.get(0)?,
                chat_id: row.get(1)?,
                sender_id: row.get(2)?,
                sender_name: row.get(3)?,
                text: row.get(4)?,
                date: row.get(5)?,
                is_outgoing: row.get::<_, i32>(6)? != 0,
                is_read: row.get::<_, i32>(7)? != 0,
                reply_to_id: row.get(8)?,
                is_deleted: row.get::<_, i32>(9)? != 0,
                original_text: row.get(10)?,
                media_type: row.get(11)?,
                media_path: row.get(12)?,
                duration: row.get(13)?,
            })
        })?;

        let mut msgs = Vec::new();
        for msg in msg_iter {
            msgs.push(msg?);
        }
        Ok(msgs)
    }

    pub fn search_messages(&self, query: &str, limit: i32) -> Result<Vec<MessageItem>> {
        let conn = self.conn.lock().unwrap();
        let pattern = format!("%{}%", query);
        let mut stmt = conn.prepare(
            "SELECT id, chat_id, sender_id, sender_name, text, date,
                    is_outgoing, is_read, reply_to_id, is_deleted, original_text,
                    media_type, media_path, duration
             FROM messages 
             WHERE text LIKE ?1
             ORDER BY date DESC 
             LIMIT ?2",
        )?;

        let msg_iter = stmt.query_map(params![pattern, limit], |row| {
            Ok(MessageItem {
                id: row.get(0)?,
                chat_id: row.get(1)?,
                sender_id: row.get(2)?,
                sender_name: row.get(3)?,
                text: row.get(4)?,
                date: row.get(5)?,
                is_outgoing: row.get::<_, i32>(6)? != 0,
                is_read: row.get::<_, i32>(7)? != 0,
                reply_to_id: row.get(8)?,
                is_deleted: row.get::<_, i32>(9)? != 0,
                original_text: row.get(10)?,
                media_type: row.get(11)?,
                media_path: row.get(12)?,
                duration: row.get(13)?,
            })
        })?;

        let mut msgs = Vec::new();
        for msg in msg_iter {
            msgs.push(msg?);
        }
        Ok(msgs)
    }

    pub fn get_chat_by_id(&self, chat_id: i64) -> Result<Option<ChatItem>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare(
            "SELECT id, title, username, chat_type, unread_count, 
                    last_message_text, last_message_date, last_message_outgoing, 
                    last_message_read, avatar_path
             FROM chats WHERE id = ?1",
        )?;
        let mut rows = stmt.query(params![chat_id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(ChatItem {
                id: row.get(0)?,
                title: row.get(1)?,
                username: row.get(2)?,
                chat_type: row.get(3)?,
                unread_count: row.get(4)?,
                last_message_text: row.get(5)?,
                last_message_date: row.get(6)?,
                last_message_outgoing: row.get::<_, i32>(7)? != 0,
                last_message_read: row.get::<_, i32>(8)? != 0,
                avatar_path: row.get(9)?,
            }))
        } else {
            Ok(None)
        }
    }

    pub fn set_setting(&self, key: &str, value: &str) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "INSERT INTO settings (key, value) VALUES (?1, ?2)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![key, value],
        )?;
        Ok(())
    }

    pub fn get_setting(&self, key: &str) -> Result<Option<String>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn.prepare("SELECT value FROM settings WHERE key = ?1")?;
        let mut rows = stmt.query(params![key])?;
        if let Some(row) = rows.next()? {
            Ok(Some(row.get(0)?))
        } else {
            Ok(None)
        }
    }
}
