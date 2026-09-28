#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Requires referral_menu=yes in a disposable deployment and Poppler's pdfinfo/
// pdftotext. Selects owned specialists through the UI, then checks the session's
// batch PDF and error responses using the authenticated browser request context.
const { spawnSync } = require('node:child_process');
const { assert, sqlString, SkipCheck } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

function inspectPdf(body) {
  const info = spawnSync('pdfinfo', ['-'], { input: body });
  const text = spawnSync('pdftotext', ['-', '-'], { input: body });
  assert(info.status === 0 && text.status === 0, 'Referral response is not a readable PDF');
  return { pages: Number(/Pages:\s+(\d+)/.exec(info.stdout.toString())?.[1]), text: text.stdout.toString() };
}

async function workflow(s) {
  for (const tool of ['pdfinfo', 'pdftotext']) {
    if (spawnSync(tool, ['-v']).error) throw new SkipCheck('Install Poppler pdfinfo and pdftotext to validate referral labels');
  }
  const menu = s.schedule.getByRole('link', { name: /^Ref$/ });
  if (!await menu.count()) throw new SkipCheck('Enable referral_menu=yes for referral label coverage');
  const ids = [];
  s.cleanup(() => {
    for (const id of ids) {
      assert(s.sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE specId=${id} AND lName=${sqlString(s.marker)}`) === '1',
        'Referral fixture ownership changed');
      s.sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${id} AND lName=${sqlString(s.marker)}`);
      assert(s.sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE specId=${id}`) === '0', 'Referral fixture remains');
    }
  });
  for (const first of ['LabelOne', 'LabelTwo']) {
    const id = s.sql.value(`INSERT INTO professionalSpecialists(fName,lName,address,salutation)
      VALUES(${sqlString(first)},${sqlString(s.marker)},'123 Fixture Street',NULL); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id), 'Referral fixture was not created');
    ids.push(id);
  }
  const page = await s.popup(s.schedule, menu, 'referral-labels');
  await page.locator('#nameQuery').fill(s.marker);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    page.locator('input[type="submit"][value="Search"]').click(),
  ]);
  assert(await page.getByText('Print up to 200 labels per batch.', { exact: true }).isVisible(), 'Referral batch limit is not visible');
  await s.step('UI selection yields every requested label, including names without salutations', async () => {
    for (const id of ids) {
      const [response] = await Promise.all([
        page.waitForResponse(r => r.url().includes('/admin/ManageBillingReferral') && r.request().method() === 'POST'),
        page.locator(`input[name="checked_${id}"]`).check(),
      ]);
      assert(response.ok(), 'Referral selection failed');
    }
    await page.locator('#checked_items_tbl').filter({ hasText: 'LabelTwo' }).waitFor();
    const response = await s.context.request.get(`${s.config.baseUrl}/printReferralLabelAction?useCheckList=true`);
    assert(response.status() === 200 && (response.headers()['content-type'] || '').includes('application/pdf'), 'Label batch did not return PDF');
    const pdf = inspectPdf(await response.body());
    assert(pdf.pages === 2 && ['LabelOne', 'LabelTwo', s.marker, '123 Fixture Street'].every(value => pdf.text.includes(value)),
      'Referral batch omitted a page, name or address');
    await page.reload();
    assert(!(await page.locator('#checked_items_tbl').innerText()).includes(s.marker), 'Successful batch did not clear its selection');
  });
  await s.step('empty and invalid selections are HTTP errors rather than empty PDFs', async () => {
    for (const query of ['useCheckList=true', 'billingreferralNo=invalid', 'billingreferralNo=12345678901', `ids=${Array(201).fill(ids[0]).join(',')}`]) {
      const response = await s.context.request.get(`${s.config.baseUrl}/printReferralLabelAction?${query}`);
      assert(response.status() === 400, 'Invalid selection looked successful');
      assert(!(response.headers()['content-type'] || '').includes('application/pdf'), 'Invalid selection was labelled as a PDF');
    }
  });
}
if (require.main === module) runWorkflow('referral-labels', workflow, { openPatient: false });
module.exports = { workflow };
