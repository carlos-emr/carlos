#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// #5727: native demographic fields, automatic lookup and intentional clears.
const strict=require('node:assert/strict');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {openEditForm}=require('./demographic-edit-update-playwright-checks');
const q=h.sqlString;
const KEYS=['statusNum','fNationCom','labelfNationCom','fNationFamilyNumber','fNationFamilyPosition','ethnicity','aboriginal'];

async function workflow(s) {
  const {sql,patient,marker,provider,master}=s;
  if(await master.locator('#statusNum').count()!==1)throw new h.SkipCheck('FIRST_NATIONS_MODULE=true is required in the installed profile');
  if(await master.locator('#fNationCom').count()!==1)throw new h.SkipCheck('Full First Nations fields require showBandNumberOnly=false');
  const list=sql.rows("SELECT id FROM LookupList WHERE name='firstNationCommunity' AND active=1");
  if(list.length!==1)throw new h.SkipCheck('Exactly one configured firstNationCommunity list is required');
  const communities=sql.rows(`SELECT value,label FROM LookupListItem WHERE lookupListId=${list[0][0]} AND active=1 ORDER BY displayOrder,id`)
    .filter(([value])=>/^\d{3}$/.test(value));
  if(new Set(communities.map(row=>row[0])).size<2)throw new h.SkipCheck('Two configured three-digit community values are required for automatic/manual selection');
  const [automatic,manual]=[communities[0],communities.find(row=>row[0]!==communities[0][0])];
  const family=BigInt('0x'+marker.slice(-10)).toString().slice(-5).padStart(5,'0');
  const originalNumber=automatic[0]+family+'07', changedNumber=manual[0]+family+'08';
  for(const number of [originalNumber,changedNumber])h.assert(sql.value(`SELECT COUNT(*) FROM demographicExt WHERE key_val='statusNum' AND value=${q(number)}`)==='0','Synthetic status number already exists');
  const controlName=marker+'-FNControl';let control;
  s.cleanup(()=>{
    const failures=[];
    try {
      sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${patient}; DELETE FROM demographiccust WHERE demographic_no=${patient}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})`)==='0','Owned demographic save support rows remain');
    }catch(error){failures.push(error);}
    try {
      const ids=sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(controlName)}`).flat();
      h.assert(ids.length<=1&&(!control||ids.length===0||ids[0]===control),'First Nations control ownership changed');
      for(const id of ids) {
        h.assert(/^[1-9]\d*$/.test(id),'Invalid control key');
        sql.execute(`DELETE FROM demographicExt WHERE demographic_no=${id}; DELETE FROM demographic WHERE demographic_no=${id} AND last_name=${q(controlName)}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${id})+(SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${id})`)==='0','First Nations control cleanup failed');
      }
      if(control)h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${control})+(SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${control})`)==='0','Captured First Nations control remains; ownership may have changed');
    }catch(error){failures.push(error);}
    if(failures.length)throw new AggregateError(failures,'First Nations cleanup: '+failures.map(e=>e.message).join('; '));
  });
  control=sql.value(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES(${q(controlName)},'Control','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(control),'First Nations control was not created');
  sql.execute(`INSERT INTO demographicExt(demographic_no,provider_no,key_val,value,date_time,hidden) VALUES
    (${control},${q(provider)},'statusNum',${q('8'+family+'0099')},NOW(),0),
    (${patient},${q(provider)},'pwFirstNationsControl',${q(marker+' unchanged é & "')},NOW(),0)`);
  const controlState=()=>JSON.stringify({patient:sql.rows(`SELECT * FROM demographic WHERE demographic_no=${control}`),ext:sql.rows(`SELECT * FROM demographicExt WHERE demographic_no=${control} ORDER BY id`)});
  const controlBefore=controlState();
  const input=name=>master.locator(`[name="${name}"]`);
  async function reopen(){await master.reload({waitUntil:'load'});await h.assertNotErrorPage(master,'First Nations master');await openEditForm(master,20000);}
  async function save(){
    const [response]=await Promise.all([master.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/demographic/DemographicUpdate')&&r.request().method()==='POST'),
      ui.clickAndAwaitReload(master,master.locator('#updateButton input[type="submit"]').first())]);
    h.assert(response.status()<400,'Native First Nations save was refused');
    h.assert(controlState()===controlBefore,'Another patient changed during First Nations save');
  }
  const ext=()=>Object.fromEntries(sql.rows(`SELECT key_val,COALESCE(value,'<SQL-NULL>') FROM demographicExt WHERE demographic_no=${patient} AND key_val IN (${KEYS.map(q).join(',')}) ORDER BY key_val`));
  async function verify(expected){
    const stored=ext();for(const [name,value] of Object.entries(expected))h.assert(stored[name]===value,'First Nations persistence differs for '+name);
    await reopen();for(const [name,value] of Object.entries(expected))if(name!=='labelfNationCom')h.assert(await input(name).inputValue()===value,'Reopened First Nations field differs for '+name);
  }
  await openEditForm(master,20000);
  // Normalize the ordinary demographic form once using its native save before
  // recording invariants; subsequent operations edit only First Nations fields.
  await save();await reopen();
  const columns=sql.rows('SHOW COLUMNS FROM demographic').map(row=>row[0]).filter(name=>!['lastUpdateDate','lastUpdateUser'].includes(name));
  const unrelated=()=>JSON.stringify({demographic:sql.rows(`SELECT ${columns.map(name=>'`'+name+'`').join(',')} FROM demographic WHERE demographic_no=${patient}`),
    extensions:sql.rows(`SELECT key_val,value,hidden FROM demographicExt WHERE demographic_no=${patient} AND key_val NOT IN (${KEYS.map(q).join(',')}) ORDER BY key_val`)});
  const unrelatedBefore=unrelated();
  async function search(number){
    const {page}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#search a').first(),{context:s.context,recorder:s.recorder,label:'first-nations-search'});
    const form=page.locator('form[name="titlesearch"]').first();await form.waitFor({state:'attached'});
    h.assert(await form.locator('select[name="search_mode"] option[value="search_band_number"]').count()===1,'Enabled First Nations profile has no Band Number search');
    const keyword=form.locator('input[name="keyword"]').first();
    if(!await keyword.isVisible())await page.locator('#searchPopUpButton').first().click();
    await form.locator('select[name="search_mode"]').selectOption('search_band_number');
    await keyword.fill(number);await ui.clickAndAwaitReload(page,form.locator('input[type="submit"]').first());
    await h.assertNotErrorPage(page,'native saved band-number search');
    const ids=(await page.locator('a[title="Master Demographic File"]').allTextContents()).map(text=>text.trim()).filter(text=>/^\d+$/.test(text));
    if(page!==s.schedule)await page.close();return ids;
  }
  await s.step('native valid status entry selects its configured community and family fields, then saves and reopens exactly',async()=>{
    await input('aboriginal').selectOption('Yes');
    h.assert(await input('statusNum').getAttribute('maxlength')==='10','Native status-number length constraint changed');
    await input('statusNum').fill(originalNumber);await input('statusNum').press('Tab');
    h.assert(await input('fNationCom').inputValue()===automatic[0]&&await input('fNationFamilyNumber').inputValue()===family&&await input('fNationFamilyPosition').inputValue()==='07','Status blur did not select the configured community and family components');
    await input('ethnicity').selectOption('2');await save();
    await verify({statusNum:originalNumber,fNationCom:automatic[0],fNationFamilyNumber:family,fNationFamilyPosition:'07',ethnicity:'2',aboriginal:'Yes'});
    strict.deepEqual(await search(originalNumber),[patient],'Newly saved band number did not find exactly its patient');
  });
  await s.step('manual community and punctuation-bearing optional values survive status revisits and save/reopen',async()=>{
    await input('fNationCom').selectOption(manual[0]);
    await input('fNationFamilyNumber').fill('FAKE é O\'Neil & "');await input('fNationFamilyPosition').fill('FAKE Ω');
    await input('statusNum').focus();await input('statusNum').press('Tab');
    h.assert(await input('fNationCom').inputValue()===manual[0],'Revisiting status replaced the manually chosen community');
    await save();await verify({statusNum:originalNumber,fNationCom:manual[0],labelfNationCom:manual[1],fNationFamilyNumber:'FAKE é O\'Neil & "',fNationFamilyPosition:'FAKE Ω',ethnicity:'2'});
  });
  await s.step('intentional community clear persists after reopen and another status blur',async()=>{
    await input('fNationCom').selectOption('');await save();await verify({fNationCom:''});
    await input('statusNum').focus();await input('statusNum').press('Tab');
    h.assert(await input('fNationCom').inputValue()==='','Reopened status blur silently repopulated a deliberately cleared community');
    await save();await verify({fNationCom:'',statusNum:originalNumber});
  });
  await s.step('changed saved band number drives search, optional clears persist, and unrelated data remain unchanged',async()=>{
    await input('statusNum').fill(changedNumber);await input('statusNum').press('Tab');
    await input('fNationFamilyNumber').fill('');await input('fNationFamilyPosition').fill('');await input('ethnicity').selectOption('-1');
    await save();await verify({statusNum:changedNumber,fNationCom:'',fNationFamilyNumber:'',fNationFamilyPosition:'',ethnicity:'-1'});
    strict.deepEqual(await search(changedNumber),[patient],'Changed band number does not find its patient');
    strict.deepEqual(await search(originalNumber),[],'Previous band number still finds a patient');
    await input('statusNum').fill('');await input('statusNum').press('Tab');await save();
    await verify({statusNum:'',fNationCom:'',fNationFamilyNumber:'',fNationFamilyPosition:'',aboriginal:''});
    h.assert(unrelated()===unrelatedBefore,'First Nations edits changed unrelated demographic/extension values');
    h.assert(controlState()===controlBefore,'First Nations edits changed the independent patient');
  });
}
if(require.main===module)runWorkflow('first-nations-roundtrip',workflow);
module.exports={workflow};
