#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Consultations list: filters, every sortable header, and paging at the 100-row edge (wave 7, search-sort).
 * User path: Schedule > Consultations (encounter/ViewConsultation) > Start / End dates, "Search on" radios, Include
 * completed > Search > each column header (twice: ascending then descending) > Prev / Next (ViewConsultationRequests.jsp).
 * Asserts, on 12 owned requests with three patients, services, consultants, teams, statuses and some empty appointment and
 * follow-up dates: every header lists all 12 rows and orders them by the column it names, ascending then descending;
 * Include completed adds exactly the two completed owned requests; the "appointment date" search lists exactly the owned
 * requests whose appointment date is in the window; and with exactly 100 owned requests in the window the page shows 100 rows
 * with no Next Page, with 101 it shows 100 rows and a Next Page that lists the one remaining row, and narrowing the dates
 * while on page two lists the matching rows instead of an empty page. Defects are collected and asserted together in the
 * last step, so every provable step runs first.
 * Fixtures: three synthetic patients, three FAKE- services and consultants, and 115 consultationRequests inserted by SQL
 * (reason carries the run marker; dates are in 2097 so no demo row falls in a window); cleanup deletes every owned row and
 * asserts none remain. Nothing is edited through the list.
 * Implements the wave-7 "search-sort" pattern (consultation list filters, sort by every header, pagination boundaries).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const k = require('./lib/search-sort-helpers');

async function readRows(page) {
  return page.$$eval('table.consult-table tbody tr', trs => trs.filter(tr => /requestId=/.test(tr.getAttribute('onclick') || '')).map(tr => {
    const td = i => ((tr.children[i] || {}).textContent || '').replace(/\s+/g, ' ').trim();
    return {
      id: /requestId=(\d+)/.exec(tr.getAttribute('onclick'))[1],
      statusCode: (/consult-status-(\d)/.exec((tr.children[0] || {}).className || '') || [])[1],
      urgency: td(1), team: td(2), patient: td(3), provider: td(4), service: td(5), consultant: td(6),
      referral: td(7), appointment: td(8), followUp: td(9),
    };
  }));
}

async function workflow(s) {
  const { sql, provider, marker } = s;
  const q = h.sqlString;
  const tag = k.nameTag(marker);
  const other = sql.rows(`SELECT provider_no FROM provider WHERE status='1' AND provider_no NOT LIKE '-%'
    AND provider_no<>${q(provider)} ORDER BY provider_no LIMIT 1`)[0];
  if (!other) throw new h.SkipCheck('No second active provider exists for the MRP column');
  const owned = `reason=${q(marker)}`;
  const insertId = (statement) => {
    const id = sql.value(`${statement}; SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A fixture row was not created');
    return id;
  };

  s.cleanup(() => {
    sql.execute(`DELETE FROM consultationRequests WHERE ${owned};
      DELETE FROM consultationServices WHERE serviceDesc LIKE ${q(`${tag}%`)};
      DELETE FROM professionalSpecialists WHERE lName LIKE ${q(`${tag}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE ${owned})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc LIKE ${q(`${tag}%`)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName LIKE ${q(`${tag}%`)})`) === '0', 'Owned consultation rows remain');
  });
  k.registerPatientCleanup(s, tag);
  const pats = [['Ada', provider], ['bob', other[0]], ['Cleo', provider]].map(([n, p]) => k.insertPatient(s, { last: `${tag}-${n}`, first: 'Fixture', provider: p }));
  const svcs = ['Alpha', 'Bravo', 'Charlie'].map(n => insertId(`INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(`${tag} ${n}`)},'1')`));
  const specs = ['Kay', 'Lee', 'Max'].map(n => insertId(`INSERT INTO professionalSpecialists (fName,lName,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Syn',${q(`${tag}-${n}`)},NOW(),0,0,0,0)`));
  const teams = ['FPW1', 'FPW2', 'FPW3'];
  const insertCols = `(referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,allergies,
    concurrentProblems,statusText,sendTo,patientWillBook,appointmentDate,followUpDate,lastUpdateDate)`;
  const row = (referral, i, status, appt, follow) => `(${referral},${svcs[(i + 1) % 3]},${specs[(i + 2) % 3]},${q(provider)},${pats[i % 3]},'${status}',
    '${[1, 2, 3][(i + 1) % 3]}',${q(marker)},'Synthetic','','','','',${q(teams[i % 3])},0,${appt},${follow},NOW())`;
  const setS = [];
  for (let i = 1; i <= 12; i += 1) {
    setS.push(row(`'2097-02-${String(i).padStart(2, '0')}'`, i, [1, 2, 3][i % 3],
      i % 4 === 0 ? 'NULL' : `'2097-03-${String(25 - i).padStart(2, '0')}'`, i % 5 === 0 ? 'NULL' : `'2097-04-${String(3 + i).padStart(2, '0')}'`));
  }
  setS.push(row("'2097-02-20'", 1, 4, 'NULL', 'NULL'), row("'2097-02-21'", 2, 4, 'NULL', 'NULL'));
  sql.execute(`INSERT INTO consultationRequests ${insertCols} VALUES ${setS.join(',')}`);
  const setP = [];
  for (let n = 0; n < 100; n += 1) setP.push(row(`DATE_ADD('2097-06-01', INTERVAL ${n} DAY)`, n, 1, 'NULL', 'NULL'));
  sql.execute(`INSERT INTO consultationRequests ${insertCols} VALUES ${setP.join(',')}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE ${owned}`) === '114', 'The consultation fixtures were not created');
  const defects = [];

  const { page: list } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations', timeout: 30000 });
  const form = list.locator('form[action$="/encounter/ViewConsultation"]');
  await form.waitFor({ state: 'visible', timeout: 30000 });
  const search = async ({ from, to, completed = false, on = 'ref' }) => {
    await list.locator('#startDate').fill(from);
    await list.locator('#endDate').fill(to);
    if (completed) await list.locator('#includeCompleted').check(); else await list.locator('#includeCompleted').uncheck();
    await list.locator(on === 'ref' ? '#searchDateRef' : '#searchDateAppt').check();
    await ui.clickAndAwaitReload(list, form.locator('input[type="submit"]'), { timeout: 30000, label: 'consultation search' });
  };
  const mine = () => readRows(list);
  const ownedIds = sql.rows(`SELECT requestId FROM consultationRequests WHERE ${owned}`).map(r => r[0]);
  const ownedOnly = async () => (await readRows(list)).filter(r => ownedIds.includes(r.id));

  await s.step('a date window lists exactly the 12 owned open requests, and Include completed adds the two completed ones', async () => {
    await search({ from: '2097-02-01', to: '2097-02-28' });
    h.assert((await mine()).length === 12 && (await ownedOnly()).length === 12, 'The February window did not list exactly the 12 open owned requests');
    await search({ from: '2097-02-01', to: '2097-02-28', completed: true });
    h.assert((await mine()).length === 14, `Include completed listed ${(await mine()).length} rows, not 14`);
    await search({ from: '2097-02-01', to: '2097-02-28' });
  });

  await s.step('"search on appointment date" lists exactly the owned requests whose appointment date is in the window', async () => {
    const expected = sql.rows(`SELECT requestId FROM consultationRequests WHERE ${owned} AND status<>'4'
      AND appointmentDate BETWEEN '2097-03-14' AND '2097-03-20' ORDER BY requestId`).map(r => r[0]);
    await search({ from: '2097-03-14', to: '2097-03-20', on: 'appt' });
    const shown = (await readRows(list)).map(r => r.id).sort((a, b) => a - b);
    h.assert(expected.length > 2 && JSON.stringify(shown) === JSON.stringify(expected),
      `The appointment-date window listed ${shown.length} rows, the owned fixtures in it number ${expected.length}`);
  });

  const columns = [
    ['Status', 0, 'statusCode', (a, b) => Number(a) - Number(b)],
    ['Team', 2, 'team', (a, b) => k.collator.compare(a, b)],
    ['Patient', 3, 'patient', (a, b) => k.collator.compare(a, b)],
    ['MRP', 4, 'provider', (a, b) => k.collator.compare(a, b)],
    ['Service', 5, 'service', (a, b) => k.collator.compare(a, b)],
    ['Consultant', 6, 'consultant', (a, b) => k.collator.compare(a, b)],
    ['Referral date', 7, 'referral', (a, b) => (a < b ? -1 : a > b ? 1 : 0)],
    ['Appointment date', 8, 'appointment', (a, b) => (a < b ? -1 : a > b ? 1 : 0)],
    ['Follow-up date', 9, 'followUp', (a, b) => (a < b ? -1 : a > b ? 1 : 0)],
  ];
  await s.step('every header lists all 12 requests ordered by its column, ascending then descending', async () => {
    await search({ from: '2097-02-01', to: '2097-02-28' });
    for (const [label, index, field, cmp] of columns) {
      for (const dir of ['ascending', 'descending']) {
        const header = list.locator('thead th').nth(index).locator('a');
        await ui.clickAndAwaitReload(list, header.first(), { timeout: 30000, label: `sort by ${label}` });
        const rows = await ownedOnly();
        if (rows.length !== 12) { defects.push(`${label} ${dir}: lists ${rows.length} of the 12 owned requests`); continue; }
        // Empty dates are compared out of the sequence: where they sit (first or last) is not the question.
        const values = rows.map(r => r[field]).filter(v => v !== '' && v !== 'N/A').map(v => (field === 'appointment' ? v.slice(0, 10) : v));
        if (field === 'appointment' && dir === 'ascending' && rows.some(r => /T\d\d:\d\d/.test(r.appointment))) {
          defects.push(`a request with an appointment date and no time shows "${rows.find(r => /T\d\d:\d\d/.test(r.appointment)).appointment}" in the Appointment column`);
        }
        const ordered = values.every((v, i) => i === 0 || (dir === 'ascending' ? cmp(values[i - 1], v) <= 0 : cmp(values[i - 1], v) >= 0));
        if (!ordered) defects.push(`${label} ${dir}: rows run ${values.join(' | ')}`);
      }
    }
  });

  await s.step('with exactly 100 requests in the window the page shows 100 rows and no Next Page', async () => {
    await search({ from: '2097-06-01', to: '2097-09-08' });
    const rows = await readRows(list);
    h.assert(rows.length === 100, `The 100-request window listed ${rows.length} rows`);
    if (await list.getByRole('button', { name: /Next/i }).count()) {
      defects.push('the window holds exactly 100 requests (one full page) yet the page offers Next Page');
    }
  });

  await s.step('with 101 requests the page shows 100 rows and Next Page lists the one remaining row', async () => {
    sql.execute(`INSERT INTO consultationRequests ${insertCols} VALUES ${row("'2097-09-08'", 1, 1, 'NULL', 'NULL')}`);
    await search({ from: '2097-06-01', to: '2097-09-08' });
    h.assert((await readRows(list)).length === 100, 'The 101-request window did not show a full first page');
    const next = list.getByRole('button', { name: /Next/i });
    h.assert(await next.count() === 1, 'The 101-request window offers no Next Page');
    await ui.clickAndAwaitReload(list, next.first(), { timeout: 30000, label: 'consultation next page' });
    const rows = await readRows(list);
    h.assert(rows.length === 1, `Page two listed ${rows.length} rows, not the one remaining request`);
    h.assert(await list.getByRole('button', { name: /Prev/i }).count() === 1, 'Page two offers no Prev button');
  });

  await s.step('narrowing the dates while on page two lists the matching requests instead of an empty page', async () => {
    await list.locator('#startDate').fill('2097-06-01');
    await list.locator('#endDate').fill('2097-06-03');
    await ui.clickAndAwaitReload(list, form.locator('input[type="submit"]'), { timeout: 30000, label: 'narrow from page two' });
    const rows = await readRows(list);
    if (rows.length !== 3) defects.push(`searching a window of 3 requests from page two lists ${rows.length} (the search keeps the old page offset)`);
  });

  await s.step('every filter, header and page of the consultation list was correct', async () => {
    h.assert(defects.length === 0, `Consultation list defects: ${defects.join('; ')} `
      + '(ViewConsultationRequests.jsp:507 offers Next when the page is full; the filter form carries the old offset (:440) into every search and sort; '
      + 'EctViewConsultationRequestsUtil.java:202 appends a literal " T00:00:00" when there is no appointment time)');
  });
}

if (require.main === module) runWorkflow('search-sort-consult-list', workflow, { openPatient: false });
module.exports = { workflow };
