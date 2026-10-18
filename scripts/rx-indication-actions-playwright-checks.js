#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Rx profile > Indication > archive one existing reason, or choose an existing
// patient diagnosis and save it as the drug's indication. No typed entry URLs.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const archive=process.env.RX_INDICATION_ACTION!=='registry';
const name=archive?'rx-indication-archive':'rx-indication-patient-registry';
async function workflow(s){
  const {sql,patient,provider,marker}=s,q=h.sqlString;
  const f=await prepareRx(s),drug=f.seed('indication'),control=f.seed('control');
  let target,kept;
  if(archive){
    const seed=(id,code)=>h.insertId(sql,`INSERT INTO drugReason(drugId,codingSystem,code,comments,primaryReasonFlag,providerNo,demographicNo,archivedFlag,dateCoded)
      VALUES(${id},'icd9',${q(code)},${q(marker)},1,${q(provider)},${patient},0,CURDATE())`,'indication');
    target=seed(drug,'401');kept=seed(control,'250');
  }else{
    s.cleanup(()=>{sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient}`);h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient}`)==='0','Owned patient diagnosis remains');});
    sql.execute(`INSERT INTO dxresearch(demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
      VALUES(${patient},CURDATE(),NOW(),'A','401','icd9',0,${q(provider)})`);
  }
  const rx=await f.open();
  const open=()=>s.popup(rx,rx.locator(`a[onclick*="popupRxReasonWindow("][onclick*=",${drug})"]`).first(),'rx-indication-action');
  let reason=await open();
  if(archive){
    await s.step('opening and closing Archive without submission preserves both drug indications',async()=>{
      await reason.locator(`a[onclick="toggleArchiveMenu('archive${target}')"]`).click();
      await reason.locator(`#archive${target} input[name="archiveReason"]`).fill(marker+' not submitted');
      await reason.close();
      h.assert(sql.value(`SELECT SUM(archivedFlag) FROM drugReason WHERE id IN (${target},${kept})`)==='0','Opening Archive changed a reason');
      reason=await open();
    });
    await s.step('Archive retains the selected indication history and leaves the other drug indication active',async()=>{
      await reason.locator(`a[onclick="toggleArchiveMenu('archive${target}')"]`).click();
      const text=`${marker} diagnosis corrected`;
      await reason.locator(`#archive${target} input[name="archiveReason"]`).fill(text);
      const [response]=await Promise.all([reason.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/RxReason')&&r.request().method()==='POST'),reason.locator(`#archive${target} input[type="submit"]`).click()]);
      h.assert(response.status()===200,'Archiving indication was refused');
      await expectValue(sql,`SELECT CONCAT(archivedFlag,'|',archivedReason) FROM drugReason WHERE id=${target}`,`1|${text}`,'Archive did not preserve its reason');
      h.assert(sql.value(`SELECT archivedFlag FROM drugReason WHERE id=${kept}`)==='0','Archive changed the other drug indication');
      if(!reason.isClosed())await reason.close();reason=await open();
      h.assert(await reason.locator(`a[onclick="toggleArchiveMenu('archive${target}')"]`).count()===0,'Archived indication remains in Current Indications');
      h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND archived=0`)==='2','Archiving an indication archived a medication');
    });
  }else{
    await s.step('the Patient Dx Registry link fills and saves exactly that drug indication',async()=>{
      await reason.locator('a[onclick^="assignPatientDxLink(\'401\'"]').click();
      h.assert(await reason.locator('#jsonDxSearch').inputValue()==='401','Registry choice did not fill the submitted diagnosis code');
      await reason.locator('#comments').fill(marker+' from registry');await reason.locator('#primaryReasonFlag').check();
      await reason.locator('#saveRxReason').click();
      await expectValue(sql,`SELECT COUNT(*) FROM drugReason WHERE drugId=${drug} AND demographicNo=${patient} AND code='401' AND primaryReasonFlag=1 AND archivedFlag=0`,'1','Registry indication did not save');
      h.assert(sql.value(`SELECT COUNT(*) FROM drugReason WHERE drugId=${control}`)==='0','Registry choice filed an indication on the control drug');
    });
  }
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
