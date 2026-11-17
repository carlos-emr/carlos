/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function () {
    'use strict';
    // Included only by the authorized annotation-open capacity response. Retrying this
    // GET has no filing side effects; other unavailable-document pages never include it.
    var url = new URL(window.location.href);
    if (!/\/documentManager\/AnnotateDocument$/.test(url.pathname)
            || !/^[1-9]\d*$/.test(url.searchParams.get('docId') || '')) { return; }
    var key = 'carlos.annotation.capacity.' + url.pathname + url.search;
    var attempt = 0;
    try {
        attempt = Math.min(5, Math.max(0, Number(window.sessionStorage.getItem(key)) || 0));
        window.sessionStorage.setItem(key, String(attempt + 1));
    } catch (ignored) { /* Storage is optional; waiting still works when disabled. */ }
    var timer;
    function schedule() {
        var delay = Math.min(8000, 1000 * Math.pow(2, attempt)) * (1 + Math.random() * 0.25);
        timer = window.setTimeout(function () { window.location.reload(); }, delay);
    }
    window.addEventListener('pagehide', function () { window.clearTimeout(timer); timer = null; });
    window.addEventListener('pageshow', function () { if (timer === null) { schedule(); } });
    schedule();
})();
