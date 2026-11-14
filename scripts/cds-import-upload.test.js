/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// The import page's chunk transfer: XMLHttpRequest, a no-progress window instead of a deadline,
// and the same-offset resend. Time is virtual, so "twenty minutes" costs nothing to run.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createHash} = require('node:crypto');

const SOURCE = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/demographic/cds-import.js'), 'utf8');
const ORIGIN = 'http://localhost';
const UPLOAD = '33333333-3333-4333-8333-333333333333';
const DIGEST = 'a'.repeat(64);
const CHUNK_URL = ORIGIN + '/carlos/form/cdsUpload?operation=chunk&uploadId=' + UPLOAD
  + '&offset=0&bytes=3&sha256=' + DIGEST + '&apiVersion=2';
const RECEIPT = {ok: true, upload: {id: UPLOAD, receivedBytes: 3}};
const settle = () => new Promise(resolve => setImmediate(resolve));

async function until(condition, message) {
  for (let turn = 0; turn < 500; turn++) {
    if (condition()) return;
    await settle();
  }
  assert.fail(message);
}

// Nothing fires until the test moves the clock.
function virtualClock() {
  let now = 0; let next = 0;
  const timers = new Map();
  return {
    timers,
    setTimeout(callback, delay = 0) { const id = ++next; timers.set(id, {callback, delay, due: now + delay}); return id; },
    clearTimeout(id) { timers.delete(id); },
    advance(millis) {
      const end = now + millis;
      for (;;) {
        const [entry] = [...timers].filter(([, timer]) => timer.due <= end)
          .sort((a, b) => a[1].due - b[1].due || a[0] - b[0]);
        if (!entry) break;
        timers.delete(entry[0]); now = entry[1].due; entry[1].callback();
      }
      now = end;
    }
  };
}

// The XMLHttpRequest surface the page uses. send() calls a prototype onsend hook the way
// CSRFGuard's hijacked send() does, so a test can install the guard's header injection.
function xhrClass(created) {
  return class FakeXhr {
    constructor() { this.upload = {}; this.headers = {}; this.readyState = 0; this.aborted = false; created.push(this); }
    open(method, url) { this.method = method; this.url = url; this.readyState = 1; }
    setRequestHeader(name, value) { this.headers[name] = name in this.headers ? this.headers[name] + ', ' + value : value; }
    send(body) { if (typeof this.onsend === 'function') this.onsend(body); this.body = body; this.sent = true; }
    abort() { this.aborted = true; this.readyState = 4; this.onreadystatechange?.(); this.onabort?.(); }
    getResponseHeader(name) { return this.replyHeaders?.[name.toLowerCase()] ?? null; }
    sending(loaded) { this.upload.onprogress?.({loaded, lengthComputable: true}); }
    reply(status, value, {type = 'application/json', version = '2', url, bytes} = {}) {
      this.status = status; this.responseURL = url ?? new URL(this.url, ORIGIN).href;
      this.replyHeaders = {'content-type': type, ...(version === null ? {} : {'x-carlos-cds-api-version': version})};
      this.readyState = 2; this.onreadystatechange?.();
      const body = bytes ?? Buffer.from(JSON.stringify({apiVersion: 2, ...value}));
      this.readyState = 3; this.onprogress?.({loaded: body.length});
      if (this.aborted) return;
      this.response = body.buffer.slice(body.byteOffset, body.byteOffset + body.length);
      this.readyState = 4; this.onreadystatechange?.(); this.onload?.();
    }
  };
}

function client() {
  const created = []; const time = virtualClock();
  const sandbox = vm.createContext({URL, TextDecoder, WritableStream, XMLHttpRequest: xhrClass(created),
    setTimeout: time.setTimeout, clearTimeout: time.clearTimeout,
    location: {origin: ORIGIN, href: ORIGIN + '/carlos/form/importUpload'}});
  vm.runInContext(SOURCE, sandbox);
  return {api: sandbox.CarlosCdsImport, created, time, Xhr: sandbox.XMLHttpRequest};
}

test('a chunk that keeps moving is never cut off, however long it takes', async () => {
  const {api, created, time} = client();
  const sent = api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
  const [xhr] = created;
  // A progress event just inside every window, for about ten minutes of a one-second window.
  for (let step = 1; step <= 600; step++) { time.advance(999); xhr.sending(step); }
  assert.equal(xhr.aborted, false);
  xhr.reply(200, RECEIPT);
  assert.equal((await sent).upload.receivedBytes, 3);
  assert.equal(time.timers.size, 0, 'no wait outlives the request');
});

test('a chunk with no progress for the window is aborted without a status, so the page sends it again', async () => {
  const {api, created, time} = client();
  const sent = api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
  const [xhr] = created;
  time.advance(600); xhr.sending(1);
  time.advance(999);
  assert.equal(xhr.aborted, false, 'the progress event restarted the window');
  time.advance(1);
  assert.equal(xhr.aborted, true);
  await assert.rejects(sent, error => error.message === 'UPLOAD_CHUNK_STALLED' && error.status === undefined);
  assert.equal(time.timers.size, 0, 'no wait outlives the request');
});

test('waiting for the receipt counts as silence, and every part of the reply restarts the window', async () => {
  const {api, created, time} = client();
  const sent = api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
  const [xhr] = created;
  xhr.sending(3);
  time.advance(999);
  xhr.readyState = 2; xhr.onreadystatechange();
  time.advance(999); xhr.onprogress({loaded: 10});
  time.advance(999);
  assert.equal(xhr.aborted, false);
  xhr.reply(200, RECEIPT);
  assert.equal((await sent).upload.receivedBytes, 3);

  const unanswered = api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
  created[1].sending(3);
  time.advance(1000);
  await assert.rejects(unanswered, error => error.message === 'UPLOAD_CHUNK_STALLED' && error.status === undefined);
});

test('a chunk is raw octets, and the token headers come from the CSRF guard hook, never twice', () => {
  const plain = client();
  plain.api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
  const [direct] = plain.created;
  assert.equal(direct.method, 'POST');
  assert.equal(direct.url, CHUNK_URL.slice(ORIGIN.length));
  assert.equal(direct.responseType, 'arraybuffer');
  assert.equal(direct.body, 'raw');
  assert.equal(direct.headers['Content-Type'], 'application/octet-stream');
  assert.equal(direct.headers['X-Requested-With'], 'XMLHttpRequest');
  assert.equal(direct.headers['CSRF-TOKEN'], 'synthetic-token');

  const guarded = client();
  guarded.Xhr.prototype.onsend = function () {
    this.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
    this.setRequestHeader('CSRF-TOKEN', 'guard-token');
  };
  guarded.api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
  const [hooked] = guarded.created;
  assert.equal(hooked.headers['X-Requested-With'], 'XMLHttpRequest');
  assert.equal(hooked.headers['CSRF-TOKEN'], 'guard-token');
  assert.equal(hooked.headers['Content-Type'], 'application/octet-stream');
});

test('a JSON refusal keeps its status and code, and any other reply is a lost receipt sent again', async () => {
  const {api, created} = client();
  for (const [status, code] of [[507, 'UPLOAD_DISK_RESERVE_REFUSED'], [429, 'UPLOAD_IO_BUSY'], [409, 'UPLOAD_BUSY']]) {
    const refused = api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
    created.at(-1).reply(status, {ok: false, code});
    await assert.rejects(refused, error => error.status === status && error.code === code);
  }
  const lost = [
    [{url: ORIGIN + '/carlos/errorpage'}, 'IMPORT_RESPONSE_UNAVAILABLE_HTTP_200'],
    [{type: 'text/html'}, 'IMPORT_RESPONSE_UNAVAILABLE_HTTP_200'],
    [{version: null}, 'IMPORT_CLIENT_REFRESH_REQUIRED'],
    [{version: '1'}, 'IMPORT_CLIENT_REFRESH_REQUIRED'],
    [{bytes: Buffer.from('{"ok":true}')}, 'IMPORT_CLIENT_REFRESH_REQUIRED'],
    [{bytes: Buffer.alloc(2 * 1024 * 1024 + 1, 32)}, 'IMPORT_RESPONSE_CAPACITY_EXCEEDED']
  ];
  for (const [options, code] of lost) {
    const sent = api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
    created.at(-1).reply(200, RECEIPT, options);
    await assert.rejects(sent, error => error.message === code && error.status === undefined);
  }
  const failed = api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', 1000);
  created.at(-1).onerror();
  await assert.rejects(failed, error => error.message === 'UPLOAD_CHUNK_NETWORK_FAILED' && error.status === undefined);
  const before = created.length;
  await assert.rejects(api.postChunk(CHUNK_URL, 'raw', 'synthetic-token', Number.NaN),
    error => error.message === 'IMPORT_CLIENT_REFRESH_REQUIRED');
  assert.equal(created.length, before, 'without a window nothing is sent');
});

test('a metadata reply restarts its wait with every piece that arrives', async () => {
  const {api} = client();
  const pieces = ['{"ok":true,', '"apiVersion":2,', '"rows":[]}'];
  const body = new ReadableStream({start(controller) {
    for (const piece of pieces) controller.enqueue(Buffer.from(piece));
    controller.close();
  }});
  let progressed = 0;
  const data = await api.boundedJson(new Response(body), undefined, () => progressed++);
  assert.equal(progressed, pieces.length);
  assert.equal(data.apiVersion, 2);
});

class Element {
  constructor() {
    this.listeners = new Map(); this.dataset = {}; this.children = []; this.hidden = true;
    this.value = ''; this.textContent = ''; this.className = '';
  }
  addEventListener(type, listener) { this.listeners.set(type, [...(this.listeners.get(type) || []), listener]); }
  get textContent() { return this.text || ''; }
  set textContent(value) { this.text = String(value); this.children = []; }
  fire(type) { return Promise.all((this.listeners.get(type) || []).map(listener => listener({preventDefault() {}}))); }
  replaceChildren(...children) { this.children = children; }
  append(...children) { this.children.push(...children); }
  removeAttribute(name) { delete this[name]; }
  scrollIntoView() {}
  focus() {}
}

// Drives the real DOMContentLoaded controller: metadata through a deferred fetch, chunks through
// the fake XMLHttpRequest, both on the virtual clock.
function page(dataset) {
  const nodes = new Map(); const requests = []; const xhrs = []; const time = virtualClock();
  const node = id => { if (!nodes.has(id)) nodes.set(id, new Element()); return nodes.get(id); };
  node('cdsImport').dataset = {context: '/carlos', owner: 'web-fixture', ...dataset};
  const document = {
    documentElement: {lang: 'en'}, getElementById: node, createElement: () => new Element(),
    querySelector: () => ({value: 'synthetic-token'}),
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') this.ready = listener; }
  };
  const reply = (value, status) => new Response(JSON.stringify({apiVersion: 2, ...value}),
    {status, headers: {'content-type': 'application/json', 'X-CARLOS-CDS-API-Version': '2'}});
  const sandbox = vm.createContext({document, URL, URLSearchParams, AbortController, TextDecoder, WritableStream,
    crypto: globalThis.crypto, XMLHttpRequest: xhrClass(xhrs),
    location: {origin: ORIGIN, href: ORIGIN + '/carlos/form/importUpload'},
    history: {replaceState() {}}, localStorage: {getItem: () => null, setItem() {}}, navigator: {},
    setTimeout: time.setTimeout, clearTimeout: time.clearTimeout,
    fetch(url, init) {
      return new Promise(resolve => {
        const row = {url, init, respond: (value, status = 200) => resolve(reply(value, status))};
        requests.push(row);
        if (url.pathname.endsWith('/cdsImportStatus')) row.respond({ok: true, rows: []});
      });
    }});
  vm.runInContext(SOURCE, sandbox); document.ready();
  const file = new File([Buffer.from('<a/>')], 'synthetic.xml');
  node('importFile').files = [file];
  node('cdsSource').selectedOptions = [{dataset: {namespace: 'urn:synthetic', profile: 'synthetic-profile'}}];
  node('providerNo').value = '999998';
  return {node, time, xhrs, file, uploads: () => requests.filter(row => row.url.pathname.endsWith('/cdsUpload'))};
}

const receipt = receivedBytes => ({id: UPLOAD, fileName: 'synthetic.xml', expectedBytes: 4, receivedBytes,
  chunkBytes: 65536, committedChunks: receivedBytes ? 1 : 0, state: 'OPEN'});

test('the page waits the server window for a chunk and sends a stalled chunk again at the same offset', async () => {
  const p = page({chunkStallMillis: '5000'});
  p.node('cdsImportForm').fire('submit');
  await until(() => p.uploads().length === 1, 'the upload was not begun');
  assert.equal(p.uploads()[0].init.body.get('operation'), 'begin');
  p.uploads()[0].respond({ok: true, upload: receipt(0)});
  await until(() => p.xhrs.length === 1, 'the first chunk was not sent');
  const [first] = p.xhrs;
  const url = new URL(first.url, ORIGIN);
  assert.equal(url.pathname, '/carlos/form/cdsUpload');
  assert.deepEqual([...url.searchParams.keys()], ['operation', 'uploadId', 'offset', 'bytes', 'sha256', 'apiVersion']);
  assert.equal(url.searchParams.get('operation'), 'chunk');
  assert.equal(url.searchParams.get('offset'), '0');
  assert.equal(url.searchParams.get('bytes'), '4');
  assert.equal(url.searchParams.get('sha256'), createHash('sha256').update('<a/>').digest('hex'));
  assert.equal(first.headers['Content-Type'], 'application/octet-stream');
  p.time.advance(4000); first.sending(2);
  p.time.advance(4999);
  assert.equal(first.aborted, false, 'progress restarted the advertised five-second window');
  p.time.advance(1);
  assert.equal(first.aborted, true);
  await until(() => [...p.time.timers.values()].some(timer => timer.delay === 500), 'no resend was scheduled');
  p.time.advance(500);
  await until(() => p.xhrs.length === 2, 'the stalled chunk was not sent again');
  const second = p.xhrs[1];
  assert.equal(second.url, first.url, 'the resend carries the same offset and hash');
  assert.equal(second.body, first.body);
  second.reply(200, {ok: true, upload: receipt(4)});
  await until(() => p.uploads().length === 2, 'the received upload was not finished');
  assert.equal(p.uploads()[1].init.body.get('operation'), 'finish');
});

test('a page without the server chunk window asks for a reload before beginning any upload', async () => {
  const p = page({});
  p.node('cdsImportForm').fire('submit');
  await until(() => /IMPORT_CLIENT_REFRESH_REQUIRED/.test(p.node('cdsNotice').textContent), 'no reload notice was shown');
  assert.equal(p.uploads().length, 0);
  assert.equal(p.xhrs.length, 0);
});
