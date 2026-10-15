#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Upload from E-Chart Documents + and verify the redirect refreshes that module
 * through the live opener, without closing the popup or reloading the chart.
 * Owns a synthetic patient, one PDF, its document/link rows and generated note.
 * Cleanup removes those records and the stored file, and asserts their absence. */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { markOpener, clickAndAwaitReload } = require('./lib/playwright-ui');
const { documentChain, openerState, assertId } = require('./lib/popup-opener-helpers');
const { textPdf } = require('./lib/stored-pdf-documents');

async function workflow(s) {
  const { sql, patient, marker } = s;
  const store = process.env.DOCUMENT_DIR;
  if (!store || !fs.existsSync(store)) throw new h.SkipCheck('DOCUMENT_DIR must identify the readable document store');
  const description = `${marker} opener upload`;
  const owned = `SELECT d.document_no,d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
    WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${h.sqlString(description)}`;
  s.cleanup(() => {
    const files = new Set();
    for (const [rawId, file] of sql.rows(owned)) {
      const id = assertId(rawId, 'Invalid owned document id');
      h.assert(file && path.basename(file) === file, 'Stored document filename is not a basename');
      files.add(path.join(store, file));
      const notes = sql.rows(`SELECT l.note_id FROM casemgmt_note_link l JOIN casemgmt_note n ON n.note_id=l.note_id
        WHERE l.table_name=5 AND l.table_id=${id} AND n.demographic_no=${patient}`);
      for (const [rawNote] of notes) {
        const note = assertId(rawNote, 'Invalid owned upload note id');
        sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id=${note} AND table_name=5 AND table_id=${id};
          DELETE FROM casemgmt_issue_notes WHERE note_id=${note};
          DELETE FROM casemgmt_note WHERE note_id=${note} AND demographic_no=${patient}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id=${note}`) === '0',
          'An upload note link was not removed');
      }
      sql.execute(`DELETE FROM ctl_document WHERE document_no=${id} AND module='demographic' AND module_id=${patient};
        DELETE FROM document WHERE document_no=${id}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${id}`) === '0', 'Uploaded document was not removed');
    }
    for (const file of files) {
      if (fs.existsSync(file)) fs.unlinkSync(file);
      h.assert(!fs.existsSync(file), 'Uploaded file was not removed');
    }
    h.assert(sql.rows(owned).length === 0, 'Owned document attachment was not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND note LIKE ${h.sqlString(`%${marker}%`)}`) === '0', 'Upload-created chart note was not removed');
  });
  const chart = await s.chart();
  const sentinel = await markOpener(chart);
  const chain = documentChain(s.context);
  let popup;
  await s.step('Documents + opens an upload form with a live chart opener', async () => {
    popup = await s.popup(chart, chart.locator('a[onclick*="ViewDocumentReport?"][onclick*="mode=add"]').first(), 'document-add');
    await popup.locator('form[action*="addEditDocument"]').waitFor();
    h.assert(await openerState(popup) === 'live', 'The document upload lost its chart opener');
    h.assert(new URL(popup.url()).searchParams.get('parentAjaxId') === 'docs', 'The upload does not target the Documents module');
  });
  await s.step('Upload stores one PDF and refreshes Documents through the redirect without a chart reload', async () => {
    const form = popup.locator('form[action*="addEditDocument"]').first();
    const types = await form.locator('select[name="docType"] option').evaluateAll(options => options.map(option => option.value).filter(Boolean));
    h.assert(types.length > 0, 'The upload has no document type');
    await form.locator('select[name="docType"]').selectOption(types[0]);
    await form.locator('input[name="docDesc"]').fill(description);
    await form.locator('input[type="file"]').setInputFiles({ name: `${marker}.pdf`, mimeType: 'application/pdf', buffer: textPdf(marker) });
    await clickAndAwaitReload(popup, form.locator('input[name="Submit"]').first());
    await expectValue(sql, `SELECT COUNT(*) FROM (${owned}) owned_documents`, '1', 'Upload did not store exactly one attachment');
    h.assert(await openerState(popup) === 'live', `The upload redirect lost its opener: ${chain.describe(popup)}`);
    h.assert(chain.chain(popup).some(hop => hop.status === 302), 'The upload did not exercise its success redirect');
    h.assert(chain.chain(popup).every(hop => hop.coop === 'same-origin'), `A document response lost the opener policy: ${chain.describe(popup)}`);
    const [rawId, file] = sql.rows(owned)[0];
    const id = assertId(rawId, 'The saved document id is invalid');
    // Navbar captions include a date and may be cropped; identify the saved PDF
    // by its real document id and require its visible caption to show the marker.
    const link = chart.locator(`#docs a[onclick*="segmentID=${id}'"]`).filter({ hasText: marker.slice(0, 12) });
    await link.waitFor({ state: 'visible', timeout: 20000 });
    h.assert((await link.innerText()).includes(marker.slice(0, 12)), 'The Documents caption does not identify the upload');
    h.assert(await chart.evaluate(name => window[name], sentinel.marker) === sentinel.token, 'The chart reloaded instead of refreshing Documents');
    h.assert(path.basename(file) === file && fs.existsSync(path.join(store, file)), 'The uploaded document file is missing');
  });
}

if (require.main === module) runWorkflow('popup-opener-document-add', workflow, { openPatient: true });
module.exports = { workflow };
