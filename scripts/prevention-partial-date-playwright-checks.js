#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// prevention-partial-date: preventions whose date is known only to the year, or the year and month,
// as a CDS import stores them (PreventionData.setPreventionDate: preventions.prevention_date is the
// first day of the period, and partial_date table 4 / field 7 holds YYYY or YYYY-MM).
// User path: E-Chart ▸ Preventions ▸
//   1. the year-only and year-and-month preventions are listed with "Date: 2019" and "Date: 2019-05";
//   2. the year-only prevention ▸ change only its comment ▸ Save: the change is saved and the
//      prevention keeps its year-only date.
// Step 2 fails on develop and runs last: the edit form's Date is an <input type="date">
// (AddPreventionData.jsp), which cannot hold "2019", so the box opens empty and, being required,
// refuses the save until a full date is typed. prevention-lifecycle covers editing preventions with
// full dates; no check edits a partial-dated one.
// Fixtures: the owned FAKE- patient and two Inf preventions seeded by SQL with their partial_date
// rows. Cleanup deletes the patient's preventions, their ext rows and their partial_date rows, and
// asserts none remain.
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const PREVENTION = 4;
const PREVENTION_DATE = 7;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const owned = `SELECT id FROM preventions WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM partial_date WHERE table_name=${PREVENTION} AND table_id IN (${owned});
      DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0', 'Owned preventions were not removed');
  });
  const seed = (date, format) => {
    const id = sql.value(`INSERT INTO preventions(demographic_no,creation_date,prevention_date,provider_no,prevention_type,
        deleted,refused,never,creator,lastUpdateDate)
      VALUES(${patient},NOW(),${sqlString(`${date} 00:00:00`)},${sqlString(provider)},'Inf','0','0','0',${sqlString(provider)},NOW());
      SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id || ''), `The ${format} prevention fixture was not created`);
    sql.execute(`INSERT INTO partial_date (table_name,table_id,field_name,format)
      VALUES (${PREVENTION},${id},${PREVENTION_DATE},${sqlString(format)})`);
    return id;
  };
  const yearOnly = seed('2019-01-01', 'YYYY');
  const yearMonth = seed('2019-05-01', 'YYYY-MM');
  /** The active preventions of the patient, as `date|format|comments`. */
  const active = () => sql.rows(`SELECT CONCAT(DATE(p.prevention_date),'|',IFNULL(pd.format,'full'),'|',IFNULL(x.val,''))
    FROM preventions p
    LEFT JOIN partial_date pd ON pd.table_name=${PREVENTION} AND pd.field_name=${PREVENTION_DATE} AND pd.table_id=p.id
    LEFT JOIN preventionsExt x ON x.prevention_id=p.id AND x.keyval='comments'
    WHERE p.demographic_no=${patient} AND p.deleted='0' ORDER BY p.prevention_date`).map(([row]) => row);

  const chart = await s.chart();
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  const link = id => index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${id}"]`).first();

  await s.step('year-only and year-and-month preventions are listed with their partial dates', async () => {
    for (const [id, expected] of [[yearOnly, '2019'], [yearMonth, '2019-05']]) {
      const text = (await link(id).innerText()).replace(/\s+/g, ' ');
      assert(new RegExp(`Date: ${expected}(\\s|$)`).test(text), `The Preventions page lists a ${expected} prevention as ${JSON.stringify(text.trim())}`);
    }
  });

  await s.step('changing only the comment of a year-only prevention saves it and keeps the date year-only', async () => {
    const editor = await s.popup(index, link(yearOnly), 'prevention-editor');
    const comment = `${marker} FAKE comment`;
    await editor.locator('[name="comments"]').fill(comment);
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close', { timeout: 5000 }).catch(() => {});
    if (!editor.isClosed()) {
      const box = editor.locator('#prevDate');
      const why = await box.evaluate(el => el.validationMessage || '').catch(() => '');
      assert(false, `The edit was not saved: the Date box holds ${JSON.stringify(await box.inputValue())} (the page sent `
        + `${JSON.stringify(await box.getAttribute('value'))}) and the form says ${JSON.stringify(why)}`);
    }
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND deleted='0'`, '2',
      'The edit did not leave two active preventions');
    const rows = active();
    assert(rows.includes(`2019-01-01|YYYY|${comment}`), `After the edit the active preventions are ${JSON.stringify(rows)}`);
  });
}

if (require.main === module) runWorkflow('prevention-partial-date', workflow, { openPatient: true });
module.exports = { workflow };
