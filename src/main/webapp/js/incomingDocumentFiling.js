/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function () {
    'use strict';
    var form = document.querySelector('form[data-incoming-filing="true"]');
    if (!form || form.dataset.filingBound) return;
    var status = document.getElementById('incoming-filing-status');
    if (!status) return;
    var endpoint = new URL(form.action, window.location.href);
    var context = window.location.pathname.split('/documentManager/')[0];
    if (endpoint.origin !== window.location.origin || endpoint.pathname !== context + '/documentManager/ManageDocument'
            || endpoint.username || endpoint.password || endpoint.search || endpoint.hash) return;
    form.dataset.filingBound = 'true';
    var running = false, uncertain = false, inactive = false, cancelWait = null, pendingNavigation = null;
    var originals = [];

    function message(text) { status.textContent = text; }
    function lock() {
        originals = Array.from(form.elements).map(element => [element, element.disabled]);
        originals.forEach(pair => { pair[0].disabled = true; });
        form.setAttribute('aria-busy', 'true');
    }
    function unlock() {
        originals.forEach(pair => { pair[0].disabled = pair[1]; });
        originals = [];
        form.setAttribute('aria-busy', 'false');
    }
    function unconfirmed() {
        uncertain = true;
        form.setAttribute('aria-busy', 'false');
        message(form.dataset.unconfirmedMessage || 'The save could not be confirmed. Check the patient documents before saving another copy.');
    }
    function wait(response, attempt) {
        var advertised = Number(response.headers.get('Retry-After'));
        var minimum = Number.isFinite(advertised) && advertised > 0 ? Math.min(8000, advertised * 1000) : 1000;
        var delay = Math.min(8000, Math.max(minimum, 1000 * Math.pow(2, Math.min(attempt, 3)))) * (1 + Math.random() * .25);
        return new Promise(resolve => {
            var timer = window.setTimeout(() => { cancelWait = null; resolve(true); }, delay);
            cancelWait = () => { window.clearTimeout(timer); cancelWait = null; resolve(false); };
        });
    }
    function nextUrl(value) {
        var next = new URL(value, window.location.href);
        var keys = ['queueId', 'pdfDir', 'pdfNo', 'pdfPageNumber'];
        if (next.origin !== endpoint.origin || next.pathname !== context + '/documentManager/ViewIncomingDocs'
                || next.username || next.password || next.hash
                || Array.from(next.searchParams.keys()).some(key => !keys.concat(['lastdemographic_no', 'entryMode']).includes(key))
                || keys.some(key => next.searchParams.getAll(key).length !== 1)
                || !/^[1-9][0-9]{0,9}$/.test(next.searchParams.get('queueId') || '')
                || Number(next.searchParams.get('queueId')) > 2147483647
                || ['lastdemographic_no', 'entryMode'].some(key => next.searchParams.getAll(key).length > 1)
                || (next.searchParams.has('lastdemographic_no') && (!/^[1-9][0-9]{0,9}$/.test(next.searchParams.get('lastdemographic_no')) || Number(next.searchParams.get('lastdemographic_no')) > 2147483647))
                || (next.searchParams.has('entryMode') && !['Normal', 'Fast'].includes(next.searchParams.get('entryMode')))
                || !['Fax', 'Mail', 'File', 'Refile'].includes(next.searchParams.get('pdfDir'))
                || !/^[1-9][0-9]*$/.test(next.searchParams.get('pdfNo') || '')
                || !/^[1-9][0-9]*$/.test(next.searchParams.get('pdfPageNumber') || '')) throw new Error('Invalid next document URL');
        return next.href;
    }
    form.addEventListener('submit', async event => {
        if (event.defaultPrevented) return;
        event.preventDefault();
        if (running || uncertain || inactive) return;
        // Capture before disabling inputs; repeated provider fields and CSRF token are retained.
        var fields = new FormData(form);
        if (event.submitter && event.submitter.name) fields.append(event.submitter.name, event.submitter.value);
        var body = new URLSearchParams();
        fields.forEach((value, key) => body.append(key, String(value)));
        var frozenBody = body.toString();
        running = true;
        lock();
        message(form.dataset.savingMessage || 'Saving…');
        var sent = false;
        try {
            var attempt = 0;
            while (!inactive) {
                sent = true;
                var response = await fetch(endpoint.href, {
                    method: 'POST', credentials: 'same-origin', redirect: 'error',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest',
                        'X-Carlos-Incoming-Filing': 'bounded-v1', 'CSRF-TOKEN': body.get('CSRF-TOKEN') || ''},
                    body: frozenBody
                });
                var data = await response.json();
                if (response.status === 503 && data && data.success === false && data.retryable === true && data.accepted === false) {
                    sent = false;
                    message(form.dataset.waitMessage || 'Waiting for document capacity. Your input is preserved.');
                    if (inactive || !await wait(response, attempt++)) break;
                    continue;
                }
                if (response.status === 422 && data && data.success === false && data.accepted === false && data.retryable === false) {
                    sent = false;
                    message(typeof data.error === 'string' ? data.error : 'The document could not be read. Review it before retrying.');
                    unlock();
                    return;
                }
                if (!response.ok || !data || data.success !== true || data.accepted !== true
                        || !Number.isSafeInteger(data.documentNo) || data.documentNo < 1) throw new Error('Unconfirmed filing');
                pendingNavigation = nextUrl(data.nextUrl);
                running = false;
                if (!inactive) window.location.assign(pendingNavigation);
                return;
            }
            // Cancellation while waiting is known to precede acceptance; input stays editable.
            message('');
            unlock();
        } catch (error) {
            if (sent) unconfirmed();
            else { message(''); unlock(); }
        } finally {
            running = false;
        }
    });
    window.addEventListener('pagehide', () => { inactive = true; if (cancelWait) cancelWait(); });
    window.addEventListener('pageshow', () => {
        inactive = false;
        if (pendingNavigation) window.location.assign(pendingNavigation);
    });
    window.addEventListener('beforeunload', event => {
        if (running || uncertain) { event.preventDefault(); event.returnValue = ''; }
    });
})();
