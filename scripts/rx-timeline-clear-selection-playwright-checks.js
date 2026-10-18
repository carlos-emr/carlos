#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-timeline-clear-selection: the medication timeline's Med List graphs what the prescriber ticked,
// including nothing (#5004).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ Timeline Drug Profile
// (rx/ViewChartDrugProfile, chartDrugProfile.jsp) ▸ tick/untick products ▸ Add Meds to Graph.
// An unticked checkbox submits nothing, and the page used to treat "no drug parameter" as a first
// visit, so clearing every box graphed and re-ticked every product. Now:
//   1. the first visit ticks and graphs both catalogue products, and lists no custom drug;
//   2. a submitted selection of one product graphs and ticks only that one;
//   3. clearing every box leaves an empty graph and no ticked product.
// No clinical record changes. Fixtures: the owned FAKE- patient and three drug rows inserted directly
// (two catalogue products with marker-derived DINs, one custom drug). Cleanup removes the patient's
// drugs and prescription rows and asserts none remain.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

function insertDrug(sql, provider, patient, { brand = null, custom = null, din = null }) {
  return h.insertId(sql, `INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,BN,GCN_SEQNO,customName,
      regional_identifier,special,archived,archived_reason,create_date,position,lastUpdateDate,dispenseInternal,quantity,
      duration,durunit,freqcode)
    VALUES (${h.sqlString(provider)},${patient},DATE_SUB(CURDATE(),INTERVAL 10 DAY),DATE_ADD(CURDATE(),INTERVAL 20 DAY),
      DATE_SUB(CURDATE(),INTERVAL 10 DAY),${brand ? h.sqlString(brand) : "''"},${custom ? 0 : 123},
      ${custom ? h.sqlString(custom) : 'NULL'},${din ? h.sqlString(din) : 'NULL'},'1 tab PO daily',0,'',NOW(),0,NOW(),0,
      '30','30','D','OD')`, 'drug');
}

async function readTimeline(page) {
  // The chart is drawn by a separate image request; let it finish before reading or submitting,
  // or the next navigation aborts it.
  await page.waitForFunction(() => {
    const img = document.querySelector('img[src*="method=ChartMeds"]');
    // complete is also true for a failed image; a drawn chart has a size.
    return img && img.complete && img.naturalWidth > 0;
  });
  return page.evaluate(() => ({
    checked: [...document.querySelectorAll('input[type="checkbox"][name="drug"]')].filter(box => box.checked).map(box => box.value).sort(),
    offered: [...document.querySelectorAll('input[type="checkbox"][name="drug"]')].map(box => box.value).sort(),
    graphed: new URL(document.querySelector('img[src*="method=ChartMeds"]').src).searchParams.getAll('drug').sort(),
  }));
}

async function submit(page) {
  await Promise.all([page.waitForEvent('load'), page.locator('input[type="submit"][value="Add Meds to Graph"]').first().click()]);
  await h.assertNotErrorPage(page, 'medication timeline');
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0', 'Owned drug rows were not removed');
  });
  // Health Canada assigns no DIN starting 99; the marker's hex makes them unique to this run.
  const digits = BigInt(`0x${marker.slice(-8)}`).toString().padStart(6, '0').slice(-6);
  const dinA = `99${digits}`;
  const dinB = `98${digits}`;
  insertDrug(sql, provider, patient, { brand: `${marker} PRODUCT A`, din: dinA });
  insertDrug(sql, provider, patient, { brand: `${marker} PRODUCT B`, din: dinB });
  insertDrug(sql, provider, patient, { custom: `${marker} CUSTOM` });
  const records = () => sql.value(`SELECT GROUP_CONCAT(CONCAT(drugid,':',archived,':',lastUpdateDate) ORDER BY drugid)
    FROM drugs WHERE demographic_no=${patient}`);
  const before = records();
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, `/rx/ViewChartDrugProfile?demographic_no=${patient}`);
  await h.assertNotErrorPage(page, 'medication timeline');
  const both = [dinA, dinB].sort();

  await s.step('the first visit graphs every catalogue product and offers no custom drug', async () => {
    const shown = await readTimeline(page);
    h.assert(JSON.stringify(shown.offered) === JSON.stringify(both), `The Med List offers ${JSON.stringify(shown.offered)}`);
    h.assert(JSON.stringify(shown.checked) === JSON.stringify(both) && JSON.stringify(shown.graphed) === JSON.stringify(both),
      `The first visit ticks ${JSON.stringify(shown.checked)} and graphs ${JSON.stringify(shown.graphed)}`);
  });

  await s.step('reducing the selection to one product graphs only that product', async () => {
    await page.locator(`input[name="drug"][value="${dinB}"]`).uncheck();
    await submit(page);
    const shown = await readTimeline(page);
    h.assert(JSON.stringify(shown.checked) === JSON.stringify([dinA]) && JSON.stringify(shown.graphed) === JSON.stringify([dinA]),
      `One product ticks ${JSON.stringify(shown.checked)} and graphs ${JSON.stringify(shown.graphed)}`);
  });

  await s.step('clearing every medication leaves an empty graph and no checked products', async () => {
    await page.locator(`input[name="drug"][value="${dinA}"]`).uncheck();
    await submit(page);
    const shown = await readTimeline(page);
    h.assert(shown.checked.length === 0 && shown.graphed.length === 0,
      `Clearing every box ticks ${JSON.stringify(shown.checked)} and graphs ${JSON.stringify(shown.graphed)}`);
    h.assert(JSON.stringify(shown.offered) === JSON.stringify(both), 'Clearing the selection changed which products are offered');
    h.assert(records() === before, 'Using the timeline changed a medication record');
  });
}

if (require.main === module) runWorkflow('rx-timeline-clear-selection', workflow, { openPatient: true });
module.exports = { workflow };
