# Fax Widget for showDocument.jsp — Implementation Plan

## Executive Summary

This document provides an extensive plan to add fax capability directly from the
**showDocument.jsp** document inbox viewer in CARLOS EMR. The feature lets users
fax an incoming document — with optional page selection — without leaving the
browser. It mirrors the existing eForm fax widget in flow and architecture.

**Issue**: [#416](https://github.com/carlos-emr/carlos/issues/416)  
**Date**: 2026-04-02  
**Author**: claude[bot] (triggered by @yingbull)

---

## 1. Code Assessment

### 1.1 Existing Fax Infrastructure

CARLOS already has a mature fax pipeline. The following components exist and are
fully functional:

| Component | File | Purpose |
|-----------|------|---------|
| `Fax2Action` | `fax/action/Fax2Action.java` | Main fax Struts2 action (prepareFax, queue, cancel, getPreview, getPageCount) |
| `FaxManager` | `managers/FaxManager.java` | Interface: render docs, create/save jobs, preview, validation |
| `FaxManagerImpl` | `managers/FaxManagerImpl.java` | Implementation (Spring `@Service`) |
| `FaxDocumentManager` | `managers/FaxDocumentManager.java` | Renders eforms/forms to PDF for faxing |
| `FaxConfig` / `FaxConfigDao` | `commn/model/FaxConfig.java` | Per-account fax gateway config (MIDDLEWARE or SRFAX) |
| `FaxJob` / `FaxJobDao` | `commn/model/FaxJob.java` | Persisted fax job queue |
| `FaxProviderClient` | `fax/provider/FaxProviderClient.java` | Abstract provider (SRFax, Middleware) |
| `CoverPage.jsp` | `webapp/fax/CoverPage.jsp` | Review & send UI with From/To/Copy fields |
| `FaxJobParams` | `fax/dto/FaxJobParams.java` | Builder DTO for fax job creation |

**Fax flow for eForm (reference):**
```
User clicks Fax button in eForm
  → AddEForm2Action (action=fax)
    → result "fax" → fax/faxAction.do?method=prepareFax&transactionType=EFORM
      → Fax2Action.prepareFax()
        → documentAttachmentManager.renderEFormWithAttachments() → Path (temp PDF)
        → request.setAttribute("faxFilePath", pdfPath)
        → return "preview"
          → fax/CoverPage.jsp  (user fills From/To/Comment/Cover)
            → POST fax/faxAction.do?method=queue
              → Fax2Action.queue()
                → faxManager.createAndSaveFaxJob()
                → faxManager.logFaxJob()
                → return "preview"  (shows success/error)
```

### 1.2 Critical Gaps for Document Faxing

The code analysis reveals three specific gaps:

#### Gap 1 — `FaxManagerImpl.renderDocument()` is a stub

```java
// FaxManagerImpl.java line 154
@Override
public Path renderDocument(LoggedInInfo loggedInInfo, int documentNo, int demographicNo) {
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, demographicNo)) {
        throw new RuntimeException("missing required sec object (_edoc)");
    }
    logger.info("Rendering document number " + documentNo + " for fax preview.");
    return null;  // ← STUB — always returns null
}
```

The `TransactionType.DOCUMENT` enum value exists and is wired in the switch
statement, but `renderDocument()` does nothing. This is the primary backend gap.

#### Gap 2 — No Fax button in `showDocument.jsp`

`showDocument.jsp` has: Acknowledge, Comment, Forward, File, Close, Print, Message,
Tickler, EChart, Master, ApptHistory, Refile — but **no Fax button**. A grep for
`fax` in the file returns zero results.

#### Gap 3 — `Fax2Action.prepareFax()` doesn't handle DOCUMENT type

```java
// Fax2Action.java line 339
public String prepareFax() {
    // ...
    if (transactionType.equals(TransactionType.EFORM)) {
        // ... handles EFORM only
        pdfPath = documentAttachmentManager.renderEFormWithAttachments(request, response);
    }
    // DOCUMENT case not handled — pdfPath stays null → "error" is returned
}
```

#### Gap 4 — `Fax2Action.cancel()` doesn't handle DOCUMENT redirect

When fax is cancelled, the DOCUMENT case should redirect back to the document
inbox, but no redirect is coded for `TransactionType.DOCUMENT`.

### 1.3 Available Backend Capabilities

Everything else needed is already in place:

- `EDocUtil.getDoc(documentNo)` → `EDoc` object with `getFilePath()` →
  `DOCUMENT_DIR + filename`
- `EDoc.getFilePath()` resolves via `CarlosProperties.DOCUMENT_DIR`
- **Apache PDFBox 3.0.7** is already in `pom.xml` (Apache 2.0 licence) — can
  copy pages, render thumbnails, validate PDFs
- `ConcatPDF` utility class exists in the codebase for PDF merging
- `faxManager.resolveAndValidateFilePath()` handles security for document paths
- `faxManager.getFaxPreviewImage()` renders a page to PNG already

---

## 2. JavaScript / Front-end Library Evaluation

### 2.1 Licence Compatibility Analysis

CARLOS is GPL v2+. Libraries used in the browser (as JS, not linked into the Java
binary) must be **GPL-compatible** to be distributed together. MIT and Apache 2.0
are universally GPL-compatible.

| Library | Licence | GPL v2 Compatible | Notes |
|---------|---------|:-----------------:|-------|
| **PDF.js** (Mozilla) | Apache 2.0 | ✅ | The standard for rendering PDFs in browser |
| **Bootstrap 5.3** | MIT | ✅ | Already in project |
| **jQuery 3.7** | MIT | ✅ | Already in project |
| **libphonenumber-js** | MIT | ✅ | Phone number validation/formatting |
| **pdf-lib** | MIT | ✅ | Client-side PDF page extraction (alternative to PDFBox server-side) |
| iText 7 / pdfiumJava | AGPL v3 | ❌ | Incompatible with GPL v2 |
| PDFtron | Commercial | ❌ | Commercial licence only |

### 2.2 Recommended JS Stack

**Phase 1 (MVP):**
- No new JS libraries required. Uses jQuery (already present) for AJAX and the
  existing Bootstrap 5 modal pattern.

**Phase 2 (Page Selector):**
- **PDF.js 4.x** (Apache 2.0) — renders individual PDF pages as canvas thumbnails
  inside the fax dialog. CDN: `https://unpkg.com/pdfjs-dist@4.x.x/build/pdf.min.mjs`
- **pdf-lib 1.17** (MIT) — optional client-side page extraction if we want to
  avoid a server round-trip for page subsetting.
  CDN: `https://unpkg.com/pdf-lib@1.17.1/dist/pdf-lib.min.js`
- **libphonenumber-js 1.x** (MIT) — optional fax number formatter (lightweight
  vs. full Google libphonenumber). CDN available on unpkg.

**Why PDF.js?**
- The only well-maintained, fully open-source (Apache 2.0) PDF renderer for browsers
- Renders each page to a `<canvas>` — perfect for page-thumbnail checkboxes
- Built-in: page count, page rendering at any scale, text layer
- Used by Firefox's built-in PDF viewer; battle-tested at scale
- Can load a URL (the existing `/documentManager/ManageDocument.do?method=display&doc_no=X`)

**Why not pdf-lib for extraction?**
- Client-side extraction works but doubles upload bandwidth (user uploads subset
  PDF from browser)
- Server-side extraction with PDFBox (already in project, Apache 2.0) is cleaner:
  user sends only a list of page numbers, server produces the subset PDF
- Recommended: use PDF.js for page thumbnails, PDFBox on server for extraction

---

## 3. Proposed Architecture

### 3.1 Phase 1 — Core Fax Button (MVP)

**Scope:** Adds a "Fax" button to `showDocument.jsp` that faxes the entire document
through the existing CoverPage.jsp flow. Requires fixing the backend stub.

```
User clicks "Fax" button in showDocument.jsp
  → Popup: fax/faxAction.do?method=prepareFax
           &transactionType=DOCUMENT
           &transactionId={documentNo}
           &demographicNo={demographicID}
    → Fax2Action.prepareFax() [DOCUMENT case, newly implemented]
      → faxManager.renderDocument(loggedInInfo, documentNo, demographicNo)
        → EDocUtil.getDoc(documentNo) → EDoc
        → validate content type (PDF or image)
        → if PDF: copy to temp dir → return Path
        → if TIFF/image: convert with PDFBox → return Path
      → return "preview"
        → fax/CoverPage.jsp (existing — no changes needed)
          → user fills To/From fields
          → POST fax/faxAction.do?method=queue
            → existing queue() creates/saves FaxJob → faxed
```

**Files changed/created:**

| # | File | Change Type | Description |
|---|------|-------------|-------------|
| 1 | `managers/FaxManagerImpl.java` | Modify | Implement `renderDocument()` — copy PDF to temp dir or convert TIFF→PDF |
| 2 | `fax/action/Fax2Action.java` | Modify | Add DOCUMENT case in `prepareFax()` and `cancel()` |
| 3 | `documentManager/showDocument.jsp` | Modify | Add "Send by Fax" button with `_fax` privilege check |
| 4 | `struts-provider.xml` | Modify | Add `DOCUMENT` cancel redirect result to fax action |

### 3.2 Phase 2 — PDF.js Page Selector Dialog (Enhanced)

**Scope:** Before the CoverPage.jsp, shows a modal with PDF page thumbnails.
User can select a subset of pages. Only selected pages are faxed.

```
User clicks "Fax" button in showDocument.jsp
  → Opens Bootstrap 5 modal:  #documentFaxModal
    → PDF.js loads document from:
       /documentManager/ManageDocument.do?method=display&doc_no={docId}
    → Renders page thumbnails as <canvas> elements with checkboxes
    → User selects pages (or "All pages")
    → User clicks "Continue to Fax"
      → AJAX POST: documentManager/DocumentFaxPrep.do
                   { docNo, demographicNo, selectedPages: [1,3,5] }
        → DocumentFaxPrep2Action.execute()
          → faxManager.renderDocumentPages(loggedInInfo, docNo, demoNo, pages)
            → EDocUtil.getDoc(docNo) → EDoc.getFilePath()
            → PDFBox: PDDocument.load(file) → extract selected pages
            → write to temp dir → return Path
          → set attributes: faxFilePath, transactionType=DOCUMENT, accounts, etc.
          → return "preview"
            → fax/CoverPage.jsp  (no changes needed)
```

**Files changed/created:**

| # | File | Change Type | Description |
|---|------|-------------|-------------|
| 1 | `managers/FaxManagerImpl.java` | Modify | Implement `renderDocument()` + new `renderDocumentPages()` |
| 2 | `managers/FaxManager.java` | Modify | Add `renderDocumentPages(... int[] pages)` to interface |
| 3 | `fax/action/Fax2Action.java` | Modify | DOCUMENT case in `prepareFax()` and `cancel()` |
| 4 | `documentManager/actions/DocumentFaxPrep2Action.java` | **Create** | New Struts2 action: validates, calls renderDocumentPages, forwards to CoverPage.jsp |
| 5 | `documentManager/showDocument.jsp` | Modify | Replace direct button with modal trigger |
| 6 | `documentManager/documentFaxWidget.js` | **Create** | PDF.js integration: load doc, render thumbnails, page selector logic |
| 7 | `struts-document.xml` | Modify | Add `documentManager/DocumentFaxPrep` action mapping |
| 8 | `struts-provider.xml` | Modify | Add DOCUMENT cancel redirect result |

---

## 4. Detailed Implementation Steps

### Step 1 — Implement `FaxManagerImpl.renderDocument()`

**File:** `src/main/java/io/github/carlos_emr/carlos/managers/FaxManagerImpl.java`

Replace the stub with real implementation:

```java
@Override
public Path renderDocument(LoggedInInfo loggedInInfo, int documentNo, int demographicNo) {
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, demographicNo)) {
        throw new RuntimeException("missing required sec object (_edoc)");
    }

    logger.info("Rendering document number " + documentNo + " for fax.");

    EDoc edoc = EDocUtil.getDoc(String.valueOf(documentNo));
    if (edoc == null) {
        throw new IllegalArgumentException("Document not found: " + documentNo);
    }

    String filePath = edoc.getFilePath();
    File sourceFile = PathValidationUtils.validateExistingPath(
        new File(filePath),
        new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"))
    );

    String contentType = edoc.getContentType();

    // If document is already a PDF, copy to temp dir for fax
    if (contentType != null && contentType.toLowerCase().contains("pdf")) {
        return nioFileManager.copyFileToTemp(sourceFile.toPath());
    }

    // If document is an image (TIFF, JPEG, PNG), convert to PDF using PDFBox
    if (contentType != null && (contentType.contains("tiff") || contentType.contains("jpeg")
            || contentType.contains("png") || contentType.contains("image"))) {
        return convertImageToPdf(sourceFile);
    }

    throw new UnsupportedOperationException(
        "Cannot fax document with content type: " + contentType);
}

/**
 * Converts an image file (TIFF, JPEG, PNG) to a single-page PDF using PDFBox.
 * PDFBox 3.x (Apache 2.0 licence) is already a project dependency.
 */
private Path convertImageToPdf(File imageFile) {
    try {
        // PDFBox 3.x API
        Path tempPdf = Files.createTempFile("fax_doc_", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            // Load image and draw on page
            PDImageXObject image = PDImageXObject.createFromFileByContent(imageFile, doc);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float scale = Math.min(
                    page.getMediaBox().getWidth() / image.getWidth(),
                    page.getMediaBox().getHeight() / image.getHeight()
                );
                cs.drawImage(image, 0, 0,
                    image.getWidth() * scale,
                    image.getHeight() * scale);
            }
            doc.save(tempPdf.toFile());
        }
        return tempPdf;
    } catch (IOException e) {
        throw new RuntimeException("Failed to convert image to PDF for fax", e);
    }
}
```

**New method for Phase 2 page extraction:**

```java
/**
 * Renders a subset of pages from a document to a temporary PDF.
 * Uses PDFBox 3.x (Apache 2.0) for page extraction.
 *
 * @param loggedInInfo  the logged-in user info
 * @param documentNo    the EDoc document number
 * @param demographicNo the patient demographic number for security check
 * @param pages         1-based page numbers to include (null = all pages)
 * @return Path to a temporary PDF file containing only the selected pages
 */
public Path renderDocumentPages(LoggedInInfo loggedInInfo, int documentNo,
                                 int demographicNo, int[] pages) {
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, demographicNo)) {
        throw new RuntimeException("missing required sec object (_edoc)");
    }

    EDoc edoc = EDocUtil.getDoc(String.valueOf(documentNo));
    String filePath = edoc.getFilePath();
    File sourceFile = PathValidationUtils.validateExistingPath(
        new File(filePath),
        new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"))
    );

    try (PDDocument fullDoc = Loader.loadPDF(sourceFile)) {
        PDDocument subsetDoc = new PDDocument();

        if (pages == null || pages.length == 0) {
            // All pages
            for (int i = 0; i < fullDoc.getNumberOfPages(); i++) {
                subsetDoc.addPage(fullDoc.getPage(i));
            }
        } else {
            // Selected pages (1-based)
            for (int pageNum : pages) {
                if (pageNum >= 1 && pageNum <= fullDoc.getNumberOfPages()) {
                    subsetDoc.addPage(fullDoc.getPage(pageNum - 1));
                }
            }
        }

        Path tempPdf = Files.createTempFile("fax_pages_", ".pdf");
        subsetDoc.save(tempPdf.toFile());
        subsetDoc.close();
        return tempPdf;

    } catch (IOException e) {
        throw new RuntimeException("Failed to extract pages for fax", e);
    }
}
```

---

### Step 2 — Update `Fax2Action.prepareFax()` for DOCUMENT type

**File:** `src/main/java/io/github/carlos_emr/carlos/fax/action/Fax2Action.java`

In the `prepareFax()` method, add a DOCUMENT case parallel to the EFORM case:

```java
public String prepareFax() {
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    TransactionType transactionType = TransactionType.valueOf(getTransactionType().toUpperCase());
    String actionForward = ERROR;
    Path pdfPath = null;
    List<FaxConfig> accounts = faxManager.getFaxGatewayAccounts(loggedInInfo);

    if (!accounts.isEmpty()) {
        if (transactionType.equals(TransactionType.EFORM)) {
            // ... existing EFORM handling unchanged ...
        } else if (transactionType.equals(TransactionType.DOCUMENT)) {
            // NEW: Render the document to PDF for faxing
            try {
                pdfPath = faxManager.renderDocument(loggedInInfo, transactionId, demographicNo);
            } catch (RuntimeException e) {
                logger.error("Failed to prepare document for fax: " + transactionId, e);
                request.setAttribute("errorMessage",
                    "This document cannot be faxed: " + e.getMessage());
                return ERROR;
            }
        }
    } else {
        request.setAttribute("message", "No active fax accounts found.");
    }

    if (pdfPath != null) {
        List<Path> documents = new ArrayList<>();
        documents.add(pdfPath);
        request.setAttribute("accounts", accounts);
        request.setAttribute("demographicNo", demographicNo);
        request.setAttribute("documents", documents);
        request.setAttribute("transactionType", transactionType.name());
        request.setAttribute("transactionId", transactionId);
        request.setAttribute("faxFilePath", pdfPath);
        request.setAttribute("letterheadFax", letterheadFax);
        request.setAttribute("professionalSpecialistName", recipient);
        request.setAttribute("fax", recipientFaxNumber);
        actionForward = "preview";
    }

    return actionForward;
}
```

**Also update `cancel()`** to redirect back to the document inbox:

```java
public String cancel() {
    // ... existing code ...
    } else if (TransactionType.DOCUMENT.name().equalsIgnoreCase(transactionType)) {
        try {
            response.sendRedirect(request.getContextPath()
                + "/documentManager/inboxManage.do?method=prepareForIndexPage");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }
    // ...
}
```

---

### Step 3 — Add Fax Button to `showDocument.jsp`

**File:** `src/main/webapp/documentManager/showDocument.jsp`

Add this import at top:
```jsp
<%@ page import="io.github.carlos_emr.carlos.managers.FaxManager" %>
```

Add the fax button in the toolbar section (near the Print button, around line 497),
wrapped in a privilege check:

```jsp
<%-- Fax button: only shown when fax is configured and user has _fax write privilege --%>
<%
    boolean faxEnabled = FaxManager.isEnabled()
        && securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null);
    // Only allow faxing PDF and image documents
    boolean docFaxable = curdoc.getContentType() != null
        && (curdoc.getContentType().contains("pdf")
            || curdoc.getContentType().contains("tiff")
            || curdoc.getContentType().contains("jpeg")
            || curdoc.getContentType().contains("png")
            || curdoc.getContentType().contains("image"));
%>
<% if (faxEnabled && docFaxable) { %>
<input type="button"
       class="btn btn-outline-secondary btn-sm"
       id="faxBtn_<%=docId%>"
       value="<fmt:message key="showDocument.btnFax"/>"
       onclick="sendDocumentByFax('<%=Encode.forJavaScriptAttribute(docId)%>',
                                  '<%=Encode.forJavaScriptAttribute(demographicID)%>')"
       <%=btnDisabled%>>
<% } %>
```

Add the JS function in the `<script>` block within the `inWindow` section:

```javascript
/**
 * Opens the fax preparation dialog for a document from the inbox viewer.
 * Triggers Fax2Action.prepareFax() with TransactionType=DOCUMENT.
 * Requires _fax write privilege (checked server-side).
 *
 * @param {string} docId         the document ID (EDoc.docId)
 * @param {string} demographicNo the linked patient demographic number (may be empty)
 */
function sendDocumentByFax(docId, demographicNo) {
    var url = contextpath + '/fax/faxAction.do'
        + '?method=prepareFax'
        + '&transactionType=DOCUMENT'
        + '&transactionId=' + encodeURIComponent(docId)
        + '&demographicNo=' + encodeURIComponent(demographicNo || '0');
    popup(800, 850, url, 'fax_doc_' + docId);
}
```

---

### Step 4 — Add i18n Keys

**File:** `src/main/resources/oscarResources_en.properties` (and other locale files)

```properties
showDocument.btnFax=Send by Fax
```

---

### Step 5 — Update struts-provider.xml for DOCUMENT cancel redirect

**File:** `src/main/webapp/WEB-INF/classes/struts-provider.xml`

Add the DOCUMENT result to the fax action:

```xml
<action name="fax/faxAction" class="io.github.carlos_emr.carlos.fax.action.Fax2Action">
    <result name="preview">/fax/CoverPage.jsp</result>
    <result name="error">/errorpage.jsp</result>
    <result name="CONSULTATION">/encounter/ViewRequest.do</result>
    <result name="EFORM" type="redirect">${ctx}/eform/efmshowform_data.jsp</result>
    <!-- NEW: DOCUMENT cancel redirect -->
    <result name="DOCUMENT" type="redirect">/documentManager/inboxManage.do?method=prepareForIndexPage</result>
    <result name="eFormError">/fax/EFormError.jsp</result>
</action>
```

---

### Step 6 (Phase 2) — Create `DocumentFaxPrep2Action.java`

**File:** `src/main/java/io/github/carlos_emr/carlos/documentManager/actions/DocumentFaxPrep2Action.java`

```java
/**
 * Struts2 action that accepts a document ID and optional page selection,
 * renders the selected pages to a temporary PDF using PDFBox, and forwards
 * to CoverPage.jsp for the standard fax cover-page review flow.
 *
 * Used by the Phase 2 PDF.js page-selector dialog in showDocument.jsp.
 *
 * @since 2026-04-02
 */
public class DocumentFaxPrep2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // Struts2 parameters
    private Integer documentNo;
    private Integer demographicNo;
    private String selectedPages;  // comma-separated, e.g. "1,3,5"

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null)) {
            throw new SecurityException("Missing required privilege: _fax");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", demographicNo)) {
            throw new SecurityException("Missing required privilege: _edoc");
        }

        // Parse page selection
        int[] pages = parseSelectedPages(selectedPages);

        List<FaxConfig> accounts = faxManager.getFaxGatewayAccounts(loggedInInfo);
        if (accounts.isEmpty()) {
            request.setAttribute("message", "No active fax accounts found.");
            return ERROR;
        }

        Path pdfPath = faxManager.renderDocumentPages(loggedInInfo, documentNo, demographicNo, pages);
        if (pdfPath == null) {
            request.setAttribute("errorMessage", "Could not prepare document for fax.");
            return ERROR;
        }

        request.setAttribute("accounts", accounts);
        request.setAttribute("demographicNo", demographicNo);
        request.setAttribute("transactionType", FaxManager.TransactionType.DOCUMENT.name());
        request.setAttribute("transactionId", documentNo);
        request.setAttribute("faxFilePath", pdfPath);
        return "preview";
    }

    /**
     * Parses a comma-separated list of 1-based page numbers.
     * Returns null (meaning all pages) if the input is blank.
     */
    private int[] parseSelectedPages(String selectedPages) {
        if (selectedPages == null || selectedPages.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(selectedPages.split(","))
            .map(String::trim)
            .filter(s -> s.matches("\\d+"))
            .mapToInt(Integer::parseInt)
            .toArray();
    }

    @StrutsParameter
    public void setDocumentNo(Integer documentNo) { this.documentNo = documentNo; }
    public Integer getDocumentNo() { return documentNo; }

    @StrutsParameter
    public void setDemographicNo(Integer demographicNo) { this.demographicNo = demographicNo; }
    public Integer getDemographicNo() { return demographicNo; }

    @StrutsParameter
    public void setSelectedPages(String selectedPages) { this.selectedPages = selectedPages; }
    public String getSelectedPages() { return selectedPages; }
}
```

---

### Step 7 (Phase 2) — Create `documentFaxWidget.js`

**File:** `src/main/webapp/documentManager/documentFaxWidget.js`

```javascript
/**
 * documentFaxWidget.js
 * ---------------------
 * PDF.js-powered page selector for the document fax dialog in showDocument.jsp.
 *
 * Licence: GPL v2 (same as CARLOS EMR)
 * Dependencies:
 *   - PDF.js 4.x (Apache 2.0) — loaded from CDN or local bundle
 *   - Bootstrap 5.3 modal (already in project)
 *   - jQuery 3.7 (already in project)
 *
 * Usage: called by sendDocumentByFax(docId, demographicNo) in showDocument.jsp
 *
 * Flow:
 *   1. Open Bootstrap modal (#documentFaxModal)
 *   2. Load PDF via PDF.js from ManageDocument.do?method=display
 *   3. Render each page as a <canvas> thumbnail inside a checkbox group
 *   4. User selects pages (default: all selected)
 *   5. "Continue to Fax" submits to DocumentFaxPrep.do with selected page list
 *   6. Server returns redirect to CoverPage.jsp
 */

// PDF.js worker — set to a CDN URL for Phase 2; can be vendored locally later
// Apache 2.0 licence, fully GPL compatible
const PDFJS_CDN_VERSION = '4.9.155';

(function ($, window) {
    'use strict';

    // -------------------------------------------------------------------------
    // Bootstrap modal scaffold (injected once per page load)
    // -------------------------------------------------------------------------
    const FAX_MODAL_HTML = `
    <div class="modal fade" id="documentFaxModal" tabindex="-1"
         aria-labelledby="documentFaxModalLabel" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="documentFaxModalLabel">
              <i class="fa-solid fa-fax"></i> Send Document by Fax
            </h5>
            <button type="button" class="btn-close"
                    data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div id="faxModalStatus" class="alert alert-info d-none" role="alert"></div>
            <div id="faxModalPageGrid"
                 style="display:flex; flex-wrap:wrap; gap:10px; justify-content:flex-start;">
              <!-- Page thumbnails rendered here by PDF.js -->
            </div>
          </div>
          <div class="modal-footer d-flex justify-content-between">
            <div>
              <button type="button" class="btn btn-outline-secondary btn-sm"
                      id="faxSelectAll">Select All</button>
              <button type="button" class="btn btn-outline-secondary btn-sm"
                      id="faxSelectNone">Select None</button>
            </div>
            <div>
              <button type="button" class="btn btn-secondary"
                      data-bs-dismiss="modal">Cancel</button>
              <button type="button" class="btn btn-primary" id="faxContinueBtn"
                      disabled>
                Continue to Fax &rarr;
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>`;

    let _pdfjs = null;
    let _currentDocId = null;
    let _currentDemoNo = null;
    let _totalPages = 0;

    // -------------------------------------------------------------------------
    // Lazy-load PDF.js from CDN (Apache 2.0)
    // -------------------------------------------------------------------------
    function loadPdfJs(callback) {
        if (_pdfjs) { callback(_pdfjs); return; }
        const script = document.createElement('script');
        script.type = 'module';
        script.src = 'https://unpkg.com/pdfjs-dist@' + PDFJS_CDN_VERSION
            + '/build/pdf.min.mjs';
        script.onload = function () {
            import('https://unpkg.com/pdfjs-dist@' + PDFJS_CDN_VERSION
                + '/build/pdf.min.mjs').then(function (pdfjsLib) {
                pdfjsLib.GlobalWorkerOptions.workerSrc =
                    'https://unpkg.com/pdfjs-dist@' + PDFJS_CDN_VERSION
                    + '/build/pdf.worker.min.mjs';
                _pdfjs = pdfjsLib;
                callback(_pdfjs);
            });
        };
        document.head.appendChild(script);
    }

    // -------------------------------------------------------------------------
    // Ensure modal exists in DOM
    // -------------------------------------------------------------------------
    function ensureModal() {
        if (!document.getElementById('documentFaxModal')) {
            document.body.insertAdjacentHTML('beforeend', FAX_MODAL_HTML);
            // Wire up Select All / Select None
            document.getElementById('faxSelectAll').addEventListener('click', function () {
                document.querySelectorAll('.faxPageCheck').forEach(function (cb) {
                    cb.checked = true;
                });
                updateContinueButton();
            });
            document.getElementById('faxSelectNone').addEventListener('click', function () {
                document.querySelectorAll('.faxPageCheck').forEach(function (cb) {
                    cb.checked = false;
                });
                updateContinueButton();
            });
            document.getElementById('faxContinueBtn').addEventListener('click',
                submitFaxRequest);
        }
    }

    // -------------------------------------------------------------------------
    // Render page thumbnails using PDF.js
    // -------------------------------------------------------------------------
    function renderPageThumbnails(pdfDoc) {
        const grid = document.getElementById('faxModalPageGrid');
        grid.innerHTML = '';
        _totalPages = pdfDoc.numPages;

        const THUMBNAIL_WIDTH = 120;

        for (let pageNum = 1; pageNum <= pdfDoc.numPages; pageNum++) {
            (function (pn) {
                pdfDoc.getPage(pn).then(function (page) {
                    const viewport = page.getViewport({ scale: 1.0 });
                    const scale = THUMBNAIL_WIDTH / viewport.width;
                    const scaledViewport = page.getViewport({ scale: scale });

                    const canvas = document.createElement('canvas');
                    canvas.width = scaledViewport.width;
                    canvas.height = scaledViewport.height;
                    canvas.style.display = 'block';
                    canvas.style.border = '1px solid #ccc';

                    const ctx = canvas.getContext('2d');
                    page.render({ canvasContext: ctx, viewport: scaledViewport });

                    const label = document.createElement('label');
                    label.style.cursor = 'pointer';
                    label.style.textAlign = 'center';
                    label.style.fontSize = '11px';

                    const checkbox = document.createElement('input');
                    checkbox.type = 'checkbox';
                    checkbox.className = 'faxPageCheck form-check-input d-block mb-1';
                    checkbox.value = String(pn);
                    checkbox.checked = true;  // default: all selected
                    checkbox.addEventListener('change', updateContinueButton);

                    label.appendChild(canvas);
                    label.appendChild(document.createTextNode('Page ' + pn));

                    const wrapper = document.createElement('div');
                    wrapper.style.textAlign = 'center';
                    wrapper.appendChild(checkbox);
                    wrapper.appendChild(label);

                    grid.appendChild(wrapper);
                    updateContinueButton();
                });
            })(pageNum);
        }
    }

    // -------------------------------------------------------------------------
    // Enable / disable Continue button based on selection
    // -------------------------------------------------------------------------
    function updateContinueButton() {
        const anyChecked = document.querySelectorAll('.faxPageCheck:checked').length > 0;
        document.getElementById('faxContinueBtn').disabled = !anyChecked;
    }

    // -------------------------------------------------------------------------
    // Submit selected pages to DocumentFaxPrep.do
    // -------------------------------------------------------------------------
    function submitFaxRequest() {
        const checked = Array.from(
            document.querySelectorAll('.faxPageCheck:checked')
        ).map(function (cb) { return cb.value; });

        const allSelected = (checked.length === _totalPages);
        const selectedPages = allSelected ? '' : checked.join(',');

        const modal = bootstrap.Modal.getInstance(
            document.getElementById('documentFaxModal'));
        if (modal) modal.hide();

        const url = window.contextpath + '/documentManager/DocumentFaxPrep.do'
            + '?documentNo=' + encodeURIComponent(_currentDocId)
            + '&demographicNo=' + encodeURIComponent(_currentDemoNo || '0')
            + '&selectedPages=' + encodeURIComponent(selectedPages);

        popup(800, 850, url, 'fax_doc_' + _currentDocId);
    }

    // -------------------------------------------------------------------------
    // Public API: openFaxDialog(docId, demographicNo, pdfUrl)
    // -------------------------------------------------------------------------
    window.openDocumentFaxDialog = function (docId, demographicNo, pdfUrl) {
        _currentDocId = docId;
        _currentDemoNo = demographicNo;

        ensureModal();

        const grid = document.getElementById('faxModalPageGrid');
        const status = document.getElementById('faxModalStatus');

        grid.innerHTML = '';
        status.textContent = 'Loading document preview…';
        status.classList.remove('d-none');
        document.getElementById('faxContinueBtn').disabled = true;

        const modalEl = document.getElementById('documentFaxModal');
        const bsModal = new bootstrap.Modal(modalEl);
        bsModal.show();

        loadPdfJs(function (pdfjsLib) {
            pdfjsLib.getDocument(pdfUrl).promise.then(function (pdfDoc) {
                status.classList.add('d-none');
                renderPageThumbnails(pdfDoc);
            }).catch(function (err) {
                status.textContent = 'Could not load PDF preview. '
                    + 'All pages will be faxed. '
                    + err.message;
                status.classList.remove('d-none');
                // Fallback: enable continue without page selection (fax all)
                document.getElementById('faxContinueBtn').disabled = false;
                document.getElementById('faxContinueBtn').onclick = function () {
                    const m = bootstrap.Modal.getInstance(modalEl);
                    if (m) m.hide();
                    const url = window.contextpath
                        + '/documentManager/DocumentFaxPrep.do'
                        + '?documentNo=' + encodeURIComponent(docId)
                        + '&demographicNo=' + encodeURIComponent(demographicNo || '0');
                    popup(800, 850, url, 'fax_doc_' + docId);
                };
            });
        });
    };

})(jQuery, window);
```

---

### Step 8 (Phase 2) — Update `showDocument.jsp` for Modal-based Fax

Replace the Phase 1 button's onclick with the modal trigger:

```jsp
<% if (faxEnabled && docFaxable) { %>
<input type="button"
       class="btn btn-outline-secondary btn-sm"
       id="faxBtn_<%=docId%>"
       value="<fmt:message key="showDocument.btnFax"/>"
       onclick="openDocumentFaxDialog(
           '<%=Encode.forJavaScriptAttribute(docId)%>',
           '<%=Encode.forJavaScriptAttribute(demographicID)%>',
           '<%=Encode.forJavaScriptAttribute(url2)%>'
       )"
       <%=btnDisabled%>>
<% } %>
```

Add script import (after existing script includes):

```jsp
<script type="text/javascript"
    src="${pageContext.servletContext.contextPath}/documentManager/documentFaxWidget.js">
</script>
```

---

### Step 9 (Phase 2) — Add Struts action mapping

**File:** `src/main/webapp/WEB-INF/classes/struts-document.xml`

```xml
<action name="documentManager/DocumentFaxPrep"
        class="io.github.carlos_emr.carlos.documentManager.actions.DocumentFaxPrep2Action">
    <result name="preview">/fax/CoverPage.jsp</result>
    <result name="error">/errorpage.jsp</result>
</action>
```

---

## 5. Security Checklist

All new code must satisfy the CARLOS security requirements:

| Requirement | How Addressed |
|-------------|---------------|
| OWASP Encoding | All JSP output uses `Encode.forJavaScriptAttribute()`, `${e:forHtml()}` |
| CSRF Protection | Existing CSRFGuard auto-injects tokens; popup form inherits |
| `SecurityInfoManager.hasPrivilege()` | Checked for `_fax` (write) and `_edoc` (read/write) in both action classes |
| `PathValidationUtils` | Used in `renderDocument()` and `renderDocumentPages()` to validate file paths |
| Parameterized queries | No new queries; uses existing DAO layer |
| No PHI in logs | `logger.info("Rendering document number " + documentNo)` — no patient data |
| Page number validation | `parseSelectedPages()` filters to `\d+` only, bounds-checked against PDF page count |

---

## 6. Testing Plan

### Unit Tests

**File:** `src/test-modern/java/io/github/carlos_emr/carlos/managers/FaxManagerDocumentUnitTest.java`

```java
@Tag("unit") @Tag("fax") @Tag("document")
class FaxManagerDocumentUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldReturnPath_whenDocumentIsPdf()
    @Test
    void shouldConvertToPath_whenDocumentIsTiff()
    @Test
    void shouldThrowSecurity_whenMissingEdocPrivilege()
    @Test
    void shouldExtractSubsetPages_whenPagesSpecified()
    @Test
    void shouldReturnAllPages_whenNoPagesSpecified()
    @Test
    void shouldThrowIllegal_whenDocumentNotFound()
}
```

**File:** `src/test-modern/java/io/github/carlos_emr/carlos/documentManager/actions/DocumentFaxPrepActionUnitTest.java`

```java
@Tag("unit") @Tag("fax") @Tag("document")
class DocumentFaxPrepActionUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldForwardToPreview_whenDocumentReady()
    @Test
    void shouldReturnError_whenNoFaxAccounts()
    @Test
    void shouldThrowSecurity_whenMissingFaxPrivilege()
    @Test
    void shouldParseCommaSeparatedPages_whenSelectedPagesGiven()
    @Test
    void shouldReturnNullPages_whenSelectedPagesIsEmpty()
}
```

### Integration Tests

**File:** `src/test-modern/java/io/github/carlos_emr/carlos/managers/FaxManagerDocumentIntegrationTest.java`

```java
@Tag("integration") @Tag("fax") @Tag("document")
class FaxManagerDocumentIntegrationTest extends CarlosTestBase {

    @Test
    void shouldRenderDocumentPdf_forPdfContentType()
    @Test
    void shouldExtractPages_andSaveTempFile()
}
```

### Manual UI Test Checklist

1. **Phase 1 — Basic Fax:**
   - [ ] Log in as provider with `_fax` write privilege
   - [ ] Open document inbox, click a PDF document
   - [ ] "Send by Fax" button appears in toolbar
   - [ ] Click button → popup opens with CoverPage.jsp
   - [ ] Fill To/From fields → click Send → fax queued
   - [ ] Verify fax job appears in Manage Faxes inbox

2. **Phase 1 — No Fax:**
   - [ ] "Send by Fax" button NOT shown when user lacks `_fax` privilege
   - [ ] "Send by Fax" button NOT shown when no fax accounts configured
   - [ ] "Send by Fax" button NOT shown for HTML documents (non-faxable content type)

3. **Phase 2 — Page Selector:**
   - [ ] Fax button opens modal dialog
   - [ ] PDF page thumbnails load correctly (PDF.js)
   - [ ] All pages checked by default
   - [ ] Deselect pages → continue button enables/disables correctly
   - [ ] "Select All" and "Select None" work
   - [ ] Continue → CoverPage.jsp opens → fax queued with selected pages only

4. **Security:**
   - [ ] Document from different patient cannot be faxed (demographic access check)
   - [ ] Path traversal in documentNo parameter is rejected
   - [ ] Large page numbers beyond PDF range are silently ignored

---

## 7. Rollout Plan

### Phase 1 (Recommended first PR)
**Effort estimate: 1–2 days**
- `FaxManagerImpl.renderDocument()` implementation (PDF copy + image conversion)
- `Fax2Action.prepareFax()` DOCUMENT case + `cancel()` DOCUMENT redirect
- Fax button in `showDocument.jsp` (Phase 1 version — direct action)
- i18n key
- Struts XML update
- Unit tests for `renderDocument()`

### Phase 2 (Follow-on PR)
**Effort estimate: 2–3 days**
- `DocumentFaxPrep2Action.java`
- `FaxManager.renderDocumentPages()` + PDFBox page extraction
- `documentFaxWidget.js` (PDF.js modal)
- Update `showDocument.jsp` to use modal
- Struts XML update
- Unit + integration tests for page extraction and action

---

## 8. Open Questions / Future Work

1. **PDF.js CDN vs. vendored:** Phase 2 loads PDF.js from unpkg CDN. For a fully
   offline/compliant deployment, `pdfjs-dist` should be vendored into
   `src/main/webapp/library/pdfjs/`. This is the same pattern used for jQuery and
   Bootstrap.

2. **TIFF multi-page conversion:** The `convertImageToPdf()` stub above creates a
   single-page PDF. Multi-page TIFFs require iterating `TIFDirectory` frames with
   PDFBox's `TIFFImageLoader`. Should be added to `renderDocument()`.

3. **Fax button in document list view:** The current plan adds fax only to the
   `showDocument.jsp` detail view. The list view (`oscarMDS/Index.jsp`) could get
   a per-row fax action in a future PR.

4. **Audit logging:** A `LogAction` call should be added when a document is sent
   to fax (transaction type DOCUMENT_FAX), consistent with other document actions.

5. **Address book search in dialog:** Phase 2 could inline the recipient/specialist
   autocomplete from `CoverPage.jsp` directly into the modal, reducing the number
   of popup windows.

6. **`libphonenumber-js`:** If real-time fax number formatting/validation is
   desired in the modal, add `libphonenumber-js 1.x` (MIT). It is ~47 KB minified
   and GPL-compatible.

---

## 9. File Summary

| File | Action | Phase |
|------|--------|-------|
| `managers/FaxManagerImpl.java` | Modify: implement `renderDocument()`, add `renderDocumentPages()` | 1 + 2 |
| `managers/FaxManager.java` | Modify: add `renderDocumentPages()` to interface | 2 |
| `fax/action/Fax2Action.java` | Modify: DOCUMENT case in prepareFax + cancel | 1 |
| `documentManager/showDocument.jsp` | Modify: add Fax button | 1 |
| `documentManager/showDocument.jsp` | Modify: switch to modal trigger | 2 |
| `documentManager/actions/DocumentFaxPrep2Action.java` | **Create** | 2 |
| `documentManager/documentFaxWidget.js` | **Create** | 2 |
| `struts-provider.xml` | Modify: DOCUMENT cancel result | 1 |
| `struts-document.xml` | Modify: DocumentFaxPrep action | 2 |
| `oscarResources_en.properties` | Modify: add `showDocument.btnFax` key | 1 |
| `FaxManagerDocumentUnitTest.java` | **Create** | 1 + 2 |
| `DocumentFaxPrepActionUnitTest.java` | **Create** | 2 |
| `FaxManagerDocumentIntegrationTest.java` | **Create** | 1 + 2 |

---

*Generated by claude[bot] for CARLOS EMR issue #416.*  
*Triggered by @yingbull on 2026-04-02.*
