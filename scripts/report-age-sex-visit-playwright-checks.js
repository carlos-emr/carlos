#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// report-age-sex-visit (coverage plan §3.6 report-clinical-reports, Ontario install).
// User path: Schedule ▸ Administration ▸ Reports ▸ Age-Sex Report (regenerates the
// reportagesex cache, then Create Report in the frame), PCN Catchment Report (paged),
// Visit Report ▸ Manage Visit Report Providers (popup, DbManageProvider) ▸ Create Report,
// and Provider Service Report ▸ Export (CSV download), for one month and two inclusive months.
// The CSV checks include the final month's first/last instants and exclude the adjacent month boundaries.
// Every number asserted is the DELTA the owned fixture contributes, read before and after
// seeding it, in a filter only the fixture can reach (a 1951/1952/1953 date): the owned patient
// (age/sex bucket, rostered, outside the PCN catchment) plus a second owned patient inside the
// catchment, owned billing headers on an owned appointment and owned encounter notes.
// Fixtures carry the run marker and are deleted (and checked gone) in cleanup. Manage Visit
// Report Providers rewrites the clinic-wide "visitreport" provider list, so the list is
// snapshotted and restored and the check runs with EXCLUSIVE=1. CDS and MIS reports are not
// driven: this install has caisi=off, which hides both menu entries.
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { parseCsv } = require('./lib/export-content-helpers');

const JOINED = '1951-03-07';
const VISIT_DATE = '1952-02-14';
const SERVICE_MONTH = '02/1953';
const AGE_SQL = (alias = 'd') => `FLOOR(DATEDIFF(CURRENT_DATE(), STR_TO_DATE(CONCAT(${alias}.year_of_birth,'-',`
  + `${alias}.month_of_birth,'-',${alias}.date_of_birth), '%Y-%m-%d')) / 365.25)`;
const AGE_BUCKETS = [[0, 4], [5, 9], [10, 14], [15, 19], [20, 24], [25, 29], [30, 34], [35, 39], [40, 44],
  [45, 49], [50, 54], [55, 59], [60, 64], [65, 69], [70, 74], [75, 79], [80, 84], [85, 89], [90, 94], [95, 200]];

async function openAdmin(s) {
  const { page } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'report-admin', timeout: 20000 });
  await page.locator('#adminNav').waitFor();
  return page;
}

async function menu(admin, selector) {
  const link = admin.locator(`#adminNav ${selector}`).first();
  await revealAuditLink(admin, link, 20000);
  await link.waitFor({ state: 'visible' });
  return link;
}

/** Cell texts of every data row in a table, trimmed. */
async function tableCells(scope, selector) {
  return scope.locator(selector).evaluateAll(rows => rows.map(row =>
    [...row.querySelectorAll('td')].map(cell => cell.textContent.replace(/\s+/g, ' ').trim())));
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const group = `PW${marker.slice(-8)}`;
  // The IS NULL flags ride along because mysql -B prints SQL NULL and the string
  // 'NULL' identically; the restore branches on them, not on the parsed values.
  const reportProviders = `SELECT id,provider_no,team,status FROM reportprovider WHERE action='visitreport' ORDER BY id`;
  const reportProviderNulls = `SELECT provider_no IS NULL,team IS NULL,status IS NULL FROM reportprovider
    WHERE action='visitreport' ORDER BY id`;
  const originalReportProviders = sql.rows(reportProviders);
  const originalNulls = sql.rows(reportProviderNulls);
  let insidePatient;
  let appointment;
  const headers = [];
  const notes = [];

  // Cleanup is registered before the first write; it removes only rows this run created.
  s.cleanup(() => {
    if (notes.length) sql.execute(`DELETE FROM casemgmt_note WHERE note_id IN (${notes.join(',')}) AND note=${q(marker)}`);
    if (headers.length) sql.execute(`DELETE FROM billing_on_cheader1 WHERE id IN (${headers.join(',')}) AND demographic_name=${q(marker)}`);
    if (appointment) sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointment} AND name=${q(marker)}`);
    const statements = [`DELETE FROM reportprovider WHERE action='visitreport'`];
    // Restore the exact rows: original ids, and SQL NULL only where the column was NULL.
    originalReportProviders.forEach(([id, ...columns], row) => {
      const [providerNo, team, status] = columns.map((value, column) => (originalNulls[row][column] === '1' ? 'NULL' : q(value)));
      statements.push(`INSERT INTO reportprovider(id,provider_no,team,action,status) VALUES (${Number(id)},${providerNo},${team},'visitreport',${status})`);
    });
    sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
    h.assert(JSON.stringify(sql.rows(reportProviders)) === JSON.stringify(originalReportProviders)
      && JSON.stringify(sql.rows(reportProviderNulls)) === JSON.stringify(originalNulls),
      'The visit-report provider list was not restored');
    sql.execute(`DELETE FROM mygroup WHERE mygroup_no=${q(group)} AND provider_no=${q(provider)}`);
    const owned = [patient, insidePatient].filter(Boolean);
    sql.execute(`DELETE FROM reportagesex WHERE demographic_no IN (${owned.join(',')})`);
    if (insidePatient) {
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${insidePatient} AND last_name=${q(`${marker}-IN`)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE note=${q(marker)})
      + (SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_name=${q(marker)})
      + (SELECT COUNT(*) FROM appointment WHERE name=${q(marker)})
      + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(group)})
      + (SELECT COUNT(*) FROM reportagesex WHERE demographic_no IN (${owned.join(',')}))
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${q(`${marker}-IN`)})`) === '0',
    'The report fixtures were not removed');
  });

  const admin = await openAdmin(s);

  // ---------------------------------------------------------------- Age-Sex Report
  const ageSexLink = () => menu(admin, 'a.xlink[data-submit-form="ageSexForm"]');
  async function regenerateAgeSex() {
    const link = await ageSexLink();
    const posted = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/oscarReport/DbReportAgeSex')
      && response.request().method() === 'POST', { timeout: 30000 });
    await link.click();
    const response = await posted;
    h.assert(response.status() === 200, `Age-Sex Report regeneration answered HTTP ${response.status()}`);
    const frame = admin.frame({ name: 'myFrame' });
    h.assert(frame, 'Age-Sex Report did not open in the administration frame');
    await frame.locator('select[name="providerview"]').waitFor();
    return frame;
  }
  async function ageSexCounts(frame, action) {
    await frame.locator(`input[name="reportAction"][value="${action}"]`).check();
    await frame.locator('select[name="providerview"]').selectOption(provider);
    await frame.locator('input[name="xml_vdate"]').fill(JOINED);
    await frame.locator('input[name="xml_appointment_date"]').fill(JOINED);
    await Promise.all([
      frame.waitForURL(url => url.pathname.endsWith('/oscarReport/ViewOscarReportAgeSex') && url.searchParams.get('reportAction') === action),
      frame.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    await frame.locator('select[name="providerview"]').waitFor();
    await h.assertNotErrorPage(frame, `Age-Sex Report (${action})`);
    const rows = await tableCells(frame, 'table tr');
    const buckets = {};
    for (const cells of rows) {
      const bucket = /^(\d+)-(\d+)$/.exec(cells[0] || '');
      if (bucket) buckets[cells[0]] = { female: Number(cells[1]), male: Number(cells[cells.length - 3]) };
    }
    const total = rows.find(cells => cells[0] === 'Total');
    h.assert(Object.keys(buckets).length === 20 && total, `Age-Sex Report (${action}) did not render its 20 age rows and total`);
    return { buckets, female: Number(total[1]), male: Number(total[total.length - 3]) };
  }

  const age = Number(sql.value(`SELECT ${AGE_SQL()} FROM demographic d WHERE demographic_no=${patient}`));
  const [low, high] = AGE_BUCKETS.find(([from, to]) => age >= from && age <= to);
  const bucket = `${low}-${high}`;
  const baseline = {};
  await s.step('Age-Sex Report opens from Administration ▸ Reports and reports a baseline for the owned join date', async () => {
    const frame = await regenerateAgeSex();
    for (const action of ['TO', 'RO', 'NR']) baseline[action] = await ageSexCounts(frame, action);
  });

  sql.execute(`UPDATE demographic SET sex='F', roster_status='RO', patient_status='AC', date_joined=${q(JOINED)},
      postal='K1A 0B1', city='Ottawa', province='ON'
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND roster_status='RO'
    AND date_joined=${q(JOINED)}`) === '1', 'The owned patient was not prepared for the report');

  await s.step(`Age-Sex Report regeneration stores the owned patient (age ${age}) and counts it once in F ${bucket}`, async () => {
    const frame = await regenerateAgeSex();
    const stored = sql.rows(`SELECT age, sex, roster, provider_no, reportdate=CURDATE() FROM reportagesex WHERE demographic_no=${patient}`);
    h.assert(JSON.stringify(stored) === JSON.stringify([[String(age), 'F', 'RO', provider, '1']]),
      `Age-Sex regeneration did not store exactly one current row for the owned patient (${stored.length} rows)`);
    const after = {};
    for (const action of ['TO', 'RO', 'NR']) after[action] = await ageSexCounts(frame, action);
    for (const action of ['TO', 'RO']) {
      h.assert(after[action].buckets[bucket].female === baseline[action].buckets[bucket].female + 1,
        `Age-Sex ${action}: F ${bucket} went from ${baseline[action].buckets[bucket].female} to ${after[action].buckets[bucket].female}, expected +1`);
      h.assert(after[action].female === baseline[action].female + 1 && after[action].male === baseline[action].male,
        `Age-Sex ${action}: totals moved by F ${after[action].female - baseline[action].female}, M ${after[action].male - baseline[action].male}; expected F +1, M 0`);
    }
    h.assert(JSON.stringify(after.NR) === JSON.stringify(baseline.NR),
      'Age-Sex "Not Rostered" changed when a rostered patient was added');
  });

  // ---------------------------------------------------------------- PCN Catchment Report
  insidePatient = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,postal,city,lastUpdateDate)
    VALUES (${q(`${marker}-IN`)},'Catchment','1975','05','06','M','AC',${q(provider)},'ON','ON','RO','L8P 4R5','Hamilton',NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(insidePatient), 'The in-catchment patient fixture was not created');

  await s.step('PCN Catchment Report lists the rostered owned patient outside the catchment and omits the one inside it', async () => {
    let panel = await ui.clickInjectsPanel(admin, await menu(admin, 'a[href$="/oscarReport/ViewOscarReportCatchment"]'),
      { marker: '#dynamic-content table' });
    const seen = [];
    let pages = 0;
    for (;;) {
      pages++;
      h.assert(pages <= 200, 'PCN Catchment Report paging did not end');
      const rows = await tableCells(panel, 'table tbody tr, table > tr');
      for (const cells of rows) if ((cells[0] || '').startsWith(marker)) seen.push(cells);
      const next = admin.locator('#dynamic-content .pagination li').last();
      if ((await next.getAttribute('class')).includes('disabled')) break;
      panel = await ui.clickInjectsPanel(admin, next.locator('a'), { marker: '#dynamic-content table' });
    }
    h.assert(seen.length === 1 && seen[0][0] === `${marker},Workflow` && seen[0][6] === 'AC' && seen[0][5] === 'K1A 0B1',
      `PCN Catchment Report over ${pages} pages listed ${seen.length} owned rows; expected only the patient outside the catchment`);
  });

  // ---------------------------------------------------------------- Visit Report
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name,vieworder)
    SELECT ${q(group)}, provider_no, last_name, first_name, '1' FROM provider WHERE provider_no=${q(provider)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(group)}`) === '1', 'The owned provider group was not created');

  let visitPanel;
  await s.step('Manage Visit Report Providers saves the ticked owned group and the Visit Report offers its provider', async () => {
    await ui.clickInjectsPanel(admin, await menu(admin, 'a.defaultvisitreport'), { marker: '#visitForm' });
    const popup = await ui.clickOpensPopup(admin, admin.locator('#dynamic-content a', { hasText: 'Manage Visit Report' }),
      { context: s.context, recorder: s.recorder, label: 'visit-report-providers', timeout: 20000 });
    await popup.locator('form[name="form1"]').waitFor();
    const box = popup.locator(`input[type="checkbox"][value="${provider}|${group}"]`);
    h.assert(await box.count() === 1, 'Manage Visit Report Providers does not list the owned group');
    h.assert(!(await box.isChecked()), 'The owned group was already ticked');
    await box.check();
    const reloaded = admin.waitForURL(url => url.pathname.endsWith('/administration') && url.searchParams.get('show') === 'visitreport');
    await Promise.all([popup.waitForEvent('close'), popup.locator('form[name="form1"] input[type="submit"]').click()]);
    await reloaded;
    // The save rewrites the list, so ids change; compare the rows by content only.
    const rows = sql.rows(reportProviders).map(([, ...columns]) => columns);
    const expected = [...originalReportProviders.map(([, ...columns]) => columns), [provider, group, 'A']]
      .sort((a, b) => a.join('|').localeCompare(b.join('|')));
    h.assert(JSON.stringify(rows.slice().sort((a, b) => a.join('|').localeCompare(b.join('|')))) === JSON.stringify(expected),
      'The visit-report provider list does not hold exactly the ticked rows');
    visitPanel = admin.locator('#dynamic-content');
    await visitPanel.locator('#visitForm').waitFor();
    h.assert(await visitPanel.locator(`#providerview option[value="${provider}"]`).count() === 1,
      'The Visit Report does not offer the provider just added to its list');
  });

  async function visitRow() {
    await visitPanel.locator('#reportActionVr').check();
    await visitPanel.locator('#providerview').selectOption(provider);
    await visitPanel.locator('#xml_vdate').fill(VISIT_DATE);
    await visitPanel.locator('#xml_appointment_date').fill(VISIT_DATE);
    const answered = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/oscarReport/ViewOscarReportVisitControl')
      && new URL(response.url()).searchParams.get('reportAction') === 'vr');
    await visitPanel.locator('#visitForm button[type="submit"]').click();
    h.assert((await answered).status() === 200, 'The Visit Report request failed');
    await visitPanel.locator('#visitForm').waitFor();
    const rows = await tableCells(visitPanel, 'table tr');
    const mine = rows.filter(cells => cells.length === 14 && cells[1].startsWith(`${provider} `));
    h.assert(mine.length === 1 && mine[0][0] === group, `The Visit Report shows ${mine.length} rows for the owned provider/group`);
    return mine[0].slice(2).map(Number);
  }

  let visitBefore;
  await s.step('Visit Report for the owned service date reports a baseline row for the owned group', async () => {
    visitBefore = await visitRow();
  });

  appointment = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,
      program_id,notes,reason,location,resources,type,style,billing,status,createdatetime,updatedatetime,creator,remarks,urgency)
    VALUES (${q(provider)},${q(VISIT_DATE)},'10:00:00','10:15:00',${q(marker)},${patient},0,'','','','','','','','B',NOW(),NOW(),
      ${q(provider)},'',''); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointment), 'The appointment fixture was not created');
  // Nursing Home (04) created and seen by the provider; Hospital (02) created by the provider for
  // another appointment provider; a deleted Clinic (00) claim that must not count.
  for (const [visitType, apptProvider, status] of [['04', provider, 'O'], ['02', '-1', 'O'], ['00', provider, 'D']]) {
    const id = sql.value(`INSERT INTO billing_on_cheader1 (header_id,demographic_no,provider_no,appointment_no,demographic_name,
        billing_date,billing_time,total,paid,status,visittype,apptProvider_no,creator,clinic)
      VALUES (0,${patient},${q(provider)},${appointment},${q(marker)},${q(VISIT_DATE)},'10:00:00',0,0,${q(status)},${q(visitType)},
        ${q(apptProvider)},${q(provider)},''); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A billing header fixture was not created');
    headers.push(id);
  }

  await s.step('Visit Report counts the owned claims by visit type for creator and appointment provider, not the deleted one', async () => {
    const after = await visitRow();
    const delta = after.map((value, index) => value - visitBefore[index]);
    // Columns: creator Clinic, Outpatient, Hospital, ER, Nursing Home, Home; then the same per appointment provider.
    h.assert(JSON.stringify(delta) === JSON.stringify([0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0]),
      `Visit Report moved by ${JSON.stringify(delta)}; expected creator Hospital +1, Nursing Home +1 and appointment-provider Nursing Home +1`);
  });

  // ---------------------------------------------------------------- Provider Service Report
  // The owned month holds no other note (asserted), so the exported row must equal the fixture.
  const role = sql.value("SELECT role_no FROM secRole WHERE role_name='doctor'");
  h.assert(/^\d+$/.test(role), 'This install has no doctor role');
  const monthNotes = `SELECT COUNT(*) FROM casemgmt_note WHERE observation_date >= '1953-01-31' AND observation_date < '1953-04-02'`;
  h.assert(sql.value(monthNotes) === '0', 'Another note already occupies the Provider Service fixture range and boundary days');
  for (const [type, demographic, observed] of [
    ['face to face encounter with client', patient, '1953-02-10 10:00:00'],
    ['face to face encounter with client', patient, '1953-02-10 11:00:00'],
    ['face to face encounter with client', insidePatient, '1953-02-10 12:00:00'],
    ['telephone encounter with client', patient, '1953-02-10 13:00:00'],
    ['face to face encounter with client', patient, '1953-03-01 00:00:00'],
    ['telephone encounter with client', insidePatient, '1953-03-31 23:59:59'],
    ['face to face encounter with client', insidePatient, '1953-04-01 00:00:00'],
    ['telephone encounter with client', patient, '1953-01-31 23:59:59'],
  ]) {
    const id = sql.value(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,
        signing_provider_no,encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived)
      VALUES (NOW(),${q(observed)},${demographic},${q(provider)},${q(marker)},1,${q(provider)},${q(type)},'',
        ${q(role)},'',UUID(),'0',0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'An encounter note fixture was not created');
    notes.push(id);
  }

  async function refusesInvalidMonths(page) {
    const exports = [];
    const record = request => {
      if (new URL(request.url()).pathname.endsWith('/oscarReport/ViewProviderServiceReportExport')) exports.push(request.url());
    };
    page.on('request', record);
    try {
      for (const value of ['', '13/1953', '02/53', 'invalid']) {
        for (const name of ['startDate', 'endDate']) {
          await page.locator(`#${name}`).fill(value);
          await page.locator('#psrForm h4').click();
        }
        await page.locator('#psrForm button[type="submit"]').click();
        await page.locator('#startDateError').waitFor({state: 'visible'});
        await page.locator('#endDateError').waitFor({state: 'visible'});
        await page.waitForTimeout(150);
        h.assert(exports.length === 0, `Invalid month ${JSON.stringify(value)} submitted a report`);
      }
    } finally {
      page.off('request', record);
    }
  }
  const shellJQuery = await admin.evaluateHandle(() => window.jQuery);
  for (const [mode, endMonth, expected] of [
    ['dynamic', SERVICE_MONTH, {'1953-02': '3,1,0,2,1,0,2', '1953-02 to 1953-02': '3,1,0,2,1,0,2'}],
    ['dynamic', '03/1953', {'1953-02': '3,1,0,2,1,0,2', '1953-03': '1,1,0,1,1,0,2', '1953-02 to 1953-03': '4,2,0,2,2,0,2'}],
    ['direct', SERVICE_MONTH, {'1953-02': '3,1,0,2,1,0,2', '1953-02 to 1953-02': '3,1,0,2,1,0,2'}],
  ]) {
    await s.step(`Provider Service ${mode} CSV labels ${SERVICE_MONTH} through ${endMonth} inclusively and counts only that range`, async () => {
      const reportPage = mode === 'direct' ? await s.context.newPage() : admin;
      try {
        if (mode === 'direct') {
          h.wireStrictPage(reportPage, 'provider-service-direct', s.recorder);
          await reportPage.goto(h.appUrl(s.config.baseUrl, '/oscarReport/ViewProviderServiceReportForm'), {waitUntil: 'load'});
        } else if (await admin.locator('#psrForm').count() === 0) {
          await ui.clickInjectsPanel(admin, await menu(admin, 'a[href$="/oscarReport/ViewProviderServiceReportForm"]'),
            {marker: '#psrForm'});
          h.assert(await admin.evaluate(original => window.jQuery === original, shellJQuery),
            'The report form replaced the Administration jQuery instance');
        }
        if (endMonth === SERVICE_MONTH) await refusesInvalidMonths(reportPage);
        for (const [field, value] of [['#startDate', SERVICE_MONTH], ['#endDate', endMonth]]) {
          await reportPage.locator(`#psrForm ${field}`).fill(value);
          // Click away, as a reader does, so the month picker closes before the next control.
          await reportPage.locator('#psrForm h4').click();
          const picked = await reportPage.locator(`#psrForm ${field}`).evaluate(input => {
            const date = input._flatpickr.selectedDates[0];
            return date && `${String(date.getMonth() + 1).padStart(2, '0')}/${date.getFullYear()}`;
          });
          h.assert(picked === value, `The picker did not parse the typed month ${value}`);
        }
        await reportPage.locator('.flatpickr-calendar.open').waitFor({ state: 'detached', timeout: 5000 }).catch(() => {});
        h.assert(await reportPage.locator('.flatpickr-calendar.open').count() === 0, 'The month picker stayed open over the Export button');
        const outcome = await ui.clickDownloadsOrOpens(reportPage, reportPage.locator('#psrForm button[type="submit"]'),
          { context: s.context, recorder: s.recorder, label: 'provider-service-export', timeout: 30000 });
        h.assert(outcome.kind === 'download' && /^provider_service_.*\.csv$/.test(outcome.download.suggestedFilename()),
          'Provider Service Report Export did not download a provider_service_*.csv file');
        const endLabel = `${endMonth.slice(3)}-${endMonth.slice(0, 2)}`;
        h.assert(outcome.download.suggestedFilename() === `provider_service_1953-02_${endLabel}.csv`,
          'The download filename is not the safe normalized date range');
        const csv = fs.readFileSync(await outcome.download.path(), 'utf8');
        const lines = csv.split('\n');
        h.assert(lines[0] === 'Agency Name,Program Name,Program Type,Date,total encounters face to face,total encounters by phone,'
          + 'total encounters with out client,unique client encountered face to face,unique clients encountered by phone,'
          + 'unique clients encountered with out client,total unique clients encountered', 'The Provider Service CSV header changed');
        const rows = parseCsv(csv);
        const clinicName = sql.value("SELECT IFNULL(clinic_name,'') FROM clinic LIMIT 1") || '';
        h.assert(rows.slice(1).filter(row => row.length > 1).every(row => row[0] === clinicName),
          'The CSV agency differs from the configured clinic name');
        const agencyRows = rows.filter(cells => cells[1] === 'all programs');
        h.assert(JSON.stringify(agencyRows.map(cells => cells[3])) === JSON.stringify(Object.keys(expected)),
          'The CSV month/summary labels differ from the requested inclusive range');
        for (const [date, counts] of Object.entries(expected)) {
          const row = agencyRows.find(cells => cells[3] === date);
          h.assert(row, `The Provider Service CSV has no all-programs row for ${date}`);
          h.assert(row.slice(4).join(',') === counts,
            `Provider Service Report ${date} reads ${row.slice(4).join(',')}; the owned notes give ${counts}`);
        }
      } finally {
        if (mode === 'direct') await reportPage.close();
      }
    });
  }
  await shellJQuery.dispose();
}

if (require.main === module) runWorkflow('report-age-sex-visit', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
