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
 * Asserts: each save reaches casemgmt_note and the history column keeps every earlier text;
 * the browser lists both owned PDFs and the note, previews the note, and its Print downloads a
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
const { createHash } = require('node:crypto');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

/** A one-page classic-xref PDF whose text names the document, so pdftotext can identify it. */
function textPdf(label) {
  const content = `BT /F1 12 Tf 40 700 Td (${label}) Tj ET\n`;
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`];
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  return Buffer.from(`${pdf}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

const sha = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');

function directory(name, ...keys) {
  const configured = keys.map(key => process.env[key]).find(Boolean);
  if (!configured) throw new h.SkipCheck(`Set ${name} to the installed directory`);
  const real = fs.realpathSync(configured);
  h.assert(fs.statSync(real).isDirectory(), `${name} is not a directory`);
  return real;
}

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const incoming = directory('INCOMINGDOCUMENT_DIR', 'INCOMINGDOCUMENT_DIR');
  const owner = fs.statSync(store);
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-note-browser-'));
  const docs = ['A', 'B'].map(key => ({
    key, label: `${marker} ${key}`, filename: `20260101000000${marker}-${key}.pdf`,
  }));
  docs.forEach(doc => { doc.file = path.join(store, doc.filename); });
  const [docA, docB] = docs;
  // EDocUtil.getRefiledDocumentFileName drops the 14-character upload timestamp and prefixes R.
  const refiled = path.join(incoming, '1', 'Refile', `R${docB.filename.substring(14)}`);
  const texts = [1, 2, 3].map(n => `${marker} note revision ${n}`);

  s.cleanup(() => {
    const ids = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned document identity');
    if (ids.length) {
      sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${ids.join(',')}) AND module='demographic' AND module_id=${patient};
        DELETE FROM document WHERE document_no IN (${ids.join(',')}) AND docdesc LIKE ${h.sqlString(`${marker}%`)}`);
    }
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    for (const file of [...docs.map(doc => doc.file), refiled]) if (fs.existsSync(file)) fs.unlinkSync(file);
    fs.rmSync(scratch, { recursive: true, force: true });
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${patient}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0',
    'Owned document or note rows were not removed');
    h.assert(![...docs.map(doc => doc.file), refiled].some(file => fs.existsSync(file)), 'Owned document files were not removed');
  });

  h.assert(!fs.existsSync(refiled), 'The refile destination already exists');
  for (const doc of docs) {
    fs.writeFileSync(doc.file, textPdf(doc.label), { flag: 'wx', mode: 0o640 });
    fs.chownSync(doc.file, owner.uid, owner.gid);
    doc.id = sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,source,updatedatetime,
        status,contenttype,contentdatetime,public1,observationdate,number_of_pages,restrictToProgram,abnormal)
      VALUES ('others',${h.sqlString(doc.label)},${h.sqlString(doc.filename)},${h.sqlString(provider)},${h.sqlString(provider)},
        '',NOW(),'A','application/pdf',NOW(),0,'2026-01-02',1,0,0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(doc.id), 'The owned document fixture was not created');
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${doc.id},'A')`);
  }
  const status = doc => sql.value(`SELECT status FROM document WHERE document_no=${doc.id}`);

  let chart;
  let noteId;
  await s.step('E-Chart: a note saved and then edited twice keeps every earlier text in its history', async () => {
    chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    for (const text of texts) {
      await editor.click();
      await editor.fill(text);
      const [saved] = await Promise.all([chart.waitForResponse(isEntry('save')), chart.locator('#saveImg').first().click()]);
      h.assert(saved.status() === 200, `Saving the note answered HTTP ${saved.status()}`);
      await expectValue(sql, `SELECT note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`,
        text, 'The edited note text did not reach casemgmt_note');
    }
    const [[uuids, latest, history]] = sql.rows(`SELECT COUNT(DISTINCT uuid),MAX(note_id),
      (SELECT history FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1)
      FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(uuids === '1', 'Editing the note created a different note instead of a revision');
    noteId = latest;
    h.assert(texts.every(text => history.includes(text)), 'The note history column lost an earlier revision');
    const rev = chart.locator('#encMainDiv a[onclick^="return showHistory("]').first();
    h.assert((await rev.innerText()).trim() === '3', 'The note editor does not show revision 3 after two edits');
  });

  let history;
  await s.step('The rev link opens the Note Revision History with the current revision', async () => {
    const rev = chart.locator('#encMainDiv a[onclick^="return showHistory("]').first();
    history = await s.popup(chart, rev, 'note-history');
    h.assert(new URL(history.url()).searchParams.get('method') === 'notehistory'
      && new URL(history.url()).searchParams.get('noteId') === noteId, 'The rev link opened the history of another note');
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

  await s.step('NoteBrowserDocumentDelete refuses GET and changes nothing', async () => {
    const url = new URL(`${s.config.baseUrl}/casemgmt/NoteBrowserDocumentDelete`);
    url.search = new URLSearchParams({ delDocumentNo: docB.id, demographicNo: patient }).toString();
    const response = await s.context.request.get(url.href, { maxRedirects: 0, failOnStatusCode: false });
    const code = response.status();
    await response.dispose();
    h.assert(code === 405, `GET NoteBrowserDocumentDelete answered HTTP ${code}, expected 405`);
    h.assert(status(docB) === 'A', 'GET NoteBrowserDocumentDelete deleted the document');
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

  await s.step('Print from the note browser downloads a PDF carrying the latest revision', async () => {
    await browser.locator('#encounterlist').selectOption(noteId);
    await browser.locator('#printnotesbutton').waitFor({ state: 'visible' });
    const [popup] = await Promise.all([s.context.waitForEvent('page'), browser.locator('#imgPrintEncounter').click()]);
    const download = await popup.waitForEvent('download');
    h.assert(!(await download.failure()), 'The note print download failed');
    const file = path.join(scratch, 'print.pdf');
    await download.saveAs(file);
    if (!popup.isClosed()) await popup.close();
    // The print control is an <input type="image"> inside the page's form: the note browser
    // itself must still be there afterwards, not replaced by that form's submission.
    await browser.locator('#encounterlist').waitFor({ state: 'attached' });
    h.assert(new URL(browser.url()).pathname.endsWith('/casemgmt/ViewNoteBrowser'), 'Printing navigated the note browser away');
    h.assert(fs.readFileSync(file).subarray(0, 5).toString('latin1') === '%PDF-', 'The note print is not a PDF');
    const text = execFileSync('pdftotext', [file, '-'], { encoding: 'utf8' });
    h.assert(text.includes(texts[2]), 'The printed PDF does not carry the latest note revision');
    h.assert(!text.includes(texts[0]), 'The printed PDF carries a superseded revision');
    await browser.close();
  });

  await s.step('The Note Revision History lists both earlier revisions', async () => {
    const body = await history.locator('body').innerText();
    h.assert(body.includes(texts[0]) && body.includes(texts[1]),
      'The note history popup shows only the current text: the two earlier revisions are not listed');
  });
}

if (require.main === module) runWorkflow('note-browser-documents', workflow, { openPatient: true });
module.exports = { workflow };
