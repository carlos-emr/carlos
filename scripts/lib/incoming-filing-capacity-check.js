/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const h = require('./playwright-harness');

function validateFilingRequest(request, owned) {
  const fields = new URLSearchParams(request.postData() || '');
  h.assert(request.method() === 'POST' && request.headers()['x-carlos-incoming-filing'] === 'bounded-v1',
    'Filing must use the explicit pre-acceptance contract');
  for (const [key, value] of Object.entries({method: 'addIncomingDocument', pdfName: owned.name,
    demog: owned.patient, documentDescription: owned.description, queueId: '1', pdfDir: 'File'})) {
    h.assert(fields.getAll(key).length === 1 && fields.get(key) === value, 'Refusing a filing outside the owned fixture');
  }
  h.assert(fields.getAll('CSRF-TOKEN').length === 1 && fields.get('CSRF-TOKEN'), 'Filing omitted its CSRF token');
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

/** Real installed form, guarded injected refusals, then one actual owned filing. */
async function checkIncomingFilingCapacity(session, page, name, source, inspect) {
  h.assert(session.patient && /^[1-9]\d*$/.test(session.patient), 'Filing requires the workflow-owned patient');
  const directory = process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR;
  h.assert(directory, 'Set RX_FAX_DOCUMENT_DIR or DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(directory);
  const description = `${session.marker} incoming filing`;
  session.cleanup(() => cleanupFiling(session, description, store));
  const original = fs.readFileSync(source);
  const owned = {name, patient: session.patient, description};
  const endpoint = new URL(await page.locator('#forms_').getAttribute('action'), page.url()).href;
  const pattern = url => url.href === endpoint;
  const targets = new Set();
  const bodies = [];
  let phase = 'unknown', attempts = 0, unsafe = false;
  const count = () => session.sql.value(`SELECT COUNT(*) FROM document WHERE docdesc=${h.sqlString(description)}`);
  const handler = async route => {
    try { bodies.push(validateFilingRequest(route.request(), owned)); }
    catch (error) { unsafe = true; await route.abort('blockedbyclient'); return; }
    attempts++;
    if (phase === 'unknown') return route.fulfill({status: 200, contentType: 'text/html', body: '<html>Unconfirmed response</html>'});
    if (attempts <= 5) {
      h.assert(fs.readFileSync(source).equals(original) && count() === '0', 'An unaccepted filing changed the source or chart');
      targets.add(route.request().url());
      return route.fulfill({status: 503, contentType: 'application/json', headers: {'Retry-After': '1', 'Cache-Control': 'no-store'},
        body: JSON.stringify({success: false, retryable: true, accepted: false, error: 'Waiting for document capacity.'})});
    }
    return route.continue();
  };
  async function fill() {
    const type = await page.locator('#docType option').evaluateAll(options => options.find(option => option.value)?.value);
    h.assert(type, 'Installed incoming form has no document type');
    await page.locator('#docType').selectOption(type);
    await page.locator('#documentDescription').fill(description);
    await page.locator('#autocompletedemo').fill(session.marker);
    await page.locator('.ui-autocomplete li').filter({hasText: session.marker}).first().click();
    h.assert(await page.locator('#demofind').inputValue() === session.patient, 'Patient selection left the owned chart');
  }
  await page.route(pattern, handler);
  try {
    await fill();
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
    const done = page.waitForResponse(response => response.url() === endpoint && response.status() === 200, {timeout: 120000});
    done.catch(() => {});
    await page.locator('#save').click();
    await page.locator('#incoming-filing-status').filter({hasText: 'Waiting for document capacity.'}).waitFor();
    h.assert(await page.locator('#save').isDisabled() && await page.locator('#documentDescription').inputValue() === description,
      'Waiting did not preserve frozen form input');
    const result = await done;
    const accepted = await result.json();
    h.assert(accepted.success === true && accepted.accepted === true && Number.isSafeInteger(accepted.documentNo), 'Installed filing did not confirm acceptance');
    await page.waitForURL(url => url.pathname.endsWith('/documentManager/ViewIncomingDocs') && url.searchParams.get('queueId') === '1');
    h.assert(!unsafe && attempts === 6 && new Set(bodies).size === 1, 'Filing retries changed inputs or did not recover after five refusals');
    h.assert(count() === '1' && !fs.existsSync(source), 'Recovered filing duplicated the document or left its queue source');
    const rows = session.sql.rows(`SELECT d.docfilename,d.numberofpages FROM document d JOIN ctl_document c ON c.document_no=d.document_no WHERE d.document_no=${accepted.documentNo} AND d.docdesc=${h.sqlString(description)} AND c.module='demographic' AND c.module_id=${session.patient}`);
    h.assert(rows.length === 1 && rows[0][1] === '1', 'Installed filing has incorrect patient linkage or page count');
    h.assert(path.basename(rows[0][0]) === rows[0][0] && rows[0][0].includes(session.marker), 'Accepted filename is not owned');
    h.assert(inspect(path.join(store, rows[0][0])).text.includes(`${session.marker} page 3`), 'Installed filing lost source page content');
    session.recorder.badResponses = session.recorder.badResponses.filter(entry => !(entry.status === 503 && targets.has(entry.url)));
    session.recorder.consoleIssues = session.recorder.consoleIssues.filter(entry => !(targets.has(entry.location?.url) && /Failed to load resource:.*503/.test(entry.text)));
  } finally { await page.unroute(pattern, handler); }
}
module.exports = {validateFilingRequest, cleanupFiling, checkIncomingFilingCapacity};
