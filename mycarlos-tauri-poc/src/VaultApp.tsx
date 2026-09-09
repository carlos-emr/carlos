import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import App from "./App";
import {
  createVaultBridge,
  vaultErrorMessage,
  type VaultBridge,
  type VaultSnapshot,
  type VaultStatus,
} from "./vault";

const AUTO_LOCK_MS = 15 * 60 * 1000;

export interface VaultAppProps {
  bridge?: VaultBridge;
}

function bytes(value: number): string {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

const defaultBridge = createVaultBridge();

export default function VaultApp({ bridge = defaultBridge }: VaultAppProps) {
  if (!bridge.native) return <App />;
  return <NativeVault bridge={bridge} />;
}

function NativeVault({ bridge }: { bridge: VaultBridge }) {
  const [status, setStatus] = useState<VaultStatus | "loading">("loading");
  const [snapshot, setSnapshot] = useState<VaultSnapshot | null>(null);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const lock = useCallback(async () => {
    await bridge.lock();
    setSnapshot(null);
    setStatus("locked");
    setNotice("Vault locked.");
  }, [bridge]);

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
    let timer = window.setTimeout(lock, AUTO_LOCK_MS);
    const restart = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(lock, AUTO_LOCK_MS);
    };
    const onVisibility = () => {
      if (document.visibilityState === "hidden") void lock();
    };
    for (const event of ["pointerdown", "keydown", "touchstart"] as const) {
      window.addEventListener(event, restart, { passive: true });
    }
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.clearTimeout(timer);
      for (const event of ["pointerdown", "keydown", "touchstart"] as const) {
        window.removeEventListener(event, restart);
      }
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [lock, status]);

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
    return <main className="vault-auth"><p>Opening myCarlos…</p></main>;
  }

  if (status === "absent") {
    return <CreateVault busy={busy} notice={notice} onCreate={(profile, passphrase) => run(async () => {
      setSnapshot(await bridge.create(passphrase, profile));
      setStatus("unlocked");
      setNotice("Encrypted vault created. Keep your passphrase safe; it cannot be recovered.");
    })} />;
  }

  if (status === "locked" || !snapshot) {
    return <UnlockVault busy={busy} notice={notice} onUnlock={(passphrase) => run(async () => {
      setSnapshot(await bridge.unlock(passphrase));
      setStatus("unlocked");
      setNotice("Vault unlocked.");
    })} onReset={(confirmation) => run(async () => {
      await bridge.reset(confirmation);
      setStatus("absent");
      setNotice("");
    })} />;
  }

  return <VaultLibrary bridge={bridge} snapshot={snapshot} busy={busy} notice={notice}
    setNotice={setNotice} run={run} refresh={refresh} onLock={lock} />;
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
  return <main className="vault-auth">
    <section className="vault-card" aria-labelledby="create-title">
      <p className="vault-kicker">myCarlos private records</p>
      <h1 id="create-title">Create your encrypted vault</h1>
      <p>Your files and record details are encrypted on this device. Your passphrase is the only recovery method.</p>
      <form onSubmit={submit}>
        <label>First patient profile<input required maxLength={120} value={profile} onChange={(e) => setProfile(e.target.value)} /></label>
        <label>Passphrase<input required type="password" autoComplete="new-password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)} /></label>
        <small>At least 8 characters with uppercase, lowercase, a number, and a symbol.</small>
        <label>Confirm passphrase<input required type="password" autoComplete="new-password" value={confirmation} onChange={(e) => setConfirmation(e.target.value)} /></label>
        {confirmation && passphrase !== confirmation && <p role="alert">Passphrases do not match.</p>}
        {notice && <p role="status">{notice}</p>}
        <button className="button primary" disabled={busy || passphrase !== confirmation}>Create vault</button>
      </form>
    </section>
  </main>;
}

function UnlockVault({ busy, notice, onUnlock, onReset }: {
  busy: boolean;
  notice: string;
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
  return <main className="vault-auth">
    <section className="vault-card" aria-labelledby="unlock-title">
      <p className="vault-kicker">myCarlos private records</p>
      <h1 id="unlock-title">Unlock your vault</h1>
      <p>The vault locks after 15 minutes of inactivity and whenever the app is backgrounded.</p>
      <form onSubmit={submit}>
        <label>Passphrase<input autoFocus required type="password" autoComplete="current-password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)} /></label>
        {notice && <p role="status">{notice}</p>}
        <button className="button primary" disabled={busy}>Unlock</button>
      </form>
      <details className="vault-reset">
        <summary>Forgot your passphrase?</summary>
        <p>There is no recovery code. Reset permanently erases this vault so you can start again.</p>
        <label>Type RESET MYCARLOS VAULT<input value={resetText} onChange={(e) => setResetText(e.target.value)} /></label>
        <button className="button danger" disabled={busy || resetText !== "RESET MYCARLOS VAULT"} onClick={() => void onReset(resetText)}>Erase vault</button>
      </details>
    </section>
  </main>;
}

function VaultLibrary({ bridge, snapshot, busy, notice, setNotice, run, refresh, onLock }: {
  bridge: VaultBridge;
  snapshot: VaultSnapshot;
  busy: boolean;
  notice: string;
  setNotice: (value: string) => void;
  run: (operation: () => Promise<void>) => Promise<void>;
  refresh: () => Promise<void>;
  onLock: () => Promise<void>;
}) {
  const [profileId, setProfileId] = useState(snapshot.profiles[0]?.id ?? "");
  const [selectedFolders, setSelectedFolders] = useState<string[]>([]);
  const [profileName, setProfileName] = useState("");
  const [folderName, setFolderName] = useState("");
  const [parentId, setParentId] = useState("");
  const [currentPassphrase, setCurrentPassphrase] = useState("");
  const [newPassphrase, setNewPassphrase] = useState("");
  const [resetText, setResetText] = useState("");
  const folders = useMemo(() => snapshot.folders.filter((folder) => folder.profileId === profileId), [snapshot, profileId]);
  const records = useMemo(() => snapshot.records.filter((record) => record.profileId === profileId), [snapshot, profileId]);
  const folderNameById = useMemo(() => new Map(folders.map((folder) => [folder.id, folder.name])), [folders]);

  const toggleImportFolder = (id: string) => setSelectedFolders((current) =>
    current.includes(id) ? current.filter((candidate) => candidate !== id) : [...current, id]);

  const importFiles = () => run(async () => {
    const outcome = await bridge.importFiles(profileId, selectedFolders);
    if (!outcome.imported.length && !outcome.skippedDuplicates.length) {
      setNotice("No files selected. Nothing changed.");
      return;
    }
    await refresh();
    const skipped = outcome.skippedDuplicates.length ? ` ${outcome.skippedDuplicates.length} duplicate(s) skipped.` : "";
    setNotice(`${outcome.imported.length} file(s) encrypted and imported.${skipped}`);
  });

  return <main className="vault-shell">
    <header className="vault-topbar">
      <div><p className="vault-kicker">Encrypted local vault</p><h1>My records</h1></div>
      <button className="button" disabled={busy} onClick={() => void onLock()}>Lock now</button>
    </header>
    <p className="vault-boundary">Synthetic-data development build. Do not use real patient information until security, privacy, signing, and release gates are approved.</p>
    {notice && <p className="vault-notice" role="status">{notice}</p>}

    <section className="vault-toolbar" aria-label="Import controls">
      <label>Patient profile<select value={profileId} onChange={(e) => { setProfileId(e.target.value); setSelectedFolders([]); }}>
        {snapshot.profiles.map((profile) => <option key={profile.id} value={profile.id}>{profile.displayName}</option>)}
      </select></label>
      <fieldset><legend>Import into folders (optional)</legend>
        {folders.length ? folders.map((folder) => <label key={folder.id} className="vault-check"><input type="checkbox" checked={selectedFolders.includes(folder.id)} onChange={() => toggleImportFolder(folder.id)} />{folder.name}</label>) : <small>No folders yet</small>}
      </fieldset>
      <button className="button primary" disabled={busy || !profileId} onClick={importFiles}>Choose files to import</button>
    </section>

    <section className="vault-records" aria-labelledby="records-title">
      <h2 id="records-title">Stored records</h2>
      {!records.length && <p>No encrypted records in this profile yet.</p>}
      {records.map((record) => <article className="vault-record" key={record.id}>
        <div><h3>{record.displayName}</h3><p>{bytes(record.plaintextSize)} · {record.sourceLabel}</p><small>Imported {new Date(record.importedAtMs).toLocaleString()}</small></div>
        <details><summary>Organize</summary>
          {folders.length ? folders.map((folder) => <label key={folder.id} className="vault-check"><input type="checkbox" checked={record.folderIds.includes(folder.id)} onChange={(event) => void run(async () => {
            const ids = event.target.checked ? [...record.folderIds, folder.id] : record.folderIds.filter((id) => id !== folder.id);
            await bridge.assignFolders(record.id, ids); await refresh(); setNotice("Folder assignments updated.");
          })} />{folder.name}</label>) : <small>Create a folder first.</small>}
        </details>
        <button className="button" disabled={busy} onClick={() => {
          if (!window.confirm("Exporting creates a plaintext copy outside the encrypted vault. Continue?")) return;
          void run(async () => setNotice(await bridge.exportFile(record.id, record.displayName) ? "Plaintext copy exported." : "Export cancelled. Nothing changed."));
        }}>Export copy</button>
        {!!record.folderIds.length && <p className="vault-tags">{record.folderIds.map((id) => folderNameById.get(id)).filter(Boolean).join(" · ")}</p>}
      </article>)}
    </section>

    <aside className="vault-settings" aria-label="Vault settings">
      <details open><summary>Profiles and folders</summary>
        <form onSubmit={(event) => { event.preventDefault(); const name = profileName; setProfileName(""); void run(async () => { await bridge.createProfile(name); await refresh(); setNotice("Profile created."); }); }}>
          <label>New profile name<input required maxLength={120} value={profileName} onChange={(e) => setProfileName(e.target.value)} /></label><button className="button" disabled={busy}>Add profile</button>
        </form>
        <form onSubmit={(event) => { event.preventDefault(); const name = folderName; setFolderName(""); void run(async () => { await bridge.createFolder(profileId, parentId || null, name); await refresh(); setNotice("Folder created."); }); }}>
          <label>New folder name<input required maxLength={120} value={folderName} onChange={(e) => setFolderName(e.target.value)} /></label>
          <label>Parent folder<select value={parentId} onChange={(e) => setParentId(e.target.value)}><option value="">None</option>{folders.map((folder) => <option key={folder.id} value={folder.id}>{folder.name}</option>)}</select></label>
          <button className="button" disabled={busy}>Add folder</button>
        </form>
      </details>
      <details><summary>Security</summary>
        <form onSubmit={(event) => { event.preventDefault(); const current = currentPassphrase; const replacement = newPassphrase; setCurrentPassphrase(""); setNewPassphrase(""); void run(async () => { await bridge.changePassphrase(current, replacement); setNotice("Passphrase changed."); }); }}>
          <label>Current passphrase<input required type="password" autoComplete="current-password" value={currentPassphrase} onChange={(e) => setCurrentPassphrase(e.target.value)} /></label>
          <label>New passphrase<input required type="password" autoComplete="new-password" value={newPassphrase} onChange={(e) => setNewPassphrase(e.target.value)} /></label>
          <button className="button" disabled={busy}>Change passphrase</button>
        </form>
        <p>Reset permanently erases the entire vault and every profile.</p>
        <label>Type RESET MYCARLOS VAULT<input value={resetText} onChange={(e) => setResetText(e.target.value)} /></label>
        <button className="button danger" disabled={busy || resetText !== "RESET MYCARLOS VAULT"} onClick={() => void run(async () => { await bridge.reset(resetText); window.location.reload(); })}>Erase entire vault</button>
      </details>
    </aside>
  </main>;
}
