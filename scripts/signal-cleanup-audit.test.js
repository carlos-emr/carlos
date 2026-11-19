/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {verdict,unavailable}=require('./signal-cleanup-audit');
test('signal audit requires an observed interruption and successful cleanup, rather than any nonzero exit',()=>{
  const interrupted={outcome:'FAIL',detail:'interrupted',cleanupFailed:false};
  assert.equal(verdict(130,interrupted,[]),'PASS');
  for(const [status,record,residue] of [[0,{outcome:'PASS'},[]],[130,undefined,[]],[1,interrupted,[]],
    [130,{...interrupted,detail:'database unavailable'},[]],[130,{...interrupted,cleanupFailed:true},[]],
    [130,interrupted,[{table:'demographic',count:1}]]])assert.equal(verdict(status,record,residue),'FAIL');
  assert.equal(verdict(2,{outcome:'SKIP'},[]),'BLOCKED');
  assert.equal(verdict(2,{outcome:'SKIP'},[{table:'demographic',count:1}]),'FAIL');
});

test('signal audit keeps province and profile prerequisites explicit instead of running an incompatible clinical workflow',()=>{
  assert.match(unavailable({provinces:['BC']},{CARLOS_SUITE_PROVINCE:'ON'}),/Requires BC/);
  assert.equal(unavailable({provinces:['ON']},{CARLOS_SUITE_PROVINCE:'ON'}),null);
  assert.match(unavailable({provinces:['all'],profile:'no-pin'},{}),/Requires no-pin/);
  assert.equal(unavailable({provinces:['all'],profile:'no-pin'},{CARLOS_PROPERTY_PROFILE_ACTIVE:'no-pin'}),null);
});

test('configured-runtime audit blocks the persistent installer password-reset prerequisite',()=>{assert.match(unavailable({name:'deb-login',provinces:['all']},{}),/fresh package initial-login/);});

test('signal audit counts real optional and ordinary checkpoint calls and excludes comments and strings',()=>{
 const {checkpointSites}=require('./signal-cleanup-audit');
 assert.equal(checkpointSites('// cancellation.throwIfCancelled()\nconst fake="cancellation?.throwIfCancelled()";'),0);
 assert.equal(checkpointSites('cancellation?.throwIfCancelled();checkCancellation?.throwIfCancelled();cancellation.throwIfCancelled();'),3);
 const interrupted={outcome:'FAIL',detail:'interrupted'};
 assert.equal(verdict(143,interrupted,[],'SIGTERM'),'PASS');
 assert.equal(verdict(130,interrupted,[],'SIGTERM'),'FAIL');
 assert.equal(verdict(143,interrupted,[],'SIGINT'),'FAIL');
});

test('missing or ambiguous credential snapshots cannot silently certify an in-place password restoration',()=>{
 const {ownedCredentialState}=require('./signal-cleanup-audit');
 for(const rows of [[],[['invalid']],[[null]],[["a".repeat(64)],["b".repeat(64)]]]) {
  assert.throws(()=>ownedCredentialState({rows:()=>rows},'FAKE-operator'),/independently readable/);
 }
 assert.equal(ownedCredentialState({rows:()=>[['a'.repeat(64)]]},'FAKE-operator'),'a'.repeat(64));
});

test('OS acceptance refuses an unawaited historical handler-injection entry point',()=>{
 const {usesOsSignalCheckpoints}=require('./signal-cleanup-audit');
 assert.equal(usesOsSignalCheckpoints('// realSignalCheckpoints:true\nh.runLegacyCheck("fake",main);'),false);
 assert.equal(usesOsSignalCheckpoints('h.runLegacyCheck("fake",main,{realSignalCheckpoints:false});'),false);
 assert.equal(usesOsSignalCheckpoints('h.runLegacyCheck("fake",main,{realSignalCheckpoints:true});'),true);
});
