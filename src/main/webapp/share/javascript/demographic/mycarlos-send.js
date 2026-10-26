/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
(function () {
    'use strict';
    const root = document.getElementById('mycarlos-send');
    const form = document.getElementById('delivery-form');
    if (!root || !form) return;
    const message = document.getElementById('delivery-message');
    const history = document.getElementById('delivery-history');
    const more = document.getElementById('delivery-more');
    const send = document.getElementById('delivery-send');
    const check = document.getElementById('delivery-check');
    const chooser = document.getElementById('delivery-device');
    const copy = {};
    document.querySelectorAll('#delivery-copy [data-copy]').forEach(node => { copy[node.dataset.copy] = node.textContent; });
    const statuses = {};
    document.querySelectorAll('#delivery-copy [data-status]').forEach(node => { statuses[node.dataset.status] = node.textContent; });
    let next = root.dataset.next || null;
    let busy = false;
    const alreadyQueued = () => Array.from(history.rows).some(row => row.dataset.delivery === root.dataset.delivery);
    const updateButtons = () => {
        chooser.disabled = busy || chooser.options.length === 0;
        send.disabled = busy || !chooser.value || alreadyQueued();
        check.disabled = busy;
        more.disabled = busy;
        more.hidden = !next;
        history.querySelectorAll('[data-retry]').forEach(button => { button.disabled = busy; });
    };
    // The page's opaque operation UUID survives reload/lost replies, preserving exact queue retries.
    const address = new URL(window.location.href);
    address.searchParams.set('deliveryId', root.dataset.delivery);
    window.history.replaceState(null, '', address);
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
        document.getElementById('delivery-empty').hidden = history.rows.length !== 0;
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
        // A removed selection requires a deliberate new device choice.
        chooser.value = selected;
        document.getElementById('delivery-no-device').hidden = devices.length !== 0;
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
    async function request(operation, older, retry) {
        if (busy) return;
        busy = true;
        message.textContent = copy.busy;
        updateButtons();
        try {
            const params = new URLSearchParams({method: operation, demographicNo: root.dataset.patient, documentId: root.dataset.document});
            const options = {method: operation === 'read' ? 'GET' : 'POST', credentials: 'same-origin', headers: {Accept: 'application/json'}};
            if (operation === 'send') {
                params.set('deliveryId', retry ? retry.deliveryId : root.dataset.delivery);
                params.set('deviceKeyId', retry ? retry.deviceKeyId : chooser.value);
            }
            let url = root.dataset.context + '/demographic/PortalDelivery';
            if (options.method === 'GET') { if (older && next) params.set('before', next); url += '?' + params.toString(); }
            else {
                if (window.csrfTokenReady) await window.csrfTokenReady;
                let token = document.querySelector('input[name="CSRF-TOKEN"]');
                if ((!token || !token.value) && typeof window.fetchCsrfToken === 'function') {
                    await window.fetchCsrfToken(root.dataset.context);
                    token = document.querySelector('input[name="CSRF-TOKEN"]');
                }
                if (!token || !token.value) throw new Error();
                params.set('CSRF-TOKEN', token.value);
                options.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
                options.body = params.toString();
            }
            const response = await fetch(url, options);
            const result = await response.json();
            if (!response.ok || result.ok !== true) {
                const failure = new Error();
                failure.userMessage = typeof result.message === 'string' ? result.message : copy.error;
                throw failure;
            }
            reconcileDevices(result.devices);
            if (!older) history.replaceChildren();
            append(result.deliveries);
            next = result.nextCursor;
            message.textContent = result.syncStatus === 'quarantined' ? copy.sync : result.syncStatus === 'busy' ? copy.busy : retry ? copy.retried : '';
        } catch (failure) {
            message.textContent = failure.userMessage || copy.error;
        } finally {
            busy = false;
            updateButtons();
        }
    }
    addRetryControls();
    chooser.addEventListener('change', updateButtons);
    form.addEventListener('submit', event => { event.preventDefault(); request('send', false); });
    check.addEventListener('click', () => request('refresh', false));
    more.addEventListener('click', () => request('read', true));
    updateButtons();
}());
