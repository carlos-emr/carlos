#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Finding 231 (#4466), the four forms the form catalogue cannot drive: 2 Minute Walk, Internet Access, Rh Immune
// Globulin and Satisfaction Scale. They have TINYINT checkbox columns but are not in the Forms menu of an Ontario
// install, so form-catalog-smoke-checkbox-restore does not reach them; their record classes made the same mistake as the
// thirteen it does reach (a column type compared with TINY, which the MySQL driver never reports), and a saved tick
// came back as the raw value 1 written into the tag as 1="" and an unticked box.
//
// User path: a saved record's form opened for the owned patient (form/<form>?demographic_no=&formId=). Save stores
// ticked boxes correctly (the catalogue proves that for the forms it reaches, and FrmRecordCheckboxRoundTripIntegrationTest
// for the real tables), so the row is seeded here and the check is the read side only: the boxes the row holds as 1 are
// ticked, the boxes it holds as 0 or NULL are not, and no checkbox carries a stray attribute named by a digit.
//
// Fixtures: the owned synthetic patient and one seeded row per form. Nothing is added to the Forms menu, so this check
// does not need EXCLUSIVE=1. Cleanup deletes the patient's rows from the four tables and asserts each gone.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const NAME = 'form-checkbox-reopen';

/*
 * view      the form's route, form/<view>
 * table     the form's table
 * ticked    checkbox columns the seeded row holds as 1
 * unticked  checkbox columns the seeded row holds as 0
 * untouched checkbox columns left out of the INSERT, so they are NULL (a box the clinician never touched)
 */
const FORMS = [
  { title: '2 Minute Walk', view: 'form2minwalk', table: 'form2MinWalk',
    ticked: ['Q1tried', 'Q1SideBySide10'], unticked: ['Q1FullTandem3To9'], untouched: ['Q1FullTandem10'] },
  { title: 'Internet Access', view: 'formInternetAccess', table: 'formInternetAccess',
    ticked: ['computerY', 'internetN'], unticked: ['computerN'], untouched: ['internetY'] },
  { title: 'Rh Immune Globulin', view: 'formRhImmuneGlobulin', table: 'formRhImmuneGlobulin',
    ticked: ['obsHisTubMolPregYes', 'obsHisMisAbortionNo'], unticked: ['obsHisTubMolPregNo'], untouched: ['obsHisMisAbortionYes'] },
  { title: 'Satisfaction Scale', view: 'formSatisfactionScale', table: 'formSatisfactionScale',
    ticked: ['believe1Y', 'receive2N'], unticked: ['believe1N'], untouched: ['receive2Y'] },
];

/** The checkbox <input> for a column, or fail: a renamed control would otherwise pass as "not ticked". */
async function checkbox(page, form, column) {
  const box = page.locator(`input[type="checkbox"][name="${column}"]`);
  const found = await box.count();
  h.assert(found === 1, `${form.title}: the page has ${found} checkboxes named ${column}, not one`);
  return box;
}

async function workflow(s) {
  const { sql, patient, provider, context } = s;
  h.assert(/^[1-9]\d*$/.test(patient), 'The owned synthetic patient was not created');

  for (const form of FORMS) {
    s.cleanup(() => {
      sql.execute(`DELETE FROM ${form.table} WHERE demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '0',
        `The owned patient's ${form.table} rows were not removed`);
    });
  }

  for (const form of FORMS) {
    await s.step(`${form.title}: the reopened form shows the saved ticks, and only those`, async () => {
      const columns = [...form.ticked, ...form.unticked];
      const values = [...form.ticked.map(() => 1), ...form.unticked.map(() => 0)];
      sql.execute(`INSERT INTO ${form.table} (demographic_no,provider_no,formCreated,${columns.join(',')})
        VALUES (${patient},${provider},CURDATE(),${values.join(',')})`);
      const id = sql.value(`SELECT MAX(ID) FROM ${form.table} WHERE demographic_no=${patient}`);
      h.assert(/^[1-9]\d*$/.test(id), `${form.title}: the seeded record was not stored`);
      const stored = sql.rows(`SELECT ${form.ticked.join(',')} FROM ${form.table} WHERE ID=${id}`)[0];
      h.assert(stored.every(value => value === '1'), `${form.title}: the seeded ticks are not stored as 1`);

      const page = await context.newPage();
      try {
        const response = await h.gotoApp(page, s.config.baseUrl,
          `/form/${form.view}?demographic_no=${patient}&formId=${id}`);
        h.assert(response && response.status() === 200, `${form.title}: the saved record answered HTTP ${response && response.status()}`);
        await h.assertNotErrorPage(page, `${form.title} reopened`);
        for (const column of form.ticked) {
          h.assert(await (await checkbox(page, form, column)).isChecked(), `${form.title}: ${column} was saved ticked and is shown unticked`);
        }
        for (const column of [...form.unticked, ...form.untouched]) {
          h.assert(!(await (await checkbox(page, form, column)).isChecked()), `${form.title}: ${column} was saved unticked and is shown ticked`);
        }
        // The defect wrote the raw column value into the tag, which the browser keeps as an attribute named 1.
        const stray = await page.evaluate(() => [...document.querySelectorAll('input[type="checkbox"]')]
          .flatMap(box => [...box.attributes].map(attribute => attribute.name).filter(name => /^\d/.test(name)).map(name => `${box.name}:${name}`)));
        h.assert(stray.length === 0, `${form.title}: checkboxes carry a stray attribute named by a digit (${stray.slice(0, 3).join(', ')})`);
      } finally {
        await page.close();
      }
    });
  }
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow, FORMS };
