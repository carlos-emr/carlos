#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { revealAuditLink } = require('./lib/playwright-link-audit');

// Own the patient so no shared chart's draft or existing lock is overwritten.
async function main() {
  return runWorkflow('echart-lock-lifecycle', async (s) => {
    const count = `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${s.patient}
      AND provider_no=${h.sqlString(s.provider)}`;
    await s.step('normal chart closure releases its note lock', async () => {
      const chart = await s.chart();
      await expectValue(s.sql, count, '1', 'Opening the chart did not acquire its note lock');
      const closed = chart.waitForEvent('close', { timeout: 20000 });
      await chart.close({ runBeforeUnload: true });
      await closed;
      // This assertion happens before cleanup and uses the real pagehide beacon.
      await expectValue(s.sql, count, '0', 'Normal chart closure left its note lock behind');
    });
    await s.step('navigation audit dismisses a previous chart hover menu', async () => {
      const chart = await s.chart();
      const menu = chart.locator('#menu2');
      await chart.locator('#menuTitle2 a').hover();
      await menu.waitFor({ state: 'visible' });
      await revealAuditLink(chart, chart.locator('#menuTitle3 a'), 20000);
      h.assert(!await menu.isVisible(), 'The previous hover menu still covers other chart links');
    });
    await s.step('awaited harness teardown releases its own lock', async () => {
      await s.chart();
      await expectValue(s.sql, count, '1', 'Reopening the chart did not acquire its note lock');
      await releaseChartLocks(s.context, s.config.baseUrl);
      await expectValue(s.sql, count, '0', 'Harness teardown left its note lock behind');
      await releaseChartLocks(s.context, s.config.baseUrl);
      h.assert(s.sql.value(count) === '0', 'Repeated teardown changed the released lock');
    });
  });
}

if (require.main === module) main();
module.exports = { main };
