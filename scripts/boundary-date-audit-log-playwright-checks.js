#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Date-range inclusivity of the Security Log Report (wave 6, boundary values, Part 2).
 * User path: Schedule > Administration > Security Log Report (admin/LogReport in the administration frame) >
 * provider, content, start / end date > Run Report.
 * Asserts, with owned audit rows stamped at midnight, 12:00 and 23:59:59 on the leap day 2004-02-29, and across the
 * 31 Dec 2003 / 1 Jan 2004 year boundary: a window of one day lists every row of that day (00:00:00 through
 * 23:59:59) and no row of the day before (23:59:59) or of the day after, including the one stamped exactly at
 * 00:00:00 of the next day.
 * Fixtures: eight owned rows in `log` (contentId = run marker, far past dates nothing else writes to); cleanup
 * deletes only rows carrying the marker and asserts they are gone.
 * Implements the wave-6 "boundary values" pattern, Part 2 (end-date inclusivity, midnight, leap day, 31 Dec/1 Jan).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const owned = () => `contentId=${q(marker)} AND action='boundary-date'`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM log WHERE ${owned()}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE ${owned()}`) === '0', 'Owned audit rows were not removed');
  });
  const stamps = [
    '2004-02-28 23:59:59', '2004-02-29 00:00:00', '2004-02-29 12:00:00', '2004-02-29 23:59:59', '2004-03-01 00:00:00', '2004-03-01 00:00:01',
    '2003-12-30 23:59:59', '2003-12-31 00:00:00', '2003-12-31 23:59:59', '2004-01-01 00:00:00', '2004-01-01 23:59:59', '2004-01-02 00:00:00',
  ];
  sql.execute(`INSERT INTO log (dateTime, provider_no, action, content, contentId, ip, data)
    VALUES ${stamps.map(stamp => `(${q(stamp)}, ${q(provider)}, 'boundary-date', 'admin', ${q(marker)}, '127.0.0.1', ${q(stamp)})`).join(',')}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE ${owned()}`) === String(stamps.length), 'The audit fixtures were not created');

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'log-administration', timeout: 20000 });
  const entry = admin.getByRole('link', { name: 'Security Log Report', exact: true, includeHidden: true }).first();
  await revealAuditLink(admin, entry, 20000);
  await entry.click();
  const iframe = admin.locator('#dynamic-content iframe[src*="/admin/LogReport"]').first();
  await iframe.waitFor({ timeout: 20000 });
  const report = await (await iframe.elementHandle()).contentFrame();
  h.assert(report, 'The Security Log Report did not load in the administration frame');
  await report.locator('form[name="myform"]').waitFor({ timeout: 20000 });

  async function run(start, end) {
    await report.locator('select[name="providerNo"]').selectOption(provider);
    await report.locator('select[name="content"]').selectOption('admin');
    for (const [selector, value] of [['#startDate1', start], ['#endDate1', end]]) {
      await report.locator(selector).fill(value);
      await report.locator('h3').first().click();
    }
    const navigated = admin.waitForEvent('framenavigated', { predicate: candidate => candidate === report, timeout: 30000 });
    navigated.catch(() => {});
    await report.locator('input[name="submit"]').click();
    await navigated;
    await report.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    h.assert(await report.locator('.alert-danger').count() === 0, 'The report rejected the dates it was given');
    const rows = await report.locator('table tr').filter({ hasText: marker }).evaluateAll(trs => trs.map(tr => tr.querySelector('td').textContent.trim().replace(/\.0+$/, '')));
    return rows.sort();
  }

  await s.step('a one-day window on the leap day lists every row of that day, from 00:00:00 to 23:59:59, and nothing else', async () => {
    const got = await run('2004-02-29', '2004-02-29');
    const want = ['2004-02-29 00:00:00', '2004-02-29 12:00:00', '2004-02-29 23:59:59'];
    h.assert(JSON.stringify(got.filter(t => want.includes(t))) === JSON.stringify(want) && !got.includes('2004-02-28 23:59:59'),
      `The leap-day window listed [${got.join(', ')}]; every row of 2004-02-29 and none of the day before is expected`);
    const extra = got.filter(t => !want.includes(t));
    h.assert(extra.length === 0, `The window ending 2004-02-29 also listed rows after its End Date: [${extra.join(', ')}] `
      + '(OscarLogDaoImpl.findForReport uses dateTime <= <end date + 1 day 00:00:00>, which admits a row stamped exactly at midnight of the next day; LogReport2Action calls getSysDateEX(end, 1))');
  });

  await s.step('a window across 31 Dec / 1 Jan keeps both edges and excludes 00:00:00 of 2 Jan', async () => {
    const got = await run('2003-12-31', '2004-01-01');
    const want = ['2003-12-31 00:00:00', '2003-12-31 23:59:59', '2004-01-01 00:00:00', '2004-01-01 23:59:59'];
    h.assert(JSON.stringify(got) === JSON.stringify(want), `The 2003-12-31..2004-01-01 window listed [${got.join(', ')}], expected [${want.join(', ')}]`);
  });
}

if (require.main === module) runWorkflow('boundary-date-audit-log', workflow, { openPatient: false });
module.exports = { workflow };
