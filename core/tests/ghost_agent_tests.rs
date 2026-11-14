use yumigram_core::ghost::GhostManager;
use yumigram_core::models::GhostConfig;
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

#[tokio::test]
async fn test_agent_executor_actions() {
    let db = Arc::new(LocalDatabase::new(":memory:").expect("DB failed"));
    let agent = AgentExecutor::new(db);

    // Test bio update action
    let bio_req = AgentActionRequest {
        action_type: "update_bio".into(),
        chat_id: None,
        text: None,
        bio: Some("Building Yumigram with AI".into()),
    };
    let res = agent.execute_action(bio_req).await;
    assert!(res.success);
    assert!(res.message.contains("Building Yumigram with AI"));

    // Test send message action validation
    let send_req = AgentActionRequest {
        action_type: "send_message".into(),
        chat_id: Some(12345),
        text: Some("Automated greeting".into()),
        bio: None,
    };
    let send_res = agent.execute_action(send_req).await;
    assert!(send_res.success);
}
