import { useCallback, useEffect, useMemo, useRef, useState, type DragEvent as ReactDragEvent, type FormEvent, type ReactNode } from "react";
import App, { Icon, type IconName } from "./App";
import {
  createVaultBridge,
  vaultErrorMessage,
  type VaultBridge,
  type VaultFolder,
  type VaultRecord,
  type VaultSnapshot,
  type VaultStatus,
} from "./vault";

const DEFAULT_AUTO_LOCK_MINUTES = 5;
const AUTO_LOCK_STORAGE_KEY = "mycarlos.autoLockMinutes.v1";
const AUTO_LOCK_OPTIONS = Array.from({ length: 15 }, (_, index) => index + 1);

function normalizeAutoLockMinutes(value: unknown): number {
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 15
    ? parsed
    : DEFAULT_AUTO_LOCK_MINUTES;
}

function readAutoLockMinutes(): number {
  try {
    return normalizeAutoLockMinutes(window.localStorage.getItem(AUTO_LOCK_STORAGE_KEY));
  } catch {
    return DEFAULT_AUTO_LOCK_MINUTES;
  }
}

function persistAutoLockMinutes(value: number): void {
  try {
    window.localStorage.setItem(AUTO_LOCK_STORAGE_KEY, String(value));
  } catch {
    // The clamped in-memory setting remains active when webview storage is unavailable.
  }
}

export interface VaultAppProps {
  bridge?: VaultBridge;
}

function bytes(value: number): string {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

type NativeSection = "records" | "security";
type NativeView = "list" | "grid";
type NativeSort = "newest" | "name";
type DragItem = { kind: "records"; ids: string[] } | { kind: "folder"; id: string };

function recordKind(name: string): { icon: IconName; label: string } {
  const normalized = name.toLocaleLowerCase();
  if (normalized.includes("blood") || normalized.includes("test") || normalized.includes("lab")) {
    return { icon: "flask", label: "Test result" };
  }
  if (normalized.includes("image") || normalized.includes("x-ray") || normalized.includes("scan")) {
    return { icon: "image", label: "Imaging" };
  }
  if (normalized.includes("prescription") || normalized.includes("medication")) {
    return { icon: "pill", label: "Prescription" };
  }
  return { icon: "letter", label: "Document" };
}

const defaultBridge = createVaultBridge();

function VaultAuthFrame({ state, children }: { state: "Locked" | "Setting up" | "Opening"; children: ReactNode }) {
  return <div className="evaluation-page native-vault-page">
    <div className="evaluation-banner" role="note"><strong>Synthetic-data development build</strong><span>Do not use real patient information</span></div>
    <div className="page-wrap native-auth-wrap">
      <section className="app-window native-auth-window" aria-label="myCarlos private vault">
        <header className="titlebar"><span className="window-dots" aria-hidden="true"><i /><i /><i /></span><span className="window-title">myCarlos</span><span className="native-auth-state"><Icon name="shield" /> {state}</span></header>
        <main className="vault-auth">{children}</main>
      </section>
    </div>
  </div>;
}

export default function VaultApp({ bridge = defaultBridge }: VaultAppProps) {
  if (!bridge.native) return <App />;
  return <NativeVault bridge={bridge} />;
}

function NativeVault({ bridge }: { bridge: VaultBridge }) {
  const [status, setStatus] = useState<VaultStatus | "loading">("loading");
  const [snapshot, setSnapshot] = useState<VaultSnapshot | null>(null);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [concealed, setConcealed] = useState(false);
  const [autoLockMinutes, setAutoLockMinutes] = useState(readAutoLockMinutes);
  const lockingRef = useRef(false);

  const lock = useCallback(async () => {
    if (lockingRef.current) return;
    lockingRef.current = true;
    try {
      await bridge.lock();
      setSnapshot(null);
      setConcealed(false);
      setStatus("locked");
      setNotice("Vault locked.");
    } finally {
      lockingRef.current = false;
    }
  }, [bridge]);

  const requestLock = useCallback((concealImmediately = false) => {
    if (concealImmediately) setConcealed(true);
    void lock().catch((error) => setNotice(vaultErrorMessage(error)));
  }, [lock]);

  useEffect(() => {
    let active = true;
    bridge.status().then(async (next) => {
      if (next === "unlocked") {
        const current = await bridge.snapshot();
        if (active) setSnapshot(current);
      }
      if (active) setStatus(next);
    }).catch((error) => {
        if (active) {
          setNotice(vaultErrorMessage(error));
          setStatus("locked");
        }
      });
    return () => { active = false; };
  }, [bridge]);

  useEffect(() => {
    if (status !== "unlocked") return;
    const delayMs = autoLockMinutes * 60 * 1000;
    let timer = window.setTimeout(requestLock, delayMs);
    const restart = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(requestLock, delayMs);
    };
    const onVisibility = () => {
      if (document.visibilityState === "hidden") requestLock(true);
    };
    for (const event of ["pointerdown", "keydown", "touchstart"] as const) {
      window.addEventListener(event, restart, { passive: true });
    }
    document.addEventListener("visibilitychange", onVisibility);
    if (document.visibilityState === "hidden") requestLock(true);
    return () => {
      window.clearTimeout(timer);
      for (const event of ["pointerdown", "keydown", "touchstart"] as const) {
        window.removeEventListener(event, restart);
      }
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [autoLockMinutes, requestLock, status]);

  const updateAutoLockMinutes = (value: unknown) => {
    const normalized = normalizeAutoLockMinutes(value);
    setAutoLockMinutes(normalized);
    persistAutoLockMinutes(normalized);
    setNotice(`Automatic locking set to ${normalized} minute${normalized === 1 ? "" : "s"}.`);
  };

  const run = async (operation: () => Promise<void>) => {
    setBusy(true);
    setNotice("");
    try {
      await operation();
    } catch (error) {
      setNotice(vaultErrorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const refresh = async () => setSnapshot(await bridge.snapshot());

  if (status === "loading") {
    return <VaultAuthFrame state="Opening"><p>Opening myCarlos…</p></VaultAuthFrame>;
  }

  if (status === "absent") {
    return <CreateVault busy={busy} notice={notice} onCreate={(profile, passphrase) => run(async () => {
      setSnapshot(await bridge.create(passphrase, profile));
      setConcealed(false);
      setStatus("unlocked");
      setNotice("Encrypted vault created. Keep your passphrase safe; it cannot be recovered.");
    })} />;
  }

  if (status === "locked" || !snapshot) {
    return <UnlockVault busy={busy} notice={notice} autoLockMinutes={autoLockMinutes} onUnlock={(passphrase) => run(async () => {
      const current = await bridge.unlock(passphrase);
      setSnapshot(current);
      setConcealed(false);
      setStatus("unlocked");
      setNotice(current.degraded ? "Vault unlocked in read-only recovery mode. Export important records and free storage before unlocking again." : "Vault unlocked.");
    })} onReset={(confirmation) => run(async () => {
      if (await bridge.reset(confirmation)) {
        setStatus("absent");
        setNotice("");
      } else {
        setNotice("Vault erase cancelled. Nothing changed.");
      }
    })} />;
  }

  if (concealed) {
    return <VaultAuthFrame state="Locked"><p>Vault content is hidden while myCarlos finishes locking…</p></VaultAuthFrame>;
  }

  return <VaultLibrary bridge={bridge} snapshot={snapshot} busy={busy} notice={notice}
    setNotice={setNotice} run={run} refresh={refresh} onLock={lock}
    autoLockMinutes={autoLockMinutes} onAutoLockMinutes={updateAutoLockMinutes} />;
}

function CreateVault({ busy, notice, onCreate }: {
  busy: boolean;
  notice: string;
  onCreate: (profile: string, passphrase: string) => Promise<void>;
}) {
  const [profile, setProfile] = useState("");
  const [passphrase, setPassphrase] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (passphrase !== confirmation) return;
    const secret = passphrase;
    setPassphrase("");
    setConfirmation("");
    void onCreate(profile, secret);
  };
  return <VaultAuthFrame state="Setting up">
    <section className="vault-card" aria-labelledby="create-title">
      <p className="vault-kicker">myCarlos private records</p>
      <h1 id="create-title">Create your encrypted vault</h1>
      <p>Your files and record details are encrypted on this device. Your passphrase is the only recovery method.</p>
      <form onSubmit={submit}>
        <label>First patient profile<input required maxLength={120} value={profile} onChange={(e) => setProfile(e.target.value)} /></label>
        <label>Passphrase<input required maxLength={1024} type="password" autoComplete="new-password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)} /></label>
        <small>Use at least 15 characters. Spaces are allowed; common passwords, names, and predictable patterns are rejected locally.</small>
        <label>Confirm passphrase<input required maxLength={1024} type="password" autoComplete="new-password" value={confirmation} onChange={(e) => setConfirmation(e.target.value)} /></label>
        {confirmation && passphrase !== confirmation && <p role="alert">Passphrases do not match.</p>}
        {notice && <p role="status">{notice}</p>}
        <button className="button primary" disabled={busy || passphrase !== confirmation}>Create vault</button>
      </form>
    </section>
  </VaultAuthFrame>;
}

function UnlockVault({ busy, notice, autoLockMinutes, onUnlock, onReset }: {
  busy: boolean;
  notice: string;
  autoLockMinutes: number;
  onUnlock: (passphrase: string) => Promise<void>;
  onReset: (confirmation: string) => Promise<void>;
}) {
  const [passphrase, setPassphrase] = useState("");
  const [resetText, setResetText] = useState("");
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const secret = passphrase;
    setPassphrase("");
    void onUnlock(secret);
  };
  return <VaultAuthFrame state="Locked">
    <section className="vault-card" aria-labelledby="unlock-title">
      <p className="vault-kicker">myCarlos private records</p>
      <h1 id="unlock-title">Unlock your vault</h1>
      <p>The vault locks after {autoLockMinutes} minute{autoLockMinutes === 1 ? "" : "s"} of inactivity and whenever the app is backgrounded.</p>
      <form onSubmit={submit}>
        <label>Passphrase<input autoFocus required maxLength={1024} type="password" autoComplete="current-password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)} /></label>
        {notice && <p role="status">{notice}</p>}
        <button className="button primary" disabled={busy}>Unlock</button>
      </form>
      <details className="vault-reset">
        <summary>Forgot your passphrase?</summary>
        <p>There is no recovery code. Reset permanently erases this vault so you can start again.</p>
        <label>Type RESET MYCARLOS VAULT<input maxLength={21} value={resetText} onChange={(e) => setResetText(e.target.value)} /></label>
        <button className="button danger" disabled={busy || resetText !== "RESET MYCARLOS VAULT"} onClick={() => void onReset(resetText)}>Erase vault</button>
      </details>
    </section>
  </VaultAuthFrame>;
}

function VaultLibrary({ bridge, snapshot, busy, notice, setNotice, run, refresh, onLock, autoLockMinutes, onAutoLockMinutes }: {
  bridge: VaultBridge;
  snapshot: VaultSnapshot;
  busy: boolean;
  notice: string;
  setNotice: (value: string) => void;
  run: (operation: () => Promise<void>) => Promise<void>;
  refresh: () => Promise<void>;
  onLock: () => Promise<void>;
  autoLockMinutes: number;
  onAutoLockMinutes: (value: unknown) => void;
}) {
  const [profileId, setProfileId] = useState(snapshot.profiles[0]?.id ?? "");
  const [section, setSection] = useState<NativeSection>("records");
  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [view, setView] = useState<NativeView>("list");
  const [sort, setSort] = useState<NativeSort>("newest");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [moveFolderId, setMoveFolderId] = useState("");
  const [activeRecordId, setActiveRecordId] = useState<string | null>(null);
  const [showFolderForm, setShowFolderForm] = useState(false);
  const [dragItem, setDragItem] = useState<DragItem | null>(null);
  const [dropTarget, setDropTarget] = useState<string | null>(null);
  const [profileName, setProfileName] = useState("");
  const [folderName, setFolderName] = useState("");
  const [currentPassphrase, setCurrentPassphrase] = useState("");
  const [newPassphrase, setNewPassphrase] = useState("");
  const [newPassphraseConfirmation, setNewPassphraseConfirmation] = useState("");
  const [resetText, setResetText] = useState("");
  const [folderToMoveId, setFolderToMoveId] = useState("");
  const [folderDestinationId, setFolderDestinationId] = useState("");
  const dialogRef = useRef<HTMLElement | null>(null);
  const dialogReturnFocusRef = useRef<HTMLElement | null>(null);
  const profile = snapshot.profiles.find((candidate) => candidate.id === profileId) ?? snapshot.profiles[0];
  const folders = useMemo(
    () => snapshot.folders.filter((folder) => folder.profileId === profileId),
    [snapshot.folders, profileId],
  );
  const records = useMemo(
    () => snapshot.records.filter((record) => record.profileId === profileId),
    [snapshot.records, profileId],
  );
  const currentFolder = folders.find((folder) => folder.id === currentFolderId) ?? null;
  const folderNameById = useMemo(
    () => new Map(folders.map((folder) => [folder.id, folder.name])),
    [folders],
  );
  const sidebarFolders = useMemo(() => {
    const children = new Map<string | null, VaultFolder[]>();
    for (const folder of folders) {
      const siblings = children.get(folder.parentId) ?? [];
      siblings.push(folder);
      children.set(folder.parentId, siblings);
    }
    for (const siblings of children.values()) siblings.sort((left, right) => left.name.localeCompare(right.name));
    const tree: Array<{ folder: VaultFolder; depth: number }> = [];
    const visited = new Set<string>();
    const visit = (parentId: string | null, depth: number) => {
      for (const folder of children.get(parentId) ?? []) {
        if (visited.has(folder.id)) continue;
        visited.add(folder.id);
        tree.push({ folder, depth });
        visit(folder.id, depth + 1);
      }
    };
    visit(null, 0);
    return tree;
  }, [folders]);
  const normalizedQuery = query.trim().toLocaleLowerCase();
  const visibleFolders = useMemo(
    () => folders
      .filter((folder) => folder.parentId === currentFolderId)
      .filter((folder) => !normalizedQuery || folder.name.toLocaleLowerCase().includes(normalizedQuery))
      .sort((left, right) => left.name.localeCompare(right.name)),
    [currentFolderId, folders, normalizedQuery],
  );
  const visibleRecords = useMemo(() => {
    const matching = records.filter((record) => {
      const inFolder = currentFolderId
        ? record.folderIds.includes(currentFolderId)
        : record.folderIds.length === 0;
      return inFolder && (!normalizedQuery || record.displayName.toLocaleLowerCase().includes(normalizedQuery));
    });
    return matching.sort((left, right) => sort === "name"
      ? left.displayName.localeCompare(right.displayName)
      : right.importedAtMs - left.importedAtMs);
  }, [currentFolderId, normalizedQuery, records, sort]);
  const selectedRecords = records.filter((record) => selectedIds.includes(record.id));
  const activeRecord = records.find((record) => record.id === activeRecordId) ?? null;
  const readOnly = snapshot.degraded;

  useEffect(() => {
    if (profile && profile.id !== profileId) setProfileId(profile.id);
  }, [profile, profileId]);

  useEffect(() => {
    setCurrentFolderId(null);
    setSelectedIds([]);
    setQuery("");
  }, [profileId]);

  useEffect(() => {
    setSelectedIds([]);
    setQuery("");
  }, [currentFolderId]);

  useEffect(() => {
    if (!activeRecordId) return;
    dialogReturnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const dialog = dialogRef.current;
    const focusable = () => Array.from(dialog?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), select:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ) ?? []);
    focusable()[0]?.focus();
    const containFocus = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setActiveRecordId(null);
        return;
      }
      if (event.key !== "Tab") return;
      const controls = focusable();
      if (!controls.length) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", containFocus);
    return () => {
      window.removeEventListener("keydown", containFocus);
      dialogReturnFocusRef.current?.focus();
    };
  }, [activeRecordId]);

  const importFiles = () => run(async () => {
    const outcome = await bridge.importFiles(profileId, currentFolderId ? [currentFolderId] : []);
    if (!outcome.imported.length && !outcome.skippedDuplicates.length) {
      setNotice("No files selected. Nothing changed.");
      return;
    }
    await refresh();
    const skipped = outcome.skippedDuplicates.length ? ` ${outcome.skippedDuplicates.length} duplicate(s) skipped.` : "";
    setNotice(`${outcome.imported.length} file(s) encrypted and imported.${skipped}`);
  });

  const toggleSelected = (id: string) => setSelectedIds((current) =>
    current.includes(id) ? current.filter((candidate) => candidate !== id) : [...current, id]);

  const moveSelected = () => run(async () => {
    await bridge.assignFoldersBatch(selectedRecords.map((record) => record.id), moveFolderId ? [moveFolderId] : []);
    await refresh();
    setSelectedIds([]);
    setNotice(`${selectedRecords.length} document${selectedRecords.length === 1 ? "" : "s"} moved.`);
  });

  const startDrag = (event: ReactDragEvent, item: DragItem) => {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("application/x-mycarlos-item", JSON.stringify(item));
    setDragItem(item);
  };

  const readDragItem = (event: ReactDragEvent): DragItem | null => {
    if (dragItem) return dragItem;
    try {
      const value = JSON.parse(event.dataTransfer.getData("application/x-mycarlos-item")) as DragItem;
      if (value.kind === "folder" && typeof value.id === "string") return value;
      if (value.kind === "records" && Array.isArray(value.ids) && value.ids.every((id) => typeof id === "string")) return value;
    } catch {
      return null;
    }
    return null;
  };

  const dragOver = (event: ReactDragEvent, zone: string) => {
    if (!readDragItem(event)) return;
    event.preventDefault();
    event.stopPropagation();
    event.dataTransfer.dropEffect = "move";
    setDropTarget(zone);
  };

  const dropInto = (event: ReactDragEvent, folderId: string | null) => {
    event.preventDefault();
    event.stopPropagation();
    const item = readDragItem(event);
    setDropTarget(null);
    setDragItem(null);
    if (!item) return;
    void run(async () => {
      if (item.kind === "records") {
        await bridge.assignFoldersBatch(item.ids, folderId ? [folderId] : []);
        setSelectedIds([]);
        setNotice(`${item.ids.length} document${item.ids.length === 1 ? "" : "s"} moved to ${folderId ? folderNameById.get(folderId) : "My records"}.`);
      } else {
        const folder = folders.find((candidate) => candidate.id === item.id);
        if (!folder || folder.id === folderId) {
          setNotice("A folder cannot be moved into itself.");
          return;
        }
        await bridge.updateFolder(folder.id, folderId, folder.name);
        setNotice(`${folder.name} moved to ${folderId ? folderNameById.get(folderId) : "My records"}.`);
      }
      await refresh();
    });
  };

  const dragEnd = () => {
    setDragItem(null);
    setDropTarget(null);
  };

  const recordDragItem = (recordId: string): DragItem => ({
    kind: "records",
    ids: selectedIds.includes(recordId) ? selectedRecords.map((record) => record.id) : [recordId],
  });

  const dragClass = (base: string, item: DragItem) => {
    const dragging = item.kind === "folder"
      ? dragItem?.kind === "folder" && dragItem.id === item.id
      : dragItem?.kind === "records" && item.ids.some((id) => dragItem.ids.includes(id));
    return `${base} native-draggable${dragging ? " native-dragging" : ""}`;
  };

  const dropClass = (base: string, zone: string) =>
    `${base}${dropTarget === zone ? " native-drop-target" : ""}`;

  const exportRecord = (record: VaultRecord) => {
    if (!window.confirm("Saving creates a readable file outside the encrypted vault. myCarlos cannot erase that copy later, and deleting this record will not remove it. Continue?")) return;
    void run(async () => setNotice(await bridge.exportFile(record.id)
      ? "A readable copy was saved to this computer."
      : "Save cancelled. Nothing changed."));
  };

  const deleteRecord = (record: VaultRecord) => {
    if (!window.confirm(`Permanently delete ${record.displayName} from this vault? This cannot be undone here. Readable exports and the clinic's source medical record are not deleted.`)) return;
    void run(async () => {
      await bridge.deleteRecord(record.id);
      setActiveRecordId(null);
      setSelectedIds((current) => current.filter((id) => id !== record.id));
      await refresh();
      setNotice(`${record.displayName} was permanently deleted.`);
    });
  };

  const folderCount = (id: string) => records.filter((record) => record.folderIds.includes(id)).length;
  const locationTitle = currentFolder?.name ?? "My records";

  return <div className="evaluation-page native-vault-page">
    <div className="evaluation-banner" role="note">
      <strong>Synthetic-data development build</strong>
      <span>Files and record details are encrypted and saved on this device</span>
    </div>
    <div className="page-wrap">
      <section className="app-window" aria-label="myCarlos encrypted record library">
        <header className="titlebar" inert={activeRecord ? true : undefined}>
          <span className="window-dots" aria-hidden="true"><i /><i /><i /></span>
          <span className="window-title">myCarlos</span>
          <button className="unlock-pill native-lock-button" type="button" disabled={busy} onClick={() => void onLock()}>
            <Icon name="lock-open" /> Unlocked · Lock now
          </button>
        </header>

        <label className="mobile-section-picker" inert={activeRecord ? true : undefined}>
          <span>Section</span>
          <select aria-label="Section" value={section} onChange={(event) => setSection(event.target.value as NativeSection)}>
            <option value="records">My records</option>
            <option value="security">Security</option>
          </select>
        </label>

        <div className="app-body" inert={activeRecord ? true : undefined}>
          <aside className="sidebar">
            <div className="brand">
              <span className="brand-mark"><Icon name="activity" /></span>
              <span><strong>myCarlos</strong><small>{profile?.displayName ?? "Private vault"}</small></span>
            </div>
            <nav className="side-nav" aria-label="Record library">
              <button className={dropClass(section === "records" && !currentFolderId ? "selected" : "", "sidebar:root")} type="button" onClick={() => { setSection("records"); setCurrentFolderId(null); }} onDragOver={(event) => dragOver(event, "sidebar:root")} onDrop={(event) => dropInto(event, null)}>
                <Icon name="folder" /> My records <span className="nav-count">{records.filter((record) => !record.folderIds.length).length}</span>
              </button>
              <span className="nav-label">Folders</span>
              {sidebarFolders.map(({ folder, depth }) => <button className={dropClass(dragClass(currentFolderId === folder.id ? "selected" : "", { kind: "folder", id: folder.id }), `sidebar:${folder.id}`)} style={{ paddingLeft: `${10 + Math.min(depth, 8) * 14}px` }} type="button" key={folder.id} draggable={!busy && !readOnly} onDragStart={(event) => startDrag(event, { kind: "folder", id: folder.id })} onDragEnd={dragEnd} onDragOver={(event) => dragOver(event, `sidebar:${folder.id}`)} onDrop={(event) => dropInto(event, folder.id)} onClick={() => { setSection("records"); setCurrentFolderId(folder.id); }}>
                <Icon name="folder" /> <span className="native-nav-name">{folder.name}</span><span className="nav-count">{folderCount(folder.id)}</span>
              </button>)}
              {!folders.length && <small className="native-sidebar-empty">No folders yet</small>}
              <span className="nav-label">Settings</span>
              <button className={section === "security" ? "selected" : ""} type="button" onClick={() => setSection("security")}><Icon name="shield" /> Security</button>
            </nav>
            <div className="storage">
              <span>Encrypted local vault</span>
              <div className="storage-meter"><span style={{ width: `${Math.min(100, Math.max(8, records.length * 4))}%` }} /></div>
              <small>{records.length} document{records.length === 1 ? "" : "s"} · {folders.length} folder{folders.length === 1 ? "" : "s"}</small>
            </div>
          </aside>

          {section === "records" && <main className={dropClass("library-main", `pane:${currentFolderId ?? "root"}`)} onDragOver={(event) => dragOver(event, `pane:${currentFolderId ?? "root"}`)} onDrop={(event) => dropInto(event, currentFolderId)}>
            <div className="native-profile-row">
              <button className={dropClass("breadcrumb native-breadcrumb", "breadcrumb:root")} type="button" onDragOver={(event) => dragOver(event, "breadcrumb:root")} onDrop={(event) => dropInto(event, null)} onClick={() => setCurrentFolderId(null)}>My records</button>
              {currentFolder && <><span aria-hidden="true">/</span><strong>{currentFolder.name}</strong></>}
              <label className="native-profile-select"><span>Patient</span><select value={profileId} onChange={(event) => setProfileId(event.target.value)}>{snapshot.profiles.map((item) => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label>
            </div>
            <div className="main-head">
              <div><h1>{locationTitle}</h1><p>{visibleFolders.length} folders · {visibleRecords.length} documents in this location</p></div>
              <div className="head-actions">
                <button className="button" type="button" disabled={readOnly} onClick={() => setShowFolderForm((current) => !current)}><Icon name="folder-plus" /><span>New folder</span></button>
                <button className="button primary" type="button" disabled={busy || readOnly || !profileId} onClick={() => void importFiles()} aria-label="Choose files to import"><Icon name="plus" /><span>{busy ? "Working…" : "New"}</span></button>
              </div>
            </div>

            {showFolderForm && <form className="native-inline-form" onSubmit={(event) => { event.preventDefault(); const name = folderName; setFolderName(""); void run(async () => { await bridge.createFolder(profileId, currentFolderId, name); await refresh(); setShowFolderForm(false); setNotice(`${name} was created.`); }); }}>
              <label>Folder name<input autoFocus required maxLength={120} value={folderName} onChange={(event) => setFolderName(event.target.value)} /></label>
              <button className="button primary" disabled={busy || readOnly}>Create</button>
              <button className="button" type="button" onClick={() => setShowFolderForm(false)}>Cancel</button>
            </form>}

            {folders.length > 0 && <details className="native-folder-move"><summary>Move a folder with the keyboard</summary><div><label>Folder<select value={folderToMoveId} onChange={(event) => setFolderToMoveId(event.target.value)}><option value="">Choose a folder</option>{folders.map((folder) => <option value={folder.id} key={folder.id}>{folder.name}</option>)}</select></label><label>Destination<select value={folderDestinationId} onChange={(event) => setFolderDestinationId(event.target.value)}><option value="">My records</option>{folders.filter((folder) => folder.id !== folderToMoveId).map((folder) => <option value={folder.id} key={folder.id}>{folder.name}</option>)}</select></label><button className="button" type="button" disabled={busy || readOnly || !folderToMoveId} onClick={() => void run(async () => { const folder = folders.find((candidate) => candidate.id === folderToMoveId); if (!folder) return; await bridge.updateFolder(folder.id, folderDestinationId || null, folder.name); await refresh(); setNotice(`${folder.name} moved.`); })}>Move folder</button></div></details>}

            {snapshot.degraded && <div className="purpose-note warning" role="alert"><Icon name="info" /><span><strong>Read-only recovery mode.</strong> A redundant vault metadata copy could not be repaired. Export important records and free storage; the vault will reject changes until it can repair itself on a later unlock.</span></div>}

            <label className="search"><Icon name="search" /><span className="sr-only">Search this location</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search this location" /></label>
            <div className="viewbar">
              <span className="native-location-chip">{currentFolder ? "Inside folder" : "Top level"}</span>
              <span className="native-drag-hint">Hold and drag a document or folder to move it</span>
              <div className="view-controls">
                <label className="native-sort"><Icon name="sort" /><span className="sr-only">Sort records</span><select value={sort} onChange={(event) => setSort(event.target.value as NativeSort)}><option value="newest">Newest first</option><option value="name">Name A–Z</option></select></label>
                <span className="segment" aria-label="Choose record view">
                  <button className={view === "list" ? "selected" : ""} type="button" aria-label="List view" aria-pressed={view === "list"} onClick={() => setView("list")}><Icon name="list" /></button>
                  <button className={view === "grid" ? "selected" : ""} type="button" aria-label="Grid view" aria-pressed={view === "grid"} onClick={() => setView("grid")}><Icon name="grid" /></button>
                </span>
              </div>
            </div>

            {selectedIds.length > 0 && <div className="bulkbar" role="status">
              <strong>{selectedIds.length} selected</strong><span>Move the selected documents.</span>
              <div className="bulk-actions native-move-actions"><select aria-label="Move selected to" value={moveFolderId} onChange={(event) => setMoveFolderId(event.target.value)}><option value="">My records</option>{folders.map((folder) => <option value={folder.id} key={folder.id}>{folder.name}</option>)}</select><button type="button" disabled={busy || readOnly} onClick={() => void moveSelected()}>Move</button><button type="button" onClick={() => setSelectedIds([])}>Clear</button></div>
            </div>}
            <p className="status-line" role="status" aria-live="polite">{notice}</p>

            {view === "list" ? <div className="filelist native-filelist" aria-label={`${visibleFolders.length + visibleRecords.length} visible library items`}>
              <div className="file-head" aria-hidden="true"><span /><span className="sorted">Name</span><span className="column">Source</span><span className="column">Date added</span><span /></div>
              {visibleFolders.map((folder) => <article aria-label={`${folder.name} folder`} className={dropClass(dragClass("file-row", { kind: "folder", id: folder.id }), `list:${folder.id}`)} key={folder.id} draggable={!busy && !readOnly} onDragStart={(event) => startDrag(event, { kind: "folder", id: folder.id })} onDragEnd={dragEnd} onDragOver={(event) => dragOver(event, `list:${folder.id}`)} onDrop={(event) => dropInto(event, folder.id)}>
                <span />
                <button className="file-name native-file-open" type="button" onClick={() => setCurrentFolderId(folder.id)}><span className="document-icon folder"><Icon name="folder" /></span><span className="name-copy"><strong>{folder.name}</strong><small>{folderCount(folder.id)} items</small></span></button>
                <span className="column">—</span><span className="column">Folder</span>
                <button className="more-button" type="button" aria-label={`Open ${folder.name}`} onClick={() => setCurrentFolderId(folder.id)}><Icon name="more" /></button>
              </article>)}
              {visibleRecords.map((record) => { const kind = recordKind(record.displayName); const item = recordDragItem(record.id); return <article aria-label={`${record.displayName} document`} className={dragClass("file-row", item)} key={record.id} draggable={!busy && !readOnly} onDragStart={(event) => startDrag(event, item)} onDragEnd={dragEnd}>
                <button className={`check ${selectedIds.includes(record.id) ? "checked" : ""}`} type="button" aria-label={`Select ${record.displayName}`} aria-pressed={selectedIds.includes(record.id)} onClick={() => toggleSelected(record.id)} />
                <div className="file-name"><span className={`document-icon ${kind.icon}`}><Icon name={kind.icon} /></span><span className="name-copy"><button className="record-open" type="button" onClick={() => setActiveRecordId(record.id)}>{record.displayName}</button><small>{bytes(record.plaintextSize)} · {kind.label}</small></span></div>
                <span className="column">{record.sourceLabel}</span><span className="column">{new Date(record.importedAtMs).toLocaleDateString()}</span>
                <button className="more-button" type="button" aria-label={`More options for ${record.displayName}`} onClick={() => setActiveRecordId(record.id)}><Icon name="more" /></button>
              </article>; })}
              {!visibleFolders.length && !visibleRecords.length && <div className="empty-state"><Icon name="folder" /><strong>{query ? "No matching records" : "This location is empty"}</strong><span>{query ? "Try another search." : "Add a folder or import a document here."}</span></div>}
            </div> : <div className="file-grid" aria-label={`${visibleFolders.length + visibleRecords.length} visible library items`}>
              {visibleFolders.map((folder) => <article aria-label={`${folder.name} folder`} className={dropClass(dragClass("file-tile", { kind: "folder", id: folder.id }), `grid:${folder.id}`)} key={folder.id} draggable={!busy && !readOnly} onDragStart={(event) => startDrag(event, { kind: "folder", id: folder.id })} onDragEnd={dragEnd} onDragOver={(event) => dragOver(event, `grid:${folder.id}`)} onDrop={(event) => dropInto(event, folder.id)}><button className="tile-open" type="button" onClick={() => setCurrentFolderId(folder.id)}><span className="tile-preview folder"><Icon name="folder" /></span><span className="tile-caption"><span><strong>{folder.name}</strong><small>{folderCount(folder.id)} items</small></span></span></button></article>)}
              {visibleRecords.map((record) => { const kind = recordKind(record.displayName); const item = recordDragItem(record.id); return <article aria-label={`${record.displayName} document`} className={dragClass("file-tile", item)} key={record.id} draggable={!busy && !readOnly} onDragStart={(event) => startDrag(event, item)} onDragEnd={dragEnd}><button className="tile-open" type="button" onClick={() => setActiveRecordId(record.id)}><span className="tile-preview paper-preview"><i /><i /><i /><i /></span><span className="tile-caption"><span className={`document-icon ${kind.icon}`}><Icon name={kind.icon} /></span><span><strong>{record.displayName}</strong><small>{new Date(record.importedAtMs).toLocaleDateString()}</small></span></span></button></article>; })}
              {!visibleFolders.length && !visibleRecords.length && <div className="empty-state"><Icon name="folder" /><strong>{query ? "No matching records" : "This location is empty"}</strong><span>{query ? "Try another search." : "Add a folder or import a document here."}</span></div>}
            </div>}
          </main>}

          {section === "security" && <main className="library-main purpose-screen">
            <div className="main-head"><div><h1>Security</h1><p>Your encrypted vault and access settings</p></div></div>
            <div className="purpose-note warning"><Icon name="info" /><span><strong>Development build.</strong> Use synthetic files only. Recovery and backup are not implemented.</span></div>
            <p className="status-line" role="status" aria-live="polite">{notice}</p>
            <div className="setting-list">
              <section className="setting-row"><div><h2>Encryption <span className="state-pill">On — always</span></h2><p>Files, names, folders, and record details are encrypted on this device.</p></div></section>
              <section className="setting-row native-setting-form"><div><h2>Lock automatically <span className="state-pill">{autoLockMinutes} minute{autoLockMinutes === 1 ? "" : "s"}</span></h2><p>The vault locks after the selected period without keyboard, pointer, or touch activity. This non-medical setting is clamped to 1–15 minutes.</p></div><div><label>Automatic lock delay<select value={autoLockMinutes} onChange={(event) => onAutoLockMinutes(event.target.value)}>{AUTO_LOCK_OPTIONS.map((minutes) => <option value={minutes} key={minutes}>{minutes} minute{minutes === 1 ? "" : "s"}</option>)}</select></label><button className="button" type="button" onClick={() => void onLock()}>Lock now</button></div></section>
              <section className="setting-row native-setting-form"><div><h2>Change passphrase</h2><p>Use at least 15 characters and avoid common names or predictable phrases. Until recovery kits are implemented, forgetting the new passphrase permanently loses access.</p></div><form onSubmit={(event) => { event.preventDefault(); if (newPassphrase !== newPassphraseConfirmation) return; const current = currentPassphrase; const replacement = newPassphrase; setCurrentPassphrase(""); setNewPassphrase(""); setNewPassphraseConfirmation(""); void run(async () => { await bridge.changePassphrase(current, replacement); await refresh(); setNotice("Passphrase changed."); }); }}><label>Current passphrase<input required maxLength={1024} type="password" autoComplete="current-password" value={currentPassphrase} onChange={(event) => setCurrentPassphrase(event.target.value)} /></label><label>New passphrase<input required maxLength={1024} type="password" autoComplete="new-password" value={newPassphrase} onChange={(event) => setNewPassphrase(event.target.value)} /></label><label>Confirm new passphrase<input required maxLength={1024} type="password" autoComplete="new-password" value={newPassphraseConfirmation} onChange={(event) => setNewPassphraseConfirmation(event.target.value)} /></label>{newPassphraseConfirmation && newPassphrase !== newPassphraseConfirmation && <p role="alert">New passphrases do not match.</p>}<button className="button" disabled={busy || readOnly || newPassphrase !== newPassphraseConfirmation}>Change passphrase</button></form></section>
              <section className="setting-row"><div><h2>Readable copies and screenshots</h2><p>Exports and screenshots leave vault protection. Deleting a record from myCarlos cannot erase those copies or the clinic's source medical record.</p></div></section>
              <section className="setting-row native-setting-form"><div><h2>Patient profiles</h2><p>Keep each person’s filing cabinet separate inside this vault.</p></div><form onSubmit={(event) => { event.preventDefault(); const name = profileName; setProfileName(""); void run(async () => { await bridge.createProfile(name); await refresh(); setNotice(`${name} was added.`); }); }}><label>New profile name<input required maxLength={120} value={profileName} onChange={(event) => setProfileName(event.target.value)} /></label><button className="button" disabled={busy || readOnly}>Add profile</button></form></section>
              <section className="setting-row native-danger-setting"><div><h2>Erase entire vault</h2><p>Permanently deletes every encrypted document, profile, and folder. This cannot be undone.</p></div><details><summary>Show reset controls</summary><label>Type RESET MYCARLOS VAULT<input maxLength={21} value={resetText} onChange={(event) => setResetText(event.target.value)} /></label><button className="button danger" disabled={busy || resetText !== "RESET MYCARLOS VAULT"} onClick={() => void run(async () => { if (await bridge.reset(resetText)) window.location.reload(); else setNotice("Vault erase cancelled. Nothing changed."); })}>Erase entire vault</button></details></section>
            </div>
          </main>}
        </div>

        {activeRecord && <div className="dialog-backdrop" role="presentation" onMouseDown={() => setActiveRecordId(null)}><section ref={dialogRef} className="record-dialog" role="dialog" aria-modal="true" aria-labelledby="native-record-title" onMouseDown={(event) => event.stopPropagation()}>
          <header className="dialog-head"><div><span className="eyebrow">Encrypted document</span><h2 id="native-record-title">{activeRecord.displayName}</h2></div><button className="dialog-close" type="button" aria-label="Close document details" onClick={() => setActiveRecordId(null)}>×</button></header>
          <div className="document-preview" aria-label="Encrypted document details"><span className={`document-icon ${recordKind(activeRecord.displayName).icon}`}><Icon name={recordKind(activeRecord.displayName).icon} /></span><div className="preview-paper" aria-hidden="true"><i /><i /><i /><i /><i /></div><p>The file stays encrypted in the vault. Save a copy only when you need a readable file outside myCarlos.</p></div>
          <dl className="record-metadata"><div><dt>Kind</dt><dd>{recordKind(activeRecord.displayName).label}</dd></div><div><dt>Source</dt><dd>{activeRecord.sourceLabel}</dd></div><div><dt>Date added</dt><dd>{new Date(activeRecord.importedAtMs).toLocaleString()}</dd></div><div><dt>File</dt><dd>{bytes(activeRecord.plaintextSize)}</dd></div><div><dt>Folder</dt><dd>{activeRecord.folderIds.map((id) => folderNameById.get(id)).filter(Boolean).join(", ") || "My records"}</dd></div></dl>
          <footer className="dialog-actions native-dialog-actions"><label>Move to<select disabled={readOnly} value={activeRecord.folderIds[0] ?? ""} onChange={(event) => void run(async () => { await bridge.assignFolders(activeRecord.id, event.target.value ? [event.target.value] : []); await refresh(); setNotice("Document moved."); })}><option value="">My records</option>{folders.map((folder) => <option value={folder.id} key={folder.id}>{folder.name}</option>)}</select></label><button className="button danger" type="button" disabled={busy || readOnly} onClick={() => deleteRecord(activeRecord)}>Permanently delete</button><button className="button primary" type="button" disabled={busy} onClick={() => exportRecord(activeRecord)}>Save a copy to this computer</button></footer>
        </section></div>}
      </section>
    </div>
  </div>;
}
