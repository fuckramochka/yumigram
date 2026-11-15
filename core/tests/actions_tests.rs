use yumigram_core::actions::ActionExecutor;
use yumigram_core::client::TelegramManager;
use yumigram_core::db::LocalDatabase;
use yumigram_core::ghost::GhostManager;
use yumigram_core::models::{ActionRequest, ChatItem, GhostConfig, MessageItem};
use std::sync::Arc;

#[test]
fn test_ghost_manager_toggles() {
    let manager = GhostManager::new(GhostConfig::default());

    // By default, suppression is OFF
    assert!(!manager.should_suppress_read_receipt());
    assert!(!manager.should_suppress_typing());
    assert!(!manager.should_suppress_online());

    // Enable full Ghost Mode
    manager.update_config(GhostConfig {
        hide_read: true,
        hide_typing: true,
        hide_online: true,
    });

    assert!(manager.should_suppress_read_receipt());
    assert!(manager.should_suppress_typing());
    assert!(manager.should_suppress_online());
}

#[tokio::test]
async fn test_action_executor_real_capabilities() {
    let db = Arc::new(LocalDatabase::new(":memory:").expect("DB failed"));
    let actions = ActionExecutor::new(db.clone(), None);

    // Seed test chat & messages
    let chat = ChatItem {
        id: 1001,
        title: "Команда Yumigram".into(),
        username: Some("yumigram_team".into()),
        chat_type: "group".into(),
        unread_count: 2,
        last_message_text: Some("Коли реліз нової версії?".into()),
        last_message_date: 1731600000,
        last_message_outgoing: false,
        last_message_read: false,
        avatar_path: None,
    };
    db.save_chat(&chat).expect("Save chat failed");

    let msg1 = MessageItem {
        id: 1,
        chat_id: 1001,
        sender_id: 200,
        sender_name: "Олексій".into(),
        text: "Привіт! Ми завершили інтеграцію MTProto та аудіоплеєра?".into(),
        date: 1731600000,
        is_outgoing: false,
        is_read: true,
        reply_to_id: None,
        is_deleted: false,
        original_text: None,
        media_type: None,
        media_path: None,
        duration: None,
    };
    let msg2 = MessageItem {
        id: 2,
        chat_id: 1001,
        sender_id: 201,
        sender_name: "Марія".into(),
        text: "Так, всі тести пройдено. Треба сьогодні викласти оновлення в реліз!".into(),
        date: 1731600050,
        is_outgoing: false,
        is_read: false,
        reply_to_id: None,
        is_deleted: false,
        original_text: None,
        media_type: None,
        media_path: None,
        duration: None,
    };
    db.save_message(&msg1).expect("Save msg1 failed");
    db.save_message(&msg2).expect("Save msg2 failed");

    // 1. list_chats
    let list_res = actions.execute_action(ActionRequest {
        action_type: "list_chats".into(),
        chat_id: None,
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
    }).await;
    assert!(list_res.success);
    assert!(list_res.message.contains("1 активних"));
    assert!(list_res.data.unwrap().contains("Команда Yumigram"));

    // 2. read_chat
    let read_res = actions.execute_action(ActionRequest {
        action_type: "read_chat".into(),
        chat_id: Some(1001),
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: Some(10),
    }).await;
    assert!(read_res.success);
    assert!(read_res.data.unwrap().contains("інтеграцію MTProto"));

    // 3. search_messages
    let search_res = actions.execute_action(ActionRequest {
        action_type: "search_messages".into(),
        chat_id: None,
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: Some("аудіоплеєра".into()),
        limit: Some(5),
    }).await;
    assert!(search_res.success);
    assert!(search_res.message.contains("1 повідомлень"));

    // 4. update_profile persists fields locally; without a connected MTProto client
    //    the action must honestly report failure instead of pretending success.
    let prof_res = actions.execute_action(ActionRequest {
        action_type: "update_profile".into(),
        chat_id: None,
        text: None,
        bio: Some("Building Yumigram with real MTProto".into()),
        first_name: Some("Yumi".into()),
        last_name: Some("Developer".into()),
        query: None,
        limit: None,
    }).await;
    assert!(!prof_res.success);
    assert!(prof_res.message.contains("не підключений"));
    assert_eq!(db.get_setting("profile_first_name").unwrap(), Some("Yumi".into()));
    assert_eq!(db.get_setting("profile_bio").unwrap(), Some("Building Yumigram with real MTProto".into()));

    // 5. Unknown actions are rejected, never faked
    let unknown_res = actions.execute_action(ActionRequest {
        action_type: "summarize".into(),
        chat_id: None,
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
    }).await;
    assert!(!unknown_res.success);
}

#[tokio::test]
async fn test_telegram_manager_connect_lifecycle() {
    let test_session = "test_tg_lifecycle.session";
    let _ = std::fs::remove_file(test_session);

    // 1st run: file does not exist
    {
        let tg = TelegramManager::new(test_session.to_string());
        let connect_res = tg.connect().await;
        println!("1st Connect result: {:?}", connect_res);
        assert!(connect_res.is_ok());

        let is_auth = tg.is_authorized().await;
        println!("1st Is authorized: {:?}", is_auth);
        assert!(!is_auth);
    }

    // 2nd run: file ALREADY EXISTS!
    {
        let tg2 = TelegramManager::new(test_session.to_string());
        let connect_res2 = tg2.connect().await;
        println!("2nd Connect result: {:?}", connect_res2);
        assert!(connect_res2.is_ok());

        let is_auth2 = tg2.is_authorized().await;
        println!("2nd Is authorized: {:?}", is_auth2);
        assert!(!is_auth2);
    }

    let _ = std::fs::remove_file(test_session);
}
