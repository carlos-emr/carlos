#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #5693, the 2004 rehabilitation-study questionnaires after their Frm*Record classes were replaced by one
// JPA entity per table and RehabStudyFormService. form-catalog-smoke drives fifteen of the eighteen through the
// Forms menu, and form-checkbox-reopen reads seeded rows of the other three; this check drives what neither does.
//
//   save        Two Minute Walk, Internet Access and Satisfaction Scale (not in an Ontario Forms menu) are opened
//               for the owned patient (form/<view>?demographic_no=&formId=0); ticked boxes and typed answers are
//               saved through form/formname: one new row holding exactly those values, the signed-in provider and
//               the "N/A" study ID; the redisplayed form shows them again.
//   study ID    a patient enrolled in rehabStudy2004 opens a new SF-36 that shows the study ID, and Save stores it.
//   versions    Save on a reopened SF-36 adds a version and leaves the version it was opened from unchanged.
//   patient     a saved version requested under another patient's number is refused with HTTP 403 and shows none
//               of its values.
//
// Fixtures: the owned synthetic patient, one rehabStudy2004 enrolment of that patient, and the rows Save stores.
// Nothing is added to the Forms menu, so the check does not need EXCLUSIVE=1. Cleanup deletes the owned patient's
// rows from the four questionnaire tables and the enrolment, and asserts each gone.
const crypto = require('crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NAME = 'rehab-study-forms';

/*
 * view   the form's route, form/<view>
 * table  the form's table
 * ticks  checkbox columns ticked before Save; others  checkbox columns left unticked
 * typed  text answers typed before Save: { name, value }
 */
const FORMS = [
  { title: 'Two Minute Walk', view: 'form2minwalk', table: 'form2MinWalk', ticks: ['Q1tried'],
    others: ['Q1SideBySide10'], typed: [{ name: 'distance', value: '12.5' }, { name: 'Q1Cmt', value: 'Walked A&B 1/2 lap' }] },
  { title: 'Internet Access', view: 'formInternetAccess', table: 'formInternetAccess', ticks: ['computerY'],
    others: ['computerN'], typed: [{ name: 'internetOtherTx', value: 'FAKE library 1/2' }] },
  { title: 'Satisfaction Scale', view: 'formSatisfactionScale', table: 'formSatisfactionScale', ticks: ['believe1Y'],
    others: ['believe1N'], typed: [{ name: 'otTreats4', value: '12' }] },
];
const SF36 = { title: 'SF-36', view: 'formSF36', table: 'formSF36' };
const TABLES = [...FORMS.map(form => form.table), SF36.table];

function isFormPost(response) {
  return response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/form/formname');
}

/** Presses Save, accepting the form's confirmations, and requires the redirect form/formname answers. */
async function save(s, page, label) {
  const posted = s.context.waitForEvent('response', { predicate: isFormPost, timeout: 20000 });
  posted.catch(() => {});
  const dialogs = await h.withExpectedDialogs(page,
    () => page.locator('input[type="submit"][value="Save"]').first().click());
  const response = await posted.catch(() => null);
  h.assert(response, `${label}: Save posted nothing to form/formname (dialogs: ${dialogs.map(d => d.text.slice(0, 60)).join('; ') || 'none'})`);
  h.assert(response.status() === 302, `${label}: the save was answered ${response.status()}, not accepted with a redirect`);
  await page.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
  await h.assertNotErrorPage(page, `${label}: the redisplayed form`);
  return new URL(page.url()).searchParams.get('formId');
}

async function open(s, page, view, patient, formId, label) {
  const response = await h.gotoApp(page, s.config.baseUrl, `/form/${view}?demographic_no=${patient}&formId=${formId}`);
  h.assert(response && response.status() === 200, `${label}: the form answered HTTP ${response && response.status()}`);
  await h.assertNotErrorPage(page, label);
}

async function workflow(s) {
  const { sql, patient, provider, context } = s;
  h.assert(/^[1-9]\d*$/.test(patient), 'The owned synthetic patient was not created');
  // A study ID no shipped data uses, so the enrolment row is the check's own.
  const studyId = String(900000000 + crypto.randomInt(0, 99999999));
  for (const table of TABLES) {
    s.cleanup(() => {
      sql.execute(`DELETE FROM ${table} WHERE demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM ${table} WHERE demographic_no=${patient}`) === '0',
        `The owned patient's ${table} rows were not removed`);
    });
  }
  s.cleanup(() => {
    sql.execute(`DELETE FROM rehabStudy2004 WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM rehabStudy2004 WHERE demographic_no=${patient}`) === '0',
      "The owned patient's study enrolment was not removed");
  });

  for (const form of FORMS) {
    await s.step(`${form.title}: Save stores the ticked boxes and typed answers once, and the redisplayed form shows them`, async () => {
      const page = await context.newPage();
      try {
        await open(s, page, form.view, patient, 0, `${form.title} (new)`);
        h.assert(await page.locator('input[name="studyID"]').inputValue() === 'N/A',
          `${form.title}: a patient who is not enrolled was not shown the N/A study ID`);
        for (const box of form.ticks) await page.locator(`input[type="checkbox"][name="${box}"]`).check();
        for (const field of form.typed) await page.locator(`input[name="${field.name}"]`).fill(field.value);
        const id = await save(s, page, form.title);
        h.assert(/^[1-9]\d*$/.test(String(id)), `${form.title}: the redisplayed form names no saved version`);
        await expectValue(sql, `SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`, '1',
          `${form.title}: Save did not store exactly one row`);
        const columns = ['ID', 'provider_no', 'studyID', ...form.ticks, ...form.others, ...form.typed.map(field => field.name)];
        const [row] = sql.rows(`SELECT ${columns.map(column => `\`${column}\``).join(',')} FROM ${form.table}
          WHERE demographic_no=${patient}`);
        const stored = Object.fromEntries(columns.map((column, index) => [column, row[index]]));
        h.assert(stored.ID === id, `${form.title}: the redisplayed form is not the stored version`);
        h.assert(stored.provider_no === provider, `${form.title}: the version is not attributed to the signed-in provider`);
        h.assert(stored.studyID === 'N/A', `${form.title}: the stored study ID is ${stored.studyID}, not N/A`);
        for (const box of form.ticks) h.assert(stored[box] === '1', `${form.title}: ${box} was ticked and is stored as ${stored[box]}`);
        for (const box of form.others) h.assert(stored[box] === '0', `${form.title}: ${box} was not ticked and is stored as ${stored[box]}`);
        for (const field of form.typed) {
          h.assert(stored[field.name] === field.value, `${form.title}: ${field.name} is stored as ${stored[field.name]}`);
        }
        for (const box of form.ticks) {
          h.assert(await page.locator(`input[type="checkbox"][name="${box}"]`).isChecked(), `${form.title}: the redisplayed ${box} is unticked`);
        }
        for (const box of form.others) {
          h.assert(!(await page.locator(`input[type="checkbox"][name="${box}"]`).isChecked()), `${form.title}: the redisplayed ${box} is ticked`);
        }
        for (const field of form.typed) {
          h.assert(await page.locator(`input[name="${field.name}"]`).inputValue() === field.value,
            `${form.title}: the redisplayed ${field.name} differs from what was saved`);
        }
      } finally {
        await page.close();
      }
    });
  }

  let firstId;
  await s.step('SF-36: an enrolled patient sees the study ID on a new form, and Save stores it', async () => {
    sql.execute(`INSERT INTO rehabStudy2004 (studyID, demographic_no) VALUES (${studyId}, ${patient})`);
    const page = await context.newPage();
    try {
      await open(s, page, SF36.view, patient, 0, 'SF-36 (new)');
      h.assert(await page.locator('input[name="studyID"]').inputValue() === studyId,
        'SF-36: the new form does not carry the patient\'s study ID');
      h.assert((await page.locator('body').innerText()).includes(studyId), 'SF-36: the new form does not show the study ID');
      await page.locator('input[type="checkbox"][name="Q1Ex"]').check();
      await page.locator('input[name="Q1Cmt"]').fill(`${s.marker} first version`);
      firstId = await save(s, page, 'SF-36');
      const [row] = sql.rows(`SELECT studyID, Q1Ex, Q1Cmt FROM formSF36 WHERE ID=${firstId} AND demographic_no=${patient}`);
      h.assert(row && row[0] === studyId, 'SF-36: Save did not store the study ID');
      h.assert(row[1] === '1' && row[2] === `${s.marker} first version`, 'SF-36: Save did not store the answers');
    } finally {
      await page.close();
    }
  });

  await s.step('SF-36: Save on a reopened version adds a version and leaves the original unchanged', async () => {
    const page = await context.newPage();
    try {
      await open(s, page, SF36.view, patient, firstId, 'SF-36 (reopened)');
      h.assert(await page.locator('input[type="checkbox"][name="Q1Ex"]').isChecked(), 'SF-36: the reopened version lost its tick');
      h.assert(await page.locator('input[name="Q1Cmt"]').inputValue() === `${s.marker} first version`,
        'SF-36: the reopened version does not show its answer');
      await page.locator('input[name="Q1Cmt"]').fill(`${s.marker} second version`);
      const secondId = await save(s, page, 'SF-36 (reopened)');
      h.assert(Number(secondId) > Number(firstId), 'SF-36: saving the reopened version did not create a later version');
      await expectValue(sql, `SELECT COUNT(*) FROM formSF36 WHERE demographic_no=${patient}`, '2',
        'SF-36: the reopened save did not add exactly one version');
      h.assert(sql.value(`SELECT Q1Cmt FROM formSF36 WHERE ID=${firstId}`) === `${s.marker} first version`,
        'SF-36: saving the reopened version changed the version it was opened from');
      h.assert(sql.value(`SELECT studyID FROM formSF36 WHERE ID=${secondId}`) === studyId,
        'SF-36: the new version did not keep the study ID');
    } finally {
      await page.close();
    }
  });

  await s.step("SF-36: a version requested under another patient's number is refused and shows none of its values", async () => {
    const other = sql.value(`SELECT MIN(demographic_no) FROM demographic WHERE demographic_no<>${patient}
      AND patient_status='AC'`);
    h.assert(/^[1-9]\d*$/.test(String(other)), 'No other active patient exists to request the version under');
    const where = `demographic_no IN (${patient}, ${other})`;
    const before = sql.value(`SELECT COUNT(*) FROM formSF36 WHERE ${where}`);
    const query = `demographic_no=${other}&formId=${firstId}`;
    for (const path of [
      `/form/${SF36.view}?${query}`,
      `/form/forwardname?form_link=${SF36.view}.jsp&${query}`,
    ]) {
      const route = path.split('?')[0];
      const response = await context.request.get(h.appUrl(s.config.baseUrl, path),
        { maxRedirects: 0, failOnStatusCode: false });
      const body = await response.text();
      h.assert(response.status() === 403, `SF-36: ${route} returned ${response.status()} for a foreign version (expected 403)`);
      h.assert(!body.includes(s.marker) && !/name="Q1Ex"[^>]*checked/.test(body),
        `SF-36: ${route} showed a saved answer to another patient`);
      await h.assertRefused(s, { response: { status: response.status(), headers: response.headers(), body },
        table: SF36.table, where, before, label: `the cross-patient request to ${route}` });
    }
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow, FORMS };
