/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
/*
 * "Send to myCarlos" for one document: the standalone page (mycarlosSend.jsp) and the dialog on the
 * document page (mycarlosSendDialog.jspf). CarlosMyCarlosSend.mount(root, options) wires one root; every
 * lookup stays inside that root, so several documents on one inbox page each send their own.
 *
 * The root carries data-context, data-patient, data-document and, on the standalone page,
 * data-delivery and data-next. Its parts are marked data-mycarlos-part (form, message, device, send,
 * check, new, history, empty, no-device, service-date, document-title, more; form may be absent where
 * a host page's parser dropped it), and its copy is in
 * [data-copy] and [data-status] spans inside its copy part. Options: persistInUrl keeps the operation id in the page URL
 * (the standalone page only: a dialog must never rewrite the document page's URL), and
 * onBusyChange(busy, operation) hears when a request starts and ends.
 *
 * Portal text is written with textContent only.
 */
(function () {
    'use strict';
    // The inbox can load this twice: once itself, once in a document page whose scripts run.
    if (window.CarlosMyCarlosSend) return;

    // A request that hears nothing back in this time ends as failed, so a stalled connection cannot
    // hold the dialog open (Close and Esc wait for a send) or keep the buttons disabled. It is longer
    // than two portal calls at their longest allowed deadline (59 s each). A send tried again after a
    // timeout reuses its operation id, so it cannot queue a second copy.
    const REQUEST_TIMEOUT_MS = 120000;

    // One deadline for a whole request (token wait, fetch and reply). Browsers without
    // AbortSignal.timeout (Safari before 16, which has <dialog> from 15.4) get a timer instead.
    function deadline() {
        if (typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function') {
            return AbortSignal.timeout(REQUEST_TIMEOUT_MS);
        }
        if (typeof AbortController !== 'function') return null;
        const controller = new AbortController();
        setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
        return controller.signal;
    }

    // A wait that ends with the deadline's reason when the deadline passes first. (An older browser
    // gives an aborted signal no reason.)
    function within(signal, promise) {
        if (!signal) return promise;
        const stop = () => signal.reason || new Error('timeout');
        return Promise.race([promise, new Promise((resolve, reject) => {
            if (signal.aborted) reject(stop());
            else signal.addEventListener('abort', () => reject(stop()), {once: true});
        })]);
    }

    // The operation this page last submitted for each patient's document: its id, its device and
    // whether it is known to be queued. The inbox redraws a document by rebuilding its dialog, and
    // the server tells copies apart by operation id only, so a rebuilt dialog takes this operation
    // back (a send whose reply was lost is retried, never queued again under a new id).
    const submitted = new Map();

    function mount(root, options) {
        if (!root) return null;
        if (root.mycarlosSend) return root.mycarlosSend;
        const settings = Object.assign({persistInUrl: false, onBusyChange: null}, options || {});
        const part = name => root.querySelector('[data-mycarlos-part="' + name + '"]');
        const form = part('form');
        const message = part('message');
        const history = part('history');
        const more = part('more');
        const send = part('send');
        const check = part('check');
        const chooser = part('device');
        const fresh = part('new');
        const empty = part('empty');
        const noDevice = part('no-device');
        const serviceDate = part('service-date');
        const title = part('document-title');
        // The form part is optional: see where Send is wired below.
        if (!message || !history || !send || !check || !chooser) return null;
        const copyBox = part('copy') || root;
        const copy = {};
        copyBox.querySelectorAll('[data-copy]').forEach(node => { copy[node.dataset.copy] = node.textContent; });
        const statuses = {};
        copyBox.querySelectorAll('[data-status]').forEach(node => { statuses[node.dataset.status] = node.textContent; });
        let next = root.dataset.next || null;
        let busy = false;
        // Settles when the running request ends; a first-page read asked for meanwhile runs then.
        let idle = Promise.resolve();
        let reread = null;
        // Every operation id known to be queued: each row a reply listed and each send the server took.
        // The server lists the patient's deliveries a page at a time and keeps this document's, so the
        // current operation's row can drop off the first page while the operation stays queued.
        const queued = new Set(Array.from(history.rows, row => row.dataset.delivery));
        // Set when the chosen device is no longer offered; cleared only by the clinician's own choice.
        let choiceLost = false;
        const key = root.dataset.patient + '/' + root.dataset.document;
        // The submitted operation's device, offered again by a rebuilt dialog's first read.
        let keptDevice = null;
        const kept = submitted.get(key);
        if (kept && !root.dataset.delivery) {
            root.dataset.delivery = kept.deliveryId;
            keptDevice = kept.deviceKeyId;
        }
        const alreadyQueued = () => {
            const own = submitted.get(key);
            return queued.has(root.dataset.delivery) || Boolean(own && own.queued && own.deliveryId === root.dataset.delivery);
        };
        const updateButtons = () => {
            chooser.disabled = busy || chooser.options.length === 0;
            send.disabled = busy || !chooser.value || !root.dataset.delivery || alreadyQueued();
            check.disabled = busy;
            if (more) {
                more.disabled = busy;
                more.hidden = !next;
            }
            // In the dialog, a new copy needs a new operation id; it is offered once this one is queued.
            if (fresh && fresh.tagName === 'BUTTON') {
                fresh.disabled = busy;
                fresh.hidden = !alreadyQueued();
            }
            history.querySelectorAll('[data-retry]').forEach(button => { button.disabled = busy; });
        };
        // The page's opaque operation UUID survives reload/lost replies, preserving exact queue retries.
        if (settings.persistInUrl && root.dataset.delivery) {
            const address = new URL(window.location.href);
            address.searchParams.set('deliveryId', root.dataset.delivery);
            window.history.replaceState(null, '', address);
        }
        const append = rows => {
            rows.forEach(item => {
                queued.add(item.deliveryId);
                if (Array.from(history.rows).some(row => row.dataset.delivery === item.deliveryId && row.dataset.device === item.deviceKeyId)) return;
                const row = document.createElement('tr');
                row.dataset.delivery = item.deliveryId;
                row.dataset.device = item.deviceKeyId;
                row.dataset.status = item.status;
                const date = document.createElement('td');
                const parsed = new Date(item.queuedAt);
                date.textContent = Number.isNaN(parsed.getTime()) ? item.queuedAt : parsed.toLocaleString();
                const status = document.createElement('td');
                status.textContent = statuses[item.status] || statuses.error;
                row.append(date, status);
                history.append(row);
            });
            addRetryControls();
            if (empty) empty.hidden = history.rows.length !== 0;
        };
        const reconcileDevices = devices => {
            const selected = chooser.value;
            // Whether a choice was on offer before this read: then an empty one stays empty.
            const offered = chooser.options.length > 0;
            chooser.replaceChildren();
            devices.forEach(device => {
                const option = document.createElement('option');
                option.value = device.deviceKeyId;
                option.textContent = device.acceptedAt + ' · ' + device.fingerprint;
                chooser.append(option);
            });
            // The device to keep: the one chosen before this read or, on a rebuilt dialog's first read,
            // the submitted operation's own. A removed one requires a deliberate new device choice, on
            // this read and every later one, a read that offered no device at all included. Only when
            // nothing was on offer yet, nothing is kept and no choice was lost (the dialog's first read)
            // is the first device offered, as the standalone page offers it.
            const wanted = keptDevice !== null ? keptDevice : selected;
            const constrained = keptDevice !== null || offered;
            keptDevice = null;
            if (constrained && !devices.some(device => device.deviceKeyId === wanted)) choiceLost = true;
            if (choiceLost) chooser.value = '';
            else if (constrained) chooser.value = wanted;
            if (noDevice) noDevice.hidden = devices.length !== 0;
        };
        // What became of the copy a send submitted, from its own row in the reply. The action answers
        // 200 whatever the outbox recorded, so "Sent" is kept for an upload the portal took (waiting
        // for pickup, or picked up since); a copy still to send or refused shows its state, and one
        // the reply does not list claims nothing ("Needs checking").
        const outcome = (deliveries, deliveryId, deviceKeyId) => {
            const own = deliveries.find(item => item.deliveryId === deliveryId && item.deviceKeyId === deviceKeyId);
            const status = own ? own.status : '';
            return status === 'waiting' || status === 'picked_up' ? (copy.sent || '') : (statuses[status] || statuses.error);
        };
        const addRetryControls = () => {
            Array.from(history.rows).forEach(row => {
                if (row.dataset.status !== 'staged' || row.querySelector('[data-retry]')) return;
                const button = document.createElement('button');
                button.type = 'button';
                button.dataset.retry = '';
                button.textContent = copy.retry;
                button.addEventListener('click', () => request('send', false, {
                    deliveryId: row.dataset.delivery, deviceKeyId: row.dataset.device
                }));
                row.lastElementChild.append(' ', button);
            });
        };
        // Only this origin's PortalDelivery: a context that is not a plain path is refused, never fetched.
        const endpoint = () => {
            const url = new URL(root.dataset.context + '/demographic/PortalDelivery', window.location.origin);
            if (url.origin !== window.location.origin) throw new Error();
            return url;
        };
        // The root's own token input when it is filled, else any filled one on the page (one token a
        // session); csrfTokenFetch.js fills them all when none is.
        const csrfToken = () => {
            const own = root.querySelector('input[name="CSRF-TOKEN"]');
            if (own && own.value) return own;
            return Array.from(document.querySelectorAll('input[name="CSRF-TOKEN"]')).find(input => input.value) || own;
        };
        const announce = (state, operation) => {
            if (typeof settings.onBusyChange === 'function') settings.onBusyChange(state, operation);
        };
        async function request(operation, older, retry, renew) {
            if (busy) return;
            busy = true;
            let ended;
            idle = new Promise(resolve => { ended = resolve; });
            // The queued operation a new copy replaces, as it stood when the new copy was asked for.
            const renewedFrom = renew ? root.dataset.delivery : null;
            message.textContent = operation === 'send' ? (copy.sending || copy.busy) : copy.busy;
            updateButtons();
            announce(true, operation);
            const signal = deadline();
            try {
                const params = new URLSearchParams({method: operation, demographicNo: root.dataset.patient, documentId: root.dataset.document});
                const options = {method: operation === 'read' ? 'GET' : 'POST', credentials: 'same-origin', headers: {Accept: 'application/json'}};
                if (signal) options.signal = signal;
                if (operation === 'send') {
                    params.set('deliveryId', retry ? retry.deliveryId : root.dataset.delivery);
                    params.set('deviceKeyId', retry ? retry.deviceKeyId : chooser.value);
                    // Kept for the page before the request leaves, as its reply can be lost.
                    if (!retry) submitted.set(key, {deliveryId: root.dataset.delivery, deviceKeyId: chooser.value, queued: false});
                }
                const url = endpoint();
                if (options.method === 'GET') {
                    if (older && next) params.set('before', next);
                    url.search = params.toString();
                } else {
                    if (window.csrfTokenReady) await within(signal, window.csrfTokenReady);
                    let token = csrfToken();
                    if ((!token || !token.value) && typeof window.fetchCsrfToken === 'function') {
                        await within(signal, window.fetchCsrfToken(root.dataset.context));
                        token = csrfToken();
                    }
                    if (!token || !token.value) throw new Error();
                    params.set('CSRF-TOKEN', token.value);
                    options.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
                    options.body = params.toString();
                }
                const response = await fetch(url.href, options);
                const result = await response.json();
                if (!response.ok || result.ok !== true) {
                    const failure = new Error();
                    failure.userMessage = typeof result.message === 'string' ? result.message : copy.error;
                    throw failure;
                }
                // The server staged this operation, or found it staged: it is never a new copy again.
                if (operation === 'send') queued.add(params.get('deliveryId'));
                // A read names the operation id for the next send; the dialog takes it when it has none,
                // or when a new copy was asked for.
                if (operation === 'read' && typeof result.deliveryId === 'string' && (renew || !root.dataset.delivery)) {
                    root.dataset.delivery = result.deliveryId;
                    // A new copy is a new operation: the queued one it replaces is not taken back again.
                    // Only that one: this reply can come late, from a dialog the page has replaced since,
                    // after the replacement kept a newer send for retry.
                    const current = submitted.get(key);
                    if (renew && current && current.deliveryId === renewedFrom) submitted.delete(key);
                }
                reconcileDevices(result.devices);
                if (title && typeof result.title === 'string') title.textContent = result.title;
                if (serviceDate) serviceDate.textContent = result.serviceDate || copy.dateNotRecorded;
                if (!older) history.replaceChildren();
                append(result.deliveries);
                next = result.nextCursor;
                const own = submitted.get(key);
                if (own && queued.has(own.deliveryId)) own.queued = true;
                message.textContent = result.syncStatus === 'quarantined' ? copy.sync
                    : result.syncStatus === 'busy' ? copy.busy
                    : retry ? copy.retried
                    : operation === 'send' ? outcome(result.deliveries, params.get('deliveryId'), params.get('deviceKeyId')) : '';
            } catch (failure) {
                message.textContent = (failure && failure.userMessage) || copy.error;
            } finally {
                busy = false;
                ended();
                updateButtons();
                announce(false, operation);
            }
        }
        // A read of the first page now, or right after the request that is running (an older page, a
        // status check), so a dialog reopened meanwhile never keeps what it showed before it closed.
        const read = () => {
            if (!busy) return request('read', false);
            if (!reread) reread = idle.then(() => { reread = null; return read(); });
            return reread;
        };
        addRetryControls();
        chooser.addEventListener('change', () => {
            choiceLost = false;
            updateButtons();
        });
        // The inbox's reading view inserts the document inside its own reassign form, where the parser
        // drops the dialog's <form> and keeps its controls. Send then sends from its click, which never
        // submits the outer form (the controls name the dropped form, so they belong to none).
        if (form) form.addEventListener('submit', event => { event.preventDefault(); request('send', false); });
        else send.addEventListener('click', event => { event.preventDefault(); request('send', false); });
        check.addEventListener('click', () => request('refresh', false));
        if (more) more.addEventListener('click', () => request('read', true));
        if (fresh && fresh.tagName === 'BUTTON') fresh.addEventListener('click', () => request('read', false, null, true));
        updateButtons();
        root.mycarlosSend = {
            read,
            isBusy: () => busy,
            focusTarget: () => (chooser.disabled ? null : chooser)
        };
        return root.mycarlosSend;
    }

    window.CarlosMyCarlosSend = {mount};
    // The standalone page mounts itself; the dialog mounts when it first opens.
    const page = document.getElementById('mycarlos-send');
    if (page) mount(page, {persistInUrl: true});
}());
