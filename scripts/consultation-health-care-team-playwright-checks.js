#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// #6201: the actual HCT form saves both unmapped and mapped professional contacts.
// #6202: a team member without a college, MSP or billing number is listed, marked and saves; a
// member without consent to contact is left out but counted, so the list is never silently short.
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {createConsultationSubmitFixture} = require('./lib/consultation-submit-fixture');

// Reopening a stamped HCT form can create another signature before a request
// references it. Only a verified run-owned patient may lose those unreferenced
// consultation signatures; linked signatures and other module types survive.
function cleanupUnusedHctSignatures(sql, patient, marker) {
  h.assert(/^[1-9]\d*$/.test(String(patient)) && /^FAKE-PW[0-9a-f]{16}$/.test(marker),
    'Invalid HCT fixture owner');
  h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE demographic_no=? AND last_name=?',
    [patient, marker]) === '1', 'HCT patient fixture ownership changed');
  const unused = `demographicId=? AND ModuleType='CONSULTATION'
    AND NOT EXISTS (SELECT 1 FROM consultationRequests WHERE signature_img=CAST(DigitalSignature.id AS CHAR))
    AND NOT EXISTS (SELECT 1 FROM consultationRequestsArchive WHERE signature_img=CAST(DigitalSignature.id AS CHAR))`;
  sql.execute('DELETE FROM DigitalSignature WHERE ' + unused, [patient]);
  h.assert(sql.value('SELECT COUNT(*) FROM DigitalSignature WHERE ' + unused, [patient]) === '0',
    'Unused owned HCT signatures remain');
}

async function workflow(s) {
  const {sql, marker, patient, provider} = s;
  const owned = createConsultationSubmitFixture(sql, patient);
  const specialty = marker + ' Specialty';
  const unmatched = marker + ' Unmapped role';
  const details = {phone: '555-0100', fax: '555-0101', address: 'FAKE synthetic address', note: marker + ' annotation'};
  s.cleanup(() => {
    const failures = [];
    for (const cleanup of [() => owned.cleanup(),
      () => sql.execute('DELETE FROM DemographicContact WHERE demographicNo=? AND creator=? AND note=?', [patient, provider, marker]),
      () => sql.execute('DELETE FROM Contact WHERE lastName=? AND firstName=?', [marker, 'FAKE']),
      () => sql.execute('DELETE FROM consultationServices WHERE serviceDesc=?', [specialty]),
      () => cleanupUnusedHctSignatures(sql, patient, marker)]) {
      try { cleanup(); } catch (error) { failures.push(error); }
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=? AND note=?)
      + (SELECT COUNT(*) FROM Contact WHERE lastName=? AND firstName=?)
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=?)`,
    [patient, marker, marker, 'FAKE', specialty]) === '0', 'Owned HCT fixtures remain');
    if (failures.length) throw new AggregateError(failures, 'HCT fixture cleanup failed');
  });
  // Every owned Contact is FAKE/<marker> and every owned link carries note=<marker>, so the
  // cleanup above removes the extra #6202 members too.
  function teamMember(memberDetails, number, role, consent, label) {
    const id = h.insertId(sql, `INSERT INTO Contact
      (type,lastName,firstName,address,workPhone,fax,note,cpso,deleted)
      VALUES('ProfessionalContact',?,?,?,?,?,?,?,0)`, label + ' professional contact',
    [marker, 'FAKE', memberDetails.address, memberDetails.phone, memberDetails.fax, memberDetails.note, number]);
    const memberLink = h.insertId(sql, `INSERT INTO DemographicContact
      (facilityId,creator,deleted,demographicNo,contactId,role,type,category,note,consentToContact,active)
      VALUES(0,?,0,?,?,?,2,'professional',?,?,1)`, label + ' patient HCT link',
    [provider, patient, id, role, marker, consent]);
    return {contact: id, link: memberLink};
  }
  const {contact, link} = teamMember(details, '999998', unmatched, 1, 'numbered');
  const originalContact = JSON.stringify(sql.rows('SELECT * FROM Contact WHERE id=?', [contact]));

  async function openForm() {
    const list = await s.context.newPage();
    h.wireStrictPage(list, 'hct-consultation-list', s.recorder);
    await h.gotoApp(list, s.config.baseUrl,
      `/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${patient}`);
    const form = await s.popup(list, list.locator('a.btn').filter({hasText: /New Consultation/i}).first(), 'hct-consultation');
    await form.locator('select#specialist').waitFor();
    await form.waitForLoadState('networkidle');
    return {form, list};
  }

  async function formFor(reason, target = {link, details}) {
    const {form, list} = await openForm();
    await form.locator('select#specialist').selectOption(String(target.link));
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    h.assert(await form.locator('[name="phone"]').inputValue() === target.details.phone, 'HCT phone was not filled');
    h.assert(await form.locator('[name="fax"]').inputValue() === target.details.fax, 'HCT fax was not filled');
    h.assert(await form.locator('[name="address"]').inputValue() === target.details.address, 'HCT address was not filled');
    h.assert(await form.locator('#annotation').inputValue() === target.details.note, 'HCT annotation was not filled');
    return {form, list};
  }
  async function save(reason, service, target = {link, details}) {
    const {form, list} = await formFor(reason, target);
    h.assert(await form.locator('#hctService').inputValue() === service, 'HCT service fallback or exact match was lost');
    const [response] = await Promise.all([
      form.waitForResponse(answer => answer.request().method() === 'POST'
        && new URL(answer.url()).pathname.endsWith('/encounter/RequestConsultation'), {timeout: 90000}),
      form.locator('[name="submitSaveOnly"]').click({noWaitAfter: true}),
    ]);
    h.assert(response.status() === 302, 'HCT consultation did not return its save-confirmation redirect');
    const confirmation = new URL(response.headers().location, form.url());
    h.assert(confirmation.pathname.endsWith('/encounter/oscarConsultationRequest/ViewConfirmConsultationRequest')
      && confirmation.searchParams.get('de') === String(patient), 'HCT save redirected to an unexpected destination');
    await form.waitForURL(url => url.pathname === confirmation.pathname, {timeout: 90000});
    await form.waitForLoadState('domcontentloaded', {timeout: 90000});
    h.assert(/has been\s+Created/i.test(await form.locator('body').innerText()), 'HCT consultation was not confirmed');
    const rows = sql.rows(`SELECT serviceId, demographicContactId, IFNULL(specId,''), reason
      FROM consultationRequests WHERE demographicNo=? AND reason=?`, [patient, reason]);
    h.assert(JSON.stringify(rows) === JSON.stringify([[service, String(target.link), '', reason]]),
      'Saved HCT consultation has the wrong identity or service');
    h.assert(JSON.stringify(sql.rows('SELECT * FROM Contact WHERE id=?', [contact])) === originalContact,
      'Saving a consultation changed the professional contact');
    await form.close();
    await list.close();
  }
  await s.step('a team member without any matching specialty saves with the existing HCT default', async () => {
    await save(owned.reason('unmapped'), '0');
  });
  await s.step('a mapped specialty saves its exact service and the same patient-contact identity', async () => {
    const service = h.insertId(sql, "INSERT INTO consultationServices(serviceDesc,active) VALUES(?,'1')", 'mapped HCT specialty', [specialty]);
    sql.execute('UPDATE DemographicContact SET role=? WHERE id=? AND demographicNo=? AND note=?', [specialty, link, patient, marker]);
    await save(owned.reason('mapped'), service);
  });
  await s.step('#6202: a member without a college or billing number is listed, marked and saves', async () => {
    // Free text with quotes, a backtick and "${" also proves the team registry is emitted as
    // encoded JavaScript strings: before #6202 this note broke out of a template literal.
    const numberless = {phone: '555-0110', fax: '555-0111', address: 'FAKE "numberless" address',
      note: marker + ' dietitian `${globalThis.hctInjected = 1}` </script> note'};
    for (const number of [null, '']) {
      const member = teamMember(numberless, number, unmatched, 1, 'numberless');
      const {form, list} = await openForm();
      const option = form.locator(`select#specialist option[value="${member.link}"]`);
      h.assert(await option.count() === 1, 'A team member without a college or billing number is missing from the list');
      h.assert(await option.getAttribute('data-referral-number') === 'missing', 'The numberless member is not marked');
      h.assert(/no college\/billing #/.test(await option.innerText()), 'The numberless member has no visible marker');
      h.assert(await form.locator(`select#specialist option[value="${link}"]`).getAttribute('data-referral-number') === 'present',
        'A member with a number is marked as missing one');
      const hint = form.locator('#hctNoNumberHint');
      h.assert(await hint.isVisible() && /prints without a number/.test(await hint.innerText()),
        'The form does not explain what a missing number means');
      h.assert(await form.evaluate(() => globalThis.hctInjected) === undefined, 'A contact note ran as script');
      await form.close();
      await list.close();
      await save(owned.reason(number === null ? 'numberless-null' : 'numberless-blank'), '0',
        {link: member.link, details: numberless});
      sql.execute('UPDATE DemographicContact SET deleted=1 WHERE id=? AND demographicNo=? AND note=?',
        [member.link, patient, marker]);
    }
  });
  await s.step('#6202: a member without consent to contact is not offered but is counted', async () => {
    const withoutConsent = teamMember(details, '999997', unmatched, 0, 'non-consenting');
    const {form, list} = await openForm();
    h.assert(await form.locator(`select#specialist option[value="${withoutConsent.link}"]`).count() === 0,
      'A member without consent to contact was offered');
    const hint = form.locator('#hctNoConsentHint');
    h.assert(await hint.isVisible() && /^1 health care team member\(s\) not listed: consent to contact/.test(
      (await hint.innerText()).trim()), 'The form does not say a member was left out for want of consent');
    h.assert(await form.locator('#hctNoNumberHint').count() === 0,
      'The missing-number hint shows although every listed member has a number');
    await form.close();
    await list.close();
  });
}

if (require.main === module) runWorkflow('consultation-health-care-team', workflow, {openMaster: false});
module.exports = {workflow, cleanupUnusedHctSignatures};
