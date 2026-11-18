/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {scheduleRoute}=require('./lib/second-patient-window');
test('second patient retains the saved day sheet route in root and named contexts',()=>{
  for(const prefix of ['','/carlos']) {
    const base=new URL('http://127.0.0.1:8080'+(prefix||'/'));
    assert.equal(scheduleRoute(base,base.origin+prefix+'/provider/providercontrol?year=2032&view=0'),'/'+'provider/providercontrol?year=2032&view=0');
  }
});
test('second patient refuses missing, credentialed, sibling-application and foreign schedule URLs',()=>{
  const base=new URL('https://localhost/carlos');
  for(const url of [undefined,'','https://other.invalid/carlos/provider/providercontrol','https://user@localhost/carlos/provider/providercontrol','https://localhost/carlos2/provider/providercontrol'])
    assert.throws(()=>scheduleRoute(base,url));
});
