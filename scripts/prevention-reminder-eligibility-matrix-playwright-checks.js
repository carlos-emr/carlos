#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 9: which prevention reminders a patient is given, by age, sex and history.
 * prevention-reminders covers the flu shot at 70 and a twelve-year-old Td, prevention-screening-reminder-controls a
 * mammogram's next date and Never Remind, prevention-completed-externally an outside flu shot. This covers the rest of
 * the rules that act on age, sex and the kinds of record a clinician enters.
 * User path: E-Chart ▸ Preventions (the Prevention Recommendations box); records are entered and amended through the
 * type's form on that page (Completed, Completed externally, Ineligible; Smoking Yes / Never), never by SQL.
 * Expectations come from prevention.drl's DOCUMENTATION (its schedule reference and each rule group's comment), not
 * from the rules' conditions; scripts/lib/prevention-eligibility-rules.js quotes the sentences and
 * scripts/prevention-eligibility-rules.test.js keeps them matched to the rule file.
 * Asserts:
 *   - age and sex, no history: for women and men on the last day before and the first day of every documented
 *     boundary (13, 25, 40, 45, 65, 70, 75, 81), each of seven rules shows its message exactly when documented, in
 *     the documented box; every mismatch of every row is reported together.
 *   - history: a FIT 25 months old is overdue and amending its date to 6 months ago clears it (the archived date no
 *     longer counts); a FIT completed externally counts as done; a colonoscopy 11 years old supersedes FIT and is
 *     itself due; an ineligible mammogram, and an ineligible HPV-CERVIX, silence their rules; a mammogram 24 months
 *     old is coming due and amended to 25 months it is overdue; a current smoker of 60 is offered LDCT and amending
 *     the assessment to non-smoker withdraws it; recording AAA and BMD clears their reminders.
 * Ages are set by SQL on the owned patient, from the date the application itself offers as today (its clock, which
 * can differ from the database's). SKIP when the clinic loads its own rule base from the database (ResourceStorage);
 * a PREVENTION_FILE override cannot be seen from here and would show up as mismatches.
 * Fixtures: the runWorkflow FAKE- patient, whose sex and birth date are set per row; its preventions and their ext
 * rows are cleared between cases and removed in cleanup.
 */
const h = require('./lib/playwright-harness');
const {
  applicationToday, datesBefore, openPreventionIndex, ownPatientPreventions, preventionForms, readRecommendations,
} = require('./lib/prevention-coverage');
const { AGE_RULES, birthDateSql, boundaryAges, describeAge, documentedToFire } = require('./lib/prevention-eligibility-rules');
const { runWorkflow } = require('./lib/workflow-session');

const FIT_OVERDUE = 'FIT is overdue for this patient';
const COLONOSCOPY_DUE = 'Colonoscopy is over 10 years old';
const COLONOSCOPY_SUPERSEDES = 'Colonoscopy makes FIT Ineligible';
const MAM_NONE = 'No Mammogram records can be found for this patient';
const MAM_COMING = 'Mammogram is coming due for this patient';
const MAM_OVERDUE = 'Mammogram is overdue for this patient';
const MAM_NEXT_DATE = 'MAM is overdue for this patient';
const HPV_NONE = 'Neighter HPV-CERVIX nor PAP records can be found for this patient';
const LDCT_OFFER = 'LDCT lung cancer screening recommended for high-risk smoker aged 55-74 (no prior LDCT on record)';
const SMOKING_ASK = 'Need to ask about Smoking';
const AAA_DUE = 'AAA is overdue for this patient';
const BMD_DUE = 'Need Bone Mineral Density Test';

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM ResourceStorage WHERE resourceType='PREVENTION_RULES' AND active=1") !== '0') {
    throw new h.SkipCheck('The clinic loads its own prevention rules from ResourceStorage; the documented defaults do not apply');
  }
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const q = h.sqlString;
  ownPatientPreventions(s);
  const index = await openPreventionIndex(s);
  const today = await applicationToday(s, index);
  const dates = datesBefore(sql, today);
  const setPatient = (sex, dob) => sql.execute(`UPDATE demographic SET sex=${q(sex)}, year_of_birth=DATE_FORMAT(${dob},'%Y'),
    month_of_birth=DATE_FORMAT(${dob},'%m'), date_of_birth=DATE_FORMAT(${dob},'%d')
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const recommendations = async () => {
    const shown = await readRecommendations(index);
    h.assert(!shown.failed, 'Decision support reported an error');
    return shown;
  };
  /** A fresh case: no preventions on record and the given sex and age, birthday today. */
  async function startCase(sex, years) {
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    setPatient(sex, birthDateSql(today, { years, lastDay: false }));
    await index.reload();
    return recommendations();
  }
  /** Records one prevention through its form, as `given` (Completed given, given_ext, ineligible; Smoking yes, never). */
  async function record(type, given, date, fill) {
    const forms = preventionForms(s, index, type);
    const editor = await forms.openNew();
    await editor.locator(`[name="given"][value="${given}"]`).check();
    await editor.locator('#prevDate').fill(date);
    if (fill) await fill(editor);
    const id = await forms.saveNew(editor);
    return { forms, id };
  }
  /** Amends a listed record through its form and returns the replacement's id. */
  async function amend(forms, id, change) {
    const editor = await forms.open(id);
    await change(editor);
    return forms.saveAmendment(editor, id);
  }
  const has = (lines, message) => lines.includes(message);

  await s.step('each documented reminder shows exactly within its age and sex range, on both sides of every boundary', async () => {
    const failures = [];
    for (const sex of ['F', 'M']) {
      for (const point of boundaryAges()) {
        setPatient(sex, birthDateSql(today, point));
        await index.reload();
        const shown = await readRecommendations(index);
        const row = describeAge(sex, point);
        if (shown.failed) failures.push(`${row}: decision support reported an error`);
        for (const rule of AGE_RULES) {
          const asWarning = has(shown.warnings, rule.message);
          const asReminder = has(shown.reminders, rule.message);
          const expected = documentedToFire(rule, sex, point.years);
          if (expected !== (asWarning || asReminder)) failures.push(`${row}: ${rule.rule} ${expected ? 'missing' : 'shown'}`);
          else if (expected && rule.kind === 'warning' && !asWarning) failures.push(`${row}: ${rule.rule} shown as a reminder`);
          else if (expected && rule.kind === 'reminder' && !asReminder) failures.push(`${row}: ${rule.rule} shown as a warning`);
        }
      }
    }
    h.assert(await applicationToday(s, index) === today, 'The application\'s date changed during the run; run it again');
    h.assert(!failures.length, `${failures.length} eligibility mismatch(es): ${failures.join('; ')}`);
  });

  await s.step('a FIT 25 months old is overdue, and amending its date to 6 months ago clears the warning', async () => {
    const baseline = await startCase('F', 60);
    h.assert(has(baseline.warnings, FIT_OVERDUE), 'A woman of 60 with no FIT on record is not told it is overdue');
    const { forms, id } = await record('FOBT', 'given', dates.monthsAgo(25));
    let shown = await recommendations();
    h.assert(has(shown.warnings, FIT_OVERDUE), 'A FIT 25 months old is not reported overdue');
    const recent = dates.monthsAgo(6);
    const amended = await amend(forms, id, editor => editor.locator('#prevDate').fill(recent));
    h.assert(sql.value(`SELECT DATE(prevention_date) FROM preventions WHERE id=${amended}`) === recent, 'The corrected date was not stored');
    shown = await recommendations();
    h.assert(!has(shown.warnings, FIT_OVERDUE), 'The FIT is still reported overdue after its date was corrected to 6 months ago');
  });

  await s.step('a FIT completed externally 6 months ago counts as done', async () => {
    await startCase('F', 60);
    const { id } = await record('FOBT', 'given_ext', dates.monthsAgo(6), editor =>
      editor.locator('#providerName').fill(`${marker} outside lab`));
    h.assert(sql.value(`SELECT refused FROM preventions WHERE id=${id}`) === '3', 'The FIT was not stored as completed externally');
    const shown = await recommendations();
    h.assert(!has(shown.warnings, FIT_OVERDUE), 'A FIT completed externally 6 months ago is reported overdue');
  });

  await s.step('a colonoscopy 11 years old supersedes FIT and is itself due for repeat', async () => {
    await startCase('F', 60);
    await record('COLONOSCOPY', 'given', dates.yearsAgo(11));
    const shown = await recommendations();
    h.assert(has(shown.warnings, COLONOSCOPY_DUE), 'An 11-year-old colonoscopy is not reported due for repeat');
    h.assert(has(shown.reminders, COLONOSCOPY_SUPERSEDES), 'The colonoscopy is not said to make FIT ineligible');
    h.assert(!has(shown.warnings, FIT_OVERDUE), 'FIT is still asked for although a colonoscopy is on record');
  });

  await s.step('an ineligible mammogram silences every mammogram reminder', async () => {
    const baseline = await startCase('F', 60);
    h.assert(has(baseline.warnings, MAM_NONE), 'A woman of 60 with no mammogram on record is not told so');
    await record('MAM', 'ineligible', today);
    const shown = await recommendations();
    const mammogram = [...shown.warnings, ...shown.reminders].filter(line =>
      [MAM_NONE, MAM_COMING, MAM_OVERDUE, MAM_NEXT_DATE].includes(line) || line.startsWith('Last Mammogram was done'));
    h.assert(!mammogram.length, `An ineligible mammogram still shows: ${mammogram.join(' | ')}`);
  });

  await s.step('a mammogram 24 months old is coming due, and corrected to 25 months it is overdue', async () => {
    await startCase('F', 60);
    const { forms, id } = await record('MAM', 'given', dates.monthsAgo(24));
    let shown = await recommendations();
    h.assert(has(shown.reminders, MAM_COMING) && !has(shown.warnings, MAM_OVERDUE),
      `A mammogram 24 months old shows warnings [${shown.warnings.join(' | ')}] and reminders [${shown.reminders.join(' | ')}]`);
    h.assert(has(shown.reminders, 'Last Mammogram was done 24 month(s) ago'), 'The months since the mammogram are not shown as 24');
    await amend(forms, id, editor => editor.locator('#prevDate').fill(dates.monthsAgo(25)));
    shown = await recommendations();
    h.assert(has(shown.warnings, MAM_OVERDUE) && !has(shown.reminders, MAM_COMING),
      `A mammogram corrected to 25 months shows warnings [${shown.warnings.join(' | ')}] and reminders [${shown.reminders.join(' | ')}]`);
    h.assert(has(shown.reminders, 'Last Mammogram was done 25 month(s) ago'), 'The archived date still counts');
  });

  await s.step('an ineligible HPV-CERVIX silences the missing-cervical-screening warning', async () => {
    const baseline = await startCase('F', 40);
    h.assert(has(baseline.warnings, HPV_NONE), 'A woman of 40 with no cervical screening on record is not told so');
    await record('HPV-CERVIX', 'ineligible', today);
    const shown = await recommendations();
    h.assert(!has(shown.warnings, HPV_NONE), 'An ineligible HPV-CERVIX still raises the missing-screening warning');
  });

  await s.step('a current smoker of 60 is offered LDCT, and amending the assessment to non-smoker withdraws it', async () => {
    const baseline = await startCase('M', 60);
    h.assert(has(baseline.reminders, SMOKING_ASK) || has(baseline.warnings, SMOKING_ASK), 'A man of 60 with no smoking history is not asked about it');
    h.assert(!has(baseline.warnings, LDCT_OFFER), 'LDCT is offered with no smoking history on record');
    const { forms, id } = await record('Smoking', 'yes', today);
    h.assert(sql.value(`SELECT refused FROM preventions WHERE id=${id}`) === '0', 'Smoking Yes was not stored as a current smoker');
    let shown = await recommendations();
    h.assert(has(shown.warnings, LDCT_OFFER), 'A current smoker of 60 is not offered LDCT');
    h.assert(!has(shown.reminders, SMOKING_ASK) && !has(shown.warnings, SMOKING_ASK), 'The smoking question is still asked after it was answered');
    const amended = await amend(forms, id, editor => editor.locator('[name="given"][value="never"]').check());
    h.assert(sql.value(`SELECT refused FROM preventions WHERE id=${amended}`) === '1', 'Smoking Never was not stored as a non-smoker');
    shown = await recommendations();
    h.assert(!has(shown.warnings, LDCT_OFFER), 'LDCT is still offered after the assessment was corrected to non-smoker');
  });

  await s.step('recording the AAA screen and a bone density test clears their reminders', async () => {
    const baseline = await startCase('M', 70);
    h.assert(has(baseline.warnings, AAA_DUE) && has(baseline.reminders, BMD_DUE), 'A man of 70 with neither on record is not told about AAA and BMD');
    await record('AAA', 'given', today);
    await record('BMD', 'given', today);
    const shown = await recommendations();
    h.assert(!has(shown.warnings, AAA_DUE), 'The AAA warning remains after the screen was recorded');
    h.assert(!has(shown.reminders, BMD_DUE), 'The bone density reminder remains after the test was recorded');
  });
}

if (require.main === module) runWorkflow('prevention-reminder-eligibility-matrix', workflow, { preflight });
module.exports = { workflow };
