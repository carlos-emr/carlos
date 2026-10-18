#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Consultations > choose owned consultant > Search > referral/appointment date
// and Include Completed, or the overdue referrals' Add Tickler control.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');
const tickler = process.env.CONSULT_FOLLOWUP_ACTION === 'tickler';
const name = tickler ? 'consultation-overdue-tickler' : 'consultation-date-completion-filters';
async function workflow(s) {
  const {sql, patient, marker, provider} = s;
  const q = h.sqlString;
  let service, specialist;
  s.cleanup(() => {
    const requests=`SELECT requestId FROM consultationRequests WHERE demographicNo=${patient}`;
    sql.execute(`DELETE FROM consultationRequestExtArchive WHERE requestId IN (${requests});
      DELETE FROM consultationRequestsArchive WHERE demographicNo=${patient};
      DELETE FROM consultationRequestExt WHERE requestId IN (${requests});
      DELETE FROM consultationRequests WHERE demographicNo=${patient}`);
    if(service) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${service}; DELETE FROM consultationServices WHERE serviceId=${service} AND serviceDesc=${q(marker)}`);
    if(specialist)sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${specialist} AND lName=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient}`)==='0','Owned referrals remain');
  });
  service=h.insertId(sql,`INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(marker)},'1')`,'service');
  specialist=h.insertId(sql,`INSERT INTO professionalSpecialists(fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Followup',${q(marker)},'555-0160','555-0161','Synthetic',NOW(),0,0,0,0)`,'specialist');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${service},${specialist})`);
  function seed(days,status,apptDays) {
    return h.insertId(sql,`INSERT INTO consultationRequests(referalDate,appointmentDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,allergies,concurrentProblems,statusText,sendTo,patientWillBook,lastUpdateDate)
      VALUES(DATE_SUB(CURDATE(),INTERVAL ${days} DAY),DATE_ADD(CURDATE(),INTERVAL ${apptDays} DAY),${service},${specialist},${q(provider)},${patient},${q(status)},'2',${q(marker+' referral')},'','','','','','',0,NOW())`,'referral');
  }
  const overdue=seed(14,'1',5), recent=seed(2,'1',10), completed=seed(14,'4',5);
  let list;
  await s.step('Consultations search isolates the owned consultant and its open referrals',async()=>{
    ({page:list}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.getByRole('link',{name:'Consultations',exact:true}),{context:s.context,recorder:s.recorder,label:name}));
    await list.locator('#consultantSearch').pressSequentially(marker,{delay:20});
    await list.locator('#consultantSuggestions [role="option"]').filter({hasText:marker}).click();
    await list.locator('#includeCompleted').uncheck();
    await list.locator('#startDate').fill('');await list.locator('#endDate').fill('');
    await ui.clickAndAwaitReload(list,list.locator('#consultationFilterForm input[type="submit"]'));
    h.assert(await list.locator('#consultantId').inputValue()===specialist,'Consultant selection lost');
    await list.locator(`tr[onclick*="requestId=${overdue}'"]`).waitFor();
    h.assert(await list.locator(`tr[onclick*="requestId=${recent}'"]`).count()===1,'Recent control missing');
    h.assert(await list.locator(`tr[onclick*="requestId=${completed}'"]`).count()===0,'Completed control included by default');
  });
  if(tickler) {
    await s.step('overdue consultation Add Tickler preserves the patient and suggested follow-up message',async()=>{
      const link=list.locator('a[href*="/tickler/ViewAddTickler?"]');
      h.assert(await link.count()===1,'No overdue Add Tickler control');
      const href=await link.getAttribute('href');
      h.assert(new URL(href,list.url()).searchParams.getAll('demo').every(x=>x===patient),'Bulk control targets unowned patients');
      const dialogs=await h.withExpectedDialogs(list,async()=>{
        await Promise.all([list.waitForEvent('dialog'),link.click()]);
      },{accept:false});
      h.assert(dialogs.length===1&&dialogs[0].type==='confirm','Bulk tickler cancellation was not confirmed');
      let form;
      await h.withExpectedDialogs(list,async()=>{form=await s.popup(list,link,'consultation-followup-tickler');},{accept:true});
      h.assert(await form.locator('form[name="serviceform"] input[name="demographic_no"]').inputValue()===patient,'The overdue referral task form lost its patient');
      h.assert((await form.locator('#ticklerMessage').inputValue()).includes('Nothing Done'),'The overdue referral task form lost its suggested message');
      h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient}`)==='0','Opening or cancelling follow-up wrote a task');
    });
  } else {
    await s.step('referral-date range and Include Completed select the correct owned referrals',async()=>{
      const date=sql.value("SELECT DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 14 DAY),'%Y-%m-%d')");
      await list.locator('#startDate').fill(date);await list.locator('#endDate').fill(date);
      await list.locator('#searchDateRef').check();await list.locator('#includeCompleted').check();
      await ui.clickAndAwaitReload(list,list.locator('#consultationFilterForm input[type="submit"]'));
      h.assert(await list.locator(`tr[onclick*="requestId=${overdue}'"]`).count()===1,'Matching referral omitted');
      h.assert(await list.locator(`tr[onclick*="requestId=${completed}'"]`).count()===1,'Include Completed omitted matching completed referral');
      h.assert(await list.locator(`tr[onclick*="requestId=${recent}'"]`).count()===0,'Out of range referral included');
    });
    await s.step('appointment-date range switches the date basis and preserves completion and consultant filters',async()=>{
      const date=sql.value("SELECT DATE_FORMAT(DATE_ADD(CURDATE(),INTERVAL 10 DAY),'%Y-%m-%d')");
      await list.locator('#startDate').fill(date);await list.locator('#endDate').fill(date);await list.locator('#searchDateAppt').check();
      await ui.clickAndAwaitReload(list,list.locator('#consultationFilterForm input[type="submit"]'));
      h.assert(await list.locator(`tr[onclick*="requestId=${recent}'"]`).count()===1,'Appointment date match omitted');
      h.assert(await list.locator('table.consult-table tbody tr[onclick]').count()===1,'Appointment date filter retained other referrals');
      h.assert(await list.locator('#includeCompleted').isChecked()&&await list.locator('#consultantId').inputValue()===specialist,'Search dropped other filters');
    });
  }
}
if(require.main===module)runWorkflow(name,workflow,{openMaster:false});
module.exports={workflow};
