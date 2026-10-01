#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, personal-contact directory. User path: Schedule ▸ Search ▸ Master
 * Record ▸ Contacts "Manage" (demographic/Contact?method=manage) ▸ Add Contact ▸ type
 * "Contact" ▸ search ▸ "Add/Edit contact" (method=addContact) ▸ fill ▸ Save
 * (POST demographic/Contact, method=saveContact).
 *
 * Contact2Action guards only saveManage and removeContact with a POST check. Its other
 * writers -- saveContact, saveProContact, savePharmacyInfo, addPharmacy, removePharmacy,
 * setEmergencyContact, setDNC, setMRP -- run on a GET: the route "Contact" has no mutator
 * prefix and HttpMethodGuardFilter's method list is matched EXACTLY (saveContact is not
 * "save"); the class is outside the GET-rejection contract. saveContact rewrites any
 * Contact row named by contact.id (Struts binds contact.* from a query string). The check
 * creates a directory contact through the page, asserts the row, replays the captured save
 * as GET/HEAD with a changed last name for that same (owned) contact.id, and asserts 405 and
 * an unchanged row in the LAST step. (The set-flag and pharmacy writers have no UI caller and are
 * reported, not probed.)
 *
 * Fixtures: the owned synthetic patient; the Contact row the page creates carries the run
 * marker as its last name; cleanup deletes Contact rows carrying the marker and asserts it.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');

const NAME = 'get-reject-contact-directory';

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const lastName = `${marker}C`;
  const owned = `SELECT id FROM Contact WHERE LOCATE(${q(marker)}, lastName) = 1`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE contactId IN (SELECT id FROM (${owned}) t);
      DELETE FROM Contact WHERE LOCATE(${q(marker)}, lastName) = 1`);
    h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE LOCATE(${q(marker)}, lastName) = 1`) === '0',
      'Owned directory contacts were not removed');
  });
  let saved;
  let contactId;
  let pageDefect = null;

  await s.step('Contacts ▸ Add ▸ search ▸ Add/Edit contact ▸ Save creates the directory contact (POST method=saveContact)', async () => {
    const editor = await s.popup(s.master, s.master.locator('[onclick*="Contact?method=manage"]').first(), 'contacts');
    await editor.locator('a[onclick="addContact();"]').click();
    await editor.locator('[name="contact_1.type"]').selectOption('2');
    const search = await s.popup(editor, editor.locator('a[onclick*="doPersonalSearch"]').first(), 'contact-search');
    // addEditContact.jsp's script block does not parse (finding below), so the page raises
    // errors while it is used. They are moved out of the strict recorder INTO the final
    // assertion -- nothing is dropped -- so they cannot hide the GET probe.
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

  await s.step('the saveContact replayed as GET/HEAD for the owned contact with a changed name is recorded', async () => {
    await ledger.probe(s, { label: 'demographic/Contact?method=saveContact', path: saved.path,
      params: replayParams(saved.params, { 'contact.id': contactId, 'contact.lastName': `${marker}GET` }),
      snapshot: () => sql.value(`SELECT CONCAT(lastName,'|',firstName) FROM Contact WHERE id=${contactId}`) });
  });

  await s.step('the contact directory save refused GET/HEAD, the owned contact is unchanged, and the page ran cleanly', async () => {
    const problems = [];
    try { ledger.assertAllRefused(); } catch (error) { problems.push(error.message); }
    if (pageDefect) problems.push(pageDefect);
    h.assert(!problems.length, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
