#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// #5718: independent truth tables, real chart/list/detail, native source change.
const h=require('./lib/playwright-harness');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {openSecondSession}=require('./lib/concurrency-support');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {createGuidelineLoginFixture}=require('./decision-support-guidelines-playwright-checks');
const {createOwnedPatient,removeOwnedPatient,newOwnedMarker}=require('./lib/owned-patient');
const {prepareRx}=require('./lib/rx-ui-workflow');
const {seedOwnedBill}=require('./billing-on-invoice-third-party-playwright-checks');
const {masterRecordLink}=require('./lib/master-record-link');
const {readPatientState,assertPatientStateUnchanged}=require('./echart-two-patient-isolation-playwright-checks');
const q=h.sqlString;
const attr=value=>String(value).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const normal=value=>value.replace(/[\[\]'\s]/g,'');

/** Fixed clinical expectations, not results obtained from the rule engine. */
function cases(marker,days) {
  const rows=[];
  const condition=(type,op,value,a,b,after=a,params={})=>({type,op,value,expected:{A:a,B:b,after},params});
  const add=(name,...conditions)=>rows.push({name,conditions});
  const c=condition;
  add('dx-any',c('dxcodes','any','icd9:2700',true,false,false));
  add('dx-multi-any',c('dxcodes','any','icd9:2700,icd9:2701',true,false,true));
  add('dx-all',c('dxcodes','all','icd9:2700,icd9:2701',true,false,false));
  add('dx-not',c('dxcodes','not','icd9:2700',false,true,true));
  add('dx-notany',c('dxcodes','notany','icd9:2700,icd9:9999',false,true,true));
  add('dx-notall',c('dxcodes','notall','icd9:2700,icd9:9999',true,true));
  add('sex-any',c('sex','any','F',true,false));
  add('sex-not',c('sex','not','F',false,true));
  add('sex-all-distinct',c('sex','all','F,M',false,false));
  add('sex-notall-distinct',c('sex','notall','F,M',true,true));
  add('age-ge-boundary',c('age','any','>=30 y',true,true));
  add('age-gt-boundary',c('age','any','>30 y',false,true));
  add('age-eq-boundary',c('age','any','=30 y',true,false));
  add('age-le-boundary',c('age','any','<=30 y',true,false));
  add('age-lt-boundary',c('age','any','<30 y',false,false));
  add('age-all',c('age','all','>=30 y,<=30 y',true,false));
  add('age-not',c('age','not','=30 y',false,true));
  add('age-notany',c('age','notany','<30 y,>30 y',true,false));
  add('age-notall',c('age','notall','=30 y,>30 y',true,true));
  add('age-months',c('age','any','=360 m',true,false));
  add('age-days',c('age','any','='+days+' d',true,false));
  for(const [type,first,second,missing] of [
    ['drugs','atc:N05BA01','atc:N05BA02','atc:N05BA99'],
    ['notes',"'"+marker+"Alpha'","'"+marker+"Beta'","'"+marker+"Missing'"],
  ]) {
    add(type+'-any',c(type,'any',first,true,false));
    add(type+'-absent',c(type,'any',missing,false,false));
    add(type+'-all',c(type,'all',first+','+second,true,false));
    add(type+'-not',c(type,'not',first,false,true));
    add(type+'-notany',c(type,'notany',first+','+missing,false,true));
    add(type+'-notall',c(type,'notall',first+','+missing,true,true));
  }
  const billing={payer:'MSP',notInDays:'30'};
  // billedFor + notInDays is the shipped eligibility condition: a recent bill
  // makes it FALSE; absence or a bill exactly at the boundary makes it TRUE.
  add('billing-recent',c('billedFor','any','A003A',false,true,false,billing));
  add('billing-absent',c('billedFor','any','A998A',true,true,true,billing));
  add('billing-boundary',c('billedFor','any','A004A',true,true,true,billing));
  add('billing-inside-boundary',c('billedFor','any','A004A',false,true,false,{payer:'MSP',notInDays:'31'}));
  add('billing-multi',c('billedFor','any','A003A,A004A',false,true,false,billing));
  add('combined-dx-sex',c('dxcodes','any','icd9:2700',true,false,false),c('sex','any','F',true,false));
  add('combined-different-failures',c('age','any','=30 y',true,false),c('sex','any','M',false,true));
  add('combined-notes-drug',c('notes','any',"'"+marker+"Alpha'",true,false),c('drugs','any','atc:N05BA99',false,false));
  return rows;
}

/** Own provider-scoped definitions; all evaluation and source updates use UI. */
async function workflow(s) {
  const {sql,patient,marker,provider}=s;
  if(await masterRecordLink(s.master,'/billing/CA/ON/ViewBillingONHistory').count()===0)throw new h.SkipCheck('Operator billing fixtures require the actual native ON billing profile');
  let rulesClean=true;
  const fixture=createGuidelineLoginFixture(s), originalCleanup=fixture.cleanup.bind(fixture);
  fixture.cleanup=()=>{h.assert(rulesClean,'Guideline rules remain; retain their provider');originalCleanup();};
  s.cleanup(()=>{
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(fixture.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(fixture.providerNo)}`)==='0','Guideline default-program fixture remains');
  });
  sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id)
    SELECT program_id,${q(fixture.providerNo)},role_id,team_id FROM program_provider WHERE provider_no=${q(provider)};
    INSERT INTO provider_default_program(provider_no,program_id,signnote)
    SELECT ${q(fixture.providerNo)},program_id,signnote FROM provider_default_program WHERE provider_no=${q(provider)}`);
  const today=sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
  const dob=age=>{
    const year=Number(today.slice(0,4))-age,month=Number(today.slice(5,7));
    const day=Math.min(Number(today.slice(8,10)),new Date(Date.UTC(year,month,0)).getUTCDate());
    return year+'-'+String(month).padStart(2,'0')+'-'+String(day).padStart(2,'0');
  };
  const birthday=dob(30),days=Math.round((Date.parse(today+'T00:00:00Z')-Date.parse(birthday+'T00:00:00Z'))/86400000);
  sql.execute(`UPDATE demographic SET year_of_birth=${q(birthday.slice(0,4))},month_of_birth=${q(birthday.slice(5,7))},date_of_birth=${q(birthday.slice(8,10))} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const controlMarker=newOwnedMarker();let control;
  s.cleanup(()=>{
    const ids=sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(controlMarker)}`).flat();
    h.assert(ids.length<=1&&(!control||!ids.length||ids[0]===control),'Guideline control ownership changed');
    for(const id of ids)removeOwnedPatient(sql,id,controlMarker);
    if(control)h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${control}`)==='0','Guideline control remains under a changed owner');
  });
  control=createOwnedPatient(sql,{marker:controlMarker,provider,firstName:'OperatorControl'});
  const otherDob=dob(31);
  sql.execute(`UPDATE demographic SET sex='M',year_of_birth=${q(otherDob.slice(0,4))},month_of_birth=${q(otherDob.slice(5,7))},date_of_birth=${q(otherDob.slice(8,10))} WHERE demographic_no=${control} AND last_name=${q(controlMarker)}`);
  const rx=await prepareRx(s);
  for(const [name,atc] of [['Alpha','N05BA01'],['Beta','N05BA02'],['AlphaDuplicate','N05BA01']])rx.seed(name,{ATC:atc});
  const diagnoses=[];
  s.cleanup(()=>{
    sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient}`)==='0','Owned guideline diagnoses remain');
  });
  for(const code of ['2700','2701'])diagnoses.push(h.insertId(sql,`INSERT INTO dxresearch(demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES(${patient},CURDATE(),NOW(),'A',${q(code)},'icd9',0,${q(provider)})`,'operator diagnosis'));
  for(const token of [marker+'Alpha',marker+'Beta',marker+'Alpha'])h.insertId(sql,`INSERT INTO casemgmt_note
    (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,include_issue_innote,encounter_type,program_no,reporter_caisi_role,reporter_program_team,history,uuid,locked,archived,position,appointmentNo)
    VALUES(NOW(),NOW(),${patient},${q(provider)},${q(token)},1,${q(provider)},0,'',0,'0','0',${q(token)},UUID(),'0',0,0,0)`,'operator note');
  for(const [code,ago] of [['A003A',0],['A004A',30]])seedOwnedBill(s,{payProgram:'HCP',status:'O',code,fee:'10.00',date:new Date(Date.parse(today+'T00:00:00Z')-ago*86400000).toISOString().slice(0,10)});
  const definitions=cases(marker,days);
  definitions.forEach((rule,index)=>{rule.uuid=marker+'-'+index;});
  s.cleanup(()=>{
    const errors=[];
    for(const rule of definitions)try {
      const uuid=rule.uuid;
      h.assert(sql.value(`SELECT COUNT(*) FROM dsGuidelineProviderMap WHERE guideline_uuid=${q(uuid)} AND provider_no<>${q(fixture.providerNo)}`)==='0','Owned guideline acquired a foreign provider mapping');
      sql.execute(`DELETE FROM dsGuidelineProviderMap WHERE guideline_uuid=${q(uuid)} AND provider_no=${q(fixture.providerNo)};
        DELETE FROM dsGuidelines WHERE uuid=${q(uuid)} AND title=${q(marker+' '+rule.name)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM dsGuidelines WHERE uuid=${q(uuid)})+(SELECT COUNT(*) FROM dsGuidelineProviderMap WHERE guideline_uuid=${q(uuid)})`)==='0','Owned guideline cleanup failed');
    }catch(error){errors.push(error);}
    rulesClean=errors.length===0;
    if(errors.length)throw new AggregateError(errors,'Operator guideline cleanup failed');
  });
  for(const rule of definitions) {
    const uuid=rule.uuid;
    h.assert(sql.value(`SELECT COUNT(*) FROM dsGuidelines WHERE uuid=${q(uuid)}`)==='0','Rule namespace already exists');
    rule.title=marker+' '+rule.name;rule.warning=marker+' consequence '+rule.name;
    const xml='<guideline title="'+attr(rule.title)+'"><conditions>'+rule.conditions.map(c=>
      '<condition type="'+c.type+'" '+c.op+'="'+attr(c.value)+'">'+Object.entries(c.params).map(([key,value])=>'<param key="'+key+'" value="'+attr(value)+'"/>').join('')+'</condition>').join('')
      +'</conditions><consequence><warning strength="warning">'+rule.warning+'</warning></consequence></guideline>';
    rulesClean=false;
    rule.id=h.insertId(sql,`INSERT INTO dsGuidelines(uuid,title,version,author,xml,source,engine,dateStart,status)
      VALUES(${q(uuid)},${q(rule.title)},1,'FAKE operator fixture',${q(xml)},'local','drools',NOW(),'A')`,'operator guideline');
    sql.execute(`INSERT INTO dsGuidelineProviderMap(provider_no,guideline_uuid) VALUES(${q(fixture.providerNo)},${q(uuid)})`);
  }
  const session=await openSecondSession(s,{label:'operator-provider',config:{...s.config,testUser:fixture.username}});
  // Searching may reuse the first Master Record's named window. Each chart is
  // opened through a newly searched native master and closed before switching.
  const defect=[];let visitNumber=0;
  const snapshot=owner=>({clinical:readPatientState(sql,owner),drugs:sql.rows(`SELECT * FROM drugs WHERE demographic_no=${owner} ORDER BY drugid`),
    dx:sql.rows(`SELECT * FROM dxresearch WHERE demographic_no=${owner} ORDER BY dxresearch_no`),
    bills:sql.rows(`SELECT * FROM billing_on_cheader1 WHERE demographic_no=${owner} ORDER BY id`),
    billItems:sql.rows(`SELECT * FROM billing_on_item WHERE ch1_id IN (SELECT id FROM billing_on_cheader1 WHERE demographic_no=${owner}) ORDER BY id`)});
  const note=(okay,message)=>{if(!okay)defect.push(message);};
  const popup=(page,control,label)=>ui.clickOpensPopup(page,control,{context:session.context,recorder:s.recorder,label,timeout:20000});
  async function masterFor(owner,ownerMarker) {
    // This owned provider's native Search window is named and otherwise reused,
    // which is neither a new popup nor a navigation of the schedule tab.
    for(const page of session.context.pages())if(page!==session.schedule&&!page.isClosed())await page.close();
    return (await openMasterRecord(session.context,session.schedule,s.recorder,{searchTerm:ownerMarker,preferredDemographicNo:owner,timeout:20000})).masterPage;
  }
  /** Compare every rule on each surface, retaining all independent mismatches. */
  async function visit(label,owner,ownerMarker) {
    const observed=[];
    const masterPage=await masterFor(owner,ownerMarker);
    const before=snapshot(owner), otherBefore=snapshot(owner===patient?control:patient);
    const chart=await openChart(session.context,masterPage,s.recorder,20000);await waitForNavbars(chart,20000);
    const heading=chart.locator('#Guidelines .nav-menu-title a');await heading.filter({hasText:'Decision Support Alerts'}).waitFor({state:'visible'});
    const expand=chart.locator('#Guidelines img[src$="/encounter/graphics/expand.gif"]');
    if(await expand.count()) {
      h.assert(await expand.count()===1,'Ambiguous native guideline expansion controls');
      const [response]=await Promise.all([chart.waitForResponse(r=>{
        const url=new URL(r.url());return url.origin===new URL(s.config.baseUrl).origin&&url.searchParams.get('cmd')==='Guidelines'&&!url.searchParams.has('numToDisplay');
      }),expand.click()]);
      h.assert(response.status()===200,'Native guideline expansion failed');
      await chart.locator('#Guidelines img[src$="/messenger/img/collapse.gif"]').first().waitFor({state:'visible'});
    }
    const chartCounts=new Map();
    for(const rule of definitions) {
      const expected=rule.conditions.every(c=>c.expected[label]);
      const warning=chart.locator(`#Guidelines a.links[onclick*="method=detail&guidelineId=${rule.id}&"]`);
      const count=await warning.count();chartCounts.set(rule.id,count);
      note(count===(expected?1:0),label+' '+rule.name+': chart warning differs from truth table');
    }
    let list=await popup(chart,heading,'operator-guideline-list');
    for(const rule of definitions) {
      const expected=rule.conditions.every(c=>c.expected[label]);
      const row=list.locator('table.dsTable tr').filter({has:list.getByRole('cell',{name:rule.title,exact:true})});
      h.assert(await row.count()===1,'Native guideline list is missing or duplicates '+rule.name);
      const cells=(await row.locator('td').allInnerTexts()).map(text=>text.trim());
      note(new RegExp('^'+(expected?'Passed':'Failed')+'\\b').test(cells[5]),label+' '+rule.name+': list evaluation differs from truth table');
      const link=row.getByRole('link',{name:'More Info',exact:true}),url=new URL(await link.getAttribute('href'),list.url());
      h.assert(url.origin===new URL(s.config.baseUrl).origin&&url.searchParams.get('guidelineId')===rule.id&&url.searchParams.get('demographic_no')===owner,'Native detail link changed patient/rule identity');
      await ui.clickAndAwaitReload(list,link);
      const body=await list.locator('body').innerText();
      note(body.includes('GUIDELINE PASSED: '+rule.warning)===expected,label+' '+rule.name+': detail consequence differs');
      const rows=await list.locator('table.dsTable tr').filter({has:list.locator('td')}).evaluateAll(trs=>trs.map(tr=>[...tr.cells].map(td=>td.innerText.trim())));
      observed.push({rule:rule.name,expected,chartWarnings:chartCounts.get(rule.id),listResult:cells[5],
        detailConsequence:body.includes('GUIDELINE PASSED: '+rule.warning),conditions:rows.map((value,index)=>({expected:rule.conditions[index],rendered:value}))});
      h.assert(rows.length===rule.conditions.length,'Detail omitted a condition for '+rule.name);
      for(let i=0;i<rows.length;i++) {
        const c=rule.conditions[i],actual=rows[i];
        note(actual[0]===c.type&&actual[1]===c.op&&normal(actual[2])===normal(c.value),label+' '+rule.name+': detail condition identity/configuration changed');
        note(actual[4]===(c.expected[label]?'Passed':'Fail'),label+' '+rule.name+': per-condition result differs');
        if(c.type==='sex')note(actual[3]===(label==='B'?'M':'F'),label+' '+rule.name+': wrong actual sex');
        if(c.type==='age')note(actual[3]===(label==='B'?'31 y':'30 y'),label+' '+rule.name+': wrong actual age');
        if(c.type==='drugs'&&label!=='B')note(JSON.stringify(actual[3].split(',').map(value=>value.trim()).sort())===JSON.stringify(['atc:N05BA01','atc:N05BA01','atc:N05BA02'].sort()),label+' '+rule.name+': actual medications differ from owned source');
        // Notes/billing sources have no implemented detail actual-value reader;
        // do not invent content. Their evaluation and independent SQL are still
        // strict; the shipped display limitation remains explicit in inventory.
      }
      await ui.clickAndAwaitReload(list,list.getByRole('button',{name:'List Guidelines',exact:true}));
    }
    await list.close();await chart.close();
    const directory=process.env.DS_OPERATOR_ARTIFACT_DIR||path.join(os.tmpdir(),'carlos-ds-operators-'+marker);
    const file=h.buildArtifactPath(directory,marker+'-'+label+'-'+(++visitNumber),'.json');
    fs.writeFileSync(file,JSON.stringify({profile:'ON',label,observed},null,2),{flag:'wx',mode:0o600});
    const after=snapshot(owner);
    assertPatientStateUnchanged(before.clinical,after.clinical,'Guideline evaluation');
    h.assert(JSON.stringify(before)===JSON.stringify(after),'Guideline evaluation mutated source clinical records');
    h.assert(JSON.stringify(otherBefore)===JSON.stringify(snapshot(owner===patient?control:patient)),'Guideline evaluation changed another patient');
    console.log('  Evaluated '+definitions.length+' exact operator rules for '+label+'; '+defect.length+' cumulative mismatches.');
  }
  // Collect all independent read-only outcomes before the final fatal assertion;
  // do not label a phase with recorded mismatches as passing.
  await visit('A',patient,marker);await visit('B',control,controlMarker);await visit('A',patient,marker);
  h.assertStrictPage(s.recorder);
  await s.step('the unmapped provider receives none of the owned rule warnings',async()=>{
    const original=await s.chart();await original.locator('#Guidelines .nav-menu-title a').waitFor();
    for(const rule of definitions)h.assert(await original.locator(`#Guidelines a.links[onclick*="guidelineId=${rule.id}&"]`).count()===0,'Rule leaked to the unmapped provider: '+rule.name);
  });
  await s.step('native diagnosis resolution changes only that fact and fresh evaluations update without restart',async()=>{
    const masterPage=await masterFor(patient,marker);
    const chart=await openChart(session.context,masterPage,s.recorder,20000);await waitForNavbars(chart,20000);
    const registry=await popup(chart,chart.locator('a[onclick*="setupDxResearch"]').first(),'operator-diagnosis-registry');
    const row=registry.locator('#startdate1st'+diagnoses[0]).locator('xpath=ancestor::tr[1]');
    await ui.clickAndAwaitReload(registry,row.getByRole('link',{name:'Resolve',exact:true}));
    await expectValue(sql,`SELECT status FROM dxresearch WHERE dxresearch_no=${diagnoses[0]} AND demographic_no=${patient}`,'C','Native resolution did not persist');
    h.assert(sql.value(`SELECT status FROM dxresearch WHERE dxresearch_no=${diagnoses[1]} AND demographic_no=${patient}`)==='A','Native resolution changed the other diagnosis');
    await registry.close();await chart.close();
  });
  await visit('after',patient,marker);await visit('B',control,controlMarker);
  await s.step('all chart, list and per-condition outcomes match the independently specified truth tables',async()=>{
    h.assert(defect.length===0,'Decision-support truth-table failures ('+defect.length+'): '+defect.join('; '));
  });
}
if(require.main===module)runWorkflow('decision-support-operators',workflow,{preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Provider-scoped operator fixtures require EXCLUSIVE=1');
}});
module.exports={workflow,cases};
