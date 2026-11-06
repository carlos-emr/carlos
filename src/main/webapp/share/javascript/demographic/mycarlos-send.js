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
 * check, new, history, empty, no-device, service-date, document-title, more), and its copy is in
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
        if (!form || !message || !history || !send || !check || !chooser) return null;
        const copyBox = part('copy') || root;
        const copy = {};
        copyBox.querySelectorAll('[data-copy]').forEach(node => { copy[node.dataset.copy] = node.textContent; });
        const statuses = {};
        copyBox.querySelectorAll('[data-status]').forEach(node => { statuses[node.dataset.status] = node.textContent; });
        let next = root.dataset.next || null;
        let busy = false;
        const alreadyQueued = () => Array.from(history.rows).some(row => row.dataset.delivery === root.dataset.delivery);
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
            chooser.replaceChildren();
            devices.forEach(device => {
                const option = document.createElement('option');
                option.value = device.deviceKeyId;
                option.textContent = device.acceptedAt + ' · ' + device.fingerprint;
                chooser.append(option);
            });
            // A removed selection requires a deliberate new device choice. With none made yet (the
            // dialog's first read), the first device is offered, as the standalone page offers it.
            if (selected) chooser.value = selected;
            if (noDevice) noDevice.hidden = devices.length !== 0;
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
            message.textContent = operation === 'send' ? (copy.sending || copy.busy) : copy.busy;
            updateButtons();
            announce(true, operation);
            try {
                const params = new URLSearchParams({method: operation, demographicNo: root.dataset.patient, documentId: root.dataset.document});
                const options = {method: operation === 'read' ? 'GET' : 'POST', credentials: 'same-origin', headers: {Accept: 'application/json'}};
                if (operation === 'send') {
                    params.set('deliveryId', retry ? retry.deliveryId : root.dataset.delivery);
                    params.set('deviceKeyId', retry ? retry.deviceKeyId : chooser.value);
                }
                const url = endpoint();
                if (options.method === 'GET') {
                    if (older && next) params.set('before', next);
                    url.search = params.toString();
                } else {
                    if (window.csrfTokenReady) await window.csrfTokenReady;
                    let token = csrfToken();
                    if ((!token || !token.value) && typeof window.fetchCsrfToken === 'function') {
                        await window.fetchCsrfToken(root.dataset.context);
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
                // A read names the operation id for the next send; the dialog takes it when it has none,
                // or when a new copy was asked for.
                if (operation === 'read' && typeof result.deliveryId === 'string' && (renew || !root.dataset.delivery)) {
                    root.dataset.delivery = result.deliveryId;
                }
                reconcileDevices(result.devices);
                if (title && typeof result.title === 'string') title.textContent = result.title;
                if (serviceDate) serviceDate.textContent = result.serviceDate || copy.dateNotRecorded;
                if (!older) history.replaceChildren();
                append(result.deliveries);
                next = result.nextCursor;
                message.textContent = result.syncStatus === 'quarantined' ? copy.sync
                    : result.syncStatus === 'busy' ? copy.busy
                    : retry ? copy.retried
                    : operation === 'send' ? (copy.sent || '') : '';
            } catch (failure) {
                message.textContent = failure.userMessage || copy.error;
            } finally {
                busy = false;
                updateButtons();
                announce(false, operation);
            }
        }
        addRetryControls();
        chooser.addEventListener('change', updateButtons);
        form.addEventListener('submit', event => { event.preventDefault(); request('send', false); });
        check.addEventListener('click', () => request('refresh', false));
        if (more) more.addEventListener('click', () => request('read', true));
        if (fresh && fresh.tagName === 'BUTTON') fresh.addEventListener('click', () => request('read', false, null, true));
        updateButtons();
        root.mycarlosSend = {
            read: () => request('read', false),
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
