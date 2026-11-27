#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The rotavirus and tetanus-pertussis reminders that follow the Canadian Immunization Guide (#5048), read on one
 * patient's Preventions page. prevention-reminders covers a 70-year-old's adult Tdap and repeat Td; the Drools unit
 * tests fire every rule on prevention.drl. This check proves the installed page shows the infant, teen and young
 * adult paths the same way, with each dose entered through the type's own form.
 * User path: E-Chart ▸ Preventions (the Prevention Recommendations box); ▸ Rota-1 / Rota-5 / Rota / Tdap ▸ Completed
 * or Ineligible ▸ date ▸ Save.
 * Asserts:
 *   - an infant of 90 days with no rotavirus dose is asked for the first dose; at 150 days (past 15 weeks) is not;
 *   - at 150 days, a Rota-1 given 56 days ago asks for the second dose, 28 days after it;
 *   - a Rota-5 given 28 days ago as the second dose asks for the third dose (a Rot-5 in the series), not a second;
 *   - an "ineligible" rotavirus record dated the day of the second dose takes the third-dose reminder away;
 *   - a 15-year-old with no tetanus record is asked for the teen Tdap, not a Td or the adult Tdap; recording a Tdap
 *     today takes it away;
 *   - an 18-year-old whose only Tdap was given at 15 is asked for the adult Tdap (one dose), not the teen one.
 * Ages and dates are set from the date the application offers as today (its clock, which can differ from the
 * database's). SKIP when the clinic loads its own rule base from the database (ResourceStorage).
 * Fixtures: the runWorkflow FAKE- patient, whose birth date is set per case; its preventions and their ext rows are
 * cleared between cases and removed in cleanup.
 */
const h = require('./lib/playwright-harness');
const {
  applicationToday, openPreventionIndex, ownPatientPreventions, preventionForms, readRecommendations,
} = require('./lib/prevention-coverage');
const { runWorkflow } = require('./lib/workflow-session');

const ROTA_FIRST = 'Needs First Rota vaccine';
const ROTA_SECOND = 'Needs Second Rota vaccine';
const ROTA_THIRD = 'Needs Third Rota vaccine';
const TEEN_TDAP = 'Needs Tdap';
const ADULT_TDAP = 'Needs adult Tdap (one dose)';
const TD_FIRST = 'Needs Td';
const TD_REPEAT = 'Needs repeat Td';

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM ResourceStorage WHERE resourceType='PREVENTION_RULES' AND active=1") !== '0') {
    throw new h.SkipCheck('The clinic loads its own prevention rules from ResourceStorage; the shipped rules do not apply');
  }
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const q = h.sqlString;
  ownPatientPreventions(s);
  const index = await openPreventionIndex(s);
  const today = await applicationToday(s, index);
  const daysAgo = days => sql.value(`SELECT DATE_SUB(${q(today)}, INTERVAL ${Number(days)} DAY)`);
  const yearsAgo = years => sql.value(`SELECT DATE_SUB(${q(today)}, INTERVAL ${Number(years)} YEAR)`);

  /** A fresh case: no preventions on record, born on the given yyyy-mm-dd date. */
  async function startCase(born) {
    h.assert(/^\d{4}-\d{2}-\d{2}$/.test(born), `Birth date "${born}" is not a yyyy-mm-dd date`);
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    sql.execute(`UPDATE demographic SET year_of_birth=DATE_FORMAT(${q(born)},'%Y'), month_of_birth=DATE_FORMAT(${q(born)},'%m'),
      date_of_birth=DATE_FORMAT(${q(born)},'%d') WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    await index.reload();
    return recommendations();
  }
  /** Every warning and reminder line of the box, after checking decision support ran without an error. */
  async function recommendations() {
    const shown = await readRecommendations(index);
    h.assert(!shown.failed, 'Decision support reported an error');
    return [...shown.warnings, ...shown.reminders];
  }
  /** Records one prevention through its form, as `given` (Completed given, ineligible), on the date. */
  async function record(type, given, date) {
    const forms = preventionForms(s, index, type);
    const editor = await forms.openNew();
    await editor.locator(`[name="given"][value="${given}"]`).check();
    await editor.locator('#prevDate').fill(date);
    const id = await forms.saveNew(editor);
    const status = { given: '0', ineligible: '2' }[given];
    h.assert(sql.value(`SELECT CONCAT(DATE(prevention_date),'|',refused) FROM preventions WHERE id=${id}`) === `${date}|${status}`,
      `The ${type} was not stored as ${given} on ${date}`);
    return id;
  }
  const expect = (shown, message, wanted, why) =>
    h.assert(shown.includes(message) === wanted, `${why}: ${wanted ? 'missing' : 'shown'} "${message}" in [${shown.join(' | ')}]`);

  await s.step('an infant of 90 days is asked for the first rotavirus dose, and at 150 days (past 15 weeks) is not', async () => {
    let shown = await startCase(daysAgo(90));
    expect(shown, ROTA_FIRST, true, 'An infant of 90 days with no rotavirus dose');
    shown = await startCase(daysAgo(150));
    expect(shown, ROTA_FIRST, false, 'An infant of 150 days, past the 15-week limit for a first dose');
  });

  await s.step('at 150 days, a Rota-1 given 56 days ago asks for the second dose', async () => {
    await record('Rota-1', 'given', daysAgo(56));
    const shown = await recommendations();
    expect(shown, ROTA_SECOND, true, 'One Rota-1 dose 56 days old');
    expect(shown, ROTA_FIRST, false, 'One Rota-1 dose on file');
    expect(shown, ROTA_THIRD, false, 'One Rota-1 dose on file');
  });

  await s.step('a Rota-5 given 28 days ago as the second dose asks for the third dose, not a second', async () => {
    await record('Rota-5', 'given', daysAgo(28));
    const shown = await recommendations();
    expect(shown, ROTA_THIRD, true, 'A Rota-1 then a Rota-5, the second 28 days old');
    expect(shown, ROTA_SECOND, false, 'Two rotavirus doses on file');
  });

  await s.step('an ineligible rotavirus record dated the day of the second dose takes the third-dose reminder away', async () => {
    await record('Rota', 'ineligible', daysAgo(28));
    const shown = await recommendations();
    expect(shown, ROTA_THIRD, false, 'A rotavirus record marked ineligible on the day of the second dose');
    expect(shown, ROTA_SECOND, false, 'Two rotavirus doses and an ineligible record on file');
  });

  await s.step('a 15-year-old with no tetanus record is asked for the teen Tdap, and a Tdap today takes it away', async () => {
    let shown = await startCase(yearsAgo(15));
    expect(shown, TEEN_TDAP, true, 'A 15-year-old with no booster on file');
    expect(shown, TD_FIRST, false, 'A 15-year-old with no tetanus record');
    expect(shown, ADULT_TDAP, false, 'A 15-year-old');
    await record('Tdap', 'given', today);
    shown = await recommendations();
    expect(shown, TEEN_TDAP, false, 'A 15-year-old with a Tdap today');
    expect(shown, TD_FIRST, false, 'A 15-year-old with a Tdap today');
  });

  await s.step('an 18-year-old whose only Tdap was given at 15 is asked for the adult Tdap, not the teen one', async () => {
    await startCase(yearsAgo(18));
    await record('Tdap', 'given', yearsAgo(3));
    const shown = await recommendations();
    expect(shown, ADULT_TDAP, true, 'An 18-year-old whose only Tdap was the teen dose at 15');
    expect(shown, TEEN_TDAP, false, 'An 18-year-old');
    expect(shown, TD_FIRST, false, 'An 18-year-old while the adult Tdap is due');
    expect(shown, TD_REPEAT, false, 'An 18-year-old while the adult Tdap is due');
  });
}

if (require.main === module) runWorkflow('prevention-cig-reminders', workflow, { preflight });
module.exports = { workflow, preflight };
