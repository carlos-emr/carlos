# Fax from Show-Document — Implementation Plan

## Goal

Enable providers to fax inbox documents directly from the **showDocument** viewer, with a
combined pharmacy + specialist fax-recipient autocomplete that surfaces typed results with
Bootstrap 5 badges indicating the recipient category.

---

## Phase 1 — Core fax-from-inbox flow (DONE)

### Deliverables

| # | What | File(s) |
|---|------|---------|
| 1 | DAO search method for specialists+services by keyword | `ServiceSpecialistsDao.searchSpecialistsWithService` |
| 2 | JSON autocomplete endpoint: combined pharmacy + specialist | `FaxRecipientSearch2Action` |
| 3 | Vanilla-JS autocomplete module with Bootstrap badge rendering | `faxRecipientAutocomplete.js` |
| 4 | Action that validates + opens the fax composition form for a document | `FaxDocument2Action` |
| 5 | Stub implementation for `FaxManagerImpl.renderDocument()` | `FaxManagerImpl` |
| 6 | Fax button in the document inbox viewer | `showDocument.jsp` |
| 7 | Swap jQuery UI contact-search autocomplete in CoverPage.jsp | `CoverPage.jsp` |
| 8 | Struts action registrations | `struts-provider.xml`, `struts-document.xml` |
| 9 | Error page for non-faxable documents | `faxNotAvailable.jsp` |

### Architecture decisions

**Phase 1 only supports PDF documents.** Inbox documents stored as TIFF/image require
server-side PDF conversion before faxing. That conversion path is deferred to Phase 2.

**Why a dedicated `FaxDocument2Action` instead of extending `Fax2Action`?**
The existing `Fax2Action.prepareFax()` is oriented toward eforms and requires the PDF to be
rendered before the action runs (because it calls `documentAttachmentManager`). For inbox
documents the file already exists on disk; wiring that through the existing prepareFax path
would require passing the file path as a parameter the user can tamper with. The new action
resolves the document server-side and sets the path as a request attribute, then forwards to
`CoverPage.jsp` directly.

**Autocomplete endpoint: GET, not POST.**
The `/fax/SearchFaxRecipient` endpoint is a read-only search returning non-PHI data (provider
names, service names, pharmacy names, fax numbers from an administrative directory). GET is
appropriate; no CSRF token is needed.

**Specialist deduplication per service.**
A specialist enrolled in N services produces N autocomplete rows, each with the service
description as its badge. This matches the clinical requirement: the same specialist may have
different context for different referral services (and may have service-specific contact
numbers in future data model iterations).

**`hideFromView` respected.**
The `searchSpecialistsWithService` HQL query filters `pro.hideFromView = false`, so
specialists marked hidden from search are never returned in fax recipient results.

**Pharmacy fax numbers.**
Pharmacies are searched by name/address using the existing
`PharmacyInfoDao.searchPharmacyByNameAddressCity(term, "")` query. Active pharmacies with a
non-blank `fax` field are included. The badge label is always the literal string `"pharmacy"`.

### New routes

| Route | Class | HTTP | Auth |
|-------|-------|------|------|
| `GET /fax/SearchFaxRecipient?term=` | `FaxRecipientSearch2Action` | GET | `_fax r` |
| `GET /documentManager/FaxDocument?docId=` | `FaxDocument2Action` | GET | `_edoc r` + `_fax r` |

### Result keys used by FaxDocument2Action

| Key | Value |
|-----|-------|
| `"preview"` | Forward to `/WEB-INF/jsp/fax/CoverPage.jsp` |
| `"noFax"` | Forward to `/WEB-INF/jsp/documentManager/faxNotAvailable.jsp` |

---

## Phase 2 — PDF.js annotation viewer before fax (DONE)

Providers can now review and annotate a PDF document inside CARLOS before sending it to fax.
Annotations are saved back to the original file on disk and are embedded in the faxed copy.

### New user flow

1. Provider clicks **Fax** on a PDF inbox document.
2. `FaxDocument2Action` (GET, `?faxReady` absent) forwards to `FaxAnnotateViewer.jsp`.
3. The viewer renders the PDF via PDF.js and presents an annotation toolbar:
   - **T** — FreeText (type text on the document)
   - **pen** — Ink / freehand drawing
   - **highlighter** — Highlight
   - **fa-signature** — Provider signature stamp
4. The provider's signature stamp is the same one captured under **Provider Preferences**
   (`ProviderSignatureStamp2Action` / `ProviderSignatureImage2Action`) — the same image already
   used for consultations, prescriptions, and eForms. If a saved stamp exists, the viewer offers
   "Use saved signature"; if not (or the provider chooses "Draw new signature"), a Bootstrap
   modal canvas lets them draw one, which is saved back to Provider Preferences storage
   (`UserProperty.PROVIDER_CONSULT_SIGNATURE`, file `consult_sig_<providerNo>.png` in the eForm
   image directory) so it is available everywhere the stamp is used, not just in this viewer.
5. Clicking **Save & Continue to Fax** POSTs the annotated PDF to `SaveAnnotatedDocument`.
6. The server overwrites the document file and writes an audit log entry that lists the
   annotation types used (`signed`, `text`, `drawn`, `highlighted`) but **no content**.
7. The browser navigates to `FaxDocument?docId=N&faxReady=true`, which forwards to
   `CoverPage.jsp` for fax composition.

### Architecture decisions

**PDF.js delivery via WebJar.**
`org.webjars.npm:pdfjs-dist:4.4.168` is added as a `<scope>runtime</scope>` Maven dependency.
Tomcat 11 (Servlet 6.0) auto-serves `META-INF/resources/webjars/**` from JAR classpath
entries, making the assets available at `/webjars/pdfjs-dist/4.4.168/...` without any
additional servlet mapping. The `struts.action.excludePattern` was extended to include
the `.mjs` extension and the `^/webjars/.*` prefix.

**ES modules in JSP.**
PDF.js 4.x is ES-module-only (`pdf.mjs`, `pdf.worker.mjs`, `pdf_viewer.mjs`). Because JSP
EL `${}` and JS template literals both use `${}`, dynamic import paths are built with
`<%= request.getContextPath() %>` scriptlets:

```javascript
const pdfJsPath = '<%= request.getContextPath() %>/webjars/pdfjs-dist/4.4.168';
const pdfjsLib  = await import(pdfJsPath + '/build/pdf.mjs');
```


**Signature stamp injection.**
PDF.js's STAMP mode normally opens an OS file picker (`<input type="file">`). To feed the
drawn canvas PNG instead, a capture-phase `click` listener intercepts the hidden file input,
sets its `.files` property via `DataTransfer`, and dispatches a synthetic `change` event.

**Signature storage reuses Provider Preferences — no duplicate storage.**
The fax viewer does not maintain its own signature image or endpoints. It calls the existing
`/provider/providerSignatureStamp?method=check` (returns `{exists, imageUrl}`) and
`?method=saveDrawn` (persists a base64 PNG from a canvas) routes, and renders the image via the
existing `/provider/providerSignatureImage` route — all gated by the existing `_pref r`/`_pref w`
checks in `ProviderSignatureStamp2Action`/`ProviderSignatureImage2Action`. This keeps a single
source of truth: a signature saved or updated from the fax viewer is immediately the same
signature used for consultations, prescriptions, and eForms, and vice versa. (An earlier draft
of this feature stored a separate copy at `{DOCUMENT_DIR}/signatures/provider_{providerNo}.png`
via dedicated `GetProviderSignature2Action`/`SaveProviderSignature2Action` classes — that
duplicate storage was removed in favor of the shared Provider Preferences signature.)

**PHI-safe annotation logging.**
The client sends only the set of tool-type labels used (`signed`, `text`, `drawn`,
`highlighted`). `SaveAnnotatedDocument2Action` filters these against a fixed allowlist before
writing them to the log. No annotation content is transmitted or stored in any log.

**`faxReady` URL parameter.**
`FaxDocument2Action` checks `?faxReady=true`. Absent → `annotate` result (viewer).
Present → `preview` result (CoverPage). This avoids duplicating the EDoc lookup and
privilege checks.

### New routes

| Route | Class | HTTP | Auth |
|-------|-------|------|------|
| `GET  /documentManager/ServeDocument?docId=` | `ServeDocument2Action` | GET | `_edoc r` |
| `POST /documentManager/SaveAnnotatedDocument` | `SaveAnnotatedDocument2Action` | POST | `_edoc w` |

### Reused routes (pre-existing — Provider Preferences signature stamp)

| Route | Class | HTTP | Auth |
|-------|-------|------|------|
| `GET  /provider/providerSignatureStamp?method=check` | `ProviderSignatureStamp2Action` | GET | `_pref r` |
| `POST /provider/providerSignatureStamp?method=saveDrawn` | `ProviderSignatureStamp2Action` | POST | `_pref w` |
| `GET  /provider/providerSignatureImage` | `ProviderSignatureImage2Action` | GET | `_pref r` |

### Updated result keys for FaxDocument2Action

| Key | Value |
|-----|-------|
| `"annotate"` | Forward to `/WEB-INF/jsp/fax/FaxAnnotateViewer.jsp` |
| `"preview"` | Forward to `/WEB-INF/jsp/fax/CoverPage.jsp` |
| `"noFax"` | Forward to `/WEB-INF/jsp/documentManager/faxNotAvailable.jsp` |

---

## Phase 3 — Non-PDF document faxing (future)

Inbox documents stored as multi-page TIFF or other image formats cannot be passed to the
fax sender as-is. Phase 3 will:

1. Implement `FaxManagerImpl.renderDocument()` to convert image documents to PDF using
   PDFBox or an equivalent library (the eform path already uses PDFBox via
   `DocumentAttachmentManagerImpl`).
2. Store the converted PDF in the existing fax temp directory (same as eforms/prescriptions).
3. Remove the "PDF only" restriction in `FaxDocument2Action`.
4. Update `showDocument.jsp` to enable the Fax button for non-PDF documents once Phase 3
   lands (the button is already rendered but disabled with a tooltip for non-PDFs).

---

## Phase 4 — Fax status tracking from inbox (future)

After a document is faxed, surface the fax job status (queued / sent / error) back in the
inbox viewer so providers know the outcome without leaving the context.

---

## Files changed in Phase 1

```text
src/main/java/io/github/carlos_emr/carlos/commn/dao/
  ServiceSpecialistsDao.java                         (+searchSpecialistsWithService)
  ServiceSpecialistsDaoImpl.java                     (+searchSpecialistsWithService impl)

src/main/java/io/github/carlos_emr/carlos/fax/action/
  FaxRecipientSearch2Action.java                     (NEW)

src/main/java/io/github/carlos_emr/carlos/documentManager/actions/
  FaxDocument2Action.java                            (NEW)

src/main/java/io/github/carlos_emr/carlos/managers/
  FaxManagerImpl.java                                (renderDocument stub → basic impl)

src/main/webapp/js/
  faxRecipientAutocomplete.js                        (NEW)

src/main/webapp/WEB-INF/jsp/documentManager/
  showDocument.jsp                                   (Fax button added)
  faxNotAvailable.jsp                                (NEW)

src/main/webapp/WEB-INF/jsp/fax/
  CoverPage.jsp                                      (autocomplete swapped)

src/main/webapp/WEB-INF/classes/
  struts-provider.xml                                (+fax/SearchFaxRecipient)
  struts-document.xml                                (+documentManager/FaxDocument)

src/main/resources/
  oscarResources_en.properties                       (+showDocument.btnFax, +faxPdfOnlyTooltip)
  oscarResources_es.properties                       (+showDocument.btnFax, +faxPdfOnlyTooltip)
  oscarResources_fr.properties                       (+showDocument.btnFax, +faxPdfOnlyTooltip)
  oscarResources_pl.properties                       (+showDocument.btnFax, +faxPdfOnlyTooltip)
  oscarResources_pt_BR.properties                    (+showDocument.btnFax, +faxPdfOnlyTooltip)
```

## Files changed in Phase 2

```text
pom.xml
  +org.webjars.npm:pdfjs-dist:4.4.168 (runtime scope)

src/main/webapp/WEB-INF/classes/
  struts.xml                                         (excludePattern: +mjs, +/webjars/*)
  struts-document.xml                                (+FaxDocument annotate result,
                                                      +documentManager/ServeDocument,
                                                      +documentManager/SaveAnnotatedDocument)

src/main/java/io/github/carlos_emr/carlos/documentManager/actions/
  FaxDocument2Action.java                            (+faxReady branch → annotate vs preview)
  ServeDocument2Action.java                          (NEW — streams PDF for PDF.js)
  SaveAnnotatedDocument2Action.java                  (NEW — persists annotated PDF, audit log)

src/main/webapp/WEB-INF/jsp/fax/
  FaxAnnotateViewer.jsp                              (NEW — PDF.js annotation viewer; signature
                                                      check/save/render calls the existing
                                                      Provider Preferences signature-stamp routes,
                                                      no new signature storage added)

src/test/java/io/github/carlos_emr/carlos/app/contract/
  MutatorActionGetRejectionContractTest.java         (+SaveAnnotatedDocument2Action)
```

> **Note:** an earlier draft of Phase 2 added dedicated `fax/ProviderSignature` (GET) and
> `fax/SaveProviderSignature` (POST) routes backed by `GetProviderSignature2Action`/
> `SaveProviderSignature2Action`, storing a separate PNG at
> `{DOCUMENT_DIR}/signatures/provider_{providerNo}.png`. That duplicate storage was removed —
> the fax viewer now reads/writes the same signature stamp used by Provider Preferences
> (`ProviderSignatureStamp2Action` / `ProviderSignatureImage2Action`,
> `UserProperty.PROVIDER_CONSULT_SIGNATURE`).
