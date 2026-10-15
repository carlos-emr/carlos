#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Roster / enrolment changes and the Enrollment History popup (gap-records, §2.4 master record).
 * User path: Schedule ▸ Search ▸ Master Record ▸ Edit ▸ Roster Status "RO" + date + Enrolled To ▸
 * Update Record ▸ "Enrollment History" (clinic status heading) popup; Edit again ▸ Roster Status "TE" +
 * termination date + reason ▸ Update Record ▸ Enrollment History again.
 * Asserts: the roster columns reach the demographic row after each update; every update archives the
 * patient (demographicArchive carries the roster status, dates and enrolled-to provider); the Master
 * Record read view shows the stored status; the popup's "current status" block and its history
 * table agree with the database (RO row with its roster date and enrolled-to provider, then the TE row
 * with termination date and reason, in time order); the owned patient's history never lists a
 * status the clinic did not set.
 * Fixtures: the runWorkflow FAKE- patient only (NR roster status; no archive). Cleanup is the harness's
 * (demographicArchive rows of the owned patient are removed with it).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;

async function openEdit(master) {
  await master.locator('#editBtn').click();
  await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
  await master.locator('#updateButton').waitFor({ state: 'visible', timeout: TIMEOUT });
  // The form refuses an empty Canadian postal code client-side, so an unrelated roster edit needs one.
  await master.locator('[name="postal"]').fill('K1A0B1');
}

/**
 * Choose a roster status the way a user does. Changing it runs updateEnrolledTo(), which on 2026.08
 * throws (rosterSelect.getValue is not a function, edit.jsp:854). That script error is moved out of
 * the strict recorder here so every later step is still proven, and re-asserted by the last step.
 */
async function chooseRoster(master, recorder, value, scriptErrors) {
  const before = recorder.pageErrors.length;
  await h.withExpectedDialogs(master, () => master.locator('select[name="roster_status"]').selectOption(value),
    { accept: false });
  for (let index = recorder.pageErrors.length - 1; index >= before; index--) {
    if (/getValue is not a function/.test(recorder.pageErrors[index].text)) {
      scriptErrors.push(...recorder.pageErrors.splice(index, 1));
    }
  }
}

async function saveEdit(master) {
  await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(),
    { timeout: TIMEOUT, label: 'Update Record' });
}

async function setDate(master, prefix, [year, month, day], dayName = 'day') {
  await master.locator(`[name="${prefix}_year"]`).fill(year);
  await master.locator(`[name="${prefix}_month"]`).fill(month);
  await master.locator(`[name="${prefix}_${dayName}"]`).fill(day);
}

/** Rows of the history table as [status, enrolledTo, rostered, terminated, reason] text arrays. */
async function historyRows(popup) {
  return popup.$$eval('#enrollmentHistoryTbl tbody tr', rows => rows.map(row =>
    [...row.querySelectorAll('td')].slice(1).map(cell => cell.textContent.trim())));
}

/** The "current enrollment status" definition list as a label -> value map. */
async function currentStatus(popup) {
  return popup.$$eval('dl.row', lists => {
    const out = {};
    for (const list of lists) {
      const terms = [...list.querySelectorAll('dt')];
      for (const dt of terms) out[dt.textContent.replace(/:\s*$/, '').trim()] = dt.nextElementSibling.textContent.trim();
    }
    return out;
  });
}

async function workflow(s) {
  const { sql, patient, master, context, recorder } = s;
  const row = `demographic WHERE demographic_no=${patient}`;
  const archived = `demographicArchive WHERE demographic_no=${patient}`;
  const provider = s.provider;
  const scriptErrors = [];
  const providerName = sql.value(`SELECT CONCAT(last_name, ', ', first_name) FROM provider WHERE provider_no=${h.sqlString(provider)}`);

  async function openHistory() {
    const link = master.getByRole('link', { name: /Enrollment History/i }).first();
    h.assert(await link.count() === 1, 'The Master Record has no Enrollment History link');
    return s.popup(master, link, 'enrollment-history');
  }

  await s.step('Update Record refuses RO without a date or enrolled-to, and TE without dates, storing nothing', async () => {
    await openEdit(master);
    const url = master.url();
    const submit = master.locator('#updateButton input[type="submit"]').first();
    const alerts = master.locator('#carlos-alert-container .alert');
    async function refused(expected, why) {
      const before = await alerts.count();
      await submit.click();
      await alerts.nth(before).waitFor({ timeout: TIMEOUT });
      const text = (await alerts.nth(before).innerText()).trim();
      h.assert(expected.test(text), `${why}: the refusal says "${text}"`);
      h.assert(master.url() === url, `${why}: the form was submitted anyway`);
    }
    await chooseRoster(master, recorder, 'RO', scriptErrors);
    await refused(/Enrolment Date/i, 'RO with no roster date');
    await setDate(master, 'roster_date', ['2024', '03', '05'], 'day');
    await refused(/enrol to/i, 'RO with no enrolled-to provider');
    await chooseRoster(master, recorder, 'TE', scriptErrors);
    await refused(/Enrolment Termination (Date|Reason)/i, 'TE with no termination date or reason');
    h.assert(sql.value(`SELECT roster_status FROM ${row}`) === 'NR' && sql.value(`SELECT COUNT(*) FROM ${archived}`) === '0',
      'A refused roster change still reached the database');
    await master.locator('[name="roster_date_year"], [name="roster_date_month"], [name="roster_date_day"]')
      .evaluateAll(inputs => inputs.forEach(input => { input.value = ''; }));
  });

  await s.step('rostering the patient (RO, date, enrolled-to) is stored and archived', async () => {
    await chooseRoster(master, recorder, 'RO', scriptErrors);
    await setDate(master, 'roster_date', ['2024', '03', '05'], 'day');
    await master.locator('select[name="roster_enrolled_to"]').selectOption(provider);
    await saveEdit(master);
    await expectValue(sql, `SELECT CONCAT(roster_status,'|',DATE(roster_date),'|',roster_enrolled_to) FROM ${row}`,
      `RO|2024-03-05|${provider}`, 'Update Record did not store the roster status, date and enrolled-to provider');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${archived} AND roster_status='RO' AND DATE(roster_date)='2024-03-05'
      AND roster_enrolled_to=${h.sqlString(provider)}`) === '1',
    'The roster update did not archive exactly one record carrying the new roster state');
  });

  await s.step('Enrollment History shows the current status and the RO history row', async () => {
    const popup = await openHistory();
    await popup.locator('h5').first().waitFor({ timeout: TIMEOUT });
    const current = await currentStatus(popup);
    h.assert(current.Status === 'Rostered', `Current status reads "${current.Status}" instead of Rostered`);
    h.assert(/2024/.test(current['Date Rostered'] || '') && /\b0?5\b/.test(current['Date Rostered']),
      'The current-status block does not show the roster date typed into the form');
    h.assert((current['Enrolled To'] || '').includes(providerName.split(',')[0]),
      'The current-status block does not name the enrolled-to provider');
    const rows = await historyRows(popup);
    const rostered = rows.filter(cells => cells[0] === 'Rostered');
    h.assert(rostered.length === 1 && rostered[0][1].includes(providerName.split(',')[0]),
      `The history table must list one Rostered row naming the enrolled-to provider (saw ${rows.length} rows)`);
    h.assert(!rows.some(cells => cells[0] !== 'Rostered'), 'The history lists a status the clinic never set');
    await popup.close();
  });

  await s.step('terminating the roster (TE, termination date, reason) is stored and archived', async () => {
    await openEdit(master);
    await chooseRoster(master, recorder, 'TE', scriptErrors);
    await setDate(master, 'roster_termination_date', ['2025', '06', '30'], 'day');
    await master.locator('select[name="roster_termination_reason"]').selectOption('12');
    await saveEdit(master);
    await expectValue(sql, `SELECT CONCAT(roster_status,'|',DATE(roster_termination_date),'|',roster_termination_reason) FROM ${row}`,
      'TE|2025-06-30|12', 'Update Record did not store the termination status, date and reason');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${archived}`) === '2', 'Each update must archive the patient exactly once');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${archived} AND roster_status='TE' AND roster_termination_reason='12'`) === '1',
      'The termination update did not archive the terminated roster state');
  });

  await s.step('the Master Record read view shows the terminated status', async () => {
    const view = await master.locator('#clinicStatus').innerText();
    h.assert(/TE|terminated/i.test(view), 'The Master Record clinic-status section does not show the terminated roster status');
  });

  await s.step('Enrollment History lists RO then TE with dates, provider and reason', async () => {
    const popup = await openHistory();
    await popup.locator('h5').first().waitFor({ timeout: TIMEOUT });
    const current = await currentStatus(popup);
    h.assert(current.Status === 'Terminated', `Current status reads "${current.Status}" instead of Terminated`);
    h.assert(/2025/.test(current['Date Terminated'] || '') && (current['Termination Reason'] || '').includes('Health Number error'),
      'The current-status block does not show the termination date and reason');
    const rows = await historyRows(popup);
    h.assert(rows.length === 2, `The history must list the two roster changes (saw ${rows.length})`);
    h.assert(rows[0][0] === 'Rostered' && rows[1][0] === 'Terminated', 'The history is not in time order (Rostered, then Terminated)');
    h.assert(rows[1][3].length > 0 && /2025/.test(rows[1][3]) && rows[1][4].includes('Health Number error'),
      'The terminated row does not show its termination date and reason');
    await popup.close();
  });

  await s.step('choosing a roster status raises no script error (enrolled-to confirmation works)', async () => {
    h.assert(scriptErrors.length === 0,
      `Changing Roster Status threw ${scriptErrors.length} script error(s): updateEnrolledTo() calls rosterSelect.getValue(), `
      + 'which a <select> does not have, so the "enrol to the MRP" confirmation never runs');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-enrollment-history', workflow, { openPatient: true });
