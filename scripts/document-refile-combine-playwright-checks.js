#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Stored-document editing, combining, browsing and refiling, driven from the chart.
 * User path: Schedule > Search > Master Record > Documents (eDoc report popup) >
 *   Edit (ViewEditDocument popup) > Update; tick two PDFs > Combine PDF (combinePDFs);
 *   Browse (ViewDocumentBrowser) > select one / two documents > Refile to the default queue
 *   (DocumentRefile), with the browser's two-document combined preview.
 * Asserts: the edit popup posts, closes and reloads the report, and the document row carries
 * the new description, type and observation date; the combined download is a %PDF whose
 * page count (pdfinfo) is the sum of both owned PDFs and whose text carries both; the
 * browser renders without script errors, previews the combined PDF, refiles the selected
 * document as an exact byte copy under INCOMINGDOCUMENT_DIR/1/Refile, and DocumentRefile
 * refuses GET without copying anything.
 * Fixtures: owned patient, two owned PDFs (rows + files under DOCUMENT_DIR, names carry the
 * marker), the refiled copy; all removed and verified gone. Turns on the test provider's
 * edoc_browser_in_document_report preference (the only opener of the browser) and restores
 * it exactly, so run with EXCLUSIVE=1.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md eDocument section
 * (document-refile-combine). Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR), INCOMINGDOCUMENT_DIR,
 * plus the harness contract; needs pdfinfo/pdftotext.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const PREFERENCE = 'edoc_browser_in_document_report';

/** A classic-xref PDF with one text line per page, so pdftotext can prove which file is which. */
function textPdf(label, pages) {
  const kids = Array.from({ length: pages }, (_, index) => `${4 + index * 2} 0 R`).join(' ');
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', `<< /Type /Pages /Kids [${kids}] /Count ${pages} >>`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  for (let page = 1; page <= pages; page++) {
    const content = `BT /F1 12 Tf 40 700 Td (${label} page ${page}) Tj ET\n`;
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents ${objects.length + 2} 0 R >>`);
    objects.push(`<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`);
  }
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  pdf += offsets.map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  return Buffer.from(`${pdf}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

function pdfFacts(file) {
  h.assert(fs.readFileSync(file).subarray(0, 5).toString('latin1') === '%PDF-', 'The response is not a PDF');
  const info = execFileSync('pdfinfo', [file], { encoding: 'utf8' });
  return { pages: Number(info.match(/^Pages:\s+(\d+)/m)?.[1]), text: execFileSync('pdftotext', [file, '-'], { encoding: 'utf8' }) };
}

const sha = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');

function directory(name, ...candidates) {
  const configured = candidates.map(key => process.env[key]).find(Boolean);
  if (!configured) throw new h.SkipCheck(`Set ${name} to the installed directory`);
  const real = fs.realpathSync(configured);
  h.assert(fs.statSync(real).isDirectory(), `${name} is not a directory`);
  return real;
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const incoming = directory('INCOMINGDOCUMENT_DIR', 'INCOMINGDOCUMENT_DIR');
  const owner = fs.statSync(store);
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-refile-combine-'));
  // Stored names follow the uploader's 14-digit timestamp prefix; the refile copy drops it.
  const docs = [{ key: 'A', pages: 2 }, { key: 'B', pages: 3 }].map(doc => ({
    ...doc, label: `${marker} ${doc.key}`, filename: `20260101000000${marker}-${doc.key}.pdf`,
  }));
  docs.forEach(doc => { doc.file = path.join(store, doc.filename); });
  const refiled = path.join(incoming, '1', 'Refile', `R${marker}-A.pdf`);
  const preferenceWhere = `provider_no=${h.sqlString(provider)} AND name=${h.sqlString(PREFERENCE)}`;
  const preference = sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE ${preferenceWhere} ORDER BY id`);
  h.assert(preference.length <= 1, 'The test provider has duplicate document-browser preference rows');

  s.cleanup(() => {
    const ids = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned document identity');
    if (ids.length) {
      sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${ids.join(',')}) AND module='demographic' AND module_id=${patient};
        DELETE FROM document WHERE document_no IN (${ids.join(',')}) AND docdesc LIKE ${h.sqlString(`${marker}%`)}`);
    }
    for (const file of [...docs.map(doc => doc.file), refiled]) if (fs.existsSync(file)) fs.unlinkSync(file);
    fs.rmSync(scratch, { recursive: true, force: true });
    if (preference.length) {
      const [id, value, isNull] = preference[0];
      sql.execute(`UPDATE property SET value=${isNull === '1' ? 'NULL' : h.sqlString(value)} WHERE id=${id} AND ${preferenceWhere}`);
    } else sql.execute(`DELETE FROM property WHERE ${preferenceWhere}`);
    h.assert(JSON.stringify(sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE ${preferenceWhere} ORDER BY id`))
      === JSON.stringify(preference), 'The document-browser preference was not restored');
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc LIKE ${h.sqlString(`${marker}%`)}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM ctl_document WHERE module='demographic' AND module_id=${patient}`) === '0',
    'Owned document rows were not removed');
    h.assert(![...docs.map(doc => doc.file), refiled].some(file => fs.existsSync(file)), 'Owned document files were not removed');
  });

  for (const doc of docs) {
    fs.writeFileSync(doc.file, textPdf(doc.label, doc.pages), { flag: 'wx', mode: 0o640 });
    fs.chownSync(doc.file, owner.uid, owner.gid);
    doc.id = sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,source,updatedatetime,
        status,contenttype,contentdatetime,public1,observationdate,number_of_pages,restrictToProgram,abnormal)
      VALUES ('others',${h.sqlString(doc.label)},${h.sqlString(doc.filename)},${h.sqlString(provider)},${h.sqlString(provider)},
        '',NOW(),'A','application/pdf',NOW(),0,'2026-01-02',${doc.pages},0,0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(doc.id), 'The owned document fixture was not created');
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${doc.id},'A')`);
  }
  if (preference.length) sql.execute(`UPDATE property SET value='yes' WHERE id=${preference[0][0]} AND ${preferenceWhere}`);
  else sql.execute(`INSERT INTO property (provider_no,name,value) VALUES (${h.sqlString(provider)},${h.sqlString(PREFERENCE)},'yes')`);
  const [docA, docB] = docs;

  let report;
  await s.step('Master Record > Documents lists both owned PDFs', async () => {
    report = await s.popup(s.master, s.master.locator('a[onclick*="/documentManager/ViewDocumentReport"]').first(), 'edoc-report');
    for (const doc of docs) {
      await report.locator(`#docNo${doc.id}`).waitFor({ state: 'attached' });
      h.assert(await report.locator(`a[title="${doc.label}"]`).count() === 1, 'An owned document is missing from the report');
    }
  });

  const edited = { desc: `${docA.label} edited`, type: 'consult', date: '2026-02-03' };
  await s.step('Edit popup (ViewEditDocument) saves description, type and date, then reloads the report', async () => {
    const row = report.locator('tr', { has: report.locator(`#docNo${docA.id}`) });
    const editor = await s.popup(report, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-edit');
    h.assert(await editor.locator('input[name="mode"]').inputValue() === docA.id, 'The edit popup opened another document');
    await editor.locator('select[name="docType"]').selectOption(edited.type);
    await editor.locator('input[name="docDesc"]').fill(edited.desc);
    await editor.locator('#observationDate').fill(edited.date);
    const [post] = await Promise.all([
      editor.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/addEditDocument')
        && response.request().method() === 'POST'),
      report.waitForEvent('load'),
      editor.locator('input[name="Submit"]').click(),
    ]);
    h.assert(post.status() < 400, `The document edit POST answered HTTP ${post.status()}`);
    if (!editor.isClosed()) await editor.waitForEvent('close');
    await report.locator(`a[title="${edited.desc}"]`).waitFor({ state: 'attached' });
    await expectValue(sql, `SELECT CONCAT_WS('|',docdesc,doctype,observationdate,status) FROM document WHERE document_no=${docA.id}`,
      `${edited.desc}|${edited.type}|${edited.date}|A`, 'The edited description, type or observation date did not reach the document row');
    h.assert(sql.value(`SELECT CONCAT_WS('|',docfilename,contenttype) FROM document WHERE document_no=${docA.id}`)
      === `${docA.filename}|application/pdf`, 'Editing metadata changed the stored file reference');
  });

  await s.step('Combine PDF downloads one PDF holding both owned documents (pdfinfo page count)', async () => {
    for (const doc of docs) await report.locator(`#docNo${doc.id}`).check();
    const button = report.locator('input[type="button"][onclick*="submitForm"]');
    // Settled either way so a refused click cannot leave an unhandled rejection behind.
    const downloaded = report.waitForEvent('download').catch(error => error);
    const [request] = await Promise.all([
      report.waitForRequest(candidate => candidate.url().includes('combinePDFs') && candidate.method() === 'POST'),
      button.click(),
    ]);
    h.assert(new URL(request.url()).pathname === new URL(`${s.config.baseUrl}/documentManager/combinePDFs`).pathname,
      'Combine PDF posts somewhere other than the combinePDFs action');
    const download = await downloaded;
    if (download instanceof Error) throw download;
    h.assert(!(await download.failure()), 'The combined PDF download failed');
    const file = path.join(scratch, 'combined.pdf');
    await download.saveAs(file);
    const facts = pdfFacts(file);
    h.assert(facts.pages === docA.pages + docB.pages, `The combined PDF has ${facts.pages} pages, expected ${docA.pages + docB.pages}`);
    for (const doc of docs) for (let page = 1; page <= doc.pages; page++) {
      h.assert(facts.text.includes(`${doc.label} page ${page}`), 'The combined PDF is missing a page of an owned document');
    }
  });

  let browser;
  await s.step('Browse opens the document browser without script errors', async () => {
    const link = report.locator('a[href*="/documentManager/ViewDocumentBrowser"]').first();
    await Promise.all([report.waitForURL(url => url.pathname.endsWith('/documentManager/ViewDocumentBrowser')), link.click()]);
    browser = report;
    await browser.locator('#doclist').waitFor({ state: 'attached' });
    h.assertStrictPage(s.recorder);
    for (const doc of docs) h.assert(await browser.locator(`#doclist option[value^="${doc.id}-"]`).count() === 1,
      'An owned document is missing from the document browser');
  });

  await s.step('Selecting two PDFs previews them combined (combinePDFs inline)', async () => {
    const values = docs.map(doc => `${doc.id}-application/pdf`);
    const [preview] = await Promise.all([
      browser.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/combinePDFs')),
      browser.locator('#doclist').selectOption(values),
    ]);
    h.assert(preview.status() === 200 && /application\/pdf/.test(preview.headers()['content-type'] || ''),
      'The combined preview did not answer a PDF');
    h.assert(await browser.locator('#docdisp iframe[src*="combinePDFs"]').count() === 1, 'The combined preview frame was not shown');
  });

  await s.step('Refile copies the selected PDF byte-for-byte into the default queue', async () => {
    await browser.locator('#doclist').selectOption(`${docA.id}-application/pdf`);
    await browser.locator('#refilebutton').waitFor({ state: 'visible' });
    await browser.locator('#queueList').selectOption('1');
    const [post] = await Promise.all([
      browser.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/DocumentRefile')
        && response.request().method() === 'POST'),
      browser.waitForURL(url => url.pathname.endsWith('/documentManager/ViewDocumentBrowser')),
      browser.locator('#refilebutton input[type="button"]').click(),
    ]);
    h.assert(post.status() === 302, `The refile POST answered HTTP ${post.status()}`);
    await browser.locator('#doclist').waitFor({ state: 'attached' });
    h.assert(!new URL(browser.url()).searchParams.get('errorMessage'), 'The document browser reported a refile failure');
    h.assert(fs.existsSync(refiled) && sha(refiled) === sha(docA.file), 'The refiled copy is missing or differs from the stored PDF');
    h.assert(sql.value(`SELECT CONCAT_WS('|',status,docfilename) FROM document WHERE document_no=${docA.id}`)
      === `A|${docA.filename}`, 'Refiling changed the source document row');
  });

  await s.step('DocumentRefile refuses GET and copies nothing', async () => {
    fs.unlinkSync(refiled);
    const url = new URL(`${s.config.baseUrl}/documentManager/DocumentRefile`);
    url.search = new URLSearchParams({ refileDocumentNo: docA.id, queueId: '1' }).toString();
    const response = await s.context.request.get(url.href, { maxRedirects: 0, failOnStatusCode: false });
    h.assert(response.status() === 405, `GET DocumentRefile answered HTTP ${response.status()}, expected 405`);
    h.assert(!fs.existsSync(refiled), 'GET DocumentRefile copied the document');
  });
}

if (require.main === module) runWorkflow('document-refile-combine', workflow, { openPatient: true });
module.exports = { workflow };
