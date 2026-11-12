#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Health Care Team: a new external professional added through the panel's search, then that member's Edit.
 * gap-records-health-care-team-panel covers Add and Remove of an internal provider only.
 * User path: Master Record ▸ Edit ▸ Health Care Team ▸ External ▸ Search ▸ Add/Edit Professional Contact ▸ the form ▸
 * Save; the member's edit ▸ the form ▸ another specialty and phone ▸ Save; the same Add for a contact named O'Brien.
 * Asserts: Save stores the professional (a professional specialist) and puts it on the patient's team with the chosen
 * specialty as its role, listed with an edit control; the member's edit opens the form filled in with that contact,
 * and Save stores the new specialty on the same team row and the new phone on the same professional, and the list
 * shows the new specialty; LAST, a contact named O'Brien is put on the team the same way.
 * Requires: DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM=true (the packaged default).
 * Fixtures: the runWorkflow FAKE- patient; the professional records it creates (keyed to the marker) and the patient's
 * team rows are removed in cleanup.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const TYPE_CONTACT = '2'; // the panel's External choice

async function workflow(s) {
  const { sql, patient, marker, master, context } = s;
  const q = h.sqlString;
  const plainName = `${marker}Hct`;
  const apostropheName = `${marker}O'Brien`.slice(0, 30);
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient};
      DELETE FROM professionalSpecialists WHERE lName IN (${q(plainName)},${q(apostropheName)});
      DELETE FROM Contact WHERE lastName IN (${q(plainName)},${q(apostropheName)})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName IN (${q(plainName)},${q(apostropheName)}))
      + (SELECT COUNT(*) FROM Contact WHERE lastName IN (${q(plainName)},${q(apostropheName)}))`) === '0',
    'The owned professionals or the patient\'s team rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient}`) === '0', 'The owned patient already has contacts');
  await master.locator('#editBtn').click();
  await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
  if (!await master.locator('#addEditHealthCareTeam').count()) throw new h.SkipCheck('Enable DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM=true for the patient team panel');
  const panel = master.locator('#addEditHealthCareTeam');
  const list = master.locator('#listHealthCareTeam');
  const specialist = name => sql.rows(`SELECT specId, IFNULL(specType,''), IFNULL(phone,'') FROM professionalSpecialists WHERE lName=${q(name)}`);
  const team = id => sql.rows(`SELECT id, type, IFNULL(role,''), deleted FROM DemographicContact WHERE demographicNo=${patient}
    AND category='professional' AND contactId=${q(id)}`);
  const specialtyOptions = sql.rows('SELECT id, TRIM(specialty) FROM ContactSpecialty ORDER BY id LIMIT 2');

  /** External ▸ Search ▸ Add/Edit Professional Contact ▸ the form ▸ Save, for a new professional. */
  async function addNew(name, specialty) {
    if (!await panel.locator('#searchHealthCareTeamButton').isVisible()) await panel.locator('#searchInternalExternal').selectOption(TYPE_CONTACT);
    const search = await s.popup(master, panel.locator('#searchHealthCareTeamButton'), 'team-search');
    await search.locator('a[href*="method=addProContact"]').first().click();
    await search.locator('[name="pcontact.lastName"]').waitFor({ timeout: TIMEOUT });
    await search.locator('[name="pcontact.lastName"]').fill(name);
    await search.locator('[name="pcontact.firstName"]').fill('Team');
    await search.locator('select[name="pcontact.specialty"]').selectOption(specialty);
    await search.locator('[name="pcontact.workPhone"]').fill('416-555-0150');
    await Promise.all([search.waitForEvent('load'), search.locator('input[name="submitbtn"]').click()]);
    await master.waitForTimeout(1500); // the form hands the contact to the panel, which saves it and redraws the list
    if (!search.isClosed()) await search.close();
  }

  let specId;
  let memberId;
  const [first, second] = specialtyOptions;
  h.assert(first && second, 'The specialty list has fewer than two choices');

  await s.step('a new external professional is saved and put on the team with its specialty, listed with an edit control', async () => {
    await addNew(plainName, first[0]);
    const pros = specialist(plainName);
    h.assert(pros.length === 1 && pros[0][1] === first[0] && pros[0][2] === '416-555-0150', `The saved professional is ${JSON.stringify(pros)}`);
    [specId] = pros[0];
    const rows = team(specId);
    h.assert(rows.length === 1 && rows[0][1] === '3' && rows[0][2] === first[0] && rows[0][3] === '0', `The team rows are ${JSON.stringify(rows)}`);
    [memberId] = rows[0];
    await list.locator(`input.actionlink[value="edit"][id$="_${memberId}"]`).waitFor({ timeout: TIMEOUT });
    h.assert((await list.innerText()).includes(plainName), 'The team list does not show the new professional');
  });

  await s.step('the member\'s edit opens it filled in, and Save stores the new specialty and phone on the same rows', async () => {
    const [form] = await Promise.all([
      context.waitForEvent('page', { timeout: TIMEOUT }),
      list.locator(`input.actionlink[value="edit"][id$="_${memberId}"]`).click(),
    ]);
    await form.locator('[name="pcontact.lastName"]').waitFor({ timeout: TIMEOUT });
    h.assert(await form.locator('[name="pcontact.lastName"]').inputValue() === plainName, 'The edit form is not filled in with the member');
    h.assert(await form.locator('select[name="pcontact.specialty"]').inputValue() === first[0], 'The edit form does not show the member\'s specialty');
    h.assert(await form.locator('[name="demographicContactId"]').inputValue() === memberId, 'The edit form is not tied to the member\'s team row');
    await form.locator('select[name="pcontact.specialty"]').selectOption(second[0]);
    await form.locator('[name="pcontact.workPhone"]').fill('416-555-0188');
    await Promise.all([form.waitForEvent('load'), form.locator('input[name="submitbtn"]').click()]);
    await master.waitForTimeout(1500);
    if (!form.isClosed()) await form.close();
    await expectValue(sql, `SELECT CONCAT(IFNULL(specType,''),'|',IFNULL(phone,'')) FROM professionalSpecialists WHERE specId=${specId}`,
      `${second[0]}|416-555-0188`, 'The edit did not store the new specialty and phone on the same professional');
    const rows = team(specId);
    h.assert(rows.length === 1 && rows[0][0] === memberId && rows[0][2] === second[0] && rows[0][3] === '0',
      `After the edit the team rows are ${JSON.stringify(rows)}`);
    h.assert((await list.innerText()).includes(second[1]), `The team list does not show the new specialty ${second[1]}`);
  });

  await s.step('a professional named O\'Brien is put on the team the same way', async () => {
    await addNew(apostropheName, first[0]);
    const pros = specialist(apostropheName);
    h.assert(pros.length === 1, `Save stored ${pros.length} professionals named O'Brien`);
    const rows = team(pros[0][0]);
    h.assert(rows.length === 1 && rows[0][3] === '0', `The professional named O'Brien is not on the team: ${JSON.stringify(rows)}`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-health-care-team-edit', workflow, { openPatient: true });
