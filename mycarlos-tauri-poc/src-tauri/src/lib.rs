mod vault;

use serde::{Deserialize, Serialize};
#[cfg(desktop)]
use std::{fs, io, path::Path};
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
            VaultError::ImportBatchLimit => Self {
                code: "import_batch_limit",
                message: "Choose no more than 100 files in one import.",
            },
            VaultError::NoSpace => Self {
                code: "no_space",
                message: "There is not enough storage to complete this operation.",
            },
            VaultError::Storage => Self {
                code: "storage",
                message: "The storage operation could not be completed. Review the current vault state before retrying.",
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
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DeleteRecordRequest {
    record_id: Uuid,
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

#[cfg(desktop)]
fn open_regular_local_file(path: &Path) -> io::Result<fs::File> {
    let metadata = fs::symlink_metadata(path)?;
    if !metadata.file_type().is_file() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "selected path is not a regular file",
        ));
    }
    let mut options = fs::OpenOptions::new();
    options.read(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(libc::O_NOFOLLOW);
    }
    let file = options.open(path)?;
    if !file.metadata()?.file_type().is_file() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "selected handle is not a regular file",
        ));
    }
    Ok(file)
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
        picker_app
            .dialog()
            .file()
            .add_filter("PDF documents", &["pdf"])
            .blocking_pick_files()
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?;
    let Some(paths) = picked else {
        return Ok(vault::ImportOutcome {
            imported: Vec::new(),
            skipped_duplicates: Vec::new(),
        });
    };
    vault::validate_import_count(paths.len()).map_err(PublicError::from)?;
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
        #[cfg(desktop)]
        {
            if let Ok(local_path) = path.clone().into_path() {
                let file = open_regular_local_file(&local_path).map_err(|error| {
                    if error.kind() == io::ErrorKind::InvalidInput {
                        PublicError::from(VaultError::Invalid)
                    } else {
                        PublicError::from(VaultError::Storage)
                    }
                })?;
                sources.push(ImportSource {
                    display_name,
                    reader: Box::new(file),
                });
                continue;
            }
        }
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
    let suggested_name = store
        .export_name(request.record_id)
        .map_err(PublicError::from)?;
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

    if let Ok(destination_path) = destination.clone().into_path() {
        let store = store.inner().clone();
        tauri::async_runtime::spawn_blocking(move || {
            store.export_atomic(request.record_id, &destination_path)
        })
        .await
        .map_err(|_| PublicError::from(VaultError::Storage))?
        .map_err(PublicError::from)?;
        return Ok(true);
    }

    // Android content providers can return a content URI rather than a filesystem path, so an
    // atomic rename is unavailable. Authenticate the complete object before opening/truncating
    // the selected URI; the second pass streams the verified plaintext to the provider.
    let store_for_verify = store.inner().clone();
    tauri::async_runtime::spawn_blocking(move || {
        store_for_verify.export(request.record_id, std::io::sink())
    })
    .await
    .map_err(|_| PublicError::from(VaultError::Storage))?
    .map_err(PublicError::from)?;

    let mut options = OpenOptions::new();
    options.write(true).create(true).truncate(true);
    let mut output = app
        .fs()
        .open(destination, options)
        .map_err(|_| PublicError::from(VaultError::Storage))?;
    let store = store.inner().clone();
    tauri::async_runtime::spawn_blocking(move || store.export(request.record_id, &mut output))
        .await
        .map_err(|_| PublicError::from(VaultError::Storage))?
        .map_err(PublicError::from)?;
    Ok(true)
}

#[tauri::command]
fn vault_delete_record(
    store: State<'_, Arc<VaultStore>>,
    request: DeleteRecordRequest,
) -> CommandResult<()> {
    store.delete_record(request.record_id).map_err(Into::into)
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
            vault_delete_record,
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

    #[test]
    fn native_selection_limit_is_available_before_files_are_opened() {
        assert!(vault::validate_import_count(vault::MAX_IMPORT_FILES).is_ok());
        assert!(matches!(
            vault::validate_import_count(vault::MAX_IMPORT_FILES + 1),
            Err(VaultError::ImportBatchLimit)
        ));
    }

    #[cfg(unix)]
    #[test]
    fn local_import_rejects_symbolic_links() {
        use std::os::unix::fs::symlink;

        let temp = tempfile::tempdir().unwrap();
        let target = temp.path().join("record.pdf");
        let link = temp.path().join("selected.pdf");
        fs::write(&target, b"%PDF-synthetic").unwrap();
        symlink(&target, &link).unwrap();

        assert_eq!(
            open_regular_local_file(&link).unwrap_err().kind(),
            io::ErrorKind::InvalidInput
        );
    }

    #[test]
    fn production_webview_configuration_has_no_development_network_access() {
        let config: serde_json::Value =
            serde_json::from_str(include_str!("../tauri.conf.json")).unwrap();
        let security = &config["app"]["security"];
        let production_csp = security["csp"].as_str().unwrap();
        assert!(!production_csp.contains("ws:"));
        for directive in [
            "object-src 'none'",
            "frame-src 'none'",
            "worker-src 'none'",
            "base-uri 'none'",
            "form-action 'none'",
        ] {
            assert!(production_csp.contains(directive));
        }
        assert!(security["devCsp"]
            .as_str()
            .unwrap()
            .contains("ws://localhost:1421"));
        assert_eq!(security["freezePrototype"], true);
        assert_eq!(config["build"]["removeUnusedCommands"], true);

        let capability: serde_json::Value =
            serde_json::from_str(include_str!("../capabilities/default.json")).unwrap();
        assert_eq!(capability["permissions"], serde_json::json!([]));
    }
}
