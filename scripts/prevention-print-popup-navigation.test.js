/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { EventEmitter } = require('node:events');
const h = require('./lib/playwright-harness');
const { workflow } = require('./prevention-print-playwright-checks');

const PICKER_STEP = 'Enter in the vaccine box opens the top suggestion and never prints';
const DEFAULT_BASE = 'https://127.0.0.1:8443/carlos';
function destination(base = DEFAULT_BASE, changes = {}) {
  const url = new URL(h.appUrl(new URL(base), '/prevention/ViewAddPreventionData'));
  for (const [key, value] of Object.entries({ prevention: 'Tdap', demographic_no: '42', brandName: 'ADACEL', ...changes })) {
    url.searchParams.set(key, value);
  }
  return url.href;
}

// Exercise the real workflow's native keyboard step. Other steps and SQL are
// inert setup mocks, so these tests cannot launch a browser or touch a database.
function session({ initialUrl = '', targetUrl = destination(), base = DEFAULT_BASE } = {}) {
  const calls = [];
  const context = new EventEmitter();
  const chart = { locator: () => ({ first: () => ({}) }) };
  const index = new EventEmitter();
  const editor = new EventEmitter();
  const pages = [chart, index];
  let currentUrl = initialUrl;
  let input = '';
  let armed = false;
  context.pages = () => [...pages];
  index.waitForEvent = (event, options) => {
    assert.equal(event, 'popup'); assert.deepEqual(options, { timeout: 20000 });
    armed = true; calls.push(['popup-armed']);
    return new Promise(resolve => index.once(event, resolve));
  };
  editor.url = () => { calls.push(['editor-url', currentUrl]); return currentUrl; };
  editor.waitForLoadState = async state => { calls.push(['load-state', state]); };
  editor.waitForURL = async (predicate, options) => {
    calls.push(['destination-wait', options]);
    assert.deepEqual(options, { timeout: 20000, waitUntil: 'domcontentloaded' });
    let url;
    try { url = new URL(targetUrl); } catch { throw new Error('Native editor did not commit a destination'); }
    if (!predicate(url)) throw new Error('Native editor did not commit the expected origin and context route');
    currentUrl = targetUrl;
    calls.push(['destination-committed', currentUrl]);
  };
  editor.goto = () => { throw new Error('The keyboard check must not navigate to an invented editor URL'); };
  const box = {
    async click() { calls.push(['box-click']); },
    async pressSequentially(text) { input += text; calls.push(['typed', text]); },
    async fill(text) { input = text; calls.push(['fill', text]); },
    async evaluate(callback) {
      assert.match(String(callback), /isComposing:\s*true/);
      calls.push(['ime-confirmation']);
    },
    async press(key) {
      assert.equal(key, 'Enter'); calls.push(['native-key', key, input]);
      if (input === 'adac') {
        assert.equal(armed, true, 'The popup listener must precede native Enter');
        pages.push(editor); index.emit('popup', editor);
      }
    },
  };
  index.locator = selector => {
    if (selector === '#immunization') return box;
    assert.equal(selector, '#immunization_choices .ac-item');
    return { first: () => ({ async waitFor() { assert.equal(input, 'adac'); calls.push(['suggestion-visible']); } }) };
  };
  index.waitForTimeout = async () => {};
  index.isClosed = () => false;
  index.url = () => h.appUrl(new URL(base), '/prevention/ViewPreventionIndex?demographic_no=42');
  const s = {
    config: { baseUrl: new URL(base) }, patient: '42', marker: 'FAKE-PW-NAVIGATION', provider: '999998', context,
    recorder: h.createRecorder(), sql: { value: () => '7001', execute() {} }, cleanup() {},
    chart: async () => chart, popup: async () => index,
    async step(label, body) { if (label === PICKER_STEP) await body(); },
  };
  return { s, calls, editor };
}

for (const initialUrl of ['', 'about:blank']) {
  test(`native Enter waits past the initial ${initialUrl || 'empty URL'} document`, async () => {
    const f = session({ initialUrl });
    await workflow(f.s);
    assert.deepEqual(f.calls.filter(c => c[0] === 'native-key'), [['native-key', 'Enter', 'zzqx'], ['native-key', 'Enter', 'adac']]);
    assert.equal(f.calls.filter(c => c[0] === 'ime-confirmation').length, 1);
    assert.equal(f.calls.filter(c => c[0] === 'destination-wait').length, 1);
    const committed = f.calls.findIndex(c => c[0] === 'destination-committed');
    const read = f.calls.findIndex(c => c[0] === 'editor-url');
    assert.ok(committed >= 0 && read > committed, 'The native destination must commit before URL assertions');
    assert.equal(f.s.recorder.badResponses.length, 0);
  });
}

for (const base of ['https://127.0.0.1:8443/nested/carlos', 'https://127.0.0.1:8443']) {
  test(`the native editor preserves application context ${new URL(base).pathname}`, async () => {
    const targetUrl = destination(base);
    await workflow(session({ base, targetUrl }).s);
  });
}

test('an already committed correct native popup remains accepted', async () => {
  await workflow(session({ initialUrl: destination() }).s);
});

for (const [name, targetUrl] of [
  ['another origin', destination().replace('127.0.0.1', 'example.invalid')],
  ['another port', destination().replace(':8443', ':9443')],
  ['a different application context', destination().replace('/carlos/', '/other/')],
  ['a login redirect', 'https://127.0.0.1:8443/carlos/login'],
  ['a popup that stays blank', 'about:blank'],
  ['a popup that never acquires a URL', ''],
]) {
  test(`native Enter refuses ${name}`, async () => {
    await assert.rejects(workflow(session({ initialUrl: targetUrl, targetUrl }).s));
  });
}

for (const [name, changes] of [
  ['another patient', { demographic_no: '43' }],
  ['a different prevention', { prevention: 'Td' }],
  ['a different brand', { brandName: 'BOOSTRIX' }],
]) {
  test(`the committed native editor refuses ${name}`, async () => {
    const targetUrl = destination(DEFAULT_BASE, changes);
    await assert.rejects(workflow(session({ initialUrl: targetUrl, targetUrl }).s), /Enter opened/);
  });
}
