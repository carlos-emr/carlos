#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict');
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {workflow:merge}=require('./demographic-merge-playwright-checks');
const {populatedMergeFixture}=require('./lib/populated-merge-fixture');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {moduleList}=require('./lib/echart-module-list');
const {releaseChartLocks}=require('./lib/chart-lock-cleanup');
const {failureMark}=require('./lib/concurrency-support');
const {consumeUnloadBeacons}=require('./echart-note-draft-recovery-playwright-checks');
const {markProblems,takeProblems}=require('./lib/form-problems');
const {heldClinicalRequests}=require('./lib/held-clinical-requests');
const {textPdf}=require('./lib/stored-pdf-documents');
function preflightConfig(){if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')
  throw new h.SkipCheck('Populated merge acceptance requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1');}
function assertExclusiveRows(actual,expected,label){assert.deepEqual([...actual].sort(),[...expected].sort(),label+': missing, foreign or duplicate clinical rows');}
function moduleLedger(){const failures=[];return {failures,async check(label,operation){const count=failures.length;try{await operation();console.log((failures.length===count?'  PASS clinical module: ':'  COMPLETED with clinical findings: ')+label);}catch(error){failures.push(new Error(label+': '+error.message,{cause:error}));console.error('  FINDING clinical module: '+label+': '+error.message);}},finish(){if(failures.length)throw new AggregateError(failures,'Populated merge clinical acceptance: '+failures.map(error=>error.message).join('; '));}};}
async function workflow(s){
  const ledger=moduleLedger(),lifecycle={childrenRemoved:true};let fixture,guard,primary;
  const labels=new Set();
  async function concern(label,operation){
    const before=markProblems(s.recorder),pages=new Set(s.context.pages()),local=[];
    const relabel=page=>{const name='merge-clinical-'+label;h.wireStrictPage(page,name,s.recorder);labels.add(name);local.push(name);};
    s.context.on('page',relabel);
    try{await ledger.check(label,operation);}finally{
      s.context.off('page',relabel);
      await ledger.check(label+' browser diagnostics',async()=>{
        const findings=takeProblems(s.recorder,[...labels],before);h.assert(!findings.length,findings.join('; '));
      });
      await ledger.check(label+' popup cleanup',async()=>{
        const opened=s.context.pages().filter(page=>!pages.has(page));
        await releaseChartLocks(s.context,s.config.baseUrl,opened);
        const mark=failureMark(s.recorder);
        for(const page of opened.reverse())if(!page.isClosed()){await page.waitForLoadState('networkidle');await page.close();}
        consumeUnloadBeacons(s.recorder,mark);
      });
    }
  }
  async function inspect(patient,phase,{tailViaHead=false}={}){
    await concern(phase+' '+patient.tag,async()=>{
      let {masterPage:master}=await openMasterRecord(s.context,s.schedule,s.recorder,{searchTerm:patient.marker,preferredDemographicNo:tailViaHead?fixture.head.patient:patient.patient,timeout:20000});
      if(tailViaHead){
        h.assert(new URL(master.url()).searchParams.get('demographic_no')===fixture.head.patient,'Merged patient search did not open its head');
        const link=master.locator('.demo-toolbar-id a').filter({hasText:'#'+patient.patient});
        h.assert(await link.count()===1,'Merged head lacks its native tail record link');
        await Promise.all([master.waitForURL(url=>url.searchParams.get('demographic_no')===patient.patient),link.click()]);
      }
      h.assert(new URL(master.url()).searchParams.get('demographic_no')===patient.patient,'Native record navigation selected a different clinical owner');
      const chart=await openChart(s.context,master,s.recorder,20000);await waitForNavbars(chart,20000);
      h.assert(new URL(chart.url()).searchParams.get('demographicNo')===patient.patient,'E-Chart did not retain the selected head/tail owner');
      for(const [name,divId,key] of [['prescriptions','Rx','drug'],['allergies','allergies','allergy']])await concern(phase+' '+patient.tag+' '+name,async()=>{
        const visible=phase==='merged'&&patient===fixture.head?[fixture.head,fixture.tail]:[patient];
        const excluded=fixture.fixtures.filter(source=>!visible.includes(source));
        const list=moduleList(chart,divId);
        h.assert(await list.shown()===String(visible.length)&&await list.rows.count()===visible.length,'Clinical module has missing, foreign or duplicate merged source records');
        for(const source of visible){
          const row=list.row(source[key]);await row.waitFor();h.assert((await row.innerText()).includes(source[key]),'Owned clinical content is missing');
          if(key==='drug')h.assert((await row.getAttribute('title')).includes('Take 1 tablet daily'),'Prescription directions are missing');
        }
        for(const other of excluded)h.assert(await list.row(other[key]).count()===0,'An unrelated source/control record leaked into this module');
        const label='merge-clinical-'+name+'-view';labels.add(label);
        const view=await s.popup(chart,chart.locator('#menuTitle'+divId+' a').first(),label);
        h.assert(new URL(view.url()).searchParams.get('demographicNo')===patient.patient,'Full clinical module selected another source patient');
        const content=await view.locator('body').innerText();
        for(const source of visible){h.assert(content.includes(source[key]),'Full clinical module omitted its owned record');
          if(key==='allergy')h.assert(content.includes(source.reaction),'Full allergy view omitted the source reaction');}
        for(const other of excluded)h.assert(!content.includes(other[key]),'Full clinical module disclosed an unrelated source/control record');
      });
      await concern(phase+' '+patient.tag+' signed notes and revisions',async()=>{
        const latest=patient.notes.at(-1),note=chart.locator('#n'+latest.id);
        await note.waitFor({state:'visible'});h.assert((await note.innerText()).includes(latest.text.split('\n')[0]),'Latest signed note is missing');
        h.assert(await chart.locator('#signed'+latest.id).inputValue()==='true','Signed revision is not displayed as signed');
        for(const other of fixture.fixtures.filter(other=>other!==patient))for(const revision of other.notes)h.assert(await chart.locator('#n'+revision.id).count()===0,'A foreign note appears under this patient');
        const rev=note.locator('a[onclick^="return showHistory("]');
        const history=await s.popup(chart,rev,'merge-clinical-note-history');labels.add('merge-clinical-note-history');
        const address=new URL(history.url());h.assert(address.searchParams.get('noteId')===latest.id&&address.searchParams.get('demographicNo')===patient.patient,'Native revision history lost source attribution');
        const text=await history.locator('body').innerText();
        for(const revision of patient.notes)h.assert(text.includes(revision.text),'Native revision history omitted signed source content');
        h.assert((text.match(/Signed by/g)||[]).length===2,'Native history lost the two signing records');
        for(const other of fixture.fixtures.filter(other=>other!==patient))h.assert(!text.includes(other.label),'Foreign revision leaked into source history');
      });
      await concern(phase+' '+patient.tag+' documents',async()=>{
        const browser=await s.popup(chart,chart.locator('#note-control-panel button',{hasText:'Browse Notes'}),'merge-clinical-documents');labels.add('merge-clinical-documents');
        h.assert(new URL(browser.url()).searchParams.get('demographic_no')===patient.patient,'Document browser selected another patient');
        const visibleDocs=phase==='merged'&&patient===fixture.head?[...fixture.head.docs,...fixture.tail.docs]:patient.docs;
        assertExclusiveRows(await browser.locator('#doclist option').evaluateAll(options=>options.map(option=>option.value).filter(value=>value!=='0')),visibleDocs.map(doc=>doc.id+'-application/pdf'),'Source document browser');
        for(const doc of visibleDocs){const start=guard.records.length;await browser.locator('#doclist').selectOption(doc.id+'-application/pdf');
          await browser.locator('#docdisp iframe').waitFor();const source=new URL(await browser.locator('#docdisp iframe').getAttribute('src'),browser.url());
          h.assert(source.searchParams.get('doc_no')===doc.id,'Document preview selected another source PDF');
          const receipt=await guard.wait(record=>guard.records.indexOf(record)>=start&&record.kind==='document-read'&&record.documentId===doc.id&&record.state==='acknowledged');
          h.assert(receipt.status===200&&(receipt.headers['content-type']||'').startsWith('application/pdf'),'Native source preview did not serve a PDF');
          h.assert(receipt.body.equals(textPdf(doc.label,doc.pages)),'Native document preview changed, truncated or substituted the original PDF bytes');
        }
      });
      await concern(phase+' '+patient.tag+' measurements',async()=>{
        const index=await s.popup(chart,chart.getByRole('link',{name:'Measurements',exact:true}).first(),'merge-clinical-measurements');labels.add('merge-clinical-measurements');
        const history=await s.popup(index,index.locator('a[onclick*="SetupDisplayHistory?type=WT"]').first(),'merge-clinical-measurement-history');labels.add('merge-clinical-measurement-history');
        h.assert(new URL(history.url()).searchParams.get('demographicNo')===patient.patient,'Measurement history selected another source patient');
        const ids=s.sql.rows('SELECT id FROM measurements WHERE demographicNo=? ORDER BY id',[patient.patient]).flat();
        assertExclusiveRows(await history.locator('input[name="deleteCheckbox"]').evaluateAll(inputs=>inputs.map(input=>input.value)),ids,'Source WT history');
        const row=history.locator('tr.data').filter({has:history.locator('input[name="deleteCheckbox"][value="'+ids[0]+'"]')});
        h.assert((await row.locator('td[title="data"]').innerText()).trim()===patient.weight,'Measurement history changed the source value');
        h.assert((await row.locator('td[title="observed date"]').innerText()).trim()==='2026-01-02','Measurement history changed the observation date');
      });
    });
  }
  try{await merge(s,{
    async prepare(ids){
      fixture=populatedMergeFixture(s,ids,lifecycle);
      guard=heldClinicalRequests(s,{identity:s.marker,matches:url=>url.origin===new URL(s.config.baseUrl).origin&&['/admin/MergeRecords','/CaseManagementEntry','/documentManager/ManageDocument'].some(path=>url.pathname.endsWith(path)),select:request=>{
        const url=new URL(request.url());
        if(url.pathname.endsWith('/documentManager/ManageDocument')&&url.searchParams.get('method')==='display'){
          const ids=url.searchParams.getAll('doc_no');
          h.assert(request.method()==='GET'&&ids.length===1&&fixture.fixtures.some(patient=>patient.docs.some(doc=>doc.id===ids[0])),'Native PDF preview escaped the owned document namespace');
          return {kind:'document-read',write:false,documentId:ids[0]};
        }
        if(request.method()!=='POST')return null;
        const data=new URLSearchParams(request.postData()||'');
        if(new URL(request.url()).pathname.endsWith('/admin/MergeRecords')){
          const owned=s.sql.rows('SELECT demographic_no FROM demographic WHERE last_name=?',[s.marker]).flat();
          h.assert(data.getAll('records').length>0&&data.getAll('records').every(id=>owned.includes(id)),'Native merge selected an unowned patient');
          h.assert(['merge','unmerge'].includes(data.get('mergeAction')),'Unfamiliar native merge operation');return {kind:'merge',write:true};
        }
        if(data.get('method')==='autosave'){h.assert(false,'Read-only clinical inspection attempted an autosave');}
        return null;
      }});s.cleanup(()=>guard.cleanup());await guard.install();
      for(const patient of fixture.fixtures)await inspect(patient,'before merge');
      fixture.assertPreserved('Native baseline inspection');
      ledger.finish();
    },
    async afterMerge(){fixture.assertPreserved('Native merge');await inspect(fixture.head,'merged');await inspect(fixture.tail,'merged',{tailViaHead:true});fixture.assertPreserved('Merged clinical inspection');},
    async afterUnmerge(){fixture.assertPreserved('Native unmerge');for(const patient of fixture.fixtures)await inspect(patient,'unmerged');fixture.assertPreserved('Unmerged clinical inspection');},
    beforeCleanup:()=>h.assert(lifecycle.childrenRemoved,'Clinical child cleanup failed; preserve merge parents'),
  });ledger.finish();}catch(error){primary=ledger.failures.length&&!ledger.failures.every(failure=>error.errors?.includes(failure))
    ?new AggregateError([error,...ledger.failures],error.message+'; '+ledger.failures.map(failure=>failure.message).join('; ')):error;}
  try{if(guard)await guard.shutdown();}catch(error){primary=primary?new AggregateError([primary,error],primary.message+'; request drain: '+error.message):error;}
  if(primary)throw primary;
}
if(require.main===module)runWorkflow('demographic-merge-clinical-history',workflow,{openPatient:false,preflightConfig});
module.exports={workflow,preflightConfig,assertExclusiveRows,moduleLedger};
