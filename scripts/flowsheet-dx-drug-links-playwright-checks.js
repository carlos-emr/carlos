#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The Diabetes Flowsheet's left panel links a current diagnosis or a current drug to the flowsheet (#4974).
 * User path: the patient's chart ▸ Measurements ▸ Diabetes Flowsheet (a popup, template diab2). The panel lists the
 * patient's diagnoses and current drugs; each one the flowsheet doesn't link yet is a link that posts to
 * encounter/oscarMeasurements/FlowSheetDrugAction (a diagnosis writes flowsheet_dx, a drug flowsheet_drug).
 * Asserted:
 *   - the flowsheet lists the owned diagnosis and the owned current drug, each as a link;
 *   - linking the diagnosis saves one flowsheet_dx row and comes back to the flowsheet, which shows it linked
 *     (highlighted, no longer a link);
 *   - a reload of that page shows the flowsheet again and saves nothing more;
 *   - linking the drug saves one flowsheet_drug row and comes back to the flowsheet, which shows it linked instead
 *     of offering it again;
 *   - LAST: the same drug clicked in a flowsheet opened before the link (a second window) comes back to the
 *     flowsheet and saves nothing more.
 * Fixtures: the owned FAKE- patient, with one active ICD-9 250 diagnosis and one current FAKE drug (ATC C10AA05),
 * both set-up rather than the behaviour under test. Every row the check writes or makes the application write is
 * keyed to that patient; cleanup deletes the patient's flowsheet_dx and flowsheet_drug rows, the diagnosis and the
 * drug, and asserts each is gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const TEMPLATE = 'diab2';
const DX_CODE = '250';
const DX_SYSTEM = 'icd9';
const ATC = 'C10AA05';
const FLOWSHEET_ROUTE = '/encounter/oscarMeasurements/ViewTemplateFlowSheet';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  offered: 'the flowsheet lists the patient\'s diagnosis and current drug, each as a link',
  dx: 'linking the diagnosis saves it once and comes back to the flowsheet, showing it linked',
  reload: 'a reload of that page shows the flowsheet again and saves nothing more',
  drug: 'linking the drug saves it once and comes back to the flowsheet, showing it linked instead of offering it',
  repeat: 'the same drug clicked in a flowsheet opened before the link saves nothing more',
};

/** The panel's link form for one diagnosis or drug, by the hidden fields the page posts. */
function dxLink(page) {
  return page.locator('#dxFullListing form[action$="FlowSheetDrugAction"]')
    .filter({ has: page.locator('input[name="method"][value="dxSave"]') })
    .filter({ has: page.locator(`input[name="dxCode"][value="${DX_CODE}"]`) });
}

function drugLink(page) {
  return page.locator('#rxFullListing form[action$="FlowSheetDrugAction"]')
    .filter({ has: page.locator('input[name="method"][value="save"]') })
    .filter({ has: page.locator(`input[name="atcCode"][value="${ATC}"]`) });
}

/** Clicks a panel link and waits for the page it ends on. */
async function follow(page, form) {
  const [response] = await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
    form.locator('a').click(),
  ]);
  return response;
}

/** Asserts the page is the owned patient's Diabetes Flowsheet, answered with HTTP 200. */
async function assertFlowsheet(page, response, label, patient) {
  h.assert(response && response.status() === 200, `${label} answered HTTP ${response ? response.status() : 'nothing'}, not 200`);
  await h.assertNotErrorPage(page, label);
  const at = new URL(page.url());
  h.assert(at.pathname.endsWith(FLOWSHEET_ROUTE) && at.searchParams.get('demographic_no') === String(patient)
    && at.searchParams.get('template') === TEMPLATE, `${label} ended at ${at.pathname}, not the patient's flowsheet`);
  await page.locator('#rxFullListing').waitFor({ state: 'attached', timeout: TIMEOUT });
}

async function workflow(s) {
  const { sql, patient, provider } = s;
  const q = h.sqlString;
  const links = () => sql.value(`SELECT CONCAT(
      (SELECT COUNT(*) FROM flowsheet_dx WHERE demographic_no=${patient} AND flowsheet=${q(TEMPLATE)}
        AND dx_code=${q(DX_CODE)} AND dx_code_type=${q(DX_SYSTEM)}), '/',
      (SELECT COUNT(*) FROM flowsheet_drug WHERE demographic_no=${patient} AND flowsheet=${q(TEMPLATE)}
        AND atc_code=${q(ATC)}))`);
  let dxId = null;
  let drugId = null;
  s.cleanup(() => {
    sql.execute(`DELETE FROM flowsheet_dx WHERE demographic_no=${patient}; DELETE FROM flowsheet_drug WHERE demographic_no=${patient}`);
    if (dxId) sql.execute(`DELETE FROM dxresearch WHERE dxresearch_no=${dxId} AND demographic_no=${patient}`);
    if (drugId) sql.execute(`DELETE FROM drugs WHERE drugid=${drugId} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM flowsheet_dx WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM flowsheet_drug WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})`) === '0', 'The owned flowsheet links, diagnosis or drug were not removed');
  });
  dxId = sql.value(`INSERT INTO dxresearch (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (${patient},CURDATE(),NOW(),'A',${q(DX_CODE)},${q(DX_SYSTEM)},0,${q(provider)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(dxId), 'The diagnosis fixture was not created');
  drugId = sql.value(`INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,archived,
      archived_reason,create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode,ATC)
    VALUES (${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),'FAKE atorvastatin',
      'FAKE atorvastatin 20 mg OD',0,'',NOW(),1,NOW(),0,'30','30','D','OD',${q(ATC)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(drugId), 'The drug fixture was not created');

  let flowsheet;
  await s.step(STEP.offered, async () => {
    const chart = await s.chart();
    const link = chart.locator(`a[onclick*="ViewTemplateFlowSheet"][onclick*="template=${TEMPLATE}'"]`).first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    flowsheet = await s.popup(chart, link, 'diabetes-flowsheet');
    await flowsheet.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(flowsheet, 'Diabetes Flowsheet');
    h.assert(await dxLink(flowsheet).count() === 1, 'The flowsheet does not offer the patient\'s diagnosis as a link');
    h.assert(await drugLink(flowsheet).count() === 1, 'The flowsheet does not offer the patient\'s current drug as a link');
    h.assert(links() === '0/0', 'The flowsheet links something before any click');
  });

  await s.step(STEP.dx, async () => {
    const response = await follow(flowsheet, dxLink(flowsheet));
    await assertFlowsheet(flowsheet, response, 'Linking the diagnosis', patient);
    h.assert(links() === '1/0', `Linking the diagnosis saved ${links().split('/')[0]} flowsheet_dx rows, not 1`);
    h.assert(await dxLink(flowsheet).count() === 0, 'The linked diagnosis is still offered as a link');
    h.assert(await flowsheet.locator('#dxFullListing span[style*="yellow"]').count() === 1,
      'The flowsheet does not show the diagnosis as linked');
  });

  await s.step(STEP.reload, async () => {
    const response = await flowsheet.reload({ waitUntil: 'domcontentloaded', timeout: 60000 });
    await assertFlowsheet(flowsheet, response, 'The reload', patient);
    h.assert(links() === '1/0', `The reload left ${links().split('/')[0]} flowsheet_dx rows, not 1`);
  });

  let older;
  await s.step(STEP.drug, async () => {
    // A second window on the same flowsheet, opened before the link: it still offers the drug (the last step).
    older = await s.context.newPage();
    h.wireStrictPage(older, 'diabetes-flowsheet-older', s.recorder);
    await h.gotoApp(older, s.config.baseUrl, `${FLOWSHEET_ROUTE}?demographic_no=${patient}&template=${TEMPLATE}`);
    h.assert(await drugLink(older).count() === 1, 'The second flowsheet window does not offer the drug');
    const response = await follow(flowsheet, drugLink(flowsheet));
    await assertFlowsheet(flowsheet, response, 'Linking the drug', patient);
    h.assert(links() === '1/1', `Linking the drug saved ${links().split('/')[1]} flowsheet_drug rows, not 1`);
    h.assert(await drugLink(flowsheet).count() === 0, 'The linked drug is still offered as a link');
    h.assert(await flowsheet.locator('#rxFullListing span[style*="yellow"]').count() === 1,
      'The flowsheet does not show the drug as linked');
  });

  await s.step(STEP.repeat, async () => {
    const response = await follow(older, drugLink(older));
    await assertFlowsheet(older, response, 'The drug clicked in the older window', patient);
    h.assert(links() === '1/1', `The repeated drug link left ${links().split('/')[1]} flowsheet_drug rows, not 1`);
    h.assert(await drugLink(older).count() === 0, 'The older window, reloaded by the link, still offers the drug');
    await older.close();
    await flowsheet.close();
  });
}

if (require.main === module) runWorkflow('flowsheet-dx-drug-links', workflow);
module.exports = { workflow, STEP };
