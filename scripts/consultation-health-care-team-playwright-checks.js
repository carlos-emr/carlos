#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// #6201: the actual HCT form saves both unmapped and mapped professional contacts.
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {createConsultationSubmitFixture} = require('./lib/consultation-submit-fixture');

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
      () => sql.execute('DELETE FROM consultationServices WHERE serviceDesc=?', [specialty])]) {
      try { cleanup(); } catch (error) { failures.push(error); }
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=? AND note=?)
      + (SELECT COUNT(*) FROM Contact WHERE lastName=? AND firstName=?)
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=?)`,
    [patient, marker, marker, 'FAKE', specialty]) === '0', 'Owned HCT fixtures remain');
    if (failures.length) throw new AggregateError(failures, 'HCT fixture cleanup failed');
  });
  const contact = h.insertId(sql, `INSERT INTO Contact
    (type,lastName,firstName,address,workPhone,fax,note,cpso,deleted)
    VALUES('ProfessionalContact',?,?,?,?,?,?,?,0)`, 'professional contact',
  [marker, 'FAKE', details.address, details.phone, details.fax, details.note, '999998']);
  const link = h.insertId(sql, `INSERT INTO DemographicContact
    (facilityId,creator,deleted,demographicNo,contactId,role,type,category,note,consentToContact,active)
    VALUES(0,?,0,?,?,?,2,'professional',?,1,1)`, 'patient HCT link',
  [provider, patient, contact, unmatched, marker]);
  const originalContact = JSON.stringify(sql.rows('SELECT * FROM Contact WHERE id=?', [contact]));

  async function formFor(reason) {
    const list = await s.context.newPage();
    h.wireStrictPage(list, 'hct-consultation-list', s.recorder);
    await h.gotoApp(list, s.config.baseUrl,
      `/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${patient}`);
    const form = await s.popup(list, list.locator('a.btn').filter({hasText: /New Consultation/i}).first(), 'hct-consultation');
    await form.locator('select#specialist').waitFor();
    await form.waitForLoadState('networkidle');
    await form.locator('select#specialist').selectOption(link);
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    h.assert(await form.locator('[name="phone"]').inputValue() === details.phone, 'HCT phone was not filled');
    h.assert(await form.locator('[name="fax"]').inputValue() === details.fax, 'HCT fax was not filled');
    h.assert(await form.locator('[name="address"]').inputValue() === details.address, 'HCT address was not filled');
    h.assert(await form.locator('#annotation').inputValue() === details.note, 'HCT annotation was not filled');
    return {form, list};
  }
  async function save(reason, service) {
    const {form, list} = await formFor(reason);
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
    h.assert(JSON.stringify(rows) === JSON.stringify([[service, link, '', reason]]), 'Saved HCT consultation has the wrong identity or service');
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
}

if (require.main === module) runWorkflow('consultation-health-care-team', workflow, {openMaster: false});
module.exports = {workflow};
