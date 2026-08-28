import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  createPlatformBridge,
  type PlatformBridge,
  type RuntimeInfo,
  type SelectedDocument,
} from "./platform";

type Category = "Test results" | "Letters" | "Imaging" | "Prescriptions" | "Other";
type Filter = "All" | Category;
type ViewMode = "list" | "grid";
type IconName =
  | "activity"
  | "camera"
  | "clock"
  | "flask"
  | "folder"
  | "folder-plus"
  | "grid"
  | "heart"
  | "image"
  | "info"
  | "letter"
  | "list"
  | "lock-open"
  | "more"
  | "pill"
  | "plus"
  | "reset"
  | "search"
  | "shield"
  | "sort"
  | "star"
  | "trash";

interface FolderItem {
  id: string;
  title: string;
  subtitle: string;
  date: string;
  sessionOnly?: boolean;
}

interface DemoDocument {
  id: string;
  title: string;
  subtitle: string;
  source: string;
  added: string;
  category: Category;
  icon: IconName;
  sessionOnly?: boolean;
}

const sampleFolders: FolderItem[] = [
  { id: "folder-heart", title: "Heart & blood pressure", subtitle: "9 items", date: "Renamed 2 Aug 2026" },
  { id: "folder-results", title: "Test results 2026", subtitle: "14 items", date: "9 Jul 2026" },
  { id: "folder-old", title: "Old records from Dr. Chen", subtitle: "11 items", date: "Mar 2019" },
];

const sampleDocuments: DemoDocument[] = [
  {
    id: "sample-bloodwork",
    title: "Bloodwork — cholesterol and liver panel",
    subtitle: "2 pages · PDF",
    source: "Maple Creek Medical",
    added: "12 Aug 2026",
    category: "Test results",
    icon: "flask",
  },
  {
    id: "sample-cardiology",
    title: "Specialist letter — cardiology",
    subtitle: "2 pages · PDF",
    source: "Maple Creek Medical",
    added: "19 Aug 2026",
    category: "Letters",
    icon: "letter",
  },
  {
    id: "sample-xray",
    title: "Chest X-ray report",
    subtitle: "1 page · PDF",
    source: "Riverside Imaging",
    added: "1 Aug 2026",
    category: "Imaging",
    icon: "image",
  },
  {
    id: "sample-prescription",
    title: "Prescription — ramipril 5mg",
    subtitle: "1 page · PDF",
    source: "Maple Creek Medical",
    added: "22 Jul 2026",
    category: "Prescriptions",
    icon: "pill",
  },
  {
    id: "sample-photo",
    title: "Letter from physio (photo)",
    subtitle: "1 page · JPEG",
    source: "Added by you",
    added: "14 Jul 2026",
    category: "Other",
    icon: "camera",
  },
];

const filters: Filter[] = ["All", "Test results", "Letters", "Imaging"];

function Icon({ name }: { name: IconName }) {
  let content: ReactNode;

  switch (name) {
    case "heart":
      content = <path d="M20.8 4.7a5.5 5.5 0 0 0-7.8 0L12 5.8l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.5a5.5 5.5 0 0 0 0-7.8Z" />;
      break;
    case "activity":
      content = <><path d="M3 12h4l2.5-7 5 14 2.5-7h4" /><path d="M20.8 4.7a5.5 5.5 0 0 0-7.8 0L12 5.8l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.5a5.5 5.5 0 0 0 0-7.8Z" /></>;
      break;
    case "folder":
      content = <path d="M3 6.5h6l2 2H21v9.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />;
      break;
    case "folder-plus":
      content = <><path d="M3 7h6l2 2h10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" /><path d="M12 12v5M9.5 14.5h5" /></>;
      break;
    case "clock":
      content = <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3.5 2" /></>;
      break;
    case "star":
      content = <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9Z" />;
      break;
    case "trash":
      content = <><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" /></>;
      break;
    case "flask":
      content = <><path d="M9 3h6M10 3v6l-5 9a2 2 0 0 0 1.8 3h10.4A2 2 0 0 0 19 18l-5-9V3" /><path d="M7.5 15h9" /></>;
      break;
    case "letter":
      content = <><path d="M4 7h16v12H4Z" /><path d="m4 8 8 6 8-6M8 4h8" /></>;
      break;
    case "image":
      content = <><rect x="4" y="4" width="16" height="16" rx="2" /><circle cx="9" cy="9" r="1.5" /><path d="m5 18 5-5 3 3 2-2 4 4" /></>;
      break;
    case "pill":
      content = <><path d="M7.2 5.2a3 3 0 0 1 4.2 0l1.4 1.4a3 3 0 0 1 0 4.2l-2 2a3 3 0 0 1-4.2 0l-1.4-1.4a3 3 0 0 1 0-4.2Z" /><path d="m7 10 3-3M14 13l5 5M13 17l4-4" /></>;
      break;
    case "shield":
      content = <path d="M12 3 5 6v5c0 4.7 2.9 8 7 10 4.1-2 7-5.3 7-10V6Z" />;
      break;
    case "plus":
      content = <path d="M12 5v14M5 12h14" />;
      break;
    case "search":
      content = <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></>;
      break;
    case "sort":
      content = <><path d="M8 5v14M5 16l3 3 3-3M14 7h6M14 12h4M14 17h2" /></>;
      break;
    case "list":
      content = <><path d="M9 6h11M9 12h11M9 18h11" /><circle cx="4" cy="6" r="1" /><circle cx="4" cy="12" r="1" /><circle cx="4" cy="18" r="1" /></>;
      break;
    case "grid":
      content = <><rect x="4" y="4" width="6" height="6" /><rect x="14" y="4" width="6" height="6" /><rect x="4" y="14" width="6" height="6" /><rect x="14" y="14" width="6" height="6" /></>;
      break;
    case "more":
      content = <><circle cx="12" cy="5" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="12" cy="19" r="1" /></>;
      break;
    case "lock-open":
      content = <><rect x="5" y="10" width="14" height="10" rx="2" /><path d="M9 10V7a4 4 0 0 1 7.5-2" /></>;
      break;
    case "camera":
      content = <><path d="M4 8h4l1.5-2h5L16 8h4v11H4Z" /><circle cx="12" cy="13" r="3" /></>;
      break;
    case "info":
      content = <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7h.01" /></>;
      break;
    case "reset":
      content = <path d="M4 9V4l3 3a8 8 0 1 1-2 8" />;
      break;
  }

  return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">{content}</svg>;
}

function formatBytes(size?: number): string {
  if (size === undefined) return "PDF";
  if (size < 1024) return `${size} B · PDF`;
  return `${Math.round(size / 1024)} KB · PDF`;
}

function toDemoDocument(file: SelectedDocument): DemoDocument {
  return {
    id: `session-${crypto.randomUUID()}`,
    title: file.name,
    subtitle: formatBytes(file.sizeBytes),
    source: "Chosen on this device",
    added: "Just now",
    category: "Letters",
    icon: "letter",
    sessionOnly: true,
  };
}

function isPdf(file: SelectedDocument): boolean {
  return file.name.toLocaleLowerCase().endsWith(".pdf");
}

export interface AppProps {
  bridge?: PlatformBridge;
}

const defaultBridge = createPlatformBridge();

export default function App({ bridge = defaultBridge }: AppProps) {
  const [folders, setFolders] = useState(sampleFolders);
  const [documents, setDocuments] = useState(sampleDocuments);
  const [runtime, setRuntime] = useState<RuntimeInfo | null>(null);
  const [runtimeError, setRuntimeError] = useState(false);
  const [importing, setImporting] = useState(false);
  const [notice, setNotice] = useState("Showing synthetic records for evaluation.");
  const [filter, setFilter] = useState<Filter>("All");
  const [query, setQuery] = useState("");
  const [view, setView] = useState<ViewMode>("list");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  useEffect(() => {
    let active = true;
    bridge
      .getRuntimeInfo()
      .then((info) => active && setRuntime(info))
      .catch(() => active && setRuntimeError(true));
    return () => {
      active = false;
    };
  }, [bridge]);

  const sessionDocumentCount = useMemo(
    () => documents.filter((document) => document.sessionOnly).length,
    [documents],
  );
  const sessionFolderCount = useMemo(
    () => folders.filter((folder) => folder.sessionOnly).length,
    [folders],
  );

  const filteredDocuments = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    return documents.filter((document) => {
      const matchesFilter = filter === "All" || document.category === filter;
      const matchesQuery =
        normalizedQuery.length === 0 ||
        `${document.title} ${document.source} ${document.category}`
          .toLocaleLowerCase()
          .includes(normalizedQuery);
      return matchesFilter && matchesQuery;
    });
  }, [documents, filter, query]);

  const filteredFolders = useMemo(() => {
    if (filter !== "All") return [];
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) return folders;
    return folders.filter((folder) => folder.title.toLocaleLowerCase().includes(normalizedQuery));
  }, [filter, folders, query]);

  const visibleCount = filteredFolders.length + filteredDocuments.length;
  const hasSessionChanges = sessionDocumentCount + sessionFolderCount > 0;

  async function chooseDocument() {
    setImporting(true);
    try {
      const selected = await bridge.selectPdf();
      if (!selected) {
        setNotice("No file selected. Nothing changed.");
        return;
      }
      if (!isPdf(selected)) {
        setNotice("This evaluation accepts PDF files only.");
        return;
      }
      setDocuments((current) => [toDemoDocument(selected), ...current]);
      setFilter("All");
      setQuery("");
      setNotice(`${selected.name} was added for this session only.`);
    } catch {
      setNotice("The file picker could not be opened. No file was accessed.");
    } finally {
      setImporting(false);
    }
  }

  function createFolder() {
    const number = sessionFolderCount + 1;
    setFolders((current) => [
      ...current,
      {
        id: `session-folder-${crypto.randomUUID()}`,
        title: `New sample folder ${number}`,
        subtitle: "Empty folder",
        date: "Created just now",
        sessionOnly: true,
      },
    ]);
    setFilter("All");
    setQuery("");
    setNotice(`New sample folder ${number} was created for this session only.`);
  }

  function resetEvaluation() {
    setFolders(sampleFolders);
    setDocuments(sampleDocuments);
    setFilter("All");
    setQuery("");
    setSelectedIds([]);
    setNotice("Evaluation reset. Only the built-in sample records are shown.");
  }

  function toggleSelected(id: string) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((selectedId) => selectedId !== id) : [...current, id],
    );
  }

  function setCategory(nextFilter: Filter) {
    setFilter(nextFilter);
    setSelectedIds([]);
  }

  function showPlaceholder(label: string) {
    setNotice(`${label} is shown for visual evaluation but is not implemented in this slice.`);
  }

  return (
    <div className="evaluation-page">
      <div className="evaluation-banner" role="note">
        <strong>Technology evaluation only</strong>
        <span>Synthetic files only · nothing is encrypted or saved</span>
      </div>

      <div className="page-wrap">
        <section className="app-window" aria-label="MyVitalHistory record library evaluation">
          <header className="titlebar">
            <span className="window-dots" aria-hidden="true"><i /><i /><i /></span>
            <span className="window-title">MyVitalHistory</span>
            <span className="unlock-pill"><Icon name="lock-open" /> Unlocked</span>
          </header>

          <div className="app-body">
            <aside className="sidebar">
              <div className="brand">
                <span className="brand-mark"><Icon name="activity" /></span>
                <span>
                  <strong>MyVitalHistory</strong>
                  <small>Sample patient</small>
                </span>
              </div>

              <nav className="side-nav" aria-label="Record library">
                <button className={filter === "All" ? "selected" : ""} type="button" onClick={() => setCategory("All")}>
                  <Icon name="folder" /> My records
                </button>
                <button type="button" onClick={() => showPlaceholder("Recent records")}><Icon name="clock" /> Recent</button>
                <button type="button" onClick={() => showPlaceholder("Starred records")}><Icon name="star" /> Starred <span className="nav-count">3</span></button>
                <button type="button" onClick={() => showPlaceholder("Trash")}><Icon name="trash" /> Trash <span className="nav-count">2</span></button>

                <span className="nav-label">By kind</span>
                <button className={filter === "Test results" ? "selected" : ""} type="button" onClick={() => setCategory("Test results")}>
                  <Icon name="flask" /> Test results <span className="nav-count">18</span>
                </button>
                <button className={filter === "Letters" ? "selected" : ""} type="button" onClick={() => setCategory("Letters")}>
                  <Icon name="letter" /> Letters <span className="nav-count">11</span>
                </button>
                <button className={filter === "Imaging" ? "selected" : ""} type="button" onClick={() => setCategory("Imaging")}>
                  <Icon name="image" /> Imaging <span className="nav-count">6</span>
                </button>
                <button className={filter === "Prescriptions" ? "selected" : ""} type="button" onClick={() => setCategory("Prescriptions")}><Icon name="pill" /> Prescriptions <span className="nav-count">7</span></button>

                <span className="nav-label">Settings</span>
                <button type="button" onClick={() => showPlaceholder("Security and backup")}><Icon name="shield" /> Security & backup</button>
                <button type="button" onClick={() => showPlaceholder("Health data")}><Icon name="heart" /> Health data</button>
              </nav>

              <div className="storage">
                <span>Backup not connected</span>
                <div className="storage-meter"><span /></div>
                <small>Evaluation data stays in this session</small>
              </div>
            </aside>

            <main className="library-main">
              <div className="breadcrumb"><strong>My records</strong></div>
              <div className="main-head">
                <div>
                  <h1>My records</h1>
                  <p>{folders.length} folders · {documents.length} documents · sample data</p>
                </div>
                <div className="head-actions">
                  <button className="button" type="button" onClick={createFolder}>
                    <Icon name="folder-plus" /><span>New folder</span>
                  </button>
                  <button
                    className="button primary"
                    type="button"
                    onClick={chooseDocument}
                    disabled={importing}
                    aria-label="New document — choose a sample PDF"
                  >
                    <Icon name="plus" /><span>{importing ? "Opening…" : "New"}</span>
                  </button>
                </div>
              </div>

              <label className="search">
                <Icon name="search" />
                <span className="sr-only">Search your records</span>
                <input
                  type="search"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search your records"
                />
              </label>

              <div className="viewbar">
                <div className="filter-chips" aria-label="Filter records">
                  {filters.map((option) => (
                    <button
                      className={`chip ${filter === option ? "selected" : ""}`}
                      type="button"
                      aria-pressed={filter === option}
                      onClick={() => setCategory(option)}
                      key={option}
                    >
                      {option}
                    </button>
                  ))}
                </div>
                <div className="view-controls">
                  <button className="sort-button" type="button"><Icon name="sort" /> Newest first <span>⌄</span></button>
                  <span className="segment" aria-label="Choose record view">
                    <button
                      className={view === "list" ? "selected" : ""}
                      type="button"
                      aria-label="List view"
                      aria-pressed={view === "list"}
                      onClick={() => setView("list")}
                    ><Icon name="list" /></button>
                    <button
                      className={view === "grid" ? "selected" : ""}
                      type="button"
                      aria-label="Grid view"
                      aria-pressed={view === "grid"}
                      onClick={() => setView("grid")}
                    ><Icon name="grid" /></button>
                  </span>
                </div>
              </div>

              {selectedIds.length > 0 && (
                <div className="bulkbar" role="status">
                  <strong>{selectedIds.length} selected</strong>
                  <span>Selection is visual only in this evaluation.</span>
                  <button type="button" onClick={() => setSelectedIds([])}>Clear</button>
                </div>
              )}

              <p className="status-line" role="status" aria-live="polite">{notice}</p>

              {view === "list" ? (
                <div className="filelist" aria-label={`${visibleCount} visible library items`}>
                  <div className="file-head" aria-hidden="true">
                    <span />
                    <span className="sorted">Name ↑</span>
                    <span className="column">Sent by</span>
                    <span className="column">Date added</span>
                    <span />
                  </div>

                  {filteredFolders.map((folder) => (
                    <article className="file-row" key={folder.id}>
                      <button
                        className={`check ${selectedIds.includes(folder.id) ? "checked" : ""}`}
                        type="button"
                        aria-label={`Select ${folder.title}`}
                        aria-pressed={selectedIds.includes(folder.id)}
                        onClick={() => toggleSelected(folder.id)}
                      />
                      <div className="file-name">
                        <span className="document-icon folder"><Icon name="folder" /></span>
                        <span className="name-copy"><strong>{folder.title}</strong><small>{folder.subtitle}</small></span>
                      </div>
                      <span className="column">—</span>
                      <span className="column">{folder.date}</span>
                      <button className="more-button" type="button" aria-label={`More options for ${folder.title}`}><Icon name="more" /></button>
                    </article>
                  ))}

                  {filteredDocuments.map((document) => (
                    <article className="file-row" key={document.id}>
                      <button
                        className={`check ${selectedIds.includes(document.id) ? "checked" : ""}`}
                        type="button"
                        aria-label={`Select ${document.title}`}
                        aria-pressed={selectedIds.includes(document.id)}
                        onClick={() => toggleSelected(document.id)}
                      />
                      <div className="file-name">
                        <span className={`document-icon ${document.icon}`}><Icon name={document.icon} /></span>
                        <span className="name-copy">
                          <strong>{document.title}</strong>
                          <small>{document.subtitle}{document.sessionOnly ? " · session only" : ""}</small>
                        </span>
                      </div>
                      <span className="column">{document.source}</span>
                      <span className="column">{document.added}</span>
                      <button className="more-button" type="button" aria-label={`More options for ${document.title}`}><Icon name="more" /></button>
                    </article>
                  ))}

                  {visibleCount === 0 && (
                    <div className="empty-state"><Icon name="search" /><strong>No matching records</strong><span>Try another search or filter.</span></div>
                  )}
                </div>
              ) : (
                <div className="file-grid" aria-label={`${visibleCount} visible library items`}>
                  {filteredFolders.map((folder) => (
                    <article className="file-tile" key={folder.id}>
                      <span className="tile-preview folder"><Icon name="folder" /></span>
                      <div className="tile-caption"><strong>{folder.title}</strong><small>{folder.subtitle}</small></div>
                    </article>
                  ))}
                  {filteredDocuments.map((document) => (
                    <article className="file-tile" key={document.id}>
                      <span className="tile-preview paper-preview"><i /><i /><i /><i /></span>
                      <div className="tile-caption"><span className={`document-icon ${document.icon}`}><Icon name={document.icon} /></span><span><strong>{document.title}</strong><small>{document.added}</small></span></div>
                    </article>
                  ))}
                  {visibleCount === 0 && (
                    <div className="empty-state"><Icon name="search" /><strong>No matching records</strong><span>Try another search or filter.</span></div>
                  )}
                </div>
              )}
            </main>
          </div>
        </section>

        <details className="evaluation-details">
          <summary><Icon name="info" /> Evaluation details <span>View the Tauri boundary and safety scope</span></summary>
          <div className="evaluation-content">
            <section>
              <h2>Hello from Tauri</h2>
              {runtime ? (
                <>
                  <p className="runtime-message">{runtime.message}</p>
                  <dl>
                    <div><dt>Runtime</dt><dd>{runtime.platform}</dd></div>
                    <div><dt>Architecture</dt><dd>{runtime.architecture}</dd></div>
                    <div><dt>App version</dt><dd>{runtime.appVersion}</dd></div>
                    <div><dt>Bridge</dt><dd>{runtime.native ? "Rust command" : "Browser preview"}</dd></div>
                  </dl>
                </>
              ) : runtimeError ? (
                <p className="runtime-error" role="alert">The runtime information command was unavailable.</p>
              ) : (
                <p>Checking the application shell…</p>
              )}
            </section>
            <section>
              <h2>Evaluation boundaries</h2>
              <ul>
                <li>The picker returns only a filename; this POC does not read the file.</li>
                <li>No accounts, CARLOS connection, encryption, backup, or persistence.</li>
                <li>Desktop and mobile layouts share this React interface.</li>
              </ul>
              <button className="button reset-button" type="button" onClick={resetEvaluation} disabled={!hasSessionChanges}>
                <Icon name="reset" /> Reset session
              </button>
            </section>
          </div>
        </details>
      </div>
    </div>
  );
}
