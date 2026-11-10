#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {PNG}=require('pngjs');
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const pdf=require('./lib/incoming-mixed-pdf');
const {retireOwnedFile}=require('./lib/owned-file-retirement');
const {createPageEditCleanupGuard}=require('./lib/incoming-page-edit-capacity-check');
const {createIncomingPreviewFixture}=require('./lib/incoming-preview-fixture');
const {retainFixtures,trackWrite}=require('./lib/uncertain-server-writes');
const {settleOperations}=require('./graceful-signal-cancellation');
const q=h.sqlString,hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');

/** Identify which native transport produced malformed image bytes. */
function decodePng(bytes,label) {
  try{return PNG.sync.read(bytes);}catch(error){throw new Error(label+': '+error.message+' (length '+bytes.length+', signature '+bytes.subarray(0,8).toString('hex')+', tail '+bytes.subarray(-20).toString('hex')+')');}
}

/** Locate each asymmetric colour block independently of preview resolution. */
function colourBounds(image,colour) {
  let left=image.width,right=-1,top=image.height,bottom=-1,count=0;
  for(let y=0;y<image.height;y++)for(let x=0;x<image.width;x++) {
    const i=(y*image.width+x)*4;
    if(image.data[i+3]>=250&&colour.every((value,c)=>Math.abs(image.data[i+c]-value)<=15)) {
      left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);count++;
    }
  }
  h.assert(count>100,'Native preview is missing a scanned-page colour identity');
  return [left/image.width,top/image.height,(right+1)/image.width,(bottom+1)/image.height,count/(image.width*image.height)];
}
function verifyPreview(actual,reference,id) {
  h.assert(Math.abs(actual.width/actual.height-reference.width/reference.height)<0.01,'Native preview has wrong page geometry');
  for(const colour of [[[220,30,40],[20,160,50],[30,70,220]][id-1],[240,150,20]]) {
    const expected=colourBounds(reference,colour),observed=colourBounds(actual,colour);
    h.assert(observed.every((value,index)=>Math.abs(value-expected[index])<0.025),'Native preview has wrong scanned content/orientation');
  }
}

async function workflow(s) {
  const root=fs.realpathSync(process.env.INCOMINGDOCUMENT_DIR),directory=path.join(root,'1','File'),recycle=path.join(root,'1','File_deleted'),cache=path.join(root,'1','File_cache');
  h.assert(fs.realpathSync(directory)===directory&&directory.startsWith(root+path.sep),'Incoming queue must be an ordinary installed directory');
  const sourceName=s.marker+'.pdf',extractedName=s.marker+'E3.pdf',controlName=s.marker+'-control.pdf';
  const source=path.join(directory,sourceName),extracted=path.join(directory,extractedName),control=path.join(directory,controlName);
  const original=pdf.fixturePdf(s.marker),baseline=pdf.inspect(original),owned=new Map(),guards=[];
  h.assert(baseline[0].text===''&&baseline[1].text===''&&baseline[2].text===s.marker+' MIXED THREE','Fixture must contain two genuine image-only scans and one mixed page');
  pdf.verifyPages(baseline,[{id:1},{id:2},{id:3}],baseline);
  const listing=dir=>new Map(fs.existsSync(dir)?fs.readdirSync(dir).map(name=>{
    const file=path.join(dir,name),stat=fs.lstatSync(file);h.assert(stat.isFile()&&!stat.isSymbolicLink(),'Queue listing contains a nonordinary entry');return [name,hash(fs.readFileSync(file))];
  }):[]);
  const beforeQueue=listing(directory),beforeRecycle=listing(recycle);
  const previews=createIncomingPreviewFixture(s,{names:[sourceName,extractedName,controlName],cache});
  function remember(file) {
    const stat=fs.lstatSync(file);h.assert(stat.isFile()&&!stat.isSymbolicLink(),'Owned mixed PDF was replaced by a nonordinary file');
    if(!owned.has(file))owned.set(file,new Set());owned.get(file).add(hash(fs.readFileSync(file)));
  }
  const keys=['incoming_document_default_queue','incoming_document_entry_mode','view_document_as'],where=`provider_no=${q(s.provider)} AND name IN (${keys.map(q).join(',')})`;
  const preferences=s.sql.rows(`SELECT id,name,value,IF(value IS NULL,1,0) FROM property WHERE ${where} ORDER BY id`);
  s.cleanup(()=>{
    for(const guard of guards)guard.assertCleanup();
    previews.cleanup();
    for(const [file,digests] of owned)retireOwnedFile(file,bytes=>digests.has(hash(bytes)));
    h.assert([...owned.keys()].every(file=>!fs.existsSync(file)),'Owned mixed PDF/recycle files remain');
    const ids=preferences.map(row=>row[0]);h.assert(ids.every(id=>/^[1-9]\d*$/.test(id)),'Invalid incoming preference identity');
    s.sql.execute(`DELETE FROM property WHERE ${where}${ids.length?' AND id NOT IN ('+ids.join(',')+')':''}`);
    for(const [id,name,value,isNull] of preferences)s.sql.execute(`UPDATE property SET value=${isNull==='1'?'NULL':q(value)} WHERE id=${id} AND provider_no=${q(s.provider)} AND name=${q(name)}`);
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,name,value,IF(value IS NULL,1,0) FROM property WHERE ${where} ORDER BY id`))===JSON.stringify(preferences),'Incoming preferences were not restored');
    for(const [dir,before] of [[directory,beforeQueue],[recycle,beforeRecycle]])for(const [name,digest] of before)h.assert(listing(dir).get(name)===digest,'An unrelated queue/recycle document changed');
  });
  const owner=fs.statSync(directory);
  for(const file of [source,control]){h.assert(!fs.existsSync(file),'Mixed-PDF namespace already exists');owned.set(file,new Set([hash(original)]));fs.writeFileSync(file,original,{flag:'wx',mode:0o640});fs.chownSync(file,owner.uid,owner.gid);remember(file);}
  h.assert(!fs.existsSync(extracted),'Extracted mixed-PDF namespace already exists');
  await previews.install();
  const {page:inbox}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#inboxLink').first(),{context:s.context,recorder:s.recorder,label:'mixed-incoming-inbox'});
  const page=await s.popup(inbox,inbox.locator('a[href*="ViewIncomingDocs"]').first(),'mixed-incoming-editor');
  async function reload(action){await Promise.all([page.waitForEvent('domcontentloaded'),action()]);await page.waitForLoadState('networkidle');}
  if(await page.locator('#queueList').inputValue()!=='1')await reload(()=>page.locator('#queueList').selectOption('1'));
  await reload(()=>page.locator('button[onclick="loadPdf(\'1\',\'File\');"]').first().click());
  async function open(name){await reload(()=>page.locator('#SelectPdfList').selectOption(name));h.assert((await page.locator('fieldset legend').allTextContents()).some(text=>text.includes(name)),'Incoming editor opened another document');}
  await open(sourceName);
  if(await page.locator('#imageTypeList').inputValue()!=='Image')await reload(()=>page.locator('#imageTypeList').selectOption('Image'));
  async function preview(number,id,turns=0) {
    const awaitedName=await page.locator('form[name="PdfInfoForm"] input[name="pdfName"]').inputValue();
    const ready=page.waitForResponse(response=>{const url=new URL(response.url());return url.pathname.endsWith('/documentManager/ManageDocument')&&url.searchParams.get('method')==='viewIncomingDocPageAsImage'&&url.searchParams.get('pdfName')===awaitedName&&url.searchParams.get('curPage')===String(number);},{timeout:45000});
    ready.catch(()=>{});
    await page.locator('#SelectPageList').selectOption(String(number));const response=await ready;
    h.assert(response.status()===200&&response.headers()['content-type']?.startsWith('image/png'),'Native incoming preview failed');
    // Like nativeGraphResponse, use this exact UI request's upstream bytes.
    // CDP response.body() text-decodes PNGs declaring charset=UTF-8.
    const captured=previews.read(response);
    verifyPreview(decodePng(captured.body,'Native preview '+number),pdf.rotateRaster(baseline[id-1].raster,turns),id);
    previews.assertSafe();
    await page.waitForLoadState('networkidle');
    const frameUrl=new URL(await page.locator('#docdisp iframe').getAttribute('src'),s.config.baseUrl);
    h.assert(frameUrl.searchParams.get('pdfName')===awaitedName&&frameUrl.searchParams.get('curPage')===String(number),'Displayed preview frame names another document/page');
    const displayed=await page.frameLocator('#docdisp iframe').locator('img').evaluate(image=>({complete:image.complete,width:image.naturalWidth,height:image.naturalHeight}));
    const image=decodePng(captured.body,'Displayed preview');
    h.assert(displayed.complete&&displayed.width===image.width&&displayed.height===image.height,'Browser did not display the native scanned preview');
  }
  await s.step('native image previews preserve scanned identities, mixed geometry and existing rotations',async()=>{for(let i=1;i<=3;i++)await preview(i,i);});
  async function edit(action,button,promptText) {
    const guard=createPageEditCleanupGuard({marker:s.marker,source,owned:[...owned.keys()]});guards.push(guard);
    let forwarded=false,acknowledged=false;
    const matches=request=>request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/documentManager/ViewIncomingDocs')&&new URLSearchParams(request.postData()||'').get('pdfAction')===action;
    let dispatchFailure;
    const pattern='**/documentManager/ViewIncomingDocs*';
    const handler=async route=>{
      if(!matches(route.request()))return route.continue();
      try {
        const fields=new URLSearchParams(route.request().postData()||'');
        h.assert(fields.get('pdfName')===sourceName&&fields.get('pdfDir')==='File'&&fields.get('queueList')==='1','Native edit left its exact owned document/queue');
        guard.forward(route.request());forwarded=true;await route.continue();
      } catch(error){dispatchFailure=error;guard.unknown('Native edit dispatch could not be established');retainFixtures(s.context);await route.abort();}
    };
    await page.route(pattern,handler);
    const operation=(async()=>{
      try {
        let response;const ready=page.waitForResponse(r=>matches(r.request()),{timeout:45000});ready.catch(()=>{});
        const dialogs=await h.withExpectedDialogs(page,async()=>{[response]=await settleOperations([ready,ui.clickAndAwaitReload(page,button)]);},{promptText});
        const failure=await response.finished();h.assert(!dispatchFailure&&!failure,'Native PDF edit response did not complete');
        if(!forwarded){guard.unknown('Native edit was not supervised');retainFixtures(s.context);throw new Error('Native PDF edit escaped supervision');}
        acknowledged=true;guard.completed();
        h.assert(response.status()===200,'Native PDF edit failed');await page.waitForLoadState('networkidle');
        h.assert(dialogs.length===(action==='ExtractPagePDF'||action==='DeletePage'?1:0),'Native edit dialog contract changed');
        for(const file of [source,extracted])if(fs.existsSync(file))remember(file);
        for(const name of listing(recycle).keys())if(!beforeRecycle.has(name)&&name.startsWith(s.marker)){const file=path.join(recycle,name);remember(file);}
      } catch(error){if(forwarded&&!acknowledged){guard.unknown('Native edit acknowledgement missing');retainFixtures(s.context);}throw error;}
      finally{await page.unroute(pattern,handler);}
    })();trackWrite(s.context,operation);await operation;
    h.assert(fs.readFileSync(control).equals(original),'Incoming edit changed the unrelated control');
  }
  await s.step('native extraction retains exact scanned page order, crop boxes, rotation, pixels and annotation',async()=>{
    await edit('ExtractPagePDF',page.locator('button[onclick^="extractPagePdf("]'),'2');
    pdf.verifyPages(pdf.inspect(fs.readFileSync(source)),[{id:1},{id:3}],baseline);
    pdf.verifyPages(pdf.inspect(fs.readFileSync(extracted)),[{id:2}],baseline);
    await open(extractedName);await preview(1,2);await open(sourceName);await preview(1,1);await preview(2,3);
  });
  await s.step('native single and all-page rotation preserve relative geometry and scanned/mixed content',async()=>{
    await page.locator('#SelectPageList').selectOption('1');await page.waitForLoadState('networkidle');
    await edit('Rotate90',page.locator('button[onclick^="rotatePdf("]').filter({hasText:'+90'}));
    pdf.verifyPages(pdf.inspect(fs.readFileSync(source)),[{id:1,turns:1},{id:3}],baseline);await preview(1,1,1);
    await edit('RotateAll180',page.locator('button[onclick^="rotateAllPagePdf("]').filter({hasText:'180'}));
    pdf.verifyPages(pdf.inspect(fs.readFileSync(source)),[{id:1,turns:3},{id:3,turns:2}],baseline);await preview(1,1,3);await preview(2,3,2);
    pdf.verifyPages(pdf.inspect(fs.readFileSync(extracted)),[{id:2}],baseline);
  });
  await s.step('native selected-page deletion preserves the remaining mixed page, extraction and supported recycle output',async()=>{
    await page.locator('#SelectPageList').selectOption('1');await page.waitForLoadState('networkidle');
    await edit('DeletePage',page.locator('button[onclick^="deletePagePdf("]'));
    pdf.verifyPages(pdf.inspect(fs.readFileSync(source)),[{id:3,turns:2}],baseline);
    pdf.verifyPages(pdf.inspect(fs.readFileSync(extracted)),[{id:2}],baseline);
    const added=[...listing(recycle).keys()].filter(name=>!beforeRecycle.has(name));
    if((process.env.INCOMINGDOCUMENT_RECYCLEBIN||'true').trim()!=='false') {
      h.assert(added.length===1&&added[0].startsWith(s.marker),'Deleted page did not reach its exact owned recycle output');
      pdf.verifyPages(pdf.inspect(fs.readFileSync(path.join(recycle,added[0]))),[{id:1,turns:3}],baseline);
    } else h.assert(added.length===0,'Disabled recycling still wrote a file');
    await open(sourceName);await preview(1,3,2);await open(extractedName);await preview(1,2);
  });
}
if(require.main===module)runWorkflow('incoming-mixed-pdf',workflow,{openPatient:false,openMaster:false,preflight:()=>require('./lib/export-content-helpers').requirePoppler('pdfinfo','pdftoppm','pdftotext'),preflightConfig:()=>{
  if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1'||!process.env.INCOMINGDOCUMENT_DIR)throw new h.SkipCheck('Mixed PDF edits require CARLOS_DISPOSABLE_HOST=true, EXCLUSIVE=1 and the installed incoming queue');
}});
module.exports={workflow,colourBounds,verifyPreview};
