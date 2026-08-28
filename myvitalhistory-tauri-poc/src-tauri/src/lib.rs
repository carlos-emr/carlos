use serde::Serialize;

#[derive(Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeInfo {
    platform: String,
    architecture: String,
    app_version: String,
    message: String,
}

fn current_runtime_info() -> RuntimeInfo {
    RuntimeInfo {
        platform: std::env::consts::OS.to_owned(),
        architecture: std::env::consts::ARCH.to_owned(),
        app_version: env!("CARGO_PKG_VERSION").to_owned(),
        message: "Hello from the Tauri Rust boundary".to_owned(),
    }
}

#[tauri::command]
fn runtime_info() -> RuntimeInfo {
    current_runtime_info()
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![runtime_info])
        .run(tauri::generate_context!())
        .expect("error while running MyVitalHistory Tauri evaluation");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runtime_info_contains_only_non_sensitive_build_data() {
        let info = current_runtime_info();

        assert_eq!(info.platform, std::env::consts::OS);
        assert_eq!(info.architecture, std::env::consts::ARCH);
        assert_eq!(info.app_version, env!("CARGO_PKG_VERSION"));
        assert_eq!(info.message, "Hello from the Tauri Rust boundary");
    }
}
