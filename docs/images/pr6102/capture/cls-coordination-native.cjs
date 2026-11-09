/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos',fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const h=require(root+'/scripts/lib/playwright-harness');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {workflow:uploadFixture,openUploader}=require(root+'/scripts/lab-upload-playwright-checks');
const {versionMessage,VERSIONS}=require(root+'/scripts/lab-version-ingestion-playwright-checks');
const {createHl7ChainLockFixture}=require(root+'/scripts/lib/lab-routing-fixture');
const {fence,settleWrites}=require('./mysql-row-fence.cjs');const q=h.sqlString;
async function workflow(s){
 h.assert(process.env.EXCLUSIVE==='1'&&process.env.CARLOS_DISPOSABLE_VM==='true','Requires an exclusive disposable database');
 await s.chart();
 await uploadFixture(s,{initialContent:(acc,last)=>versionMessage(acc,last,VERSIONS.original),afterInitial:async source=>{
  const clsAccession=source.accession+'CLS',controlAccession=source.accession+'CONTROL';
  const clsFixture=createHl7ChainLockFixture(query=>s.sql.execute(query),query=>s.sql.rows(query),'CLS','*',{exclusive:true});
  const controlFixture=createHl7ChainLockFixture(query=>s.sql.execute(query),query=>s.sql.rows(query),'CML',controlAccession,{exclusive:true});
  s.cleanup(()=>clsFixture.cleanup());s.cleanup(()=>controlFixture.cleanup());
  const directory=fs.mkdtempSync(path.join(path.dirname(source.filePath),'cls-')),clsFile=path.join(directory,source.fileName);
  const message=versionMessage(clsAccession,s.marker,{...VERSIONS.original,key:'CLS',value:'6.1'}).split('\r').map(line=>{
   const fields=line.split('|');
   if(fields[0]==='MSH'){fields[3]='OPEN ENGINE';fields[4]='CLS';}
   if(fields[0]==='PID')fields[5]=s.marker+', Workflow';
   if(fields[0]==='OBR'){fields[3]=clsAccession+'^SYNTHETIC';fields[4]='7010^SYNTHETIC CLS PANEL^L01N';fields[14]=VERSIONS.original.time;fields[20]=clsAccession;}
   if(fields[0]==='OBX')fields[3]='7010^SYNTHETIC CLS RESULT^L01N';return fields.join('|');
  }).join('\r');
  fs.writeFileSync(clsFile,Buffer.from(message,'latin1'));
  fs.writeFileSync(source.filePath,Buffer.from(versionMessage(controlAccession,s.marker,{...VERSIONS.original,key:'CONTROL',value:'6.2'}),'latin1'));
  const first=await openUploader(s);await first.popup.locator('#importFiles').setInputFiles(clsFile);await first.popup.locator('#type').selectOption('CLS');
  const second=await openUploader(s);await second.popup.locator('#importFiles').setInputFiles(source.filePath);await second.popup.locator('#type').selectOption('CML');
  async function submit(page){const [response]=await Promise.all([page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/lab/CA/ALL/insideLabUpload'),{timeout:60000}),page.locator('#uploadForm button[type=submit]').click()]);
   h.assert(response.status()===200,'Native source upload failed');const item=page.locator('#file-list .file-item').filter({hasText:source.fileName}).first();await item.waitFor({state:'visible'});return(await item.locator('.upload-text').innerText()).trim();}
  const key=crypto.createHash('sha256').update('3:CLS*').digest().readInt32BE(0)|0x80000000;
  s.sql.execute(`INSERT IGNORE INTO providerLabRoutingLock(lab_no) VALUES(${key})`);const held=await fence(s.config.mysql,key);
  let importing,queued=false,controlMillis,dispatchError;const started=Date.now();
  try{
   importing=submit(first.popup);importing.catch(()=>{});const deadline=Date.now()+15000;
   while(Date.now()<deadline){if(Number(s.sql.value(`SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w JOIN information_schema.INNODB_TRX b ON b.trx_id=w.blocking_trx_id WHERE b.trx_mysql_thread_id=${held.connection}`))>=1){queued=true;break;}await new Promise(resolve=>setTimeout(resolve,100));}
   h.assert(queued,'CLS ingestion did not wait on its physical global coordination row');
   const controlStart=Date.now();h.assert(await submit(second.popup)==='Uploaded successfully','Unrelated CML import blocked behind CLS coordination');controlMillis=Date.now()-controlStart;
   h.assert(controlMillis<20000,'Unrelated CML import exceeded its controlled contention bound');
  }catch(error){dispatchError=error;}finally{try{await held.release();}catch(error){dispatchError=dispatchError?new AggregateError([dispatchError,error],'Dispatch and fence release failed'):error;}}
  const [imported]=await settleWrites(dispatchError,[importing]);h.assert(imported==='Uploaded successfully','Released CLS ingestion failed');
  const elapsed=Date.now()-started;h.assert(elapsed<60000,'Controlled CLS lock latency exceeded the upload bound');
  const raw=s.sql.rows(`SELECT m.type,i.accessionNum FROM hl7TextMessage m JOIN hl7TextInfo i ON i.lab_no=m.lab_id WHERE i.accessionNum IN (${q(clsAccession)},${q(controlAccession)}) ORDER BY m.type`);
  h.assert(JSON.stringify(raw)===JSON.stringify([['CLS',clsAccession],['CML',controlAccession]]),'Native source-qualified imports were lost or misclassified');
  console.log('EVIDENCE '+JSON.stringify({clsObservedWaiting:queued,clsElapsedMillis:elapsed,unrelatedCmlMillis:controlMillis,sourceTypes:raw.map(row=>row[0])}));
  for(const page of new Set([first.popup,first.inbox,second.popup,second.inbox]))if(page!==s.schedule&&!page.isClosed())await page.close();
 }});
 h.assertStrictPage(s.recorder);
}
runWorkflow('cls-source-coordination',workflow);
