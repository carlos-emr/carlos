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
  get textContent() { return this.text || ''; }
  set textContent(value) { this.text = String(value); this.children = []; }
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
  const nodes = new Map(), requests = [], timers = new Map(), routes = []; let nextTimer = 0;
  const node = id => { if (!nodes.has(id)) nodes.set(id, new Element()); return nodes.get(id); };
  node('cdsImport').dataset = {context: '/carlos', owner: 'web-fixture'};
  const document = {
    documentElement: {lang: options.language || 'en'}, getElementById: node, createElement: () => new Element(),
    querySelector: () => ({value: 'synthetic-token'}),
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') this.ready = listener; }
  };
  const response = (value, status = 200) => new Response(JSON.stringify({ok: true, apiVersion: 2, ...value}),
    {status, headers: {'content-type': 'application/json', 'X-CARLOS-CDS-API-Version': '2'}});
  const sandbox = vm.createContext({document, URL, URLSearchParams, AbortController, TextDecoder, WritableStream,
    location: {origin: 'http://localhost', href: 'http://localhost/carlos/form/importUpload'},
    history: {replaceState(state, title, url) { routes.push(url); }},
    localStorage: {getItem: () => null, setItem() {}}, navigator: {},
    setTimeout(callback, delay) { const id = ++nextTimer; timers.set(id, {callback, delay}); return id; },
    clearTimeout(id) { timers.delete(id); },
    fetch(url, init) {
      let resolve, reject; const pending = new Promise((done, fail) => { resolve = done; reject = fail; });
      const section = url.searchParams.get('section') || 'job';
      const row = {url, init, section, respond: (value, status) => resolve(response(value, status)), reject}; requests.push(row);
      if (url.pathname.endsWith('/cdsImportStatus') && section !== 'job' &&
          !(options.deferPages && ['patients', 'diagnostics'].includes(section))) row.respond({rows: []});
      if (url.pathname.endsWith('/cdsImportControl') && !options.deferControls) row.respond({});
      return pending;
    }
  });
  vm.runInContext(SOURCE, sandbox); document.ready();
  const jobs = () => requests.filter(row => row.url.pathname.endsWith('/cdsImportStatus') && row.section === 'job');
  return {node, requests, jobs, timers, routes,
    open(id = RUN) { node('runId').value = id; return node('openRun').fire('click'); },
    click(id) { return node(id).fire('click'); },
    controls() { return requests.filter(row => row.url.pathname.endsWith('/cdsImportControl')); },
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

test('scheduled polling preserves inactivity while manual reads remain user activity', async () => {
  const f = browser(); const opened = f.open(); await settle();
  f.jobs()[0].respond(status(1, false)); await opened;
  assert.equal(f.jobs()[0].url.searchParams.has('autoRefresh'), false);
  assert.equal(f.page('patients', 0).url.searchParams.has('autoRefresh'), false);
  assert.equal(f.page('diagnostics', 0).url.searchParams.has('autoRefresh'), false);

  f.takePoll()(); await settle();
  assert.equal(f.jobs()[1].url.searchParams.get('autoRefresh'), 'true');
  f.jobs()[1].respond(status(2, false)); await settle();
  assert.equal(f.page('patients', 1).url.searchParams.get('autoRefresh'), 'true');
  assert.equal(f.page('diagnostics', 1).url.searchParams.get('autoRefresh'), 'true');

  await f.click('nextPatients'); await f.click('nextDiagnostics');
  assert.equal(f.page('patients', 2).url.searchParams.has('autoRefresh'), false);
  assert.equal(f.page('diagnostics', 2).url.searchParams.has('autoRefresh'), false);
  const reopened = f.click('retryJobStatus'); await settle();
  assert.equal(f.jobs()[2].url.searchParams.has('autoRefresh'), false);
  f.jobs()[2].respond(status(3)); await reopened;
});

for (const language of ['en', 'fr']) test(`the public fatal-supervisor alert blocks Resume and shows repair guidance in ${language}`, async () => {
  const f = browser({language}); const opened = f.open(); await settle();
  const stopped = status(1);
  stopped.summary.workerAlert = 'IMPORT_SUPERVISOR_FATAL';
  f.jobs()[0].respond(stopped); await opened;
  assert.equal(f.node('resumeRun').disabled, true);
  assert.equal(f.node('cdsWorkerAlert').hidden, false);
  assert.match(f.node('cdsWorkerAlert').textContent, /IMPORT_SUPERVISOR_FATAL/);
  assert.equal(f.node('cdsOperationalAlert').hidden, false);
  const restart = language === 'fr' ? /réparation et un redémarrage du service/ : /administrator repair and service restart/;
  assert.match(f.node('cdsOperationalAlert').textContent, restart);

  // A healthy supervisor can leave a suspended review explicitly retryable.
  const reopened = f.open(); await settle();
  const review = status(2);
  Object.assign(review.summary, {workerAlert: 'REVIEW_WORKER_FATAL', reviewErrorCode: 'REVIEW_WORKER_FATAL', pendingReviewCount: 1});
  f.jobs()[1].respond(review); await reopened;
  assert.equal(f.node('resumeRun').disabled, false);
  assert.equal(f.node('cdsOperationalAlert').hidden, false);
  assert.doesNotMatch(f.node('cdsOperationalAlert').textContent, restart);
  assert.match(f.node('cdsOperationalAlert').textContent, language === 'fr' ? /Reprendre réessaie cette révision/ : /Resume retries that preserved review/);

  const refreshed = f.open(); await settle();
  f.jobs()[2].respond(status(3)); await refreshed;
  assert.equal(f.node('resumeRun').disabled, false);
  assert.equal(f.node('cdsWorkerAlert').hidden, true);
  assert.equal(f.node('cdsOperationalAlert').hidden, true);
});

for (const oldTerminal of [false, true]) test(`a late ${oldTerminal ? 'terminal' : 'pending'} same-run response cannot replace the reopened terminal observation`, async () => {
  const f = browser(); const old = f.open(); await settle(); const fresh = f.open(); await settle();
  assert.equal(f.jobs().length, 2);
  f.jobs()[1].respond(status(2)); await fresh;
  f.jobs()[0].respond(status(1, oldTerminal)); await old;
  assert.equal(f.status().job.reportGeneration, 2); assert.equal(f.polls().length, 0);
  assert.equal(f.node('jobOverview').dataset.fresh, 'true'); assert.equal(f.node('runHeading').scrolls, 1);
});

function startControl(fixture, operation) {
  if (operation !== 'review') return fixture.click(operation === 'cancel' ? 'cancelRun' : 'resumeRun');
  fixture.node('reviewKind').value = 'DECLINE_PATIENT';
  fixture.node('reviewPatientKey').value = 'a'.repeat(64);
  fixture.node('reviewReason').value = 'Synthetic review reason';
  return fixture.node('cdsReviewForm').fire('submit');
}

for (const operation of ['cancel', 'resume', 'review']) {
  for (const sameRun of [true, false]) {
    // A different job cannot own the original job's pending-control notice.
    for (const notice of sameRun ? ['owned', 'none', 'unrelated'] : ['none', 'unrelated']) {
      for (const outcome of ['success', 'failure']) test(`a late ${operation} ${outcome} acknowledgement keeps a ${sameRun ? 'same-job' : 'different-job'} ${notice} view consistent`, async () => {
        const f = browser({deferControls: true}); const initial = f.open(); await settle();
        f.jobs()[0].respond(status(1, true, RUN, {cancelRequested: false})); await initial;
        const action = startControl(f, operation); await settle();
        const selected = sameRun ? RUN : OTHER, reopened = f.open(selected); await settle();
        f.jobs()[1].respond(status(1, true, selected, {cancelRequested: false})); await reopened;
        if (sameRun && notice !== 'none') {
          await startControl(f, operation); await settle();
          assert.match(f.node('cdsNotice').textContent, /awaiting acknowledgement/);
        }
        if (notice === 'unrelated') await f.click('copyJobStatus');
        const previousNotice = f.node('cdsNotice').textContent;
        f.node('cdsReviewForm').hidden = false; f.node('reviewReason').value = 'NEW_VIEW_DRAFT';
        const scrolls = f.node('runHeading').scrolls, routeCount = f.routes.length, route = f.routes.at(-1);
        assert.equal(f.controls().length, 1, 'the duplicate must be refused before sending another mutation');
        assert.equal(f.polls().length, 0, 'the reopened observation was terminal before the old request settled');
        if (outcome === 'success') f.controls()[0].respond({});
        else f.controls()[0].reject(Error('CONTROL_ACK_UNCONFIRMED'));
        await settle();

        if (sameRun && outcome === 'success') {
          assert.equal(f.node('cdsNotice').hidden, notice !== 'unrelated', 'only the exact owned pending notice may be cleared');
          assert.equal(f.jobs().length, 3, 'a late same-job acknowledgement must refresh its potentially obsolete terminal observation');
          assert.equal(f.jobs()[2].init.method, 'GET');
          assert.equal(f.jobs()[2].url.searchParams.get('runId'), RUN);
          assert.equal(f.status().job.reportGeneration, 1, 'the view is retained while its read-only refresh is pending');
          assert.equal(f.node('jobOverview').dataset.fresh, 'true');
          const fresh = status(2, operation === 'cancel', RUN, {cancelRequested: operation === 'cancel'});
          fresh.summary.resumeRequested = operation === 'resume'; fresh.summary.reviewVersion = operation === 'review' ? 1 : 0;
          f.jobs()[2].respond(fresh);
        } else assert.equal(f.jobs().length, 2, 'a failure or another selected job must not start an unsolicited status read');
        await action; await settle();

        assert.equal(f.controls().length, 1, 'settlement never retries the mutation');
        assert.equal(f.status().job.id, selected);
        assert.equal(f.node('jobOverview').dataset.fresh, 'true');
        assert.equal(f.node('cdsReviewForm').hidden, false, 'an obsolete review acknowledgement cannot hide the newer form');
        assert.equal(f.node('reviewReason').value, 'NEW_VIEW_DRAFT');
        assert.equal(f.node('runHeading').scrolls, scrolls, 'refreshing status must not reopen or scroll the newer view');
        assert.equal(f.routes.at(-1), route);
        assert.ok(f.routes.slice(routeCount).every(value => value === route), 'the read-only refresh must not navigate away');
        if (sameRun && outcome === 'success') {
          assert.equal(f.status().job.reportGeneration, 2);
          assert.equal(f.status().job.cancelRequested, operation === 'cancel');
          assert.equal(f.status().summary.resumeRequested, operation === 'resume');
          assert.equal(f.status().summary.reviewVersion, operation === 'review' ? 1 : 0);
          assert.equal(f.polls().length, operation === 'cancel' ? 0 : 1);
        } else {
          assert.equal(f.status().job.reportGeneration, 1); assert.equal(f.polls().length, 0);
        }
        if (notice === 'unrelated') {
          assert.equal(f.node('cdsNotice').hidden, false); assert.equal(f.node('cdsNotice').textContent, previousNotice);
        } else if (sameRun && notice === 'owned' && outcome === 'failure') {
          assert.match(f.node('cdsNotice').textContent, /Acknowledgement unconfirmed/);
          assert.doesNotMatch(f.node('cdsNotice').textContent, /awaiting acknowledgement/);
          assert.equal(f.node('cdsNotice').children.length, 0, 'the status read that preceded this failure cannot permit dismissal');
          const checked = f.click('retryJobStatus'); await settle(); f.jobs()[2].respond(status(2)); await checked;
          assert.equal(f.node('cdsNotice').children.length, 1, 'only a later fresh original-job observation permits dismissal');
          assert.equal(f.controls().length, 1);
        } else assert.equal(f.node('cdsNotice').hidden, true);
      });
    }
  }
}

for (const operation of ['cancel', 'resume', 'review']) {
  for (const sameRun of [false, true]) test(`an obsolete ${operation} rejection cannot change a ${sameRun ? 'reopened same-job' : 'different-job'} view and remains discoverable on the original job`, async () => {
    const f = browser({deferControls: true}); const first = f.open(); await settle();
    f.jobs()[0].respond(status(1)); await first;
    const action = startControl(f, operation); await settle();
    assert.equal(f.controls().length, 1); assert.equal(f.controls()[0].init.body.get('runId'), RUN);
    const selected = sameRun ? RUN : OTHER, next = f.open(selected); await settle();
    f.jobs()[1].respond(status(2, false, selected)); await next;
    f.node('cdsNotice').hidden = false; f.node('cdsNotice').textContent = 'CURRENT_VIEW_NOTICE';
    f.controls()[0].reject(Error('OLD_CONTROL_UNCONFIRMED')); await action; await settle();
    assert.equal(f.node('cdsNotice').textContent, 'CURRENT_VIEW_NOTICE');
    assert.equal(f.status().job.id, selected); assert.equal(f.status().job.reportGeneration, 2);
    assert.equal(f.node('jobOverview').dataset.fresh, 'true'); assert.equal(f.polls().length, 1);
    assert.equal(f.jobs().length, 2); assert.equal(f.controls().length, 1);

    const original = f.open(RUN); await settle();
    assert.match(f.node('cdsNotice').textContent, new RegExp(operation, 'i'));
    assert.match(f.node('cdsNotice').textContent, /Acknowledgement unconfirmed/);
    assert.ok(f.node('cdsNotice').textContent.includes(RUN));
    assert.equal(f.node('cdsNotice').children.length, 0, 'no dismissal before a fresh status observation');
    f.jobs()[2].respond(status(3)); await original;
    assert.equal(f.node('cdsNotice').children.length, 1);
    await f.node('cdsNotice').children[0].fire('click');
    assert.equal(f.node('cdsNotice').hidden, true);
    assert.equal(f.controls().length, 1, 'checking or dismissing a warning must not resend a mutation');
    assert.equal(f.polls().length, 0); assert.equal(f.status().job.reportGeneration, 3);
  });

  test(`a current ${operation} rejection remains visible and cannot be retried before checking and dismissing its original-job warning`, async () => {
    const f = browser({deferControls: true}); const first = f.open(); await settle();
    f.jobs()[0].respond(status(1)); await first;
    const action = startControl(f, operation); await settle();
    await startControl(f, operation); await settle();
    assert.equal(f.controls().length, 1, 'an in-flight request cannot be duplicated');
    assert.equal(f.node('cdsNotice').children.length, 0, 'an in-flight request cannot be dismissed');
    f.controls()[0].reject(Error('CURRENT_CONTROL_UNCONFIRMED')); await action; await settle();
    assert.equal(f.node('cdsNotice').hidden, false);
    assert.match(f.node('cdsNotice').textContent, /Acknowledgement unconfirmed/);
    assert.ok(f.node('cdsNotice').textContent.includes(RUN));
    assert.match(f.node('cdsNotice').textContent, /IMPORT_CONTROL_OUTCOME_UNCONFIRMED/);
    await startControl(f, operation); await settle(); assert.equal(f.controls().length, 1);
    const other = f.open(OTHER); await settle(); f.jobs()[1].respond(status(2, true, OTHER)); await other;
    assert.equal(f.node('cdsNotice').hidden, true, 'the original control warning must not follow navigation');
    const original = f.open(RUN); await settle(); f.jobs()[2].respond(status(3)); await original;
    assert.equal(f.node('cdsNotice').children.length, 1);
    await f.node('cdsNotice').children[0].fire('click');
    assert.equal(f.node('cdsNotice').hidden, true); assert.equal(f.controls().length, 1);
    assert.equal(f.node('jobOverview').dataset.fresh, 'true'); assert.equal(f.status().job.reportGeneration, 3);
  });
}

test('uncertain control warnings are bounded without eviction and refuse a new target before sending', async () => {
  const f = browser({deferControls: true});
  const ids = Array.from({length: 21}, (_, index) => '11111111-1111-4111-8111-' + String(index).padStart(12, '0'));
  for (let index = 0; index < 20; index++) {
    const opened = f.open(ids[index]); await settle(); f.jobs()[index].respond(status(1, true, ids[index])); await opened;
    const action = startControl(f, 'cancel'); await settle();
    f.controls()[index].reject(Error('IMPORT_CONTROL_OUTCOME_UNCONFIRMED')); await action;
  }
  const last = f.open(ids[20]); await settle(); f.jobs()[20].respond(status(1, true, ids[20])); await last;
  await startControl(f, 'cancel'); assert.equal(f.controls().length, 20);
  assert.match(f.node('cdsNotice').textContent, /earlier jobs/); assert.equal(f.node('cdsNotice').children.length, 20);
  const earliest = f.node('cdsNotice').children[0].fire('click'); await settle();
  f.jobs()[21].respond(status(2, true, ids[0])); await earliest;
  assert.ok(f.node('cdsNotice').textContent.includes(ids[0])); assert.match(f.node('cdsNotice').textContent, /unconfirmed/);
  await f.node('cdsNotice').children[0].fire('click');
  const retried = f.open(ids[20]); await settle(); f.jobs()[22].respond(status(2, true, ids[20])); await retried;
  const action = startControl(f, 'cancel'); await settle(); assert.equal(f.controls().length, 21);
  f.controls()[20].reject(Error('IMPORT_CONTROL_OUTCOME_UNCONFIRMED')); await action;
});

test('an unconfirmed control warning retains only a safe code rather than raw exception or review text', async () => {
  const f = browser({deferControls: true}); const opened = f.open(); await settle();
  f.jobs()[0].respond(status(1)); await opened;
  const action = startControl(f, 'review'); await settle();
  f.controls()[0].reject(Error('PRIVATE_CLINICAL_TEXT')); await action; await settle();
  assert.match(f.node('cdsNotice').textContent, /IMPORT_CONTROL_OUTCOME_UNCONFIRMED/);
  assert.equal(f.node('cdsNotice').textContent.includes('PRIVATE'), false);
  assert.equal(f.node('cdsNotice').textContent.includes('Synthetic review reason'), false);
  const refreshed = f.open(); await settle(); f.jobs()[1].respond(status(2)); await refreshed;
  assert.match(f.node('cdsNotice').textContent, /IMPORT_CONTROL_OUTCOME_UNCONFIRMED/);
  assert.equal(f.node('cdsNotice').textContent.includes('PRIVATE'), false);
  assert.equal(f.controls().length, 1);
  await f.node('cdsNotice').children[0].fire('click');
  startControl(f, 'review'); await settle();
  f.controls()[1].respond({ok: false, code: 'REVIEW_VERSION_CONFLICT'}, 409); await settle();
  assert.match(f.node('cdsNotice').textContent, /REVIEW_VERSION_CONFLICT/);
  assert.equal(f.node('cdsNotice').textContent.includes('Synthetic review reason'), false);
});

test('a queued dismissal cannot erase a later current status failure or lose the original uncertain control warning', async () => {
  const f = browser({deferControls: true}); const opened = f.open(); await settle();
  f.jobs()[0].respond(status(1)); await opened;
  const action = startControl(f, 'cancel'); await settle(); f.controls()[0].reject(Error('CONTROL_UNCONFIRMED')); await action;
  const refreshed = f.open(); await settle(); f.jobs()[1].respond(status(2, false)); await refreshed;
  const dismiss = f.node('cdsNotice').children[0]; assert.ok(dismiss);
  f.takePoll()(); await settle(); f.jobs()[2].reject(Error('CURRENT_STATUS_UNAVAILABLE')); await settle();
  await dismiss.fire('click');
  assert.match(f.node('cdsNotice').textContent, /CURRENT_STATUS_UNAVAILABLE/);
  assert.equal(f.node('cdsNotice').hidden, false); assert.equal(f.node('jobOverview').dataset.fresh, 'false');
  const original = f.open(); await settle(); f.jobs()[3].respond(status(3)); await original;
  assert.match(f.node('cdsNotice').textContent, /Acknowledgement unconfirmed/);
  assert.equal(f.node('cdsNotice').children.length, 1); assert.equal(f.controls().length, 1);
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
