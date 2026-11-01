/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {hasSignalEntryPoint}=require('./lib/script-signal-safety');
test('signal inventory follows actual calls through cyclic dependencies without accepting comments or strings',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-signals-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const write=(name,source)=>{const file=path.join(root,name+'.js');fs.writeFileSync(file,source);return file;};
  const fake=write('fake',"// runCheck()\nconst text='runWorkflow()'; module.exports={runCheck(){}};");
  assert.equal(hasSignalEntryPoint(fake),false);
  const imported=write('imported',"if(require.main===module)h.runCheck({});");
  const caller=write('caller',"require('./imported');");
  assert.equal(hasSignalEntryPoint(imported),true);assert.equal(hasSignalEntryPoint(caller),false,'Importing an unused CLI guard must not certify the caller');
  const first=write('first',"require('./second');require('./safe');"),second=write('second',"require('./first');");
  write('safe',"h.runCheck({run:()=>{}});");
  assert.equal(hasSignalEntryPoint(first),true);assert.equal(hasSignalEntryPoint(second),true);
  const cycle=write('cycle',"require('./closed');");write('closed',"require('./cycle');");
  assert.equal(hasSignalEntryPoint(cycle),false);
});

test('unused local or imported function bodies do not certify a signal entry point',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-signals-unused-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const write=(name,source)=>{const file=path.join(root,name+'.js');fs.writeFileSync(file,source);return file;};
  const unused=write('unused',"function unused(){h.runCheck({});} module.exports={unused};");
  assert.equal(hasSignalEntryPoint(unused),false);
  assert.equal(hasSignalEntryPoint(write('consumer',"require('./unused'); doUnprotectedWork();")),false);
  assert.equal(hasSignalEntryPoint(write('called',"function start(){h.runCheck({});} start();")),true);
  assert.equal(hasSignalEntryPoint(write('iife',"(async()=>{h.runCheck({});})();")),true);
});

test('local calls discover nested reachable functions without accepting an unused sibling',t=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-signals-nested-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const file=path.join(root,'nested.js');fs.writeFileSync(file,'function outer(){function inner(){h.runCheck({});}inner();}outer();');assert.equal(hasSignalEntryPoint(file),true);
 const unused=path.join(root,'unused.js');fs.writeFileSync(unused,'function outer(){function inner(){h.runCheck({});}}outer();');assert.equal(hasSignalEntryPoint(unused),false);
});

test('nested bindings cannot replace a called function in its parent lexical scope',t=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-signal-scope-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const cases=[
  [false,'function start(){doUnprotectedWork();}function outer(){function start(){h.runCheck({});}}outer();start();'],
  [true,'function start(){h.runCheck({});}function outer(){function start(){doUnprotectedWork();}}outer();start();'],
  [false,'function start(){h.runCheck({});}function outer(start){start();}outer(()=>{});'],
  [true,'function outer(){const start=()=>h.runCheck({});start();}outer();'],
  [false,'function outer(){const start=()=>doUnprotectedWork();{const start=()=>h.runCheck({});}start();}outer();']
 ];
 for(const [index,[expected,source]] of cases.entries()){const file=path.join(root,index+'.js');fs.writeFileSync(file,source);assert.equal(hasSignalEntryPoint(file),expected,source);}
});
