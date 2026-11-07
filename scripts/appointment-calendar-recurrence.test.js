/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {calendarCases}=require('./appointment-calendar-recurrence-playwright-checks');
for(const [today,anchor] of [['2026-11-01',2028],['2028-02-29',2032],['2033-12-31',2036],['2095-12-31',2104],['2099-12-31',2104],['2395-12-31',2396]]) {
  test(`calendar examples remain future and retain explicit leap/common expectations after ${today}`,()=>{
    const cases=calendarCases(new Date(today+'T12:00:00Z'));
    assert.equal(cases.length,6);assert.equal(new Set(cases.map(c=>c.id)).size,6);
    assert.deepEqual(cases.find(c=>c.id==='feb29-biennial').dates,[`${anchor}-02-29`,`${anchor+2}-02-28`,`${anchor+4}-02-29`]);
    assert.deepEqual(cases.find(c=>c.id==='jan31-common').dates,[`${anchor+1}-01-31`,`${anchor+1}-02-28`,`${anchor+1}-03-31`]);
    for(const c of cases) {
      assert(Date.parse(c.start)>Date.parse(today));assert(Date.parse(c.start)-Date.parse(today)<3650*86400000);
      for(const date of c.dates)assert.equal(new Date(date+'T00:00:00Z').toISOString().slice(0,10),date,'all expected dates must actually exist');
    }
  });
}
