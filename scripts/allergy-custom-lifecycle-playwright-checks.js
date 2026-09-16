#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §3.2: non-drug allergy with real confirmation dialogs,
// amendment retaining the original, and cancelled/accepted archive.
const { assert, withExpectedDialogs } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient}`));
  const chart = await s.chart();
  const page = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergy-list');
  const description = marker.slice(-16).toUpperCase();
  let original;
  let amended;
  const rows = () => `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`;
  const form = page.locator('#RxAddAllergyForm');
  await s.step('cancel custom allergy, then accept and save a non-drug reaction', async () => {
    await page.locator('#searchString').fill(description);
    const cancel = await withExpectedDialogs(page, () => page.locator('input[value="Custom Allergy"]').click(), { accept: false });
    assert(cancel.length === 1 && cancel[0].type === 'confirm', 'Custom allergy did not ask for confirmation');
    assert(sql.value(rows()) === '0', 'Cancelled custom allergy wrote a row');
    const accept = await withExpectedDialogs(page, () => page.locator('input[value="Custom Allergy"]').click());
    assert(accept.length === 1 && accept[0].type === 'confirm', 'Custom allergy bypassed confirmation');
    await form.waitFor({ state: 'visible' });
    assert(await form.locator('[name="formDemographicNo"]').inputValue() === patient, 'Allergy editor lost patient context');
    await form.locator('#reactionDescription').fill(marker);
    await form.locator('[name="nonDrug"]').selectOption('on');
    await form.locator('[name="severityOfReaction"]').selectOption('3');
    await form.locator('[name="onSetOfReaction"]').selectOption('1');
    await form.locator('[name="lifeStage"]').selectOption('A');
    await form.locator('#startDate').fill('2026-01-02');
    await form.locator('input[type="submit"]').click();
    await expectValue(sql, rows(), '1', 'Custom allergy was not saved');
    original = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT CONCAT(nonDrug,'|',severity_of_reaction,'|',reaction) FROM allergies WHERE allergyid=${original}`)
      === `1|3|${marker}`, 'Non-drug flag, severity or reaction was silently lost');
    await page.locator(`#allergy_${original}`).waitFor({ state: 'visible' });
  });
  await s.step('amend creates a replacement and archives the original', async () => {
    await page.locator(`#allergy_${original} a.modifyAllergyLink`).click();
    await form.waitFor({ state: 'visible' });
    assert(await form.locator('#allergyToArchive').inputValue() === original, 'Amendment lost the original allergy');
    assert(await form.locator('#reactionDescription').inputValue() === marker, 'Reopened reaction was lost');
    await form.locator('#reactionDescription').fill(`${marker}-EDIT`);
    await form.locator('[name="severityOfReaction"]').selectOption('1');
    await form.locator('input[type="submit"]').click();
    await expectValue(sql, rows(), '2', 'Amendment overwrote history instead of creating a replacement');
    assert(sql.value(`SELECT archived FROM allergies WHERE allergyid=${original}`) === '1', 'Original reaction remains active');
    amended = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND archived=0`);
    assert(/^[1-9]\d*$/.test(amended) && amended !== original, 'Amendment did not leave one active replacement');
    assert(sql.value(`SELECT CONCAT(nonDrug,'|',severity_of_reaction,'|',reaction) FROM allergies WHERE allergyid=${amended}`)
      === `1|1|${marker}-EDIT`, 'Amendment lost clinical details');
    await page.locator(`#allergy_${amended}`).waitFor({ state: 'visible' });
  });
  await s.step('cancelled archive preserves the row; accepted archive removes it from the active list', async () => {
    const remove = page.locator(`#allergy_${amended} a.deleteAllergyLink`);
    const cancel = await withExpectedDialogs(page, () => remove.click(), { accept: false });
    assert(cancel.length === 1 && cancel[0].type === 'confirm', 'Archive did not ask for confirmation');
    assert(sql.value(`SELECT archived FROM allergies WHERE allergyid=${amended}`) === '0', 'Cancelled archive changed the allergy');
    const accept = await withExpectedDialogs(page, () => remove.click());
    assert(accept.length === 1 && accept[0].type === 'confirm', 'Archive bypassed confirmation');
    await expectValue(sql, `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND archived=1`, '2',
      'Archive did not preserve both historical rows');
    await page.locator(`#allergy_${amended}`).waitFor({ state: 'detached' });
  });
}
if (require.main === module) runWorkflow('allergy-custom-lifecycle', workflow);
module.exports = { workflow };
