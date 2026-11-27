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
    // The portal's fixed vocabularies (urgency, appointment type). Only values taken from these
    // lists are kept for a retry.
    const URGENCIES = ['routine', 'soon', 'as_soon_as_possible'];
    const BOOKING_KINDS = ['follow_up', 'annual_exam', 'lab_review'];
    const choice = (options, value) => options.find(option => option === value);
    // The provider list is built by the server for the chosen window, but a retry kept in this
    // browser's storage is checked again before it is reused: a provider number the action accepts.
    const PROVIDER_NUMBER = /^[A-Za-z0-9-]{1,6}$/;
    const VISIT_MODES = ['in_person', 'phone', 'video'];
    // The reasons the action refuses an offer with before anything reaches the portal.
    const OFFER_REFUSALS = ['no_open_times', 'offering_off'];
    const WINDOW_DEBOUNCE_MS = 400;
    const wholeNumber = (value, min, max) => {
        const text = String(value).trim();
        const number = Number(text);
        return /^\d{1,3}$/.test(text) && Number.isInteger(number) && number >= min && number <= max ? number : null;
    };
    // The offer kept with a retry: the provider and the window it was first sent with, so that a
    // retry sends the same (the action gives a retried operation the times it already offered).
    function validOffer(offer) {
        return !!offer && typeof offer === 'object' && PROVIDER_NUMBER.test(String(offer.provider))
            && wholeNumber(offer.after, 0, 365) !== null && wholeNumber(offer.within, 1, 92) !== null
            && wholeNumber(offer.count, 1, 8) !== null;
    }
    // What the panel says about offering open times; anything that is not that shape means "not offered".
    function parseOffering(value) {
        const valid = !!value && typeof value === 'object' && typeof value.mayOffer === 'boolean'
            && typeof value.codesConfigured === 'boolean' && VISIT_MODES.includes(value.visitMode)
            && [value.maxAfterDays, value.maxWithinDays, value.maxCount].every(limit =>
                Number.isInteger(limit) && limit > 0 && limit <= 366);
        return valid ? value : null;
    }
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
        // How many open times the last request was sent with, for the "sent" message that follows.
        let sentTimes = 0;
        const text = key => key === 'offer.sentTimes'
            ? message(key).replace('{count}', String(sentTimes)) : message(key);
        const status = (...keys) => {
            role('status').textContent = keys.flat().filter(Boolean).map(text).join(' ');
        };
        const create = role('create');
        const send = role('send');
        const urgency = role('urgency');
        const type = role('appointmentType');
        // Offering open times with the request (#3850). Absent when the user may not create.
        const offerGroup = role('offer');
        const offerToggle = role('offerToggle');
        const offerNote = role('offerNote');
        const offerFields = role('offerFields');
        const offerProvider = role('offerProvider');
        const offerAfter = role('offerAfter');
        const offerWithin = role('offerWithin');
        const offerCount = role('offerCount');
        const offerMode = role('offerMode');
        const key = 'portal.booking.pending:' + root.dataset.actor + ':' + root.dataset.patient;
        // What the panel said about offering, and the provider list for the window last asked for.
        let offering = null;
        let providersRequest = 0;
        let providersState = '';
        let windowTimer = null;
        let pending = null;
        let busy = false;
        let eligible = false;
        let storageReady = false;
        let mayWithdraw = false;
        // For the box on the page: the last confirmed list, and what it must say beside it.
        let shownPrompts = null;
        let accountInactive = false;
        let withdrawFailed = false;
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
                        || !URGENCIES.includes(pending.urgency)
                        || !BOOKING_KINDS.includes(pending.appointmentType)
                        || (pending.offer !== undefined && !validOffer(pending.offer))) {
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
            updateBox(matches);
            if (!create) { return; }
            create.hidden = !eligible || !matches;
            if (pending) {
                urgency.value = pending.urgency;
                type.value = pending.appointmentType;
            }
            urgency.disabled = type.disabled = busy || !!pending;
            updateOffer();
            send.disabled = busy || !eligible || !storageReady || (offerOn() && !offerProvider.value);
            send.textContent = message(pending ? 'retry' : 'send');
        }
        // Whether the request will carry open times: the box is on, and offering is possible.
        function offerOn() {
            return !!offerToggle && offerToggle.checked && !offerToggle.disabled;
        }
        function setOfferValues(offer) {
            offerAfter.value = String(offer.after);
            offerWithin.value = String(offer.within);
            offerCount.value = String(offer.count);
            if (!Array.from(offerProvider.options).some(option => option.value === offer.provider)) {
                // A retry from before this page loaded: the provider is shown by its number until
                // the list is read again (a name is never taken from storage).
                offerProvider.append(new Option(offer.provider, offer.provider));
            }
            offerProvider.value = offer.provider;
        }
        // The offer controls follow what the panel said, the retry kept in storage, and the list.
        function updateOffer() {
            if (!offerGroup) { return; }
            offerGroup.hidden = !offering;
            if (!offering) { return; }
            if (pending) {
                offerToggle.checked = !!pending.offer;
                if (pending.offer) { setOfferValues(pending.offer); }
            }
            const possible = offering.mayOffer && offering.codesConfigured;
            offerToggle.disabled = busy || !!pending || !possible;
            if (!possible) { offerToggle.checked = false; }
            offerFields.hidden = !offerToggle.checked;
            [offerProvider, offerAfter, offerWithin, offerCount].forEach(field => {
                field.disabled = busy || !!pending;
            });
            const noteKey = !offering.codesConfigured ? 'offer.off' : !offering.mayOffer ? 'offer.noSchedule'
                : offerToggle.checked ? providersState : '';
            offerNote.textContent = noteKey ? message(noteKey) : '';
            offerNote.hidden = !noteKey;
            offerMode.textContent = message('offer.modeLabel') + ' ' + message('offer.mode.' + offering.visitMode);
        }
        // The providers with bookable time in the window, asked of the server (never built here).
        async function loadProviders() {
            if (!offerOn() || !offering) { return; }
            const after = wholeNumber(offerAfter.value, 0, offering.maxAfterDays);
            const within = wholeNumber(offerWithin.value, 1, offering.maxWithinDays);
            // A window that is not valid is reported by the field itself when the request is sent.
            if (after === null || within === null) { return; }
            const request = ++providersRequest;
            providersState = 'offer.loadingProviders';
            update();
            try {
                const body = await post({ method: 'providers', offerAfterDays: String(after),
                    offerWithinDays: String(within) });
                if (request !== providersRequest) { return; }
                if (!Array.isArray(body.providers) || !body.providers.every(provider =>
                        provider && PROVIDER_NUMBER.test(String(provider.providerNo))
                        && typeof provider.name === 'string')) {
                    throw new Error('invalid provider list');
                }
                const previous = offerProvider.value;
                offerProvider.replaceChildren(new Option(message('offer.providerChoose'), ''));
                body.providers.forEach(provider => offerProvider.append(new Option(provider.name, provider.providerNo)));
                if (previous && body.providers.some(provider => provider.providerNo === previous)) {
                    offerProvider.value = previous;
                }
                providersState = body.providers.length ? ''
                    : body.codesConfigured === false ? 'offer.off' : 'offer.noProviders';
            } catch (_) {
                if (request === providersRequest) { providersState = 'offer.providersFailed'; }
            } finally { if (request === providersRequest) { update(); } }
        }
        // The parameters of a create request for a kept request (its offer, if it has one, flattened).
        function createParams(request) {
            const params = { operationId: request.operationId, urgency: request.urgency,
                appointmentType: request.appointmentType };
            if (request.offer) {
                params.offerFrom = request.offer.provider;
                params.offerAfterDays = String(request.offer.after);
                params.offerWithinDays = String(request.offer.within);
                params.offerCount = String(request.offer.count);
            }
            return params;
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
                    // The action says why it refused an offer (no open time, offering not set up).
                    try {
                        const refusal = await response.json();
                        if (typeof refusal.reason === 'string') { failure.reason = refusal.reason; }
                    } catch (_) { /* a refusal without a body has no reason to show */ }
                    throw failure;
                }
                const body = await response.json();
                if (body.ok !== true) { throw new Error('unconfirmed response'); }
                return body;
            } finally { clearTimeout(timer); }
        }
        const validPrompt = prompt => prompt && Number.isSafeInteger(prompt.id) && prompt.id > 0
            && URGENCIES.includes(prompt.urgency)
            && BOOKING_KINDS.includes(prompt.appointmentType)
            && ['sent', 'read', 'choice_pending', 'booked', 'declined_all', 'withdrawn', 'expired'].includes(prompt.state);
        const OPEN_STATES = ['sent', 'read', 'choice_pending'];
        function promptDate(prompt, options) {
            const date = new Date(prompt.createdAt);
            return Number.isNaN(date.getTime()) ? '' : date.toLocaleString(undefined, options);
        }
        const DATE_AND_TIME = { dateStyle: 'medium', timeStyle: 'short' };
        const DATE_ONLY = { dateStyle: 'medium' };
        function promptState(prompt) {
            // A request in the read state says "Read" once, not "Read · Read".
            if (prompt.state === 'read') { return message('state.read'); }
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
            const type = document.createElement('b');
            type.textContent = message(last.appointmentType);
            summary.replaceChildren(message('last') + ' ', type, ' · ' + [message(last.urgency),
                promptDate(last, DATE_ONLY), promptState(last)].filter(Boolean).join(' · '));
            openCount.textContent = message('openCount')
                .replace('{count}', String(prompts.filter(prompt => OPEN_STATES.includes(prompt.state)).length));
            openCount.hidden = false;
        }
        // The box never shows one patient's requests under another: after a patient change on the
        // appointment it says so, as the dialog does. Otherwise it also says what the dialog would:
        // a send still unconfirmed, a failed withdrawal, an inactive account, or no session storage.
        function updateBox(matches) {
            const note = role('note');
            if (!matches) {
                role('summary').textContent = message('patientChanged');
                role('openCount').hidden = true;
                note.hidden = true;
                return;
            }
            if (shownPrompts) { summarize(shownPrompts); }
            const noteKey = pending ? 'uncertain' : withdrawFailed ? 'withdrawFailed'
                : accountInactive ? 'inactive' : storageReady ? null : 'storage';
            note.textContent = noteKey ? message(noteKey) : '';
            note.hidden = !noteKey;
        }
        function render(prompts) {
            role('prompts').replaceChildren();
            shownPrompts = prompts;
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
                    cell(promptState(prompt)), cell(promptDate(prompt, DATE_AND_TIME)));
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
                offering = parseOffering(body.offering);
                if (offering && offerAfter) {
                    offerAfter.max = String(offering.maxAfterDays);
                    offerWithin.max = String(offering.maxWithinDays);
                    offerCount.max = String(offering.maxCount);
                }
                mayWithdraw = body.mayWithdraw;
                accountInactive = body.mayCreate && !eligible;
                withdrawFailed = false;
                render(body.prompts);
                const inactive = accountInactive ? 'inactive' : null;
                if (pending) {
                    // The unconfirmed request stays in view, with the reason it cannot be retried now.
                    status(lead, 'uncertain', inactive);
                } else {
                    // lead: what just happened (sent, withdrawn, not sent), then the account state.
                    status(lead, inactive || (lead ? null : storageReady ? 'ready' : 'storage'));
                }
            } catch (_) {
                eligible = false; mayWithdraw = false; accountInactive = false; shownPrompts = null; offering = null;
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
                    // The stored values are the list entries themselves, never the raw field values.
                    const chosenUrgency = choice(URGENCIES, urgency.value);
                    const chosenType = choice(BOOKING_KINDS, type.value);
                    if (!chosenUrgency || !chosenType) { update(); return; }
                    const request = { operationId: newOperationId(), urgency: chosenUrgency, appointmentType: chosenType };
                    if (offerOn()) {
                        // The window and count are whole numbers inside the limits the panel gave (the
                        // fields say what is wrong); the provider is one the server listed.
                        const offer = { provider: offerProvider.value,
                            after: wholeNumber(offerAfter.value, 0, offering.maxAfterDays),
                            within: wholeNumber(offerWithin.value, 1, offering.maxWithinDays),
                            count: wholeNumber(offerCount.value, 1, offering.maxCount) };
                        const bad = [[offerAfter, offer.after], [offerWithin, offer.within], [offerCount, offer.count]]
                            .find(([, value]) => value === null);
                        if (bad) { bad[0].reportValidity(); bad[0].focus(); update(); return; }
                        if (!PROVIDER_NUMBER.test(offer.provider)) { offerProvider.focus(); update(); return; }
                        request.offer = offer;
                    }
                    sessionStorage.setItem(key, JSON.stringify(request));
                    pending = request;
                }
            } catch (_) { storageReady = false; status('storage'); update(); return; }
            busy = true; update(); status('sending');
            let confirmed = false;
            let refused = false;
            // What to say when the action refused before anything reached the portal.
            let refusalKey = 'notSent';
            try {
                const body = await post(Object.assign({ method: 'create' }, createParams(pending)));
                if (!validPrompt(body.prompt) || typeof body.created !== 'boolean'
                        || body.prompt.urgency !== pending.urgency
                        || body.prompt.appointmentType !== pending.appointmentType) {
                    throw new Error('invalid confirmation');
                }
                sessionStorage.removeItem(key);
                sentTimes = pending.offer && Number.isInteger(body.offeredTimes) && body.offeredTimes > 0
                    ? body.offeredTimes : 0;
                pending = null; confirmed = true;
                status('sent', sentTimes > 0 ? 'offer.sentTimes' : null);
            } catch (failure) {
                // No open time in the window, or offering not set up: nothing was stored, so the
                // retry identity goes and staff can change the window and send again.
                const offerRefused = failure.status === 409 && OFFER_REFUSALS.includes(failure.reason);
                if (offerRefused) {
                    refusalKey = failure.reason === 'no_open_times' ? 'offer.noOpenTimes' : 'offer.off';
                }
                refused = offerRefused || failure.notSent === true || DEFINITE_REFUSALS.includes(failure.status);
                if (refused && fresh) {
                    try { sessionStorage.removeItem(key); } catch (_) { /* nothing left to retry */ }
                    pending = null;
                } else {
                    // Kept: this or an earlier attempt with it may have reached the portal.
                    status('uncertain');
                }
            }
            finally { busy = false; update(); }
            if (confirmed) { await refresh(sentTimes > 0 ? ['sent', 'offer.sentTimes'] : 'sent'); }
            // A refusal says so, then shows the refreshed state (for example, not on the portal).
            if (refused) { await refresh(pending ? null : refusalKey); }
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
            } catch (_) { withdrawFailed = true; status('withdrawFailed'); }
            finally { busy = false; update(); }
            if (confirmed) { await refresh('withdrawn'); }
            // A failed withdrawal stays disabled until staff refresh the confirmed status.
        }
        if (send) { send.addEventListener('click', submit); }
        if (offerGroup) {
            // Turning offering on asks the server which providers have bookable time in the window; a
            // change to the window asks again, once the typing has paused.
            offerToggle.addEventListener('change', () => { update(); if (offerToggle.checked) { loadProviders(); } });
            offerProvider.addEventListener('change', update);
            [offerAfter, offerWithin].forEach(input => input.addEventListener('input', () => {
                clearTimeout(windowTimer);
                windowTimer = setTimeout(loadProviders, WINDOW_DEBOUNCE_MS);
            }));
        }
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
            if (typeof dialog.close === 'function') { dialog.close(); } else { dialog.removeAttribute('open'); open.focus(); }
        }));
        // Escape closes a modal dialog without a click; either way, focus goes back to the box.
        dialog.addEventListener('close', () => open.focus());
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
