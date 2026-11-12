#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The chart's note browser and note history, driven from the E-Chart.
 * User path: Schedule > Search > Master Record > E-Chart > type a note > Save, edit it twice
 *   (Save); Browse Notes (casemgmt/ViewNoteBrowser popup) > encounter list > Print;
 *   document list > Delete (NoteBrowserDocumentDelete) > View status Deleted > Undelete
 *   (NoteBrowserDocumentUndelete) > Published > Refile to the default queue
 *   (NoteBrowserDocumentRefile); the note's "rev" link > Note Revision History popup
 *   (CaseManagementEntry method=notehistory, rendering showHistory.jsp).
 * Asserts: each save inserts an immutable casemgmt_note revision and keeps earlier text;
 * the browser lists both owned PDFs and the note, previews the note, and its Print produces a
 * PDF whose text carries the latest revision; Delete/Undelete flip document.status D/A and the
 * lists follow; Refile copies the stored PDF byte-for-byte to INCOMINGDOCUMENT_DIR/1/Refile and
 * leaves the row alone; NoteBrowserDocumentDelete refuses GET; the history popup shows the
 * current revision and every earlier one.
 * Fixtures: owned FAKE- patient, two owned PDFs (rows, ctl_document links, files carrying the
 * marker), the owned note and the refiled copy; all removed and verified gone.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md chart section
 * (note-browser-documents). Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR), INCOMINGDOCUMENT_DIR,
 * plus the harness contract; needs pdftotext.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const {
  requirePoppler, pdfText, sha, directory, ownedPdfDocuments, seedOwnedPdfDocuments,
  removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const incoming = directory('INCOMINGDOCUMENT_DIR', 'INCOMINGDOCUMENT_DIR');
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-note-browser-'));
  const docs = ownedPdfDocuments(store, marker, [{ key: 'A' }, { key: 'B' }]);
  const [docA, docB] = docs;
  // EDocUtil.getRefiledDocumentFileName drops the 14-character upload timestamp and prefixes R.
  const refiled = path.join(incoming, '1', 'Refile', `R${docB.filename.substring(14)}`);
  const owned = { sql, marker, patient, docs, files: [...docs.map(doc => doc.file), refiled] };
  const texts = [1, 2, 3].map(n => `${marker} note revision ${n}`);
  texts[0] += "\n<em>literal & text</em>";
  let storedHistory;
  let appointmentNo;

  s.cleanup(() => {
    removeOwnedPdfDocuments(owned);
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    if (appointmentNo) {
      h.assert(/^[1-9]\d*$/.test(appointmentNo)
        && sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointmentNo}
          AND demographic_no=${patient} AND notes=${h.sqlString(marker)}`) === '1',
      'Appointment ownership changed before cleanup');
      sql.execute(`DELETE FROM appointmentArchive WHERE appointment_no=${appointmentNo};
        DELETE FROM appointment WHERE appointment_no=${appointmentNo} AND demographic_no=${patient} AND notes=${h.sqlString(marker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointmentNo}`) === '0',
        'The owned appointment was not removed');
    }
    fs.rmSync(scratch, { recursive: true, force: true });
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned note rows were not removed');
    assertOwnedPdfDocumentsRemoved(owned);
  });

  h.assert(!fs.existsSync(refiled), 'The refile destination already exists');
  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  const status = doc => sql.value(`SELECT status FROM document WHERE document_no=${doc.id}`);

  // Independent of the Note Browser page, so it runs before the steps that page's script
  // errors stop: a GET must not delete a seeded, published document.
  await s.step('NoteBrowserDocumentDelete refuses GET and changes nothing', async () => {
    const query = new URLSearchParams({ delDocumentNo: docB.id, demographicNo: patient }).toString();
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, `/casemgmt/NoteBrowserDocumentDelete?${query}`), { maxRedirects: 0, failOnStatusCode: false });
    const code = response.status();
    await response.dispose();
    h.assert(code === 405, `GET NoteBrowserDocumentDelete answered HTTP ${code}, expected 405`);
    h.assert(status(docB) === 'A', 'GET NoteBrowserDocumentDelete deleted the document');
  });

  let chart;
  let noteId;
  await s.step('E-Chart: a note saved and then edited twice keeps every earlier text in its history', async () => {
    chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    const snapshots = [];
    for (const text of texts) {
      await editor.click();
      await editor.fill(text);
      const [saved] = await Promise.all([chart.waitForResponse(isEntry('save')), chart.locator('#saveImg').first().click()]);
      h.assert(saved.status() === 200, `Saving the note answered HTTP ${saved.status()}`);
      await expectValue(sql, `SELECT note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`,
        text, 'The edited note text did not reach casemgmt_note');
      const rows = sql.rows(`SELECT note_id,note,provider_no,signed,signing_provider_no,
        update_date,observation_date,uuid,appointmentNo FROM casemgmt_note
        WHERE demographic_no=${patient} ORDER BY note_id`);
      h.assert(rows.length === snapshots.length + 1, 'Saving an edit overwrote a previous note row');
      h.assert(JSON.stringify(rows.slice(0, -1)) === JSON.stringify(snapshots),
        'An edit changed the text, author, signature, timestamps or metadata of a prior revision');
      snapshots.push(rows.at(-1));
      const latestId = rows.at(-1)[0];
      await chart.waitForFunction(id => document.forms.caseManagementEntryForm.elements.noteId.value === id, latestId);
    }
    const [[uuids, latest, history]] = sql.rows(`SELECT COUNT(DISTINCT uuid),MAX(note_id),
      (SELECT history FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1)
      FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(uuids === '1', 'Editing the note created a different note instead of a revision');
    noteId = latest;
    h.assert(texts.every(text => history.includes(text)), 'The note history column lost an earlier revision');
    storedHistory = history;
    const rev = chart.locator('#encMainDiv a[onclick^="return showHistory("]').first();
    h.assert((await rev.innerText()).trim() === '3', 'The note editor does not show revision 3 after two edits');
  });

  let history;
  await s.step('The rev link opens the Note Revision History with the current revision', async () => {
    const rev = chart.locator('#encMainDiv a[onclick^="return showHistory("]').first();
    history = await s.popup(chart, rev, 'note-history');
    h.assert(new URL(history.url()).searchParams.get('method') === 'notehistory'
      && new URL(history.url()).searchParams.get('noteId') === noteId
      && new URL(history.url()).searchParams.get('demographicNo') === patient, 'The rev link opened the history of another note or patient');
    await history.locator('h3', { hasText: 'Note Revision History' }).waitFor();
    h.assert((await history.locator('body').innerText()).includes(texts[2]), 'The history popup does not show the current revision');
  });

  let browser;
  await s.step('Browse Notes lists both owned PDFs and the note, and previews the latest revision', async () => {
    browser = await s.popup(chart, chart.locator('#note-control-panel button', { hasText: 'Browse Notes' }), 'note-browser');
    h.assert(new URL(browser.url()).searchParams.get('demographic_no') === patient, 'The note browser opened another patient');
    for (const doc of docs) {
      h.assert(await browser.locator(`#doclist option[value="${doc.id}-application/pdf"][title="${doc.label}"]`).count() === 1,
        'An owned document is missing from the note browser');
    }
    h.assert(await browser.locator(`#encounterlist option[value="${noteId}"]`).count() === 1, 'The owned note is missing from the encounter list');
    // The page's whole script must parse: OnLoad, the list handlers and every button live in it.
    h.assertStrictPage(s.recorder);
    // OnLoad selects the first encounter and shows it through CaseManagementEntry displayNotes.
    const preview = browser.frameLocator('#docdisp iframe');
    await preview.locator('body', { hasText: texts[2] }).waitFor();
  });

  async function submitFrom(button, action) {
    const [post] = await Promise.all([
      browser.waitForResponse(r => new URL(r.url()).pathname.endsWith(`/casemgmt/${action}`) && r.request().method() === 'POST'),
      browser.waitForURL(url => url.pathname.endsWith('/casemgmt/ViewNoteBrowser')),
      button.click(),
    ]);
    h.assert(post.status() === 302, `${action} answered HTTP ${post.status()}`);
    await browser.locator('#doclist').waitFor({ state: 'attached' });
    h.assert(!new URL(browser.url()).searchParams.get('errorMessage'), `The note browser reported a ${action} failure`);
  }
  const listed = doc => browser.locator(`#doclist option[value="${doc.id}-application/pdf"]`).count();
  async function reload(view) {
    await Promise.all([browser.waitForEvent('load'), browser.locator('#selviewstatus').selectOption(view)]);
    await browser.locator('#doclist').waitFor({ state: 'attached' });
  }

  await s.step('Delete marks the selected document D and drops it from the published list', async () => {
    await browser.locator('#doclist').selectOption(`${docA.id}-application/pdf`);
    await submitFrom(browser.locator('#docbuttons input[type="button"][value="Delete"]'), 'NoteBrowserDocumentDelete');
    h.assert(status(docA) === 'D' && status(docB) === 'A', 'Delete did not mark exactly the selected document deleted');
    h.assert(await listed(docA) === 0 && await listed(docB) === 1, 'The published list did not follow the delete');
  });

  await s.step('Refile copies the selected PDF byte-for-byte into the default queue', async () => {
    await browser.locator('#doclist').selectOption(`${docB.id}-application/pdf`);
    await browser.locator('#refilebutton').waitFor({ state: 'visible' });
    await browser.locator('#queueList').selectOption('1');
    await submitFrom(browser.locator('#refilebutton input[type="button"][value="Refile"]'), 'NoteBrowserDocumentRefile');
    h.assert(fs.existsSync(refiled) && sha(refiled) === sha(docB.file), 'The refiled copy is missing or differs from the stored PDF');
    h.assert(sql.value(`SELECT CONCAT_WS('|',status,docfilename) FROM document WHERE document_no=${docB.id}`)
      === `A|${docB.filename}`, 'Refiling changed the source document row');
  });

  await s.step('View status Deleted lists the deleted document and Undelete restores status A', async () => {
    await reload('deleted');
    h.assert(await listed(docA) === 1 && await listed(docB) === 0, 'The deleted view does not list exactly the deleted document');
    await browser.locator('#doclist').selectOption(`${docA.id}-application/pdf`);
    await submitFrom(browser.locator('#docbuttons input[type="button"][value="Undelete"]'), 'NoteBrowserDocumentUndelete');
    h.assert(status(docA) === 'A', 'Undelete did not restore the document status');
    h.assert(await listed(docA) === 0, 'The deleted view still lists the restored document');
    await reload('active');
    h.assert(await listed(docA) === 1 && await listed(docB) === 1, 'The published view does not list both documents again');
  });

  await s.step('Print from the note browser produces a PDF carrying the latest revision', async () => {
    await browser.locator('#encounterlist').selectOption(noteId);
    await browser.locator('#printnotesbutton').waitFor({ state: 'visible' });
    const beforePages = new Set(s.context.pages());
    const [printRequest] = await Promise.all([
      s.context.waitForEvent('request', { predicate: request => {
        const url = new URL(request.url());
        return request.isNavigationRequest() && url.pathname.endsWith('/CaseManagementEntry')
          && url.searchParams.get('method') === 'print';
      }, timeout: 30000 }),
      browser.locator('#imgPrintEncounter').click(),
    ]);
    const url = new URL(printRequest.url());
    h.assert(printRequest.method() === 'GET' && url.searchParams.get('demographicNo') === patient
      && url.searchParams.get('notes2print') === noteId, 'The print control requested another patient or revision');
    // A PDF navigation can become a viewer or a download before the popup event is
    // delivered. Read the exact URL requested by the real control through the same
    // authenticated context, independent of the browser's PDF presentation setting.
    const response = await s.context.request.get(url.href, { maxRedirects: 0 });
    const file = path.join(scratch, 'print.pdf');
    try {
      h.assert(response.status() === 200 && (response.headers()['content-type'] || '').includes('application/pdf'),
        'The note print response is not a PDF');
      fs.writeFileSync(file, await response.body());
    } finally { await response.dispose(); }
    for (const page of s.context.pages()) {
      if (!beforePages.has(page) && !page.isClosed()) await page.close();
    }
    h.assert(fs.readFileSync(file).subarray(0, 5).toString() === '%PDF-', 'The note print has no PDF signature');
    // The print control is an <input type="image"> inside the page's form: the note browser
    // itself must still be there afterwards, not replaced by that form's submission.
    await browser.locator('#encounterlist').waitFor({ state: 'attached' });
    h.assert(new URL(browser.url()).pathname.endsWith('/casemgmt/ViewNoteBrowser'), 'Printing navigated the note browser away');
    h.assert(fs.readFileSync(file).subarray(0, 5).toString('latin1') === '%PDF-', 'The note print is not a PDF');
    const text = pdfText(file);
    h.assert(text.includes(texts[2]), 'The printed PDF does not carry the latest note revision');
    h.assert(!text.includes(texts[0]), 'The printed PDF carries a superseded revision');
    await browser.close();
  });

  await s.step('The Note Revision History lists both earlier revisions', async () => {
    const body = await history.locator('body').innerText();
    h.assert(body.includes(texts[0]) && body.includes(texts[1]),
      'The note history popup shows only the current text: the two earlier revisions are not listed');
    const stored = history.locator('.note-text-history-content');
    h.assert(await stored.count() === 1, 'The saved note should expose one stored text history');
    const displayed = await stored.innerText();
    const normalize = value => value.replace(/\r\n?/g, '\n').split('\n')
      .map(line => line.replace(/[^\S\n]+/g, ' ').trim()).join('\n').trim();
    h.assert(normalize(displayed) === normalize(storedHistory), 'The popup changed or omitted stored history text');
    h.assert(displayed.indexOf(texts[2]) < displayed.indexOf(texts[1])
      && displayed.indexOf(texts[1]) < displayed.indexOf(texts[0]), 'Stored revisions are not newest first');
    h.assert(await stored.locator('em').count() === 0, 'Stored note markup became an HTML element');
  });
  await s.step('History refuses a note requested under a different patient', async () => {
    const url = new URL(history.url());
    url.searchParams.set('demographicNo', '2147483647');
    const response = await s.context.request.get(url.href);
    h.assert(response.status() === 403, `Cross-patient history answered HTTP ${response.status()}`);
    const body = await response.text();
    h.assert(!texts.some(text => body.includes(text)), 'A refused history request exposed clinical text');
    await response.dispose();
  });
  await s.step('Both tickler dialogs open history for their patient and note', async () => {
    const message = `${marker} history tickler`;
    let ticklerId;
    let ownershipConfirmed = false;
    const ownedTickler = `demographic_no=${patient} AND message=${h.sqlString(message)}`;
    s.cleanup(() => {
      if (!ownershipConfirmed) return;
      // Recover a committed insert even when the SQL client never returned its ID.
      const ids = sql.value(`SELECT GROUP_CONCAT(tickler_no) FROM tickler WHERE ${ownedTickler}`) || '0';
      h.assert(/^[0-9]+(?:,[0-9]+)*$/.test(ids), 'Invalid owned tickler cleanup identifiers');
      sql.execute(`DELETE FROM casemgmt_note_link WHERE table_name=10 AND table_id IN (${ids}) AND note_id=${noteId};
        DELETE FROM tickler_update WHERE tickler_no IN (${ids});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids});
        DELETE FROM tickler_link WHERE tickler_no IN (${ids});
        DELETE FROM tickler WHERE tickler_no IN (${ids}) AND ${ownedTickler}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE ${ownedTickler})
        + (SELECT COUNT(*) FROM casemgmt_note_link WHERE table_name=10 AND table_id IN (${ids}))
        + (SELECT COUNT(*) FROM tickler_update WHERE tickler_no IN (${ids}))
        + (SELECT COUNT(*) FROM tickler_comments WHERE tickler_no IN (${ids}))
        + (SELECT COUNT(*) FROM tickler_link WHERE tickler_no IN (${ids}))`) === '0',
        'The owned history tickler or a child row was not removed');
    });
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${ownedTickler}`) === '0', 'The history tickler marker already exists');
    ownershipConfirmed = true;
    ticklerId = sql.value(`INSERT INTO tickler
      (demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES (${patient},${h.sqlString(message)},'A',NOW(),NOW(),${h.sqlString(provider)},'Normal',${h.sqlString(provider)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(ticklerId), 'The history tickler fixture was not created');
    sql.execute(`INSERT INTO casemgmt_note_link (table_name,table_id,note_id) VALUES (10,${ticklerId},${noteId})`);
    for (const route of ['ViewTicklerMain', 'ViewTicklerDemoMain']) {
      const page = await s.context.newPage();
      await h.gotoApp(page, s.config.baseUrl, `/tickler/${route}?demoview=${patient}&ticklerview=A`);
      if (route === 'ViewTicklerMain') {
        await page.locator('#ticklerResults_filter input[type="search"]').fill(message);
        await page.locator('#ticklerResults tbody tr').filter({hasText: message}).locator('a.noteDialogLink').click();
      } else {
        await page.locator(`a[onclick*="openNoteDialog('${patient}','${ticklerId}')"]`).click();
      }
      await page.locator('#note-form').waitFor({state: 'visible'});
      const popup = await s.popup(page, page.locator('#tickler_note_revision_url'), `${route}-history`);
      const params = new URL(popup.url()).searchParams;
      h.assert(params.get('demographicNo') === patient && params.get('noteId') === noteId,
        `${route} omitted or changed the history patient/note`);
      await popup.locator('h3', {hasText: 'Note Revision History'}).waitFor();
      h.assert((await popup.locator('.note-text-history-content').innerText()).includes(texts[0]),
        `${route} did not show the earlier saved note text`);
      await popup.close();
      await page.close();
    }
  });

  await s.step('Multiple saved rows expose cumulative text history only on the latest row', async () => {
    // The three rows above were created through the editor, each with cumulative history.
    // Copy the persisted metadata too: nullable database defaults are invalid for
    // primitive model fields such as appointmentNo and locked.
    const metadata = ['observation_date', 'demographic_no', 'provider_no', 'uuid', 'program_no',
      'signed', 'include_issue_innote', 'signing_provider_no', 'encounter_type', 'billing_code',
      'reporter_caisi_role', 'reporter_program_team', 'password', 'locked', 'archived', 'position',
      'appointmentNo', 'hourOfEncounterTime', 'minuteOfEncounterTime',
      'hourOfEncTransportationTime', 'minuteOfEncTransportationTime'].join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '3',
      'The editor did not retain exactly three saved revisions');
    const response = await history.reload();
    h.assert(response.status() === 200, `Multiple-row history answered HTTP ${response.status()}`);
    const stored = history.locator('.note-text-history-content');
    h.assert(await stored.count() === 1, 'Cumulative history is repeated beneath older saved rows');
    const normalize = value => value.replace(/\r\n?/g, '\n').split('\n')
      .map(line => line.replace(/[^\S\n]+/g, ' ').trim()).join('\n').trim();
    h.assert(normalize(await stored.innerText()) === normalize(storedHistory),
      'The single expansion does not retain the latest complete stored history');
    h.assert(await stored.locator('em').count() === 0, 'Stored markup became HTML after loading multiple rows');
    // innerText retains the line breaks rendered from stored multiline prose.
    const rows = await history.locator('body > div > div:first-child').all();
    const rowTexts = (await Promise.all(rows.map(row => row.innerText()))).map(normalize);
    h.assert(JSON.stringify(rowTexts) === JSON.stringify(texts.map(normalize)),
      'A saved row lost its own revision text or the rows are out of order');
    // DATETIME stores seconds: a later saved row can have the same update timestamp.
    const newestText = `${marker} same-second newest revision`;
    const newestHistory = `${newestText}\n----------------History Record----------------\n${storedHistory}`;
    sql.execute(`INSERT INTO casemgmt_note (update_date, note, history, ${metadata})
      SELECT update_date, ${h.sqlString(newestText)}, ${h.sqlString(newestHistory)}, ${metadata}
      FROM casemgmt_note WHERE note_id=${noteId} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '4',
      'The same-second fixture did not create exactly one newer saved revision');
    await history.reload();
    h.assert(await stored.count() === 1 && normalize(await stored.innerText()) === normalize(newestHistory),
      'A timestamp tie selected an older cumulative history and omitted the newest revision');

  });

  await s.step('a stale editor cannot supersede a newer revision and retains its unsaved text', async () => {
    // The last fixture above represents another session saving after this editor opened.
    const before = sql.rows(`SELECT note_id,note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`);
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    const draft = `${marker} unsaved stale edit`;
    await editor.fill(draft);
    const mark = failureMark(s.recorder);
    const dialogs = await h.withExpectedDialogs(chart, async () => {
      const [saved] = await Promise.all([chart.waitForResponse(isEntry('save')), chart.locator('#saveImg').first().click()]);
      h.assert(saved.status() === 409, `A stale edit answered HTTP ${saved.status()}, expected a conflict`);
      await chart.waitForTimeout(300);
    });
    h.assert(dialogs.some(dialog => dialog.type === 'alert'), 'The stale save did not report its failure');
    h.assert(await editor.inputValue() === draft, 'The refused save discarded the unsaved draft');
    h.assert(JSON.stringify(sql.rows(`SELECT note_id,note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`))
      === JSON.stringify(before), 'The refused stale save changed a revision');
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/CaseManagementEntry$/ });
  });

  await s.step('a stale JSON sign-and-exit request leaves its appointment and note revisions unchanged', async () => {
    // This endpoint also serves API clients; probe its rejection contract using the live session/CSRF token.
    appointmentNo = sql.value(`INSERT INTO appointment
      (provider_no,appointment_date,start_time,end_time,name,demographic_no,program_id,notes,
       reason,location,resources,type,style,billing,status,createdatetime,updatedatetime,creator,remarks,urgency)
      SELECT ${h.sqlString(provider)},CURDATE(),'11:30:00','11:44:00',CONCAT(last_name,',',first_name),
        ${patient},0,${h.sqlString(marker)},${h.sqlString(marker)},'','','','','','t',NOW(),NOW(),${h.sqlString(provider)},'',''
      FROM demographic WHERE demographic_no=${patient}; SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(appointmentNo), 'The owned appointment fixture was not created');
    const before = sql.rows(`SELECT note_id,note,signed,signing_provider_no,appointmentNo
      FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`);
    const token = await ui.csrfTokenPresent(chart);
    const response = await s.context.request.post(h.appUrl(s.config.baseUrl, '/CaseManagementEntry'), {
      form: { method: 'issueNoteSaveJson', demographic_no: patient, noteId, appointment_no: appointmentNo,
        value: `${marker} rejected JSON signature`, sign: 'true', signAndExit: 'true', 'CSRF-TOKEN': token },
      headers: { 'CSRF-TOKEN': token }, maxRedirects: 0, failOnStatusCode: false,
    });
    try { h.assert(response.status() === 409, `The stale JSON save answered HTTP ${response.status()}`); }
    finally { await response.dispose(); }
    h.assert(sql.value(`SELECT status FROM appointment WHERE appointment_no=${appointmentNo}`) === 't',
      'The rejected JSON save changed the appointment status');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${appointmentNo}`) === '0',
      'The rejected JSON save archived an appointment change');
    h.assert(JSON.stringify(sql.rows(`SELECT note_id,note,signed,signing_provider_no,appointmentNo
      FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`)) === JSON.stringify(before),
    'The rejected JSON save changed a note revision');
  });

  await s.step('stale full-form Save presents an encoded recovery copy without changing saved revisions', async () => {
    const before = sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`);
    const draft = `\n ${marker} unsaved full-form draft\n</textarea><script>window.draftExecuted=true</script> & literal text`;
    await chart.locator('#encMainDiv textarea[name="caseNote_note"]').fill(draft);
    const form = chart.locator('form[name="caseManagementEntryForm"]');
    const originalTarget = await form.getAttribute('target');
    const mark = failureMark(s.recorder);
    let recoveryPage;
    try {
      // Attach the strict recorder before this window receives the navigation response.
      // Keep the stale editor open so it can also exercise Sign & Save below.
      const recoveryTarget = `note-recovery-${marker}`;
      [recoveryPage] = await Promise.all([
        chart.waitForEvent('popup'),
        chart.evaluate(target => { window.open('about:blank', target); }, recoveryTarget),
      ]);
      const [response] = await Promise.all([
        s.context.waitForEvent('response', { predicate: r => isEntry('save')(r) && r.request().isNavigationRequest() }),
        chart.evaluate(target => {
          document.forms.caseManagementEntryForm.target = target;
          savePage('save', 'list');
        }, recoveryTarget),
      ]);
      await response.finished();
      h.assert(response.request().frame() === recoveryPage.mainFrame(), 'The full-form response belongs to a different window');
      const submitted = new URLSearchParams(response.request().postData() || '');
      h.assert(submitted.get('noteId') === noteId
        && submitted.get('caseNote_note').replace(/\r\n?/g, '\n') === draft,
      'The full-form save did not submit the stale revision and complete draft');
      h.assert(response.status() === 409, `Stale full-form Save answered HTTP ${response.status()}`);
      const recovery = recoveryPage.locator('#unsaved-note-text');
      await recovery.waitFor({ state: 'visible' });
      h.assert(await recovery.inputValue() === draft, 'The full-form recovery page lost or changed the submitted draft');
      h.assert(await recovery.getAttribute('readonly') !== null, 'The full-form recovery copy should be read-only');
      h.assert(await recoveryPage.evaluate(() => !window.draftExecuted), 'Full-form draft text executed as script');
      h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`))
        === JSON.stringify(before), 'The refused full-form save changed a saved revision');
      h.assert(s.recorder.badResponses.slice(mark.responses).every(entry => entry.url === response.url()),
        'The recovery step recorded an unrelated failure');
      consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/CaseManagementEntry$/ });
    } finally {
      await form.evaluate((element, target) => {
        if (target === null) element.removeAttribute('target');
        else element.setAttribute('target', target);
      }, originalTarget);
      if (recoveryPage) await recoveryPage.close();
    }
  });

  await s.step('stale Sign & Save presents an encoded recovery copy instead of a blank page', async () => {
    const before = sql.rows(`SELECT note_id,note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`);
    const draft = `\n ${marker} unsaved signed draft\n</textarea><script>window.draftExecuted=true</script> & literal text`;
    await chart.locator('#encMainDiv textarea[name="caseNote_note"]').fill(draft);
    const mark = failureMark(s.recorder);
    const [response] = await Promise.all([
      chart.waitForResponse(isEntry('saveAndExit')),
      chart.locator('#signSaveImg').first().click(),
    ]);
    h.assert(response.status() === 409, `Stale Sign & Save answered HTTP ${response.status()}`);
    const recovery = chart.locator('#unsaved-note-text');
    await recovery.waitFor({ state: 'visible' });
    h.assert(await recovery.inputValue() === draft, 'The recovery page lost or changed the submitted draft');
    h.assert(await recovery.getAttribute('readonly') !== null, 'The recovery copy should be read-only');
    h.assert(await chart.evaluate(() => !window.draftExecuted), 'Draft text executed as script in the recovery page');
    h.assert(JSON.stringify(sql.rows(`SELECT note_id,note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`))
      === JSON.stringify(before), 'The refused signed save changed a revision');
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/CaseManagementEntry$/ });
  });
}

if (require.main === module) runWorkflow('note-browser-documents', workflow, { openPatient: true, preflight: () => requirePoppler('pdftotext') });
module.exports = { workflow };
