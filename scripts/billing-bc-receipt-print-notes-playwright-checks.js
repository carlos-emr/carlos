#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The BC private bill receipt's Print and Print With Notes (billReceipt.jsp). Both print the receipt; Print switches
 * the print stylesheet to billReceiptPrint.css, which leaves out the Billing Notes box, and Print With Notes to
 * billReceiptPrintWithNotes.css, which prints it. billing-bc-private-bill-receipt opens the receipt and saves
 * Update Invoice and Receive Payment; nothing pressed either print button.
 *
 * User path: Master record ▸ Billing ▸ Invoice List ▸ status Bill Patient ▸ Create Report ▸ invoice number (the
 * receipt popup) ▸ type a note into Billing Notes ▸ Print; then Print With Notes.
 * Asserted on the printed page itself (the receipt rendered to PDF with its print stylesheet, once the page has asked
 * to print; the print dialog is the only thing stubbed): Print prints the invoice number and amount, no buttons, and
 * neither the Billing Notes heading nor the typed note; LAST, Print With Notes prints the same and also the heading
 * and the note.
 * The print buttons sit inside the receipt's Update Invoice form; a press that also posts that form (and reloads the
 * receipt) instead of printing is reported by the step as such. Until #5068 is fixed both print steps fail that way:
 * the buttons have no type, so each submits the form.
 * Needs Poppler's pdftotext (SKIP without it). Fixtures: one private bill (billing + billingmaster, 45.00) for the
 * workflow's own FAKE patient, made a BC patient. Nothing is meant to be saved; cleanup deletes every BC billing row
 * of that patient (with any bill_recipients and billingnote rows a stray Update Invoice wrote) and asserts none is
 * left. BC installs only (billregion=BC).
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { requirePoppler, pdfText } = require('./lib/stored-pdf-documents');

const TIMEOUT = 30000;
const AMOUNT = '45.00';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'the invoice number opens the receipt with its Billing Notes box and both print buttons',
  plain: 'Print prints the receipt without the Billing Notes heading or the typed note',
  notes: 'Print With Notes prints the receipt with the Billing Notes heading and the typed note',
};

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
  requirePoppler('pdftotext');
}

async function workflow(s) {
  bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const { billingNo } = bc.seedOwnedBcBill(s, { billingtype: 'Pri', status: 'P', amount: AMOUNT });
  const note = `FAKE print note ${s.marker.slice(-8)}`;
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-receipt-print-'));
  s.cleanup(() => fs.rmSync(scratch, { recursive: true, force: true }));

  let receipt;
  /**
   * Press a print button and return the text of the page as it prints, once the page has asked to print. The buttons
   * sit inside the Update Invoice form, so a press that also submits that form is reported as such.
   */
  async function printed(button, name) {
    const posts = [];
    const onRequest = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/billing/CA/BC/UpdateBilling')) posts.push(request.url());
    };
    receipt.on('request', onRequest);
    const before = await receipt.evaluate(() => window.__printed);
    await receipt.locator('button', { hasText: new RegExp(`^${button}$`) }).click();
    // The page waits for the chosen stylesheet to load before it calls print(); a navigation ends that wait.
    const outcome = await Promise.race([
      receipt.waitForFunction(count => window.__printed === count + 1, before, { timeout: TIMEOUT }).then(() => 'printed'),
      receipt.waitForEvent('framenavigated', { predicate: frame => frame === receipt.mainFrame(), timeout: TIMEOUT }).then(() => 'reloaded'),
    ]).catch(error => (/context was destroyed|navigat/i.test(error.message) ? 'reloaded' : `failed: ${error.message.split('\n')[0]}`));
    receipt.off('request', onRequest);
    h.assert(posts.length === 0 && outcome === 'printed',
      `${button} ${posts.length ? `posted the Update Invoice form (${posts.length} POST to UpdateBilling) and` : ''} ended "${outcome}" instead of printing`);
    const file = path.join(scratch, `${name}.pdf`);
    await receipt.pdf({ path: file, printBackground: false });
    return pdfText(file).replace(/\s+/g, ' ');
  }

  await s.step(STEP.open, async () => {
    const statusPage = await bc.openBillStatus(s, ui);
    await bc.reportBillStatus(statusPage, ui, { status: 'P' });
    const link = statusPage.locator(`a[href*="billingView?billing_no=${billingNo}&receipt=yes"]`);
    h.assert(await link.count() === 1, 'Bill Status did not list the owned private bill with its invoice link');
    receipt = await s.popup(statusPage, link, 'bc-receipt');
    await receipt.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(receipt, 'BC private bill receipt');
    h.assert(await receipt.locator('textarea[name="messageNotes"]').count() === 1, 'The receipt has no Billing Notes box');
    for (const button of ['Print', 'Print With Notes']) {
      h.assert(await receipt.locator('button', { hasText: new RegExp(`^${button}$`) }).count() === 1, `The receipt has no ${button} button`);
    }
    // Only the dialog is stubbed: print() is counted, so the page's own wait and stylesheet switch run as they do.
    await receipt.evaluate(() => { window.__printed = 0; window.print = () => { window.__printed += 1; }; });
    await receipt.locator('textarea[name="messageNotes"]').fill(note);
  });

  await s.step(STEP.plain, async () => {
    const text = await printed('Print', 'plain');
    h.assert(text.includes(String(billingNo)) && text.includes(AMOUNT), `The printed receipt lacks the invoice number or amount: ${text.slice(0, 300)}`);
    h.assert(!/Print With Notes|Update Invoice/.test(text), 'The printed receipt shows the page buttons');
    h.assert(!text.includes('Billing Notes') && !text.includes(note), 'Print printed the Billing Notes heading or the note');
  });

  await s.step(STEP.notes, async () => {
    const text = await printed('Print With Notes', 'with-notes');
    h.assert(text.includes(String(billingNo)) && text.includes(AMOUNT), `The printed receipt lacks the invoice number or amount: ${text.slice(0, 300)}`);
    h.assert(!/Print With Notes|Update Invoice/.test(text), 'The printed receipt shows the page buttons');
    h.assert(text.includes('Billing Notes') && text.includes(note),
      `Print With Notes did not print the Billing Notes heading and the note: ${text.slice(0, 400)}`);
  });
}

if (require.main === module) runWorkflow('billing-bc-receipt-print-notes', workflow, { preflight });
module.exports = { workflow, STEP };
