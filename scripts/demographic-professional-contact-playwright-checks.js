#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Master Record contacts: the professional search's Add/Edit Professional Contact form, a personal contact's own details
 * edited from the Master Record, and finding the new professional contact again. contact-lifecycle covers the patient's
 * associations with contacts it seeds by SQL; get-reject-contact-directory only replays the saves as GET.
 * User path: Master Record ▸ Contacts ▸ Add (professional) ▸ External ▸ Search ▸ Add/Edit Professional Contact ▸ the
 * form ▸ Save; Master Record ▸ Other Contacts ▸ details ▸ Edit ▸ Save; Contacts ▸ Add (professional) ▸ External ▸
 * Search for the new contact.
 * Asserts: the form stores one record with the typed name, specialty, CPSO and work phone (today a professional
 * specialist); a personal contact's Edit stores the changed details on the same Contact row, with no second row; LAST,
 * the External search lists the contact just added, so it can be chosen.
 * Requires: NEW_CONTACTS_UI and NEW_CONTACTS_UI_EXTERNAL_CONTACT true (the packaged default).
 * Fixtures: the runWorkflow FAKE- patient; the professional record it creates and a SQL-seeded personal contact with its
 * association, all keyed to the marker and removed in cleanup.
 */
const { assert, sqlString } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const TYPE_CONTACT = '2'; // DemographicContact.TYPE_CONTACT, an external contact

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const proName = `${marker}Pro`;
  const personalName = `${marker}Friend`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient};
      DELETE FROM Contact WHERE lastName IN (${sqlString(proName)},${sqlString(personalName)});
      DELETE FROM professionalSpecialists WHERE lName=${sqlString(proName)}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM Contact WHERE lastName IN (${sqlString(proName)},${sqlString(personalName)}))
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${sqlString(proName)})`) === '0',
    'The owned contacts, the professional record or the patient\'s associations were not removed');
  });
  let editor;
  const openContacts = async () => {
    editor = await s.popup(s.master, masterRecordLink(s.master, 'Contact?method=manage').first(), 'contacts');
  };
  const pro = name => editor.locator(`[name="procontact_1.${name}"]`);
  // The professional search's Add/Edit link opens the form as a professional specialist (contactType=3), so the saved
  // record lands in professionalSpecialists today; #4987 decides whether that or an external contact is the target.
  const saved = () => sql.rows(`SELECT 'specialist', IFNULL(fName,''), IFNULL(specType,''), IFNULL(referralNo,''), IFNULL(phone,'')
    FROM professionalSpecialists WHERE lName=${sqlString(proName)}
    UNION ALL SELECT 'contact', IFNULL(firstName,''), IFNULL(specialty,''), IFNULL(cpso,''), IFNULL(workPhone,'')
    FROM Contact WHERE type='ProfessionalContact' AND lastName=${sqlString(proName)}`);
  const searchFromExternalRow = async () => {
    await openContacts();
    await editor.locator('a[onclick="addProContact();"]').click();
    const types = await pro('type').locator('option').evaluateAll(options => options.map(option => option.value));
    assert(types.includes(TYPE_CONTACT), 'The professional row offers no External type: run the server with NEW_CONTACTS_UI_EXTERNAL_CONTACT=true');
    await pro('type').selectOption(TYPE_CONTACT);
    return s.popup(editor, editor.locator('#procontact_1 a[onclick*="doProfessionalSearch"]').first(), 'professional-search');
  };
  let specialty;

  await s.step('the professional search\'s Add/Edit form stores one record with what was typed', async () => {
    const search = await searchFromExternalRow();
    await clickAndAwaitReload(search, search.locator('a[href*="/demographic/Contact?method=addProContact"]').first());
    await search.locator('[name="pcontact.lastName"]').fill(proName);
    await search.locator('[name="pcontact.firstName"]').fill('Specialist');
    specialty = await search.locator('select[name="pcontact.specialty"] option').nth(1).getAttribute('value');
    await search.locator('select[name="pcontact.specialty"]').selectOption(specialty);
    await search.locator('[name="pcontact.cpso"]').fill('99123');
    await search.locator('[name="pcontact.workPhone"]').fill('416-555-0142');
    await clickAndAwaitReload(search, search.locator('input[name="submitbtn"]'));
    const rows = saved();
    assert(rows.length === 1, `Save stored ${rows.length} professional records: ${JSON.stringify(rows)}`);
    assert(JSON.stringify(rows[0].slice(1)) === JSON.stringify(['Specialist', specialty, '99123', '416-555-0142']),
      `The stored professional record is ${JSON.stringify(rows[0])}`);
    await search.close();
    await editor.close();
  });

  await s.step('a personal contact\'s Edit from the Master Record stores the new details on the same row', async () => {
    const personal = sql.value(`INSERT INTO Contact (type,lastName,firstName,city,residencePhone,deleted)
      VALUES ('Contact',${sqlString(personalName)},'Neighbour','Oldtown','416-555-0100',0); SELECT LAST_INSERT_ID()`);
    sql.execute(`INSERT INTO DemographicContact (facilityId,creator,created,updateDate,deleted,demographicNo,contactId,role,type,
      category,consentToContact,active) VALUES (0,${sqlString(provider)},NOW(),NOW(),0,${patient},${sqlString(personal)},'Friend',
      ${TYPE_CONTACT},'personal',1,1)`);
    await s.master.reload({ waitUntil: 'load' });
    const details = s.master.locator('#otherContacts2 a', { hasText: 'details' }).first();
    const view = await s.popup(s.master, details, 'contact-view');
    await clickAndAwaitReload(view, view.getByRole('button', { name: /edit/i }).first());
    await view.locator('[name="contact.city"]').fill('Newtown');
    await view.locator('[name="contact.residencePhone"]').fill('416-555-0177');
    await clickAndAwaitReload(view, view.locator('input[type="submit"][name="submit"]').first());
    await expectValue(sql, `SELECT CONCAT(COUNT(*),'|',MAX(city),'|',MAX(residencePhone),'|',MAX(id)) FROM Contact
      WHERE lastName=${sqlString(personalName)}`, `1|Newtown|416-555-0177|${personal}`,
    'Edit did not update the personal contact in place');
    await view.close();
  });

  await s.step('the External search lists the contact just added, so it can be chosen', async () => {
    const search = await searchFromExternalRow();
    await search.locator('input[name="keyword"]').fill(proName);
    await clickAndAwaitReload(search, search.locator('form[name="titlesearch"] input[type="submit"]').first());
    const listed = await search.locator('tr[onclick*="selectResult"]', { hasText: proName }).count();
    await search.close();
    await editor.close();
    assert(listed === 1, `The External search lists the contact just added ${listed} times, not once`);
  });
}

if (require.main === module) runWorkflow('demographic-professional-contact', workflow, { openPatient: true });
module.exports = { workflow };
