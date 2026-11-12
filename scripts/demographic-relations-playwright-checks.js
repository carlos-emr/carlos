#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Master Record ▸ Other Contacts ▸ Add Relation: link another patient, then delete the link.
 *
 * With the legacy contacts UI (NEW_CONTACTS_UI off, the default), the Master Record's Other
 * Contacts box offers "Add Relation" (demographic/AddRelation, AddAlternateContact.jsp): search for
 * the other patient, pick them, set the relation, substitute decision-maker, emergency contact and
 * notes, and save; each relation listed there has a Delete (demographic/DeleteRelation).
 * demographic-relations-pdf-labels leaves both routes out, and nothing else drove them.
 *
 * User path: Master Record ▸ Add Relation (popup) ▸ name search ▸ the second owned patient ▸
 * relation, SDM, emergency contact, notes ▸ Add Relationship; Master Record (reload); Add Relation
 * ▸ Delete (confirm).
 *
 * Asserted: the search lists the second owned patient and picking them opens the relation form for
 * the two owned patients; a GET save is refused with 405 and writes nothing; Add Relationship saves
 * one relationships row (both patients, the chosen relation, SDM and emergency flags set, the notes,
 * not deleted) and lists it; the Master Record shows the relative under Other Contacts; Delete marks
 * the row deleted and neither page lists it any more.
 *
 * Fixtures: the workflow's own FAKE patient and a second FAKE patient (last name = the run marker
 * plus "R"). Cleanup deletes every relationships row of either patient and the second patient, and
 * asserts they are gone. Skipped when the Master Record has no Add Relation (NEW_CONTACTS_UI on),
 * and when caisi is on: the search results then pick with addNameCaisi, which fills a field of the
 * opener and closes the popup instead of opening the relation form.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const TIMEOUT = 30000;
// The relation list is a table nested in a row of the page's layout table, so a relation's text is
// inside two rows; only the innermost row is the relation's own.
const RELATION_ROW = 'tr:not(:has(tr))';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const relativeName = `${marker}R`;
  let relative = '';
  s.cleanup(() => {
    const owned = relative ? `${patient},${relative}` : `${patient}`;
    sql.execute(`DELETE FROM relationships WHERE demographic_no IN (${owned}) OR relation_demographic_no IN (${owned})`);
    if (relative) {
      sql.execute(`DELETE FROM demographicArchive WHERE demographic_no=${relative};
        DELETE FROM demographic WHERE demographic_no=${relative} AND last_name=${q(relativeName)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM relationships WHERE demographic_no IN (${owned}) OR relation_demographic_no IN (${owned}))
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${q(relativeName)})`) === '0', 'The owned relationship rows or the second patient remain');
  });

  const addRelation = masterRecordLink(s.master, `/demographic/AddRelation?demo=${patient}`).first();
  if (await addRelation.count() === 0) {
    throw new h.SkipCheck('The Master Record has no Add Relation: it is shown only with the legacy contacts UI (NEW_CONTACTS_UI off)');
  }
  relative = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
      provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(relativeName)},'Relative','1975','03','04','M','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(relative), 'The second owned patient was not created');
  const relations = (where = '') => sql.value(`SELECT COUNT(*) FROM relationships WHERE demographic_no=${patient}
    AND relation_demographic_no=${relative}${where}`);
  const note = `${marker} relation note`;

  let page;
  await s.step('Add Relation opens the relation page for the owned patient', async () => {
    page = await s.popup(s.master, addRelation, 'add-relation');
    await page.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(page, 'Add Relation');
    h.assert(await page.locator('input[name="keyword"]').count() === 1, 'Add Relation has no patient search');
  });

  let relation;
  await s.step('searching finds the second owned patient and picking them opens the relation form', async () => {
    await page.locator('input[name="keyword"]').fill(relativeName);
    await ui.clickAndAwaitReload(page, page.locator('input[type="submit"][name="Submit"]').first(), { label: 'Search', timeout: TIMEOUT });
    await h.assertNotErrorPage(page, 'Add Relation search results');
    const result = page.locator(`[onclick*="addName('${relative}'"]`).first();
    if (await result.count() === 0 && await page.locator(`[onclick*="addNameCaisi('${relative}'"]`).count() > 0) {
      throw new h.SkipCheck('With caisi on, a search result picks with addNameCaisi, which closes the popup instead of opening the relation form');
    }
    h.assert(await result.count() === 1, 'The search did not list the second owned patient');
    await ui.clickAndAwaitReload(page, result, { label: 'pick the second owned patient', timeout: TIMEOUT });
    await h.assertNotErrorPage(page, 'Add Relation form');
    const form = page.locator('form[action$="/demographic/AddRelation"][method="post"]');
    h.assert(await form.locator('input[name="origDemo"]').inputValue() === String(patient), 'The relation form is not for the owned patient');
    h.assert(await form.locator('input[name="linkingDemo"]').inputValue() === String(relative), 'The relation form is not for the second owned patient');
    relation = await form.locator('#relation option').evaluateAll(options => (options.find(o => o.value) || {}).value);
    h.assert(relation, 'The relation list offers no relation');
  });

  await s.step('a GET save is refused and writes nothing', async () => {
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/demographic/AddRelation?origDemo=${patient}&linkingDemo=${relative}&relation=${encodeURIComponent(relation)}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET save answered ${response.status()}, not 405`);
    h.assert(relations() === '0', 'A refused GET saved a relation');
  });

  await s.step('Add Relationship saves the relation with its flags and notes and lists it', async () => {
    const form = page.locator('form[action$="/demographic/AddRelation"][method="post"]');
    await form.locator('#relation').selectOption(relation);
    await form.locator('#sdm').check();
    await form.locator('#emergContact').check();
    await form.locator('#notes').fill(note);
    await ui.clickAndAwaitReload(page, form.locator('input[type="submit"]'), { label: 'Add Relationship', timeout: TIMEOUT });
    await h.assertNotErrorPage(page, 'Add Relation after the save');
    await expectValue(sql, `SELECT CONCAT_WS('|',relation,sub_decision_maker,emergency_contact,notes,deleted) FROM relationships
      WHERE demographic_no=${patient} AND relation_demographic_no=${relative}`, [relation, '1', '1', note, '0'].join('|'),
    'Add Relationship did not save the relation, its flags and its notes');
    const listed = page.locator(RELATION_ROW).filter({ hasText: relativeName }).filter({ hasText: note });
    h.assert(await listed.count() === 1, 'The relation page does not list the saved relation');
  });

  await s.step('the Master Record lists the relative under Other Contacts', async () => {
    await s.master.reload();
    await h.assertNotErrorPage(s.master, 'Master Record after adding a relation');
    h.assert((await s.master.locator('#otherContacts').innerText()).includes(relativeName),
      'Other Contacts does not list the relative');
  });

  await s.step('Delete marks the relation deleted and neither page lists it', async () => {
    const row = page.locator(RELATION_ROW).filter({ hasText: relativeName }).filter({ hasText: note });
    // The page's strict dialog handler dismisses any dialog not declared here, so the confirm is
    // accepted through withExpectedDialogs.
    const dialogs = await h.withExpectedDialogs(page, () => ui.clickAndAwaitReload(page,
      row.locator('form[action$="/demographic/DeleteRelation"] a'), { label: 'Delete', timeout: TIMEOUT }));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask for confirmation once');
    await h.assertNotErrorPage(page, 'Add Relation after the delete');
    await expectValue(sql, `SELECT deleted FROM relationships WHERE demographic_no=${patient} AND relation_demographic_no=${relative}`, '1',
      'Delete did not mark the relation deleted');
    h.assert(await page.locator(RELATION_ROW).filter({ hasText: relativeName }).filter({ hasText: note }).count() === 0,
      'The relation page still lists the deleted relation');
    await s.master.reload();
    h.assert(!(await s.master.locator('#otherContacts').innerText()).includes(relativeName),
      'Other Contacts still lists the deleted relative');
  });
}

if (require.main === module) runWorkflow('demographic-relations', workflow, { openPatient: true });
module.exports = { workflow };
