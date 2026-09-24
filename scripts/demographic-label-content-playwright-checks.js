#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openPrintMenu, assertIsPdf } = require('./demographic-labels-playwright-checks');
const { contextPathOf } = require('./anonymous-access-refused-playwright-checks');

function assertPatientText(text, surname, address) {
  const compact = value => value.replace(/\s+/g, '');
  h.assert(compact(text).includes(compact(surname)), 'Generated label omitted the owned patient name');
  if (address) h.assert(compact(text).includes(compact(address)), 'Generated label omitted the owned address');
}
// With new_label_print=true each menu item opens a ViewPrint* HTML wrapper whose
// iframe#pdf holds the real document; the legacy setting opens the PDF itself.
// The iframe src is relative to the wrapper and must stay inside THIS application:
// same origin AND under the configured context path, not another app on the host.
function resolvePdfUrl(popupUrl, iframeSrc, baseUrl) {
  if (!iframeSrc) return popupUrl;
  const target = new URL(iframeSrc, popupUrl);
  const contextPath = contextPathOf(baseUrl);
  h.assert(target.origin === new URL(baseUrl).origin && target.pathname.startsWith(`${contextPath}/`),
    'Label wrapper embedded a PDF from outside the CARLOS application');
  return target.href;
}
async function pdfUrlFor(produced, baseUrl) {
  if (produced.kind !== 'popup' || !produced.page) return produced.url;
  const frame = produced.page.locator('iframe#pdf');
  const src = await frame.count() > 0 ? await frame.first().getAttribute('src') : null;
  return resolvePdfUrl(produced.url, src, baseUrl);
}
async function workflow(s) {
  try { execFileSync('pdftotext', ['-v'], { stdio: 'pipe', timeout: 5000 }); }
  catch { throw new h.SkipCheck('Label content validation requires Poppler pdftotext'); }
  const address = "42 O'Neil & Test Lane";
  s.sql.execute(`UPDATE demographic SET address=${h.sqlString(address)},city='Fixture City',postal='K1A0B1',phone='6135550101' WHERE demographic_no=${s.patient}`);
  await s.master.reload({ waitUntil: 'domcontentloaded' });
  for (const label of ['PDF Envelope', 'PDF Label', 'PDF Address Label', 'PDF Chart Label', 'Client Lab Label']) {
    await s.step(`${label} contains the selected patient's data`, async () => {
      const menu = await openPrintMenu(s.master, 20000);
      const produced = await ui.clickDownloadsOrOpens(s.master, menu.getByRole('link', { name: label, exact: true }),
        { context: s.context, recorder: s.recorder, label: 'owned-label', timeout: 30000 });
      try {
        const response = await s.context.request.get(await pdfUrlFor(produced, s.config.baseUrl), { maxRedirects: 0 });
        const bytes = await response.body();
        assertIsPdf({ label }, response.status(), response.headers()['content-type'] || '', bytes);
        const text = execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8', timeout: 15000, maxBuffer: 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] });
        assertPatientText(text, s.marker, /Envelope|Address/.test(label) ? address : null);
      } finally { if (produced.page) await produced.page.close(); }
    });
  }
  await s.step('print settings produce exactly two selected address labels', async () => {
    const menu = await openPrintMenu(s.master, 20000);
    const settings = await s.popup(s.master, menu.getByRole('link', { name: 'Print Label', exact: true }), 'label-settings');
    for (let i = 1; i <= 5; i++) await settings.locator(`[name="label${i}checkbox"]`).uncheck();
    await settings.locator('[name="label3checkbox"]').check();
    await settings.locator('[name="label3no"]').fill('2');
    await settings.locator('[name="left"]').fill('20');
    await settings.locator('[name="top"]').fill('30');
    await settings.locator('[name="Submit"]').click();
    await settings.locator('.label-block').first().waitFor();
    h.assert(await settings.locator('.label-block').count() === 2, 'Print settings ignored the selected label count');
    for (const block of await settings.locator('.label-block').all()) assertPatientText(await block.innerText(), s.marker, address);
    h.assert(await settings.locator('.label-block').first().evaluate(el => el.style.left) === '20px', 'Print preview ignored its left offset');
    h.assert(await settings.locator('.label-block').first().evaluate(el => el.style.top) === '30px', 'Print preview ignored its top offset');
  });
}
if (require.main === module) runWorkflow('demographic-label-content', workflow);
module.exports = { workflow, assertPatientText, resolvePdfUrl };
