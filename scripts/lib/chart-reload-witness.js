/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const h = require('./playwright-harness');
const { createHash } = require('node:crypto');
const hash = value => createHash('sha256').update(value).digest('hex');
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** Passive Chromium observations only: never intercept, replay or change traffic. */
async function observeOwnedReload(page, form) {
  const cdp = await page.context().newCDPSession(page);
  const before = (await cdp.send('Page.getFrameTree')).frameTree.frame;
  const operations = new Map();
  let closed = false;
  const handlers = {
    'Network.requestWillBeSent': event => {
      if (event.type !== 'Ping' || event.request.url !== form.endpoint) return;
      if (operations.has(event.requestId)) { operations.get(event.requestId).duplicateRequest = true; return; }
      const fields = event.request.postData === undefined ? null : new URLSearchParams(event.request.postData);
      operations.set(event.requestId, { requestId: event.requestId, frameId: event.frameId,
        loaderId: event.loaderId, timestamp: event.timestamp, wallTime: event.wallTime,
        method: event.request.method, type: event.type, endpoint: event.request.url,
        bodyAvailable: fields !== null, bodyMatches: fields === null ? null : same([...fields.keys()].sort(),
          ['CSRF-TOKEN', 'demographicNo', 'method', 'noteId', 'providerNo'])
          && fields.get('method') === 'releaseNoteLock' && fields.get('demographicNo') === form.patient
          && fields.get('providerNo') === form.provider && fields.get('noteId') === form.noteId
          && fields.get('CSRF-TOKEN') === form.csrf });
    },
    'Network.responseReceivedExtraInfo': event => {
      const operation = operations.get(event.requestId); if (!operation) return;
      operation.ackCount = (operation.ackCount || 0) + 1;
      operation.acknowledgment = { requestId: event.requestId, status: event.statusCode,
        application: Object.keys(event.headers || {}).some(name => name.toLowerCase() === h.APPLICATION_HEADER) };
    },
    'Network.loadingFinished': event => {
      const operation = operations.get(event.requestId); if (operation) {
        operation.terminalCount = (operation.terminalCount || 0) + 1; operation.terminal = { kind: 'finished' };
      }
    },
    'Network.loadingFailed': event => {
      const operation = operations.get(event.requestId); if (operation) {
        operation.terminalCount = (operation.terminalCount || 0) + 1; operation.terminal = {
          kind: 'failed', errorText: event.errorText, canceled: event.canceled, blockedReason: event.blockedReason || null };
      }
    },
  };
  for (const [event, handler] of Object.entries(handlers)) cdp.on(event, handler);
  await cdp.send('Network.enable', { maxPostDataSize: 65536 });
  return {
    async finish(count) {
      const deadline = Date.now() + 15000;
      while (operations.size < count || [...operations.values()].some(op => !op.terminal || !op.acknowledgment)) {
        h.assert(Date.now() < deadline, 'The passive native-reload operations did not terminate with application acknowledgments');
        await new Promise(resolve => setTimeout(resolve, 25));
      }
      const after = (await cdp.send('Page.getFrameTree')).frameTree.frame;
      return { oldFrame: before.id, oldLoader: before.loaderId, newFrame: after.id, newLoader: after.loaderId,
        operations: [...operations.values()], transport: 'unaltered native browser requests; passive CDP only' };
    },
    async close() {
      if (closed) return; closed = true;
      for (const [event, handler] of Object.entries(handlers)) cdp.off(event, handler);
      await cdp.detach();
    },
  };
}

/** Select only exact native abort entries after their shared transport proof. */
function selectProvenAbortEntries(recorder, { page, form, requests, witness }) {
  h.assert(witness.oldFrame === witness.newFrame && witness.oldLoader && witness.newLoader
    && witness.oldLoader !== witness.newLoader && requests.length >= 1 && requests.length <= 2
    && witness.operations.length === requests.length
    && new Set(witness.operations.map(operation => operation.requestId)).size === requests.length,
  'Native reload witness is missing, ambiguous or not a new owned document');
  for (const operation of witness.operations) {
    h.assert(operation.frameId === witness.oldFrame && operation.loaderId === witness.oldLoader
      && operation.endpoint === form.endpoint && operation.method === 'POST' && operation.type === 'Ping'
      && !operation.duplicateRequest && operation.bodyMatches !== false && operation.terminal
      && operation.terminalCount === 1 && operation.ackCount === 1
      && operation.acknowledgment?.requestId === operation.requestId
      && operation.acknowledgment.status === 200 && operation.acknowledgment.application,
    'A native old-document operation lacks its exact application200 acknowledgment');
  }
  const canceled = witness.operations.filter(operation => operation.terminal.kind === 'failed');
  const failed = requests.filter(item => item.terminal === 'failed');
  h.assert(failed.length === canceled.length && failed.length <= 1
    && requests.every(item => ['finished', 'failed'].includes(item.terminal)),
  'Native terminal events are missing or cannot be associated uniquely');
  const entries = [];
  for (const item of requests) {
    h.assert(item.request.frame().page() === page && item.request.method() === 'POST'
      && item.request.resourceType() === 'ping' && item.request.url() === form.endpoint,
    'Native reload Request lost its exact owned page, method or signed endpoint');
  }
  for (const item of failed) {
    const op = canceled[0]; const entry = item.rawFailure;
    h.assert(op.terminal.errorText === 'net::ERR_ABORTED' && op.terminal.canceled === true
      && op.terminal.blockedReason === null && item.request.failure()?.errorText === 'net::ERR_ABORTED'
      && entry && recorder.requestFailures.filter(value => value === entry).length === 1
      && entry.url === form.endpoint && entry.resourceType === 'ping' && entry.errorText === 'net::ERR_ABORTED',
    'Only the uniquely associated original native navigation-abort entry may be acknowledged');
    entries.push(entry);
  }
  return entries;
}

/** Validate the complete witness before selecting any exact recorder entry. */
function provenReloadAborts(recorder, { page, form, reopened, requests, witness, recovered }) {
  h.assert(recovered && reopened.timeOrigin !== form.timeOrigin && reopened.url === form.url
    && reopened.patient === form.patient && reopened.program === form.program && reopened.token === form.token,
  'Native reload acknowledgment requires successful same-context raw-draft recovery in a new document');
  const entries = selectProvenAbortEntries(recorder, { page, form, requests, witness });
  return { entries, audit: { phase: 'owned same-URL native reload', blobBytesUnavailable: witness.operations.some(op => !op.bodyAvailable),
    endpointSha256: hash(form.endpoint), oldTimeOrigin: form.timeOrigin, newTimeOrigin: reopened.timeOrigin,
    rawFailures: entries.map(entry => ({ ...entry })), witness } };
}

/** Print completion has a different destination and no raw-draft retry contract. */
function provenPrintNavigationAborts(recorder, { page, form, arrived, requests, witness, state }) {
  const destination = new URL(arrived.url);
  const source = new URL(form.endpoint);
  h.assert(same([...source.searchParams.keys()], ['chartContext']) && typeof form.token === 'string'
    && form.token.length > 0 && source.searchParams.get('chartContext') === form.token,
  'Print completion native endpoint has an unknown or repeated chart authority');
  h.assert(source.pathname.endsWith('/CaseManagementEntry') && destination.origin === source.origin
    && destination.pathname === source.pathname.replace(/\/CaseManagementEntry$/, '/demographic/DemographicEdit')
    && same([...destination.searchParams.keys()].sort(), ['dboperation', 'demographic_no', 'displaymode'])
    && destination.searchParams.get('demographic_no') === form.patient
    && destination.searchParams.get('displaymode') === 'edit'
    && destination.searchParams.get('dboperation') === 'search_detail'
    && arrived.status === 200 && arrived.application === true
    && Number.isFinite(arrived.timeOrigin) && Number.isFinite(form.timeOrigin) && arrived.timeOrigin > form.timeOrigin,
  'Print completion did not navigate to the exact owned master record in a new application document');
  const sessions = new Set([form.session, form.session?.split('.')[0]]);
  const owned = state.beforeLocks.filter(row => sessions.has(row[1]) && row[3] === form.provider
    && row[4] === form.noteId && row[5] === form.patient);
  h.assert(typeof form.session === 'string' && form.session.length > 0 && owned.length === 1
    && same(state.before, state.after)
    && same(state.beforeLocks.filter(row => row !== owned[0]), state.afterLocks)
    && new Set(requests.map(item => item.request)).size === requests.length,
  'Print completion changed clinical state, failed to remove exactly its authenticated lock, or changed another lock');
  const entries = selectProvenAbortEntries(recorder, { page, form, requests, witness });
  return { entries, audit: { phase: 'owned print completion navigation', blobBytesUnavailable: witness.operations.some(op => !op.bodyAvailable),
    endpointSha256: hash(form.endpoint), oldTimeOrigin: form.timeOrigin, newTimeOrigin: arrived.timeOrigin,
    rawFailures: entries.map(entry => ({ ...entry })), witness } };
}

module.exports = { observeOwnedReload, provenReloadAborts, provenPrintNavigationAborts };
