#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {stageFromSearch} = require('./rx-edit-discontinue-playwright-checks');
const {formularyLuCodes, LIMITED_USE, RENAL} = require('./rx-interactions-renal-luc-playwright-checks');
const {masterRecordLink} = require('./lib/master-record-link');
const {openSecondPatient} = require('./lib/second-patient-window');
const {removeMarkedPatients} = require('./lib/gap-records-fixtures');
const {rxSave} = require('./lib/mutation-families');
const {viewScriptFrame}=require('./rx-drug-name-quotes-playwright-checks');
async function workflow(s) {
  const {sql, patient, marker} = s, otherName=marker+'-CONTROL';
  const cleanup=rxSave(s);s.cleanup(()=>cleanup.cleanup());
  s.cleanup(()=>removeMarkedPatients(sql,otherName));
  sql.execute(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(otherName)},'Workflow','1980','01','02','F','AC',${h.sqlString(s.provider)},'ON','ON','NR',NOW())`);
  const otherPatient=sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(otherName)}`);
  const otherCleanup=rxSave({...s,patient:otherPatient});s.cleanup(()=>otherCleanup.cleanup());
  const rx=await s.popup(s.master,masterRecordLink(s.master,'/rx/choosePatient').first(),'limited-use-rx');
  const expected=formularyLuCodes(LIMITED_USE.din);
  h.assert(expected.length>=2,'Limited-use fixture needs at least two controlled codes');
  const controlCard=await stageFromSearch(rx,RENAL.term,RENAL.name);
  const base='1 tab PO daily', controlDirections='1 tab PO BID';
  await rx.locator(`#instructions_${controlCard}`).fill(controlDirections);await rx.locator(`#instructions_${controlCard}`).press('Tab');
  const card=await stageFromSearch(rx,LIMITED_USE.term,LIMITED_USE.name);
  const instructions=rx.locator(`#instructions_${card}`),codes=rx.locator(`#luc_${card} a[onclick*="addLuCode("]`);
  await codes.first().waitFor({state:'visible'});await instructions.fill(base);await instructions.press('Tab');
  const {masterPage:otherMaster}=await openSecondPatient(s,otherPatient,otherName);
  const otherRx=await s.popup(otherMaster,masterRecordLink(otherMaster,'/rx/choosePatient').first(),'limited-use-control-rx');
  const otherCard=await stageFromSearch(otherRx,LIMITED_USE.term,LIMITED_USE.name);
  const otherInstructions=otherRx.locator(`#instructions_${otherCard}`);await otherInstructions.fill('CONTROL directions');await otherInstructions.press('Tab');
  let wanted,script;
  await s.step('choose offered LU codes through the actual links and preserve both control cards',async()=>{
    h.assert(JSON.stringify((await codes.allInnerTexts()).map(value=>value.trim()).filter(Boolean))===JSON.stringify(expected),'Offered codes differ from the controlled formulary');
    await otherRx.bringToFront();
    for(const code of [expected[0],expected[0],expected[1]])await rx.locator(`#luc_${card}`).getByRole('link',{name:code,exact:true}).click();
    // addLuCode appends an explicit annotation; repeated clicks append another
    // annotation rather than replacing existing directions or another selected code.
    wanted=base+` LU Code: ${expected[0]} LU Code: ${expected[0]} LU Code: ${expected[1]}`;
    h.assert(await instructions.inputValue()===wanted,'LU selection did not preserve directions and the append contract');
    h.assert(await rx.locator(`#instructions_${controlCard}`).inputValue()===controlDirections,'LU selection changed another medication');
    h.assert(await otherInstructions.inputValue()==='CONTROL directions','LU selection changed another patient window');
    h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no IN (${patient},${otherPatient})`)==='0','Selecting unsaved codes created a prescription');
  });
  await s.step('Save Only persists LU text on the selected medication and reprint preview',async()=>{
    const [saved]=await Promise.all([
      rx.waitForResponse(r=>r.request().method()==='POST' && new URL(r.url()).pathname.endsWith('/rx/WriteScript')),
      rx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status()===200,'Saving the limited-use prescription failed');script=String((await saved.json()).scriptId);
    h.assert(/^[1-9]\d*$/.test(script),'Save returned no prescription identity');
    // drugs.special is the complete formatted prescription (brand, quantity,
    // repeats and directions), rather than the directions textarea alone.
    const selected=`demographic_no=${patient} AND BN=${h.sqlString(LIMITED_USE.name)}`;
    await expectValue(sql,`SELECT LOCATE(${h.sqlString(wanted)},special)>0 FROM drugs WHERE ${selected} ORDER BY drugid DESC LIMIT 1`,'1','Saved medication lost selected LU codes');
    const [[din,special]]=sql.rows(`SELECT regional_identifier,special FROM drugs WHERE ${selected} ORDER BY drugid DESC LIMIT 1`);
    h.assert(Number(din)===Number(LIMITED_USE.din)&&special.split(' LU Code: ').length-1===3,'Saved prescription identifies a different product or altered the selected code count');
    const controlSpecial=sql.value(`SELECT special FROM drugs WHERE demographic_no=${patient} AND BN=${h.sqlString(RENAL.name)} ORDER BY drugid DESC LIMIT 1`);
    h.assert(controlSpecial.includes(controlDirections)&&!controlSpecial.includes('LU Code:'),'Saving LU codes changed control directions');
    await rx.reload({waitUntil:'networkidle'});
    await rx.getByRole('link',{name:'Reprint',exact:true}).first().click();
    await rx.locator(`#reprint a[onclick*="reprint2('${script}')"]`).first().click();
    const host=await viewScriptFrame(rx);
    h.assert(host,'The real Reprint control did not open a prescription view');
    await host.locator('#preview').waitFor({state:'attached'});
    const preview=host.frameLocator('#preview');
    await preview.locator('body').filter({hasText:wanted}).waitFor({state:'visible',timeout:30000});
    h.assert((await preview.locator('body').innerText()).includes(wanted),'Prescription print preview lost the LU annotations');
  });
  await s.step('removing an unsaved LU card and closing its window creates no prescription',async()=>{
    const remove=otherRx.locator(`#set_${otherCard} a[onclick*="removePrescribingDrug"]`);
    await remove.click();await otherRx.locator(`#set_${otherCard}`).waitFor({state:'detached'});await otherRx.close();
    h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${otherPatient}`)==='0','Removing an unsaved LU card created a prescription');
  });
}
if(require.main===module)runWorkflow('rx-limited-use-code-persistence',workflow,{openPatient:true});
module.exports={workflow};
