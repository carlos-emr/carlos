#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// prevention-partial-date: preventions whose date is known only to the year, or the year and month,
// as a CDS import stores them (PreventionData.setPreventionDate: preventions.prevention_date is the
// first day of the period, and partial_date table 4 / field 7 holds YYYY or YYYY-MM).
// User path: E-Chart ▸ Preventions ▸
//   1. the year-only and year-and-month preventions are listed with "Date: 2019" and "Date: 2019-05";
//   2. a prevention with a full date still opens with a date picker holding that date;
//   3. the year-and-month prevention ▸ type the full date 2019-05-20 ▸ Save: it is saved as that day;
//   4. the year-only prevention ▸ change only its comment ▸ Save: the change is saved and the
//      prevention keeps its year-only date;
//   5. the year-only prevention ▸ type 2019-13 ▸ Save: the form refuses it and nothing is saved;
//   6. the year-only prevention ▸ type the impossible day 2019-02-31 ▸ Save: the form refuses it and nothing
//      is saved; then 2019-02-28 in the same editor ▸ Save: that day is saved.
// Step 4 fails on develop: the edit form's Date is an <input type="date"> (AddPreventionData.jsp),
// which cannot hold "2019", so the box opens empty and, being required, refuses the save until a full
// date is typed (finding 302). Steps 5 and 6 need the year-only box, so they run after step 4. Step 6 also needs
// #4815's calendar check: the box's pattern alone lets 2019-02-31 through, and the server refuses it with HTTP 400.
// The server side is fine: echart-prevention-row-links
// posts the edit form's fields with prevDate=2026-09 directly and asserts the replacement keeps
// YYYY-MM; this check covers the form path. prevention-lifecycle covers edits with full dates.
// Fixtures: the owned FAKE- patient, two Inf preventions seeded by SQL with their partial_date rows and
// one with a full date. Cleanup deletes the patient's preventions, their ext rows and their partial_date rows, and
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
  const fullDate = sql.value(`INSERT INTO preventions(demographic_no,creation_date,prevention_date,provider_no,prevention_type,
      deleted,refused,never,creator,lastUpdateDate)
    VALUES(${patient},NOW(),'2019-06-15 00:00:00',${sqlString(provider)},'Inf','0','0','0',${sqlString(provider)},NOW());
    SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(fullDate || ''), 'The full-date prevention fixture was not created');
  /** The active preventions of the patient, as `date|format|comments`. */
  const active = () => sql.rows(`SELECT CONCAT(DATE(p.prevention_date),'|',IFNULL(pd.format,'full'),'|',IFNULL(x.val,''))
    FROM preventions p
    LEFT JOIN partial_date pd ON pd.table_name=${PREVENTION} AND pd.field_name=${PREVENTION_DATE} AND pd.table_id=p.id
    LEFT JOIN preventionsExt x ON x.prevention_id=p.id AND x.keyval='comments'
    WHERE p.demographic_no=${patient} AND p.deleted='0' ORDER BY p.prevention_date`).map(([row]) => row);

  const chart = await s.chart();
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  const link = id => index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${id}"]`).first();
  /** Opens a prevention's edit form. A save reloads the Preventions page by itself, so wait for the
   *  prevention's link there, and reload only if it has not appeared. */
  const edit = async (id, label) => {
    await index.waitForLoadState('load');
    if (!(await link(id).waitFor({ state: 'visible', timeout: 10000 }).then(() => true, () => false))) {
      await index.reload({ waitUntil: 'load' });
      await link(id).waitFor({ state: 'visible', timeout: 10000 });
    }
    return s.popup(index, link(id), label);
  };
  /** Clicks Save; resolves true when the editor closed (saved), false when the form kept it open. */
  const save = async editor => {
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close', { timeout: 5000 }).catch(() => {});
    return editor.isClosed();
  };
  const activeCount = () => sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND deleted='0'`);

  await s.step('year-only and year-and-month preventions are listed with their partial dates', async () => {
    for (const [id, expected] of [[yearOnly, '2019'], [yearMonth, '2019-05']]) {
      const text = (await link(id).innerText()).replace(/\s+/g, ' ');
      assert(new RegExp(`Date: ${expected}(\\s|$)`).test(text), `The Preventions page lists a ${expected} prevention as ${JSON.stringify(text.trim())}`);
    }
  });

  await s.step('a prevention with a full date still opens with a date picker holding that date', async () => {
    const editor = await edit(fullDate, 'prevention-editor-full');
    const box = editor.locator('#prevDate');
    const found = { type: await box.getAttribute('type'), value: await box.inputValue() };
    assert(found.type === 'date' && found.value === '2019-06-15', `The full-date prevention's Date box is ${JSON.stringify(found)}`);
    await editor.close();
  });

  await s.step('typing a full date over a year-and-month prevention saves that day', async () => {
    const editor = await edit(yearMonth, 'prevention-editor-month');
    await editor.locator('#prevDate').fill('2019-05-20');
    assert(await save(editor), 'The edit with a full date was not saved');
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE id=${yearMonth} AND deleted='1'`, '1', 'The edit did not replace the prevention');
    const rows = active();
    assert(rows.includes('2019-05-20|full|'), `After the edit the active preventions are ${JSON.stringify(rows)}`);
  });

  let yearOnlyNow = yearOnly;
  await s.step('changing only the comment of a year-only prevention saves it and keeps the date year-only', async () => {
    const editor = await edit(yearOnly, 'prevention-editor');
    const comment = `${marker} FAKE comment`;
    const box = editor.locator('#prevDate');
    const offered = await box.inputValue();
    await editor.locator('[name="comments"]').fill(comment);
    if (!(await save(editor))) {
      const why = await box.evaluate(el => el.validationMessage || '').catch(() => '');
      assert(false, `The edit was not saved: the Date box holds ${JSON.stringify(offered)} (the page sent `
        + `${JSON.stringify(await box.getAttribute('value'))}) and the form says ${JSON.stringify(why)}`);
    }
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE id=${yearOnly} AND deleted='1'`, '1', 'The edit did not replace the prevention');
    const rows = active();
    assert(offered === '2019' && rows.includes(`2019-01-01|YYYY|${comment}`),
      `The box offered ${JSON.stringify(offered)}; after the edit the active preventions are ${JSON.stringify(rows)}`);
    yearOnlyNow = sql.value(`SELECT p.id FROM preventions p JOIN preventionsExt x ON x.prevention_id=p.id AND x.keyval='comments'
      WHERE p.demographic_no=${patient} AND p.deleted='0' AND x.val=${sqlString(comment)}`);
  });

  await s.step('a year-only prevention\'s Date box refuses an impossible month and saves nothing', async () => {
    const editor = await edit(yearOnlyNow, 'prevention-editor-invalid');
    const before = activeCount();
    const box = editor.locator('#prevDate');
    await box.fill('2019-13');
    const saved = await save(editor);
    const why = saved ? '' : await box.evaluate(el => el.validationMessage || '');
    assert(!saved && why !== '' && activeCount() === before
      && sql.value(`SELECT deleted FROM preventions WHERE id=${yearOnlyNow}`) === '0',
      `2019-13 was ${saved ? 'saved' : `refused with ${JSON.stringify(why)}`}, and the active count went from ${before} to ${activeCount()}`);
    await editor.close();
  });

  await s.step('a year-only prevention\'s Date box refuses an impossible day, then saves the corrected day', async () => {
    const editor = await edit(yearOnlyNow, 'prevention-editor-impossible-day');
    const before = activeCount();
    const box = editor.locator('#prevDate');
    await box.fill('2019-02-31');
    const saved = await save(editor);
    const why = saved ? '' : await box.evaluate(el => el.validationMessage || '');
    assert(!saved && why !== '' && activeCount() === before
      && sql.value(`SELECT deleted FROM preventions WHERE id=${yearOnlyNow}`) === '0',
      `2019-02-31 was ${saved ? 'saved' : `refused with ${JSON.stringify(why)}`}, and the active count went from ${before} to ${activeCount()}`);
    // The same editor, corrected: the refusal must not block the next submit. An edit replaces the
    // prevention: the old row is marked deleted and a new one is added.
    await box.fill('2019-02-28');
    assert(await save(editor), 'The corrected 2019-02-28 was not saved');
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE id=${yearOnlyNow} AND deleted='1'`, '1', 'The corrected edit did not replace the prevention');
    const rows = active();
    assert(rows.some(row => row.startsWith('2019-02-28|full|')), `After the corrected edit the active preventions are ${JSON.stringify(rows)}`);
  });
}

if (require.main === module) runWorkflow('prevention-partial-date', workflow, { openPatient: true });
module.exports = { workflow };
