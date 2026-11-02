/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {validateRows,ownedScheduleProvider}=require('./lib/owned-schedule-provider');
const provider='888888',creator='999998',template='P:OnCallClinic';
function intent(){return{provider,creator,template,weekly:[{provider_no:provider,creator,available:'1',status:'A',sdate:'2031-03-03',edate:'2031-03-28',day_of_week:'2 3 4 5 6 ',avail_hourB:'',avail_hour:['MON','TUE','WED','THU','FRI'].map(d=>`<${d}>${template}</${d}>`).join('')}],dates:[{provider_no:provider,creator,available:'1',status:'A',sdate:'2031-03-03',priority:'b',reason:'',hour:template}]};}
test('schedule cleanup admits only the owned native weekday range and template',()=>{validateRows(intent());validateRows({...intent(),weekly:[],dates:[]});});
for(const [field,value] of Object.entries({provider_no:'other',creator:'other',sdate:'2031-03-01',available:'0',status:'D',priority:'a',reason:'foreign',hour:'different'}))test('schedule cleanup retains changed date '+field,()=>{const data=intent();data.dates[0][field]=value;assert.throws(()=>validateRows(data),/changed/);});
for(const [field,value] of Object.entries({edate:'2031-03-29',day_of_week:'1 2 3 ',avail_hourB:'foreign',avail_hour:'changed'}))test('schedule cleanup retains changed weekly '+field,()=>{const data=intent();data.weekly[0][field]=value;assert.throws(()=>validateRows(data),/changed/);});
test('schedule cleanup rejects duplicate dates and refuses mutation before confirmed browser shutdown',()=>{const data=intent();data.dates.push({...data.dates[0]});assert.throws(()=>validateRows(data),/duplicate/);let writes=0;const fixture=ownedScheduleProvider({value:()=>creator,execute(){writes++;}},{testUser:'test'});assert.throws(()=>fixture.cleanup(),/shutdown/);assert.equal(writes,0);});
