use yumigram_core::db::LocalDatabase;
use yumigram_core::models::{ChatItem, MessageItem};

#[test]
fn test_db_init_and_chat_operations() {
    // In-memory or temporary database
    let db = LocalDatabase::new(":memory:").expect("Failed to initialize SQLite in memory");

    let chat = ChatItem {
        id: 1001,
        title: "Test Community".into(),
        username: Some("test_comm".into()),
        chat_type: "group".into(),
        unread_count: 5,
        last_message_text: Some("Hello World".into()),
        last_message_date: 1700000000,
        last_message_outgoing: false,
        last_message_read: false,
        avatar_path: None,
    };

    // Save chat
    db.save_chat(&chat).expect("Failed to save chat");

    // Retrieve chats
    let chats = db.get_chats().expect("Failed to get chats");
    assert_eq!(chats.len(), 1);
    assert_eq!(chats[0].id, 1001);
    assert_eq!(chats[0].title, "Test Community");
    assert_eq!(chats[0].unread_count, 5);
}

#[test]
fn test_anti_delete_verification() {
    let db = LocalDatabase::new(":memory:").expect("Failed to initialize SQLite in memory");

    let msg = MessageItem {
        id: 42,
        chat_id: 1001,
        sender_id: 555,
        sender_name: "Alice".into(),
        text: "Секретне повідомлення, яке видалять!".into(),
        date: 1700000010,
        is_outgoing: false,
        is_read: true,
        reply_to_id: None,
        is_deleted: false,
        original_text: None,
        media_type: None,
    };

    db.save_message(&msg).expect("Failed to save message");

    // Verify initial message is NOT deleted
    let msgs_before = db.get_messages(1001, 10, 0).expect("Failed to get messages");
    assert_eq!(msgs_before.len(), 1);
    assert!(!msgs_before[0].is_deleted);

    // Simulate Telegram server sending DeleteMessages
    db.mark_message_deleted(1001, 42).expect("Failed to mark deleted");

    // Anti-Delete check: Message MUST still exist, but marked as is_deleted = true!
    let msgs_after = db.get_messages(1001, 10, 0).expect("Failed to get messages");
    assert_eq!(msgs_after.len(), 1);
    assert_eq!(msgs_after[0].id, 42);
    assert!(msgs_after[0].is_deleted, "Message must be preserved with is_deleted = true");
    assert_eq!(msgs_after[0].text, "Секретне повідомлення, яке видалять!");
}

#[test]
fn test_anti_edit_verification() {
    let db = LocalDatabase::new(":memory:").expect("Failed to initialize SQLite in memory");

    let msg = MessageItem {
        id: 100,
        chat_id: 2002,
        sender_id: 777,
        sender_name: "Bob".into(),
        text: "Оригінальний текст до правки".into(),
        date: 1700000020,
        is_outgoing: false,
        is_read: true,
        reply_to_id: None,
        is_deleted: false,
        original_text: None,
        media_type: None,
    };

    db.save_message(&msg).expect("Failed to save message");

    // Simulate edit event
    db.mark_message_edited(2002, 100, "Новий змінений текст").expect("Failed to edit message");

    let msgs = db.get_messages(2002, 10, 0).expect("Failed to get messages");
    assert_eq!(msgs.len(), 1);
    assert_eq!(msgs[0].text, "Новий змінений текст");
    assert_eq!(msgs[0].original_text, Some("Оригінальний текст до правки".into()));
}

#[test]
fn test_settings_storage() {
    let db = LocalDatabase::new(":memory:").expect("Failed to initialize SQLite in memory");

    db.set_setting("ghost_mode", "true").expect("Failed to set setting");
    let val = db.get_setting("ghost_mode").expect("Failed to get setting");
    assert_eq!(val, Some("true".into()));

    let missing = db.get_setting("non_existent").expect("Failed to query");
    assert_eq!(missing, None);
}
