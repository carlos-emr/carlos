#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-represcribe-long-term: the Rx page's "Represcribe Long Term Meds" link (#cmdRePrescribe, SearchDrug3.jsp).
// It posts rx/rePrescribe2 method=repcbAllLongTerm, which stages a copy of each of the patient's current long-term
// drugs; Save Only (rx/WriteScript updateSaveAllDrugs) stores the copies and archives each original as
// "represcribed". rx-fax-reprint-represcribe and rx-med-history cover re-prescribing ticked drugs; no check pressed
// this link. The drugs table has no column linking a copy to its original, so a copy is matched to its original by
// name, instructions and long-term flag.
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ "Represcribe Long Term Meds" ▸ Save Only.
//   1. the link stages exactly one card for each current long-term drug, and none for the acute drug or the
//      discontinued long-term drug;
//   2. Save Only stores one long-term copy of each, dated today, archives each original as "represcribed", and
//      leaves the acute drug and the discontinued drug as they were;
//   3. the drug profile then lists each long-term drug once, as its copy.
// Fixtures: the owned FAKE- patient and four marker-named custom drugs inserted for it: two current long-term drugs,
// one current acute drug and one discontinued long-term drug. Cleanup removes the patient's drugs, prescriptions,
// drugReason and DigitalSignature rows, and asserts none remain. No clinic-wide state is changed.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const q = h.sqlString;
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
/** Waits for the response the predicate names; a timeout says which request never answered. */
const answered = (page, predicate, what) => page.waitForResponse(predicate, { timeout: 60000 })
  .catch(error => { throw new Error(`${what} never answered: ${error.message.split('\n')[0]}`); });

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})`) === '0', 'Owned Rx rows were not removed');
  });

  /** Inserts one marker-named custom drug, started 20 days ago, for the owned patient; returns its id and fields. */
  function insertDrug(suffix, longTerm, archivedReason) {
    const name = `${marker}-${suffix}`;
    const special = `${name} one tablet daily`;
    const id = sql.value(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,
      special,archived,archived_reason,archived_date,position,dispenseInternal,create_date,lastUpdateDate,long_term)
      VALUES(${q(provider)},${patient},DATE_SUB(CURDATE(),INTERVAL 20 DAY),DATE_ADD(CURDATE(),INTERVAL 10 DAY),
      DATE_SUB(CURDATE(),INTERVAL 20 DAY),${q(name)},0,${q(name)},1,1,'OD','30','D','30',0,${q(special)},
      ${archivedReason ? 1 : 0},${q(archivedReason || '')},${archivedReason ? 'NOW()' : 'NULL'},0,0,NOW(),NOW(),${longTerm});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The ${suffix} fixture drug was not inserted`);
    return { id, name, special };
  }
  const longTerm = [insertDrug('LT1', 1), insertDrug('LT2', 1)];
  const acute = insertDrug('ACUTE', 0);
  const stopped = insertDrug('LTSTOPPED', 1, 'noLongerNecessary');

  // The Master Record's Prescriptions link, by the URL it opens (an onclick on older pages, a .patient-nav href since #4468).
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(),
    'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  const stagedNames = () => rx.locator("[id^='drugName_']").evaluateAll(els => els.map(el => el.value.trim()));

  await s.step('the link stages one card for each current long-term drug and none for the others', async () => {
    h.assert((await stagedNames()).length === 0, 'Cards were staged before the link was pressed');
    const [staged] = await Promise.all([
      answered(rx, response => isPost('/rx/rePrescribe2')(response)
        && new URLSearchParams(response.request().postData() || '').get('method') === 'repcbAllLongTerm',
        'the long-term re-prescribe (rx/rePrescribe2 repcbAllLongTerm)'),
      rx.locator('#cmdRePrescribe').click(),
    ]);
    h.assert(staged.status() === 200, `rx/rePrescribe2 answered HTTP ${staged.status()}`);
    await rx.waitForFunction(count => document.querySelectorAll("[id^='drugName_']").length >= count, longTerm.length,
      { timeout: 20000 });
    await rx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const names = (await stagedNames()).sort();
    h.assert(JSON.stringify(names) === JSON.stringify(longTerm.map(drug => drug.name).sort()),
      `The link staged ${JSON.stringify(names)}, not the two current long-term drugs`);
  });

  await s.step('Save Only stores a dated long-term copy of each and archives each original as re-prescribed', async () => {
    const [saved] = await Promise.all([
      answered(rx, response => isPost('/rx/WriteScript')(response)
        && new URL(response.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs',
        'Save Only (rx/WriteScript updateSaveAllDrugs)'),
      rx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '6', 'Save Only did not store two copies');
    for (const original of longTerm) {
      const copies = sql.rows(`SELECT drugid, long_term, archived, special, DATE_FORMAT(rx_date,'%Y-%m-%d')=DATE_FORMAT(CURDATE(),'%Y-%m-%d'),
        DATE_FORMAT(written_date,'%Y-%m-%d')=DATE_FORMAT(CURDATE(),'%Y-%m-%d') FROM drugs
        WHERE demographic_no=${patient} AND customName=${q(original.name)} AND drugid<>${original.id}`);
      h.assert(copies.length === 1, `${original.name} has ${copies.length} copies`);
      const [[, copyLongTerm, copyArchived, copySpecial, startsToday, writtenToday]] = copies;
      h.assert(copyLongTerm === '1' && copyArchived === '0', `The copy of ${original.name} is long_term=${copyLongTerm}, archived=${copyArchived}`);
      // The copy's text is rebuilt as name, instructions and "Qty:30 Repeats:0", one per line.
      h.assert(copySpecial.replace(/\s+/g, ' ').includes(original.special),
        `The copy of ${original.name} carries ${JSON.stringify(copySpecial)}`);
      h.assert(startsToday === '1' && writtenToday === '1', `The copy of ${original.name} is not dated today`);
      const [[archived, reason]] = sql.rows(`SELECT archived, archived_reason FROM drugs WHERE drugid=${original.id}`);
      h.assert(archived === '1' && reason === 'represcribed', `${original.name} was left archived=${archived}, reason ${JSON.stringify(reason)}`);
    }
    const untouched = sql.rows(`SELECT drugid, archived, archived_reason FROM drugs WHERE drugid IN (${acute.id},${stopped.id}) ORDER BY drugid`);
    h.assert(JSON.stringify(untouched) === JSON.stringify([[acute.id, '0', ''], [stopped.id, '1', 'noLongerNecessary']]),
      `The acute and discontinued drugs changed: ${JSON.stringify(untouched)}`);
  });

  await s.step('the drug profile lists each long-term drug once, as its copy', async () => {
    await rx.reload({ waitUntil: 'domcontentloaded' });
    await rx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await rx.locator(`#prescrip_${acute.id}`).waitFor({ state: 'visible', timeout: 20000 });
    for (const original of longTerm) {
      const copy = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND customName=${q(original.name)} AND archived=0`);
      h.assert(await rx.locator(`#prescrip_${copy}`).count() === 1, `The profile does not list the copy of ${original.name}`);
      h.assert(await rx.locator(`#prescrip_${original.id}`).count() === 0, `The profile still lists the original ${original.name}`);
    }
    h.assert(await rx.locator(`#prescrip_${stopped.id}`).count() === 0, 'The profile lists the discontinued long-term drug');
  });
}

if (require.main === module) runWorkflow('rx-represcribe-long-term', workflow, { openPatient: true });
module.exports = { workflow };
