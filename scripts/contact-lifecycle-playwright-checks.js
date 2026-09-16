#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.4: associate an external contact; round-trip SDM, emergency,
// consent and notes; cancel unsaved edits; update and delete the association.
const { assert, sqlString } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, marker } = s;
  const contactName = `${marker} O'Neil "A&B"`;
  let contact;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient}`);
    if (contact) sql.execute(`DELETE FROM Contact WHERE id=${contact} AND lastName=${sqlString(contactName)}`);
  });
  contact = sql.value(`INSERT INTO Contact(type,lastName,firstName,deleted) VALUES('Contact',${sqlString(contactName)},'Fixture',0); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(contact), 'Contact directory fixture was not created');
  let editor;
  const open = async () => {
    editor = await s.popup(s.master, s.master.locator('[onclick*="Contact?method=manage"]').first(), 'contacts');
  };
  const field = name => editor.locator(`[name="contact_1.${name}"]`);
  const row = () => `SELECT CONCAT(role,'|',sdm,'|',ec,'|',consentToContact,'|',active,'|',note)
    FROM DemographicContact WHERE demographicNo=${patient} AND contactId=${sqlString(contact)} AND deleted=0`;
  const save = async () => {
    await editor.locator('input[type="submit"]').click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
  };
  await s.step('discard unsaved personal and professional rows without deleting persisted contacts', async () => {
    await open();
    await editor.locator('a[onclick="addContact();"]').click();
    await editor.locator('a[onclick="addProContact();"]').click();
    const ids = await editor.locator('#contact_1 [id], #procontact_1 [id]').evaluateAll(elements => elements.map(element => element.id));
    assert(new Set(ids).size === ids.length, 'Personal and professional contact controls share duplicate IDs');
    await editor.locator('#contact_1 a[onclick*="deleteContact("]').click();
    await editor.locator('#procontact_1 a[onclick*="deleteProContact("]').click();
    await save();
    assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient}`) === '0',
      'Discarding unsaved rows created an association');
    assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE id=${contact}`) === '1',
      'Discarding unsaved associations deleted the directory fixture');
  });
  await s.step('select contact through search and save its clinical contact flags', async () => {
    await open();
    await editor.locator('a[onclick="addContact();"]').click();
    await field('type').selectOption('2');
    const search = await s.popup(editor, editor.locator('a[onclick*="doPersonalSearch"]'), 'contact-search');
    await search.locator('[name="keyword"]').fill(marker);
    await clickAndAwaitReload(search, search.locator('input[type="submit"]').first());
    await search.locator('tr[onclick]').filter({ hasText: marker }).click();
    assert(await field('contactId').inputValue() === contact, 'Contact search selected the wrong record');
    assert(await field('contactName').inputValue() === `${contactName},Fixture`, 'Contact search corrupted punctuation in the name');
    await field('role').selectOption('Guardian');
    await field('sdm').check();
    await field('ec').check();
    await field('consentToContact').selectOption('1');
    await field('note').fill(marker);
    await save();
    await expectValue(sql, row(), `Guardian|true|true|1|1|${marker}`, 'Contact flags or note did not persist');
    await s.master.locator('#otherContacts2 li').filter({ hasText: marker }).waitFor({ state: 'visible' });
  });
  await s.step('reopen preserves fields and cancelling an edit writes nothing', async () => {
    await open();
    assert(await field('sdm').isChecked() && await field('ec').isChecked(), 'Contact flags did not reopen');
    assert(await field('note').inputValue() === marker, 'Contact note did not reopen');
    assert(await field('role').inputValue() === 'Guardian' && await field('consentToContact').inputValue() === '1',
      'Contact role or consent did not reopen');
    await field('note').fill(`${marker}-CANCEL`);
    await editor.locator('[name="cancel"]').click();
    assert(sql.value(row()) === `Guardian|true|true|1|1|${marker}`, 'Cancelled contact edit changed stored values');
  });
  await s.step('updates persist and removing the association preserves the directory contact', async () => {
    await open();
    await field('sdm').uncheck();
    await field('ec').uncheck();
    await field('consentToContact').selectOption('0');
    await field('note').fill(`${marker}-EDIT`);
    await save();
    await expectValue(sql, `SELECT CONCAT(consentToContact,'|',note) FROM DemographicContact
      WHERE demographicNo=${patient} AND deleted=0`, `0|${marker}-EDIT`, 'Edited consent or note was lost');
    await open();
    assert(!await field('sdm').isChecked() && !await field('ec').isChecked(), 'Cleared SDM/emergency flags remained checked');
    await editor.locator('a[onclick*="deleteContact("]').click();
    await save();
    await expectValue(sql, `SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient} AND deleted=0`, '0',
      'Deleted contact association is still active');
    assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE id=${contact}`) === '1', 'Removing the association deleted the directory contact');
    await open();
    assert(await editor.locator('#contact_container [name$=".contactId"]').count() === 0, 'Deleted contact still appears after reopening');
  });
  await s.step('professional no-consent and inactive selections persist independently and reopen', async () => {
    await editor.locator('a[onclick="addProContact();"]').click();
    const pro = name => editor.locator(`[name="procontact_1.${name}"]`);
    const search = await s.popup(editor, editor.locator('a[onclick*="doProfessionalSearch"]'), 'provider-contact-search');
    await search.getByRole('link', { name: s.provider, exact: true }).click();
    assert(await pro('contactId').inputValue() === s.provider, 'Professional search selected the wrong provider');
    await pro('consentToContact').selectOption('0');
    await pro('active').selectOption('0');
    await save();
    await expectValue(sql, `SELECT CONCAT(consentToContact,'|',active) FROM DemographicContact
      WHERE demographicNo=${patient} AND contactId=${sqlString(s.provider)} AND deleted=0`, '0|0',
      'Professional no-consent or inactive selection was silently lost');
    await open();
    assert(await pro('consentToContact').inputValue() === '0' && await pro('active').inputValue() === '0',
      'Professional consent/status did not reopen');
    await pro('consentToContact').selectOption('1');
    await pro('active').selectOption('1');
    await save();
    await expectValue(sql, `SELECT CONCAT(consentToContact,'|',active) FROM DemographicContact
      WHERE demographicNo=${patient} AND contactId=${sqlString(s.provider)} AND deleted=0`, '1|1',
      'Professional consent/status update did not persist');
  });
  await s.step('internal patient search creates a correctly typed reciprocal association', async () => {
    // Seed only the related patient. Find and select that patient through the
    // actual picker, then create and edit the relationship through the form.
    let related;
    // demographic.last_name is VARCHAR(30); the 23-character marker plus this
    // suffix must round-trip unchanged for the cleanup ownership check.
    const relatedName = `${marker}-O'N`;
    s.cleanup(() => {
      if (!related) return;
      assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${related}
        AND last_name=${sqlString(relatedName)}`) === '1', 'Related patient fixture ownership changed');
      sql.execute(`DELETE FROM DemographicContact WHERE demographicNo IN (${patient},${related})`);
      assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo IN (${patient},${related})`) === '0',
        'Owned reciprocal associations were not removed');
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${related} AND last_name=${sqlString(relatedName)}`);
      assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${related}`) === '0',
        'The owned related patient was not removed');
    });
    related = sql.value(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${sqlString(relatedName)},'Related','1960','01','02','M','AC',
        ${sqlString(s.provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(related), 'Related patient fixture was not created');
    await open();
    await editor.locator('a[onclick="addContact();"]').click();
    await field('type').selectOption('1');
    const patientSearch = await s.popup(editor, editor.locator('a[onclick*="doPersonalSearch"]').first(), 'patient-contact-search');
    await patientSearch.locator('[name="keyword"]').fill(relatedName);
    await clickAndAwaitReload(patientSearch, patientSearch.locator('input[type="submit"]').first());
    await patientSearch.locator('tr[onclick]').filter({
      has: patientSearch.locator(`input[name="demographic_no"][value="${related}"]`),
    }).locator('td.lastName').click();
    assert(await field('contactId').inputValue() === related, 'Internal search selected the wrong patient');
    assert(await field('contactName').inputValue() === `${relatedName},Related`, 'Internal search corrupted the patient display name');
    await field('role').selectOption('Parent');
    await save();
    const association = sql.value(`SELECT id FROM DemographicContact WHERE demographicNo=${patient}
      AND contactId=${sqlString(related)} AND type=1 AND category='personal' AND deleted=0`);
    assert(/^[1-9]\d*$/.test(association), 'Internal patient selection did not create an association');
    await expectValue(sql, `SELECT CONCAT(type,'|',role,'|',contactId) FROM DemographicContact
      WHERE id=${association} AND demographicNo=${patient}`, `1|Parent|${related}`,
      'Editing the relationship moved or changed the original association');
    await expectValue(sql, `SELECT CONCAT(type,'|',role,'|',contactId,'|',sdm,'|',ec) FROM DemographicContact
      WHERE demographicNo=${related} AND deleted=0`, `1|Daughter|${patient}||`,
      'The reciprocal relationship has the wrong type or unrequested SDM/emergency flags');
    await open();
    assert(await field('type').isDisabled(), 'Existing contact type must remain immutable');
    assert(await field('role').inputValue() === 'Parent', 'Internal contact role did not reopen');
    await field('note').fill(`${marker}-INTERNAL`);
    await save();
    assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${related}`) === '1',
      'Saving an existing relationship duplicated its reciprocal association');
  });
}
if (require.main === module) runWorkflow('contact-lifecycle', workflow);
module.exports = { workflow };
