use std::{path::PathBuf, sync::{Arc, RwLock}};
use axum::{Router, routing::{get, post}, extract::{State, Path, DefaultBodyLimit}, Json, middleware::{self, Next}, response::{Response, IntoResponse, Sse, sse::{Event, KeepAlive}, Redirect}, http::{header, HeaderMap, Request, StatusCode}};
use base64::Engine;
use tauri::Manager;
use std::future::IntoFuture;

/// The only page served without a password. It carries no application data.
const PASSWORD_PAGE: &str = include_str!("web_password.html");

#[derive(Clone)]
struct WebState {
    app: tauri::AppHandle,
    authority: String,
    password_path: PathBuf,
    /// The login the browser presents. `None` is first run: everything except the
    /// password endpoints stays closed until someone on loopback sets it.
    password: Arc<RwLock<Option<String>>>,
}

fn credential(password: &str) -> String {
    format!("Basic {}", base64::engine::general_purpose::STANDARD.encode(format!("admin:{password}")))
}

fn equal(left: &[u8], right: &[u8]) -> bool {
    use subtle::ConstantTimeEq;
    bool::from(left.ct_eq(right))
}

fn authorized(state: &WebState, headers: &HeaderMap) -> bool {
    let guard = state.password.read().unwrap();
    let Some(password) = guard.as_deref() else { return false };
    equal(headers.get(header::AUTHORIZATION).map(|value| value.as_bytes()).unwrap_or_default(), credential(password).as_bytes())
}

/// Write through a sibling file so a crash can never leave a truncated password.
fn store_password(path: &std::path::Path, password: &str) -> std::io::Result<()> {
    use std::io::Write;
    use std::os::unix::fs::PermissionsExt;
    let temporary = path.with_extension("tmp");
    let mut file = std::fs::OpenOptions::new().write(true).create(true).truncate(true).open(&temporary)?;
    file.set_permissions(std::fs::Permissions::from_mode(0o600))?;
    file.write_all(password.as_bytes())?;
    file.sync_all()?;
    drop(file);
    std::fs::rename(&temporary, path)
}

async fn guard(State(state): State<WebState>, request: Request<axum::body::Body>, next: Next) -> Response {
    let path = request.uri().path().to_owned();
    let headers = request.headers();
    if headers.get("host").and_then(|v| v.to_str().ok()) != Some(state.authority.as_str()) {
        return StatusCode::FORBIDDEN.into_response();
    }
    if let Some(origin) = headers.get("origin") {
        if origin.to_str().ok() != Some(format!("http://{}", state.authority).as_str()) { return StatusCode::FORBIDDEN.into_response(); }
    }
    // The password endpoints authenticate themselves; every other path never does.
    if path == "/password" || path == "/api/password" { return next.run(request).await; }
    if state.password.read().unwrap().is_none() {
        return if path.starts_with("/api/") {
            (StatusCode::FORBIDDEN, Json(serde_json::json!({"error":"尚未设置 Web 管理密码，请先打开 /password"}))).into_response()
        } else {
            Redirect::to("/password").into_response()
        };
    }
    if !authorized(&state, request.headers()) {
        return (StatusCode::UNAUTHORIZED, [("www-authenticate", "Basic realm=\"CC Switch\"")]).into_response();
    }
    next.run(request).await
}

async fn password_page() -> Response {
    ([(header::CONTENT_TYPE, "text/html; charset=utf-8"), (header::CACHE_CONTROL, "no-store")], PASSWORD_PAGE).into_response()
}

async fn password_status(State(state): State<WebState>, headers: HeaderMap) -> Response {
    let stored = state.password.read().unwrap().clone();
    let authorized = authorized(&state, &headers);
    let body = serde_json::json!({
        "configured": stored.is_some(),
        "authorized": authorized,
        // The password already travels in the Authorization header of this request.
        "password": if authorized { stored } else { None },
    });
    // Never let a browser or proxy keep a response that can carry the password.
    ([(header::CACHE_CONTROL, "no-store")], Json(body)).into_response()
}

#[derive(serde::Deserialize)]
struct PasswordUpdate { password: String, #[serde(default)] current: String }

fn invalid(message: &str) -> Response {
    (StatusCode::BAD_REQUEST, Json(serde_json::json!({"error":message}))).into_response()
}

async fn password_update(State(state): State<WebState>, headers: HeaderMap, Json(body): Json<PasswordUpdate>) -> Response {
    let next = body.password.trim().to_owned();
    if next.len() < 8 { return invalid("密码至少 8 位"); }
    if next.len() > 128 { return invalid("密码最多 128 位"); }
    if !next.bytes().all(|byte| byte.is_ascii_graphic()) { return invalid("密码只能包含可见的 ASCII 字符"); }
    if let Some(existing) = state.password.read().unwrap().clone() {
        // Either this browser already authenticated, or the caller proves the old password.
        if !authorized(&state, &headers) && !equal(body.current.trim().as_bytes(), existing.as_bytes()) {
            return (StatusCode::UNAUTHORIZED, Json(serde_json::json!({"error":"当前密码不正确"}))).into_response();
        }
    }
    if let Err(error) = store_password(&state.password_path, &next) {
        return (StatusCode::INTERNAL_SERVER_ERROR, Json(serde_json::json!({"error":error.to_string()}))).into_response();
    }
    *state.password.write().unwrap() = Some(next);
    Json(serde_json::json!({"ok": true})).into_response()
}

async fn invoke(State(state): State<WebState>, Path(name): Path<String>, Json(args): Json<serde_json::Value>) -> Response {
    if name == "get_home_dir" { return Json(serde_json::json!({"result":crate::config::get_home_dir()})).into_response(); }
    static ALLOWED: std::sync::OnceLock<Vec<String>> = std::sync::OnceLock::new();
    if !ALLOWED.get_or_init(|| serde_json::from_str(include_str!("web_commands.json")).expect("Pinned command list")).contains(&name) {
        return StatusCode::NOT_FOUND.into_response();
    }
    let Some(command) = tauri::inventory::iter::<tauri::Command>.into_iter().find(|c| c.name == name) else {
        return (StatusCode::NOT_FOUND, Json(serde_json::json!({"error":"Unknown command"}))).into_response();
    };
    match (command.run)(state.app, args).await {
        Ok(value) => Json(serde_json::json!({"result": value})).into_response(),
        Err(error) => (StatusCode::BAD_REQUEST, Json(serde_json::json!({"error":error}))).into_response(),
    }
}

async fn events(State(state): State<WebState>) -> impl IntoResponse {
    use futures::StreamExt;
    let stream = tokio_stream::wrappers::BroadcastStream::new(state.app.events.subscribe()).map(|event| {
        Ok::<_, std::convert::Infallible>(Event::default().data(match event {
            Ok(value) => value.to_string(),
            Err(_) => serde_json::json!({"event":"transport-resync-required","payload":null}).to_string(),
        }))
    });
    Sse::new(stream).keep_alive(KeepAlive::default())
}

pub async fn run() -> Result<(), Box<dyn std::error::Error>> {
    env_logger::init();
    // Match the desktop bootstrap: both TLS backends occur in the dependency
    // graph, so rustls must not try to select an ambiguous default lazily.
    let _ = rustls::crypto::ring::default_provider().install_default();
    let port: u16 = std::env::var("CC_SWITCH_PORT").unwrap_or_else(|_| "19870".into()).parse()?;
    if port == 0 { return Err("Port must be nonzero".into()); }
    let authority = format!("127.0.0.1:{port}");
    // Claim the port before startup recovery can touch CLI configuration.
    let listener = tokio::net::TcpListener::bind(&authority).await?;
    let data = crate::config::get_app_config_dir();
    std::fs::create_dir_all(&data)?;
    use std::os::unix::{fs::OpenOptionsExt, io::AsRawFd};
    let instance_lock = std::fs::OpenOptions::new().write(true).create(true).mode(0o600).open(data.join("web-instance.lock"))?;
    // The descriptor stays owned until shutdown; never unlock another instance.
    if unsafe { libc::flock(instance_lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        return Err("This data directory is already in use".into());
    }
    let password_path = data.join("web_password");
    // A missing file is first run, not a failure: the panel creates its own login
    // instead of writing a secret no one can read.
    let password = match std::fs::read_to_string(&password_path) {
        Ok(value) => {
            let value = value.trim().to_owned();
            if value.is_empty() { return Err("Empty web password; delete the file to set a new one".into()); }
            Some(value)
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
        Err(error) => return Err(error.into()),
    };
    let configured = password.is_some();
    let app = tauri::AppHandle::default();
    let state = crate::AppState::new(Arc::new(crate::Database::init()?));
    state.proxy_service.set_app_handle(app.clone());
    let startup_state = state.clone();
    tokio::task::spawn_blocking(move || crate::web_startup::initialize(&startup_state)).await?;
    crate::usage_events::init(app.clone());
    crate::proxy::http_client::init(state.db.get_global_proxy_url()?.as_deref())?;
    crate::services::provider::ProviderService::scrub_leaked_gemini_common_config(&state).await?;
    let common_state = state.clone();
    tokio::task::spawn_blocking(move || crate::web_startup::initialize_common_config_snippets(&common_state)).await?;
    crate::mode::controller::startup(&state).await;
    app.manage(crate::commands::CodexOAuthState(state.codex_oauth_manager.clone()));
    app.manage(state);
    app.manage(crate::services::kimi_oauth::KimiOAuthManager::default());
    app.manage(crate::commands::skill::SkillServiceState(Arc::new(crate::SkillService::new())));
    app.manage(crate::commands::CopilotAuthState(Arc::new(tokio::sync::RwLock::new(crate::proxy::providers::copilot_auth::CopilotAuthManager::new(data.clone())))));
    app.manage(crate::commands::XaiOAuthState(Arc::new(tokio::sync::RwLock::new(crate::proxy::providers::xai_oauth_auth::XaiOAuthManager::new(data.clone())))));
    app.manage(crate::commands::AgyAccountState(Arc::new(crate::services::agy_account::AgyAccountManager::new(data.clone()))));
    app.manage(crate::commands::ClaudeAccountState(Arc::new(crate::services::claude_account::ClaudeAccountManager::new(data))));
    let web = WebState { app, authority: authority.clone(), password_path: password_path.clone(), password: Arc::new(RwLock::new(password)) };
    let shutdown_app = web.app.clone();
    let ui = PathBuf::from(std::env::var("CC_SWITCH_UI_DIR").unwrap_or_else(|_| "ui".into()));
    if !ui.join("index.html").is_file() { return Err("Missing Web UI".into()); }
    let router = Router::new().route("/api/invoke/:name", post(invoke)).route("/api/events", get(events))
        .route("/password", get(password_page)).route("/api/password", get(password_status).post(password_update))
        .fallback_service(tower_http::services::ServeDir::new(ui))
        .layer(DefaultBodyLimit::max(4 * 1024 * 1024))
        .layer(middleware::from_fn_with_state(web.clone(), guard)).with_state(web);
    match configured {
        true => println!("CC Switch 4.0.30 Web: http://{authority}; user: admin; password file: {}", password_path.display()),
        false => println!("CC Switch 4.0.30 Web: http://{authority}; no password set; open http://{authority}/password to create one; user: admin"),
    }
    let mut terminate = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())?;
    let mut hangup = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::hangup())?;
    tokio::select! {
        result = axum::serve(listener, router).into_future() => { result?; },
        _ = async { tokio::select! { _ = terminate.recv() => {}, _ = hangup.recv() => {}, _ = tokio::signal::ctrl_c() => {} } } => {
            if let Some(state) = shutdown_app.try_state::<crate::AppState>() {
                if tokio::time::timeout(std::time::Duration::from_secs(8), crate::mode::controller::detach_all(state.inner())).await.is_err() {
                    log::warn!("Shutdown cleanup timed out; startup recovery will retry");
                }
            }
        }
    }
    Ok(())
}
