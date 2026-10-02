#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Date-range inclusivity of the Tickler list From / To filter (wave 6, boundary values, Part 2).
 * User path: Schedule > Tickler (list) > search box > From / To dates > Create Report.
 * Asserts, with ticklers whose service date is at 00:00:00, 12:00:00 and 23:59:59 on the leap day 2028-02-29 and
 * across the 31 Dec 2027 / 1 Jan 2028 year boundary: a one-day window on the leap day lists every tickler of that
 * day and none of the neighbouring days (23:59:59 on 28 Feb, 00:00:00 on 1 Mar); a 31 Dec to 1 Jan window lists the
 * four ticklers of those two days and neither 30 Dec 23:59:59 nor 2 Jan 00:00:00.
 * Fixtures: eleven ticklers for the owned patient carrying the run marker; cleanup deletes them with their comments
 * and updates and asserts none remain. Windows run in the list's own From / To boxes; nothing is saved.
 * Implements the wave-6 "boundary values" pattern, Part 2 (tickler filters, midnight / 23:59:59, leap day, 31 Dec / 1 Jan).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForTicklerTable } = require('./tickler-forward-filters-playwright-checks');

const STAMPS = {
  y1: '2027-12-30 23:59:59', y2: '2027-12-31 00:00:00', y3: '2027-12-31 23:59:59', y4: '2028-01-01 00:00:00', y5: '2028-01-01 23:59:59', y6: '2028-01-02 00:00:00',
  l1: '2028-02-28 23:59:59', l2: '2028-02-29 00:00:00', l3: '2028-02-29 12:00:00', l4: '2028-02-29 23:59:59', l5: '2028-03-01 00:00:00',
};

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const owned = `demographic_no=${patient} AND message LIKE ${q(`${marker}%`)}`;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${owned}`).map(row => row[0]);
    ids.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Owned tickler id is invalid'));
    if (ids.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${owned} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${owned}`) === '0', 'Owned ticklers were not removed');
  });
  const byLabel = {};
  for (const [label, stamp] of Object.entries(STAMPS)) {
    const id = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${patient},${q(`${marker} ${label}`)},'A',NOW(),${q(stamp)},${q(provider)},'Normal',${q(provider)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `Tickler fixture ${label} was not created`);
    byLabel[`${marker} ${label}`] = label;
  }

  const { page: list } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_tickler)').first(),
    { context: s.context, recorder: s.recorder, label: 'tickler-list', timeout: 20000 });
  await waitForTicklerTable(list);
  await list.locator('#ticklerResults_filter input[type="search"]').fill(marker);
  await waitForTicklerTable(list);

  async function runWindow(from, to) {
    for (const [selector, value] of [['#xml_vdate', from], ['#xml_appointment_date', to]]) {
      const input = list.locator(selector);
      await input.click();
      await input.press('Control+a');
      await input.pressSequentially(value);
      await input.press('Enter');
      await list.locator('h3, .card-header, body').first().click({ position: { x: 2, y: 2 } }).catch(() => {});
      h.assert(await input.inputValue() === value, `${selector} did not keep ${value}`);
    }
    // The assignee filter is a narrowing convenience, not the subject of this check. Multisite installs leave it empty until a
    // site is chosen (it then has neither the provider nor "all"), so a missing option is not a failure.
    const assignee = list.locator('#assignedTo');
    await assignee.selectOption(provider, { timeout: 3000 })
      .catch(() => assignee.selectOption('all', { timeout: 3000 }))
      .catch(() => {});
    const [response] = await Promise.all([
      list.waitForResponse(r => r.request().method() === 'GET' && h.pathOnly(r.url()).endsWith('/tickler/ListTicklers')
        && new URL(r.url()).searchParams.get('startDate') === from && new URL(r.url()).searchParams.get('endDate') === to, { timeout: 30000 }),
      list.locator('#formSubmitBtn').click(),
    ]);
    h.assert(response.status() === 200, `The tickler list answered HTTP ${response.status()}`);
    const json = await response.json();
    h.assert(Array.isArray(json.data), 'ListTicklers did not answer a DataTables payload');
    await waitForTicklerTable(list);
    return json.data.map(row => byLabel[String(row.message || '').trim()]).filter(Boolean).sort().join(',');
  }

  await s.step('a one-day window on the leap day lists the ticklers from 00:00:00 to 23:59:59 and neither neighbouring day', async () => {
    const got = await runWindow('2028-02-29', '2028-02-29');
    h.assert(got === 'l2,l3,l4', `The leap-day window listed [${got}], expected [l2,l3,l4]`);
  });
  await s.step('a 31 Dec to 1 Jan window keeps both edge days whole and excludes the day before and the day after', async () => {
    const got = await runWindow('2027-12-31', '2028-01-01');
    h.assert(got === 'y2,y3,y4,y5', `The 2027-12-31..2028-01-01 window listed [${got}], expected [y2,y3,y4,y5]`);
  });
}

if (require.main === module) runWorkflow('boundary-date-tickler', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
