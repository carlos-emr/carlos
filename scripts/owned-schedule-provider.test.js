/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {validateRows,ownedScheduleProvider,waitForScheduleProvider}=require('./lib/owned-schedule-provider');
const provider='888888',creator='999998',template='P:OnCallClinic';
function intent(){return{provider,creator,template,weekly:[{provider_no:provider,creator,available:'1',status:'A',sdate:'2031-03-03',edate:'2031-03-28',day_of_week:'2 3 4 5 6 ',avail_hourB:'',avail_hour:['MON','TUE','WED','THU','FRI'].map(d=>`<${d}>${template}</${d}>`).join('')}],dates:[{provider_no:provider,creator,available:'1',status:'A',sdate:'2031-03-03',priority:'b',reason:'',hour:template}]};}
test('schedule cleanup admits only the owned native weekday range and template',()=>{validateRows(intent());validateRows({...intent(),weekly:[],dates:[]});});
for(const [field,value] of Object.entries({provider_no:'other',creator:'other',sdate:'2031-03-01',available:'0',status:'D',priority:'a',reason:'foreign',hour:'different'}))test('schedule cleanup retains changed date '+field,()=>{const data=intent();data.dates[0][field]=value;assert.throws(()=>validateRows(data),/changed/);});
for(const [field,value] of Object.entries({edate:'2031-03-29',day_of_week:'1 2 3 ',avail_hourB:'foreign',avail_hour:'changed'}))test('schedule cleanup retains changed weekly '+field,()=>{const data=intent();data.weekly[0][field]=value;assert.throws(()=>validateRows(data),/changed/);});
test('schedule cleanup rejects duplicate dates and refuses mutation before confirmed browser shutdown',()=>{const data=intent();data.dates.push({...data.dates[0]});assert.throws(()=>validateRows(data),/duplicate/);let writes=0;const fixture=ownedScheduleProvider({value:()=>creator,execute(){writes++;}},{testUser:'test'});assert.throws(()=>fixture.cleanup(),/shutdown/);assert.equal(writes,0);});

test('the schedule picker waits for its owned provider through a warm cache',async()=>{
  let time=0,loads=0,pauses=0;
  const load=async()=>{loads++;return{locator(selector){assert.match(selector,/value="888888"/);return{count:async()=>time>=300000?1:0};}};};
  const frame=await waitForScheduleProvider(load,provider,{now:()=>time,pause:async ms=>{time+=ms;pauses++;}});
  assert.ok(frame);assert.equal(time,300000);assert.equal(loads,21);assert.equal(pauses,20);
});
test('an already offered schedule provider needs no delay',async()=>{
  const frame={locator:()=>({count:async()=>1})};
  assert.equal(await waitForScheduleProvider(async()=>frame,provider,{pause:async()=>assert.fail('unexpected delay')}),frame);
});
test('a persistent missing provider fails after cache expiry and cancellation is preserved',async()=>{
  let time=0;const frame={locator:()=>({count:async()=>0})};
  await assert.rejects(waitForScheduleProvider(async()=>frame,provider,{now:()=>time,pause:async ms=>{time+=ms;}}),/never offered.*cache expired/);
  const cancelled=new Error('interrupted');
  await assert.rejects(waitForScheduleProvider(async()=>frame,provider,{checkpoint:()=>{throw cancelled;}}),error=>error===cancelled);
  assert.equal(await waitForScheduleProvider(async()=>null,provider),null);
});
