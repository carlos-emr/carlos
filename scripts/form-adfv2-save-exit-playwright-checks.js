#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Finding 240 (#4598), the Save and Exit half. User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸
// Forms menu ▸ ADFv2 ▸ type ▸ Save and Exit ▸ confirm.
//
// Save and Exit posts form/formname with submit=exit, and the save is answered with close.jsp, which
// closes the window it is loaded in. That has to be the form's own window: no second window opens, the
// form window closes, and exactly one formAdfV2 row holds the typed values for the signed-in provider.
// Before the fix the form posted into a window named apptProviderSearch, which CARLOS never opens, so
// the browser created one for the POST, close.jsp closed that one, and the clinician's window stayed
// open on the unsaved form. The Save half (the record redisplayed in the window Save was pressed in) is
// form-catalog-smoke-adfv2-window; scripts/adfv2-save-window-target.test.js runs the page's handlers.
//
// Fixtures: the owned synthetic patient and one marker-named Forms-menu registration of formadfv2 (the
// shipped row is hidden on Ontario installs and registrations are clinic-wide, so it must not run
// concurrently with other checks: live wrapper EXCLUSIVE=1). Cleanup deletes the patient's formAdfV2
// rows and the registration and asserts both gone.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NAME = 'form-adfv2-save-exit';
// The catalogue's prose: text the front door's WAF scores, so a refused save fails here rather than passing on clean text.
const PROSE = "BP 120/80 & HR 72; pt's c/o SOB";
const FACILITY = 'FAKE Facility';

const isFormPost = response => response.request().method() === 'POST'
  && new URL(response.url()).pathname.endsWith('/form/formname');

/**
 * close.jsp closes its window while the CSRFGuard client script the response filter injects is still loading, and
 * Chromium reports that load as aborted. It is the browser giving up a load because its document went away, not an
 * application failure: excuse exactly that one, on the form's window, proven by navigatedAway(), and nothing else
 * (the same rule as legacy-lab-display-playwright-checks.js).
 */
function excuseAbandonedCsrfGuardLoad(recorder, label) {
  for (let i = recorder.requestFailures.length - 1; i >= 0; i -= 1) {
    const entry = recorder.requestFailures[i];
    if (entry.label === label && entry.resourceType === 'script' && /ERR_ABORTED/.test(entry.errorText || '')
      && new URL(entry.url).pathname.endsWith('/csrfguard')
      && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) {
      recorder.requestFailures.splice(i, 1);
    }
  }
}

async function workflow(s) {
  const { sql, patient, provider, marker, context } = s;
  const name = `${marker} ADF`;
  const value = `../form/formadfv2.jsp?fixture=${marker}&demographic_no=`;
  const text = `${marker} ${PROSE}`;
  const count = `SELECT COUNT(*) FROM formAdfV2 WHERE demographic_no=${patient}`;
  h.assert(name.length <= 30, `The owned registration name exceeds encounterForm.form_name: ${name}`);

  s.cleanup(() => {
    sql.execute(`DELETE FROM formAdfV2 WHERE demographic_no=${patient}`);
    h.assert(sql.value(count) === '0', 'The owned patient\'s formAdfV2 rows were not removed');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${h.sqlString(`%fixture=${marker}&%`)}`) === '0',
      'The owned Forms-menu registration was not removed');
  });
  sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
    SELECT ${h.sqlString(value)},${h.sqlString(name)},'formAdfV2',COALESCE(MAX(hidden),0)+1 FROM encounterForm`);

  const chart = await s.chart();
  let form;
  await s.step('ADFv2 opens from the Forms menu for the owned patient', async () => {
    await chart.locator('#menuTitle1 a').hover();
    const link = chart.getByRole('link', { name, exact: true });
    const listed = await link.count();
    h.assert(listed === 1, `The Forms menu lists the registered ADFv2 ${listed} times, not once`);
    form = await s.popup(chart, link, 'form-ADF');
    h.assert(new URL(form.url()).searchParams.get('demographic_no') === patient, 'The form opened for another patient');
    // The form repeats its buttons above and below the questions; the first row is the one a clinician sees on opening.
    await form.getByRole('button', { name: 'Save and Exit', exact: true }).first().waitFor({ state: 'visible', timeout: 10000 });
  });

  await s.step('ADFv2: Save and Exit stores the form and closes the window it was pressed in, opening no other', async () => {
    await form.locator('[name="actProblem"]').fill(text);
    await form.locator('[name="sendFacility"]').fill(FACILITY);
    await form.locator('[name="capTreatDecY"]').check();
    const before = Number(sql.value(count));
    // Every page the context opens from here on is a window Save and Exit created; runWorkflow wires each one strictly.
    const opened = [];
    const onPage = page => opened.push(page);
    context.on('page', onPage);
    const posted = context.waitForEvent('response', { predicate: isFormPost, timeout: 15000 });
    const closed = form.waitForEvent('close', { timeout: 15000 });
    // Observed below; a failure raised first must not surface later as an unhandled rejection.
    posted.catch(() => {});
    closed.catch(() => {});
    let dialogs;
    try {
      dialogs = await h.withExpectedDialogs(form, async () => {
        try {
          await form.getByRole('button', { name: 'Save and Exit', exact: true }).first().click({ noWaitAfter: true });
        } catch (error) {
          // close.jsp can close the window before Chromium acknowledges the click; only that is accepted.
          if (!form.isClosed() || !/Target page, context or browser has been closed/.test(error.message)) throw error;
        }
      });
      const response = await posted.catch(() => null);
      h.assert(response, 'Save and Exit posted nothing to form/formname');
      h.assert(response.status() === 200, `Save and Exit was answered ${response.status()}, not with the closing page`);
      h.assert(response.frame().page() === form,
        'Save and Exit was answered in another window, and the one it was pressed in still shows the unsaved form');
      await closed.catch(() => {});
    } finally {
      context.off('page', onPage);
    }
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm',
      `Save and Exit must ask for one confirmation (dialogs: ${dialogs.map(d => d.type).join(', ') || 'none'})`);
    h.assert(form.isClosed(), 'The window Save and Exit was pressed in is still open');
    h.assert(!opened.length, `Save and Exit opened ${opened.length} other window(s)`);
    await expectValue(sql, count, String(before + 1), 'Save and Exit did not store exactly one new row');
    const [row] = sql.rows(`SELECT actProblem,sendFacility,capTreatDecY,provider_no FROM formAdfV2
      WHERE demographic_no=${patient} ORDER BY ID DESC LIMIT 1`);
    h.assert(row[0] === text, 'The stored actProblem differs from what was typed');
    h.assert(row[1] === FACILITY, 'The stored sendFacility differs from what was typed');
    h.assert(row[2] === '1', 'The stored capTreatDecY is not ticked');
    h.assert(row[3] === provider, 'The row is not attributed to the signed-in provider');
    excuseAbandonedCsrfGuardLoad(s.recorder, 'form-ADF');
  });
}

if (require.main === module) runWorkflow(NAME, workflow);
module.exports = { workflow };
