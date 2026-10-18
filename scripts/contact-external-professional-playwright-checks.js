#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4987: a professional contact added from the Contacts editor's External row's search is saved as an
// external professional contact (a Contact row), is chosen for that row, can be found by the External search, and
// links to the patient when the editor is saved. The Health Care Team panel's "Add/Edit Professional Contact"
// link still creates professional specialists.
const { assert, sqlString, gotoApp } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const EXTERNAL = '2';
const SPECIALIST = '3';

async function workflow(s) {
  const { sql, patient, marker } = s;
  // professionalSpecialists.lName is VARCHAR(32); the 23-character marker plus this suffix fits, with an apostrophe
  // to keep the hand-back to the editor honest about quoting.
  const lastName = `${marker}-O'N`;
  const contactRows = () => `SELECT COUNT(*) FROM Contact WHERE type='ProfessionalContact'
    AND lastName=${sqlString(lastName)} AND firstName='External' AND deleted=0`;
  const specialistRows = () => `SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${sqlString(lastName)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient}`);
    sql.execute(`DELETE FROM Contact WHERE type='ProfessionalContact' AND lastName=${sqlString(lastName)}`);
    sql.execute(`DELETE FROM professionalSpecialists WHERE lName=${sqlString(lastName)}`);
    assert(sql.value(`${contactRows().replace(' AND deleted=0', '')}`) === '0'
      && sql.value(specialistRows()) === '0', 'Owned professional contact fixtures were not removed');
  });

  let editor;
  const open = async () => {
    // A popup with the same window name is reused, not reopened.
    if (editor && !editor.isClosed()) await editor.close();
    editor = await s.popup(s.master, masterRecordLink(s.master, 'Contact?method=manage').first(), 'contacts');
  };
  const pro = (name, row = 1) => editor.locator(`[name="procontact_${row}.${name}"]`);
  const save = async () => {
    await editor.locator('input[type="submit"]').click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
  };
  const openExternalSearch = async (row = 1) => {
    await pro('type', row).selectOption(EXTERNAL);
    const search = await s.popup(editor, editor.locator(`#procontact_${row} a[onclick*="doProfessionalSearch"]`), 'external-professional-search');
    await search.locator('input[name="keyword"][type="text"]').fill(marker);
    await clickAndAwaitReload(search, search.locator('input[type="submit"]').first());
    return search;
  };

  await s.step('the Add link opens the form for the type the External row asked for', async () => {
    await open();
    await editor.locator('a[onclick="addProContact();"]').click();
    const search = await openExternalSearch();
    const add = search.getByRole('link', { name: 'Add/Edit Professional Contact' });
    const href = new URL(await add.getAttribute('href'), search.url());
    assert(href.searchParams.get('contactType') === EXTERNAL, `External row's Add link asks for contactType=${href.searchParams.get('contactType')}`);
    assert(await search.locator('form[name="titlesearch"] input[name="contactType"]').inputValue() === EXTERNAL,
      'A repeated search from the External row lost its contact type');
    await search.close();
  });

  await s.step('contact type falls back to the Health Care Team behaviour for anything but External', async () => {
    const probe = await s.context.newPage();
    const contextPath = `/${new URL(s.master.url()).pathname.split('/')[1]}`;
    for (const [query, expected] of [
      ['', SPECIALIST], ['&contactType=3', SPECIALIST], ['&contactType=2', EXTERNAL],
      ['&contactType=0', SPECIALIST], ['&contactType=2x', SPECIALIST], ['&contactType=99', SPECIALIST],
    ]) {
      await probe.goto(new URL(`${contextPath}/demographic/ViewProContactSearch?form=updatedelete&elementName=contactName&elementId=contactId${query}`,
        s.master.url()).toString());
      const href = new URL(await probe.getByRole('link', { name: 'Add/Edit Professional Contact' }).getAttribute('href'), probe.url());
      assert(href.searchParams.get('contactType') === expected,
        `contactType "${query}" produced Add link type ${href.searchParams.get('contactType')}, not ${expected}`);
    }
    await probe.close();
  });

  await s.step('a contact added from the External search is saved as an external professional contact and chosen for the row', async () => {
    await open();
    await editor.locator('a[onclick="addProContact();"]').click();
    const search = await openExternalSearch();
    await search.getByRole('link', { name: 'Add/Edit Professional Contact' }).click();
    await search.locator('#addEditProfessionalForm').waitFor();
    await search.locator('[id="pcontact.lastName"]').fill(lastName);
    await search.locator('[id="pcontact.firstName"]').fill('External');
    await search.locator('[id="pcontact.workPhone"]').fill('555-0100');
    await search.locator('input[name="submitbtn"]').click();
    if (!search.isClosed()) await search.waitForEvent('close');
    assert(sql.value(contactRows()) === '1', 'The External Add did not create one external professional contact');
    assert(sql.value(specialistRows()) === '0', 'The External Add created a professional specialist');
    const contact = sql.value(`SELECT id FROM Contact WHERE type='ProfessionalContact' AND lastName=${sqlString(lastName)}`);
    assert(await pro('contactId').inputValue() === contact, 'The saved contact was not chosen for the External row');
    assert(await pro('contactName').inputValue() === `${lastName},External`, 'The row shows the wrong name for the saved contact');
    assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient}`) === '0',
      'Choosing the contact linked it before the Contacts editor was saved');
    await save();
    await expectValue(sql, `SELECT CONCAT(type,'|',category,'|',contactId) FROM DemographicContact
      WHERE demographicNo=${patient} AND deleted=0`, `2|professional|${contact}`,
      'The saved external professional contact was not linked to the patient');
  });

  await s.step('the External search lists the contact that was just added', async () => {
    await open();
    // The patient's saved association is row 1; the new row is row 2.
    await editor.locator('a[onclick="addProContact();"]').click();
    const search = await openExternalSearch(2);
    const result = search.locator('tr[onclick]').filter({ hasText: 'External' });
    assert(await result.count() === 1, 'The External search did not list the contact that was just added');
    await result.click();
    assert(await pro('contactName', 2).inputValue() === `${lastName},External`, 'The listed contact could not be chosen');
    await editor.locator('[name="cancel"]').click();
  });

  await s.step('the Health Care Team panel still adds a new external contact as a professional specialist', async () => {
    if (!await s.master.locator('#healthCareTeam').count()) return; // panel disabled; the other steps stand
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient}`);
    const panel = await s.context.newPage();
    await gotoApp(panel, s.config.baseUrl, `/demographic/ViewManageHealthCareTeam?view=detached&demographicNo=${patient}`);
    await panel.locator('#searchInternalExternal').selectOption({ index: 1 });
    await panel.locator('#searchHealthCareTeamInput').fill(marker);
    const search = await s.popup(panel, panel.locator('#searchHealthCareTeamButton'), 'health-care-team-search');
    const add = search.getByRole('link', { name: 'Add/Edit Professional Contact' });
    const href = new URL(await add.getAttribute('href'), search.url());
    assert(href.searchParams.get('contactType') === SPECIALIST, 'The Health Care Team Add link no longer asks for a specialist');
    await add.click();
    await search.locator('#addEditProfessionalForm').waitFor();
    await search.locator('[id="pcontact.lastName"]').fill(lastName);
    await search.locator('[id="pcontact.firstName"]').fill('Team');
    await search.locator('[id="pcontact.workPhone"]').fill('555-0101');
    await search.locator('input[name="submitbtn"]').click();
    // The panel's popUpData hands the contact to its own AJAX save and the popup stays open; wait on the rows.
    await expectValue(sql, `SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${sqlString(lastName)} AND fName='Team'`, '1',
      'The Health Care Team Add did not create a professional specialist');
    await expectValue(sql, `SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient}
      AND type=3 AND category='professional' AND deleted=0`, '1', 'The specialist was not put on the patient\'s team');
    await panel.close();
  });
}
if (require.main === module) runWorkflow('contact-external-professional', workflow);
module.exports = { workflow };
