#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {openCalculator, FRACTURE_CASES, parsePrediction} = require('./clinical-calculators-playwright-checks');
const {openSecondPatient} = require('./lib/second-patient-window');
const {openChart} = require('./echart-navbar-modules-playwright-checks');
const {reloadChart}=require('./chart-session-state-two-window-isolation-playwright-checks');
const {removeMarkedPatients} = require('./lib/gap-records-fixtures');
const {chartNoteSave} = require('./lib/mutation-families');
const NOTE = '#encMainDiv textarea[name="caseNote_note"]';
/** Verify calculator paste/save/reopen with a second-patient control and owned-note cleanup. */
async function workflow(s) {
  const {sql, patient, marker} = s, controlName = marker + '-CONTROL';
  s.cleanup(() => removeMarkedPatients(sql, controlName));
  sql.execute(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(controlName)},'Workflow','1980','01','02','F','AC',${h.sqlString(s.provider)},'ON','ON','NR',NOW())`);
  const controlPatient = sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(controlName)}`);
  const mainCleanup = chartNoteSave(s), controlCleanup = chartNoteSave({...s, patient: controlPatient, marker: controlName});
  s.cleanup(() => controlCleanup.cleanup()); s.cleanup(() => mainCleanup.cleanup());
  let chart = await s.chart();
  await chart.locator(NOTE).first().fill(marker + '-NS-fracture existing text');
  const {masterPage: controlMaster} = await openSecondPatient(s,controlPatient,controlName);
  const controlChart = await openChart(s.context, controlMaster, s.recorder, 20000);
  const controlText = controlName + '-NS-control draft'; await controlChart.locator(NOTE).first().fill(controlText);
  for (const scenario of [FRACTURE_CASES[0], FRACTURE_CASES.find(value => value.sex === 'M')]) {
    await s.step(`${scenario.sex} fracture calculation pastes into its opener and saves exact note text`, async () => {
      const calc = await openCalculator(s.context, chart, 'Osteoporotic Fracture', s.recorder, 20000);
      const form = calc.locator('form[name="calCorArDi"]');
      await form.locator(`input[name="sex"][value="${scenario.sex}"]`).check();
      await form.locator('input[name="age"]').fill(scenario.age);
      await form.locator('select[name="tScore"]').selectOption({label: scenario.tScore});
      await form.locator('input[type="button"]').first().click();
      const prediction = (await calc.locator('textarea[name="prediction"]').inputValue()).trim();
      h.assert(parsePrediction(prediction).probability === scenario.probability, 'Fracture fixture probability differs from the published calculator table');
      const before = await chart.locator(NOTE).first().inputValue();
      // Focus another patient's chart before Paste to prove opener binding.
      await controlChart.bringToFront(); await controlChart.locator(NOTE).first().click();
      await Promise.all([calc.waitForEvent('close'), calc.locator('input[value="Paste"]').click()]);
      const after = await chart.locator(NOTE).first().inputValue();
      h.assert(after.startsWith(before) && after.includes(prediction), 'Fracture Paste replaced the note or lost the prediction');
      h.assert(await controlChart.locator(NOTE).first().inputValue() === controlText, 'Fracture Paste changed another patient window');
      // Manual Save intentionally aborts active drafts. Pause the next tick and
      // finish the current draft before this calculator check explicitly saves.
      await chart.evaluate(() => clearTimeout(window.autoSaveTimer));
      await chart.waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation,
        undefined, { timeout: 30000 });
      const [saved] = await Promise.all([
        chart.waitForResponse(r => r.request().method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
          && new URLSearchParams(r.request().postData() || '').get('method') === 'save'),
        chart.locator('#saveImg').first().click(),
      ]);
      await saved.finished();
      h.assert(saved.status() === 200, `Saving the pasted note returned HTTP ${saved.status()}`);
      // The note save trims surrounding whitespace and normalizes line endings.
      // Compare its complete content in hex so the SQL batch framing cannot
      // consume the trailing newline appended by the calculator's Paste.
      const storedText=after.trim().replace(/\r/g,'');
      await expectValue(sql, `SELECT HEX(REPLACE(note,CHAR(13),'')) FROM casemgmt_note WHERE demographic_no=${patient} AND LOCATE(${h.sqlString(marker+'-NS-')},note)>0 ORDER BY note_id DESC LIMIT 1`, Buffer.from(storedText).toString('hex').toUpperCase(),
        'Saved chart note differs from the exact pasted text');
      const id = sql.value(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient} AND LOCATE(${h.sqlString(marker+'-NS-')},note)>0 ORDER BY note_id DESC LIMIT 1`);
      await chart.locator(`#n${id}`).waitFor({state:'visible'});
      await reloadChart(s,chart,'fracture note reopened');
      await chart.locator(`#n${id}`).waitFor({state:'visible'});
      // The list initially shows a 50-character preview. Expand its actual
      // Maximize control before checking the persisted clinical output.
      const expand=chart.locator(`#n${id} img[name="fullViewTrigger"]`);
      if(await expand.isVisible()) {
        const [expanded]=await Promise.all([chart.waitForResponse(r=>r.request().method()==='POST'
          &&new URL(r.url()).pathname.endsWith('/CaseManagementView')&&new URLSearchParams(r.request().postData()||'').get('method')==='viewNote'),expand.click()]);
        h.assert(expanded.status()===200,'Expanding the saved fracture note failed');
        await chart.waitForFunction(id=>document.getElementById('full'+id)?.value==='true',id);
      }
      const editor=chart.locator(`#caseNote_note${id}`);
      const editable=await editor.isVisible();
      const shown=(editable?await editor.inputValue():await chart.locator(`#txt${id}`).innerText()).replace(/\s+/g,' ');
      h.assert(shown.includes(prediction.replace(/\s+/g,' ')),
        `Reopened chart does not show the persisted prediction (displayed probability ${parsePrediction(shown).probability||'absent'})`);
      // Open the saved note's real edit control before the next append.
      const edit = chart.locator(`#edit${id}`);
      if (!editable && await edit.count()) await edit.click();
      await chart.locator(NOTE).first().waitFor({state: 'visible'});
    });
  }
  await s.step('closing a calculated or refused calculator without Paste leaves both notes unchanged', async () => {
    const before = await chart.locator(NOTE).first().inputValue();
    const stored = sql.rows(`SELECT note_id,note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`);
    for (const age of ['52', '']) {
      const calc = await openCalculator(s.context, chart, 'Osteoporotic Fracture', s.recorder, 20000);
      await calc.locator('input[name="age"]').fill(age); await calc.locator('form[name="calCorArDi"] input[type="button"]').first().click();
      const prediction = await calc.locator('textarea[name="prediction"]').inputValue();
      if (!age) h.assert(!parsePrediction(prediction).probability, 'Invalid fracture input produced a probability');
      await calc.close();
      h.assert(await chart.locator(NOTE).first().inputValue() === before && await controlChart.locator(NOTE).first().inputValue() === controlText,
        'Closing without Paste altered a chart draft');
      h.assert(JSON.stringify(sql.rows(`SELECT note_id,note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`)) === JSON.stringify(stored),
        'Closing without Paste wrote a clinical note');
    }
  });
}
if (require.main === module) runWorkflow('fracture-paste-persistence', workflow, {openPatient: true});
module.exports = {workflow};
