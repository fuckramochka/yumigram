use std::sync::Arc;
use yumigram_core::db::LocalDatabase;
use yumigram_core::plugins::{PluginManager, PluginPermission};

#[test]
fn test_plugin_registration_and_listing() {
    let db = Arc::new(LocalDatabase::new(":memory:").expect("Failed to create memory DB"));
    let manager = PluginManager::new(db);

    let list = manager.list_plugins();
    assert_eq!(list.len(), 1);
    assert_eq!(list[0].manifest.id, "org.yumigram.plugin.kaomoji");
    assert_eq!(list[0].manifest.name, "Kaomoji & Shrug Expander");
    assert!(list[0].is_enabled);
    assert!(list[0].manifest.permissions.contains(&PluginPermission::ModifyOutgoing));
}

#[test]
fn test_plugin_message_transformation_hook() {
    let db = Arc::new(LocalDatabase::new(":memory:").expect("Failed to create memory DB"));
    let manager = PluginManager::new(db);

    let original = "I don't know :shrug: and look at this :lenny:".to_string();
    let transformed = manager.process_outgoing_message(12345, original);

    assert_eq!(transformed, "I don't know ¯\\_(ツ)_/¯ and look at this ( ͡° ͜ʖ ͡°)");
}

#[test]
fn test_plugin_disable_toggle() {
    let db = Arc::new(LocalDatabase::new(":memory:").expect("Failed to create memory DB"));
    let manager = PluginManager::new(db.clone());

    // Disable plugin
    manager.set_plugin_enabled("org.yumigram.plugin.kaomoji", false).expect("Failed to disable");

    let list = manager.list_plugins();
    assert!(!list[0].is_enabled);

    // When disabled, message transformation hook should NOT alter the text!
    let original = "Hello :shrug:".to_string();
    let unchanged = manager.process_outgoing_message(12345, original.clone());
    assert_eq!(unchanged, original);

    // Verify persisted in SQLite
    let saved_setting = db.get_setting("plugin_enabled_org.yumigram.plugin.kaomoji").unwrap();
    assert_eq!(saved_setting, Some("false".into()));
}
