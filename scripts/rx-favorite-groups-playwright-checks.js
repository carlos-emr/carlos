#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
async function workflow(s) {
  const {sql,patient,provider,marker,config} = s, q=h.sqlString;
  const foreign='FAKEPW';
  const favorite=h.insertId(sql,`INSERT INTO favorites(provider_no,favoritename,customName,GCN_SEQNO,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,nosubs,prn,special,dispenseInternal)
    VALUES(${q(provider)},${q(marker+' favourite')},${q(marker+' medication')},0,1,1,'OID','30','D','30',0,0,0,'1 tab PO OD',0)`,'owned favourite');
  s.cleanup(()=>sql.execute(`DELETE FROM favorites WHERE favoriteid=${favorite} AND provider_no=${q(provider)} AND favoritename=${q(marker+' favourite')}`));
  s.cleanup(()=>sql.execute(`DELETE FROM rx_favorite_group WHERE name LIKE ${q(marker+'%')} AND provider_no IN (${q(provider)},${q(foreign)})`));
  const foreignGroup=h.insertId(sql,`INSERT INTO rx_favorite_group(provider_no,name,position) VALUES(${q(foreign)},${q(marker+' private')},0)`,'private group control');
  const page=await s.context.newPage();
  await h.gotoApp(page,config.baseUrl,`/rx/choosePatient?demographicNo=${patient}`);
  await page.locator('#searchString').waitFor();
  await page.locator('a[href*="/rx/favoriteGroups?"]').first().click();
  await page.getByRole('heading',{name:'Favourite groups',exact:true}).waitFor();
  async function create(name) {
    const form=page.locator('form').filter({has:page.locator('input[name="command"][value="create"]')});
    await form.locator('input[name="name"]').fill(name);
    await Promise.all([page.waitForURL(/favoriteGroups\?/, {waitUntil:'networkidle'}),form.getByRole('button',{name:'Create group',exact:true}).click()]);
    await page.locator('details summary').filter({hasText:name}).waitFor();
    return sql.value(`SELECT id FROM rx_favorite_group WHERE provider_no=${q(provider)} AND name=${q(name)}`);
  }
  let first,second;
  await s.step('native CSRF-protected forms create, rename, reorder and assign groups without changing templates',async()=>{
    first=await create(marker+' one'); second=await create(marker+' two');
    let group=page.locator('details').filter({has:page.locator(`input[name="groupId"][value="${first}"]`)});
    await group.locator('input[name="name"]').fill(marker+' renamed');
    await group.getByRole('button',{name:'Rename',exact:true}).click();
    await page.locator('summary').filter({hasText:marker+' renamed'}).waitFor();
    group=page.locator('details').filter({has:page.locator(`input[name="groupId"][value="${second}"]`)});
    await group.getByRole('button',{name:'Move up',exact:true}).click();
    await page.waitForLoadState('networkidle');
    h.assert(Number(sql.value(`SELECT position FROM rx_favorite_group WHERE id=${second}`))<Number(sql.value(`SELECT position FROM rx_favorite_group WHERE id=${first}`)),'Group reordering did not persist');
    const assignment=page.locator('form').filter({has:page.locator(`input[name="favoriteId"][value="${favorite}"]`)});
    await assignment.locator(`input[name="groupIds"][value="${first}"]`).check();
    await assignment.locator(`input[name="groupIds"][value="${second}"]`).check();
    await assignment.getByRole('button',{name:'Save groups',exact:true}).click();
    await page.waitForLoadState('networkidle');
    h.assert(sql.value(`SELECT COUNT(*) FROM rx_favorite_group_member WHERE favorite_id=${favorite}`)==='2','Memberships did not persist');
    h.assert(sql.value(`SELECT special FROM favorites WHERE favoriteid=${favorite}`)==='1 tab PO OD','Grouping changed the medication template');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='0','Group management prescribed medication');
  });
  await s.step('ownership, method and CSRF boundaries refuse forged group changes before writes',async()=>{
    const csrf=await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const url=`${config.baseUrl}/rx/favoriteGroups`;
    for(const [command,extra] of [['assign',{favoriteId:favorite,groupIds:foreignGroup}],['delete',{groupId:foreignGroup}]]) {
      const result=await s.context.request.post(url,{form:{command,demographicNo:patient,...extra},headers:{'CSRF-TOKEN':csrf,'X-Requested-With':'XMLHttpRequest'}});
      h.assert(result.status()===404,`A foreign group was not refused (HTTP ${result.status()})`);
    }
    const get=await s.context.request.get(url,{params:{demographicNo:patient,command:'delete',groupId:first}});
    h.assert(get.status()===405,'A GET changed groups');
    const csrfRefusal=await s.context.request.post(url,{form:{demographicNo:patient,command:'delete',groupId:first}});
    h.assert(csrfRefusal.status()===403,'Group changes without CSRF were accepted');
    h.assert(sql.value(`SELECT COUNT(*) FROM rx_favorite_group_member WHERE favorite_id=${favorite}`)==='2','Rejected changes altered memberships');
  });
  await s.step('the sidebar collapses groups and group deletion leaves the favourite available in Ungrouped',async()=>{
    await h.gotoApp(page,config.baseUrl,`/rx/choosePatient?demographicNo=${patient}`);
    const group=page.locator('details.rx-favorite-group').filter({has:page.locator('summary').filter({hasText:marker+' renamed'})});
    await group.locator('summary').click(); h.assert(await group.getAttribute('open')===null,'The sidebar group did not collapse');
    await page.locator('a[href*="/rx/favoriteGroups?"]').first().click();
    for(const id of [first,second]) {
      await page.locator('details').filter({has:page.locator(`input[name="groupId"][value="${id}"]`)}).getByRole('button',{name:'Delete group',exact:true}).click();
      await page.waitForLoadState('networkidle');
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM favorites WHERE favoriteid=${favorite}`)==='1','Deleting groups deleted the favourite');
    h.assert(sql.value(`SELECT COUNT(*) FROM rx_favorite_group_member WHERE favorite_id=${favorite}`)==='0','Deleted group memberships remain');
    const ungrouped=page.locator('details').filter({has:page.locator('summary').filter({hasText:/^Ungrouped$/})});
    const selected=ungrouped.getByRole('link',{name:marker+' favourite',exact:true});
    await selected.click();
    await page.locator('input[name^="drugName_"]').first().waitFor();
    h.assert(await page.locator('input[name^="drugName_"]').count()===1,'Choosing one favourite staged multiple medications');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='0','Choosing a favourite saved medication without Save');
  });
}
if(require.main===module)runWorkflow('rx-favorite-groups',workflow,{openPatient:true});
module.exports={workflow};
