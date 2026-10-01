#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field length of a document description (wave 6, boundary values).
 * User path: Schedule > Search > Master Record > E-Chart > Documents "+" > choose type, description and a small PDF > Add.
 * Asserts: a description of exactly the 255-character column that carries an apostrophe, accents, CJK, an emoji,
 * "&amp;", quotes, a backslash, "%41", "+" and ";" is stored byte for byte; and, last, a description one character past the
 * column is refused or visibly limited rather than silently cut.
 * Fixtures: the owned FAKE- patient and a throw-away PDF; cleanup deletes the owned document / ctl_document rows and the
 * stored files they name (inside the document store only; SKIP when it is not readable) and asserts they are gone.
 * Implements the wave-6 "boundary values" pattern, Part 1 (document description).
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const b = require('./lib/boundary-values');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;

function documentStore() {
  if (process.env.BOUNDARY_DOCUMENT_DIR) return process.env.BOUNDARY_DOCUMENT_DIR;
  try {
    const line = fs.readFileSync('/etc/carlos-emr/carlos.properties', 'utf8').split('\n').find(l => /^\s*DOCUMENT_DIR\s*=/.test(l));
    if (line) return line.split('=').slice(1).join('=').trim();
  } catch (error) { /* not on the packaged host */ }
  return process.env.DOCUMENT_DIR;
}

function tinyPdf(directory, name) {
  const body = '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n';
  const file = path.join(directory, name);
  fs.writeFileSync(file, body);
  return file;
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const store = documentStore();
  if (!store || !fs.existsSync(store)) throw new h.SkipCheck('The document store is not readable here (set BOUNDARY_DOCUMENT_DIR)');
  const T = b.TOKENS;
  const column = b.columnLength(sql, 'document', 'docdesc');
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'bnd-doc-'));
  const rows = () => sql.rows(`SELECT d.document_no, d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
    WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc LIKE ${q(`${marker}%`)}`);
  s.cleanup(() => {
    const files = new Set();
    for (const [no, file] of rows()) {
      h.assert(/^[1-9]\d*$/.test(no), 'Owned document id is invalid');
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
  });
  const chart = await s.chart();

  async function upload(description, label) {
    const before = rows().length;
    const add = await s.popup(chart, chart.locator('a[onclick*="ViewDocumentReport?"][onclick*="mode=add"]').first(), `document-add-${label}`);
    const form = add.locator('form[action*="addEditDocument"]').first();
    await form.waitFor({ state: 'visible', timeout: 20000 });
    const types = await form.locator('select[name="docType"] option').evaluateAll(options => options.map(option => option.value).filter(value => value));
    await form.locator('select[name="docType"]').selectOption(types[0]);
    await form.locator('input[name="docDesc"]').fill(description);
    const shown = await form.locator('input[name="docDesc"]').inputValue();
    await form.locator('input[type="file"]').setInputFiles(tinyPdf(scratch, `bnd-${label}.pdf`));
    await form.locator('input[name="Submit"]').first().click();
    const deadline = Date.now() + 25000;
    while (Date.now() < deadline && rows().length <= before) await new Promise(resolve => setTimeout(resolve, 400));
    await new Promise(resolve => setTimeout(resolve, 1000));
    // A refused upload leaves the add page open and re-rendered with its message; a saved one closes it.
    const text = add.isClosed() ? '' : (await add.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
    if (!add.isClosed()) await add.close().catch(() => {});
    return { shown, text };
  }

  const exact = `${marker} ${'E'} ${[T.apostrophe, T.latin, T.cjk, T.emoji, T.entity, T.quotes, T.backslash, T.percent, T.plus, T.semicolon].join(' ')}`;
  const full = exact + 'x'.repeat(column - b.cpLength(exact));
  h.assert(b.cpLength(full) === column, 'Test bug: the description is not exactly the column length');
  await s.step('a description of exactly the column length with special characters is stored byte for byte', async () => {
    await upload(full, 'E');
    const found = sql.rows(`SELECT d.document_no FROM document d JOIN ctl_document c ON c.document_no=d.document_no WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc LIKE ${q(`${marker} E%`)}`);
    h.assert(found.length === 1, 'The upload with a full-length description did not store exactly one document');
    b.assertStored(sql, 'document', 'docdesc', `document_no=${found[0][0]}`, full, 'Document description at the column length');
  });

  await s.step('a description one character past the column is refused or visibly limited, never silently cut', async () => {
    const over = `${marker} O${'x'.repeat(column + 1 - b.cpLength(marker) - 2)}`;
    h.assert(b.cpLength(over) === column + 1, 'Test bug: the long description is not one past the column');
    const { shown, text } = await upload(over, 'O');
    const found = sql.rows(`SELECT d.document_no FROM document d JOIN ctl_document c ON c.document_no=d.document_no WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc LIKE ${q(`${marker} O%`)}`);
    if (found.length === 0) {
      // Nothing stored is a valid answer only when the page names the length as the problem; any other failed save
      // (a duplicate-name refusal, a write error) is not evidence that the length was checked.
      h.assert(/too long|exceed|maximum|characters|length|limit/i.test(text),
        `The ${b.cpLength(shown)}-character description was not saved but the page gave no length-related refusal (page said: "${text.slice(0, 160)}")`);
      return;
    }
    b.assertNotSilentlyTruncated(sql, 'document', 'docdesc', `document_no=${found[0][0]}`, shown, 'Document description past the column');
    h.assert(b.cpLength(shown) <= column, `The description box accepted ${b.cpLength(shown)} characters but document.docdesc holds ${column}`);
  });
}

if (require.main === module) runWorkflow('boundary-document-text', workflow, { openPatient: true });
module.exports = { workflow };
