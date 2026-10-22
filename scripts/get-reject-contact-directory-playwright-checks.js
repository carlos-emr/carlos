#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, personal-contact directory. User path: Schedule ▸ Search ▸ Master
 * Record ▸ Contacts "Manage" (demographic/Contact?method=manage) ▸ Add Contact ▸ type
 * "Contact" ▸ search ▸ "Add/Edit contact" (method=addContact) ▸ fill ▸ Save
 * (POST demographic/Contact, method=saveContact).
 *
 * Contact2Action's writers -- saveManage, removeContact, saveContact, saveProContact -- are
 * POST-only (405 on GET/HEAD before any read or write) since issue #3682. Before that only the
 * first two were: the route "Contact" has no mutator prefix, HttpMethodGuardFilter's method list
 * is matched EXACTLY (saveContact is not "save"), and the class is outside the GET-rejection
 * contract, so a replayed saveContact rewrote any Contact row named by contact.id (Struts binds
 * contact.* from a query string). The check creates a directory contact and, through the External
 * row's professional search and its "Add/Edit Professional Contact" form (method=saveProContact),
 * an external professional contact (a Contact row since #5020; before it the form always made a
 * professional specialist); replays each captured save as GET/HEAD with a changed last name for
 * that same (owned) row; and asserts 405 and unchanged rows in the LAST step. (The set-flag and
 * pharmacy writers, which had no UI caller, were removed in issues #3509 and #4402;
 * contact-removed-targets proves a request naming one writes nothing.)
 *
 * Fixtures: the owned synthetic patient; the Contact and professionalSpecialists rows the pages
 * create carry the run marker as their last name; cleanup deletes rows carrying the marker (and
 * the patient's professional associations to them) and asserts it.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
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

  await s.step('Contacts ▸ Add professional ▸ External search ▸ Add/Edit Professional Contact ▸ Save creates the external contact (POST method=saveProContact)', async () => {
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
    savedPro = await captureRequest(search, url => url.pathname.endsWith('/demographic/Contact'),
      () => search.locator('input[name="submitbtn"]').click());
    h.assert(savedPro.params.get('method') === 'saveProContact', 'The professional form did not post method=saveProContact');
    // Since #5020 the External row's form asks for contactType=2, so the save is an external
    // professional contact in the Contact table, handed back to the row (the popup then closes).
    h.assert(savedPro.params.get('contactType') === '2', `The External row's form posted contactType=${savedPro.params.get('contactType')}`);
    await expectValue(sql, `SELECT COUNT(*) FROM Contact WHERE type='ProfessionalContact' AND lastName=${q(proLastName)}`, '1',
      'Save did not create the external professional contact');
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

  await s.step('the saveProContact replayed as GET/HEAD with a changed name is recorded', async () => {
    // pcontact.id stays 0 (a new contact), so an accepted replay would CREATE a second owned
    // professional record. The snapshot counts both tables the form can write (an external
    // contact, or a specialist for any other type) so neither kind of write goes unseen.
    await ledger.probe(s, { label: 'demographic/Contact?method=saveProContact', path: savedPro.path,
      params: replayParams(savedPro.params, { 'pcontact.lastName': `${marker}PGET` }),
      snapshot: () => sql.value(`SELECT CONCAT(
          (SELECT CONCAT(COUNT(*),'|',IFNULL(GROUP_CONCAT(CONCAT(id,':',lastName) ORDER BY id),''))
            FROM Contact WHERE type='ProfessionalContact' AND LOCATE(${q(marker)}, lastName) = 1),
          '||',
          (SELECT CONCAT(COUNT(*),'|',IFNULL(GROUP_CONCAT(CONCAT(specId,':',lName) ORDER BY specId),''))
            FROM professionalSpecialists WHERE LOCATE(${q(marker)}, lName) = 1))`) });
    h.assert(/^[1-9]\d*$/.test(proContactId), 'The external professional contact id was not captured');
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
