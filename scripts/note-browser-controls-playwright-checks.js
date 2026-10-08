#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The note browser's sort, view and Print controls never submit its form to the GET-only gate
 * (issue #4368).
 *
 * casemgmt/ViewNoteBrowser (ViewClinical2Action) answers GET and HEAD only, and the page's
 * DisplayDoc form names it as its action. A control that submits that form (the old
 * <input type="image"> Print control, or a filter handler calling form.submit()) replaces the
 * note browser with a 405 page.
 *
 * User path: Schedule > Search > Master Record > E-Chart > type a note > Save; Browse Notes
 *   (casemgmt/ViewNoteBrowser popup) > Sort by Update / Observation / Content > View "others" >
 *   View "All" > encounter list > Print.
 * Asserts: the page's script parses and defines its handlers; the Print control is a
 *   type="button" control, not a submit control; every sort choice re-opens the note browser with
 *   a GET answering 200 that carries the chosen sortorder and no CSRF token, keeps the select on
 *   that choice, and lists the three owned documents in that order (each order is distinct);
 *   the doc-type view re-opens it with a GET that keeps the sort and lists only that type; Print
 *   opens the print popup, which downloads a PDF, and leaves the note browser on the same URL;
 *   no request other than GET ever reaches ViewNoteBrowser and none of its responses is a 405.
 * Fixtures: the run's FAKE- patient, three owned PDFs (document + ctl_document rows, files under
 *   DOCUMENT_DIR) with distinct content, update and observation dates, two of type "others" and
 *   one of type "lab", and the owned note; all removed and verified gone.
 * Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR), plus the harness contract.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

const TIMEOUT = 20000;
const GATE = '/casemgmt/ViewNoteBrowser';

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

/*
 * Dates chosen so each sort gives a different order (EDocUtil.EDocSort, all DESC):
 *   Content     (contentdatetime): C, B, A
 *   Update      (updatedatetime):  A, C, B
 *   Observation (observationdate): B, A, C
 */
const DATES = {
  A: { doctype: 'others', content: '2025-01-01 09:00:00', update: '2025-03-01 09:00:00', observation: '2025-02-01' },
  B: { doctype: 'others', content: '2025-02-01 09:00:00', update: '2025-01-01 09:00:00', observation: '2025-03-01' },
  C: { doctype: 'lab', content: '2025-03-01 09:00:00', update: '2025-02-01 09:00:00', observation: '2025-01-01' },
};
const ORDERS = { Content: ['C', 'B', 'A'], Update: ['A', 'C', 'B'], Observation: ['B', 'A', 'C'] };

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const docs = ownedPdfDocuments(store, marker, [{ key: 'A' }, { key: 'B' }, { key: 'C' }]);
  const byKey = Object.fromEntries(docs.map(doc => [doc.key, doc]));
  const owned = { sql, marker, patient, docs, files: docs.map(doc => doc.file) };
  const noteText = `${marker} note browser controls`;

  s.cleanup(() => {
    removeOwnedPdfDocuments(owned);
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned note rows were not removed');
    assertOwnedPdfDocumentsRemoved(owned);
  });

  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  for (const doc of docs) {
    const d = DATES[doc.key];
    sql.execute(`UPDATE document SET doctype=${h.sqlString(d.doctype)}, contentdatetime=${h.sqlString(d.content)},
      updatedatetime=${h.sqlString(d.update)}, observationdate=${h.sqlString(d.observation)}
      WHERE document_no=${doc.id} AND docdesc=${h.sqlString(doc.label)}`);
    h.assert(sql.value(`SELECT CONCAT_WS('|',doctype,contentdatetime,updatedatetime,observationdate) FROM document WHERE document_no=${doc.id}`)
      === [d.doctype, d.content, d.update, d.observation].join('|'), `Document ${doc.key} did not take its fixture dates`);
  }

  // Every request the note browser (or anything else) sends to the GET-only gate.
  const gateRequests = [];
  const gateRefusals = [];
  s.context.on('request', request => {
    if (new URL(request.url()).pathname.endsWith(GATE)) gateRequests.push(request.method());
  });
  s.context.on('response', response => {
    if (new URL(response.url()).pathname.endsWith(GATE) && response.status() === 405) gateRefusals.push(response.request().method());
  });
  const assertGateOnlyGot = () => {
    h.assert(gateRequests.every(method => method === 'GET'), `The note browser sent ${gateRequests.filter(m => m !== 'GET').join(', ')} to ${GATE}`);
    h.assert(gateRefusals.length === 0, `${GATE} answered 405 to ${gateRefusals.join(', ')}`);
  };

  let chart;
  let noteId;
  await s.step('E-Chart saves an owned note', async () => {
    chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await editor.click();
    await editor.fill(noteText);
    const [saved] = await Promise.all([chart.waitForResponse(isEntry('save'), { timeout: TIMEOUT }), chart.locator('#saveImg').first().click()]);
    h.assert(saved.status() === 200, `Saving the note answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`, noteText,
      'The note did not reach casemgmt_note');
    noteId = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient}`);
  });

  let notes;
  // The owned documents' keys in the order #doclist shows them.
  const listedOrder = async () => {
    const values = await notes.locator('#doclist option').evaluateAll(options => options.map(option => option.value));
    const keyOf = Object.fromEntries(docs.map(doc => [`${doc.id}-application/pdf`, doc.key]));
    return values.filter(value => keyOf[value]).map(value => keyOf[value]);
  };

  await s.step('Browse Notes opens with its handlers and a Print control that cannot submit the form', async () => {
    notes = await s.popup(chart, chart.locator('#note-control-panel button', { hasText: 'Browse Notes' }), 'note-browser');
    h.assert(new URL(notes.url()).pathname.endsWith(GATE) && new URL(notes.url()).searchParams.get('demographic_no') === patient,
      'Browse Notes did not open the note browser for the owned patient');
    await notes.locator('#doclist').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await notes.evaluate(() => ['ReLoadDoc', 'LoadView', 'reloadNoteBrowser', 'PrintEncounter'].every(name => typeof window[name] === 'function')),
      'The note browser script did not define its filter and print handlers');
    const control = await notes.locator('#imgPrintEncounter').evaluate(el => ({ tag: el.tagName, type: el.type }));
    h.assert(control.tag === 'BUTTON' && control.type === 'button', `The Print control is a ${control.tag} of type ${control.type}, which submits the form`);
    h.assert(await notes.locator('form[name="DisplayDoc"] input[type="image"], form[name="DisplayDoc"] input[type="submit"], form[name="DisplayDoc"] button:not([type="button"])').count() === 0,
      'The DisplayDoc form still carries a control that submits it to the GET-only gate');
    h.assert(await notes.locator('#selsortorder').inputValue() === 'Content', 'The note browser did not open sorted by content date');
    const order = await listedOrder();
    h.assert(JSON.stringify(order) === JSON.stringify(ORDERS.Content), `The default content-date order lists ${order} instead of ${ORDERS.Content}`);
  });

  async function reloadVia(action, expect) {
    const [response] = await Promise.all([
      notes.waitForResponse(r => new URL(r.url()).pathname.endsWith(GATE) && r.request().isNavigationRequest(), { timeout: TIMEOUT }),
      action(),
    ]);
    h.assert(response.request().method() === 'GET', `The note browser reloaded with ${response.request().method()}`);
    h.assert(response.status() === 200, `The note browser reload answered HTTP ${response.status()}`);
    await notes.waitForLoadState('load', { timeout: TIMEOUT });
    await notes.locator('#doclist').waitFor({ state: 'attached', timeout: TIMEOUT });
    const url = new URL(notes.url());
    h.assert(url.pathname.endsWith(GATE) && url.searchParams.get('demographic_no') === patient, 'The reload left the owned patient\'s note browser');
    h.assert(!url.searchParams.has('CSRF-TOKEN'), 'The reload copied the CSRF token into the URL');
    for (const [name, value] of Object.entries(expect)) {
      h.assert(url.searchParams.get(name) === value, `The reload sent ${name}=${url.searchParams.get(name)} instead of ${value}`);
    }
  }

  for (const sortorder of ['Update', 'Observation', 'Content']) {
    await s.step(`Sort by ${sortorder} re-opens the note browser with GET in that order`, async () => {
      await reloadVia(() => notes.locator('#selsortorder').selectOption(sortorder), { sortorder, viewstatus: 'active', view: 'all' });
      h.assert(await notes.locator('#selsortorder').inputValue() === sortorder, `The sort select did not stay on ${sortorder}`);
      const order = await listedOrder();
      h.assert(JSON.stringify(order) === JSON.stringify(ORDERS[sortorder]), `Sorting by ${sortorder} lists ${order} instead of ${ORDERS[sortorder]}`);
      assertGateOnlyGot();
    });
  }

  await s.step('View "others" re-opens with GET, keeps the sort and lists only that type', async () => {
    await reloadVia(() => notes.locator('#selsortorder').selectOption('Observation'), { sortorder: 'Observation' });
    const link = notes.locator('a[onclick*="LoadView(\'others\')"]');
    h.assert(await link.count() === 1, 'The note browser does not offer the "others" document type');
    await reloadVia(() => link.click(), { view: 'others', sortorder: 'Observation', viewstatus: 'active' });
    h.assert(await notes.locator('input[name="view"]').inputValue() === 'others', 'The page lost the document-type view');
    h.assert(await notes.locator('#selsortorder').inputValue() === 'Observation', 'Changing the view lost the sort');
    const expected = ORDERS.Observation.filter(key => DATES[key].doctype === 'others');
    const order = await listedOrder();
    h.assert(JSON.stringify(order) === JSON.stringify(expected), `The "others" view lists ${order} instead of ${expected}`);

    await reloadVia(() => notes.locator('a[onclick*="LoadView(\'all\')"]').click(), { view: 'all', sortorder: 'Observation' });
    const all = await listedOrder();
    h.assert(JSON.stringify(all) === JSON.stringify(ORDERS.Observation), `The "All" view lists ${all} instead of ${ORDERS.Observation}`);
    assertGateOnlyGot();
  });

  await s.step('Print opens the print popup and leaves the note browser as it was', async () => {
    h.assert(await notes.locator(`#encounterlist option[value="${noteId}"]`).count() === 1, 'The owned note is missing from the encounter list');
    await notes.locator('#encounterlist').selectOption(noteId);
    await notes.locator('#printnotesbutton').waitFor({ state: 'visible', timeout: TIMEOUT });
    const before = notes.url();
    const order = await listedOrder();
    let popup;
    try {
      // The print view answers with a download, so the popup never commits that URL: read the request.
      const isPrint = request => new URL(request.url()).pathname.endsWith('/CaseManagementEntry')
        && new URL(request.url()).searchParams.get('method') === 'print';
      let printRequest;
      [popup, printRequest] = await Promise.all([
        s.context.waitForEvent('page', { timeout: TIMEOUT }),
        s.context.waitForEvent('request', { predicate: isPrint, timeout: TIMEOUT }),
        notes.locator('#imgPrintEncounter').click(),
      ]);
      const printUrl = new URL(printRequest.url());
      h.assert(printUrl.pathname.endsWith('/CaseManagementEntry') && printUrl.searchParams.get('method') === 'print'
        && printUrl.searchParams.get('demographicNo') === patient && printUrl.searchParams.get('notes2print') === noteId,
      'Print did not open the print view for the selected note');
      const download = await popup.waitForEvent('download', { timeout: TIMEOUT });
      h.assert(!(await download.failure()), 'The note print download failed');
      h.assert(fs.readFileSync(await download.path()).subarray(0, 5).toString('latin1') === '%PDF-', 'The note print is not a PDF');
    } finally {
      if (popup && !popup.isClosed()) await popup.close().catch(() => {});
    }
    await notes.locator('#encounterlist').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(notes.url() === before, 'Printing navigated the note browser away');
    h.assert(JSON.stringify(await listedOrder()) === JSON.stringify(order), 'Printing changed the note browser\'s document list');
    h.assert(await notes.locator('#selsortorder').inputValue() === 'Observation', 'Printing reset the note browser\'s sort');
    assertGateOnlyGot();
    await notes.close();
  });
}

if (require.main === module) runWorkflow('note-browser-controls', workflow, { openPatient: true });
module.exports = { workflow, ORDERS, DATES };
