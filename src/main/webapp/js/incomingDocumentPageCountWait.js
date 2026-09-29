/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function () {
    'use strict';
    var marker = document.getElementById('incomingDocumentPageCountWait');
    if (!marker || marker.getAttribute('data-auto-retry') !== 'true') return;
    var target;
    try { target = new URL(marker.getAttribute('data-retry-url'), window.location.href); }
    catch (ignored) { return; }
    var context = window.location.pathname.split('/documentManager/')[0];
    if (target.origin !== window.location.origin || target.pathname !== context + '/documentManager/ViewIncomingDocs'
            || target.username || target.password || target.hash
            || Array.from(target.searchParams.keys()).some(key => !['queueId', 'pdfDir', 'pdfNo', 'pdfPageNumber', 'lastdemographic_no', 'entryMode'].includes(key))
            || ['queueId', 'pdfDir', 'pdfNo', 'pdfPageNumber'].some(key => target.searchParams.getAll(key).length !== 1)
            || !/^[1-9][0-9]{0,9}$/.test(target.searchParams.get('queueId') || '')
                || Number(target.searchParams.get('queueId')) > 2147483647
                || ['lastdemographic_no', 'entryMode'].some(key => target.searchParams.getAll(key).length > 1)
                || (target.searchParams.has('lastdemographic_no') && (!/^[1-9][0-9]{0,9}$/.test(target.searchParams.get('lastdemographic_no')) || Number(target.searchParams.get('lastdemographic_no')) > 2147483647))
                || (target.searchParams.has('entryMode') && !['Normal', 'Fast'].includes(target.searchParams.get('entryMode')))
            || !['Fax', 'Mail', 'File', 'Refile'].includes(target.searchParams.get('pdfDir'))
            || !/^[1-9][0-9]*$/.test(target.searchParams.get('pdfNo') || '')
            || !/^[1-9][0-9]*$/.test(target.searchParams.get('pdfPageNumber') || '')) return;
    var attempt = 0;
    var key = 'carlos.incomingPageCount.capacity';
    try {
        attempt = Math.min(3, Math.max(0, Number(window.sessionStorage.getItem(key)) || 0));
        window.sessionStorage.setItem(key, String(Math.min(3, attempt + 1)));
    } catch (ignored) { /* Waiting does not depend on storage availability. */ }
    var timer;
    function schedule() {
        timer = window.setTimeout(function () { window.location.replace(target.href); },
            Math.min(8000, 1000 * Math.pow(2, attempt)) * (1 + Math.random() * .25));
    }
    window.addEventListener('pagehide', function () { window.clearTimeout(timer); timer = null; });
    window.addEventListener('pageshow', function () { if (timer === null) schedule(); });
    schedule();
})();
