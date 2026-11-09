/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const h=require(root+'/scripts/lib/playwright-harness'),ui=require(root+'/scripts/lib/playwright-ui');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {workflow:uploadFixture,uploadThroughPopup}=require(root+'/scripts/lab-upload-playwright-checks');
const {versionMessage,VERSIONS}=require(root+'/scripts/lab-version-ingestion-playwright-checks');
const {openInbox,openSidebar,setMode,search}=require(root+'/scripts/inbox-file-forward-playwright-checks');
const q=h.sqlString;
function original(accession,last){
 const lines=versionMessage(accession,last,VERSIONS.original).split('\r');
 const second=lines.find(line=>line.startsWith('OBX|')).split('|');Object.assign(second,{1:'2',3:'7020^SYNTHETIC SECOND RESULT',5:'9.2',7:'3-20',8:'N'});
 lines.splice(lines.findIndex(line=>line.startsWith('FTS|')),0,second.join('|'));return lines.join('\r');
}
async function settled(page){await page.waitForLoadState('load');try{await page.waitForLoadState('networkidle',{timeout:5000});}catch(error){if(error.name!=='TimeoutError')throw error;}}
async function eventually(check,timeout=8000){const deadline=Date.now()+timeout;while(Date.now()<deadline){if(await check())return true;await new Promise(resolve=>setTimeout(resolve,100));}return false;}
async function workflow(s){
 h.assert(process.env.EXCLUSIVE==='1'&&process.env.CARLOS_DISPOSABLE_VM==='true','Requires an exclusive disposable installed database');
 const output=process.env.CML_INBOX_SCREENSHOT_DIR;h.assert(output&&path.isAbsolute(output),'Absolute screenshot directory required');fs.mkdirSync(output,{recursive:true});
 const captures=[],defects=[],results=[];
 const record=(okay,message)=>{if(!okay){defects.push(message);console.log('  FAIL clinical inbox assertion: '+message);}};
 async function capture(page,label){await settled(page);await page.locator('#oscar-spinner-screen').waitFor({state:'hidden',timeout:15000});await page.locator('#oscar-spinner').waitFor({state:'hidden',timeout:15000});await page.locator('#loadingLabel').waitFor({state:'hidden',timeout:15000});await page.locator('#loadInboxListProgress').waitFor({state:'hidden',timeout:15000});const image=String(captures.length+1).padStart(3,'0')+'-'+label+'.png',file=path.join(output,image);await page.screenshot({path:file,fullPage:true});captures.push({image,label,route:new URL(page.url()).pathname,browser:s.context.browser().version(),sha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')});fs.writeFileSync(path.join(output,'captures.json'),JSON.stringify(captures,null,2)+'\n');}
 await s.chart();
 await uploadFixture(s,{initialContent:original,afterInitial:async source=>{
  const ids=()=>s.sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(source.accession)} ORDER BY lab_no`).flat();const first=ids()[0];
  const status=id=>s.sql.value(`SELECT status FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${id} AND provider_no=${q(s.provider)}`);
  s.cleanup(()=>{const owned=ids().map(q).join(',');if(owned)s.sql.execute(`DELETE FROM log WHERE content='lab' AND demographic_no=${s.patient} AND contentId IN (${owned})`);});
  for(const [index,mode] of ['list','preview'].entries()){
   const version={...VERSIONS.corrected,key:'PARTIAL-'+mode.toUpperCase(),time:index?'20260310130000':VERSIONS.corrected.time,value:index?'13.2':VERSIONS.corrected.value};
   fs.writeFileSync(source.filePath,Buffer.from(versionMessage(source.accession,s.marker,version),'latin1'));
   h.assert(await uploadThroughPopup(s,source.filePath,source.fileName)==='Uploaded successfully','Native partial source upload failed');const partial=ids().at(-1);
   if(s.schedule.isClosed())s.schedule=await s.context.newPage();if(s.schedule.url()!==s.scheduleUrl)await s.schedule.goto(s.scheduleUrl,{waitUntil:'load'});
   const inbox=await openInbox(s.schedule,s.context,s.recorder,'cml-'+mode+'-live-inbox');await setMode(inbox,'list');await openSidebar(inbox);
   await inbox.locator('#statusNew').check();await inbox.locator('#specificPatients').check();await inbox.locator('#inputLastName').fill(s.marker);await search(inbox);
   await inbox.locator('#rapidReviewToggle').check();if(mode==='preview')await setMode(inbox,'preview');
   if(mode==='preview'){
    const area=inbox.locator('#inboxViewItems');
    for(let attempt=0;attempt<5&&await inbox.evaluate(()=>hasMoreData);attempt++){
     const box=await area.boundingBox();await inbox.mouse.move(box.x+box.width-3,box.y+Math.min(box.height-5,100));await inbox.mouse.wheel(0,16000);await new Promise(resolve=>setTimeout(resolve,500));await settled(inbox);
    }
   }
   console.log('PRECONDITION '+JSON.stringify(await inbox.evaluate(()=>({hasMoreData,isFetchingData,rapidReviewState}))));
   h.assert(await inbox.evaluate(()=>!hasMoreData&&!isFetchingData&&rapidReviewState),'Requires a fully loaded inbox with Rapid Review enabled');
   let report,frame,control;
   if(mode==='list'){
    const row=inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${partial}"]`);await row.waitFor({state:'visible'});
    report=await ui.clickOpensPopup(inbox,row.locator('a[onclick*="reportWindow"]').first(),{context:s.context,recorder:s.recorder,label:'cml-live-list-partial',timeout:30000});await settled(report);frame=report;
   }else{
    const card=inbox.locator(`.document-card[data-lab-type="HL7"][data-segment-id="${partial}"]`);await card.waitFor({state:'visible'});await card.scrollIntoViewIfNeeded();
    const handle=await card.locator('iframe').first().elementHandle();frame=await handle.contentFrame();h.assert(frame,'Native preview iframe not available');report=inbox;
    await frame.locator('form[id="acknowledgeForm_'+partial+'"]').waitFor({state:'attached'});await settled(frame);await frame.locator('#oscar-spinner').waitFor({state:'hidden'});await settled(report);
   }
   control=frame.locator('input[onclick*="ackLab"],button[onclick*="ackLab"]').first();await control.waitFor({state:'visible'});
   await capture(report,mode+'-partial-before-ack');
   const badgeBefore=Number(await inbox.locator('#totalLabsCount').inputValue());
   const messageBefore=s.sql.rows(`SELECT lab_id,message FROM hl7TextMessage WHERE lab_id IN (${ids().join(',')}) ORDER BY lab_id`);
   await h.withExpectedDialogs(report,async()=>{
    const [response]=await Promise.all([report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus')),control.click()]);h.assert(response.status()===200,'Native partial acknowledgment failed');
   });
   const selector=mode==='list'?`tr[data-lab-type="HL7"][data-segment-id="${first}"]`:`.document-card[data-lab-type="HL7"][data-segment-id="${first}"]`;
   // Do not accept an older row left momentarily on screen before its asynchronous
   // acknowledgment notification has started the authoritative re-fetch.
   await eventually(async()=>badgeBefore-Number(await inbox.locator('#totalLabsCount').inputValue())===1
       && !await inbox.locator('#oscar-spinner-screen').isVisible()
       && await inbox.evaluate(()=>!isFetchingData));
   const retained=await eventually(async()=>await inbox.locator(selector).isVisible().catch(()=>false)
       && !await inbox.locator('#oscar-spinner-screen').isVisible());
   if(mode==='preview'&&retained){
    const handle=await inbox.locator(selector).locator('iframe').first().elementHandle();const retainedFrame=await handle.contentFrame();h.assert(retainedFrame,'Retained preview iframe missing');
    await retainedFrame.locator('form[id="acknowledgeForm_'+first+'"]').waitFor({state:'attached'});await settled(retainedFrame);
    await retainedFrame.locator('#oscar-spinner').waitFor({state:'hidden'});
    h.assert((await retainedFrame.locator('body').innerText()).includes('9.2'),'Retained preview did not render its omitted observation');
   }
   await capture(inbox,mode+'-live-inbox-after-ack');
   record(retained,mode+': unread original disappeared until manual refresh');
   const badgeAfter=Number(await inbox.locator('#totalLabsCount').inputValue());
   record(Number.isFinite(badgeBefore)&&badgeBefore-badgeAfter===1,mode+': server-cleared routing row was not counted exactly once');
   h.assert(status(first)==='N'&&status(partial)==='A','Partial acknowledgment incorrectly changed review ownership');
   h.assert(JSON.stringify(messageBefore)===JSON.stringify(s.sql.rows(`SELECT lab_id,message FROM hl7TextMessage WHERE lab_id IN (${ids().join(',')}) ORDER BY lab_id`)),'Review changed raw source bytes');
   let advanced=false;
   if(mode==='list'){
    let next;
    advanced=await eventually(async()=>{for(const page of s.context.pages())if(page!==inbox&&!page.isClosed()&&await page.locator('form[id="acknowledgeForm_'+first+'"]').count().catch(()=>0)){next=page;return true;}return false;},retained?8000:1000);
    record(advanced,'list: Rapid Review skipped the retained original');if(next){await settled(next);await next.close();}
   }else if(retained){
    advanced=await inbox.locator(selector).evaluate(node=>{const box=node.getBoundingClientRect();return box.bottom>0&&box.top<window.innerHeight;});
    record(advanced,'preview: Rapid Review did not bring the retained source into view');
   }
   results.push({mode,partial,retained,rapidReview:advanced,originalRemainsNew:true,badgeBefore,badgeAfter});
   for(const page of new Set([report,inbox]))if(page!==s.schedule&&!page.isClosed()){await settled(page);await page.close();}
  }
  console.log('EVIDENCE '+JSON.stringify({sourceVersions:ids().length,results,manualRefreshesAfterAck:0}));
 }});
 h.assertStrictPage(s.recorder);h.assert(defects.length===0,defects.join('; '));
}
runWorkflow('cml-live-inbox-retention',workflow);
