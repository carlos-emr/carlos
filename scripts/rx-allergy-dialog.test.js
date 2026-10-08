/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// Rx "Add an Allergy" dialogue failure handling (#3355, #3488): a refused dialogue request is
// reported in the page instead of spinning silently, an answer without the expected fragment never
// replaces the dialogue container with nothing, and a save the server does not confirm keeps the
// dialogue and every entered value on screen with a retry that cannot double-record the allergy.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const dialogModule = require(path.join(__dirname, '../src/main/webapp/share/javascript/rx-allergy-dialog.js'));

const FIELDS = [
    ['CSRF-TOKEN', 'TOKEN-123'],
    ['formDemographicNo', '7'],
    ['demographicNo', '7'],
    ['reactionDescription', 'hives after first dose'],
    ['ID', '44452'],
    ['name', 'PENICILLINS'],
    ['type', '10'],
    ['startDate', '2024-01-15'],
];

function fakeElement(extra = {}) {
    return Object.assign({
        textContent: '',
        style: {},
        attributes: {},
        setAttribute(name, value) { this.attributes[name] = String(value); },
        getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null; },
        removeAttribute(name) { delete this.attributes[name]; },
    }, extra);
}

function fakePage({ fetchImpl, withStatusRegion = true } = {}) {
    const listeners = [];
    const assigned = [];
    const fetchCalls = [];
    const submitButton = fakeElement({ disabled: false, focused: 0, focus() { this.focused++; } });
    const statusRegion = fakeElement({ className: 'allergySaveStatus' });
    const inserted = [];
    const form = fakeElement({
        id: 'RxAddAllergyForm',
        action: 'https://emr.example/carlos/rx/addAllergy2',
        firstChild: null,
        querySelector(selector) {
            if (selector === '.allergySaveStatus') return withStatusRegion ? statusRegion : (inserted[0] || null);
            return null;
        },
        querySelectorAll(selector) {
            assert.match(selector, /type="submit"/);
            return [submitButton];
        },
        insertBefore(node) { inserted.push(node); },
    });
    const requestRegion = fakeElement({ id: 'allergyRequestStatus' });
    const win = {
        document: {
            body: fakeElement({ firstChild: null, insertBefore() {} }),
            createElement() { return fakeElement(); },
            addEventListener(type, listener, capture) { listeners.push({ type, listener, capture }); },
            getElementById(id) { return id === 'allergyRequestStatus' ? requestRegion : null; },
        },
        FormData: class {
            constructor(owner, submitter) {
                assert.equal(owner, form);
                this.entries = FIELDS.slice();
                if (submitter) this.entries.push(['submit', 'Add Allergy']);
            }
            [Symbol.iterator]() { return this.entries[Symbol.iterator](); }
        },
        URLSearchParams,
        fetch(url, options) {
            fetchCalls.push({ url, options });
            return fetchImpl(url, options, fetchCalls.length);
        },
        location: { assign(url) { assigned.push(url); } },
    };
    return { win, form, submitButton, statusRegion, requestRegion, listeners, assigned, fetchCalls, inserted };
}

function response(status, url) {
    return { ok: status >= 200 && status < 300, status, url, text: () => Promise.resolve('<html></html>') };
}

test('a confirmed save posts the form body unchanged and only then opens the allergy list', async () => {
    const listUrl = 'https://emr.example/carlos/rx/showAllergy?demographicNo=7';
    const page = fakePage({ fetchImpl: () => Promise.resolve(response(200, listUrl)) });
    const dialog = dialogModule.create(page.win);

    const outcome = await dialog.save(page.form, page.submitButton);

    assert.equal(outcome, 'saved');
    assert.deepEqual(page.assigned, [listUrl]);
    assert.equal(page.fetchCalls.length, 1);
    const { url, options } = page.fetchCalls[0];
    assert.equal(url, page.form.action);
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.redirect, 'follow');
    const body = new URLSearchParams(options.body);
    // The CSRF token and both halves of the patient binding travel exactly as the classic post sent them.
    assert.equal(body.get('CSRF-TOKEN'), 'TOKEN-123');
    assert.equal(body.get('formDemographicNo'), '7');
    assert.equal(body.get('demographicNo'), '7');
    assert.equal(body.get('reactionDescription'), 'hives after first dose');
    assert.equal(body.get('submit'), 'Add Allergy');
    assert.equal(page.submitButton.disabled, true, 'the button stays disabled while the list loads');
    assert.equal(page.statusRegion.textContent, '');
});

test('a second submit while the save is out is ignored, so a double click records one allergy', async () => {
    let resolveFetch;
    const page = fakePage({ fetchImpl: () => new Promise((resolve) => { resolveFetch = resolve; }) });
    const dialog = dialogModule.create(page.win);

    const first = dialog.save(page.form, page.submitButton);
    const second = await dialog.save(page.form, page.submitButton);

    assert.equal(second, 'ignored');
    assert.equal(page.fetchCalls.length, 1);
    assert.equal(page.form.getAttribute('aria-busy'), 'true');
    resolveFetch(response(200, '/carlos/rx/showAllergy?demographicNo=7'));
    assert.equal(await first, 'saved');
});

test('a refused save keeps the dialogue, says NOT saved, and re-enables a retry that saves once', async () => {
    const page = fakePage({
        fetchImpl: (url, options, call) => Promise.resolve(call === 1
            ? response(403, url)
            : response(200, '/carlos/rx/showAllergy?demographicNo=7')),
    });
    const dialog = dialogModule.create(page.win);

    assert.equal(await dialog.save(page.form, page.submitButton), 'failed');
    assert.deepEqual(page.assigned, [], 'a refused save must not leave the patient\'s page');
    assert.match(page.statusRegion.textContent, /^Allergy NOT saved: the server refused the request \(HTTP 403\)/);
    assert.match(page.statusRegion.textContent, /Your entries are still in the form below\./);
    assert.equal(page.statusRegion.style.display, 'block');
    assert.equal(page.submitButton.disabled, false);
    assert.equal(page.submitButton.focused, 1, 'keyboard focus returns to Add Allergy for the retry');
    assert.equal(page.form.getAttribute('data-allergy-saving'), null);

    assert.equal(await dialog.save(page.form, page.submitButton), 'saved');
    assert.equal(page.fetchCalls.length, 2);
    assert.deepEqual(page.assigned, ['/carlos/rx/showAllergy?demographicNo=7']);
    assert.equal(page.statusRegion.style.display, 'none', 'the retry clears the earlier failure');
});

for (const [label, fetchImpl, expected] of [
    ['an answer that is not the allergy list (the login page)',
        () => Promise.resolve(response(200, 'https://emr.example/carlos/index')),
        /could not be confirmed: the server answered with an unexpected page; your session may have ended/],
    ['a server error', (url) => Promise.resolve(response(500, url)), /could not be confirmed: the server answered HTTP 500/],
    ['no answer at all', () => Promise.reject(new TypeError('Failed to fetch')), /could not be confirmed: the server could not be reached/],
]) {
    test(`${label} is reported as unconfirmed, never as saved, and asks for a check before retrying`, async () => {
        const page = fakePage({ fetchImpl });
        const dialog = dialogModule.create(page.win);

        assert.equal(await dialog.save(page.form, page.submitButton), 'failed');
        assert.deepEqual(page.assigned, []);
        assert.match(page.statusRegion.textContent, expected);
        assert.match(page.statusRegion.textContent, /so it is not recorded twice/);
        assert.equal(page.submitButton.disabled, false);
    });
}

test('a form rendered without a status region gets one, announced as an alert', async () => {
    const page = fakePage({
        withStatusRegion: false,
        fetchImpl: (url) => Promise.resolve(response(403, url)),
    });
    const dialog = dialogModule.create(page.win);

    await dialog.save(page.form, page.submitButton);

    assert.equal(page.inserted.length, 1);
    assert.equal(page.inserted[0].getAttribute('role'), 'alert');
    assert.match(page.inserted[0].textContent, /Allergy NOT saved/);
});

function formListeners(page) {
    page.form.listeners = [];
    page.form.addEventListener = (type, listener, capture) => page.form.listeners.push({ type, listener, capture });
}

function submitEvent(target, page, defaultPrevented = false) {
    return {
        target, currentTarget: target, defaultPrevented, submitter: page.submitButton, prevented: false,
        preventDefault() { this.prevented = true; this.defaultPrevented = true; },
    };
}

test('the save is bound on the allergy form itself, from a capture-phase document listener', () => {
    const page = fakePage({ fetchImpl: () => new Promise(() => {}) });
    formListeners(page);
    const dialog = dialogModule.create(page.win);
    dialog.install();
    dialog.install();
    const documentListeners = page.listeners.filter((entry) => entry.type === 'submit');
    assert.equal(documentListeners.length, 1, 'installing twice must not double-bind');
    // The injected form sits inside the allergy search form, and Blink stops a nested form's submit
    // event at the enclosing form: only capture is guaranteed to reach the document.
    assert.equal(documentListeners[0].capture, true);
    const bind = documentListeners[0].listener;

    bind(submitEvent(fakeElement({ id: 'searchAllergy2', addEventListener: () => assert.fail('only the allergy form is bound') }), page));
    bind(submitEvent(page.form, page));
    bind(submitEvent(page.form, page));
    assert.equal(page.form.listeners.length, 1, 'each form element is bound once');
    assert.equal(page.form.listeners[0].capture, false,
        'target phase: after CSRFGuard\'s capture-phase hook has put the current token in the form');
    assert.equal(page.fetchCalls.length, 0, 'binding alone sends nothing');

    const onSubmit = page.form.listeners[0].listener;
    const cancelled = submitEvent(page.form, page, true);
    onSubmit(cancelled);
    assert.equal(page.fetchCalls.length, 0, 'a submission another handler cancelled is left alone');

    const allergy = submitEvent(page.form, page);
    onSubmit(allergy);
    assert.equal(allergy.prevented, true);
    assert.equal(page.fetchCalls.length, 1);
});

test('without fetch the allergy form is not taken over and posts the classic way, as before', () => {
    const page = fakePage({ fetchImpl: () => assert.fail('fetch must not be called') });
    formListeners(page);
    delete page.win.fetch;
    const dialog = dialogModule.create(page.win);
    dialog.install();
    const bind = page.listeners.find((entry) => entry.type === 'submit').listener;
    const allergy = submitEvent(page.form, page);

    bind(allergy);

    assert.equal(page.form.listeners.length, 0);
    assert.equal(allergy.prevented, false);
});

test('a failed dialogue request names the step and what it means for the chart, never echoing the URL', () => {
    const cases = [
        ['/carlos/rx/addReaction2', 403, /^The allergy details form could not be opened: the server refused the request \(HTTP 403\); your session may have ended\. Nothing was saved\.$/],
        ['/carlos/rx/addReaction2?ID=0&name=%3Cscript%3E', 200, /^The allergy details form could not be opened: the server answered with an unexpected page/],
        ['/carlos/rx/deleteAllergy2', 0, /^The allergy could not be inactivated: the server could not be reached\. Reload this page to check/],
        ['/carlos/rx/searchAllergy2', 500, /^The allergy search did not complete: the server answered HTTP 500\. Nothing was saved\.$/],
        ['/carlos/rx/unknown', 502, /^The allergy request did not complete: the server answered HTTP 502\. Reload this page/],
    ];
    for (const [url, status, expected] of cases) {
        const message = dialogModule.requestFailureMessage(url, status);
        assert.match(message, expected, url);
        assert.ok(!message.includes('script') && !message.includes('?'), `${url} leaked into the message`);
    }
});

test('reportRequestFailure fills the page region as text and clearRequestFailure hides it again', () => {
    const page = fakePage({ fetchImpl: () => assert.fail('no save expected') });
    const dialog = dialogModule.create(page.win);

    dialog.reportRequestFailure('/carlos/rx/addReaction2', 403);
    assert.match(page.requestRegion.textContent, /could not be opened/);
    assert.equal(page.requestRegion.style.display, 'block');

    dialog.clearRequestFailure();
    assert.equal(page.requestRegion.textContent, '');
    assert.equal(page.requestRegion.style.display, 'none');
});

// ShowAllergies2.jsp's own AJAX plumbing, run as the page defines it.
function loadAllergyPageScript() {
    const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/ShowAllergies2.jsp'), 'utf8');
    const start = source.indexOf('function paramHasNKDA(');
    const end = source.indexOf('//--> Check if search field is empty.', start);
    assert.ok(start > 0 && end > start, 'ShowAllergies2.jsp AJAX helpers not found');
    return source.slice(start, end)
        .replace(/<%--[\s\S]*?--%>/g, '')
        .replace(/\$\{ pageContext\.servletContext\.contextPath \}/g, '/carlos');
}

function allergyPageContext() {
    const calls = { ajax: [], replaced: [], removedLoader: 0, reports: [], cleared: 0, reloads: 0, alerts: [] };
    const selection = (selector) => ({
        selector,
        removeClass() { calls.removedLoader++; return this; },
        replaceWith(found) { calls.replaced.push({ selector, found }); return this; },
        bindActionEvents() {},
    });
    const $ = (selector) => selection(selector);
    $.ajax = (options) => calls.ajax.push(options);
    $.each = (items, fn) => items.forEach((item, i) => fn(i, item));
    const context = {
        $,
        jQuery: (selector, html) => ({ length: String(html).includes(`data-target="${selector}"`) ? 1 : 0, selector }),
        document: { forms: { searchAllergy2: { iNKDA: { value: '0' }, hasDrugAllergy: { value: 'false' } } } },
        location: { reload() { calls.reloads++; } },
        alert: (message) => calls.alerts.push(message),
        paramNKDA: 'name=No Known Drug Allergies',
        CarlosAllergyDialog: {
            reportRequestFailure(url, status) { calls.reports.push({ url, status }); },
            clearRequestFailure() { calls.cleared++; },
        },
    };
    context.window = context;
    vm.runInNewContext(loadAllergyPageScript(), context);
    return { context, calls };
}

test('the allergy page reports a refused dialogue request instead of spinning silently (#3355)', () => {
    const { context, calls } = allergyPageContext();

    context.sendSearchRequest('/carlos/rx/addReaction2', 'ID=44452&name=PENICILLINS&type=10', '#addAllergyDialogue');
    assert.equal(calls.ajax.length, 1);
    assert.equal(typeof calls.ajax[0].error, 'function', 'the dialogue request has no error handler');
    calls.ajax[0].error({ status: 403 });

    assert.deepEqual(calls.reports, [{ url: '/carlos/rx/addReaction2', status: 403 }]);
    assert.ok(calls.removedLoader > 0, 'the spinner was left turning');
    assert.deepEqual(calls.replaced, []);
});

test('an answer without the dialogue fragment is reported and never replaces the container with nothing', () => {
    const { context, calls } = allergyPageContext();

    context.sendSearchRequest('/carlos/rx/addReaction2', 'ID=0&type=0&name=X', '#addAllergyDialogue');
    calls.ajax[0].success('<html><body>Please log in</body></html>');

    assert.deepEqual(calls.replaced, [], 'replaceWith() with nothing would delete the dialogue container');
    assert.deepEqual(calls.reports, [{ url: '/carlos/rx/addReaction2', status: 200 }]);
});

test('a usable answer is rendered and clears an earlier failure; a failed inactivation does not reload', () => {
    const { context, calls } = allergyPageContext();

    context.sendSearchRequest('/carlos/rx/addReaction2', 'ID=0&type=0&name=X', '#addAllergyDialogue');
    calls.ajax[0].success('<td id="addAllergyDialogue" data-target="#addAllergyDialogue"></td>');
    assert.equal(calls.replaced.length, 1);
    assert.equal(calls.replaced[0].selector, '#addAllergyDialogue');
    assert.equal(calls.cleared, 1);
    assert.equal(calls.reloads, 0);

    context.sendSearchRequest('/carlos/rx/deleteAllergy2', 'ID=5&demographicNo=7&action=delete', '.Step1Text');
    calls.ajax[1].success('<html>login</html>');
    assert.equal(calls.reloads, 0, 'a reload would wipe the failure the clinician needs to see');
    assert.deepEqual(calls.reports.at(-1), { url: '/carlos/rx/deleteAllergy2', status: 200 });

    calls.ajax[1].success('<td class="Step1Text" data-target=".Step1Text"></td>');
    assert.equal(calls.reloads, 1);
});
