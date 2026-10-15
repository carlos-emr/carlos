/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {createHash} = require('node:crypto');
const h = require('./playwright-harness');
const {prepareIncomingFilingProgram} = require('./incoming-filing-program-fixture');

function validateFilingRequest(request, owned) {
  const fields = new URLSearchParams(request.postData() || '');
  h.assert(request.method() === 'POST' && request.headers()['x-carlos-incoming-filing'] === 'bounded-v1',
    'Filing must use the explicit pre-acceptance contract');
  for (const [key, value] of Object.entries({method: 'addIncomingDocument', pdfName: owned.name,
    demog: owned.patient, documentDescription: owned.description, queueId: '1', pdfDir: 'File'})) {
    h.assert(fields.getAll(key).length === 1 && fields.get(key) === value, 'Refusing a filing outside the owned fixture');
  }
  h.assert(fields.getAll('CSRF-TOKEN').length === 1 && fields.get('CSRF-TOKEN'), 'Filing omitted its CSRF token');
  h.assert(fields.getAll('sourceRevision').length === 1 && /^[a-f0-9]{64}$/.test(fields.get('sourceRevision'))
    && fields.get('sourceRevision') === owned.revision, 'Filing changed its observed source revision');
  return request.postData();
}

function cleanupFiling(session, description, store) {
  const {sql, patient, marker, provider} = session;
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`) === '1',
    'Filing patient ownership changed');
  const rows = sql.rows(`SELECT document_no,docfilename FROM document WHERE docdesc=${h.sqlString(description)} AND doccreator=${h.sqlString(provider)}`);
  for (const [id, filename] of rows) {
    h.assert(/^[1-9]\d*$/.test(id), 'Invalid filing fixture identity');
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no=${id} AND (module!='demographic' OR module_id!=${patient})`) === '0',
      'Filing acquired an unowned patient link');
    h.assert(filename.includes(marker) && path.basename(filename) === filename, 'Stored filing filename ownership changed');
    const file = path.join(store, filename);
    if (fs.existsSync(file)) h.assert(fs.lstatSync(file).isFile() && !fs.lstatSync(file).isSymbolicLink()
      && fs.realpathSync(file).startsWith(store + path.sep), 'Stored filing path ownership changed');
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient} AND provider_no='-1' AND note LIKE ${h.sqlString(`%${description}%`)}`).map(row => row[0]);
    h.assert(notes.every(note => /^[1-9]\d*$/.test(note)), 'Invalid filing note identity');
    if (notes.length) {
      const ids = notes.join(',');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN(${ids}) AND (table_name!=5 OR table_id!=${id})`) === '0',
        'Filing note acquired an unowned document link');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN(${ids}); DELETE FROM casemgmt_note_ext WHERE note_id IN(${ids}); DELETE FROM casemgmt_note_link WHERE note_id IN(${ids}); DELETE FROM casemgmt_note WHERE note_id IN(${ids}) AND demographic_no=${patient}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${id} AND lab_type='DOC' AND demographic_no!=${patient}`) === '0',
      'Filing acquired an unowned routing link');
    sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${id} AND lab_type='DOC'; DELETE FROM patientLabRouting WHERE lab_no=${id} AND lab_type='DOC' AND demographic_no=${patient}; DELETE FROM document_storage WHERE documentNo=${id}; DELETE FROM ctl_document WHERE document_no=${id} AND module='demographic' AND module_id=${patient}; DELETE FROM document WHERE document_no=${id} AND docdesc=${h.sqlString(description)} AND doccreator=${h.sqlString(provider)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${id}`) === '0', 'Owned filing cleanup failed');
    if (fs.existsSync(file)) fs.unlinkSync(file);
  }
}

/** Capture server acceptance before exposing it to the UI that immediately navigates. */
function createFilingForwarder(owned, expectedBody, guard, onBusy = () => {}) {
  h.assert(guard && typeof guard.forward === 'function', 'Filing requires a durable cleanup guard');
  let sent = false, pending, inFlight = false, terminal = false, busy = 0, failure;
  let resolveResult, rejectResult;
  const result = new Promise((resolve, reject) => {resolveResult = resolve; rejectResult = reject;});
  result.catch(() => {});
  async function transmit(route) {
    let response;
    try {
      const body = validateFilingRequest(route.request(), owned);
      h.assert(body === expectedBody(), 'Real filing changed its frozen form input');
      if (!sent) {guard.forward(route.request()); sent = true;}
      // Each UI retry sends one actual POST. Never follow redirects or replay
      // transport failures; only a proven pre-acceptance response permits retry.
      response = await route.fetch({maxRedirects: 0, maxRetries: 0, timeout: 90000});
      const bytes = await response.body();
      const headers = response.headers();
      h.assert(/^application\/json(?:\s*;|$)/i.test(headers['content-type'] || ''),
        'Real filing returned an unconfirmed response');
      const data = JSON.parse(bytes.toString('utf8'));
      if (response.status() === 503) {
        const retryAfter = headers['retry-after'];
        h.assert(data && data.success === false && data.accepted === false && data.retryable === true
          && /^[1-9]\d*$/.test(retryAfter || '') && Number(retryAfter) <= 60,
        'Real filing returned an invalid pre-acceptance busy response');
        onBusy(route.request());
        await route.fulfill({response, body: bytes});
        await response.dispose(); response = undefined;
        busy++;
        return data;
      }
      h.assert(response.status() === 200 && data && data.success === true && data.accepted === true
        && Number.isSafeInteger(data.documentNo) && data.documentNo > 0,
      'Installed filing did not confirm acceptance');
      terminal = true;
      // Capture first; forwarding this exact response may immediately navigate.
      await route.fulfill({response, body: bytes});
      await response.dispose(); response = undefined;
      guard.completed();
      resolveResult(data);
      return data;
    } catch (error) {
      terminal = true; failure = error;
      if (guard.pending()) guard.unknown('Filing transport or acceptance could not be confirmed');
      rejectResult(error);
      await route.abort('failed').catch(() => {});
      throw error;
    } finally {
      try {if (response) await response.dispose();}
      finally {inFlight = false;}
    }
  }
  return {
    result, busyCount: () => busy,
    forward(route) {
      if (inFlight || terminal) return Promise.reject(new Error('Refusing a concurrent or second accepted real filing'));
      inFlight = true;
      pending = transmit(route);
      pending.catch(() => {});
      return pending;
    },
    async drain() {if (pending) await pending; if (failure) throw failure;},
  };
}

/** Claim individual recorder entries only for responses to proven busy requests. */
function createFilingBusyRecorder(recorder, endpoint) {
  const before = new Set(recorder.badResponses), firstConsole = new Set(recorder.consoleIssues);
  const requests = new Set(), observed = new Set(), proven = new Set();
  return {
    expect(request) {
      h.assert(!requests.has(request), 'Busy request was counted twice'); requests.add(request);
    },
    observe(response) {
      const request = response.request();
      if (!requests.has(request)) return;
      h.assert(!observed.has(request) && response.status() === 503
        && request.method() === 'POST' && response.url() === endpoint
        && /^application\/json(?:\s*;|$)/i.test(response.headers()['content-type'] || ''),
      'Validated busy request has an unexpected browser response');
      observed.add(request);
      const entry = [...recorder.badResponses].reverse().find(value => !before.has(value) && !proven.has(value)
        && value.status === 503 && value.method === 'POST' && value.url === endpoint
        && /^application\/json(?:\s*;|$)/i.test(value.contentType || ''));
      h.assert(entry, 'Validated busy response is missing from strict recorder');
      proven.add(entry);
    },
    finish(expected) {
      h.assert(requests.size === expected && observed.size === expected && proven.size === expected,
        'Recorded filing busy responses differ from proven responses');
      const counts = new Map();
      for (const entry of proven) counts.set(entry.label, (counts.get(entry.label) || 0) + 1);
      const console = recorder.consoleIssues.filter(entry => !firstConsole.has(entry) && counts.has(entry.label)
        && entry.type === 'error' && entry.location?.url === endpoint && /^Failed to load resource:.*\b503\b/.test(entry.text || ''));
      for (const [label, count] of counts) h.assert(console.filter(entry => entry.label === label).length <= count,
        'Unexpected additional filing capacity console errors');
      recorder.badResponses = recorder.badResponses.filter(entry => !proven.has(entry));
      recorder.consoleIssues = recorder.consoleIssues.filter(entry => !console.includes(entry));
    },
  };
}

/** Real installed form, guarded injected refusals, then one actual owned filing. */
async function checkIncomingFilingCapacity(session, page, name, source, inspect, {staleRevision, guard} = {}) {
  h.assert(session.patient && /^[1-9]\d*$/.test(session.patient), 'Filing requires the workflow-owned patient');
  const directory = process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR;
  h.assert(directory, 'Set RX_FAX_DOCUMENT_DIR or DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(directory);
  const description = `${session.marker} incoming filing`;
  prepareIncomingFilingProgram(session);
  session.cleanup(() => {guard.assertCleanup(); cleanupFiling(session, description, store);});
  const original = fs.readFileSync(source);
  const owned = {name, patient: session.patient, description, revision: createHash('sha256').update(original).digest('hex')};
  const endpoint = new URL(await page.locator('#forms_').getAttribute('action'), page.url()).href;
  const pattern = url => url.href === endpoint;
  const busyRecorder = createFilingBusyRecorder(session.recorder, endpoint);
  const bodies = [];
  let phase = 'unknown', attempts = 0, unsafe = false;
  const forwarding = createFilingForwarder(owned, () => bodies[0], guard, request => {
    h.assert(fs.readFileSync(source).equals(original) && count() === '0', 'A real unaccepted filing changed the source or chart');
    busyRecorder.expect(request);
  });
  const handlers = new Set();
  const failures = [];
  const count = () => session.sql.value(`SELECT COUNT(*) FROM document WHERE docdesc=${h.sqlString(description)}`);
  const handle = async route => {
    try { bodies.push(validateFilingRequest(route.request(), owned)); }
    catch (error) { unsafe = true; await route.abort('blockedbyclient'); return; }
    attempts++;
    if (phase === 'unknown') return route.fulfill({status: 200, contentType: 'text/html', body: '<html>Unconfirmed response</html>'});
    if (attempts <= 5) {
      h.assert(fs.readFileSync(source).equals(original) && count() === '0', 'An unaccepted filing changed the source or chart');
      busyRecorder.expect(route.request());
      return route.fulfill({status: 503, contentType: 'application/json', headers: {'Retry-After': '1', 'Cache-Control': 'no-store'},
        body: JSON.stringify({success: false, retryable: true, accepted: false, error: 'Waiting for document capacity.'})});
    }
    return forwarding.forward(route);
  };
  const handler = route => {
    const pending = handle(route).catch(async error => {
      failures.push(error);
      await route.abort('failed').catch(() => {});
    }).finally(() => handlers.delete(pending));
    handlers.add(pending);
    return pending;
  };
  async function fill() {
    const type = await page.locator('#docType option').evaluateAll(options => options.find(option => option.value)?.value);
    h.assert(type, 'Installed incoming form has no document type');
    await page.locator('#docType').selectOption(type);
    await page.locator('#documentDescription').fill(description);
    const search = page.waitForResponse(response => {
      const request = response.request();
      return new URL(response.url()).pathname.endsWith('/demographic/SearchDemographic')
        && request.method() === 'POST' && new URLSearchParams(request.postData() || '').get('query') === session.marker;
    }, {timeout: 20000});
    search.catch(() => {});
    const input = page.locator('#autocompletedemo');
    await input.click();
    await input.fill('');
    await input.pressSequentially(session.marker, {delay: 30});
    const searched = await search;
    h.assert(searched.status() === 200, 'Patient autocomplete search failed');
    const results = (await searched.json()).results;
    h.assert(Array.isArray(results) && results.filter(item => String(item.demographicNo) === session.patient).length === 1,
      'Patient autocomplete response must contain exactly one owned chart');
    const candidates = page.locator('.ui-autocomplete:visible .ui-menu-item');
    await candidates.first().waitFor({state: 'visible'});
    // The display formatter may uppercase names or include escaped highlight
    // markup. Select the owned patient identity, never a surname/text match.
    const matches = await candidates.evaluateAll((items, patient) => items.map((item, index) => {
      const data = window.jQuery(item).data('ui-autocomplete-item');
      return data && String(data.demographicNo) === patient ? index : -1;
    }).filter(index => index >= 0), session.patient);
    h.assert(matches.length === 1, 'Patient autocomplete must offer exactly one owned chart');
    await candidates.nth(matches[0]).click();
    h.assert(await page.locator('#demofind').inputValue() === session.patient, 'Patient selection left the owned chart');
  }
  const observeBusy = response => {
    try {busyRecorder.observe(response);} catch (error) {failures.push(error);}
  };
  page.on('response', observeBusy);
  await page.route(pattern, handler);
  try {
    await fill();
    if (staleRevision) {
      h.assert(/^[a-f0-9]{64}$/.test(staleRevision) && staleRevision !== owned.revision, 'Stale filing probe needs an actual older revision');
      const fields = await page.locator('#forms_').evaluate(form =>
        Object.fromEntries([...new FormData(form)].map(([key, value]) => [key, String(value)])));
      const rejected = await session.context.request.post(endpoint, {
        form: {...fields, sourceRevision: staleRevision},
        headers: {'X-CARLOS-Incoming-Filing': 'bounded-v1', Referer: page.url()}, maxRedirects: 0,
      });
      try {
        const data = await rejected.json();
        h.assert(rejected.status() === 409 && data.success === false && data.accepted === false
          && data.retryable === false && data.sourceChanged === true, 'Stale filing did not return a safe source conflict');
        h.assert(fs.readFileSync(source).equals(original) && count() === '0', 'Stale filing changed the queue or chart');
      } finally {await rejected.dispose();}
    }
    const safeGet = new URL('ViewIncomingDocs', endpoint);
    safeGet.search = new URLSearchParams({queueId: '1', pdfDir: 'File', pdfNo: await page.locator('#forms_ [name="pdfNo"]').inputValue(), pdfPageNumber: '1'}).toString();
    await page.locator('#save').click();
    await page.locator('#incoming-filing-status').filter({hasText: /check|confirm/i}).waitFor();
    h.assert(attempts === 1 && await page.locator('#save').isDisabled(), 'Unknown acceptance enabled another filing');
    h.assert(await page.locator('#documentDescription').inputValue() === description && await page.locator('#demofind').inputValue() === session.patient,
      'Unknown acceptance discarded the selected patient or entered description');
    h.assert(fs.readFileSync(source).equals(original) && count() === '0', 'Injected unknown response performed a real filing');
    await h.withExpectedDialogs(page, () => page.goto(safeGet.href));
    await page.waitForLoadState('networkidle');
    h.assert(attempts === 1, 'Unknown acceptance automatically retried');
    phase = 'busy'; attempts = 0; bodies.length = 0;
    await fill();
    await page.locator('#save').click();
    await page.locator('#incoming-filing-status').filter({hasText: 'Waiting for document capacity.'}).waitFor();
    h.assert(await page.locator('#save').isDisabled() && await page.locator('#documentDescription').inputValue() === description,
      'Waiting did not preserve frozen form input');
    let completionTimer;
    const accepted = await Promise.race([forwarding.result, new Promise((resolve, reject) => {
      completionTimer = setTimeout(() => reject(new Error('Filing did not finish its bounded retry check')), 120000);
    })]).finally(() => clearTimeout(completionTimer));
    await page.waitForURL(url => url.pathname.endsWith('/documentManager/ViewIncomingDocs') && url.searchParams.get('queueId') === '1');
    h.assert(!unsafe && attempts === 6 + forwarding.busyCount() && new Set(bodies).size === 1, 'Filing retries changed inputs or did not recover after five refusals');
    h.assert(count() === '1' && !fs.existsSync(source), 'Recovered filing duplicated the document or left its queue source');
    const rows = session.sql.rows(`SELECT d.docfilename,d.number_of_pages FROM document d JOIN ctl_document c ON c.document_no=d.document_no WHERE d.document_no=${accepted.documentNo} AND d.docdesc=${h.sqlString(description)} AND c.module='demographic' AND c.module_id=${session.patient}`);
    h.assert(rows.length === 1 && rows[0][1] === '1', 'Installed filing has incorrect patient linkage or page count');
    h.assert(path.basename(rows[0][0]) === rows[0][0] && rows[0][0].includes(session.marker), 'Accepted filename is not owned');
    h.assert(inspect(path.join(store, rows[0][0])).text.includes(`${session.marker} page 3`), 'Installed filing lost source page content');
    busyRecorder.finish(5 + forwarding.busyCount());
  } finally {
    // Cancel automatic retries before waiting for a possibly accepted POST. The
    // durable guard retains both source and chart fixtures for unknown acceptance.
    await page.evaluate(() => window.dispatchEvent(new Event('pagehide'))).catch(() => {});
    while (handlers.size) await Promise.all([...handlers]);
    try {await forwarding.drain();}
    finally {await page.unroute(pattern, handler); page.off('response', observeBusy);}
    if (failures.length) throw failures[0];
  }
}
module.exports = {validateFilingRequest, createFilingForwarder, createFilingBusyRecorder, cleanupFiling, checkIncomingFilingCapacity};
