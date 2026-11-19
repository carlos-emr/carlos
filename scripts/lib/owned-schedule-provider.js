/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness'),{newOwnedMarker}=require('./owned-patient'),{throwawayLoginFixture}=require('./throwaway-login-fixture');
const START='2031-03-03',END='2031-03-28';
// SQL fixture writes do not evict ProviderDao's five-minute active-provider cache.
// Reload the real picker until it offers this run's provider; never select a shared one.
async function waitForScheduleProvider(loadFrame, provider, {now=Date.now,
  pause=ms=>new Promise(resolve=>setTimeout(resolve,ms)), checkpoint=()=>{}}={}) {
  h.assert(/^[1-9]\d{0,5}$/.test(String(provider)), 'Invalid owned schedule provider');
  const deadline=now()+330000;
  let frame=await loadFrame();
  while(frame && await frame.locator(`select[name="provider_no"] option[value="${provider}"]`).count()===0) {
    checkpoint();
    h.assert(now()<deadline, 'Schedule Setting never offered the owned provider after the active-provider cache expired; the login must be allowed to select another provider (check _admin.schedule.curprovider_only restrictions)');
    await pause(15000); checkpoint(); frame=await loadFrame();
  }
  return frame;
}
function validateRows({weekly,dates,provider,creator,template}) {
  h.assert(weekly.length<=1,'Unexpected owned weekly schedules; retain them');
  const hours=['MON','TUE','WED','THU','FRI'].map(day=>`<${day}>${template}</${day}>`).join('');
  const common=row=>row.provider_no===provider&&row.creator===creator&&row.available==='1'&&row.status==='A';
  for(const row of weekly)h.assert(template&&common(row)&&row.sdate===START&&row.edate===END&&row.day_of_week==='2 3 4 5 6 '&&row.avail_hourB===''&&row.avail_hour===hours,'Owned weekly schedule changed; retain it');
  h.assert(new Set(dates.map(row=>row.sdate)).size===dates.length,'Unexpected duplicate owned schedule dates');
  for(const row of dates){const day=new Date(row.sdate+'T12:00:00Z').getUTCDay();h.assert(template&&common(row)&&row.sdate>=START&&row.sdate<=END&&day>=1&&day<=5&&row.priority==='b'&&row.reason===''&&row.hour===template,'Owned schedule date changed; retain it');}
}
function ownedScheduleProvider(sql,config) {
  const q=h.sqlString,sourceProvider=sql.value(`SELECT provider_no FROM security WHERE user_name=${q(config.testUser)}`),creator=sql.value(`SELECT CONCAT(last_name,',',first_name) FROM provider WHERE provider_no=${q(sourceProvider)}`),marker=newOwnedMarker();
  let fixture=null,template=null;
  const count=provider=>sql.value(`SELECT (SELECT COUNT(*) FROM rschedule WHERE provider_no=${q(provider)})+(SELECT COUNT(*) FROM scheduledate WHERE provider_no=${q(provider)})`);
  return {get providerNo(){return fixture?.providerNo;},expectTemplate(value){h.assert(typeof value==='string'&&value.length>0&&value.length<=255,'Invalid offered schedule template');template=value;},
    create(){
      for(let attempt=0;attempt<5;attempt++){
        fixture=throwawayLoginFixture({sql,marker,provider:sourceProvider,testUser:config.testUser,reserveSchedule:true});fixture.create();
        if(count(fixture.providerNo)==='0')return;
        fixture.cleanup();fixture=null;
      }
      throw new Error('No throwaway provider free of schedule rows');
    },
    cleanup({browserClosed=false}={}){
      h.assert(browserClosed,'Browser shutdown is unconfirmed; retain the schedule fixture');if(!fixture)return;
      const provider=fixture.providerNo;h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(provider)} AND last_name=${q(marker)}`)==='1','Owned schedule provider changed; retain its rows');
      const read=table=>{const columns=sql.rows('SHOW COLUMNS FROM '+table).map(row=>row[0]);return sql.rows(`SELECT * FROM ${table} WHERE provider_no=${q(provider)}`).map(row=>Object.fromEntries(columns.map((name,index)=>[name,row[index]])));};
      validateRows({weekly:read('rschedule'),dates:read('scheduledate'),provider,creator,template});
      sql.execute(`DELETE FROM scheduledate WHERE provider_no=${q(provider)};DELETE FROM rschedule WHERE provider_no=${q(provider)}`);
      h.assert(count(provider)==='0','Owned schedule rows remain');fixture.cleanup();fixture=null;
    },
  };
}
module.exports={ownedScheduleProvider,validateRows,waitForScheduleProvider};
