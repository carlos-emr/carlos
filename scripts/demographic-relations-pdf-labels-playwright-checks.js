#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient-information PDF built from demographic/DemographicPdfLabel
 * (coverage plan §2.4 `demographic-relations-pdf-labels`).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Messenger "+"
 * (messenger/SendDemoMessage, the compose page with this patient linked) ▸
 * Attach ▸ "Demographic information" ▸ Preview. The preview posts only the item key
 * (previewItem=demographic) to messenger/Doc2PDF, which renders DemographicPdfLabel
 * on the server and answers with the PDF the clinician sees (issue #4133: no page
 * HTML travels from the browser any more).
 *
 * Asserts: compose and the chooser are scoped to the owned patient; the preview
 * request carries the item key and no page HTML; the preview response is a
 * complete PDF whose text (pdftotext) carries the patient's name and address;
 * previewing persists nothing (no message, no patient link); and, last, that the
 * address is reproduced literally, markup-like characters included (the label
 * page now encodes patient fields).
 *
 * Fixtures and cleanup: the owned FAKE- patient from runWorkflow, given a
 * synthetic address; it is removed by the workflow session, after any messenger
 * rows a defective preview linked to it or marked with the run marker. Nothing is sent.
 *
 * Not covered here: demographic/AddRelation and DeleteRelation have no UI entry
 * while NEW_CONTACTS_UI is on (the Master Record's "Add Relation" pill is rendered
 * only for the legacy contacts UI); printDemoLabelAction, printDemoAddressLabelAction,
 * printDemoChartLabelAction and printClientLabLabelAction are already proven with
 * pdftotext by demographic-label-content.
 */
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { assertIsPdf } = require('./demographic-labels-playwright-checks');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const compact = text => text.replace(/\s+/g, '');

function pdfText(bytes) {
  return execFileSync('pdftotext', ['-', '-'], {
    input: bytes, encoding: 'utf8', timeout: 15000, maxBuffer: 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'],
  });
}

async function workflow(s) {
  try { execFileSync('pdftotext', ['-v'], { stdio: 'pipe', timeout: 5000 }); } catch {
    throw new h.SkipCheck('PDF content validation requires Poppler pdftotext');
  }
  // Markup-like characters are ordinary address text; a correct page prints them.
  const address = `${s.marker.slice(-6)} O'Neil & <Fixture> Lane`;
  const city = 'Fixture City';
  const markerLike = h.sqlString(`%${s.marker}%`);
  // Registered before the first write: a defective preview that sends a message or
  // links one to the patient must not leave messenger rows behind (msgDemoMap has
  // no cascade from demographic). Messages linked to the owned patient were created
  // by this run, since the patient did not exist before it.
  s.cleanup(() => {
    const ids = s.sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${markerLike}
      OR themessage LIKE ${markerLike} UNION SELECT messageID FROM msgDemoMap WHERE demographic_no=${s.patient}`)
      .map(([id]) => Number(id)).filter(id => id > 0);
    const list = ids.length ? ids.join(',') : '-1';
    s.sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list}) OR demographic_no=${s.patient};
      DELETE FROM messagelisttbl WHERE message IN (${list});
      DELETE FROM messagetbl WHERE messageid IN (${list})`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${list}) OR demographic_no=${s.patient})
      + (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${list}))
      + (SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${list}))`) === '0',
    'Messenger rows created by the preview were not removed');
  });
  s.sql.execute(`UPDATE demographic SET address=${h.sqlString(address)},city=${h.sqlString(city)},
    postal='K1A0B1' WHERE demographic_no=${s.patient} AND last_name=${h.sqlString(s.marker)}`);
  const persisted = () => s.sql.value(`SELECT (SELECT COUNT(*) FROM msgDemoMap WHERE demographic_no=${s.patient})
    + (SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${markerLike} OR themessage LIKE ${markerLike})`);
  h.assert(persisted() === '0', 'The owned patient already has messenger rows');

  let compose;
  await s.step('E-Chart ▸ Messenger + opens compose with the owned patient linked', async () => {
    const chart = await s.chart();
    compose = await s.popup(chart, chart.locator('a[onclick*="/messenger/SendDemoMessage?demographic_no="]').first(),
      'compose-with-patient');
    await compose.locator('input[name="attachDemo"]').waitFor();
    h.assert(await compose.locator('input[name="demographic_no"]').inputValue() === s.patient,
      'Compose did not link the patient whose chart opened it');
    h.assert((await compose.locator('input[name="selectedDemo"]').inputValue()).includes(s.marker),
      'Compose does not show the linked patient');
  });

  let chooser;
  let main;
  let preview;
  await s.step('Attach lists this patient\'s Demographic information for preview', async () => {
    // The chooser page holds its content in a full-page <iframe name="main">; the
    // shared popup helper reads the outer page's body text, so the real content is
    // asserted inside the frame instead.
    const opened = s.context.waitForEvent('page', { timeout: TIMEOUT });
    await compose.locator('input[name="attachDemo"]').click();
    chooser = await opened;
    await chooser.waitForURL(url => url.pathname.endsWith('/messenger/attachmentFrameset'),
      { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    main = chooser.frameLocator('iframe[name="main"]');
    preview = main.locator('button[data-preview-item="demographic"]');
    await preview.waitFor({ timeout: TIMEOUT });
    h.assert(await main.locator('input[name="demographic_no"]').inputValue() === s.patient,
      'The attachment chooser is for another patient');
    h.assert((await main.locator('body').innerText()).includes(s.marker), 'The attachment chooser does not name the patient');
  });

  let held;
  await s.step('Preview posts the item key for the owned patient, not page HTML', async () => {
    // Hold the page's own Doc2PDF POST (unchanged) until its body is checked, so a
    // failure of the conversion is reported by its own step below.
    let release;
    held = new Promise(resolve => { release = resolve; });
    // Await the registration so the route is active before the click can fire the POST.
    await chooser.route('**/messenger/Doc2PDF', route => release(route), { times: 1 });
    await preview.click();
  });

  let route;
  await s.step('previewing sends nothing and links nothing to the patient', async () => {
    // Bounded: a preview whose handler never posts the captured HTML must fail
    // here with that defect, not hang until the suite's per-script timeout.
    let timer;
    route = await Promise.race([held, new Promise(resolve => { timer = setTimeout(resolve, TIMEOUT, null); })]);
    clearTimeout(timer);
    h.assert(route, 'The preview never posted to messenger/Doc2PDF');
    const body = new URLSearchParams(route.request().postData() || '');
    h.assert(body.get('isPreview') === 'true' && body.get('previewItem') === 'demographic',
      'The preview did not ask messenger/Doc2PDF for the Demographic information item');
    h.assert(body.get('demographic_no') === s.patient, 'The preview asked for another patient');
    h.assert(!body.has('srcText'), 'The preview still posts page HTML (srcText) to messenger/Doc2PDF');
    h.assert(persisted() === '0', 'Previewing an attachment persisted a message or patient link');
  });

  let text;
  await s.step('the preview answers with a complete PDF carrying the patient and persists nothing', async () => {
    // Fetched through the held route: a PDF frame navigation has no readable body in
    // Chromium (the response seen by the page is the built-in viewer's wrapper HTML).
    const response = await route.fetch();
    const bytes = await response.body();
    await route.fulfill({ response });
    // Re-checked once the conversion request has completed, so a write made by
    // Doc2PDF itself is caught too.
    h.assert(persisted() === '0', 'The preview conversion (messenger/Doc2PDF) persisted a message or patient link');
    // A front-door WAF that refuses the preview POST leaves the clinician with no preview.
    h.assert(response.status() === 200, `messenger/Doc2PDF answered HTTP ${response.status()} instead of the preview PDF`);
    assertIsPdf({ label: 'Demographic information preview' }, response.status(), response.headers()['content-type'] || '', bytes);
    text = pdfText(bytes);
    h.assert(compact(text).includes(compact(`${s.marker},`)) && text.includes('Workflow'),
      'The preview PDF does not carry the owned patient\'s name');
    h.assert(text.includes(city) && text.includes('K1A0B1'), 'The preview PDF does not carry the patient\'s city and postal code');
  });

  // Last on purpose: an encoding regression must not hide the facts above.
  await s.step('the PDF reproduces the patient\'s address literally', async () => {
    h.assert(compact(text).includes(compact(address)),
      'The patient-information PDF lost markup-like characters of the address: DemographicPdfLabel no longer encodes patient fields');
  });
}

if (require.main === module) runWorkflow('demographic-relations-pdf-labels', workflow);
module.exports = { workflow };
