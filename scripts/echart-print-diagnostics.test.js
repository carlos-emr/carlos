/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createRequire } = require('node:module');
const test = require('node:test');
const vm = require('node:vm');
const { textPdf } = require('./lib/stored-pdf-documents');

const driver = path.join(__dirname, 'echart-print-playwright-checks.js');
const endpoint = 'http://127.0.0.1:8080/carlos/CaseManagementEntry';
const note = 'FAKE print regression body';
function load({ fileSystem } = {}) {
  const source = fs.readFileSync(process.env.ECHART_PRINT_REVIEW_SOURCE || driver, 'utf8');
  const boundary = source.indexOf('(async () => {');
  assert.ok(boundary > 0);
  const ownRequire = createRequire(driver);
  const context = vm.createContext({
    require: name => name === 'playwright' ? {} : name === 'fs' && fileSystem ? fileSystem : ownRequire(name),
    process: { env: {} }, console, URL, URLSearchParams, setTimeout, clearTimeout,
  });
  vm.runInContext(source.slice(0, boundary) + '\ndemographicNo="101";patientMarker="FAKEOWNEDMARKER";', context);
  context.check = vm.runInContext('() => typeof assertBrowserDiagnostics === "function"'
    + ' ? assertBrowserDiagnostics() : assert(badResponses.length === 0, "unexpected HTTP errors or dialogs")', context);
  return context;
}
function response({ status = 200, type = 'document', content = 'application/pdf', body = '', url = endpoint } = {}) {
  const request = { method: () => 'POST', url: () => url, postData: () => body, resourceType: () => type };
  return { status: () => status, url: () => url, request: () => request, headers: () => ({ 'content-type': content }) };
}

for (const [name, event, payload] of [
  ['uncaught error', 'pageerror', new Error('unbaselined chart failure')],
  ['severe console error', 'console', { type: () => 'error', text: () => 'unbaselined chart error', location: () => ({}) }],
  ['script request failure', 'requestfailed', { resourceType: () => 'script', url: () => `${endpoint}/broken.js`, failure: () => ({ errorText: 'net::ERR_FAILED' }) }],
  ['image request failure', 'requestfailed', { resourceType: () => 'image', url: () => `${endpoint}/broken.png`, failure: () => ({ errorText: 'net::ERR_FAILED' }) }],
  ['HTML script response', 'response', response({ type: 'script', content: 'text/html' })],
  ['HTTP error including common favicon exemption', 'response', response({ status: 404, url: 'http://127.0.0.1:8080/favicon.ico' })],
]) test(`standalone print rejects ${name}`, async () => {
  const box = load();
  const page = new EventEmitter();
  box.wirePage(page, 'owned-print');
  page.emit(event, payload);
  assert.throws(box.check, /browser reported|unexpected HTTP/);
});

test('standalone print retains one explicit dialog handler and rejects unknown dialogs', async () => {
  const box = load();
  const page = new EventEmitter();
  box.wirePage(page, 'owned-print');
  assert.equal(page.listenerCount('dialog'), 1);
  let accepts = 0;
  const dialog = message => ({ type: () => 'confirm', message: () => message, accept: async () => { accepts++; } });
  page.emit('dialog', dialog('You have started to edit this note in another window; continue?'));
  assert.doesNotThrow(box.check);
  page.emit('dialog', dialog('Nothing to print'));
  assert.throws(box.check, /unexpected HTTP/);
  assert.equal(accepts, 2);
});

function nativePrint(t, { corrupt = false, requestChanges = {}, throwClick = false } = {}) {
  const box = load();
  const context = new EventEmitter();
  const page = new EventEmitter();
  const popup = new EventEmitter();
  context.pages = () => [page];
  context.waitForEvent = (event, { predicate }) => new Promise(resolve => {
    const receive = value => {
      if (!predicate(value)) return;
      context.off(event, receive);
      resolve(value);
    };
    context.on(event, receive);
  });
  page.context = () => context;
  page.isClosed = () => false;
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'echart-print-review-'));
  t.after(() => fs.rmSync(scratch, { recursive: true, force: true }));
  let bytes = textPdf('FAKEOWNEDMARKER ' + 'synthetic complete print content '.repeat(25));
  if (corrupt) bytes = bytes.subarray(0, bytes.lastIndexOf(Buffer.from('%%EOF')));
  const file = path.join(scratch, 'native.pdf');
  fs.writeFileSync(file, bytes);
  const textarea = { value: note };
  const elements = { printOps: { style: {} }, notes2print: { value: '' } };
  for (const flag of ['printCPP', 'printRx', 'printLabs', 'printPreventions', 'printAllergies']) elements[flag] = { value: 'false' };
  const form = { action: endpoint, elements: { demographicNo: { value: '101' }, chartContext: { value: 'signed-owned' } } };
  box.document = { forms: { caseManagementEntryForm: form },
    getElementsByName: () => [textarea], getElementById: id => elements[id] };
  page.evaluate = async (fn, arg) => fn(arg);
  page.locator = selector => selector === '#printopAll' ? { check: async () => {} } : { click: async () => {
    if (throwClick) throw new Error('native print click failed');
    const body = new URLSearchParams({ method: 'print', demographicNo: '101', chartContext: 'signed-owned',
      caseNote_note: note, notes2print: 'ALL_NOTES', pType: '', printCPP: 'false', printRx: 'false',
      printLabs: 'false', printPreventions: 'false', printAllergies: 'false', ...requestChanges });
    if (requestChanges.extraMethod) { body.delete('extraMethod'); body.append('method', 'autosave'); }
    context.emit('page', popup);
    popup.emit('download', { url: () => endpoint, failure: async () => null, path: async () => file });
    context.emit('response', response({ body: body.toString() }));
  } };
  return { box, context, page, popup };
}
function cleanObservers({ context, page, popup }) {
  assert.equal(context.listenerCount('response'), 0);
  assert.equal(context.listenerCount('page'), 0);
  for (const target of [page, popup]) {
    assert.equal(target.listenerCount('download'), 0);
    assert.equal(target.listenerCount('popup'), 0);
  }
}

test('standalone print retains the actual popup PDF, full identity, and cancels observers', async t => {
  const state = nativePrint(t);
  const result = await state.box.printChart(state.page, note, [], false);
  assert.equal(result.pdf.signature, true);
  assert.ok(result.pdf.bytes > 1024);
  cleanObservers(state);
});
for (const [label, changes, message] of [
  ['foreign chart context', { chartContext: 'another-chart' }, /owned chart context/],
  ['repeated conflicting method', { extraMethod: true }, /unexpected method/],
  ['wrong selected sections', { printCPP: 'true' }, /unexpected printCPP/],
  ['wrong note body', { caseNote_note: 'another note' }, /unexpected caseNote_note/],
  ['nonempty All date sentinel', { pType: 'false' }, /unexpected pType/],
]) test(`standalone print rejects ${label} and retires both observers`, async t => {
  const state = nativePrint(t, { requestChanges: changes });
  await assert.rejects(state.box.printChart(state.page, note, [], false), message);
  cleanObservers(state);
});
test('standalone print rejects an extractable PDF missing its final EOF marker', async t => {
  const state = nativePrint(t, { corrupt: true });
  await assert.rejects(state.box.printChart(state.page, note, [], false), /truncated/);
  cleanObservers(state);
});
test('a failed native print trigger cancels response and download observers', async t => {
  const state = nativePrint(t, { throwClick: true });
  await assert.rejects(state.box.printChart(state.page, note, [], false), /native print click failed/);
  cleanObservers(state);
});

function nativeAllIdentity(noteText, submitted, extra = []) {
  const box = load();
  const body = new URLSearchParams([
    ['method', 'print'], ['demographicNo', '101'], ['chartContext', 'signed-owned'],
    ['caseNote_note', submitted], ['notes2print', 'ALL_NOTES'], ['pType', ''],
    ...['printCPP', 'printRx', 'printLabs', 'printPreventions', 'printAllergies'].map(name => [name, 'false']),
    ...extra,
  ]);
  const request = { method: () => 'POST', url: () => endpoint, postData: () => body.toString() };
  return () => box.assertPrintRequestIdentity(request, endpoint, 'signed-owned', noteText, []);
}

test('native All identity accepts its single empty date-radio sentinel', () => {
  assert.doesNotThrow(nativeAllIdentity('Routine follow up.', 'Routine follow up.'));
});
test('native All identity accepts only HTML textarea CRLF serialization while keeping clinical bytes exact', () => {
  assert.doesNotThrow(nativeAllIdentity('First H&P + café 中文\nSecond line\n  Trailing spaces  ',
    'First H&P + café 中文\r\nSecond line\r\n  Trailing spaces  '));
});

for (const [label, submitted] of [
  ['unserialized LF', 'First\nSecond'], ['lone CR', 'First\rSecond'],
  ['extra newline', 'First\r\n\r\nSecond'], ['missing newline', 'FirstSecond'],
  ['changed clinical text', 'First\r\nAnother'], ['changed trailing spaces', 'First\r\nSecond '],
]) test(`native All identity rejects ${label}`, () => {
  assert.throws(nativeAllIdentity('First\nSecond', submitted), /unexpected caseNote_note/);
});
for (const extra of [[['pType', '']], [['pType', 'false']], [['pType', 'true']], [['caseNote_note', 'First']]]) {
  test(`native All identity rejects duplicate field ${JSON.stringify(extra)}`, () => {
    assert.throws(nativeAllIdentity('First', 'First', extra), /unexpected pType|unexpected caseNote_note/);
  });
}

function nativeNavigation(t, change) {
  let box; let preservedBeforeConsume = false;
  const fileSystem = { ...fs, mkdtempSync(prefix) {
    if (change === 'directory') throw new Error('FAKE directory unavailable');
    return fs.mkdtempSync(prefix);
  }, writeFileSync(file, data, options) {
    if (change === 'artifact' || change === 'refused-artifact') throw new Error('FAKE artifact write unavailable');
    const value = JSON.parse(data);
    if (value.audit && !preservedBeforeConsume) {
      assert.equal(vm.runInContext('recorder.requestFailures.some(entry => entry.errorText === "net::ERR_ABORTED")', box), true);
      fs.writeFileSync(file, data, options); preservedBeforeConsume = true;
    } else fs.writeFileSync(file, data, options);
  } };
  box = load({ fileSystem }); const page = new EventEmitter(); const cdp = new EventEmitter();
  const target = 'http://127.0.0.1:8080/carlos/demographic/DemographicEdit?demographic_no=101&displaymode=edit&dboperation=search_detail';
  const signed = `${endpoint}?chartContext=signed-owned${change === 'force' ? '&force=true' : ''}`;
  const form = { patient: '101', provider: '999998', noteId: '0', token: 'signed-owned', csrf: 'owned-csrf',
    endpoint: signed, url: `${endpoint}?method=setUpMainEncounter`, timeOrigin: 1 };
  let departed = false; const artifacts = [];
  box.console = { ...console, log(message) { if (message.startsWith('Native print navigation proof: ')) artifacts.push(message.split(': ')[1]); } };
  const beforeDirectories = new Set(fs.readdirSync(os.tmpdir()).filter(name => name.startsWith('carlos-print-completion-')));
  t.after(() => { for (const name of fs.readdirSync(os.tmpdir()).filter(name => name.startsWith('carlos-print-completion-')))
    if (!beforeDirectories.has(name)) fs.rmSync(path.join(os.tmpdir(), name), { recursive: true, force: true }); });
  cdp.send = async command => command === 'Page.getFrameTree'
    ? { frameTree: { frame: { id: 'frame', loaderId: departed ? 'new' : 'old' } } } : {};
  cdp.detach = async () => {};
  page.context = () => ({ cookies: async () => [{ name: 'JSESSIONID', value: 'owned.route' }], newCDPSession: async () => cdp });
  page.evaluate = async () => departed ? { url: target, timeOrigin: 2 } : { ...form };
  box.wirePage(page, 'owned-print');
  const rows = [['1', 'owned', 'ip', '999998', '0', '101', 'date'], ['2', 'other', 'ip', '99', '0', '202', 'date']];
  const sql = { rows(query) {
    if (query.includes('FROM casemgmt_note_lock ORDER BY')) return departed ? rows.slice(1) : rows;
    return [['FAKE unchanged clinical state']];
  }, value: () => departed ? '0' : '1' };
  const request = { resourceType: () => 'ping', method: () => 'POST', url: () => signed,
    postData: () => null, frame: () => ({ page: () => page }), failure: () => ({ errorText: 'net::ERR_ABORTED' }) };
  page.goto = async (url, options) => {
    assert.equal(url, target); assert.equal(options.waitUntil, 'load');
    page.emit('request', request);
    cdp.emit('Network.requestWillBeSent', { type: 'Ping', requestId: 'native1', frameId: 'frame', loaderId: 'old',
      request: { url: signed, method: 'POST' } });
    cdp.emit('Network.loadingFailed', { requestId: 'native1', errorText: 'net::ERR_ABORTED', canceled: true });
    page.emit('requestfailed', request);
    if (change === 'unknown') page.emit('requestfailed', { resourceType: () => 'script', url: () => `${endpoint}/broken.js`, failure: () => ({ errorText: 'net::ERR_FAILED' }) });
    if (change === 'duplicate') page.emit('requestfailed', request);
    departed = true;
    cdp.emit('Network.responseReceivedExtraInfo', { requestId: 'native1', statusCode: change?.startsWith('refused') ? 403 : 200,
      headers: { [require('./lib/playwright-harness').APPLICATION_HEADER]: 'none' } });
    return { url: () => target, status: () => 200,
      headers: () => ({ [require('./lib/playwright-harness').APPLICATION_HEADER]: 'none' }) };
  };
  return { box, page, sql, artifacts, cdp, preservedBeforeConsume: () => preservedBeforeConsume };
}

test('actual print navigation observes native events, preserves private proof, and acknowledges only its original entry', async t => {
  const state = nativeNavigation(t);
  await state.box.completePrintNavigation(state.page, state.sql);
  assert.doesNotThrow(state.box.check);
  assert.equal(state.artifacts.length, 1);
  assert.equal(state.preservedBeforeConsume(), true);
  const artifact = JSON.parse(fs.readFileSync(state.artifacts[0], 'utf8'));
  assert.equal(artifact.audit.blobBytesUnavailable, true);
  assert.equal(artifact.audit.rawFailures[0].errorText, 'net::ERR_ABORTED');
  assert.equal(artifact.audit.witness.operations[0].acknowledgment.requestId, 'native1');
  assert.equal(fs.statSync(state.artifacts[0]).mode & 0o777, 0o600);
  assert.equal(state.page.listenerCount('request'), 1); // Original strict recorder remains.
  assert.equal(state.page.listenerCount('requestfinished'), 0);
  assert.equal(state.page.listenerCount('requestfailed'), 1); // Original strict recorder remains.
  assert.equal(state.cdp.listenerCount('Network.loadingFailed'), 0);
});

test('a real unrelated failure remains strict after the exact native print abort is acknowledged', async t => {
  const state = nativeNavigation(t, 'unknown');
  await state.box.completePrintNavigation(state.page, state.sql);
  assert.throws(state.box.check, /browser reported/);
});
for (const change of ['duplicate', 'refused']) test(`native print ${change} refuses acknowledgment and retires private observers`, async t => {
  const state = nativeNavigation(t, change);
  await assert.rejects(state.box.completePrintNavigation(state.page, state.sql));
  assert.throws(state.box.check, /browser reported/);
  assert.equal(state.page.listenerCount('requestfailed'), 1);
  assert.equal(state.cdp.listenerCount('Network.loadingFailed'), 0);
});

for (const change of ['artifact', 'refused-artifact', 'directory']) test(`native print ${change} failure preserves evidence and always retires observers`, async t => {
  const state = nativeNavigation(t, change);
  await assert.rejects(state.box.completePrintNavigation(state.page, state.sql), error => {
    if (change === 'refused-artifact') {
      assert.match(error.message, /lacks its exact application200 acknowledgment/);
      assert.match(error.message, /Print proof cleanup: FAKE artifact write unavailable/);
    } else assert.match(error.message, /FAKE (artifact write|directory) unavailable/);
    return true;
  });
  if (change !== 'directory') assert.throws(state.box.check, /browser reported/);
  assert.equal(state.page.listenerCount('request'), 1);
  assert.equal(state.page.listenerCount('requestfinished'), 0);
  assert.equal(state.page.listenerCount('requestfailed'), 1);
  assert.equal(state.cdp.listenerCount('Network.loadingFailed'), 0);
  assert.equal(state.preservedBeforeConsume(), false);
});

test('a coherently changed native endpoint with force fails before private observers or navigation', async t => {
  const state = nativeNavigation(t, 'force');
  await assert.rejects(state.box.completePrintNavigation(state.page, state.sql), /exact owned editor authority/);
  assert.doesNotThrow(state.box.check);
  assert.equal(state.page.listenerCount('requestfailed'), 1);
  assert.equal(state.cdp.listenerCount('Network.loadingFailed'), 0);
});
