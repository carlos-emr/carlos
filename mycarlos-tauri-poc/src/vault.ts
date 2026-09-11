import { invoke } from "@tauri-apps/api/core";

export type VaultStatus = "absent" | "locked" | "unlocked";

export interface PatientProfile {
  id: string;
  displayName: string;
  createdAtMs: number;
}

export interface VaultFolder {
  id: string;
  profileId: string;
  parentId: string | null;
  name: string;
  createdAtMs: number;
}

export interface VaultRecord {
  id: string;
  profileId: string;
  folderIds: string[];
  displayName: string;
  sourceLabel: string;
  mediaType: string;
  plaintextSize: number;
  importedAtMs: number;
}

export interface VaultSnapshot {
  profiles: PatientProfile[];
  folders: VaultFolder[];
  records: VaultRecord[];
  degraded: boolean;
}

export interface ImportOutcome {
  imported: string[];
  skippedDuplicates: string[];
}

export interface VaultBridge {
  readonly native: boolean;
  status(): Promise<VaultStatus>;
  create(passphrase: string, initialProfileName: string): Promise<VaultSnapshot>;
  unlock(passphrase: string): Promise<VaultSnapshot>;
  lock(): Promise<void>;
  snapshot(): Promise<VaultSnapshot>;
  changePassphrase(currentPassphrase: string, newPassphrase: string): Promise<void>;
  createProfile(displayName: string): Promise<string>;
  createFolder(profileId: string, parentId: string | null, name: string): Promise<string>;
  updateFolder(folderId: string, parentId: string | null, name: string): Promise<void>;
  assignFolders(recordId: string, folderIds: string[]): Promise<void>;
  assignFoldersBatch(recordIds: string[], folderIds: string[]): Promise<void>;
  importFiles(profileId: string, folderIds: string[]): Promise<ImportOutcome>;
  exportFile(recordId: string): Promise<boolean>;
  deleteRecord(recordId: string): Promise<void>;
  reset(confirmation: string): Promise<boolean>;
}

interface PublicError {
  code?: string;
  message?: string;
}

export function vaultErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null && "message" in error) {
    const message = (error as PublicError).message;
    if (typeof message === "string") return message;
  }
  return "The vault operation could not be completed.";
}

function isNativeRuntime(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

export function createVaultBridge(): VaultBridge {
  const native = isNativeRuntime();
  return {
    native,
    status: () => invoke<VaultStatus>("vault_status"),
    create: (passphrase, initialProfileName) =>
      invoke<VaultSnapshot>("vault_create", { request: { passphrase, initialProfileName } }),
    unlock: (passphrase) => invoke<VaultSnapshot>("vault_unlock", { request: { passphrase } }),
    lock: () => invoke<void>("vault_lock"),
    snapshot: () => invoke<VaultSnapshot>("vault_snapshot"),
    changePassphrase: (currentPassphrase, newPassphrase) =>
      invoke<void>("vault_change_passphrase", {
        request: { currentPassphrase, newPassphrase },
      }),
    createProfile: (displayName) =>
      invoke<string>("vault_create_profile", { request: { displayName } }),
    createFolder: (profileId, parentId, name) =>
      invoke<string>("vault_create_folder", { request: { profileId, parentId, name } }),
    updateFolder: (folderId, parentId, name) =>
      invoke<void>("vault_update_folder", { request: { folderId, parentId, name } }),
    assignFolders: (recordId, folderIds) =>
      invoke<void>("vault_assign_folders", { request: { recordId, folderIds } }),
    assignFoldersBatch: (recordIds, folderIds) =>
      invoke<void>("vault_assign_folders_batch", { request: { recordIds, folderIds } }),
    importFiles: (profileId, folderIds) =>
      invoke<ImportOutcome>("vault_import_begin", { request: { profileId, folderIds } }),
    exportFile: (recordId) =>
      invoke<boolean>("vault_export_begin", { request: { recordId } }),
    deleteRecord: (recordId) =>
      invoke<void>("vault_delete_record", { request: { recordId } }),
    reset: (confirmation) => invoke<boolean>("vault_reset", { request: { confirmation } }),
  };
}
