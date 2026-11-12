/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'appointment-patient-typeahead-playwright-checks.js'),'utf8');
const main=source.slice(source.indexOf('async function main() {'),source.indexOf('\nif (require.main === module)'));
async function run(options={}){
 const events=[],original=new Error('scenario fault'),cleanup=new Error('monitor cleanup fault');let scope;
 const context={appointmentNo:'1',providerNo:'999998',daysAhead:400,searchTerm:'FAKE-',bookingDate:undefined,
 assert,Date,AggregateError,PAGES:{add:{label:'add'},edit:{label:'edit'}},
 readConfig:()=>({mysql:{},baseUrl:'https://127.0.0.1/carlos'}),createRecorder:()=>({}),
 createSqlRunner:()=>({rows:()=>[['7'],['9']],dispose(){events.push('dispose');}}),
 appointmentMonitorCleanup:value=>{scope=value;return{async remember(){events.push('remember');if(options.rememberFault)throw original;},cleanup(){events.push('cleanup');if(options.cleanupFault)throw cleanup;}};},
 launchBrowser:async()=>{events.push('launch');if(options.launchFault)throw original;return{async close(){events.push('close');}};},
 newContext:async()=>({}),login:async()=>({async close(){events.push('landing-close');}}),
 arrowBlurSubmit:async()=>{events.push('scenario');if(options.scenarioFault)throw original;},handEditSubmit:async()=>{},blankSubmit:async()=>{},submitWithoutBlur:async()=>{},searchHandoffs:async()=>{},assertNoPageErrors:()=>events.push('assertions')};
 let error;try{await vm.runInNewContext('('+main+')()',context);}catch(e){error=e;}
 return{events,error,original,cleanup,scope,context};
}
test('actual fixture captures its session before any page and closes browser before scoped cleanup',async()=>{const r=await run();assert.equal(r.error,undefined);assert.ok(r.events.indexOf('remember')<r.events.indexOf('scenario'));assert.ok(r.events.indexOf('close')<r.events.indexOf('cleanup'));assert.equal(r.events.at(-1),'dispose');assert.equal(r.scope.date,r.context.bookingDate);assert.match(r.scope.date,/^\d{4}-\d{2}-\d{2}$/);});
test('scenario and monitor-cleanup faults are both retained and client disposal always runs',async()=>{const r=await run({scenarioFault:true,cleanupFault:true});assert.ok(r.error instanceof AggregateError);assert.deepEqual(r.error.errors,[r.original,r.cleanup]);assert.equal(r.events.at(-1),'dispose');});
test('missing session refuses all scenarios but still closes and disposes',async()=>{const r=await run({rememberFault:true});assert.equal(r.error,r.original);assert.ok(!r.events.includes('scenario'));assert.deepEqual(r.events.slice(-3),['close','cleanup','dispose']);});
test('browser launch failure still attempts monitor cleanup and disposes SQL client',async()=>{const r=await run({launchFault:true});assert.equal(r.error,r.original);assert.deepEqual(r.events,['launch','cleanup','dispose']);});
test('actual Add-page date remains the cleanup date when the wall clock crosses midnight',()=>{const start=source.indexOf('const PAGES = {');const end=source.indexOf('\n/**',start);const context={providerNo:'999998',appointmentNo:'1',bookingDate:'2027-11-23',URLSearchParams,Date};vm.runInNewContext(source.slice(start,end)+'\nthis.pages=PAGES;',context);const url=new URL('https://127.0.0.1'+context.pages.add.path());assert.equal(url.searchParams.get('year'),'2027');assert.equal(url.searchParams.get('month'),'11');assert.equal(url.searchParams.get('day'),'23');});

test('monitor SQL adapter preserves every returned identity rather than one scalar',async()=>{const r=await run();assert.equal(r.scope.sql('SELECT id FROM PageMonitor'),'7\n9');});
