#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 encounter forms. User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸
// Forms menu ▸ <form> ▸ fill ▸ Save (form/formname, or the form's own route) ▸ reopen from the
// E-Chart's saved-forms entry (form/forwardshortcutname?formId=latest) ▸ Print where the form
// prints server-side. Table-driven over Ontario encounter forms no other check saves (Annual
// Health Review, Mental Health Form 1, Discharge Summary, Palliative Care, Peri-Menopausal,
// MMSE, Vascular Tracker): each form's prose cell carries clinical punctuation, a structured
// field is set, the save is asserted in the form's own table (one owned row, exact values, the
// signed-in provider), on the redisplayed page, after reopening, and in the print; Palliative Care
// is also saved a second time from the redisplayed window. Every form's result is recorded and
// the check fails at the end, so one broken form never hides the rest.
// Fixtures: the owned synthetic patient and marker-named Forms-menu registrations of each form
// (the shipped rows are hidden on Ontario installs and are clinic-wide, so run EXCLUSIVE=1);
// cleanup deletes every form row of the owned patient and the registrations, and asserts both.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { pdfText } = require('./form-print-pdf-playwright-checks');

const NAME = 'clinical-forms-save-reopen';
const PROSE = "BP 120/80 & HR 72; pt's c/o SOB";
const DATE = { value: '2026/09/30', stored: '2026-09-30' };
const check = name => ({ name, check: true, stored: '1' });
const date = name => ({ name, value: DATE.value, stored: DATE.stored });

// key: menu suffix; path/query: the shipped form_value; idColumn: the table's key column;
// provider: the table records the saving provider; confirm: Save asks before posting;
// print: 'page' (a server-rendered print popup) or 'pdf' (a POST answered with a PDF), pressed
// on the reopened form; resave: Save again from the window the first Save redisplayed.
const FORMS = [
  { key: 'ANN', title: 'Annual Health Review', path: '../form/formannual.jsp', table: 'formAnnual',
    idColumn: 'ID', provider: true, confirm: true, prose: 'currentConcerns',
    fields: [check('headN'), date('formDate')], print: { button: 'Print Page', kind: 'page' } },
  { key: 'MH1', title: 'Mental Health Form 1', path: '../form/formMentalHealthForm1.jsp',
    table: 'formMentalHealthForm1', idColumn: 'id', provider: false, confirm: false, prose: 'observation',
    fields: [check('threatened'), { name: 'onDate', value: '2026/09/30', stored: '2026/09/30' },
      { name: 'todayDate', value: '2026-10-01', stored: '2026-10-01' }], print: { button: 'Print Pdf', kind: 'pdf' } },
  { key: 'DS', title: 'Discharge Summary', path: '../form/formDischargeSummary.jsp', table: 'formDischargeSummary',
    idColumn: 'id', provider: true, confirm: true, prose: 'briefSummary', fields: [date('dischargeDate')] },
  { key: 'PC', title: 'Palliative Care', path: '../form/formpalliativecare.jsp', table: 'formPalliativeCare',
    idColumn: 'ID', provider: true, confirm: true, prose: 'pain1', fields: [date('date1')], resave: true },
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

async function revealSavedForm(page, link) {
  // Initial folding omits entries; collapsing an already-loaded list hides its li.
  // The image owns the click handler in both cases, and its URL is locale-independent.
  if (!await link.isVisible()) await page.locator('#forms img[src$="/expand.gif"]:visible').click();
  await link.waitFor({ state: 'visible' });
}

async function workflow(s, { forms = FORMS, foldSavedForms = false } = {}) {
  const { sql, patient, provider, marker } = s;
  const results = [];
  const registrations = forms.map(form => ({
    form, name: `${marker} ${form.key}`, value: `${form.path}?fixture=${marker}&${form.query || ''}demographic_no=`,
  }));
  // Extra owned aliases put the selected saved form beyond the navbar's first six
  // entries without creating additional patient records or changing clinic settings.
  if (foldSavedForms) {
    const form = forms[0];
    for (let i = 0; i < 8; i++) registrations.unshift({
      form, name: `BC ${marker} ${i}`, fixtureOnly: true,
      value: `${form.path}?fixture=${marker}&fold=${i}&demographic_no=`,
    });
  }
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
    h.assert(name.length <= 30, 'The owned registration name exceeds encounterForm.form_name');
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${h.sqlString(value)},${h.sqlString(name)},${h.sqlString(form.table)},COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  }
  const chart = await s.chart();

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

  for (const registration of registrations.filter(item => !item.fixtureOnly)) {
    const { form, name } = registration;
    const entry = { form, name, text: `${marker} ${PROSE}${form.key === 'ANN' ? ' <follow-up>' : ''}` };
    results.push(entry);
    // Each form's menu entry is asserted in its own attempt, so one missing registration is
    // recorded without stopping the remaining forms.
    await attempt(entry, 'is listed once in the Forms menu and opens for the owned patient', async () => {
      await chart.locator('#menuTitle1 a').hover();
      const link = chart.getByRole('link', { name, exact: true });
      h.assert(await link.count() === 1, 'The Forms menu does not list the registered form exactly once');
      entry.page = await s.popup(chart, link, `form-${form.key}`);
      h.assert(new URL(entry.page.url()).searchParams.get('demographic_no') === patient, 'The form opened for another patient');
      h.assert(form.prose, 'The form opened; add its prose and structured fields to FORMS');
      await entry.page.locator(`[name="${form.prose}"]`).first().waitFor({ state: 'visible' });
    });

    if (form.key === 'MH1') {
      await attempt(entry, 'each real date is validated and cancelling Save and Exit preserves the unsaved form', async () => {
        const { page } = entry;
        const originalUrl = page.url();
        let posts = 0;
        const countPost = request => {
          if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/form/formname')) posts++;
        };
        page.on('request', countPost);
        try {
          await page.locator(`[name="${form.prose}"]`).fill(entry.text);
          const dates = form.fields.filter(field => !field.check);
          for (const field of dates) await page.locator(`[name="${field.name}"]`).fill(field.value);
          for (const field of dates) {
            const input = page.locator(`[name="${field.name}"]`);
            for (const invalidValue of ['2026/02/30', '2026//01', '2026/01/', '2026//', '2026--01']) {
              await input.fill(invalidValue);
              const invalid = await h.withExpectedDialogs(page,
                () => page.getByRole('button', { name: 'Save', exact: true }).first().click());
              h.assert(invalid.length === 1 && invalid[0].type === 'alert'
                && /valid date/i.test(invalid[0].text), 'An invalid date must show one useful validation alert');
              h.assert(await input.inputValue() === invalidValue, 'Validation discarded the typed date');
              h.assert(await input.evaluate(element => element === element.ownerDocument.activeElement),
                'Validation did not focus the invalid date');
              h.assert(page.url() === originalUrl && posts === 0, 'Invalid-date validation submitted the form');
              h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '0',
                'Invalid-date validation wrote a form record');
            }
            await input.fill(field.value);
          }
          const cancelled = await h.withExpectedDialogs(page,
            () => page.getByRole('button', { name: 'Save and Exit', exact: true }).first().click(), { accept: false });
          h.assert(cancelled.length === 1 && cancelled[0].type === 'confirm',
            'Save and Exit must ask for one cancellable confirmation');
          h.assert(page.url() === originalUrl && posts === 0 && !page.isClosed(),
            'Cancelling Save and Exit submitted or closed the form');
          h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '0',
            'Cancelling Save and Exit wrote a form record');
          for (const field of dates) h.assert(await fieldValue(page, field) === field.value,
            'Cancelling Save and Exit discarded a typed date');
          h.assert(await page.locator(`[name="${form.prose}"]`).inputValue() === entry.text,
            'Cancelling Save and Exit discarded the typed prose');
          h.assertStrictPage(s.recorder, labels(form));
        } finally {
          page.off('request', countPost);
        }
      });
    }

    await attempt(entry, 'Save stores exactly the typed prose and structured fields for the signed-in provider', async () => {
      const { page } = entry;
      await page.locator(`[name="${form.prose}"]`).first().fill(entry.text);
      for (const field of form.fields) {
        const input = page.locator(`[name="${field.name}"]`).first();
        if (field.check) await input.check(); else await input.fill(field.value);
      }
      const posted = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/formname'));
      const landed = page.waitForURL(url => url.pathname.endsWith('/form/forwardname'), { waitUntil: 'domcontentloaded' });
      // Observed below; a failure raised first must not surface later as an unhandled rejection.
      posted.catch(() => {});
      landed.catch(() => {});
      // The confirmation is asserted after the save has been proven, so a missing prompt does
      // not hide whether the form still saves.
      entry.dialogs = await h.withExpectedDialogs(page, () => page.getByRole('button', { name: 'Save', exact: true }).first().click());
      h.assert((await posted).status() === 302, 'The save was not accepted');
      await landed;
      const columns = [form.idColumn, form.prose, ...form.fields.map(field => field.name), form.provider ? 'provider_no' : "''"];
      await expectValue(sql, `SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`, '1', 'Save did not store exactly one row');
      const [row] = sql.rows(`SELECT ${columns.join(',')} FROM ${form.table} WHERE demographic_no=${patient}`);
      h.assert(row[1] === entry.text, 'The stored prose differs from what was typed');
      form.fields.forEach((field, i) => h.assert(row[2 + i] === field.stored, `The stored ${field.name} differs from what was set`));
      if (form.provider) h.assert(row[row.length - 1] === provider, 'The row is not attributed to the signed-in provider');
      entry.id = row[0];
      h.assert(new URL(page.url()).searchParams.get('formId') === entry.id, 'Save redisplayed another record');
    });

    await attempt(entry, 'the redisplayed form shows the saved values', () => assertShown(entry.page, form, entry.text, 'The redisplayed form'));
  }

  // Reopen from a fresh E-Chart, whose navbar lists each saved form as a forwardshortcutname entry.
  // A new page rather than chart.reload(): leaving the encounter fires an unload beacon that the
  // strict recorder reports as an aborted request.
  const fresh = await s.context.newPage();
  h.wireStrictPage(fresh, 'reopen-chart', s.recorder);
  await fresh.goto(chart.url(), { waitUntil: 'domcontentloaded' });
  await waitForNavbars(fresh, 20000);
  for (const entry of results) {
    const { form } = entry;
    let page;
    await attempt(entry, 'reopening from the E-Chart restores the saved values', async () => {
      const link = fresh.locator(`#leftNavBar a[onclick*="formname=${entry.name}&"], #rightNavBar a[onclick*="formname=${entry.name}&"]`).first();
      if (foldSavedForms) h.assert(!await link.isVisible(),
        'The folded-navigation fixture did not put the saved form beyond the first page');
      await revealSavedForm(fresh, link);
      page = await ui.clickOpensPopup(fresh, link, { context: s.context, recorder: s.recorder,
        label: `reopen-${form.key}`, timeout: 20000, position: { x: 8, y: 9 } });
      const params = new URL(page.url()).searchParams;
      h.assert(params.get('demographic_no') === patient, 'The saved-form entry opened another patient');
      h.assert(params.get('formId') === entry.id, 'The saved-form entry did not open the saved record');
      await assertShown(page, form, entry.text, 'The reopened form');
    });

    if (foldSavedForms) {
      await attempt(entry, 'a previously loaded and collapsed saved-form list expands and reopens the same record', async () => {
        await page.close();
        await fresh.locator('#forms img[src$="/collapse.gif"]:visible').first().click();
        const link = fresh.locator(`#forms a[onclick*="formname=${entry.name}&"]`).first();
        h.assert(await link.count() > 0 && !await link.isVisible(),
          'The cached-list fixture did not retain a hidden saved-form anchor');
        await revealSavedForm(fresh, link);
        page = await ui.clickOpensPopup(fresh, link, { context: s.context, recorder: s.recorder,
          label: `reopen-${form.key}`, timeout: 20000, position: { x: 8, y: 9 } });
        const params = new URL(page.url()).searchParams;
        h.assert(params.get('demographic_no') === patient && params.get('formId') === entry.id,
          'Expanding the cached list opened a different patient or saved record');
        await assertShown(page, form, entry.text, 'The reopened form from the cached list');
      });
    }

    if (form.print) {
      await attempt(entry, form.print.kind === 'pdf' ? 'Print Pdf answers a PDF carrying the saved prose'
        : 'Print Page renders the saved form for the owned patient', async () => {
        const button = page.getByRole('button', { name: form.print.button, exact: true }).first();
        if (form.print.kind === 'page') {
          const print = await s.popup(page, button, `print-${form.key}`);
          const printUrl = new URL(print.url());
          h.assert(printUrl.pathname.endsWith('/form/formannualfemaleprint'), 'Print did not use the gated form route');
          h.assert(printUrl.searchParams.get('demographic_no') === patient, 'Print opened another patient');
          h.assert(printUrl.searchParams.get('formId') === entry.id, 'Print opened another saved record');
          h.assert(await print.locator('[name="ID"]').inputValue() === entry.id, 'Print rendered another saved record');
          h.assert((await print.locator('body').innerText()).includes(DATE.value), 'Print omitted the saved review date');
          h.assert(await print.locator('follow-up').count() === 0, 'Print treated clinical prose as HTML');
          h.assert((await print.locator('body').innerText()).includes(entry.text), 'Print does not show the saved prose');
          await print.close();
          return;
        }
        // A PDF navigation hands Playwright the viewer's HTML, so the bytes are read in transit.
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
        h.assert(body.subarray(0, 4).toString() === '%PDF', 'Print answered something other than a PDF document');
        h.assert(pdfText(body).includes(marker), 'The PDF does not carry the saved prose');
      });
    }
    if (page && !page.isClosed()) await page.close();

    await attempt(entry, form.confirm ? 'Save asked for confirmation exactly once' : 'Save raised no dialog', async () => {
      h.assert(entry.dialogs.length === (form.confirm ? 1 : 0),
        form.confirm ? 'Save did not ask for confirmation exactly once' : 'Save raised a dialog');
    });

    if (form.resave) {
      // A clinician keeps working in the window Save redisplayed; its next Save must be accepted.
      // Encounter forms are versioned: every Save inserts a new row (FrmRecordHelp.saveFormRecord)
      // and the chart reopens formId=latest, so the revision is the second row, not an overwrite.
      await attempt(entry, 'a second Save from the redisplayed form is accepted and files the revision as the next version', async () => {
        h.assert(await entry.page.locator('script[src$="/csrfguard"]').count() === 1,
          'The redisplayed form must load CSRFGuard exactly once');
        await entry.page.waitForFunction(() => {
          const token = document.querySelector('form input[name="CSRF-TOKEN"]');
          return token && token.value.length > 0;
        });
        const revised = `${entry.text} (revised)`;
        await entry.page.locator(`[name="${form.prose}"]`).first().fill(revised);
        const posted = entry.page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/formname'));
        posted.catch(() => {});
        await h.withExpectedDialogs(entry.page, () => entry.page.getByRole('button', { name: 'Save', exact: true }).first().click());
        const response = await posted;
        h.assert(response.status() === 302, 'The second save was refused');
        await expectValue(sql, `SELECT ${form.prose} FROM ${form.table} WHERE demographic_no=${patient}
          ORDER BY ${form.idColumn} DESC LIMIT 1`, revised, 'The second save did not store the revision');
        h.assert(sql.value(`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`) === '2',
          'The second save did not file exactly one new version');
        h.assert(sql.value(`SELECT ${form.prose} FROM ${form.table} WHERE demographic_no=${patient}
          AND ${form.idColumn}=${entry.id}`) === entry.text, 'The second save altered the first saved version');
        const columns = [form.idColumn, ...form.fields.map(field => field.name), 'provider_no'];
        const [revision] = sql.rows(`SELECT ${columns.join(',')} FROM ${form.table}
          WHERE demographic_no=${patient} ORDER BY ${form.idColumn} DESC LIMIT 1`);
        const latest = revision[0];
        form.fields.forEach((field, i) => h.assert(revision[i + 1] === field.stored,
          `The revision did not preserve the stored ${field.name}`));
        h.assert(revision[revision.length - 1] === provider,
          'The revision is not attributed to the signed-in provider');
        h.assert(latest !== entry.id, 'The revision did not get its own record ID');
        await entry.page.waitForURL(url => url.pathname.endsWith('/form/forwardname')
          && url.searchParams.get('formId') === latest, { waitUntil: 'domcontentloaded' });
        await assertShown(entry.page, form, revised, 'The redisplayed revision');
        const revisedChart = await s.context.newPage();
        h.wireStrictPage(revisedChart, 'revision-chart', s.recorder);
        await revisedChart.goto(chart.url(), { waitUntil: 'domcontentloaded' });
        await waitForNavbars(revisedChart, 20000);
        const saved = revisedChart.locator(`#forms a[onclick*="formname=${entry.name}&"]`).first();
        await revealSavedForm(revisedChart, saved);
        const reopened = await ui.clickOpensPopup(revisedChart, saved, {
          context: s.context, recorder: s.recorder, label: `reopen-${form.key}`,
          timeout: 20000, position: { x: 8, y: 9 },
        });
        const params = new URL(reopened.url()).searchParams;
        h.assert(params.get('demographic_no') === patient && params.get('formId') === latest,
          'The saved-form entry did not reopen the latest version for the owned patient');
        await assertShown(reopened, form, revised, 'The reopened revision');
        await reopened.close();
        await revisedChart.close();
      });
    }
    if (entry.page && !entry.page.isClosed()) await entry.page.close();
  }
  for (const entry of results) {
    await attempt(entry, 'raised no JavaScript-layer problems', async () => h.assertStrictPage(s.recorder, labels(entry.form)));
  }
  await fresh.close();

  const failed = results.filter(entry => entry.failure);
  h.assert(!failed.length, `${failed.length} of ${results.length} forms failed: `
    + failed.map(entry => `${entry.form.title}: ${entry.failure}`).join(' | '));
}

if (require.main === module) runWorkflow(NAME, workflow, {
  preflight: () => require('./lib/export-content-helpers').requirePoppler('pdftotext'),
});
module.exports = { workflow, FORMS };
