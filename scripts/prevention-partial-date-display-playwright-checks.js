#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// prevention-partial-date-display: where a prevention dated only to the year, or the year and month,
// is shown outside the Preventions list. A CDS import stores such a date as the first day of the period
// in preventions.prevention_date plus a partial_date row (table 4, field 7: YYYY or YYYY-MM); the
// Preventions list shows "Date: 2019" (covered by prevention-partial-date).
// User path: E-Chart ▸ the Preventions box in the side bar, and Preventions ▸ the prevention's edit page.
//   1. the Preventions box row of a year-only prevention shows its year, not a day (dd-MMM-yyyy);
//   2. that prevention's edit page Summary says "on 2019", not "on 2019-01-01 00:00";
//   3. after a year-and-month prevention of the same type is added, the row shows that year and month
//      without a day;
//   4. that prevention's Summary says "on 2019-05", not "on 2019-05-01 00:00".
// The box row formats item.getDate() as dd-MMM-yyyy (LeftNavBarDisplay.jsp, EctDisplayPrevention2Action),
// and PreventionData.getPreventionById builds the Summary from the full stored date; neither reads the
// partial_date row.
// Fixtures: the owned FAKE- patient and two Inf preventions seeded by SQL with their partial_date rows.
// Cleanup deletes the patient's preventions, their ext rows and their partial_date rows, and asserts none
// remain.
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const PREVENTION = 4;
const PREVENTION_DATE = 7;
const TYPE = 'Inf';
const ROW_LINKS = '#leftNavBar a.links[onclick*="addPreventionData"], #rightNavBar a.links[onclick*="addPreventionData"]';
// A day the stored date does not have: dd-MMM-yyyy as the box prints it, or yyyy-mm-dd.
const INVENTED_DAY = /\b\d{1,2}-[A-Za-z]{3}-\d{4}\b|\b\d{4}-\d{2}-\d{2}\b/;

const STEP = {
  rowYear: "a year-only prevention's row in the E-Chart Preventions box shows its year, not a day",
  summaryYear: "a year-only prevention's edit page Summary gives its year, not a day and time",
  rowMonth: "a year-and-month prevention's row in the Preventions box shows its year and month, not a day",
  summaryMonth: "a year-and-month prevention's edit page Summary gives its year and month, not a day and time",
};

async function workflow(s) {
  const { sql, patient, provider } = s;
  const owned = `SELECT id FROM preventions WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM partial_date WHERE table_name=${PREVENTION} AND table_id IN (${owned});
      DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0', 'Owned preventions were not removed');
    assert(sql.value(`SELECT COUNT(*) FROM partial_date WHERE table_name=${PREVENTION} AND table_id IN (${owned})`) === '0',
      'Owned partial_date rows were not removed');
  });
  const seed = (date, format) => {
    const id = sql.value(`INSERT INTO preventions(demographic_no,creation_date,prevention_date,provider_no,prevention_type,
        deleted,refused,never,creator,lastUpdateDate)
      VALUES(${patient},NOW(),${sqlString(`${date} 00:00:00`)},${sqlString(provider)},${sqlString(TYPE)},'0','0','0',${sqlString(provider)},NOW());
      SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id || ''), `The ${format} prevention fixture was not created`);
    sql.execute(`INSERT INTO partial_date (table_name,table_id,field_name,format)
      VALUES (${PREVENTION},${id},${PREVENTION_DATE},${sqlString(format)})`);
    return id;
  };

  /** The text of the Preventions box row that edits record `id`: its name link and its date link. */
  const rowText = async (chart, id) => {
    const needle = `ViewAddPreventionData?id=${id}`;
    const read = () => chart.locator(ROW_LINKS).evaluateAll((links, n) => links
      .filter(link => (link.getAttribute('onclick') || '').replace(/\\x26/g, '&').includes(n))
      .map(link => (link.textContent || '').replace(/\s+/g, ' ').trim()), needle);
    await chart.locator(ROW_LINKS).first().waitFor({ state: 'attached' });
    let texts = await read();
    if (texts.length === 0) {
      // The box lists six rows first (warnings and undocumented preventions lead); the rest sit behind
      // its expander, which is where a clinician would look.
      const expander = chart.locator('#preventions img[src*="expand.gif"]').first();
      if (await expander.count() > 0) {
        await expander.click();
        await chart.waitForFunction(({ selector, n }) => [...document.querySelectorAll(selector)]
          .some(link => (link.getAttribute('onclick') || '').replace(/\\x26/g, '&').includes(n)),
        { selector: ROW_LINKS, n: needle }, { timeout: 10000 }).catch(() => {});
        texts = await read();
      }
    }
    assert(texts.length > 0, `The Preventions box has no row for the ${TYPE} prevention ${id}`);
    return texts.join(' | ');
  };

  /** The read-only Summary on the prevention's edit page, opened from the Preventions list. */
  const summaryOf = async (chart, id, label) => {
    const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), `${label}-list`);
    const link = index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${id}"]`).first();
    await link.waitFor({ state: 'visible' });
    const editor = await s.popup(index, link, label);
    const summary = (await editor.locator('#summary').inputValue()).split('\n')[0];
    await editor.close();
    await index.close();
    return summary;
  };

  const yearOnly = seed('2019-01-01', 'YYYY');
  const chart = await s.chart();

  await s.step(STEP.rowYear, async () => {
    const text = await rowText(chart, yearOnly);
    assert(/\b2019\b/.test(text) && !INVENTED_DAY.test(text),
      `The Preventions box row of a prevention dated 2019 reads ${JSON.stringify(text)}`);
  });

  await s.step(STEP.summaryYear, async () => {
    const summary = await summaryOf(chart, yearOnly, 'prevention-editor-year');
    assert(/ on 2019( |$)/.test(summary) && !INVENTED_DAY.test(summary),
      `The Summary of a prevention dated 2019 reads ${JSON.stringify(summary)}`);
  });

  const yearMonth = seed('2019-05-01', 'YYYY-MM');

  await s.step(STEP.rowMonth, async () => {
    await chart.reload({ waitUntil: 'load' });
    // The box shows the newest record of each type, now the 2019-05 one.
    const text = await rowText(chart, yearMonth);
    assert(/\b2019\b/.test(text) && /\b(05|May)\b/.test(text) && !INVENTED_DAY.test(text),
      `The Preventions box row of a prevention dated 2019-05 reads ${JSON.stringify(text)}`);
  });

  await s.step(STEP.summaryMonth, async () => {
    const summary = await summaryOf(chart, yearMonth, 'prevention-editor-month');
    assert(/ on 2019-05( |$)/.test(summary) && !INVENTED_DAY.test(summary),
      `The Summary of a prevention dated 2019-05 reads ${JSON.stringify(summary)}`);
  });
}

if (require.main === module) runWorkflow('prevention-partial-date-display', workflow);
module.exports = { workflow, STEP };
