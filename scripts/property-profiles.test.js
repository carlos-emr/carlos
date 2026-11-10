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
  const supervisor=path.join(__dirname,'fixtures','property-profile','supervisor.cjs');
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

test('every declared profile Spring module has a shipped application context',()=>{
  const {PROFILES}=require('./lib/property-profiles');
  for(const properties of Object.values(PROFILES))for(const module of (properties.ModuleNames||'').split(',').filter(Boolean)) {
    assert.equal(fs.existsSync(path.join(__dirname,'../src/main/resources',`applicationContext${module}.xml`)),true,`Profile requests absent Spring module ${module}`);
  }
});
test('suite rejects invalid property-profile selections and unsafe targets before entering the mutating wrapper',()=>{
  const {spawnSync}=require('node:child_process');
  const cli=path.join(__dirname,'run-playwright-suite.js');
  for(const [selection,env,diagnostic] of [
    [['--only','definitely-misspelled-check'],{},/Unknown.*definitely-misspelled-check/i],
    [['--only','appointment-email-option','--skip','appointment-email-option'],{},/No checks matched/],
    [['--only','appointment-email-option'],{BASE_URL:'https://unapproved.invalid/carlos',CARLOS_DISPOSABLE_HOST:'true'},/non-loopback BASE_URL/],
  ]) {
    const result=spawnSync(process.execPath,[cli,'--profile','appointment-options',...selection],{encoding:'utf8',env:{...process.env,BASE_URL:'https://127.0.0.1/carlos',MYSQL_HOST:'localhost',CARLOS_DISPOSABLE_HOST:'',ALLOW_NON_LOCAL_BASE_URL:'',ALLOW_NON_LOCAL_MYSQL_HOST:'',CARLOS_PROPERTY_PROFILE_ACTIVE:'',...env}});
    assert.equal(result.status,1,result.stderr);assert.match(result.stderr,diagnostic);
    assert.doesNotMatch(result.stderr,/Property profiles require|lstat|systemctl/,'profile wrapper ran before selection validation');
  }
});

test('active profile environment marker cannot bypass the private inherited handoff',async()=>{
  const {verifyActiveProfile}=require('./lib/property-profiles');
  await assert.rejects(verifyActiveProfile('address-lock',{env:{CARLOS_PROPERTY_PROFILE_ACTIVE:'address-lock'},file:'/unreadable/should-not-be-opened'}),/private parent handoff/);
});

test('strict profile CLI refuses an unmarked host before entering profile execution', () => {
  const {spawnSync} = require('node:child_process');
  const cli = path.join(__dirname, 'run-playwright-suite.js');
  const result = spawnSync(process.execPath, [cli, '--strict-release', '--profile', 'appointment-options',
    '--only', 'appointment-email-option'], {encoding: 'utf8', env: {...process.env,
      BASE_URL: 'https://127.0.0.1/carlos', MYSQL_HOST: 'localhost', CARLOS_DISPOSABLE_HOST: '',
      CARLOS_PROPERTY_PROFILE_ACTIVE: '', CARLOS_PROFILE_READINESS_SECONDS: '120'}});
  assert.equal(result.status, 1);
  assert.equal(result.stdout, '');
  assert.match(result.stderr, /CARLOS_DISPOSABLE_HOST=true/);
  assert.doesNotMatch(result.stderr, /does not permit --dry-run/);
});
test('profile readiness budget is validated before any configuration mutation',async t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-profile-budget-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'properties');fs.writeFileSync(file,'original');let restarted=false;
  await assert.rejects(withPropertyProfile('address-lock',()=>assert.fail('must not run'),{file,env:{CARLOS_DISPOSABLE_HOST:'true',CARLOS_PROFILE_READINESS_SECONDS:'0'},restart(){restarted=true;}}),/10 to 1800/);
  assert.equal(fs.readFileSync(file,'utf8'),'original');assert.equal(restarted,false);
});

test('supervisor preserves a failed child handoff status and restores configuration',t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-profile-invalid-child-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'properties');fs.writeFileSync(file,'original');
  const result=require('node:child_process').spawnSync(process.execPath,[path.join(__dirname,'fixtures/property-profile/supervisor.cjs')],{env:{...process.env,PROFILE_TEST_DIR:dir,PROFILE_TEST_REJECT_HANDOFF:'true'},encoding:'utf8',timeout:10000});
  assert.ok([1,20].includes(result.status),result.stderr);assert.match(result.stderr,/private parent handoff/);assert.equal(fs.existsSync(path.join(dir,'ready')),false);assert.equal(fs.readFileSync(file,'utf8'),'original');
});
