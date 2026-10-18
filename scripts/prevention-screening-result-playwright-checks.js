#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Screening preventions record a result. The screening layout (PAP, MAM, FOBT, PSA, HPV-CERVIX, BMD, COLONOSCOPY and
 * the other PAPMAM items in PreventionItems.xml) has a Result box, Pending / Normal / Abnormal / Other with a reason,
 * that the injection-layout checks never reach.
 * User path: E-Chart ▸ Preventions ▸ PAP ▸ Completed, date, Result Abnormal and a reason ▸ Save; the list; the record ▸
 * Result Normal ▸ Save; the record ▸ Set Next Date ▸ Never Remind with a reason ▸ Save; the record again.
 * Asserts: a new screening opens with Result Pending; Abnormal and its reason reach preventionsExt, and the list marks
 * the record "result:abnormal" on the abnormal colour; the reopened form shows both; Normal archives the abnormal record
 * (its result kept) and stores one replacement that the list no longer marks; Never Remind stores never=1 with no next
 * date and keeps its reason, and the reopened form shows the box ticked, the next date off and the reason.
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup.
 */
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TYPE = 'PAP';
const ABNORMAL_COLOUR = 'rgb(238, 95, 91)'; // index.jsp r(): #ee5f5b for an abnormal result

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
    WHERE p.demographic_no=${patient}; DELETE FROM preventions WHERE demographic_no=${patient}`));
  const chart = await s.chart();
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  let editor;
  let id;
  const active = () => {
    const rows = sql.rows(`SELECT id FROM preventions WHERE demographic_no=${patient}
      AND prevention_type=${sqlString(TYPE)} AND deleted=0`);
    assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]), `Expected one active ${TYPE} record, found ${rows.length}`);
    return rows[0][0];
  };
  const ext = (prevention, key) => sql.value(`SELECT IFNULL((SELECT GROUP_CONCAT(val ORDER BY id SEPARATOR '|')
    FROM preventionsExt WHERE prevention_id=${prevention} AND keyval=${sqlString(key)}),'(none)')`);
  const record = () => index.locator(`.preventionProcedure[onclick*="id=${id}"]`).first();
  async function open() { editor = await s.popup(index, record(), 'prevention-editor'); }
  async function save() {
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
  }
  async function replaced(previous) {
    const now = active();
    assert(now !== previous, 'Saving the edit did not store a replacement record');
    await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${previous}`, '1', 'The edited record was not archived');
    id = now;
    await record().waitFor({ state: 'visible' });
  }
  async function shown() {
    const line = record().locator('p').first();
    return { text: (await line.innerText()).replace(/\s+/g, ' '), colour: await line.evaluate(p => getComputedStyle(p).backgroundColor) };
  }
  const reason = `${marker} repeat in 6 months`;
  const neverReason = `${marker} hysterectomy`;

  await s.step('a new PAP opens with Result Pending; Completed with Result Abnormal and a reason is stored', async () => {
    const link = index.locator(`a[onclick*="ViewAddPreventionData"][onclick*="prevention=${TYPE}&"]`).first();
    editor = await s.popup(index, link, 'prevention-editor');
    assert(await editor.locator('[name="prevention"]').inputValue() === TYPE, `The list opened a form for another type than ${TYPE}`);
    assert(await editor.locator('[name="result"][value="pending"]').isChecked(), 'A new screening did not open with Result Pending');
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill('2026-03-04');
    await editor.locator('[name="result"][value="abnormal"]').check();
    await editor.locator('[name="reason"]').fill(reason);
    await save();
    id = active();
    await expectValue(sql, `SELECT CONCAT(refused,'|',DATE(prevention_date),'|',never) FROM preventions WHERE id=${id}`,
      '0|2026-03-04|0', 'The completed screening was not stored with its date');
    assert(ext(id, 'result') === 'abnormal', `The stored result is ${ext(id, 'result')}, not abnormal`);
    assert(ext(id, 'reason') === reason, `The stored reason is ${ext(id, 'reason')}`);
  });

  await s.step('the list marks the record abnormal on the abnormal colour', async () => {
    await record().waitFor({ state: 'visible' });
    const { text, colour } = await shown();
    assert(text.includes('result:abnormal'), `The list does not mark the record abnormal: ${text.slice(0, 160)}`);
    assert(text.includes('2026-03-04'), `The list does not show the screening date: ${text.slice(0, 160)}`);
    assert(colour === ABNORMAL_COLOUR, `The abnormal record is on ${colour}, not the abnormal colour`);
  });

  await s.step('reopened, the form shows Abnormal and the reason; Normal stores one replacement the list no longer marks', async () => {
    await open();
    assert(await editor.locator('[name="result"][value="abnormal"]').isChecked(), 'The reopened screening lost its Abnormal result');
    assert(await editor.locator('[name="reason"]').inputValue() === reason, 'The reopened screening lost its reason');
    await editor.locator('[name="result"][value="normal"]').check();
    await editor.locator('[name="reason"]').fill('');
    const previous = id;
    await save();
    await replaced(previous);
    assert(ext(previous, 'result') === 'abnormal', 'Archiving the abnormal record changed its stored result');
    assert(ext(id, 'result') === 'normal', `The replacement's result is ${ext(id, 'result')}, not normal`);
    assert(['', '(none)'].includes(ext(id, 'reason')), `The cleared reason was stored as ${ext(id, 'reason')}`);
    const { text, colour } = await shown();
    assert(!text.includes('result:'), `The list still marks the normal record: ${text.slice(0, 160)}`);
    assert(colour !== ABNORMAL_COLOUR, 'The normal record is still on the abnormal colour');
  });

  await s.step('Never Remind with a reason is stored without a next date and reopens ticked with its reason', async () => {
    await open();
    await editor.getByRole('link', { name: /set next date/i }).first().click();
    await editor.locator('#nextDateDiv').waitFor({ state: 'visible' });
    await editor.locator('#neverWarn').check();
    assert(await editor.locator('#nextDate').isDisabled(), 'Ticking Never Remind left the next date editable');
    await editor.locator('[name="neverReason"]').fill(neverReason);
    const previous = id;
    await save();
    await replaced(previous);
    await expectValue(sql, `SELECT CONCAT(never,'|',IFNULL(next_date,'none')) FROM preventions WHERE id=${id}`,
      '1|none', 'Never Remind was not stored, or a next date was');
    assert(ext(id, 'neverReason') === neverReason, `The stored never-remind reason is ${ext(id, 'neverReason')}`);
    assert(ext(id, 'result') === 'normal', 'Setting Never Remind changed the stored result');
    await open();
    assert(await editor.locator('#neverWarn').isChecked(), 'The reopened screening lost Never Remind');
    assert(await editor.locator('#nextDate').isDisabled(), 'The reopened screening offers a next date despite Never Remind');
    assert(await editor.locator('[name="neverReason"]').inputValue() === neverReason, 'The reopened screening lost the never-remind reason');
    await editor.close();
  });
}

if (require.main === module) runWorkflow('prevention-screening-result', workflow);
module.exports = { workflow };
