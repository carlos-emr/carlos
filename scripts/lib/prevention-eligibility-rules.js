/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The age and sex eligibility of the prevention reminders (issue #5279 section 9), as prevention.drl DOCUMENTS it.
 *
 * Each row's range, sex and wording come from the rule file's own documentation: its header's SCHEDULE REFERENCE
 * and the comment block above each rule group (quoted in `documented`). They are deliberately not read from the
 * rules' eval() conditions, which are the behaviour under test. scripts/prevention-eligibility-rules.test.js keeps
 * the table honest: every quoted sentence and every message must still be in the rule file, and every message in
 * the rule it is attributed to.
 *
 * Each row is a rule that fires on age and sex alone when the patient has no record of the prevention, so a
 * patient with no history is in range exactly when the message shows.
 */

/** Where prevention.drl lives, relative to the repository root. */
const RULE_FILE = 'src/main/resources/oscar/prevention/prevention.drl';

const AGE_RULES = Object.freeze([
  {
    rule: 'MAM 3', kind: 'warning', message: 'No Mammogram records can be found for this patient',
    sex: 'F', minAge: 40, maxAge: 74,
    documented: ['MAM (2-year cycle, 40-74)', 'Rule 3: WARNING if no mammogram records exist'],
  },
  {
    rule: 'AAA 1', kind: 'warning', message: 'AAA is overdue for this patient',
    sex: 'M', minAge: 65, maxAge: 80,
    documented: ['Canadian guideline: one time screening for Men ages 65-80', 'AAA screen overdue no AAA screen on record'],
  },
  {
    rule: 'FOBT 2', kind: 'warning', message: 'FIT is overdue for this patient',
    sex: null, minAge: 45, maxAge: 74,
    documented: ['FOBT/FIT (2-year cycle, 45-74)', 'Rule 2: WARNING - No FIT/FOBT or colonoscopy records at all (ages 45-74)'],
  },
  {
    rule: 'HPV-CERVIX 3', kind: 'warning', message: 'Neighter HPV-CERVIX nor PAP records can be found for this patient',
    sex: 'F', minAge: 25, maxAge: 69,
    documented: ['HPV-CERVIX (5-year cycle, 25-69)', 'Rule 3: WARNING if neither HPV-CERVIX nor PAP records exist'],
  },
  {
    rule: 'Bone Mineral Density', kind: 'reminder', message: 'Need Bone Mineral Density Test',
    sex: null, minAge: 65, maxAge: null,
    documented: ['BMD (65+)', 'Single rule: REMINDER if no BMD test on record (ever)'],
  },
  {
    // The rule's comment calls it a WARNING while its consequence adds a reminder, so the documentation does not settle
    // which box it belongs in (app-findings-log row 321). Only the documented age range is asserted: kind is null.
    rule: 'Smoking', kind: null, message: 'Need to ask about Smoking',
    sex: null, minAge: 13, maxAge: null,
    documented: ['Smoking assessment (13+)', 'Screen all patients 13+ for tobacco use'],
  },
  {
    rule: 'Inf 3', kind: 'warning', message: 'Needs Flu Shot',
    sex: null, minAge: 65, maxAge: null,
    documented: ['Inf (annual 65+)', 'Inf 3: Never had a flu shot'],
  },
]);

/**
 * The ages either side of every documented boundary: the last day of the age below it and the first day of it.
 * `years` is the completed age; `lastDay` means the birthday that ends this age is tomorrow.
 */
function boundaryAges(rules = AGE_RULES) {
  const firsts = new Set();
  for (const rule of rules) {
    firsts.add(rule.minAge);
    if (rule.maxAge !== null) firsts.add(rule.maxAge + 1);
  }
  const points = [];
  for (const first of [...firsts].sort((a, b) => a - b)) {
    points.push({ years: first - 1, lastDay: true }, { years: first, lastDay: false });
  }
  return points;
}

/** Whether the documentation says the rule applies to a patient of this sex and completed age with no history. */
function documentedToFire(rule, sex, years) {
  return (rule.sex === null || rule.sex === sex) && years >= rule.minAge && (rule.maxAge === null || years <= rule.maxAge);
}

/** A row label for reports: "F 39 (turns 40 tomorrow)" or "F 40 (40th birthday today)". */
function describeAge(sex, point) {
  return point.lastDay ? `${sex} ${point.years} (turns ${point.years + 1} tomorrow)` : `${sex} ${point.years} (birthday today)`;
}

/** SQL for the birth date giving `point` on `today` (a yyyy-mm-dd literal from the application, not the database). */
function birthDateSql(today, point) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(today)) throw new Error('today must be a yyyy-mm-dd date');
  if (!Number.isInteger(point.years) || point.years < 0) throw new Error('years must be a whole number');
  return point.lastDay
    ? `DATE_ADD(DATE_SUB('${today}', INTERVAL ${point.years + 1} YEAR), INTERVAL 1 DAY)`
    : `DATE_SUB('${today}', INTERVAL ${point.years} YEAR)`;
}

module.exports = { AGE_RULES, RULE_FILE, birthDateSql, boundaryAges, describeAge, documentedToFire };
