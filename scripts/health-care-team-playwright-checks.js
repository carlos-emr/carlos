#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  // runWorkflow creates this patient and verifies its unique marker before cleanup.
  h.assert(s.sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${s.patient}`) === '0',
    'The owned patient unexpectedly has existing contact associations');
  const ownedAssociation = `demographicNo=${s.patient} AND contactId=${h.sqlString(s.provider)} AND type=0 AND category='professional'`;
  s.cleanup(() => {
    const ids = s.sql.rows(`SELECT id FROM DemographicContact WHERE ${ownedAssociation}`).flat();
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned association identity');
    for (const id of ids) s.sql.execute(`DELETE FROM DemographicContact WHERE id=${id} AND ${ownedAssociation}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${s.patient}`) === '0',
      'Unexpected association retained with its parent for recovery');
  });
  if (!await s.master.locator('#healthCareTeam').count()) {
    throw new h.SkipCheck('Enable DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM=true for the patient team workflow');
  }
  const page = await s.context.newPage();
  // Use the supported detached editor, with the parent panel enabled for AJAX replies.
  const route = `/demographic/ViewManageHealthCareTeam?view=detached&demographicNo=${s.patient}`;
  await h.gotoApp(page, s.config.baseUrl, route);
  await page.locator('#internalProviderList').waitFor();
  await s.step('add the selected internal provider to the owned patient team', async () => {
    await page.locator('#internalProviderList').selectOption(s.provider);
    await page.locator('#addHealthCareTeamButton').click();
    await expectValue(s.sql, `SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${s.patient}
      AND contactId=${h.sqlString(s.provider)} AND type=0 AND category='professional' AND deleted=0`, '1', 'Internal provider was not added to the patient team');
    await page.locator('#listHealthCareTeam input[value="remove"]').waitFor();
  });
  await s.step('reopen the team in both display and edit views', async () => {
    const associationId = s.sql.value(`SELECT id FROM DemographicContact WHERE ${ownedAssociation} AND deleted=0`);
    h.assert(/^[1-9]\d*$/.test(associationId), 'Saved team membership has no valid identity');
    const providerName = s.sql.rows(`SELECT first_name,last_name FROM provider WHERE provider_no=${h.sqlString(s.provider)}`)[0];
    h.assert(providerName && providerName.every(Boolean), 'Selected provider has no display name');
    const display = await s.context.newPage();
    await h.gotoApp(display, s.config.baseUrl, `/demographic/ViewDisplayHealthCareTeam?view=detached&demographicNo=${s.patient}`);
    h.assert((await display.locator('body').innerText()).includes(s.marker), 'Team display opened the wrong patient');
    const member = display.locator(`#healthCareTeam li[id="${associationId}"] .info`);
    h.assert(await member.count() === 1, 'Team display omitted the saved provider row');
    const displayedName = await member.innerText();
    h.assert(providerName.every(part => displayedName.includes(part)), 'Team display showed the wrong provider');
    await h.gotoApp(page, s.config.baseUrl, route);
    h.assert(await page.locator('#listHealthCareTeam input[value="remove"]').count() === 1, 'Reopened team lost or duplicated the provider');
    await display.close();
  });
  await s.step('remove the team membership without deleting the provider', async () => {
    await page.locator('#listHealthCareTeam input[value="remove"]').click();
    // The database commit can precede the AJAX response. Wait for its rendered
    // result before navigating, so reopening cannot abort an in-flight removal.
    await page.locator('#listHealthCareTeam input[value="remove"]').waitFor({ state: 'detached' });
    await expectValue(s.sql, `SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${s.patient} AND deleted=0`, '0', 'Removed team membership remained active');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(s.provider)}`) === '1', 'Team removal deleted the provider');
    await h.gotoApp(page, s.config.baseUrl, route);
    h.assert(await page.locator('#listHealthCareTeam input[value="remove"]').count() === 0, 'Removed provider still appears after reopening');
  });
}
if (require.main === module) runWorkflow('health-care-team', workflow);
module.exports = { workflow };
