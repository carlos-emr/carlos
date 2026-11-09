/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawn}=require('node:child_process');
test('incompatible legacy signal modes fail before starting operations or installing handlers',()=>{
  const {runLegacyCheck}=require('./lib/legacy-check');
  const before=['SIGINT','SIGTERM'].map(signal=>process.listenerCount(signal));
  let started=false;
  assert.throws(()=>runLegacyCheck('invalid-modes',()=>{started=true;},
    {existingSignalHandlers:true,realSignalCheckpoints:true}),/cannot use real signal checkpoints/);
  assert.equal(started,false);
  assert.deepEqual(['SIGINT','SIGTERM'].map(signal=>process.listenerCount(signal)),before);
});
async function run(t,{signal,result,selfSignal}={}) {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-legacy-check-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const child=spawn(process.execPath,[path.join(__dirname,'fixtures/legacy-check/worker.cjs')],
    {env:{...process.env,LEGACY_CHECK_FIXTURE_DIR:dir,RESULT_JSON:path.join(dir,'result.json'),...(selfSignal?{LEGACY_CHECK_OS_CHECKPOINTS:'true',CARLOS_DISPOSABLE_HOST:'true',CARLOS_INTERRUPT_AFTER_CHECKPOINT:'1',CARLOS_INTERRUPT_SIGNAL:selfSignal}:{}),LEGACY_CHECK_RESULT:result===undefined?'':String(result)},stdio:['ignore','pipe','pipe']});
  let output='';child.stdout.on('data',data=>output+=data);child.stderr.on('data',data=>output+=data);
  const closed=new Promise((resolve,reject)=>{child.once('error',reject);child.once('close',(code,killed)=>resolve({code,killed}));});
  t.after(()=>{if(child.exitCode===null)child.kill('SIGKILL');});
  if(signal) {
    const deadline=Date.now()+5000;while(!fs.existsSync(path.join(dir,'ready'))&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,5));
    assert(fs.existsSync(path.join(dir,'ready')),'Fixture did not become ready');child.kill(signal);
  }
  return {dir,output:()=>output,...await closed};
}
for(const [signal,code] of [['SIGINT',130],['SIGTERM',143]])test(`${signal} drains the accepted write, unwinds finally and starts no later operation`,async t=>{
  const result=await run(t,{signal});assert.equal(result.code,code,result.output());assert.equal(result.killed,null);
  assert(fs.existsSync(path.join(result.dir,'settled')));assert(fs.existsSync(path.join(result.dir,'cleaned')));
  assert(!fs.existsSync(path.join(result.dir,'continued')));const structured=JSON.parse(fs.readFileSync(path.join(result.dir,'result.json'),'utf8'));
  assert.equal(structured.detail,'interrupted');
});
for(const result of [0,1,2])test(`legacy result ${result} preserves pass/failure/prerequisite status after cleanup`,async t=>{
  const actual=await run(t,{result});assert.equal(actual.code,result,actual.output());assert(fs.existsSync(path.join(actual.dir,'cleaned')));
  assert.match(actual.output(),new RegExp(['PASS','FAIL','SKIP'][result]+' legacy-fixture'));
});

test('legacy failures cannot reintroduce original query strings through structured results',()=>{
 const {sanitizedError}=require('./lib/legacy-check');
 const original=new Error('failed at "https://host/carlos/chart?demographic_no=123"');
 original.failedStep='open /carlos/chart?demographic_no=456';original.secret='must not be copied';
 const safe=sanitizedError(original);assert.notEqual(safe,original);
 assert.equal(safe.message,'failed at "https://host/carlos/chart"');assert.equal(safe.failedStep,'open /carlos/chart');
 assert(!/123|456|must not/.test(JSON.stringify(safe)));assert.equal(safe.cause,undefined);
});


test('cleanup attempts later restorations after a failure and preserves both failures in order',async()=>{
 const {finishLegacyCleanup}=require('./lib/legacy-check');
 const actions=[],first=new Error('close failed'),second=new Error('row restore failed');
 await assert.rejects(finishLegacyCleanup([
  async()=>{actions.push('close');throw first;},
  ()=>{actions.push('restore');throw second;},
  ()=>{actions.push('remove credential file');},
 ]),error=>error instanceof AggregateError&&error.errors[0]===first&&error.errors[1]===second);
 assert.deepEqual(actions,['close','restore','remove credential file']);
});

for(const [signal,code] of [['SIGINT',130],['SIGTERM',143]])test(`${signal} delivered by the OS at an awaited checkpoint cannot start the next operation`,async t=>{
 const result=await run(t,{selfSignal:signal});assert.equal(result.code,code,result.output());
 assert(fs.existsSync(path.join(result.dir,'settled')));assert(fs.existsSync(path.join(result.dir,'cleaned')));
 assert(!fs.existsSync(path.join(result.dir,'continued')));
 const record=JSON.parse(fs.readFileSync(path.join(result.dir,'result.json'),'utf8'));
 assert.equal(record.outcome,'FAIL');assert.equal(record.detail,'interrupted');
});

for(const [signal,code] of [['SIGINT',130],['SIGTERM',143]])test(`legacy observer retains ${signal} without replacing existing cleanup handlers`,()=>{
 const {EventEmitter}=require('node:events');
 const {observeLegacySignals}=require('./lib/legacy-check');
 const signalProcess=new EventEmitter();let existingCalls=0;
 const existing=()=>{existingCalls++;};signalProcess.on(signal,existing);
 const observer=observeLegacySignals({signalProcess});
 signalProcess.emit(signal);signalProcess.emit(signal);
 assert.equal(existingCalls,2);assert.equal(observer.exitCode,code);
 assert.throws(()=>observer.throwIfCancelled(),error=>observer.isCancellation(error));
 observer.dispose();assert.deepEqual(signalProcess.listeners(signal),[existing]);
 assert.equal(signalProcess.listenerCount(signal==='SIGINT'?'SIGTERM':'SIGINT'),0);
});
