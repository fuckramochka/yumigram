use yumigram_core::ai::{AiConnector, PrivacyGuard};
use yumigram_core::ghost::GhostManager;
use yumigram_core::models::{ChatItem, GhostConfig, MessageItem};
use yumigram_core::agent::AgentExecutor;
use yumigram_core::db::LocalDatabase;
use yumigram_core::models::AgentActionRequest;
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

#[test]
fn test_privacy_guard_sanitization() {
    // 1. Phone number masking
    let phone_sample = "Зателефонуй мені на +380991234567 терміново!";
    let (sanitized_phone, was_sanitized) = PrivacyGuard::sanitize_text(phone_sample);
    assert!(was_sanitized);
    assert!(!sanitized_phone.contains("+380991234567"));
    assert!(sanitized_phone.contains("[PHONE_REDACTED]"));

    // 2. Email masking
    let email_sample = "Надішли звіт на alex.dev@example.org до кінця дня";
    let (sanitized_email, was_sanitized) = PrivacyGuard::sanitize_text(email_sample);
    assert!(was_sanitized);
    assert!(!sanitized_email.contains("alex.dev@example.org"));
    assert!(sanitized_email.contains("[EMAIL_REDACTED]"));

    // 3. Credit card masking
    let card_sample = "Ось номер картки: 4111 2222 3333 4444 для оплати";
    let (sanitized_card, was_sanitized) = PrivacyGuard::sanitize_text(card_sample);
    assert!(was_sanitized);
    assert!(!sanitized_card.contains("4111 2222 3333 4444"));
    assert!(sanitized_card.contains("[CARD_REDACTED]"));

    // 4. API Secret / Token masking
    let token_sample = "Мій api_key=AIzaSyD-9x9x9xsecretToken для доступу";
    let (sanitized_token, was_sanitized) = PrivacyGuard::sanitize_text(token_sample);
    assert!(was_sanitized);
    assert!(!sanitized_token.contains("AIzaSyD-9x9x9xsecretToken"));
    assert!(sanitized_token.contains("[SECRET_REDACTED]"));

    // 5. Clean text remains untouched
    let clean_sample = "Привіт, зустрінемося сьогодні о 18:00 у центрі?";
    let (res_clean, was_clean) = PrivacyGuard::sanitize_text(clean_sample);
    assert!(!was_clean);
    assert_eq!(res_clean, clean_sample);
}

#[test]
fn test_gemini_payload_builder_and_parser() {
    let prompt = "Склади короткий підсумок";
    let payload = AiConnector::build_gemini_request_payload(prompt);
    assert!(payload.contains("parts"));
    assert!(payload.contains("maxOutputTokens"));

    // Mock Gemini API Response JSON
    let gemini_json = r#"{
      "candidates": [
        {
          "content": {
            "parts": [
              {
                "text": "```json\n{\n  \"chat_title\": \"Команда Yumigram\",\n  \"key_topics\": [\"Реліз\", \"Тести\"],\n  \"unanswered_questions\": [],\n  \"action_items\": [\"Запустити білд\"],\n  \"summary_text\": \"Успішна підготовка релізу\"\n}\n```"
              }
            ]
          }
        }
      ]
    }"#;

    let parsed_text = AiConnector::parse_gemini_response_text(gemini_json).expect("Parse failed");
    assert!(parsed_text.contains("Команда Yumigram"));
    assert!(parsed_text.contains("Реліз"));
}

#[tokio::test]
async fn test_agent_executor_full_capabilities() {
    let db = Arc::new(LocalDatabase::new(":memory:").expect("DB failed"));
    let agent = AgentExecutor::new(db.clone(), None);

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
        text: "Привіт! Ми завершили інтеграцію MTProto та аудіоплеєра? Зателефонуй на +380501112233 якщо що.".into(),
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

    // 1. Test list_chats
    let list_req = AgentActionRequest {
        action_type: "list_chats".into(),
        chat_id: None,
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
        api_key: None,
        provider: None,
        strict_privacy: None,
        raw_ai_response: None,
    };
    let list_res = agent.execute_action(list_req).await;
    assert!(list_res.success);
    assert!(list_res.message.contains("1 активних"));
    assert!(list_res.data.unwrap().contains("Команда Yumigram"));

    // 2. Test read_chat
    let read_req = AgentActionRequest {
        action_type: "read_chat".into(),
        chat_id: Some(1001),
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: Some(10),
        api_key: None,
        provider: None,
        strict_privacy: None,
        raw_ai_response: None,
    };
    let read_res = agent.execute_action(read_req).await;
    assert!(read_res.success);
    assert!(read_res.data.unwrap().contains("інтеграцію MTProto"));

    // 3. Test intelligent summarization with Local ONNX / Privacy Guard
    let sum_req = AgentActionRequest {
        action_type: "summarize".into(),
        chat_id: Some(1001),
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
        api_key: None,
        provider: Some("local_onnx".into()),
        strict_privacy: Some(true),
        raw_ai_response: None,
    };
    let sum_res = agent.execute_action(sum_req).await;
    assert!(sum_res.success);
    let sum_data = sum_res.data.unwrap();
    assert!(sum_data.contains("Local ONNX"));
    assert!(sum_data.contains("privacy_protected"));
    // PII phone number MUST be redacted!
    assert!(!sum_data.contains("+380501112233"));

    // 4. Test AI Settings
    let set_ai_req = AgentActionRequest {
        action_type: "set_ai_settings".into(),
        chat_id: None,
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
        api_key: Some("test_gemini_key_12345".into()),
        provider: Some("gemini".into()),
        strict_privacy: Some(true),
        raw_ai_response: None,
    };
    let set_ai_res = agent.execute_action(set_ai_req).await;
    assert!(set_ai_res.success);

    let get_ai_req = AgentActionRequest {
        action_type: "get_ai_settings".into(),
        chat_id: None,
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
        api_key: None,
        provider: None,
        strict_privacy: None,
        raw_ai_response: None,
    };
    let get_ai_res = agent.execute_action(get_ai_req).await;
    assert!(get_ai_res.success);
    let settings_str = get_ai_res.data.unwrap();
    assert!(settings_str.contains("test_gemini_key_12345"));
    assert!(settings_str.contains("gemini"));

    // 5. Test prepare_ai_summary
    let prep_req = AgentActionRequest {
        action_type: "prepare_ai_summary".into(),
        chat_id: Some(1001),
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
        api_key: None,
        provider: None,
        strict_privacy: Some(true),
        raw_ai_response: None,
    };
    let prep_res = agent.execute_action(prep_req).await;
    assert!(prep_res.success);
    let prep_payload = prep_res.data.unwrap();
    assert!(prep_payload.contains("generationConfig"));
    assert!(prep_payload.contains("[PHONE_REDACTED]"));

    // 6. Test parse_ai_summary
    let mock_gemini_resp = r#"{
      "candidates": [
        {
          "content": {
            "parts": [
              {
                "text": "```json\n{\n  \"chat_title\": \"Команда Yumigram\",\n  \"key_topics\": [\"Реліз\", \"MTProto\"],\n  \"unanswered_questions\": [\"Чи готові всі тести?\"],\n  \"action_items\": [\"Викласти нову версію ввечері\"],\n  \"summary_text\": \"Команда обговорила успішний стан розробки та домовилася про випуск релізу.\"\n}\n```"
              }
            ]
          }
        }
      ]
    }"#;
    let parse_req = AgentActionRequest {
        action_type: "parse_ai_summary".into(),
        chat_id: Some(1001),
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
        api_key: None,
        provider: None,
        strict_privacy: None,
        raw_ai_response: Some(mock_gemini_resp.into()),
    };
    let parse_res = agent.execute_action(parse_req).await;
    assert!(parse_res.success);
    let parsed_data = parse_res.data.unwrap();
    assert!(parsed_data.contains("Викласти нову версію"));
    assert!(parsed_data.contains("Google Gemini"));

    // 7. Test draft_reply
    let draft_req = AgentActionRequest {
        action_type: "draft_reply".into(),
        chat_id: Some(1001),
        text: Some("погодитися викласти сьогодні ввечері".into()),
        bio: None,
        first_name: None,
        last_name: None,
        query: None,
        limit: None,
        api_key: None,
        provider: None,
        strict_privacy: None,
        raw_ai_response: None,
    };
    let draft_res = agent.execute_action(draft_req).await;
    assert!(draft_res.success);
    assert!(draft_res.data.unwrap().contains("Домовилися!"));

    // 8. Test search_messages
    let search_req = AgentActionRequest {
        action_type: "search_messages".into(),
        chat_id: None,
        text: None,
        bio: None,
        first_name: None,
        last_name: None,
        query: Some("аудіоплеєра".into()),
        limit: Some(5),
        api_key: None,
        provider: None,
        strict_privacy: None,
        raw_ai_response: None,
    };
    let search_res = agent.execute_action(search_req).await;
    assert!(search_res.success);
    assert!(search_res.message.contains("1 повідомлень"));

    // 9. Test update_profile
    let profile_req = AgentActionRequest {
        action_type: "update_profile".into(),
        chat_id: None,
        text: None,
        bio: Some("Building Yumigram with AI and Pixel OS Material Design".into()),
        first_name: Some("Yumi".into()),
        last_name: Some("Developer".into()),
        query: None,
        limit: None,
        api_key: None,
        provider: None,
        strict_privacy: None,
        raw_ai_response: None,
    };
    let prof_res = agent.execute_action(profile_req).await;
    assert!(prof_res.success);
    assert_eq!(db.get_setting("profile_first_name").unwrap(), Some("Yumi".into()));
    assert_eq!(db.get_setting("profile_bio").unwrap(), Some("Building Yumigram with AI and Pixel OS Material Design".into()));
}
