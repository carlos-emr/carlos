#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const fs=require('node:fs'),path=require('node:path');
const {createHash}=require('node:crypto');
const {execFileSync}=require('node:child_process');
const {PNG}=require('pngjs');
const h=require('./lib/playwright-harness'),x=require('./lib/export-content-helpers');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {requirePoppler}=require('./lib/stored-pdf-documents');
const {readRegular,retireOwnedFile}=require('./lib/owned-file-retirement');
const {reserveInstanceIds,rendererFileFixture}=require('./eform-deployed-page-exclusion-playwright-checks');
const graph=require('./lib/pdf-graph');
const q=h.sqlString,NAME='eform-image-zip-roundtrip';
const imageRoot=()=>process.env.EFORM_IMAGE_DIR||'/var/lib/carlos-emr/CarlosDocument/eform/images';
const renderRoot=()=>process.env.EFORM_RENDER_TEMP_DIR||'/var/lib/carlos-emr/catalina/work/carlos/eform-browser-pdf-temp';
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');

/** Independent pixel fixtures: pale-blue page background and red/black inline tile. */
function asset(kind) {
  h.assert(['background','inline'].includes(kind),'Unknown image fixture');
  const png=new PNG({width:kind==='background'?600:100,height:kind==='background'?800:60});
  for(let y=0;y<png.height;y++)for(let x=0;x<png.width;x++) {
    const rgb=kind==='background'?(x<300?[190,220,250]:[250,225,180]):(x<50?[220,30,40]:[20,20,20]);
    const offset=(y*png.width+x)*4;png.data.set([...rgb,255],offset);
  }
  return PNG.sync.write(png);
}

/** Refuse missing/foreign/changed ZIP resources, independently of the importer. */
function verifyArchive(bytes,{name,html,filename,assets,provider}) {
  const entries=x.unzip(bytes);
  const expected=[name+'/eform.properties',name+'/'+filename,...assets.map(a=>name+'/'+a.name)].sort();
  const footer=bytes.lastIndexOf(Buffer.from([0x50,0x4b,0x05,0x06]));
  h.assert(footer>=0&&footer+22<=bytes.length&&footer+22+bytes.readUInt16LE(footer+20)===bytes.length
    &&bytes.readUInt16LE(footer+8)===expected.length&&bytes.readUInt16LE(footer+10)===expected.length,
  'ZIP has an unexpected central-directory resource count');
  h.assert(JSON.stringify(Object.keys(entries).sort())===JSON.stringify(expected),'ZIP has missing, duplicate or unexpected resource names');
  h.assert(entries[name+'/'+filename].equals(Buffer.from(html)),'Export changed template HTML');
  const properties=entries[name+'/eform.properties'].toString('latin1').split(/\r?\n/);
  for(const [key,value] of [['form.name',name],['form.htmlFilename',filename],['form.creator',provider]])
    h.assert(properties.includes(key+'='+value),'Export properties changed '+key);
  for(const a of assets) {
    h.assert(entries[name+'/'+a.name].equals(a.bytes),'Export changed image bytes: '+a.kind);
    h.assert(html.includes('${oscar_image_path}'+a.name),'Template image is not bound to its included local asset');
  }
}

/** The rendered 600x800 CSS page must preserve both independently coloured regions. */
function verifyPdf(bytes,note) {
  h.assert(graph.pageCount(bytes)===1,'Imported image eForm PDF has the wrong page count');
  h.assert(graph.pageText(bytes,1).includes(note),'PDF lost the entered clinical value');
  const png=PNG.sync.read(execFileSync('pdftoppm',['-png','-r','96','-f','1','-l','1','-singlefile','-'],
    {input:bytes,timeout:30000,maxBuffer:8*1024*1024}));
  verifyRaster(png);
  h.assert(!/Submit|Download|Print Only|eForm Tools/.test(graph.pageText(bytes,1)),'PDF includes the eForm toolbar');
}

/** Verify actual pixels; extracted text cannot stand in for a missing image. */
function verifyRaster(png) {
  h.assert(Math.abs(png.width-600)<=2&&Math.abs(png.height-800)<=2,'PDF changed image page geometry');
  h.assert(png.data.length===png.width*png.height*4,'PDF raster is incomplete');
  for(const [x,y,rgb] of [[50,50,[190,220,250]],[550,50,[250,225,180]],[220,320,[220,30,40]],[280,320,[20,20,20]]]) {
    for(let dy=-2;dy<=2;dy++)for(let dx=-2;dx<=2;dx++) {
      const offset=((y+dy)*png.width+x+dx)*4;
      h.assert(rgb.every((value,index)=>Math.abs(png.data[offset+index]-value)<=8),'PDF omitted or changed the '+x+','+y+' image region');
    }
  }
}

/** Export actual resources, remove the source, then import/use only the downloaded ZIP. */
async function workflow(s) {
  const {sql,marker,patient,provider}=s,name=marker+'ZIP',filename=marker+'.html';
  const assets=['background','inline'].map(kind=>({kind,name:marker+'-'+kind+'.png',bytes:asset(kind)}));
  const html='<html><head><title>'+name+'</title><script>var needToConfirm=false;function releaseDirtyFlag(){needToConfirm=false;}</script></head><body>'
    +'<form method="post" id="FormName" name="FormName"><div id="page1" style="position:relative;width:600px;height:800px">'
    +'<img id="background" src="${oscar_image_path}'+assets[0].name+'" style="position:absolute;left:0;top:0;width:600px;height:800px">'
    +'<img id="inline" src="${oscar_image_path}'+assets[1].name+'" style="position:absolute;left:200px;top:300px;width:100px;height:60px">'
    +'<input id="ownedNote" name="ownedNote" value="" style="position:absolute;left:40px;top:150px;width:500px;font-size:16px">'
    +'</div></form></body></html>';
  const root=imageRoot(),rootStat=fs.statSync(root),scratch=x.scratchDir(),fids=new Set();let sourceId,importId,claimed=false;
  const baselineFiles=new Map(fs.readdirSync(root).filter(n=>fs.lstatSync(path.join(root,n)).isFile()).map(n=>[n,digest(readRegular(path.join(root,n)).bytes)]));
  const library=()=>JSON.stringify(sql.rows(`SELECT * FROM eform WHERE form_name<>${q(name)} OR form_name IS NULL ORDER BY fid`));
  const baselineLibrary=library();
  const unchanged=()=>{
    h.assert(library()===baselineLibrary,'Unrelated eForm library rows changed');
    for(const [file,hash] of baselineFiles)h.assert(digest(readRegular(path.join(root,file)).bytes)===hash,'Unrelated eForm image changed');
  };
  h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${q(name)}`)==='0','ZIP template namespace already exists');
  for(const a of assets)h.assert(!fs.existsSync(path.join(root,a.name)),'ZIP image namespace already exists');
  s.cleanup(()=>fs.rmSync(scratch,{recursive:true,force:true}));
  const rendered=rendererFileFixture(renderRoot(),[name]);s.cleanup(()=>rendered.cleanup());
  s.cleanup(()=>{
    if(!claimed)return;
    const named=sql.rows(`SELECT fid FROM eform WHERE form_name=${q(name)}`).flat();
    h.assert(named.length<=1,'Ambiguous ZIP template ownership');
    const ids=[...new Set([...named,...fids])];
    h.assert(ids.every(id=>/^[1-9]\d*$/.test(id)),'Invalid ZIP template identity');
    for(const fid of ids) {
      if(sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${fid}`)==='0')continue;
      h.assert(!importId||fid===importId,'ZIP imported template identity changed');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${fid} AND form_name=${q(name)} AND file_name=${q(filename)} AND form_html=${q(html)} AND form_creator=${q(provider)}`)==='1','ZIP template bytes/ownership changed');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE (fid=${fid} OR form_name=${q(name)}) AND COALESCE(
        (fid=${fid} AND form_name=${q(name)} AND demographic_no=${patient} AND form_provider=${q(provider)}),0)=0`)==='0','ZIP instance ownership changed');
      const saved=sql.rows(`SELECT fdid FROM eform_data WHERE fid=${fid}`).flat();
      h.assert(saved.every(id=>/^[1-9]\d*$/.test(id)),'Invalid ZIP instance identity');
      if(saved.length) {
        h.assert(sql.value(`SELECT COUNT(*) FROM EFormDocs WHERE fdid IN (${saved.join(',')})`)==='0','Unexpected ZIP instance attachments');
        sql.execute(`DELETE FROM eform_values WHERE fdid IN (${saved.join(',')}); DELETE FROM eform_data WHERE fid=${fid} AND demographic_no=${patient}`);
      }
      sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(name)}`);
    }
    for(const fid of fids)h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform_data WHERE fid=${fid})+(SELECT COUNT(*) FROM eform_values WHERE fid=${fid})`)==='0','Retired source/import instance references remain');
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(name)})+(SELECT COUNT(*) FROM eform_data WHERE form_name=${q(name)} OR demographic_no=${patient})`)==='0','Owned ZIP eForm rows remain');
    for(const a of assets)retireOwnedFile(path.join(root,a.name),bytes=>bytes.equals(a.bytes));
    for(const a of assets)h.assert(!fs.existsSync(path.join(root,a.name)),'Owned ZIP image remains');
    unchanged();
  });
  claimed=true;
  for(const a of assets) {const file=path.join(root,a.name);fs.writeFileSync(file,a.bytes,{flag:'wx',mode:0o644});fs.chownSync(file,rootStat.uid,rootStat.gid);}
  sql.execute(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(name)},${q(filename)},${q(name)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1)`);
  sourceId=sql.value(`SELECT fid FROM eform WHERE form_name=${q(name)}`);h.assert(/^[1-9]\d*$/.test(sourceId),'Source ZIP template missing');fids.add(sourceId);
  reserveInstanceIds(s,{id:sourceId,name});
  const manager=await s.context.newPage();let exported;
  await s.step('native export contains exact HTML, properties and both image resources',async()=>{
    await h.gotoApp(manager,s.config.baseUrl,'/eform/efmformmanager');
    const link=manager.locator(`#eformTbl a[href*="method=exportEForm"][href*="fid=${sourceId}"]`);
    exported=await x.saveDownload(manager,scratch,()=>link.click(),{route:/\/eform\/manageEForm$/});
    h.assert(exported.status===200,'ZIP export failed');verifyArchive(exported.bytes,{name,html,filename,assets,provider});
  });
  await s.step('source template and images are absent before the exported bytes are imported',async()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${sourceId}`)==='0','Source unexpectedly has patient instances');
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${sourceId} AND form_name=${q(name)} AND form_html=${q(html)}`)==='1','Source template changed');
    sql.execute(`DELETE FROM eform WHERE fid=${sourceId} AND form_name=${q(name)}`);
    for(const a of assets)retireOwnedFile(path.join(root,a.name),bytes=>bytes.equals(a.bytes));
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${q(name)}`)==='0'&&assets.every(a=>!fs.existsSync(path.join(root,a.name))),'Source resources remain and could conceal an incomplete import');
    unchanged();
    await h.gotoApp(manager,s.config.baseUrl,'/eform/efmformmanager');
    await manager.locator('#eformOptions a[href="#import"]').click();
    const frame=manager.frameLocator('#importFrame');
    await frame.locator('#zippedForm').setInputFiles({name:name+'.zip',mimeType:'application/zip',buffer:exported.bytes});
    const [response]=await Promise.all([manager.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/eform/manageEForm')),
      frame.locator('input.upload[type="submit"]').click()]);
    const body=await response.text();h.assert(response.status()===200&&body.includes('Your eform was imported.'),'Native import did not report success');
    importId=sql.value(`SELECT fid FROM eform WHERE form_name=${q(name)}`);h.assert(/^[1-9]\d*$/.test(importId)&&importId!==sourceId,'Import did not create a new template');fids.add(importId);
    h.assert(sql.value(`SELECT form_html FROM eform WHERE fid=${importId}`)===html,'Imported template HTML changed');
    for(const a of assets)h.assert(readRegular(path.join(root,a.name)).bytes.equals(a.bytes),'Imported image bytes changed');
    unchanged();
  });
  const chart=await s.chart(),list=await s.popup(chart,chart.locator('#menuTitleeforms a').first(),'ZIP-chart-eform-list');
  let form=await s.popup(list,list.locator('#efmTable a').filter({hasText:name}),'ZIP-imported-form');
  const note=marker+'IMPORTED',subject=marker+' saved ZIP';let fdid;
  async function images(page) {
    for(const a of assets) {
      const img=page.locator('#'+a.kind);
      await img.evaluate(image=>image.decode());
      h.assert(await img.evaluate((image,size)=>image.complete&&image.naturalWidth===size[0]&&image.naturalHeight===size[1],a.kind==='background'?[600,800]:[100,60]),'Imported '+a.kind+' does not decode');
      const response=await page.context().request.get(await img.evaluate(image=>image.src));
      try{h.assert(response.status()===200&&(await response.body()).equals(a.bytes),'Application did not serve the exact imported '+a.kind+' image');}finally{await response.dispose();}
    }
  }
  await s.step('chart-launched imported form saves both images and exact owned clinical value',async()=>{
    await form.locator('#remoteDownloadButton').waitFor();await images(form);
    h.assert(new URL(form.url()).searchParams.get('fid')===importId&&new URL(form.url()).searchParams.get('demographic_no')===patient,'Chart opened another imported form/patient');
    await form.locator('#ownedNote').fill(note);await form.locator('#remote_eform_subject').fill(subject);
    const download=await x.saveDownload(form,scratch,()=>form.locator('#remoteDownloadButton').click(),{timeout:120000});
    await expectValue(sql,`SELECT COUNT(*) FROM eform_data WHERE fid=${importId} AND demographic_no=${patient}`,'1','Native ZIP form save did not create exactly one instance');
    fdid=sql.value(`SELECT fdid FROM eform_data WHERE fid=${importId} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_values WHERE fdid=${fdid} AND fid=${importId} AND demographic_no=${patient} AND var_name='ownedNote' AND var_value=${q(note)}`)==='1','Imported form clinical value was not stored exactly');
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${fdid} AND form_name=${q(name)} AND subject=${q(subject)} AND form_provider=${q(provider)}`)==='1','Imported saved instance attribution changed');
    verifyPdf(download.bytes,note);
  });
  await s.step('native saved-instance reopening retains entered text and both restored image bindings',async()=>{
    if(!form.isClosed())await form.close();
    const saved=await s.popup(chart,chart.locator('a[onclick*="/eform/efmpatientformlist?"]').first(),'ZIP-saved-list');
    const row=saved.locator('#efmTable tbody tr').filter({hasText:name});
    h.assert(await row.count()===1,'Saved ZIP instance list identity is ambiguous');
    form=await s.popup(saved,row.locator('a[onclick*="efmshowform_data?"]').filter({hasText:name}),'ZIP-reopened-instance');
    h.assert(new URL(form.url()).searchParams.get('fdid')===fdid&&await form.locator('#ownedNote').inputValue()===note,'Reopened ZIP instance lost identity/value');
    await images(form);unchanged();
  });
}

if(require.main===module)runWorkflow(NAME,workflow,{preflightConfig:()=>{
  if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Image ZIP lifecycle requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1');
  for(const root of [imageRoot(),renderRoot()]) {
    if(!fs.existsSync(root))throw new h.SkipCheck('Installed eForm image/renderer directory is required');
    h.assert(path.isAbsolute(root)&&fs.realpathSync(root)===root&&fs.lstatSync(root).isDirectory(),'eForm fixture store must be a real absolute directory');
  }
  requirePoppler('pdfinfo','pdftotext','pdftoppm');
}});
module.exports={asset,verifyArchive,verifyRaster,verifyPdf,workflow};
