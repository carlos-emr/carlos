#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {stageFromSearch} = require('./rx-edit-discontinue-playwright-checks');
const {masterRecordLink} = require('./lib/master-record-link');
const {openSecondPatient} = require('./lib/second-patient-window');
const {removeMarkedPatients} = require('./lib/gap-records-fixtures');
const {renalInputFixture, CASES} = require('./lib/renal-input-fixture');
const {RENAL} = require('./rx-interactions-renal-luc-playwright-checks');

async function workflow(s) {
  const {sql, patient, marker} = s, controlName=marker+'-CONTROL';
  const groupIds=[],typeIds=[];let styleId;
  s.cleanup(()=>{
    if(groupIds.length)sql.execute(`DELETE FROM measurementGroup WHERE id IN (${groupIds.join(',')}) AND name='Renal Dosing'`);
    if(styleId)sql.execute(`DELETE FROM measurementGroupStyle WHERE groupID=${styleId} AND groupName='Renal Dosing'`);
    if(typeIds.length)sql.execute(`DELETE FROM measurementType WHERE id IN (${typeIds.join(',')}) AND typeDisplayName LIKE ${h.sqlString(marker+'%')}`);
    for(const [table,key,ids] of [['measurementGroup','id',groupIds],['measurementType','id',typeIds],['measurementGroupStyle','groupID',styleId?[styleId]:[]]])
      if(ids.length)h.assert(sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${key} IN (${ids.join(',')})`)==='0','Owned renal measurement configuration remains');
  });
  // The packaged demo schema has no Renal Dosing group. Supply an owned group
  // for the existing panel link; never alter a configured group's definitions.
  if(sql.value("SELECT COUNT(*) FROM measurementGroup WHERE name='Renal Dosing'")==='0') {
    for(const [type,instruction,validation] of [['WT','in kg','5'],['SCR','in umol/L','14']]) {
      const display=marker+'-'+type;
      typeIds.push(h.insertId(sql,`INSERT INTO measurementType(type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
        VALUES (${h.sqlString(type)},${h.sqlString(display)},${h.sqlString(display)},${h.sqlString(instruction)},${h.sqlString(validation)},NOW())`,'owned renal measurement type'));
      groupIds.push(h.insertId(sql,`INSERT INTO measurementGroup(name,typeDisplayName) VALUES ('Renal Dosing',${h.sqlString(display)})`,'owned renal group entry'));
    }
    if(sql.value("SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName='Renal Dosing'")==='0')styleId=h.insertId(sql,"INSERT INTO measurementGroupStyle(groupName,cssID) VALUES ('Renal Dosing',0)",'owned renal group style');
  }
  renalInputFixture(s).apply(CASES['male-valid']);
  s.cleanup(()=>removeMarkedPatients(sql,controlName));
  sql.execute(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(controlName)},'Control','1966','01','01','M','AC',${h.sqlString(s.provider)},'ON','ON','NR',NOW())`);
  const controlPatient=sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(controlName)}`);
  renalInputFixture({...s,patient:controlPatient}).apply(CASES['female-valid']);
  const rx=await s.popup(s.master,masterRecordLink(s.master,'/rx/choosePatient').first(),'renal-correction-rx');
  let card;
  const [initial]=await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
    (async()=>{card=await stageFromSearch(rx,RENAL.term,RENAL.name);})()]);
  const originalAtc=new URL(initial.url()).searchParams.get('atcCode');
  h.assert(originalAtc&&originalAtc.includes(RENAL.atc),'Initial renal lookup does not identify the controlled drug');
  const panel=rx.locator(`#renalDosing_${card}`);
  await panel.locator('table.equation').waitFor({state:'visible'});
  h.assert((await panel.innerText()).includes('Clcr 80 ='),'Initial clearance is wrong');
  h.assert(await panel.locator('tr.selected').count()===0,'Initial high clearance selected a reduced dose');
  const {masterPage:controlMaster}=await openSecondPatient(s,controlPatient,controlName);
  const controlRx=await s.popup(controlMaster,masterRecordLink(controlMaster,'/rx/choosePatient').first(),'renal-correction-control');
  const controlCard=await stageFromSearch(controlRx,RENAL.term,RENAL.name),controlPanel=controlRx.locator(`#renalDosing_${controlCard}`);
  await controlPanel.locator('table.equation').waitFor({state:'visible'});
  const controlBefore=await controlPanel.innerText();
  const measurements=who=>sql.rows(`SELECT type,dataField,measuringInstruction,DATE_FORMAT(dateObserved,'%Y-%m-%d'),providerNo FROM measurements WHERE demographicNo=${who} ORDER BY id`);
  const controlStored=JSON.stringify(measurements(controlPatient));
  const openMeasurements=async()=>{
    const popup=await s.popup(rx,panel.locator('a[onclick*="SetupMeasurements"]').first(),'renal-measurements');
    const params=new URL(popup.url()).searchParams;
    h.assert((params.get('demographicNo')||params.get('demographic_no'))===patient && params.get('groupName')==='Renal Dosing','Measurement popup lost its patient or group');
    h.assert(await popup.locator('input[name="demographicNo"]').inputValue()===patient,'Rendered measurement form belongs to another patient');
    await popup.locator('#row-WT td:nth-child(3) input').waitFor({state:'visible'});
    await popup.locator('tr[id="row-SCR" i] td:nth-child(3) input').waitFor({state:'visible'});
    return popup;
  };
  const refresh=async()=>{
    const [response]=await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
      panel.getByRole('button',{name:'Refresh',exact:true}).click()]);
    const params=new URL(response.url()).searchParams;
    h.assert(response.status()===200,`Renal refresh returned HTTP ${response.status()}`);
    h.assert(params.get('demographicNo')===patient,'Refresh lost the originating patient');
    h.assert(params.get('atcCode')===originalAtc,`Refresh changed the controlled drug ATC (${params.get('atcCode')})`);
    h.assert(await panel.locator('table.equation').count()===1,'Refresh appended a duplicate panel');
  };
  await s.step('measurement links save corrected values, units, dates and provider on the originating patient',async()=>{
    const popup=await openMeasurements(),today=sql.value('SELECT CURDATE()');
    for(const [type,value] of [['WT','40'],['SCR','200']]) {
      const row=popup.locator(`tr[id="row-${type}" i]`);
      await row.locator(`input[type="radio"][value="${type==='WT'?'in kg':'in umol/L'}"]`).check();
      await row.locator('td:nth-child(3) input').fill(value);
      await row.locator('input[name^="date-"]').fill(today);
      await row.locator('input[name^="comments-"]').fill(marker);
    }
    const closed=popup.waitForEvent('close');
    const [response]=await Promise.all([popup.waitForResponse(r=>r.request().method()==='POST' && /\/encounter\/Measurements\?ajax=true/.test(r.url())),
      popup.locator('input[name="Button"][value="Submit"]').click()]);
    // Successful Submit closes this popup immediately; reading its response
    // body after that close races page teardown. Assert HTTP and the exact
    // persisted observations below instead of depending on a detached body.
    h.assert(response.status()===200,'Renal measurement save failed');
    await closed;
    await expectValue(sql,`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND comments=${h.sqlString(marker)} AND dateObserved=CURDATE() AND providerNo=${h.sqlString(s.provider)}`,'2','Corrected observations were not stored');
    const added=measurements(patient).slice(-2).sort((a,b)=>a[0].localeCompare(b[0]));
    h.assert(JSON.stringify(added)===JSON.stringify([['SCR','200','in umol/L',today,s.provider],['WT','40','in kg',today,s.provider]]),'Saved renal values, units, dates or provider differ');
  });
  await s.step('Refresh replaces the equation and crosses into the inclusive reduced-dose recommendation',async()=>{
    await controlRx.bringToFront();await refresh();
    await rx.waitForFunction(id=>document.querySelector(id)?.textContent.includes('Clcr 20 ='),`#renalDosing_${card}`);
    const text=(await panel.innerText()).replace(/\s+/g,' ');
    h.assert(/(?:^|[^\d.])40\s*\[kg/.test(text) && /(?:^|[^\d.])200\s+sCr/.test(text),'Refresh retained old clinical inputs');
    const date=sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%b-%d')");
    h.assert(text.split(date).length-1===2,'Refresh retained old observation dates');
    h.assert(await panel.locator('tr.selected').count()===1 && (await panel.locator('tr.selected').innerText()).includes('250-500 mg po q18h'),'New clearance selected the wrong dose');
    h.assert(await controlPanel.innerText()===controlBefore && JSON.stringify(measurements(controlPatient))===controlStored,'Correction affected the other patient');
  });
  await s.step('closing unsaved correction leaves stored measurements and the refreshed recommendation unchanged',async()=>{
    const before=JSON.stringify(measurements(patient)),popup=await openMeasurements();
    await popup.locator('#row-WT td:nth-child(3) input').fill('99');await popup.close();await refresh();
    h.assert(JSON.stringify(measurements(patient))===before,'Cancelled measurement entry persisted data');
    h.assert((await panel.innerText()).includes('Clcr 20 =') && await panel.locator('tr.selected').count()===1,'Cancelled correction changed the dose');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no IN (${patient},${controlPatient})`)==='0','Renal correction saved an unintended prescription');
  });
}
if(require.main===module)runWorkflow('rx-renal-correction',workflow,{openPatient:true});
module.exports={workflow};
