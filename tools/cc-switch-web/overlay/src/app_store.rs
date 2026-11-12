use std::path::PathBuf;
pub fn get_app_config_dir_override() -> Option<PathBuf> {
    std::env::var_os("CC_SWITCH_DATA_DIR").map(PathBuf::from)
}
pub fn refresh_app_config_dir_override(_: &tauri::AppHandle) -> Option<PathBuf> {
    get_app_config_dir_override()
}
