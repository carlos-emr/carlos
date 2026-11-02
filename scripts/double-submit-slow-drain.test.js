/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{armSlowServer}=require('./lib/double-submit-helpers');
test('slow-response removal drains an accepted request and disposes its response before cleanup',async()=>{
 let handler,release,done=false,disposed=false,removed=false;const accepted=new Promise(resolve=>{release=resolve;});
 const context={route:async(pattern,value)=>{handler=value;},unroute:async()=>{removed=true;}};
 const disarm=await armSlowServer(context,/owned/,1),request=handler({request:()=>({method:()=> 'POST'}),fetch:async()=>{await accepted;return{dispose:async()=>{disposed=true;}};},fulfill:async()=>{done=true;}});
 let drained=false;const cleanup=disarm().then(()=>{drained=true;});await new Promise(resolve=>setImmediate(resolve));assert(removed);assert(!drained);assert(!done);release();await Promise.all([request,cleanup]);assert(done&&disposed&&drained);
});

test('an unknown POST outcome retains all workflow rows while still disposing credentials',async()=>{
 const {cleanupOwnedWorkflow}=require('./lib/workflow-session');let handler,disposed=false,childDeleted=false;
 const browser={close:async()=>{}},context={browser:()=>browser,route:async(_pattern,value)=>{handler=value;},unroute:async()=>{}};
 const disarm=await armSlowServer(context,/owned/,1);
 await handler({request:()=>({method:()=> 'POST'}),fetch:async()=>{throw new Error('response lost after acceptance');}});
 await assert.rejects(disarm(),/outcome is unknown/);
 const sql={value:()=> '1',execute:()=>assert.fail('uncertain fixtures must remain'),dispose:()=>{disposed=true;}};
 await assert.rejects(cleanupOwnedWorkflow({browser,sql,patient:'42',marker:'owned',cleanups:[()=>{childDeleted=true;}]}),/retain fixtures/);
 assert.equal(disposed,true);assert.equal(childDeleted,false);
});
