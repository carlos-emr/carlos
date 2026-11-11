/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const { selectBillingSiteAndProvider, validateBillingContext } = require('./lib/billing-site-context');
const h = require('./lib/playwright-harness');
const context = {multisites:true, clinic:'FAKE Clinic', providerNo:'999998', ohipNo:'123456'};
function nativePage({sites=['none','FAKE Clinic'], current='none', mappings={'FAKE Clinic':['000000','999998|123456']}, global=[], siteCount, sticky=true}={}) {
  const state={site:current, provider:'', options:sites===null?global:[], changed:[]};
  const node = option => typeof option==='string'?{value:option,disabled:false,closest:()=>null}:{...option,closest:()=>option.groupDisabled?{disabled:true}:null};
  const options = values => ({evaluateAll:async fn=>fn(values.map(node))});
  const providers={waitFor:async()=>{},count:async()=>1,locator:name=>{assert.equal(name,'option');return options(state.options);},inputValue:async()=>state.provider,
    selectOption:async value=>{assert(state.options.map(node).some(o=>o.value===value&&!o.disabled));state.changed.push(['provider',value]);if(sticky)state.provider=value;}};
  const clinics={count:async()=>siteCount??(sites===null?0:1),locator:name=>{assert.equal(name,'option');return options(sites||[]);},inputValue:async()=>state.site,
    selectOption:async value=>{assert((sites||[]).map(node).some(o=>o.value===value&&!o.disabled));state.changed.push(['site',value]);state.site=value;state.options=mappings[value]||[];}};
  if(sites!==null&&current!=='none')state.options=mappings[current]||[];
  return {state,locator:selector=>{if(selector==='select[name="xml_provider"]')return providers;if(selector==='select[name="site"]')return clinics;throw Error('Unexpected locator');}};
}
test('native empty default clinic is selected before the exact offered physician',async()=>{const page=nativePage();assert.deepEqual(await selectBillingSiteAndProvider(page,'999998'),context);assert.deepEqual(page.state.changed,[['site','FAKE Clinic'],['provider','999998|123456']]);});
test('current offered clinic is preferred to another clinic',async()=>{const page=nativePage({sites:['none','Other','FAKE Clinic'],current:'FAKE Clinic',mappings:{Other:['1|11'],'FAKE Clinic':['999998|123456']}});assert.equal((await selectBillingSiteAndProvider(page,'999998')).clinic,'FAKE Clinic');});
test('a native clinic with no billable physician does not hide a later valid clinic',async()=>{const page=nativePage({sites:['none','Empty','FAKE Clinic']});assert.equal((await selectBillingSiteAndProvider(page,'999998')).clinic,'FAKE Clinic');assert.deepEqual(page.state.changed.slice(0,2),[['site','Empty'],['site','FAKE Clinic']]);});
test('a disabled clinic is never adopted',async()=>{const page=nativePage({sites:['none',{value:'Foreign',disabled:true},'FAKE Clinic'],mappings:{Foreign:['1|11'],'FAKE Clinic':['999998|123456']}});assert.equal((await selectBillingSiteAndProvider(page,'999998')).clinic,'FAKE Clinic');assert(!page.state.changed.some(v=>v[1]==='Foreign'));});
test('duplicate offered clinic values fail before selecting anything',async()=>{const page=nativePage({sites:['none','FAKE Clinic','FAKE Clinic']});await assert.rejects(selectBillingSiteAndProvider(page,'999998'),/duplicate clinic/);assert.deepEqual(page.state.changed,[]);});
test('no offered clinic is an explicit failure',async()=>assert.rejects(selectBillingSiteAndProvider(nativePage({sites:['none']}),'999998'),/no eligible clinic/));
test('multiple clinic controls are refused',async()=>assert.rejects(selectBillingSiteAndProvider(nativePage({siteCount:2}),'999998'),/ambiguous clinic/));
test('a malformed physician cannot be selected',async()=>assert.rejects(selectBillingSiteAndProvider(nativePage({mappings:{'FAKE Clinic':['000000','foreign','999998|not-an-ohip']}}),'999998'),/no billable physician/));
test('a disabled physician or disabled optgroup is never selected',async()=>{const page=nativePage({mappings:{'FAKE Clinic':[{value:'1|11',disabled:true},{value:'2|22',groupDisabled:true},'999998|123456']}});assert.equal((await selectBillingSiteAndProvider(page,'1')).providerNo,'999998');});
test('duplicate physician values fail',async()=>assert.rejects(selectBillingSiteAndProvider(nativePage({mappings:{'FAKE Clinic':['999998|123456','999998|123456']}}),'999998'),/duplicate physician/));
test('a physician-selection mismatch is refused',async()=>assert.rejects(selectBillingSiteAndProvider(nativePage({sticky:false}),'999998'),/selection did not persist/));
test('nonmultisite invoice preserves its native eligible physician and NULL clinic',async()=>{const page=nativePage({sites:null,global:['000000','999998|123456']});assert.deepEqual(await selectBillingSiteAndProvider(page,'999998'),{...context,multisites:false,clinic:null});assert.deepEqual(page.state.changed,[['provider','999998|123456']]);});
function readOnlySql({providerCount='1',siteCount='1'}={}) {const calls=[];return{calls,value(query){assert(/^SELECT\b/.test(query));calls.push(query);return /FROM provider WHERE/.test(query)?providerCount:siteCount;},execute(){throw Error('Unexpected SQL mutation');}};}
test('context revalidation uses only exact active provider and existing operator/site memberships',()=>{const sql=readOnlySql();assert.equal(validateBillingContext(sql,'999997',context),context);assert.equal(sql.calls.length,2);assert(sql.calls[1].includes("operator_site.provider_no='999997'"));assert(sql.calls[1].includes("billing_site.provider_no='999998'"));assert(sql.calls[1].includes('site.status<>0'));});
test('an ineligible changed provider is refused before site lookup or writes',()=>{const sql=readOnlySql({providerCount:'0'});assert.throws(()=>validateBillingContext(sql,'999997',context),/no longer active/);assert.equal(sql.calls.length,1);});
test('a foreign or inactive clinic is refused without writes',()=>assert.throws(()=>validateBillingContext(readOnlySql({siteCount:'0'}),'999997',context),/not an unambiguous active site/));
test('ambiguous same-name sites are refused without writes',()=>assert.throws(()=>validateBillingContext(readOnlySql({siteCount:'2'}),'999997',context),/not an unambiguous active site/));
test('missing/placeholder clinic and invalid identifiers are refused before any SQL',()=>{for(const invalid of [{...context,clinic:null},{...context,clinic:'none'},{...context,providerNo:'1 OR 1=1'},{...context,ohipNo:'not-a-number'}]){const sql=readOnlySql();assert.throws(()=>validateBillingContext(sql,'999997',invalid));assert.equal(sql.calls.length,0);}});
test('clinic names remain literal exact values in SELECT predicates',()=>{const sql=readOnlySql();validateBillingContext(sql,'999997',{...context,clinic:"FAKE O'Clinic"});assert(sql.calls[1].includes("site.name='FAKE O''Clinic'"));});
function actualSeeder(filename=path.join(__dirname,'billing-on-invoice-third-party-playwright-checks.js')) {
 const module={exports:{}};function requireStub(name){if(name==='./lib/playwright-harness')return h;if(name==='./lib/billing-site-context')return{validateBillingContext};return{};}requireStub.main=null;
 vm.runInNewContext(fs.readFileSync(filename,'utf8'),{require:requireStub,module,exports:module.exports,__dirname:path.dirname(filename),process:{env:{}},console},{filename});return module.exports.seedOwnedBill;
}
function seedSession({providerCount='1',siteCount='1'}={}) {
 const queries=[],cleanups=[];let id=40;return {queries,cleanups,provider:'999997',patient:'314159',marker:'FAKE-PW-SITE',cleanup:fn=>cleanups.push(fn),sql:{value(query){queries.push(query);if(/SELECT COUNT\(\*\) FROM provider WHERE/.test(query))return providerCount;if(/SELECT COUNT\(DISTINCT site.site_id\)/.test(query))return siteCount;if(/^SELECT provider_no FROM provider/.test(query))return context.providerNo;if(/^SELECT ohip_no FROM provider/.test(query))return context.ohipNo;if(/^INSERT INTO billing_on_(cheader1|item)/.test(query))return String(++id);throw Error('Unexpected fixture SQL');},rows(query){queries.push(query);assert(/^SELECT s.site_id FROM providersite/.test(query));return[];},execute(){throw Error('No provider/site or other SQL execute is allowed during seeding');}}};
}
const bill={payProgram:'HCP',status:'O',code:'A007A',fee:'10.00',date:'2026-10-19',billingContext:context};
test('actual shared seeder binds all three owned header clinics and never writes providers/sites',()=>{const seed=actualSeeder();const s=seedSession();for(const status of ['O','B','S'])seed(s,{...bill,status});const headers=s.queries.filter(q=>/^INSERT INTO billing_on_cheader1/.test(q));assert.equal(headers.length,3);for(const q of headers){assert(q.includes("'999998'"));assert(q.endsWith("'999997', 'FAKE Clinic'); SELECT LAST_INSERT_ID()"));}assert.equal(s.cleanups.length,3);assert(s.queries.every(q=>/^SELECT\b|^INSERT INTO billing_on_(cheader1|item)\b/.test(q)));});
test('actual shared seeder refuses stale context before inserting owned rows',()=>{const s=seedSession({siteCount:'0'});assert.throws(()=>actualSeeder()(s,bill),/not an unambiguous active site/);assert(!s.queries.some(q=>/^INSERT/.test(q)));});
test('actual shared seeder preserves NULL clinic for an explicitly nonmultisite native context',()=>{const s=seedSession();actualSeeder()(s,{...bill,billingContext:{...context,multisites:false,clinic:null}});assert(s.queries.find(q=>/^INSERT INTO billing_on_cheader1/.test(q)).endsWith("'999997', NULL); SELECT LAST_INSERT_ID()"));});
