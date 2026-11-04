#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {stageFromSearch}=require('./rx-edit-discontinue-playwright-checks');
const {masterRecordLink}=require('./lib/master-record-link');
const {drugRefPair,interactionCheck,cardFor,shownMarkers,assertMarkers}=require('./rx-interaction-warnings-playwright-checks');
const PRODUCTS=[{term:'TYLENOL EXTRA STRENGTH',name:'TYLENOL EXTRA STRENGTH',atc:'N02BE01'},
  {term:'VALIUM 5',name:'VALIUM 5 TAB',atc:'N05BA01'}];
async function workflow(s) {
  const {sql,patient,provider}=s,predicate=`provider_no=${h.sqlString(provider)} AND name='rxInteractionWarningLevel'`;
  const query=`SELECT id,HEX(value),value IS NULL FROM property WHERE ${predicate} ORDER BY id`,original=sql.rows(query);
  s.cleanup(()=>{
    sql.execute(`DELETE FROM property WHERE ${predicate}`);
    for(const [id,hex,isNull] of original) {
      h.assert(/^\d+$/.test(id)&&/^[A-F0-9]*$/i.test(hex||''),'Invalid provider preference snapshot');
      sql.execute(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${h.sqlString(provider)},'rxInteractionWarningLevel',${isNull==='1'?'NULL':`UNHEX('${hex}')`})`);
    }
    h.assert(JSON.stringify(sql.rows(query))===JSON.stringify(original),'Provider warning preferences were not restored exactly');
    sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${patient};DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='0','Minor-interaction check unexpectedly saved a prescription');
  });
  const pair=drugRefPair(sql,...PRODUCTS.map(p=>p.atc));
  h.assert(pair.severity==='minor','The controlled DrugRef pair is not graded minor');
  const openPreferences=async()=>{
    const page=await s.popup(s.schedule,s.schedule.getByTitle(/Edit your personal setting/i).first(),'minor-warning-preferences');
    await page.locator('button[data-bs-target="#secRx"]').click();
    return page;
  };
  async function providerLevel(value) {
    const page=await openPreferences(),select=page.locator('#rxInteractionWarningLevel');
    h.assert(JSON.stringify(await select.locator('option').evaluateAll(nodes=>nodes.map(n=>n.value)))===JSON.stringify(['0','1','2','3','4']),'Offered warning levels changed');
    const [response]=await Promise.all([page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/provider/rxInteractionWarningLevel')),select.selectOption(value)]);
    h.assert(response.status()===200&&(await response.text()).trim()==='ok','Provider warning preference failed to save');
    await expectValue(sql,`SELECT value FROM property WHERE ${predicate} ORDER BY id DESC LIMIT 1`,value,'Warning preference did not persist');
    await page.close();const reopened=await openPreferences();
    h.assert(await reopened.locator('#rxInteractionWarningLevel').inputValue()===value,'Warning preference did not reopen');await reopened.close();
  }
  await providerLevel('1');
  const rx=await s.popup(s.master,masterRecordLink(s.master,'/rx/choosePatient').first(),'minor-interactions-rx');
  let first,second;
  await s.step('the genuine minor pair is checked, names both partners and displays authoritative tooltip details',async()=>{
    let body=await interactionCheck(rx,patient,async()=>{first=await stageFromSearch(rx,PRODUCTS[0].term,PRODUCTS[0].name);});
    h.assert(body.status==='checked'&&body.warnings.length===0,'The non-interacting single-product control was not checked and clear');
    body=await interactionCheck(rx,patient,async()=>{second=await stageFromSearch(rx,PRODUCTS[1].term,PRODUCTS[1].name);});
    h.assert(body.status==='checked'&&body.level===1,'Minor pair lookup was unavailable or used the wrong warning level');
    await assertMinor(true);
  });
  async function assertMinor(visible) {
    if(!visible){assertMarkers(await shownMarkers(rx),[],'when minor interactions are suppressed');return;}
    const firstPartner=pair.affecting.includes('ACETAMINOPHEN')?pair.affected:pair.affecting;
    const secondPartner=pair.affecting.includes('ACETAMINOPHEN')?pair.affecting:pair.affected;
    assertMarkers(await shownMarkers(rx),[`minor_${first}: Minor interaction: ${firstPartner}`,`minor_${second}: Minor interaction: ${secondPartner}`],'for the controlled minor pair');
    for(const id of [first,second]) {
      const title=await rx.locator(`#minor_${id}`).getAttribute('title');
      h.assert(title.includes(pair.summary)&&(!pair.comment||title.includes(pair.comment)),'Minor tooltip lost authoritative effect, evidence or comment');
    }
  }
  async function recheck(level) {
    const body=await interactionCheck(rx,patient,()=>rx.reload());
    h.assert(body.level===level&&body.status===(level===4?'off':'checked'),'Lookup failed or resolved the wrong warning level');
    first=await cardFor(rx,PRODUCTS[0].name);second=await cardFor(rx,PRODUCTS[1].name);
    await assertMinor(level<=1);
  }
  for(const level of ['2','3','4','1'])await s.step(`provider level ${level} persists, reopens and applies to the minor markers`,async()=>{
    await providerLevel(level);await recheck(Number(level));
  });
  const patientLevel=async(value)=>{
    if(await s.master.locator('#editBtn').isVisible())await s.master.locator('#editBtn').click();
    await s.master.locator('#editDemographic').waitFor({state:'visible'});
    await s.master.locator('select[name="rxInteractionWarningLevel"]').selectOption(value);
    await ui.clickAndAwaitReload(s.master,s.master.locator('#updateButton input[type="submit"]').first(),{label:'Update patient warning level',timeout:30000});
    await expectValue(sql,`SELECT value FROM demographicExt WHERE demographic_no=${patient} AND key_val='rxInteractionWarningLevel' ORDER BY id DESC LIMIT 1`,value,'Patient warning level did not persist');
  };
  await s.step('patient Low overrides provider None; clearing the patient value restores provider fallback',async()=>{
    await providerLevel('4');await patientLevel('1');await recheck(1);
    await patientLevel('0');await recheck(4);
  });
  await s.step('provider Not Specified falls back to the current facility without changing its setting',async()=>{
    const facilities=sql.rows(`SELECT f.id,f.rxInteractionWarningLevel FROM provider_facility pf JOIN Facility f ON f.id=pf.facility_id WHERE pf.provider_no=${h.sqlString(provider)} AND f.disabled=0`);
    h.assert(facilities.length===1,'Facility fallback requires one unambiguous active login facility');
    const snapshot=JSON.stringify(facilities),level=Number(facilities[0][1]);
    h.assert(level>=0&&level<=4,'Facility warning level is invalid');await providerLevel('0');await recheck(level);
    h.assert(JSON.stringify(sql.rows(`SELECT f.id,f.rxInteractionWarningLevel FROM provider_facility pf JOIN Facility f ON f.id=pf.facility_id WHERE pf.provider_no=${h.sqlString(provider)} AND f.disabled=0`))===snapshot,'Facility warning preference changed');
  });
  await s.step('removing the partner clears the minor marker through a successful lookup',async()=>{
    await providerLevel('1');await recheck(1);
    const body=await interactionCheck(rx,patient,()=>rx.locator(`#set_${second} a[onclick*="removePrescribingDrug"]`).click());
    h.assert(body.status==='checked'&&body.warnings.length===0,'Partner removal did not produce a checked-and-clear result');
    assertMarkers(await shownMarkers(rx),[],'after partner removal');
  });
}
if(require.main===module)runWorkflow('rx-minor-interaction-levels',workflow,{openPatient:true});
module.exports={workflow};
