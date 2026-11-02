/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),h=require('./lib/playwright-harness'),{finishRows}=require('./waf-clinical-text-corpus-playwright-checks');
test('a later missing prerequisite cannot downgrade an earlier WAF row failure to SKIP',()=>{
 const skip=new h.SkipCheck('No lab template'),failure={label:'clinical corpus row',error:new Error('WAF refusal')};
 assert.throws(()=>finishRows([failure],[skip]),error=>!(error instanceof h.SkipCheck)&&error.failedStep==='clinical corpus row'&&/WAF refusal/.test(error.message));
 assert.throws(()=>finishRows([failure,{label:'other',error:new Error('another failure')}],[skip]),error=>error.failedStep==='2 route rows failed');
});
test('all-success returns and a prerequisite-only result preserves its original skip',()=>{
 finishRows([],[]);const skip=new h.SkipCheck('No lab template');assert.throws(()=>finishRows([],[skip]),error=>error===skip);
});
