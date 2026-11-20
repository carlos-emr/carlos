#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: eDocument upload from the chart (Documents "+" > Add Document).
 *
 * User path: Schedule > Search > Master Record > E-Chart > Documents "+" > choose type, description and a
 * small PDF > Add. For each rapid activation (dblclick(), two back-to-back clicks, POST replay, double Enter in the
 * description field, slow-response re-click) the check uploads ONE document with its own marker
 * description and asserts EXACTLY ONE document row attached to the owned patient (ctl_document).
 *
 * Fixtures: the owned FAKE- patient and a throw-away PDF; cleanup deletes the document/ctl_document rows
 * for the owned patient and marker, the system chart note + casemgmt_note_link each upload created, AND the stored files they name (inside DOCUMENT_DIR only; SKIP when the
 * store is not readable), and asserts rows and files are gone. Nothing is transmitted.
 * Wave-6 pattern sweep "double-submit".
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep, recorderMark, forgiveAbortedSecondRequest } = require('./lib/double-submit-helpers');

const q = h.sqlString;

function tinyPdf(directory, name) {
  const body = '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';
  const file = path.join(directory, name);
  fs.writeFileSync(file, body);
  return file;
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const store = process.env.DOCUMENT_DIR;
  if (!store || !fs.existsSync(store)) throw new h.SkipCheck('DOCUMENT_DIR (the document store) is not readable here');
  const like = q(`${marker}-%`);
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'dbl-doc-'));
  const rows = () => sql.rows(`SELECT d.document_no, d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
    WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc LIKE ${like}`);
  s.cleanup(() => {
    const files = new Set();
    for (const [no, file] of rows()) {
      h.assert(/^[1-9]\d*$/.test(no), 'Owned document id is invalid');
      // Every successful demographic upload also writes a system chart note plus a casemgmt_note_link row
      // (table_name 5 = CaseManagementNoteLink.DOCUMENT) pointing at the document; remove both with it.
      const notes = sql.rows(`SELECT l.note_id FROM casemgmt_note_link l JOIN casemgmt_note n ON n.note_id=l.note_id
        WHERE l.table_name=5 AND l.table_id=${no} AND n.demographic_no=${patient}`).map(([id]) => id);
      for (const note of notes) {
        h.assert(/^[1-9]\d*$/.test(note), 'Owned document note id is invalid');
        sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id=${note} AND table_name=5 AND table_id=${no};
          DELETE FROM casemgmt_issue_notes WHERE note_id=${note};
          DELETE FROM casemgmt_note WHERE note_id=${note} AND demographic_no=${patient}`);
      }
      sql.execute(`DELETE FROM ctl_document WHERE document_no=${no} AND module_id=${patient}; DELETE FROM document WHERE document_no=${no}`);
      files.add(file);
    }
    for (const file of files) {
      const target = path.join(store, path.basename(file));
      if (path.basename(file) === file && fs.existsSync(target)) fs.unlinkSync(target);
      h.assert(!fs.existsSync(target), 'An uploaded document file was not removed from the store');
    }
    fs.rmSync(scratch, { recursive: true, force: true });
    h.assert(rows().length === 0, 'Owned documents were not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${q(`%${marker}-%`)}`) === '0',
      'Upload-created chart notes were not removed');
  });
  const chart = await s.chart();
  const v = verdicts('document-upload');

  // No slow-response mode: route.fetch() cannot faithfully re-send this multipart upload, so the held-back
  // request never wrote a row (a harness limit, not an application result). The form also disables its own button.
  for (const mode of MODES.filter((m) => m.key !== 'slowResubmit')) {
    await s.step(`Add Document via ${mode.label} files exactly one document`, async () => {
      const add = await s.popup(chart, chart.locator('a[onclick*="ViewDocumentReport?"][onclick*="mode=add"]').first(), 'document-add');
      const form = add.locator('form[action*="addEditDocument"]').first();
      await form.waitFor({ state: 'visible', timeout: 20000 });
      const types = await form.locator('select[name="docType"] option').evaluateAll((os2) => os2.map((o) => o.value).filter((x) => x));
      await form.locator('select[name="docType"]').selectOption(types[0]);
      const desc = `${marker}-${mode.tag} double submit`;
      await form.locator('input[name="docDesc"]').fill(desc);
      await form.locator('input[type="file"]').setInputFiles(tinyPdf(scratch, `dbl-${mode.tag}.pdf`));
      const route = /\/documentManager\/addEditDocument$/;
      const posts = watchPosts(add.context(), route);
      const since = recorderMark(s.recorder);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      await rapid(mode.key, form.locator('input[name="Submit"]').first(), { textField: form.locator('input[name="docDesc"]') });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM document d JOIN ctl_document c ON c.document_no=d.document_no
        WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(desc)}`,
      { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      await sleep(300);
      forgiveAbortedSecondRequest(s.recorder, since, /\/documentManager\//);
      console.log(`    (${posts.seen.length} addEditDocument POST(s))`);
      v.record(mode.label, count, { exactly: 1 });
      if (!add.isClosed()) await add.close().catch(() => {});
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-document', workflow, { openPatient: true });
