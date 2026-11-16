#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
// #5809: keep the standalone geometry check, and independently inspect the real
// Download PDFs from both a new form and its saved instance on the installed app.
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const {execFileSync}=require('node:child_process');
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const x=require('./lib/export-content-helpers');
const graph=require('./lib/pdf-graph');
const {requirePoppler}=require('./lib/stored-pdf-documents');
const {serverLogFromEnvironment}=require('./lib/server-log');
const {bundleMessage}=require('./lib/throwaway-login-fixture');
const {readRegular,retireOwnedFile}=require('./lib/owned-file-retirement');
const q=h.sqlString;
const SHAPES=['clean','wrapped','direct'];
const MARKS=[['TOP',20,20],['RIGHT',630,20],['LOWER',20,900],['CORNER',630,900],['CLINICAL',250,450]];
const rendererTemp=()=>process.env.EFORM_RENDER_TEMP_DIR||'/var/lib/carlos-emr/catalina/work/carlos/eform-browser-pdf-temp';

/** Exactly one fresh successful completion for the selected two-page revision. */
function completedRender(log,fdid) {
  h.assert(/^[1-9]\d*$/.test(fdid),'Invalid saved-eForm completion identity');
  const receipts=[...log.matchAll(/Browser eForm renderer completed: fdid=([1-9]\d*) pages=([1-9]\d*) bytes=([1-9]\d*)(?= |$)/g)]
    .filter(match=>match[1]===fdid);
  return receipts.length===1&&receipts[0][2]==='2';
}

// Saved-eForm PDFs have one renderer and no fallback (EformDataManagerImpl renders only through
// EFormBrowserPdfService), and an unattached packet is delivered byte-for-byte as Chromium's
// Page.printToPDF wrote it, so the PDF's own Producer names the renderer on every install.
const BROWSER_PRODUCER=/^Skia\/PDF m[1-9]\d*$/;

/** The Producer line of pdfinfo output ('' when the PDF carries none). */
function producerOf(pdfinfoOutput) {
  const found=/^Producer:[ \t]*(.*)$/m.exec(pdfinfoOutput);
  return found?found[1].trim():'';
}

/** The Producer the downloaded PDF itself declares. */
function pdfProducer(bytes) {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-eform-producer-'));
  try {
    const file=path.join(dir,'in.pdf');fs.writeFileSync(file,bytes);
    return producerOf(execFileSync('pdfinfo',['-enc','UTF-8',file],{encoding:'utf8',timeout:30000}));
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
}

/** Whether the renderer logged its INFO start line for this saved instance (installs at INFO or finer). */
function renderLoggedAtInfo(log,fdid) {
  return log.split('\n').some(line=>/Browser eForm renderer starting: fdid=([1-9]\d*)(?= |$)/.exec(line)?.[1]===fdid);
}

/**
 * The browser renderer produced this download. The PDF's Producer is required on every install.
 * The INFO completion receipt is required too when the server log carries the renderer's INFO
 * lines; a default install logs at WARN and never writes them, which is no evidence either way.
 */
function rendererConfirmed({producer,log,fdid}) {
  if(!BROWSER_PRODUCER.test(producer))return false;
  return !renderLoggedAtInfo(log||'',fdid)||completedRender(log,fdid);
}

/** Fixed authored geometry, independent of the production geometry JavaScript. */
function fixture(shape,marker) {
  h.assert(SHAPES.includes(shape),'Unknown eForm fixture shape');
  h.assert(/^FAKE-PW[a-f0-9]{16}$/.test(marker),'Invalid page-exclusion fixture marker');
  const page=n=>`<div id="page${n}" style="position:relative;width:720px;height:940px;page-break-after:${n===1?'always':'auto'}">`
    // The renderer sizes paper to visible content. A visible full-page outline
    // defines that extent without an external image or clinic-wide image fixture.
    +'<div style="position:absolute;left:0;top:0;width:720px;height:940px;box-sizing:border-box;border:1px solid #555"></div>'
    +MARKS.map(([label,left,top])=>`<span style="position:absolute;left:${left}px;top:${top}px;font:8px/10px Arial;color:black">P${n}${label}</span>`).join('')+'</div>';
  const interstitial=shape==='clean'?'':'<div id="owned-interstitial" data-carlos-print-decoration="true" style="height:90px">NONPAGEINTERSTITIAL</div>';
  const pages=page(1)+interstitial+page(2);
  const form='<form method="post" action="" name="FormName" id="FormName">';
  return '<html><head><title>'+marker+' page exclusion '+shape+'</title><script>var needToConfirm=false;function releaseDirtyFlag(){needToConfirm=false;}</script></head><body>'
    +(shape==='direct'?form+'</form>'+pages:form+pages+'</form>')+'</body></html>';
}

/** Recover a lost download acknowledgment by its marker title, never by age alone. */
function rendererFileFixture(root,titles,{titleOf=graph.pdfTitle}={}) {
  h.assert(path.isAbsolute(root)&&fs.realpathSync(root)===root,'Renderer temp store must be an absolute real directory');
  const identity=fs.lstatSync(root);
  h.assert(identity.isDirectory()&&!identity.isSymbolicLink(),'Renderer temp store is not a real directory');
  const before=new Set(fs.readdirSync(root));
  return {cleanup(){
    const current=fs.lstatSync(root);
    h.assert(current.isDirectory()&&current.dev===identity.dev&&current.ino===identity.ino,'Renderer temp store changed; retain its contents');
    const failures=[];let removed=0;
    for(const name of fs.readdirSync(root)) {
      if(before.has(name)||!name.startsWith('eform-browser-render-'))continue;
      try {
        h.assert(/^eform-browser-render-\d+\.pdf$/.test(name),'Unfamiliar new renderer artifact; retain it');
        const file=path.join(root,name),observed=readRegular(file);
        if(!observed)continue;
        h.assert(titles.includes(titleOf(observed.bytes)),'New renderer artifact lacks an owned marker title; retain it');
        retireOwnedFile(file,bytes=>bytes.equals(observed.bytes)&&titles.includes(titleOf(bytes)));removed++;
      }catch(error){failures.push(error);}
    }
    if(failures.length)throw new AggregateError(failures,'Renderer fixture cleanup failed: '+failures.map(error=>error.message).join('; '));
    h.assert(fs.readdirSync(root).every(name=>before.has(name)||!name.startsWith('eform-browser-render-')),'New owned renderer artifacts remain');
    console.log('    Removed '+removed+' owned server-rendered PDF fixture(s); preexisting files retained');
  }};
}

/** Poppler grayscale raster at 72 dpi: actual ink, separate from text extraction. */
function raster(bytes,page) {
  const image=execFileSync('pdftoppm',['-gray','-r','72','-f',String(page),'-l',String(page),'-singlefile','-'],
    {input:bytes,timeout:30000,maxBuffer:4*1024*1024});
  const header=/^P5\s+(\d+)\s+(\d+)\s+255\s/.exec(image.subarray(0,100).toString('latin1'));
  h.assert(header,'Deployed eForm PDF has no grayscale raster');
  const width=Number(header[1]),height=Number(header[2]),pixels=image.subarray(header[0].length);
  h.assert(width>0&&height>0&&width*height===pixels.length,'Deployed eForm PDF raster is incomplete');
  return {width,height,pixels};
}

/** Exactly two ordered, complete pages at the CSS-pixel-to-PDF-point scale. */
function verify(pages,images) {
  h.assert(pages.length===2&&images.length===2,'Deployed eForm PDF must contain exactly two pages');
  const boxes=[];
  for(const [index,page] of pages.entries()) {
    h.assert(Math.abs(page.width-540)<1&&Math.abs(page.height-705)<1,'Deployed eForm PDF changed authored paper dimensions: '+page.width+' x '+page.height+' pt');
    h.assert(page.words.length===MARKS.length,'Deployed eForm PDF has extra or missing content, including non-page interstitial');
    const image=images[index];
    h.assert(image.pixels.length===image.width*image.height,'Deployed eForm PDF raster bytes are incomplete');
    for(const [label,left,top] of MARKS) {
      const text=`P${index+1}${label}`,words=page.words.filter(word=>word.text===text);
      h.assert(words.length===1,'Missing, duplicated or wrong-page eForm clinical content: '+text);
      const word=words[0];
      h.assert(Math.abs(word.xMin-left*0.75)<=1&&Math.abs(word.yMin-top*0.75)<=3,'Shifted eForm clinical content: '+text);
      h.assert(word.xMin>=0&&word.yMin>=0&&word.xMax<=page.width&&word.yMax<=page.height,'Clipped eForm clinical content: '+text);
      let ink=0;
      for(let y=Math.max(0,Math.floor(word.yMin*image.height/page.height));y<Math.min(image.height,Math.ceil(word.yMax*image.height/page.height));y++)
        for(let x=Math.max(0,Math.floor(word.xMin*image.width/page.width));x<Math.min(image.width,Math.ceil(word.xMax*image.width/page.width));x++)
          if(image.pixels[y*image.width+x]<200)ink++;
      h.assert(ink>3,'Invisible eForm clinical content has no actual rendered ink: '+text);
      boxes.push({text,x:word.xMin,y:word.yMin,right:word.xMax,bottom:word.yMax});
    }
  }
  return boxes;
}

/** Reserve past dangling legacy fdid references; none of those rows is ours. */
function reserveInstanceIds(s,template) {
  const refs=s.sql.rows(`SELECT DISTINCT c.TABLE_NAME,c.COLUMN_NAME FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
    WHERE c.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE' AND LOWER(c.COLUMN_NAME)='fdid'
    UNION SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='eform_data' AND REFERENCED_COLUMN_NAME='fdid'`);
  h.assert(refs.length>0,'Cannot inventory saved-eForm identity references');
  let maximum=0;
  for(const [table,column] of refs) {
    h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(table)&&/^[A-Za-z_][A-Za-z0-9_]*$/.test(column),'Unfamiliar saved-eForm reference identifier');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM \`${table}\` WHERE \`${column}\` IS NOT NULL
      AND CAST(\`${column}\` AS CHAR) NOT REGEXP '^(-1|[0-9]+)$'`)==='0','Unfamiliar saved-eForm reference value; no identity reserved');
    const value=Number(s.sql.value(`SELECT GREATEST(COALESCE(MAX(CAST(\`${column}\` AS SIGNED)),0),0) FROM \`${table}\``));
    h.assert(Number.isSafeInteger(value)&&value>=0&&value<2147483646,'No safe saved-eForm identity range');
    maximum=Math.max(maximum,value);
  }
  const next=Number(s.sql.value(`SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='eform_data'`));
  h.assert(Number.isSafeInteger(next)&&next>0&&next<2147483647,'Cannot read a usable saved-eForm auto increment');
  if(next>maximum)return;
  // Cleanup was registered by marker before this INSERT, including a lost acknowledgment.
  const seed=maximum+1;
  s.sql.execute(`INSERT INTO eform_data(fdid,fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,
    form_data,showLatestFormOnly,patient_independent,roleType)
    VALUES(${seed},${template.id},${q(template.name)},${q(s.marker+'-IDSEED')},${s.patient},1,CURDATE(),CURTIME(),${q(s.provider)},'',0,0,'')`);
  s.sql.execute(`DELETE FROM eform_data WHERE fdid=${seed} AND fid=${template.id} AND form_name=${q(template.name)} AND demographic_no=${s.patient}`);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${seed}`)==='0','Saved-eForm identity sentinel was not removed');
}

async function workflow(s) {
  const templates=SHAPES.map(shape=>({shape,name:s.marker+' page exclusion '+shape}));
  // Register durable marker ownership before any template INSERT/ID allocation.
  s.cleanup(()=>{
    const failures=[];
    for(const template of templates)try {
      const ids=s.sql.rows(`SELECT fid FROM eform WHERE form_name=${q(template.name)}`).flat();
      h.assert(ids.length<=1&&ids.every(id=>/^[1-9]\d*$/.test(id)),'Ambiguous owned page-exclusion template');
      if(!ids.length) {
        h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE form_name=${q(template.name)}`)==='0','Template disappeared while saved instances remain');
        continue;
      }
      const fid=ids[0];
      h.assert(!template.id||template.id===fid,'Page-exclusion template identity changed');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE (fid=${fid} OR form_name=${q(template.name)})
        AND NOT (fid=${fid} AND form_name=${q(template.name)} AND demographic_no=${s.patient})`)==='0','Saved-eForm ownership changed');
      const fdids=s.sql.rows(`SELECT fdid FROM eform_data WHERE fid=${fid} AND demographic_no=${s.patient}`).flat();
      h.assert(fdids.every(id=>/^[1-9]\d*$/.test(id)),'Invalid owned saved-eForm identity');
      if(fdids.length) {
        const list=fdids.join(',');
        h.assert(s.sql.value(`SELECT COUNT(*) FROM EFormDocs WHERE fdid IN (${list})`)==='0','Unexpected attachments: retain instance for reconciliation');
        s.sql.execute(`DELETE FROM eform_values WHERE fdid IN (${list})`);
        h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_values WHERE fdid IN (${list})`)==='0','Owned saved-eForm values remain');
        s.sql.execute(`DELETE FROM eform_data WHERE fdid IN (${list}) AND demographic_no=${s.patient} AND fid=${fid}`);
      }
      h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${fid} OR form_name=${q(template.name)}`)==='0','Owned saved eForm remains');
      s.sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(template.name)}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${fid} OR form_name=${q(template.name)}`)==='0','Owned eForm library fixture remains');
    }catch(error){failures.push(new Error(template.shape+': '+error.message,{cause:error}));}
    try{h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${s.patient}`)==='0','Unexpected saved-eForm rows remain on the owned patient');}
    catch(error){failures.push(error);}
    if(failures.length)throw new AggregateError(failures,'Page-exclusion fixture cleanup failed: '+failures.map(error=>error.message).join('; '));
  });
  const scratch=x.scratchDir();s.cleanup(()=>fs.rmSync(scratch,{recursive:true,force:true}));
  const rendererFiles=rendererFileFixture(rendererTemp(),templates.map(template=>template.name));
  s.cleanup(()=>rendererFiles.cleanup());
  const reader=serverLogFromEnvironment();
  const chart=await s.chart();
  for(const template of templates) {
    template.id=s.sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
      showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${q(template.name)},'',${q(template.name)},CURDATE(),CURTIME(),${q(s.provider)},1,${q(fixture(template.shape,s.marker))},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(template.id),'Owned page-exclusion template was not created');
  }
  reserveInstanceIds(s,templates[0]);
  // Native Add uses a named window. Open it once after all templates exist and
  // retain it; closing/reopening it also has an unrelated unload regression.
  const list=await s.popup(chart,chart.locator('#menuTitleeforms a').first(),'page-exclusion-add-list');
  let control,patientList;
  for(const template of templates) {
    let form=await s.popup(list,list.locator('#efmTable a').filter({hasText:template.name}),'page-exclusion-new-'+template.shape);
    let fdid,originalRow;
    for(const stage of ['new','saved'])await s.step(template.shape+' '+stage+': native server PDF keeps both complete clinical pages and excludes only decoration',async()=>{
      if(stage==='saved') {
        if(!form.isClosed())await form.close();
        if(!patientList)patientList=await s.popup(chart,chart.locator('a[onclick*="/eform/efmpatientformlist?"]').first(),'page-exclusion-patient-list');
        else await patientList.reload({waitUntil:'load'});
        const row=patientList.locator('#efmTable tbody tr').filter({hasText:template.name});
        h.assert(await row.count()===1,'Patient eForm list must show one owned saved instance');
        const link=row.locator('a[onclick*="efmshowform_data?"]').filter({hasText:template.name});
        h.assert((await link.getAttribute('onclick')).includes('fdid='+fdid+'&'),'Native patient-list link names another saved instance');
        form=await s.popup(patientList,link,'page-exclusion-saved-'+template.shape);
        h.assert(new URL(form.url()).searchParams.get('fdid')===fdid,'Native saved eForm opened the wrong instance');
      }
      await form.locator('#remoteDownloadButton').waitFor({state:'visible'});
      const topology=await form.locator('#page1').evaluate(node=>({parent:node.parentElement.tagName,form:node.closest('form')?.id||null}));
      h.assert(template.shape==='direct'?topology.parent==='BODY'&&topology.form===null:topology.form==='FormName',
        'The native '+stage+' eForm did not preserve the requested '+template.shape+' page topology: '+JSON.stringify(topology));
      if(stage==='new')await form.locator('#remote_eform_subject').fill(template.name);
      const mark=reader.mark();
      let approvedFdid;
      const downloaded=await x.saveDownload(form,scratch,async()=>{
        await form.locator('#remoteDownloadButton').click();
        if(template.shape!=='clean') {
          // An interstitial is BLOCKING even when marked as decoration: only
          // pre/post-page decorations are advisory. Review the exact omission
          // and approve through the native one-time download form, without save.
          const approval=form.locator('form[action*="/eform/downloadEFormPdf"]');
          await approval.waitFor({state:'visible'});
          approvedFdid=await approval.locator('input[name="fdid"]').inputValue();
          h.assert(/^[1-9]\d*$/.test(approvedFdid)&&s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${approvedFdid}
            AND fid=${template.id} AND demographic_no=${s.patient}`)==='1','Approval is not bound to the exact owned saved revision');
          h.assert(await approval.locator('input[name="demographicNo"]').inputValue()===s.patient
            &&await approval.locator('input[name="renderApproval"]').inputValue(),'Native approval lacks its patient binding or one-time token');
          const items=await form.locator('.card-body > ul > li').allTextContents();
          for(const key of ['failedContentResources','excludedContentElements','signatureMissing','providerStampMissing','timerCompatibilityFailure',
            'severeConsoleErrors','containedInteractions','stabilizationCapped','labDecisionSupportStubbed']) {
            const label=bundleMessage('eform.renderIssue.'+key,key);
            const item=items.filter(text=>text.trim().startsWith(label+':'));
            const value=key==='excludedContentElements'?'1':['failedContentResources','severeConsoleErrors','containedInteractions'].includes(key)?'0':'false';
            h.assert(item.length===1&&item[0].trim()===label+': '+value,'Native approval lists an unexpected or missing omission: '+key);
          }
          const savedBefore=s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${template.id} AND demographic_no=${s.patient}`);
          await approval.locator('button[type="submit"]').click();
          h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${template.id} AND demographic_no=${s.patient}`)===savedBefore,'Approving PDF output created another saved revision');
        }
      },{timeout:120000});
      h.assert(downloaded.bytes.subarray(0,5).toString()==='%PDF-','Native eForm download is not a PDF');
      // Submit & PDF saves a new revision even when opened from a saved instance.
      // Preserve the original revision and require exactly one new one per click.
      const expected=stage==='new'?'1':'2';
      await expectValue(s.sql,`SELECT COUNT(*) FROM eform_data WHERE fid=${template.id} AND demographic_no=${s.patient}`,expected,'Native PDF download did not save exactly one revision');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${template.id} AND demographic_no=${s.patient}
        AND status=1 AND form_name=${q(template.name)} AND subject=${q(template.name)} AND form_provider=${q(s.provider)}`)===expected,'Saved PDF revision changed ownership, subject or status');
      const actual=s.sql.value(`SELECT MAX(fdid) FROM eform_data WHERE fid=${template.id} AND demographic_no=${s.patient}`);
      h.assert(!approvedFdid||actual===approvedFdid,'PDF approval rendered another saved revision');
      if(stage==='saved') {
        h.assert(actual!==fdid,'Saved-instance PDF did not create its new revision');
        h.assert(JSON.stringify(s.sql.rows(`SELECT * FROM eform_data WHERE fdid=${fdid}`))===JSON.stringify(originalRow),'Saved-instance PDF altered its original revision');
      }else originalRow=s.sql.rows(`SELECT * FROM eform_data WHERE fdid=${actual}`);
      fdid=actual;
      const producer=pdfProducer(downloaded.bytes);
      h.assert(BROWSER_PRODUCER.test(producer),'Native eForm download was not written by the browser renderer: Producer '+JSON.stringify(producer));
      let log='';
      if(reader.configured) {
        // The journal can lag the download by a moment. Without the renderer's INFO start line the
        // install logs at WARN and no receipt will come; with it, wait for the completion receipt.
        const deadline=Date.now()+10000,quiet=Date.now()+2000;
        do {
          log=reader.since(mark);
          if(rendererConfirmed({producer,log,fdid})&&(renderLoggedAtInfo(log,fdid)||Date.now()>=quiet))break;
          await new Promise(resolve=>setTimeout(resolve,200));
        }while(Date.now()<deadline);
      }
      h.assert(rendererConfirmed({producer,log,fdid}),'The saved-eForm browser renderer logged this instance but did not confirm both authored pages');
      const pages=Array.from({length:graph.pageCount(downloaded.bytes)},(_,index)=>graph.pageWords(downloaded.bytes,index+1));
      console.log('    PDF '+template.shape+' '+stage+': '+JSON.stringify(pages.map(page=>({width:page.width,height:page.height,words:page.words}))));
      const boxes=verify(pages,pages.map((_,index)=>raster(downloaded.bytes,index+1)));
      if(!control)control=boxes;
      for(let i=0;i<boxes.length;i++)for(const key of ['x','y','right','bottom'])
        h.assert(Math.abs(boxes[i][key]-control[i][key])<=0.3,'Interstitial or saved rendering shifted clinical content relative to the clean control');
    });
    if(!form.isClosed())await form.close();
  }
}

if(require.main===module)runWorkflow('eform-deployed-page-exclusion',workflow,{preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Owned eForm library/identity allocation requires EXCLUSIVE=1');
  // Optional: with a readable server log at INFO the renderer's own completion receipt is checked too.
  const reader=serverLogFromEnvironment();
  if(reader.configured)reader.since(reader.mark());
  requirePoppler('pdfinfo','pdftotext','pdftoppm');
  if(!fs.existsSync(rendererTemp()))throw new h.SkipCheck('A readable installed renderer temp store is required; set EFORM_RENDER_TEMP_DIR for a non-default Catalina base');
  h.assert(fs.lstatSync(rendererTemp()).isDirectory()&&fs.realpathSync(rendererTemp())===rendererTemp(),'Renderer temp store must be a real directory');
  fs.readdirSync(rendererTemp());
}});
module.exports={fixture,verify,reserveInstanceIds,rendererFileFixture,completedRender,producerOf,rendererConfirmed,workflow};
