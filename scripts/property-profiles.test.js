/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {withPropertyProfile, propertiesFor} = require('./lib/property-profiles');
const {spawn}=require('node:child_process');
for (const failAt of ['none', 'check', 'restart']) test(`profile restores byte-exact properties after ${failAt}`, async t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-profile-'));
  t.after(() => fs.rmSync(dir, {recursive: true, force: true}));
  const file = path.join(dir, 'properties'), original = Buffer.from('# keep\r\nlogin_lock=true\r\nlast=no-final-newline');
  fs.writeFileSync(file, original, {mode: 0o640}); fs.chmodSync(file, 0o640); let restarts = 0, ran = false;
  const call = withPropertyProfile('address-lock', () => {
    ran = true; assert.match(fs.readFileSync(file, 'utf8'), /login_lock=false/);
    if (failAt === 'check') throw new Error('check failure'); return 0;
  }, {env: {CARLOS_DISPOSABLE_HOST: 'true'}, file, readRestarts: () => '0', restart: () => {
    restarts++; if (failAt === 'restart' && restarts === 1) throw new Error('restart failure');
  }});
  if (failAt === 'none') assert.equal(await call, 0); else await assert.rejects(call, /failure/);
  assert.equal(restarts, 2); assert.equal(ran, failAt !== 'restart');
  assert.deepEqual(fs.readFileSync(file), original); assert.equal(fs.statSync(file).mode & 0o777, 0o640);
});
test('unknown profiles and missing consent programs fail before mutation', () => {
  assert.throws(() => propertiesFor('typo'), /Unknown/);
  assert.throws(() => propertiesFor('private-consent', {}), /CONSENT_PROGRAM_ID/);
});

for(const signal of ['SIGINT','SIGTERM'])test(`profile supervisor restores configuration after ${signal} and child cleanup`,async t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-profile-signal-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'properties'),original='# exact snapshot\r\nlogin_lock=true';fs.writeFileSync(file,original);
  const worker=path.join(dir,'worker.cjs'),supervisor=path.join(dir,'supervisor.cjs');
  fs.writeFileSync(worker,`const fs=require('node:fs'),path=require('node:path');
    process.on('SIGTERM',()=>{fs.writeFileSync(path.join(process.env.PROFILE_TEST_DIR,'cleaned'),'yes');process.exit(0);});
    fs.writeFileSync(path.join(process.env.PROFILE_TEST_DIR,'ready'),String(process.pid));setInterval(()=>{},1000);`);
  fs.writeFileSync(supervisor,`const fs=require('node:fs');
    const {withPropertyProfile,runProfileChild}=require(${JSON.stringify(require.resolve('./lib/property-profiles'))});
    withPropertyProfile('address-lock',({signal})=>runProfileChild([${JSON.stringify(worker)}],'address-lock',signal),
      {env:{CARLOS_DISPOSABLE_HOST:'true'},file:${JSON.stringify(file)},readRestarts:()=> '0',restart:()=>fs.appendFileSync(${JSON.stringify(path.join(dir,'restarts'))},'restart\\n')})
      .then(()=>{process.exitCode=0;},error=>{console.error(error.message);process.exitCode=20;});`);
  const child=spawn(process.execPath,[supervisor],{env:{...process.env,PROFILE_TEST_DIR:dir},stdio:['ignore','pipe','pipe']});
  let diagnostics='';child.stderr.on('data',chunk=>{diagnostics+=chunk;});
  const exited=new Promise((resolve,reject)=>{child.once('error',reject);child.once('close',(code)=>resolve(code));});
  t.after(()=>{if(child.exitCode===null)child.kill('SIGKILL');});
  const deadline=Date.now()+10000;
  while(!fs.existsSync(path.join(dir,'ready'))&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,25));
  assert(fs.existsSync(path.join(dir,'ready')),'Profile child did not become ready');
  assert.match(fs.readFileSync(file,'utf8'),/login_lock=false/);child.kill(signal);
  assert.equal(await exited,20,diagnostics);
  assert.match(diagnostics,/interrupted/);
  assert.equal(fs.readFileSync(path.join(dir,'cleaned'),'utf8'),'yes');
  assert.equal(fs.readFileSync(file,'utf8'),original);
  assert.equal(fs.readFileSync(path.join(dir,'restarts'),'utf8'),'restart\nrestart\n');
});
