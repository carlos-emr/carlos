/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createHash} = require('node:crypto');
const h = require('./playwright-harness');

function validatePageEdit(request, name, revision) {
  const fields = new URLSearchParams(request.postData() || '');
  h.assert(request.method() === 'POST', 'Expected a native page-edit POST');
  for (const [key, value] of Object.entries({pdfAction: 'Rotate90', pdfName: name, pdfDir: 'File',
    pdfPageNumber: '1', defaultQueue: '1', queueList: '1', sourceRevision: revision})) {
    h.assert(fields.getAll(key).length === 1 && fields.get(key) === value, 'Page edit left its owned frozen intent');
  }
  h.assert(fields.getAll('queueId').length <= 1 && (!fields.has('queueId') || fields.get('queueId') === '1'),
    'Page edit changed its effective queue');
  h.assert(fields.getAll('CSRF-TOKEN').length === 1 && fields.get('CSRF-TOKEN'), 'Native page edit omitted its CSRF token');
  return fields;
}
function escape(value) {
  return String(value).replace(/[&<>"']/g, character => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[character]));
}
function waitingPage(fields, endpoint) {
  const url = new URL(endpoint);
  h.assert(url.pathname.endsWith('/documentManager/ViewIncomingDocs') && !url.search && !url.hash,
    'Native wait fixture requires the exact installed page-edit endpoint');
  const context = url.pathname.slice(0, -'/documentManager/ViewIncomingDocs'.length);
  h.assert(fields.getAll('pdfNo').length === 1 && /^[1-9]\d*$/.test(fields.get('pdfNo')),
    'Native wait fixture requires one positive document index');
  h.assert(fields.getAll('pdfDir').length === 1 && fields.get('pdfDir') === 'File'
    && fields.getAll('defaultQueue').length === 1 && fields.get('defaultQueue') === '1'
    && fields.getAll('queueList').length === 1 && fields.get('queueList') === '1'
    && fields.getAll('queueId').length <= 1 && (!fields.has('queueId') || fields.get('queueId') === '1'),
  'Native wait cancellation left the owned queue');
  const cancel = context + '/documentManager/ViewIncomingDocs?'
    + new URLSearchParams({queueId: '1', pdfDir: 'File', pdfNo: fields.get('pdfNo'), pdfPageNumber: '1'});
  return '<!doctype html><html><body><p role="status">Waiting for document capacity.</p>'
    + '<form id="incoming-page-edit-wait" data-accepted="false" method="post" action="' + escape(url.href) + '">'
    + [...fields].map(([key, value]) => '<input type="hidden" name="' + escape(key) + '" value="' + escape(value) + '">').join('')
    + '</form><a id="incoming-page-edit-cancel" href="' + escape(cancel) + '">Cancel waiting</a><script src="'
    + escape(context + '/js/incomingDocumentPageEditWait.js') + '"></script></body></html>';
}

/** Persist acceptance uncertainty before transport; browser closure cannot prove server drain. */
function createPageEditCleanupGuard({marker, source, owned, evidenceRoot = os.tmpdir()}) {
  const directory = fs.mkdtempSync(path.join(evidenceRoot, 'incoming-page-edit-recovery-'));
  fs.chmodSync(directory, 0o700);
  const journal = path.join(directory, 'journal.json');
  const state = {marker, source, state: 'not-forwarded', uncertain: false, files: []};
  function save() {
    const pending = journal + '.next';
    const fd = fs.openSync(pending, 'wx', 0o600);
    try {fs.writeFileSync(fd, JSON.stringify(state, null, 2) + '\n'); fs.fsyncSync(fd);}
    finally {fs.closeSync(fd);}
    fs.renameSync(pending, journal);
    const dir = fs.openSync(directory, 'r');
    try {fs.fsyncSync(dir);} finally {fs.closeSync(dir);}
  }
  save();
  return {
    journal,
    forward(request) {
      h.assert(state.state === 'not-forwarded', 'Refusing another forwarded native edit');
      state.files = owned.map(file => {
        if (!fs.existsSync(file)) return {file, absent: true};
        const stat = fs.lstatSync(file);
        h.assert(stat.isFile() && !stat.isSymbolicLink(), 'Owned incoming fixture identity changed');
        return {file, size: stat.size, inode: stat.ino, device: stat.dev, mode: stat.mode,
          uid: stat.uid, gid: stat.gid, mtimeMs: stat.mtimeMs,
          sha256: createHash('sha256').update(fs.readFileSync(file)).digest('hex')};
      });
      state.request = {url: request.url(), method: request.method(),
        bodySha256: createHash('sha256').update(request.postData() || '').digest('hex')};
      state.state = 'forwarded'; save();
    },
    completed() {state.state = 'response-completed'; save();},
    unknown(reason) {state.uncertain = true; state.reason = reason; save();},
    pending() {return state.state === 'forwarded';},
    assertCleanup() {
      h.assert(!state.uncertain && state.state !== 'forwarded',
        `Incoming mutation completion is unknown; retain all fixtures and inspect ${journal}`);
    },
  };
}

function removeInjectedFailures(recorder, proven, firstConsole) {
  h.assert(proven.size === 5 && [...proven].every(entry => entry.status === 503 && entry.method === 'POST'),
    'Expected exactly five proven injected native POST responses');
  h.assert([...proven].every(entry => recorder.badResponses.includes(entry)), 'Injected response evidence was lost');
  recorder.badResponses = recorder.badResponses.filter(entry => !proven.has(entry));
  const counts = new Map();
  for (const entry of proven) {
    const key = JSON.stringify([entry.label, entry.url]); counts.set(key, (counts.get(key) || 0) + 1);
  }
  recorder.consoleIssues = recorder.consoleIssues.filter((entry, index) => {
    const key = JSON.stringify([entry.label, entry.location?.url]);
    if (index < firstConsole || !/^Failed to load resource:.*503/.test(entry.text) || !counts.get(key)) return true;
    counts.set(key, counts.get(key) - 1); return false;
  });
}

async function withDeadline(promise, milliseconds) {
  let timer;
  try {return await Promise.race([promise, new Promise((resolve, reject) => {
    timer = setTimeout(() => reject(new Error('Native edit response did not finish')), milliseconds);
  })]);} finally {clearTimeout(timer);}
}

/** Inject only unaccepted admission responses; final POST reaches the installed application. */
async function checkIncomingPageEditCapacity(session, page, name, source, click, guard) {
  h.assert(guard && typeof guard.assertCleanup === 'function', 'Native edit requires its caller cleanup guard');
  const original = fs.readFileSync(source), revision = createHash('sha256').update(original).digest('hex');
  const endpoint = await page.locator('form[name="PdfInfoForm"]').evaluate(form => form.action);
  const pattern = url => url.href === endpoint;
  const bodies = [], injected = new Set(), proven = new Set();
  let unsafe = false, stopping = false, finished = false, forwarded;
  const firstConsole = session.recorder.consoleIssues.length;
  const responseSeen = response => {
    if (!injected.has(response.request()) || response.status() !== 503) return;
    // The harness listener was registered first and just appended this response's entry.
    const entry = session.recorder.badResponses.at(-1);
    if (entry && entry.url === response.url() && entry.method === 'POST' && entry.status === 503) proven.add(entry);
  };
  const requestFailed = request => {
    if (request === forwarded) {
      try {guard.unknown('Forwarded native request transport failed');}
      catch (error) {unsafe = true;} // The durable pre-forward journal still records unresolved work.
    }
  };
  const handler = async route => {
    try {
      if (stopping) return await route.abort('blockedbyclient');
      if (route.request().method() !== 'POST') return await route.continue();
      const fields = validatePageEdit(route.request(), name, revision);
      const body = route.request().postData();
      h.assert(!bodies.length || body === bodies[0], 'Native edit changed its frozen POST snapshot');
      bodies.push(body);
      if (bodies.length <= 5) {
        h.assert(fs.readFileSync(source).equals(original), 'Unaccepted native admission changed source bytes');
        injected.add(route.request());
        return await route.fulfill({status: 503, contentType: 'text/html; charset=UTF-8',
          headers: {'Retry-After': '1', 'Cache-Control': 'no-store'}, body: waitingPage(fields, endpoint)});
      }
      h.assert(bodies.length === 6, 'Native wait submitted more than one accepted edit');
      guard.forward(route.request());
      forwarded = route.request();
      return await route.continue();
    } catch (error) {
      unsafe = true;
      try {
        if (forwarded === route.request()) guard.unknown('Native forwarding failed after acceptance became possible');
      } finally {await route.abort('blockedbyclient').catch(() => {});}
    }
  };
  page.on('response', responseSeen);
  page.on('requestfailed', requestFailed);
  await page.route(pattern, handler);
  try {
    const complete = page.waitForResponse(response => response.request() === forwarded, {timeout: 120000});
    complete.catch(() => {});
    await click();
    await page.locator('#incoming-page-edit-wait').waitFor({state: 'attached'});
    const response = await complete;
    const failure = await withDeadline(response.finished(), 120000);
    h.assert(!failure, 'Native edit response transport did not complete');
    guard.completed();
    guard.assertCleanup();
    h.assert(response.status() === 200, 'Installed native edit did not succeed');
    await page.locator('#SelectPageList').waitFor();
    await page.waitForLoadState('networkidle');
    h.assert(!unsafe && bodies.length === 6 && new Set(bodies).size === 1,
      'Native page edit did not preserve one frozen request across five refusals');
    removeInjectedFailures(session.recorder, proven, firstConsole);
    finished = true;
  } finally {
    stopping = true;
    let closed = finished;
    try {
      if (guard.pending()) guard.unknown('Native response completion was not proven before fixture teardown');
    } finally {
      if (!finished) {
        // Keep interception installed until this page cannot schedule another native retry.
        try {await page.close({runBeforeUnload: false}); closed = true;}
        catch (error) {guard.unknown('Could not close the native retry page; retain fixtures');}
      }
      if (closed) {
        await page.unroute(pattern, handler);
        page.off('response', responseSeen);
        page.off('requestfailed', requestFailed);
      }
    }
  }
}
module.exports = {validatePageEdit, waitingPage, createPageEditCleanupGuard, removeInjectedFailures, checkIncomingPageEditCapacity};
