#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Usage Report, the practice-profile report (gap-records, Reports §3.6 report-usage).
 * User path: Schedule ▸ Administration ▸ Reports ▸ Usage Report (admin/ViewUsageReport, loaded into the
 * admin shell's #dynamic-content) ▸ pick a provider, Start / End Date ▸ Run Report.
 * Asserts, for an owned provider whose roster is six active patients (ages 5 M, 30 F, 30 M, 50 F, 70 M,
 * 90 F) plus one inactive patient: the form refuses an empty date range client-side; Practice Size counts
 * the active roster only; the age-group rows carry the share of the roster and each group's male / female
 * share (and "---" for an empty group); Scheduled Appts, Encounter Note and Rx counts equal the rows
 * dated inside the typed range (2024-05-01 .. 2024-05-31); the last steps assert that the End Date is
 * inclusive (an appointment and an encounter note dated on it are counted) and that percentages read as
 * clean numbers -- both currently wrong on 2026.08.
 * Fixtures: an idle demo provider (no patients or activity of any kind, else SKIP), six active + one inactive patient (marker surname), appointments, encounter
 * notes and prescriptions with the marker; all deleted in cleanup and checked gone. Counts are scoped to the
 * owned provider, so nothing global is asserted.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;

/**
 * An active demo provider with no patients, appointments, notes or prescriptions at all. The provider
 * dropdown comes from a 5-minute cache of active providers, so a provider row inserted by SQL would not be
 * offered; an idle demo provider is, and holds nothing but this run's marker rows while the check runs.
 */
function idleProviderNo(sql) {
  const free = sql.rows(`SELECT p.provider_no FROM provider p WHERE p.status='1' AND p.provider_no NOT LIKE '-%'
    AND p.provider_no REGEXP '^[0-9]{1,2}$'
    AND NOT EXISTS (SELECT 1 FROM demographic d WHERE d.provider_no=p.provider_no)
    AND NOT EXISTS (SELECT 1 FROM appointment a WHERE a.provider_no=p.provider_no)
    AND NOT EXISTS (SELECT 1 FROM casemgmt_note n WHERE n.provider_no=p.provider_no)
    AND NOT EXISTS (SELECT 1 FROM drugs x WHERE x.provider_no=p.provider_no)
    AND NOT EXISTS (SELECT 1 FROM billing_on_cheader1 b WHERE b.provider_no=p.provider_no)
    AND NOT EXISTS (SELECT 1 FROM providerLabRouting r WHERE r.provider_no=p.provider_no)
    ORDER BY CAST(p.provider_no AS UNSIGNED) DESC LIMIT 1`);
  if (!free.length) throw new h.SkipCheck('no idle demo provider is available for the usage-report roster');
  return free[0][0];
}

async function workflow(s) {
  const { sql, marker, context, recorder, patient } = s;
  const q = h.sqlString;
  const prov = idleProviderNo(sql);
  const today = new Date();
  // Date of birth `years` ago, a month before today's day so the age is unambiguous at any time of year.
  const dob = years => {
    const d = new Date(today.getFullYear() - years - 1, today.getMonth(), 15);
    return [String(d.getFullYear()), String(d.getMonth() + 1).padStart(2, '0'), '15'];
  };
  const roster = [[5, 'M'], [30, 'F'], [30, 'M'], [50, 'F'], [70, 'M'], [90, 'F']];
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE provider_no=${q(prov)} AND special=${q(marker)}`);
    sql.execute(`DELETE FROM casemgmt_note WHERE provider_no=${q(prov)} AND uuid LIKE ${q(marker + '%')}`);
    sql.execute(`DELETE FROM appointment WHERE provider_no=${q(prov)} AND name=${q(marker)}`);
    sql.execute(`DELETE FROM demographic WHERE provider_no=${q(prov)} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE provider_no=${q(prov)})
      +(SELECT COUNT(*) FROM casemgmt_note WHERE provider_no=${q(prov)})
      +(SELECT COUNT(*) FROM appointment WHERE provider_no=${q(prov)})
      +(SELECT COUNT(*) FROM demographic WHERE provider_no=${q(prov)})`) === '0', 'Usage report fixtures were not removed');
  });

  await s.step('fixtures: an idle demo provider given a six-patient active roster, one inactive patient and dated activity', async () => {
    const people = [...roster.map(([age, sex]) => [age, sex, 'AC']), [40, 'F', 'IN']];
    const ids = people.map(([age, sex, status], index) => {
      const [y, m, d] = dob(age);
      return sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth, date_of_birth, sex,
        patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
        VALUES (${q(marker)}, ${q('Usage' + index)}, ${q(y)}, ${q(m)}, ${q(d)}, ${q(sex)}, ${q(status)}, ${q(prov)},
        'ON', 'ON', 'NR', NOW()); SELECT LAST_INSERT_ID()`);
    });
    const appt = (date, label) => sql.execute(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, status, creator, lastUpdateUser) VALUES (${q(prov)}, ${q(date)}, '09:00:00', '09:15:00', ${q(marker)},
      ${ids[0]}, 't', ${q(s.provider)}, ${q(s.provider)})`);
    appt('2024-05-10'); appt('2024-05-20'); appt('2024-05-31'); appt('2024-06-01'); appt('2024-04-30');
    const note = (when, key) => sql.execute(`INSERT INTO casemgmt_note (note, history, uuid, provider_no, observation_date, update_date,
      demographic_no, signed, archived, program_no) VALUES ('Usage fixture note', 'Usage fixture note', ${q(marker + key)}, ${q(prov)},
      ${q(when)}, ${q(when)}, ${ids[0]}, 1, 0, '10000')`);
    note('2024-05-15 09:00:00', 'a'); note('2024-05-31 10:00:00', 'b'); note('2024-06-01 00:00:00', 'c'); note('2024-05-31 23:59:59', 'd');
    const rx = (date, demo) => sql.execute(`INSERT INTO drugs (provider_no, demographic_no, written_date, rx_date, end_date, special, position,
      lastUpdateDate, dispenseInternal, archived) VALUES (${q(prov)}, ${demo}, ${q(date)}, ${q(date)}, ${q(date)}, ${q(marker)}, 1, NOW(), 0, 0)`);
    rx('2024-05-12', ids[0]); rx('2024-05-13', ids[0]); rx('2024-05-14', ids[1]); rx('2024-07-01', ids[2]);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE provider_no=${q(prov)}`) === '7', 'Roster fixtures not created');
  });

  let panel;
  await s.step('Administration ▸ Reports ▸ Usage Report opens the form and refuses an empty date range', async () => {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context, recorder, label: 'usage-administration', timeout: TIMEOUT });
    await admin.locator('#adminNav').waitFor();
    const link = admin.locator('#adminNav a[href$="/admin/ViewUsageReport"]').first();
    await revealAuditLink(admin, link, TIMEOUT);
    panel = await ui.clickInjectsPanel(admin, link, { marker: '#usageForm' });
    await panel.locator('select[name="providerNo"]').selectOption(prov);
    await panel.locator('button[type="submit"]').click();
    await admin.waitForTimeout(500);
    h.assert(await panel.locator('#usageForm label.error, #usageForm .error').count() > 0
      && await panel.locator('legend').count() === 0, 'An empty date range was not refused by the form');
    s.admin = admin;
  });

  let numbers;
  await s.step('Run Report shows the roster size, age/sex shares and the counts for the typed range', async () => {
    const admin = s.admin;
    const type = async (id, value) => {
      const box = panel.locator(id); await box.click(); await box.press('Control+a'); await box.pressSequentially(value); await box.press('Tab');
    };
    await type('#startDate', '2024-05-01');
    await type('#endDate', '2024-05-31');
    await panel.locator('select[name="providerNo"]').selectOption(prov);
    await panel.locator('button[type="submit"]').click();
    await admin.locator('#dynamic-content legend').first().waitFor({ timeout: TIMEOUT });
    const size = (await admin.locator('#dynamic-content dl dd .badge').first().innerText()).trim();
    h.assert(size === '6', `Practice Size reads ${size} instead of the six active patients`);
    const rows = await admin.locator('#dynamic-content table').first().locator('tbody tr').evaluateAll(trs =>
      trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent.trim())));
    h.assert(rows.length === 5, 'The age/sex table does not have its five age groups');
    const counts = (await admin.locator('#dynamic-content table').nth(1).locator('tbody tr td').allInnerTexts()).map(text => Number(text.trim()));
    numbers = { rows, counts };
    // Share of the six-patient roster, within 0.01 (exact: 16.67 / 33.33 / 16.67 / 16.67 / 16.67).
    const expected = [[1 / 6, 1, 0], [2 / 6, 0.5, 0.5], [1 / 6, 0, 1], [1 / 6, 1, 0], [1 / 6, 0, 1]];
    rows.forEach((row, index) => {
      expected[index].forEach((share, column) => {
        // parses a percentage as a number for an assertion; not HTML escaping.
        // nosemgrep: javascript.lang.security.audit.incomplete-sanitization.incomplete-sanitization
        const shown = Number(row[column + 1].replace('%', ''));
        h.assert(Math.abs(shown - share * 100) < 0.01, `Age group ${row[0]} column ${column + 1} shows ${row[column + 1]}, not ${(share * 100).toFixed(2)}%`);
      });
    });
  });

  await s.step('Encounter Note and Rx counts equal the rows dated inside the range', async () => {
    const { counts } = numbers;
    // encounter notes 2024-05-15 (+ the 05-31 10:00 one, asserted in the End Date step), distinct patients with Rx = 2.
    h.assert(counts[5] === 2, `Rx New/Renewals reads ${counts[5]} instead of the two patients prescribed to in the range`);
    h.assert(counts[1] === 0, `Billing reads ${counts[1]} for a provider with no invoices`);
  });

  await s.step('the End Date is inclusive and percentages read as clean numbers', async () => {
    const { counts, rows } = numbers;
    const problems = [];
    if (counts[0] !== 3) problems.push(`Scheduled Appts counts ${counts[0]} not 3 (the 2024-05-31 appointment is dropped)`);
    if (counts[2] !== 3) problems.push(`Encounter Note counts ${counts[2]} not 3 (the full end date must be included and next-day midnight excluded)`);
    const dirty = rows.flatMap(row => row.slice(1)).filter(cell => !/^(---|\d+(\.\d{1,2})?%)$/.test(cell));
    if (dirty.length) problems.push(`percentages carry float noise (${dirty.slice(0, 2).join(', ')})`);
    h.assert(problems.length === 0, `Usage Report: ${problems.join('; ')}`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-usage-report', workflow, { openPatient: true, openMaster: false });
