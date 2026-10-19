#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Rx entry actions that must stop before clinical writes: empty Save Only /
// Save And Print; declining the Custom Drug / Note warning preserves the draft.
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const action=process.env.RX_ENTRY_ACTION||'empty-save';
const names={'empty-save':'rx-empty-save-only','empty-print':'rx-empty-save-print',
  'decline-custom':'rx-custom-drug-decline','decline-note':'rx-custom-note-decline'};
async function workflow(s){
  h.assert(names[action],'Unknown RX_ENTRY_ACTION');
  const f=await prepareRx(s),rx=await f.open();
  const decline=action.startsWith('decline');
  await s.step(decline?'declining the warning leaves the typed search and empty draft without a write'
    :'saving an empty prescription explains the missing medication without a write or print window',async()=>{
    const requests=[];
    const watch=r=>{if(r.method()==='POST'&&/\/rx\/(WriteScript|UpdateScript|search|deleteRx)$/.test(new URL(r.url()).pathname))requests.push(r);};
    rx.on('request',watch);
    const text=s.marker+' proposed';
    if(decline)await rx.locator('#searchString').fill(text);
    const button=decline?(action==='decline-custom'?'#customDrug':'#customNote'):(action==='empty-print'?'#saveButton':'#saveOnlyButton');
    let dialogs;
    try{dialogs=await h.withExpectedDialogs(rx,()=>rx.locator(button).click(),{accept:!decline});}
    finally{rx.off('request',watch);}
    h.assert(dialogs.length===1&&dialogs[0].type===(decline?'confirm':'alert'),'The entry action did not explain its warning');
    h.assert(requests.length===0,'The stopped entry action still posted a write');
    h.assert(await rx.locator('[id^="drugName_"]').count()===0,'The stopped entry action staged a medication');
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient})+(SELECT COUNT(*) FROM prescription WHERE demographic_no=${s.patient})`)==='0','The stopped entry action saved clinical rows');
    h.assert(rx.frames().every(frame=>!new URL(frame.url()==='about:blank'?'https://invalid/':frame.url()).pathname.endsWith('/rx/viewScript')),'Empty Save opened a prescription print window');
    if(decline)h.assert(await rx.locator('#searchString').inputValue()===text,'Declining the warning lost the typed search');
  });
}
if(require.main===module)runWorkflow(names[action]||'rx-entry-guard',workflow);
module.exports={workflow};
