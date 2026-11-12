/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const { watchChartPrintDownload } = require('./chart-print-download');
const { directory, requirePoppler, ownedPdfDocuments, textPdf,
  removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved, sha, pdfText } = require('./stored-pdf-documents');
const q = h.sqlString;

function requireNoteBrowserDocuments() {
  requirePoppler('pdftotext');
  directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  directory('INCOMINGDOCUMENT_DIR', 'INCOMINGDOCUMENT_DIR');
}

/** Reuse the Rx workflow's two owned programs, never change a shared provider default. */
async function checkNoteBrowserProgramContext(s, { context, charts, tokens, programs, provider, role }) {
  const { sql, patient, marker, config } = s;
  h.assert(charts.length === 2 && tokens.length === 2 && tokens[0] && tokens[0] !== tokens[1]
    && programs.length === 2 && programs.every(id => /^[1-9]\d*$/.test(id))
    && /^[1-9]\d*$/.test(provider) && /^[1-9]\d*$/.test(role), 'Browse Notes needs two distinct owned program windows');
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
    AND last_name=${q(marker)}`) === '1', 'Browse Notes patient ownership changed');
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const incoming = directory('INCOMINGDOCUMENT_DIR', 'INCOMINGDOCUMENT_DIR');
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-note-context-'));
  s.cleanup(() => fs.rmSync(scratch, { recursive: true, force: true }));
  const docMarker = `${marker}-NB`;
  const docs = ownedPdfDocuments(store, docMarker, [{ key: 'delete' }, { key: 'refile' }]);
  const [deletedDoc, refiledDoc] = docs;
  const refiled = path.join(incoming, '1', 'Refile', `R${refiledDoc.filename.substring(14)}`);
  const plannedFiles = [...docs.map(doc => doc.file), refiled];
  const ownedFiles = new Map();
  const owned = { sql, marker: docMarker, patient, docs, files: [] };
  const claimFile = (file, identity) => {
    h.assert(identity.isFile(), 'A newly created Browse Notes file is not regular');
    ownedFiles.set(file, { dev: identity.dev, ino: identity.ino });
    owned.files.push(file);
  };
  const text = `${marker}-origin-program-browser-note H&P + café 中文`;
  let noteId;
  let browser;
  const snapshot = () => ({ ...readPatientState(sql, patient),
    locks: sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`),
    drugs: sql.rows(`SELECT * FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`),
    prescriptions: sql.rows(`SELECT * FROM prescription WHERE demographic_no=${patient} ORDER BY script_no`),
    defaults: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(provider)} ORDER BY program_id`),
    memberships: sql.rows(`SELECT * FROM program_provider WHERE provider_no=${q(provider)} ORDER BY id`),
    admissions: sql.rows(`SELECT * FROM admission WHERE client_id=${patient} ORDER BY am_id`),
  });
  // Check every collision before registering file/document cleanup. A failed wx
  // later is also safe: only a successfully opened new inode can be claimed.
  h.assert(plannedFiles.every(file => !fs.lstatSync(file, { throwIfNoEntry: false })),
    'A Browse Notes fixture file already exists');
  h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc LIKE ${q(`${docMarker}%`)}
    OR docfilename IN (${docs.map(doc => q(doc.filename)).join(',')})`) === '0',
  'A Browse Notes document marker or stored name already exists');
  h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic'
    AND module_id=${patient}`) === '0', 'The Rx fixture already has a document relationship');
  const cleanup = () => {
    for (const file of plannedFiles) {
      const actual = fs.lstatSync(file, { throwIfNoEntry: false });
      if (!actual) continue;
      const expected = ownedFiles.get(file);
      h.assert(expected && actual.isFile() && actual.dev === expected.dev && actual.ino === expected.ino,
        'A collided or replaced Browse Notes file was retained; refusing to unlink it');
    }
    const capturedIds = docs.map(doc => doc.id).filter(Boolean);
    const discoveredIds = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${q(`${docMarker}%`)}`)
      .map(row => row[0]);
    const cleanupIds = [...new Set([...capturedIds, ...discoveredIds])];
    h.assert(cleanupIds.every(id => /^[1-9]\d*$/.test(id)), 'Document cleanup identities changed');
    if (cleanupIds.length) {
      const links = sql.rows(`SELECT module,module_id,document_no FROM ctl_document
        WHERE document_no IN (${cleanupIds.join(',')})`);
      h.assert(links.every(row => row[0] === 'demographic' && row[1] === patient),
        'A Browse Notes document acquired another patient relationship; retaining its rows and files');
    }
    removeOwnedPdfDocuments(owned);
    assertOwnedPdfDocumentsRemoved(owned);
    if (!noteId) {
      const recovered = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}
        AND provider_no=${q(provider)} AND BINARY note=${q(text)} AND program_no=${q(programs[0])}`);
      h.assert(recovered.length <= 1, 'Temporary Browse Notes note ownership is ambiguous');
      noteId = recovered[0]?.[0];
    }
    if (noteId) {
      const rows = sql.rows(`SELECT demographic_no,provider_no,note FROM casemgmt_note WHERE note_id=${noteId}`);
      h.assert(rows.length === 0 || (rows.length === 1 && rows[0][0] === patient
        && rows[0][1] === provider && rows[0][2] === text), 'Temporary Browse Notes note ownership changed');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id=${noteId};
        DELETE FROM casemgmt_note_ext WHERE note_id=${noteId};
        DELETE FROM casemgmt_note_link WHERE note_id=${noteId};
        DELETE FROM hash_audit WHERE type='enc' AND id=${noteId};
        DELETE FROM casemgmt_note WHERE note_id=${noteId} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${noteId}`) === '0',
        'The temporary Browse Notes note survived cleanup');
    }
    fs.rmSync(scratch, { recursive: true, force: true });
  };
  s.cleanup(cleanup);
  const initial = snapshot();
  const storeOwner = fs.statSync(store);
  for (const doc of docs) {
    const fd = fs.openSync(doc.file, 'wx', 0o640);
    try {
      claimFile(doc.file, fs.fstatSync(fd));
      fs.writeFileSync(fd, textPdf(doc.label, doc.pages));
      fs.fchownSync(fd, storeOwner.uid, storeOwner.gid);
    } finally { fs.closeSync(fd); }
    doc.id = h.insertId(sql, `INSERT INTO document
      (doctype,docdesc,docfilename,doccreator,responsible,source,updatedatetime,status,contenttype,
       contentdatetime,public1,observationdate,number_of_pages,restrictToProgram,abnormal)
      VALUES ('others',${q(doc.label)},${q(doc.filename)},${q(provider)},${q(provider)},'',NOW(),'A',
        'application/pdf',NOW(),0,'2026-01-02',${doc.pages},0,0)`, 'Browse Notes PDF');
    h.assert(/^[1-9]\d*$/.test(doc.id), 'The owned Browse Notes document key is invalid');
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status)
      VALUES ('demographic',${patient},${doc.id},'A')`);
  }
  noteId = h.insertId(sql, `INSERT INTO casemgmt_note
    (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
     include_issue_innote,encounter_type,program_no,reporter_caisi_role,reporter_program_team,
     history,uuid,locked,archived,position,appointmentNo)
    VALUES (NOW(),DATE_ADD(NOW(),INTERVAL 1 MINUTE),${patient},${q(provider)},${q(text)},1,${q(provider)},
      0,'',${q(programs[0])},${q(role)},'0',${q(text)},UUID(),'0',0,0,0)`, 'origin-program Browse Notes note');
  h.assert(/^[1-9]\d*$/.test(noteId), 'The temporary Browse Notes note key is invalid');
  const stable = snapshot();
  const documentColumns = sql.rows(`SELECT COLUMN_NAME FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document'
      AND LOWER(COLUMN_NAME) NOT IN ('status','updatedatetime') ORDER BY ORDINAL_POSITION`).flat();
  h.assert(documentColumns.includes('program_id') && documentColumns.includes('document_no')
    && documentColumns.every(column => /^[A-Za-z][A-Za-z0-9_]*$/.test(column)),
  'The document fixture has no complete stable-column contract');
  const stableDocColumns = documentColumns.map(column => '`' + column + '`').join(',');
  const docState = () => sql.rows(`SELECT ${stableDocColumns} FROM document
    WHERE document_no IN (${docs.map(doc => doc.id).join(',')}) ORDER BY document_no`);
  const stableDocs = JSON.stringify(docState());
  const documentLinks = () => sql.rows(`SELECT module,module_id,document_no,status FROM ctl_document
    WHERE document_no IN (${docs.map(doc => doc.id).join(',')}) ORDER BY document_no,module,module_id`);
  const stableLinks = JSON.stringify(documentLinks());
  h.assert(documentLinks().length === docs.length && documentLinks().every(row =>
    row[0] === 'demographic' && row[1] === patient && row[3] === 'A'),
  'The staged PDFs do not have exactly their owned patient relationships');
  const sourceHashes = docs.map(doc => sha(doc.file));
  const status = doc => sql.value(`SELECT status FROM document WHERE document_no=${doc.id}`);
  const assertContext = async label => {
    const address = new URL(browser.url());
    h.assert(address.pathname.endsWith('/casemgmt/ViewNoteBrowser')
      && address.searchParams.get('chartContext') === tokens[0]
      && await browser.locator('form[name="DisplayDoc"] input[name="chartContext"]').inputValue() === tokens[0],
    `${label}: the note browser lost its originating program context`);
    h.assert(address.searchParams.get('demographic_no') === patient,
      `${label}: the note browser changed its patient`);
    await browser.locator('#encounterlist').selectOption(noteId);
    const preview = browser.frameLocator('#docdisp iframe');
    await preview.locator('body', { hasText: text }).waitFor();
    const source = new URL(await browser.locator('#docdisp iframe').getAttribute('src'), browser.url());
    h.assert(source.searchParams.get('chartContext') === tokens[0]
      && source.searchParams.get('demographicNo') === patient
      && source.searchParams.get('notes2print') === noteId,
    `${label}: the actual note preview lost its patient, note or program`);
    await browser.waitForLoadState('networkidle');
    assertPatientStateUnchanged(stable, snapshot(), label);
    h.assert(JSON.stringify(docState()) === stableDocs, `${label}: document metadata changed beyond status/timestamp`);
    h.assert(JSON.stringify(documentLinks()) === stableLinks, `${label}: a document patient relationship changed`);
    h.assert(docs.every((doc, index) => fs.existsSync(doc.file) && sha(doc.file) === sourceHashes[index]),
      `${label}: a stored PDF was removed or changed`);
  };
  const selectDocument = async doc => {
    await browser.locator('#doclist').selectOption(`${doc.id}-application/pdf`);
    await browser.waitForLoadState('networkidle');
  };
  const mutate = async (action, button) => {
    const postWait = browser.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith(`/casemgmt/${action}`));
    const loaded = browser.waitForEvent('load');
    postWait.catch(() => {});
    loaded.catch(() => {});
    await button.click();
    const post = await postWait;
    await loaded;
    const body = new URLSearchParams(post.request().postData() || '');
    // Claim the refile only after the exact owned native operation acknowledged
    // it and the produced bytes match. A later token assertion can then fail
    // without stranding this run's copy or deleting an unproven collided file.
    if (action === 'NoteBrowserDocumentRefile' && post.status() === 302
        && body.get('refileDocumentNo') === refiledDoc.id && body.get('queueId') === '1'
        && !new URL(post.headers().location, post.url()).searchParams.get('errorMessage')) {
      h.assert(fs.existsSync(refiled) && sha(refiled) === sourceHashes[1],
        'The acknowledged refile did not produce the exact owned PDF');
      claimFile(refiled, fs.lstatSync(refiled));
    }
    const contexts = body.getAll('chartContext');
    h.assert(post.status() === 302 && contexts.length === 1 && contexts[0] === tokens[0],
      `${action}: the native document form lost its signed originating context`);
    const target = new URL(post.headers().location, post.url());
    h.assert(target.searchParams.get('chartContext') === tokens[0],
      `${action}: the native redirect lost its signed originating context`);
    h.assert(!new URL(browser.url()).searchParams.get('errorMessage'), `${action}: document mutation reported an error`);
    await assertContext(action);
  };
  await s.step('Browse Notes keeps the originating program after the same patient enters its other program', async () => {
    browser = await ui.clickOpensPopup(charts[0], charts[0].locator('#note-control-panel button', { hasText: 'Browse Notes' }),
      { context, recorder: s.recorder, label: 'note-browser-program-context', timeout: 30000 });
    await assertContext('Initial Browse Notes');
  });
  await s.step('native document Delete and Undelete retain the original program in their form, redirect and note preview', async () => {
    await selectDocument(deletedDoc);
    await mutate('NoteBrowserDocumentDelete', browser.locator('#docbuttons input[value="Delete"]'));
    h.assert(status(deletedDoc) === 'D' && status(refiledDoc) === 'A', 'Delete changed another owned document');
    await Promise.all([browser.waitForEvent('load'), browser.locator('#selviewstatus').selectOption('deleted')]);
    await assertContext('Deleted list refresh');
    await selectDocument(deletedDoc);
    await mutate('NoteBrowserDocumentUndelete', browser.locator('#docbuttons input[value="Undelete"]'));
    h.assert(status(deletedDoc) === 'A' && status(refiledDoc) === 'A', 'Undelete changed another owned document');
  });
  await s.step('native document Refile retains the original program through note preview and an actual PDF download', async () => {
    await Promise.all([browser.waitForEvent('load'), browser.locator('#selviewstatus').selectOption('active')]);
    await assertContext('Published list refresh');
    await selectDocument(refiledDoc);
    await browser.locator('#queueList').selectOption('1');
    await mutate('NoteBrowserDocumentRefile', browser.locator('#refilebutton input[value="Refile"]'));
    h.assert(fs.existsSync(refiled) && sha(refiled) === sha(refiledDoc.file)
      && status(deletedDoc) === 'A' && status(refiledDoc) === 'A', 'Refile did not preserve its exact PDF/source rows');
    const pagesBefore = new Set(context.pages());
    const downloaded = watchChartPrintDownload(context, h.appUrl(config.baseUrl, '/CaseManagementEntry'));
    const responseWait = context.waitForEvent('response', { predicate: response => {
      const target = new URL(response.url());
      return response.request().isNavigationRequest() && target.pathname.endsWith('/CaseManagementEntry')
        && target.searchParams.get('method') === 'print';
    }, timeout: 30000 });
    responseWait.catch(() => {});
    try {
      await browser.locator('#imgPrintEncounter').click();
      const response = await responseWait;
      const target = new URL(response.url());
      h.assert(response.status() === 200 && target.searchParams.get('chartContext') === tokens[0]
        && target.searchParams.get('demographicNo') === patient && target.searchParams.get('notes2print') === noteId,
      'The native note print changed its patient, selected note or originating context');
      const download = await downloaded.promise;
      h.assert(download && download.url() === response.url() && await download.failure() === null,
        'The originating-program note PDF did not download successfully');
      const file = path.join(scratch, 'origin-program.pdf');
      await download.saveAs(file);
      const bytes = fs.readFileSync(file);
      h.assert(bytes.length > 1000 && bytes.subarray(0, 5).toString('latin1') === '%PDF-'
        && /%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1'))
        && pdfText(file).replace(/\s+/g, ' ').includes(text.replace(/\s+/g, ' ')),
      'The actual downloaded PDF did not retain the originating-program Unicode note');
    } finally { downloaded.cancel(); }
    for (const page of context.pages()) if (!pagesBefore.has(page) && !page.isClosed()) await page.close();
    await assertContext('Post-print Browse Notes');
  });
  await browser.close();
  cleanup();
  assertPatientStateUnchanged(initial, snapshot(), 'Temporary Browse Notes fixture cleanup');
}

module.exports = { checkNoteBrowserProgramContext, requireNoteBrowserDocuments };
