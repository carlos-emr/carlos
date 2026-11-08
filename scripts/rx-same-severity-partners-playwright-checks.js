#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// #5734: validated catalog pairs; no DrugRef catalog writes or shared preferences.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {openSecondSession}=require('./lib/concurrency-support');
const {masterRecordLink}=require('./lib/master-record-link');
const {prepareRx}=require('./lib/rx-ui-workflow');
const {stageFromSearch}=require('./rx-edit-discontinue-playwright-checks');
const {drugRefPair,interactionCheck,cardFor}=require('./rx-interaction-warnings-playwright-checks');
const q=h.sqlString;
const PRODUCTS={
  central:{term:'CIPRO 500',name:'CIPRO 500MG',atc:'J01MA02',generic:'CIPROFLOXACIN'},
  warfarin:{term:'COUMADIN 5',name:'COUMADIN TAB 5MG',atc:'B01AA03',generic:'WARFARIN'},
  phenytoin:{term:'DILANTIN 100',name:'DILANTIN 100MG',atc:'N03AB02',generic:'PHENYTOIN'},
  control:{term:'GLUCOPHAGE 500',name:'GLUCOPHAGE 500MG',atc:'A10BA02',generic:'METFORMIN'},
};

/** Drive same-severity aggregation across current and staged medication sources. */
async function workflow(s) {
  const {sql,patient,marker}=s;
  const fixture=throwawayLoginFixture({sql,marker,provider:s.provider,testUser:s.config.testUser});
  s.cleanup(()=>fixture.cleanup());fixture.create({roleNames:['doctor']});
  const rxFixture=await prepareRx(s);
  s.cleanup(()=>{
    const notes=sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).flat();
    h.assert(notes.every(id=>/^[1-9]\d*$/.test(id)),'Invalid owned medication note ID');
    if(notes.length)sql.execute(`DELETE FROM hash_audit WHERE type='enc' AND id IN (${notes.join(',')})`);
    sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${patient}; DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})`)==='0','Owned patient preference support rows remain');
  });
  const current=rxFixture.seed('current phenytoin',{BN:PRODUCTS.phenytoin.name,customName:PRODUCTS.phenytoin.name,GN:PRODUCTS.phenytoin.generic,ATC:PRODUCTS.phenytoin.atc,
    special:PRODUCTS.phenytoin.name+' 1 capsule PO daily',long_term:1,GCN_SEQNO:0});
  const pair={warfarin:drugRefPair(sql,PRODUCTS.central.atc,PRODUCTS.warfarin.atc),
    phenytoin:drugRefPair(sql,PRODUCTS.central.atc,PRODUCTS.phenytoin.atc),
    minor:drugRefPair(sql,PRODUCTS.warfarin.atc,PRODUCTS.phenytoin.atc)};
  h.assert(pair.warfarin.severity==='moderate'&&pair.phenytoin.severity==='moderate'&&pair.minor.severity==='minor','Catalog grades no longer establish the fixed multi-partner fixture');
  const set=Object.values(PRODUCTS).map(product=>q(product.atc)).join(',');
  const catalog=()=>JSON.stringify(sql.rows(`SELECT * FROM drugref2.interactions WHERE affectingatc IN (${set}) OR affectedatc IN (${set}) ORDER BY affectingatc,affectedatc,significance,effect,evidence,comment`));
  const catalogBefore=catalog();
  for(const other of [PRODUCTS.central,PRODUCTS.warfarin,PRODUCTS.phenytoin])h.assert(sql.value(`SELECT COUNT(*) FROM drugref2.interactions WHERE
    (affectingatc=${q(PRODUCTS.control.atc)} AND affectedatc=${q(other.atc)}) OR (affectedatc=${q(PRODUCTS.control.atc)} AND affectingatc=${q(other.atc)})`)==='0','The selected noninteracting catalog control now has an interaction');
  const session=await openSecondSession(s,{label:'multi-partner-provider',config:{...s.config,testUser:fixture.username}});
  const owner=q(fixture.providerNo);
  async function providerLevel(value) {
    const preferences=await ui.clickOpensPopup(session.schedule,session.schedule.getByTitle(/Edit your personal setting/i).first(),{context:session.context,recorder:s.recorder,label:'multi-partner-preferences'});
    await preferences.locator('button[data-bs-target="#secRx"]').click();
    const [response]=await Promise.all([preferences.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/provider/rxInteractionWarningLevel')),
      preferences.locator('#rxInteractionWarningLevel').selectOption(value)]);
    h.assert(response.status()===200&&(await response.text()).trim()==='ok','Native provider threshold save failed');
    await expectValue(sql,`SELECT value FROM property WHERE provider_no=${owner} AND name='rxInteractionWarningLevel' ORDER BY id DESC LIMIT 1`,value,'Owned provider threshold did not persist');
    await preferences.close();
  }
  await providerLevel('2');
  const rx=await ui.clickOpensPopup(session.master,masterRecordLink(session.master,'/rx/choosePatient').first(),{context:session.context,recorder:s.recorder,label:'multi-partner-rx'});
  await rx.locator('#searchString').waitFor({state:'visible'});
  h.assert(new URL(rx.url()).searchParams.get('demographicNo')===patient,'Rx is bound to another patient');
  const cards={};let currentActive=true;
  const add=async name=>{const p=PRODUCTS[name];cards[name]=await stageFromSearch(rx,p.term,p.name);};
  const checked=async(action,level)=>{
    const result=await interactionCheck(rx,patient,action);
    h.assert(result.status===(level===4?'off':'checked')&&result.level===level,'Interaction check was unavailable, unchecked or at the wrong threshold');
    h.assert(!result.unchecked?.length,'A controlled catalog product was reported unchecked');
    return result;
  };
  function expected(level) {
    if(level>=3)return [];
    const result=[];
    const append=(card,severity,partner,detail)=>result.push({card,severity,partner,detail});
    if(cards.central) {
      for(const name of ['warfarin','phenytoin'])if(cards[name]||(name==='phenytoin'&&currentActive)) {
        append(cards.central,'moderate',PRODUCTS[name].generic,pair[name]);
        if(cards[name])append(cards[name],'moderate',PRODUCTS.central.generic,pair[name]);
      }
    }
    if(level<=1&&cards.warfarin&&(cards.phenytoin||currentActive)) {
      append(cards.warfarin,'minor',PRODUCTS.phenytoin.generic,pair.minor);
      if(cards.phenytoin)append(cards.phenytoin,'minor',PRODUCTS.warfarin.generic,pair.minor);
    }
    return result;
  }
  /** Require each authoritative pair exactly once in JSON and the rendered marker/detail. */
  async function verify(body,level) {
    const rows=expected(level);
    const identity=row=>[String(row.randomId??row.card),row.severity,row.partner].join('|');
    const actual=body.warnings.map(identity).sort(),wanted=rows.map(identity).sort();
    h.assert(JSON.stringify(actual)===JSON.stringify(wanted),'Interaction response lost, duplicated or retained a partner: '+JSON.stringify({actual,wanted}));
    const visible=await rx.locator('[data-rx-interaction]').evaluateAll(nodes=>nodes.filter(node=>node.style.display==='inline').map(node=>({id:node.id,text:node.textContent,title:node.title})));
    const grouped=new Map();
    for(const row of rows){const id=row.severity+'_'+row.card;if(!grouped.has(id))grouped.set(id,[]);grouped.get(id).push(row);}
    h.assert(visible.length===grouped.size,'Wrong number of severity markers');
    for(const marker of visible) {
      const entries=grouped.get(marker.id);h.assert(entries,'Stale card or unrelated severity marker remains: '+marker.id);
      const partners=marker.text.slice(marker.text.indexOf(':')+1).trim().split(', ').sort();
      h.assert(JSON.stringify(partners)===JSON.stringify(entries.map(row=>row.partner).sort()),'Rendered severity marker lost or duplicated a partner');
      for(const entry of entries)h.assert(marker.title.includes(entry.detail.summary)&&(!entry.detail.comment||marker.title.includes(entry.detail.comment)),'Partner tooltip lost effect/evidence/comment');
    }
    if(cards.control)h.assert(!visible.some(marker=>marker.id.endsWith('_'+cards.control)),'Noninteracting control acquired a warning');
  }
  async function reload(level) {
    const body=await checked(()=>rx.reload(),level);await rx.locator('#searchString').waitFor({state:'visible'});
    for(const name of Object.keys(cards))cards[name]=await cardFor(rx,PRODUCTS[name].name);
    await verify(body,level);return body;
  }
  await s.step('two validated moderate partners from current and staged sources share one complete severity marker',async()=>{
    let body=await checked(()=>add('control'),2);await verify(body,2);
    body=await checked(()=>add('warfarin'),2);await verify(body,2);
    body=await checked(()=>add('central'),2);await verify(body,2);
    h.assert(body.warnings.filter(row=>String(row.randomId)===cards.central&&row.severity==='moderate').length===2,'Central drug did not retain both same-severity partners');
  });
  await s.step('selective staged removal preserves the current partner; re-add returns one new card without stale warnings',async()=>{
    const removed=cards.warfarin;
    let body=await checked(()=>rx.locator(`#set_${removed} a[onclick*="removePrescribingDrug"]`).click(),2);
    delete cards.warfarin;h.assert(await rx.locator('#set_'+removed).count()===0,'Removed staged card remains');await verify(body,2);
    body=await checked(()=>add('warfarin'),2);h.assert(cards.warfarin!==removed,'Re-added medication reused the removed card identity');await verify(body,2);
    h.assert(!body.warnings.some(row=>String(row.randomId)===removed),'Response still names a removed card');
  });
  await s.step('native current-medication discontinuation removes only its partner, then a staged re-add restores both',async()=>{
    await rx.locator('#discont_'+current).click();await rx.locator('#discontinueUI').waitFor({state:'visible'});
    h.assert(await rx.locator('#disDrugId').inputValue()===current&&(await rx.locator('#disDrug').innerText()).includes(PRODUCTS.phenytoin.name),'Discontinue targets another medication');
    await rx.locator('#disReason').selectOption('drugInteraction');
    let body=await checked(()=>rx.locator('#discontinueUI input[onclick*="Discontinue2("]').click(),2);
    await expectValue(sql,`SELECT CONCAT(archived,'|',archived_reason) FROM drugs WHERE drugid=${current} AND demographic_no=${patient}`,'1|drugInteraction','Current partner was not discontinued exactly');
    currentActive=false;await verify(body,2);
    body=await checked(()=>add('phenytoin'),2);await verify(body,2);
  });
  await s.step('provider thresholds and patient overrides preserve every applicable partner and distinguish off from checked',async()=>{
    for(const level of [3,4,1,2]){await providerLevel(String(level));await reload(level);}
    await providerLevel('4');await reload(4);
    await session.master.reload({waitUntil:'load'});await session.master.locator('#editBtn').click();await session.master.locator('#editDemographic').waitFor({state:'visible'});
    await session.master.locator('select[name="rxInteractionWarningLevel"]').selectOption('2');
    await ui.clickAndAwaitReload(session.master,session.master.locator('#updateButton input[type="submit"]').first());
    h.assert(sql.value(`SELECT value FROM demographicExt WHERE demographic_no=${patient} AND key_val='rxInteractionWarningLevel'`)==='2','Patient override did not persist');await reload(2);
    await session.master.locator('#editBtn').click();await session.master.locator('#editDemographic').waitFor({state:'visible'});
    await session.master.locator('select[name="rxInteractionWarningLevel"]').selectOption('0');
    await ui.clickAndAwaitReload(session.master,session.master.locator('#updateButton input[type="submit"]').first());await reload(4);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='1','Staged cards unexpectedly persisted prescriptions');
    h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${current} AND demographic_no=${patient}`)==='1','Threshold changes altered discontinued source');
    h.assert(catalog()===catalogBefore,'Controlled DrugRef catalog rows changed');
  });
}
if(require.main===module)runWorkflow('rx-same-severity-partners',workflow,{preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Synthetic clinician fixtures require EXCLUSIVE=1');
}});
module.exports={workflow};
