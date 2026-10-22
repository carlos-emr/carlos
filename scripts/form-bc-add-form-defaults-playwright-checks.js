#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5051 (BC installs only): a new BC install offers the current antenatal record, BC-AR 2020, in the E-Chart's
 * Forms menu, not the older BC-AR and BC-AR 2007. bc/V1.0.59.3 makes that change while the four BC antenatal rows
 * are still as the baseline seeded them (BC-AR 1, BC-AR 2007 1, BC-AR 2012 0, BC-AR 2020 0; encounterForm.hidden is
 * the menu position and 0 hides a form), and leaves a clinic's own Select Forms choices alone.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Forms menu; and the chart's saved-forms list ▸ a saved
 * BC-AR record.
 * Asserted:
 *   1. the four BC antenatal rows are as the migration leaves a new install (BC-AR 2020 shown, BC-AR and BC-AR 2007
 *      hidden, BC-AR 2012 hidden). Still at the baseline's values means the migration did not run: FAIL. Anything
 *      else is a clinic's own choice in Select Forms, which the check cannot judge: SKIP;
 *   2. the Forms menu offers BC-AR 2020 and offers neither BC-AR nor BC-AR 2007;
 *   3. hiding a form hides only its "(new)" entry: a patient's saved BC-AR record is still listed in the chart and
 *      opens.
 * Fixtures: the owned FAKE- patient and one saved formBCAR row for it (inserted by SQL, as a record saved before the
 * change would be), removed with the patient. Reads the clinic's form list; changes none of it.
 *
 *   npm run test:form-bc-add-form-defaults-playwright
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const NAME = 'form-bc-add-form-defaults';
const ROWS = {
  'BC-AR': '../form/formbcar.jsp?demographic_no=',
  'BC-AR 2007': '../form/formbcar2007.jsp?demographic_no=',
  'BC-AR 2012': '../form/formbcar2012.jsp?demographic_no=',
  'BC-AR 2020': '../form/formBCAR2020pg1.jsp?demographic_no=',
};
const SHOWN = row => Number(row) > 0;

async function workflow(s) {
  const { sql, patient } = s;
  const q = h.sqlString;
  const hidden = Object.fromEntries(Object.entries(ROWS).map(([name, value]) =>
    [name, sql.value(`SELECT hidden FROM encounterForm WHERE form_value=${q(value)}`)]));
  if (Object.values(hidden).some(value => value === '')) throw new h.SkipCheck('This BC check needs the BC antenatal form rows');

  await s.step('the BC antenatal rows are as the migration leaves a new install: BC-AR 2020 shown, the older records hidden', async () => {
    const baseline = hidden['BC-AR'] === '1' && hidden['BC-AR 2007'] === '1' && hidden['BC-AR 2012'] === '0' && hidden['BC-AR 2020'] === '0';
    h.assert(!baseline, 'the BC antenatal rows are still at the baseline values (BC-AR and BC-AR 2007 shown, BC-AR 2020 hidden): '
      + 'bc/V1.0.59.3 did not run');
    const migrated = !SHOWN(hidden['BC-AR']) && !SHOWN(hidden['BC-AR 2007']) && !SHOWN(hidden['BC-AR 2012']) && SHOWN(hidden['BC-AR 2020']);
    if (!migrated) {
      throw new h.SkipCheck(`the clinic has its own BC antenatal choices (${JSON.stringify(hidden)}), so a new install's defaults cannot be judged`);
    }
  });

  let chart;
  await s.step('the Forms menu offers BC-AR 2020 and neither BC-AR nor BC-AR 2007', async () => {
    chart = await s.chart();
    await ui.openFormsMenu(chart);
    await chart.getByRole('link', { name: 'BC-AR 2020', exact: true }).waitFor({ state: 'visible', timeout: 20000 });
    for (const older of ['BC-AR', 'BC-AR 2007']) {
      h.assert(await chart.getByRole('link', { name: older, exact: true }).count() === 0, `the Forms menu still offers ${older}`);
    }
  });

  await s.step('a saved BC-AR record is still listed in the chart and opens', async () => {
    s.cleanup(() => {
      sql.execute(`DELETE FROM formBCAR WHERE demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM formBCAR WHERE demographic_no=${patient}`) === '0', 'the owned BC-AR row remains');
    });
    const formId = sql.value(`INSERT INTO formBCAR (demographic_no, provider_no, formCreated, formEdited, c_surname)
      VALUES (${patient}, ${q(s.provider)}, CURDATE(), NOW(), ${q(s.marker)}); SELECT LAST_INSERT_ID()`);
    const refreshed = await s.context.newPage();
    await refreshed.goto(chart.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(refreshed, 20000);
    await chart.close();
    chart = refreshed;
    const opener = 'a[onclick*="/form/forwardshortcutname?formname=BC-AR&"]';
    const entry = chart.locator('#leftNavBar li, #rightNavBar li').filter({ has: chart.locator(opener) });
    await entry.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    h.assert(await entry.count() === 1, `the chart lists the saved BC-AR record ${await entry.count()} times, not once`);
    const page = await ui.clickOpensPopup(chart, entry.first().locator(opener).first(),
      { context: s.context, recorder: s.recorder, label: 'bcar-saved', timeout: 20000, position: { x: 8, y: 9 } });
    h.assert(await page.locator('[name="formId"]').first().inputValue() === formId, 'the chart opened another BC-AR record');
    h.assert(await page.locator('[name="c_surname"]').first().inputValue() === s.marker, 'the saved BC-AR record lost its surname');
    await page.close();
  });
}

if (require.main === module) runWorkflow(NAME, workflow);
module.exports = { workflow };
