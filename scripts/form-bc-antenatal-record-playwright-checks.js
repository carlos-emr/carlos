#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * BC Antenatal Record browser checks (BC installs only): BC-AR 2020 (form/BCAR2020, FrmBCAR20202Action,
 * five pages plus Attachments) and BC-AR 2012 (formbcar2012, saved through form/formname).
 *
 * The BC seed registers both forms but leaves them off the encounter's form list (encounterForm.hidden=0;
 * hidden is the display order, 0 means not shown). The check adds them through Administration > Select Forms,
 * the way a clinic switches them on, and removes them again at the end. It records every encounterForm row
 * first and asserts the form list is as a clinic saw it afterwards: the same forms shown, in the same order (Select
 * Forms renumbers the shown forms 1..n, #4952, so the numbers themselves can change). Cleanup puts the recorded
 * display order back and asserts the rows identical. The form list is clinic-wide, so the check must not run
 * concurrently with other checks (live wrapper: EXCLUSIVE=1).
 *
 * Steps:
 *   1. Select Forms adds BC-AR 2020 and BC-AR 2012, and the encounter's form list offers them.
 *   2. BC-AR 2012 opens from the list, saves one field and shows it again when reopened. It runs before the
 *      BC-AR 2020 steps because it does not depend on them, and a failing step ends the run.
 *   3. BC-AR 2020 opens from the list with the owned patient's name and date of birth filled in.
 *   4. A page 1 value is saved on the way to page 2, and page 2 shows the same, now saved, form.
 *   5. A page 2 value saves; reopening the form from the chart shows both pages' values.
 *   6. Pages 3, 4, 5 and Attachments open from the page links and keep the same form.
 *   7. Print of pages 1 and 2 is a PDF with each value on its own page, and the print is logged.
 *   8. Save and Exit stores the change and closes the form window.
 *   9. Select Forms removes both forms again and the form list is as it was found (the shown forms and their order).
 *
 * BC-AR 2012's referring-doctor prefill is deliberately not asserted: release reads the family doctor's
 * referral number with SxmlMisc and develop with DemographicXml, and the check runs against both.
 *
 * Rows: formBCAR2020 / formBCAR2020Data / formBCAR2020Text and formBCAR2012 rows belong to the owned FAKE
 * patient and are removed with it, and so is the PrintResourceLog row the Print step writes for that patient
 * (resourceName BCAR2020, resourceId = the owned patient): a print record of a FAKE patient the run deletes is residue.
 *
 *   npm run test:form-bc-antenatal-record-playwright
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { scratchDir, saveDownload, requirePoppler, squash } = require('./lib/export-content-helpers');
const { pageText } = require('./lib/pdf-graph');
const { encounterForms, formListAsSeen } = require('./lib/encounter-form-list');

const BCAR2020 = 'BC-AR 2020';
const BCAR2012 = 'BC-AR 2012';
const MIN_PANEL_CHARS = 500;

async function openSelectForms(page, s) {
  await h.gotoApp(page, s.config.baseUrl, '/administration');
  await page.locator('#dynamic-content').waitFor({ state: 'attached', timeout: 30000 });
  // The navigation accordion can keep the link collapsed; its click handler is what loads the panel.
  await page.evaluate(() => {
    const link = [...document.querySelectorAll('a.contentLink')]
      .find(anchor => /\/form\/setupSelect$/.test(anchor.getAttribute('href') || ''));
    if (!link) throw new Error('Administration has no Select Forms link');
    link.click();
  });
  await page.locator('#dynamic-content #selectForm').waitFor({ state: 'attached', timeout: 30000 });
}

async function selectFormsLists(page) {
  return page.evaluate(() => {
    const panel = document.getElementById('dynamic-content');
    const options = name => [...panel.querySelectorAll(`select[name="${name}"] option`)].map(o => o.value);
    return { available: options('selectedAddTypes'), selected: options('selectedDeleteTypes') };
  });
}

/** Moves one form between the Select Forms lists with the panel's own button, then waits for the new panel. */
async function moveForm(page, { button, list, name }) {
  const stale = await page.locator('#dynamic-content #selectForm').elementHandle();
  const posted = page.waitForResponse(r => r.request().method() === 'POST'
    && new URL(r.url()).pathname.endsWith('/form/select'), { timeout: 30000 });
  await page.locator(`#dynamic-content select[name="${list}"]`).selectOption(name);
  await page.locator(`#dynamic-content ${button}`).click();
  const response = await posted;
  h.assert(response.status() === 200 && (await response.text()).length >= MIN_PANEL_CHARS,
    `Select Forms ${button} for ${name} did not return the panel (HTTP ${response.status()})`);
  await page.waitForFunction(old => {
    const form = document.querySelector('#dynamic-content #selectForm');
    return !!form && form !== old;
  }, stale, { timeout: 30000 });
  return selectFormsLists(page);
}

/** Opens a BC-AR 2020 page link; with nothing changed the link navigates without saving. */
async function openPageLink(page, pageNo) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'load' }),
    page.locator(`a[onclick*="onPageChange('${pageNo}')"]`).first().click(),
  ]);
}

async function workflow(s) {
  requirePoppler('pdftotext');
  const before = encounterForms(s.sql);
  const row = name => before.find(r => r[0] === name);
  if (!row(BCAR2020) || !row(BCAR2012)) {
    throw new h.SkipCheck('This BC check needs the BC encounter form seed (BC-AR 2020 and BC-AR 2012)');
  }
  // The workflow fixture leaves the contact columns NULL. A patient added or edited through the master record
  // stores "" for a blank field, and BC-AR 2020's new-form prefill cannot take a NULL there (pending finding),
  // so the owned patient gets the values a blank master-record form saves.
  // Drop this once the prefill takes NULL (the pending finding; its number goes here when it is filed).
  const blanked = s.sql.value(`UPDATE demographic SET hin='', ver='', hc_type='BC', address='', city='', province='BC', postal='',
    phone='', phone2='' WHERE demographic_no=${s.patient} AND last_name=${h.sqlString(s.marker)}; SELECT ROW_COUNT()`);
  h.assert(blanked === '1', `The owned patient's contact fields were not blanked (${blanked} rows)`);
  const added = [];
  s.cleanup(() => {
    // A run that failed before step 9 still owes the clinic its form list: put the recorded display order back.
    for (const [name, value, , hidden] of before) {
      s.sql.execute(`UPDATE encounterForm SET hidden=${Number(hidden)}
        WHERE form_name=${h.sqlString(name)} AND form_value=${h.sqlString(value)} AND hidden<>${Number(hidden)}`);
    }
    h.assert(JSON.stringify(encounterForms(s.sql)) === JSON.stringify(before),
      'The encounter form list is not as the check found it');
  });
  const owned2020 = `SELECT id FROM formBCAR2020 WHERE demographic_no=${s.patient}`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM formBCAR2020Data WHERE form_id IN (${owned2020});
      DELETE FROM formBCAR2020Text WHERE form_id IN (${owned2020});
      DELETE FROM formBCAR2020 WHERE demographic_no=${s.patient};
      DELETE FROM formBCAR2012 WHERE demographic_no=${s.patient};
      DELETE FROM PrintResourceLog WHERE resourceName='BCAR2020' AND resourceId=${h.sqlString(s.patient)}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM formBCAR2020 WHERE demographic_no=${s.patient})
      + (SELECT COUNT(*) FROM formBCAR2012 WHERE demographic_no=${s.patient})
      + (SELECT COUNT(*) FROM PrintResourceLog WHERE resourceName='BCAR2020' AND resourceId=${h.sqlString(s.patient)})`) === '0',
    'BC-AR rows or its print record remain on the owned patient');
  });
  const latest2020 = () => s.sql.value(`SELECT MAX(id) FROM formBCAR2020 WHERE demographic_no=${s.patient}`);
  const value2020 = (formId, pageNo, field) => s.sql.value(`SELECT val FROM formBCAR2020Data
    WHERE form_id=${formId} AND page_no=${pageNo} AND field=${h.sqlString(field)}`);
  const pageOne = `${s.marker} page one`;
  // One short word: the printout's planned-birth box is 59pt wide and cuts a longer value at its first word (#4907).
  const pageTwo = `H${s.marker.slice(-7)}`;
  let chart;
  let form;

  await s.step('Select Forms adds BC-AR 2020 and BC-AR 2012, and the encounter form list offers them', async () => {
    const admin = await s.context.newPage();
    await openSelectForms(admin, s);
    for (const name of [BCAR2020, BCAR2012]) {
      if (Number(row(name)[3]) > 0) continue;
      const lists = await moveForm(admin, { button: '#add', list: 'selectedAddTypes', name });
      h.assert(lists.selected.includes(name) && !lists.available.includes(name), `Select Forms did not add ${name}`);
      added.push(name);
    }
    await admin.close();
    for (const name of [BCAR2020, BCAR2012]) {
      h.assert(Number(s.sql.value(`SELECT hidden FROM encounterForm WHERE form_name=${h.sqlString(name)}`)) > 0,
        `${name} is still off the encounter form list`);
    }
    chart = await s.chart();
    await ui.openFormsMenu(chart);
    for (const name of [BCAR2020, BCAR2012]) {
      await chart.getByRole('link', { name, exact: true }).waitFor({ state: 'visible', timeout: 20000 });
    }
  });

  await s.step('BC-AR 2012 saves a field and shows it again when reopened', async () => {
    const language = `${s.marker.slice(-8)} lang`;
    await ui.openFormsMenu(chart);
    let page = await s.popup(chart, chart.getByRole('link', { name: BCAR2012, exact: true }), 'bcar2012-new');
    h.assert(await page.locator('[name="c_surname"]').inputValue() === s.marker, 'BC-AR 2012 did not fill in the surname');
    await page.locator('[name="pg1_langPref"]').fill(language);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'load' }),
      page.getByRole('button', { name: 'Save', exact: true }).first().click(),
    ]);
    h.assert(s.sql.value(`SELECT pg1_langPref FROM formBCAR2012 WHERE demographic_no=${s.patient} ORDER BY ID DESC LIMIT 1`) === language,
      'BC-AR 2012 did not save the field');
    await page.close();
    const refreshed = await s.context.newPage();
    await refreshed.goto(chart.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(refreshed, 20000);
    chart = refreshed;
    const entry = chart.locator('#leftNavBar li, #rightNavBar li')
      .filter({ has: chart.locator('a[onclick*="/form/forwardshortcutname"]') })
      .filter({ hasText: BCAR2012 });
    await entry.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    h.assert(await entry.count() === 1, 'The chart should list exactly one saved BC-AR 2012');
    page = await ui.clickOpensPopup(chart, entry.first().locator('a[onclick*="/form/forwardshortcutname"]').first(),
      { context: s.context, recorder: s.recorder, label: 'bcar2012-reopen', timeout: 20000, position: { x: 8, y: 9 } });
    h.assert(await page.locator('[name="pg1_langPref"]').inputValue() === language, 'Reopened BC-AR 2012 lost the field');
    await page.close();
  });

  await s.step('BC-AR 2020 opens from the form list with the patient filled in', async () => {
    await ui.openFormsMenu(chart);
    form = await s.popup(chart, chart.getByRole('link', { name: BCAR2020, exact: true }), 'bcar2020-new');
    h.assert(new URL(form.url()).pathname.endsWith('/form/formBCAR2020pg1'), `BC-AR 2020 opened ${form.url()}`);
    h.assert(await form.locator('#formId').inputValue() === '0', 'A new BC-AR 2020 should not have a form id yet');
    // Page 1 shows the patient's name twice (Part 1 and the Part 2 header); both copies are filled from the chart.
    const values = name => form.locator(`[name="${name}"]`).evaluateAll(els => els.map(e => e.value));
    h.assert(JSON.stringify(await values('t_patientSurname')) === JSON.stringify([s.marker, s.marker]), 'Surname not filled in');
    h.assert(JSON.stringify(await values('t_patientGivenName')) === JSON.stringify(['Workflow', 'Workflow']), 'Given name not filled in');
    h.assert(await form.locator('[name="t_patientDOB"]').inputValue() === '02/01/1980', 'Date of birth not filled in');
  });

  await s.step('page 1 saves on the way to page 2, and page 2 shows the same saved form', async () => {
    await form.locator('[name="t_comments"]').fill(pageOne);
    await form.locator('[name="c_allergiesNone"]').check();
    let landed;
    const dialogs = await h.withExpectedDialogs(form, async () => {
      [landed] = await Promise.all([
        form.waitForNavigation({ waitUntil: 'load' }),
        form.locator('a[onclick*="onPageChange(\'2\')"]').first().click(),
      ]);
    });
    h.assert(dialogs.length === 1 && /save the changes/i.test(dialogs[0].text || ''),
      'Leaving a changed page should offer to save it first');
    const formId = latest2020();
    h.assert(/^[1-9]\d*$/.test(formId || ''), 'Moving to page 2 did not save page 1');
    h.assert(value2020(formId, 1, 't_comments') === pageOne && value2020(formId, 1, 'c_allergiesNone') === 'X',
      'The saved form lost the page 1 values');
    h.assert(value2020(formId, 0, 't_patientSurname') === s.marker, 'The saved form lost the patient surname');
    h.assert(landed && landed.status() === 200, `After saving page 1 the browser landed on HTTP ${landed && landed.status()}`
      + ` at ${new URL(form.url()).pathname}`);
    h.assert(await form.locator('[name="t_plannedBirthAt20Wks"]').count() === 1, 'Page 2 of BC-AR 2020 is not shown');
    h.assert(await form.locator('#formId').inputValue() === formId, 'Page 2 is not the form that was just saved');
  });

  await s.step('page 2 saves, and reopening the form from the chart shows both pages', async () => {
    const firstId = latest2020();
    await form.locator('[name="t_plannedBirthAt20Wks"]').fill(pageTwo);
    const [landed] = await Promise.all([
      form.waitForNavigation({ waitUntil: 'load' }),
      form.getByRole('button', { name: 'Save', exact: true }).click(),
    ]);
    h.assert(landed && landed.status() === 200, `Save on page 2 landed on HTTP ${landed && landed.status()}`);
    const formId = latest2020();
    h.assert(Number(formId) > Number(firstId), 'Save on page 2 did not store a new version of the form');
    h.assert(value2020(formId, 2, 't_plannedBirthAt20Wks') === pageTwo, 'The page 2 value was not saved');
    h.assert(value2020(formId, 1, 't_comments') === pageOne, 'The new version lost the page 1 value');
    h.assert(await form.locator('[name="t_plannedBirthAt20Wks"]').inputValue() === pageTwo, 'Page 2 lost its value after Save');
    await form.close();
    // A fresh chart page rather than chart.reload(): leaving the encounter fires its unload beacon, which the
    // strict recorder counts as a failed request (see rh-form-workflow).
    const refreshed = await s.context.newPage();
    await refreshed.goto(chart.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(refreshed, 20000);
    chart = refreshed;
    const entry = chart.locator('#leftNavBar li, #rightNavBar li')
      .filter({ has: chart.locator('a[onclick*="/form/forwardshortcutname"]') })
      .filter({ hasText: BCAR2020 });
    await entry.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    h.assert(await entry.count() === 1, 'The chart should list exactly one saved BC-AR 2020');
    form = await ui.clickOpensPopup(chart, entry.first().locator('a[onclick*="/form/forwardshortcutname"]').first(),
      { context: s.context, recorder: s.recorder, label: 'bcar2020-reopen', timeout: 20000, position: { x: 8, y: 9 } });
    h.assert(await form.locator('#formId').inputValue() === formId, 'The chart reopened an older version of the form');
    h.assert(await form.locator('[name="t_comments"]').inputValue() === pageOne, 'Reopened page 1 lost its value');
    await openPageLink(form, '2');
    h.assert(await form.locator('[name="t_plannedBirthAt20Wks"]').inputValue() === pageTwo, 'Reopened page 2 lost its value');
  });

  await s.step('pages 3, 4, 5 and Attachments open from the page links with the same form', async () => {
    const formId = latest2020();
    for (const pageNo of ['3', '4', '5', '6']) {
      await openPageLink(form, pageNo);
      h.assert(await form.locator('form[action$="/form/BCAR2020"]').count() === 1, `Page link ${pageNo} did not show the form`);
      h.assert(await form.locator('#formId').inputValue() === formId, `Page link ${pageNo} lost the form id`);
    }
    await openPageLink(form, '1');
  });

  await s.step('print of pages 1 and 2 is a PDF with each value on its own page', async () => {
    const formId = latest2020();
    const logged = () => s.sql.value(`SELECT COUNT(*) FROM PrintResourceLog
      WHERE resourceName='BCAR2020' AND resourceId=${h.sqlString(s.patient)} AND providerNo=${h.sqlString(s.provider)}`);
    const logsBefore = Number(logged());
    await form.getByRole('button', { name: 'Print', exact: true }).click();
    const dialog = form.locator('.ui-dialog').filter({ has: form.locator('#print-dialog') });
    await dialog.waitFor({ state: 'visible', timeout: 20000 });
    await form.locator('#print_pr1').check();
    await form.locator('#print_pr2').check();
    const pdf = await saveDownload(form, scratchDir(),
      () => dialog.getByRole('button', { name: 'Print', exact: true }).click(), { route: /\/form\/BCAR2020$/ });
    h.assert(pdf.bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'Print did not return a PDF');
    const first = squash(pageText(pdf.bytes, 1));
    const second = squash(pageText(pdf.bytes, 2));
    h.assert(first.includes(squash(pageOne)) && !first.includes(squash(pageTwo)), 'PDF page 1 should carry the page 1 value only');
    h.assert(second.includes(squash(pageTwo)) && !second.includes(squash(pageOne)), 'PDF page 2 should carry the page 2 value only');
    h.assert(first.includes(s.marker) && second.includes(s.marker), 'Each printed page should name the patient');
    h.assert(Number(logged()) === logsBefore + 1, 'The print was not logged');
    h.assert(latest2020() === formId, 'Printing stored a new version of the form');
  });

  await s.step('Save and Exit stores the change and closes the form window', async () => {
    const firstId = latest2020();
    await form.locator('[name="t_comments"]').fill(`${pageOne} exit`);
    const closed = form.waitForEvent('close', { timeout: 20000 });
    closed.catch(() => {});
    await form.getByRole('button', { name: 'Save and Exit', exact: true }).click();
    await closed;
    // close.jsp shuts the window at once, so the browser abandons whatever was still loading (the injected
    // CSRFGuard script). Drop only the failures the harness proves that close abandoned, as
    // eform-email-attachment-binding does; anything else still fails the step.
    const failures = s.recorder.requestFailures;
    for (let i = failures.length - 1; i >= 0; i -= 1) {
      const entry = failures[i];
      if (entry.label === 'bcar2020-reopen' && /ERR_ABORTED/.test(entry.errorText || '')
        && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) failures.splice(i, 1);
    }
    const formId = latest2020();
    h.assert(Number(formId) > Number(firstId) && value2020(formId, 1, 't_comments') === `${pageOne} exit`,
      'Save and Exit did not store the change');
    h.assert(value2020(formId, 2, 't_plannedBirthAt20Wks') === pageTwo, 'Save and Exit lost the page 2 value');
  });

  await s.step('Select Forms removes both forms again and the form list is as it was', async () => {
    const admin = await s.context.newPage();
    await openSelectForms(admin, s);
    for (const name of [...added].reverse()) {
      const lists = await moveForm(admin, { button: '#delete', list: 'selectedDeleteTypes', name });
      h.assert(!lists.selected.includes(name) && lists.available.includes(name), `Select Forms did not remove ${name}`);
    }
    await admin.close();
    h.assert(formListAsSeen(encounterForms(s.sql)) === formListAsSeen(before),
      'Removing the forms did not leave the form list as it was (the shown forms and their order)');
  });
}

if (require.main === module) runWorkflow('form-bc-antenatal-record', workflow);
module.exports = { workflow };
