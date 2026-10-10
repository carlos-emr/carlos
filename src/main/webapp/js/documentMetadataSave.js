/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function (root) {
    'use strict';
    // AJAX fragments may include this file again. Keep pending/uncertain operations.
    if (root.CarlosDocumentMetadata) return;
    const operations = new Map();
    let departed = false;
    // Bumped on every pagehide so a bfcache restore cannot revive an operation
    // whose request was in flight, or whose outcome was shown, before departure.
    let epoch = 0;
    const positiveId = value => /^[1-9][0-9]*$/.test(String(value)) && Number.isSafeInteger(Number(value));
    const byId = id => document.getElementById(id);
    const message = name => (root.CarlosDocumentMutationMessages || {})[name] || {
        pending: 'Saving document changes.', saved: 'Document changes saved.',
        rejected: 'The document change was not accepted. Check the selection and try again.',
        uncertain: 'The document change could not be confirmed. Check the document before trying again.',
        queueRejected: 'The document queue update was not accepted. Try the action again.',
        queueUncertain: 'The document queue update could not be confirmed. Check the queue before trying again.'
    }[name];
    function csrf(form) {
        const input = form.querySelector('input[name="CSRF-TOKEN"]') || document.querySelector('input[name="CSRF-TOKEN"]');
        return input ? input.value : '';
    }
    function fingerprint(form) {
        return JSON.stringify(Array.from(form.elements).filter(el => !['button', 'submit', 'reset'].includes(el.type) && el.name !== 'saved')
            .map(el => [el.name, el.value, el.checked, el.disabled]));
    }
    function controls(form) {
        return Array.from(form.querySelectorAll('button, input[type="submit"], input[type="button"], input[type="reset"]'));
    }
    function show(entry, state) {
        entry.state = state;
        const current = byId(entry.form.id);
        // A replacement row must not bypass an uncertain operation for this document.
        if (current) {
            const requestForm = entry.requestForm && byId(entry.requestForm.id);
            const buttons = controls(current).concat(requestForm ? controls(requestForm) : []);
            for (const control of buttons) {
                if (!entry.controls.has(control)) entry.controls.set(control, control.disabled);
                control.disabled = ['pending', 'uncertain', 'queuePending', 'queueUncertain'].includes(state) ? true : entry.controls.get(control);
            }
            let status = byId('document-metadata-status-' + entry.id);
            if (!status || status.parentNode !== current) {
                status = document.createElement('div');
                status.id = 'document-metadata-status-' + entry.id;
                status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
                current.appendChild(status);
            }
            status.textContent = message(state === 'queuePending' ? 'pending' : state);
        }
    }
    function clearSaved(id) {
        const saved = byId('saved' + id), label = byId('saveSucessMsg_' + id);
        if (saved) saved.value = 'false';
        if (label) label.style.display = 'none';
    }
    function current(entry) {
        return !departed && entry.epoch === epoch && entry.form.isConnected !== false && byId(entry.form.id) === entry.form
            && csrf(entry.form) === entry.token && fingerprint(entry.form) === entry.fingerprint
            && (!entry.requestForm || (entry.requestForm.isConnected !== false && byId(entry.requestForm.id) === entry.requestForm && fingerprint(entry.requestForm) === entry.requestFingerprint))
            && (!entry.anchor || (entry.anchor.isConnected !== false && entry.anchor.parentNode === entry.parent));
    }
    function documentId(form) {
        const data = new URLSearchParams(new FormData(form));
        const ids = data.getAll('documentId'), legacy = data.getAll('doc_no');
        if (ids.length > 1 || legacy.length > 1) return null;
        const id = ids.length ? ids[0] : legacy[0];
        if (!positiveId(id) || (legacy.length && legacy[0] !== id) || form.id !== 'forms_' + id) return null;
        return id;
    }
    function begin(form, id, anchor, metadata) {
        if (!form || !positiveId(id)) return null;
        const existing = operations.get(id);
        if (existing && ['pending', 'uncertain', 'queuePending', 'queueUncertain', 'queueRejected'].includes(existing.state)) { show(existing, existing.state); return null; }
        if (metadata) clearSaved(id);
        const entry = {id, form, token: csrf(form), controls: new Map(), metadata, anchor, parent: anchor && anchor.parentNode, epoch};
        operations.set(id, entry);
        if (!entry.token || departed || documentId(form) !== id) { show(entry, 'rejected'); return null; }
        return entry;
    }
    function rejected(body, entry) {
        return body && body.success === false && body.accepted === false && String(body.document) === entry.id;
    }
    function accepted(body, entry) {
        return body && body.success === true && body.accepted === true && String(body.document) === entry.id
            && positiveId(body.document);
    }
    function saveIndicator(entry) {
        const saved = byId('saved' + entry.id), label = byId('saveSucessMsg_' + entry.id);
        if (saved) saved.value = 'true';
        if (label) label.style.display = '';
    }
    async function finishQueue(entry) {
        show(entry, 'queuePending');
        try {
            if (!current(entry)) throw new Error('Document context changed');
            const response = await fetch(root.CarlosDocumentMetadataContext + '/documentManager/inboxManage', {
                method: 'POST', credentials: 'same-origin', redirect: 'error',
                headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': entry.token},
                body: entry.queueBody
            });
            if (response.redirected || !/^application\/json(?:\s*;|$)/i.test(response.headers.get('Content-Type') || '')) throw new Error('Unconfirmed queue response');
            const body = await response.json();
            if (!current(entry)) throw new Error('Document context changed');
            if (!response.ok && rejected(body, entry)) { show(entry, 'queueRejected'); return false; }
            if (!response.ok || !accepted(body, entry)) throw new Error('Unconfirmed queue response');
            show(entry, 'saved');
            if (entry.onSuccess) entry.onSuccess(entry.metadataResponse, entry.id);
            return true;
        } catch (_) {
            show(entry, 'queueUncertain');
            return false;
        }
    }
    async function send(entry, data, validate, onSuccess, options) {
        entry.fingerprint = fingerprint(entry.form);
        show(entry, 'pending');
        try {
            const response = await fetch(root.CarlosDocumentMetadataContext + (options.endpoint || '/documentManager/ManageDocument'), {
                method: 'POST', credentials: 'same-origin', redirect: 'error',
                headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': entry.token},
                body: data.toString()
            });
            if (response.redirected || !/^application\/json(?:\s*;|$)/i.test(response.headers.get('Content-Type') || '')) throw new Error('Unconfirmed response');
            const body = await response.json();
            if (!current(entry)) throw new Error('Document context changed');
            if (rejected(body, entry) && !response.ok) { show(entry, 'rejected'); return; }
            if (!response.ok || (!options.acknowledgement && !accepted(body, entry)) || !validate(body)) throw new Error('Unconfirmed response');
            entry.metadataAccepted = options.metadata === true;
            show(entry, 'saved');
            if (options.metadata) saveIndicator(entry);
            if (options.queue) {
                entry.metadataResponse = body;
                entry.onSuccess = onSuccess;
                entry.queueBody = new URLSearchParams({method: 'updateDocStatusInQueue', docid: entry.id, 'CSRF-TOKEN': entry.token}).toString();
                await finishQueue(entry);
            } else if (onSuccess) onSuccess(body, entry.id);
        } catch (_) {
            // Even a lost reply may follow a committed write. Never replay it automatically
            // or allow another click to overwrite work whose outcome is unknown.
            if (entry.metadata && !entry.metadataAccepted && byId(entry.form.id) === entry.form) clearSaved(entry.id);
            show(entry, 'uncertain');
        }
    }
    function save(formId, onSuccess, completeQueue) {
        const form = byId(formId), id = form && documentId(form);
        if (!id) {
            const match = form && /^forms_([1-9][0-9]*)$/.exec(form.id);
            if (match) begin(form, match[1], null, true);
            return false;
        }
        const previous = operations.get(id);
        if (previous && previous.state === 'queueRejected') {
            if (completeQueue && previous.kind === 'metadata' && current(previous)) finishQueue(previous);
            else show(previous, completeQueue ? 'queueUncertain' : 'queueRejected');
            return false;
        }
        const entry = begin(form, id, null, true);
        if (!entry) return false;
        entry.kind = 'metadata';
        const data = new URLSearchParams(new FormData(form));
        const methods = data.getAll('method'), demographics = data.getAll('demog');
        if (methods.length !== 1 || !['documentUpdate', 'documentUpdateAjax'].includes(methods[0]) || demographics.length > 1) {
            show(entry, 'rejected'); return false;
        }
        data.set('method', 'documentUpdateAjax');
        data.set('documentId', id);
        // A provider-primary or unfiled document legitimately has no patient target.
        const target = demographics[0];
        send(entry, data, body => body.patientId === null || (positiveId(body.patientId) && String(body.patientId) === target), onSuccess, {metadata: true, queue: completeQueue === true});
        return false;
    }
    function unlink(type, id, provider, anchor, onSuccess) {
        id = String(id); provider = String(provider);
        const form = byId('forms_' + id);
        if (type !== 'DOC' || !/^[a-zA-Z0-9_-]+$/.test(provider) || !anchor || !anchor.parentNode) return false;
        const entry = begin(form, id, anchor);
        if (!entry) return false;
        const data = new URLSearchParams({method: 'removeLinkFromDocument', docType: type, docId: id, providerNo: provider, 'CSRF-TOKEN': entry.token});
        send(entry, data, body => Array.isArray(body.linkedProviders) && body.linkedProviders.every(link => link && typeof link.providerNo === 'string' && link.providerNo !== provider),
            body => { entry.parent.remove(); if (onSuccess) onSuccess(body, id); }, {});
        return false;
    }
    function actionEntry(id, kind) {
        const form = byId('forms_' + id);
        if (!form || documentId(form) !== id) return null;
        const previous = operations.get(id);
        if (previous && previous.state === 'queueRejected') {
            // The initial acknowledgement was already accepted. Only its queue phase
            // may be manually retried, with its original body, token and callback.
            if (previous.kind === kind && current(previous)) finishQueue(previous);
            else show(previous, 'queueUncertain');
            return null;
        }
        const entry = begin(form, id, null, false);
        if (entry) entry.kind = kind;
        return entry;
    }
    function acknowledge(formId, onSuccess) {
        const requestForm = byId(formId);
        const match = /^acknowledgeForm_([1-9][0-9]*)$/.exec(formId);
        if (!requestForm || !match) return false;
        const entry = actionEntry(match[1], 'acknowledge');
        if (!entry) return false;
        const data = new URLSearchParams(new FormData(requestForm));
        if (data.getAll('labType').length !== 1 || data.get('labType') !== 'DOC' || data.getAll('segmentID').length !== 1 || data.get('segmentID') !== entry.id || csrf(requestForm) !== entry.token) { show(entry, 'rejected'); return false; }
        data.set('ajaxcall', 'yes');
        data.set('CSRF-TOKEN', entry.token);
        entry.requestForm = requestForm; entry.requestFingerprint = fingerprint(requestForm);
        send(entry, data, body => body && body.success !== false && body.accepted !== false && Number.isSafeInteger(body.clearedCount) && body.clearedCount >= 0,
            onSuccess, {acknowledgement: true, endpoint: '/oscarMDS/UpdateStatus', queue: true});
        return false;
    }
    function file(id, onSuccess) {
        id = String(id);
        const entry = actionEntry(id, 'file');
        if (!entry) return false;
        const data = new URLSearchParams({method: 'fileLabAjax', flaggedLabId: id, labType: 'DOC', 'CSRF-TOKEN': entry.token});
        // DOC FileLabs confirms provider filing AND queue disposition in one transaction.
        send(entry, data, () => true, onSuccess, {endpoint: '/oscarMDS/FileLabs'});
        return false;
    }
    root.addEventListener('pagehide', function () {
        departed = true;
        epoch += 1;
        for (const entry of operations.values()) if (entry.state === 'pending' || entry.state === 'queuePending') show(entry, entry.metadataAccepted ? 'queueUncertain' : 'uncertain');
    });
    // A bfcache restore is not evidence that a request was rejected: operations from
    // before departure keep their uncertain state (epoch no longer matches), but the
    // restored page must accept new, unrelated actions instead of refusing them all.
    root.addEventListener('pageshow', function (event) {
        if (event && event.persisted) departed = false;
    });
    root.CarlosDocumentMetadata = {save, unlink, acknowledge, file};
}(window));
