/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{deferNativeLinkSyntaxErrors}=require('./rx-limited-use-code-persistence-playwright-checks');
const hrefs=Array(3).fill('javascript: return void();'),error=()=>({label:'limited-use-rx',text:'Illegal return statement'});
test('LU diagnostic deferral preserves unrelated errors and requires three exact native broken hrefs',()=>{
 const before={label:'other',text:'earlier'},other={label:'limited-use-rx',text:'other problem'},recorder={pageErrors:[before,error(),error(),error(),other]};
 assert.equal(deferNativeLinkSyntaxErrors(recorder,1,hrefs).length,3);assert.deepEqual(recorder.pageErrors,[before,other]);
 for(const links of [hrefs.slice(1),['#',...hrefs.slice(1)]]){const current={pageErrors:[error(),error(),error()]};assert.equal(deferNativeLinkSyntaxErrors(current,0,links).length,0);assert.equal(current.pageErrors.length,3);}
 const current={pageErrors:[error(),error()]};assert.equal(deferNativeLinkSyntaxErrors(current,0,hrefs).length,0);assert.equal(current.pageErrors.length,2);
});
