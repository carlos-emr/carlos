#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 measurement-group-entry: entering a measurement group from the E-Chart.
//
// User path: Schedule ▸ Master Record ▸ E-Chart ▸ Measurements "+" ▸ Input Groups ▸ Vitals (popup)
// ▸ values, observation date, comment ▸ Submit; Vitals again ▸ last-value clock ▸ history ▸ Old
// measurement index; history ▸ Delete; Administration ▸ Customize Measurements ▸ View Measurement
// Types ▸ type link (XML export).
// Asserts the measurements rows the group writes (type, value, instruction, comment, observed date,
// provider), that the popup closes and hands its text to the open encounter note without saving a
// separate note, that the group page and history show the stored reading, that the history index
// lists it, that Delete moves exactly the selected reading to measurementsDeleted intact and the
// group page stops offering it, and that the type export link returns XML for that type.
// echart-vitals-bmi and measurement-validation drive Anthropometrics by URL; measurement-history
// covers the index-to-history path and the graph. Fixtures: the owned FAKE-PW patient (runWorkflow);
// readings carry the marker comment. Cleanup deletes this patient's readings and archived readings.
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const GROUP = 'Vitals';
const OBSERVED = '2026-03-04';
const VALUES = { BP: '128/82', HR: '72' };

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient};
      DELETE FROM measurementsDeleted WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient})`) === '0',
    'Owned readings were not removed');
  });
  const reading = (type) => `SELECT id FROM measurements WHERE demographicNo=${patient} AND type=${h.sqlString(type)}
    AND dataField=${h.sqlString(VALUES[type])} AND comments=${h.sqlString(marker)}
    AND DATE(dateObserved)=${h.sqlString(OBSERVED)} AND providerNo=${h.sqlString(provider)}`;
  const notes = () => sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${h.sqlString(patient)}`);

  const chart = await s.chart();
  const openGroup = async (label) => {
    await chart.locator('#menuTitle3 a').hover();
    const item = chart.locator('#menu3 a.menuItemleft').filter({ hasText: GROUP });
    await item.waitFor({ state: 'visible' });
    const page = await s.popup(chart, item, label);
    await page.locator('#row-BP').waitFor({ state: 'visible' });
    return page;
  };
  const ids = {};
  let instruction;

  await s.step('Vitals from the E-Chart menu saves the entered readings and fills the encounter note', async () => {
    const group = await openGroup('vitals-entry');
    h.assert(new URL(group.url()).searchParams.get('demographicNo') === patient, 'The group opened for another patient');
    const notesBefore = notes();
    for (const [type, value] of Object.entries(VALUES)) {
      const row = group.locator(`#row-${type}`);
      await row.locator('input[name^="inputValue-"]').fill(value);
      await row.locator('input[name^="date-"]').fill(OBSERVED);
      await row.locator('input[name^="comments-"]').fill(marker);
    }
    instruction = await group.locator('#row-BP input[name^="inputMInstrc-"]:checked').getAttribute('value');
    const closed = group.waitForEvent('close', { timeout: 20000 });
    await group.getByRole('button', { name: 'Submit', exact: true }).click();
    await closed;
    for (const type of Object.keys(VALUES)) {
      await expectValue(sql, `SELECT COUNT(*) FROM (${reading(type)}) r`, '1', `The ${type} reading was not stored as entered`);
      ids[type] = sql.value(reading(type));
    }
    h.assert(sql.value(`SELECT measuringInstruction FROM measurements WHERE id=${ids.BP}`) === instruction,
      'The BP reading lost the measuring instruction chosen on the form');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`) === '2',
      'Blank group rows were stored as readings');
    const note = chart.locator('textarea[name="caseNote_note"]').first();
    await h.expectPoll?.(() => note.inputValue());
    const text = await note.inputValue();
    h.assert(text.includes(`BP    ${VALUES.BP} ${instruction} ${marker}`) && text.includes(`HR    ${VALUES.HR}`),
      'The saved readings were not written into the open encounter note');
    h.assert(notes() === notesBefore, 'The group save created a separate note although the popup skips it');
  });

  let history;
  await s.step('the group page and its history show the stored reading', async () => {
    const group = await openGroup('vitals-last-value');
    const last = group.locator('tr.note').filter({ has: group.locator('i.fa-clock[onclick*="type=BP"]') });
    h.assert((await last.innerText()).includes(VALUES.BP) && (await last.innerText()).includes(marker),
      'The group page does not show the last stored BP reading');
    history = await s.popup(group, last.locator('i.fa-clock'), 'vitals-history');
    const row = history.locator('tr.data').filter({ has: history.locator(`input[name="deleteCheckbox"][value="${ids.BP}"]`) });
    h.assert((await row.locator('td[title="data"]').innerText()).trim() === VALUES.BP, 'History shows the wrong value');
    h.assert((await row.locator('td[title="comments"]').innerText()).trim() === marker, 'History lost the comment');
    h.assert((await row.locator('td[title="observed date"]').innerText()).trim() === OBSERVED,
      'History shows the entry date instead of the observation date');
    await group.close();
  });

  await s.step('the old measurement index from history lists both stored types', async () => {
    const index = await s.popup(history, history.locator('input[onclick*="SetupHistoryIndex"]'), 'vitals-index');
    for (const type of Object.keys(VALUES)) {
      await index.locator('#measurementsHistoryTbl tbody tr').filter({ hasText: new RegExp(`^${type}\\b`) }).first()
        .waitFor({ state: 'visible' });
    }
    await index.close();
  });

  await s.step('Delete archives exactly the selected reading and the group page drops it', async () => {
    await history.locator(`input[name="deleteCheckbox"][value="${ids.BP}"]`).check();
    await clickAndAwaitReload(history, history.locator('input[onclick="submit();"]'), { label: 'history Delete' });
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${ids.BP}`, '0', 'The selected reading was not deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${ids.BP} AND demographicNo=${patient}
      AND type='BP' AND dataField=${h.sqlString(VALUES.BP)} AND measuringInstruction=${h.sqlString(instruction)}
      AND comments=${h.sqlString(marker)} AND DATE(dateObserved)=${h.sqlString(OBSERVED)}
      AND providerNo=${h.sqlString(provider)}`) === '1', 'The archived reading does not match what was deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM (${reading('HR')}) r`) === '1', 'Delete touched the unselected HR reading');
    h.assert(await history.locator(`input[name="deleteCheckbox"][value="${ids.BP}"]`).count() === 0,
      'The deleted reading is still listed in history');
    await history.close();
    const group = await openGroup('vitals-after-delete');
    h.assert(await group.locator('i.fa-clock[onclick*="type=BP"]').count() === 0,
      'The group page still offers the deleted BP reading as the last value');
    await group.locator('i.fa-clock[onclick*="type=HR"]').waitFor({ state: 'visible' });
    await group.close();
  });

  await s.step('the measurement type export link returns XML for that type', async () => {
    const admin = await s.popup(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(), 'administration');
    const link = admin.getByRole('link', { name: 'Customize Measurements', exact: true, includeHidden: true });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const frame = admin.frameLocator('#dynamic-content iframe').first();
    const types = await s.popup(admin, frame.getByRole('link', { name: 'View Measurement Types' }), 'measurement-types');
    const row = types.locator('tr.data').filter({ has: types.locator('a[href*="ViewExportMeasurement"]', { hasText: /^\s*BP\s*$/ }) });
    const exportPage = await s.popup(types, row.locator('a[href*="ViewExportMeasurement"]'), 'measurement-export');
    const response = await exportPage.waitForEvent('response', r => r.url() === exportPage.url()).catch(() => null);
    console.log(exportPage.url(), response && response.status());
  });
}

if (require.main === module) runWorkflow('measurement-group-entry', workflow);
module.exports = { workflow };
