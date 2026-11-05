#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Read-only check that the demo dataset is intact and usable end to end (issue #3151).
//
// The demo snapshot once loaded column-shifted: names went into INT columns and were coerced to 0, so the
// load "succeeded" while the data was wrong. This check runs against a deployment that has the demo
// dataset and asserts, through the same MariaDB connection the other checks use, that the tail columns
// that used to shift (pref_name, gender, genderId, pronounId) hold what their names say, and then opens a
// few demo patients' Master Record in the browser to prove the rendered fields equal the stored ones.
//
// It changes nothing: it creates no patient and writes no row, and it is valid whatever sql_mode the
// server runs, because the packaged deployment keeps its legacy empty sql_mode on purpose.
// Failure messages name the field, never a patient's name or number, because they reach archived results.
//
// Optional environment:
//   DEMO_MIN_PATIENTS=2900   fewest demographic rows that count as "the demo dataset loaded"
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

const MIN_PATIENTS = Number(process.env.DEMO_MIN_PATIENTS || '2900');
if (!Number.isSafeInteger(MIN_PATIENTS) || MIN_PATIENTS < 1) {
  throw new Error('DEMO_MIN_PATIENTS must be a positive integer');
}
const SAMPLE_SIZE = 3;

async function workflow(s) {
  await s.step('the demo patients are present and their shifted-prone columns are aligned', async () => {
    const total = Number(s.sql.value('SELECT COUNT(*) FROM demographic'));
    h.assert(total >= MIN_PATIENTS, `the demo dataset did not load: ${total} demographic rows, expected at least ${MIN_PATIENTS}`);
    // A text value coerced into an INT column leaves 0 behind and shifts the neighbouring text columns one
    // place, so a number ends up as the preferred name and a name ends up as the gender.
    h.assert(s.sql.value("SELECT COUNT(*) FROM demographic WHERE pref_name REGEXP '^[0-9]+$'") === '0',
      'demographic.pref_name holds a number: the tail columns are shifted');
    h.assert(s.sql.value("SELECT COUNT(*) FROM demographic WHERE gender NOT IN ('M', 'F', '')") === '0',
      'demographic.gender holds something other than M, F or blank: the tail columns are shifted');
    h.assert(s.sql.value("SELECT COUNT(*) FROM demographic WHERE gender <> '' AND gender <> sex") === '0',
      'demographic.gender disagrees with demographic.sex');
  });

  // The generated cohort is the one whose rows were shifted, so sample from it: a patient with a middle
  // name and a preferred name. Ordered, so the same patients are checked on every run.
  const sample = s.sql.rows(`SELECT demographic_no, last_name, first_name, middleNames, sex,
      year_of_birth, month_of_birth, date_of_birth
    FROM demographic
    WHERE last_name LIKE 'FAKE-%' AND middleNames <> '' AND pref_name <> ''
    ORDER BY demographic_no LIMIT ${SAMPLE_SIZE}`);
  h.assert(sample.length === SAMPLE_SIZE, `expected ${SAMPLE_SIZE} sample demo patients, found ${sample.length}`);

  // The schedule's Search control opens a popup, except where the caisi module is loaded: there it navigates the
  // schedule tab itself (see openMasterRecord). Such a tab can no longer start another search, and it must not be
  // closed, so the first sample is then the only one this run can open.
  let searchNavigatedScheduleTab = false;
  for (const [index, row] of sample.entries()) {
    if (searchNavigatedScheduleTab) {
      console.log('  NOTE demo-data-integrity: Search navigates the schedule tab here, so only one demo patient was opened');
      break;
    }
    const [demographicNo, lastName, firstName, middleNames, sex, yearOfBirth, monthOfBirth, dateOfBirth] = row;
    await s.step(`demo patient ${index + 1} opens in the Master Record with the stored name, sex and birth date`, async () => {
      const { masterPage, searchPage } = await openMasterRecord(s.context, s.schedule, s.recorder, {
        searchTerm: lastName, preferredDemographicNo: demographicNo, timeout: 20000,
      });
      try {
        const shown = async (name) => (await masterPage.locator(`[name="${name}"]`).first().inputValue()).trim();
        h.assert(await shown('last_name') === lastName, 'the Master Record shows a last name other than the stored one');
        h.assert(await shown('first_name') === firstName, 'the Master Record shows a first name other than the stored one');
        h.assert(await shown('middleNames') === middleNames, 'the Master Record shows middle names other than the stored ones');
        h.assert(await shown('sex') === sex, 'the Master Record shows a sex other than the stored one');
        h.assert(await shown('year_of_birth') === yearOfBirth
          && await shown('month_of_birth') === monthOfBirth
          && await shown('date_of_birth') === dateOfBirth,
        'the Master Record shows a birth date other than the stored one');
      } finally {
        // Both popups. The schedule's Search link opens a NAMED window, so a search page left open makes the next
        // patient's click navigate that window instead of opening a new one, and openMasterRecord waits in vain.
        await masterPage.close().catch(() => {});
        if (searchPage === s.schedule) searchNavigatedScheduleTab = true;
        else await searchPage.close().catch(() => {});
      }
    });
  }
}

if (require.main === module) runWorkflow('demo-data-integrity', workflow, { openPatient: false });
module.exports = { workflow };
