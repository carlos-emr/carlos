#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// #5735: native multi-row SOB preview/apply with existing fee history. SQL is
// fixture setup and an independent oracle; no real service code is changed.
const {randomInt}=require('node:crypto');
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {authenticatedProbe}=require('./lib/authenticated-probe');
const {seedOwnedBill}=require('./billing-on-invoice-third-party-playwright-checks');
const {openAdmin,adminFrame,navigates}=require('./billing-on-admin-config-playwright-checks');
const q=h.sqlString;
const SOB='/billing/CA/ON/ViewBenefitScheduleUpload';

// Integer cents -> four implied decimals, independently of production parsers.
function scheduleLine(code,date,cents) {
  h.assert(/^[WY]\d{3}$/.test(code)&&/^\d{8}$/.test(date)&&Number.isSafeInteger(cents)&&cents>0,'Invalid owned SOB line');
  const line=code+date+'99999999'+String(BigInt(cents)*100n).padStart(11,'0')+'0'.repeat(44);
  h.assert(line.length===75,'Owned SOB line must have exactly 75 characters');
  return line;
}
async function workflow(s) {
  const {sql,marker}=s;
  const codes=[];
  for(let i=0;i<4;i++) {
    let candidate;
    for(let attempt=0;attempt<100;attempt++) {
      const value='WY'[randomInt(2)]+String(randomInt(1000)).padStart(3,'0');
      if(!codes.includes(value)&&sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code LIKE ${q(value+'%')}`)==='0'){candidate=value;break;}
    }
    if(!candidate)throw new h.SkipCheck('Four unused synthetic SOB codes are required');
    codes.push(candidate);
  }
  const [changed,same,added,control]=codes;
  const service=code=>q(code+'A');
  const list=codes.map(service).join(',');
  // Register before the first write so partially completed SQL setup is owned.
  s.cleanup(()=>{
    const failures=[];
    for(const code of codes)try {
      const foreign=sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${service(code)}
        AND description NOT LIKE ${q(marker+'%')} AND NOT(service_code=${service(added)} AND description='----')`);
      h.assert(foreign==='0','Owned service-code namespace has unexpected rows; retaining that code');
      sql.execute(`DELETE FROM billingservice WHERE service_code=${service(code)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${service(code)}`)==='0','Owned SOB history remains');
    }catch(error){failures.push(error);}
    if(failures.length)throw new AggregateError(failures,'SOB fixture cleanup failed: '+failures.map(error=>error.message).join('; '));
  });
  for(const [code,value,date] of [[changed,'5.55','2019-01-01'],[changed,'10.01','2020-01-01'],[same,'20.02','2020-01-01'],[control,'77.77','2020-01-01']]) {
    sql.execute(`INSERT INTO billingservice(service_compositecode,service_code,description,value,percentage,billingservice_date,specialty,region,anaesthesia,termination_date,gstFlag,sliFlag)
      VALUES('',${service(code)},${q(marker+' '+code)},${q(value)},'',${q(date)},'','ON','00','9999-12-31',0,0)`);
  }
  const bill=seedOwnedBill(s,{payProgram:'HCP',status:'O',code:changed+'A',fee:'10.01',date:'2020-06-01'});
  const history=()=>JSON.stringify({header:sql.rows(`SELECT * FROM billing_on_cheader1 WHERE id=${bill.headerId}`),
    item:sql.rows(`SELECT * FROM billing_on_item WHERE id=${bill.itemId}`)});
  const historicClaim=history();
  const rows=code=>sql.rows(`SELECT * FROM billingservice WHERE service_code=${service(code)} ORDER BY billingservice_date,billingservice_no`);
  const state=()=>JSON.stringify(codes.map(rows));
  const initial=state(),oldChanged=rows(changed),unchanged=JSON.stringify(rows(same)),untouched=JSON.stringify(rows(control));
  function controls() {
    h.assert(history()===historicClaim,'SOB import changed a historical claim');
    h.assert(JSON.stringify(rows(same))===unchanged&&JSON.stringify(rows(control))===untouched,'SOB import changed an unchanged or unrelated fee');
  }
  const admin=await openAdmin(s);
  let frame;
  const valid=[scheduleLine(changed,'20210101',1234),scheduleLine(same,'20210101',2002),scheduleLine(added,'20210101',3333)];
  async function upload(lines) {
    frame=await adminFrame(admin,SOB,'input[name="importFile"]');
    await frame.locator('input[name="importFile"]').setInputFiles({name:'owned-multi-sob.txt',mimeType:'text/plain',buffer:Buffer.from(lines.join('\n')+'\n')});
    await frame.locator('input[name="showChangedCodes"]').check();
    await frame.locator('input[name="showNewCodes"]').check();
    await frame.locator('input[name="forceUpdate"]').uncheck();
    await navigates(admin,frame,frame.locator('input[type="submit"][name="Submit"]'));
  }
  async function preview() {
    h.assert(await frame.locator('.alert-success').count()===1,'Valid multi-row upload did not report success');
    const actual=[];
    for(const row of await frame.locator('#sbForm tr').filter({has:frame.locator('input[name="change"]')}).all()) {
      const cells=(await row.locator('td').allInnerTexts()).map(value=>value.trim());
      actual.push([cells[1],cells[2],cells[3],cells[5],cells[6],cells[7]]);
    }
    actual.sort((a,b)=>a[0].localeCompare(b[0]));
    const expected=[[changed+'A','10.01','12.34',marker+' '+changed,'20210101','99999999'],[added+'A','-----','33.33','----','20210101','99999999']].sort((a,b)=>a[0].localeCompare(b[0]));
    // The view uses a run of hyphens for an absent current fee.
    for(const row of actual)if(row[0]===added+'A'&&/^-+$/.test(row[1]))row[1]='-----';
    h.assert(JSON.stringify(actual)===JSON.stringify(expected),'Preview codes, old/new fees, descriptions or effective periods disagree');
  }
  await s.step('mixed valid and malformed lines refuse the complete file and preserve all fee history',async()=>{
    await upload([valid[0],added+'20210101',valid[2]]);
    h.assert(await frame.locator('.alert-danger').count()===1&&/problem uploading this SOB file/.test(await frame.locator('.alert-danger').innerText()),'Malformed file has no explicit upload refusal');
    h.assert(await frame.locator('input[name="change"]').count()===0,'Malformed mixed file offered partial changes');
    h.assert(state()===initial,'Malformed mixed file changed fees');controls();
    console.log('  Contract: malformed-file feedback is file-level; the release does not render per-line diagnostics.');
  });
  await s.step('corrected multi-row preview is read-only and abandoning it changes nothing',async()=>{
    await upload(valid);await preview();h.assert(state()===initial,'Preview changed fees');controls();
    frame=await adminFrame(admin,'/billing/CA/ON/AddEditServiceCode','input[name="service_code"]');
    h.assert(state()===initial,'Abandoning preview changed fees');controls();
  });
  await s.step('GET and tokenless POST cannot apply the previewed fee changes',async()=>{
    await upload(valid);await preview();
    const value=await frame.locator('#sbForm input[name="change"]').first().getAttribute('value');
    const get=await authenticatedProbe(s,h.appUrl(s.config.baseUrl,'/billing/CA/ON/benefitScheduleChange'),{params:{change:value}});
    try{h.assert(get.status()===405,'SOB apply accepted GET');}finally{await get.dispose();}
    const post=await s.context.request.post(h.appUrl(s.config.baseUrl,'/billing/CA/ON/benefitScheduleChange'),{form:{change:value},maxRedirects:0});
    try{h.assert(post.status()===403,'SOB apply accepted a tokenless POST');}finally{await post.dispose();}
    h.assert(state()===initial,'Refused SOB request changed fees');controls();
  });
  await s.step('native apply inserts the two expected rows and preserves earlier periods and billed amounts',async()=>{
    for(const checkbox of await frame.locator('#sbForm input[name="change"]').all())await checkbox.check();
    await navigates(admin,frame,frame.locator('#sbForm input[type="submit"]'));
    const applied=(await frame.locator('li').allInnerTexts()).map(text=>text.trim()).filter(text=>codes.some(code=>text.startsWith(code+'A '))).sort();
    h.assert(applied.length===2&&applied.some(text=>text.startsWith(changed+'A ')&&text.endsWith('12.34'))
      &&applied.some(text=>text.startsWith(added+'A ')&&text.endsWith('33.33')),'Apply feedback disagrees with selected changes');
    const periods=sql.rows(`SELECT service_code,value,billingservice_date,termination_date,region FROM billingservice WHERE service_code IN (${list}) ORDER BY service_code,billingservice_date,billingservice_no`);
    const expected=[[changed+'A','5.55','2019-01-01','9999-12-31','ON'],[changed+'A','10.01','2020-01-01','9999-12-31','ON'],[changed+'A','12.34','2021-01-01','9999-12-31','ON'],[same+'A','20.02','2020-01-01','9999-12-31','ON'],[added+'A','33.33','2021-01-01','9999-12-31','ON'],[control+'A','77.77','2020-01-01','9999-12-31','ON']].sort((a,b)=>a[0].localeCompare(b[0])||a[2].localeCompare(b[2]));
    h.assert(JSON.stringify(periods)===JSON.stringify(expected),'Applied fee periods or row counts disagree');
    h.assert(JSON.stringify(rows(changed).slice(0,2))===JSON.stringify(oldChanged),'Existing historical fee rows were overwritten');controls();
  });
  const afterApply=state();
  await s.step('reuploading the same schedule offers no changes and creates no duplicate periods',async()=>{
    await upload(valid);
    h.assert(await frame.locator('.alert-success').count()===1&&await frame.locator('#sbForm input[name="change"]').count()===0,'Identical schedule was not an unchanged successful preview');
    h.assert(state()===afterApply,'Identical schedule duplicated or mutated fee periods');controls();
  });
  await s.step('native code administration reopens both current prices without changing the historical claim',async()=>{
    for(const [code,value] of [[changed,'12.34'],[same,'20.02'],[added,'33.33'],[control,'77.77']]) {
      frame=await adminFrame(admin,'/billing/CA/ON/AddEditServiceCode','input[name="service_code"]');
      await frame.locator('input[name="service_code"]').fill(code+'A');
      await navigates(admin,frame,frame.locator('button[name="submitFrm"][value="Search"]').first());
      h.assert(await frame.locator('input[name="value"]').inputValue()===value,'Reopened code administration has the wrong current price');
    }
    h.assert(state()===afterApply,'Reopening fee administration changed history');controls();
  });
}
if(require.main===module)runWorkflow('billing-on-benefit-history',workflow,{openMaster:false});
module.exports={workflow,scheduleLine};
