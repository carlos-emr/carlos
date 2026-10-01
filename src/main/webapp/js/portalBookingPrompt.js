/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. */
(function () {
    'use strict';
    function mount(root) {
        if (root.dataset.mounted) { return; }
        root.dataset.mounted = 'true';
        const role = name => root.querySelector('[data-role="' + name + '"]');
        const message = key => {
            const node = root.querySelector('[data-message="' + key + '"]');
            return node ? node.textContent : '';
        };
        const status = key => { role('status').textContent = message(key); };
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
        } catch (_) { status('storage'); }
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
        async function post(values) {
            if (window.csrfTokenReady) { await window.csrfTokenReady; }
            const token = document.querySelector('input[name="CSRF-TOKEN"]');
            if (!token || !token.value) { throw new Error('csrf unavailable'); }
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 20000);
            try {
                const response = await fetch(root.dataset.endpoint, {
                    method: 'POST', credentials: 'same-origin', cache: 'no-store',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded',
                        'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': token.value },
                    body: new URLSearchParams(Object.assign({ demographicNo: root.dataset.patient }, values)),
                    signal: controller.signal
                });
                if (!response.ok) { throw new Error('request failed'); }
                const body = await response.json();
                if (body.ok !== true) { throw new Error('unconfirmed response'); }
                return body;
            } finally { clearTimeout(timer); }
        }
        const validPrompt = prompt => prompt && Number.isSafeInteger(prompt.id) && prompt.id > 0
            && ['routine', 'soon', 'as_soon_as_possible'].includes(prompt.urgency)
            && ['follow_up', 'annual_exam', 'lab_review'].includes(prompt.appointmentType)
            && ['sent', 'read', 'choice_pending', 'booked', 'declined_all', 'withdrawn', 'expired'].includes(prompt.state);
        function render(prompts) {
            role('prompts').replaceChildren();
            if (!prompts.length) {
                const item = document.createElement('li'); item.textContent = message('empty');
                role('prompts').append(item);
            }
            for (const prompt of prompts) {
                const item = document.createElement('li');
                const date = new Date(prompt.createdAt);
                item.textContent = message(prompt.appointmentType) + ' | ' + message(prompt.urgency)
                    + ' | ' + message('state.' + prompt.state) + ' | '
                    + message(prompt.readAt ? 'read' : 'unread') + ' | ' + message('created')
                    + ' ' + (Number.isNaN(date.getTime()) ? '' : date.toLocaleString());
                if (mayWithdraw && prompt.state !== 'withdrawn') {
                    const template = role('withdraw-template').content.querySelector('button');
                    if (template) {
                        const button = template.cloneNode(true);
                        button.addEventListener('click', () => withdraw(prompt.id));
                        item.append(button);
                    }
                }
                role('prompts').append(item);
            }
        }
        async function refresh(successMessage) {
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
                status(pending ? 'uncertain' : successMessage ||
                    (body.mayCreate && !eligible ? 'inactive' : storageReady ? 'ready' : 'storage'));
            } catch (_) {
                eligible = false; mayWithdraw = false;
                role('prompts').replaceChildren();
                if (successMessage) {
                    role('status').textContent = message(successMessage) + ' ' + message('unavailable');
                } else { status(pending ? 'uncertain' : 'unavailable'); }
            } finally {
                busy = false; role('refresh').disabled = false; update();
            }
        }
        async function submit() {
            if (busy || !eligible || !storageReady || !samePatient()) { update(); return; }
            try {
                if (!pending) {
                    pending = { operationId: crypto.randomUUID(), urgency: urgency.value, appointmentType: type.value };
                    sessionStorage.setItem(key, JSON.stringify(pending));
                }
            } catch (_) { storageReady = false; status('storage'); update(); return; }
            busy = true; update(); status('sending');
            let confirmed = false;
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
            } catch (_) { status('uncertain'); }
            finally { busy = false; update(); }
            if (confirmed) { await refresh('sent'); }
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
        update(); refresh();
    }
    function init() { document.querySelectorAll('[data-portal-booking]').forEach(mount); }
    if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); }
    else { init(); }
}());
