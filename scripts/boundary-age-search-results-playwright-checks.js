#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Age shown in patient search results on the day before, on and after a birthday (wave 6, boundary values, Part 2).
 * User path: Schedule > an empty slot > Add Appointment popup > patient name search > the Age column of the results
 * (demographicsearch2apptresults.jsp). Nothing is booked.
 * Asserts: a patient whose birthday is tomorrow shows one year less than one whose birthday is today, who shows the
 * same age as one whose birthday was yesterday (a person who turns 25 today is 25), and a 29 Feb birthday patient
 * shows the age the calendar gives today.
 * Fixtures: four synthetic patients inserted by SQL with birth dates computed from the database date; cleanup removes
 * them and asserts none remain.
 * Implements the wave-6 "boundary values" pattern, Part 2 (age on / before / after a birthday, 29 Feb birthday).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const tag = `FAKE-PW${marker.slice(-6)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographic WHERE last_name LIKE ${q(`${tag}-%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${q(`${tag}-%`)}`) === '0', 'Owned patients were not removed');
  });
  const seed = (label, dobExpression) => sql.execute(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    SELECT ${q(`${tag}-${label}`)},'Age',YEAR(d),LPAD(MONTH(d),2,'0'),LPAD(DAY(d),2,'0'),'F','AC',${q(provider)},'ON','ON','NR',NOW()
    FROM (SELECT ${dobExpression} AS d) x`);
  seed('BEFORE', 'DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 25 YEAR), INTERVAL 1 DAY)');
  seed('ON', 'DATE_SUB(CURDATE(), INTERVAL 25 YEAR)');
  seed('AFTER', 'DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 25 YEAR), INTERVAL 1 DAY)');
  seed('LEAP', "'2000-02-29'");
  const expectedLeap = Number(sql.value("SELECT TIMESTAMPDIFF(YEAR, '2000-02-29', CURDATE())"));
  const stored = sql.rows(`SELECT last_name, CONCAT(year_of_birth,'-',month_of_birth,'-',date_of_birth), TIMESTAMPDIFF(YEAR, STR_TO_DATE(CONCAT(year_of_birth,'-',month_of_birth,'-',date_of_birth),'%Y-%m-%d'), CURDATE())
    FROM demographic WHERE last_name LIKE ${q(`${tag}-%`)} ORDER BY last_name`);
  h.assert(stored.length === 4, 'The age fixtures were not created');
  const trueAge = Object.fromEntries(stored.map(([name, , age]) => [name.slice(tag.length + 1), Number(age)]));
  h.assert(trueAge.BEFORE === 24 && trueAge.ON === 25 && trueAge.AFTER === 25 && trueAge.LEAP === expectedLeap, 'Test bug: the database does not give the ages the check expects');

  await s.step('the booking search lists the fixtures with the ages the calendar gives (birthday tomorrow / today / yesterday, 29 Feb)', async () => {
    const slots = s.schedule.locator(`a.adhour[onclick*="provider_no=${provider}&"]`);
    const popup = await ui.clickOpensPopup(s.schedule, slots.nth(2), { context: s.context, recorder: s.recorder, label: 'age-search', timeout: 20000 });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await popup.locator('#keyword').fill(tag);
    await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const shown = {};
    for (const label of Object.keys(trueAge)) {
      const row = popup.locator('table tr').filter({ hasText: `${tag}-${label}`.toUpperCase() }).or(popup.locator('table tr').filter({ hasText: `${tag}-${label}` })).first();
      await row.waitFor({ state: 'visible', timeout: 20000 });
      shown[label] = (await row.locator('td.age').first().innerText()).trim();
    }
    await popup.close().catch(() => {});
    const wrong = Object.keys(trueAge).filter(label => String(shown[label]).replace(/\D+$/, '') !== String(trueAge[label]));
    h.assert(wrong.length === 0, `Search results show the wrong age for: ${wrong.map(label => `${label} shows ${shown[label]}, calendar age ${trueAge[label]}`).join('; ')}. `
      + 'Demographic.getAge() runs Utility.calcAge(y, m, d); a patient must already be the new age on the day of the birthday');
  });
}

if (require.main === module) runWorkflow('boundary-age-search-results', workflow, { openPatient: false });
module.exports = { workflow };
