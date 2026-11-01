#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {execFileSync}=require('node:child_process');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {appointmentAdd,ticklerAdd,chartNoteSave,rxSave}=require('./lib/mutation-families');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {masterRecordLink}=require('./lib/master-record-link');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');

async function workflow(s) {
  h.assert(process.env.CARLOS_DISPOSABLE_HOST==='true','Upgrade canaries require a disposable install');
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
    execFileSync('bash',['/root/carlos/scripts/lib/deb-upgrade-install.sh'],{stdio:'inherit',timeout:900000});
    queries.forEach((query,index)=>h.assert(JSON.stringify(s.sql.rows(query))===before[index],`Upgrade changed retained clinical canary ${index}`));
  });
  await s.step('sign in again and reopen the retained patient, tasks, chart note and prescription',async()=>{
    const browser=await h.launchBrowser(s.config);s.cleanup(()=>browser.close());
    const context=await h.newContext(browser,s.config);context.setDefaultTimeout(30000);
    context.on('page',page=>h.wireStrictPage(page,'deb-upgrade-canaries',s.recorder));
    const schedule=await h.login(context,s.config,s.recorder);
    const {masterPage:master}=await openMasterRecord(context,schedule,s.recorder,{searchTerm:s.marker,preferredDemographicNo:s.patient,timeout:30000});
    h.assert(new URL(master.url()).searchParams.get('demographic_no')===s.patient,'Upgrade reopened another patient');
    const ticklers=await ui.clickOpensPopup(master,masterRecordLink(master,'/tickler/ViewTicklerMain').first(),{context,recorder:s.recorder,label:'upgraded-ticklers',timeout:30000});
    await ticklers.getByText(`${s.marker}-TA-UPGRADE`,{exact:false}).first().waitFor({state:'visible'});
    const chart=await openChart(context,master,s.recorder,30000);await waitForNavbars(chart,30000);
    h.assert((await chart.locator('body').innerText()).includes(`${s.marker}-NS-UPGRADE`),'Reopened chart lost the retained note');
    const rx=await ui.clickOpensPopup(master,masterRecordLink(master,'/rx/choosePatient').first(),{context,recorder:s.recorder,label:'upgraded-prescription',timeout:30000});
    await rx.getByText(`${s.marker}-RX-UPGRADE`,{exact:false}).first().waitFor({state:'visible'});
  });
}
if(require.main===module)runWorkflow('deb-upgrade-canaries',workflow,{openPatient:true});
module.exports={workflow};
