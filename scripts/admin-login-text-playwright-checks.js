#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Opt-in: changes the disposable deployment's agreement text, then restores it.
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  if (process.env.PLAYWRIGHT_ALLOW_LOGIN_TEXT_WRITE !== 'true') {
    throw new h.SkipCheck('Set PLAYWRIGHT_ALLOW_LOGIN_TEXT_WRITE=true on an isolated deployment to exercise agreement upload');
  }
  h.assert(process.env.BASE_DOCUMENT_DIR, 'Set BASE_DOCUMENT_DIR to the disposable deployment base document directory');
  const target = path.join(fs.realpathSync(process.env.BASE_DOCUMENT_DIR), 'login', 'AcceptableUseAgreement.txt');
  const stat = fs.existsSync(target) ? fs.lstatSync(target) : null;
  h.assert(!stat || stat.isFile(), 'Agreement fixture refuses a non-regular target');
  const original = stat ? fs.readFileSync(target) : null;
  const fixture = Buffer.from(`${s.marker} synthetic agreement & acceptance.\n`);
  const condition = "name IN ('aua_valid_from','aua_valid_duration')";
  const before = s.sql.rows(`SELECT id,name,value FROM property WHERE ${condition} ORDER BY id`);
  const ids = before.map(row => row[0]);
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid agreement property identity');
  const added = `${condition}${ids.length ? ` AND id NOT IN (${ids.join(',')})` : ''}`;
  s.cleanup(() => {
    const current = fs.existsSync(target) ? fs.readFileSync(target) : null;
    h.assert(current === null || current.equals(fixture) || original && current.equals(original),
      'Agreement text changed outside the owned upload; retain it for recovery');
    if (original) {
      fs.writeFileSync(target, original); fs.chmodSync(target, stat.mode & 0o777);
      if (fs.statSync(target).uid !== stat.uid || fs.statSync(target).gid !== stat.gid) fs.chownSync(target, stat.uid, stat.gid);
      h.assert(fs.readFileSync(target).equals(original), 'Agreement text restoration failed');
    } else if (fs.existsSync(target)) fs.unlinkSync(target);
    const rows = s.sql.rows(`SELECT name,value FROM property WHERE ${added}`);
    h.assert(rows.every(([name,value]) => name === 'aua_valid_duration' && value === '17 days'),
      'Unexpected concurrent agreement changes require recovery');
    s.sql.execute(`DELETE FROM property WHERE ${added} AND name='aua_valid_duration' AND value='17 days'`);
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,name,value FROM property WHERE ${condition} ORDER BY id`)) === JSON.stringify(before),
      'Agreement property restoration failed');
  });
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'agreement-admin' });
  const menu = admin.locator('a[rel$="/admin/uploadEntryText"]');
  if (!await menu.count()) throw new h.SkipCheck('Enable LOGINTEST=yes to expose the agreement administration workflow');
  const panel = menu.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
  if (!await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
  async function open() {
    await menu.click();
    const frame = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
    h.assert(frame, 'Agreement upload iframe did not open');
    await frame.locator('[name="importFile"]').waitFor();
    return frame;
  }
  let page = await open();
  await s.step('reject an empty upload visibly without modifying agreement text or validity', async () => {
    await page.locator('[name="importFile"]').setInputFiles({ name: 'empty.txt', mimeType: 'text/plain', buffer: Buffer.alloc(0) });
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.locator('input[type="submit"]').click()]);
    h.assert((await page.locator('[role="alert"]').innerText()).trim(), 'Empty upload was silently accepted');
    const current = fs.existsSync(target) ? fs.readFileSync(target) : null;
    h.assert(original ? current && current.equals(original) : current === null, 'Rejected upload changed agreement text');
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,name,value FROM property WHERE ${condition} ORDER BY id`)) === JSON.stringify(before),
      'Rejected upload changed agreement validity');
  });
  await s.step('reject mutation by GET without changing validity', async () => {
    const url = new URL(page.url());
    url.searchParams.set('validDurationNumber', '17');
    url.searchParams.set('validDurationPeriod', 'days');
    const response = await s.context.request.get(url.toString());
    h.assert(response.status() === 405 && response.headers().allow === 'POST', 'Agreement mutation by GET was accepted');
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,name,value FROM property WHERE ${condition} ORDER BY id`)) === JSON.stringify(before),
      'Rejected GET changed agreement validity');
  });
  await s.step('reject invalid agreement date visibly before writing text or validity', async () => {
    await page.locator('[name="validForever"]').check();
    await page.locator('[name="foreverFrom"]').fill('not-a-date');
    await page.locator('[name="importFile"]').setInputFiles({ name: 'coverage-agreement.txt', mimeType: 'text/plain', buffer: fixture });
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.locator('input[type="submit"]').click()]);
    h.assert((await page.locator('[role="alert"]').innerText()).trim(), 'Invalid agreement date was silently accepted');
    const current = fs.existsSync(target) ? fs.readFileSync(target) : null;
    h.assert(original ? current && current.equals(original) : current === null, 'Invalid validity changed agreement text');
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,name,value FROM property WHERE ${condition} ORDER BY id`)) === JSON.stringify(before),
      'Invalid date changed agreement validity');
  });
  await s.step('upload and reopen the exact synthetic agreement', async () => {
    await page.locator('[name="validForever"]').uncheck();
    await page.locator('[name="validDurationNumber"]').selectOption('17');
    await page.locator('[name="validDurationPeriod"]').selectOption('days');
    await page.locator('[name="importFile"]').setInputFiles({ name: 'coverage-agreement.txt', mimeType: 'text/plain', buffer: fixture });
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.locator('input[type="submit"]').click()]);
    h.assert(fs.existsSync(target) && fs.readFileSync(target).equals(fixture), 'Agreement upload did not persist exact bytes');
    page = await open();
    h.assert((await page.locator('#auaText').innerText()).includes(s.marker), 'Uploaded agreement did not reopen');
    h.assert(await page.locator('[name="validDurationNumber"]').inputValue() === '17'
      && await page.locator('[name="validDurationPeriod"]').inputValue() === 'days', 'Agreement validity did not persist');
  });
}
if (require.main === module) runWorkflow('admin-login-text', workflow, { openPatient: false });
module.exports = { workflow };
