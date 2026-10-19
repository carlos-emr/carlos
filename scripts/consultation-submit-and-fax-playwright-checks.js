#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * New consultation request ▸ "Add Recipient" and "Submit And Fax": the request is saved once and the fax cover page is
 * addressed to the consultant and to the extra recipient. Nothing is ever sent.
 *
 * WHY THIS CHECK EXISTS. consultation-edit-status presses Update And Fax on a saved request, and the other consultation
 * checks submit with Submit or Submit And Print. Nothing pressed Submit And Fax on a new request, and nothing used the
 * form's Additional Fax Recipients (AddOtherFaxProvider), whose rows travel to the cover page as faxRecipients and come
 * back as its copy recipients.
 *
 * User path: the patient's consultation list (ViewDisplayDemographicConsultationRequests) ▸ New Consultation ▸ the owned
 * service and consultant through their pickers ▸ reason ▸ Letterhead (expand) ▸ Additional Fax Recipients: name + fax ▸
 * Add Recipient ▸ Submit And Fax (encounter/RequestConsultation) ▸ the fax cover page ▸ Send.
 * Asserted:
 *   1. Add Recipient refuses a number that isn't a fax number (its alert, no row) and lists a valid one with its name.
 *   2. Submit And Fax saves exactly one request for the owned patient with the chosen service, consultant and reason, and
 *      opens the cover page posting to ConsultationFormFax, addressed to the consultant's fax, with the added recipient
 *      among its copy recipients and no fax job.
 *   3. With no active fax sender account, Send is refused with an alert and queues no fax job (nothing leaves the host).
 *      Where an active sender account exists this step SKIPs before pressing Send, as consultation-edit-status does.
 * Fixtures: FAKE- patient (runWorkflow), FAKE- service, consultant (with a fax number) and service link seeded by SQL. The
 * service and consultant are in the clinic-wide pickers while the run lasts, so the check must not run concurrently with
 * other checks (live wrapper: EXCLUSIVE=1).
 * Cleanup deletes the request with its archive / ext rows and the consultation signature the save records for the owned
 * patient, and every owned row, and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const { insertId } = h;
const q = h.sqlString;
const TIMEOUT = 30000;
const CONSULTANT_FAX = '555-0171';
const EXTRA = { name: 'FAKE-PW Copy, Recipient', fax: '905-555-0199' };

async function workflow(s) {
  const { sql, marker, patient } = s;
  const serviceName = `${marker}-Svc`;
  const lastName = `${marker}-Spec`;
  const reason = `${marker} referral reason`;
  const requests = () => sql.rows(`SELECT requestId, serviceId, specId FROM consultationRequests WHERE demographicNo=${patient}
    ORDER BY requestId`);
  let serviceId;
  let specId;
  s.cleanup(() => {
    for (const [id] of requests()) {
      sql.execute(`DELETE FROM consultationRequestExtArchive WHERE requestId=${id};
        DELETE FROM consultationRequestsArchive WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${id};
        DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`);
    }
    sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
    if (serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${q(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${q(lastName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM consultationRequestsArchive WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(lastName)})`) === '0',
    'The owned request, its history or signature, or the owned service or consultant remains');
  });
  serviceId = insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(serviceName)},'1')`, 'service');
  specId = insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${q(lastName)},'555-0170',${q(CONSULTANT_FAX)},'3 Synthetic Way',NOW(),0,0,0,0)`, 'consultant');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);
  const faxJobs = () => sql.value(`SELECT COUNT(*) FROM faxes WHERE demographicNo=${patient}`);

  const list = await s.context.newPage();
  h.wireStrictPage(list, 'consultation-list', s.recorder);
  await h.gotoApp(list, s.config.baseUrl, `/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${patient}`);
  await h.assertNotErrorPage(list, 'consultation list');
  const form = await s.popup(list, list.locator('a.btn', { hasText: /New Consultation/i }).first(), 'consultation-form');
  await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: TIMEOUT });
  const added = () => form.locator('#addFaxRecipient input[name="faxRecipients"]').evaluateAll(inputs => inputs.map(input => JSON.parse(input.value)));

  /** Type into a picker and choose the entry that starts with `text`. */
  async function pick(input, text) {
    await form.locator(input).fill(text);
    const item = form.locator('ul.ui-autocomplete:visible li.ui-menu-item').filter({ hasText: text }).first();
    await item.waitFor({ state: 'visible', timeout: 15000 });
    await item.click();
    await form.waitForTimeout(250);
  }

  await s.step('Add Recipient refuses a number that is not a fax number and lists a valid one', async () => {
    if (await form.locator('#fax_button').count() !== 1) throw new h.SkipCheck('This install does not offer Submit And Fax (consultation_fax_enabled or the _fax right)');
    await pick('#serviceInput', serviceName);
    h.assert(await form.locator('#service').inputValue() === serviceId, 'The service picker did not choose the owned service');
    await pick('#specialistInput', lastName);
    h.assert(await form.locator('#specialist').inputValue() === specId, 'The consultant picker did not choose the owned consultant');
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    // Additional Fax Recipients sit in the Letterhead section, which starts collapsed.
    await form.locator('a[href="#collapseLetterhead"]').click();
    await form.locator('#searchHealthCareTeamInput').waitFor({ state: 'visible', timeout: 15000 });
    const addButton = form.locator('#addFaxRecipient button', { hasText: 'Add Recipient' });
    await form.locator('#searchHealthCareTeamInput').fill(EXTRA.name);
    await form.locator('#copytoSpecialistFax').fill('12');
    const refused = await h.withExpectedDialogs(form, () => addButton.click());
    h.assert(refused.length === 1 && refused[0].type === 'alert' && /fax number you entered is invalid/i.test(refused[0].text),
      `An invalid fax number raised ${JSON.stringify(refused)}, not the invalid-number alert`);
    h.assert((await added()).length === 0, 'Add Recipient listed an invalid fax number');
    await form.locator('#copytoSpecialistFax').fill(EXTRA.fax);
    await addButton.click();
    h.assert(JSON.stringify(await added()) === JSON.stringify([EXTRA]), `Add Recipient listed ${JSON.stringify(await added())}, not the typed recipient`);
    h.assert(await form.locator('#searchHealthCareTeamInput').inputValue() === '' && await form.locator('#copytoSpecialistFax').inputValue() === '',
      'Add Recipient did not clear its name and fax boxes');
  });

  await s.step('Submit And Fax saves one request and opens the cover page for the consultant and the added recipient', async () => {
    // The button is enabled once the form has a fax number to send to (updateFaxButton).
    await form.waitForFunction(() => !document.getElementById('fax_button').disabled, null, { timeout: 20000 });
    await clickAndAwaitReload(form, form.locator('#fax_button'));
    const cover = form.locator('#coverPageForm');
    await cover.waitFor({ state: 'attached', timeout: TIMEOUT });
    const saved = requests();
    h.assert(saved.length === 1 && saved[0][1] === serviceId && saved[0][2] === specId,
      `Submit And Fax stored ${JSON.stringify(saved)}, not one request with the owned service and consultant`);
    await expectValue(sql, `SELECT COUNT(*) FROM consultationRequests WHERE requestId=${saved[0][0]} AND reason=${q(reason)}`, '1',
      'The saved request does not carry the typed reason');
    h.assert((await cover.getAttribute('action')).endsWith('/encounter/oscarConsultationRequest/ConsultationFormFax'),
      'The fax cover page does not post to the consultation fax action');
    h.assert(await form.locator('#searchProfessionalSpecialist_fax').inputValue() === CONSULTANT_FAX,
      'The cover page is not addressed to the consultant\'s fax number');
    const copies = await form.locator('#coverPageForm input[type="hidden"][name="copyToRecipients"]').evaluateAll(inputs => inputs.map(input => input.value));
    // The cover page keeps the recipient's fax number as digits only.
    h.assert(copies.length === 1 && copies[0].includes(`"fax":"${EXTRA.fax.replace(/\D/g, '')}"`) && copies[0].includes(`"name":"${EXTRA.name}"`),
      `The cover page's copy recipients are ${JSON.stringify(copies)}, not the added recipient`);
    h.assert(faxJobs() === '0', 'Submit And Fax queued a fax job before Send');
  });

  await s.step('with no active fax sender account, Send is refused and queues nothing', async () => {
    // An active sender is an environment prerequisite, not a defect: Send cannot be pressed without risk of transmitting.
    if (sql.value('SELECT COUNT(*) FROM fax_config WHERE active=1') !== '0') {
      throw new h.SkipCheck('An active fax sender account exists; Send is not pressed where it could transmit');
    }
    h.assert(await form.locator('#senderFaxAccount option').count() === 0, 'The cover page offers a fax sender account');
    await form.locator('#searchProfessionalSpecialist_name').fill(`${lastName}, Ann`);
    const refusal = await h.withExpectedDialogs(form, () => clickAndAwaitReload(form, form.locator('#btnSend')));
    h.assert(refusal.length === 1 && /could not be sent/i.test(refusal[0].text) && /not configured/i.test(refusal[0].text),
      'The fax Send was not refused for the missing sender account');
    h.assert(faxJobs() === '0', 'A fax job was queued');
  });
}

if (require.main === module) runWorkflow('consultation-submit-and-fax', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
