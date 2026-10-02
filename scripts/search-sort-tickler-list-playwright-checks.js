#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Tickler list: paging, the result count, sorting and the status / creator / MRP filters (wave 7, search-sort).
 * User path: Schedule > Tickler (tickler/ViewTicklerMain) > the search box > Next page > the Service Date and Priority
 * headers > the Status, Created By and MRP filters > Create Report (tickler/ListTicklers, server-side DataTables).
 * Asserts, on 55 active owned ticklers (50 a page) plus three completed and two deleted: the count line reads "1 to 50 of
 * 55" over 50 rows, the next page shows the other five and the pages together hold each tickler once; the default and the
 * Service Date header order the pages by service date across the page boundary; the Status filter returns exactly the 55 /
 * 3 / 2 owned rows; Created By and MRP narrow to the owned rows of that provider; and the Priority header orders the rows
 * by priority (High, Normal, Low), ascending and descending. Defects are collected and asserted together in the last step.
 * Fixtures: two synthetic patients with different MRPs and 60 ticklers inserted by SQL (messages carry the run marker);
 * cleanup deletes the ticklers with their rows and the patients and asserts none remain. The saved-view preference is not touched.
 * Implements the wave-7 "search-sort" pattern (tickler filters by status/provider, sort, pagination boundaries, count text).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForTicklerTable } = require('./tickler-forward-filters-playwright-checks');
const k = require('./lib/search-sort-helpers');

const isListRequest = response => response.request().method() === 'GET' && h.pathOnly(response.url()).endsWith('/tickler/ListTicklers');

/** Run `action` and return the ListTicklers JSON it provokes (matching `want`), once the table is idle again. */
async function listAfter(page, want, action) {
  const [response] = await Promise.all([
    page.waitForResponse(r => isListRequest(r) && want(new URL(r.url()).searchParams), { timeout: 30000 }),
    action(),
  ]);
  h.assert(response.status() === 200, `ListTicklers answered HTTP ${response.status()}`);
  const json = await response.json();
  await waitForTicklerTable(page);
  return json;
}

/** Rows currently drawn (comment rows excluded) as {id,date,priority,creator,assignee,status,message}. */
async function drawnRows(page) {
  return page.$$eval('#ticklerResults tbody tr:not(.comment-row)', trs => trs.filter(tr => tr.querySelector('input[name="checkbox"]')).map(tr => {
    const td = i => ((tr.children[i] || {}).textContent || '').replace(/\s+/g, ' ').trim();
    return { id: tr.querySelector('input[name="checkbox"]').value, date: td(4), priority: td(6), creator: td(3), assignee: td(7), status: td(8), message: td(9) };
  }));
}

/** [from, to, of] read from the DataTables count line. */
async function countLine(page) {
  const text = (await page.locator('#ticklerResults_info').innerText()).replace(/,/g, '');
  const m = /(\d+)\D+(\d+)\D+(\d+)/.exec(text);
  h.assert(m, `The tickler list's count line is unreadable: "${text}"`);
  return { from: Number(m[1]), to: Number(m[2]), of: Number(m[3]), text };
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const tag = k.nameTag(marker);
  const other = sql.rows(`SELECT provider_no FROM provider WHERE status='1' AND provider_no NOT LIKE '-%'
    AND provider_no<>${h.sqlString(provider)} ORDER BY provider_no LIMIT 1`)[0];
  if (!other) throw new h.SkipCheck('No second active provider exists for the Created By / MRP filters');
  const otherNo = other[0];
  const owned = `message LIKE ${h.sqlString(`${marker}%`)}`;

  // Patients first, ticklers second: cleanups run in reverse, so the ticklers go before the patients.
  k.registerPatientCleanup(s, tag);
  const patient2 = k.insertPatient(s, { last: `${tag}-Tickler`, first: 'Fixture', provider: otherNo });
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${owned}`).map(r => r[0]);
    ids.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Owned tickler id is not numeric'));
    if (ids.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${owned} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${owned}`) === '0', 'Owned ticklers were not removed');
  });
  const prio = ['Normal', 'High', 'Low'];
  const values = [];
  for (let i = 1; i <= 52; i += 1) {
    values.push(`(${patient},${h.sqlString(`${marker} T${String(i).padStart(2, '0')}`)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL ${i} DAY),${h.sqlString(provider)},'${prio[i % 3]}',${h.sqlString(provider)})`);
  }
  for (let i = 1; i <= 3; i += 1) {
    values.push(`(${patient2},${h.sqlString(`${marker} O${i}`)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL ${i + 60} DAY),${h.sqlString(otherNo)},'Normal',${h.sqlString(otherNo)})`);
    values.push(`(${patient},${h.sqlString(`${marker} C${i}`)},'C',NOW(),DATE_SUB(CURDATE(),INTERVAL ${i + 70} DAY),${h.sqlString(provider)},'Normal',${h.sqlString(provider)})`);
  }
  for (let i = 1; i <= 2; i += 1) {
    values.push(`(${patient},${h.sqlString(`${marker} D${i}`)},'D',NOW(),DATE_SUB(CURDATE(),INTERVAL ${i + 80} DAY),${h.sqlString(provider)},'Normal',${h.sqlString(provider)})`);
  }
  sql.execute(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to) VALUES ${values.join(',')}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${owned}`) === '60', 'The tickler fixtures were not created');
  const defects = [];

  const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_tickler)').first(),
    { context: s.context, recorder: s.recorder, label: 'tickler-list', timeout: 20000 });
  const list = opened.page;
  h.assert(h.pathOnly(list.url()).endsWith('/tickler/ViewTicklerMain'), 'The schedule Tickler link did not open the tickler list');
  await waitForTicklerTable(list);
  const search = needle => listAfter(list, p => p.get('search[value]') === needle,
    () => list.locator('#ticklerResults_filter input[type="search"]').fill(needle));
  let json = await search(marker);
  let firstPage;

  await s.step('the count line reads "1 to 50 of 55" over 50 rows of the active owned ticklers', async () => {
    firstPage = await drawnRows(list);
    const line = await countLine(list);
    h.assert(firstPage.length === 50 && line.from === 1 && line.to === 50 && line.of === 55,
      `The count line says "${line.text}" over ${firstPage.length} rows, not 1 to 50 of 55 over 50`);
    h.assert(json.recordsFiltered === 55, `ListTicklers answered recordsFiltered=${json.recordsFiltered}, not 55`);
    h.assert(firstPage.every(r => r.status.length), 'A drawn row has no status');
  });

  await s.step('the next page shows the other five and the pages together hold each tickler once, by service date', async () => {
    json = await listAfter(list, p => p.get('start') === '50', () => list.locator('#ticklerResults_next a').click());
    const secondPage = await drawnRows(list);
    const line = await countLine(list);
    h.assert(secondPage.length === 5 && line.from === 51 && line.to === 55 && line.of === 55,
      `Page two says "${line.text}" over ${secondPage.length} rows, not 51 to 55 of 55 over 5`);
    const ids = [...firstPage, ...secondPage].map(r => r.id);
    h.assert(new Set(ids).size === 55, `The two pages hold ${new Set(ids).size} distinct ticklers, not 55`);
    const dates = [...firstPage, ...secondPage].map(r => r.date);
    h.assert(dates.every((d, i) => i === 0 || dates[i - 1] >= d), 'The default order is not newest service date first across the page boundary');
    h.assert(await list.locator('#ticklerResults_next.disabled').count() === 1, 'The last page still offers a next page');
  });

  await s.step('the Service Date header reverses the order across the page boundary', async () => {
    json = await listAfter(list, p => p.get('order[0][column]') === '4' && p.get('order[0][dir]') === 'asc' && p.get('start') === '0',
      () => list.locator('#ticklerResults thead th').nth(4).click());
    const a = await drawnRows(list);
    json = await listAfter(list, p => p.get('start') === '50', () => list.locator('#ticklerResults_next a').click());
    const b = await drawnRows(list);
    const dates = [...a, ...b].map(r => r.date);
    h.assert(a.length === 50 && b.length === 5 && dates.every((d, i) => i === 0 || dates[i - 1] <= d),
      'Service Date ascending is not oldest first across the page boundary');
  });

  await s.step('the Status filter returns exactly the owned active, completed and deleted ticklers', async () => {
    const counts = {};
    for (const [value, want] of [['C', 3], ['D', 2], ['A', 55]]) {
      json = await listAfter(list, p => p.get('status') === value,
        async () => { await list.locator('#ticklerview').selectOption(value); });
      const line = await countLine(list);
      counts[value] = `${line.of}/${(await drawnRows(list)).length}`;
      if (line.of !== want) defects.push(`Status ${value}: the count line says ${line.of}, the owned fixtures number ${want}`);
      if (value !== 'A') {
        const rows = await drawnRows(list);
        if (rows.length !== want || rows.some(r => !r.message.startsWith(marker))) defects.push(`Status ${value}: the rows are not the ${want} owned ones`);
      }
    }
    h.assert(counts.A.startsWith('55/'), `Back on Active the count is ${counts.A}`);
  });

  await s.step('Created By and MRP narrow the list to the owned rows of that provider', async () => {
    for (const [select, label] of [['#providerview', 'Created By'], ['#mrpview', 'MRP']]) {
      const param = select === '#providerview' ? 'provider' : 'mrp';
      json = await listAfter(list, p => p.get(param) === otherNo, async () => {
        await list.locator(select).selectOption(otherNo);
        await list.locator('#formSubmitBtn').click();
      });
      const rows = await drawnRows(list);
      const line = await countLine(list);
      if (rows.length !== 3 || line.of !== 3 || rows.some(r => !/ O\d$/.test(r.message))) {
        defects.push(`${label} = other provider: expected the 3 owned rows O1-O3, the page shows ${rows.length} rows and says "${line.text}"`);
      }
      json = await listAfter(list, p => p.get(param) === provider, async () => {
        await list.locator(select).selectOption(provider);
        await list.locator('#formSubmitBtn').click();
      });
      const line2 = await countLine(list);
      if (line2.of !== 52) defects.push(`${label} = test provider: the count line says ${line2.of}, the owned fixtures number 52`);
      await listAfter(list, p => p.get(param) === '', async () => {
        await list.locator(select).selectOption('all');
        await list.locator('#formSubmitBtn').click();
      });
    }
  });

  await s.step('the Priority header orders the rows High, Normal, Low (and the reverse)', async () => {
    const rank = { High: 0, Normal: 1, Low: 2 };
    for (const dir of ['asc', 'desc']) {
      json = await listAfter(list, p => p.get('order[0][column]') === '6' && p.get('order[0][dir]') === dir && p.get('start') === '0',
        () => list.locator('#ticklerResults thead th').nth(6).click());
      const rows = await drawnRows(list);
      const seq = [...new Set(rows.map(r => r.priority))];
      const ranked = seq.map(p => rank[p]);
      const ok = ranked.every((r, i) => i === 0 || (dir === 'asc' ? ranked[i - 1] <= r : ranked[i - 1] >= r));
      if (!ok) defects.push(`Priority ${dir}: the rows run ${seq.join(' > ')} instead of ${dir === 'asc' ? 'High, Normal, Low' : 'Low, Normal, High'}`);
    }
  });

  await s.step('every filter and sort returned the owned ticklers correctly', async () => {
    h.assert(defects.length === 0, `Tickler list defects: ${defects.join('; ')} `
      + '(TicklerDaoImpl.java:89-92 orders by the priority text column, so Low sorts between High and Normal)');
  });
}

if (require.main === module) runWorkflow('search-sort-tickler-list', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
