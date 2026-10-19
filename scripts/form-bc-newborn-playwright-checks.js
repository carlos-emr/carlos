#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * BC-NewBorn 2008 browser check (BC installs only): the British Columbia Newborn Record 2008, three pages
 * (formBCNewBorn2008pg1-3, FrmBCNewBorn2008Record), saved through form/formname and printed through
 * form/createpdf.
 *
 * The BC seed puts the form on the encounter's form list (encounterForm.hidden=2; hidden is the display order,
 * 0 means not shown), so the check opens it from there and changes no clinic-wide setting. It still records
 * every encounterForm row and asserts they are unchanged at the end.
 *
 * Steps:
 *   1. The encounter's form list offers BC-NewBorn 2008.
 *   2. It opens from the list as a new form with the mother's name filled in from the chart.
 *   3. Save (after its confirmation) stores a page 1 field and shows the saved form.
 *   4. The chart lists the saved form, and reopening it shows the field.
 *   5. Print of page 1 is a PDF carrying the saved field and the mother's name, and stores nothing.
 *   6. The Part 2 page links open pages 2 and 3 of the same form.
 *
 * Rows: formBCNewBorn2008 rows belong to the owned FAKE patient and are removed with it. form/createpdf writes
 * no print log.
 *
 *   npm run test:form-bc-newborn-playwright
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { requirePoppler, squash } = require('./lib/export-content-helpers');
const { pageText } = require('./lib/pdf-graph');

const FORM = 'BC-NewBorn 2008';

/** Every encounterForm row, so the end state can be compared with the start, display order included. */
function encounterForms(sql) {
  return sql.rows('SELECT form_name,form_value,form_table,hidden FROM encounterForm ORDER BY form_name,form_value');
}

/**
 * Presses a control that posts the form to form/createpdf in a new window and returns what createpdf answered.
 * The answer is taken at the network layer (the new window only shows it), and the window is closed afterwards.
 */
async function createPdfAnswer(s, press) {
  const seen = [];
  const keep = async route => {
    try {
      const response = await route.fetch({ maxRedirects: 0 });
      const body = await response.body();
      await route.fulfill({ response, body });
      seen.push({ status: response.status(), type: response.headers()['content-type'] || '', body });
    } catch (error) {
      seen.push({ status: 0, type: '', body: Buffer.alloc(0), error: error.message.split('\n')[0] });
      await route.abort().catch(() => {});
    }
  };
  const isCreatePdf = url => url.pathname.endsWith('/form/createpdf');
  await s.context.route(isCreatePdf, keep);
  const opened = [];
  const onPage = page => opened.push(page);
  s.context.on('page', onPage);
  try {
    await press();
    const deadline = Date.now() + 60000;
    while (!seen.length && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
  } finally {
    s.context.off('page', onPage);
    await s.context.unroute(isCreatePdf, keep).catch(() => {});
    for (const page of opened) await page.close().catch(() => {});
  }
  h.assert(seen.length, 'Print sent nothing to form/createpdf within 60 s');
  h.assert(!seen[0].error, `The print request failed: ${seen[0].error}`);
  return seen[0];
}

async function workflow(s) {
  requirePoppler('pdftotext');
  const before = encounterForms(s.sql);
  const row = before.find(r => r[0] === FORM);
  if (!row) throw new h.SkipCheck(`This BC check needs the BC encounter form seed (${FORM})`);
  s.cleanup(() => {
    h.assert(JSON.stringify(encounterForms(s.sql)) === JSON.stringify(before),
      'The encounter form list is not as the check found it');
  });
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM formBCNewBorn2008 WHERE demographic_no=${s.patient}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM formBCNewBorn2008 WHERE demographic_no=${s.patient}`) === '0',
      'BC-NewBorn 2008 rows remain on the owned patient');
  });
  const stored = () => s.sql.rows(`SELECT ID, MothersHospitalID FROM formBCNewBorn2008
    WHERE demographic_no=${s.patient} ORDER BY ID`);
  // The printout's hospital number box is narrow: one short word.
  const hospitalId = `N${s.marker.slice(-7)}`;
  const mother = `${s.marker}, Workflow`;
  let chart;
  let form;
  let formId;

  await s.step('the encounter form list offers BC-NewBorn 2008', async () => {
    h.assert(Number(row[3]) > 0, `${FORM} is off the encounter form list on this install (hidden=${row[3]}); the BC seed shows it`);
    chart = await s.chart();
    await ui.openFormsMenu(chart);
    await chart.getByRole('link', { name: FORM, exact: true }).waitFor({ state: 'visible', timeout: 20000 });
  });

  await s.step('BC-NewBorn 2008 opens from the form list with the mother filled in', async () => {
    await ui.openFormsMenu(chart);
    form = await s.popup(chart, chart.getByRole('link', { name: FORM, exact: true }), 'bcnewborn2008-new');
    h.assert(new URL(form.url()).pathname.endsWith('/form/formBCNewBorn2008'), `${FORM} opened ${form.url()}`);
    h.assert(await form.locator('[name="formId"]').inputValue() === '0', 'A new form should not have a form id yet');
    h.assert(await form.locator('[name="MothersName"]').inputValue() === mother, "The mother's name was not filled in from the chart");
    h.assert(await form.locator('[name="c_surname"]').first().inputValue() === s.marker, 'The surname was not filled in from the chart');
  });

  await s.step('Save stores a page 1 field and shows the saved form', async () => {
    await form.locator('[name="MothersHospitalID"]').fill(hospitalId);
    const dialogs = await h.withExpectedDialogs(form, () => Promise.all([
      form.waitForNavigation({ waitUntil: 'load' }),
      form.getByRole('button', { name: 'Save', exact: true }).first().click(),
    ]));
    h.assert(dialogs.length === 1 && /save/i.test(dialogs[0].text), `Save should ask once to confirm (${JSON.stringify(dialogs.map(d => d.text))})`);
    const rows = stored();
    h.assert(rows.length === 1 && rows[0][1] === hospitalId, `Save did not store the field (${JSON.stringify(rows)})`);
    formId = rows[0][0];
    h.assert(await form.locator('[name="formId"]').inputValue() === formId, 'The saved form does not carry its new form id');
    h.assert(await form.locator('[name="MothersHospitalID"]').inputValue() === hospitalId, 'The saved form does not show the field');
    await form.close();
  });

  await s.step('the chart lists the saved form and reopening it shows the field', async () => {
    const refreshed = await s.context.newPage();
    await refreshed.goto(chart.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(refreshed, 20000);
    await chart.close();
    chart = refreshed;
    // The entry's title and its date are two links to the same form. The old BC-NewBorn form shares the
    // "BC-NewBorn" prefix, so the entry is matched by its exact form name.
    const opener = `a[onclick*="/form/forwardshortcutname?formname=${FORM}&"]`;
    const entry = chart.locator('#leftNavBar li, #rightNavBar li').filter({ has: chart.locator(opener) });
    await entry.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    h.assert(await entry.count() === 1, `The chart should list exactly one saved ${FORM}`);
    form = await ui.clickOpensPopup(chart, entry.first().locator(opener).first(),
      { context: s.context, recorder: s.recorder, label: 'bcnewborn2008-reopen', timeout: 20000, position: { x: 8, y: 9 } });
    h.assert(await form.locator('[name="formId"]').inputValue() === formId, 'The chart opened another form');
    h.assert(await form.locator('[name="MothersHospitalID"]').inputValue() === hospitalId, 'The reopened form lost the field');
  });

  await s.step("print of page 1 is a PDF with the saved field and the mother's name", async () => {
    const answer = await createPdfAnswer(s, () => form.getByRole('button', { name: 'Print', exact: true }).first().click());
    h.assert(answer.status === 200 && /application\/pdf/.test(answer.type), `Print answered ${answer.status} ${answer.type}, not a PDF`);
    h.assert(answer.body.subarray(0, 5).toString('latin1') === '%PDF-', 'Print did not return a PDF document');
    const printed = squash(pageText(answer.body, 1));
    h.assert(printed.includes(squash(hospitalId)), 'The printed page does not carry the saved field');
    // The baby's surname (c_surname) is printed too, so the mother's name is matched whole ("<surname>, <given>").
    h.assert(printed.includes(squash(mother)), "The printed page does not carry the mother's name");
    h.assert(JSON.stringify(stored().map(r => r[0])) === JSON.stringify([formId]), 'Printing stored a new version of the form');
  });

  await s.step('the Part 2 page links open pages 2 and 3 of the same form', async () => {
    for (const [index, pageNo] of [[0, 2], [1, 3]]) {
      // Each link is followed from page 1 so a failure names the link that broke.
      if (!new URL(form.url()).pathname.endsWith('/form/formBCNewBorn2008pg1')) {
        await form.goto(h.appUrl(s.config.baseUrl,
          `/form/formBCNewBorn2008pg1?demographic_no=${s.patient}&formId=${formId}`), { waitUntil: 'load' });
      }
      const link = form.locator('a[href*="formBCNewBorn2008pg"]').nth(index);
      const [response] = await Promise.all([form.waitForNavigation({ waitUntil: 'load' }), link.click()]);
      const reached = new URL(form.url()).pathname;
      h.assert(response && response.status() === 200 && reached.endsWith(`/form/formBCNewBorn2008pg${pageNo}`),
        `The Part 2 link to page ${pageNo} reached ${reached} (HTTP ${response ? response.status() : 'none'})`);
      h.assert(await form.locator('[name="formId"]').inputValue() === formId, `Page ${pageNo} lost the form id`);
      // Every page sets c_lastVisited to "pg1"; form_link names the page itself (Save returns to it).
      h.assert(await form.locator('[name="form_link"]').inputValue() === `formBCNewBorn2008pg${pageNo}.jsp`,
        `The link to page ${pageNo} showed another page`);
    }
  });
}

if (require.main === module) runWorkflow('form-bc-newborn', workflow);
module.exports = { workflow };
