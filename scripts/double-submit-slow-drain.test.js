/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{armSlowServer}=require('./lib/double-submit-helpers');
test('slow-response removal drains an accepted request and disposes its response before cleanup',async()=>{
 let handler,release,done=false,disposed=false,removed=false;const accepted=new Promise(resolve=>{release=resolve;});
 const context={route:async(pattern,value)=>{handler=value;},unroute:async()=>{removed=true;}};
 const disarm=await armSlowServer(context,/owned/,1),request=handler({request:()=>({method:()=> 'POST'}),fetch:async()=>{await accepted;return{dispose:async()=>{disposed=true;}};},fulfill:async()=>{done=true;}});
 let drained=false;const cleanup=disarm().then(()=>{drained=true;});await new Promise(resolve=>setImmediate(resolve));assert(removed);assert(!drained);assert(!done);release();await Promise.all([request,cleanup]);assert(done&&disposed&&drained);
});
