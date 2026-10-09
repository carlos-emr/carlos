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
 * Fixtures: twelve owned rows in `log` (contentId = run marker, far past dates nothing else writes to); cleanup
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

  async function run(start, end, selectedProvider) {
    await report.locator('select[name="providerNo"]').selectOption(selectedProvider);
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

  const windows = [
    { start: '2004-02-29', end: '2004-02-29', want: ['2004-02-29 00:00:00', '2004-02-29 12:00:00', '2004-02-29 23:59:59'] },
    { start: '2003-12-31', end: '2004-01-01', want: ['2003-12-31 00:00:00', '2003-12-31 23:59:59', '2004-01-01 00:00:00', '2004-01-01 23:59:59'] },
  ];
  for (const [scope, selectedProvider] of [['specific provider', provider], ['all providers', '*']]) {
    for (const { start, end, want } of windows) {
      await s.step(`${scope}: ${start} through ${end} includes both selected dates and excludes adjacent days`, async () => {
        const got = await run(start, end, selectedProvider);
        h.assert(JSON.stringify(got) === JSON.stringify(want),
          `The ${start}..${end} window listed [${got.join(', ')}], expected [${want.join(', ')}]; next midnight must be excluded`);
      });
    }
  }
}

if (require.main === module) runWorkflow('boundary-date-audit-log', workflow, { openPatient: false });
module.exports = { workflow };
