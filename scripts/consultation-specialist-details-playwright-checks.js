#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Picking a consultant on the consultation form loads that specialist's details
 * (ConsultationFormRequest.jsp onSpecialistSelected): the form posts the id to
 * getProfessionalSpecialist, puts the answer's annotation in #annotation, and shows the
 * eForm button only for a specialist with an eForm; the button opens that eForm for the
 * patient (eform/efmformadd_data?fid=...). Picking a specialist without either clears the
 * annotation and hides the button. No functional check named getProfessionalSpecialist before
 * (lib/authz-read-routes.js lists it for the authorization read check only).
 *
 * Fixtures: an owned eForm, consultation service and two specialists (one with an
 * annotation and the eForm, one with neither); all removed by their own keys.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const DETAILS_ROUTE = '/getProfessionalSpecialist';
const EFORM_ROUTE = '/eform/efmformadd_data';
const TEMPLATE_HTML = '<html><body><form><input type="text" name="specialistDetailsFixture"></form></body></html>';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  let service;
  let eformId;
  const specialists = [];
  // The form is only opened, never saved, so no consultation rows are made or removed.
  s.cleanup(() => {
    if (service) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${service}; DELETE FROM consultationServices WHERE serviceId=${service}`);
    for (const id of specialists) sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${id}`);
    if (eformId) sql.execute(`DELETE FROM eform WHERE fid=${eformId} AND form_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(marker)}`) === '0', 'Owned service remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE lName LIKE ${q(`${marker}%`)}`) === '0', 'Owned specialists remain');
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${q(marker)}`) === '0', 'Owned eForm remains');
  });
  eformId = h.insertId(sql, `INSERT INTO eform (form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
      showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES (${q(marker)},'','Specialist details fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(TEMPLATE_HTML)},0,0,'',0,1)`, 'owned eForm');
  service = h.insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(marker)},'1')`, 'consultation service');
  const annotation = `${marker} annotation for the referring provider`;
  const detailed = `${marker}-A`;
  const plain = `${marker}-B`;
  specialists.push(h.insertId(sql, `INSERT INTO professionalSpecialists
      (fName,lName,annotation,eformId,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES ('FAKE',${q(detailed)},${q(annotation)},${eformId},NOW(),0,0,0,0)`, 'detailed specialist'));
  specialists.push(h.insertId(sql, `INSERT INTO professionalSpecialists
      (fName,lName,annotation,eformId,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES ('FAKE',${q(plain)},NULL,NULL,NOW(),0,0,0,0)`, 'plain specialist'));
  const [detailedId, plainId] = specialists;
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${service},${detailedId}),(${service},${plainId})`);

  const list = await s.context.newPage();
  h.wireStrictPage(list, 'patient-consultations', s.recorder);
  await list.goto(`${s.config.baseUrl}/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${patient}`);
  const form = await s.popup(list, list.getByRole('link', { name: /New Consultation/i }), 'specialist-details');
  await form.locator('#specialistInput').waitFor();
  await form.waitForFunction(() => typeof allSpecialistsData !== 'undefined' && allSpecialistsData.length > 0);
  await form.waitForLoadState('networkidle');

  const detailsFor = id => form.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname.endsWith(DETAILS_ROUTE)
    && new URLSearchParams(response.request().postData() || '').get('id') === String(id), { timeout: 20000 });
  async function pick(selector, text) {
    await form.locator(selector).click();
    await form.locator(selector).fill(text);
    await form.locator('.ui-autocomplete:visible .ui-menu-item').filter({ hasText: text }).first().click();
  }
  const eformButton = form.locator('#eFormButton');
  await s.step('the owned consultation service is picked', async () => {
    await pick('#serviceInput', marker);
    h.assert(await form.locator('#service').inputValue() === service, 'The service picker did not select the owned service');
  });

  await s.step('picking a consultant loads its annotation and shows its eForm button', async () => {
    const details = detailsFor(detailedId);
    await pick('#specialistInput', detailed);
    h.assert(await form.locator('#specialist').inputValue() === detailedId, 'The picker did not select the detailed specialist');
    const answer = await (await details).json();
    h.assert(answer.annotation === annotation && String(answer.eformId) === eformId,
      `getProfessionalSpecialist did not return the owned annotation and eForm: ${JSON.stringify({ annotation: answer.annotation, eformId: answer.eformId })}`);
    await form.waitForFunction(expected => document.getElementById('annotation').value === expected, annotation);
    await eformButton.waitFor({ state: 'visible' });
  });

  await s.step('the eForm button opens that eForm for this patient', async () => {
    const eform = await s.popup(form, eformButton, 'specialist-eform');
    await eform.waitForURL(url => url.pathname.endsWith(EFORM_ROUTE), { timeout: 20000 });
    const url = new URL(eform.url());
    h.assert(url.searchParams.get('fid') === eformId, `The eForm button opened fid=${url.searchParams.get('fid')} instead of the specialist's eForm`);
    h.assert(url.searchParams.get('demographic_no') === patient, 'The eForm button opened the eForm for another patient');
    await eform.locator('input[name="specialistDetailsFixture"]').waitFor({ state: 'attached' });
    await eform.close();
  });

  await s.step('picking a consultant without details clears the annotation and hides the eForm button', async () => {
    const details = detailsFor(plainId);
    await pick('#specialistInput', plain);
    h.assert(await form.locator('#specialist').inputValue() === plainId, 'The picker did not select the plain specialist');
    const answer = await (await details).json();
    h.assert(!answer.annotation && !(answer.eformId > 0), `getProfessionalSpecialist returned details for the plain specialist: ${JSON.stringify(answer)}`);
    await form.waitForFunction(() => document.getElementById('annotation').value === '');
    await eformButton.waitFor({ state: 'hidden' });
  });
}

if (require.main === module) runWorkflow('consultation-specialist-details', workflow, { openMaster: false });
module.exports = { workflow };
