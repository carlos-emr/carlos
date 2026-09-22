#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §3.4: refused → completed → ineligible → deleted prevention.
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

function activePrevention(sql, patient) {
  const rows = sql.rows(`SELECT id FROM preventions WHERE demographic_no=${patient}
    AND prevention_type='Inf' AND deleted=0`);
  assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]),
    'Expected exactly one active prevention for the owned patient');
  return rows[0][0];
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  const chart = await s.chart();
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  let editor;
  let id;
  const link = () => index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${id}"]`).first();
  const status = () => `SELECT CONCAT(refused,'|',deleted,'|',DATE(prevention_date)) FROM preventions WHERE id=${id} AND demographic_no=${patient}`;
  async function save() {
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
  }
  async function replacement(previous, previousStatus) {
    await expectValue(sql, `SELECT CONCAT(refused,'|',deleted) FROM preventions WHERE id=${previous} AND demographic_no=${patient}`,
      `${previousStatus}|1`, 'Amendment did not archive its original prevention');
    id = activePrevention(sql, patient);
    assert(/^[1-9]\d*$/.test(id) && id !== previous, 'Amendment did not create exactly one replacement');
    await link().waitFor({ state: 'visible' });
  }
  async function open() { editor = await s.popup(index, link(), 'prevention-editor'); }
  await s.step('record a refused prevention and display it in the opener', async () => {
    await index.locator('#immunization').fill('Fluzone');
    editor = await s.popup(index, index.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first(), 'prevention-editor');
    assert(await editor.locator('[name="prevention"]').inputValue() === 'Inf', 'The picker opened a different prevention');
    await editor.locator('[name="given"][value="refused"]').check();
    await editor.locator('#prevDate').fill('2026-01-02');
    await editor.locator('[name="comments"]').fill(marker);
    await save();
    id = activePrevention(sql, patient);
    assert(/^[1-9]\d*$/.test(id), 'Refused prevention was not recorded');
    await expectValue(sql, status(), '1|0|2026-01-02', 'Refusal status/date did not persist');
    await link().waitFor({ state: 'visible' });
  });
  await s.step('reopen and correct to completed, preserving comments', async () => {
    await open();
    assert(await editor.locator('[name="given"][value="refused"]').isChecked(), 'Reopened refusal changed status');
    assert(await editor.locator('[name="comments"]').inputValue() === marker, 'Prevention comments disappeared');
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('[name="comments"]').fill(`${marker}-EDIT`);
    const previous = id;
    await save();
    await replacement(previous, '1');
    await expectValue(sql, status(), '0|0|2026-01-02', 'Completed correction did not persist');
    assert(sql.value(`SELECT COUNT(*) FROM preventionsExt WHERE prevention_id=${id} AND keyval='comments'
      AND val=${sqlString(`${marker}-EDIT`)}`) === '1', 'Edited comments were not stored');
  });
  await s.step('ineligible status reopens and delete retains the audit record', async () => {
    await open();
    await editor.locator('[name="given"][value="ineligible"]').check();
    const previous = id;
    await save();
    await replacement(previous, '0');
    await expectValue(sql, status(), '2|0|2026-01-02', 'Ineligible status did not persist');
    await open();
    assert(await editor.locator('[name="given"][value="ineligible"]').isChecked(), 'Ineligible status did not reopen');
    await editor.locator('input[name="delete"]').click();
    await expectValue(sql, status(), '2|1|2026-01-02', 'Delete lost the prevention or failed to archive it');
    await link().waitFor({ state: 'detached' });
  });
}
if (require.main === module) runWorkflow('prevention-lifecycle', workflow);
module.exports = { workflow, activePrevention };
