#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): the coronary artery disease risk calculator's ANSWERS, its paste into the
 * chart note, and the Framingham/UKPDS risk calculator.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ header "Calculators" ▸ Coronary Artery
 * Disease Risk Prediction ▸ sex, age, HDL, total cholesterol, systolic BP (treated or not), smoker,
 * diabetic ▸ Calculate ▸ Paste (writes the prediction into the open chart note); and ▸ Framingham /
 * UKPDS Risk Calculator (static page) ▸ the 10-year risk table.
 * clinical-calculators proves the coronary calculator REFUSES bad ages and that two ages score
 * differently; it never asserts a point total for the risk factors, the risk level or the percentage,
 * and never presses Paste. These are the numbers a statin decision is read from.
 *
 * Asserts: for seven combinations (both sexes, treated / untreated BP, smoker, the 34 / 79 / 20 age
 * edges and the moderate-risk boundary) the Total Point Count equals the sum worked out from the Canadian
 * Framingham tables (transcribed in CASES), the risk level follows the sex-specific cut-offs, the target
 * lipid levels match the level, and the printed 10-year percentage equals the percentage in the page's own
 * grid row that contains the total; Diabetic forces HIGH >= 20%; Paste closes the calculator and puts
 * the prediction text into the chart note textarea (nothing is saved). The Framingham page computes a
 * full 7x5 risk table whose values are numeric, never fall as systolic BP or the cholesterol ratio
 * rise, and are never lower for a current smoker.
 *
 * Fixtures: the owned FAKE-PW patient (runWorkflow); the note is typed into, never saved (the
 * draft/lock rows are removed by runWorkflow). Coverage plan §2.5 calculators.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {openCalculator} = require('./clinical-calculators-playwright-checks');

const NOTE_EDITOR = '#encMainDiv textarea[name="caseNote_note"]';
const LIPIDS = {
  HIGH: ['2.0', '4.0'], MODERATE: ['3.5', '5.0'], LOW: ['5.0', '6.0'],
};

/*
 * {sex, age, hdl option, total-cholesterol option, bp option, treated, smoker} -> expected total and
 * level. Totals: age factor + HDL + total cholesterol (age band) + BP + smoker (age band), from the
 * page's Framingham tables. Female HIGH > 22, LOW < 20; male HIGH > 14, LOW < 13.
 */
const CASES = [
  {why: 'female 45, untreated BP, no smoking', sex: 'F', age: '45', hdl: '2', tch: '2', bp: '3', treated: false, smoker: false, total: 12, level: 'LOW'},
  {why: 'male 60, treated BP, smoker', sex: 'M', age: '60', hdl: '1', tch: '3', bp: '4', treated: true, smoker: true, total: 17, level: 'HIGH'},
  {why: 'male 50, untreated BP, no smoking', sex: 'M', age: '50', hdl: '3', tch: '1', bp: '2', treated: false, smoker: false, total: 8, level: 'LOW'},
  {why: 'female 70: the moderate band', sex: 'F', age: '70', hdl: '4', tch: '4', bp: '5', treated: true, smoker: true, total: 22, level: 'MODERATE'},
  {why: 'female 34: the lowest age factor, a negative total', sex: 'F', age: '34', hdl: '3', tch: '0', bp: '1', treated: false, smoker: false, total: -7, level: 'LOW'},
  {why: 'male 79: the oldest age', sex: 'M', age: '79', hdl: '2', tch: '4', bp: '3', treated: false, smoker: true, total: 17, level: 'HIGH'},
  {why: 'female 20: the youngest age, highest total', sex: 'F', age: '20', hdl: '1', tch: '4', bp: '5', treated: true, smoker: true, total: 23, level: 'HIGH'},
];

/** The grid's "Total Risk Points" cell text -> [low, high] (both inclusive; open ends are +-Infinity). */
function parseRange(text) {
  const t = text.replace(/\s+/g, ' ').trim();
  let m = t.match(/^<\s*(-?\d+)$/);
  if (m) return [-Infinity, Number(m[1]) - 1];
  m = t.match(/^(?:>=|>)\s*(-?\d+)$/);
  if (m) return [Number(m[1]), Infinity];
  m = t.match(/^(-?\d+)\s*-\s*(-?\d+)$/);
  if (m) return [Number(m[1]), Number(m[2])];
  m = t.match(/^(-?\d+)$/);
  if (m) return [Number(m[1]), Number(m[1])];
  return null;
}

async function gridPercent(page, sex, total) {
  const prefix = sex === 'F' ? '2cell' : 'cell';
  const rows = await page.evaluate(p => {
    const out = [];
    for (let n = 1; n <= 20; n += 1) {
      const l = document.getElementById(`${p}L${n}`);
      const r = document.getElementById(`${p}R${n}`);
      if (l && r) out.push([l.textContent, r.textContent]);
    }
    return out;
  }, prefix);
  const hit = rows.find(([range]) => { const r = parseRange(range); return r && total >= r[0] && total <= r[1]; });
  h.assert(hit, `The ${sex === 'F' ? 'female' : 'male'} grid has no row for a total of ${total} (${JSON.stringify(rows)})`);
  return hit[1].replace(/\s+/g, '').replace('&lt;', '<');
}

async function workflow(s) {
  const chart = await s.chart();
  const editor = chart.locator(NOTE_EDITOR).first();
  await editor.waitFor({state: 'visible'});
  let calc;
  const form = () => calc.locator('form[name="calCorArDi"]');
  const prediction = () => calc.locator('textarea[name="prediction"]');
  const run = async c => {
    await form().locator(`input[name="sex"][value="${c.sex}"]`).check();
    await form().locator('input[name="age"]').fill(c.age);
    await form().locator('select[name="HDL"]').selectOption(c.hdl);
    await form().locator('select[name="TCh"]').selectOption(c.tch);
    await form().locator('select[name="BP"]').selectOption(c.bp);
    await form().locator(`input[name="treated"][value="${c.treated ? 'yes' : 'no'}"]`).check();
    await form().locator('input[name="cigs"]').setChecked(c.smoker);
    await form().locator('input[name="diabetic"]').setChecked(false);
    await form().locator('input[type="button"][value="Calculate"]').click();
    return prediction().inputValue();
  };

  await s.step('the coronary calculator scores every risk factor, level and percentage correctly', async () => {
    calc = await openCalculator(s.context, chart, 'Coronary Artery', s.recorder, 20000);
    for (const c of CASES) {
      const text = await run(c);
      const total = text.match(/Total Point Count:\s*(-?\d+)/);
      h.assert(total && Number(total[1]) === c.total,
        `${c.why}: Total Point Count should be ${c.total}, the page says ${total ? total[1] : '(nothing)'}`);
      const risk = text.match(/10-year Risk:\s*(\w+)\s*-\s*(.+)/);
      h.assert(risk && risk[1] === c.level, `${c.why}: the risk level should be ${c.level}, the page says ${risk ? risk[1] : '(nothing)'}`);
      const [ldl, ratio] = LIPIDS[c.level];
      h.assert(text.includes(`LDL-C Level(mmol/L) - < ${ldl}`) && text.includes(`TC/HDL-C Ratio - < ${ratio}`),
        `${c.why}: the target lipid levels do not match a ${c.level} risk`);
      const percent = await gridPercent(calc, c.sex, c.total);
      h.assert(risk[2].replace(/\s+/g, '') === percent, `${c.why}: the printed 10-year risk ${risk[2]} differs from the grid row for ${c.total} points (${percent})`);
    }
  });

  await s.step('Diabetic forces HIGH >= 20% whatever the points', async () => {
    const c = CASES[4];
    await run(c);
    await form().locator('input[name="diabetic"]').check();
    await form().locator('input[type="button"][value="Calculate"]').click();
    const text = await prediction().inputValue();
    h.assert(/10-year Risk: HIGH >= 20%/.test(text), `A diabetic patient should read HIGH >= 20%, the page says ${JSON.stringify(text.trim().slice(0, 200))}`);
  });

  await s.step('Paste closes the calculator and writes the prediction into the chart note', async () => {
    await run(CASES[0]);
    const wanted = (await prediction().inputValue()).trim().split('\n')[0];
    await editor.click();
    const before = await editor.inputValue();
    const closed = calc.waitForEvent('close', {timeout: 10000});
    await calc.locator('input[type="button"][value="Paste"]').click();
    await closed;
    await chart.waitForFunction(({selector, needle}) => {
      const el = document.querySelector(selector);
      return Boolean(el && el.value.includes(needle));
    }, {selector: NOTE_EDITOR, needle: 'Total Point Count:  12'}, {timeout: 10000})
      .catch(() => { throw new Error('Paste did not put the prediction into the chart note'); });
    const after = await editor.inputValue();
    h.assert(after.startsWith(before) && after.includes(wanted), 'Paste replaced the note instead of appending the prediction');
  });

  await s.step('the Framingham risk table is numeric, monotonic in BP and cholesterol, and higher for smokers', async () => {
    const page = await openCalculator(s.context, chart, 'Framingham', s.recorder, 20000);
    try {
      const table = async () => page.evaluate(() => {
        const rows = [];
        for (let bp = 2; bp <= 8; bp += 1) {
          const row = [];
          for (let c = 1; c <= 5; c += 1) row.push(document.getElementById(`bp${bp}c${c}`).textContent.trim());
          rows.push(row);
        }
        return rows;
      });
      // Cells read "9%" or, past the table's ceiling, "≥30%".
      const num = t => parseFloat(t.replace(/[^\d.]/g, ''));
      const nonSmoker = await table();
      h.assert(nonSmoker.flat().every(t => Number.isFinite(num(t))), `The Framingham table has non-numeric cells: ${JSON.stringify(nonSmoker[0])}`);
      for (let r = 0; r < 7; r += 1) {
        for (let c = 0; c < 5; c += 1) {
          if (c > 0) h.assert(num(nonSmoker[r][c]) >= num(nonSmoker[r][c - 1]), 'Risk falls as the cholesterol ratio rises');
          if (r > 0) h.assert(num(nonSmoker[r][c]) >= num(nonSmoker[r - 1][c]), 'Risk falls as systolic BP rises');
        }
      }
      await page.locator('#cCurSmoker').check();
      const smoker = await table();
      for (let r = 0; r < 7; r += 1) {
        for (let c = 0; c < 5; c += 1) {
          h.assert(num(smoker[r][c]) >= num(nonSmoker[r][c]), `A current smoker reads a LOWER risk than a non-smoker (row ${r}, column ${c})`);
        }
      }
      h.assert(smoker.flat().some((t, i) => num(t) > num(nonSmoker.flat()[i])), 'Switching to current smoker changed no risk value');
      await page.locator('#cAge').fill('20');
      await page.locator('input.btn[value="Calculate"]').click();
      h.assert(await page.locator('#cAge').inputValue() === '30', 'An age below the table (20) was not clamped to 30');
    } finally {
      await page.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow('gap-clinical-calculators-coronary', workflow, {openPatient: true});
module.exports = {workflow, CASES};
