use crate::db::LocalDatabase;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::{Arc, RwLock};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum PluginPermission {
    ReadMessages,
    SendMessages,
    ModifyOutgoing,
    AccessMedia,
    NetworkAccess,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginManifest {
    pub id: String,
    pub name: String,
    pub version: String,
    pub author: String,
    pub description: String,
    pub min_core_version: String,
    pub permissions: Vec<PluginPermission>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginState {
    pub manifest: PluginManifest,
    pub is_enabled: bool,
}

pub trait YumigramPlugin: Send + Sync {
    fn manifest(&self) -> PluginManifest;
    fn on_enable(&self) -> Result<(), String> {
        Ok(())
    }
    fn on_disable(&self) -> Result<(), String> {
        Ok(())
    }
    /// Intercepts and transforms an outgoing message before sending over MTProto
    fn on_before_send_message(&self, _chat_id: i64, text: String) -> String {
        text
    }
}

/// Sample built-in production plugin: Shrug & Kaomoji Text Expander
pub struct KaomojiExpanderPlugin;

impl YumigramPlugin for KaomojiExpanderPlugin {
    fn manifest(&self) -> PluginManifest {
        PluginManifest {
            id: "org.yumigram.plugin.kaomoji".into(),
            name: "Kaomoji & Shrug Expander".into(),
            version: "1.0.0".into(),
            author: "Yumigram Team".into(),
            description: "Автоматично замінює :shrug: на ¯\\_(ツ)_/¯ та :lenny: на ( ͡° ͜ʖ ͡°)".into(),
            min_core_version: "0.1.0".into(),
            permissions: vec![PluginPermission::ModifyOutgoing],
        }
    }

    fn on_before_send_message(&self, _chat_id: i64, text: String) -> String {
        text.replace(":shrug:", "¯\\_(ツ)_/¯")
            .replace(":lenny:", "( ͡° ͜ʖ ͡°)")
            .replace(":tableflip:", "(╯°□°)╯︵ ┻━┻")
            .replace(":unflip:", "┬─┬ノ( º _ ºノ)")
    }
}

pub struct PluginManager {
    plugins: RwLock<HashMap<String, Arc<dyn YumigramPlugin>>>,
    enabled_states: RwLock<HashMap<String, bool>>,
    db: Arc<LocalDatabase>,
}

impl PluginManager {
    pub fn new(db: Arc<LocalDatabase>) -> Self {
        let mgr = Self {
            plugins: RwLock::new(HashMap::new()),
            enabled_states: RwLock::new(HashMap::new()),
            db,
        };

        // Register default plugins
        mgr.register_plugin(Arc::new(KaomojiExpanderPlugin));
        mgr
    }

    pub fn register_plugin(&self, plugin: Arc<dyn YumigramPlugin>) {
        let id = plugin.manifest().id;
        // Check saved enabled state in SQLite
        let is_enabled = self
            .db
            .get_setting(&format!("plugin_enabled_{}", id))
            .ok()
            .flatten()
            .map(|v| v == "true")
            .unwrap_or(true); // enabled by default

        self.plugins.write().unwrap().insert(id.clone(), plugin);
        self.enabled_states.write().unwrap().insert(id, is_enabled);
    }

    pub fn set_plugin_enabled(&self, id: &str, enabled: bool) -> Result<(), String> {
        let mut states = self.enabled_states.write().unwrap();
        if let Some(state) = states.get_mut(id) {
            *state = enabled;
            let _ = self.db.set_setting(&format!("plugin_enabled_{}", id), if enabled { "true" } else { "false" });
            Ok(())
        } else {
            Err(format!("Plugin '{}' not found", id))
        }
    }

    pub fn list_plugins(&self) -> Vec<PluginState> {
        let plugins = self.plugins.read().unwrap();
        let states = self.enabled_states.read().unwrap();

        plugins
            .iter()
            .map(|(id, p)| PluginState {
                manifest: p.manifest(),
                is_enabled: *states.get(id).unwrap_or(&false),
            })
            .collect()
    }

    /// Pipes outgoing message through all active plugins with `ModifyOutgoing` permission
    pub fn process_outgoing_message(&self, chat_id: i64, mut text: String) -> String {
        let plugins = self.plugins.read().unwrap();
        let states = self.enabled_states.read().unwrap();

        for (id, plugin) in plugins.iter() {
            if *states.get(id).unwrap_or(&false) {
                let has_permission = plugin
                    .manifest()
                    .permissions
                    .contains(&PluginPermission::ModifyOutgoing);
                if has_permission {
                    text = plugin.on_before_send_message(chat_id, text);
                }
            }
        }
        text
    }
}
