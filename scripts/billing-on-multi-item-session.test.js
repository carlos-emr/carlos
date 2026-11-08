/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {createRequire}=require('node:module');

test('actual multi-item caller preserves inherited SQL/provider/context and physician-dependent cleanup',async()=>{
  const file=path.join(__dirname,'billing-on-multi-item-payments-playwright-checks.js'),nativeRequire=createRequire(file),module={exports:{}};
  const stop=new Error('owned seed prefix verified'),registered=[];let completed=false;
  const sql={rows(query){if(query.includes('SELECT DISTINCT service_code'))return [['_ONE'],['_TWO'],['_THREE']];assert(query.includes('SELECT id FROM billing_on_cheader1'));return [];}};
  const session={sql,patient:'123',marker:'FAKE-PW0123456789abcdef',provider:'999998',context:{browserIdentity:'owned'}};
  const dependent=Object.create(session);dependent.cleanup=callback=>registered.push(callback);
  const physician={providerNo:'970001',dependentSession:dependent},context={providerNo:'970001',ohipNo:'960001',multisites:false,clinic:null};
  const overrides={
    './lib/owned-billing-physician':{createOwnedBillingPhysician:s=>{assert.equal(s,session);return physician;}},
    './billing-on-invoice-third-party-playwright-checks':{
      captureNativeInvoiceContext:async(s,p)=>{assert.equal(s,session);assert.equal(p,physician);return context;},billDate:()=> '2026-01-02',
      seedOwnedBill(s,options){
        assert.equal(s.sql,sql);assert.equal(s.provider,session.provider);assert.equal(s.context,session.context);
        assert.equal(options.billingContext,context);assert.equal(options.fee,'11.23');
        s.cleanup(()=>{completed=true;});throw stop;
      },
    },
  };
  function req(id){return Object.hasOwn(overrides,id)?overrides[id]:nativeRequire(id);}
  req.main=null;
  vm.runInNewContext(fs.readFileSync(file,'utf8'),{require:req,module,exports:module.exports,process,console,URL},{filename:file});
  await assert.rejects(module.exports.workflow(session),error=>error===stop);
  assert.equal(registered.length,1,'Dependent cleanup bypassed the physician gate');
  await registered[0]();assert.equal(completed,true);
});
