/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const vm=require('node:vm');
const {X509Certificate}=require('node:crypto');
const yaml=require('js-yaml');
test('WAF HTTPS bootstrap exports valid public roots readable by APT sandbox',()=>{
 const source=fs.readFileSync(path.join(__dirname,'../.github/workflows/waf-policy.yml'),'utf8');
 const config=yaml.load(source),step=config.jobs.policy.steps.find(s=>s.name==='Bootstrap trusted HTTPS roots');
 const originalMask=process.umask(0o077);
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'waf-roots-')),file=path.join(directory,'roots.pem');
 try {
  vm.runInNewContext(step.with.script,{require:name=>name==='node:fs'?{
   writeFileSync:(_destination,contents,options)=>fs.writeFileSync(file,contents,options),
   chmodSync:(_destination,mode)=>fs.chmodSync(file,mode),
  }:require(name)});
  assert.equal(fs.statSync(file).mode&0o444,0o444,'APT drops root privileges while fetching');
  assert.ok(new X509Certificate(fs.readFileSync(file)).ca);
  assert.doesNotMatch(source,/Verify-(?:Peer|Host)[=: ]+false|allow-unauthenticated/);
 } finally {process.umask(originalMask);fs.rmSync(directory,{recursive:true,force:true});}
});
