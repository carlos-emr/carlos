#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// prevention-print-selection: what the Preventions page prints is what the clinician picks.
// User path: E-Chart ▸ Preventions ▸ Enable Print ▸ untick / tick each prevention's box ▸ Print, or
// Print Immunizations Only (the button Enable Print adds).
//   1. Enable Print shows one ticked box per recorded prevention and adds Print Immunizations Only;
//   2. with the immunization unticked, Print returns a PDF of the screening only, headed
//      "Immunizations and Screenings", which says the patient has no immunization entries;
//   3. with both ticked, Print Immunizations Only returns a PDF headed "Immunizations" that carries
//      the immunization and leaves the screening out;
//   4. with no box ticked, Print warns that a prevention must be selected and posts nothing.
// Each PDF is read with pdftotext. prevention-admin covers the plain Enable Print ▸ Print of one
// immunization; nothing else prints from this page.
// Fixtures: the owned FAKE- patient and two preventions seeded for it, an immunization (Inf) and a
// screening (Tuberculosis, under "Screenings" in PreventionItems.xml), each on its own date.
// Cleanup deletes the patient's preventions and their ext rows and asserts none remain.
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const { assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickDownloadsOrOpens } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const IMMUNIZATION = { type: 'Inf', date: '2026-02-03' };
const SCREENING = { type: 'Tuberculosis', date: '2026-03-04' };

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  s.cleanup(() => {
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0', 'Owned preventions were not removed');
  });
  for (const { type, date } of [IMMUNIZATION, SCREENING]) {
    const id = sql.value(`INSERT INTO preventions(demographic_no,creation_date,prevention_date,provider_no,prevention_type,
        deleted,refused,never,creator,lastUpdateDate)
      VALUES(${patient},NOW(),${sqlString(date)},${sqlString(provider)},${sqlString(type)},'0','0','0',${sqlString(provider)},NOW());
      SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id), `The ${type} fixture was not created`);
  }

  const chart = await s.chart();
  const openIndex = async () => {
    const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
    await index.locator('input[name="printButton"]').waitFor({ state: 'visible' });
    return index;
  };
  /** The print box of one prevention type: its value is the row index that names the type in preventionHeader<i>. */
  async function boxFor(index, type) {
    const row = await index.evaluate(name => {
      const header = [...document.querySelectorAll('input[id^="preventionHeader"]')].find(input => input.value === name);
      return header ? header.id.slice('preventionHeader'.length) : null;
    }, type);
    assert(row !== null, `The Preventions page lists no ${type} row to print`);
    return index.locator(`input[name="printHP"][value="${row}"]`);
  }
  async function printedText(index, button, label) {
    const outcome = await clickDownloadsOrOpens(index, button, { context: s.context, recorder: s.recorder, label, timeout: 20000 });
    assert(outcome.kind === 'download', `${label} did not deliver a file`);
    const bytes = fs.readFileSync(await outcome.download.path());
    assert(bytes.subarray(0, 4).toString('latin1') === '%PDF', `${label} did not return PDF bytes`);
    const text = execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8' });
    assert(text.includes(marker), `${label} printed another patient`);
    return text;
  }
  let index = await openIndex();

  await s.step('Enable Print shows a ticked box for each recorded prevention and adds Print Immunizations Only', async () => {
    const boxes = index.locator('input[name="printHP"]');
    assert(await boxes.count() === 2, `The page offers ${await boxes.count()} print boxes for two recorded preventions`);
    assert(!(await boxes.first().isVisible()), 'The print boxes show before Enable Print');
    await index.locator('input[name="printButton"]').click();
    assert(await index.locator('input[name="printButton"]').inputValue() === 'Print', 'Enable Print did not switch the button to Print');
    for (const { type } of [IMMUNIZATION, SCREENING]) {
      const own = await boxFor(index, type);
      assert(await own.isVisible() && await own.isChecked(), `The ${type} print box is not shown ticked`);
    }
    assert(await index.locator('input[name="printImmButton"]').count() === 1, 'Enable Print did not add Print Immunizations Only');
  });

  await s.step('Print with the immunization unticked prints the screening alone', async () => {
    await (await boxFor(index, IMMUNIZATION.type)).uncheck();
    const text = await printedText(index, index.locator('input[name="printButton"]'), 'prevention-print-screening');
    assert(text.includes('Immunizations and Screenings'), 'The print is not headed "Immunizations and Screenings"');
    assert(text.includes(SCREENING.type) && text.includes(SCREENING.date), 'The ticked screening is missing from the print');
    assert(!text.includes(IMMUNIZATION.date), 'The unticked immunization was printed');
    assert(text.includes('No immunization entries for this patient'), 'The print does not say there are no immunization entries');
  });

  await s.step('Print Immunizations Only leaves the ticked screening out', async () => {
    await (await boxFor(index, IMMUNIZATION.type)).check();
    const text = await printedText(index, index.locator('input[name="printImmButton"]'), 'prevention-print-immunizations');
    assert(/(^|\n)\s*Immunizations\s*(\n|$)/.test(text) && !text.includes('Immunizations and Screenings'),
      'The immunizations-only print is not headed "Immunizations"');
    assert(text.includes(IMMUNIZATION.type) && text.includes(IMMUNIZATION.date), 'The immunization is missing from the immunizations-only print');
    assert(!text.includes(SCREENING.date) && !text.includes(SCREENING.type), 'The immunizations-only print carries the screening');
  });

  await s.step('Print with no box ticked warns and posts nothing', async () => {
    for (const { type } of [IMMUNIZATION, SCREENING]) await (await boxFor(index, type)).uncheck();
    const posts = s.recorder.requestLog.filter(entry => entry.method === 'POST').length;
    const dialogs = await withExpectedDialogs(index, () => index.locator('input[name="printButton"]').click(), { accept: true });
    assert(dialogs.length === 1 && /check at least one prevention/.test(dialogs[0].text),
      `Print with nothing ticked showed ${JSON.stringify(dialogs.map(d => d.text))}`);
    // onPrint() returns before submit(), so a post would already be on its way; a short wait lets it reach the log.
    await index.waitForTimeout(500);
    assert(s.recorder.requestLog.filter(entry => entry.method === 'POST').length === posts, 'Print with nothing ticked posted the form');
    await index.close();
  });
}

if (require.main === module) runWorkflow('prevention-print-selection', workflow, { openPatient: true });
module.exports = { workflow };
