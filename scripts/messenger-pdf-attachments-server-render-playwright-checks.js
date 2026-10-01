#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Messenger chart-PDF attachments, rendered server-side (carlos-emr/carlos#4133).
 *
 * User path: Master Record ▸ E-Chart ▸ Messenger "+" (messenger/SendDemoMessage) ▸
 * Attach Patient (messenger/attachmentFrameset ▸ messenger/PreviewPDF) ▸ Preview, then
 * tick items ▸ Attach (messenger/Doc2PDF) ▸ Send to self.
 *
 * The chooser used to load each item into a hidden frame and post its HTML to
 * messenger/Doc2PDF as srcText. That let any request choose the PDF's content and the
 * packaged WAF refused the posted page (its <script> blocks), so preview and attach
 * answered 403 through the front door. It now posts item keys; the server renders each
 * item itself. This check asserts:
 *   - Preview posts only the patient and an item key (no page HTML) and answers a real
 *     PDF carrying the patient's name, city and address -- the address reproduced
 *     literally although it contains markup-like characters (demographicpdflabel.jsp
 *     used to write it unencoded, finding L61);
 *   - a request that still sends srcText cannot put that text into the PDF, and an item
 *     key the chooser never offers is refused (400);
 *   - Attach renders every ticked item in ONE request, closes the chooser and shows the
 *     compose attachment indicator; the encounter item is not offered for a patient
 *     with no encounter record;
 *   - sending stores one OK PDF entry per ticked item, titled by the server, on the
 *     message linked to the patient.
 * Run it through the packaged front door (EXPECT_FRONT_DOOR=true) to prove no WAF
 * exclusion is needed: every request above is ordinary.
 *
 * Fixtures: the owned FAKE- patient from runWorkflow (given a synthetic address), an
 * owned messenger group holding the test provider; cleanup removes both and every
 * message carrying the run marker.
 */
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { csrfToken, watchFrontDoor } = require('./lib/front-door-checks');

const TIMEOUT = 30000;
const compact = text => text.replace(/\s+/g, '');

function pdfText(bytes) {
  return execFileSync('pdftotext', ['-', '-'], {
    input: bytes, encoding: 'utf8', timeout: 15000, maxBuffer: 4 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'],
  });
}

function assertPdf(label, status, type, bytes) {
  h.assert(status === 200, `${label}: messenger/Doc2PDF answered HTTP ${status}`);
  h.assert(/application\/pdf/i.test(type), `${label}: answered ${type || 'no content type'} instead of a PDF`);
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', `${label}: the body is not a PDF`);
  h.assert(/%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), `${label}: content follows the PDF trailer`);
}

/** The CSRFGuard master token for this session, read the way the page's own script does. */
async function workflow(s) {
  const assertFrontDoor = watchFrontDoor(s);
  try { execFileSync('pdftotext', ['-v'], { stdio: 'pipe', timeout: 5000 }); } catch {
    throw new h.SkipCheck('PDF content validation requires Poppler pdftotext');
  }
  const provider = h.sqlString(s.provider);
  const like = h.sqlString(`%${s.marker}%`);
  // Markup-like characters are ordinary address text; a correct page prints them.
  const address = `${s.marker.slice(-6)} O'Neil & <Fixture> Lane`;
  const city = 'Fixture City';
  const subject = `${s.marker} chart PDFs`;
  let groupId;

  const listMark = Number(s.sql.value('SELECT IFNULL(MAX(id), 0) FROM messagelisttbl'));
  const demoMapMark = Number(s.sql.value('SELECT IFNULL(MAX(id), 0) FROM msgDemoMap'));
  s.cleanup(() => {
    const ids = s.sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${like}`).map(([id]) => Number(id));
    const list = ids.length ? ids.join(',') : '-1';
    s.sql.execute(`DELETE FROM msgDemoMap WHERE (messageID IN (${list}) AND id > ${demoMapMark}) OR (demographic_no=${s.patient} AND id > ${demoMapMark});
      DELETE FROM messagelisttbl WHERE message IN (${list}) AND id > ${listMark};
      DELETE FROM messagetbl WHERE messageid IN (${list}) AND thesubject LIKE ${like}`);
    if (groupId) {
      s.sql.execute(`DELETE FROM groupMembers_tbl WHERE groupID=${groupId} AND provider_No=${provider};
        DELETE FROM groups_tbl WHERE groupID=${groupId} AND groupDesc=${h.sqlString(s.marker)}`);
    }
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${list}))
      + (SELECT COUNT(*) FROM msgDemoMap WHERE demographic_no=${s.patient})
      + (SELECT COUNT(*) FROM groups_tbl WHERE groupID=${groupId || -1})`) === '0', 'Owned messenger rows were not removed');
  });
  // An owned group makes the test provider a recipient without touching the shared contact list.
  groupId = Number(s.sql.value(`INSERT INTO groups_tbl(parentID,groupDesc) VALUES(0,${h.sqlString(s.marker)}); SELECT LAST_INSERT_ID()`));
  h.assert(groupId > 0, 'Messenger group fixture was not created');
  s.sql.execute(`INSERT INTO groupMembers_tbl(groupID,provider_No,facilityId) VALUES(${groupId},${provider},0)`);
  s.sql.execute(`UPDATE demographic SET address=${h.sqlString(address)},city=${h.sqlString(city)},postal='K1A0B1'
    WHERE demographic_no=${s.patient} AND last_name=${h.sqlString(s.marker)}`);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM eChart WHERE demographicNo=${s.patient}`) === '0',
    'The owned patient unexpectedly has an encounter record');

  let compose;
  await s.step('E-Chart ▸ Messenger + opens compose with the owned patient linked', async () => {
    const chart = await s.chart();
    compose = await s.popup(chart, chart.locator('a[onclick*="/messenger/SendDemoMessage?demographic_no="]').first(),
      'messenger-compose');
    await compose.locator('input[name="attachDemo"]').waitFor();
    h.assert(await compose.locator('input[name="demographic_no"]').inputValue() === s.patient,
      'Compose did not link the patient whose chart opened it');
  });

  let chooser;
  let main;
  const openChooser = async () => {
    // The host page is only the frame, so the chooser is asserted in its main frame.
    [chooser] = await Promise.all([
      s.context.waitForEvent('page', { timeout: TIMEOUT }),
      compose.locator('input[name="attachDemo"]').click(),
    ]);
    await chooser.waitForURL(url => url.pathname.endsWith('/messenger/attachmentFrameset'),
      { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    // The frame element can be attached a tick before Playwright registers its Frame.
    const deadline = Date.now() + TIMEOUT;
    while (!(main = chooser.frame({ name: 'main' })) && Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    h.assert(main, 'The attachment frameset has no main frame');
    await main.waitForURL(/\/messenger\/PreviewPDF/, { timeout: TIMEOUT });
    await main.locator('form#attachForm').waitFor({ timeout: TIMEOUT });
  };

  // Observes the Doc2PDF exchange without changing what the browser receives: a PDF
  // navigation becomes a download in headless Chromium and has no readable body otherwise.
  const capturePreview = async key => {
    let captured;
    const handler = async route => {
      const response = await route.fetch();
      captured = { status: response.status(), type: response.headers()['content-type'] || '',
        body: await response.body(), post: route.request().postData() || '' };
      await route.fulfill({ response });
    };
    await s.context.route('**/messenger/Doc2PDF', handler);
    try {
      await main.locator(`button[data-preview-item="${key}"]`).click();
      const deadline = Date.now() + TIMEOUT;
      while (!captured && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
    } finally {
      await s.context.unroute('**/messenger/Doc2PDF', handler);
    }
    return captured;
  };

  await s.step('Attach Patient lists this patient\'s items by key, with no hidden source frame', async () => {
    await openChooser();
    h.assert(await chooser.locator('frame, iframe[name="srcFrame"]').count() === 0, 'The chooser still has a hidden source frame');
    const keys = await main.locator('input[name="item"]').evaluateAll(inputs => inputs.map(input => input.value));
    h.assert(JSON.stringify(keys) === JSON.stringify(['demographic', 'prescriptions']),
      `The chooser offered ${JSON.stringify(keys)}; a patient with no encounter record has no encounter item`);
    h.assert(await main.locator('input[name="srcText"], input[name="uriArray"]').count() === 0,
      'The chooser still carries page-HTML fields');
    h.assert(await main.locator('input[name="demographic_no"]').inputValue() === s.patient, 'The chooser is for another patient');
    h.assert((await main.locator('body').innerText()).includes(s.marker), 'The chooser does not name the patient');
  });

  let previewText;
  await s.step('Preview posts only the item key and streams a server-rendered PDF of the patient', async () => {
    const captured = await capturePreview('demographic');
    h.assert(captured, 'Preview did not post to messenger/Doc2PDF');
    const post = new URLSearchParams(captured.post);
    h.assert(post.get('isPreview') === 'true' && post.get('previewItem') === 'demographic'
      && post.get('demographic_no') === s.patient, 'Preview did not post the patient and the demographic item key');
    const allowed = new Set(['CSRF-TOKEN', 'demographic_no', 'isPreview', 'previewItem', 'item']);
    h.assert([...post.keys()].every(name => allowed.has(name)),
      `Preview posted fields beyond the item key: ${[...post.keys()].join(', ')}`);
    assertPdf('Demographic preview', captured.status, captured.type, captured.body);
    previewText = pdfText(captured.body);
    h.assert(compact(previewText).includes(compact(`${s.marker},`)) && previewText.includes('Workflow'),
      'The preview PDF does not carry the owned patient\'s name');
    h.assert(previewText.includes(city) && previewText.includes('K1A0B1'), 'The preview PDF does not carry the city and postal code');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${like}`) === '0', 'Previewing wrote a message');
    await chooser.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await chooser.close();
  });

  await s.step('the patient-information PDF reproduces the address literally (encoded, not interpreted)', async () => {
    h.assert(compact(previewText).includes(compact(address)),
      'The PDF lost markup-like characters of the address: DemographicPdfLabel writes patient fields unencoded');
  });

  await s.step('a forged srcText cannot reach the PDF and an unknown item key is refused', async () => {
    const token = await csrfToken(compose, s.config.baseUrl);
    const forged = `${s.marker} FORGED-CONTENT`;
    const url = h.appUrl(s.config.baseUrl, '/messenger/Doc2PDF');
    const response = await s.context.request.post(url, { headers: { 'CSRF-TOKEN': token }, form: {
      'CSRF-TOKEN': token, demographic_no: s.patient, isPreview: 'true', previewItem: 'demographic', srcText: forged } });
    const bytes = await response.body();
    assertPdf('Forged preview', response.status(), response.headers()['content-type'] || '', bytes);
    const text = pdfText(bytes);
    h.assert(!text.includes('FORGED-CONTENT') && text.includes(city), 'Request-supplied srcText reached the PDF');
    const unknown = await s.context.request.post(url, { headers: { 'CSRF-TOKEN': token }, form: {
      'CSRF-TOKEN': token, demographic_no: s.patient, isPreview: 'true', previewItem: 'security' } });
    h.assert(unknown.status() === 400, `An unknown item key answered HTTP ${unknown.status()}, not 400`);
  });

  await s.step('Attach renders both ticked items in one request and returns to compose', async () => {
    await openChooser();
    for (const key of ['demographic', 'prescriptions']) {
      await main.locator(`input[name="item"][value="${key}"]`).check();
    }
    const posts = [];
    const onRequest = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/messenger/Doc2PDF')) posts.push(request.postData() || '');
    };
    const since = s.recorder.requestFailures.length;
    s.context.on('request', onRequest);
    try {
      await Promise.all([
        chooser.waitForEvent('close', { timeout: 60000 }),
        main.locator('button[name="Attach"]').click(),
      ]);
    } finally { s.context.off('request', onRequest); }
    // Wait for the refreshed compose page first: by then the closed window's request events
    // have all been delivered, so the clean-up below cannot miss a late abort.
    await compose.locator('#pdf-attachment-indicator').waitFor({ state: 'visible', timeout: TIMEOUT });
    // The result page closes its own window at once, which cancels that page's csrfguard
    // script load; consume exactly those aborts, nothing else.
    for (let i = s.recorder.requestFailures.length - 1; i >= since; i--) {
      const entry = s.recorder.requestFailures[i];
      if (entry.resourceType === 'script' && entry.errorText === 'net::ERR_ABORTED'
        && new URL(entry.url).pathname.endsWith('/csrfguard')) s.recorder.requestFailures.splice(i, 1);
    }
    h.assert(posts.length === 1, `Attach made ${posts.length} Doc2PDF requests instead of one`);
    const body = new URLSearchParams(posts[0]);
    h.assert(JSON.stringify(body.getAll('item')) === JSON.stringify(['demographic', 'prescriptions'])
      && body.get('isPreview') === 'false' && !body.has('srcText'), 'Attach did not post exactly the two ticked item keys');
  });

  await s.step('with an encounter record, the encounter item is offered and previews as a PDF of that record', async () => {
    // Registered before the insert; eChart rows are owned by the run's patient only.
    s.cleanup(() => s.sql.execute(`DELETE FROM eChart WHERE demographicNo=${s.patient}`));
    s.sql.execute(`INSERT INTO eChart(timeStamp,demographicNo,providerNo,subject,encounter)
      VALUES(NOW(),${s.patient},${provider},${h.sqlString(s.marker)},${h.sqlString(`${s.marker} FAKE-PW encounter text`)})`);
    // Compose stays open after Attach; previewing from a fresh chooser changes no attachment.
    await openChooser();
    const keys = await main.locator('input[name="item"]').evaluateAll(inputs => inputs.map(input => input.value));
    h.assert(JSON.stringify(keys) === JSON.stringify(['demographic', 'encounter', 'prescriptions']),
      `With an encounter record the chooser offered ${JSON.stringify(keys)}`);
    const captured = await capturePreview('encounter');
    h.assert(captured, 'The encounter preview did not post to messenger/Doc2PDF');
    h.assert(new URLSearchParams(captured.post).get('previewItem') === 'encounter', 'The encounter preview posted another item');
    assertPdf('Encounter preview', captured.status, captured.type, captured.body);
    h.assert(pdfText(captured.body).includes('FAKE-PW encounter text'), 'The encounter PDF does not carry the encounter record');
    await chooser.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await chooser.close();
  });

  await s.step('sending stores one titled, rendered PDF per ticked item on the patient-linked message', async () => {
    await compose.locator(`#member_group_${groupId}`).check();
    await compose.locator('#subject').fill(subject);
    const editor = compose.locator('.toastui-editor-ww-container .ProseMirror').first();
    await editor.click();
    await compose.keyboard.type(`${s.marker} body`);
    await Promise.all([
      compose.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: TIMEOUT }),
      compose.locator('button[type="submit"]', { hasText: /Send Message/i }).click(),
    ]);
    await expectValue(s.sql, `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${h.sqlString(subject)}`, '1',
      'Send did not write one messagetbl row');
    const id = s.sql.value(`SELECT messageid FROM messagetbl WHERE thesubject=${h.sqlString(subject)}`);
    h.assert(s.sql.value(`SELECT demographic_no FROM msgDemoMap WHERE messageID=${id} AND id > ${demoMapMark}`) === s.patient,
      'The message was not linked to the patient');
    const stored = s.sql.value(`SELECT CAST(pdfattachment AS CHAR) FROM messagetbl WHERE messageid=${id}`);
    const entries = stored.match(/<PDF>.*?<\/PDF>/gs) || [];
    h.assert(entries.length === 2, `The message stored ${entries.length} PDF entries instead of 2`);
    const fileIds = entries.map(entry => (entry.match(/<FILE_ID>([^<]*)<\/FILE_ID>/) || [])[1]);
    h.assert(JSON.stringify(fileIds) === JSON.stringify(['0', '1']),
      `The stored PDFs do not carry distinct FILE_IDs: ${JSON.stringify(fileIds)}`);
    const titles = entries.map(entry => (entry.match(/<TITLE>([^<]*)<\/TITLE>/) || [])[1] || '');
    h.assert(titles[0].includes(s.marker) && /information/i.test(titles[0]) && /prescriptions/i.test(titles[1]),
      `The stored titles are not the server-computed ones: ${JSON.stringify(titles)}`);
    for (const entry of entries) {
      const content = (entry.match(/<CONTENT>([^<]*)<\/CONTENT>/) || [])[1] || '';
      h.assert(entry.includes('<STATUS>OK</STATUS>') && Buffer.from(content, 'base64').subarray(0, 5).toString('latin1') === '%PDF-',
        'An attached item was not stored as a rendered PDF');
    }
  });
  assertFrontDoor();
}

if (require.main === module) runWorkflow('messenger-pdf-attachments-server-render', workflow);
module.exports = { workflow };
