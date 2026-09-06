<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
    FaxAnnotateViewer.jsp
    PDF.js-based annotation viewer for fax preparation.

    Loaded via FaxDocument2Action (GET /documentManager/FaxDocument?docId=N).
    After the provider optionally annotates the document, "Save & Continue to Fax"
    POSTs the annotated PDF to SaveAnnotatedDocument, then navigates back to
    FaxDocument?faxReady=true which forwards to CoverPage.jsp.

    Annotation tools (all built into PDF.js 4.x):
      - Text     : AnnotationEditorType.FREETEXT  (3)
      - Draw     : AnnotationEditorType.INK        (9)
      - Highlight: AnnotationEditorType.HIGHLIGHT  (1)
      - Sign     : AnnotationEditorType.STAMP      (13) + custom signature pad

    The annotation log records only the set of tool types activated during the
    session — no annotation content is logged to avoid PHI exposure.

    @param docId        (request attribute, int) Document number
    @param demographicNo (request attribute, int) Linked demographic, or 0
    @since 2026-06
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"
         import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String ctx = request.getContextPath();
    int    docId = (Integer) request.getAttribute("docId");

    // web.xml filtering is not enabled for WEB-INF/web.xml, so getInitParameter would return
    // the raw Maven placeholder "${pdfjs.version}". Hardcode the version here to match pom.xml.
    String pdfjsVersion = "6.0.227";

    String pdfjsBase = ctx + "/webjars/pdfjs-dist/" + pdfjsVersion;
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title><fmt:message key="faxAnnotateViewer.title"/></title>

<link rel="stylesheet" href="<%=SafeEncode.forHtmlAttribute(ctx)%>/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
<link rel="stylesheet" href="<%=SafeEncode.forHtmlAttribute(ctx)%>/css/fontawesome-all.min.css"/>
<link rel="stylesheet" href="<%=SafeEncode.forHtmlAttribute(pdfjsBase)%>/web/pdf_viewer.css"/>

<style>
    html, body { margin: 0; padding: 0; height: 100%; overflow: hidden; background: #404040; }

    /* ── Toolbar ─────────────────────────────────────────────────── */
    #faxToolbar {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 10px;
        background: #1e2125;
        border-bottom: 1px solid #444;
        height: 48px;
        box-sizing: border-box;
        flex-shrink: 0;
        user-select: none;
    }
    .tool-btn {
        width: 36px; height: 36px; border-radius: 6px;
        border: 1px solid transparent;
        background: transparent; color: #ccc;
        font-size: 15px; cursor: pointer;
        display: flex; align-items: center; justify-content: center;
        transition: background .15s, color .15s;
    }
    .tool-btn:hover  { background: #2e3338; color: #fff; }
    .tool-btn.active { background: #0d6efd; color: #fff; border-color: #0b5ed7; }
    .tool-separator  { width: 1px; height: 28px; background: #444; margin: 0 4px; }

    /* Page navigation */
    #pageNav { display: flex; align-items: center; gap: 4px; color: #ccc; font-size: 13px; }
    #pageNav input[type=number] {
        width: 44px; text-align: center;
        background: #2e3338; border: 1px solid #555; color: #eee;
        border-radius: 4px; padding: 2px 4px; font-size: 13px;
    }
    #pageNav input[type=number]:focus { outline: none; border-color: #0d6efd; }

    .toolbar-spacer { flex: 1; }

    /* Save & Fax button */
    #btnSaveFax { height: 34px; white-space: nowrap; }

    /* ── PDF viewer area ─────────────────────────────────────────── */
    #viewerContainer {
        position: absolute;
        top: 48px; bottom: 0; left: 0; right: 0;
        overflow: auto;
        background: #525659;
    }
    .pdfViewer { padding: 12px 0; }
    .pdfViewer .page { margin: 0 auto 8px; box-shadow: 0 2px 8px rgba(0,0,0,.5); }

    /* ── Annotation edit toolbar — appear ABOVE the annotation ─────
       pdf_viewer.css 6.x default places the toolbar below (inset-block-start:
       calc(100% + 6px)).  We restore the PDF.js 4.x convention of showing it
       above the annotation so the toolbar doesn't hide surrounding content.
       The override uses both the logical property and the physical property
       (with !important on the physical one) to beat inline style.top values
       that HighlightEditor sets via JavaScript. ───────────────────────── */
    :root { --editor-toolbar-vert-offset: -4px; }

    .annotationEditorLayer .editToolbar {
        inset-block-start: calc(0% - var(--editor-toolbar-height, 28px) - 4px) !important;
    }

    /* Disable the JS-injected style.top on highlight toolbars by re-applying
       the above override as a physical top rule at higher specificity. */
    .annotationEditorLayer .highlightEditor > .editToolbar,
    .annotationEditorLayer .freeTextEditor > .editToolbar,
    .annotationEditorLayer .inkEditor > .editToolbar,
    .annotationEditorLayer .stampEditor > .editToolbar {
        top: calc(0% - var(--editor-toolbar-height, 28px) - 4px) !important;
        inset-block-start: calc(0% - var(--editor-toolbar-height, 28px) - 4px) !important;
    }

    /* ── Stamp placement mode — crosshair on the whole viewer area ─ */
    #viewerContainer.stamp-placement-mode,
    #viewerContainer.stamp-placement-mode * {
        cursor: crosshair !important;
    }

    /* ── Signature modal canvas ──────────────────────────────────── */
    #signatureCanvas {
        border: 1px solid #dee2e6;
        border-radius: 4px;
        cursor: crosshair;
        width: 100%;
        touch-action: none;
    }
    #signaturePreview {
        border: 1px solid #dee2e6;
        border-radius: 4px;
        max-width: 100%;
        display: none;
    }

    /* ── Status overlay ─────────────────────────────────────────── */
    #statusOverlay {
        display: none;
        position: fixed; inset: 0;
        background: rgba(0,0,0,.55);
        z-index: 2000;
        justify-content: center;
        align-items: center;
    }
    #statusOverlay.show { display: flex; }
    #statusOverlay .spinner-border { width: 3rem; height: 3rem; }
</style>
</head>
<body>

<%-- CSRF token for fetch() POST requests --%>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>

<%-- ── Toolbar ───────────────────────────────────────────────────────── --%>
<div id="faxToolbar">

    <%-- Annotation tool buttons --%>
    <button class="tool-btn" id="btnText"      title="<fmt:message key='faxAnnotateViewer.btn.addText'/>"    onclick="setMode('freetext')">
        <i class="fas fa-font"></i>
    </button>
    <button class="tool-btn" id="btnDraw"      title="<fmt:message key='faxAnnotateViewer.btn.draw'/>"       onclick="setMode('ink')">
        <i class="fas fa-pen"></i>
    </button>
    <button class="tool-btn" id="btnHighlight" title="<fmt:message key='faxAnnotateViewer.btn.highlight'/>"  onclick="setMode('highlight')">
        <i class="fas fa-highlighter"></i>
    </button>
    <button class="tool-btn" id="btnSign"      title="<fmt:message key='faxAnnotateViewer.btn.signature'/>"  onclick="openSignatureOrInsert()">
        <i class="fas fa-signature"></i>
    </button>
    <%-- title is set dynamically to today's formatted date by the module script --%>
    <button class="tool-btn" id="btnDate" title="" onclick="insertTodayDate()">
        <i class="fa-solid fa-calendar-day"></i>
    </button>

    <div class="tool-separator"></div>

    <%-- Cursor / selection mode --%>
    <button class="tool-btn active" id="btnSelect" title="<fmt:message key='faxAnnotateViewer.btn.select'/>"  onclick="setMode('none')">
        <i class="fas fa-mouse-pointer"></i>
    </button>

    <div class="tool-separator"></div>

    <%-- Page navigation --%>
    <div id="pageNav">
        <button class="tool-btn" id="btnPrev" title="<fmt:message key='faxAnnotateViewer.btn.prevPage'/>" onclick="changePage(-1)">
            <i class="fas fa-chevron-left"></i>
        </button>
        <input type="number" id="pageInput" value="1" min="1" onchange="goToPage(parseInt(this.value))"/>
        <span id="pageCount">/ 1</span>
        <button class="tool-btn" id="btnNext" title="<fmt:message key='faxAnnotateViewer.btn.nextPage'/>" onclick="changePage(1)">
            <i class="fas fa-chevron-right"></i>
        </button>
    </div>

    <div class="tool-separator"></div>

    <%-- Zoom --%>
    <button class="tool-btn" title="<fmt:message key='faxAnnotateViewer.btn.zoomOut'/>" onclick="adjustZoom(-0.25)">
        <i class="fas fa-search-minus"></i>
    </button>
    <button class="tool-btn" title="<fmt:message key='faxAnnotateViewer.btn.zoomIn'/>"  onclick="adjustZoom(0.25)">
        <i class="fas fa-search-plus"></i>
    </button>

    <div class="toolbar-spacer"></div>

    <button class="btn btn-primary btn-sm" id="btnSaveFax" onclick="saveAndFax()">
        <i class="fas fa-fax me-1"></i><fmt:message key="faxAnnotateViewer.btn.saveAndFax"/>
    </button>
</div>

<%-- ── PDF viewer area ─────────────────────────────────────────────────── --%>
<div id="viewerContainer" tabindex="0">
    <div id="viewer" class="pdfViewer"></div>
</div>

<%-- ── Signature modal ─────────────────────────────────────────────────── --%>
<div class="modal fade" id="signatureModal" tabindex="-1" aria-labelledby="sigModalLabel" aria-hidden="true">
  <div class="modal-dialog modal-lg">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title" id="sigModalLabel">
            <i class="fas fa-signature me-2"></i><fmt:message key="faxAnnotateViewer.modal.sigTitle"/>
        </h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="<fmt:message key='faxAnnotateViewer.modal.close'/>"></button>
      </div>
      <div class="modal-body">
        <p class="small text-muted mb-1"><fmt:message key="faxAnnotateViewer.modal.sigInstruction"/></p>
        <canvas id="signatureCanvas" width="700" height="160"></canvas>
        <div class="mt-2 d-flex gap-2">
            <button class="btn btn-sm btn-outline-secondary" onclick="clearSignaturePad()">
                <i class="fas fa-eraser me-1"></i><fmt:message key="faxAnnotateViewer.modal.clear"/>
            </button>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal"><fmt:message key="faxAnnotateViewer.modal.cancel"/></button>
        <button type="button" class="btn btn-primary" id="btnApplySignature" onclick="applySignature()">
            <i class="fas fa-stamp me-1"></i><fmt:message key="faxAnnotateViewer.modal.placeSignature"/>
        </button>
      </div>
    </div>
  </div>
</div>

<%-- ── Save progress overlay ───────────────────────────────────────────── --%>
<div id="statusOverlay">
    <div class="text-center text-white">
        <div class="spinner-border text-light mb-3" role="status"></div>
        <div id="statusMsg" class="fw-semibold"><fmt:message key="faxAnnotateViewer.status.saving"/></div>
    </div>
</div>

<%-- Bootstrap bundle --%>
<script src="<%=SafeEncode.forHtmlAttribute(ctx)%>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

<%-- ── i18n strings for JavaScript (resolved server-side by JSP) ──────── --%>
<script>
<c:set var="msg_statusSaving"><fmt:message key='faxAnnotateViewer.status.saving'/></c:set>
<c:set var="msg_statusOpeningFax"><fmt:message key='faxAnnotateViewer.status.openingFax'/></c:set>
<c:set var="msg_alertNoSignature"><fmt:message key='faxAnnotateViewer.alert.noSignature'/></c:set>
<c:set var="msg_alertSaveFailed"><fmt:message key='faxAnnotateViewer.alert.saveFailed'/></c:set>
<c:set var="msg_alertSaveFailedDetail"><fmt:message key='faxAnnotateViewer.alert.saveFailedDetail'/></c:set>
<c:set var="msg_i18nLocale"><fmt:message key='global.i18nLanguagecode'/></c:set>
window.FAX_I18N = Object.freeze({
    statusSaving:          "${carlos:forJavaScript(msg_statusSaving)}",
    statusOpeningFax:      "${carlos:forJavaScript(msg_statusOpeningFax)}",
    alertNoSignature:      "${carlos:forJavaScript(msg_alertNoSignature)}",
    alertSaveFailed:       "${carlos:forJavaScript(msg_alertSaveFailed)}",
    alertSaveFailedDetail: "${carlos:forJavaScript(msg_alertSaveFailedDetail)}",
    i18nLocale:            "${carlos:forJavaScript(msg_i18nLocale)}"
});
</script>

<%-- ── PDF.js + annotation logic (ES module) ──────────────────────────── --%>
<script type="module">
'use strict';

const CTX       = '<%=SafeEncode.forJavaScript(ctx)%>';
const PDFJS     = '<%=SafeEncode.forJavaScript(pdfjsBase)%>';
const DOC_ID    = <%=docId%>;

let CSRF_TOKEN = document.querySelector('input[name="CSRF-TOKEN"]')?.value ?? '';
// Start the CSRF token fetch as early as possible; awaited before each POST so the
// token is guaranteed to be populated even if the user acts before DOMContentLoaded
// handlers have had time to complete their async fetch.
const csrfBootstrap = fetchCsrfToken(CTX);

// ── Dynamic imports (paths computed at runtime to avoid JSP/template-literal conflicts) ──
const pdfjsLib  = await import(PDFJS + '/build/pdf.mjs');
const viewerPkg = await import(PDFJS + '/web/pdf_viewer.mjs');

const { PDFViewer, EventBus, PDFLinkService } = viewerPkg;
const { AnnotationEditorType, GlobalWorkerOptions, getDocument, AnnotationEditorUIManager } = pdfjsLib;

GlobalWorkerOptions.workerSrc = PDFJS + '/build/pdf.worker.mjs';

// Capture AnnotationEditorLayer instances as they register with the UIManager.
// PDF.js stores layers in a #-private Map; patching the public addLayer method
// is the only reliable way to obtain layer references without modifying pdf.mjs.
// Layers are keyed by 0-based pageIndex, matching pdfViewer.currentPageNumber-1.
const _annotationLayers = new Map();
const _origAddLayer = AnnotationEditorUIManager.prototype.addLayer;
AnnotationEditorUIManager.prototype.addLayer = function(layer) {
    _annotationLayers.set(layer.pageIndex, layer);
    return _origAddLayer.call(this, layer);
};

// ── Viewer setup ──────────────────────────────────────────────────────────
const eventBus    = new EventBus();
const linkService = new PDFLinkService({ eventBus });

const pdfViewer = new PDFViewer({
    container: document.getElementById('viewerContainer'),
    viewer:    document.getElementById('viewer'),
    eventBus,
    linkService,
    // Start with no active tool; the editor infrastructure is still fully initialised.
    annotationEditorMode: AnnotationEditorType.NONE,
    // 6.x: highlight colours must be supplied at construction time; the old
    // switchannotationeditorparams event-dispatch approach no longer works.
    annotationEditorHighlightColors: 'yellow=#FFFF98,green=#53FFBC,blue=#80EBFF,pink=#FFCBE6,red=#FF4F5F',
});

linkService.setViewer(pdfViewer);

// ── Load the PDF ──────────────────────────────────────────────────────────
const pdfDocument = await getDocument({
    url:     CTX + '/documentManager/ServeDocument?docId=' + DOC_ID,
    // 6.x: WASM decoders (JBIG2, OpenJPEG, QCMS) live in wasm/; without this
    // the worker tries a bare-specifier JS fallback that browsers can't resolve.
    wasmUrl: PDFJS + '/wasm/',
}).promise;
pdfViewer.setDocument(pdfDocument);
linkService.setDocument(pdfDocument);

// ── Page info wiring ──────────────────────────────────────────────────────
eventBus.on('pagesinit', () => {
    document.getElementById('pageCount').textContent = '/ ' + pdfViewer.pagesCount;
    document.getElementById('pageInput').max = pdfViewer.pagesCount;
});

eventBus.on('pagechanging', ({ pageNumber }) => {
    document.getElementById('pageInput').value = pageNumber;
});

// ── Annotation type tracking ──────────────────────────────────────────────
const usedAnnotationTypes = new Set();
let currentMode = AnnotationEditorType.NONE;

eventBus.on('annotationeditorstateschanged', ({ details }) => {
    if (details && details.hasSomethingToUndo) {
        switch (currentMode) {
            case AnnotationEditorType.FREETEXT:   usedAnnotationTypes.add('text');        break;
            case AnnotationEditorType.INK:        usedAnnotationTypes.add('drawn');       break;
            case AnnotationEditorType.HIGHLIGHT:  usedAnnotationTypes.add('highlighted'); break;
            case AnnotationEditorType.STAMP:      usedAnnotationTypes.add('signed');      break;
        }
    }
});

// ── Annotation mode switching (exposed to onclick handlers) ───────────────
const modeMap = {
    none:      AnnotationEditorType.NONE,
    freetext:  AnnotationEditorType.FREETEXT,
    ink:       AnnotationEditorType.INK,
    highlight: AnnotationEditorType.HIGHLIGHT,
    stamp:     AnnotationEditorType.STAMP,
};
const modeButtons = {
    none:      document.getElementById('btnSelect'),
    freetext:  document.getElementById('btnText'),
    ink:       document.getElementById('btnDraw'),
    highlight: document.getElementById('btnHighlight'),
    stamp:     document.getElementById('btnSign'),
};

window.setMode = function(modeName) {
    currentMode = modeMap[modeName] ?? AnnotationEditorType.NONE;
    pdfViewer.annotationEditorMode = { mode: currentMode };
    // Show crosshair when awaiting a stamp click so the user knows to click on the PDF.
    document.getElementById('viewerContainer')
        .classList.toggle('stamp-placement-mode', modeName === 'stamp');
    Object.entries(modeButtons).forEach(([name, btn]) => {
        if (btn) btn.classList.toggle('active', name === modeName);
    });
};

// ── Page navigation ───────────────────────────────────────────────────────
window.changePage = function(delta) {
    const next = pdfViewer.currentPageNumber + delta;
    if (next >= 1 && next <= pdfViewer.pagesCount) {
        pdfViewer.currentPageNumber = next;
    }
};
window.goToPage = function(n) {
    if (n >= 1 && n <= pdfViewer.pagesCount) {
        pdfViewer.currentPageNumber = n;
    }
};
window.adjustZoom = function(delta) {
    pdfViewer.currentScale = Math.max(0.25, Math.min(4, pdfViewer.currentScale + delta));
};

// ── Signature modal ───────────────────────────────────────────────────────
let signatureModal = null;

// Places a signature stamp at the centre of the current page.
//
// pasteEditor() was replaced by createAndAddNewEditor() because:
//   1. The synthetic ClipboardEvent paste approach was blocked by Chrome's
//      clipboard security policy (clipboardData.items always empty).
//   2. Passing width/height in pasteEditor() params has no effect — the
//      AnnotationEditor base constructor (pdf.mjs line 4963) unconditionally
//      sets this.width = this.height = null, wiping any constructor param.
//
// createAndAddNewEditor() returns the StampEditor synchronously, before the
// async imageManager.getFromFile() callback fires.  We set editor.width/height
// in that window so StampEditor.#createCanvas() sees truthy values and uses
// them instead of falling through to the 75% MAX_RATIO default clamp.
async function insertSignatureViaFastPath(file) {
    // Switch to STAMP mode so PDF.js creates AnnotationEditorLayer instances
    // for each page (they're built lazily when an edit mode is first activated).
    pdfViewer.annotationEditorMode = { mode: AnnotationEditorType.STAMP };

    // Poll for the current-page layer — layer creation is async and runs through
    // PDF.js's rendering pipeline; allow up to 500 ms (50 × 10 ms ticks).
    const pageIndex = pdfViewer.currentPageNumber - 1;
    let layer = null;
    for (let i = 0; i < 50; i++) {
        layer = _annotationLayers.get(pageIndex);
        if (layer) break;
        await new Promise(r => setTimeout(r, 10));
    }

    if (!layer) {
        return;
    }

    const editor = layer.createAndAddNewEditor(
        { offsetX: 0, offsetY: 0 },
        true,               // isCentered — positions stamp at page centre
        { bitmapFile: file }
    );
    if (!editor) {
        return;
    }

    // Set size BEFORE the async getFromFile() callback fires #createCanvas().
    // Values are page-dimension fractions: 0.20 × 0.05 ≈ 150 × 50 pt on letter/A4.
    editor.width  = 0.20;
    editor.height = 0.05;

    usedAnnotationTypes.add('signed');
}

// Click handler for the signature toolbar button.
// If the provider has a saved signature stamp it is inserted directly at the
// centre of the visible page — no modal shown.  When no stamp exists the draw
// modal opens so the provider can create one.
window.openSignatureOrInsert = async function() {
    try {
        const checkUrl = CTX + '/provider/providerSignatureStamp?method=check';
        const res  = await fetch(checkUrl);
        const json = await res.json();
        if (res.ok && json.exists && json.imageUrl) {
            const imgRes = await fetch(json.imageUrl);
            const blob   = await imgRes.blob();
            await insertSignatureViaFastPath(
                new File([blob], 'signature.png', { type: 'image/png' })
            );
            const _sb = document.getElementById('btnSign');
            _sb.classList.add('active');
            setTimeout(() => _sb.classList.remove('active'), 800);
            return;
        }
    } catch (e) {
        // Fall through to modal
    }
    if (!signatureModal) {
        signatureModal = new bootstrap.Modal(document.getElementById('signatureModal'));
    }
    clearSignaturePad();
    signatureModal.show();
};

window.clearSignaturePad = function() {
    const canvas = document.getElementById('signatureCanvas');
    canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
};

// ── Today's date insertion ────────────────────────────────────────────────
// Format today's date using the locale code from oscarResources (e.g. "en-GB",
// "fr-fr").  If the tag is not recognised by Intl, fall back to the browser's
// default locale so there's always a human-readable result.
const _dateLocale  = window.FAX_I18N.i18nLocale;
const _dateOptions = { year: 'numeric', month: 'short', day: 'numeric' };
const _todayFormatted = (() => {
    const today = new Date();
    try {
        new Intl.DateTimeFormat(_dateLocale, _dateOptions);  // throws on invalid tag
        return today.toLocaleDateString(_dateLocale, _dateOptions);
    } catch (_) {
        return today.toLocaleDateString(undefined, _dateOptions);
    }
})();

// Set the button's tooltip to today's formatted date once the DOM is ready.
document.getElementById('btnDate').title = _todayFormatted;

// Inserts today's date as a FreeText stamp centred on the current page.
// Uses AnnotationEditorLayer.createAndAddNewEditor (public API) to obtain a
// direct reference to the new editor so content can be pre-filled before
// commit(), bypassing the clipboard entirely.
window.insertTodayDate = async function() {
    pdfViewer.annotationEditorMode = { mode: AnnotationEditorType.FREETEXT };

    const pageIndex = pdfViewer.currentPageNumber - 1;
    let layer = null;
    for (let i = 0; i < 50; i++) {
        layer = _annotationLayers.get(pageIndex);
        if (layer) break;
        await new Promise(r => setTimeout(r, 10));
    }
    if (!layer) {
        console.error('[date] no annotation editor layer for page', pageIndex);
        return;
    }

    // Create a centred FreeText editor and immediately capture the reference.
    const editor = layer.createAndAddNewEditor({ offsetX: 0, offsetY: 0 }, true, {});
    if (!editor) return;

    // Set text and commit SYNCHRONOUSLY — no yield between creation and commit.
    // Yielding (even a setTimeout(0)) allows updateMode(FREETEXT)'s pending
    // microtask chain to resume: it calls unselectAll() → commitOrRemove() on
    // the (still empty) active editor → remove() → this.parent = null, causing
    // "can't access property setEditingState, this.parent is null" in disableEditMode.
    // editorDiv is created synchronously inside render() → no tick is needed.
    editor.editorDiv.innerText = _todayFormatted;
    editor.commit();

    usedAnnotationTypes.add('text');

    // Brief visual feedback on the date button.
    const btn = document.getElementById('btnDate');
    btn.classList.add('active');
    setTimeout(() => btn.classList.remove('active'), 800);

    // Stay in freetext mode so the annotation editor layer remains enabled
    // and the new date box can be dragged to the correct position.
    // setMode('none') would call #disableAll() → layer.disable() →
    // pointer-events:none on the whole layer, making the text box unreachable.
    // The viewer's internal mode is already FREETEXT (set at the top of this
    // function), so this call only updates the toolbar button highlight.
    setMode('freetext');
};

window.applySignature = async function() {
    const canvas = document.getElementById('signatureCanvas');
    const px     = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
    if (!Array.prototype.some.call(px, (v, i) => i % 4 === 3 && v > 10)) {
        alert(window.FAX_I18N.alertNoSignature);
        return;
    }

    const dataUrl = canvas.toDataURL('image/png');

    // Persist as the provider's signature stamp (Administration > Provider
    // Preferences > Signature) so it's available everywhere, not just here.
    // Best-effort: signature insertion into the PDF continues even on failure.
    try {
        await csrfBootstrap;
        CSRF_TOKEN = document.querySelector('input[name="CSRF-TOKEN"]')?.value ?? '';
        const body = new URLSearchParams({ signatureData: dataUrl, 'CSRF-TOKEN': CSRF_TOKEN });
        const saveRes = await fetch(CTX + '/provider/providerSignatureStamp?method=saveDrawn', {
            method:  'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body:    body.toString(),
        });
        const result = await saveRes.json();
        if (!result.success) {
            console.warn('Could not persist signature:', result.error);
        }
    } catch (e) {
        console.warn('Could not persist signature:', e);
    }

    signatureModal.hide();

    const res  = await fetch(dataUrl);
    const blob = await res.blob();
    await insertSignatureViaFastPath(
        new File([blob], 'signature.png', { type: 'image/png' })
    );
    const _sb2 = document.getElementById('btnSign');
    _sb2.classList.add('active');
    setTimeout(() => _sb2.classList.remove('active'), 800);
};

// ── Signature pad drawing ─────────────────────────────────────────────────
{
    const canvas = document.getElementById('signatureCanvas');
    const ctx2   = canvas.getContext('2d');
    ctx2.strokeStyle = '#000';
    ctx2.lineWidth   = 2;
    ctx2.lineCap     = 'round';
    ctx2.lineJoin    = 'round';
    let drawing = false;

    function getPos(e) {
        const r = canvas.getBoundingClientRect();
        const scaleX = canvas.width  / r.width;
        const scaleY = canvas.height / r.height;
        const src = e.touches ? e.touches[0] : e;
        return [(src.clientX - r.left) * scaleX, (src.clientY - r.top) * scaleY];
    }

    canvas.addEventListener('pointerdown',  e => { drawing = true; ctx2.beginPath(); const [x, y] = getPos(e); ctx2.moveTo(x, y); });
    canvas.addEventListener('pointermove',  e => { if (!drawing) return; const [x, y] = getPos(e); ctx2.lineTo(x, y); ctx2.stroke(); });
    canvas.addEventListener('pointerup',    () => { drawing = false; });
    canvas.addEventListener('pointerleave', () => { drawing = false; });
}

// ── Save & Fax ────────────────────────────────────────────────────────────
window.saveAndFax = async function() {
    // If the provider made no annotations, skip the save round-trip entirely
    // and navigate straight to fax composition with the original document.
    if (usedAnnotationTypes.size === 0) {
        window.location.href = CTX + '/documentManager/FaxDocument?docId=' + DOC_ID + '&faxReady=true';
        return;
    }

    const btn     = document.getElementById('btnSaveFax');
    const overlay = document.getElementById('statusOverlay');
    const msg     = document.getElementById('statusMsg');

    btn.disabled = true;
    msg.textContent = window.FAX_I18N.statusSaving;
    overlay.classList.add('show');

    try {
        // Serialise the annotated PDF (includes all PDF.js annotation editor layers)
        const pdfBytes = await pdfDocument.saveDocument();

        const blob     = new Blob([new Uint8Array(pdfBytes)], { type: 'application/pdf' });
        const formData = new FormData();
        formData.append('docId',           DOC_ID);
        formData.append('pdfFile',         blob, 'annotated.pdf');
        formData.append('annotationTypes', [...usedAnnotationTypes].join(','));

        await csrfBootstrap;
        CSRF_TOKEN = document.querySelector('input[name="CSRF-TOKEN"]')?.value ?? '';
        formData.append('CSRF-TOKEN', CSRF_TOKEN);

        // No Content-Type header — browser sets multipart/form-data with boundary automatically
        const res = await fetch(CTX + '/documentManager/SaveAnnotatedDocument', {
            method: 'POST',
            body:   formData,
        });

        if (!res.ok) {
            throw new Error('Server returned ' + res.status);
        }

        const result = await res.json();
        if (!result.success) {
            throw new Error(result.error ?? 'Save failed');
        }

        msg.textContent = window.FAX_I18N.statusOpeningFax;
        window.location.href = CTX + '/documentManager/FaxDocument?docId=' + DOC_ID + '&faxReady=true';

    } catch (e) {
        overlay.classList.remove('show');
        btn.disabled = false;
        console.error('Save & Fax failed:', e);
        alert(window.FAX_I18N.alertSaveFailed + '\n\n' + window.FAX_I18N.alertSaveFailedDetail + ' ' + e.message);
    }
};
</script>

</body>
</html>
