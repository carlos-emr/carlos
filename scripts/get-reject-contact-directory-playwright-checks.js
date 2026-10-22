#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, personal-contact directory. User path: Schedule ▸ Search ▸ Master
 * Record ▸ Contacts "Manage" (demographic/Contact?method=manage) ▸ Add Contact ▸ type
 * "Contact" ▸ search ▸ "Add/Edit contact" (method=addContact) ▸ fill ▸ Save
 * (POST demographic/Contact, method=saveContact).
 *
 * Contact2Action's writers -- saveManage, removeContact, saveContact, saveProContact,
 * savePharmacyInfo, addPharmacy, removePharmacy, setEmergencyContact, setDNC, setMRP -- are
 * POST-only (405 on GET/HEAD before any read or write) since issue #3682. Before that only the
 * first two were: the route "Contact" has no mutator prefix, HttpMethodGuardFilter's method list
 * is matched EXACTLY (saveContact is not "save"), and the class is outside the GET-rejection
 * contract, so a replayed saveContact rewrote any Contact row named by contact.id (Struts binds
 * contact.* from a query string). The check creates a directory contact and, through the
 * External row's professional search and its "Add/Edit Professional Contact" form
 * (method=saveProContact), an external professional contact (a Contact row of type
 * ProfessionalContact; since #4987 that form no longer makes a professional specialist for the
 * External row); replays each captured save as GET/HEAD with a changed last name; and asserts
 * 405 and unchanged rows in the LAST step. (The set-flag and pharmacy writers have no UI caller
 * and are reported, not probed; see issue #4402.)
 *
 * Fixtures: the owned synthetic patient; the Contact rows the pages create carry the run marker
 * as their last name; cleanup deletes rows carrying the marker (and the patient's associations to
 * them) and asserts it. It also removes any marker professionalSpecialists row, which only a
 * regression of #4987 would create.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');
const { masterRecordLink } = require('./lib/master-record-link');

const NAME = 'get-reject-contact-directory';

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const lastName = `${marker}C`;
  const owned = `SELECT id FROM Contact WHERE LOCATE(${q(marker)}, lastName) = 1`;
  const ownedSpecialists = `SELECT specId FROM professionalSpecialists WHERE LOCATE(${q(marker)}, lName) = 1`;
  s.cleanup(() => {
    // Registered first, so it runs after the directory cleanup below (cleanups run in reverse).
    // Only a regression of #4987 makes a specialist here; the save step then fails, and this
    // removes what it made.
    sql.execute(`DELETE FROM DemographicContact WHERE type=3 AND demographicNo=${s.patient} AND contactId IN (SELECT specId FROM (${ownedSpecialists}) t);
      DELETE FROM professionalSpecialists WHERE LOCATE(${q(marker)}, lName) = 1`);
    h.assert(sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE LOCATE(${q(marker)}, lName) = 1`) === '0',
      'Owned professional specialists were not removed');
  });
  s.cleanup(() => {
    // DemographicContact.contactId is a Contact id only for type 2 (TYPE_CONTACT); other types hold
    // demographic or provider numbers, which an owned Contact id must never be matched against.
    sql.execute(`DELETE FROM DemographicContact WHERE type=2 AND demographicNo=${s.patient} AND contactId IN (SELECT id FROM (${owned}) t);
      DELETE FROM Contact WHERE LOCATE(${q(marker)}, lastName) = 1`);
    h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE LOCATE(${q(marker)}, lastName) = 1`) === '0',
      'Owned directory contacts were not removed');
  });
  let saved;
  let contactId;
  let savedPro;
  let editor;
  let cpso;
  let proContactId;
  const proLastName = `${marker}P`;
  let pageDefect = null;

  await s.step('Contacts ▸ Add ▸ search ▸ Add/Edit contact ▸ Save creates the directory contact (POST method=saveContact)', async () => {
    editor = await s.popup(s.master, masterRecordLink(s.master, 'Contact?method=manage').first(), 'contacts');
    await editor.locator('a[onclick="addContact();"]').click();
    await editor.locator('[name="contact_1.type"]').selectOption('2');
    const search = await s.popup(editor, editor.locator('a[onclick*="doPersonalSearch"]').first(), 'contact-search');
    // Preserve any editor script errors in the final assertion so an independent UI
    // regression cannot prevent the GET probe from running. No errors are discarded.
    const since = s.recorder.pageErrors.length;
    await clickAndAwaitReload(search, search.locator('a[href*="/demographic/Contact?method=addContact"]').first());
    await search.locator('input[name="contact.lastName"]').fill(lastName);
    await search.locator('input[name="contact.firstName"]').fill('Directory');
    saved = await captureRequest(search, url => url.pathname.endsWith('/demographic/Contact'),
      () => search.locator('input[type="submit"][name="submit"]').first().click());
    h.assert(saved.params.get('method') === 'saveContact', 'The contact form did not post method=saveContact');
    await expectValue(sql, `SELECT COUNT(*) FROM Contact WHERE lastName=${q(lastName)} AND firstName='Directory'`, '1',
      'Save did not create the directory contact');
    contactId = sql.value(`SELECT id FROM Contact WHERE lastName=${q(lastName)}`);
    // The save re-renders the same page; let it finish, then close it so no later error of
    // this page can arrive after the hand-over below.
    await search.waitForLoadState('load').catch(() => {});
    await search.close();
    const errors = s.recorder.pageErrors.splice(since).map(e => e.text.split('\n')[0]);
    if (errors.length) {
      pageDefect = `the Add/Edit contact page raised ${errors.length} JavaScript error(s): ${[...new Set(errors)].join(' | ')}`;
      console.log(`  observed ${NAME}: ${pageDefect}`);
    }
  });

  await s.step('Contacts ▸ Add professional ▸ External ▸ search ▸ Add/Edit Professional Contact ▸ Save creates the external professional contact (POST method=saveProContact)', async () => {
    // Manage Contacts opens in a named window, so the one the first step opened is reused.
    if (!editor || editor.isClosed()) {
      editor = await s.popup(s.master, masterRecordLink(s.master, 'Contact?method=manage').first(), 'contacts');
    }
    await editor.locator('a[onclick="addProContact();"]').click();
    await editor.locator('[name="procontact_1.type"]').selectOption('2');
    const search = await s.popup(editor, editor.locator('a[onclick*="doProfessionalSearch"]').first(), 'procontact-search');
    await clickAndAwaitReload(search, search.locator('a[href*="/demographic/Contact?method=addProContact"]').first());
    await search.locator('input[name="pcontact.lastName"]').fill(proLastName);
    await search.locator('input[name="pcontact.firstName"]').fill('Professional');
    await search.locator('input[name="pcontact.workPhone"]').fill('416-555-0142');
    // A six-digit CPSO no existing contact or specialist carries, so the stored row and the
    // replay's changed CPSO below are this run's alone.
    do {
      cpso = String(100000 + randomInt(900000));
    } while (sql.value(`SELECT (SELECT COUNT(*) FROM professionalSpecialists WHERE referralNo=${q(cpso)})
      + (SELECT COUNT(*) FROM Contact WHERE cpso=${q(cpso)})`) !== '0');
    await search.locator('input[name="pcontact.cpso"]').fill(cpso);
    savedPro = await captureRequest(search, url => url.pathname.endsWith('/demographic/Contact'),
      () => search.locator('input[name="submitbtn"]').click());
    h.assert(savedPro.params.get('method') === 'saveProContact', 'The professional form did not post method=saveProContact');
    // The External row asks for an external professional contact (contactType 2, #4987).
    h.assert(savedPro.params.get('contactType') === '2', 'The External row\'s form did not post contactType=2 (#4987)');
    await expectValue(sql, `SELECT COUNT(*) FROM Contact WHERE type='ProfessionalContact' AND lastName=${q(proLastName)} AND cpso=${q(cpso)}`, '1',
      'Save did not create the external professional contact');
    h.assert(sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE LOCATE(${q(marker)}, lName) = 1`) === '0',
      'Save from the External row created a professional specialist (#4987)');
    proContactId = sql.value(`SELECT id FROM Contact WHERE type='ProfessionalContact' AND lastName=${q(proLastName)}`);
    await search.waitForLoadState('load').catch(() => {});
    if (!search.isClosed()) await search.close();
    if (!editor.isClosed()) await editor.close();
  });

  await s.step('the saveContact replayed as GET/HEAD for the owned contact with a changed name is recorded', async () => {
    await ledger.probe(s, { label: 'demographic/Contact?method=saveContact', path: saved.path,
      params: replayParams(saved.params, { 'contact.id': contactId, 'contact.lastName': `${marker}GET` }),
      snapshot: () => sql.value(`SELECT CONCAT(lastName,'|',firstName) FROM Contact WHERE id=${contactId}`) });
  });

  await s.step('the saveProContact replayed as GET/HEAD with a changed name and CPSO is recorded', async () => {
    // pcontact.id stays empty (a new contact), so an accepted replay would CREATE a second
    // professional contact under the changed name and CPSO; the snapshot lists the owned
    // professional rows (and any owned specialist) to catch that.
    await ledger.probe(s, { label: 'demographic/Contact?method=saveProContact', path: savedPro.path,
      params: replayParams(savedPro.params, { 'pcontact.lastName': `${marker}PGET`, 'pcontact.cpso': String((Number(cpso) + 1) % 1000000).padStart(6, '0') }),
      snapshot: () => sql.value(`SELECT CONCAT(
          (SELECT CONCAT(COUNT(*),'|',IFNULL(GROUP_CONCAT(CONCAT(id,':',lastName,':',IFNULL(cpso,'')) ORDER BY id),''))
             FROM Contact WHERE type='ProfessionalContact' AND LOCATE(${q(marker)}, lastName) = 1),
          '#', (SELECT COUNT(*) FROM professionalSpecialists WHERE LOCATE(${q(marker)}, lName) = 1))`) });
    h.assert(/^[1-9]\d*$/.test(proContactId), 'The professional contact id was not captured');
  });

  await s.step('both contact saves refused GET/HEAD, the owned rows are unchanged, and the page ran cleanly', async () => {
    const problems = [];
    try { ledger.assertAllRefused(); } catch (error) { problems.push(error.message); }
    if (pageDefect) problems.push(pageDefect);
    h.assert(!problems.length, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
