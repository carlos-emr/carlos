mod vault;

use serde::{Deserialize, Serialize};
use std::{
    sync::Arc,
    time::{SystemTime, UNIX_EPOCH},
};
use tauri::{Emitter, Manager, State};
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_fs::{FsExt, OpenOptions};
use uuid::Uuid;
use vault::{ImportSource, VaultError, VaultSnapshot, VaultStatus, VaultStore};
use zeroize::Zeroize;

#[derive(Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeInfo {
    platform: String,
    architecture: String,
    app_version: String,
    message: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct PublicError {
    code: &'static str,
    message: &'static str,
}

type CommandResult<T> = Result<T, PublicError>;

impl From<VaultError> for PublicError {
    fn from(error: VaultError) -> Self {
        match error {
            VaultError::AlreadyExists => Self {
                code: "already_exists",
                message: "A vault already exists on this device.",
            },
            VaultError::Missing => Self {
                code: "missing",
                message: "No vault exists on this device.",
            },
            VaultError::Locked => Self {
                code: "locked",
                message: "Unlock the vault to continue.",
            },
            VaultError::WrongPassphrase => Self {
                code: "wrong_passphrase",
                message: "That passphrase did not unlock the vault.",
            },
            VaultError::Corrupt => Self {
                code: "corrupt",
                message: "The vault is damaged or incomplete. No data was changed.",
            },
            VaultError::NotFound => Self {
                code: "not_found",
                message: "That item is no longer available.",
            },
            VaultError::Invalid => Self {
                code: "invalid",
                message: "Check the requested information and try again.",
            },
            VaultError::NoSpace => Self {
                code: "no_space",
                message: "There is not enough storage to complete this operation.",
            },
            VaultError::Storage => Self {
                code: "storage",
                message:
                    "The storage operation could not be completed. No partial import was kept.",
            },
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct CreateVaultRequest {
    passphrase: String,
    initial_profile_name: String,
}

#[derive(Deserialize)]
struct PassphraseRequest {
    passphrase: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ChangePassphraseRequest {
    current_passphrase: String,
    new_passphrase: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct NameRequest {
    display_name: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct FolderRequest {
    profile_id: Uuid,
    parent_id: Option<Uuid>,
    name: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct UpdateFolderRequest {
    folder_id: Uuid,
    parent_id: Option<Uuid>,
    name: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct AssignFoldersRequest {
    record_id: Uuid,
    folder_ids: Vec<Uuid>,
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ImportRequest {
    profile_id: Uuid,
    folder_ids: Vec<Uuid>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ExportRequest {
    record_id: Uuid,
    suggested_name: String,
}

#[derive(Deserialize)]
struct ResetRequest {
    confirmation: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ImportProgress {
    job_id: Uuid,
    state: &'static str,
}

fn current_runtime_info() -> RuntimeInfo {
    RuntimeInfo {
        platform: std::env::consts::OS.to_owned(),
        architecture: std::env::consts::ARCH.to_owned(),
        app_version: env!("CARGO_PKG_VERSION").to_owned(),
        message: "Hello from the Tauri Rust boundary".to_owned(),
    }
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .try_into()
        .unwrap_or(u64::MAX)
}

#[tauri::command]
fn runtime_info() -> RuntimeInfo {
    current_runtime_info()
}

#[tauri::command]
fn vault_status(store: State<'_, Arc<VaultStore>>) -> VaultStatus {
    store.status()
}

#[tauri::command]
async fn vault_create(
    store: State<'_, Arc<VaultStore>>,
    mut request: CreateVaultRequest,
) -> CommandResult<VaultSnapshot> {
    let store = store.inner().clone();
    let result = tauri::async_runtime::spawn_blocking(move || {
        let result = store
            .create(&request.passphrase, &request.initial_profile_name, now_ms())
            .and_then(|_| store.snapshot());
        request.passphrase.zeroize();
        result
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?;
    result.map_err(Into::into)
}

#[tauri::command]
async fn vault_unlock(
    store: State<'_, Arc<VaultStore>>,
    mut request: PassphraseRequest,
) -> CommandResult<VaultSnapshot> {
    let store = store.inner().clone();
    let result = tauri::async_runtime::spawn_blocking(move || {
        let result = store
            .unlock(&request.passphrase)
            .and_then(|_| store.snapshot());
        request.passphrase.zeroize();
        result
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?;
    result.map_err(Into::into)
}

#[tauri::command]
fn vault_lock(store: State<'_, Arc<VaultStore>>) {
    store.lock();
}

#[tauri::command]
fn vault_snapshot(store: State<'_, Arc<VaultStore>>) -> CommandResult<VaultSnapshot> {
    store.snapshot().map_err(Into::into)
}

#[tauri::command]
async fn vault_change_passphrase(
    store: State<'_, Arc<VaultStore>>,
    mut request: ChangePassphraseRequest,
) -> CommandResult<()> {
    let store = store.inner().clone();
    let result = tauri::async_runtime::spawn_blocking(move || {
        let result = store.change_passphrase(&request.current_passphrase, &request.new_passphrase);
        request.current_passphrase.zeroize();
        request.new_passphrase.zeroize();
        result
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?;
    result.map_err(Into::into)
}

#[tauri::command]
fn vault_create_profile(
    store: State<'_, Arc<VaultStore>>,
    request: NameRequest,
) -> CommandResult<Uuid> {
    store
        .create_profile(&request.display_name, now_ms())
        .map_err(Into::into)
}

#[tauri::command]
fn vault_create_folder(
    store: State<'_, Arc<VaultStore>>,
    request: FolderRequest,
) -> CommandResult<Uuid> {
    store
        .create_folder(
            request.profile_id,
            request.parent_id,
            &request.name,
            now_ms(),
        )
        .map_err(Into::into)
}

#[tauri::command]
fn vault_update_folder(
    store: State<'_, Arc<VaultStore>>,
    request: UpdateFolderRequest,
) -> CommandResult<()> {
    store
        .update_folder(request.folder_id, request.parent_id, &request.name)
        .map_err(Into::into)
}

#[tauri::command]
fn vault_assign_folders(
    store: State<'_, Arc<VaultStore>>,
    request: AssignFoldersRequest,
) -> CommandResult<()> {
    store
        .assign_folders(request.record_id, request.folder_ids)
        .map_err(Into::into)
}

#[tauri::command]
async fn vault_import_begin(
    app: tauri::AppHandle,
    store: State<'_, Arc<VaultStore>>,
    request: ImportRequest,
) -> CommandResult<vault::ImportOutcome> {
    let picker_app = app.clone();
    let picked = tauri::async_runtime::spawn_blocking(move || {
        picker_app.dialog().file().blocking_pick_files()
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?;
    let Some(paths) = picked else {
        return Ok(vault::ImportOutcome {
            imported: Vec::new(),
            skipped_duplicates: Vec::new(),
        });
    };
    let job_id = Uuid::new_v4();
    let _ = app.emit(
        "vault-import-progress",
        ImportProgress {
            job_id,
            state: "reading",
        },
    );
    let mut sources = Vec::with_capacity(paths.len());
    for path in paths {
        let display_name = path.to_string();
        let mut options = OpenOptions::new();
        options.read(true);
        let file = app
            .fs()
            .open(path, options)
            .map_err(|_| PublicError::from(VaultError::Storage))?;
        sources.push(ImportSource {
            display_name,
            reader: Box::new(file),
        });
    }
    let store = store.inner().clone();
    let outcome = tauri::async_runtime::spawn_blocking(move || {
        store.import(request.profile_id, request.folder_ids, sources, now_ms())
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?
    .map_err(PublicError::from)?;
    let _ = app.emit(
        "vault-import-progress",
        ImportProgress {
            job_id,
            state: "complete",
        },
    );
    Ok(outcome)
}

#[tauri::command]
async fn vault_export_begin(
    app: tauri::AppHandle,
    store: State<'_, Arc<VaultStore>>,
    request: ExportRequest,
) -> CommandResult<bool> {
    let picker_app = app.clone();
    let suggested_name = request.suggested_name.clone();
    let destination = tauri::async_runtime::spawn_blocking(move || {
        picker_app
            .dialog()
            .file()
            .set_file_name(suggested_name)
            .blocking_save_file()
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?;
    let Some(destination) = destination else {
        return Ok(false);
    };
    let mut options = OpenOptions::new();
    options.write(true).create(true).truncate(true);
    let mut output = app
        .fs()
        .open(destination, options)
        .map_err(|_| PublicError::from(VaultError::Storage))?;
    let store = store.inner().clone();
    tauri::async_runtime::spawn_blocking(move || {
        let result = store.export(request.record_id, &mut output);
        if result.is_err() {
            let _ = output.set_len(0);
        }
        result
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?
    .map_err(PublicError::from)?;
    Ok(true)
}

#[tauri::command]
fn vault_reset(store: State<'_, Arc<VaultStore>>, request: ResetRequest) -> CommandResult<()> {
    if request.confirmation != "RESET MYCARLOS VAULT" {
        return Err(PublicError::from(VaultError::Invalid));
    }
    store.reset().map_err(Into::into)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .setup(|app| {
            app.manage(Arc::new(VaultStore::new(
                app.path().app_data_dir()?.join("vault-v1"),
            )));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            runtime_info,
            vault_status,
            vault_create,
            vault_unlock,
            vault_lock,
            vault_snapshot,
            vault_change_passphrase,
            vault_create_profile,
            vault_create_folder,
            vault_update_folder,
            vault_assign_folders,
            vault_import_begin,
            vault_export_begin,
            vault_reset
        ])
        .run(tauri::generate_context!())
        .expect("error while running myCarlos");
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
    #[test]
    fn public_errors_do_not_expose_internal_details() {
        let error = PublicError::from(VaultError::Storage);
        assert_eq!(error.code, "storage");
        assert!(!error.message.contains('/'));
    }
}
