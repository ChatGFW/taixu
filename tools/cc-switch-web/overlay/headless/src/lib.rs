use std::{any::{Any, TypeId}, collections::HashMap, future::Future, pin::Pin, sync::{Arc, RwLock}};
pub use taixu_command::command;
pub use serde_json;
pub use inventory;
static EVENT_BUS: std::sync::OnceLock<tokio::sync::broadcast::Sender<serde_json::Value>> = std::sync::OnceLock::new();
pub type CommandFuture = Pin<Box<dyn Future<Output = Result<serde_json::Value, serde_json::Value>> + Send>>;
pub struct Command { pub name: &'static str, pub run: fn(AppHandle, serde_json::Value) -> CommandFuture }
inventory::collect!(Command);
#[derive(Clone)]
pub struct AppHandle {
    states: Arc<RwLock<HashMap<TypeId, Arc<dyn Any + Send + Sync>>>>,
    pub events: tokio::sync::broadcast::Sender<serde_json::Value>,
}
impl Default for AppHandle {
    fn default() -> Self {
        Self { states: Default::default(), events: EVENT_BUS.get_or_init(|| tokio::sync::broadcast::channel(256).0).clone() }
    }
}
pub struct State<'a, T: Send + Sync + 'static>(Arc<T>, std::marker::PhantomData<&'a T>);
impl<T: Send + Sync + 'static> State<'_, T> { pub fn inner(&self) -> &T { &self.0 } }
impl<T: Send + Sync + 'static> std::ops::Deref for State<'_, T> { type Target = T; fn deref(&self) -> &T { &self.0 } }
pub trait Manager {
    fn try_state<T: Send + Sync + 'static>(&self) -> Option<State<'_, T>>;
    fn state<T: Send + Sync + 'static>(&self) -> State<'_, T> { self.try_state().expect("Missing managed state") }
}
impl Manager for AppHandle {
    fn try_state<T: Send + Sync + 'static>(&self) -> Option<State<'_, T>> {
        self.states.read().ok()?.get(&TypeId::of::<T>())?.clone().downcast().ok().map(|state| State(state, std::marker::PhantomData))
    }
}
impl AppHandle {
    pub fn manage<T: Send + Sync + 'static>(&self, state: T) { self.states.write().unwrap().insert(TypeId::of::<T>(), Arc::new(state)); }
    pub fn try_state<T: Send + Sync + 'static>(&self) -> Option<State<'_, T>> { Manager::try_state(self) }
}
pub trait Emitter {
    fn emit<S: serde::Serialize + Clone>(&self, event: &str, payload: S) -> Result<(), String>;
}
impl Emitter for AppHandle {
    fn emit<S: serde::Serialize + Clone>(&self, event: &str, payload: S) -> Result<(), String> {
        let value = serde_json::to_value(payload).map_err(|e| e.to_string())?;
        let _ = self.events.send(serde_json::json!({"event":event,"payload":value}));
        Ok(())
    }
}
pub struct Window;
pub mod async_runtime {
    pub use tokio::task::{spawn, spawn_blocking};
    pub fn handle() -> tokio::runtime::Handle { tokio::runtime::Handle::current() }
    pub fn block_on<F: std::future::Future>(future: F) -> F::Output { handle().block_on(future) }
}
pub mod ipc {
    pub type InvokeResponseBody = serde_json::Value;
    pub trait IpcResponse: serde::Serialize {
        fn body(&self) -> Result<InvokeResponseBody, serde_json::Error> { serde_json::to_value(self) }
    }
    impl<T: serde::Serialize> IpcResponse for T {}
    #[derive(serde::Serialize)]
    #[serde(transparent)]
    pub struct Response(Vec<u8>);
    impl Response { pub fn new(bytes: Vec<u8>) -> Self { Self(bytes) } }
    #[derive(serde::Deserialize)]
    #[serde(transparent, bound = "")]
    pub struct Channel<T = InvokeResponseBody> { id: String, #[serde(skip)] marker: std::marker::PhantomData<T> }
    impl<T: serde::Serialize> Channel<T> {
        pub fn send(&self, value: T) -> Result<(), String> {
            let bus = super::EVENT_BUS.get().ok_or("Event transport unavailable")?;
            bus.send(serde_json::json!({"event": self.id, "payload":value})).map(|_| ()).map_err(|_| "Event listener disconnected".into())
        }
    }
}
