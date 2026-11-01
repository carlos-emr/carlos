/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {keepOwnReloadFailure}=require('./clinical-form-followup-playwright-checks');
test('own reload excuses only its already-running read request by object identity',()=>{
 const request={},other={},base=new URL('https://127.0.0.1/carlos'),entry={requestRef:new WeakRef(request),resourceType:'xhr',errorText:'net::ERR_ABORTED',url:'https://127.0.0.1/carlos/encounter/displayForms'};
 assert.equal(keepOwnReloadFailure(entry,new Set([request]),base),false);
 for(const changed of [{requestRef:new WeakRef(other)},{requestRef:undefined},{resourceType:'fetch'},{errorText:'net::ERR_FAILED'},{url:'https://foreign.invalid/carlos/encounter/displayForms'},{url:'https://127.0.0.1/carlos/CaseManagementEntry'}])assert.equal(keepOwnReloadFailure({...entry,...changed},new Set([request]),base),true);
 assert.equal(keepOwnReloadFailure(entry,new Set(),base),true);
});
