#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5090 (BC installs only): three BC form fields that never came back after a save, each because of the page's
 * markup rather than its encoding.
 *   - BC-AR 2007 pages 2 and 3 opened the doula cell as "<td" with no ">": the browser read the doula box as the
 *     cell's attributes, so there was no box to type in and a saved doula was never shown or posted again.
 *   - BC-AR 2012 page 1 printed the substance-use "No" box's saved state as text after the box, so the box never showed
 *     ticked and the next Save cleared it.
 *   - BC-NewBorn 2008 pages 2 and 3 saved themselves as page 1 (c_lastVisited), so a record always reopened on page 1.
 *   - BC-NewBorn pages 2 and 3 showed page 1's date in their own date boxes, so the next Save stored it over theirs.
 *   - The Chart Checklist built its free-text description boxes as Java strings with the saved value unencoded, so a
 *     saved "'" ended the box's value and "<" or "&" did not come back as typed (CodeRabbit, PR #5167).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Forms menu ▸ the form ▸ its page link ▸ type or tick ▸
 * Save; then a fresh E-Chart ▸ the saved form in the chart's list ▸ the reopened record.
 * Asserted, per form: Save stores the value (the row read back over SQL), and the record reopened from the chart shows
 * it: the doula on both BC-AR 2007 pages, the ticked "No" box on BC-AR 2012 (with no saved state printed as text),
 * BC-NewBorn 2008 reopened on page 2, the page it was saved from, BC-NewBorn page 2's own date, and a Chart Checklist
 * description holding ' " < > & exactly.
 *
 * Fixtures: the owned FAKE- patient and one marker-named Forms-menu registration per form (clinic-wide, so EXCLUSIVE=1);
 * cleanup deletes the registrations and the owned patient's records of these forms and asserts them gone.
 *
 *   npm run test:form-bc-legacy-field-restore-playwright
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const NAME = 'form-bc-legacy-field-restore';
const FORMS = [
  { code: 'AR07', title: 'BC-AR 2007', view: 'formbcar2007', table: 'formBCAR2007' },
  { code: 'AR12', title: 'BC-AR 2012', view: 'formbcar2012', table: 'formBCAR2012' },
  { code: 'NB08', title: 'BC-NewBorn 2008', view: 'formBCNewBorn2008', table: 'formBCNewBorn2008' },
  { code: 'NB', title: 'BC-NewBorn', view: 'formbcnewborn', table: 'formBCNewBorn' },
  { code: 'CL', title: 'Chart Checklist', view: 'formbcclientchartchecklist', table: 'formBCClientChartChecklist' },
];
// A doula name with the characters a round trip damages, inside the boxes' maxlength (100).
const doula = (page) => `FAKE doula ${page} "A&B" <c>`;

async function workflow(s) {
  const { sql, patient, marker } = s;
  const q = h.sqlString;
  const present = sql.value(`SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME IN (${FORMS.map(form => q(form.table)).join(',')})`);
  if (present !== String(FORMS.length)) throw new h.SkipCheck(`This BC check needs the ${FORMS.map(form => form.title).join(', ')} tables`);
  const entries = Object.fromEntries(FORMS.map(form => [form.code, { form, name: `${marker.slice(-12)} ${form.code}` }]));
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterForm WHERE form_value LIKE ${q(`%fixture=${marker}&%`)}`);
    sql.execute(FORMS.map(form => `DELETE FROM ${form.table} WHERE demographic_no=${patient}`).join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${q(`%fixture=${marker}&%`)})
      + ${FORMS.map(form => `(SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient})`).join('+')}`) === '0',
    'The owned Forms-menu registrations or form records were not removed');
  });
  for (const { form, name } of Object.values(entries)) {
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${q(`../form/${form.view}.jsp?fixture=${marker}&demographic_no=`)},${q(name)},${q(form.table)},COALESCE(MAX(hidden),0)+1
      FROM encounterForm`);
  }

  /** The newest row of the owned patient's record, the named columns only. */
  const newest = (table, columns) => {
    const row = sql.rows(`SELECT ID, ${columns.map(c => `\`${c}\``).join(',')} FROM ${table}
      WHERE demographic_no=${patient} ORDER BY ID DESC LIMIT 1`)[0];
    h.assert(row, `Save stored no ${table} row`);
    return { id: row[0], values: Object.fromEntries(columns.map((c, i) => [c, row[i + 1]])) };
  };
  const open = async (entry) => {
    const chart = await s.chart();
    await ui.openFormsMenu(chart);
    const link = chart.getByRole('link', { name: entry.name, exact: true });
    await link.waitFor({ state: 'visible', timeout: 20000 });
    const page = await s.popup(chart, link, `restore-${entry.form.code}`);
    h.assert(new URL(page.url()).searchParams.get('demographic_no') === patient, 'the form opened for another patient');
    return page;
  };
  /** Follow the page's own link to another page of the same record. */
  const goToPage = async (page, view) => {
    const link = page.locator(`a[href*="${view}?"]:not([href^="javascript"])`).first();
    h.assert(await link.count(), `there is no link to ${view} on ${new URL(page.url()).pathname}`);
    await Promise.all([page.waitForURL(url => url.pathname.endsWith(`/form/${view}`), { waitUntil: 'load', timeout: 30000 }),
      link.click()]);
  };
  const save = async (page) => {
    const button = page.getByRole('button', { name: 'Save', exact: true }).first();
    await button.waitFor({ state: 'visible', timeout: 15000 });
    await h.withExpectedDialogs(page, () => Promise.all([page.waitForNavigation({ waitUntil: 'load', timeout: 30000 }), button.click()]));
    await h.assertNotErrorPage(page, 'the saved form');
  };
  /** The page a record opened on: the page names itself in form_link. */
  const shownPage = async (page) => (await page.locator('[name="form_link"]').first().inputValue().catch(() => ''))
    .replace(/\.jsp$/, '') || new URL(page.url()).pathname.split('/').pop();
  /** A fresh chart lists the saved record once; opening it reopens that record. */
  const reopen = async (entry, formId) => {
    const first = await s.chart();
    const chart = await s.context.newPage();
    s.cleanup(() => chart.close().catch(() => {}));
    await chart.goto(first.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(chart, 20000);
    const opener = `a[onclick*="/form/forwardshortcutname?formname=${entry.name}&"]`;
    const saved = chart.locator('#leftNavBar li, #rightNavBar li').filter({ has: chart.locator(opener) });
    await saved.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    h.assert(await saved.count() === 1, `the chart should list the saved ${entry.form.title} once (listed ${await saved.count()} times)`);
    const page = await ui.clickOpensPopup(chart, saved.first().locator(opener).first(),
      { context: s.context, recorder: s.recorder, label: `restore-${entry.form.code}-reopen`, timeout: 20000, position: { x: 8, y: 9 } });
    h.assert(await page.locator('[name="formId"]').first().inputValue() === formId, 'the chart opened another record');
    return page;
  };

  await s.step('BC-AR 2007: the doula on pages 2 and 3 is stored and shown again when the record reopens', async () => {
    const entry = entries.AR07;
    const page = await open(entry);
    for (const view of ['formbcar2007pg2', 'formbcar2007pg3']) {
      await goToPage(page, view);
      const field = view.endsWith('pg2') ? 'pg2_doula' : 'pg3_doula';
      const box = page.locator(`input[type="text"][name="${field}"]`);
      h.assert(await box.count() === 1 && await box.isVisible(), `${view} offers no doula box (${field})`);
      await box.fill(doula(view.slice(-3)));
      await save(page);
    }
    const row = newest(entry.form.table, ['pg2_doula', 'pg3_doula']);
    h.assert(row.values.pg2_doula === doula('pg2') && row.values.pg3_doula === doula('pg3'),
      `${entry.form.table} stored the doula as ${JSON.stringify(row.values)}`);
    await page.close();
    const reopened = await reopen(entry, row.id);
    for (const view of ['formbcar2007pg3', 'formbcar2007pg2']) {
      if (await shownPage(reopened) !== view) await goToPage(reopened, view);
      const field = view.endsWith('pg2') ? 'pg2_doula' : 'pg3_doula';
      const shown = await reopened.locator(`input[type="text"][name="${field}"]`).inputValue();
      h.assert(shown === doula(view.slice(-3)), `the reopened ${view} shows the doula as ${JSON.stringify(shown)}`);
    }
    await reopened.close();
  });

  await s.step('BC-AR 2012: the substance-use "No" box stays ticked when the record reopens', async () => {
    const entry = entries.AR12;
    const page = await open(entry);
    if (await shownPage(page) !== 'formbcar2012pg1') await goToPage(page, 'formbcar2012pg1');
    await page.locator('input[type="checkbox"][name="pg1_subUseNo"]').check();
    await save(page);
    const row = newest(entry.form.table, ['pg1_subUseNo']);
    h.assert(row.values.pg1_subUseNo === '1', `${entry.form.table} stored pg1_subUseNo as ${JSON.stringify(row.values.pg1_subUseNo)}`);
    await page.close();
    const reopened = await reopen(entry, row.id);
    if (await shownPage(reopened) !== 'formbcar2012pg1') await goToPage(reopened, 'formbcar2012pg1');
    h.assert(await reopened.locator('input[type="checkbox"][name="pg1_subUseNo"]').isChecked(),
      'the reopened BC-AR 2012 page 1 shows the substance-use "No" box unticked');
    const leaked = await reopened.evaluate(() => /checked\s*=\s*['"]?checked/i.test(document.body.innerText));
    h.assert(!leaked, 'the reopened BC-AR 2012 page 1 prints a checkbox state as text');
    await reopened.close();
  });

  await s.step('BC-NewBorn 2008: a record saved from page 2 reopens on page 2', async () => {
    const entry = entries.NB08;
    const page = await open(entry);
    await goToPage(page, 'formBCNewBorn2008pg2');
    await save(page);
    const row = newest(entry.form.table, ['c_lastVisited']);
    h.assert(row.values.c_lastVisited === 'pg2', `${entry.form.table} stored c_lastVisited ${JSON.stringify(row.values.c_lastVisited)}, not "pg2"`);
    await page.close();
    const reopened = await reopen(entry, row.id);
    const on = await shownPage(reopened);
    h.assert(on === 'formBCNewBorn2008pg2', `the record saved from page 2 reopened on ${on}`);
    await reopened.close();
  });

  await s.step("BC-NewBorn: page 2's own date is stored and shown again when the record reopens", async () => {
    const entry = entries.NB;
    const page = await open(entry);
    await goToPage(page, 'formbcnewbornpg2');
    // A date unlike page 1's (a new record dates every page today), in the record's dd/MM/yyyy.
    const pageTwoDate = '05/03/2025';
    await page.locator('input[name="pg2_formDate"]').fill(pageTwoDate);
    await save(page);
    const row = newest(entry.form.table, ['pg1_formDate', 'pg2_formDate']);
    h.assert(row.values.pg2_formDate === '2025-03-05', `${entry.form.table} stored pg2_formDate ${JSON.stringify(row.values.pg2_formDate)}`);
    h.assert(row.values.pg1_formDate !== row.values.pg2_formDate, 'page 1 holds the same date, so the page could not show the wrong one');
    await page.close();
    const reopened = await reopen(entry, row.id);
    if (await shownPage(reopened) !== 'formbcnewbornpg2') await goToPage(reopened, 'formbcnewbornpg2');
    const shown = await reopened.locator('input[name="pg2_formDate"]').inputValue();
    h.assert(shown === pageTwoDate, `the reopened page 2 shows its date as ${JSON.stringify(shown)}, not ${pageTwoDate}`);
    await reopened.close();
  });

  await s.step('Chart Checklist: a free-text description is stored and shown again exactly', async () => {
    const entry = entries.CL;
    const page = await open(entry);
    const description = `FAKE desc 'A&B' "q" <c>`;
    await page.locator('input[type="text"][name="descCon0"]').fill(description);
    await save(page);
    const row = newest(entry.form.table, ['descCon0']);
    h.assert(row.values.descCon0 === description, `${entry.form.table} stored descCon0 as ${JSON.stringify(row.values.descCon0)}`);
    await page.close();
    const reopened = await reopen(entry, row.id);
    const boxes = reopened.locator('input[type="text"][name="descCon0"]');
    h.assert(await boxes.count() === 1, `the reopened Chart Checklist has ${await boxes.count()} descCon0 boxes`);
    const shown = await boxes.inputValue();
    h.assert(shown === description, `the reopened Chart Checklist shows the description as ${JSON.stringify(shown)}`);
    await reopened.close();
  });
}

if (require.main === module) runWorkflow(NAME, workflow);
module.exports = { workflow };
