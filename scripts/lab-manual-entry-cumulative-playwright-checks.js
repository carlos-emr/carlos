#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Manual lab entry and the cumulative lab views (coverage plan: lab-manual-entry-cumulative).
 *
 * User path: Schedule ▸ Inbox ▸ Create Lab (oscarMDS/ViewCreateLab popup) ▸ Add Test twice
 * (oscarMDS/ViewCreateLabTest fragments) ▸ Submit to EMR (oscarMDS/SubmitLab, confirm()); then
 * the run patient's E-Chart ▸ Lab Result menu ▸ Grid Display (lab/ViewCumulativeLabValues3) ▸ the
 * new lab's date column (lab display) ▸ a test name (lab/CA/ON/ViewLabValues) ▸ Plot
 * (lab/CA/ON/ViewLabValuesGraph and its graph image); and Lab Result menu ▸ Row Display ▸ a test
 * (lab/ViewDisplayLabValue, injected by AJAX).
 * Asserts: the lab reaches hl7TextInfo under its accession, is matched to the patient and routed
 * to the submitting provider, and its measurements carry the entered values; every view shows
 * the values, units, ranges and date that were typed; the graph answers image bytes.
 * Last (pinned to app-findings-log.md finding 149): a second lab whose result, units and range carry
 * inert markup (xss-poison payloads 1100-1102) is shown by Row Display, and hovering its row must not
 * build a [data-xp] element in the tooltip. The markup reaches the lab the way a lab's own message
 * would (the stored HL7 text is rewritten by SQL, as imported data is), never through the WAF-guarded
 * form. The controls before it (the row shows the text literally, the tooltip source carries it) sit
 * in their own step, so a fixture problem is not mistaken for the known failure.
 * Fixtures: the run's FAKE- patient and the labs (unique accessions); cleanup removes each lab's
 * routing, measurement, message and checksum rows and its archived file, and asserts it.
 */
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { removeOwnedHl7Labs } = require('./lab-forwarding-rules-playwright-checks');

const TIMEOUT = 30000;
const DATE = '2026-09-30';
// CML test codes that measurementMap maps to LOINC (718-7, 4544-3), so Grid Display lists them.
const TESTS = [
  { code: '2010', name: 'HEMOGLOBIN', value: '137', unit: 'g/L', low: '120', high: '160', flag: 'N' },
  { code: '2013', name: 'HEMATOCRIT', value: '0.52', unit: 'L/L', low: '0.36', high: '0.46', flag: 'A' },
];

// Finding 149 fixture: a second lab (accession ML...) whose result, units and reference range are replaced,
// in the stored HL7 text, by inert markup. Payload numbers 1100-1199 belong to this check (1000-1099 are
// xss-poison-note-history's). The text avoids the HL7 separators (| ^ ~ \\ &) so the stored message still
// parses to exactly these values.
// The result is numeric when the form files it: the lab handler stores a measurement (which Row Display's lookup
// starts from) only for a numeric result. The stored message is rewritten afterwards.
const POISON_TEST = { code: '3210', name: 'SODIUM', value: '7391', unit: 'PXUNIT', low: 'PXLOW', high: 'PXHIGH', flag: 'N' };
const POISON_FIELD = { result: 1100, units: 1101, range: 1102 };
const poison = (n) => `FAKE-XP<i data-xp="${n}">q</i> "dq" 'sq'`;
const POISONED_TOOLTIP_STEP = 'hovering a Row Display row whose result, units and range carry markup builds no element from them';

/** Opens the chart's Lab Result menu item ("+" reveals it on hover) as a popup. */
async function openLabMenuItem(s, chart, name, label) {
  // callers supply only the fixed Grid Display and Row Display labels.
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
  const item = chart.locator('#menu2 a').filter({ hasText: new RegExp(`^\\s*${name}\\s*$`) }).first();
  await item.waitFor({ state: 'attached', timeout: TIMEOUT });
  if (!await item.isVisible()) {
    await chart.locator('#menuTitle2 a').first().hover();
    await item.waitFor({ state: 'visible', timeout: TIMEOUT });
  }
  return s.popup(chart, item, label);
}

/**
 * Inbox ▸ Create Lab: opens the form as a clinician does, types the lab and its tests and submits it
 * (one confirm). Returns the Create Lab popup and the Inbox page it was opened from, both still open,
 * once the form reports success.
 */
async function submitCreateLab(s, { accession, tests, label }) {
  const { recorder } = s;
  const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
    { context: s.context, recorder, label: `${label}-inbox`, timeout: TIMEOUT });
  const form = await s.popup(inbox, inbox.locator('a[href*="oscarMDS/ViewCreateLab"]').first(), `${label}-create`);
  await form.locator('#labname').selectOption('CML');
  await form.locator('#accession').fill(accession);
  await form.locator('#lab_req_date').fill(`${DATE} 08:30`);
  await form.locator('#lastname').fill(s.marker);
  await form.locator('#firstname').fill('Workflow');
  await form.locator('#sex').selectOption('F');
  await form.locator('#dob').fill('1980-01-02');
  for (let index = 1; index <= tests.length; index++) {
    const test = tests[index - 1];
    await form.getByRole('link', { name: 'Add Test' }).click();
    const field = (name) => form.locator(`[id="test_${index}.${name}"]`);
    await field('valDate').waitFor({ state: 'visible', timeout: TIMEOUT });
    await field('valDate').fill(`${DATE} 09:00`);
    await field('code').fill(test.code);
    await field('lab_test_name').fill(test.name);
    await field('codeVal').fill(test.value);
    await field('codeUnit').fill(test.unit);
    await field('refRangeLow').fill(test.low);
    await field('refRangeHigh').fill(test.high);
    await field('flag').selectOption(test.flag);
  }
  h.assert(await form.locator('#test_num').inputValue() === String(tests.length), 'The form does not count every test');
  const dialogs = await h.withExpectedDialogs(form, async () => {
    await ui.clickAndAwaitReload(form, form.locator('form[name="testForm"] button[type="submit"]'),
      { timeout: 60000, label: 'Submit to EMR' });
  });
  h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Submitting did not ask for exactly one confirmation');
  await form.locator('.alert-success').waitFor({ state: 'visible', timeout: TIMEOUT });
  h.assert(await form.locator('.alert-danger').count() === 0, 'The lab submission reported an error');
  return { form, inbox };
}

async function workflow(s) {
  const { sql, patient, provider } = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const accession = `ML${stamp}`;
  let labNo = null;
  // Cleanup deletes every lab under this accession, so it must be unused before it is claimed.
  h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '0',
    'The run accession is already in use');
  s.cleanup(() => {
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`).map(([id]) => id);
    if (labNo) labs.push(labNo);
    removeOwnedHl7Labs(sql, labs);
  });

  await s.step('Inbox ▸ Create Lab files a two-test lab for the patient', async () => {
    const { form, inbox } = await submitCreateLab(s, { accession, tests: TESTS, label: 'manual-lab' });
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`, '1',
      'The submitted lab did not reach hl7TextInfo under its accession');
    labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`);
    h.assert(/^[1-9]\d*$/.test(labNo), 'The submitted lab has no lab number');
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}`) === patient,
      'The submitted lab was not matched to the run patient');
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}
      AND provider_no=${h.sqlString(provider)}`) === '1', 'The submitted lab was not routed to the submitting provider');
    for (const test of TESTS) {
      const stored = sql.rows(`SELECT m.dataField, DATE(m.dateObserved) FROM measurements m
        JOIN measurementsExt l ON l.measurement_id=m.id AND l.keyval='lab_no' AND l.val=${h.sqlString(labNo)}
        JOIN measurementsExt i ON i.measurement_id=m.id AND i.keyval='identifier' AND i.val=${h.sqlString(test.code)}
        WHERE m.demographicNo=${patient}`);
      h.assert(JSON.stringify(stored) === JSON.stringify([[test.value, DATE]]),
        `The ${test.name} measurement does not hold the entered value and date`);
    }
    await form.close();
    if (inbox !== s.schedule) await inbox.close();
  });

  const chart = await s.chart();
  let grid;
  await s.step('E-Chart ▸ Lab Result ▸ Grid Display shows the entered values under the lab date', async () => {
    grid = await openLabMenuItem(s, chart, 'Grid Display', 'manual-lab-grid');
    const header = grid.locator(`#cumulativeLabTable thead a[data-seg-id="${labNo}"]`);
    h.assert(await header.count() === 1, 'Grid Display has no column for the new lab');
    h.assert((await header.innerText()).trim() === '30 Sep 26', 'The lab column is not dated as entered');
    for (const test of TESTS) {
      const row = grid.locator('#cumulativeLabTable tbody tr').filter({ has: grid.locator('td:first-child', { hasText: test.name }) });
      h.assert(await row.count() === 1, `Grid Display has no ${test.name} row`);
      const cells = (await row.locator('td').allInnerTexts()).map((text) => text.trim());
      h.assert(cells[1] === test.value && cells[2] === DATE && cells[3] === test.value,
        `Grid Display shows ${test.name} as ${JSON.stringify(cells.slice(1))}`);
    }
  });

  let values;
  await s.step('the lab display\'s test link opens lab values with the entered result, range and units', async () => {
    const report = await s.popup(grid, grid.locator(`#cumulativeLabTable thead a[data-seg-id="${labNo}"]`), 'manual-lab-display');
    const test = TESTS[1];
    values = await s.popup(report, report.locator('a[href*="CA/ON/ViewLabValues"]').filter({ hasText: test.name }).first(),
      'manual-lab-values');
    const rows = values.locator('#tblDiscs tbody tr').filter({ hasText: test.value });
    h.assert(await rows.count() === 1, 'Lab values does not list the entered result exactly once');
    const cells = (await rows.locator('td').allInnerTexts()).map((text) => text.trim());
    h.assert(cells[0] === test.name && cells[1] === test.value && cells[2] === test.flag
      && cells[3] === `${test.low} - ${test.high}` && cells[4] === test.unit && cells[5].startsWith(DATE),
    `Lab values shows ${JSON.stringify(cells)}`);
    await report.close();
  });

  await s.step('Plot on lab values draws the graph image for the test', async () => {
    await ui.clickAndAwaitReload(values, values.getByRole('button', { name: 'Plot' }), { timeout: TIMEOUT, label: 'Plot' });
    h.assert(new URL(values.url()).pathname.endsWith('/lab/CA/ON/ViewLabValuesGraph'), 'Plot did not open the graph page');
    const image = values.locator('img[src*="GraphMeasurements"]');
    h.assert(await image.count() === 1, 'The graph page has no graph image');
    await values.waitForFunction(() => {
      const img = document.querySelector('img[src*="GraphMeasurements"]');
      return img && img.complete && img.naturalWidth > 0;
    }, null, { timeout: TIMEOUT });
    // Read the same URL the page rendered; the browser gives a check no image bytes otherwise.
    const response = await values.context().request.get(await image.evaluate((img) => img.src));
    const bytes = await response.body();
    h.assert(response.status() === 200 && /^image\//.test(response.headers()['content-type'] || ''),
      `The graph answered HTTP ${response.status()} ${response.headers()['content-type']}`);
    const png = bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
    const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8;
    h.assert((png || jpeg) && bytes.length > 1000, 'The graph image is not a rendered PNG or JPEG');
    await values.close();
    await grid.close();
  });

  await s.step('E-Chart ▸ Lab Result ▸ Row Display loads each test\'s entered value on demand', async () => {
    const rowDisplay = await openLabMenuItem(s, chart, 'Row Display', 'manual-lab-rows');
    for (const test of TESTS) {
      const button = rowDisplay.getByRole('button', { name: test.name, exact: true });
      h.assert(await button.count() === 1, `Row Display does not list ${test.name}`);
      // Hold the real row request until the loading state is measured; its response is unmodified.
      let releaseRow;
      const loadingState = new Promise(resolve => { releaseRow = resolve; });
      const routePattern = '**/lab/ViewDisplayLabValue';
      const holdRow = async route => { await loadingState; await route.continue(); };
      await rowDisplay.route(routePattern, holdRow);
      let response;
      try {
        [response] = await Promise.all([
          rowDisplay.waitForResponse((r) => new URL(r.url()).pathname.endsWith('/lab/ViewDisplayLabValue'), { timeout: TIMEOUT }),
          (async () => {
            await button.click();
            const loader = rowDisplay.locator('#cumulativeLab img[src$="/images/spinner.jpg"]').last();
            await loader.waitFor({ state: 'visible', timeout: TIMEOUT });
            await loader.evaluate(img => img.decode());
            h.assert((await loader.locator('..').getByRole('status').innerText()).trim() === 'Loading ...',
              'The loading indicator did not expose its localized status text');
            const bounds = await loader.boundingBox();
            h.assert(bounds && bounds.width === 100 && bounds.height === 77,
              'The row loading image did not retain the standard 100 by 77 pixel size');
            releaseRow();
          })(),
        ]);
      } finally {
        releaseRow();
        await rowDisplay.unroute(routePattern, holdRow);
      }
      h.assert(response.status() === 200, `lab/ViewDisplayLabValue answered HTTP ${response.status()}`);
      const section = rowDisplay.locator('#cumulativeLab .preventionSection').filter({ hasText: test.name.slice(0, 8) }).last();
      await section.waitFor({ state: 'visible', timeout: TIMEOUT });
      h.assert(await rowDisplay.locator('#cumulativeLab [role="status"]').count() === 0,
        'Loaded lab results retained the temporary loading status');
      const shown = (await section.locator('.preventionProcedure p').allInnerTexts()).map((text) => text.replace(/\s+/g, ' ').trim());
      h.assert(shown.length === 1 && shown[0].startsWith(`${test.value} ${DATE}`),
        `Row Display shows ${test.name} as ${JSON.stringify(shown)}`);
      await section.locator('.preventionProcedure').hover();
      await rowDisplay.waitForFunction(({ value, units, range }) =>
        typeof oDv !== 'undefined' && getComputedStyle(oDv).visibility === 'visible'
          && dvHdr.textContent.trim() === value && dvBdy.textContent.trim() === `${units} ${range}`,
      { value: test.value, units: test.unit, range: `${test.low} - ${test.high}` }, { timeout: TIMEOUT });
    }
    await rowDisplay.close();
  });

  // ---- Finding 149: Row Display tooltips render lab text as HTML -----------------------------------
  const poisonAccession = `ML${crypto.randomBytes(4).toString('hex').toUpperCase()}`;
  let poisonLabNo = null;
  h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(poisonAccession)}`) === '0',
    'The markup lab accession is already in use');
  s.cleanup(() => {
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(poisonAccession)}`).map(([id]) => id);
    if (poisonLabNo) labs.push(poisonLabNo);
    removeOwnedHl7Labs(sql, labs);
  });
  let rowDisplay;
  let section;
  const openPoisonedRow = async () => {
    rowDisplay = await openLabMenuItem(s, chart, 'Row Display', 'manual-lab-poison-rows');
    const button = rowDisplay.getByRole('button', { name: POISON_TEST.name, exact: true });
    h.assert(await button.count() === 1, `Row Display does not list ${POISON_TEST.name}`);
    await Promise.all([
      rowDisplay.waitForResponse((r) => new URL(r.url()).pathname.endsWith('/lab/ViewDisplayLabValue'), { timeout: TIMEOUT }),
      button.click(),
    ]);
    section = rowDisplay.locator('#cumulativeLab .preventionSection').filter({ hasText: POISON_TEST.name.slice(0, 8) }).last();
    await section.waitFor({ state: 'visible', timeout: TIMEOUT });
  };

  await s.step('Row Display lists a lab with markup in its result, units and range as text and holds it in the tooltip source', async () => {
    const { form, inbox } = await submitCreateLab(s, { accession: poisonAccession, tests: [POISON_TEST], label: 'manual-lab-poison' });
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(poisonAccession)}`, '1',
      'The markup lab did not reach hl7TextInfo under its accession');
    poisonLabNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(poisonAccession)}`);
    h.assert(/^[1-9]\d*$/.test(poisonLabNo), 'The markup lab has no lab number');
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${poisonLabNo}`) === patient,
      'The markup lab was not matched to the run patient');
    // The lab arrived through the form with plain placeholders; the stored message is then rewritten the way an
    // imported lab's own text would carry markup. The WAF guards the form, not data already stored.
    const stored = sql.value(`SELECT message FROM hl7TextMessage WHERE lab_id=${poisonLabNo}`);
    const hl7 = Buffer.from(stored, 'base64').toString('utf8');
    const range = `${POISON_TEST.low} - ${POISON_TEST.high}`;
    // The OBX fields result|units|range, as the generator writes them (OBX-5, -6, -7).
    const fields = `|GENERAL|${POISON_TEST.value}|${POISON_TEST.unit}|${range}|`;
    h.assert(hl7.split(fields).length === 2, 'The stored lab message does not hold the result, units and range fields exactly once');
    const marked = hl7.replace(fields, () => `|GENERAL|${poison(POISON_FIELD.result)}|${poison(POISON_FIELD.units)}|${poison(POISON_FIELD.range)}|`);
    sql.execute(`UPDATE hl7TextMessage SET message=${h.sqlString(Buffer.from(marked, 'utf8').toString('base64'))}
      WHERE lab_id=${poisonLabNo}`);
    await form.close();
    if (inbox !== s.schedule) await inbox.close();

    await openPoisonedRow();
    const procedure = section.locator('.preventionProcedure');
    h.assert(await procedure.count() === 1, `Row Display shows ${await procedure.count()} value(s) for the markup lab, expected 1`);
    h.assert(await rowDisplay.locator('[data-xp]').count() === 0,
      'Row Display built an element from the markup before any tooltip was shown');
    const shown = (await procedure.locator('p').innerText()).replace(/\s+/g, ' ').trim();
    h.assert(shown.startsWith(poison(POISON_FIELD.result)),
      'Row Display does not show the markup in the result as the literal text');
    // scanBO moved the row's title into these properties when the page loaded; they are what boxover draws.
    const source = await procedure.evaluate((el) => ({ header: el.boHDR, body: el.boBDY }));
    h.assert(source.header === poison(POISON_FIELD.result)
      && source.body === `${poison(POISON_FIELD.units)} ${poison(POISON_FIELD.range)}`,
    'The tooltip source of the markup row is not the stored result, units and range');
    await rowDisplay.close();
  });

  // The control of the pinned step below: Row Display opens (openPoisonedRow asserts the lab's button is listed once and
  // that its values load), the row is hovered and the tooltip is on the screen. The page stays open for the next step,
  // so the pinned one holds nothing but the question it exists to ask.
  await s.step('hovering a Row Display row whose result, units and range carry markup shows its tooltip', async () => {
    await openPoisonedRow();
    await section.locator('.preventionProcedure').hover();
    await rowDisplay.waitForFunction(() => typeof oDv !== 'undefined' && getComputedStyle(oDv).visibility === 'visible'
      && typeof dvHdr !== 'undefined' && dvHdr.textContent.trim() !== '', null, { timeout: TIMEOUT });
  });

  // Pinned: holds only the assertion finding 149 breaks. DisplayLabValue.jsp attribute-encodes the result, units and
  // range into the row's title, the browser decodes the attribute, and boxover.js writes it with innerHTML.
  await s.step(POISONED_TOOLTIP_STEP, async () => {
    try {
      const built = await rowDisplay.locator('[data-xp]').evaluateAll((elements) => elements.map((el) => ({
        field: el.getAttribute('data-xp'), inTooltip: !!el.closest('#oDv') })));
      h.assert(built.length === 0,
        `Hovering the Row Display row built ${built.length} element(s) from lab text in its tooltip (data-xp ${built.map((e) => e.field).join(', ')})`);
    } finally {
      await rowDisplay.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow('lab-manual-entry-cumulative', workflow, { contextOptions: { locale: 'en-US' } });
module.exports = { workflow };
