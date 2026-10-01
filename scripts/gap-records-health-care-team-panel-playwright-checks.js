#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Health Care Team panel inside the Master Record edit form (gap-records, §2.4 master record).
 * health-care-team drives the detached editor (view=detached); this check enters the way a clinician does:
 * Schedule ▸ Search ▸ Master Record ▸ Edit ▸ Health Care Team panel (manageHealthCareTeam.jsp included by
 * edit-form-clinical.jsp) ▸ choose an internal provider ▸ Add ▸ Remove on the new row.
 * Asserts: Add writes one active DemographicContact (internal provider, professional category) for the
 * owned patient and lists it with a remove control; Remove deactivates it and keeps the provider; and, last,
 * that every label, heading, button and placeholder of the panel is a translated text (the panel's
 * <fmt:setBundle> sits inside the detached-only branch, so embedded it prints "???key???").
 * Fixtures: the runWorkflow FAKE- patient; the one association is deleted in cleanup and checked gone.
 * SKIPs when DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM is off (no panel rendered).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, patient, provider, master } = s;
  const owned = `demographicNo=${patient} AND contactId=${h.sqlString(provider)} AND type=0 AND category='professional'`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient}`) === '0', 'The owned team association was not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient}`) === '0', 'The owned patient already has contacts');
  await master.locator('#editBtn').click();
  await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
  if (!await master.locator('#addEditHealthCareTeam').count()) throw new h.SkipCheck('Enable DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM=true for the patient team panel');
  const panel = master.locator('#addEditHealthCareTeam');

  await s.step('Add puts the chosen internal provider on the owned patient\'s team and lists it', async () => {
    await panel.locator('#internalProviderList').selectOption(provider);
    await panel.locator('#addHealthCareTeamButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM DemographicContact WHERE ${owned} AND deleted=0`, '1', 'Add did not store exactly one active team member');
    await master.locator('#listHealthCareTeam input[value="remove"]').waitFor({ timeout: TIMEOUT });
    h.assert(await master.locator('#listHealthCareTeam input[value="remove"]').count() === 1, 'The new member is not listed with a remove control');
  });

  await s.step('Remove deactivates the membership without deleting the provider', async () => {
    await master.locator('#listHealthCareTeam input[value="remove"]').click();
    await master.locator('#listHealthCareTeam input[value="remove"]').waitFor({ state: 'detached', timeout: TIMEOUT });
    await expectValue(sql, `SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient} AND deleted=0`, '0', 'Remove left the membership active');
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(provider)}`) === '1', 'Remove deleted the provider');
  });

  await s.step('the panel shows translated labels, not unresolved message keys', async () => {
    const html = await master.locator('#editDemographic').evaluate(node => node.innerHTML);
    const keys = html.match(/\?\?\?[A-Za-z0-9_.]+\?\?\?/g) || [];
    h.assert(keys.length === 0, `The Health Care Team panel prints ${keys.length} unresolved message key(s), e.g. ${[...new Set(keys)].slice(0, 3).join(', ')}`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-health-care-team-panel', workflow, { openPatient: true });
