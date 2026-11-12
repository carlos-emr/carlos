#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule provider Search > choose a provider with an empty schedule > weekday/time/code/limit >
// Submit > result row > Add Appointment. Only the slot handoff, no booking.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {parseSlot}=require('./appointment-search-history-playwright-checks');
const limit=process.env.APPOINTMENT_SEARCH_ACTION==='limit';
const name=limit?'appointment-search-result-limit':'appointment-search-weekday-time-code';
async function workflow(s){
  const {sql,marker}=s,q=h.sqlString;
  const provider=sql.value(`SELECT p.provider_no FROM provider p WHERE p.status='1' AND p.provider_no NOT LIKE '-%'
    AND NOT EXISTS(SELECT 1 FROM scheduledate d WHERE d.provider_no=p.provider_no AND d.sdate BETWEEN CURDATE() AND DATE_ADD(CURDATE(),INTERVAL 180 DAY)) ORDER BY p.provider_no LIMIT 1`),template=marker.slice(-20);
  h.assert(provider,'Need an active provider with no scheduled dates in the search window');
  const appointmentsBefore=sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${q(provider)}`);
  const first='B',second='2';
  h.assert(sql.value("SELECT COUNT(*) FROM scheduletemplatecode WHERE code IN ('B','2')")==='2','Need seeded B/2 schedule codes');
  s.cleanup(()=>{
    sql.execute(`DELETE FROM scheduledate WHERE provider_no=${q(provider)} AND hour=${q(template)};
      DELETE FROM scheduletemplate WHERE provider_no=${q(provider)} AND name=${q(template)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM scheduletemplate WHERE provider_no=${q(provider)} AND name=${q(template)})+(SELECT COUNT(*) FROM scheduledate WHERE provider_no=${q(provider)} AND hour=${q(template)})`)==='0','Owned search schedule remains');
  });
  const chars='_'.repeat(96).split('');
  for(const i of [36,37,38,39,52,53,54,55])chars[i]=limit?first:(i%2?second:first);
  sql.execute(`INSERT INTO scheduletemplate(provider_no,name,summary,timecode) VALUES(${q(provider)},${q(template)},${q(marker)},${q(chars.join(''))})`);
  const days=sql.rows(`SELECT DATE_FORMAT(DATE_ADD(CURDATE(),INTERVAL n DAY),'%Y-%m-%d'),DAYOFWEEK(DATE_ADD(CURDATE(),INTERVAL n DAY)) FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) x WHERE DAYOFWEEK(DATE_ADD(CURDATE(),INTERVAL n DAY)) BETWEEN 2 AND 6 AND NOT EXISTS (SELECT 1 FROM scheduledate d WHERE d.provider_no=${q(provider)} AND d.sdate=DATE_ADD(CURDATE(),INTERVAL n DAY)) ORDER BY n LIMIT 3`);
  h.assert(days.length===3,'Need three future weekdays');
  for(const [day]of days)sql.execute(`INSERT INTO scheduledate(sdate,provider_no,available,priority,reason,hour,creator,status)
    VALUES(${q(day)},${q(provider)},'1','a',${q(marker)},${q(template)},${q(s.provider)},'A')`);
  let search;
  async function run(){
    await ui.clickAndAwaitReload(search,search.locator('form[name="searchForm"] button[type="submit"]'));
    return Promise.all((await search.locator('tr[onclick^="selectSlot"]').all()).map(async row=>parseSlot(await row.getAttribute('onclick'))));
  }
  await s.step('the day-sheet Search control offers the owned provider and schedule codes',async()=>{
    search=await s.popup(s.schedule,s.schedule.locator(`input[name="searchview"][onclick*="'${s.provider}'"]`).first(),'appointment-filter-search');
    await search.locator('#provider_no').selectOption(provider);
    await search.locator('#startTime').selectOption('9');await search.locator('#endTime').selectOption('14');
    await search.locator('#numberOfResults').selectOption('10');
    await search.locator('#code').selectOption(first);
  });
  if(limit){
    await s.step('Number of Results limits the offered appointments to the selected maximum',async()=>{
      const results=await run();
      h.assert(results.length>0&&results.every(x=>x.provider===provider),'Search did not return the owned schedule');
      h.assert(results.length<=10,`Number of Results 10 returned ${results.length} slots`);
    });
  }else{
    await s.step('weekday and time range exclude other days and afternoon slots and code selects its duration',async()=>{
      await search.locator('#dayOfWeek').selectOption(days[0][1]);
      await search.locator('#endTime').selectOption('10');await search.locator('#code').selectOption(second);
      const results=await run();
      h.assert(results.length===2,'Weekday/time/code did not return exactly two slots');
      h.assert(results.every(x=>x.provider===provider&&x.date===days[0][0]&&['09:15','09:45'].includes(x.start)&&x.duration===30),'Result violates chosen provider/day/time/code/duration');
      for(const [id,value]of [['provider_no',provider],['dayOfWeek',days[0][1]],['startTime','9'],['endTime','10'],['code',second]])h.assert(await search.locator('#'+id).inputValue()===value,'Search lost a selected filter');
      const add=await s.popup(search,search.locator('tr[onclick^="selectSlot"]').first(),'filtered-slot-add');
      const u=new URL(add.url());
      h.assert(u.searchParams.get('provider_no')===provider&&u.searchParams.get('start_time')===results[0].start,'Filtered result opened another provider or time');
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${q(provider)}`)===appointmentsBefore,'Search or opening slot booked an appointment');
    });
  }
}
if(require.main===module)runWorkflow(name,workflow,{openPatient:false});
module.exports={workflow};
