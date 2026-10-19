/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const fs=require('node:fs');const assert=require('node:assert/strict');const crypto=require('node:crypto');
const {buildArtifactPath}=require('./playwright-harness');
const sha=value=>crypto.createHash('sha256').update(value).digest('hex');
const LOOKUP='/eform/efmformapconfig_lookup';const SERVLET='/EFormApCacheForPdfGenerationServlet';
const SOURCE_KEYS=['apcacheSourceSha256','fixtureSourceSha256','helperSourceSha256','jspSourceSha256','testSourceSha256'];
function positive(value){assert.match(String(value),/^[1-9]\d*$/);return String(value);}
function resolveOwnedLookup(request,baseUrl,intent,fixture){
  const url=new URL(request.url());const base=new URL(baseUrl);const context=base.pathname.replace(/\/+$/,'');
  if(url.origin!==base.origin||url.pathname!==context+LOOKUP)return null;
  const keys=url.searchParams.getAll('key');if(!keys.includes(intent.missingKey))return null;
  assert.equal(request.method(),'GET');assert.equal(url.hash,'');assert.equal(url.username,'');assert.equal(url.password,'');assert.deepEqual(keys,[intent.missingKey]);
  assert.deepEqual(url.searchParams.getAll('oscarAPCacheLookupType'),[intent.missingKey]);
  const ids=[...url.searchParams.getAll('fid'),...url.searchParams.getAll('efmfid')];
  const patients=[...url.searchParams.getAll('demographic_no'),...url.searchParams.getAll('efmdemographic_no')];
  assert(ids.length>0&&ids.every(v=>v===fixture.negativeFid),'Owned negative lookup must use its exact imported template');
  assert(patients.length>0&&patients.every(v=>v===fixture.patient),'Owned negative lookup must use its exact FAKE patient');
  return {method:'GET',path:LOOKUP,key:intent.missingKey,fid:fixture.negativeFid,patient:fixture.patient};
}
function createApNegativeProof(directory,intent){
  assert.deepEqual(Object.keys(intent).sort(),['missingKey','negativeName','ownedMarker','runId','sourceHashes']);
  assert.match(intent.runId,/^\d{13}-\d+$/);assert.match(intent.missingKey,/^renderer_missing_ap_\d{13}$/);
  assert.equal(intent.negativeName,'Playwright APCache missing '+intent.runId);assert.match(intent.ownedMarker,/^FAKE-PW[0-9a-f]{16}$/);
  assert.deepEqual(Object.keys(intent.sourceHashes).sort(),SOURCE_KEYS);
  for(const hash of Object.values(intent.sourceHashes))assert.match(hash,/^[a-f0-9]{64}$/);
  const filename=buildArtifactPath(directory,'apcache-negative-'+intent.runId,'.json');const fd=fs.openSync(filename,'wx',0o600);
  let closed=false;let fixture=null;const pending=new Set();const errors=[];const requests=new WeakMap();
  const record={schemaVersion:1,...intent,intentWrittenAt:new Date().toISOString(),requests:[]};
  const save=()=>{assert(!closed);fs.ftruncateSync(fd,0);fs.writeSync(fd,JSON.stringify(record,null,2)+'\n',0,'utf8');fs.fsyncSync(fd);};save();
  return {filename,
    bindFixture(value){assert.equal(fixture,null);assert.deepEqual(Object.keys(value).sort(),['negativeFid','patient','templateHtmlSha256']);
      fixture={negativeFid:positive(value.negativeFid),patient:positive(value.patient),templateHtmlSha256:value.templateHtmlSha256};
      assert.match(fixture.templateHtmlSha256,/^[a-f0-9]{64}$/);record.fixture=fixture;save();},
    attach(context,baseUrl){
      context.on('request',request=>{try{if(!fixture)return;const owned=resolveOwnedLookup(request,baseUrl,intent,fixture);if(!owned)return;
        const item={...owned,startedAt:new Date().toISOString()};requests.set(request,item);record.requests.push(item);save();}catch(error){errors.push(error);}});
      context.on('response',response=>{const item=requests.get(response.request());if(!item)return;
        const operation=(async()=>{const html=await response.text();assert.equal(response.status(),200);
          const escaped=intent.missingKey.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
          assert(new RegExp('name="oscarAPCacheLookupFailures"\\s+value="'+escaped+'"').test(html),'Actual lookup response lacks its exact failure marker');
          Object.assign(item,{status:response.status(),failureMarkerPresent:true,responseSha256:sha(html),finishedAt:new Date().toISOString()});save();})()
          .catch(error=>errors.push(error));pending.add(operation);operation.finally(()=>pending.delete(operation));});
    },
    directPost(value){assert(Object.keys(value).every(k=>['startedAt','finishedAt','method','path','status'].includes(k)));record.directPost={...record.directPost,...value};save();},
    update(value){assert(Object.keys(value).every(k=>['positiveWorkflowCompleted','negativeWorkflowCompleted','browserClosed','cleanupAcknowledged'].includes(k)));Object.assign(record,value);save();},
    async drain(){await Promise.all([...pending]);assert.equal(errors.length,0,'Passive negative lookup proof failed');assert.equal(record.requests.length,2,'Exactly the two actual negative interactive lookups are required');
      assert(record.requests.every(r=>r.status===200&&r.failureMarkerPresent===true&&r.finishedAt&&r.responseSha256), 'Every actual negative lookup must have completed successfully with its failure marker');},
    close(){if(!closed){fs.closeSync(fd);closed=true;}},
  };
}
function verifyApNegativeFaultWindow(text,receipt,expectedSources,auditText){
  assert.deepEqual(Object.keys(expectedSources||{}).sort(),SOURCE_KEYS);assert.deepEqual(receipt.sourceHashes,expectedSources);
  for(const h of Object.values(expectedSources))assert.match(h,/^[a-f0-9]{64}$/);
  assert.deepEqual(Object.keys(receipt).sort(),['browserClosed','cleanupAcknowledged','directPost','fixture','intentWrittenAt','missingKey','negativeName','negativeWorkflowCompleted','ownedMarker','positiveWorkflowCompleted','requests','runId','schemaVersion','sourceHashes']);
  assert.equal(receipt.schemaVersion,1);assert.match(receipt.runId,/^\d{13}-\d+$/);assert.equal(receipt.negativeName,'Playwright APCache missing '+receipt.runId);
  assert.match(receipt.missingKey,/^renderer_missing_ap_\d{13}$/);assert.match(receipt.ownedMarker,/^FAKE-PW[0-9a-f]{16}$/);
  assert.deepEqual(Object.keys(receipt.fixture||{}).sort(),['negativeFid','patient','templateHtmlSha256']);
  positive(receipt.fixture?.patient);positive(receipt.fixture?.negativeFid);assert.match(receipt.fixture?.templateHtmlSha256||'',/^[a-f0-9]{64}$/);
  for(const field of ['positiveWorkflowCompleted','negativeWorkflowCompleted','browserClosed','cleanupAcknowledged'])assert.equal(receipt[field],true);
  assert(Number.isFinite(Date.parse(receipt.intentWrittenAt)));assert.equal(receipt.requests.length,2);
  let previous=Date.parse(receipt.intentWrittenAt);
  for(const request of receipt.requests){
    assert.deepEqual(Object.keys(request).sort(),['failureMarkerPresent','fid','finishedAt','key','method','path','patient','responseSha256','startedAt','status']);
    assert.equal(request.method,'GET');assert.equal(request.path,LOOKUP);assert.equal(request.key,receipt.missingKey);
    assert.equal(request.fid,receipt.fixture.negativeFid);assert.equal(request.patient,receipt.fixture.patient);
    assert.equal(request.status,200);assert.equal(request.failureMarkerPresent,true);assert.match(request.responseSha256,/^[a-f0-9]{64}$/);
    assert(Date.parse(request.startedAt)>=previous&&Date.parse(request.finishedAt)>=Date.parse(request.startedAt));previous=Date.parse(request.finishedAt);
  }
  const post=receipt.directPost;assert.deepEqual(Object.keys(post||{}).sort(),['finishedAt','method','path','startedAt','status']);
  assert.equal(post.method,'POST');assert.equal(post.path,SERVLET);assert([403,405].includes(post.status));
  assert(Date.parse(post.startedAt)>=previous&&Date.parse(post.finishedAt)>=Date.parse(post.startedAt));
  assert(!/HHH000099|hibernate\.AssertionFailure/.test(text));
  const events=[...String(text).matchAll(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) +ERROR ([^\n]+)(?:\n([\s\S]*?))?(?=^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3} +(?:TRACE|DEBUG|INFO|WARN|ERROR) |$(?![\s\S]))/gm)];
  const csrf=post.status===403;assert.equal(events.length,csrf?3:2,'Every ERROR must be this case exact measured negative event');
  const jspEvents=events.filter(e=>e[2].startsWith('eform.efmformapconfig_005flookup_jsp '));assert.equal(jspEvents.length,2);
  for(let i=0;i<2;i++){
    assert.match(jspEvents[i][2],/^eform\.efmformapconfig_005flookup_jsp \(efmformapconfig_005flookup_jsp\.java:\d+\) - AP config lookup requested an unconfigured key=renderer_missing_ap_\d{13} fid=[1-9]\d*$/);
    assert(jspEvents[i][2].endsWith('key='+receipt.missingKey+' fid='+receipt.fixture.negativeFid));
    assert.equal((jspEvents[i][3]||'').trim(),'');
    const at=Date.parse(jspEvents[i][1].replace(' ','T').replace(',','.')+'Z');assert(at>=Date.parse(receipt.requests[i].startedAt)&&at<=Date.parse(receipt.requests[i].finishedAt));
  }
  if(csrf){const event=events.filter(e=>!jspEvents.includes(e));assert.equal(event.length,1);
    assert.match(event[0][2],/^action\.Log \(Log\.java:73\) - CSRF violation: \(user:<anonymous>, ip:(?:127\.0\.0\.1|0:0:0:0:0:0:0:1|::1), method:POST, error:The token should exist in the storage at this point\)$/);
    assert.equal((event[0][3]||'').trim(),'');const at=Date.parse(event[0][1].replace(' ','T').replace(',','.')+'Z');
    assert(at>=Date.parse(post.startedAt)&&at<=Date.parse(post.finishedAt));
  }
  const auditLines=String(auditText).split(/\r?\n/).filter(line=>line.trim());
  assert(auditLines.every(line=>/^(?:UNKNOWN|KNOWN)\s/.test(line)||line.startsWith('FAIL server log audit:')||(csrf&&/^ {10}\(.+\)$/.test(line))), 'Unrecognized audit evidence is refused');
  const auditRows=String(auditText).split(/\r?\n/).filter(line=>/^(?:UNKNOWN|KNOWN)\s/.test(line));
  assert.equal(auditRows.length,csrf?2:1);assert.match(auditRows[0],/^UNKNOWN\s+2\s+ERROR eform\.efmformapconfig_005flookup_jsp \(efmformapconfig_005flookup_jsp\.java:\d+\)$/);
  if(csrf)assert.match(auditRows[1],/^KNOWN\s+1\s+ERROR action\.Log \(Log\.java:73\) csrf-method:POST csrf-error:The token should exist in the storage at this point$/);
  const summaries=String(auditText).split(/\r?\n/).filter(line=>line.startsWith('FAIL server log audit:'));
  assert.equal(summaries.length,1);assert.match(summaries[0],new RegExp('^FAIL server log audit: 1 unexplained signature\\(s\\), '+(csrf?1:0)+' known, '+(csrf?3:2)+' event\\(s\\) since [0-9 :\\-]+ UTC$'));
  return {intentionalLookupFaultEvents:2,actualTokenlessPostFaultEvents:csrf?1:0,allOtherErrorsRefused:true,rawStrictAuditUnmodified:true};
}
module.exports={createApNegativeProof,resolveOwnedLookup,verifyApNegativeFaultWindow,sha,SOURCE_KEYS};
