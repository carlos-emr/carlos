#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Master Record > Edit > embedded Health Care Team. The existing team check
// opens detached URLs and adds one internal provider. Here real patient controls
// exercise two memberships, removal scoping, and the external search callback.
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const external = process.env.TEAM_ACTION === 'external';
const name = external ? 'health-care-team-external-picker' : 'health-care-team-embedded-actions';
async function workflow(s) {
  const {sql, patient, marker} = s;
  let contact;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient};
      DELETE FROM demographicExtArchive WHERE demographic_no=${patient};
      DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    if (contact) sql.execute(`DELETE FROM Contact WHERE id=${contact} AND lastName=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient}`) === '0', 'Owned team remains');
  });
  const master = s.master;
  await s.step('Edit opens the embedded patient team through the Master Record', async () => {
    await master.locator('#editBtn').click();
    await master.locator('#internalProviderList').waitFor({state:'visible'});
  });
  const count = () => sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient} AND deleted=0`);
  async function add(provider) {
    await master.locator('#internalProviderList').selectOption(provider);
    const [response] = await Promise.all([
      master.waitForResponse(r => new URL(r.url()).pathname.endsWith('/demographic/Contact') && r.request().method()==='POST'),
      master.locator('#addHealthCareTeamButton').click(),
    ]);
    h.assert(response.status()===200, 'Adding team member failed');
  }
  if (external) {
    contact=h.insertId(sql, `INSERT INTO Contact(type,lastName,firstName,deleted,systemId) VALUES('ProfessionalContact',${h.sqlString(marker)},'External',0,'2')`, 'external contact');
    await s.step('External search selects the owned directory contact into the embedded team', async () => {
      await master.locator('#searchInternalExternal').selectOption('2');
      await master.locator('#searchHealthCareTeamInput').fill(marker);
      const search=await s.popup(master,master.locator('#searchHealthCareTeamButton'),'team-external-search');
      const row=search.locator('tr[onclick*="selectResult"]').filter({hasText:marker});
      await row.waitFor({state:'visible'});
      await row.click();
      await expectValue(sql,`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient} AND contactId=${h.sqlString(contact)} AND type=2 AND deleted=0`,'1','The external picker did not add the selected contact');
      h.assert(count()==='1','External selection added extra team members');
      await master.locator('#listHealthCareTeam tr').filter({hasText:marker}).waitFor({state:'visible'});
    });
  } else {
    let providers;
    await s.step('two internal additions persist and render separate patient team memberships',async()=>{
      providers=await master.locator('#internalProviderList option').evaluateAll(o=>o.map(x=>x.value).filter(Boolean).slice(0,2));
      h.assert(providers.length===2,'Need two active providers');
      for(const provider of providers) await add(provider);
      h.assert(count()==='2','Two additions did not create two memberships');
      await master.waitForFunction(()=>document.querySelectorAll('#listHealthCareTeam input[value="remove"]').length===2);
      h.assert(await master.locator('#listHealthCareTeam input[value="remove"]').count()===2,'Embedded team omitted or duplicated members');
    });
    await s.step('removing one embedded team member preserves the other membership and both providers',async()=>{
      const id=sql.value(`SELECT id FROM DemographicContact WHERE demographicNo=${patient} AND contactId=${h.sqlString(providers[0])} AND deleted=0`);
      await master.locator(`#listHealthCareTeam input[id$="_${id}"][value="remove"]`).click();
      await master.locator(`#listHealthCareTeam input[id$="_${id}"]`).waitFor({state:'detached'});
      h.assert(count()==='1','Removing one member affected the wrong number of memberships');
      h.assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient} AND contactId=${h.sqlString(providers[1])} AND deleted=0`)==='1','Unchecked member was removed');
      h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no IN (${providers.map(h.sqlString).join(',')})`)==='2','Removal affected provider records');
    });
  }
}
if(require.main===module) runWorkflow(name,workflow);
module.exports={workflow};
