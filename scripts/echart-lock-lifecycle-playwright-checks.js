#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

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
    await s.step('stale session teardown preserves an explicitly transferred lock', async () => {
      const firstChart = await s.chart();
      const second = await h.newContext(s.context.browser(), s.config);
      let takeovers = 0;
      second.on('page', page => h.wireStrictPage(page, 'second-session', s.recorder, {
        async dialogHandler(dialog) {
          if (dialog.type() === 'confirm'
            && /^You have started to edit this note in another window at [^\n]+\.\nDo you wish to continue\?$/.test(dialog.message())) {
            takeovers++;
            await dialog.accept();
          } else {
            s.recorder.unexpectedDialogs.push({ label: 'second-session', type: dialog.type(), text: dialog.message() });
            await dialog.dismiss();
          }
        },
      }));
      try {
        const schedule = await h.login(second, s.config, s.recorder);
        const { masterPage } = await openMasterRecord(second, schedule, s.recorder, {
          searchTerm: s.marker, preferredDemographicNo: s.patient, timeout: 20000,
        });
        const secondChart = await openChart(second, masterPage, s.recorder, 20000);
        await waitForNavbars(secondChart, 20000);
        const sessionId = (await second.cookies()).find(cookie => cookie.name === 'JSESSIONID')?.value;
        h.assert(sessionId, 'The second authenticated session has no session cookie');
        const secondOwner = `${count} AND session_id=${h.sqlString(sessionId)}`;
        await expectValue(s.sql, secondOwner, '1', 'The explicit takeover did not transfer ownership');
        h.assert(takeovers === 1, 'Expected exactly one explicit takeover confirmation');
        await releaseChartLocks(s.context, s.config.baseUrl);
        h.assert(s.sql.value(secondOwner) === '1', 'Stale teardown deleted the second session lock');
        await firstChart.close();
        await releaseChartLocks(second, s.config.baseUrl);
        await expectValue(s.sql, count, '0', 'The current owner could not release its lock');
      } finally {
        await second.close();
      }
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
