/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
(function () {
    'use strict';
    const panel = document.getElementById('mycarlos-devices');
    if (!panel) return;
    const labels = new Map(Array.from(panel.querySelectorAll('[data-key]'), node => [node.dataset.key, node.textContent]));
    const list = document.getElementById('mycarlos-devices-list');
    const status = document.getElementById('mycarlos-devices-status');
    const check = document.getElementById('mycarlos-devices-check');
    let busy = false;
    function text(key) { return labels.get(key) || labels.get('error.changed'); }
    function element(tag, content) {
        const node = document.createElement(tag);
        if (content !== undefined) node.textContent = content;
        return node;
    }
    async function request(method, fields) {
        const params = new URLSearchParams({demographicNo: panel.dataset.patient, ...fields});
        const headers = {'Accept': 'application/json'};
        const options = {method, credentials: 'same-origin', redirect: 'error', cache: 'no-store', headers};
        let target = panel.dataset.endpoint;
        if (method === 'GET') target += '?' + params.toString();
        else {
            if (window.csrfTokenReady) await window.csrfTokenReady;
            const token = document.querySelector('input[name="CSRF-TOKEN"]');
            if (!token || !token.value) throw new Error('token unavailable');
            headers['CSRF-TOKEN'] = token.value;
            headers['X-Requested-With'] = 'XMLHttpRequest';
            options.body = params;
        }
        const response = await fetch(target, options);
        const payload = await response.json();
        if (!response.ok || payload.ok !== true) throw new Error('approval request unconfirmed');
        return payload;
    }
    function render(state) {
        if (!Array.isArray(state.pending) || !Array.isArray(state.devices)) throw new Error('invalid approval state');
        list.replaceChildren();
        if (!state.pending.length && !state.devices.length) list.append(element('p', text('none')));
        state.devices.forEach(device => {
            const entry = element('div');
            entry.append(element('strong', text(device.removedAt ? 'status.removed' : 'status.accepted')));
            entry.append(element('pre', device.fingerprint));
            list.append(entry);
        });
        state.pending.forEach(row => {
            const entry = element('div');
            entry.append(element('p', text(row.expired ? 'status.expired' : row.firstDevice ? 'first.body' : 'later.hint')));
            entry.append(element('pre', row.fingerprint));
            if (row.firstDevice && !row.expired && panel.dataset.write === 'true') {
                const form = element('form');
                form.className = 'mycarlos-device-approval-form';
                form.method = 'post';
                form.action = panel.dataset.endpoint;
                const label = element('label', text('device.fingerprint'));
                label.className = 'mycarlos-device-fingerprint';
                const input = element('input');
                input.type = 'text';
                input.required = true;
                input.minLength = input.maxLength = 64;
                input.pattern = '[0-9a-f]{64}';
                input.autocomplete = 'off';
                input.spellcheck = false;
                label.append(input);
                const confirmation = element('label');
                confirmation.className = 'mycarlos-device-confirmation';
                const confirmed = element('input');
                confirmed.type = 'checkbox';
                confirmed.required = true;
                confirmation.append(confirmed, document.createTextNode(text('first.confirm')));
                const submit = element('button', text('first.action'));
                submit.type = 'submit';
                submit.className = 'portal-button portal-button--primary';
                form.append(label, confirmation, submit);
                form.addEventListener('submit', event => {
                    event.preventDefault();
                    if (!form.reportValidity()) return;
                    run('approveFirst', {challengeId: row.challengeId, challengeSha256: row.challengeSha256,
                        fingerprint: input.value, confirmed: String(confirmed.checked)});
                });
                entry.append(form);
            }
            list.append(entry);
        });
    }
    async function run(operation, fields) {
        if (busy) return;
        busy = true;
        panel.setAttribute('aria-busy', 'true');
        if (check) check.disabled = true;
        panel.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = true; });
        try {
            const state = await request(operation === 'read' ? 'GET' : 'POST', {method: operation, ...fields});
            render(state);
            status.textContent = state.refusedRequests && state.refusedRequests.length
                ? text(state.refusedRequests.every(reason => reason === 'challenge_expired')
                    ? 'status.expired' : 'status.unavailable') : '';
        } catch (_) {
            status.textContent = text('status.unavailable');
        } finally {
            busy = false;
            panel.removeAttribute('aria-busy');
            if (check) check.disabled = false;
            panel.querySelectorAll('button[type="submit"]').forEach(button => { button.disabled = false; });
        }
    }
    if (check) check.addEventListener('click', () => run('refresh', {}));
    run('read', {});
}());
