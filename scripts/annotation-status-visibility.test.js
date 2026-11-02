/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'e2e/fax/annotate-document-playwright-checks.js'),'utf8');
const start=source.indexOf('async function emptyAnnotationStatusDisplay('),helper=source.slice(start,source.indexOf('\n}',start)+2);
for(const display of ['none','block'])test('empty status observation waits for emptiness and reports actual CSS '+display,async()=>{
  const element={textContent:'Waiting for document capacity'},context=vm.createContext({document:{getElementById:()=>element},getComputedStyle:()=>({display})});
  vm.runInContext(helper,context);
  let disposed=false;
  const page={async waitForFunction(predicate,args,options){
    assert.equal(options.timeout,30000);
    assert.equal(predicate(),false,'a visible busy message is not an empty status');
    element.textContent='';
    const snapshot=predicate();
    element.textContent='New asynchronous status';
    return {jsonValue:async()=>snapshot,async dispose(){disposed=true;}};
  }};
  assert.equal(await context.emptyAnnotationStatusDisplay(page),display);
  assert.equal(disposed,true);
});
