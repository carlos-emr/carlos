#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Add a patient with every section filled, and the guards on the way in (gap-records, §2.4).
 * User path: Schedule ▸ Search ▸ a no-match search ▸ Create Demographic (demographic/DemographicAdd) ▸
 * fill identity, contact, health-card, roster, status and provider fields ▸ Add Record
 * (DemographicAddRecord) ▸ Go to record. Then Search ▸ Create Demographic again for the guards.
 * Asserts: an empty submit and a health number failing the Ontario mod-10 check are refused with nothing
 * stored; Add Record writes the demographic row (names, title, languages, addresses, phones, email, DOB,
 * sex, HIN/version/type, roster status+date+enrolled-to, chart, patient status, MRP, referral doctor),
 * the demographicExt keys (cell, phone extensions, comment, aboriginal, PHU), the demographiccust
 * nurse/resident, one archive row, one current program admission and one "add" audit row; the new Master Record
 * shows what was typed; the duplicate-name confirmation, when dismissed, stores nothing; a second patient
 * with the same health number is refused ("duplicate") and stores nothing; and, last, dates typed into
 * Effective / Renewal / Date Joined / Date Rostered reach the database (they do not: see the final step).
 * Fixtures: patients carrying the run marker surname and one unused synthetic Ontario HIN. Cleanup removes
 * every row of those patients (not the append-only audit log) and asserts none remain.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { freshOntarioHin, removeMarkedPatients } = require('./lib/gap-records-fixtures');

const TIMEOUT = 30000;

/** Schedule ▸ Search ▸ no-match search ▸ Create Demographic; resolves to the add-form popup. */
async function openAddForm(s, term) {
  const search = await ui.clickOpensPopup(s.schedule, s.schedule.locator('#search a, a:has-text("Search")').first(),
    { context: s.context, recorder: s.recorder, label: 'add-search', timeout: TIMEOUT });
  await search.locator('#keyword, input[name="keyword"]').first().fill(term);
  await search.locator("input[type='submit']").first().click();
  await search.waitForLoadState('networkidle').catch(() => {});
  await search.locator("form[action$='/demographic/ViewDemographicAddARecordHtm'] button[type='submit']").first().click();
  await search.locator('form[name="adddemographic"]').waitFor({ timeout: TIMEOUT });
  return search;
}

async function workflow(s) {
  const { sql, marker, config } = s;
  const hin = freshOntarioHin(sql);
  s.cleanup(() => removeMarkedPatients(sql, marker));
  const row = `demographic WHERE last_name=${h.sqlString(marker)}`;
  const provider = s.provider;
  const typed = {
    first: 'Added', middle: 'Midname', used: 'Addy', address: '1 Fixture Way', city: 'Testville', postal: 'K1A0B1',
    resAddress: '2 Residence Rd', resCity: 'Homeville', resPostal: 'M5V2T6', phone: '613-555-0101', ext: '123', work: '613-555-0102',
    wext: '45', cell: '613-555-0103', comment: `call after 5 ${marker}`, email: `${marker.toLowerCase()}@example.invalid`,
    dob: '1985-07-09', chart: `PW${marker.slice(-8)}`, doc: 'FAKE-Referrer', ohip: '123456',
  };
  let add;

  await s.step('the add form is reached from Search ▸ Create Demographic and refuses an empty submit', async () => {
    add = await openAddForm(s, marker);
    const url = add.url();
    await add.locator('input[type="submit"][value="Add Record"]').first().click();
    await add.waitForTimeout(500);
    h.assert(add.url() === url && await add.locator('form[name="adddemographic"] :invalid').count() > 0,
      'An empty form was submitted instead of being refused by the required-field validation');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${row}`) === '0', 'A refused submit stored a patient');
  });

  await s.step('a health number failing the Ontario check digit is refused with an alert and stores nothing', async () => {
    const form = add.locator('form[name="adddemographic"]');
    await form.locator('input[name="last_name"]').fill(marker);
    await form.locator('input[name="first_name"]').fill(typed.first);
    await form.locator('select[name="sex"]').selectOption('F');
    await form.locator('input[name="inputDOB"]').fill(typed.dob);
    await form.locator('input[name="postal"]').fill(typed.postal);
    await form.locator('input[name="hin"]').fill(hin.slice(0, 9) + String((Number(hin[9]) + 1) % 10));
    const url = add.url();
    const dialogs = await h.withExpectedDialogs(add,
      () => add.locator('input[type="submit"][value="Add Record"]').first().click(), { accept: true });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'A bad health number must raise exactly one alert');
    h.assert(add.url() === url && sql.value(`SELECT COUNT(*) FROM ${row}`) === '0', 'A bad health number was stored');
  });

  await s.step('Add Record stores every section: demographic, extension keys, custom staff, archive, admission, audit', async () => {
    const form = add.locator('form[name="adddemographic"]');
    await form.locator('input[name="hin"]').fill(hin);
    await form.locator('input[name="ver"]').fill('AB');
    await form.locator('input[name="middleNames"]').fill(typed.middle);
    await form.locator('input[name="nameUsed"]').fill(typed.used);
    await form.locator('select[name="title"]').selectOption('MS');
    await form.locator('select[name="official_lang"]').selectOption('French');
    await form.locator('select[name="spoken_lang"]').selectOption('Afar');
    await form.locator('input[name="address"]').fill(typed.address);
    await form.locator('input[name="city"]').fill(typed.city);
    await form.locator('input[name="residentialAddress"]').fill(typed.resAddress);
    await form.locator('input[name="residentialCity"]').fill(typed.resCity);
    await form.locator('input[name="residentialPostal"]').fill(typed.resPostal);
    await form.locator('input[name="phone"]').fill(typed.phone);
    await form.locator('input[name="hPhoneExt"]').fill(typed.ext);
    await form.locator('input[name="phone2"]').fill(typed.work);
    await form.locator('input[name="wPhoneExt"]').fill(typed.wext);
    await form.locator('input[name="demo_cell"]').fill(typed.cell);
    await form.locator('textarea[name="phoneComment"]').fill(typed.comment);
    await form.locator('select[name="newsletter"]').selectOption('Paper');
    await form.locator('select[name="aboriginal"]').selectOption('No');
    await form.locator('input[name="email"]').fill(typed.email);
    await form.locator('select[name="staff"]').selectOption(provider);
    await form.locator('select[name="cust2"]').selectOption(provider);
    await form.locator('input[name="r_doctor"]').fill(typed.doc);
    await form.locator('input[name="r_doctor_ohip"]').fill(typed.ohip);
    await form.locator('select[name="roster_status"]').selectOption('FS');
    await form.locator('select[name="roster_enrolled_to"]').selectOption(provider);
    // A PHU from the install's own lookup list (the first real option, whichever the default preselects not being chosen).
    const phus = await form.locator('select[name="PHU"] option').evaluateAll(options => options.map(option => option.value).filter(Boolean));
    h.assert(phus.length > 0, 'The add form offers no Public Health Unit to choose from');
    const phu = phus[phus.length - 1];
    await form.locator('select[name="PHU"]').selectOption(phu);
    await form.locator('input[name="chart_no"]').fill(typed.chart);
    await form.locator('select[name="patient_status"]').selectOption('IN');
    await add.locator('input[type="submit"][value="Add Record"]').first().click();
    await add.getByText(/Successful Addition of a Demographic Record/i).waitFor({ timeout: TIMEOUT });
    await expectValue(sql, `SELECT COUNT(*) FROM ${row}`, '1', 'Add Record did not store exactly one patient');
    const [d] = sql.rows(`SELECT first_name, middleNames, title, official_lang, spoken_lang, address, city, province, postal,
      residentialAddress, residentialCity, residentialPostal, phone, phone2, email, year_of_birth, month_of_birth, date_of_birth,
      sex, hin, ver, hc_type, roster_status, chart_no, patient_status, provider_no,
      newsletter, family_doctor FROM ${row}`);
    // The form upper-cases first and middle names (upCaseCtrl), by design.
    const want = [typed.first.toUpperCase(), typed.middle.toUpperCase(), 'MS', 'French', 'Afar', typed.address, typed.city, 'CA-ON', typed.postal,
      typed.resAddress, typed.resCity, typed.resPostal, typed.phone, typed.work, typed.email, '1985', '07', '09',
      'F', hin, 'AB', 'ON', 'FS', typed.chart, 'IN', provider, 'Paper'];
    const names = ['first_name', 'middleNames', 'title', 'official_lang', 'spoken_lang', 'address', 'city', 'province', 'postal',
      'residentialAddress', 'residentialCity', 'residentialPostal', 'phone', 'phone2', 'email', 'year_of_birth', 'month_of_birth',
      'date_of_birth', 'sex', 'hin', 'ver', 'hc_type', 'roster_status', 'chart_no',
      'patient_status', 'provider_no', 'newsletter'];
    const wrong = names.filter((name, index) => d[index] !== want[index]);
    h.assert(wrong.length === 0, `Typed fields missing from the stored patient: ${wrong.map(n => n + '=' + d[names.indexOf(n)]).join(', ')}`);
    h.assert(d[27].includes(`<rd>${typed.doc}</rd>`) && d[27].includes(`<rdohip>${typed.ohip}</rdohip>`),
      'The referral doctor and OHIP number were not stored');
    const id = sql.value(`SELECT demographic_no FROM ${row}`);
    const ext = Object.fromEntries(sql.rows(`SELECT key_val, value FROM demographicExt WHERE demographic_no=${id}`));
    h.assert(ext.demo_cell === typed.cell && ext.hPhoneExt === typed.ext && ext.wPhoneExt === typed.wext
      && ext.phoneComment === typed.comment && ext.aboriginal === 'No', 'The extension keys (cell, extensions, comment, aboriginal) were not stored');
    h.assert(sql.value(`SELECT roster_enrolled_to FROM demographic WHERE demographic_no=${id}`) === provider,
      'The roster "enrolled to" provider was not stored');
    h.assert(ext.PHU === phu, 'The chosen Public Health Unit was not stored');
    h.assert(sql.value(`SELECT cust2 FROM demographiccust WHERE demographic_no=${id}`) === provider,
      'The resident staff selection was not stored');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=${id}`) === '1',
      'The new patient was not archived exactly once');
    await expectValue(sql, `SELECT COUNT(*) FROM admission WHERE client_id=${id} AND admission_status='current'`, '1',
      'The new patient was not admitted to a program');
    // The new patient's own row: a provider-wide count is polluted by concurrent checks adding patients as the same login.
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE action='add' AND content='demographic' AND provider_no=${h.sqlString(provider)}
      AND (contentId=${h.sqlString(String(id))} OR demographic_no=${id})`, '1', 'The add was not audited');
  });

  await s.step('Go to record opens the new Master Record showing what was typed', async () => {
    const link = add.getByRole('link', { name: /Go to record/i }).first();
    await link.click();
    await add.locator('#editBtn').waitFor({ timeout: TIMEOUT });
    const text = (await add.locator('body').innerText()).toLowerCase();
    for (const value of [marker, typed.first, typed.address, typed.city, typed.email, typed.chart]) {
      h.assert(text.includes(value.toLowerCase()), 'The Master Record of the new patient does not show a field that was typed into the add form');
    }
    await add.close();
  });

  await s.step('the duplicate-name confirmation, when dismissed, stores no second patient', async () => {
    add = await openAddForm(s, marker);
    const form = add.locator('form[name="adddemographic"]');
    await form.locator('input[name="last_name"]').fill(marker);
    await form.locator('input[name="first_name"]').fill(typed.first);
    await form.locator('select[name="sex"]').selectOption('F');
    await form.locator('input[name="inputDOB"]').fill('1990-02-03');
    await form.locator('input[name="postal"]').fill(typed.postal);
    const dialogs = await h.withExpectedDialogs(add,
      () => add.locator('input[type="submit"][value="Add Record"]').first().click(), { accept: false });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'A patient with the same name must raise the duplicate confirmation');
    await add.waitForTimeout(500);
    h.assert(sql.value(`SELECT COUNT(*) FROM ${row}`) === '1', 'Dismissing the duplicate confirmation still stored a second patient');
  });

  await s.step('a second patient with the same health number is refused and stores nothing', async () => {
    const form = add.locator('form[name="adddemographic"]');
    await form.locator('input[name="first_name"]').fill('Other');
    await form.locator('input[name="hin"]').fill(hin);
    await form.locator('input[name="ver"]').fill('AB');
    await add.locator('input[type="submit"][value="Add Record"]').first().click();
    await add.waitForLoadState('networkidle').catch(() => {});
    const text = await add.locator('body').innerText();
    h.assert(!/Successful Addition of a Demographic Record/i.test(text) && /duplicate|already/i.test(text),
      'A health number already held by another patient was not refused as a duplicate');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${row}`) === '1', 'A duplicate health number stored a second patient');
    await add.close();
  });

  await s.step('dates typed into the date boxes (roster, effective, renewal, joined) reach the database', async () => {
    // add.jsp parseDateField() builds its selectors from a JS template literal whose ${fieldId} the JSP
    // consumes as EL (add.jsp:576-578), so the hidden year/month/day parts of every date box stay empty.
    const problems = [];
    add = await openAddForm(s, marker);
    let form = add.locator('form[name="adddemographic"]');
    const fill = async (name, value) => {
      const box = form.locator(`input[name="${name}"]`);
      await box.click(); await box.press('Control+a'); await box.pressSequentially(value); await box.press('Tab');
    };
    await form.locator('input[name="last_name"]').fill(marker);
    await form.locator('input[name="first_name"]').fill('Dates');
    await form.locator('select[name="sex"]').selectOption('M');
    await form.locator('input[name="inputDOB"]').fill('1991-04-05');
    await form.locator('input[name="postal"]').fill(typed.postal);
    await fill('eff_date', '2024-01-02');
    await fill('hc_renew_date', '2027-01-02');
    await fill('date_joined', '2024-02-03');
    await h.withExpectedDialogs(add, () => add.locator('input[type="submit"][value="Add Record"]').first().click(), { accept: true });
    await add.getByText(/Successful Addition of a Demographic Record/i).waitFor({ timeout: TIMEOUT });
    const [dates] = sql.rows(`SELECT IFNULL(DATE(eff_date),''), IFNULL(DATE(hc_renew_date),''), IFNULL(DATE(date_joined),''), end_date IS NULL
      FROM ${row} AND first_name='Dates'`);
    if (dates[0] !== '2024-01-02') problems.push('Effective Date');
    if (dates[1] !== '2027-01-02') problems.push('Renewal Date');
    if (dates[2] !== '2024-02-03') problems.push('Date Joined');
    if (dates[3] !== '1') problems.push('End Date (stored although it was never typed)');
    await add.close();
    add = await openAddForm(s, marker);
    form = add.locator('form[name="adddemographic"]');
    await form.locator('input[name="last_name"]').fill(marker);
    await form.locator('input[name="first_name"]').fill('Rostered');
    await form.locator('select[name="sex"]').selectOption('F');
    await form.locator('input[name="inputDOB"]').fill('1992-05-06');
    await form.locator('input[name="postal"]').fill(typed.postal);
    await form.locator('select[name="roster_status"]').selectOption('RO');
    await form.locator('select[name="roster_enrolled_to"]').selectOption(provider);
    await fill('roster_date', '2024-03-05');
    const dialogs = await h.withExpectedDialogs(add,
      () => add.locator('input[type="submit"][value="Add Record"]').first().click(), { accept: true });
    if (dialogs.length) problems.push(`rostering a new patient (Add Record said: "${dialogs[0].text}")`);
    else {
      await add.getByText(/Successful Addition of a Demographic Record/i).waitFor({ timeout: TIMEOUT });
      if (sql.value(`SELECT IFNULL(DATE(roster_date),'') FROM ${row} AND first_name='Rostered'`) !== '2024-03-05') problems.push('Date Rostered');
    }
    await add.close();
    h.assert(problems.length === 0, `Typed dates were lost or refused: ${problems.join('; ')} (the date boxes do not fill their hidden year/month/day parts)`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-demographic-add-fields', workflow, { openPatient: false });
