// Evaluation installers use debug builds, but should still launch as a GUI app.
#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]

fn main() {
    mycarlos_tauri_poc_lib::run();
}
