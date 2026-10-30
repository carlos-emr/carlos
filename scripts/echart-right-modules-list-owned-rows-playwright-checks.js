#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The eChart's right-column modules list what the patient has: Dx Registry
 * (encounter/displayDisease), Allergies (encounter/displayAllergy) and Medications
 * (encounter/displayRx). Every chart check loads these fragments and echart-navbar-modules
 * opens their links, but no check asserted what they list. This seeds one owned row of each
 * for the workflow's patient and asserts the module shows it with its title, full title and
 * count; then opens a second owned patient's chart and asserts none of the rows shows there
 * (the modules read the chart's session bean, which is what a mix-up would corrupt).
 *
 * The rows of these three modules carry no link of their own (their URL is "return false;"),
 * so the module page is not opened here; the Dx Registry, allergy and Rx pages have their own
 * checks.
 *
 * Fixtures: owned dxresearch, allergies and drugs rows for the workflow's patient, and a second
 * FAKE patient; all removed by their own keys.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { moduleList, seedSecondPatient, openSecondChart, releaseLocksAfter, closeChart } = require('./lib/echart-module-list');

// Diabetes mellitus: '250' on a plain ICD-9 catalogue, '250.0' where the codes are dotted.
const DX_CODE_PREFIX = '250';

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const dxCode = sql.value(`SELECT icd9 FROM icd9 WHERE icd9 LIKE ${q(`${DX_CODE_PREFIX}%`)} ORDER BY icd9 LIMIT 1`);
  h.assert(dxCode, `No icd9 code starting ${DX_CODE_PREFIX} in this database`);
  const dxDescription = sql.value(`SELECT description FROM icd9 WHERE icd9=${q(dxCode)} LIMIT 1`);
  h.assert(dxDescription, `No icd9 description for ${dxCode} in this database`);
  const allergy = `${marker} allergy`;
  const drugName = `${marker} drug`;
  let dxId;
  let allergyId;
  let drugId;
  s.cleanup(() => {
    if (drugId) sql.execute(`DELETE FROM drugs WHERE drugid=${drugId} AND demographic_no=${patient}`);
    if (allergyId) sql.execute(`DELETE FROM allergies WHERE allergyid=${allergyId} AND demographic_no=${patient}`);
    if (dxId) sql.execute(`DELETE FROM dxresearch WHERE dxresearch_no=${dxId} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === '0', 'Owned drug remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Owned allergy remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient}`) === '0', 'Owned diagnosis remains');
  });
  dxId = h.insertId(sql, `INSERT INTO dxresearch (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (${patient},CURDATE(),NOW(),'A',${q(dxCode)},'icd9',0,${q(provider)})`, 'owned diagnosis');
  allergyId = h.insertId(sql, `INSERT INTO allergies (demographic_no,entry_date,start_date,DESCRIPTION,TYPECODE,reaction,nonDrug,
      severity_of_reaction,onset_of_reaction,archived,position,lastUpdateDate,providerNo)
    VALUES (${patient},CURDATE(),CURDATE(),${q(allergy)},0,${q(`${marker} reaction`)},1,'2','1',0,0,NOW(),${q(provider)})`, 'owned allergy');
  drugId = h.insertId(sql, `INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,archived,archived_reason,
      create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode)
    VALUES (${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${q(drugName)},
      ${q(`${drugName} 10 mg\nTake 1 tablet daily`)},0,'',NOW(),1,NOW(),0,'30','30','D','OD')`, 'owned drug');
  const second = seedSecondPatient(s);

  await releaseLocksAfter(s, () => steps(s, { second, dxDescription, allergy, drugName }));
}

async function steps(s, { second, dxDescription, allergy, drugName }) {
  const chart = await s.chart();
  const dx = moduleList(chart, 'Dx');
  const allergies = moduleList(chart, 'allergies');
  const rx = moduleList(chart, 'Rx');

  await s.step('the Dx Registry module lists the owned diagnosis with its description, and counts it', async () => {
    const row = dx.row(dxDescription);
    await row.waitFor({ state: 'attached' });
    h.assert((await row.innerText()).trim() === dxDescription, 'The diagnosis row does not show the description');
    // The full title is the description and the start date (EctDisplayDx2Action).
    h.assert(new RegExp(`^${dxDescription.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')} \\d{4}-\\d{2}-\\d{2}$`).test(await row.getAttribute('title') || ''),
      `The diagnosis row's full title is not the description and date: ${await row.getAttribute('title')}`);
    const shown = await dx.shown();
    h.assert(shown === '1', `The Dx Registry module counts ${shown} rows instead of the one owned diagnosis`);
  });

  await s.step('the Allergies module lists the owned allergy by its description, and counts it', async () => {
    const row = allergies.row(allergy);
    await row.waitFor({ state: 'attached' });
    // The provider's CPP preferences can append " Start Date:…" / " Severity:…" to the title.
    h.assert((await row.innerText()).trim().startsWith(allergy), 'The allergy row does not show the description');
    h.assert((await row.getAttribute('title') || '').startsWith(allergy), 'The allergy row\'s full title does not start with the description');
    const shown = await allergies.shown();
    h.assert(shown === '1', `The Allergies module counts ${shown} rows instead of the one owned allergy`);
  });

  await s.step('the Medications module lists the owned drug by its name and directions, and counts it', async () => {
    const row = rx.row(drugName);
    await row.waitFor({ state: 'attached' });
    h.assert((await row.innerText()).includes(drugName), 'The drug row does not show the drug name');
    h.assert((await row.getAttribute('title') || '').includes('Take 1 tablet daily'), 'The drug row\'s full title does not carry the directions');
    const shown = await rx.shown();
    h.assert(shown === '1', `The Medications module counts ${shown} rows instead of the one owned drug`);
  });

  await s.step('a second patient\'s chart lists none of them', async () => {
    const other = await openSecondChart(s, second);
    for (const [name, divId] of [['Dx Registry', 'Dx'], ['Allergies', 'allergies'], ['Medications', 'Rx']]) {
      const list = moduleList(other, divId);
      const shown = await list.shown();
      h.assert(shown === '0', `${name} shows ${shown} rows for a patient who has none`);
      h.assert(await list.rows.count() === 0, `${name} lists a row for a patient who has none`);
    }
    await closeChart(s, other);
  });
}

if (require.main === module) runWorkflow('echart-right-modules-list-owned-rows', workflow);
module.exports = { workflow };
