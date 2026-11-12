use tauri::{command, AppHandle, State, Emitter};
use serde_json::{json, Value};

#[command]
fn transport_echo(state: State<'_, usize>, request_id: String, optional_value: Option<bool>) -> Result<Value, String> {
    Ok(json!({"state":*state,"id":request_id,"optional":optional_value}))
}

#[command]
async fn transport_error() -> Result<(), Value> {
    Err(json!({"kind":"conflict","revision":2}))
}

#[test]
fn injection_validation_and_structured_errors() {
    let runtime = tokio::runtime::Builder::new_multi_thread().enable_all().build().unwrap();
    runtime.block_on(async {
        let app = AppHandle::default();
        app.manage(7usize);
        let command = tauri::inventory::iter::<tauri::Command>.into_iter().find(|c| c.name == "transport_echo").unwrap();
        assert_eq!((command.run)(app.clone(), json!({"requestId":"r1"})).await.unwrap(), json!({"state":7,"id":"r1","optional":null}));
        assert!((command.run)(app.clone(), json!({"requestId":17})).await.is_err());
        let error = tauri::inventory::iter::<tauri::Command>.into_iter().find(|c| c.name == "transport_error").unwrap();
        assert_eq!((error.run)(app.clone(), json!({})).await.unwrap_err(), json!({"kind":"conflict","revision":2}));
        let mut events = app.events.subscribe();
        app.emit("provider-switched", json!({"id":"test"})).unwrap();
        assert_eq!(events.recv().await.unwrap(), json!({"event":"provider-switched","payload":{"id":"test"}}));
        let channel: tauri::ipc::Channel<Value> = serde_json::from_value(json!("channel:test")).unwrap();
        channel.send(json!({"part":1})).unwrap();
        assert_eq!(events.recv().await.unwrap(), json!({"event":"channel:test","payload":{"part":1}}));
    });
}
