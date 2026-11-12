// The browser receives business events; native tray updates have no Web UI.
pub fn refresh_tray_menu(_: &tauri::AppHandle) {}
pub fn schedule_tray_refresh(_: &tauri::AppHandle) {}
pub fn apply_tray_style(_: &tauri::AppHandle) {}
pub fn schedule_tray_status_check(_: &tauri::AppHandle) {}
pub fn tray_language() -> String {
    crate::settings::get_settings().language.unwrap_or_else(|| "en".into())
}
