/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// The document page's "Send to myCarlos" dialog (mycarlos-send-dialog.js over mycarlos-send.js), run
// as shipped on a deterministic DOM: run with `node --test scripts/mycarlos-send-dialog.test.js`.
const test = require('node:test');
const assert = require('node:assert/strict');
const {Element, FakeDocument, sendRoot, load, tick, delivery, state} = require('./lib/mycarlos-send-fake-dom');

const MINTED = delivery(500).deliveryId;
const SCRIPTS = ['mycarlos-send.js', 'mycarlos-send-dialog.js'];

function page({documents = ['44'], scripts = SCRIPTS, context = '/FAKE'} = {}) {
    const document = new FakeDocument();
    const dialogs = {};
    const openers = {};
    for (const id of documents) {
        const {root, parts} = sendRoot(document, {dialog: true, data: {document: id, context}, token: ''});
        document.ids['mycarlos-send-dialog-' + id] = root;
        const opener = new Element(document, 'a');
        opener.dataset.mycarlosDialog = 'mycarlos-send-dialog-' + id;
        dialogs[id] = {root, parts};
        openers[id] = opener;
    }
    // The page's token, as CSRFGuard puts in the document page's own forms.
    const pageToken = new Element(document, 'input'); pageToken.name = 'CSRF-TOKEN'; pageToken.value = 'FAKE-CSRF';
    document.pageInputs.push(pageToken);
    const {window, calls} = load(document, {scripts});
    return {document, dialogs, openers, window, calls};
}

async function openRead(view, id = '44', minted = MINTED, rows = []) {
    const event = view.openers[id].click();
    assert.equal(event.defaultPrevented, true, 'the link does not open the standalone page');
    await tick();
    const read = view.calls.at(-1);
    view.calls.at(-1).resolve(state(rows, {deliveryId: minted})); await tick(); await tick();
    return read;
}

test('opens as a modal dialog, reads once, takes the minted id and moves focus to the device', async () => {
    const view = page();
    const {root, parts} = view.dialogs['44'];
    const read = await openRead(view);
    assert.equal(root.open, true);
    assert.equal(view.calls.length, 1);
    const url = new URL(read.url);
    assert.equal(url.origin, 'https://localhost');
    assert.equal(url.pathname, '/FAKE/demographic/PortalDelivery');
    assert.equal(read.options.method, 'GET');
    assert.equal(url.searchParams.get('method'), 'read');
    assert.equal(url.searchParams.has('deliveryId'), false, 'the server names the id');
    assert.equal(root.dataset.delivery, MINTED);
    assert.equal(parts['document-title'].textContent, 'FAKE Document');
    assert.equal(view.document.focused, parts.device);
    assert.equal(view.window.history.replaced.length, 0, 'the document page URL is never rewritten');
});

test('Close and Esc wait while a send runs, until its result shows', async () => {
    const view = page();
    const {root, parts} = view.dialogs['44'];
    await openRead(view);
    parts.form.dispatch('submit'); await tick();
    const sent = new URLSearchParams(view.calls[1].options.body);
    assert.equal(view.calls[1].options.method, 'POST');
    assert.equal(sent.get('method'), 'send');
    assert.equal(sent.get('deliveryId'), MINTED);
    assert.equal(sent.get('deviceKeyId'), 'FAKE-current-device');
    assert.equal(sent.get('CSRF-TOKEN'), 'FAKE-CSRF', 'a filled token on the page is used');
    assert.equal(parts.message.textContent, 'sending');
    assert.equal(parts.close.disabled, true);
    parts.close.disabled = false; parts.close.click();
    assert.equal(root.open, true, 'Close does not close while sending');
    assert.equal(root.pressEscape(), false, 'Esc does not close while sending');
    assert.equal(root.open, true);
    view.calls[1].resolve(state([{...delivery(500, 'staged'), deviceKeyId: 'FAKE-current-device'}])); await tick();
    assert.equal(parts.message.textContent, 'sent');
    assert.equal(parts.close.disabled, false);
    assert.equal(root.pressEscape(), true);
    assert.equal(root.open, false);
    assert.equal(view.document.focused, view.openers['44'], 'focus returns to the Send button');
});

test('a failed send shows what went wrong, then lets the dialog close', async () => {
    const view = page();
    const {root, parts} = view.dialogs['44'];
    await openRead(view);
    parts.form.dispatch('submit'); await tick();
    view.calls[1].resolve({ok: false, message: 'FAKE portal refused'}, 409); await tick();
    assert.equal(parts.message.textContent, 'FAKE portal refused');
    assert.equal(parts.close.disabled, false);
    parts.close.click();
    assert.equal(root.open, false);
});

test('reading or checking status does not hold the dialog open', async () => {
    const view = page();
    const {root, parts} = view.dialogs['44'];
    await openRead(view);
    parts.check.click(); await tick();
    assert.equal(parts.close.disabled, false);
    assert.equal(root.pressEscape(), true);
});

test('with the switch flipped, Close works at once even while sending', async () => {
    const flipped = {name: 'mycarlos-send-dialog.js',
        transform: source => source.replace('const CLOSE_WHILE_SENDING = false;', 'const CLOSE_WHILE_SENDING = true;')};
    const view = page({scripts: ['mycarlos-send.js', flipped]});
    assert.equal(view.window.CarlosMyCarlosSendDialog.CLOSE_WHILE_SENDING, true);
    const {root, parts} = view.dialogs['44'];
    await openRead(view);
    parts.form.dispatch('submit'); await tick();
    assert.equal(parts.close.disabled, false);
    assert.equal(root.pressEscape(), true);
});

test('ships with Close blocked while sending, as Ben decided', () => {
    assert.equal(page().window.CarlosMyCarlosSendDialog.CLOSE_WHILE_SENDING, false);
});

test('reopening reads again and keeps the same operation id; a new copy takes a new one', async () => {
    const view = page();
    const {root, parts} = view.dialogs['44'];
    await openRead(view);
    parts.form.dispatch('submit'); await tick();
    const queued = [{...delivery(500, 'staged'), deviceKeyId: 'FAKE-current-device'}];
    view.calls[1].resolve(state(queued)); await tick();
    assert.equal(parts.send.disabled, true, 'the same copy is not sent twice');
    assert.equal(parts.new.hidden, false, 'a new copy is offered');
    parts.close.click();
    await openRead(view, '44', delivery(501).deliveryId, queued);
    assert.equal(root.dataset.delivery, MINTED, 'reopening keeps the id');
    parts.new.click(); await tick();
    assert.equal(new URL(view.calls[3].url).searchParams.get('method'), 'read');
    view.calls[3].resolve(state(queued, {deliveryId: delivery(502).deliveryId})); await tick();
    assert.equal(root.dataset.delivery, delivery(502).deliveryId);
    assert.equal(parts.send.disabled, false);
    assert.equal(parts.new.hidden, true);
});

test('two documents on one inbox page each send their own document', async () => {
    const view = page({documents: ['44', '45']});
    await openRead(view, '44');
    view.dialogs['44'].parts.close.click();
    await openRead(view, '45', delivery(600).deliveryId);
    view.dialogs['45'].parts.form.dispatch('submit'); await tick();
    const sent = new URLSearchParams(view.calls.at(-1).options.body);
    assert.equal(sent.get('documentId'), '45');
    assert.equal(sent.get('deliveryId'), delivery(600).deliveryId);
    assert.equal(view.dialogs['44'].root.open, false);
});

test('loaded twice, as the inbox can, it still opens once on one click', async () => {
    const view = page({scripts: [...SCRIPTS, ...SCRIPTS]});
    assert.equal(view.document.clickHandlers.length, 1);
    view.openers['44'].click(); await tick();
    assert.equal(view.calls.length, 1);
});

test('a context that is not a plain path is refused, and nothing is fetched', async () => {
    const view = page({context: '\\/carlos'});
    const {parts} = view.dialogs['44'];
    view.openers['44'].click(); await tick(); await tick();
    assert.equal(view.calls.length, 0);
    assert.equal(parts.message.textContent, 'error');
});

test('a link with no dialog on the page is left to open the standalone page', () => {
    const view = page();
    const stray = new Element(view.document, 'a');
    stray.dataset.mycarlosDialog = 'mycarlos-send-dialog-missing';
    assert.equal(stray.click().defaultPrevented, false);
    assert.equal(view.calls.length, 0);
});
