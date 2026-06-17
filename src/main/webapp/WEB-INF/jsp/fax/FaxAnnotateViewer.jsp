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
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
    String ctx = request.getContextPath();
    int    docId         = (Integer) request.getAttribute("docId");
    int    demographicNo = (Integer) request.getAttribute("demographicNo");

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
<title>Annotate Document for Fax</title>

<link rel="stylesheet" href="<%=ctx%>/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
<link rel="stylesheet" href="<%=ctx%>/css/fontawesome-all.min.css"/>
<link rel="stylesheet" href="<%=pdfjsBase%>/web/pdf_viewer.css"/>

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
    <button class="tool-btn" id="btnText"      title="Add text (T)"         onclick="setMode('freetext')">
        <i class="fas fa-font"></i>
    </button>
    <button class="tool-btn" id="btnDraw"      title="Draw freehand lines"  onclick="setMode('ink')">
        <i class="fas fa-pen"></i>
    </button>
    <button class="tool-btn" id="btnHighlight" title="Highlight"             onclick="setMode('highlight')">
        <i class="fas fa-highlighter"></i>
    </button>
    <button class="tool-btn" id="btnSign"      title="Add signature stamp"  onclick="openSignatureModal()">
        <i class="fas fa-signature"></i>
    </button>

    <div class="tool-separator"></div>

    <%-- Cursor / selection mode --%>
    <button class="tool-btn active" id="btnSelect" title="Select / cursor"  onclick="setMode('none')">
        <i class="fas fa-mouse-pointer"></i>
    </button>

    <div class="tool-separator"></div>

    <%-- Page navigation --%>
    <div id="pageNav">
        <button class="tool-btn" id="btnPrev" title="Previous page" onclick="changePage(-1)">
            <i class="fas fa-chevron-left"></i>
        </button>
        <input type="number" id="pageInput" value="1" min="1" onchange="goToPage(parseInt(this.value))"/>
        <span id="pageCount">/ 1</span>
        <button class="tool-btn" id="btnNext" title="Next page" onclick="changePage(1)">
            <i class="fas fa-chevron-right"></i>
        </button>
    </div>

    <div class="tool-separator"></div>

    <%-- Zoom --%>
    <button class="tool-btn" title="Zoom out" onclick="adjustZoom(-0.25)">
        <i class="fas fa-search-minus"></i>
    </button>
    <button class="tool-btn" title="Zoom in"  onclick="adjustZoom(0.25)">
        <i class="fas fa-search-plus"></i>
    </button>

    <div class="toolbar-spacer"></div>

    <button class="btn btn-primary btn-sm" id="btnSaveFax" onclick="saveAndFax()">
        <i class="fas fa-fax me-1"></i>Save &amp; Continue to Fax
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
            <i class="fas fa-signature me-2"></i>Provider Signature
        </h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
      </div>
      <div class="modal-body">
        <div id="sigExistingSection" style="display:none" class="mb-3">
            <p class="small text-muted mb-1">Your saved signature (from Provider Preferences):</p>
            <img id="signaturePreview" alt="Saved signature" class="mb-2"/>
            <div class="d-flex gap-2">
                <button class="btn btn-sm btn-outline-primary" onclick="useExistingSignature()">
                    Use saved signature
                </button>
                <button class="btn btn-sm btn-outline-secondary" onclick="showSignaturePad()">
                    Draw new signature
                </button>
            </div>
        </div>
        <div id="sigDrawSection">
            <p class="small text-muted mb-1">
                Draw your signature below. Saving here updates your signature in
                Provider Preferences and will be used wherever your signature stamp appears.
            </p>
            <canvas id="signatureCanvas" width="700" height="160"></canvas>
            <div class="mt-2 d-flex gap-2">
                <button class="btn btn-sm btn-outline-secondary" onclick="clearSignaturePad()">
                    <i class="fas fa-eraser me-1"></i>Clear
                </button>
            </div>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
        <button type="button" class="btn btn-primary" id="btnApplySignature" onclick="placeSignature()">
            <i class="fas fa-stamp me-1"></i>Place Signature on Document
        </button>
      </div>
    </div>
  </div>
</div>

<%-- ── Save progress overlay ───────────────────────────────────────────── --%>
<div id="statusOverlay">
    <div class="text-center text-white">
        <div class="spinner-border text-light mb-3" role="status"></div>
        <div id="statusMsg" class="fw-semibold">Saving annotated document…</div>
    </div>
</div>

<%-- Bootstrap bundle --%>
<script src="<%=ctx%>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

<%-- ── PDF.js + annotation logic (ES module) ──────────────────────────── --%>
<script type="module">
'use strict';

const CTX       = '<%=ctx%>';
const PDFJS     = '<%=pdfjsBase%>';
const DOC_ID    = <%=docId%>;

let CSRF_TOKEN = document.querySelector('input[name="CSRF-TOKEN"]')?.value ?? '';

// ── Dynamic imports (paths computed at runtime to avoid JSP/template-literal conflicts) ──
const pdfjsLib  = await import(PDFJS + '/build/pdf.mjs');
const viewerPkg = await import(PDFJS + '/web/pdf_viewer.mjs');

const { PDFViewer, EventBus, PDFLinkService } = viewerPkg;
const { AnnotationEditorType, GlobalWorkerOptions, getDocument } = pdfjsLib;

GlobalWorkerOptions.workerSrc = PDFJS + '/build/pdf.worker.mjs';

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
    // Direct setter is more reliable than the event bus in PDF.js 4.x
    pdfViewer.annotationEditorMode = { mode: currentMode };
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
let existingSignatureDataUrl = null;
let pendingSignatureFile = null;

// Cache for the signature file picker intercept
let interceptNextStampFilePicker = false;

// Intercept PDF.js's stamp file input click in capture phase so we can feed
// our pre-created signature instead of opening the OS file dialog.
document.addEventListener('click', (e) => {
    if (!interceptNextStampFilePicker) return;
    const el = e.target;
    if (el.tagName === 'INPUT' && el.type === 'file' && el.accept && el.accept.includes('image')) {
        e.stopImmediatePropagation();
        e.preventDefault();
        interceptNextStampFilePicker = false;
        if (pendingSignatureFile) {
            feedFileToInput(el, pendingSignatureFile);
            pendingSignatureFile = null;
        }
    }
}, true);

function feedFileToInput(input, file) {
    const dt = new DataTransfer();
    dt.items.add(file);
    Object.defineProperty(input, 'files', {
        value: dt.files, writable: true, configurable: true
    });
    input.dispatchEvent(new Event('change', { bubbles: true }));
}

window.openSignatureModal = async function() {
    if (!signatureModal) {
        signatureModal = new bootstrap.Modal(document.getElementById('signatureModal'));
    }

    // Check for the provider's existing signature stamp (Administration > Provider
    // Preferences > Signature). This is the single source of truth for the provider's
    // signature image — the fax viewer never maintains its own copy.
    try {
        const checkRes  = await fetch(CTX + '/provider/providerSignatureStamp?method=check');
        const checkJson = await checkRes.json();
        if (checkRes.ok && checkJson.exists && checkJson.imageUrl) {
            const imgRes = await fetch(checkJson.imageUrl);
            const blob   = await imgRes.blob();
            existingSignatureDataUrl = URL.createObjectURL(blob);
            const preview = document.getElementById('signaturePreview');
            preview.src = existingSignatureDataUrl;
            preview.style.display = 'block';
            document.getElementById('sigExistingSection').style.display = 'block';
            document.getElementById('sigDrawSection').style.display = 'none';
        } else {
            existingSignatureDataUrl = null;
            document.getElementById('sigExistingSection').style.display = 'none';
            showSignaturePad();
        }
    } catch {
        existingSignatureDataUrl = null;
        document.getElementById('sigExistingSection').style.display = 'none';
        showSignaturePad();
    }

    // Reset the canvas
    clearSignaturePad();
    signatureModal.show();
};

window.showSignaturePad = function() {
    document.getElementById('sigExistingSection').style.display = 'none';
    document.getElementById('sigDrawSection').style.display = 'block';
    clearSignaturePad();
};

window.clearSignaturePad = function() {
    const canvas = document.getElementById('signatureCanvas');
    const ctx2   = canvas.getContext('2d');
    ctx2.clearRect(0, 0, canvas.width, canvas.height);
};

window.useExistingSignature = async function() {
    if (!existingSignatureDataUrl) return;
    signatureModal.hide();
    await insertSignatureAsStamp(existingSignatureDataUrl, false);
};

// Dispatches the footer "Place Signature on Document" button to the right handler
// depending on which section of the modal is currently visible.
window.placeSignature = async function() {
    if (document.getElementById('sigExistingSection').style.display !== 'none') {
        // Existing signature is shown — place it without touching saveDrawn
        await window.useExistingSignature();
        return;
    }
    // Draw pad is shown — ensure the user has actually drawn something
    const canvas = document.getElementById('signatureCanvas');
    const px = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
    const hasContent = Array.prototype.some.call(px, (v, i) => i % 4 === 3 && v > 10);
    if (!hasContent) {
        alert('Please draw your signature before placing it on the document.');
        return;
    }
    await window.applySignature();
};

window.applySignature = async function() {
    const canvas  = document.getElementById('signatureCanvas');
    const dataUrl = canvas.toDataURL('image/png');
    CSRF_TOKEN = document.querySelector('input[name="CSRF-TOKEN"]')?.value ?? '';

    // Persist as the provider's signature stamp (same storage used by Provider
    // Preferences for consultations/prescriptions/eForms) so it's available
    // everywhere, not just in this fax viewer.
    try {
        const body = new URLSearchParams({ signatureData: dataUrl, 'CSRF-TOKEN': CSRF_TOKEN });
        const res = await fetch(CTX + '/provider/providerSignatureStamp?method=saveDrawn', {
            method:  'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
        });
        const result = await res.json();
        if (!result.success) {
            console.warn('Could not persist signature:', result.error);
        }
    } catch (e) {
        console.warn('Could not persist signature:', e);
    }

    signatureModal.hide();
    await insertSignatureAsStamp(dataUrl, true);
};

async function insertSignatureAsStamp(dataUrl, isNew) {
    const res  = await fetch(dataUrl);
    const blob = await res.blob();
    const file = new File([blob], 'signature.png', { type: 'image/png' });

    pendingSignatureFile = file;
    interceptNextStampFilePicker = true;

    // Switch to STAMP mode; PDF.js will show a click target and then try to open
    // a file dialog when the user clicks the canvas — which we intercept above.
    window.setMode('stamp');
    usedAnnotationTypes.add('signed');
}

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
    const btn     = document.getElementById('btnSaveFax');
    const overlay = document.getElementById('statusOverlay');
    const msg     = document.getElementById('statusMsg');

    btn.disabled = true;
    msg.textContent = 'Saving annotated document…';
    overlay.classList.add('show');

    try {
        // Serialise the annotated PDF (includes all PDF.js annotation editor layers)
        const pdfBytes = await pdfDocument.saveDocument();

        const blob     = new Blob([new Uint8Array(pdfBytes)], { type: 'application/pdf' });
        const formData = new FormData();
        formData.append('docId',           DOC_ID);
        formData.append('pdfFile',         blob, 'annotated.pdf');
        formData.append('annotationTypes', [...usedAnnotationTypes].join(','));

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

        msg.textContent = 'Opening fax composition…';
        window.location.href = CTX + '/documentManager/FaxDocument?docId=' + DOC_ID + '&faxReady=true';

    } catch (e) {
        overlay.classList.remove('show');
        btn.disabled = false;
        console.error('Save & Fax failed:', e);
        alert('Failed to save the annotated document. Please try again.\n\nDetail: ' + e.message);
    }
};
</script>

</body>
</html>
