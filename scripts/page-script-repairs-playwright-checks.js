#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Page-script repairs from issue #4131: four pages whose inline JavaScript failed to parse or
 * bound to the wrong element, so every control on them was dead.
 *
 * User paths (every page entered through the control a user clicks):
 *   1. Schedule > Search > Master Record > Documents (eDoc report popup) > Browse
 *      (documentManager/ViewDocumentBrowser) > the owned PDF > Edit (ViewEditDocument popup).
 *   2. Master Record > E-Chart > type a note > Save; Browse Notes (casemgmt/ViewNoteBrowser
 *      popup) > View status Deleted / Published (GET reloads) > encounter > Print.
 *   3. Schedule > Administration > Labs/Inbox > Lab Forwarding Rules (admin/labForwardingRules
 *      panel) > choose the test provider > Update (the confirm is dismissed: nothing is saved).
 *   4. Administration > System Management > Jobs Management (admin/ViewJobs in #myFrame) >
 *      the owned job's name (editor) > its calendar (schedule dialog) > Cancel.
 * Asserts: each page's script parses (strict page: no SyntaxError, no page error) and its
 * handlers exist; the Browse link and the browser's own URL carry categorykey=private and never
 * the patient's name; the default selection shows the PDF preview and the Refile control; Edit
 * opens the editor for that document; the note browser's filters re-open it with GET (no 405)
 * and Print opens the print popup without navigating the browser away; choosing a provider in
 * Lab Forwarding Rules loads that provider's rules and Update asks to confirm instead of
 * refusing; Jobs Management lists a markup-bearing job name as text, its name link opens the
 * editor, and the schedule dialog restores the stored cron (0 15,45 3 * * *) with full 0-59 /
 * 1-31 pickers, and Cancel leaves the row untouched; a stored schedule the pickers cannot show
 * (weekday names) is explained and its Save is disabled, so it is never rewritten.
 * Fixtures: the run's FAKE- patient, one owned document type containing '+' (ctl_doctype), one
 * owned PDF of that type (document + ctl_document rows, file under
 * DOCUMENT_DIR), the owned note, a DISABLED job type with a nonexistent class plus a DISABLED
 * job on it (never schedulable); the test provider's edoc_browser_in_document_report preference
 * is turned on and restored exactly, so do not run it concurrently with another check that reads
 * it. Cleanup removes exactly those rows and the file, and asserts they are gone.
 * Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR, as deb-install-validation.md exports it), plus the
 * harness contract.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { fixturePdf } = require('./incoming-pdf-extraction-playwright-checks');

const TIMEOUT = 20000;
const PREFERENCE = 'edoc_browser_in_document_report';
const CRON = '0 15,45 3 * * *';

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

/**
 * The install's document store. DOCUMENT_DIR names it; RX_FAX_DOCUMENT_DIR, which the
 * deb-install validation already exports for the same directory, is accepted in its place.
 * Called from runWorkflow's preflight, so a run without it skips before any fixture work.
 */
function documentStore() {
  const store = process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
  if (!store) throw new h.SkipCheck('DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR) is not set; this check stores one owned PDF there');
  let real;
  try {
    real = fs.realpathSync(store);
  } catch (error) {
    throw new Error(`DOCUMENT_DIR does not exist or is not accessible (${error.code})`);
  }
  h.assert(fs.statSync(real).isDirectory(), 'DOCUMENT_DIR is not a directory');
  return real;
}

/** One owned PDF on the patient's chart: the row, its chart link and the stored file. */
function seedDocument(s, store, docType) {
  const { sql, marker, patient, provider } = s;
  const filename = `${marker}.pdf`;
  const file = path.join(store, filename);
  let documentNo = null;
  s.cleanup(() => {
    if (documentNo) {
      h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${documentNo} AND docdesc=${h.sqlString(marker)}`) === '1',
        'Document fixture ownership changed');
      sql.execute(`DELETE FROM ctl_document WHERE document_no=${documentNo} AND module='demographic' AND module_id=${patient};
        DELETE FROM document WHERE document_no=${documentNo} AND docdesc=${h.sqlString(marker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${documentNo}`) === '0', 'The owned document row was not removed');
    }
    if (fs.existsSync(file)) fs.unlinkSync(file);
    h.assert(!fs.existsSync(file), 'The owned PDF was not removed');
  });
  h.assert(!fs.existsSync(file), 'The owned PDF path already exists');
  fs.writeFileSync(file, fixturePdf(marker), { mode: 0o644, flag: 'wx' });
  documentNo = sql.value(`INSERT INTO document
    (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
    VALUES (${h.sqlString(docType)},${h.sqlString(marker)},${h.sqlString(filename)},${h.sqlString(provider)},${h.sqlString(provider)},
      'A','application/pdf',0,3,0,CURDATE(),NOW(),NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(documentNo), 'Document fixture was not inserted');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${documentNo},'A')`);
  return documentNo;
}

/**
 * Turns the report's Browse link on for the test provider and restores the row exactly. The
 * value is snapshotted as HEX so NULL and the literal string 'NULL' round-trip (mysql -B prints
 * both alike), and cleanup refuses to overwrite a row that changed under the run.
 */
function enableBrowseLink(s) {
  const { sql, provider } = s;
  const where = `provider_no=${h.sqlString(provider)} AND name=${h.sqlString(PREFERENCE)}`;
  const snapshot = () => sql.rows(`SELECT id,IFNULL(HEX(value),'NULL') FROM property WHERE ${where} ORDER BY id`);
  const before = snapshot();
  h.assert(before.length <= 1, 'The test provider has duplicate document-browser preference rows');
  for (const [id, hex] of before) {
    h.assert(/^\d+$/.test(id) && (hex === 'NULL' || /^[0-9A-F]*$/i.test(hex)), 'Unexpected preference snapshot');
  }
  let insertedId = null;
  let applied = false;
  s.cleanup(() => {
    const current = snapshot();
    if (!applied) {
      h.assert(JSON.stringify(current) === JSON.stringify(before), 'The document-browser preference changed before the run set it');
      return;
    }
    const ours = JSON.stringify(current.map(([id]) => [id, Buffer.from('yes').toString('hex').toUpperCase()]));
    h.assert(JSON.stringify(current) === ours && current.length === 1
      && current[0][0] === (before.length ? before[0][0] : insertedId),
    'The document-browser preference changed during the run; it was left as found for an operator to check');
    if (before.length) {
      const [id, hex] = before[0];
      sql.execute(`UPDATE property SET value=${hex === 'NULL' ? 'NULL' : `UNHEX('${hex}')`} WHERE id=${id} AND ${where}`);
    } else sql.execute(`DELETE FROM property WHERE id=${insertedId} AND ${where}`);
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'The document-browser preference was not restored');
  });
  if (before.length) {
    sql.execute(`UPDATE property SET value='yes' WHERE id=${before[0][0]} AND ${where}`);
    applied = true;
  } else {
    insertedId = sql.value(`INSERT INTO property (provider_no,name,value) VALUES (${h.sqlString(provider)},${h.sqlString(PREFERENCE)},'yes');
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(insertedId), 'The preference fixture was not inserted');
    applied = true;
  }
}

/** A disabled job type that names no real class, and a disabled job on it with a stored cron. */
function seedJob(s, jobName) {
  const { sql, marker, provider } = s;
  const typeHigh = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM OscarJobType'));
  const jobHigh = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM OscarJob'));
  const ownedType = `id>${typeHigh} AND name LIKE ${h.sqlString(`${marker}%`)}`;
  const ownedJob = `id>${jobHigh} AND name LIKE ${h.sqlString(`${marker}%`)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM OscarJob WHERE ${ownedJob}; DELETE FROM OscarJobType WHERE ${ownedType}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM OscarJob WHERE ${ownedJob})+(SELECT COUNT(*) FROM OscarJobType WHERE ${ownedType})`) === '0',
      'Owned job fixtures were not removed');
  });
  const className = `io.github.carlos_emr.carlos.FakePw${marker.slice(-16)}NoSuchJob`;
  const typeId = sql.value(`INSERT INTO OscarJobType(name,description,className,enabled,updated)
    VALUES(${h.sqlString(`${marker} type`)},${h.sqlString(`${marker} never runs`)},${h.sqlString(className)},0,NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(typeId), 'The job type fixture was not created');
  const jobId = sql.value(`INSERT INTO OscarJob(name,description,oscarJobTypeId,cronExpression,providerNo,enabled,updated)
    VALUES(${h.sqlString(jobName)},${h.sqlString(`${marker} disabled fixture`)},${typeId},${h.sqlString(CRON)},${h.sqlString(provider)},0,NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(jobId), 'The job fixture was not created');
  return { typeId, jobId };
}

async function workflow(s) {
  const { sql, marker, patient, provider, recorder } = s;
  const store = documentStore();
  // An owned document type containing '+': the note browser's doc-type filter must survive
  // repeated reloads without the '+' being decoded into a space.
  const docType = `${marker}+plus`;
  let docTypeId = null;
  s.cleanup(() => {
    if (!docTypeId) return;
    sql.execute(`DELETE FROM ctl_doctype WHERE id=${docTypeId} AND doctype=${h.sqlString(docType)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_doctype WHERE doctype=${h.sqlString(docType)}`) === '0', 'The owned document type was not removed');
  });
  docTypeId = sql.value(`INSERT INTO ctl_doctype (module,doctype,status) VALUES ('demographic',${h.sqlString(docType)},'A'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(docTypeId), 'The document type fixture was not inserted');
  const documentNo = seedDocument(s, store, docType);
  const optionValue = `${documentNo}-application/pdf`;
  enableBrowseLink(s);

  // ---- Document Browser ------------------------------------------------------------------
  let browser;
  await s.step('Documents > Browse opens the document browser with a scope token, not the patient\'s name', async () => {
    const report = await s.popup(s.master, s.master.locator('a[onclick*="/documentManager/ViewDocumentReport"]').first(), 'edoc-report');
    const link = report.locator('a[href*="/documentManager/ViewDocumentBrowser"]').first();
    await link.waitFor({ state: 'attached', timeout: TIMEOUT });
    const href = new URL(await link.getAttribute('href'), report.url());
    h.assert(href.searchParams.get('categorykey') === 'private', 'The Browse link does not send categorykey=private');
    h.assert(!decodeURIComponent(href.href).includes(marker), 'The Browse link carries the patient\'s name');
    await Promise.all([report.waitForURL(url => url.pathname.endsWith('/documentManager/ViewDocumentBrowser'), { timeout: TIMEOUT }), link.click()]);
    browser = report;
    await browser.locator('#doclist').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(!decodeURIComponent(browser.url()).includes(marker), 'The document browser URL carries the patient\'s name');
    h.assert(await browser.evaluate(() => ['setdefaultdoc', 'getDoc', 'DocEdit', 'RefileDoc', 'DeleteDoc'].every(name => typeof window[name] === 'function')),
      'The document browser script did not define its handlers');
    h.assert(await browser.locator('input[name="categorykey"]').inputValue() === 'private', 'The browser re-posts a categorykey other than the token');
    h.assert((await browser.locator('body').innerText()).includes('Private Documents'), 'The browser does not label the private list');
  });

  await s.step('the default selection previews the owned PDF and shows Refile for it', async () => {
    h.assert(await browser.locator(`#doclist option[value="${optionValue}"]`).count() === 1, 'The owned PDF is missing from the document browser');
    await browser.locator('#doclist').selectOption(optionValue);
    await browser.locator(`#docdisp iframe[src*="doc_no=${documentNo}"]`).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await browser.locator('#docbuttons').isVisible(), 'The document buttons stay hidden for a selected document');
    h.assert(await browser.locator('#refilebutton').isVisible(), 'The Refile control is hidden for a PDF');
  });

  await s.step('Edit opens the document editor for the selected PDF', async () => {
    const editor = await s.popup(browser, browser.locator('#docbuttons input[type="button"][onclick="DocEdit();"]'), 'edoc-edit');
    const url = new URL(editor.url());
    h.assert(url.pathname.endsWith('/documentManager/ViewEditDocument') && url.searchParams.get('editDocumentNo') === documentNo,
      'Edit did not open the editor for the selected document');
    await editor.close();
    await browser.close();
  });

  // ---- Note Browser ----------------------------------------------------------------------
  const noteText = `${marker} note browser print`;
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned note rows were not removed');
  });
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
  await s.step('Browse Notes opens with a script that parses and previews the latest note', async () => {
    notes = await s.popup(chart, chart.locator('#note-control-panel button', { hasText: 'Browse Notes' }), 'note-browser');
    h.assert(new URL(notes.url()).searchParams.get('demographic_no') === patient, 'The note browser opened another patient');
    await notes.locator('#doclist').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await notes.evaluate(() => ['OnLoad', 'getDoc', 'getEncounter', 'showEncounter', 'PrintEncounter', 'DocEdit'].every(name => typeof window[name] === 'function')),
      'The note browser script did not define its handlers');
    h.assert(await notes.locator(`#doclist option[value="${optionValue}"]`).count() === 1, 'The owned PDF is missing from the note browser');
    h.assert(await notes.locator(`#encounterlist option[value="${noteId}"]`).count() === 1, 'The owned note is missing from the encounter list');
    await notes.frameLocator('#docdisp iframe').locator('body', { hasText: noteText }).waitFor({ timeout: TIMEOUT });
  });

  async function chooseStatus(value) {
    const [response] = await Promise.all([
      notes.waitForResponse(r => new URL(r.url()).pathname.endsWith('/casemgmt/ViewNoteBrowser') && r.request().isNavigationRequest(), { timeout: TIMEOUT }),
      notes.locator('#selviewstatus').selectOption(value),
    ]);
    h.assert(response.request().method() === 'GET', `Changing the view status sent ${response.request().method()}`);
    h.assert(response.status() === 200, `Changing the view status answered HTTP ${response.status()}`);
    await notes.locator('#doclist').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(new URL(notes.url()).searchParams.get('viewstatus') === value, 'The reloaded note browser lost the chosen status');
    h.assert(!new URL(notes.url()).searchParams.has('CSRF-TOKEN'), 'The filter reload copied the CSRF token into the URL');
  }

  await s.step('View status re-opens the GET-only note browser with GET', async () => {
    await chooseStatus('deleted');
    h.assert(await notes.locator(`#doclist option[value="${optionValue}"]`).count() === 0, 'The deleted view lists a published document');
    await chooseStatus('active');
    h.assert(await notes.locator(`#doclist option[value="${optionValue}"]`).count() === 1, 'The published view lost the owned document');
  });

  await s.step('a document type containing "+" survives the type filter and repeated reloads', async () => {
    const link = notes.locator('a[onclick*="LoadView("]', { hasText: docType });
    h.assert(await link.count() === 1, 'The owned document type is not offered as a filter');
    await Promise.all([
      notes.waitForURL(url => url.pathname.endsWith('/casemgmt/ViewNoteBrowser') && url.searchParams.get('view') === docType, { timeout: TIMEOUT }),
      link.click(),
    ]);
    await notes.locator('#doclist').waitFor({ state: 'attached', timeout: TIMEOUT });
    for (const status of ['deleted', 'active']) {
      await chooseStatus(status);
      h.assert(new URL(notes.url()).searchParams.get('view') === docType, `Reloading with status ${status} changed the document-type filter`);
      h.assert(await notes.locator('input[name="view"]').inputValue() === docType, 'The page lost the exact document-type filter');
    }
    h.assert(await notes.locator(`#doclist option[value="${optionValue}"]`).count() === 1,
      'The "+" document type no longer matches its own document after a reload');
  });

  await s.step('Print opens the print popup and leaves the note browser in place', async () => {
    await notes.locator('#encounterlist').selectOption(noteId);
    await notes.locator('#printnotesbutton').waitFor({ state: 'visible', timeout: TIMEOUT });
    const before = notes.url();
    let posted = false;
    const listener = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/casemgmt/ViewNoteBrowser')) posted = true;
    };
    s.context.on('request', listener);
    let popup;
    try {
      [popup] = await Promise.all([s.context.waitForEvent('page', { timeout: TIMEOUT }), notes.locator('#imgPrintEncounter').click()]);
      const download = await popup.waitForEvent('download', { timeout: TIMEOUT });
      h.assert(!(await download.failure()), 'The note print download failed');
      const file = await download.path();
      h.assert(fs.readFileSync(file).subarray(0, 5).toString('latin1') === '%PDF-', 'The note print is not a PDF');
    } finally {
      s.context.off('request', listener);
      if (popup && !popup.isClosed()) await popup.close().catch(() => {});
    }
    await notes.locator('#encounterlist').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(!posted, 'Print submitted the note browser form to the GET-only gate');
    h.assert(notes.url() === before, 'Printing navigated the note browser away');
    await notes.close();
  });

  // ---- Administration: Lab Forwarding Rules and Jobs Management --------------------------
  let admin;
  async function openAdmin() {
    if (admin && !admin.isClosed()) return admin;
    ({ page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder, label: 'page-script-administration', timeout: TIMEOUT }));
    await admin.waitForLoadState('load', { timeout: TIMEOUT });
    return admin;
  }

  await s.step('Lab Forwarding Rules loads the chosen provider and Update asks to confirm', async () => {
    await openAdmin();
    const link = admin.locator('#adminNav a.contentLink[href$="/admin/labForwardingRules"]').first();
    await revealAuditLink(admin, link, TIMEOUT);
    await ui.clickInjectsPanel(admin, link, { marker: '#provider-selection', timeout: TIMEOUT });
    const noneSelected = 'No provider has been selected.';
    h.assert((await admin.locator('#dynamic-content').innerText()).includes(noneSelected), 'The rules page did not open with no provider chosen');
    const [reloaded] = await Promise.all([
      admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/admin/labForwardingRules')
        && new URL(r.url()).searchParams.get('providerNo') === provider, { timeout: TIMEOUT }),
      admin.locator('#dynamic-content #provider-selection').selectOption(provider),
    ]);
    h.assert(reloaded.status() === 200, `Loading the provider's rules answered HTTP ${reloaded.status()}`);
    await admin.waitForFunction(({ value, none }) => {
      const select = document.querySelector('#dynamic-content #provider-selection');
      const panel = document.querySelector('#dynamic-content');
      return select && select.value === value && panel && !panel.innerText.includes(none);
    }, { value: provider, none: noneSelected }, { timeout: TIMEOUT });
    let posted = false;
    const listener = request => { if (request.method() === 'POST' && request.url().includes('/admin/ForwardingRules')) posted = true; };
    s.context.on('request', listener);
    try {
      const dialogs = await h.withExpectedDialogs(admin, () => admin.locator('#ForwardRulesForm input[type="submit"]').click(),
        { accept: false });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm',
        `Update did not ask to confirm saving the chosen provider's rules (got ${JSON.stringify(dialogs.map(d => d.type))})`);
    } finally { s.context.off('request', listener); }
    h.assert(!posted, 'A dismissed confirmation still posted the rules');
  });

  const jobName = `${marker} <b>bold</b> job`;
  const { jobId, typeId } = seedJob(s, jobName);
  let jobs;
  const jobRow = () => jobs.locator('#jobTable tbody tr', { hasText: marker });
  const isRest = (p, method) => r => r.request().method() === method && new URL(r.url()).pathname.endsWith(`/ws/rs/jobs/${p}`);
  await s.step('Jobs Management lists the owned job name as text', async () => {
    await openAdmin();
    // Matched by route, not label: the label is the localized admin.jobs.title.
    const link = admin.locator('#adminNav a.xlink[rel$="/admin/ViewJobs"]').first();
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame[src*="/admin/ViewJobs"]');
    await iframe.waitFor({ timeout: TIMEOUT });
    jobs = await (await iframe.elementHandle()).contentFrame();
    h.assert(jobs, 'Jobs Management did not load in the administration frame');
    await jobs.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
    await jobs.locator(`#jobType option[value="${typeId}"]`).waitFor({ state: 'attached', timeout: TIMEOUT });
    await jobRow().waitFor({ timeout: TIMEOUT });
    await jobs.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, { timeout: TIMEOUT });
    h.assert(await jobRow().locator('b').count() === 0 && (await jobRow().locator('td').nth(1).innerText()).trim() === jobName,
      'Jobs Management renders the stored job name as HTML instead of text');
    h.assert(await jobRow().locator('td').first().locator('i.fa-calendar.blue').count() === 1, 'A scheduled job is not flagged as scheduled');
  });

  const dialogButton = (dialogId, name) => jobs.locator(`.ui-dialog:has(#${dialogId})`).getByRole('button', { name, exact: true });
  await s.step('the job name opens the editor with the stored job', async () => {
    const [loaded] = await Promise.all([admin.waitForResponse(isRest(`job/${jobId}`, 'GET'), { timeout: TIMEOUT }),
      jobRow().getByRole('link', { name: jobName, exact: true }).click()]);
    h.assert(loaded.status() === 200, 'The job editor could not load the stored job');
    await jobs.locator('#new-job').waitFor({ state: 'visible', timeout: TIMEOUT });
    await jobs.waitForFunction(id => document.getElementById('jobId').value === String(id), jobId, { timeout: TIMEOUT });
    h.assert(await jobs.locator('#jobName').inputValue() === jobName && await jobs.locator('#jobType').inputValue() === String(typeId)
      && !(await jobs.locator('#jobEnabled').isChecked()), 'The job editor did not load the stored values');
    await dialogButton('new-job', 'Cancel').click();
    await jobs.locator('#new-job').waitFor({ state: 'hidden', timeout: TIMEOUT });
  });

  await s.step('the schedule dialog restores the stored cron with complete pickers', async () => {
    const [loaded] = await Promise.all([admin.waitForResponse(isRest(`job/${jobId}`, 'GET'), { timeout: TIMEOUT }),
      jobRow().locator('td').first().locator('a').click()]);
    h.assert(loaded.status() === 200, 'The schedule dialog could not load the stored job');
    await jobs.locator('#scheduleDialog').waitFor({ state: 'visible', timeout: TIMEOUT });
    await jobs.waitForFunction(id => document.getElementById('scheduleJobId').value === String(id), jobId, { timeout: TIMEOUT });
    const selected = select => jobs.locator(select).evaluate(el => [...el.selectedOptions].map(option => option.value).join(','));
    h.assert(await jobs.locator('#minute_chooser_choose').isChecked() && await jobs.locator('#hour_chooser_choose').isChecked()
      && await jobs.locator('#day_chooser_every').isChecked() && await jobs.locator('#month_chooser_every').isChecked()
      && await jobs.locator('#weekday_chooser_every').isChecked(), 'The schedule dialog did not restore which parts are chosen');
    h.assert(await selected('#minute') === '15,45' && await selected('#hour') === '3' && await jobs.locator('#minute').isEnabled(),
      'The schedule dialog did not restore the stored minutes and hour');
    const values = select => jobs.locator(`${select} option`).evaluateAll(options => options.map(option => option.value).join(','));
    h.assert(await values('#minute') === Array.from({ length: 60 }, (_, i) => i).join(','), 'The minute picker does not offer 0-59');
    h.assert(await values('#day') === Array.from({ length: 31 }, (_, i) => i + 1).join(','), 'The day picker does not offer 1-31');
    await dialogButton('scheduleDialog', 'Cancel').click();
    await jobs.locator('#scheduleDialog').waitFor({ state: 'hidden', timeout: TIMEOUT });
    h.assert(sql.value(`SELECT CONCAT_WS('|',cronExpression,enabled) FROM OscarJob WHERE id=${jobId}`) === `${CRON}|0`,
      'Cancelling the schedule dialog changed the job');
  });

  await s.step('a stored schedule the editor cannot show exactly blocks Save instead of being rewritten', async () => {
    // Weekday names cannot be shown by the numeric picker; opening and saving must not turn this
    // job into "every day".
    const unshowable = '0 0 9 * * MON-FRI';
    sql.execute(`UPDATE OscarJob SET cronExpression=${h.sqlString(unshowable)} WHERE id=${jobId} AND name=${h.sqlString(jobName)}`);
    const [loaded] = await Promise.all([admin.waitForResponse(isRest(`job/${jobId}`, 'GET'), { timeout: TIMEOUT }),
      jobRow().locator('td').first().locator('a').click()]);
    h.assert(loaded.status() === 200, 'The schedule dialog could not load the stored job');
    await jobs.locator('#scheduleDialog').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert((await jobs.locator('#scheduleDialog .validateTips').innerText()).includes(unshowable),
      'The dialog does not explain that the stored schedule cannot be edited here');
    const save = dialogButton('scheduleDialog', 'Save');
    h.assert(await save.isDisabled(), 'Save stays enabled for a schedule the dialog cannot represent');
    let posted = false;
    const listener = request => { if (request.method() === 'POST' && request.url().includes('/ws/rs/jobs/saveCrontabExpression')) posted = true; };
    s.context.on('request', listener);
    try {
      await save.click({ force: true });
      await jobs.waitForTimeout(500);
    } finally { s.context.off('request', listener); }
    h.assert(!posted, 'A disabled Save still posted the schedule');
    await dialogButton('scheduleDialog', 'Cancel').click();
    await jobs.locator('#scheduleDialog').waitFor({ state: 'hidden', timeout: TIMEOUT });
    h.assert(sql.value(`SELECT cronExpression FROM OscarJob WHERE id=${jobId}`) === unshowable, 'The unshowable schedule was changed');
  });
}

if (require.main === module) runWorkflow('page-script-repairs', workflow, { preflight: () => { documentStore(); } });
module.exports = { workflow };
