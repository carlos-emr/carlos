#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Issue #4132: <rewrite:reWrite> built its link from request.getRequestURI(), which after a
// gate forward is the internal /WEB-INF/jsp/... view path, so Prevention Print and eDoc
// Combine PDF posted to /carlos/WEB-INF/jsp/... and answered 404. This check drives both
// buttons the way a clinician does and proves each one now posts to its Struts route and
// returns a real PDF.
//
// Owns a synthetic FAKE-PW patient, one prevention and one PDF document; all are removed.
// Requires the workflow env contract plus DOCUMENT_DIR (the installed document store the
// check writes its one-page fixture PDF into, as stored-document-mutations does).
// Local disposable database only.
const fs = require('node:fs');
const path = require('node:path');
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { fixturePdf } = require('./incoming-pdf-extraction-playwright-checks');

/** The form action the browser will POST to, resolved exactly as the browser resolves it. */
async function resolvedAction(page, selector) {
  return page.locator(selector).first().evaluate(form => new URL(form.getAttribute('action'), document.baseURI));
}

/** Clicks a control that submits a form answering with an attachment, and returns the PDF bytes. */
async function submitForDownload(page, control, pathname, label) {
  const download = page.waitForEvent('download', { timeout: 60000 });
  // A failed response assertion below must not leave this waiter rejecting unhandled
  // (it would surface during fixture cleanup); the original promise is still awaited.
  download.catch(() => {});
  // Matches any POST so a regression to a /WEB-INF/... target is reported with its URL
  // instead of timing out waiting for the expected route.
  const [answer] = await Promise.all([
    page.waitForResponse(r => r.request().method() === 'POST', { timeout: 60000 }),
    control.click(),
  ]);
  assert(new URL(answer.url()).pathname === pathname,
    `${label} posted to ${new URL(answer.url()).pathname}, not ${pathname}`);
  assert(answer.status() === 200, `${label} answered HTTP ${answer.status()} from ${new URL(answer.url()).pathname}`);
  assert(/^application\/pdf/i.test(answer.headers()['content-type'] || ''), `${label} did not answer application/pdf`);
  const file = await (await download).path();
  const bytes = fs.readFileSync(file);
  assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', `${label} download is not a PDF`);
  // A header alone would accept a truncated stream; a complete PDF ends with its trailer.
  assert(bytes.subarray(-1024).toString('latin1').includes('%%EOF'), `${label} download is truncated (no %%EOF)`);
  return bytes;
}

async function workflow(s) {
  const { sql, patient, marker, provider, config } = s;
  const contextPath = new URL(config.baseUrl).pathname.replace(/\/$/, '');

  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  const prevention = sql.value(`INSERT INTO preventions
    (demographic_no,creation_date,prevention_date,provider_no,prevention_type,deleted,refused,never,creator,lastUpdateDate)
    VALUES (${patient},NOW(),'2026-01-02',${sqlString(provider)},'Inf','0','0','0',${sqlString(provider)},NOW());
    SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(prevention), 'Prevention fixture was not inserted');

  await s.step('Prevention Print posts to /prevention/printPrevention and returns a PDF', async () => {
    const chart = await s.chart();
    const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
    const action = await resolvedAction(index, 'form[name="printFrm"]');
    assert(!action.pathname.includes('/WEB-INF/'), `Print form targets an internal path: ${action.pathname}`);
    assert(action.pathname === `${contextPath}/prevention/printPrevention`,
      `Print form targets ${action.pathname}, not the printPrevention route`);
    const button = index.locator('input[name="printButton"]');
    await button.click();
    assert(await button.inputValue() === 'Print', 'Enable Print did not switch the button to Print');
    assert(await index.locator('input[name="printHP"]:checked').count() > 0, 'No prevention is selected for printing');
    await submitForDownload(index, button, `${contextPath}/prevention/printPrevention`, 'Prevention Print');
    await index.close();
  });

  const configured = process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR;
  assert(configured, 'Set DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(configured);
  const filename = `${marker}-combine.pdf`;
  const file = path.join(store, filename);
  let documentNo;
  let createdFile = false;
  s.cleanup(() => {
    if (documentNo) {
      assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${documentNo} AND docdesc=${sqlString(marker)}`) === '1',
        'Document fixture ownership changed');
      sql.execute(`DELETE FROM ctl_document WHERE document_no=${documentNo} AND module='demographic' AND module_id=${patient};
        DELETE FROM document WHERE document_no=${documentNo} AND docdesc=${sqlString(marker)}`);
    }
    // Only a file this run created is removed: 'wx' refuses an existing path, and that
    // pre-existing file is not ours to delete.
    if (createdFile) fs.rmSync(file, { force: true });
  });
  fs.writeFileSync(file, fixturePdf(marker), { flag: 'wx', mode: 0o644 });
  createdFile = true;
  documentNo = sql.value(`INSERT INTO document
    (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
    VALUES ('lab',${sqlString(marker)},${sqlString(filename)},${sqlString(provider)},${sqlString(provider)},
      'A','application/pdf',0,3,0,CURDATE(),NOW(),NOW()); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(documentNo), 'Document fixture was not inserted');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${documentNo},'A')`);

  await s.step('eDoc Combine PDF posts to /documentManager/combinePDFs and returns the merged PDF', async () => {
    const report = await s.context.newPage();
    await report.goto(`${config.baseUrl}/documentManager/ViewDocumentReport?function=demographic&functionid=${patient}`);
    const checkbox = report.locator(`#docNo${documentNo}`);
    await checkbox.waitFor({ state: 'visible' });
    const combine = report.locator('input[type="button"][onclick*="submitForm"]');
    assert(!(await combine.getAttribute('onclick')).includes('WEB-INF'), 'Combine PDF button targets an internal path');
    await checkbox.check();
    await submitForDownload(report, combine, `${contextPath}/documentManager/combinePDFs`, 'Combine PDF');
    await report.close();
  });
}

if (require.main === module) runWorkflow('rewrite-tag-routes', workflow);
module.exports = { workflow };
