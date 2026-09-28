/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const path = require('node:path');
const h = require('./playwright-harness');
const {captureMetadataState, createMetadataTransport, freezeMetadataIntent, checkMetadataUiAttempt} = require('./document-metadata-check');

function metadataEnabled(value = process.env.STORED_DOCUMENT_METADATA) {
  assert(value === undefined || value === 'false' || value === 'true', 'STORED_DOCUMENT_METADATA must be true or false');
  return value === 'true';
}
function recorderSnapshot(recorder) {
  return Object.fromEntries(['badResponses', 'requestFailures', 'consoleIssues'].map(key => [key, new Set(recorder[key])]));
}
/** Only entries belonging to the one proven intercepted request may be reconciled. */
function reconcileFault({recorder, before, label, intent, mode, counts, requests, responses, failures}) {
  assert(['rejected500', 'html200', 'lostBeforeForward'].includes(mode));
  assert.equal(requests.size, 1); assert.deepEqual(counts, {calls: 1, forwarded: 0, settled: true});
  const [request] = requests;
  assert.equal(request.url(), intent.endpoint); assert.equal(request.method(), 'POST');
  if (mode === 'lostBeforeForward') {
    assert.equal(responses.length, 0); assert.deepEqual(failures, [request]);
  } else {
    assert.equal(failures.length, 0); assert.equal(responses.length, 1);
    assert.equal(responses[0].request, request);
    assert.equal(responses[0].status, mode === 'rejected500' ? 500 : 200);
  }
  const added = key => recorder[key].filter(entry => !before[key].has(entry) && entry.label === label);
  const bad = added('badResponses').filter(entry => entry.url === intent.endpoint && entry.method === 'POST'
    && entry.status === 500 && /^application\/json(?:\s*;|$)/i.test(entry.contentType || ''));
  assert.equal(bad.length, mode === 'rejected500' ? 1 : 0, 'Injected fault response ledger mismatch');
  const failed = added('requestFailures').filter(entry => entry.url === intent.endpoint && entry.resourceType === request.resourceType()
    && entry.errorText === request.failure()?.errorText);
  assert.equal(failed.length, mode === 'lostBeforeForward' ? 1 : 0, 'Injected fault request ledger mismatch');
  const consoleEntries = added('consoleIssues').filter(entry => entry.location?.url === intent.endpoint && entry.type === 'error'
    && (mode === 'rejected500' ? /^Failed to load resource:.*\b500\b/.test(entry.text || '')
      : mode === 'lostBeforeForward' && /^Failed to load resource:.*\bERR_FAILED\b/.test(entry.text || '')));
  assert(consoleEntries.length <= (mode === 'html200' ? 0 : 1), 'Unexpected repeated injected resource errors');
  recorder.badResponses = recorder.badResponses.filter(entry => !bad.includes(entry));
  recorder.requestFailures = recorder.requestFailures.filter(entry => !failed.includes(entry));
  recorder.consoleIssues = recorder.consoleIssues.filter(entry => !consoleEntries.includes(entry));
}
function faultLedger(page, recorder, label, intent, transport, mode) {
  const before = recorderSnapshot(recorder), responses = [], failures = [];
  const response = value => {if (transport.requests().has(value.request())) responses.push({request: value.request(), status: value.status()});};
  const failed = value => {if (transport.requests().has(value)) failures.push(value);};
  page.on('response', response); page.on('requestfailed', failed);
  return {
    finish() {reconcileFault({recorder, before, label, intent, mode, counts: transport.counts(), requests: transport.requests(), responses, failures});},
    stop() {page.off('response', response); page.off('requestfailed', failed);},
  };
}
function actionIntent(kind, identity, endpoint, token) {
  assert(['queue', 'unlink'].includes(kind));
  const fields = kind === 'queue' ? {method: 'updateDocStatusInQueue', docid: identity.document, 'CSRF-TOKEN': token}
    : {method: 'removeLinkFromDocument', docType: 'DOC', docId: identity.document, providerNo: identity.provider, 'CSRF-TOKEN': token};
  return Object.freeze({kind, document: identity.document, patient: identity.patient, provider: identity.provider,
    endpoint, token, body: new URLSearchParams(fields).toString()});
}
async function waitStatus(page, id, state) {
  await page.waitForFunction(({id, state}) => {
    const node = document.getElementById('document-metadata-status-' + id);
    return node && node.textContent === window.CarlosDocumentMutationMessages?.[state];
  }, {id, state});
}

/** Dedicated opt-in phase: queue/provider dispositions are not shared with page-mutation tests. */
async function runInstalledMetadata(session, fixture, schema) {
  const id = fixture.sourceId, root = String(session.config.baseUrl).replace(/\/?$/, '/');
  const manage = new URL('documentManager/ManageDocument', root).href, queue = new URL('documentManager/inboxManage', root).href;
  const identity = {document: id, patient: session.patient, provider: session.provider, marker: session.marker,
    file: fixture.sourceFile, store: path.dirname(fixture.sourceFile)};
  const contexts = [], pages = new Set(), pending = new Set(), transports = new Set(), bindings = new WeakMap();
  let guardFailure, classification;
  function wire(page) {
    pages.add(page); page.setDefaultTimeout(120000);
    h.wireStrictPage(page, 'stored-metadata', session.recorder);
    page.on('request', request => pending.add(request));
    page.on('requestfinished', request => pending.delete(request));
    page.on('requestfailed', request => {
      pending.delete(request);
      if ([...transports].some(value => value.ownsForwarded(request))) {
        try {fixture.transportFailed();} catch (error) {guardFailure ||= error;}
      }
    });
  }
  function transport(intent, mode = 'real') {
    const value = createMetadataTransport({intent, fixture, mode,
      capture: () => captureMetadataState(session.sql, identity, schema, intent.kind),
      accept: receipt => fixture.acceptMetadataTransition(receipt)});
    transports.add(value); return value;
  }
  const guard = async route => {
    try {
      const request = route.request(), url = new URL(request.url());
      if (!['GET', 'HEAD'].includes(request.method())) {
        const page = request.frame().page(), bound = bindings.get(page)?.get(request.url());
        assert(bound && request.method() === 'POST', 'Unexpected installed metadata write');
        await bound.route(route); return;
      }
      if (url.pathname.endsWith('/documentManager/ManageDocument')) {
        assert.equal(url.searchParams.getAll('method').length, 1);
        if (url.searchParams.get('method') === 'searchDocumentDescriptions') {
          assert.deepEqual([...url.searchParams.keys()].sort(), ['method', 'term']);
          assert(url.searchParams.get('term').startsWith(session.marker + ' metadata '), 'Description lookup escaped the synthetic fixture');
        } else {
          assert.equal(url.searchParams.getAll('doc_no').length, 1); assert.equal(url.searchParams.get('doc_no'), id);
          assert(['viewDocPage', 'showPage', 'display'].includes(url.searchParams.get('method')), 'Unexpected metadata document read');
        }
      }
      assert(!['/documentManager/SplitDocument', '/documentManager/inboxManage', '/oscarMDS/UpdateStatus', '/oscarMDS/FileLabs']
        .some(action => url.pathname.endsWith(action)), 'Metadata phase attempted another action through GET');
      if (url.pathname.endsWith('/documentManager/ViewShowDocument')) {
        assert.deepEqual(url.searchParams.getAll('segmentID'), [id]);
      }
      await route.continue();
    } catch (error) {guardFailure ||= error; await route.abort('blockedbyclient').catch(() => {});}
  };
  function bind(page, endpoint, value) {
    if (!bindings.has(page)) bindings.set(page, new Map());
    bindings.get(page).set(endpoint, value);
  }
  async function ownContext(existing) {
    const context = existing || await h.newContext(session.context.browser(), session.config);
    contexts.push({context, existing: Boolean(existing)}); context.on('page', wire);
    if (!existing) await h.login(context, session.config, session.recorder);
    await context.route('**/*', guard); return context;
  }
  async function viewer(context, label, popup = false) {
    const url = new URL(`documentManager/ViewShowDocument?segmentID=${id}&inWindow=true`, root).href;
    let page;
    if (popup) {
      const opener = await context.newPage(), opened = context.waitForEvent('page'); opened.catch(() => {});
      await opener.evaluate(url => {window.open(url, 'owned-metadata-queue');}, url);
      page = await opened;
    } else {page = await context.newPage(); await page.goto(url);}
    h.relabelStrictPage(page, label);
    await page.waitForLoadState('networkidle');
    await page.waitForFunction(id => document.getElementById('forms_' + id)
      && document.querySelector('input[name="CSRF-TOKEN"]')?.value && window.CarlosDocumentMetadata, id);
    assert.equal(await page.locator('#demofind' + id).inputValue(), identity.patient);
    assert.equal(await page.locator('#forms_' + id + ' [name="flagproviders"]').count(), 0);
    return page;
  }
  async function fill(page, suffix) {
    const field = page.locator('#docType_' + id);
    if (!classification) {
      const options = await field.locator('option').evaluateAll(nodes => nodes.map(node => node.value));
      classification = options.includes('Consult') ? 'Consult' : options.find(value => value && value !== 'Lab');
      assert(classification && classification !== 'Lab', 'Installed metadata check needs another real classification');
    }
    await field.selectOption(classification);
    await page.locator('#docDesc_' + id).fill(session.marker + ' metadata ' + suffix);
    await page.waitForLoadState('networkidle');
    return freezeMetadataIntent(page, identity, manage);
  }
  async function saveAttempt(page, label, mode, suffix) {
    const intent = await fill(page, suffix), current = transport(intent, mode);
    const ledger = mode === 'real' ? null : faultLedger(page, session.recorder, label, intent, current, mode);
    try {await checkMetadataUiAttempt({page, intent, transport: current, mode}); ledger?.finish();}
    finally {ledger?.stop();}
    assert.equal(current.counts().calls, 1); fixture.assertUnchanged();
    await page.close();
  }
  try {
    const first = await ownContext(session.context);
    for (const mode of ['rejected500', 'html200', 'lostBeforeForward']) {
      const label = 'stored-metadata-' + mode;
      await session.step('metadata ' + mode + ' preserves the draft without replay', async () => {
        const page = await viewer(first, label); await saveAttempt(page, label, mode, mode);
      });
    }
    const second = await ownContext();
    const cookies = await Promise.all([first.cookies(root), second.cookies(root)]);
    const sessions = cookies.map(list => list.filter(cookie => cookie.name === 'JSESSIONID'));
    assert(sessions.every(list => list.length === 1 && list[0].value)
      && sessions[0][0].value !== sessions[1][0].value, 'Metadata contexts must have independent authenticated sessions');
    // Both forms observe the original classification before either save.
    const pageA = await viewer(first, 'stored-metadata-session-a'), pageB = await viewer(second, 'stored-metadata-session-b');
    await session.step('two independent sessions save real metadata without duplicating DOC routing', async () => {
      await saveAttempt(pageA, 'stored-metadata-session-a', 'real', 'session-a');
      await saveAttempt(pageB, 'stored-metadata-session-b', 'real', 'session-b');
    });
    await session.step('confirmed metadata gates queue completion and manual retry sends only the queue phase', async () => {
      const label = 'stored-metadata-queue', page = await viewer(first, label, true);
      const metadataIntent = await fill(page, 'queue'), metadata = transport(metadataIntent);
      const queueIntent = actionIntent('queue', identity, queue, metadataIntent.token), refused = transport(queueIntent, 'rejected500');
      const ledger = faultLedger(page, session.recorder, label, queueIntent, refused, 'rejected500');
      bind(page, manage, metadata);
      bind(page, queue, {route: async route => {
        assert.equal(metadata.durablyAccepted(), true, 'Queue update preceded durable metadata acceptance');
        assert.equal(await page.locator('#saved' + id).inputValue(), 'true');
        await refused.route(route);
      }});
      try {
        await page.locator('#saveNext' + id).click(); await waitStatus(page, id, 'queueRejected');
        await metadata.drain(); await refused.drain();
        assert(!page.isClosed()); assert.equal(await page.locator('#saved' + id).inputValue(), 'true');
        assert.equal(await page.locator('#saveSucessMsg_' + id).isVisible(), true);
        fixture.assertUnchanged(); ledger.finish();
        const accepted = transport(queueIntent); bind(page, queue, accepted);
        const closed = page.waitForEvent('close'); closed.catch(() => {});
        await page.locator('#saveNext' + id).click(); await closed; await accepted.drain();
        assert.deepEqual(metadata.counts(), {calls: 1, forwarded: 1, settled: true});
        assert.deepEqual(accepted.counts(), {calls: 1, forwarded: 1, settled: true});
        fixture.assertUnchanged();
      } finally {ledger.stop();}
    });
    for (const mode of ['rejected500', 'html200', 'lostBeforeForward', 'real']) {
      await session.step('provider unlink ' + mode + ' removes the anchor only after confirmed acceptance', async () => {
        const label = 'stored-metadata-unlink-' + mode, page = await viewer(first, label);
        const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
        const intent = actionIntent('unlink', identity, manage, token), current = transport(intent, mode);
        const anchor = page.locator('#forms_' + id + ' a[onclick*="removeLink("]');
        assert.equal(await anchor.count(), 1);
        const expected = new RegExp("^removeLink\\('DOC',\\s*'" + id + "',\\s*'" + identity.provider + "',\\s*this\\);return false;$");
        assert(expected.test((await anchor.getAttribute('onclick')).trim()), 'Unlink anchor is not the exact owned provider');
        bind(page, manage, current);
        const ledger = mode === 'real' ? null : faultLedger(page, session.recorder, label, intent, current, mode);
        try {
          await anchor.click(); await waitStatus(page, id, mode === 'real' ? 'saved' : mode === 'rejected500' ? 'rejected' : 'uncertain');
          await current.drain();
          assert.equal(await anchor.count(), mode === 'real' ? 0 : 1);
          if (mode === 'html200' || mode === 'lostBeforeForward') {
            assert.equal(await page.locator('#save' + id).isDisabled(), true);
            await anchor.click();
          }
          await page.waitForTimeout(1200); assert.equal(current.counts().calls, 1);
          ledger?.finish(); fixture.assertUnchanged();
        } finally {ledger?.stop();}
        await page.close();
      });
    }
    if (guardFailure) throw guardFailure;
    console.log('  COVERAGE stored-metadata: real two-session classification saves, DOC deduplication, queue-only manual retry and provider unlink; independent denied-provider credentials remain required for installed cross-user denial coverage');
  } finally {
    // Freeze producers before draining. Any forwarded uncertainty stays sticky.
    for (const page of pages) if (!page.isClosed()) await page.evaluate(() => window.dispatchEvent(new Event('pagehide'))).catch(() => {});
    const results = await Promise.allSettled([...transports].map(value => value.drain()));
    const deadline = Date.now() + 120000;
    while (pending.size && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
    assert.equal(pending.size, 0, 'Metadata requests did not drain; retain fixture');
    for (const page of pages) if (!page.isClosed()) await page.close();
    for (const {context, existing} of contexts.reverse()) {context.off('page', wire); await context.unroute('**/*', guard); if (!existing) await context.close();}
    fixture.closeAfterDrain();
    const failed = results.find(result => result.status === 'rejected');
    if (failed) throw failed.reason;
    if (guardFailure) throw guardFailure;
  }
}
module.exports = {metadataEnabled, recorderSnapshot, reconcileFault, actionIntent, runInstalledMetadata};
