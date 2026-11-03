/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const {assertControlledRefusal}=require('./error-sanitization-playwright-checks');
test('former error probes accept a controlled input refusal',()=>{
  assert.doesNotThrow(()=>assertControlledRefusal('probe',400,'Invalid input',{},'SYNTHETIC_SECRET'));
  assert.doesNotThrow(()=>assertControlledRefusal('missing address',400,'Invalid input',{}));
});
test('former error probes reject login redirects, regressions, reflected input and sanitized 500 bodies',()=>{
  for(const status of [200,302,401,403,404,500]) {
    assert.throws(()=>assertControlledRefusal('probe',status,'Invalid input',{},'SYNTHETIC_SECRET'),/expected HTTP 400/);
  }
  for(const body of ['SYNTHETIC_SECRET','java.lang.Exception: hidden','org.hibernate.Error',
    'An error occurred. Reference ID: 12345678-1234-1234-1234-123456789abc']) {
    assert.throws(()=>assertControlledRefusal('probe',400,body,{},'SYNTHETIC_SECRET'));
  }
  assert.throws(()=>assertControlledRefusal('probe',400,'Invalid input',{'content-disposition':'attachment'},'SYNTHETIC_SECRET'),/download/);
});
test('each malformed satellite address is rejected if reflected raw or HTML encoded',()=>{
  const address='<b>Dr A</b><br>Clinic<br>Street';
  for(const body of [address,'Invalid input: &lt;b&gt;Dr A&lt;/b&gt;&lt;br&gt;Clinic&lt;br&gt;Street']) {
    assert.throws(()=>assertControlledRefusal('satellite',400,body,{},address),/reflected invalid input/);
  }
});
