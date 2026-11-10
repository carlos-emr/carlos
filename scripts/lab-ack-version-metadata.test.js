/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const index=fs.readFileSync('src/main/webapp/share/javascript/oscarMDSIndex.js','utf8');
const jsp=fs.readFileSync('src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp','utf8');
function source(text,name,indent='') {const start=text.indexOf(indent+'function '+name+'(');assert.ok(start>=0);const end=text.indexOf('\n'+indent+'}\n',start);assert.ok(end>start);return text.slice(start,end+indent.length+3);}
for (const macro of [false, true]) test((macro ? 'macro' : 'acknowledgment') + ' sends one in-place update and an empty cached-client marker', () => {
 const messages=[];
 const context=vm.createContext({document:{getElementById:()=>({elements:{segmentID:{value:'12'},labType:{value:'HL7'}}})},BroadcastChannel:class {postMessage(message){messages.push(message);}close(){}},self:{},window:{}});
 vm.runInContext(macro?source(jsp,'notifyInboxhubAfterMacro','    '):source(index,'notifyInboxhubAcknowledged'),context);
 const chain={affectedVersionIds:[10,11,12]};
 if(macro)context.notifyInboxhubAfterMacro('acknowledgeForm_12',3,chain);else context.notifyInboxhubAcknowledged('12','HL7',3,chain);
 assert.equal(messages.length,1);assert.deepEqual(Array.from(messages[0].retainedVersionIds),[]);
 assert.equal(messages[0].clearedCount,3);
});
test('no-BroadcastChannel delivery uses the opener in-place contract',()=>{
 const calls=[];const opener={dropAcknowledgedInboxhubItem(...args){calls.push(args);return true;}};
 const context=vm.createContext({BroadcastChannel:class {constructor(){throw Error('unsupported');}},self:{opener},window:{}});
 vm.runInContext(source(index,'notifyInboxhubAcknowledged'),context);context.notifyInboxhubAcknowledged('12','HL7',3,{affectedVersionIds:[10,11,12]});
 assert.equal(calls.length,1);assert.deepEqual(Array.from(calls[0][3]),[]);assert.equal(calls[0][2],3);
});
test('popup acknowledgment removes only server-affected IDs, despite an older posted chain',async()=>{
 const removed=[],notifications=[];let closed=0;const jQuery=()=>({length:0});jQuery.post=()=>Promise.resolve({clearedCount:1,affectedVersionIds:[12],retainedVersionIds:[]});
 const window={close(){closed++;}};const context=vm.createContext({jQuery,window,_in_window:true,contextpath:'',document:{getElementById:()=>({})},serializeFormToObject:()=>({labType:'HL7',multiID:'10,11,12'}),removeOpenerInboxRow:(id)=>removed.push(id),notifyInboxhubAcknowledged:(...args)=>notifications.push(args)});
 vm.runInContext(index.slice(index.indexOf('function serverClearedCount('),index.indexOf('function fileDoc(')),context);
 await context.updateStatus('acknowledgeForm_12');assert.deepEqual(removed,['12']);assert.equal(closed,1);assert.deepEqual(Array.from(notifications[0][3].affectedVersionIds),['12']);
});

test('an inaccessible opener does not turn a committed acknowledgment into a reported failure',()=>{
 const denied=Object.defineProperty({},'dropAcknowledgedInboxhubItem',{get(){const error=new Error('cross-origin');error.name='SecurityError';throw error;}});
 const context=vm.createContext({BroadcastChannel:class {constructor(){throw Error('unsupported');}},self:{opener:denied},window:{}});
 vm.runInContext(source(index,'notifyInboxhubAcknowledged'),context);
 assert.doesNotThrow(()=>context.notifyInboxhubAcknowledged('12','HL7',1,{affectedVersionIds:[12],retainedVersionIds:[]}));
});
