/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function () {
    'use strict';
    // Included only in the authorized GET-only incoming-preview capacity503 HTML.
    // Reload this iframe's current safe read; never reconstruct a target or replay
    // a mutation. Leaving/changing the preview disposes its outstanding timer.
    if (!document.getElementById('incomingDocumentCapacityWait')) return;
    var url = new URL(window.location.href);
    if (!/\/documentManager\/ManageDocument$/.test(url.pathname)
            || !['viewIncomingDocPageAsPdf', 'viewIncomingDocPageAsImage'].includes(url.searchParams.get('method'))
            || !/^\d+$/.test(url.searchParams.get('queueId') || '')
            || !/^[1-9]\d*$/.test(url.searchParams.get('curPage') || '1')
            || !url.searchParams.get('pdfName') || !url.searchParams.has('pdfDir')) return;
    // One bounded key per preview mode, never a patient/file name or request URL.
    var key = 'carlos.incomingPreview.capacity.' + url.searchParams.get('method');
    var attempt = 0;
    try {
        attempt = Math.min(3, Math.max(0, Number(window.sessionStorage.getItem(key)) || 0));
        window.sessionStorage.setItem(key, String(Math.min(3, attempt + 1)));
    } catch (ignored) { /* Storage is optional; no patient data is needed to wait. */ }
    var timer;
    function schedule() {
        var delay = Math.min(8000, 1000 * Math.pow(2, attempt)) * (1 + Math.random() * .25);
        timer = window.setTimeout(function () { window.location.reload(); }, delay);
    }
    window.addEventListener('pagehide', function () { window.clearTimeout(timer); timer = null; });
    window.addEventListener('pageshow', function () { if (timer === null) schedule(); });
    schedule();
})();
