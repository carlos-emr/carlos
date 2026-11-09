#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {masterRecordLink}=require('./lib/master-record-link');
const {selectTicklerAssignee}=require('./lib/tickler-clinic');
const {waitForTicklerTable}=require('./tickler-forward-filters-playwright-checks');

/** Exercise shipped Add/Edit controls and verify their exact persisted calendar dates. */
async function workflow(s) {
 const {sql,patient,provider,marker}=s,q=h.sqlString,message=marker+' calendar clamp';
 const owned=`demographic_no=${patient} AND message=${q(message)} AND creator=${q(provider)}`;
 s.cleanup(()=>{
  const ids=sql.rows(`SELECT tickler_no FROM tickler WHERE ${owned}`).flat();
  h.assert(ids.every(id=>/^[1-9]\d*$/.test(id)),'Ambiguous calendar fixture IDs');
  if(ids.length)sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids});
   DELETE FROM tickler_link WHERE tickler_no IN (${ids});DELETE FROM tickler_comments WHERE tickler_no IN (${ids});
   DELETE FROM tickler_update WHERE tickler_no IN (${ids});DELETE FROM tickler WHERE ${owned}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${owned}`)==='0','Calendar fixture remains');
 });
 const list=await s.popup(s.master,masterRecordLink(s.master,'/tickler/ViewTicklerMain').first(),'calendar-task-list');
 await waitForTicklerTable(list);
 let id;
 await s.step('Add clamps month/year offsets and preserves cumulative, subtract, Clear and manual-base behavior',async()=>{
  const add=await s.popup(list,list.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(),'calendar-add');
  await add.locator('#ticklerMessage').waitFor();
  await add.clock.setFixedTime(new Date('2027-01-31T12:00:00Z'));
  const date=add.locator('input[name="xml_appointment_date"]'),oneMonth=add.getByRole('button',{name:'1m',exact:true});
  const expect=async value=>h.assert(await date.inputValue()===value,'Add quick pick produced a different calendar date');
  await date.fill('2027-01-31');await oneMonth.click();await expect('2027-02-28');
  await oneMonth.click();await expect('2027-03-31');
  await oneMonth.click({modifiers:['Shift']});await expect('2027-02-28');
  await oneMonth.click({button:'right'});await expect('2027-01-31');
  await add.getByRole('button',{name:'Clear',exact:true}).click();await expect('2027-01-31');
  await date.fill('2028-02-29');await add.getByRole('button',{name:'1y',exact:true}).click();await expect('2029-02-28');
  await oneMonth.click();await expect('2029-03-29');
  await date.fill('2027-01-31');await oneMonth.click();await expect('2027-02-28');
  await selectTicklerAssignee(add,provider);await add.locator('#ticklerMessage').fill(message);
  const [response]=await Promise.all([
   add.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/tickler/DbTicklerAdd')),
   add.waitForEvent('close'),add.locator('form[name="serviceform"] input.btn-primary[name="Button"]').first().click(),
  ]);
  h.assert(response.status()===200,'Calendar task did not save');
  h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${owned} AND DATE_FORMAT(service_date,'%Y-%m-%d')='2027-02-28' AND task_assigned_to=${q(provider)}`)==='1','Add stored another date, patient or recipient');
  id=sql.value(`SELECT tickler_no FROM tickler WHERE ${owned}`);h.assert(/^[1-9]\d*$/.test(id),'Calendar task identity missing');
 });
 await s.step('Edit quick picks remain based on today, clamp leap years and save the exact resulting date',async()=>{
  await list.locator('#xml_vdate').fill('2027-02-28');await list.locator('#xml_appointment_date').fill('2027-02-28');
  await Promise.all([list.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/tickler/ListTicklers')),list.locator('#formSubmitBtn').click()]);
  await waitForTicklerTable(list);
  await Promise.all([list.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/tickler/ListTicklers')&&new URL(r.url()).searchParams.get('search[value]')===marker),list.locator('#ticklerResults_filter input').fill(marker)]);
  await waitForTicklerTable(list);
  const row=list.locator('#ticklerResults tbody tr').filter({hasText:message});
  const edit=await s.popup(list,row.locator('a[onclick*="openTicklerEdit"]'),'calendar-edit');
  await edit.locator('#xml_appointment_date').waitFor();
  await edit.clock.setFixedTime(new Date('2027-01-31T12:00:00Z'));
  const date=edit.locator('#xml_appointment_date');
  await edit.locator('a').filter({hasText:/^1m$/}).click();
  h.assert(await date.inputValue()==='2027-02-28','Edit rolled January into March');
  await edit.clock.setFixedTime(new Date('2028-02-29T12:00:00Z'));
  await edit.locator('a').filter({hasText:/^1y$/}).click();
  h.assert(await date.inputValue()==='2029-02-28','Edit year offset did not clamp leap day');
  await edit.locator('a').filter({hasText:/^1m$/}).click();
  h.assert(await date.inputValue()==='2028-03-29','Edit unexpectedly accumulated offsets');
  await edit.locator('a').filter({hasText:/^1y$/}).click();
  const [response]=await Promise.all([
   edit.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/tickler/EditTickler')),
   edit.waitForEvent('close'),edit.locator('input[name="updateTickler"]').click(),
  ]);
  h.assert(response.status()===200&&sql.value(`SELECT DATE_FORMAT(service_date,'%Y-%m-%d') FROM tickler WHERE tickler_no=${id}`)==='2029-02-28','Edit did not store the chosen clamped date');
 });
}
if(require.main===module)runWorkflow('tickler-calendar-quick-picks',workflow);
module.exports={workflow};
