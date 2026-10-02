#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Master Record edit: identity, health card, staff, status and chart-alert fields, and the HIN guard
 * (gap-records, §2.4 demographic edit; demographic-edit-update only round-trips five free-text fields).
 * User path: Schedule ▸ Search ▸ Master Record ▸ Edit ▸ change the fields below ▸ Update Record
 * (demographic/DemographicUpdate) ▸ the refreshed Master Record; then Edit ▸ the health number of another
 * patient ▸ Update Record.
 * Asserts: sex, date of birth, title, languages, health number / version / type, effective and renewal
 * dates, resident, patient status and status date, chart number, referral doctor, phone extensions, cell,
 * phone comment, e-mail reach demographic / demographicExt / demographiccust (alert and notes) and are
 * shown again by the refreshed record; each save archives the patient once; saving another patient's
 * health number is refused with the duplicate-HIN message naming that patient, and NOTHING typed in the same
 * submission (a new city) or in the archive is written (the guard must run before any persistence).
 * Fixtures: the runWorkflow FAKE- patient; a second marker-suffixed patient holding the contested HIN and one
 * unused synthetic Ontario HIN. The second patient is removed in cleanup and checked gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { freshOntarioHin, removeMarkedPatients } = require('./lib/gap-records-fixtures');

const TIMEOUT = 20000;

async function openEdit(master) {
  await master.locator('#editBtn').click();
  await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
  await master.locator('#updateButton').waitFor({ state: 'visible', timeout: TIMEOUT });
}

async function typeInto(master, name, value) {
  const box = master.locator(`[name="${name}"]`).first();
  await box.click();
  await box.press('Control+a');
  await box.pressSequentially(value);
  await box.press('Tab');
}

async function workflow(s) {
  const { sql, patient, marker, master, provider } = s;
  const hin = freshOntarioHin(sql);
  const other = `${marker}-B`;
  const otherHin = freshOntarioHin(sql);
  s.cleanup(() => removeMarkedPatients(sql, other));
  // The harness's own parent cleanup removes the owned patient's demographicExt and demographicArchive rows only; the
  // update this check drives also writes demographiccust and demographicExtArchive (keyed by archive id), so remove those
  // here, before the generic cleanup deletes the archive rows they hang from.
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographicExtArchive WHERE archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient})
      OR demographic_no=${patient};
      DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient}
        OR archiveId IN (SELECT id FROM demographicArchive WHERE demographic_no=${patient}))`) === '0',
    'The owned patient\'s custom-field and extension archive rows were not removed');
  });
  const row = `demographic WHERE demographic_no=${patient}`;
  const archived = `demographicArchive WHERE demographic_no=${patient}`;
  const typed = {
    cell: '613-555-0171', ext: '321', wext: '654', comment: `after hours ${marker}`, email: `${marker.toLowerCase()}@example.invalid`,
    chart: `ED${marker.slice(-8)}`, doc: 'FAKE-Referrer', ohip: '654321', alert: `ALERT ${marker}`, notes: `NOTES ${marker}`,
  };
  sql.execute(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth, date_of_birth, sex, patient_status,
    provider_no, hin, ver, hc_type, province, roster_status, lastUpdateDate) VALUES (${h.sqlString(other)}, 'Holder', '1970', '05', '06', 'M', 'AC',
    ${h.sqlString(provider)}, ${h.sqlString(otherHin)}, 'AB', 'ON', 'ON', 'NR', NOW())`);

  await s.step('Update Record stores identity, health card, staff, status and alert fields and archives the patient once', async () => {
    await openEdit(master);
    await master.locator('#mrp').selectOption(provider);
    await master.locator('#enrolledTo').selectOption('');
    const enrolled = await h.withExpectedDialogs(master, () => master.locator('#roster_status').selectOption('RO'));
    h.assert(enrolled.length === 1 && await master.locator('#enrolledTo').inputValue() === provider,
      'Rostering did not offer and apply the MRP as the enrolled provider');
    await h.withExpectedDialogs(master, () => master.locator('#roster_status').selectOption('NR'), { accept: false });
    h.assert(await master.locator('#enrolledTo').inputValue() === provider, 'Declining cleared the enrolled provider');
    await master.locator('#roster_status').selectOption('RO');
    const cleared = await h.withExpectedDialogs(master, () => master.locator('#roster_status').selectOption('NR'));
    h.assert(cleared.length === 1 && await master.locator('#enrolledTo').inputValue() === '',
      'Leaving the roster did not clear the enrolled provider after confirmation');
    await master.locator('[name="postal"]').fill('K1A0B1');
    await master.locator('select[name="title"]').selectOption('DR');
    await master.locator('select[name="official_lang"]').selectOption('French');
    await master.locator('select[name="spoken_lang"]').selectOption('Afar');
    await master.locator('select[name="sex"]').selectOption('M');
    await typeInto(master, 'dob', '1981-03-04');
    await master.locator('#hinBox').fill(hin);
    await master.locator('#verBox').fill('AB');
    await master.locator('select[name="hc_type"]').selectOption('ON');
    for (const [prefix, parts] of [['eff_date', ['2024', '01', '02']], ['hc_renew_date', ['2027', '01', '02']]]) {
      await master.locator(`[name="${prefix}_year"]`).fill(parts[0]);
      await master.locator(`[name="${prefix}_month"]`).fill(parts[1]);
      await master.locator(`[name="${prefix}_date"]`).fill(parts[2]);
    }
    await master.locator('[name="demo_cell"]').fill(typed.cell);
    await master.locator('[name="hPhoneExt"]').fill(typed.ext);
    await master.locator('[name="wPhoneExt"]').fill(typed.wext);
    await master.locator('[name="phoneComment"]').fill(typed.comment);
    await master.locator('[name="email"]').fill(typed.email);
    await master.locator('[name="chart_no"]').fill(typed.chart);
    await master.locator('[name="r_doctor"]').fill(typed.doc);
    await master.locator('[name="r_doctor_ohip"]').fill(typed.ohip);
    await master.locator('select[name="resident"]').selectOption(provider);
    await master.locator('select[name="patient_status"]').selectOption('IN');
    for (const [suffix, value] of [['year', '2025'], ['month', '02'], ['day', '03']]) {
      await master.locator(`[name="patientstatus_date_${suffix}"]`).fill(value);
    }
    await master.locator('textarea[name="alert"]').fill(typed.alert);
    await master.locator('textarea[name="notes"]').fill(typed.notes);
    await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Update Record' });

    await expectValue(sql, `SELECT sex FROM ${row}`, 'M', 'The changed sex was not stored');
    const [d] = sql.rows(`SELECT title, official_lang, spoken_lang, CONCAT(year_of_birth,'-',month_of_birth,'-',date_of_birth), hin, ver, hc_type,
      DATE(eff_date), DATE(hc_renew_date), patient_status, DATE(patient_status_date), chart_no, email, family_doctor FROM ${row}`);
    const want = ['DR', 'French', 'Afar', '1981-03-04', hin, 'AB', 'ON', '2024-01-02', '2027-01-02', 'IN', '2025-02-03', typed.chart, typed.email];
    const names = ['title', 'official_lang', 'spoken_lang', 'dob', 'hin', 'ver', 'hc_type', 'eff_date', 'hc_renew_date',
      'patient_status', 'patient_status_date', 'chart_no', 'email'];
    const wrong = names.filter((name, index) => d[index] !== want[index]);
    h.assert(wrong.length === 0, `Edited fields missing from the stored patient: ${wrong.join(', ')}`);
    h.assert(d[13].includes(`<rd>${typed.doc}</rd>`) && d[13].includes(`<rdohip>${typed.ohip}</rdohip>`), 'The referral doctor was not stored');
    const ext = Object.fromEntries(sql.rows(`SELECT key_val, value FROM demographicExt WHERE demographic_no=${patient}`));
    h.assert(ext.demo_cell === typed.cell && ext.hPhoneExt === typed.ext && ext.wPhoneExt === typed.wext && ext.phoneComment === typed.comment,
      'The extension keys (cell, extensions, comment) were not stored');
    const [cust] = sql.rows(`SELECT cust2, cust3, content FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(cust && cust[0] === provider && cust[1] === typed.alert && cust[2].includes(typed.notes), 'The resident, alert and notes were not stored');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${archived}`) === '1', 'The update did not archive the patient exactly once');
  });

  await s.step('the refreshed Master Record shows the stored values', async () => {
    const text = await master.locator('body').innerText();
    for (const value of [typed.alert, typed.notes, typed.email, typed.cell, typed.chart, '1981-03-04', hin]) {
      h.assert(text.includes(value), 'The refreshed Master Record does not show a value that was just saved');
    }
  });

  await s.step('another patient\'s health number is refused with the duplicate message and nothing is written', async () => {
    const before = sql.rows(`SELECT city, hin, ver, patient_status, chart_no, lastUpdateDate FROM ${row}`);
    const cust = sql.rows(`SELECT cust3 FROM demographiccust WHERE demographic_no=${patient}`);
    await openEdit(master);
    await master.locator('[name="city"]').fill('DupCity');
    await master.locator('#hinBox').fill(otherHin);
    await master.locator('#verBox').fill('AB');
    await master.locator('textarea[name="alert"]').fill('SHOULD NOT BE STORED');
    await master.locator('#updateButton input[type="submit"]').first().click();
    await master.getByText(/already in use by/i).waitFor({ timeout: TIMEOUT });
    h.assert(await master.getByRole('link', { name: new RegExp(other, 'i') }).count() === 1,
      'The duplicate-HIN message does not name the patient holding the number');
    h.assert(JSON.stringify(sql.rows(`SELECT city, hin, ver, patient_status, chart_no, lastUpdateDate FROM ${row}`)) === JSON.stringify(before)
      && JSON.stringify(sql.rows(`SELECT cust3 FROM demographiccust WHERE demographic_no=${patient}`)) === JSON.stringify(cust)
      && sql.value(`SELECT COUNT(*) FROM ${archived}`) === '1',
    'A refused duplicate-HIN update still wrote part of the submission');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-demographic-edit-clinical', workflow, { openPatient: true });
