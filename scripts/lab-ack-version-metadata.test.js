/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const index=fs.readFileSync('src/main/webapp/share/javascript/oscarMDSIndex.js','utf8');
const jsp=fs.readFileSync('src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp','utf8');
function source(text,name,indent='') {const start=text.indexOf(indent+'function '+name+'(');assert.ok(start>=0);const end=text.indexOf('\n'+indent+'}\n',start);assert.ok(end>start);return text.slice(start,end+indent.length+3);}
for(const macro of [false,true])test((macro?'macro':'acknowledgment')+' carries retained IDs and a compatible refresh for older inboxes',()=>{
 const messages=[];
 const context=vm.createContext({document:{getElementById:()=>({elements:{segmentID:{value:'12'},labType:{value:'HL7'}}})},BroadcastChannel:class {postMessage(message){messages.push(message);}close(){}},self:{},window:{}});
 vm.runInContext(macro?source(jsp,'notifyInboxhubAfterMacro','    '):source(index,'notifyInboxhubAcknowledged'),context);
 const chain={affectedVersionIds:[12],retainedVersionIds:[11]};
 if(macro)context.notifyInboxhubAfterMacro('acknowledgeForm_12',1,chain);else context.notifyInboxhubAcknowledged('12','HL7',1,chain);
 assert.equal(messages.length,2);assert.deepEqual(Array.from(messages[0].retainedVersionIds),[11]);
 assert.equal(messages[1].compatibilityFor,'12');assert.equal(messages[1].segmentID,undefined);
});
test('a fully covered acknowledgment keeps the single-message in-place protocol',()=>{
 const messages=[];const context=vm.createContext({BroadcastChannel:class {postMessage(message){messages.push(message);}close(){}}});
 vm.runInContext(source(index,'notifyInboxhubAcknowledged'),context);context.notifyInboxhubAcknowledged('12','HL7',1,{affectedVersionIds:[12],retainedVersionIds:[]});assert.equal(messages.length,1);
});
test('no-BroadcastChannel delivery refreshes an older opener that cannot interpret retained IDs',()=>{
 let refreshes=0,drops=0;const opener={dropAcknowledgedInboxhubItem:function(id,type,count){drops++;},refreshInboxhubAfterHrmRevoke(){refreshes++;}};
 const context=vm.createContext({BroadcastChannel:class {constructor(){throw Error('unsupported');}},self:{opener},window:{}});
 vm.runInContext(source(index,'notifyInboxhubAcknowledged'),context);context.notifyInboxhubAcknowledged('12','HL7',1,{affectedVersionIds:[12],retainedVersionIds:[11]});assert.equal(refreshes,1);assert.equal(drops,0);
});
test('popup acknowledgment removes only server-affected IDs, despite an older posted chain',async()=>{
 const removed=[],notifications=[];let closed=0;const jQuery=()=>({length:0});jQuery.post=()=>Promise.resolve({clearedCount:1,affectedVersionIds:[12],retainedVersionIds:[10,11]});
 const window={close(){closed++;}};const context=vm.createContext({jQuery,window,_in_window:true,contextpath:'',document:{getElementById:()=>({})},serializeFormToObject:()=>({labType:'HL7',multiID:'10,11,12'}),removeOpenerInboxRow:(id)=>removed.push(id),notifyInboxhubAcknowledged:(...args)=>notifications.push(args)});
 vm.runInContext(index.slice(index.indexOf('function serverClearedCount('),index.indexOf('function fileDoc(')),context);
 await context.updateStatus('acknowledgeForm_12');assert.deepEqual(removed,['12']);assert.equal(closed,1);assert.deepEqual(Array.from(notifications[0][3].retainedVersionIds),['10','11']);
});
