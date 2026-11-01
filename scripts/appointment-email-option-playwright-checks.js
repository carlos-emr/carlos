#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// The release renders emailPt when emailApptReminder=yes but neither appointment
// action consumes it. This check records both real submissions and exposes the
// missing opt-in round trip; booking success is never counted as reminder success.
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
async function workflow(s) {
  process.env.APPOINTMENT_DEMOGRAPHIC_NO=s.patient;process.env.APPOINTMENT_PROVIDER_NO=s.provider;
  const f=require('./appointment-lifecycle-playwright-checks');f.initMysqlDefaults();
  s.cleanup(()=>{try{f.cleanupRows();}finally{f.cleanupMysqlDefaults();}});
  s.sql.execute(`UPDATE demographic SET email=${h.sqlString(s.marker+'@carlos.invalid')} WHERE demographic_no=${s.patient}`);
  await f.openDaySheet(s.schedule);
  let selected,ordinary;
  await s.step('Add posts checked and unchecked email choices for the owned appointment controls',async()=>{
    const submissions=[];
    const onRequest=request=>{
      if(request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/appointment/AddRecord'))submissions.push(new URLSearchParams(request.postData()||''));
    };
    s.context.on('request',onRequest);
    try {
      ordinary=await f.bookFromSlot(s.context,s.schedule,{slotIndex:2,configureAdd:page=>page.locator('input[name="emailPt"]').uncheck()});
      selected=await f.bookFromSlot(s.context,s.schedule,{slotIndex:4,configureAdd:page=>page.locator('input[name="emailPt"]').check()});
    } finally{s.context.off('request',onRequest);}
    h.assert(submissions.length===2&&!submissions[0].has('emailPt')&&submissions[1].get('emailPt')==='email reminder','The actual Add requests did not carry distinct reminder choices');
    h.assert(ordinary.id!==selected.id,'Reminder and ordinary controls share an appointment identity');
  });
  await s.step('cancelled reminder edits leave the appointment unchanged',async()=>{
    const before=s.sql.rows(`SELECT * FROM appointment WHERE appointment_no=${ordinary.id}`);
    const editor=await f.openEditPopup(s.context,s.schedule,ordinary.id);
    await editor.locator('input[name="emailPt"]').check();await editor.close();
    h.assert(JSON.stringify(s.sql.rows(`SELECT * FROM appointment WHERE appointment_no=${ordinary.id}`))===JSON.stringify(before),'Cancelled reminder edit changed the appointment');
  });
  await s.step('Edit posts both choices and a repeated checked Save does not duplicate either booking',async()=>{
    const requests=[];
    const listener=request=>{if(request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/appointment/UpdateRecord'))requests.push(new URLSearchParams(request.postData()||''));};
    s.context.on('request',listener);
    try {
      for(const [booking,checked] of [[ordinary,true],[ordinary,true],[selected,false]]) {
        const editor=await f.openEditPopup(s.context,s.schedule,booking.id);
        await editor.locator('input[name="emailPt"]').setChecked(checked);
        const [response]=await Promise.all([editor.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/appointment/UpdateRecord')),editor.locator('#updateButton').click()]);
        h.assert(response.status()===200,'Reminder Edit failed');await editor.close();
      }
    } finally {s.context.off('request',listener);}
    h.assert(requests.length===3&&requests.slice(0,2).every(body=>body.get('emailPt')==='email reminder')&&!requests[2].has('emailPt'),'Native Edit did not submit the checked, repeated and unchecked choices');
    h.assert(f.stampedAppointments().length===2,'Reminder edits duplicated a booking');
  });
  await s.step('requested reminder has an implemented and visible persisted outcome',async()=>{
    const editor=await f.openEditPopup(s.context,s.schedule,ordinary.id);
    const checked=await editor.locator('input[name="emailPt"]').isChecked();await editor.close();
    // No queue/transport assertion is possible before the product defines one.
    // A missing implementation must remain a visible finding, never a green
    // reminder result manufactured from an appointment row or invented queue.
    h.assert(checked,'emailPt was submitted checked, but the release neither records the choice nor handles emailPt in an appointment action (finding 329)');
  });
}
if(require.main===module)runWorkflow('appointment-email-option',workflow,{openPatient:true,openMaster:false});
module.exports={workflow};
