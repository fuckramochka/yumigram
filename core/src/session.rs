use grammers_session::types::{ChannelState, DcOption, PeerId, PeerInfo, UpdateState, UpdatesState};
use grammers_session::{BoxFuture, Session, SessionData};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::RwLock;

#[derive(serde::Serialize, serde::Deserialize)]
struct PersistentSession {
    home_dc: i32,
    dc_options: HashMap<i32, DcOption>,
    peer_infos: HashMap<PeerId, PeerInfo>,
    updates_state: UpdatesState,
}

pub struct FileSession {
    path: PathBuf,
    state: RwLock<PersistentSession>,
}

#[derive(Debug)]
pub struct SessionError;

impl std::fmt::Display for SessionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Session lock error")
    }
}

impl std::error::Error for SessionError {}

impl FileSession {
    pub fn load_or_create<P: AsRef<Path>>(path: P) -> Self {
        let p = path.as_ref().to_path_buf();
        let state = if p.exists() {
            match std::fs::read_to_string(&p) {
                Ok(content) => serde_json::from_str::<PersistentSession>(&content).unwrap_or_else(|_| {
                    let d = SessionData::default();
                    PersistentSession {
                        home_dc: d.home_dc,
                        dc_options: d.dc_options,
                        peer_infos: d.peer_infos,
                        updates_state: d.updates_state,
                    }
                }),
                Err(_) => {
                    let d = SessionData::default();
                    PersistentSession {
                        home_dc: d.home_dc,
                        dc_options: d.dc_options,
                        peer_infos: d.peer_infos,
                        updates_state: d.updates_state,
                    }
                }
            }
        } else {
            let d = SessionData::default();
            PersistentSession {
                home_dc: d.home_dc,
                dc_options: d.dc_options,
                peer_infos: d.peer_infos,
                updates_state: d.updates_state,
            }
        };

        Self {
            path: p,
            state: RwLock::new(state),
        }
    }

    fn persist(&self) {
        if let Ok(guard) = self.state.read() {
            if let Ok(json) = serde_json::to_string(&*guard) {
                if let Some(parent) = self.path.parent() {
                    let _ = std::fs::create_dir_all(parent);
                }
                let _ = std::fs::write(&self.path, json);
            }
        }
    }
}

impl Session for FileSession {
    type Error = SessionError;

    fn home_dc_id(&self) -> Result<i32, Self::Error> {
        let guard = self.state.read().map_err(|_| SessionError)?;
        Ok(guard.home_dc)
    }

    fn set_home_dc_id(&self, dc_id: i32) -> BoxFuture<'_, Result<(), Self::Error>> {
        Box::pin(async move {
            {
                let mut guard = self.state.write().map_err(|_| SessionError)?;
                guard.home_dc = dc_id;
            }
            self.persist();
            Ok(())
        })
    }

    fn dc_option(&self, dc_id: i32) -> Result<Option<DcOption>, Self::Error> {
        let guard = self.state.read().map_err(|_| SessionError)?;
        Ok(guard.dc_options.get(&dc_id).cloned())
    }

    fn set_dc_option(&self, dc_option: &DcOption) -> BoxFuture<'_, Result<(), Self::Error>> {
        let opt = dc_option.clone();
        Box::pin(async move {
            {
                let mut guard = self.state.write().map_err(|_| SessionError)?;
                guard.dc_options.insert(opt.id, opt);
            }
            self.persist();
            Ok(())
        })
    }

    fn peer(&self, peer: PeerId) -> BoxFuture<'_, Result<Option<PeerInfo>, Self::Error>> {
        Box::pin(async move {
            let guard = self.state.read().map_err(|_| SessionError)?;
            Ok(guard.peer_infos.get(&peer).cloned())
        })
    }

    fn cache_peer(&self, peer: PeerInfo) -> BoxFuture<'_, Result<(), Self::Error>> {
        Box::pin(async move {
            {
                let mut guard = self.state.write().map_err(|_| SessionError)?;
                let id = peer.id();
                guard
                    .peer_infos
                    .entry(id)
                    .or_insert_with(|| peer.clone())
                    .extend_info(&peer);
            }
            self.persist();
            Ok(())
        })
    }

    fn updates_state(&self) -> BoxFuture<'_, Result<UpdatesState, Self::Error>> {
        Box::pin(async move {
            let guard = self.state.read().map_err(|_| SessionError)?;
            Ok(guard.updates_state.clone())
        })
    }

    fn set_update_state(&self, update: UpdateState) -> BoxFuture<'_, Result<(), Self::Error>> {
        Box::pin(async move {
            {
                let mut guard = self.state.write().map_err(|_| SessionError)?;
                match update {
                    UpdateState::All(updates_state) => {
                        guard.updates_state = updates_state;
                    }
                    UpdateState::Primary { pts, date, seq } => {
                        guard.updates_state.pts = pts;
                        guard.updates_state.date = date;
                        guard.updates_state.seq = seq;
                    }
                    UpdateState::Secondary { qts } => {
                        guard.updates_state.qts = qts;
                    }
                    UpdateState::Channel { id, pts } => {
                        guard.updates_state.channels.retain(|c| c.id != id);
                        guard.updates_state.channels.push(ChannelState { id, pts });
                    }
                }
            }
            self.persist();
            Ok(())
        })
    }
}
