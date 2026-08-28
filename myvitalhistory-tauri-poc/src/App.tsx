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
type AppSection = "records" | "recent" | "starred" | "trash" | "security" | "health";
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
  starred?: boolean;
  sessionOnly?: boolean;
}

interface TrashItem {
  id: string;
  title: string;
  subtitle: string;
  previousLocation: string;
  countdown: string;
  icon: IconName;
  document?: DemoDocument;
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
    starred: true,
  },
  {
    id: "sample-cardiology",
    title: "Specialist letter — cardiology",
    subtitle: "2 pages · PDF",
    source: "Maple Creek Medical",
    added: "19 Aug 2026",
    category: "Letters",
    icon: "letter",
    starred: true,
  },
  {
    id: "sample-xray",
    title: "Chest X-ray report",
    subtitle: "1 page · PDF",
    source: "Riverside Imaging",
    added: "1 Aug 2026",
    category: "Imaging",
    icon: "image",
    starred: true,
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

const sampleTrashItems: TrashItem[] = [
  {
    id: "trash-thyroid",
    title: "Bloodwork — thyroid",
    subtitle: "Deleted 3 days ago",
    previousLocation: "Maple Creek Medical",
    countdown: "Gone in 27 days",
    icon: "flask",
  },
  {
    id: "trash-scans",
    title: "Scans to sort out",
    subtitle: "Deleted yesterday · 4 items",
    previousLocation: "My records",
    countdown: "Gone in 29 days",
    icon: "folder",
  },
];

const sampleRecentIds = ["sample-cardiology", "sample-bloodwork", "sample-xray"];

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
  const [activeSection, setActiveSection] = useState<AppSection>("records");
  const [filter, setFilter] = useState<Filter>("All");
  const [query, setQuery] = useState("");
  const [view, setView] = useState<ViewMode>("list");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [trashItems, setTrashItems] = useState(sampleTrashItems);
  const [recentIds, setRecentIds] = useState(sampleRecentIds);
  const [activeDocumentId, setActiveDocumentId] = useState<string | null>(null);
  const [cloudBackup, setCloudBackup] = useState(true);
  const [driveBackup, setDriveBackup] = useState(false);
  const [biometricUnlock, setBiometricUnlock] = useState(true);
  const [autoLock, setAutoLock] = useState("15 minutes");
  const [appleHealth, setAppleHealth] = useState(false);
  const [healthConnect, setHealthConnect] = useState(false);

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

  useEffect(() => {
    if (!activeDocumentId) return;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setActiveDocumentId(null);
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [activeDocumentId]);

  const sessionFolderCount = useMemo(
    () => folders.filter((folder) => folder.sessionOnly).length,
    [folders],
  );

  const filteredDocuments = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    const matchingDocuments = documents.filter((document) => {
      const matchesSection =
        activeSection === "records" ||
        (activeSection === "recent" && recentIds.includes(document.id)) ||
        (activeSection === "starred" && document.starred);
      const matchesFilter = activeSection !== "records" || filter === "All" || document.category === filter;
      const matchesQuery =
        normalizedQuery.length === 0 ||
        `${document.title} ${document.source} ${document.category}`
          .toLocaleLowerCase()
          .includes(normalizedQuery);
      return matchesSection && matchesFilter && matchesQuery;
    });
    if (activeSection !== "recent") return matchingDocuments;
    return matchingDocuments.sort((left, right) => recentIds.indexOf(left.id) - recentIds.indexOf(right.id));
  }, [activeSection, documents, filter, query, recentIds]);

  const filteredFolders = useMemo(() => {
    if (activeSection !== "records" || filter !== "All") return [];
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) return folders;
    return folders.filter((folder) => folder.title.toLocaleLowerCase().includes(normalizedQuery));
  }, [activeSection, filter, folders, query]);

  const visibleCount = filteredFolders.length + filteredDocuments.length;
  const selectedDocuments = documents.filter((document) => selectedIds.includes(document.id));
  const activeDocument = documents.find((document) => document.id === activeDocumentId) ?? null;
  const starredCount = documents.filter((document) => document.starred).length;
  const hasSessionChanges =
    documents !== sampleDocuments ||
    folders !== sampleFolders ||
    trashItems.length !== sampleTrashItems.length ||
    !cloudBackup ||
    driveBackup ||
    !biometricUnlock ||
    autoLock !== "15 minutes" ||
    appleHealth ||
    healthConnect;
  const libraryTitle =
    activeSection === "recent" ? "Recent" : activeSection === "starred" ? "Starred" : "My records";
  const libraryDescription =
    activeSection === "recent"
      ? "Records you recently opened or added"
      : activeSection === "starred"
        ? `${filteredDocuments.length} records kept close at hand`
        : `${folders.length} folders · ${documents.length} documents · sample data`;

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
      const document = toDemoDocument(selected);
      setDocuments((current) => [document, ...current]);
      setRecentIds((current) => [document.id, ...current]);
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
    setTrashItems(sampleTrashItems);
    setRecentIds(sampleRecentIds);
    setActiveDocumentId(null);
    setActiveSection("records");
    setFilter("All");
    setQuery("");
    setSelectedIds([]);
    setCloudBackup(true);
    setDriveBackup(false);
    setBiometricUnlock(true);
    setAutoLock("15 minutes");
    setAppleHealth(false);
    setHealthConnect(false);
    setNotice("Evaluation reset. Only the built-in sample records are shown.");
  }

  function toggleSelected(id: string) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((selectedId) => selectedId !== id) : [...current, id],
    );
  }

  function openDocument(document: DemoDocument) {
    setActiveDocumentId(document.id);
    setRecentIds((current) => [document.id, ...current.filter((id) => id !== document.id)].slice(0, 8));
    setNotice(`${document.title} was opened in the evaluation preview.`);
  }

  function toggleStar(document: DemoDocument) {
    const nextStarred = !document.starred;
    setDocuments((current) =>
      current.map((candidate) =>
        candidate.id === document.id ? { ...candidate, starred: nextStarred } : candidate,
      ),
    );
    setNotice(`${document.title} was ${nextStarred ? "added to" : "removed from"} Starred.`);
  }

  function moveDocumentsToTrash(items: DemoDocument[]) {
    if (items.length === 0) return;
    const ids = new Set(items.map((document) => document.id));
    const deletedItems = items.map<TrashItem>((document) => ({
      id: `deleted-${document.id}`,
      title: document.title,
      subtitle: "Deleted just now",
      previousLocation: document.source,
      countdown: "Gone in 30 days",
      icon: document.icon,
      document,
    }));
    setDocuments((current) => current.filter((document) => !ids.has(document.id)));
    setTrashItems((current) => [...deletedItems, ...current]);
    setRecentIds((current) => current.filter((id) => !ids.has(id)));
    setSelectedIds((current) => current.filter((id) => !ids.has(id)));
    setActiveDocumentId(null);
    setNotice(
      items.length === 1
        ? `${items[0].title} was moved to Trash.`
        : `${items.length} documents were moved to Trash.`,
    );
  }

  function starSelectedDocuments() {
    if (selectedDocuments.length === 0) return;
    const ids = new Set(selectedDocuments.map((document) => document.id));
    setDocuments((current) =>
      current.map((document) => (ids.has(document.id) ? { ...document, starred: true } : document)),
    );
    setSelectedIds([]);
    setNotice(`${selectedDocuments.length} document${selectedDocuments.length === 1 ? "" : "s"} added to Starred.`);
  }

  function setCategory(nextFilter: Filter) {
    setActiveSection("records");
    setFilter(nextFilter);
    setSelectedIds([]);
  }

  function showSection(section: AppSection) {
    setActiveSection(section);
    setQuery("");
    setSelectedIds([]);
    setNotice(
      section === "records"
        ? "Showing synthetic records for evaluation."
        : `${section[0].toLocaleUpperCase()}${section.slice(1)} is a session-only demonstration.`,
    );
  }

  function restoreTrashItem(item: TrashItem) {
    setTrashItems((current) => current.filter((candidate) => candidate.id !== item.id));
    const restoredDocument = item.document ?? {
      id: `restored-${item.id}`,
      title: item.title,
      subtitle: "Restored sample · PDF",
      source: item.previousLocation,
      added: "Restored just now",
      category: "Other",
      icon: item.icon,
      sessionOnly: true,
    };
    setDocuments((current) => [restoredDocument, ...current]);
    setRecentIds((current) => [restoredDocument.id, ...current.filter((id) => id !== restoredDocument.id)]);
    setNotice(`${item.title} was restored to My records.`);
  }

  function emptyTrash() {
    setTrashItems([]);
    setNotice("The sample trash was emptied for this session only.");
  }

  function noteDemoAction(action: string) {
    setNotice(`${action} is demonstrated visually; no security material was created or changed.`);
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

          <label className="mobile-section-picker">
            <span>Section</span>
            <select
              aria-label="Section"
              value={activeSection}
              onChange={(event) => showSection(event.target.value as AppSection)}
            >
              <option value="records">My records</option>
              <option value="recent">Recent</option>
              <option value="starred">Starred</option>
              <option value="trash">Trash</option>
              <option value="security">Security & backup</option>
              <option value="health">Health data</option>
            </select>
          </label>

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
                <button className={activeSection === "records" && filter === "All" ? "selected" : ""} type="button" onClick={() => setCategory("All")}>
                  <Icon name="folder" /> My records
                </button>
                <button className={activeSection === "recent" ? "selected" : ""} type="button" onClick={() => showSection("recent")}><Icon name="clock" /> Recent</button>
                <button className={activeSection === "starred" ? "selected" : ""} type="button" onClick={() => showSection("starred")}><Icon name="star" /> Starred <span className="nav-count">{starredCount}</span></button>
                <button className={activeSection === "trash" ? "selected" : ""} type="button" onClick={() => showSection("trash")}><Icon name="trash" /> Trash <span className="nav-count">{trashItems.length}</span></button>

                <span className="nav-label">By kind</span>
                <button className={activeSection === "records" && filter === "Test results" ? "selected" : ""} type="button" onClick={() => setCategory("Test results")}>
                  <Icon name="flask" /> Test results <span className="nav-count">18</span>
                </button>
                <button className={activeSection === "records" && filter === "Letters" ? "selected" : ""} type="button" onClick={() => setCategory("Letters")}>
                  <Icon name="letter" /> Letters <span className="nav-count">11</span>
                </button>
                <button className={activeSection === "records" && filter === "Imaging" ? "selected" : ""} type="button" onClick={() => setCategory("Imaging")}>
                  <Icon name="image" /> Imaging <span className="nav-count">6</span>
                </button>
                <button className={activeSection === "records" && filter === "Prescriptions" ? "selected" : ""} type="button" onClick={() => setCategory("Prescriptions")}><Icon name="pill" /> Prescriptions <span className="nav-count">7</span></button>

                <span className="nav-label">Settings</span>
                <button className={activeSection === "security" ? "selected" : ""} type="button" onClick={() => showSection("security")}><Icon name="shield" /> Security & backup</button>
                <button className={activeSection === "health" ? "selected" : ""} type="button" onClick={() => showSection("health")}><Icon name="heart" /> Health data</button>
              </nav>

              <div className="storage">
                <span>Backup concept only</span>
                <div className="storage-meter"><span /></div>
                <small>Evaluation data stays in this session</small>
              </div>
            </aside>

            {(activeSection === "records" || activeSection === "recent" || activeSection === "starred") && (
            <main className="library-main">
              <div className="breadcrumb"><strong>{libraryTitle}</strong></div>
              <div className="main-head">
                <div>
                  <h1>{libraryTitle}</h1>
                  <p>{libraryDescription}</p>
                </div>
                {activeSection === "records" && (
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
                )}
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
                {activeSection === "records" && (
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
                )}
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
                  <span>{selectedDocuments.length > 0 ? "Apply an action to the selected documents." : "Choose a document to use record actions."}</span>
                  <div className="bulk-actions">
                    <button type="button" onClick={starSelectedDocuments} disabled={selectedDocuments.length === 0}>Star</button>
                    <button type="button" onClick={() => moveDocumentsToTrash(selectedDocuments)} disabled={selectedDocuments.length === 0}>Trash</button>
                    <button type="button" onClick={() => setSelectedIds([])}>Clear</button>
                  </div>
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
                          <button className="record-open" type="button" onClick={() => openDocument(document)}>{document.title}</button>
                          <small>{document.subtitle}{document.sessionOnly ? " · session only" : ""}</small>
                        </span>
                      </div>
                      <span className="column">{document.source}</span>
                      <span className="column">{document.added}</span>
                      <button className="more-button" type="button" aria-label={`More options for ${document.title}`} onClick={() => openDocument(document)}><Icon name="more" /></button>
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
                      <button className="tile-open" type="button" onClick={() => openDocument(document)}>
                        <span className="tile-preview paper-preview"><i /><i /><i /><i /></span>
                        <span className="tile-caption"><span className={`document-icon ${document.icon}`}><Icon name={document.icon} /></span><span><strong>{document.title}</strong><small>{document.added}</small></span></span>
                      </button>
                    </article>
                  ))}
                  {visibleCount === 0 && (
                    <div className="empty-state"><Icon name="search" /><strong>No matching records</strong><span>Try another search or filter.</span></div>
                  )}
                </div>
              )}
            </main>
            )}

            {activeSection === "trash" && (
              <main className="library-main purpose-screen">
                <div className="breadcrumb"><strong>Trash</strong></div>
                <div className="main-head">
                  <div>
                    <h1>Trash</h1>
                    <p>Kept for 30 days, then deleted for good</p>
                  </div>
                  <div className="head-actions">
                    <button className="button danger" type="button" onClick={emptyTrash} disabled={trashItems.length === 0}>
                      <Icon name="trash" /> Empty trash
                    </button>
                  </div>
                </div>

                <div className="purpose-note">
                  <Icon name="info" />
                  <span>Deleted medical records remain recoverable here for 30 days. Restoring puts an item back where it was.</span>
                </div>
                <p className="status-line" role="status" aria-live="polite">{notice}</p>

                <div className="filelist trash-list" aria-label={`${trashItems.length} items in trash`}>
                  <div className="file-head" aria-hidden="true">
                    <span />
                    <span className="sorted">Name</span>
                    <span className="column">Where it was</span>
                    <span className="column">Deleted</span>
                    <span />
                  </div>
                  {trashItems.map((item) => (
                    <article className="file-row" key={item.id}>
                      <span className="trash-marker"><Icon name="trash" /></span>
                      <div className="file-name">
                        <span className={`document-icon ${item.icon}`}><Icon name={item.icon} /></span>
                        <span className="name-copy"><strong>{item.title}</strong><small>{item.subtitle}</small></span>
                      </div>
                      <span className="column">{item.previousLocation}</span>
                      <span className="column countdown">{item.countdown}</span>
                      <button className="restore-button" type="button" onClick={() => restoreTrashItem(item)}>Restore</button>
                    </article>
                  ))}
                  {trashItems.length === 0 && (
                    <div className="empty-state"><Icon name="trash" /><strong>Trash is empty</strong><span>Deleted records would remain here for 30 days.</span></div>
                  )}
                </div>
              </main>
            )}

            {activeSection === "security" && (
              <main className="library-main purpose-screen">
                <div className="main-head">
                  <div>
                    <h1>Security & backup</h1>
                    <p>How your records would be protected and copied</p>
                  </div>
                </div>
                <div className="purpose-note warning">
                  <Icon name="info" />
                  <span><strong>Evaluation controls only.</strong> These settings change this screen for the current session; no encryption keys or backups are created.</span>
                </div>
                <p className="status-line" role="status" aria-live="polite">{notice}</p>

                <div className="setting-list">
                  <section className="setting-row">
                    <div>
                      <h2>Encryption <span className="state-pill">On — always</span></h2>
                      <p>Your records would be scrambled on this device using your passphrase. This cannot be turned off.</p>
                    </div>
                  </section>

                  <section className="setting-row">
                    <div>
                      <h2>Recovery sheet <span className="state-pill warning">Not created</span></h2>
                      <p>A printable recovery sheet could reopen the vault if the passphrase is forgotten. Printing a new one would invalidate the old sheet.</p>
                    </div>
                    <button className="button" type="button" onClick={() => noteDemoAction("Recovery sheet creation")}><span>Preview sheet</span></button>
                  </section>

                  <section className="setting-row">
                    <div>
                      <h2>Backup to iCloud <span className={`state-pill ${cloudBackup ? "" : "off"}`}>{cloudBackup ? "On" : "Off"}</span></h2>
                      <p>The production concept copies records while they are still scrambled, so the cloud provider cannot read them.</p>
                    </div>
                    <button
                      className={`toggle ${cloudBackup ? "" : "off"}`}
                      type="button"
                      aria-label="Backup to iCloud demo"
                      aria-pressed={cloudBackup}
                      onClick={() => setCloudBackup((current) => !current)}
                    />
                  </section>

                  <section className="setting-row">
                    <div>
                      <h2>Backup to a folder or drive <span className={`state-pill ${driveBackup ? "" : "off"}`}>{driveBackup ? "On" : "Off"}</span></h2>
                      <p>A second encrypted copy could be stored on a USB drive or in a folder chosen by the patient.</p>
                    </div>
                    <button
                      className={`toggle ${driveBackup ? "" : "off"}`}
                      type="button"
                      aria-label="Backup to a folder demo"
                      aria-pressed={driveBackup}
                      onClick={() => setDriveBackup((current) => !current)}
                    />
                  </section>

                  <section className="setting-row">
                    <div>
                      <h2>Lock automatically</h2>
                      <p>Closes the vault after a period without use.</p>
                    </div>
                    <label className="select-control">
                      <span className="sr-only">Automatic lock delay</span>
                      <select value={autoLock} onChange={(event) => setAutoLock(event.target.value)}>
                        <option>1 minute</option>
                        <option>5 minutes</option>
                        <option>15 minutes</option>
                        <option>1 hour</option>
                        <option>Never</option>
                      </select>
                    </label>
                  </section>

                  <section className="setting-row">
                    <div>
                      <h2>Unlock with biometrics <span className={`state-pill ${biometricUnlock ? "" : "off"}`}>{biometricUnlock ? "On" : "Off"}</span></h2>
                      <p>Face ID, Touch ID, or the Android device credential could unlock the app; the passphrase would still be required after restarting.</p>
                    </div>
                    <button
                      className={`toggle ${biometricUnlock ? "" : "off"}`}
                      type="button"
                      aria-label="Biometric unlock demo"
                      aria-pressed={biometricUnlock}
                      onClick={() => setBiometricUnlock((current) => !current)}
                    />
                  </section>

                  <section className="setting-row">
                    <div>
                      <h2>Change passphrase</h2>
                      <p>A real change would re-lock every record with the new passphrase.</p>
                    </div>
                    <button className="button" type="button" onClick={() => noteDemoAction("Passphrase change")}><span>Show purpose</span></button>
                  </section>
                </div>
              </main>
            )}

            {activeSection === "health" && (
              <main className="library-main purpose-screen">
                <div className="main-head">
                  <div>
                    <h1>Health data</h1>
                    <p>Readings from your phone and devices, alongside your records</p>
                  </div>
                </div>

                <div className="purpose-note warning">
                  <Icon name="flask" />
                  <span><strong>Concept only — mobile devices.</strong> Apple Health and Health Connect store readings such as blood pressure and weight, not letters or reports. No health permission is requested by this evaluation.</span>
                </div>

                <div className="setting-list health-connections">
                  <section className="setting-row">
                    <div>
                      <h2>Apple Health <span className={`state-pill ${appleHealth ? "" : "off"}`}>{appleHealth ? "Demo connected" : "Not connected"}</span></h2>
                      <p>On iPhone, the patient could choose to read blood pressure, weight, and steps. Nothing would be written back.</p>
                    </div>
                    <button className="button" type="button" aria-pressed={appleHealth} onClick={() => setAppleHealth((current) => !current)}>{appleHealth ? "Disconnect demo" : "Connect demo"}</button>
                  </section>

                  <section className="setting-row">
                    <div>
                      <h2>Health Connect <span className={`state-pill ${healthConnect ? "" : "off"}`}>{healthConnect ? "Demo connected" : "Not connected"}</span></h2>
                      <p>The Android equivalent could provide the same kinds of readings through a separate permission.</p>
                    </div>
                    <button className="button" type="button" aria-pressed={healthConnect} onClick={() => setHealthConnect((current) => !current)}>{healthConnect ? "Disconnect demo" : "Connect demo"}</button>
                  </section>
                </div>

                {appleHealth || healthConnect ? (
                  <section className="health-preview" aria-label="Synthetic health reading preview">
                    <div className="health-preview-heading">
                      <div><span className="eyebrow">Synthetic preview</span><h2>Sample health snapshot</h2></div>
                      <span className="state-pill">Demo data</span>
                    </div>
                    <div className="metric-grid">
                      <article><Icon name="heart" /><strong>122/78</strong><span>Blood pressure · mmHg</span></article>
                      <article><Icon name="activity" /><strong>6,420</strong><span>Steps today</span></article>
                      <article><Icon name="clock" /><strong>72 bpm</strong><span>Resting heart rate</span></article>
                    </div>
                  </section>
                ) : (
                  <div className="health-empty">
                    <span className="brand-mark"><Icon name="activity" /></span>
                    <h2>Nothing connected</h2>
                    <p>A production connection could place trends beside clinic documents, giving the patient one view of the whole picture.</p>
                  </div>
                )}
              </main>
            )}
          </div>

          {activeDocument && (
            <div className="dialog-backdrop" role="presentation" onMouseDown={() => setActiveDocumentId(null)}>
              <section
                className="record-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="record-dialog-title"
                onMouseDown={(event) => event.stopPropagation()}
              >
                <header className="dialog-head">
                  <div>
                    <span className="eyebrow">Synthetic document preview</span>
                    <h2 id="record-dialog-title">{activeDocument.title}</h2>
                  </div>
                  <button className="dialog-close" type="button" aria-label="Close document preview" onClick={() => setActiveDocumentId(null)}>×</button>
                </header>

                <div className="document-preview" aria-label="Placeholder document contents">
                  <span className={`document-icon ${activeDocument.icon}`}><Icon name={activeDocument.icon} /></span>
                  <div className="preview-paper" aria-hidden="true"><i /><i /><i /><i /><i /></div>
                  <p>Preview placeholder — this evaluation does not read the selected file.</p>
                </div>

                <dl className="record-metadata">
                  <div><dt>Kind</dt><dd>{activeDocument.category}</dd></div>
                  <div><dt>Sent by</dt><dd>{activeDocument.source}</dd></div>
                  <div><dt>Date added</dt><dd>{activeDocument.added}</dd></div>
                  <div><dt>File</dt><dd>{activeDocument.subtitle}</dd></div>
                </dl>

                <footer className="dialog-actions">
                  <button className="button" type="button" onClick={() => toggleStar(activeDocument)}>
                    <Icon name="star" /> {activeDocument.starred ? "Remove star" : "Add to Starred"}
                  </button>
                  <button className="button danger" type="button" onClick={() => moveDocumentsToTrash([activeDocument])}>
                    <Icon name="trash" /> Move to Trash
                  </button>
                </footer>
              </section>
            </div>
          )}
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
