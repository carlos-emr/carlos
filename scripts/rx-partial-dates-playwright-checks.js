#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-partial-dates: a prescription whose start or written date is known only to the month or the year
// (the card's "more" Start Date and Written Date take yyyy, yyyy-mm or yyyy-mm-dd).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ type a name ▸ Custom Drug ▸
// Instructions ▸ the card's "more" link ▸ Start Date 2019-05, Written Date 2019 ▸ Save Only, then
//   1. the drugs row stores the first day of each period and partial_date (table 2) records the
//      precision: start date (field 5) YYYY-MM, written date (field 3) YYYY;
//   2. the drug profile lists the start date as 2019-05;
//   3. the drug row ▸ its static script page (rx/ViewStaticScript2, which writes dates as yyyy/mm/dd) lists
//      Start Date 2019/05 and Written Date 2019, and the row's record (rx/ViewDisplayRxRecord) shows Rx Date
//      2019-05 and Written Date 2019.
// The partial dates are parsed in RxWriteScript2Action (partialDateDao.getFormat) and written with the
// drug (PartialDate.DRUGS_STARTDATE / DRUGS_WRITTENDATE); ListDrugs.jsp, StaticScript2.jsp and
// DisplayRxRecord.jsp read them back. No other Rx check types a partial date (rx-card-options types
// full ones).
// Fixtures: the owned synthetic patient and a marker-named custom drug. Cleanup removes the patient's
// drugs, their partial_date rows, prescriptions, drugReason and DigitalSignature rows, and asserts none
// remain. No DrugRef data is needed.
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');
const { masterRecordLink } = require('./lib/master-record-link');

const DRUGS = 2;
const START_DATE = 5;
const WRITTEN_DATE = 3;
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => {
    const drugIds = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(drugIds.every(id => /^[1-9]\d*$/.test(id)), 'Owned drug id is invalid');
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      ${drugIds.length ? `DELETE FROM partial_date WHERE table_name=${DRUGS} AND table_id IN (${drugIds.join(',')});` : ''}
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(), 'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  let drug;

  await s.step('a month start date and a year written date are saved with their precision', async () => {
    const card = await stageCustomDrug(rx, `${marker} FAKE partial-date drug`);
    await rx.locator(`#instructions_${card}`).fill('1 tab PO OD x 30 days');
    const [parsed] = await Promise.all([
      rx.waitForResponse(isPost('/rx/UpdateScript')),
      rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
    ]);
    h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
    await rx.locator(`#moreLessWord_${card}`).click();
    await rx.locator(`#rx_more_${card}`).waitFor({ state: 'visible' });
    await rx.locator(`#rxDate_${card}`).fill('2019-05');
    await rx.locator(`#writtenDate_${card}`).fill('2019');
    const [saved] = await Promise.all([
      rx.waitForResponse(r => isPost('/rx/WriteScript')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs'),
      rx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '1', 'Save Only did not store the drug');
    drug = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`);
    const stored = sql.value(`SELECT CONCAT(d.rx_date,'|',IFNULL(s.format,'full'),'|',d.written_date,'|',IFNULL(w.format,'full'))
      FROM drugs d
      LEFT JOIN partial_date s ON s.table_name=${DRUGS} AND s.table_id=d.drugid AND s.field_name=${START_DATE}
      LEFT JOIN partial_date w ON w.table_name=${DRUGS} AND w.table_id=d.drugid AND w.field_name=${WRITTEN_DATE}
      WHERE d.drugid=${drug}`);
    h.assert(stored === '2019-05-01|YYYY-MM|2019-01-01|YYYY', `The drug stored its dates as ${stored}`);
  });

  await s.step('the drug profile lists the start date as the month', async () => {
    const start = rx.locator(`#rxDate_${drug}`);
    await start.waitFor({ state: 'visible' });
    const shown = (await start.innerText()).trim();
    h.assert(shown === '2019-05', `The drug profile lists the start date as ${JSON.stringify(shown)}`);
  });

  await s.step('the static script page and the drug record show the month start date and the year written date', async () => {
    await clickAndAwaitReload(rx, rx.locator(`#prescrip_${drug}`), { label: 'drug row' });
    h.assert(h.pathOnly(rx.url()).endsWith('/rx/ViewStaticScript2'), 'The drug row did not open the static script page');
    const recordLink = `a[onclick*="/rx/ViewDisplayRxRecord?id=${drug}'"]`;
    const cells = await rx.locator(`tr:has(> td > ${recordLink})`).locator(':scope > td').allInnerTexts();
    const listed = { start: cells[1]?.trim(), written: cells[3]?.trim() };
    h.assert(listed.start === '2019/05' && listed.written === '2019', `The static script page lists ${JSON.stringify(listed)}`);
    const record = await s.popup(rx, rx.locator(recordLink).first(), 'rx-record');
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- label is one of this check's own fixed field names (Rx Date, Written Date)
    const field = async label => (await record.locator('td.label', { hasText: new RegExp(`^${label}:$`) }).first()
      .locator('xpath=following-sibling::td[1]').innerText()).trim();
    const shown = { rxDate: await field('Rx Date'), written: await field('Written Date') };
    h.assert(shown.rxDate === '2019-05' && shown.written === '2019', `The drug record shows ${JSON.stringify(shown)}`);
  });
}

if (require.main === module) runWorkflow('rx-partial-dates', workflow, { openPatient: true });
module.exports = { workflow };
