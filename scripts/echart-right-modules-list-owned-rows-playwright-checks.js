#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
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
const { moduleList, seedSecondPatient, openSecondChart, assertDistinctCharts, releaseLocksAfter, closeChart } = require('./lib/echart-module-list');

// Diabetes mellitus: '250' on a plain ICD-9 catalogue, '250.0' where the codes are dotted.
const DX_CODE_PREFIX = '250';

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const dxCode = sql.value(`SELECT icd9 FROM icd9 WHERE icd9 LIKE ? ORDER BY icd9 LIMIT 1`, [`${DX_CODE_PREFIX}%`]);
  h.assert(dxCode, `No icd9 code starting ${DX_CODE_PREFIX} in this database`);
  const dxDescription = sql.value(`SELECT description FROM icd9 WHERE icd9=? LIMIT 1`, [dxCode]);
  h.assert(dxDescription, `No icd9 description for ${dxCode} in this database`);
  const allergy = `${marker} allergy`;
  const drugName = `${marker} drug`;
  let dxId;
  let allergyId;
  let drugId;
  s.cleanup(() => {
    if (drugId) sql.execute(`DELETE FROM drugs WHERE drugid=? AND demographic_no=?`, [drugId, patient]);
    if (allergyId) sql.execute(`DELETE FROM allergies WHERE allergyid=? AND demographic_no=?`, [allergyId, patient]);
    if (dxId) sql.execute(`DELETE FROM dxresearch WHERE dxresearch_no=? AND demographic_no=?`, [dxId, patient]);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=?`, [patient]) === '0', 'Owned drug remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=?`, [patient]) === '0', 'Owned allergy remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=?`, [patient]) === '0', 'Owned diagnosis remains');
  });
  dxId = h.insertId(sql, `INSERT INTO dxresearch (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (?,CURDATE(),NOW(),'A',?,'icd9',0,?)`, 'owned diagnosis', [patient, dxCode, provider]);
  allergyId = h.insertId(sql, `INSERT INTO allergies (demographic_no,entry_date,start_date,DESCRIPTION,TYPECODE,reaction,nonDrug,
      severity_of_reaction,onset_of_reaction,archived,position,lastUpdateDate,providerNo)
    VALUES (?,CURDATE(),CURDATE(),?,0,?,1,'2','1',0,0,NOW(),?)`, 'owned allergy', [patient, allergy, `${marker} reaction`, provider]);
  drugId = h.insertId(sql, `INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,archived,archived_reason,
      create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode)
    VALUES (?,?,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),?,
      ?,0,'',NOW(),1,NOW(),0,'30','30','D','OD')`, 'owned drug', [provider, patient, drugName, `${drugName} 10 mg\nTake 1 tablet daily`]);
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
    const fullTitle = await row.getAttribute('title') || '';
    const titlePrefix = `${dxDescription} `;
    h.assert(fullTitle.startsWith(titlePrefix) && /^\d{4}-\d{2}-\d{2}$/.test(fullTitle.slice(titlePrefix.length)),
      `The diagnosis row's full title is not the description and date: ${fullTitle}`);
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
    const other = await openSecondChart(s, second, chart);
    for (const [name, divId] of [['Dx Registry', 'Dx'], ['Allergies', 'allergies'], ['Medications', 'Rx']]) {
      const list = moduleList(other, divId);
      const shown = await list.shown();
      h.assert(shown === '0', `${name} shows ${shown} rows for a patient who has none`);
      h.assert(await list.rows.count() === 0, `${name} lists a row for a patient who has none`);
      assertDistinctCharts(s, chart, other);
    }
    await closeChart(s, other);
  });
}

if (require.main === module) runWorkflow('echart-right-modules-list-owned-rows', workflow);
module.exports = { workflow };
