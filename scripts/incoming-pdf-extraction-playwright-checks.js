#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

function fixturePdf(marker) {
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [4 0 R 6 0 R 8 0 R] /Count 3 >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  for (let page = 1; page <= 3; page++) {
    const content = `BT /F1 12 Tf 40 700 Td (${marker} page ${page}) Tj ET\n`;
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents ${objects.length + 2} 0 R >>`);
    objects.push(`<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`);
  }
  let pdf = '%PDF-1.4\n'; const offsets = [0];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  pdf += offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  return Buffer.from(`${pdf}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}
function inspect(file) {
  const info = execFileSync('pdfinfo', [file], { encoding: 'utf8' });
  const pages = Number(info.match(/^Pages:\s+(\d+)/m)?.[1]);
  h.assert(pages > 0, 'The generated PDF has no readable pages');
  return { pages, text: execFileSync('pdftotext', [file, '-'], { encoding: 'utf8' }) };
}

async function workflow(s) {
  h.assert(process.env.INCOMINGDOCUMENT_DIR, 'Set INCOMINGDOCUMENT_DIR to the disposable deployment incoming-document directory');
  const root = fs.realpathSync(process.env.INCOMINGDOCUMENT_DIR);
  const directory = path.join(root, '1', 'File');
  h.assert(fs.existsSync(directory), 'Create the test queue 1/File directory with application ownership before this check');
  h.assert(fs.realpathSync(directory).startsWith(root + path.sep), 'Test queue resolves outside the incoming-document directory');
  const name = `${s.marker}.pdf`;
  const source = path.join(directory, name);
  const destination = path.join(directory, `${s.marker}E3.pdf`);
  const unrelated = path.join(directory, `T${name}`);
  const owned = [];
  const provider = h.sqlString(s.provider);
  const keys = ['incoming_document_default_queue', 'incoming_document_entry_mode', 'view_document_as'];
  const where = `provider_no=${provider} AND name IN(${keys.map(h.sqlString).join(',')})`;
  const preferences = s.sql.rows(`SELECT id,name,value,IF(value IS NULL,1,0) FROM property WHERE ${where} ORDER BY id`);
  s.cleanup(() => {
    for (const file of owned) if (fs.existsSync(file)) fs.unlinkSync(file);
    const ids = preferences.map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid preference fixture identity');
    s.sql.execute(`DELETE FROM property WHERE ${where}${ids.length ? ` AND id NOT IN(${ids.join(',')})` : ''}`);
    for (const [id, key, value, isNull] of preferences) s.sql.execute(`UPDATE property SET value=${isNull === '1' ? 'NULL' : h.sqlString(value)} WHERE id=${id} AND provider_no=${provider} AND name=${h.sqlString(key)}`);
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,name,value,IF(value IS NULL,1,0) FROM property WHERE ${where} ORDER BY id`)) === JSON.stringify(preferences), 'Incoming-document preference cleanup failed');
  });
  const original = fixturePdf(s.marker);
  for (const file of [source, destination, unrelated]) {
    fs.writeFileSync(file, original, { flag: 'wx', mode: 0o640 }); owned.push(file);
    // Match the queue service owner; a root-owned collision fixture would
    // fail on permissions before exercising the overwrite regression.
    const queueOwner = fs.statSync(directory);
    if (fs.statSync(file).uid !== queueOwner.uid || fs.statSync(file).gid !== queueOwner.gid) {
      fs.chownSync(file, queueOwner.uid, queueOwner.gid);
    }
  }
  h.assert(inspect(source).pages === 3, 'The synthetic input PDF is not three pages');
  const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
    { context: s.context, recorder: s.recorder, label: 'incoming-pdf-inbox' });
  const page = await s.popup(inbox, inbox.locator('a[href*="ViewIncomingDocs"]').first(), 'incoming-pdf-editor');
  async function reloadAfter(action) {
    await Promise.all([page.waitForEvent('domcontentloaded'), action()]);
    await page.waitForLoadState('networkidle');
  }
  if (await page.locator('#queueList').inputValue() !== '1') {
    await reloadAfter(() => page.locator('#queueList').selectOption('1'));
  }
  await reloadAfter(() => page.locator('button[onclick="loadPdf(\'1\',\'File\');"]').first().click());
  await reloadAfter(() => page.locator('#SelectPdfList').selectOption(name));
  h.assert((await page.locator('fieldset legend').allTextContents()).some(text => text.includes(name)), 'Incoming queue did not open the owned PDF');
  const extract = () => page.locator('button[onclick^="extractPagePdf("]');
  await s.step('reject GET mutations and POST without CSRF without changing the source', async () => {
    const form = await page.locator('form[name="PdfInfoForm"]').evaluate(form => ({
      action: form.action,
      // Only document routing fields: never send a CSRF token in a URL.
      fields: Object.fromEntries([...new FormData(form)].filter(([name]) =>
        ['pdfNo', 'pdfDir', 'pdfName', 'pdfAction', 'pdfPageNumber', 'pdfExtractPageNumber', 'defaultQueue', 'queueList', 'entryMode', 'imageType'].includes(name))),
    }));
    form.fields.pdfPageNumber = '1';
    form.fields.pdfExtractPageNumber = '2';
    for (const action of ['Rotate90', 'Rotate180', 'RotateM90', 'RotateAll90', 'RotateAll180', 'RotateAllM90', 'DeletePage', 'DeletePDF', 'ExtractPagePDF']) {
      const response = await s.context.request.get(form.action, { params: { ...form.fields, pdfAction: action } });
      h.assert(response.status() === 405, `GET ${action} returned ${response.status()} instead of 405`);
      h.assert(response.headers().allow === 'POST', 'Method rejection omitted the allowed method');
      h.assert(fs.readFileSync(source).equals(original), 'A rejected GET changed the synthetic PDF');
    }
    const response = await s.context.request.post(form.action, { form: { ...form.fields, pdfAction: 'Rotate90' } });
    h.assert(response.status() === 403, `POST without CSRF returned ${response.status()} instead of 403`);
    h.assert(fs.readFileSync(source).equals(original), 'A CSRF-rejected POST changed the synthetic PDF');
  });
  await s.step('refuse extraction of the whole PDF without changing files', async () => {
    const dialogs = await h.withExpectedDialogs(page, () => extract().click(), { promptText: '1-3' });
    h.assert(dialogs.length === 2 && dialogs[0].type === 'prompt' && dialogs[1].type === 'alert', 'Whole-document extraction was not visibly refused');
    h.assert(fs.readFileSync(source).equals(original), 'Invalid selection changed the original PDF');
  });
  await s.step('an existing extracted document is preserved and failure is visible', async () => {
    const dialogs = await h.withExpectedDialogs(page, () => reloadAfter(() => extract().click()), { promptText: '2' });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Extraction did not use its range prompt');
    h.assert(fs.readFileSync(source).equals(original), 'Failed extraction changed the original PDF');
    h.assert(fs.readFileSync(destination).equals(original), 'Extraction overwrote an existing extracted PDF');
    h.assert(fs.readFileSync(unrelated).equals(original), 'Extraction overwrote an unrelated queue file');
    h.assert((await page.locator('[role="alert"]').innerText()).trim(), 'Extraction failure was silent');
  });
  await s.step('cancel extraction without a request, dialog error, or changed file', async () => {
    const requests = [];
    const capture = request => {
      if (request.method() === 'POST' && new URLSearchParams(request.postData() || '').get('pdfAction')) requests.push(request);
    };
    page.on('request', capture);
    try {
      const dialogs = await h.withExpectedDialogs(page, () => extract().click(), { accept: false });
      await page.waitForLoadState('networkidle');
      h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Cancel produced an unexpected dialog');
      h.assert(requests.length === 0, 'Cancel submitted a document mutation');
      h.assert(fs.readFileSync(source).equals(original), 'Cancel changed the source PDF');
    } finally { page.off('request', capture); }
  });
  await s.step('reject a page outside the PDF before submitting', async () => {
    const dialogs = await h.withExpectedDialogs(page, () => extract().click(), { promptText: '4' });
    h.assert(dialogs.length === 2 && dialogs[1].type === 'alert', 'Out-of-bounds extraction was not visibly refused');
    h.assert(fs.readFileSync(source).equals(original), 'Invalid page changed the source PDF');
  });
  fs.unlinkSync(destination);
  await s.step('extract one page and retain the exact remaining pages', async () => {
    await reloadAfter(() => page.locator('#SelectPdfList').selectOption(name));
    await h.withExpectedDialogs(page, async () => {
      await extract().click();
      await page.waitForFunction(value => [...document.querySelectorAll('#SelectPdfList option')].some(option => option.value === value), path.basename(destination));
      await page.waitForLoadState('networkidle');
    }, { promptText: '2' });
    const left = inspect(source); const right = inspect(destination);
    h.assert(left.pages === 2 && right.pages === 1, 'Extraction produced incorrect page counts');
    h.assert(left.text.includes(`${s.marker} page 1`) && left.text.includes(`${s.marker} page 3`) && !left.text.includes(`${s.marker} page 2`), 'Remaining PDF contains the wrong pages');
    h.assert(right.text.includes(`${s.marker} page 2`) && !right.text.includes(`${s.marker} page 1`) && !right.text.includes(`${s.marker} page 3`), 'Extracted PDF contains the wrong page');
    h.assert(fs.readFileSync(unrelated).equals(original), 'An unrelated queue PDF changed');
    await reloadAfter(() => page.locator('#SelectPdfList').selectOption(name));
    h.assert(await page.locator('#SelectPageList option').count() === 3, 'Remaining document did not reopen with two pages');
    await reloadAfter(() => page.locator('#SelectPdfList').selectOption(path.basename(destination)));
    h.assert(await page.locator('#SelectPageList option').count() === 2, 'Extracted document did not reopen with one page');
    h.assert((await page.locator('fieldset legend').allTextContents()).some(text => text.includes(path.basename(destination))), 'Extracted PDF cannot be reopened from the queue');
  });
  await s.step('rotate a page and all pages without losing content', async () => {
    await reloadAfter(() => page.locator('#SelectPdfList').selectOption(name));
    await reloadAfter(() => page.locator('#SelectPageList').selectOption('1'));
    const rotation = number => {
      const info = execFileSync('pdfinfo', ['-f', String(number), '-l', String(number), source], { encoding: 'utf8' });
      return Number(info.match(/(?:Page\s+\d+\s+rot|Page rot):\s+(\d+)/)?.[1]);
    };
    await reloadAfter(() => page.locator('button[onclick^="rotatePdf("]').filter({ hasText: '+90' }).click());
    h.assert(rotation(1) === 90 && rotation(2) === 0, 'Single-page rotation changed the wrong pages');
    await reloadAfter(() => page.locator('button[onclick^="rotateAllPagePdf("]').filter({ hasText: '180' }).click());
    h.assert(rotation(1) === 270 && rotation(2) === 180, 'Whole-document rotation did not preserve relative page rotations');
    const result = inspect(source);
    h.assert(result.pages === 2 && result.text.includes(`${s.marker} page 1`) && result.text.includes(`${s.marker} page 3`),
      'Rotation discarded page content');
    h.assert(fs.readFileSync(unrelated).equals(original), 'Rotation changed an unrelated document');
  });
  await s.step('delete a selected page and preserve the other page and extracted document', async () => {
    await reloadAfter(() => page.locator('#SelectPageList').selectOption('1'));
    const extracted = fs.readFileSync(destination);
    const dialogs = await h.withExpectedDialogs(page,
      () => reloadAfter(() => page.locator('button[onclick^="deletePagePdf("]').click()));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Page deletion omitted its confirmation');
    const result = inspect(source);
    h.assert(result.pages === 1 && result.text.includes(`${s.marker} page 3`) && !result.text.includes(`${s.marker} page 1`),
      'Page deletion retained the wrong page');
    h.assert(fs.readFileSync(destination).equals(extracted), 'Page deletion changed the extracted document');
    h.assert(fs.readFileSync(unrelated).equals(original), 'Page deletion changed an unrelated document');
    await reloadAfter(() => page.locator('#SelectPdfList').selectOption(name));
    h.assert(await page.locator('#SelectPageList option').count() === 2, 'Single remaining page did not reopen');
  });
}
if (require.main === module) runWorkflow('incoming-pdf-extraction', workflow, { openPatient: false });
module.exports = { fixturePdf, inspect, workflow };
