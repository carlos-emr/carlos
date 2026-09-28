#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Installed-only: owns a synthetic patient, admission, PDF and all document routes.
// Requires DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR), pdfinfo, pdftotext and the normal
// workflow database/browser configuration. Never edits provider preferences/roles.
// STORED_DOCUMENT_EXPECT_CONTENT_UPDATES=false (default): assigned-document denial.
// Explicit true requires a separately controlled enabled-policy mutation phase.
const fs = require('node:fs');
const {execFileSync} = require('node:child_process');
const {createHash} = require('node:crypto');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {prepareIncomingFilingProgram} = require('./lib/incoming-filing-program-fixture');
const {createStoredDocumentFixture} = require('./lib/stored-document-mutation-fixture');
const {withStoredCapacityCheck} = require('./lib/stored-document-capacity-check');
const {preflightMetadataSchema} = require('./lib/document-metadata-check');
const {metadataEnabled, runInstalledMetadata} = require('./lib/document-metadata-installed');
const {preflightMetadataPatient, createMetadataPatientFixture} = require('./lib/document-metadata-patient-fixture');
function metadataPreflight(sql) {
  const patientCleanup = preflightMetadataPatient(sql);
  return preflightMetadataSchema(sql, {patientCleanup});
}

function validateOwnedPost(body, expected, ownedIds) {
  const fields = new URLSearchParams(body || '');
  h.assert(ownedIds.has(fields.get('document')) && fields.getAll('document').length === 1,
    'Refusing a stored mutation outside the owned fixture');
  for (const [key, values] of Object.entries(expected)) {
    h.assert(JSON.stringify(fields.getAll(key)) === JSON.stringify(values), 'Stored mutation differs from its frozen intended operation');
  }
  const allowed = new Set([...Object.keys(expected), 'CSRF-TOKEN']);
  h.assert([...fields.keys()].every(key => allowed.has(key)), 'Unexpected stored mutation field');
  h.assert(fields.getAll('CSRF-TOKEN').length <= 1, 'Duplicate stored mutation CSRF token');
  return fields;
}
function rotation(file, page) {
  const info = execFileSync('pdfinfo', ['-f', String(page), '-l', String(page), file], {encoding: 'utf8'});
  return Number(info.match(/(?:Page\s+\d+\s+rot|Page rot):\s+(\d+)/)?.[1]);
}
function expectedContentUpdates(value = process.env.STORED_DOCUMENT_EXPECT_CONTENT_UPDATES) {
  h.assert(value === undefined || value === 'false' || value === 'true',
    'STORED_DOCUMENT_EXPECT_CONTENT_UPDATES must be exactly true or false');
  return value === 'true';
}
function validatePolicyRefusal(response, data) {
  h.assert(response.status() === 403 && /^application\/json(?:\s*;|$)/i.test(response.headers()['content-type'] || '')
    && data?.success === false && data.accepted === false && data.retryable === false
    && !response.headers()['retry-after'], 'Assigned document policy did not prove nonretryable pre-acceptance refusal');
}
function createMutationTracker(fixture) {
  const forwarded = new WeakSet();
  let count = 0;
  return {
    beforeForward(request) {fixture.begin(); forwarded.add(request); count++;},
    failed(request) {if (forwarded.has(request)) fixture.transportFailed();},
    failureSince(before) {if (count > before) fixture.transportFailed();},
    count: () => count,
  };
}
async function probeAssignedPolicy(requester, endpoint, fields, intended, fixture) {
  validateOwnedPost(fields.toString(), intended, fixture.ids());
  const token = fields.get('CSRF-TOKEN');
  h.assert(token && fields.getAll('CSRF-TOKEN').length === 1, 'Policy probe requires one genuine CSRF token');
  fixture.assertUnchanged();
  // APIRequestContext bypasses browser routing. Record uncertainty here,
  // immediately before the exact locally validated owned POST is sent.
  fixture.begin();
  try {
    const response = await requester.post(endpoint, {headers: {'CSRF-TOKEN': token,
      'Content-Type': 'application/x-www-form-urlencoded'}, data: fields.toString(), maxRedirects: 0});
    const data = await response.json();
    validatePolicyRefusal(response, data);
    fixture.rejectUnaccepted();
  } catch (error) {fixture.transportFailed(); throw error;}
  fixture.assertUnchanged();
}
async function workflow(s) {
  const contentUpdates = expectedContentUpdates();
  const metadataSchema = metadataEnabled() ? metadataPreflight(s.sql) : null;
  let fixture, fixtureStarted = false;
  const {program} = prepareIncomingFilingProgram({...s, cleanup(callback) {
    s.cleanup(() => {
      h.assert(!fixtureStarted || fixture?.isCleaned(), 'Retain owned admission while stored-document recovery remains outstanding');
      return callback();
    });
  }});
  fixtureStarted = true;
  fixture = createStoredDocumentFixture(s, program, undefined, {metadataSchema});
  if (metadataSchema) {
    await runInstalledMetadata(s, fixture, metadataSchema);
    return; // Explicit separate phase; does not claim content-policy/mutation coverage.
  }
  fixture.rememberStable();
  const mutations = createMutationTracker(fixture);
  const id = fixture.sourceId, context = s.context;
  const endpoint = new URL('documentManager/SplitDocument', String(s.config.baseUrl).replace(/\/?$/, '/')).href;
  const pending = new Set(), ownedPages = new Set();
  let expected = null, guardFailure = false;
  const wire = page => {
    ownedPages.add(page);
    page.setDefaultTimeout(120000);
    page.on('request', request => {
      if (/\/(SplitDocument|ManageDocument)(?:\?|$)/.test(request.url())) pending.add(request);
    });
    page.on('requestfinished', request => pending.delete(request));
    page.on('requestfailed', request => {
      mutations.failed(request);
      pending.delete(request);
    });
  };
  context.on('page', wire);
  const mutationRoute = async route => {
    try {
      h.assert(expected && route.request().method() === 'POST', 'Unexpected stored-document mutation');
      const fields = validateOwnedPost(route.request().postData(), expected, fixture.ids());
      const token = route.request().headers()['csrf-token'];
      h.assert(token && (!fields.has('CSRF-TOKEN') || fields.get('CSRF-TOKEN') === token),
        'Stored mutation must use the frozen nonempty CSRF token');
      mutations.beforeForward(route.request());
      await route.continue();
    } catch (error) {guardFailure = true; mutations.failed(route.request()); await route.abort('blockedbyclient');}
  };
  async function installGuards(browserContext) {
    await browserContext.route('**/documentManager/SplitDocument', mutationRoute);
    await browserContext.route('**/documentManager/ManageDocument?*', async route => {
      const url = new URL(route.request().url());
      if (route.request().method() !== 'GET' || !['showPage', 'viewDocPage', 'display'].includes(url.searchParams.get('method'))
        || !fixture.ids().has(url.searchParams.get('doc_no'))) {
        guardFailure = true; await route.abort('blockedbyclient'); return;
      }
      await route.continue();
    });
  }
  await installGuards(context);
  function capacityOptions(page, label, inject = 0, finalStatus = 200) {
    const beforeForwarded = mutations.count();
    return {page, endpoint, label, inject, finalStatus, recorder: s.recorder,
      assertUnchanged: () => fixture.assertUnchanged(), onFailure: () => mutations.failureSince(beforeForwarded),
      validateRequest(request) {
        h.assert(expected, 'Stored capacity check has no owned mutation intent');
        const fields = validateOwnedPost(request.postData(), expected, fixture.ids());
        const token = request.headers()['csrf-token'];
        h.assert(token && (!fields.has('CSRF-TOKEN') || fields.get('CSRF-TOKEN') === token),
          'Stored capacity retry lost its frozen CSRF token');
      }};
  }
  let viewer, splitter, staleContext, staleSplitter, staleRevision;
  try {
    viewer = await context.newPage();
    await viewer.goto(new URL(`documentManager/ViewShowDocument?segmentID=${id}&inWindow=true`, String(s.config.baseUrl).replace(/\/?$/, '/')).href);
    await viewer.waitForLoadState('networkidle');
    async function waitImage() {
      await viewer.waitForFunction(number => {
        const image = document.getElementById('docImg_' + number);
        return image && image.complete && image.naturalWidth > 0 && image.getAttribute('data-document-image-state') === 'loaded';
      }, id);
    }
    h.assert(await viewer.locator(`#displayDocumentAs_${id}`).inputValue() === 'Image', 'Stored mutation fixture requires the normal image preview mode');
    await waitImage();
    fixture.assertUnchanged();
    const selectors = [`input[onclick^="split("]`, ...['rotate90', 'rotate180', 'removeFirstPage'].map(method => `#${method}btn_${id}`)];
    for (const selector of selectors) {
      h.assert(await viewer.locator(selector).count() === 1, 'Stored document mutation control is missing');
      h.assert(await viewer.locator(selector).isVisible() === contentUpdates,
        'Assigned document controls disagree with the explicitly expected content-update policy');
    }
    await s.step('owned document rejects GET, missing CSRF and an unauthenticated writer without changes', async () => {
      const readOnly = await context.request.get(endpoint, {params: {method: 'rotate90', document: id}, maxRedirects: 0});
      h.assert(readOnly.status() === 405, 'Stored mutation GET was not refused');
      const noToken = await context.request.post(endpoint, {form: {method: 'rotate90', document: id}, maxRedirects: 0});
      h.assert(noToken.status() === 403, 'Stored mutation without CSRF was not refused');
      const anonymous = await h.newContext(context.browser(), s.config);
      try {
        const denied = await anonymous.request.post(endpoint, {form: {method: 'rotate90', document: id, 'CSRF-TOKEN': 'invalid-owned-probe'}, maxRedirects: 0});
        h.assert([302, 303, 401, 403].includes(denied.status()), 'Unauthenticated stored mutation was not refused');
      } finally {await anonymous.close();}
      fixture.assertUnchanged();
    });
    if (!contentUpdates) {
      await s.step('default assigned-document policy refuses all four authenticated operations without changing clinical state', async () => {
        const token = await viewer.locator('input[name="CSRF-TOKEN"]').first().inputValue();
        const revision = await viewer.locator(`#sourceRevision_${id}`).inputValue();
        h.assert(token && /^[0-9a-f]{64}$/.test(revision)
          && revision === createHash('sha256').update(fs.readFileSync(fixture.sourceFile)).digest('hex'),
        'Policy denial probes require genuine CSRF and current observed source revision');
        for (const method of ['split', 'rotate90', 'rotate180', 'removeFirstPage']) {
          const intended = {method: [method], document: [id], sourceRevision: [revision]};
          if (method === 'split') Object.assign(intended, {queueID: ['1'], page: ['1,0']});
          const fields = new URLSearchParams();
          for (const [name, values] of Object.entries(intended)) for (const value of values) fields.append(name, value);
          fields.append('CSRF-TOKEN', token);
          await probeAssignedPolicy(context.request, endpoint, fields, intended, fixture);
        }
      });
      h.assert(!guardFailure, 'Policy check escaped the owned read allowlist');
      console.log('  COVERAGE stored-document-mutations: assigned-policy-denial; four real403 refusals; enabled-policy mutation phase still required');
      return;
    }
    async function mutate(method, pages, remaining) {
      expected = {method: [method], document: [id], sourceRevision: [createHash('sha256').update(fs.readFileSync(fixture.sourceFile)).digest('hex')]};
      const previousImage = await viewer.locator(`#docImg_${id}`).getAttribute('data-document-image-src');
      await withStoredCapacityCheck(capacityOptions(viewer, 'stored-document-mutations', method === 'rotate90' ? 5 : 0), async () => {
        const response = viewer.waitForResponse(r => r.url() === endpoint && r.request().method() === 'POST' && r.status() !== 503);
        response.catch(() => {});
        const click = () => viewer.locator(`#${method}btn_${id}`).click();
        if (method === 'removeFirstPage') await h.withExpectedDialogs(viewer, click);
        else await click();
        if (method === 'rotate90') {
          await viewer.locator(`#document-mutation-status-${id} button`).waitFor({state: 'visible'});
          h.assert(await viewer.locator(`#document-mutation-status-${id} [role="status"]`).textContent(),
            'Quick mutation capacity wait has no visible explanation');
          h.assert(await viewer.locator(`#sourceRevision_${id}`).inputValue() === expected.sourceRevision[0]
            && await viewer.locator(`#totalPage_${id}`).inputValue() === '3', 'Quick wait changed revision or page count');
          for (const name of ['rotate90', 'rotate180', 'removeFirstPage']) {
            h.assert(await viewer.locator(`#${name}btn_${id}`).isDisabled(), 'Quick edits remained enabled while waiting');
          }
          await viewer.evaluate(number => {
            quickDocumentMutation(number, 'rotate90'); quickDocumentMutation(number, 'rotate180'); quickDocumentMutation(number, 'removeFirstPage');
          }, id);
          fixture.assertUnchanged();
        }
        const result = await response;
        const data = await result.json();
        h.assert(result.status() === 200 && data.success === true && data.accepted === true
          && data.document === Number(id) && data.pageCount === pages, 'Stored page edit did not confirm committed success');
        await viewer.waitForFunction(({number, count}) => document.getElementById('totalPage_' + number)?.value === String(count)
          && document.getElementById('curPage_' + number)?.value === '1'
          && !document.getElementById('rotate90btn_' + number)?.disabled, {number: id, count: pages});
        await viewer.waitForFunction(({number, previous}) => document.getElementById('docImg_' + number)
          ?.getAttribute('data-document-image-src') !== previous, {number: id, previous: previousImage});
        await waitImage();
        fixture.acceptMutation(pages, remaining);
        h.assert(data.sourceRevision === createHash('sha256').update(fs.readFileSync(fixture.sourceFile)).digest('hex'),
          'Page edit returned the wrong committed source revision');
      });
      expected = null;
    }
    await s.step('real rotate90 and rotate180 preserve all three pages and refresh the viewer', async () => {
      await mutate('rotate90', 3, [1, 2, 3]);
      h.assert([1, 2, 3].every(page => rotation(fixture.sourceFile, page) === 90), 'Stored rotate90 did not rotate all pages');
      await mutate('rotate180', 3, [1, 2, 3]);
      h.assert([1, 2, 3].every(page => rotation(fixture.sourceFile, page) === 270), 'Stored rotate180 did not preserve every preceding rotation');
    });
    await s.step('an independent session selects a page before another session edits the source', async () => {
      staleContext = await h.newContext(context.browser(), s.config);
      staleContext.on('page', page => {wire(page); h.wireStrictPage(page, 'stored-document-stale-session', s.recorder);});
      await installGuards(staleContext);
      await h.login(staleContext, s.config, s.recorder);
      const originalCookies = (await context.cookies(String(s.config.baseUrl))).filter(cookie => cookie.name === 'JSESSIONID');
      const independentCookies = (await staleContext.cookies(String(s.config.baseUrl))).filter(cookie => cookie.name === 'JSESSIONID');
      h.assert(originalCookies.length === 1 && independentCookies.length === 1 && originalCookies[0].value
        && independentCookies[0].value && originalCookies[0].value !== independentCookies[0].value,
      'Stored revision check requires distinct authenticated sessions');
      staleSplitter = await staleContext.newPage();
      await staleSplitter.goto(new URL(`oscarMDS/ViewSplit?document=${id}&queueID=1&demoName=`,
        String(s.config.baseUrl).replace(/\/?$/, '/')).href);
      await staleSplitter.waitForLoadState('networkidle');
      staleRevision = await staleSplitter.locator('#splitSourceRevision').inputValue();
      h.assert(staleRevision === createHash('sha256').update(fs.readFileSync(fixture.sourceFile)).digest('hex'), 'Second session did not load the original revision');
      await staleSplitter.locator('#picker li').nth(1).locator('div').first().click();
      await staleSplitter.locator('#tool_add').click();
      h.assert(await staleSplitter.locator('#builder span.num').textContent() === '2', 'Second session selected the wrong original page');
    });
    await s.step('real first-page removal updates committed content and visible pagination', async () => {
      await mutate('removeFirstPage', 2, [2, 3]);
      h.assert(await viewer.locator(`#totalPage_${id}`).inputValue() === '2'
        && (await viewer.locator(`#viewedPage_${id}`).innerText()).trim() === '1', 'Stored removal left stale viewer page counts');
    });
    await s.step('the stale session receives a nonretryable conflict without filing the now-different page', async () => {
      await withStoredCapacityCheck(capacityOptions(staleSplitter, 'stored-document-stale-session', 0, 409), async capacity => {
        let submissions = 0;
        const priorResponses = new Set(s.recorder.badResponses), priorConsole = new Set(s.recorder.consoleIssues);
        const observe = request => {if (request.url() === endpoint && request.method() === 'POST') submissions++;};
        staleSplitter.on('request', observe);
        expected = {method: ['split'], document: [id], queueID: ['1'], page: ['2,0'], sourceRevision: [staleRevision]};
        const response = staleSplitter.waitForResponse(r => r.url() === endpoint && r.request().method() === 'POST' && r.status() !== 503);
        response.catch(() => {});
        try {
          await staleSplitter.locator('#tool_savecontinue').click();
          const result = await response, data = await result.json();
          h.assert(result.status() === 409 && data.success === false && data.accepted === false
            && data.retryable === false && data.sourceChanged === true, 'Stale selection was not rejected before publication');
          fixture.rejectUnaccepted();
          await staleSplitter.locator('#split-save-status[role="alert"]').waitFor();
          h.assert(await staleSplitter.locator('#builder > li').count() === 1
            && await staleSplitter.locator('#builder span.num').textContent() === '2', 'Source conflict discarded the selected page');
          const attemptsAtConflict = capacity.attempts();
          await new Promise(resolve => setTimeout(resolve, 1500));
          h.assert(submissions === attemptsAtConflict && capacity.attempts() === attemptsAtConflict, 'Stale source conflict automatically replayed a mutation');
          fixture.assertUnchanged();
          const expectedResponses = s.recorder.badResponses.filter(entry => !priorResponses.has(entry)
            && entry.label === 'stored-document-stale-session' && entry.method === 'POST'
            && entry.url === result.url() && entry.status === 409);
          h.assert(expectedResponses.length === 1, 'Stale probe recorded an unexpected number of failed responses');
          const expectedConsole = s.recorder.consoleIssues.filter(entry => !priorConsole.has(entry)
            && entry.label === 'stored-document-stale-session' && entry.location?.url === result.url()
            && /Failed to load resource:.*409/.test(entry.text));
          h.assert(expectedConsole.length <= 1, 'Stale probe recorded additional resource errors');
          s.recorder.badResponses = s.recorder.badResponses.filter(entry => !expectedResponses.includes(entry));
          s.recorder.consoleIssues = s.recorder.consoleIssues.filter(entry => !expectedConsole.includes(entry));
        } finally {staleSplitter.off('request', observe); expected = null;}
      });
    });
    await s.step('real split retains original bytes and patient/provider/queue routes', async () => {
      const original = fs.readFileSync(fixture.sourceFile);
      const popup = context.waitForEvent('page'); popup.catch(() => {});
      await viewer.locator('input[onclick^="split("]').click();
      splitter = await popup; await splitter.waitForLoadState('networkidle');
      const page = splitter.locator('#picker li').nth(0);
      await page.locator('div').first().click(); await splitter.locator('#tool_add').click();
      expected = {method: ['split'], document: [id], queueID: ['1'], page: ['1,0'],
        sourceRevision: [createHash('sha256').update(original).digest('hex')]};
      await withStoredCapacityCheck(capacityOptions(splitter, 'stored-document-mutations', 5), async () => {
        // Keep the app's success callback but suppress only the extra viewer popup;
        // it would race ownership registration while the accepted response is read.
        await splitter.evaluate(() => {window.popup = () => {};});
        const response = splitter.waitForResponse(r => r.url() === endpoint && r.request().method() === 'POST' && r.status() !== 503);
        response.catch(() => {});
        await splitter.locator('#tool_savecontinue').click();
        await splitter.locator('#split-save-cancel').waitFor({state: 'visible'});
        h.assert(await splitter.locator('#split-save-status[role="status"]').textContent(), 'Split capacity wait has no visible explanation');
        h.assert(await splitter.locator('#builder > li').count() === 1
          && await splitter.locator('#builder span.num').textContent() === '1'
          && await splitter.locator('#splitSourceRevision').inputValue() === expected.sourceRevision[0],
        'Split wait discarded or rebased the selected page');
        h.assert(await splitter.locator('#tool_remove').getAttribute('aria-disabled') === 'true', 'Split edits remained enabled while waiting');
        await splitter.evaluate(() => {
          document.getElementById('tool_savecontinue').click();
          document.getElementById('tool_remove').click();
          document.getElementById('tool_rotate').click();
        });
        h.assert(await splitter.locator('#builder > li').count() === 1, 'Disabled splitter controls changed the pending selection');
        fixture.assertUnchanged();
        const result = await response, data = await result.json();
        h.assert(result.status() === 200 && data.success === true && data.accepted === true
          && Number.isSafeInteger(data.newDocNum) && data.newDocNum > 0, 'Stored split did not confirm a new document');
        const splitFile = fixture.acceptSplit(String(data.newDocNum), [2]);
        h.assert(fs.readFileSync(fixture.sourceFile).equals(original), 'Stored split changed its source PDF');
        h.assert(rotation(splitFile, 1) === rotation(fixture.sourceFile, 1), 'Stored split lost selected page rotation');
        await splitter.waitForFunction(() => !splitSaveController.isLocked() && document.querySelectorAll('#builder > li').length === 0);
      });
      expected = null;
    });
    h.assert(!guardFailure, 'An installed browser request escaped the owned document allowlist');
    fixture.assertUnchanged();
    console.log('  COVERAGE stored-document-mutations: enabled-policy; real rotations/removal/split, stale-session refusal and capacity recovery');
  } finally {
    // Stop new image retries first, then drain accepted requests before closing
    // pages. Unknown writers retain the fixture rather than racing cleanup.
    for (const page of ownedPages) if (!page.isClosed()) await page.evaluate(() => window.dispatchEvent(new Event('pagehide')));
    const deadline = Date.now() + 120000;
    while (pending.size && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
    h.assert(pending.size === 0, 'Stored document requests did not drain; preserve fixture for recovery');
    for (const page of ownedPages) if (!page.isClosed()) await page.close();
    context.off('page', wire);
    if (staleContext) await staleContext.close();
    fixture.closeAfterDrain();
  }
}
if (require.main === module) runWorkflow('stored-document-mutations', workflow, {openPatient: true, openMaster: false,
  preflight: ({sql}) => {if (metadataEnabled()) metadataPreflight(sql);},
  patientFixtureFactory: metadataEnabled() ? createMetadataPatientFixture : undefined});
module.exports = {workflow, validateOwnedPost, expectedContentUpdates, validatePolicyRefusal, createMutationTracker, probeAssignedPolicy};
