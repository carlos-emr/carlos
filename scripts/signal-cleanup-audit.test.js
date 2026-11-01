/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {verdict}=require('./signal-cleanup-audit');
test('signal audit requires an observed interruption and successful cleanup, rather than any nonzero exit',()=>{
  const interrupted={outcome:'FAIL',detail:'interrupted',cleanupFailed:false};
  assert.equal(verdict(130,interrupted,[]),'PASS');
  for(const [status,record,residue] of [[0,{outcome:'PASS'},[]],[130,undefined,[]],[1,interrupted,[]],
    [130,{...interrupted,detail:'database unavailable'},[]],[130,{...interrupted,cleanupFailed:true},[]],
    [130,interrupted,[{table:'demographic',count:1}]]])assert.equal(verdict(status,record,residue),'FAIL');
  assert.equal(verdict(2,{outcome:'SKIP'},[]),'BLOCKED');
  assert.equal(verdict(2,{outcome:'SKIP'},[{table:'demographic',count:1}]),'FAIL');
});
