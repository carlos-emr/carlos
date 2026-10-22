#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Renew a medication while correcting one saved field. Unlike the initial card
// options test, each case changes or clears a pre-existing value, verifies the
// replacement and the archived source, preserves an unselected drug, and reads
// the replacement through ReRx, the profile or its saved record as appropriate.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx,saveOnly,stageExisting}=require('./lib/rx-ui-workflow');
const ui=require('./lib/playwright-ui');
const cases={
  'long-term':{column:'long_term',before:'1',after:'0',id:'longTermN',kind:'radio',beforeId:'longTermY'},
  'past-medication':{column:'past_med',before:'1',after:'0',id:'pastMedN',kind:'radio',beforeId:'pastMedY'},
  'compliance':{column:'patient_compliance',before:'0',after:'1',id:'patientComplianceY',kind:'radio',beforeId:'patientComplianceN'},
  'authoritative':{column:'non_authoritative',before:'0',after:'1',id:'nonAuthoritativeN',kind:'checkbox',checked:true},
  'outside-provider':{column:'outside_provider_name',before:'FAKE Outside Clinic',after:'',id:'ocheck',kind:'checkbox',checked:false,extra:{outside_provider_ohip:'123456'}},
  'no-substitution':{column:'nosubs',before:'1',after:'0',id:'nosubs',kind:'checkbox',checked:false},
  'refill-duration':{column:'refill_duration',before:'28',after:'0',id:'refillDuration'},
  'refill-quantity':{column:'refill_quantity',before:'30',after:'0',id:'refillQuantity'},
  'dispense-interval':{column:'dispense_interval',before:'7',after:'0',id:'dispenseInterval'},
  'protocol':{column:'protocol',before:'FAKE follow-up protocol',after:'',id:'protocol'},
  'last-refill':{column:'last_refill_date',before:'2026-01-02',after:'',id:'lastRefillDate'},
  'comment':{column:'comment',before:'',after:'FAKE renewal comment',id:'comment'},
  'pickup':{column:'pickup_datetime',before:'2026-12-01 12:30:00',after:'',id:'pickupDate',kind:'pickup'},
  'status':{column:'rxStatus',before:'Active',after:'Suspended',id:'rxStatus',kind:'select'},
  'treatment':{column:'eTreatmentType',before:'CHRON',after:'ACU',id:'eTreatmentType',kind:'select'},
  'treatment-clear':{column:'eTreatmentType',before:'CHRON',after:'--',persisted:'',id:'eTreatmentType',kind:'select'},
  'start-known':{column:'start_date_unknown',before:'0',after:'0',id:'startDateUnknown',kind:'checkbox',checked:false},
  'start-unknown':{column:'start_date_unknown',before:'0',after:'1',id:'startDateUnknown',kind:'checkbox',checked:true},
};
const action=process.env.RX_RENEWAL_FIELD;
const name=`rx-renewal-${action}`;
async function workflow(s){
  const c=cases[action];h.assert(c,'RX_RENEWAL_FIELD must name a supported renewal task');
  const {sql,patient,marker}=s,q=h.sqlString,f=await prepareRx(s);
  const old=f.seed('renewal',{comment:marker+' retain',...c.extra,[c.column]:c.before});
  const control=f.seed('control'),snapshot=JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE drugid=${control}`));
  let rx=await f.open(),key;
  const el=()=>c.kind==='select'?rx.locator(`select[name="${c.id}_${key}"]`):rx.locator(`#${c.id}_${key}`);
  const saved=drug=>sql.value(`SELECT COALESCE(CAST(\`${c.column}\` AS CHAR),'') FROM drugs WHERE drugid=${drug}`);
  async function more(){if(!await rx.locator(`#rx_more_${key}`).isVisible())await rx.locator(`#moreLessWord_${key}`).click();}
  async function shown(expected){
    await more();
    if(c.kind==='radio')h.assert(await rx.locator(`#${expected===c.before?c.beforeId:c.id}_${key}`).isChecked(),'Renewal did not restore the saved selection');
    else if(c.kind==='checkbox')h.assert(await el().isChecked()===(expected===c.before?(c.before==='1'||action==='outside-provider'):c.checked),'Renewal did not restore the saved checkbox');
    else if(c.kind==='pickup')h.assert(await el().inputValue()===(expected?expected.slice(0,10):''),'Renewal did not restore the saved pickup date');
    else h.assert(await el().inputValue()===expected,'Renewal did not restore the saved field');
  }
  await s.step('ReRx restores the saved field before the clinician changes it',async()=>{
    key=await stageExisting(rx,old);await shown(c.before);
    h.assert(saved(old)===c.before&&JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE drugid=${control}`))===snapshot,'Staging altered stored clinical data');
  });
  let replacement;
  await s.step('saving the changed renewal field preserves its source and the unselected medication',async()=>{
    if(c.kind==='radio')await el().check();
    else if(c.kind==='checkbox')await el().setChecked(c.checked);
    else if(c.kind==='select')await el().selectOption(c.after);
    else if(c.kind==='pickup'){
      const dialogs=await h.withExpectedDialogs(rx,async()=>{await el().fill('');await rx.locator(`#pickupTime_${key}`).fill('');await rx.locator('#searchString').click();});
      h.assert(dialogs.length===0,'Clearing optional pickup date/time raised invalid-format alerts');
    }
    else await el().fill(c.after);
    if(action==='start-known')await rx.locator(`#rxDate_${key}`).fill('2026-01-02');
    await saveOnly(rx);
    await expectValue(sql,`SELECT archived_reason FROM drugs WHERE drugid=${old}`,'represcribed','Renewal did not archive its source as re-prescribed');
    const rows=sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND customName=${q(marker+' renewal')} AND archived=0`);
    h.assert(rows.length===1,'Renewal did not create exactly one current replacement');replacement=rows[0][0];
    h.assert(saved(replacement)===(c.persisted??c.after),`Renewal failed to change ${c.column}`);
    h.assert(saved(old)===c.before,'Renewal changed the clinical value on its archived source');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE drugid=${control}`))===snapshot,'Renewal changed the unselected medication');
    if(action==='start-known')h.assert(sql.value(`SELECT DATE(rx_date) FROM drugs WHERE drugid=${replacement}`)==='2026-01-02','Known start date was not saved');
  });
  await s.step('the saved renewal exposes the corrected field for the next visit',async()=>{
    await rx.close();rx=await f.open();
    if(action==='start-known'||action==='start-unknown'){
      const date=rx.locator(`#createDate_${replacement}`).locator('xpath=../following-sibling::td[1]');
      h.assert((await date.innerText()).trim()===(action==='start-unknown'?'':'2026-01-02'),'Profile lost the saved start-date precision');
      return;
    }
    if(['authoritative','comment'].includes(action)){
      // Read the saved event through its real record link, then prove the next ReRx
      // carries the value over rather than asking the prescriber to re-enter it (#5056).
      await ui.clickAndAwaitReload(rx,rx.locator(`#prescrip_${replacement}`));
      const record=await s.popup(rx,rx.locator(`a[onclick*="/rx/ViewDisplayRxRecord?id=${replacement}'"]`).first(),'renewal-clinical-record');
      const field=async label=>(await record.locator('td.label').filter({hasText:label}).first().locator('xpath=following-sibling::td[1]').innerText()).trim();
      if(action==='authoritative')h.assert(await field('Non Authorative:')==='true','Saved record lost the non-authoritative selection');
      else if(action==='comment')h.assert(await field('Comment:')===c.after,'Saved record lost the renewal comment');
      await record.close();await rx.close();rx=await f.open();
    }
    key=await stageExisting(rx,replacement);await shown(c.after);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='3','Reopening renewal saved an unintended prescription');
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow,cases};
