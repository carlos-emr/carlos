#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Contact directory editor: navigate from the owned patient's Contacts manager, create a contact,
 * then edit the returned persisted record. Real Save clicks must run without script errors, preserve
 * the contact ID and round-trip quoted text. The existing get-reject-contact-directory workflow
 * independently retains its GET/HEAD write probes. Cleanup removes only marker-owned contacts and
 * this patient's type-2 links to them.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const lastName = `${marker} Contact`;
  const owned = `SELECT id FROM Contact WHERE lastName=${q(lastName)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE type=2 AND demographicNo=${patient}
      AND contactId IN (SELECT id FROM (${owned}) t);
      DELETE FROM Contact WHERE lastName=${q(lastName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE lastName=${q(lastName)}`) === '0',
      'Owned directory contacts were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE lastName=${q(lastName)}`) === '0',
    'The owned contact already exists');
  let contact;
  let contactId;

  async function save() {
    const [response] = await Promise.all([
      contact.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/demographic/Contact')),
      clickAndAwaitReload(contact, contact.locator('input[type="submit"][name="submit"]'), { label: 'Save contact' }),
    ]);
    h.assert(response.status() === 200, 'Contact Save did not succeed');
    h.assert(new URLSearchParams(response.request().postData()).get('method') === 'saveContact',
      'Contact Save did not submit the expected action');
  }

  await s.step('Contacts manager opens Add/Edit Contact and Save creates one directory record without script errors', async () => {
    const manager = await s.popup(s.master, s.master.locator('[onclick*="Contact?method=manage"]').first(), 'contacts');
    await manager.locator('a[onclick="addContact();"]').click();
    await manager.locator('[name="contact_1.type"]').selectOption('2');
    contact = await s.popup(manager, manager.locator('a[onclick*="doPersonalSearch"]').first(), 'contact-search');
    await clickAndAwaitReload(contact, contact.locator('a[href*="/demographic/Contact?method=addContact"]').first());
    await contact.locator('input[name="contact.lastName"]').fill(lastName);
    await contact.locator('input[name="contact.firstName"]').fill('Directory');
    await save();
    await expectValue(sql, `SELECT COUNT(*) FROM Contact WHERE lastName=${q(lastName)} AND firstName='Directory'`, '1',
      'Save did not create exactly one directory contact');
    contactId = sql.value(owned);
    h.assert(/^[1-9]\d*$/.test(contactId), 'The saved contact has no valid identifier');
  });

  await s.step('the saved editor focuses the real name field and updates the same contact with quoted text', async () => {
    h.assert(await contact.locator('input[name="contact.id"]').inputValue() === contactId,
      'The saved editor did not retain its contact identifier');
    h.assert(await contact.evaluate(() => document.activeElement?.name) === 'contact.lastName',
      'The editor did not focus its last-name field');
    const firstName = 'Directory "quoted"';
    const note = 'Synthetic O\'Brien & "quoted" note';
    await contact.locator('input[name="contact.firstName"]').fill(firstName);
    await contact.locator('input[name="contact.note"]').fill(note);
    await save();
    await expectValue(sql, `SELECT CONCAT(firstName,'|',note) FROM Contact WHERE id=${contactId}`,
      `${firstName}|${note}`, 'The contact edit did not persist literal quoted text');
    h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE lastName=${q(lastName)}`) === '1',
      'Editing the contact created a duplicate record');
    h.assert(await contact.locator('input[name="contact.id"]').inputValue() === contactId,
      'The contact edit changed its identifier');
    h.assert(await contact.locator('input[name="contact.firstName"]').inputValue() === firstName
      && await contact.locator('input[name="contact.note"]').inputValue() === note,
    'The returned editor did not preserve the saved text');
  });
}

if (require.main === module) runWorkflow('contact-editor', workflow, { openPatient: true });
module.exports = { workflow };
