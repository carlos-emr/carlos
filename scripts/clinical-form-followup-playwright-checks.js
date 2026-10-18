#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Follow-up visits, beyond the catalogue's first Save/reopen/print checks:
// Forms > create a baseline > saved chart entry > correct or clear an answer >
// Save > latest chart entry. Require a new immutable revision, exact intended
// change, unchanged other clinical answers, and the latest version on reopening.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {FORMS:catalog,revealSavedForm}=require('./form-catalog-smoke-playwright-checks');
const forms=catalog.filter(f=>['rourke','labreq'].includes(f.key));
const key=process.env.FOLLOWUP_FORM;
const clear=process.env.FOLLOWUP_ACTION==='clear';
const name=`clinical-followup-${key}-${clear?'clear':'revise'}`;
async function workflow(s){
  const form=forms.find(x=>x.key===key);
  h.assert(form,'FOLLOWUP_FORM must name a supported clinical form');
  h.assert(['revise','clear',undefined].includes(process.env.FOLLOWUP_ACTION),'Unknown FOLLOWUP_ACTION');
  const {sql,patient,marker,provider}=s,q=h.sqlString;
  const title=`${marker} ${form.code}`,value=`../form/${form.view}.jsp?fixture=${marker}&demographic_no=`;
  const fields=[...(form.prose?[{...form.prose,value:`${marker} baseline`}]:[]),...form.fields.map(f=>({...f}))];
  const primary=fields[0];
  h.assert(primary&&title.length<=30,'Invalid follow-up form configuration');
  if(primary.check&&!clear)primary.value='0';
  const stored=f=>f.check?(f.value==='0'?'0':'1'):(f.stored||f.value);
  const revised=primary.check?'1':(/^\d+(\.\d+)?$/.test(primary.value)?String(Number(primary.value)+1):`${marker} revised`);
  const expected=clear?(primary.check?'0':''):revised;
  s.cleanup(()=>{
    sql.execute(`DELETE FROM ${form.table} WHERE demographic_no=${patient};
      DELETE FROM encounterForm WHERE form_name=${q(title)} AND form_value=${q(value)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient})+
      (SELECT COUNT(*) FROM encounterForm WHERE form_name=${q(title)} AND form_value=${q(value)})`)==='0','Owned follow-up form rows or registration remain');
  });
  sql.execute(`UPDATE demographic SET address='1 Test St',city='Toronto',postal='M5V 2T6',phone='416-555-0100',
    phone2='416-555-0101',hin='9876543217',ver='AB',email='fake@example.invalid',roster_status='RO'
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  sql.execute(`INSERT INTO encounterForm(form_value,form_name,form_table,hidden)
    SELECT ${q(value)},${q(title)},${q(form.table)},COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  const columns=sql.rows(`SELECT COLUMN_NAME,COLUMN_KEY FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=${q(form.table)}`);
  const idColumn=columns.find(x=>x[1]==='PRI')?.[0];
  h.assert(idColumn&&fields.every(f=>columns.some(c=>c[0]===f.name)),'Follow-up configuration disagrees with the real form schema');
  const chart=await s.chart();
  let page,id,original;
  const input=f=>page.locator(`[name="${f.name}"]`).first();
  const state=()=>sql.rows(`SELECT \`${idColumn}\`,${fields.map(f=>`COALESCE(CAST(\`${f.name}\` AS CHAR),'')`).join(',')} FROM ${form.table} WHERE demographic_no=${patient} ORDER BY \`${idColumn}\``);
  async function save(){
    let response;
    await h.withExpectedDialogs(page,async()=>{
      [response]=await Promise.all([
        s.context.waitForEvent('response',{predicate:r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/form/formname')}),
        page.getByRole('button',{name:'Save',exact:true}).first().click(),
      ]);
    });
    h.assert(response.status()===302,`Save answered HTTP ${response.status()}`);
    h.assert(new URLSearchParams(response.request().postData()).get('CSRF-TOKEN'),'Save omitted CSRF protection');
    const landed=response.frame().page();
    await landed.waitForLoadState('domcontentloaded');page=landed;
  }
  async function reopen(){
    if(page&&!page.isClosed())await page.close();
    const since=s.recorder.requestFailures.length;
    await chart.reload({waitUntil:'domcontentloaded'});await waitForNavbars(chart,20000);
    // This explicit browser reload aborts the old chart's pagehide lock-release
    // beacon. Scope the expected unload signal to this reload only.
    const failures=s.recorder.requestFailures.splice(since);
    s.recorder.requestFailures.push(...failures.filter(x=>!(x.resourceType==='ping'&&x.errorText==='net::ERR_ABORTED'
      &&new URL(x.url).pathname.endsWith('/CaseManagementEntry'))));
    const link=chart.locator(`#leftNavBar a[onclick*="formname=${title}&"],#rightNavBar a[onclick*="formname=${title}&"]`).first();
    await revealSavedForm(chart,link);
    page=await ui.clickOpensPopup(chart,link,{context:s.context,recorder:s.recorder,label:`followup-${key}`,position:{x:8,y:9}});
    if(form.reveal)await form.reveal(page);
    h.assert(new URL(page.url()).searchParams.get('demographic_no')===patient,'Saved entry opened another patient');
  }
  await s.step('create the owned baseline through the real Forms menu and Save',async()=>{
    await chart.locator('#menuTitle1 a').hover();
    page=await s.popup(chart,chart.getByRole('link',{name:title,exact:true}),'followup-baseline');
    if(form.reveal)await form.reveal(page);
    for(const f of fields){if(f.check)await input(f).setChecked(stored(f)==='1');else await input(f).fill(f.value);}
    await save();
    await expectValue(sql,`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`,'1','Baseline save did not create exactly one record');
    const rows=state();h.assert(JSON.stringify(rows[0].slice(1))===JSON.stringify(fields.map(stored)),'Baseline clinical values were not stored');
    id=rows[0][0];original=JSON.stringify(sql.rows(`SELECT * FROM ${form.table} WHERE \`${idColumn}\`=${id} AND demographic_no=${patient}`));
  });
  await s.step(clear?'clear the obsolete answer on the saved form and preserve all untouched clinical fields'
    :'correct one answer on the saved form and preserve all untouched clinical fields',async()=>{
    await reopen();
    h.assert(new URL(page.url()).searchParams.get('formId')===id,'Saved entry did not open the baseline version');
    if(primary.check){
      // Make the clinician's intended transition explicit, even for legacy
      // templates that fail to restore a tick. The unmodified controls below
      // must still survive; a bad restore cannot silently erase those answers.
      if(clear)await input(primary).check();
      await input(primary).setChecked(expected==='1');
    }else await input(primary).fill(expected);
    await save();
    await expectValue(sql,`SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient}`,'2','Follow-up did not append one revision');
    const rows=state();
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM ${form.table} WHERE \`${idColumn}\`=${id} AND demographic_no=${patient}`))===original,'Follow-up overwrote the original record');
    const wanted=fields.map((f,i)=>i===0?expected:stored(f));
    h.assert(JSON.stringify(rows[1].slice(1))===JSON.stringify(wanted),`Follow-up changed clinical fields incorrectly: ${fields.filter((f,i)=>rows[1][i+1]!==wanted[i]).map(f=>f.name).join(', ')}`);
    id=rows[1][0];
  });
  await s.step('the chart reopens the latest follow-up version with the changed and unchanged answers',async()=>{
    await reopen();
    h.assert(new URL(page.url()).searchParams.get('formId')===id,'Chart reopened an older version');
    for(const [i,f]of fields.entries()){
      const wanted=i===0?expected:(f.check?stored(f):f.value),shown=f.check?(await input(f).isChecked()?'1':'0'):await input(f).inputValue();
      h.assert(shown===wanted,`Reopened follow-up lost ${f.name}`);
    }
    if(columns.some(c=>c[0]==='provider_no'))h.assert(sql.value(`SELECT provider_no FROM ${form.table} WHERE \`${idColumn}\`=${id}`)===provider,'Follow-up attributed to another provider');
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow,forms};
