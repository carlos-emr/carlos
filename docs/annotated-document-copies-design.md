# Annotated Document Copies — Design

| | |
|---|---|
| Status | Proposed, awaiting maintainer review |
| Supersedes | `docs/fax-showdocument-implementation-plan.md`, Phase 2 (PDF.js annotation viewer) |
| Replaces | The annotate-and-fax portion of PR #2897 (`add-basic-fax`) |
| Target branch | `release/2026.08` |
| Date | 2026-09-06 |

## 1. Decision record

| Decision | Choice | Why |
|---|---|---|
| Rendering | Server-rendered page images (PDFBox `PDFRenderer`, already used by `ManageDocument2Action`) with an SVG overlay in vanilla JavaScript. No PDF.js, no npm, no WebJars. | `pdfjs-dist` 6.0.227 carries CVE-2026-16633 (GHSA-hq66-cqwq-w95j, arbitrary JavaScript on opening a malicious PDF). Only 6.0.227 and 6.1.200 exist as WebJars and both are affected; the fix is 6.2.108, which is not published as a WebJar. The PR shipped 9.1 MB / 504 files to use four, and monkey-patched a private PDF.js API. Inbound documents are untrusted PDFs. |
| Persistence | Annotations are **not** stored as data. Saving composes a new PDF and files it as a **new `document` row** for the same patient. The original file is never modified. Nothing is editable after save. | Keeps the received document immutable as a clinical record. Removes the need for an annotation table, DAO, and Flyway migration. Matches the maintainer decision that a saved annotation *is* a new document. |
| Composition | Server-side with PDFBox 3.0.7 (existing dependency). Appends to page content on a copy. Text uses the shipped DejaVu TrueType fonts. Signatures embed the provider's stored stamp at save time. | The browser sends a small JSON model, never PDF bytes. Embedding the signature at save time snapshots it, so a later signature change cannot rewrite history. |
| Fax | Faxing always sends a **document row** (original or annotated copy) through `Fax2Action.prepareFax` → `CoverPage.jsp` → `queue`, with a session claim for `DOCUMENT`. | Reuses the eForm staging pattern exactly. Also closes an existing gap: `queue()` currently consumes a claim only for `EFORM` and otherwise accepts any client-supplied path inside the document store. |
| OCR | Performed upstream before the file reaches CARLOS. Stored files are stable for their whole life. | Word boxes for snap-to-text highlighting can be computed once per document and cached. No fingerprint check is needed on the render path. |

## 2. Findings that drove the replacement

- **Vulnerable, unpatchable dependency.** OSV reports exactly one advisory against `pdfjs-dist` 6.0.227: CVE-2026-16633, introduced 5.6.83, fixed 6.2.108, CVSS 3.1 8.1 High. Maven Central's `maven-metadata.xml` for `org.webjars.npm:pdfjs-dist` lists 71 versions; the only 6.x entries are 6.0.227 and 6.1.200. `dependency-review` and Socket Security both passed on the PR, so this class of issue is invisible to current CI.
- **Destructive save.** `SaveAnnotatedDocument2Action` replaced the stored file at `doc.getFilePath()` with browser-supplied bytes via a replace-move. No backup, no new version.
- **Fax queue trusts the client for documents.** `Fax2Action.queue()` calls `revalidateEformBindingBeforePromotion`, which returns immediately for non-eForm types. `validateFaxInputs` then accepts any existing file under `DOCUMENT_DIR`, and the circle-of-care check runs on the client-submitted `demographicNo`, which nothing ties to the file.
- **Branch hygiene.** `add-basic-fax` had merged `develop`, so retargeting to `release/2026.08` dragged in 55 unrelated commits touching 38 files. The fax work alone is 29 files, +2521/−97. That rebuild already exists on `claude/pr-2897-branch-target-mknhis`.

## 3. User flow

1. **Show Document** shows a **Fax** button (kept from the PR, gated on `FaxManager.isEnabled()` and `_fax` read) and a new **Annotate** button (gated on `_edoc` write). Both appear only for `application/pdf`.
2. **Annotate** opens the viewer: server-rendered page images, a toolbar (select, text, highlight, draw, signature, date), page navigation, and zoom.
3. **Save** posts the annotation JSON. The server composes a new PDF, files it as a new document for the same patient, and returns the new document number. The chart now shows both the original and the annotated copy.
4. **Save and fax** does the same, then navigates to `prepareFax` for the **new** document number. The existing cover page, recipient autocomplete, preview, and queue run unchanged.
5. **Fax as-is** from Show Document navigates straight to `prepareFax` for the original document number.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant A as AnnotateDocument2Action
    participant M as ManageDocument2Action
    participant S as SaveAnnotatedDocument2Action
    participant V as AnnotatedDocumentService
    participant C as AnnotatedDocumentComposer
    participant F as Fax2Action

    B->>A: GET /documentManager/AnnotateDocument?docId=N
    A-->>B: annotateDocument.jsp (pages, tools)
    B->>M: GET method=showPage&doc_no=N&page=p&dpi=144
    M-->>B: image/png (cached per page and dpi)
    B->>M: GET /documentManager/DocumentTextBoxes?docId=N&page=p
    M-->>B: word boxes JSON (cached per document)
    B->>S: POST annotations JSON + CSRF-TOKEN header
    S->>V: save(loggedInInfo, N, annotations)
    V->>C: compose(sourcePdf, annotations, signaturePng)
    C-->>V: bytes (verified %PDF, same page count)
    V-->>S: new documentNo N2 (new row, same patient, original untouched)
    S-->>B: {"documentNo": N2}
    B->>F: GET method=prepareFax&transactionType=DOCUMENT&transactionId=N2&demographicNo=D
    F-->>B: CoverPage.jsp with claimed app-temp copy
    B->>F: POST method=queue (cover-page form)
    F-->>B: queued; claim consumed
```

## 4. Components

Names follow `docs/architecture/layer-names.md`. Package: `io.github.carlos_emr.carlos.documentManager.annotation` unless noted.

| Class | Role | Verb | Privilege |
|---|---|---|---|
| `AnnotateDocument2Action` (`documentManager.actions`) | View gate. Loads document metadata and forwards to `/WEB-INF/jsp/documentManager/annotateDocument.jsp`. Rejects non-PDF. | GET | `_edoc` write, circle-of-care on the document's patient |
| `ManageDocument2Action.showPage` (existing) | Add a bounded `dpi` parameter (allowlist 96, 144, 192; default 96) and include it in the cache key. Everything else unchanged. | GET | `_edoc` read (existing) |
| `DocumentTextBoxes2Action` (`documentManager.actions`) | Returns word bounding boxes for one page as JSON, in normalized page coordinates, from `PDFTextStripper` `TextPosition`s. Cached under the document cache directory as `<file>_<page>.words.json`. Empty list when the page has no text layer. | GET | `_edoc` read, circle-of-care |
| `DocumentAnnotationDto` | Immutable carrier: `type`, `page`, `x`, `y`, `w`, `h`, `points`, `text`, `color`, `strokeWidth`, `fontSize`. | — | — |
| `DocumentAnnotationParser` | JSON → `List<DocumentAnnotationDto>` with the limits in section 6. Throws `IllegalArgumentException` with a non-PHI message. | — | — |
| `AnnotatedDocumentComposer` | PDFBox composition per section 7. Pure function of source path, annotations, signature path → bytes. No DAO access. | — | — |
| `AnnotatedDocumentService` | Orchestrates: authorize, load source via `EDocUtil.getDoc`, compose, write the new file into `DOCUMENT_DIR`, create the new document row through `EDoc` + `EDocUtil.addDocumentSQL` (which also writes `ctl_document`), audit via `LogAction`. Returns the new document number. | — | — |
| `SaveAnnotatedDocument2Action` (`documentManager.actions`) | POST only. Rejects GET/HEAD with 405 before any side effect. Reads the JSON body, calls the service, writes `{"documentNo": n}`. Registered in `unconditionalMutators()` of `MutatorActionGetRejectionContractUnitTest`. | POST | `_edoc` write, circle-of-care |
| `Fax2Action.prepareFax` (existing) | New `DOCUMENT` branch beside `EFORM`: derive the patient from `ctl_document`, resolve the file with `PathValidationUtils`, copy to app temp via `NioFileManager.createTempFile`, `recordClaimedFaxFilePathInSession`, set the same request attributes as the eForm path. | GET (existing verb rules) | `_fax` read, `_edoc` read, circle-of-care |
| `Fax2Action.queue` (existing) | Generalize claim consumption: `DOCUMENT` must consume a matching claim or be rejected with 403 and the staged copy deleted. `EFORM` behaviour unchanged. | POST | existing |
| `FaxDocument2Action` (PR, reduced) | Becomes a thin entry that checks fax accounts and redirects to `prepareFax` for `DOCUMENT`. The `annotate` and `preview` results go away. | GET | existing |

Removed from the PR: `ServeDocument2Action`, `FaxAnnotateViewer.jsp`, the `org.webjars.npm:pdfjs-dist` dependency and its lock entry, the `.mjs` and `^/webjars/.*` additions to `struts.action.excludePattern`, and the hardcoded PDF.js version string.

Kept from the PR: the Fax button and tooltip on `showDocument.jsp`, `FaxRecipientSearch2Action` and `faxRecipientAutocomplete.js`, `faxNotAvailable.jsp`, the `ProviderSignatureStamp2Action` JSON 403, the `CoverPage.jsp` i18n work, and all `faxAnnotateViewer.*`, `coverPage.*`, and `faxNotAvailable.*` resource keys. The viewer keys are reused by the new page.

## 5. The new document row

Created through `EDoc` + `EDocUtil.addDocumentSQL(EDoc)`, the same path `SplitDocument2Action` uses. That call also writes the `ctl_document` link.

| Field | Value | Note |
|---|---|---|
| `docfilename` | `document_<millis>_annotated.pdf` | Mirrors the existing `document_<millis>.dat` convention. Validated with `PathValidationUtils.validateGeneratedFileName` and `validateGeneratedChildPath`. |
| `docdesc` | `<source docdesc> (annotated)` | Suffix comes from a resource key so it localizes. |
| `doctype`, `docClass`, `docSubClass` | copied from source | The copy files where the original files. |
| `contenttype` | `application/pdf` | |
| `numberofpages` | from the composed PDF | Asserted equal to the source. |
| `observationdate` | copied from source | The clinical date does not change. |
| `contentdatetime`, `updatedatetime` | now | |
| `doccreator` | the saving provider | |
| `responsible` | copied from source | |
| `status` | `A` | |
| `public1` | copied from source | |
| `source` | `annotated-copy-of:<sourceDocNo>` | `Document.source` is free-text provenance today (`"REST API"` in `DocumentService`). This gives a queryable link back without a schema change. |
| `reviewer`, `reviewdatetime`, `abnormal` | empty / false | The copy has not been reviewed. Open decision, see section 14. |
| `ctl_document` | `module = demographic`, `module_id` = source's | Same patient. |

No new table. No migration.

## 6. Annotation JSON contract

The browser posts one object. Coordinates are fractions of the **displayed** page (after `/Rotate`), origin top-left, so they are independent of the render DPI.

```json
{
  "documentNo": 1234,
  "annotations": [
    { "type": "highlight", "page": 1, "x": 0.12, "y": 0.31, "w": 0.40, "h": 0.022, "color": "yellow" },
    { "type": "text",      "page": 1, "x": 0.60, "y": 0.08, "w": 0.30, "h": 0.03, "text": "Please call re: dosage", "fontSize": 11 },
    { "type": "ink",       "page": 2, "points": [[0.20,0.50],[0.21,0.51],[0.23,0.52]], "strokeWidth": 2, "color": "blue" },
    { "type": "signature", "page": 2, "x": 0.55, "y": 0.80, "w": 0.30, "h": 0.07 },
    { "type": "date",      "page": 2, "x": 0.55, "y": 0.88, "w": 0.20, "h": 0.025, "text": "2026-09-06" }
  ]
}
```

| Limit | Value |
|---|---|
| Annotations per document | 500 |
| Ink points per stroke | 5 000 |
| Text length | 2 000 characters |
| `color` | allowlist: `yellow`, `green`, `blue`, `pink`, `red`, `black` |
| `fontSize` | 6 to 36 points |
| `strokeWidth` | 0.5 to 8 points |
| Geometry | every coordinate in `[0, 1]`; `x + w <= 1`; `y + h <= 1`; `page` in `[1, numberofpages]` |
| Body size | 256 KB |

Anything outside these limits is rejected with 400 and a message that names the rule, not the content.

## 7. Composition rules

`AnnotatedDocumentComposer` never opens the stored file for writing.

1. Load the source with `Loader.loadPDF` from a read-only copy in app temp.
2. For each page with annotations, open `PDPageContentStream(doc, page, AppendMode.APPEND, compress = true, resetContext = true)`.
3. Convert normalized coordinates to user space using the page **CropBox**. Compensate for `/Rotate` 90, 180, 270 with a transform matrix so a box drawn on the displayed image lands on the same ink. `SplitDocument2Action` already reads `getRotation()`.
4. **Highlight**: filled rectangle with `PDExtendedGraphicsState` blend mode `MULTIPLY` so underlying text stays legible. Colours map to fixed RGB values.
5. **Ink**: polyline with round caps and joins, width in points.
6. **Text** and **date**: `PDType0Font.load(doc, DejaVuSans.ttf, embedSubset = true)` from `src/main/webapp/library/eforms/dejavufonts/ttf/`, resolved through the servlet context. DejaVu covers the French, Polish, and Portuguese glyphs the locales need.
7. **Signature**: `PDImageXObject.createFromFileByContent` from `consult_sig_<providerNo>.png` in the eForm image directory, scaled into the box, aspect preserved. Missing stamp → 409 with a message telling the user to set one under Provider Preferences.
8. Save to a byte array. Assert the bytes begin with `%PDF` and the page count equals the source. Only then write to disk.

Fax output is rasterized by the gateway, so the annotated copy on screen and the fax both come from the same PDF.

## 8. Security

- Every action checks `SecurityInfoManager.hasPrivilege` first and `isAllowedAccessToPatientRecord` against the patient derived from `ctl_document`, never from a request parameter.
- The browser never supplies PDF bytes, file names, or paths. Only `docId`, page numbers, a DPI from an allowlist, and the JSON model.
- All file paths go through `PathValidationUtils`. The composer reads from app temp and the service writes only into `DOCUMENT_DIR`.
- `SaveAnnotatedDocument2Action` reads the CSRF token from the `CSRF-TOKEN` header. The viewer page includes `/WEB-INF/jspf/csrf-token.jspf`.
- The viewer page sets a `Content-Security-Policy` header. Its JavaScript moves to a static file under `src/main/webapp/js/`, so the only inline blocks left are ones the page cannot do without: the server-resolved configuration object and the CSRF bootstrap in `csrf-token.jspf`. Those are admitted by a per-response `'nonce-'` in `script-src` rather than by `'unsafe-inline'`, which would also admit anything injected. `LogoutBroadcastFilter` injects a third inline block into every authenticated HTML page and reads the same nonce, published by the page as the `cspNonce` request attribute; without that it would be dropped and idle logout would stop working on this page alone. The eForm servlets already set per-page CSP, using `'unsafe-inline'`.

  A CSP that blocks a page's own inline script fails **silently** in the DOM — the viewer simply renders nothing and every save is rejected with an empty CSRF token — and is reported only on the browser console. `scripts/e2e/fax/annotate-document-playwright-checks.js` therefore asserts the header is present, that it carries a nonce, that both inline blocks actually executed, and that the console logged no CSP violation.
- Audit: `LogAction.addLog(provider, LogConst.ADD, LogConst.CON_DOCUMENT, newDocNo, ip)` on save, `READ` on view. Annotation text is PHI and is never logged.
- Temp files: the read-only source copy and the fax staging copy live under `carlos-temp` and are removed by the existing `flush` and cancel paths. The composed bytes are held in memory and written once.

## 9. Fax integration

Entry from the viewer or from Show Document is the same URL shape `AddEForm2Action.redirectToPreparedFax` already uses:

```
/fax/faxAction?method=prepareFax&transactionType=DOCUMENT&transactionId=<documentNo>&demographicNo=<n>
```

`prepareFax`, `DOCUMENT` branch:

1. `_fax` read and `_edoc` read.
2. Load the document; derive the patient from `ctl_document`; compare to the submitted `demographicNo`; reject on mismatch.
3. Circle-of-care check on the derived patient.
4. Resolve the file under `DOCUMENT_DIR` with `PathValidationUtils.validateExistingPath`.
5. Copy to app temp with `NioFileManager.createTempFile`. **This copy is what gets faxed.** The document store is never handed to the fax pipeline directly.
6. `recordClaimedFaxFilePathInSession(copy)`.
7. Set `documents`, `faxFilePath`, `transactionType`, `transactionId`, `demographicNo`, `accounts`; return `preview`.

`queue()`:

- Replace the eForm-only `revalidateEformBindingBeforePromotion` with per-type claim consumption. For `DOCUMENT`, `consumeClaimedFaxFilePathFromSession` must return a path equal to the submitted `faxFilePath`; otherwise reject with 403 and delete the staged copy. For `EFORM`, behaviour is unchanged.
- Effect: the client can no longer fax an arbitrary document-store path. `CONSULTATION` uses its own action and is untouched.

`cancel()` is unchanged; `flush` already deletes app-temp artifacts and refuses document-store paths.

## 10. Viewer

Plain HTML, CSS, and one static JavaScript file. No framework, no library.

- Each page is an `<img>` from `showPage` at the current DPI, with an absolutely positioned `<svg>` of the same size on top. Annotations are SVG elements positioned from normalized coordinates × rendered size, so zoom is a DPI change and a re-layout.
- Tools: **select** (move, resize, delete before save), **text**, **highlight** (drag a rectangle; when the page has word boxes, the rectangle snaps to the words it covers), **draw** (pointer events → simplified polyline), **signature** (places the stored stamp; the drawing modal is kept for providers with no stamp and posts to the existing `ProviderSignatureStamp2Action`), **date** (today, editable).
- Page navigation and page count from `numberofpages`.
- **Save** and **Save and fax**. After save the page shows the new document number and a link to it. Nothing on the page can modify a saved document.
- Reuses the PR's `faxAnnotateViewer.*` resource keys.

## 11. Files

**Add**

- `src/main/java/io/github/carlos_emr/carlos/documentManager/annotation/DocumentAnnotationDto.java`
- `src/main/java/io/github/carlos_emr/carlos/documentManager/annotation/DocumentAnnotationParser.java`
- `src/main/java/io/github/carlos_emr/carlos/documentManager/annotation/AnnotatedDocumentComposer.java`
- `src/main/java/io/github/carlos_emr/carlos/documentManager/annotation/AnnotatedDocumentService.java`
- `src/main/java/io/github/carlos_emr/carlos/documentManager/annotation/package-info.java`
- `src/main/java/io/github/carlos_emr/carlos/documentManager/actions/AnnotateDocument2Action.java`
- `src/main/java/io/github/carlos_emr/carlos/documentManager/actions/DocumentTextBoxes2Action.java`
- `src/main/webapp/WEB-INF/jsp/documentManager/annotateDocument.jsp`
- `src/main/webapp/js/documentAnnotate.js`

**Change**

- `src/main/java/io/github/carlos_emr/carlos/documentManager/actions/SaveAnnotatedDocument2Action.java` (rewrite: JSON in, new document out)
- `src/main/java/io/github/carlos_emr/carlos/documentManager/actions/ManageDocument2Action.java` (`dpi` on `showPage`, cache key)
- `src/main/java/io/github/carlos_emr/carlos/documentManager/actions/FaxDocument2Action.java` (reduce to redirect)
- `src/main/java/io/github/carlos_emr/carlos/fax/action/Fax2Action.java` (`DOCUMENT` branch, claim consumption)
- `src/main/webapp/WEB-INF/jsp/documentManager/showDocument.jsp` (Annotate button)
- `src/main/webapp/WEB-INF/classes/struts-document.xml` (routes)
- `src/main/webapp/WEB-INF/classes/struts.xml` (remove `.mjs` and `/webjars/`)
- `src/main/resources/oscarResources_{en,es,fr,pl,pt_BR}.properties` (new keys: Annotate button, annotated suffix, stamp-missing message)
- `src/main/resources/applicationContext*.xml` (bean for `AnnotatedDocumentService`, `AnnotatedDocumentComposer`, `DocumentAnnotationParser`)
- `src/test/java/io/github/carlos_emr/carlos/app/contract/MutatorActionGetRejectionContractUnitTest.java` (manifest)
- `pom.xml`, `dependencies-lock.json` (remove `pdfjs-dist`)

**Remove**

- `src/main/java/io/github/carlos_emr/carlos/documentManager/actions/ServeDocument2Action.java`
- `src/main/webapp/WEB-INF/jsp/fax/FaxAnnotateViewer.jsp`
- `docs/fax-showdocument-implementation-plan.md` Phase 2 sections (or the file, replaced by this document)

## 12. Tests

All names follow `should<Action>_<context><Condition>` with one underscore.

| Test class | Base | Covers |
|---|---|---|
| `DocumentAnnotationParserUnitTest` | `CarlosUnitTestBase` | every limit in section 6; rejects unknown type, out-of-range page, bad colour, oversized body |
| `AnnotatedDocumentComposerUnitTest` | `CarlosUnitTestBase` | output starts with `%PDF`; page count unchanged; source file bytes unchanged after compose; rotated page places a box on the same ink (render both, compare pixel region); missing signature raises the documented exception |
| `AnnotatedDocumentServiceUnitTest` | `CarlosUnitTestBase` | creates one new row with copied fields and `source` provenance; original `docfilename` untouched; audit log written; privilege failure creates nothing |
| `SaveAnnotatedDocument2ActionUnitTest` | `CarlosUnitTestBase` | GET → 405 with `verifyNoInteractions` on the service; POST happy path returns JSON; circle-of-care failure → 403 |
| `DocumentTextBoxes2ActionUnitTest` | `CarlosUnitTestBase` | normalized boxes for a text PDF; empty list for an image-only PDF; privilege |
| `ManageDocument2ActionShowPageDpiUnitTest` | `CarlosUnitTestBase` | allowlisted DPI accepted, others fall back to 96; cache key includes DPI |
| `Fax2ActionDocumentClaimUnitTest` | `CarlosUnitTestBase` | `prepareFax` DOCUMENT records a claim on an app-temp copy; `queue` DOCUMENT without a claim → 403 and copy deleted; with claim → promoted; `EFORM` path unchanged |
| `MutatorActionGetRejectionContractUnitTest` | existing | manifest updated |
| `scripts/e2e/fax/annotate-document-playwright-checks.js` | Playwright | open viewer, place a highlight and a signature, save, assert a new document appears in the chart and the original is byte-identical; save-and-fax reaches the cover page |

## 13. Rollout

1. Build on `claude/pr-2897-branch-target-mknhis`, which already holds the 29-file fax delta on top of `release/2026.08`.
2. Remove the PDF.js pieces first, then land the composer and service with their unit tests, then the viewer, then the fax branch.
3. Open a new PR against `release/2026.08` with `Co-Authored-By` for Peter Hutten-Czapski, Michael Yingbull, and the bots that contributed to the kept pieces. PR #2897 is closed as superseded. DCO sign-off on the rebuilt commit comes from its author; a maintainer can post the workflow's manual confirmation phrase for the kept history.
4. Ship the `queue()` claim change with the same PR. It is a security fix and should not wait.

## 14. Open decisions for maintainers

- **Review state of the copy.** Section 5 leaves `reviewer` empty. Alternative: copy the source's review state so the annotated copy does not reappear in the unreviewed inbox.
- **Retain the annotation JSON.** `Document.docxml` is unused for PDFs. Storing the posted JSON there gives an audit record of what was drawn at no schema cost. Not required for the feature.
- **Description wording.** `(annotated)` suffix versus a prefix, and whether to include the author's initials.
- **Who may annotate.** This design uses `_edoc` write. If annotation should be narrower than document editing, a new security object is needed.

## 15. Rejected: client-side PDF.js in any form

Security is the primary requirement for this feature, so the decision in section 1 is final: **no PDF is parsed in the browser.** This section records why every client-side variant was rejected, so the question does not reopen.

- **Rendering is parsing.** To draw a page, PDF.js must interpret the whole file, including fonts, image codecs, forms, and actions, as JavaScript running in the page. The known advisories (CVE-2024-4367, CVE-2026-16633) are all "open a crafted PDF, attacker code runs in the hosting site." Inbound faxes are attacker-controllable input.
- **Configuration only narrows known exploits.** Disabling scripting, XFA, `eval`, and font-face injection addresses the published cases, not the class.
- **Shadow DOM is not a boundary.** It scopes styles and IDs. Script inside a shadow root shares the page's realm, origin, cookies, DOM, and network. A closed shadow root hides nothing from code inside it.
- **Web Workers are not a boundary.** No DOM, but the same origin and cookies on every fetch.
- **A sandboxed iframe is a boundary, and was still rejected.** `sandbox="allow-scripts"` without `allow-same-origin` yields an opaque origin, and a `connect-src` limited to the vendored library path plus delivering the PDF by `postMessage` would confine a compromise to the one open document. It was rejected because it adds roughly 4.6 to 5.6 MB of vendored code that the project must track and patch itself (Maven and Dependabot cannot see it, and CI passed on the vulnerable PR), a cross-frame protocol, a CSP and CORS filter, a vendoring script, an advisory check, and a named owner with a patch deadline, all to put an untrusted parser back in the browser for a document that the server already rasterizes today. The residual risk is small but the ongoing cost is not, and the server-side design has neither.
- **Vendoring from Mozilla instead of npm or WebJars** fixes provenance and patch latency but none of the above.

## 16. Hardening the parser this design does use

The chosen design still parses untrusted PDFs, on the server, with PDFBox. CARLOS already does this for every document view and every fax preview, so nothing here is new exposure, but the feature adds a composition step and heavier use of page rendering. These controls apply to `showPage`, `DocumentTextBoxes2Action`, and `AnnotatedDocumentComposer` alike, and the first three should also be applied to the existing `ManageDocument2Action` render path in the same PR.

1. **Bounded memory.** Load with `Loader.loadPDF(file, IOUtils.createTempFileOnlyStreamCache())`, as `createCacheVersion2` already does, so object streams spill to disk rather than heap.
2. **Bounded output.** Before rendering, compute the page's CropBox at the requested DPI and refuse any page over 30 megapixels with a 422. A crafted page box of 200 by 200 inches at 144 dpi would otherwise request 830 megapixels. Today's `showPage` has no such bound.
3. **Bounded time.** `BoundedPdfTask` runs one parse on a daemon thread with a hard deadline and cancels on expiry, logging the document number only. Composition (60s), the page-count probe that enforces the page ceiling (60s), and word-box text extraction (15s) all route through it, so the ceiling lives in one place rather than being declared per call site and applied in none — which is exactly how the extraction timeout came to be a named constant that nothing read. Cancellation frees the request thread; it cannot force PDFBox to abandon a parse mid-stream, which is why the worker is a daemon. **Page rendering still has no timeout**: the megapixel ceiling above bounds the output size, which is the practical lever, but a pathological parse could still occupy a request thread. Adding the same treatment to `showPage` touches a legacy path shared by the whole document module and is deliberately left as a follow-up rather than folded into this change.
4. **Bounded input.** Annotation is offered only for documents up to 200 pages and 50 MB; larger documents can still be viewed and faxed as-is. Inbound faxes are far below this. The page count is read from the FILE, never from the `document` row's `numberofpages` — that column is metadata, legacy rows carry zero, and a ceiling checked against it can be satisfied by a document of any length. The view gate, the annotation page-range bound on save, and the ceiling all read the same number, and the copy is filed with it rather than inheriting the source row's.
5. **Decoder surface tracked by Maven.** The JBIG2, JPEG 2000, and TIFF decoders PDFBox delegates to are ImageIO plugins declared in `pom.xml`, so Dependabot and `dependency-review` see them. Keep them declared explicitly rather than transitively so version pins are visible.
6. **No JavaScript, no XFA.** PDFBox does not execute document JavaScript. The composer copies page content and appends; it never evaluates actions, form calculations, or XFA. Nothing in this design calls `PDAcroForm.flatten()` or touches the document catalog beyond page objects.
7. **Compose on a copy, verify the output.** The composer reads a read-only copy in app temp, writes to memory, asserts `%PDF` and an unchanged page count, and only then writes into `DOCUMENT_DIR`. A composition failure leaves no partial file.
8. **The viewer page is a plain page.** It receives PNG or JPEG page images decoded by the browser's own process-sandboxed image code, sets a `Content-Security-Policy` of `default-src 'self'; img-src 'self' data:; script-src 'self' 'nonce-<per-response>'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'`, and runs one static script file. There is no third-party code on it.

The one control this design does not add is out-of-process rendering, running PDFBox in a separate JVM with its own limits. It is the next step if the threat model ever calls for it, and it can be added behind the same `AnnotatedDocumentComposer` interface without touching the rest of this design.
