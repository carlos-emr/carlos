/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {triggerIntent}=require('./rx-save-failure-retry-playwright-checks');
test('late Rx fault rejects an unowned scope before producing executable SQL',()=>{
  const valid=['pw_rx_late_0123456789abcdef','123',['44'],'FAKE-PW0123456789abcdef'];
  for(const args of [[...valid.slice(0,1),'0',...valid.slice(2)],['foreign',...valid.slice(1)],[valid[0],valid[1],[],valid[3]],[valid[0],valid[1],['44); DROP TABLE drugs;'],valid[3]],[...valid.slice(0,3),'someone-else']])assert.throws(()=>triggerIntent(...args),/Invalid owned Rx fault scope/);
  const {body,message}=triggerIntent(...valid);
  assert.match(body,/NEW.demographic_no=123/);assert.match(body,/drugid NOT IN \(44\)/);
  assert.match(body,/EXISTS\(SELECT 1 FROM drugs/);assert.match(body,/SIGNAL SQLSTATE '45000'/);
  assert.equal(message,'PW_RX_LATE_0123456789abcdef');
});
