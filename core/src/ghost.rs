use crate::models::GhostConfig;
use std::sync::{Arc, RwLock};

#[derive(Clone)]
pub struct GhostManager {
    config: Arc<RwLock<GhostConfig>>,
}

impl GhostManager {
    pub fn new(config: GhostConfig) -> Self {
        Self {
            config: Arc::new(RwLock::new(config)),
        }
    }

    pub fn update_config(&self, new_config: GhostConfig) {
        let mut cfg = self.config.write().unwrap();
        *cfg = new_config;
    }

    pub fn get_config(&self) -> GhostConfig {
        self.config.read().unwrap().clone()
    }

    /// Determines if reading messages should be hidden from Telegram servers
    pub fn should_suppress_read_receipt(&self) -> bool {
        self.config.read().unwrap().hide_read
    }

    /// Determines if "typing..." status should be hidden
    pub fn should_suppress_typing(&self) -> bool {
        self.config.read().unwrap().hide_typing
    }

    /// Determines if online status updates should be suppressed
    pub fn should_suppress_online(&self) -> bool {
        self.config.read().unwrap().hide_online
    }
}
