import { useEffect, useMemo, useState } from "react";
import {
  createPlatformBridge,
  type PlatformBridge,
  type RuntimeInfo,
  type SelectedDocument,
} from "./platform";

interface DemoDocument {
  id: string;
  title: string;
  source: string;
  added: string;
  kind: "Sample" | "Session only";
  size?: string;
}

const sampleDocuments: DemoDocument[] = [
  {
    id: "sample-referral",
    title: "Sample referral letter",
    source: "Fictional clinic",
    added: "Aug 18, 2026",
    kind: "Sample",
    size: "184 KB",
  },
  {
    id: "sample-summary",
    title: "Sample visit summary",
    source: "Fictional clinic",
    added: "Aug 11, 2026",
    kind: "Sample",
    size: "92 KB",
  },
  {
    id: "sample-imaging",
    title: "Sample imaging report",
    source: "Fictional imaging centre",
    added: "Jul 29, 2026",
    kind: "Sample",
    size: "241 KB",
  },
];

function formatBytes(size?: number): string | undefined {
  if (size === undefined) return undefined;
  if (size < 1024) return `${size} B`;
  return `${Math.round(size / 1024)} KB`;
}

function toDemoDocument(file: SelectedDocument): DemoDocument {
  return {
    id: `session-${crypto.randomUUID()}`,
    title: file.name,
    source: "Chosen on this device",
    added: "Just now",
    kind: "Session only",
    size: formatBytes(file.sizeBytes),
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
  const [documents, setDocuments] = useState(sampleDocuments);
  const [runtime, setRuntime] = useState<RuntimeInfo | null>(null);
  const [runtimeError, setRuntimeError] = useState(false);
  const [importing, setImporting] = useState(false);
  const [notice, setNotice] = useState("Ready to choose a sample PDF.");

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

  const sessionCount = useMemo(
    () => documents.filter((document) => document.kind === "Session only").length,
    [documents],
  );

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
      setNotice(`${selected.name} was added for this session only.`);
    } catch {
      setNotice("The file picker could not be opened. No file was accessed.");
    } finally {
      setImporting(false);
    }
  }

  return (
    <div className="app-shell">
      <div className="evaluation-banner" role="note">
        <strong>Technology evaluation only</strong>
        <span>Do not choose a real patient file. Nothing here is encrypted or saved.</span>
      </div>

      <header className="topbar">
        <a className="brand" href="#library" aria-label="MyVitalHistory evaluation home">
          <span className="brand-mark" aria-hidden="true">♥</span>
          <span>
            <strong>MyVitalHistory</strong>
            <small>Tauri proof of concept</small>
          </span>
        </a>
        <nav aria-label="Primary navigation">
          <a href="#library" aria-current="page">My records</a>
          <a href="#evaluation">About this POC</a>
        </nav>
      </header>

      <main>
        <section className="hero" aria-labelledby="page-title">
          <div>
            <p className="eyebrow">A patient-held filing cabinet</p>
            <h1 id="page-title">Your health records, kept by you</h1>
            <p className="lede">
              This small vertical slice tests whether one web interface can feel at home on
              desktop, Android, and iOS while calling a narrow Rust boundary.
            </p>
          </div>
          <button className="primary-action" onClick={chooseDocument} disabled={importing}>
            <span aria-hidden="true">＋</span>
            {importing ? "Opening picker…" : "Choose a sample PDF"}
          </button>
        </section>

        <section className="summary-grid" aria-label="Library summary">
          <article>
            <span className="summary-icon folder-icon" aria-hidden="true">▰</span>
            <div><strong>3</strong><span>Example folders</span></div>
          </article>
          <article>
            <span className="summary-icon document-icon" aria-hidden="true">▤</span>
            <div><strong>{documents.length}</strong><span>Documents shown</span></div>
          </article>
          <article>
            <span className="summary-icon session-icon" aria-hidden="true">◷</span>
            <div><strong>{sessionCount}</strong><span>Session-only imports</span></div>
          </article>
        </section>

        <div className="content-grid">
          <section className="library-card" id="library" aria-labelledby="library-title">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Top level</p>
                <h2 id="library-title">My records</h2>
              </div>
              <span className="count-pill">{documents.length} items</span>
            </div>
            <p className="status-line" role="status" aria-live="polite">{notice}</p>
            <div className="document-list" role="list">
              {documents.map((document) => (
                <article className="document-row" role="listitem" key={document.id}>
                  <span className="pdf-badge" aria-hidden="true">PDF</span>
                  <div className="document-title">
                    <strong>{document.title}</strong>
                    <span>{document.source}</span>
                  </div>
                  <span className={`kind-badge ${document.kind === "Session only" ? "session" : ""}`}>
                    {document.kind}
                  </span>
                  <span className="document-meta">{document.added}</span>
                  <span className="document-meta">{document.size ?? "Not read"}</span>
                </article>
              ))}
            </div>
          </section>

          <aside className="evaluation-card" id="evaluation" aria-labelledby="evaluation-title">
            <p className="eyebrow">Native bridge</p>
            <h2 id="evaluation-title">Hello from Tauri</h2>
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
              <p className="runtime-loading">Checking the application shell…</p>
            )}
            <div className="boundary-note">
              <strong>What the picker does</strong>
              <p>Returns only a filename to this screen. This POC does not read or retain the file.</p>
            </div>
          </aside>
        </div>
      </main>

      <footer>
        <span>Related to CARLOS issue #3474</span>
        <span>No accounts · No clinical integration · No persistence</span>
      </footer>
    </div>
  );
}
