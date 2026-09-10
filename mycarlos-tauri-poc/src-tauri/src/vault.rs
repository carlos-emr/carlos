use argon2::{Algorithm, Argon2, Params, Version};
use atomicwrites::{AllowOverwrite, AtomicFile, Error as AtomicWriteError};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use chacha20poly1305::{
    aead::{Aead, KeyInit, Payload},
    XChaCha20Poly1305, XNonce,
};
use hkdf::Hkdf;
use hmac::{Hmac, Mac};
use rand::{rngs::OsRng, RngCore};
use serde::{Deserialize, Serialize};
use sha2::Sha256;
#[cfg(unix)]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::{
    collections::{HashMap, HashSet},
    fs::{self, File, OpenOptions},
    io::{self, BufReader, BufWriter, Read, Write},
    path::{Path, PathBuf},
    sync::Mutex,
};
use thiserror::Error;
use uuid::Uuid;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

const VAULT_FORMAT: u32 = 1;
const OBJECT_MAGIC: &[u8; 5] = b"MCVO1";
const CHUNK_SIZE: usize = 1024 * 1024;
const MAX_IMPORT_FILES: usize = 100;
const ARGON_MEMORY_KIB: u32 = 64 * 1024;
const ARGON_ITERATIONS: u32 = 3;
const ARGON_LANES: u32 = 4;

#[cfg(test)]
const TEST_TERMINATION_EXIT_CODE: i32 = 86;

#[cfg(test)]
fn terminate_at_test_boundary(boundary: &str) {
    if std::env::var("MYCARLOS_TEST_TERMINATE_AT").as_deref() == Ok(boundary) {
        std::process::exit(TEST_TERMINATION_EXIT_CODE);
    }
}

#[cfg(not(test))]
fn terminate_at_test_boundary(_boundary: &str) {}

#[cfg(test)]
fn fail_at_test_boundary(boundary: &str) -> Result<(), VaultError> {
    if std::env::var("MYCARLOS_TEST_FAIL_AT").as_deref() == Ok(boundary) {
        Err(VaultError::NoSpace)
    } else {
        Ok(())
    }
}

#[cfg(not(test))]
fn fail_at_test_boundary(_boundary: &str) -> Result<(), VaultError> {
    Ok(())
}

type HmacSha256 = Hmac<Sha256>;

#[derive(Debug, Error)]
pub enum VaultError {
    #[error("the vault has already been created")]
    AlreadyExists,
    #[error("the vault has not been created")]
    Missing,
    #[error("the vault is locked")]
    Locked,
    #[error("the passphrase is incorrect")]
    WrongPassphrase,
    #[error("the vault data is damaged or incomplete")]
    Corrupt,
    #[error("the requested item does not exist")]
    NotFound,
    #[error("the requested change is not valid")]
    Invalid,
    #[error("too many files were selected for one import")]
    ImportBatchLimit,
    #[error("there is not enough space to complete the operation")]
    NoSpace,
    #[error("the storage operation could not be completed")]
    Storage,
}

impl From<io::Error> for VaultError {
    fn from(error: io::Error) -> Self {
        if error.raw_os_error() == Some(28) || error.kind() == io::ErrorKind::StorageFull {
            Self::NoSpace
        } else {
            Self::Storage
        }
    }
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct KdfConfig {
    algorithm: String,
    version: u32,
    memory_kib: u32,
    iterations: u32,
    lanes: u32,
    salt: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct WrappedSecret {
    nonce: String,
    ciphertext: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct VaultHeader {
    magic: String,
    format_version: u32,
    vault_id: Uuid,
    kdf: KdfConfig,
    wrapped_master_key: WrappedSecret,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PatientProfile {
    pub id: Uuid,
    pub display_name: String,
    pub created_at_ms: u64,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VaultFolder {
    pub id: Uuid,
    pub profile_id: Uuid,
    pub parent_id: Option<Uuid>,
    pub name: String,
    pub created_at_ms: u64,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct StoredRecord {
    id: Uuid,
    profile_id: Uuid,
    folder_ids: Vec<Uuid>,
    display_name: String,
    source_label: String,
    media_type: String,
    plaintext_size: u64,
    imported_at_ms: u64,
    object_name: String,
    fingerprint: String,
    wrapped_object_key: WrappedSecret,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Manifest {
    format_version: u32,
    vault_id: Uuid,
    generation: u64,
    profiles: Vec<PatientProfile>,
    folders: Vec<VaultFolder>,
    records: Vec<StoredRecord>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VaultRecord {
    pub id: Uuid,
    pub profile_id: Uuid,
    pub folder_ids: Vec<Uuid>,
    pub display_name: String,
    pub source_label: String,
    pub media_type: String,
    pub plaintext_size: u64,
    pub imported_at_ms: u64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VaultSnapshot {
    pub profiles: Vec<PatientProfile>,
    pub folders: Vec<VaultFolder>,
    pub records: Vec<VaultRecord>,
}

#[derive(Clone, Copy, Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum VaultStatus {
    Absent,
    Locked,
    Unlocked,
}

pub struct ImportSource {
    pub display_name: String,
    pub reader: Box<dyn Read + Send>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportOutcome {
    pub imported: Vec<Uuid>,
    pub skipped_duplicates: Vec<String>,
}

struct UnlockedVault {
    master_key: Zeroizing<[u8; 32]>,
    manifest: Manifest,
}

pub struct VaultStore {
    root: PathBuf,
    unlocked: Mutex<Option<UnlockedVault>>,
}

impl VaultStore {
    pub fn new(root: PathBuf) -> Self {
        Self {
            root,
            unlocked: Mutex::new(None),
        }
    }

    pub fn status(&self) -> VaultStatus {
        if self
            .unlocked
            .lock()
            .expect("vault mutex poisoned")
            .is_some()
        {
            VaultStatus::Unlocked
        } else if self.root.exists() {
            VaultStatus::Locked
        } else {
            VaultStatus::Absent
        }
    }

    pub fn create(
        &self,
        passphrase: &str,
        initial_profile: &str,
        now_ms: u64,
    ) -> Result<(), VaultError> {
        validate_passphrase(passphrase)?;
        validate_name(initial_profile)?;
        if self.root.exists() {
            return Err(VaultError::AlreadyExists);
        }

        let parent = self.root.parent().ok_or(VaultError::Storage)?;
        fs::create_dir_all(parent)?;
        let stage = parent.join(format!(".mycarlos-create-{}", Uuid::new_v4()));
        create_private_dir(&stage)?;
        create_private_dir(&stage.join("objects"))?;
        create_private_dir(&stage.join("staging"))?;

        let result = (|| {
            let vault_id = Uuid::new_v4();
            let mut master_key = Zeroizing::new([0_u8; 32]);
            OsRng.fill_bytes(master_key.as_mut());
            let header = build_header(vault_id, passphrase, &master_key)?;
            atomic_json(&stage.join("header.json"), &header)?;

            let mut manifest = Manifest {
                format_version: VAULT_FORMAT,
                vault_id,
                generation: 1,
                profiles: vec![PatientProfile {
                    id: Uuid::new_v4(),
                    display_name: initial_profile.trim().to_owned(),
                    created_at_ms: now_ms,
                }],
                folders: Vec::new(),
                records: Vec::new(),
            };
            write_manifest_at(&stage, &master_key, &manifest)?;
            manifest.generation = manifest
                .generation
                .checked_add(1)
                .ok_or(VaultError::Storage)?;
            write_manifest_at(&stage, &master_key, &manifest)?;
            sync_parent(&stage);
            fs::rename(&stage, &self.root)?;
            sync_parent(parent);
            *self.unlocked.lock().expect("vault mutex poisoned") = Some(UnlockedVault {
                master_key,
                manifest,
            });
            Ok(())
        })();
        if result.is_err() {
            let _ = fs::remove_dir_all(&stage);
        }
        result
    }

    pub fn unlock(&self, passphrase: &str) -> Result<(), VaultError> {
        let header = self.read_header()?;
        let master_key = unwrap_master_key(&header, passphrase)?;
        let manifest = read_latest_manifest(&self.root, &master_key, header.vault_id)?;
        let manifest = repair_manifest_redundancy(&self.root, &master_key, manifest)?;
        remove_staging(&self.root);
        remove_orphan_objects(&self.root, &manifest);
        *self.unlocked.lock().expect("vault mutex poisoned") = Some(UnlockedVault {
            master_key,
            manifest,
        });
        Ok(())
    }

    pub fn lock(&self) {
        *self.unlocked.lock().expect("vault mutex poisoned") = None;
    }

    pub fn snapshot(&self) -> Result<VaultSnapshot, VaultError> {
        let guard = self.unlocked.lock().expect("vault mutex poisoned");
        let unlocked = guard.as_ref().ok_or(VaultError::Locked)?;
        Ok(VaultSnapshot {
            profiles: unlocked.manifest.profiles.clone(),
            folders: unlocked.manifest.folders.clone(),
            records: unlocked
                .manifest
                .records
                .iter()
                .map(|record| VaultRecord {
                    id: record.id,
                    profile_id: record.profile_id,
                    folder_ids: record.folder_ids.clone(),
                    display_name: record.display_name.clone(),
                    source_label: record.source_label.clone(),
                    media_type: record.media_type.clone(),
                    plaintext_size: record.plaintext_size,
                    imported_at_ms: record.imported_at_ms,
                })
                .collect(),
        })
    }

    pub fn create_profile(&self, display_name: &str, now_ms: u64) -> Result<Uuid, VaultError> {
        validate_name(display_name)?;
        self.mutate_manifest(|manifest| {
            let id = Uuid::new_v4();
            manifest.profiles.push(PatientProfile {
                id,
                display_name: display_name.trim().to_owned(),
                created_at_ms: now_ms,
            });
            Ok(id)
        })
    }

    pub fn create_folder(
        &self,
        profile_id: Uuid,
        parent_id: Option<Uuid>,
        name: &str,
        now_ms: u64,
    ) -> Result<Uuid, VaultError> {
        validate_name(name)?;
        self.mutate_manifest(|manifest| {
            require_profile(manifest, profile_id)?;
            if let Some(parent) = parent_id {
                let folder = manifest
                    .folders
                    .iter()
                    .find(|folder| folder.id == parent)
                    .ok_or(VaultError::NotFound)?;
                if folder.profile_id != profile_id {
                    return Err(VaultError::Invalid);
                }
                ensure_depth(manifest, Some(parent), 1)?;
            }
            let id = Uuid::new_v4();
            manifest.folders.push(VaultFolder {
                id,
                profile_id,
                parent_id,
                name: name.trim().to_owned(),
                created_at_ms: now_ms,
            });
            Ok(id)
        })
    }

    pub fn update_folder(
        &self,
        folder_id: Uuid,
        parent_id: Option<Uuid>,
        name: &str,
    ) -> Result<(), VaultError> {
        validate_name(name)?;
        self.mutate_manifest(|manifest| {
            let current = manifest
                .folders
                .iter()
                .find(|folder| folder.id == folder_id)
                .cloned()
                .ok_or(VaultError::NotFound)?;
            if parent_id == Some(folder_id) {
                return Err(VaultError::Invalid);
            }
            if let Some(parent) = parent_id {
                let parent_folder = manifest
                    .folders
                    .iter()
                    .find(|folder| folder.id == parent)
                    .ok_or(VaultError::NotFound)?;
                if parent_folder.profile_id != current.profile_id
                    || is_descendant(manifest, parent, folder_id)
                {
                    return Err(VaultError::Invalid);
                }
                ensure_depth(manifest, Some(parent), subtree_depth(manifest, folder_id)?)?;
            }
            let folder = manifest
                .folders
                .iter_mut()
                .find(|folder| folder.id == folder_id)
                .ok_or(VaultError::NotFound)?;
            folder.parent_id = parent_id;
            folder.name = name.trim().to_owned();
            Ok(())
        })
    }

    pub fn assign_folders(&self, record_id: Uuid, folder_ids: Vec<Uuid>) -> Result<(), VaultError> {
        self.mutate_manifest(|manifest| {
            let record = manifest
                .records
                .iter()
                .find(|record| record.id == record_id)
                .cloned()
                .ok_or(VaultError::NotFound)?;
            validate_folder_ids(manifest, record.profile_id, &folder_ids)?;
            manifest
                .records
                .iter_mut()
                .find(|candidate| candidate.id == record_id)
                .expect("record disappeared")
                .folder_ids = unique(folder_ids);
            Ok(())
        })
    }

    pub fn import(
        &self,
        profile_id: Uuid,
        folder_ids: Vec<Uuid>,
        sources: Vec<ImportSource>,
        now_ms: u64,
    ) -> Result<ImportOutcome, VaultError> {
        if sources.is_empty() {
            return Ok(ImportOutcome {
                imported: Vec::new(),
                skipped_duplicates: Vec::new(),
            });
        }
        if sources.len() > MAX_IMPORT_FILES {
            return Err(VaultError::ImportBatchLimit);
        }
        let mut guard = self.unlocked.lock().expect("vault mutex poisoned");
        let unlocked = guard.as_mut().ok_or(VaultError::Locked)?;
        require_profile(&unlocked.manifest, profile_id)?;
        validate_folder_ids(&unlocked.manifest, profile_id, &folder_ids)?;

        let job_id = Uuid::new_v4();
        let stage = self.root.join("staging").join(job_id.to_string());
        create_private_dir_all(&stage)?;
        let keys = derive_keys(unlocked.manifest.vault_id, &unlocked.master_key)?;
        let existing: HashSet<String> = unlocked
            .manifest
            .records
            .iter()
            .filter(|record| record.profile_id == profile_id)
            .map(|record| record.fingerprint.clone())
            .collect();
        let mut batch_fingerprints = HashSet::new();
        let mut staged = Vec::new();
        let mut skipped_duplicates = Vec::new();

        let result = (|| {
            for source in sources {
                let record_id = Uuid::new_v4();
                let object_name = format!("{}.mcobj", Uuid::new_v4());
                let object_path = stage.join(&object_name);
                let mut object_key = Zeroizing::new([0_u8; 32]);
                OsRng.fill_bytes(object_key.as_mut());
                let encrypted = encrypt_object(
                    source.reader,
                    &object_path,
                    ObjectContext {
                        vault_id: unlocked.manifest.vault_id,
                        record_id,
                        profile_id,
                    },
                    &object_key,
                    &keys.fingerprint,
                )?;
                let fingerprint = BASE64.encode(encrypted.fingerprint);
                if existing.contains(&fingerprint)
                    || !batch_fingerprints.insert(fingerprint.clone())
                {
                    fs::remove_file(&object_path)?;
                    skipped_duplicates.push(sanitize_basename(&source.display_name));
                    continue;
                }
                verify_object(
                    &object_path,
                    io::sink(),
                    unlocked.manifest.vault_id,
                    record_id,
                    &object_key,
                    encrypted.plaintext_size,
                )?;
                let wrapped_object_key =
                    wrap_secret(&keys.object_wrap, &object_key, record_id.as_bytes())?;
                staged.push((
                    object_path,
                    StoredRecord {
                        id: record_id,
                        profile_id,
                        folder_ids: unique(folder_ids.clone()),
                        display_name: sanitize_basename(&source.display_name),
                        source_label: "Manual import — unverified".to_owned(),
                        media_type: "application/octet-stream".to_owned(),
                        plaintext_size: encrypted.plaintext_size,
                        imported_at_ms: now_ms,
                        object_name,
                        fingerprint,
                        wrapped_object_key,
                    },
                ));
            }
            terminate_at_test_boundary("import.after-staging");

            let objects = self.root.join("objects");
            for (path, record) in &staged {
                let destination = objects.join(&record.object_name);
                fs::rename(path, &destination)?;
            }
            sync_parent(&objects);
            terminate_at_test_boundary("import.after-object-rename");
            let mut next = unlocked.manifest.clone();
            next.records
                .extend(staged.iter().map(|(_, record)| record.clone()));
            commit_manifest_redundant(
                &self.root,
                &unlocked.master_key,
                &mut unlocked.manifest,
                next,
            )?;
            Ok(ImportOutcome {
                imported: staged.iter().map(|(_, record)| record.id).collect(),
                skipped_duplicates,
            })
        })();
        let _ = fs::remove_dir_all(&stage);
        result
    }

    pub fn export<W: Write>(&self, record_id: Uuid, writer: W) -> Result<(), VaultError> {
        let guard = self.unlocked.lock().expect("vault mutex poisoned");
        let unlocked = guard.as_ref().ok_or(VaultError::Locked)?;
        let record = unlocked
            .manifest
            .records
            .iter()
            .find(|record| record.id == record_id)
            .ok_or(VaultError::NotFound)?;
        let keys = derive_keys(unlocked.manifest.vault_id, &unlocked.master_key)?;
        let object_key = unwrap_secret(
            &keys.object_wrap,
            &record.wrapped_object_key,
            record.id.as_bytes(),
        )?;
        verify_object(
            &self.root.join("objects").join(&record.object_name),
            writer,
            unlocked.manifest.vault_id,
            record.id,
            &object_key,
            record.plaintext_size,
        )
    }

    pub fn export_atomic(&self, record_id: Uuid, destination: &Path) -> Result<(), VaultError> {
        let vault_root = fs::canonicalize(&self.root)?;
        let destination_parent = destination.parent().ok_or(VaultError::Invalid)?;
        let destination_parent = fs::canonicalize(destination_parent)?;
        if destination_parent.starts_with(vault_root) {
            return Err(VaultError::Invalid);
        }

        let mut options = OpenOptions::new();
        options.write(true).create(true).truncate(true);
        #[cfg(unix)]
        options.mode(0o600);
        AtomicFile::new(destination, AllowOverwrite)
            .write_with_options(|file| self.export(record_id, file), options)
            .map_err(|error| match error {
                AtomicWriteError::Internal(error) => error.into(),
                AtomicWriteError::User(error) => error,
            })
    }

    pub fn delete_record(&self, record_id: Uuid) -> Result<(), VaultError> {
        let mut guard = self.unlocked.lock().expect("vault mutex poisoned");
        let unlocked = guard.as_mut().ok_or(VaultError::Locked)?;
        let mut next = unlocked.manifest.clone();
        let index = next
            .records
            .iter()
            .position(|record| record.id == record_id)
            .ok_or(VaultError::NotFound)?;
        let object_name = next.records.remove(index).object_name;

        // Commit once before unlinking the ciphertext. If the second manifest write is
        // interrupted, the newest manifest records the deletion and the older manifest cannot
        // decrypt a ciphertext that was successfully removed. Unlock repairs slot redundancy
        // before it considers orphan cleanup.
        next.generation = next.generation.checked_add(1).ok_or(VaultError::Storage)?;
        fail_at_test_boundary("delete.before-first-manifest")?;
        write_manifest_at(&self.root, &unlocked.master_key, &next)?;
        unlocked.manifest = next.clone();
        terminate_at_test_boundary("delete.after-first-manifest");
        fail_at_test_boundary("delete.after-first-manifest")?;

        let objects = self.root.join("objects");
        match fs::remove_file(objects.join(object_name)) {
            Ok(()) => sync_parent(&objects),
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            // The second manifest commit below still performs cryptographic erasure. Unlock
            // cleanup retries removal of ciphertext that no live manifest can decrypt.
            Err(_) => {}
        }
        terminate_at_test_boundary("delete.after-object-unlink");
        fail_at_test_boundary("delete.after-object-unlink")?;

        next.generation = next.generation.checked_add(1).ok_or(VaultError::Storage)?;
        write_manifest_at(&self.root, &unlocked.master_key, &next)?;
        unlocked.manifest = next;
        terminate_at_test_boundary("delete.after-second-manifest");
        Ok(())
    }

    pub fn change_passphrase(&self, current: &str, replacement: &str) -> Result<(), VaultError> {
        validate_passphrase(replacement)?;
        let header = self.read_header()?;
        let mut verified = unwrap_master_key(&header, current)?;
        let new_header = build_header(header.vault_id, replacement, &verified)?;
        atomic_json(&self.root.join("header.json"), &new_header)?;
        verified.zeroize();
        Ok(())
    }

    pub fn reset(&self) -> Result<(), VaultError> {
        self.lock();
        if !self.root.exists() {
            return Err(VaultError::Missing);
        }
        let retired = self
            .root
            .with_file_name(format!("mycarlos-vault-reset-{}", Uuid::new_v4()));
        fs::rename(&self.root, &retired)?;
        match fs::remove_dir_all(&retired) {
            Ok(()) => {
                if let Some(parent) = self.root.parent() {
                    sync_parent(parent);
                }
                Ok(())
            }
            Err(error) => {
                // Keep a failed or partially completed reset visible as the vault path instead of
                // silently presenting an empty setup screen while retired encrypted data remains.
                if !self.root.exists() {
                    let _ = fs::rename(&retired, &self.root);
                }
                Err(error.into())
            }
        }
    }

    fn read_header(&self) -> Result<VaultHeader, VaultError> {
        let data = fs::read(self.root.join("header.json")).map_err(|error| {
            if error.kind() == io::ErrorKind::NotFound {
                if self.root.exists() {
                    VaultError::Corrupt
                } else {
                    VaultError::Missing
                }
            } else {
                VaultError::Storage
            }
        })?;
        let header: VaultHeader = serde_json::from_slice(&data).map_err(|_| VaultError::Corrupt)?;
        if header.magic != "MYCARLOS-VAULT" || header.format_version != VAULT_FORMAT {
            return Err(VaultError::Corrupt);
        }
        Ok(header)
    }

    fn mutate_manifest<T>(
        &self,
        mutation: impl FnOnce(&mut Manifest) -> Result<T, VaultError>,
    ) -> Result<T, VaultError> {
        let mut guard = self.unlocked.lock().expect("vault mutex poisoned");
        let unlocked = guard.as_mut().ok_or(VaultError::Locked)?;
        let mut next = unlocked.manifest.clone();
        let result = mutation(&mut next)?;
        commit_manifest_redundant(
            &self.root,
            &unlocked.master_key,
            &mut unlocked.manifest,
            next,
        )?;
        Ok(result)
    }
}

#[derive(Zeroize, ZeroizeOnDrop)]
struct DerivedKeys {
    manifest: [u8; 32],
    object_wrap: [u8; 32],
    fingerprint: [u8; 32],
}

struct EncryptedObject {
    plaintext_size: u64,
    fingerprint: [u8; 32],
}

#[derive(Clone, Copy)]
struct ObjectContext {
    vault_id: Uuid,
    record_id: Uuid,
    profile_id: Uuid,
}

fn validate_passphrase(passphrase: &str) -> Result<(), VaultError> {
    if passphrase.chars().count() < 8 || passphrase.len() > 1024 {
        return Err(VaultError::Invalid);
    }
    let upper = passphrase.chars().any(|c| c.is_ascii_uppercase());
    let lower = passphrase.chars().any(|c| c.is_ascii_lowercase());
    let digit = passphrase.chars().any(|c| c.is_ascii_digit());
    let symbol = passphrase.chars().any(|c| !c.is_alphanumeric());
    if upper && lower && digit && symbol {
        Ok(())
    } else {
        Err(VaultError::Invalid)
    }
}

fn validate_name(name: &str) -> Result<(), VaultError> {
    let name = name.trim();
    if name.is_empty() || name.chars().count() > 120 || name.chars().any(char::is_control) {
        Err(VaultError::Invalid)
    } else {
        Ok(())
    }
}

fn sanitize_basename(value: &str) -> String {
    let raw = value.rsplit(['/', '\\']).next().unwrap_or("Imported file");
    let cleaned: String = raw.chars().filter(|c| !c.is_control()).take(240).collect();
    if cleaned.trim().is_empty() {
        "Imported file".to_owned()
    } else {
        cleaned
    }
}

fn build_header(
    vault_id: Uuid,
    passphrase: &str,
    master_key: &[u8; 32],
) -> Result<VaultHeader, VaultError> {
    let mut salt = [0_u8; 16];
    OsRng.fill_bytes(&mut salt);
    let kdf = KdfConfig {
        algorithm: "argon2id".to_owned(),
        version: 19,
        memory_kib: ARGON_MEMORY_KIB,
        iterations: ARGON_ITERATIONS,
        lanes: ARGON_LANES,
        salt: BASE64.encode(salt),
    };
    let mut wrapping_key = derive_passphrase_key(passphrase, &kdf)?;
    let wrapped_master_key = wrap_secret(&wrapping_key, master_key, vault_id.as_bytes())?;
    wrapping_key.zeroize();
    Ok(VaultHeader {
        magic: "MYCARLOS-VAULT".to_owned(),
        format_version: VAULT_FORMAT,
        vault_id,
        kdf,
        wrapped_master_key,
    })
}

fn unwrap_master_key(
    header: &VaultHeader,
    passphrase: &str,
) -> Result<Zeroizing<[u8; 32]>, VaultError> {
    let mut wrapping_key = derive_passphrase_key(passphrase, &header.kdf)?;
    let result = unwrap_secret(
        &wrapping_key,
        &header.wrapped_master_key,
        header.vault_id.as_bytes(),
    )
    .map_err(|_| VaultError::WrongPassphrase);
    wrapping_key.zeroize();
    result
}

fn derive_passphrase_key(passphrase: &str, config: &KdfConfig) -> Result<[u8; 32], VaultError> {
    if config.algorithm != "argon2id"
        || config.version != 19
        || config.memory_kib != ARGON_MEMORY_KIB
        || config.iterations != ARGON_ITERATIONS
        || config.lanes != ARGON_LANES
    {
        return Err(VaultError::Corrupt);
    }
    let salt = BASE64
        .decode(&config.salt)
        .map_err(|_| VaultError::Corrupt)?;
    if salt.len() != 16 {
        return Err(VaultError::Corrupt);
    }
    let params = Params::new(config.memory_kib, config.iterations, config.lanes, Some(32))
        .map_err(|_| VaultError::Corrupt)?;
    let argon = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    let mut output = [0_u8; 32];
    argon
        .hash_password_into(passphrase.as_bytes(), &salt, &mut output)
        .map_err(|_| VaultError::Storage)?;
    Ok(output)
}

fn derive_keys(vault_id: Uuid, master_key: &[u8; 32]) -> Result<DerivedKeys, VaultError> {
    let hkdf = Hkdf::<Sha256>::new(Some(vault_id.as_bytes()), master_key);
    let mut keys = DerivedKeys {
        manifest: [0; 32],
        object_wrap: [0; 32],
        fingerprint: [0; 32],
    };
    hkdf.expand(b"mycarlos/manifest/v1", &mut keys.manifest)
        .map_err(|_| VaultError::Corrupt)?;
    hkdf.expand(b"mycarlos/object-wrap/v1", &mut keys.object_wrap)
        .map_err(|_| VaultError::Corrupt)?;
    hkdf.expand(b"mycarlos/fingerprint/v1", &mut keys.fingerprint)
        .map_err(|_| VaultError::Corrupt)?;
    Ok(keys)
}

fn wrap_secret(key: &[u8; 32], secret: &[u8; 32], aad: &[u8]) -> Result<WrappedSecret, VaultError> {
    let cipher = XChaCha20Poly1305::new(key.into());
    let mut nonce = [0_u8; 24];
    OsRng.fill_bytes(&mut nonce);
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce), Payload { msg: secret, aad })
        .map_err(|_| VaultError::Storage)?;
    Ok(WrappedSecret {
        nonce: BASE64.encode(nonce),
        ciphertext: BASE64.encode(ciphertext),
    })
}

fn unwrap_secret(
    key: &[u8; 32],
    wrapped: &WrappedSecret,
    aad: &[u8],
) -> Result<Zeroizing<[u8; 32]>, VaultError> {
    let nonce = BASE64
        .decode(&wrapped.nonce)
        .map_err(|_| VaultError::Corrupt)?;
    let ciphertext = BASE64
        .decode(&wrapped.ciphertext)
        .map_err(|_| VaultError::Corrupt)?;
    if nonce.len() != 24 {
        return Err(VaultError::Corrupt);
    }
    let plaintext = XChaCha20Poly1305::new(key.into())
        .decrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: &ciphertext,
                aad,
            },
        )
        .map_err(|_| VaultError::Corrupt)?;
    let value: [u8; 32] = plaintext.try_into().map_err(|_| VaultError::Corrupt)?;
    Ok(Zeroizing::new(value))
}

fn manifest_path(root: &Path, generation: u64) -> PathBuf {
    root.join(format!("manifest-{}.bin", generation % 2))
}

fn write_manifest_at(
    root: &Path,
    master_key: &[u8; 32],
    manifest: &Manifest,
) -> Result<(), VaultError> {
    let keys = derive_keys(manifest.vault_id, master_key)?;
    let plaintext = Zeroizing::new(serde_json::to_vec(manifest).map_err(|_| VaultError::Storage)?);
    let mut nonce = [0_u8; 24];
    OsRng.fill_bytes(&mut nonce);
    let aad = manifest_aad(manifest.vault_id);
    let ciphertext = XChaCha20Poly1305::new((&keys.manifest).into())
        .encrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: &plaintext,
                aad: &aad,
            },
        )
        .map_err(|_| VaultError::Storage)?;
    let mut output = Vec::with_capacity(24 + ciphertext.len());
    output.extend_from_slice(&nonce);
    output.extend_from_slice(&ciphertext);
    atomic_bytes(&manifest_path(root, manifest.generation), &output)
}

fn commit_manifest_redundant(
    root: &Path,
    master_key: &[u8; 32],
    current: &mut Manifest,
    mut next: Manifest,
) -> Result<(), VaultError> {
    next.generation = current
        .generation
        .checked_add(1)
        .ok_or(VaultError::Storage)?;
    fail_at_test_boundary("manifest.before-first-write")?;
    write_manifest_at(root, master_key, &next)?;
    *current = next.clone();
    terminate_at_test_boundary("manifest.after-first-write");
    fail_at_test_boundary("manifest.after-first-write")?;

    next.generation = next.generation.checked_add(1).ok_or(VaultError::Storage)?;
    write_manifest_at(root, master_key, &next)?;
    *current = next;
    terminate_at_test_boundary("manifest.after-second-write");
    Ok(())
}

fn repair_manifest_redundancy(
    root: &Path,
    master_key: &[u8; 32],
    mut manifest: Manifest,
) -> Result<Manifest, VaultError> {
    manifest.generation = manifest
        .generation
        .checked_add(1)
        .ok_or(VaultError::Storage)?;
    write_manifest_at(root, master_key, &manifest)?;
    Ok(manifest)
}

fn read_latest_manifest(
    root: &Path,
    master_key: &[u8; 32],
    vault_id: Uuid,
) -> Result<Manifest, VaultError> {
    let keys = derive_keys(vault_id, master_key)?;
    let mut candidates = Vec::new();
    for slot in 0..=1 {
        let path = root.join(format!("manifest-{slot}.bin"));
        let Ok(data) = fs::read(path) else { continue };
        if data.len() < 40 {
            continue;
        }
        let aad = manifest_aad(vault_id);
        let Ok(plaintext) = XChaCha20Poly1305::new((&keys.manifest).into()).decrypt(
            XNonce::from_slice(&data[..24]),
            Payload {
                msg: &data[24..],
                aad: &aad,
            },
        ) else {
            continue;
        };
        let plaintext = Zeroizing::new(plaintext);
        let Ok(manifest) = serde_json::from_slice::<Manifest>(&plaintext) else {
            continue;
        };
        if manifest.format_version == VAULT_FORMAT
            && manifest.vault_id == vault_id
            && manifest_objects_exist(root, &manifest)
        {
            candidates.push(manifest);
        }
    }
    candidates
        .into_iter()
        .max_by_key(|manifest| manifest.generation)
        .ok_or(VaultError::Corrupt)
}

fn manifest_objects_exist(root: &Path, manifest: &Manifest) -> bool {
    manifest
        .records
        .iter()
        .all(|record| root.join("objects").join(&record.object_name).is_file())
}

fn manifest_aad(vault_id: Uuid) -> Vec<u8> {
    [b"mycarlos-manifest-v1:".as_slice(), vault_id.as_bytes()].concat()
}

fn object_aad(vault_id: Uuid, record_id: Uuid, index: u64, final_chunk: bool) -> Vec<u8> {
    let mut aad = Vec::with_capacity(64);
    aad.extend_from_slice(b"mycarlos-object-v1:");
    aad.extend_from_slice(vault_id.as_bytes());
    aad.extend_from_slice(record_id.as_bytes());
    aad.extend_from_slice(&index.to_be_bytes());
    aad.push(u8::from(final_chunk));
    aad
}

fn encrypt_object(
    mut reader: Box<dyn Read + Send>,
    path: &Path,
    context: ObjectContext,
    object_key: &[u8; 32],
    fingerprint_key: &[u8; 32],
) -> Result<EncryptedObject, VaultError> {
    let file = open_private_new(path)?;
    let mut writer = BufWriter::new(file);
    let mut nonce_prefix = [0_u8; 16];
    OsRng.fill_bytes(&mut nonce_prefix);
    writer.write_all(OBJECT_MAGIC)?;
    writer.write_all(&(CHUNK_SIZE as u32).to_be_bytes())?;
    writer.write_all(&nonce_prefix)?;

    let cipher = XChaCha20Poly1305::new(object_key.into());
    let mut mac =
        <HmacSha256 as Mac>::new_from_slice(fingerprint_key).map_err(|_| VaultError::Storage)?;
    mac.update(context.profile_id.as_bytes());
    let mut current = Zeroizing::new(vec![0_u8; CHUNK_SIZE]);
    let mut next = Zeroizing::new(vec![0_u8; CHUNK_SIZE]);
    let mut current_len = read_chunk(&mut reader, &mut current)?;
    let mut total = 0_u64;
    let mut index = 0_u64;
    loop {
        let next_len = if current_len == 0 && index == 0 {
            0
        } else {
            read_chunk(&mut reader, &mut next)?
        };
        let final_chunk = next_len == 0;
        mac.update(&current[..current_len]);
        total = total
            .checked_add(current_len as u64)
            .ok_or(VaultError::Invalid)?;
        let mut nonce = [0_u8; 24];
        nonce[..16].copy_from_slice(&nonce_prefix);
        nonce[16..].copy_from_slice(&index.to_be_bytes());
        let aad = object_aad(context.vault_id, context.record_id, index, final_chunk);
        let ciphertext = cipher
            .encrypt(
                XNonce::from_slice(&nonce),
                Payload {
                    msg: &current[..current_len],
                    aad: &aad,
                },
            )
            .map_err(|_| VaultError::Storage)?;
        writer.write_all(&(ciphertext.len() as u32).to_be_bytes())?;
        writer.write_all(&[u8::from(final_chunk)])?;
        writer.write_all(&ciphertext)?;
        terminate_at_test_boundary("object.after-chunk-write");
        fail_at_test_boundary("object.after-chunk-write")?;
        if final_chunk {
            break;
        }
        std::mem::swap(&mut current, &mut next);
        current_len = next_len;
        index = index.checked_add(1).ok_or(VaultError::Invalid)?;
    }
    writer.flush()?;
    writer.get_ref().sync_all()?;
    let fingerprint: [u8; 32] = mac.finalize().into_bytes().into();
    Ok(EncryptedObject {
        plaintext_size: total,
        fingerprint,
    })
}

fn verify_object<W: Write>(
    path: &Path,
    mut writer: W,
    vault_id: Uuid,
    record_id: Uuid,
    object_key: &[u8; 32],
    expected_size: u64,
) -> Result<(), VaultError> {
    let mut reader = BufReader::new(File::open(path)?);
    let mut magic = [0_u8; 5];
    reader.read_exact(&mut magic)?;
    if &magic != OBJECT_MAGIC {
        return Err(VaultError::Corrupt);
    }
    let mut size_bytes = [0_u8; 4];
    reader.read_exact(&mut size_bytes)?;
    if u32::from_be_bytes(size_bytes) as usize != CHUNK_SIZE {
        return Err(VaultError::Corrupt);
    }
    let mut nonce_prefix = [0_u8; 16];
    reader.read_exact(&mut nonce_prefix)?;
    let cipher = XChaCha20Poly1305::new(object_key.into());
    let mut total = 0_u64;
    let mut index = 0_u64;
    loop {
        let mut length_bytes = [0_u8; 4];
        reader
            .read_exact(&mut length_bytes)
            .map_err(|_| VaultError::Corrupt)?;
        let length = u32::from_be_bytes(length_bytes) as usize;
        if !(16..=CHUNK_SIZE + 16).contains(&length) {
            return Err(VaultError::Corrupt);
        }
        let mut final_byte = [0_u8; 1];
        reader
            .read_exact(&mut final_byte)
            .map_err(|_| VaultError::Corrupt)?;
        if final_byte[0] > 1 {
            return Err(VaultError::Corrupt);
        }
        let final_chunk = final_byte[0] == 1;
        let mut ciphertext = vec![0_u8; length];
        reader
            .read_exact(&mut ciphertext)
            .map_err(|_| VaultError::Corrupt)?;
        let mut nonce = [0_u8; 24];
        nonce[..16].copy_from_slice(&nonce_prefix);
        nonce[16..].copy_from_slice(&index.to_be_bytes());
        let aad = object_aad(vault_id, record_id, index, final_chunk);
        let plaintext = Zeroizing::new(
            cipher
                .decrypt(
                    XNonce::from_slice(&nonce),
                    Payload {
                        msg: &ciphertext,
                        aad: &aad,
                    },
                )
                .map_err(|_| VaultError::Corrupt)?,
        );
        total = total
            .checked_add(plaintext.len() as u64)
            .ok_or(VaultError::Corrupt)?;
        writer.write_all(&plaintext)?;
        if final_chunk {
            let mut trailing = [0_u8; 1];
            if reader.read(&mut trailing)? != 0 || total != expected_size {
                return Err(VaultError::Corrupt);
            }
            writer.flush()?;
            return Ok(());
        }
        index = index.checked_add(1).ok_or(VaultError::Corrupt)?;
    }
}

fn read_chunk(reader: &mut dyn Read, buffer: &mut [u8]) -> Result<usize, VaultError> {
    let mut used = 0;
    while used < buffer.len() {
        match reader.read(&mut buffer[used..]) {
            Ok(0) => break,
            Ok(read) => used += read,
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(error.into()),
        }
    }
    Ok(used)
}

fn atomic_json(path: &Path, value: &impl Serialize) -> Result<(), VaultError> {
    let data = serde_json::to_vec_pretty(value).map_err(|_| VaultError::Storage)?;
    atomic_bytes(path, &data)
}

fn atomic_bytes(path: &Path, data: &[u8]) -> Result<(), VaultError> {
    let mut options = OpenOptions::new();
    options.write(true).create(true).truncate(true);
    #[cfg(unix)]
    options.mode(0o600);
    AtomicFile::new(path, AllowOverwrite)
        .write_with_options(|file| file.write_all(data), options)
        .map_err(io::Error::from)?;
    Ok(())
}

fn create_private_dir(path: &Path) -> Result<(), VaultError> {
    fs::create_dir(path)?;
    #[cfg(unix)]
    fs::set_permissions(path, fs::Permissions::from_mode(0o700))?;
    Ok(())
}

fn create_private_dir_all(path: &Path) -> Result<(), VaultError> {
    fs::create_dir_all(path)?;
    #[cfg(unix)]
    fs::set_permissions(path, fs::Permissions::from_mode(0o700))?;
    Ok(())
}

fn open_private_new(path: &Path) -> Result<File, VaultError> {
    let mut options = OpenOptions::new();
    options.create_new(true).write(true);
    #[cfg(unix)]
    options.mode(0o600);
    options.open(path).map_err(Into::into)
}

fn sync_parent(parent: &Path) {
    if let Ok(directory) = File::open(parent) {
        let _ = directory.sync_all();
    }
}

fn remove_staging(root: &Path) {
    let staging = root.join("staging");
    if let Ok(entries) = fs::read_dir(&staging) {
        for entry in entries.flatten() {
            let _ = fs::remove_dir_all(entry.path());
        }
    }
}

fn remove_orphan_objects(root: &Path, manifest: &Manifest) {
    let referenced: HashSet<&str> = manifest
        .records
        .iter()
        .map(|record| record.object_name.as_str())
        .collect();
    if let Ok(entries) = fs::read_dir(root.join("objects")) {
        for entry in entries.flatten() {
            let path = entry.path();
            let is_referenced = path
                .file_name()
                .and_then(|name| name.to_str())
                .is_some_and(|name| referenced.contains(name));
            if path.is_file() && !is_referenced {
                let _ = fs::remove_file(path);
            }
        }
    }
}

fn require_profile(manifest: &Manifest, profile_id: Uuid) -> Result<(), VaultError> {
    if manifest
        .profiles
        .iter()
        .any(|profile| profile.id == profile_id)
    {
        Ok(())
    } else {
        Err(VaultError::NotFound)
    }
}

fn validate_folder_ids(
    manifest: &Manifest,
    profile_id: Uuid,
    folder_ids: &[Uuid],
) -> Result<(), VaultError> {
    if folder_ids.iter().all(|id| {
        manifest
            .folders
            .iter()
            .any(|folder| folder.id == *id && folder.profile_id == profile_id)
    }) {
        Ok(())
    } else {
        Err(VaultError::Invalid)
    }
}

fn unique(values: Vec<Uuid>) -> Vec<Uuid> {
    let mut seen = HashSet::new();
    values.into_iter().filter(|id| seen.insert(*id)).collect()
}

fn folder_map(manifest: &Manifest) -> HashMap<Uuid, Option<Uuid>> {
    manifest
        .folders
        .iter()
        .map(|folder| (folder.id, folder.parent_id))
        .collect()
}

fn ensure_depth(
    manifest: &Manifest,
    mut parent: Option<Uuid>,
    child_depth: usize,
) -> Result<(), VaultError> {
    let parents = folder_map(manifest);
    let mut depth = child_depth;
    let mut seen = HashSet::new();
    while let Some(id) = parent {
        if !seen.insert(id) {
            return Err(VaultError::Corrupt);
        }
        depth += 1;
        if depth > 32 {
            return Err(VaultError::Invalid);
        }
        parent = *parents.get(&id).ok_or(VaultError::NotFound)?;
    }
    Ok(())
}

fn is_descendant(manifest: &Manifest, mut candidate: Uuid, ancestor: Uuid) -> bool {
    let parents = folder_map(manifest);
    let mut seen = HashSet::new();
    loop {
        if candidate == ancestor {
            return true;
        }
        if !seen.insert(candidate) {
            return true;
        }
        match parents.get(&candidate).copied().flatten() {
            Some(parent) => candidate = parent,
            None => return false,
        }
    }
}

fn subtree_depth(manifest: &Manifest, root: Uuid) -> Result<usize, VaultError> {
    fn visit(
        manifest: &Manifest,
        current: Uuid,
        seen: &mut HashSet<Uuid>,
    ) -> Result<usize, VaultError> {
        if !seen.insert(current) {
            return Err(VaultError::Corrupt);
        }
        let child_depth = manifest
            .folders
            .iter()
            .filter(|folder| folder.parent_id == Some(current))
            .map(|folder| visit(manifest, folder.id, seen))
            .collect::<Result<Vec<_>, _>>()?
            .into_iter()
            .max()
            .unwrap_or(0);
        seen.remove(&current);
        Ok(child_depth + 1)
    }
    visit(manifest, root, &mut HashSet::new())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        io::Cursor,
        process::{Command, Stdio},
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc,
        },
    };

    const PASSWORD: &str = "Correct#8Horse";

    fn source(name: &str, data: &[u8]) -> ImportSource {
        ImportSource {
            display_name: name.to_owned(),
            reader: Box::new(Cursor::new(data.to_vec())),
        }
    }

    struct FailingReader;

    impl Read for FailingReader {
        fn read(&mut self, _buffer: &mut [u8]) -> io::Result<usize> {
            Err(io::Error::other("synthetic interrupted read"))
        }
    }

    struct GeneratedReader {
        remaining: u64,
        largest_request: Arc<AtomicUsize>,
    }

    impl Read for GeneratedReader {
        fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
            self.largest_request
                .fetch_max(buffer.len(), Ordering::Relaxed);
            let length = usize::try_from(self.remaining.min(buffer.len() as u64)).unwrap();
            buffer[..length].fill(0x5a);
            self.remaining -= length as u64;
            Ok(length)
        }
    }

    fn copy_directory(source: &Path, destination: &Path) {
        fs::create_dir(destination).unwrap();
        for entry in fs::read_dir(source).unwrap() {
            let entry = entry.unwrap();
            let target = destination.join(entry.file_name());
            if entry.file_type().unwrap().is_dir() {
                copy_directory(&entry.path(), &target);
            } else {
                fs::copy(entry.path(), target).unwrap();
            }
        }
    }

    fn files_below(root: &Path) -> Vec<PathBuf> {
        let mut files = Vec::new();
        for entry in fs::read_dir(root).unwrap() {
            let entry = entry.unwrap();
            if entry.file_type().unwrap().is_dir() {
                files.extend(files_below(&entry.path()));
            } else {
                files.push(entry.path());
            }
        }
        files
    }

    fn run_termination_child(root: &Path, operation: &str, boundary: &str) {
        let status = Command::new(std::env::current_exe().unwrap())
            .args(["--ignored", "--exact", "vault::tests::termination_child"])
            .env("MYCARLOS_TEST_ROOT", root)
            .env("MYCARLOS_TEST_OPERATION", operation)
            .env("MYCARLOS_TEST_TERMINATE_AT", boundary)
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .unwrap();
        assert_eq!(
            status.code(),
            Some(TEST_TERMINATION_EXIT_CODE),
            "{boundary}"
        );
    }

    fn run_failure_child(root: &Path, operation: &str, boundary: &str) {
        let status = Command::new(std::env::current_exe().unwrap())
            .args(["--ignored", "--exact", "vault::tests::failure_child"])
            .env("MYCARLOS_TEST_ROOT", root)
            .env("MYCARLOS_TEST_OPERATION", operation)
            .env("MYCARLOS_TEST_FAIL_AT", boundary)
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .unwrap();
        assert!(status.success(), "{boundary}");
    }

    #[test]
    #[ignore = "invoked as an abrupt-termination subprocess by the recovery matrix"]
    fn termination_child() {
        let Ok(root) = std::env::var("MYCARLOS_TEST_ROOT") else {
            return;
        };
        let operation = std::env::var("MYCARLOS_TEST_OPERATION").unwrap();
        let store = VaultStore::new(PathBuf::from(root));
        store.unlock(PASSWORD).unwrap();
        let snapshot = store.snapshot().unwrap();
        match operation.as_str() {
            "import" => {
                store
                    .import(
                        snapshot.profiles[0].id,
                        vec![],
                        vec![source("crash-test.pdf", b"synthetic crash boundary record")],
                        2,
                    )
                    .unwrap();
            }
            "delete" => store.delete_record(snapshot.records[0].id).unwrap(),
            _ => panic!("unknown termination-child operation"),
        }
        panic!("configured termination boundary was not reached");
    }

    #[test]
    #[ignore = "invoked as an injected-write-failure subprocess by the recovery matrix"]
    fn failure_child() {
        let Ok(root) = std::env::var("MYCARLOS_TEST_ROOT") else {
            return;
        };
        let operation = std::env::var("MYCARLOS_TEST_OPERATION").unwrap();
        let store = VaultStore::new(PathBuf::from(root));
        store.unlock(PASSWORD).unwrap();
        let snapshot = store.snapshot().unwrap();
        let failed_with_no_space = match operation.as_str() {
            "import" => matches!(
                store.import(
                    snapshot.profiles[0].id,
                    vec![],
                    vec![source(
                        "failure-test.pdf",
                        b"synthetic write failure record"
                    )],
                    2,
                ),
                Err(VaultError::NoSpace)
            ),
            "delete" => matches!(
                store.delete_record(snapshot.records[0].id),
                Err(VaultError::NoSpace)
            ),
            _ => panic!("unknown failure-child operation"),
        };
        assert!(failed_with_no_space);
    }

    #[test]
    fn vault_round_trip_survives_lock_and_restart() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let folder = store.create_folder(profile, None, "Hospital", 2).unwrap();
        let imported = store
            .import(
                profile,
                vec![folder],
                vec![source("report.bin", b"private clinical bytes")],
                3,
            )
            .unwrap();
        let record = imported.imported[0];
        store.lock();
        assert_eq!(store.status(), VaultStatus::Locked);

        let restarted = VaultStore::new(root);
        restarted.unlock(PASSWORD).unwrap();
        let mut output = Vec::new();
        restarted.export(record, &mut output).unwrap();
        assert_eq!(output, b"private clinical bytes");
        assert_eq!(restarted.snapshot().unwrap().folders[0].name, "Hospital");
    }

    #[test]
    fn wrong_password_and_tampering_fail_closed() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let record = store
            .import(profile, vec![], vec![source("record", b"secret")], 2)
            .unwrap()
            .imported[0];
        store.lock();
        assert!(matches!(
            store.unlock("Wrong#8Pass"),
            Err(VaultError::WrongPassphrase)
        ));
        store.unlock(PASSWORD).unwrap();
        let object_name = {
            let guard = store.unlocked.lock().unwrap();
            guard.as_ref().unwrap().manifest.records[0]
                .object_name
                .clone()
        };
        let path = root.join("objects").join(object_name);
        let original = fs::read(&path).unwrap();
        let mut bytes = original.clone();
        *bytes.last_mut().unwrap() ^= 1;
        fs::write(&path, bytes).unwrap();
        assert!(matches!(
            store.export(record, io::sink()),
            Err(VaultError::Corrupt)
        ));

        fs::write(&path, &original[..original.len() - 1]).unwrap();
        assert!(matches!(
            store.export(record, io::sink()),
            Err(VaultError::Corrupt)
        ));
    }

    #[test]
    fn duplicate_detection_is_profile_scoped() {
        let temp = tempfile::tempdir().unwrap();
        let store = VaultStore::new(temp.path().join("vault"));
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let first_profile = store.snapshot().unwrap().profiles[0].id;
        let second_profile = store.create_profile("Morgan", 2).unwrap();
        assert_eq!(
            store
                .import(first_profile, vec![], vec![source("a", b"same")], 3)
                .unwrap()
                .imported
                .len(),
            1
        );
        assert_eq!(
            store
                .import(first_profile, vec![], vec![source("b", b"same")], 4)
                .unwrap()
                .skipped_duplicates,
            vec!["b"]
        );
        assert_eq!(
            store
                .import(second_profile, vec![], vec![source("c", b"same")], 5)
                .unwrap()
                .imported
                .len(),
            1
        );
    }

    #[test]
    fn failed_batch_import_commits_nothing() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let result = store.import(
            profile,
            vec![],
            vec![
                source("first.txt", b"must not commit"),
                ImportSource {
                    display_name: "broken.txt".to_owned(),
                    reader: Box::new(FailingReader),
                },
            ],
            2,
        );

        assert!(matches!(result, Err(VaultError::Storage)));
        assert!(store.snapshot().unwrap().records.is_empty());
        assert_eq!(fs::read_dir(root.join("objects")).unwrap().count(), 0);
        assert_eq!(fs::read_dir(root.join("staging")).unwrap().count(), 0);
    }

    #[test]
    fn import_abrupt_termination_matrix_recovers_to_a_complete_state() {
        let temp = tempfile::tempdir().unwrap();
        let template = temp.path().join("template-vault");
        let template_store = VaultStore::new(template.clone());
        template_store.create(PASSWORD, "Jamie", 1).unwrap();
        template_store.lock();

        for (boundary, committed) in [
            ("object.after-chunk-write", false),
            ("import.after-staging", false),
            ("import.after-object-rename", false),
            ("manifest.after-first-write", true),
            ("manifest.after-second-write", true),
        ] {
            let root = temp.path().join(boundary);
            copy_directory(&template, &root);
            run_termination_child(&root, "import", boundary);

            let restarted = VaultStore::new(root.clone());
            restarted.unlock(PASSWORD).unwrap();
            assert_eq!(
                restarted.snapshot().unwrap().records.len(),
                usize::from(committed)
            );
            assert_eq!(fs::read_dir(root.join("staging")).unwrap().count(), 0);
            assert_eq!(
                fs::read_dir(root.join("objects")).unwrap().count(),
                usize::from(committed)
            );
        }
    }

    #[test]
    fn injected_no_space_import_failures_recover_to_a_complete_state() {
        let temp = tempfile::tempdir().unwrap();
        let template = temp.path().join("template-vault");
        let template_store = VaultStore::new(template.clone());
        template_store.create(PASSWORD, "Jamie", 1).unwrap();
        template_store.lock();

        for (boundary, committed) in [
            ("object.after-chunk-write", false),
            ("manifest.before-first-write", false),
            ("manifest.after-first-write", true),
        ] {
            let root = temp.path().join(boundary);
            copy_directory(&template, &root);
            run_failure_child(&root, "import", boundary);

            let restarted = VaultStore::new(root.clone());
            restarted.unlock(PASSWORD).unwrap();
            assert_eq!(
                restarted.snapshot().unwrap().records.len(),
                usize::from(committed)
            );
            assert_eq!(fs::read_dir(root.join("staging")).unwrap().count(), 0);
            assert_eq!(
                fs::read_dir(root.join("objects")).unwrap().count(),
                usize::from(committed)
            );
        }
    }

    #[test]
    fn large_imports_keep_reader_requests_bounded_to_one_chunk() {
        const LARGE_FILE_SIZE: u64 = 101 * 1024 * 1024 + 17;

        let temp = tempfile::tempdir().unwrap();
        let object = temp.path().join("large.mcobj");
        let largest_request = Arc::new(AtomicUsize::new(0));
        let encrypted = encrypt_object(
            Box::new(GeneratedReader {
                remaining: LARGE_FILE_SIZE,
                largest_request: largest_request.clone(),
            }),
            &object,
            ObjectContext {
                vault_id: Uuid::new_v4(),
                record_id: Uuid::new_v4(),
                profile_id: Uuid::new_v4(),
            },
            &[1_u8; 32],
            &[2_u8; 32],
        )
        .unwrap();

        assert_eq!(encrypted.plaintext_size, LARGE_FILE_SIZE);
        assert_eq!(largest_request.load(Ordering::Relaxed), CHUNK_SIZE);
        assert!(fs::metadata(object).unwrap().len() > LARGE_FILE_SIZE);
    }

    #[test]
    fn locked_storage_contains_no_plaintext_canaries() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Canary Patient Name", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        store
            .import(
                profile,
                vec![],
                vec![source(
                    "canary-diagnosis.txt",
                    b"recognizable medical canary",
                )],
                2,
            )
            .unwrap();
        store.lock();

        for path in files_below(&root) {
            let contents = fs::read(path).unwrap();
            assert!(!contents
                .windows(b"Canary Patient Name".len())
                .any(|value| value == b"Canary Patient Name"));
            assert!(!contents
                .windows(b"canary-diagnosis".len())
                .any(|value| value == b"canary-diagnosis"));
            assert!(!contents
                .windows(b"recognizable medical canary".len())
                .any(|value| value == b"recognizable medical canary"));
        }
    }

    #[cfg(unix)]
    #[test]
    fn vault_directories_and_files_are_private_on_unix() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        store
            .import(
                profile,
                vec![],
                vec![source("permissions.pdf", b"synthetic permissions record")],
                2,
            )
            .unwrap();
        store.lock();

        for directory in [&root, &root.join("objects"), &root.join("staging")] {
            assert_eq!(
                fs::metadata(directory).unwrap().permissions().mode() & 0o777,
                0o700
            );
        }
        for path in files_below(&root) {
            assert_eq!(
                fs::metadata(path).unwrap().permissions().mode() & 0o777,
                0o600
            );
        }
    }

    #[test]
    fn unlock_removes_uncommitted_ciphertext_objects() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let orphan = root.join("objects").join("interrupted-import.mcobj");
        fs::write(&orphan, b"synthetic orphan ciphertext").unwrap();
        store.lock();

        store.unlock(PASSWORD).unwrap();

        assert!(!orphan.exists());
    }

    #[test]
    fn incomplete_vault_can_be_reset_and_recreated() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        fs::create_dir(&root).unwrap();
        let store = VaultStore::new(root.clone());

        assert_eq!(store.status(), VaultStatus::Locked);
        assert!(matches!(store.unlock(PASSWORD), Err(VaultError::Corrupt)));
        store.reset().unwrap();
        assert_eq!(store.status(), VaultStatus::Absent);
        store.create(PASSWORD, "Jamie", 1).unwrap();
        assert_eq!(store.status(), VaultStatus::Unlocked);
    }

    #[test]
    fn nested_folder_cycles_and_excessive_depth_are_rejected() {
        let temp = tempfile::tempdir().unwrap();
        let store = VaultStore::new(temp.path().join("vault"));
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let root = store.create_folder(profile, None, "Root", 2).unwrap();
        let child = store
            .create_folder(profile, Some(root), "Child", 3)
            .unwrap();
        assert!(matches!(
            store.update_folder(root, Some(child), "Root"),
            Err(VaultError::Invalid)
        ));
    }

    #[test]
    fn change_passphrase_only_rewraps_the_vault_key() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        store
            .change_passphrase(PASSWORD, "Replacement#9Pass")
            .unwrap();
        store.lock();
        assert!(matches!(
            store.unlock(PASSWORD),
            Err(VaultError::WrongPassphrase)
        ));
        store.unlock("Replacement#9Pass").unwrap();
    }

    #[test]
    fn corrupt_latest_manifest_falls_back_without_losing_imported_records() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let record = store
            .import(
                profile,
                vec![],
                vec![source("report.pdf", b"durable synthetic record")],
                2,
            )
            .unwrap()
            .imported[0];
        let latest_generation = {
            let guard = store.unlocked.lock().unwrap();
            guard.as_ref().unwrap().manifest.generation
        };
        store.lock();
        fs::write(
            manifest_path(&root, latest_generation),
            b"synthetic corruption",
        )
        .unwrap();

        let restarted = VaultStore::new(root);
        restarted.unlock(PASSWORD).unwrap();
        assert_eq!(restarted.snapshot().unwrap().records[0].id, record);
        let mut output = Vec::new();
        restarted.export(record, &mut output).unwrap();
        assert_eq!(output, b"durable synthetic record");
    }

    #[test]
    fn two_corrupt_manifest_slots_fail_closed_without_removing_ciphertext() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        store
            .import(
                profile,
                vec![],
                vec![source("report.pdf", b"preserve encrypted object")],
                2,
            )
            .unwrap();
        let object_path = {
            let guard = store.unlocked.lock().unwrap();
            root.join("objects")
                .join(&guard.as_ref().unwrap().manifest.records[0].object_name)
        };
        store.lock();
        fs::write(root.join("manifest-0.bin"), b"corrupt slot zero").unwrap();
        fs::write(root.join("manifest-1.bin"), b"corrupt slot one").unwrap();

        let restarted = VaultStore::new(root);
        assert!(matches!(
            restarted.unlock(PASSWORD),
            Err(VaultError::Corrupt)
        ));
        assert!(object_path.exists());
    }

    #[test]
    fn swapping_ciphertext_between_records_fails_authentication() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let records = store
            .import(
                profile,
                vec![],
                vec![
                    source("first.pdf", b"first synthetic record"),
                    source("second.pdf", b"second synthetic record"),
                ],
                2,
            )
            .unwrap()
            .imported;
        let paths = {
            let guard = store.unlocked.lock().unwrap();
            guard
                .as_ref()
                .unwrap()
                .manifest
                .records
                .iter()
                .map(|record| root.join("objects").join(&record.object_name))
                .collect::<Vec<_>>()
        };
        let first = fs::read(&paths[0]).unwrap();
        let second = fs::read(&paths[1]).unwrap();
        fs::write(&paths[0], second).unwrap();
        fs::write(&paths[1], first).unwrap();

        for record in records {
            assert!(matches!(
                store.export(record, io::sink()),
                Err(VaultError::Corrupt)
            ));
        }
    }

    #[test]
    fn atomic_export_preserves_an_existing_destination_on_corruption() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let destination = temp.path().join("export.pdf");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let record = store
            .import(
                profile,
                vec![],
                vec![source("report.pdf", b"authenticated synthetic record")],
                2,
            )
            .unwrap()
            .imported[0];

        let header_before = fs::read(root.join("header.json")).unwrap();
        assert!(matches!(
            store.export_atomic(record, &root.join("header.json")),
            Err(VaultError::Invalid)
        ));
        assert_eq!(fs::read(root.join("header.json")).unwrap(), header_before);

        fs::write(&destination, b"existing destination").unwrap();
        store.export_atomic(record, &destination).unwrap();
        assert_eq!(
            fs::read(&destination).unwrap(),
            b"authenticated synthetic record"
        );

        let object_name = {
            let guard = store.unlocked.lock().unwrap();
            guard.as_ref().unwrap().manifest.records[0]
                .object_name
                .clone()
        };
        let object = root.join("objects").join(object_name);
        let mut bytes = fs::read(&object).unwrap();
        *bytes.last_mut().unwrap() ^= 1;
        fs::write(object, bytes).unwrap();
        fs::write(&destination, b"do not overwrite").unwrap();

        assert!(matches!(
            store.export_atomic(record, &destination),
            Err(VaultError::Corrupt)
        ));
        assert_eq!(fs::read(destination).unwrap(), b"do not overwrite");
    }

    #[test]
    fn unlock_finishes_deletion_interrupted_after_its_first_manifest_commit() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        store
            .import(
                profile,
                vec![],
                vec![source("delete-me.pdf", b"synthetic document bytes")],
                2,
            )
            .unwrap();

        let (master_key, mut interrupted, object_path) = {
            let guard = store.unlocked.lock().unwrap();
            let unlocked = guard.as_ref().unwrap();
            let mut interrupted = unlocked.manifest.clone();
            let object_path = root
                .join("objects")
                .join(&interrupted.records[0].object_name);
            interrupted.records.clear();
            (*unlocked.master_key, interrupted, object_path)
        };
        interrupted.generation += 1;
        write_manifest_at(&root, &master_key, &interrupted).unwrap();
        assert!(object_path.exists());
        store.lock();

        let restarted = VaultStore::new(root);
        restarted.unlock(PASSWORD).unwrap();
        assert!(restarted.snapshot().unwrap().records.is_empty());
        assert!(!object_path.exists());
    }

    #[test]
    fn deletion_abrupt_termination_matrix_finishes_cryptographic_erasure() {
        let temp = tempfile::tempdir().unwrap();
        let template = temp.path().join("template-vault");
        let template_store = VaultStore::new(template.clone());
        template_store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = template_store.snapshot().unwrap().profiles[0].id;
        template_store
            .import(
                profile,
                vec![],
                vec![source("delete-me.pdf", b"synthetic deletion record")],
                2,
            )
            .unwrap();
        template_store.lock();

        for boundary in [
            "delete.after-first-manifest",
            "delete.after-object-unlink",
            "delete.after-second-manifest",
        ] {
            let root = temp.path().join(boundary);
            copy_directory(&template, &root);
            run_termination_child(&root, "delete", boundary);

            let restarted = VaultStore::new(root.clone());
            restarted.unlock(PASSWORD).unwrap();
            assert!(restarted.snapshot().unwrap().records.is_empty());
            assert_eq!(fs::read_dir(root.join("objects")).unwrap().count(), 0);
        }
    }

    #[test]
    fn injected_no_space_deletion_failures_recover_without_half_visible_records() {
        let temp = tempfile::tempdir().unwrap();
        let template = temp.path().join("template-vault");
        let template_store = VaultStore::new(template.clone());
        template_store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = template_store.snapshot().unwrap().profiles[0].id;
        template_store
            .import(
                profile,
                vec![],
                vec![source("delete-me.pdf", b"synthetic deletion record")],
                2,
            )
            .unwrap();
        template_store.lock();

        for (boundary, deleted) in [
            ("delete.before-first-manifest", false),
            ("delete.after-first-manifest", true),
            ("delete.after-object-unlink", true),
        ] {
            let root = temp.path().join(boundary);
            copy_directory(&template, &root);
            run_failure_child(&root, "delete", boundary);

            let restarted = VaultStore::new(root.clone());
            restarted.unlock(PASSWORD).unwrap();
            assert_eq!(restarted.snapshot().unwrap().records.is_empty(), deleted);
            assert_eq!(
                fs::read_dir(root.join("objects")).unwrap().count(),
                usize::from(!deleted)
            );
        }
    }

    #[test]
    fn deleting_a_record_removes_its_key_from_both_manifest_slots() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("vault");
        let store = VaultStore::new(root.clone());
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let record = store
            .import(
                profile,
                vec![],
                vec![source("delete-me.pdf", b"synthetic document bytes")],
                2,
            )
            .unwrap()
            .imported[0];
        let object_name = {
            let guard = store.unlocked.lock().unwrap();
            guard.as_ref().unwrap().manifest.records[0]
                .object_name
                .clone()
        };

        store.delete_record(record).unwrap();
        assert!(store.snapshot().unwrap().records.is_empty());
        assert!(!root.join("objects").join(object_name).exists());

        let (master_key, vault_id, latest_generation) = {
            let guard = store.unlocked.lock().unwrap();
            let unlocked = guard.as_ref().unwrap();
            (
                *unlocked.master_key,
                unlocked.manifest.vault_id,
                unlocked.manifest.generation,
            )
        };
        fs::write(
            manifest_path(&root, latest_generation),
            b"synthetic corruption",
        )
        .unwrap();
        let fallback = read_latest_manifest(&root, &master_key, vault_id).unwrap();
        assert!(fallback.records.is_empty());
    }

    #[test]
    fn deleting_a_missing_record_does_not_change_the_manifest() {
        let temp = tempfile::tempdir().unwrap();
        let store = VaultStore::new(temp.path().join("vault"));
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let before = {
            let guard = store.unlocked.lock().unwrap();
            guard.as_ref().unwrap().manifest.generation
        };

        assert!(matches!(
            store.delete_record(Uuid::new_v4()),
            Err(VaultError::NotFound)
        ));
        let after = {
            let guard = store.unlocked.lock().unwrap();
            guard.as_ref().unwrap().manifest.generation
        };
        assert_eq!(after, before);
    }

    #[test]
    fn excessive_import_batches_are_rejected_before_files_are_read() {
        let temp = tempfile::tempdir().unwrap();
        let store = VaultStore::new(temp.path().join("vault"));
        store.create(PASSWORD, "Jamie", 1).unwrap();
        let profile = store.snapshot().unwrap().profiles[0].id;
        let sources = (0..=MAX_IMPORT_FILES)
            .map(|index| source(&format!("record-{index}.pdf"), b"%PDF-synthetic"))
            .collect();
        assert!(matches!(
            store.import(profile, vec![], sources, 2),
            Err(VaultError::ImportBatchLimit)
        ));
        assert!(store.snapshot().unwrap().records.is_empty());
    }
}
