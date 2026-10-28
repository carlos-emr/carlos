#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// billing-on-invoice-letterhead: the clinic's invoice letterhead on an Ontario third-party (private) invoice. When an
// active document of the letterhead document type exists ("invoice letterhead", carlos.properties
// invoice_head_logo_doctype), the invoice's header shows that image (billing/ca/on/DisplayInvoiceLogo) in place of the
// clinic's name, address and phone; without one it shows the clinic's details. billing-on-invoice-third-party prints the
// invoice but never with a letterhead, so the logo route had no check.
// User path: Schedule ▸ Search ▸ Master Record ▸ Billing History ▸ Print.
//   1. With the owned letterhead, the invoice header shows the letterhead image in place of the clinic's details;
//   2. once the letterhead is archived, the header shows the clinic's name and phone again, and no image;
//   3. LAST: with the letterhead active again, the image request answers its exact bytes and the browser draws it.
// Fixtures: one owned private (PAT) bill for the owned patient (seedOwnedBill), and one owned letterhead: a document
// row of the letterhead type filed under the test provider (ctl_document "providers"), with a 1 x 1 PNG written to
// DOCUMENT_DIR under the run marker. Every invoice uses the clinic's letterhead while it exists, so the check refuses
// to run when the clinic already has an active one. Cleanup deletes the document, its link and its file by id and name.
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { seedOwnedBill, openHistory, historyRow } = require('./billing-on-invoice-third-party-playwright-checks');

const q = h.sqlString;
const TIMEOUT = 20000;
const LOGO_DOCTYPE = process.env.INVOICE_LOGO_DOCTYPE || 'invoice letterhead';
const LOGO_ROUTE = '/billing/ca/on/DisplayInvoiceLogo';
// The smallest valid PNG: one opaque pixel.
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

async function workflow(s) {
  const { sql, marker, provider } = s;
  const documentDir = process.env.DOCUMENT_DIR;
  if (!documentDir) throw new h.SkipCheck('Set DOCUMENT_DIR to the installed document store');
  const active = () => sql.value(`SELECT COUNT(*) FROM document WHERE doctype=${q(LOGO_DOCTYPE)} AND status='A'`);
  h.assert(active() === '0', `The clinic already has ${active()} active "${LOGO_DOCTYPE}" document(s); every invoice `
    + 'would use one of them, so the owned letterhead could not be told apart');

  const fileName = `${marker}-letterhead.png`;
  const filePath = path.join(documentDir, fileName);
  let documentNo = null;
  s.cleanup(() => {
    if (documentNo) {
      sql.execute(`DELETE FROM ctl_document WHERE document_no=${documentNo};
        DELETE FROM document WHERE document_no=${documentNo} AND docfilename=${q(fileName)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM document WHERE document_no=${documentNo})
        + (SELECT COUNT(*) FROM ctl_document WHERE document_no=${documentNo})`) === '0', 'The owned letterhead rows were not removed');
    }
    fs.rmSync(filePath, { force: true });
    h.assert(!fs.existsSync(filePath), 'The owned letterhead file was not removed');
  });
  fs.writeFileSync(filePath, PNG);
  documentNo = h.insertId(sql, `INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,
      public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
    VALUES (${q(LOGO_DOCTYPE)},${q(`${marker} letterhead`)},${q(fileName)},${q(provider)},${q(provider)},'A','image/png',
      0,1,0,CURDATE(),NOW(),NOW())`, 'letterhead');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('providers',${q(provider)},${documentNo},'A')`);

  const { headerId } = seedOwnedBill(s, { payProgram: 'PAT', status: 'P', code: 'A007A', fee: '33.70',
    date: new Date().toISOString().slice(0, 10) });
  const history = await openHistory(s);
  const printLink = () => historyRow(history, headerId).locator('a', { hasText: 'Print' }).first();
  /** The invoice's top-left header cell: the letterhead image, or the clinic's name, address and phone. */
  const header = invoice => invoice.locator('table').first().locator('td').first();
  const logo = invoice => header(invoice).locator(`img[src$="${LOGO_ROUTE}"]`);

  /** Opens, or reloads, the invoice, and returns the letterhead request it made (or the error if it made none). */
  let invoice;
  const showInvoice = async () => {
    // Settled either way, so a failed assertion before it is awaited leaves no unhandled rejection behind.
    const logoResponse = s.context.waitForEvent('response', { timeout: TIMEOUT,
      predicate: r => h.pathOnly(r.url()).endsWith(LOGO_ROUTE) }).catch(error => error);
    if (invoice && !invoice.isClosed()) await invoice.reload();
    else invoice = await s.popup(history, printLink(), 'third-party-invoice');
    await invoice.locator('#invoiceNo').waitFor({ state: 'attached', timeout: TIMEOUT });
    await h.assertNotErrorPage(invoice, 'third-party invoice');
    return logoResponse;
  };
  const setLetterhead = status =>
    sql.execute(`UPDATE document SET status=${q(status)} WHERE document_no=${documentNo} AND docfilename=${q(fileName)}`);

  await s.step('With the owned letterhead, the invoice header shows it in place of the clinic\'s details', async () => {
    await showInvoice();
    h.assert(await logo(invoice).count() === 1, 'The invoice header does not show the letterhead');
    h.assert(!(await header(invoice).innerText()).includes('Tel.:'),
      'The invoice header still shows the clinic\'s phone beside the letterhead');
  });

  await s.step('Once the letterhead is archived, the invoice header shows the clinic\'s name and phone again', async () => {
    setLetterhead('D');
    await showInvoice();
    h.assert(await logo(invoice).count() === 0, 'The invoice still shows the archived letterhead');
    h.assert((await header(invoice).innerText()).includes('Tel.:'), 'The invoice header does not show the clinic\'s phone');
  });

  // Last: the image itself, so a failure here leaves the header steps above standing.
  await s.step('With the letterhead active again, the image request answers its bytes and the browser draws it', async () => {
    setLetterhead('A');
    const response = await showInvoice();
    h.assert(await logo(invoice).count() === 1, 'The invoice header does not show the reactivated letterhead');
    h.assert(!(response instanceof Error), `The invoice never requested the letterhead: ${response.message}`);
    h.assert(response.status() === 200, `The letterhead answered HTTP ${response.status()}`);
    const body = await response.body();
    h.assert(Buffer.compare(body, PNG) === 0,
      `The letterhead request answered ${body.length} byte(s) of ${response.headers()['content-type'] || 'no type'}, `
      + `not the owned ${PNG.length}-byte PNG`);
    h.assert(/^image\/png\b/.test(response.headers()['content-type'] || ''),
      `The letterhead was served as "${response.headers()['content-type']}"`);
    h.assert(await logo(invoice).evaluate(img => img.complete && img.naturalWidth === 1),
      'The browser did not draw the letterhead');
    await invoice.close();
  });
}

if (require.main === module) runWorkflow('billing-on-invoice-letterhead', workflow, { openPatient: true });
module.exports = { workflow };
