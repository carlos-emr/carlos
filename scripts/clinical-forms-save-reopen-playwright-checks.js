#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 encounter forms. User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸
// Forms menu ▸ <form> ▸ fill ▸ Save (form/formname, or the form's own route) ▸ reopen from the
// E-Chart's saved-forms entry (form/forwardshortcutname?formId=latest) ▸ Print where the form
// prints server-side. Table-driven over Ontario encounter forms no other check saves (Annual
// Health Review, Mental Health Form 1, Discharge Summary, Palliative Care, Peri-Menopausal,
// MMSE, Vascular Tracker): each form's prose cell carries clinical punctuation, a structured
// field is set, the save is asserted in the form's own table (one owned row, exact values, the
// signed-in provider), on the redisplayed page, after reopening, and in the print. Every form's
// result is recorded and the check fails at the end, so one broken form never hides the rest.
// Fixtures: the owned synthetic patient and marker-named Forms-menu registrations of each form
// (the shipped rows are hidden on Ontario installs and are clinic-wide, so run EXCLUSIVE=1);
// cleanup deletes every form row of the owned patient and the registrations, and asserts both.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const NAME = 'clinical-forms-save-reopen';
const PROSE = "BP 120/80 & HR 72; pt's c/o SOB";
const DATE = { value: '2026/09/30', stored: '2026-09-30' };
const check = name => ({ name, check: true, stored: '1' });
const date = name => ({ name, value: DATE.value, stored: DATE.stored });

// key: menu suffix; path/query: the shipped form_value; idColumn: the table's key column;
// provider: the table records the saving provider; confirm: Save asks before posting;
// print: 'page' (a server-rendered print popup) or 'pdf' (a POST answered with a PDF).
const FORMS = [
  { key: 'ANN', title: 'Annual Health Review', path: '../form/formannual.jsp', table: 'formAnnual',
    idColumn: 'ID', provider: true, confirm: true, prose: 'currentConcerns',
    fields: [check('headN'), date('formDate')], print: { button: 'Print Page', kind: 'page' } },
  { key: 'MH1', title: 'Mental Health Form 1', path: '../form/formMentalHealthForm1.jsp',
    table: 'formMentalHealthForm1', idColumn: 'id', provider: false, confirm: false, prose: 'observation',
    fields: [check('threatened')], print: { button: 'Print Pdf', kind: 'pdf' } },
  { key: 'DS', title: 'Discharge Summary', path: '../form/formDischargeSummary.jsp', table: 'formDischargeSummary',
    idColumn: 'id', provider: true, confirm: true, prose: 'briefSummary', fields: [date('dischargeDate')] },
  { key: 'PC', title: 'Palliative Care', path: '../form/formpalliativecare.jsp', table: 'formPalliativeCare',
    idColumn: 'ID', provider: true, confirm: true, prose: 'pain1', fields: [date('date1')] },
  { key: 'PERI', title: 'Peri-Menopausal', path: '../form/formperimenopausal.jsp', table: 'formPeriMenopausal',
    idColumn: 'ID', provider: true, confirm: true, prose: 'orf_comments', fields: [check('orf_emYes')] },
  { key: 'MMSE', title: 'Mini-Mental State Examination', path: '../form/formmmse.jsp', table: 'formMMSE',
    idColumn: 'ID', provider: true, confirm: true, prose: 'diagnosis',
    fields: [check('lc_alert'), { name: 'o_date', value: '5', stored: '5' }] },
  // The shipped registration still points at the Struts 1 SetupForm.do route; the form saves
  // through form/SubmitForm into formVTForm. Its fields are reached only once the form opens.
  { key: 'VT', title: 'Vascular Tracker', path: '../form/SetupForm.do', query: 'formName=VTForm&',
    table: 'formVTForm', idColumn: 'ID', provider: true, confirm: false, prose: null, fields: [] },
];

const labels = form => [`form-${form.key}`, `reopen-${form.key}`, `print-${form.key}`];

async function fieldValue(page, field) {
  const input = page.locator(`[name="${field.name}"]`).first();
  return field.check ? (await input.isChecked() ? '1' : '0') : input.inputValue();
}

async function assertShown(page, form, text, where) {
  if (form.prose) {
    h.assert(await page.locator(`[name="${form.prose}"]`).first().inputValue() === text,
      `${where} does not show the saved prose`);
  }
  for (const field of form.fields) {
    const shown = await fieldValue(page, field);
    h.assert(shown === (field.check ? '1' : field.value), `${where} does not show the saved ${field.name}`);
  }
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const results = [];
  const registrations = FORMS.map(form => ({
    form, name: `${marker} ${form.key}`, value: `${form.path}?fixture=${marker}&${form.query || ''}demographic_no=`,
  }));
  s.cleanup(() => {
    sql.execute(FORMS.map(form => `DELETE FROM ${form.table} WHERE demographic_no=${patient}`).join(';'));
    h.assert(sql.value(`SELECT ${FORMS.map(form => `(SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient})`)
      .join('+')}`) === '0', 'Form rows of the owned patient were not removed');
  });
  s.cleanup(() => {
    for (const { name, value } of registrations) {
      sql.execute(`DELETE FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${h.sqlString(`%fixture=${marker}&%`)}`) === '0',
      'The owned Forms-menu registrations were not removed');
  });
  // The shipped registrations are hidden on Ontario installs and enabling one would edit a demo
  // row, so the run registers its own marker-named entry pointing at the shipped form_value.
  for (const { form, name, value } of registrations) {
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${h.sqlString(value)},${h.sqlString(name)},${h.sqlString(form.table)},COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  }
  const chart = await s.chart();

  await s.step('the E-Chart Forms menu offers every registered form for the owned patient', async () => {
    await chart.locator('#menuTitle1 a').hover();
    for (const { name } of registrations) {
      h.assert(await chart.getByRole('link', { name, exact: true }).count() === 1, 'A registered form is missing from the Forms menu');
    }
    await chart.mouse.move(0, 0);
  });

  async function attempt(entry, label, body) {
    if (entry.failure) return;
    try {
      await body();
      console.log(`  PASS ${NAME}: ${entry.form.title}: ${label}`);
    } catch (error) {
      entry.failure = `${label} -- ${error.message.split('\n')[0]}`;
      console.log(`  FAIL ${NAME}: ${entry.form.title}: ${entry.failure}`);
    }
  }

  for (const registration of registrations) {
    const { form, name } = registration;
    const entry = { form, name, text: `${marker} ${PROSE}` };
    results.push(entry);
    let page;
    await attempt(entry, 'opens from the Forms menu for the owned patient', async () => {
      await chart.locator('#menuTitle1 a').hover();
      page = await s.popup(chart, chart.getByRole('link', { name, exact: true }), `form-${form.key}`);
      h.assert(new URL(page.url()).searchParams.get('demographic_no') === patient, 'The form opened for another patient');
      if (form.prose) await page.locator(`[name="${form.prose}"]`).first().waitFor({ state: 'visible' });
    });

    await attempt(entry, 'Save stores exactly the typed prose and structured fields for the signed-in provider', async () => {
      await page.locator(`[name="${form.prose}"]`).first().fill(entry.text);
      for (const field of form.fields) {
        const input = page.locator(`[name="${field.name}"]`).first();
        if (field.check) await input.check(); else await input.fill(field.value);
      }
      const posted = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/formname'));
      const landed = page.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
      const seen = await h.withExpectedDialogs(page, () => page.getByRole('button', { name: 'Save', exact: true }).first().click());
      h.assert(seen.length === (form.confirm ? 1 : 0), form.confirm ? 'Save did not ask for confirmation exactly once' : 'Save raised a dialog');
      h.assert((await posted).status() === 302, 'The save was not accepted');
      await landed;
      const columns = [form.idColumn, form.prose, ...form.fields.map(field => field.name), form.provider ? 'provider_no' : "''"];
      const query = `SELECT ${columns.join(',')} FROM ${form.table} WHERE demographic_no=${patient}`;
      await expectValue(sql, `SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`, '1', 'Save did not store exactly one row');
      const [row] = sql.rows(query);
      h.assert(row[1] === entry.text, 'The stored prose differs from what was typed');
      form.fields.forEach((field, i) => h.assert(row[2 + i] === field.stored, `The stored ${field.name} differs from what was set`));
      if (form.provider) h.assert(row[row.length - 1] === provider, 'The row is not attributed to the signed-in provider');
      entry.id = row[0];
      h.assert(new URL(page.url()).searchParams.get('formId') === entry.id, 'Save redisplayed another record');
    });

    await attempt(entry, 'the redisplayed form shows the saved values', () => assertShown(page, form, entry.text, 'The redisplayed form'));

    if (form.print) {
      await attempt(entry, form.print.kind === 'pdf' ? 'Print Pdf answers a PDF of the saved form'
        : 'Print Page renders the saved form for the owned patient', async () => {
        const button = page.getByRole('button', { name: form.print.button, exact: true }).first();
        if (form.print.kind === 'page') {
          const print = await s.popup(page, button, `print-${form.key}`);
          h.assert(new URL(print.url()).searchParams.get('demographic_no') === patient, 'Print opened another patient');
          h.assert((await print.locator('body').innerText()).includes(entry.text), 'Print does not show the saved prose');
          await print.close();
          return;
        }
        let body;
        await page.route('**/form/formname?*', async route => {
          const answer = await route.fetch();
          body = await answer.body();
          await route.fulfill({ response: answer, body });
        });
        const answered = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/formname'));
        await button.click({ noWaitAfter: true });
        const response = await answered;
        await page.unroute('**/form/formname?*');
        h.assert(response.status() === 200 && /application\/pdf/.test(response.headers()['content-type'] || ''),
          'Print did not answer a PDF');
        h.assert(body && body.subarray(0, 4).toString() === '%PDF', 'Print answered something other than a PDF document');
      });
    }
    if (page && !page.isClosed()) await page.close();
  }

  // Reopen from a fresh E-Chart, whose navbar lists each saved form as a forwardshortcutname entry.
  // A new page rather than chart.reload(): leaving the encounter fires an unload beacon that the
  // strict recorder reports as an aborted request.
  const fresh = await s.context.newPage();
  h.wireStrictPage(fresh, 'reopen-chart', s.recorder);
  await fresh.goto(chart.url(), { waitUntil: 'domcontentloaded' });
  await waitForNavbars(fresh, 20000);
  for (const entry of results) {
    if (entry.failure) continue;
    const { form } = entry;
    await attempt(entry, 'reopening from the E-Chart restores the saved values', async () => {
      const link = fresh.locator(`#leftNavBar a[onclick*="formname=${entry.name}&"], #rightNavBar a[onclick*="formname=${entry.name}&"]`).first();
      await link.waitFor({ state: 'attached' });
      const page = await ui.clickOpensPopup(fresh, link, { context: s.context, recorder: s.recorder,
        label: `reopen-${form.key}`, timeout: 20000, position: { x: 8, y: 9 } });
      const params = new URL(page.url()).searchParams;
      h.assert(params.get('demographic_no') === patient, 'The saved-form entry opened another patient');
      const latest = sql.value(`SELECT MAX(${form.idColumn}) FROM ${form.table} WHERE demographic_no=${patient}`);
      h.assert(params.get('formId') === latest, 'The saved-form entry did not open the latest record');
      await assertShown(page, form, entry.text, 'The reopened form');
      await page.close();
    });
  }
  for (const entry of results) {
    await attempt(entry, 'raised no JavaScript-layer problems', async () => h.assertStrictPage(s.recorder, labels(entry.form)));
  }
  await fresh.close();

  const failed = results.filter(entry => entry.failure);
  h.assert(!failed.length, `${failed.length} of ${results.length} forms failed: `
    + failed.map(entry => `${entry.form.title}: ${entry.failure}`).join(' | '));
}

if (require.main === module) runWorkflow(NAME, workflow);
module.exports = { workflow, FORMS };
