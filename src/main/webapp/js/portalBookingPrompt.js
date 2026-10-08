/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. */
(function () {
    'use strict';
    // The action refused these before anything reached the portal, or the portal refused
    // them outright: that attempt stored nothing. The retry identity is dropped only when it
    // was made for that very attempt; one that was tried before, or was found in storage
    // (the page may have died mid-send), may already be stored, so it is kept.
    const DEFINITE_REFUSALS = [400, 403, 404];
    const CSRF_WAIT_MS = 15000;
    function newOperationId() {
        if (crypto.randomUUID) { return crypto.randomUUID(); }
        // randomUUID needs a secure context (HTTPS or localhost); getRandomValues does not.
        const bytes = crypto.getRandomValues(new Uint8Array(16));
        bytes[6] = (bytes[6] & 0x0f) | 0x40;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
        return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
    }
    function mount(root) {
        if (root.dataset.mounted) { return; }
        root.dataset.mounted = 'true';
        const role = name => root.querySelector('[data-role="' + name + '"]');
        const message = key => {
            const node = root.querySelector('[data-message="' + key + '"]');
            return node ? node.textContent : '';
        };
        const status = (...keys) => {
            role('status').textContent = keys.filter(Boolean).map(message).join(' ');
        };
        const create = role('create');
        const send = role('send');
        const urgency = role('urgency');
        const type = role('appointmentType');
        const key = 'portal.booking.pending:' + root.dataset.actor + ':' + root.dataset.patient;
        let pending = null;
        let busy = false;
        let eligible = false;
        let storageReady = false;
        let mayWithdraw = false;
        const patientInput = root.dataset.patientInput ? document.querySelector(root.dataset.patientInput) : null;
        const patientNameInput = patientInput ? document.getElementById('keyword') : null;
        const originalPatientName = patientNameInput ? patientNameInput.value : '';
        let patientEdited = false;
        function samePatient() {
            return !patientInput || !patientEdited && patientInput.value === root.dataset.patient
                && (!patientNameInput || patientNameInput.value === originalPatientName);
        }
        try {
            const saved = sessionStorage.getItem(key);
            if (saved) {
                pending = JSON.parse(saved);
                if (!/^[a-f0-9-]{36}$/.test(pending.operationId)
                        || !['routine', 'soon', 'as_soon_as_possible'].includes(pending.urgency)
                        || !['follow_up', 'annual_exam', 'lab_review'].includes(pending.appointmentType)) {
                    throw new Error('invalid pending request');
                }
            }
            // Verify storage before enabling sends; it retains only retry identity and fixed choices.
            sessionStorage.setItem(key + ':probe', '1');
            sessionStorage.removeItem(key + ':probe');
            storageReady = true;
        } catch (_) { pending = null; status('storage'); }
        function update() {
            const matches = samePatient();
            role('prompts').hidden = !matches;
            role('refresh').disabled = busy || !matches;
            if (!matches) { status('patientChanged'); }
            if (!create) { return; }
            create.hidden = !eligible || !matches;
            if (pending) {
                urgency.value = pending.urgency;
                type.value = pending.appointmentType;
            }
            urgency.disabled = type.disabled = busy || !!pending;
            send.disabled = busy || !eligible || !storageReady;
            send.textContent = message(pending ? 'retry' : 'send');
        }
        async function csrfToken() {
            // csrf-token.jspf publishes its token fetch as window.csrfTokenReady on
            // DOMContentLoaded. It may reject, and a form on the page may already carry the token.
            let timer;
            try {
                if (window.csrfTokenReady) {
                    await Promise.race([window.csrfTokenReady, new Promise((_, reject) => {
                        timer = setTimeout(() => reject(new Error('csrf wait timed out')), CSRF_WAIT_MS);
                    })]);
                }
            } catch (_) { /* re-checked below */ } finally { clearTimeout(timer); }
            const input = Array.from(document.querySelectorAll('input[name="CSRF-TOKEN"]')).find(field => field.value);
            if (!input) {
                const failure = new Error('csrf unavailable');
                failure.notSent = true;
                throw failure;
            }
            return input.value;
        }
        async function post(values) {
            const token = await csrfToken();
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 20000);
            try {
                const response = await fetch(root.dataset.endpoint, {
                    method: 'POST', credentials: 'same-origin', cache: 'no-store',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded',
                        'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': token },
                    body: new URLSearchParams(Object.assign({ demographicNo: root.dataset.patient }, values)),
                    signal: controller.signal
                });
                if (!response.ok) {
                    const failure = new Error('request failed');
                    failure.status = response.status;
                    throw failure;
                }
                const body = await response.json();
                if (body.ok !== true) { throw new Error('unconfirmed response'); }
                return body;
            } finally { clearTimeout(timer); }
        }
        const validPrompt = prompt => prompt && Number.isSafeInteger(prompt.id) && prompt.id > 0
            && ['routine', 'soon', 'as_soon_as_possible'].includes(prompt.urgency)
            && ['follow_up', 'annual_exam', 'lab_review'].includes(prompt.appointmentType)
            && ['sent', 'read', 'choice_pending', 'booked', 'declined_all', 'withdrawn', 'expired'].includes(prompt.state);
        const OPEN_STATES = ['sent', 'read', 'choice_pending'];
        function promptDate(prompt) {
            const date = new Date(prompt.createdAt);
            return Number.isNaN(date.getTime()) ? '' : date.toLocaleString();
        }
        function promptState(prompt) {
            return message('state.' + prompt.state) + ' · ' + message(prompt.readAt ? 'read' : 'unread');
        }
        function cell(text) {
            const node = document.createElement('td'); node.textContent = text; return node;
        }
        // The box on the page: the newest request and how many are still open.
        function summarize(prompts) {
            const summary = role('summary');
            const openCount = role('openCount');
            if (!prompts.length) {
                summary.textContent = message('empty');
                openCount.hidden = true;
                return;
            }
            const last = prompts.reduce((newest, prompt) =>
                (Date.parse(prompt.createdAt) || 0) > (Date.parse(newest.createdAt) || 0) ? prompt : newest);
            summary.textContent = message('last') + ' ' + [message(last.appointmentType), message(last.urgency),
                promptDate(last), promptState(last)].filter(Boolean).join(' · ');
            openCount.textContent = message('openCount')
                .replace('{count}', String(prompts.filter(prompt => OPEN_STATES.includes(prompt.state)).length));
            openCount.hidden = false;
        }
        function render(prompts) {
            role('prompts').replaceChildren();
            summarize(prompts);
            if (!prompts.length) {
                const row = document.createElement('tr');
                const empty = cell(message('empty')); empty.colSpan = 5;
                row.append(empty);
                role('prompts').append(row);
            }
            for (const prompt of prompts) {
                const row = document.createElement('tr');
                row.append(cell(message(prompt.appointmentType)), cell(message(prompt.urgency)),
                    cell(promptState(prompt)), cell(promptDate(prompt)));
                const actions = document.createElement('td');
                if (mayWithdraw && prompt.state !== 'withdrawn') {
                    const template = role('withdraw-template').content.querySelector('button');
                    if (template) {
                        const button = template.cloneNode(true);
                        button.addEventListener('click', () => withdraw(prompt.id));
                        actions.append(button);
                    }
                }
                row.append(actions);
                role('prompts').append(row);
            }
        }
        async function refresh(lead) {
            if (busy || !samePatient()) { update(); return; }
            busy = true; role('refresh').disabled = true; update();
            try {
                const body = await post({ method: 'panel' });
                if (!Array.isArray(body.prompts) || !body.prompts.every(validPrompt)
                        || typeof body.mayCreate !== 'boolean' || typeof body.mayWithdraw !== 'boolean'
                        || body.mayCreate && typeof body.accountActive !== 'boolean') {
                    throw new Error('invalid panel');
                }
                eligible = body.mayCreate && body.accountActive === true;
                mayWithdraw = body.mayWithdraw;
                render(body.prompts);
                const inactive = body.mayCreate && !eligible ? 'inactive' : null;
                if (pending) {
                    // The unconfirmed request stays in view, with the reason it cannot be retried now.
                    status(lead, 'uncertain', inactive);
                } else {
                    // lead: what just happened (sent, withdrawn, not sent), then the account state.
                    status(lead, inactive || (lead ? null : storageReady ? 'ready' : 'storage'));
                }
            } catch (_) {
                eligible = false; mayWithdraw = false;
                role('prompts').replaceChildren();
                role('summary').textContent = message('unavailable');
                role('openCount').hidden = true;
                status(lead || (pending ? 'uncertain' : null), 'unavailable');
            } finally {
                busy = false; role('refresh').disabled = false; update();
            }
        }
        async function submit() {
            if (busy || !eligible || !storageReady || !samePatient()) { update(); return; }
            const fresh = !pending;
            try {
                if (!pending) {
                    // Kept only once it is saved, so a failed save never looks like an unconfirmed send.
                    const request = { operationId: newOperationId(), urgency: urgency.value, appointmentType: type.value };
                    sessionStorage.setItem(key, JSON.stringify(request));
                    pending = request;
                }
            } catch (_) { storageReady = false; status('storage'); update(); return; }
            busy = true; update(); status('sending');
            let confirmed = false;
            let refused = false;
            try {
                const body = await post(Object.assign({ method: 'create' }, pending));
                if (!validPrompt(body.prompt) || typeof body.created !== 'boolean'
                        || body.prompt.urgency !== pending.urgency
                        || body.prompt.appointmentType !== pending.appointmentType) {
                    throw new Error('invalid confirmation');
                }
                sessionStorage.removeItem(key);
                pending = null; confirmed = true;
                status('sent');
            } catch (failure) {
                refused = failure.notSent === true || DEFINITE_REFUSALS.includes(failure.status);
                if (refused && fresh) {
                    try { sessionStorage.removeItem(key); } catch (_) { /* nothing left to retry */ }
                    pending = null;
                } else {
                    // Kept: this or an earlier attempt with it may have reached the portal.
                    status('uncertain');
                }
            }
            finally { busy = false; update(); }
            if (confirmed) { await refresh('sent'); }
            // A refusal says so, then shows the refreshed state (for example, not on the portal).
            if (refused) { await refresh(pending ? null : 'notSent'); }
        }
        async function withdraw(id) {
            if (busy || !mayWithdraw || !samePatient()) { update(); return; }
            busy = true; update(); status('withdrawing');
            root.querySelectorAll('[data-role="prompts"] button').forEach(button => { button.disabled = true; });
            let confirmed = false;
            try {
                const body = await post({ method: 'withdraw', promptId: id });
                if (!validPrompt(body.prompt) || body.prompt.id !== id || body.prompt.state !== 'withdrawn') {
                    throw new Error('invalid withdrawal');
                }
                confirmed = true;
            } catch (_) { status('withdrawFailed'); }
            finally { busy = false; update(); }
            if (confirmed) { await refresh('withdrawn'); }
            // A failed withdrawal stays disabled until staff refresh the confirmed status.
        }
        if (send) { send.addEventListener('click', submit); }
        if (patientInput) {
            patientInput.addEventListener('input', update);
            patientInput.addEventListener('change', update);
        }
        if (patientNameInput) {
            const markPatientEdited = () => { patientEdited = true; update(); };
            patientNameInput.addEventListener('input', markPatientEdited);
            patientNameInput.addEventListener('change', markPatientEdited);
        }
        role('refresh').addEventListener('click', () => refresh());
        // The controls live in a dialog opened from the box; without script the box says so.
        const dialog = role('dialog');
        const open = role('open');
        open.hidden = false;
        open.addEventListener('click', () => {
            if (typeof dialog.showModal === 'function') { dialog.showModal(); }
            else { dialog.setAttribute('open', ''); }
        });
        root.querySelectorAll('[data-role="close"]').forEach(button => button.addEventListener('click', () => {
            if (typeof dialog.close === 'function') { dialog.close(); } else { dialog.removeAttribute('open'); }
            open.focus();
        }));
        update(); refresh();
    }
    function init() { document.querySelectorAll('[data-portal-booking]').forEach(mount); }
    // Mount after DOMContentLoaded, when csrf-token.jspf has published window.csrfTokenReady: as
    // a deferred script this runs before it. "load" covers a script added after DOMContentLoaded.
    if (document.readyState === 'complete') { init(); }
    else {
        document.addEventListener('DOMContentLoaded', init);
        window.addEventListener('load', init);
    }
}());
