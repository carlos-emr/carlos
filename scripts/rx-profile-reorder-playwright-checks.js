#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-profile-reorder: the drug profile's up and down arrows (ListDrugs.jsp; moveDrugUp/moveDrugDown in
// SearchDrug3.jsp), which post rx/reorderDrug method=update to swap the position of two of the patient's drugs and
// then refresh the profile. rx-favorites-choose-drug checks only that rx/reorderDrug refuses GET; no check pressed
// the arrows. The profile lists drugs highest position first.
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ the drug profile's arrows.
//   1. the profile lists the three drugs highest position first; the top row offers only Down, the middle row both
//      arrows, the bottom row only Up;
//   2. Down on the top drug swaps its position with the drug below it, in the database and in the profile order,
//      and leaves the third drug's position alone;
//   3. Up on the same drug swaps them back, and the original order holds after a reload.
// Fixtures: the owned FAKE- patient and three marker-named current custom drugs inserted for it with positions 1, 2
// and 3. Cleanup removes the patient's drugs and asserts none remain. No clinic-wide state is changed.
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
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0', 'Owned drugs remain');
  });
  // low, middle, high: positions 1, 2 and 3.
  const [low, middle, high] = ['A', 'B', 'C'].map((suffix, index) => {
    const name = `${marker}-${suffix}`;
    const id = sql.value(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,
      special,archived,archived_reason,position,dispenseInternal,create_date,lastUpdateDate,long_term)
      VALUES(${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),
      ${q(name)},0,${q(name)},1,1,'OD','30','D','30',0,${q(`${name} one tablet daily`)},0,'',${index + 1},0,NOW(),NOW(),0);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The ${suffix} fixture drug was not inserted`);
    return id;
  });
  const positions = () => JSON.stringify(sql.rows(`SELECT drugid, position FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`));
  const expected = (lowAt, middleAt, highAt) => JSON.stringify([[low, String(lowAt)], [middle, String(middleAt)], [high, String(highAt)]]);

  // The Master Record's Prescriptions link, by the URL it opens (an onclick on older pages, a .patient-nav href since #4468).
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(),
    'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  const down = id => rx.locator(`img[onclick^="moveDrugDown(${id},"]`);
  const up = id => rx.locator(`img[onclick^="moveDrugUp(${id},"]`);

  /** The profile's drug order, top first, once it lists the owned drugs in that order. */
  async function expectOrder(ids, label) {
    let order = [];
    for (let attempt = 0; attempt < 40; attempt += 1) {
      order = await rx.locator('[id^="prescrip_"]').evaluateAll(els => els.map(el => el.id.replace('prescrip_', '')));
      if (JSON.stringify(order) === JSON.stringify(ids)) return;
      await rx.waitForTimeout(250);
    }
    throw new Error(`${label}: the profile lists ${JSON.stringify(order)}, not ${JSON.stringify(ids)}`);
  }
  /** Which arrows the profile offers each row, top first. */
  async function arrows(ids) {
    const offered = [];
    for (const id of ids) offered.push(`${await up(id).count()}${await down(id).count()}`);
    return offered.join(' ');
  }
  /** Presses an arrow and waits for its swap to be answered. */
  async function press(arrow, what) {
    const [response] = await Promise.all([answered(rx, isPost('/rx/reorderDrug'), `${what} (rx/reorderDrug)`), arrow.click()]);
    h.assert(response.status() === 200, `${what} answered HTTP ${response.status()}`);
  }

  await s.step('the profile lists the drugs highest position first, with the arrows each row should offer', async () => {
    await rx.locator(`#prescrip_${high}`).waitFor({ state: 'visible', timeout: 20000 });
    await expectOrder([high, middle, low], 'At first');
    // Each entry is "up down": the top row offers only Down, the middle both, the bottom only Up.
    h.assert(await arrows([high, middle, low]) === '01 11 10', `The rows offer the arrows ${await arrows([high, middle, low])}`);
  });

  await s.step('Down on the top drug swaps it with the drug below it and leaves the third alone', async () => {
    await press(down(high), 'Down on the top drug');
    await expectValue(sql, `SELECT position FROM drugs WHERE drugid=${high}`, '2', 'Down did not move the top drug to position 2');
    h.assert(positions() === expected(1, 3, 2), `After Down the positions are ${positions()}`);
    await expectOrder([middle, high, low], 'After Down');
    h.assert(await arrows([middle, high, low]) === '01 11 10', `After Down the rows offer the arrows ${await arrows([middle, high, low])}`);
  });

  await s.step('Up on the same drug swaps them back, and the order holds after a reload', async () => {
    await press(up(high), 'Up on the moved drug');
    await expectValue(sql, `SELECT position FROM drugs WHERE drugid=${high}`, '3', 'Up did not move the drug back to position 3');
    h.assert(positions() === expected(1, 2, 3), `After Up the positions are ${positions()}`);
    await expectOrder([high, middle, low], 'After Up');
    await rx.reload({ waitUntil: 'domcontentloaded' });
    await rx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await rx.locator(`#prescrip_${high}`).waitFor({ state: 'visible', timeout: 20000 });
    await expectOrder([high, middle, low], 'After a reload');
  });
}

if (require.main === module) runWorkflow('rx-profile-reorder', workflow, { openPatient: true });
module.exports = { workflow };
