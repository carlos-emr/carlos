#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * CDM measurement reports (Report -> CDM Report) for the Asthma Action Plan (AACP), after
 * migration V1.0.33 moved AACP from Yes/No to Provided/Revised/Reviewed (#3900, issue #3893):
 *
 *   1. A reading saved before that change keeps its "Yes/No" measuring instruction. All three
 *      CDM screens (patients who met a guideline, patients in an abnormal range, frequency of
 *      relevant tests) must still offer that legacy instruction as a selectable line, next to
 *      the current one. Before the fix the list held only the measurementType row's current
 *      instruction, so legacy readings dropped out of every per-instruction line.
 *   2. On the "met guideline" screen, a guideline of "Provided" counts a "Provided" reading.
 *      Before the fix only yes/no guidelines could ever be met.
 *   3. A stored instruction is user-entered text (the entry form's inputMInstrc-* field). The
 *      reports offer only controlled instructions: the ones the measurement-type definitions
 *      declare plus the legacy AACP "Yes/No" allowlist. A reading whose instruction is free text
 *      (here, text carrying HTML markup) is never offered on any screen, so patient-specific
 *      text cannot be disclosed clinic-wide through the report filter and no script runs
 *      (Copilot review on #3900).
 *
 * The CDM report entry page has no link in the current menus; the check opens its route,
 * /oscarReport/oscarMeasurements/SetupSelectCDMReport, and drives every form from there as
 * a user would. It owns a synthetic patient (runWorkflow), two AACP readings on that patient
 * and a FAKE- measurement group holding AACP, and removes all three. The report itself is
 * read-only.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const LEGACY_INSTRUCTION = 'Yes/No';
const CURRENT_INSTRUCTION = 'Provided/Revised/Reviewed';
const SETUP_ROUTE = '/oscarReport/oscarMeasurements/SetupSelectCDMReport';
// Breaks out of value="..." and of element content if either is rendered unencoded. The
// handler only sets a flag; an unencoded <img src=x> would also fail the strict page check
// with a request for /x.
const HOSTILE_INSTRUCTION = '"><img src=x onerror="window.__cdmInstructionXss=1">';

/** Opens the CDM entry page, picks the fixture group and continues to one of the three screens. */
async function openCdmScreen(session, group, forward, label) {
  const page = await session.context.newPage();
  await h.gotoApp(page, session.config.baseUrl, SETUP_ROUTE);
  await h.assertNotErrorPage(page, 'CDM report entry page');
  await page.locator('select[name="value(CDMgroup)"]').selectOption(group);
  // Each Continue button sets the hidden "forward" field to its screen before submitting.
  const button = page.locator(`input[type="button"][onclick*="set('${forward}')"]`);
  await Promise.all([
    page.waitForURL(/\/oscarReport\/oscarMeasurements\/SelectCDMReport(?:$|[?#])/),
    button.click(),
  ]);
  await page.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(page, label);
  return page;
}

/** Index of the AACP row, from the hidden measurement-type field each row carries. */
async function aacpRow(page, hiddenPrefix, type = 'AACP') {
  const rows = await page.locator(`input[type="hidden"][name^="value(${hiddenPrefix}"]`).evaluateAll(
    (inputs, prefix) => inputs.map((input) => ({
      index: input.name.slice(`value(${prefix}`.length, -1),
      type: input.value,
    })), hiddenPrefix);
  const row = rows.find((candidate) => candidate.type === type);
  h.assert(row && /^\d+$/.test(row.index), `the CDM screen has no ${type} row (rows: ${JSON.stringify(rows)})`);
  return row.index;
}

async function instructionChoices(page, checkboxPrefix, row) {
  return page.locator(`input[type="checkbox"][name^="value(${checkboxPrefix}${row}"]`).evaluateAll(
    (inputs) => inputs.map((input) => ({ value: input.value, checked: input.checked })));
}

async function assertNoInjectedScript(page, screen) {
  const injected = await page.evaluate(() => window.__cdmInstructionXss === 1);
  h.assert(!injected, `${screen} ran script from a stored measuring instruction`);
  h.assert(await page.locator('img[src="x"]').count() === 0, `${screen} rendered markup from a stored measuring instruction`);
}

function assertBothInstructions(choices, screen) {
  const values = choices.map((choice) => choice.value);
  h.assert(!values.includes(HOSTILE_INSTRUCTION),
    `${screen} offers a free-text stored instruction (it must offer controlled ones only); it offers ${JSON.stringify(values)}`);
  h.assert(values.includes(CURRENT_INSTRUCTION),
    `${screen} does not offer the current AACP instruction; it offers ${JSON.stringify(values)}`);
  h.assert(values.includes(LEGACY_INSTRUCTION),
    `${screen} does not offer the legacy AACP "${LEGACY_INSTRUCTION}" instruction; it offers ${JSON.stringify(values)}`);
}

/**
 * "(met/total)" from the report line for one AACP instruction and guideline. The prefix is
 * matched as a plain string (the instruction is data and may contain markup); only the fixed
 * count suffix is parsed with a literal pattern.
 */
function reportCounts(text, instruction, guideline) {
  const prefix = `AACP ${instruction} -> ${guideline} (`;
  const start = text.indexOf(prefix);
  h.assert(start >= 0, `the CDM report has no line for AACP ${instruction} with guideline ${guideline}`);
  const match = /^(\d+(?:\.\d+)?)\/(\d+(?:\.\d+)?)\)/.exec(text.slice(start + prefix.length));
  h.assert(match, `the CDM report line for AACP ${instruction} has no (met/total) counts`);
  return { met: Number(match[1]), total: Number(match[2]) };
}

async function workflow(session) {
  const { sql, patient, provider, marker } = session;
  const group = `${marker}-CDM`;

  // Set-up, not the behaviour under test: a measurement group holding AACP (the report is
  // chosen by group), and one pre-change and one current AACP reading for the owned patient.
  session.cleanup(() => {
    sql.execute(`DELETE FROM measurementGroup WHERE name=${h.sqlString(group)};
      DELETE FROM measurements WHERE demographicNo=${patient} AND type IN ('AACP', 'Z900')`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurementGroup WHERE name=${h.sqlString(group)})
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})`) === '0',
      'the CDM report fixtures were not removed');
  });
  const aacpName = sql.value("SELECT typeDisplayName FROM measurementType WHERE type='AACP' ORDER BY id LIMIT 1");
  h.assert(aacpName, 'this install has no AACP measurement type');
  sql.execute(`INSERT INTO measurementGroup (name, typeDisplayName)
    VALUES (${h.sqlString(group)}, ${h.sqlString(aacpName)})`);
  sql.execute(`INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction,
      comments, dateObserved, dateEntered)
    VALUES ('AACP', ${patient}, ${h.sqlString(provider)}, 'Yes', ${h.sqlString(LEGACY_INSTRUCTION)}, '',
      CURDATE() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY),
           ('AACP', ${patient}, ${h.sqlString(provider)}, 'Provided', ${h.sqlString(CURRENT_INSTRUCTION)}, '',
      CURDATE() - INTERVAL 2 DAY, NOW()),
           ('AACP', ${patient}, ${h.sqlString(provider)}, 'Reviewed', ${h.sqlString(HOSTILE_INSTRUCTION)}, '',
      CURDATE() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY)`);
  h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND type='AACP'`) === '3',
    'the AACP reading fixtures were not created');

  // A dedicated numeric type keeps exact counts independent of other synthetic charts.
  const numericType = 'Z900';
  h.assert(sql.value(`SELECT COUNT(*) FROM measurementType WHERE type='${numericType}'`) === '0',
    'numeric CDM fixture type already exists; refusing to overwrite it');
  const numericName = `${marker}-numeric`;
  const numericInstruction = 'numeric fixture';
  const validation = sql.value('SELECT id FROM validations WHERE isNumeric=1 AND minValue<=1 AND maxValue1>=20 ORDER BY id LIMIT 1');
  h.assert(/^\d+$/.test(validation), 'no suitable numeric validation exists');
  session.cleanup(() => {
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient} AND type='${numericType}';
      DELETE FROM measurementType WHERE type='${numericType}' AND typeDisplayName=${h.sqlString(numericName)}`);
  });
  sql.execute(`INSERT INTO measurementType (type, typeDisplayName, typeDescription, measuringInstruction, validation, createDate)
    VALUES ('${numericType}', ${h.sqlString(numericName)}, 'Numeric CDM regression', ${h.sqlString(numericInstruction)}, '${validation}', NOW());
    INSERT INTO measurementGroup (name, typeDisplayName) VALUES (${h.sqlString(group)}, ${h.sqlString(numericName)});
    INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction, comments, dateObserved, dateEntered)
    VALUES ('${numericType}', ${patient}, ${h.sqlString(provider)}, '10', ${h.sqlString(numericInstruction)}, '',
      CURDATE() - INTERVAL 1 DAY, CONCAT(CURDATE() - INTERVAL 1 DAY, ' 14:30:12')),
      ('${numericType}', ${patient}, ${h.sqlString(provider)}, '8', 'Yes/No', '',
      CURDATE() - INTERVAL 2 DAY, CONCAT(CURDATE() - INTERVAL 2 DAY, ' 14:30:12'))`);

  for (const comparator of ['>', '<']) {
    await session.step(`numeric guideline ${comparator} 9 counts the latest afternoon reading of 10`, async () => {
      const page = await openCdmScreen(session, group, 'patientWhoMetGuideline', 'numeric CDM guideline');
      const row = await aacpRow(page, 'measurementType', numericType);
      const choices = await instructionChoices(page, 'mInstrcsCheckbox', row);
      h.assert(choices.length === 1 && choices[0].value === numericInstruction,
        'a numeric measurement offers a legacy AACP instruction');
      await page.locator(`input[name="guidelineCheckbox"][value="${row}"]`).check();
      await page.locator('input[name="guidelineB"]').nth(Number(row)).fill('9');
      await page.locator(`input[name="value(aboveBelow${row})"][value="${comparator}"]`).check();
      await Promise.all([
        page.waitForURL(/\/InitializePatientsMetGuidelineCDMReport(?:$|[?#])/),
        page.locator('input[type="submit"][name="submitBtn"]').click(),
      ]);
      await h.assertNotErrorPage(page, 'numeric guideline report');
      const text = await page.locator('body').innerText();
      const met = comparator === '>' ? '1.0' : '0.0';
      for (const instruction of [numericInstruction, '']) {
        h.assert(text.includes(`${numericType} ${instruction} -> (${met}/1.0)`),
          `numeric guideline ${comparator} 9 has incorrect counts for instruction '${instruction}'`);
      }
      await page.close();
    });
  }

  await session.step('numeric range 9 to 11 includes the latest reading of 10', async () => {
    const page = await openCdmScreen(session, group, 'patientInAbnormalRange', 'numeric CDM range');
    const row = await aacpRow(page, 'measurementTypeC', numericType);
    await page.locator(`input[name="abnormalCheckbox"][value="${row}"]`).check();
    await page.locator('input[name="lowerBound"]').nth(Number(row)).fill('9');
    await page.locator('input[name="upperBound"]').nth(Number(row)).fill('11');
    await Promise.all([
      page.waitForURL(/\/InitializePatientsInAbnormalRangeCDMReport(?:$|[?#])/),
      page.locator('input[type="submit"][name="submitBtn"]').click(),
    ]);
    await h.assertNotErrorPage(page, 'numeric range report');
    const text = await page.locator('body').innerText();
    for (const instruction of [numericInstruction, '']) {
      h.assert(text.includes(`${numericType} ${instruction} -> From 9 to 11: (1.0/1.0)`),
        `numeric range has incorrect counts for instruction '${instruction}'`);
    }
    await page.close();
  });

  await session.step('"patients who met guideline" offers the legacy instruction and counts a Provided reading', async () => {
    const page = await openCdmScreen(session, group, 'patientWhoMetGuideline', 'CDM met-guideline screen');
    const row = await aacpRow(page, 'measurementType');
    const choices = await instructionChoices(page, 'mInstrcsCheckbox', row);
    assertBothInstructions(choices, 'the met-guideline screen');
    await assertNoInjectedScript(page, 'the met-guideline screen');
    h.assert(!(await page.locator('body').innerText()).includes(HOSTILE_INSTRUCTION),
      'the met-guideline screen shows a free-text stored instruction');
    h.assert(choices.every((choice) => choice.checked), 'the AACP instructions are not selected by default');

    // Only the AACP line, with a "Provided" guideline, as a user would fill it in.
    const guidelineRows = page.locator('input[name="guidelineCheckbox"]');
    const position = await guidelineRows.evaluateAll((inputs, value) => inputs.findIndex((input) => input.value === value), row);
    h.assert(position >= 0, 'the AACP line has no selection checkbox');
    await guidelineRows.nth(position).check();
    await page.locator('input[name="guidelineB"]').nth(position).fill('Provided');
    // The free-text fixture instruction is not offered, so nothing hostile is posted back (the
    // packaged install's ModSecurity/CRS front door would refuse it with 403 anyway); the
    // report runs for the controlled instructions.
    await Promise.all([
      page.waitForURL(/\/oscarReport\/oscarMeasurements\/InitializePatientsMetGuidelineCDMReport(?:$|[?#])/),
      page.locator('input[type="submit"][name="submitBtn"]').click(),
    ]);
    await h.assertNotErrorPage(page, 'CDM met-guideline report');
    const text = await page.locator('body').innerText();
    const current = reportCounts(text, CURRENT_INSTRUCTION, 'Provided');
    h.assert(current.total >= 1 && current.met >= 1,
      `a guideline of Provided counted ${current.met} of ${current.total} current AACP readings; the fixture reading is Provided`);
    const legacy = reportCounts(text, LEGACY_INSTRUCTION, 'Provided');
    h.assert(legacy.total >= 1, 'the legacy Yes/No AACP reading is missing from its report line');
    await assertNoInjectedScript(page, 'the met-guideline report');
    await page.close();
  });

  await session.step('"patients in abnormal range" offers the legacy instruction', async () => {
    const page = await openCdmScreen(session, group, 'patientInAbnormalRange', 'CDM abnormal-range screen');
    const row = await aacpRow(page, 'measurementTypeC');
    assertBothInstructions(await instructionChoices(page, 'mInstrcsCheckboxC', row), 'the abnormal-range screen');
    await assertNoInjectedScript(page, 'the abnormal-range screen');
    await page.close();
  });

  await session.step('"frequency of relevant tests" offers the legacy instruction', async () => {
    const page = await openCdmScreen(session, group, 'freqencyOfReleventTests', 'CDM frequency screen');
    const row = await aacpRow(page, 'measurementTypeD');
    assertBothInstructions(await instructionChoices(page, 'mInstrcsCheckboxD', row), 'the frequency screen');
    await assertNoInjectedScript(page, 'the frequency screen');
    await page.close();
  });
}

if (require.main === module) runWorkflow('cdm-measurement-report', workflow);
module.exports = { workflow };
