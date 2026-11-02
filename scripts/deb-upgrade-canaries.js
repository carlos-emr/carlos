#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {runOwnedInstaller}=require('./lib/owned-installer');
async function installPackages(s) {
  try {await runOwnedInstaller('bash',['/root/carlos/scripts/lib/deb-upgrade-install.sh']);}
  catch(error){if(error.uncertain)require('./lib/uncertain-server-writes').retainFixtures(s.context);throw error;}
}
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {appointmentAdd,ticklerAdd,chartNoteSave,rxSave,openDaySheet}=require('./lib/mutation-families');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {masterRecordLink}=require('./lib/master-record-link');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');

async function reopenCanaries(s) {
    const browser=await h.launchBrowser(s.config);s.cleanup(()=>browser.close());
    const context=await h.newContext(browser,s.config);context.setDefaultTimeout(30000);
    context.on('page',page=>h.wireStrictPage(page,'deb-upgrade-canaries',s.recorder));
    const schedule=await h.login(context,s.config,s.recorder);
    const {masterPage:master}=await openMasterRecord(context,schedule,s.recorder,{searchTerm:s.marker,preferredDemographicNo:s.patient,timeout:30000});
    h.assert(new URL(master.url()).searchParams.get('demographic_no')===s.patient,'Upgrade reopened another patient');
    const appointment=s.sql.rows(`SELECT appointment_no,appointment_date,provider_no FROM appointment WHERE ${appointmentAdd(s).where('UPGRADE')} ORDER BY appointment_no`);
    h.assert(appointment.length===1,'The retained appointment is not unique');
    await openDaySheet({...s,schedule},appointment[0][1]);
    const link=schedule.locator(`a.apptLink[onclick*="appointment_no=${appointment[0][0]}"]`).first();
    await link.waitFor({state:'visible'});
    const editor=await ui.clickOpensPopup(schedule,link,{context,recorder:s.recorder,label:'upgraded-appointment',timeout:30000});
    h.assert(new URL(editor.url()).searchParams.get('appointment_no')===appointment[0][0],'Upgraded day sheet opened another appointment');
    h.assert(await editor.locator('[name="demographic_no"]').inputValue()===s.patient,'Upgraded appointment changed its patient binding');
    h.assert(await editor.locator('[name="reason"]').inputValue()===s.marker+'-AA-UPGRADE','Upgraded appointment lost its retained reason');
    await editor.close();
    const ticklers=await ui.clickOpensPopup(master,masterRecordLink(master,'/tickler/ViewTicklerMain').first(),{context,recorder:s.recorder,label:'upgraded-ticklers',timeout:30000});
    await ticklers.getByText(`${s.marker}-TA-UPGRADE`,{exact:false}).first().waitFor({state:'visible'});
    const chart=await openChart(context,master,s.recorder,30000);await waitForNavbars(chart,30000);
    // A reopened note may be the active editor. innerText omits textarea
    // values; wait for the chart's asynchronous notes and inspect both views.
    await chart.waitForFunction(text=>document.body.innerText.includes(text)
      ||Array.from(document.querySelectorAll('textarea[name="caseNote_note"]')).some(field=>field.value.includes(text)),`${s.marker}-NS-UPGRADE`,{timeout:30000});
    const rx=await ui.clickOpensPopup(master,masterRecordLink(master,'/rx/choosePatient').first(),{context,recorder:s.recorder,label:'upgraded-prescription',timeout:30000});
    await rx.locator('#drugProfile').getByText(`${s.marker}-RX-UPGRADE`,{exact:false}).first().waitFor({state:'visible'});
}

function preflight(env=process.env) {
  h.assert(env.CARLOS_DISPOSABLE_HOST==='true','Upgrade canaries require a disposable install');
}

async function workflow(s) {
  const families=[appointmentAdd(s),ticklerAdd(s),chartNoteSave(s),rxSave(s)];
  for(const family of families)s.cleanup(()=>family.cleanup());
  for(const family of families)await s.step(`create retained ${family.key} through the UI`,async()=>{
    await family.perform('UPGRADE');
    h.assert(Number(s.sql.value(`SELECT COUNT(*) FROM ${family.table} WHERE ${family.where('UPGRADE')}`))>0,
      `The ${family.key} canary was not persisted`);
  });
  const queries=[
    `SELECT last_name,first_name,patient_status FROM demographic WHERE demographic_no=${s.patient}`,
    `SELECT appointment_no,demographic_no,provider_no,appointment_date,start_time,end_time,reason FROM appointment WHERE ${families[0].where('UPGRADE')} ORDER BY appointment_no`,
    `SELECT tickler_no,demographic_no,task_assigned_to,message,status FROM tickler WHERE ${families[1].where('UPGRADE')} ORDER BY tickler_no`,
    `SELECT note_id,demographic_no,provider_no,note FROM casemgmt_note WHERE ${families[2].where('UPGRADE')} ORDER BY note_id`,
    `SELECT drugid,demographic_no,provider_no,customName,special FROM drugs WHERE ${families[3].where('UPGRADE')} ORDER BY drugid`,
  ];
  const before=queries.map(query=>JSON.stringify(s.sql.rows(query)));
  await s.step('upgrade the packages and verify the pre/post installation contract',async()=>{
    await s.context.browser().close();
    await installPackages(s);
    queries.forEach((query,index)=>h.assert(JSON.stringify(s.sql.rows(query))===before[index],`Upgrade changed retained clinical canary ${index}`));
  });
  await s.step('sign in again and reopen the retained patient, tasks, chart note and prescription',()=>reopenCanaries(s));
}
if(require.main===module)runWorkflow('deb-upgrade-canaries',workflow,{openPatient:true,cancellationGraceMs:1020000,preflightConfig:()=>preflight()});
module.exports={workflow,reopenCanaries,preflight};
