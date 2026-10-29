/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/demographic/cds-import.js'), 'utf8');
const RUN = '11111111-1111-4111-8111-111111111111';
const OTHER = '22222222-2222-4222-8222-222222222222';
const settle = () => new Promise(resolve => setImmediate(resolve));

class Element {
  constructor() {
    this.listeners = new Map(); this.dataset = {}; this.children = []; this.hidden = true;
    this.value = ''; this.textContent = ''; this.className = ''; this.scrolls = 0;
  }
  addEventListener(type, listener) { this.listeners.set(type, [...(this.listeners.get(type) || []), listener]); }
  fire(type) { return Promise.all((this.listeners.get(type) || []).map(listener => listener({preventDefault() {}}))); }
  replaceChildren(...children) { this.children = children; }
  append(...children) { this.children.push(...children); }
  removeAttribute(name) { delete this[name]; }
  scrollIntoView() { this.scrolls++; }
  focus() {}
}

// Drive the real DOMContentLoaded controller and its actual fetch/stream reader.
// Deferred responses make the browser's out-of-order network completion deterministic.
function browser(options = {}) {
  const nodes = new Map(), requests = [], timers = new Map(); let nextTimer = 0;
  const node = id => { if (!nodes.has(id)) nodes.set(id, new Element()); return nodes.get(id); };
  node('cdsImport').dataset = {context: '/carlos', owner: 'web-fixture'};
  const document = {
    documentElement: {lang: 'en'}, getElementById: node, createElement: () => new Element(),
    querySelector: () => ({value: 'synthetic-token'}),
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') this.ready = listener; }
  };
  const response = value => new Response(JSON.stringify({ok: true, apiVersion: 2, ...value}),
    {headers: {'content-type': 'application/json', 'X-CARLOS-CDS-API-Version': '2'}});
  const sandbox = vm.createContext({document, URL, URLSearchParams, AbortController, TextDecoder, WritableStream,
    location: {origin: 'http://localhost', href: 'http://localhost/carlos/form/importUpload'},
    history: {replaceState() {}}, localStorage: {getItem: () => null, setItem() {}}, navigator: {},
    setTimeout(callback, delay) { const id = ++nextTimer; timers.set(id, {callback, delay}); return id; },
    clearTimeout(id) { timers.delete(id); },
    fetch(url, init) {
      let resolve, reject; const pending = new Promise((done, fail) => { resolve = done; reject = fail; });
      const section = url.searchParams.get('section') || 'job';
      const row = {url, init, section, respond: value => resolve(response(value)), reject}; requests.push(row);
      if (url.pathname.endsWith('/cdsImportStatus') && section !== 'job' &&
          !(options.deferPages && ['patients', 'diagnostics'].includes(section))) row.respond({rows: []});
      if (url.pathname.endsWith('/cdsImportControl') && !options.deferControls) row.respond({});
      return pending;
    }
  });
  vm.runInContext(SOURCE, sandbox); document.ready();
  const jobs = () => requests.filter(row => row.url.pathname.endsWith('/cdsImportStatus') && row.section === 'job');
  return {node, requests, jobs, timers,
    open(id = RUN) { node('runId').value = id; return node('openRun').fire('click'); },
    click(id) { return node(id).fire('click'); },
    polls() { return [...timers.values()].filter(timer => timer.delay === 2000); },
    status() { return JSON.parse(node('runSummary').textContent); },
    page(section, index) { return requests.filter(row => row.section === section)[index]; },
    takePoll() {
      const [id, timer] = [...timers].find(([, value]) => value.delay === 2000) || [];
      assert.ok(timer, 'a pending operation must schedule another status read'); timers.delete(id); return timer.callback;
    }
  };
}

function status(generation, terminal = true, id = RUN, extra = {}) {
  return {job: {id, state: 'INCOMPLETE', reportGeneration: generation, cancelRequested: true,
    reportsAvailable: true, updatedAt: `2026-10-29T10:06:${30 + generation}Z`, ...extra},
  summary: {resumeRequested: false, operationTerminal: terminal, reportsDelivered: terminal,
    cleanupComplete: terminal, clinicalComplete: false, cleanComplete: false}};
}

for (const oldTerminal of [false, true]) test(`a late ${oldTerminal ? 'terminal' : 'pending'} same-run response cannot replace the reopened terminal observation`, async () => {
  const f = browser(); const old = f.open(); await settle(); const fresh = f.open(); await settle();
  assert.equal(f.jobs().length, 2);
  f.jobs()[1].respond(status(2)); await fresh;
  f.jobs()[0].respond(status(1, oldTerminal)); await old;
  assert.equal(f.status().job.reportGeneration, 2); assert.equal(f.polls().length, 0);
  assert.equal(f.node('jobOverview').dataset.fresh, 'true'); assert.equal(f.node('runHeading').scrolls, 1);
});

test('a superseded status failure cannot mark the newer successful view unknown or show its error', async () => {
  const f = browser(); const old = f.open(); await settle(); const fresh = f.open(); await settle();
  f.jobs()[1].respond(status(2)); await fresh;
  f.jobs()[0].reject(Error('OBSOLETE_HTTP_503')); await old;
  assert.equal(f.status().job.reportGeneration, 2); assert.equal(f.node('jobOverview').dataset.fresh, 'true');
  assert.equal(f.node('cancelRun').disabled, false); assert.equal(f.node('cdsNotice').hidden, true);
});

test('a current status failure preserves confirmed values and visibly stops the failed monitor', async () => {
  const f = browser(); const opened = f.open(); await settle(); f.jobs()[0].respond(status(1, false)); await opened;
  f.takePoll()(); await settle(); f.jobs()[1].reject(Error('CURRENT_HTTP_503')); await settle();
  assert.equal(f.status().job.reportGeneration, 1); assert.equal(f.node('jobOverview').dataset.fresh, 'false');
  assert.equal(f.node('cancelRun').disabled, true); assert.equal(f.node('resumeRun').disabled, true);
  assert.match(f.node('cdsNotice').textContent, /CURRENT_HTTP_503/); assert.equal(f.polls().length, 0);
});

test('a previously queued polling callback cannot start a request after the same run is reopened', async () => {
  const f = browser(); const opened = f.open(); await settle(); f.jobs()[0].respond(status(1, false)); await opened;
  const queued = f.takePoll(); const refreshed = f.click('retryJobStatus'); await settle();
  queued(); await settle(); assert.equal(f.jobs().length, 2);
  f.jobs()[1].respond(status(2)); await refreshed;
  queued(); await settle(); assert.equal(f.jobs().length, 2); assert.equal(f.polls().length, 0);
});

test('an in-flight old poll cannot overwrite a refreshed view or add another timer', async () => {
  const f = browser(); const opened = f.open(); await settle(); f.jobs()[0].respond(status(1, false)); await opened;
  f.takePoll()(); await settle(); const refreshed = f.click('retryJobStatus'); await settle();
  f.jobs()[2].respond(status(2)); await refreshed;
  f.jobs()[1].respond(status(1, false)); await settle();
  assert.equal(f.status().job.reportGeneration, 2); assert.equal(f.polls().length, 0);
});

for (const oldFailure of [false, true]) test(`older patient and diagnostic ${oldFailure ? 'failures' : 'pages'} cannot replace or invalidate the reopened view`, async () => {
  const f = browser({deferPages: true}); const old = f.open(); await settle();
  f.jobs()[0].respond(status(1)); await settle(); const fresh = f.open(); await settle();
  f.jobs()[1].respond(status(2)); await settle();
  f.page('patients', 1).respond({rows: [{patientKey: 'new', sourcePatientId: 'NEW', state: 'COMPLETE'}]});
  f.page('diagnostics', 1).respond({rows: [{id: 2, code: 'NEW', stage: 'REPORT', severity: 'INFO'}]}); await fresh;
  if (oldFailure) {
    f.page('patients', 0).reject(Error('OBSOLETE_PATIENT_FAILURE'));
    f.page('diagnostics', 0).reject(Error('OBSOLETE_DIAGNOSTIC_FAILURE'));
  } else {
    f.page('patients', 0).respond({rows: [{patientKey: 'old', sourcePatientId: 'OLD'}]});
    f.page('diagnostics', 0).respond({rows: [{id: 1, code: 'OLD'}]});
  }
  await old; assert.equal(f.node('patientRows').children[0].children[0].textContent, 'NEW');
  assert.match(f.node('diagnosticRows').children[0].children[1].textContent, /^NEW/);
  assert.equal(f.status().job.reportGeneration, 2); assert.equal(f.node('jobOverview').dataset.fresh, 'true');
  assert.equal(f.node('cdsNotice').hidden, true); assert.equal(f.polls().length, 0);
});

test('a control response for a previously selected job does not reopen or reset the newer selected job', async () => {
  const f = browser({deferControls: true}); const first = f.open(); await settle(); f.jobs()[0].respond(status(1)); await first;
  const cancelled = f.click('cancelRun'); await settle(); const other = f.open(OTHER); await settle();
  f.jobs()[1].respond(status(2, true, OTHER)); await other;
  f.requests.find(row => row.url.pathname.endsWith('/cdsImportControl')).respond({}); await cancelled;
  assert.equal(f.jobs().length, 2); assert.equal(f.status().job.id, OTHER);
  assert.equal(f.node('jobOverview').dataset.fresh, 'true'); assert.equal(f.polls().length, 0);
});

test('an unchanged periodic status read keeps the displayed diagnostic actions usable with the latest review version', async () => {
  const f = browser({deferPages: true}); const opened = f.open(); await settle();
  const first = status(1, false); first.summary.reviewVersion = 0;
  f.jobs()[0].respond(first); await settle();
  f.page('patients', 0).respond({rows: []});
  f.page('diagnostics', 0).respond({rows: [{id: 1, code: 'VALUE_REVIEW', stage: 'NORMALIZE',
    summary: 'Fixture diagnostic', patientKey: 'a'.repeat(64), severity: 'ERROR', blocking: true, resolved: false}]});
  await opened;
  f.takePoll()(); await settle(); const latest = status(1, false); latest.summary.reviewVersion = 1;
  f.jobs()[1].respond(latest); await settle();
  assert.equal(f.requests.filter(row => row.section === 'diagnostics').length, 1);
  await f.node('diagnosticRows').children[0].children[3].children[0].fire('click');
  assert.equal(f.node('cdsReviewForm').hidden, false); assert.equal(f.node('reviewVersion').value, 1);
});
