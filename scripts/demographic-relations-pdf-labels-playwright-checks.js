#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient-information PDF built from demographic/DemographicPdfLabel
 * (coverage plan §2.4 `demographic-relations-pdf-labels`).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Messenger "+"
 * (messenger/SendDemoMessage, the compose page with this patient linked) ▸
 * Attach ▸ "Demographic information" ▸ Preview. The preview loads
 * DemographicPdfLabel into the chooser's hidden frame, captures its HTML and posts
 * it to messenger/Doc2PDF, which answers with the PDF the clinician sees.
 *
 * Asserts: compose and the chooser are scoped to the owned patient; the preview
 * response is a complete PDF whose text (pdftotext) carries the patient's name and
 * address; previewing persists nothing (no message, no patient link); and, last,
 * that the address is reproduced literally -- the page writes patient fields into
 * its HTML unencoded, so markup-like text in a field is lost (open defect).
 *
 * Fixtures and cleanup: the owned FAKE- patient from runWorkflow, given a
 * synthetic address; it is removed by the workflow session. Nothing is sent.
 *
 * Not covered here: demographic/AddRelation and DeleteRelation have no UI entry
 * while NEW_CONTACTS_UI is on (the Master Record's "Add Relation" pill is rendered
 * only for the legacy contacts UI); printDemoLabelAction, printDemoAddressLabelAction,
 * printDemoChartLabelAction and printClientLabLabelAction are already proven with
 * pdftotext by demographic-label-content.
 */
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { assertIsPdf } = require('./demographic-labels-playwright-checks');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const pathIs = (response, suffix) => new URL(response.url()).pathname.endsWith(suffix);
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
  s.sql.execute(`UPDATE demographic SET address=${h.sqlString(address)},city=${h.sqlString(city)},
    postal='K1A0B1' WHERE demographic_no=${s.patient} AND last_name=${h.sqlString(s.marker)}`);
  const persisted = () => s.sql.value(`SELECT (SELECT COUNT(*) FROM msgDemoMap WHERE demographic_no=${s.patient})
    + (SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${s.marker}%`)}
      OR themessage LIKE ${h.sqlString(`%${s.marker}%`)})`);
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
    // A <frameset> page has no body text, which the shared popup helper reads as
    // blank; the chooser's real content is asserted in its 'main' frame instead.
    const opened = s.context.waitForEvent('page', { timeout: TIMEOUT });
    await compose.locator('input[name="attachDemo"]').click();
    chooser = await opened;
    await chooser.waitForLoadState('domcontentloaded');
    h.assert(new URL(chooser.url()).pathname.endsWith('/messenger/attachmentFrameset'), 'Attach opened something other than the chooser');
    main = chooser.frameLocator('frame[name="main"]');
    preview = main.locator('button[data-preview-uri*="/demographic/DemographicPdfLabel?"]');
    await preview.waitFor({ timeout: TIMEOUT });
    const uri = new URL(await preview.getAttribute('data-preview-uri'), s.config.baseUrl);
    h.assert(uri.searchParams.get('demographic_no') === s.patient, 'The Demographic information row is for another patient');
    h.assert((await main.locator('body').innerText()).includes(s.marker), 'The attachment chooser does not name the patient');
  });

  let pdfResponse;
  let download;
  await s.step('Preview loads DemographicPdfLabel for the owned patient into the source frame', async () => {
    const label = chooser.waitForResponse(r => pathIs(r, '/demographic/DemographicPdfLabel'), { timeout: TIMEOUT });
    pdfResponse = chooser.waitForResponse(r => pathIs(r, '/messenger/Doc2PDF') && r.request().method() === 'POST',
      { timeout: 60000 });
    pdfResponse.catch(() => {});
    download = chooser.waitForEvent('download', { timeout: 60000 }).catch(() => null);
    await preview.click();
    const rendered = await label;
    h.assert(rendered.status() === 200, 'DemographicPdfLabel did not render for the preview');
    h.assert(new URL(rendered.url()).searchParams.get('demographic_no') === s.patient, 'The preview rendered another patient');
    const source = chooser.frameLocator('frame[name="srcFrame"]').locator('body');
    await source.getByText(s.marker).first().waitFor({ timeout: TIMEOUT });
    h.assert((await source.innerText()).includes(city), 'The rendered patient information omits the city');
  });

  await s.step('previewing sends nothing and links nothing to the patient', async () => {
    h.assert(persisted() === '0', 'Previewing an attachment persisted a message or patient link');
  });

  let text;
  await s.step('the preview answers with a complete PDF carrying the patient', async () => {
    const response = await pdfResponse;
    // The page posts the captured HTML (its <script> blocks included) as srcText;
    // a front-door WAF that refuses that body leaves the clinician with no preview.
    h.assert(response.status() === 200, `messenger/Doc2PDF answered HTTP ${response.status()} instead of the preview PDF`);
    let bytes = await response.body().catch(() => null);
    if (!bytes || !bytes.length) {
      // A headless browser without a PDF viewer turns the frame's PDF into a download.
      const file = await download;
      h.assert(file, 'The preview produced neither a PDF body nor a download');
      bytes = fs.readFileSync(await file.path());
    }
    assertIsPdf({ label: 'Demographic information preview' }, response.status(), response.headers()['content-type'] || '', bytes);
    text = pdfText(bytes);
    h.assert(compact(text).includes(compact(`${s.marker},`)) && text.includes('Workflow'),
      'The preview PDF does not carry the owned patient\'s name');
    h.assert(text.includes(city) && text.includes('K1A0B1'), 'The preview PDF does not carry the patient\'s city and postal code');
  });

  // Last on purpose: the open encoding defect must not hide the facts above.
  await s.step('the PDF reproduces the patient\'s address literally', async () => {
    h.assert(compact(text).includes(compact(address)),
      'The patient-information PDF lost markup-like characters of the address: DemographicPdfLabel writes patient fields unencoded');
  });
}

if (require.main === module) runWorkflow('demographic-relations-pdf-labels', workflow);
module.exports = { workflow };
