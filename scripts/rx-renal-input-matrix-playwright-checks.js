#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {stageFromSearch} = require('./rx-edit-discontinue-playwright-checks');
const {masterRecordLink} = require('./lib/master-record-link');
const {CASES, renalInputFixture} = require('./lib/renal-input-fixture');
async function workflow(s) {
  const name = process.env.RX_RENAL_CASE || 'male-valid';
  h.assert(Object.hasOwn(CASES,name),'Unknown RX_RENAL_CASE');
  const testCase = CASES[name], fixture = renalInputFixture(s);
  fixture.apply(CASES['male-valid']);
  const rx = await s.popup(s.master,masterRecordLink(s.master,'/rx/choosePatient').first(),'renal-matrix-rx');
  const card = await stageFromSearch(rx,'CIPRO 500','CIPRO 500MG');
  const panel = rx.locator(`#renalDosing_${card}`);
  await panel.locator('table.equation').waitFor({state:'visible'});
  await s.step(`${name}: render independent clinical inputs and exact inclusive recommendation boundaries`,async()=>{
    fixture.apply(testCase);
    const [response] = await Promise.all([
      rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
      panel.getByRole('link',{name:'Refresh',exact:true}).click(),
    ]);
    h.assert(response.status()===200,`Renal rendering returned HTTP ${response.status()}`);
    h.assert(new URL(response.url()).searchParams.get('demographicNo')===s.patient,'Renal refresh changed its patient');
    await rx.waitForFunction(({id, token}) => document.querySelector(id)?.textContent.includes(token),
      {id: `#renalDosing_${card}`, token: testCase.clearance === undefined ? 'N/A' : `Clcr ${testCase.clearance} =`});
    const text=(await panel.innerText()).replace(/\s+/g,' '), selected=panel.locator('table.sofT tr.selected');
    if(testCase.clearance===undefined) {
      h.assert(text.includes('N/A') && !/NaN|Infinity/.test(text),'Unusable renal input was displayed as a usable number');
      h.assert(await selected.count()===0,'Unusable renal input retained a selected recommendation');
    } else {
      h.assert(text.includes(`Clcr ${testCase.clearance} =`),'Displayed creatinine clearance differs from the independent expected value');
      const inRange=testCase.clearance>=5 && testCase.clearance<=30;
      h.assert(await selected.count()===(inRange?1:0),'The 5–30 inclusive ciprofloxacin recommendation boundary is wrong');
      if(inRange)h.assert((await selected.innerText()).includes('250-500 mg po q18h'),'Wrong renal dose was highlighted');
      if(testCase.source)h.assert(text.includes(testCase.source),'The displayed creatinine source date is wrong');
    }
  });
  await s.step('restoring valid observations refreshes the same panel without retaining stale input',async()=>{
    fixture.apply(CASES['female-valid']);
    await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
      panel.getByRole('link',{name:'Refresh',exact:true}).click()]);
    await rx.waitForFunction(({id})=>document.querySelector(id)?.textContent.includes('Clcr 68 ='),{id:`#renalDosing_${card}`});
    h.assert(await panel.locator('table.sofT tr.selected').count()===0,'Restored valid high clearance retained the previous recommendation');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`)==='0','Input checking unexpectedly saved a prescription');
  });
}
if(require.main===module)runWorkflow('rx-renal-input-matrix',workflow,{openPatient:true});
module.exports={workflow};
