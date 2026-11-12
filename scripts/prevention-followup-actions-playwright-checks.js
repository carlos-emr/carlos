#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Preventions > brand > Set Next Date > Save > reopen > Never Remind > Save;
// independent branch resumes reminders and removes the suppression reason.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {activePrevention}=require('./prevention-lifecycle-playwright-checks');
const resume=process.env.PREVENTION_FOLLOWUP_ACTION==='resume';
const name=resume?'prevention-resume-reminders':'prevention-next-date-never-remind';
async function workflow(s){
  const {sql,patient,marker}=s,q=h.sqlString;
  s.cleanup(()=>{
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`)==='0','Owned preventions remain');
  });
  const chart=await s.chart(),index=await s.popup(chart,chart.locator('a[onclick*="ViewPreventionIndex"]').first(),'prevention-followup-index');
  let editor,id;
  const open=async()=>{
    editor=await s.popup(index,index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${id}"]`).first(),'prevention-followup-edit');
  };
  const save=async()=>{
    await Promise.all([editor.waitForEvent('close'),editor.locator('input[type="submit"][name="action"]').first().click()]);
    id=activePrevention(sql,patient);
    await index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${id}"]`).first().waitFor();
  };
  const disclose=async()=>{
    if(!await editor.locator('#nextDateDiv').isVisible())await editor.locator('a[onclick*="showHideNextDate"]').click();
    await editor.locator('#nextDateDiv').waitFor({state:'visible'});
  };
  await s.step('the brand picker opens a prevention entry for the owned patient',async()=>{
    await index.locator('#immunization').fill('Fluzone');
    editor=await s.popup(index,index.locator('#immunization_choices .ac-item').first(),'prevention-followup-add');
    h.assert(await editor.locator('[name="prevention"]').inputValue()==='Inf','Wrong prevention type');
    await editor.locator('#prevDate').fill('2026-01-02');
    await editor.locator('[name="comments"]').fill(marker);
  });
  {
    await s.step('Set Next Date persists the chosen date and reopens it without suppressing reminders',async()=>{
      await disclose();await editor.locator('#nextDate').fill('2027-01-02');await save();
      h.assert(sql.value(`SELECT CONCAT(DATE(next_date),'|',never) FROM preventions WHERE id=${id}`)==='2027-01-02|0','Next date or reminder flag was not saved');
      await open();await disclose();h.assert(await editor.locator('#nextDate').inputValue()==='2027-01-02'&&!await editor.locator('#neverWarn').isChecked(),'Next date did not reopen');
    });
    await s.step('Never Remind and its reason replace the reminder settings and round-trip',async()=>{
      const previous=id;await editor.locator('#neverWarn').check();await editor.locator('[name="neverReason"]').fill(`${marker} declined reminders`);
      h.assert(await editor.locator('#nextDate').isDisabled(),'Never Remind did not disable Next Date');await save();
      await expectValue(sql,`SELECT deleted FROM preventions WHERE id=${previous}`,'1','Reminder edit did not preserve the old record as deleted');
      h.assert(sql.value(`SELECT never FROM preventions WHERE id=${id}`)==='1','Never Remind was not saved');
      h.assert(sql.value(`SELECT val FROM preventionsExt WHERE prevention_id=${id} AND keyval='neverReason'`)===`${marker} declined reminders`,'Reminder reason was not saved');
      await open();await disclose();h.assert(await editor.locator('#neverWarn').isChecked()&&await editor.locator('[name="neverReason"]').inputValue()===`${marker} declined reminders`,'Reminder preference or reason did not reopen');
    });
    if(resume) await s.step('resume reminders clears Never Remind and its reason and saves a new next date',async()=>{
      await editor.locator('#neverWarn').uncheck();
      await editor.locator('[name="neverReason"]').fill('');
      await editor.locator('#nextDate').fill('2027-02-03');await save();
      h.assert(sql.value(`SELECT CONCAT(never,'|',DATE(next_date)) FROM preventions WHERE id=${id}`)==='0|2027-02-03','Resuming reminders did not persist');
      h.assert(sql.value(`SELECT COALESCE(MAX(val),'') FROM preventionsExt WHERE prevention_id=${id} AND keyval='neverReason'`)==='','Cleared reason remained stored');
      await open();await disclose();h.assert(!await editor.locator('#neverWarn').isChecked()&&await editor.locator('#nextDate').inputValue()==='2027-02-03','Resumed reminder did not reopen');
    });
  }
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
