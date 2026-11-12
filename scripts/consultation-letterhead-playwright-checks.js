#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// consultation-letterhead: the letterhead a referral goes out on.
// User path:
//   1. E-Chart ▸ Consultations "+" (a new request) ▸ Letterhead: choosing a provider shows that
//      provider's address, phone and fax in the letterhead; choosing the clinic again shows the
//      clinic's;
//   2. Schedule ▸ Consultations ▸ a saved referral whose letterhead is that provider opens with the
//      provider selected and the provider's details shown;
//   3. its Update And Print Preview prints the provider's name, address and phone at the top of the
//      PDF, not the clinic's.
// A provider's letterhead details come from that provider's prescription contact preferences
// (rxAddress, rxCity, rxProvince, rxPostal, rxPhone, faxnumber; RxProviderData.convertProvider), read
// on every page. The check sets them for an existing active provider: the provider list itself is
// cached for five minutes (CacheConfig.ACTIVE_PROVIDERS), so a provider added by SQL would not be
// offered. No other check touches the letterhead; the print-preview and edit-status checks print with
// the default one.
// Fixtures: the owned FAKE- patient; FAKE contact preferences on one existing active provider (other
// than the signed-in one), snapshotted and restored; a FAKE service, specialist and referral (seeded
// by SQL like consultation-edit-status). Cleanup deletes the referral with its archive and ext rows
// and stamped signature, the service and the specialist, restores the preferences, and asserts it.
// The preferences belong to a clinic provider, so run it EXCLUSIVE.
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;
const squash = text => String(text).replace(/\s+/g, ' ').trim();

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const serviceName = `${marker}-Svc`;
  const specialist = `${marker}-Spec`;
  const reason = `${marker} letterhead referral`;
  const letterheadNo = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no NOT LIKE '-%'
    AND provider_no<>${q(provider)} AND provider_type='doctor' AND first_name<>'' AND last_name<>''
    AND lastUpdateDate < NOW() - INTERVAL 10 MINUTE ORDER BY provider_no+0 LIMIT 1`);
  h.assert(/^\d+$/.test(letterheadNo || ''), 'No existing active doctor (other than the signed-in one) to use as the letterhead');
  const surname = sql.value(`SELECT last_name FROM provider WHERE provider_no=${q(letterheadNo)}`);
  const prefs = {
    rxAddress: `${marker.slice(-6)} Letterhead Road`, rxCity: 'Faketown', rxProvince: 'ON', rxPostal: 'A1A 1A1',
    rxPhone: '555-0171', faxnumber: '555-0172',
  };
  const letterhead = {
    surname,
    address: squash(`${prefs.rxAddress} ${prefs.rxCity} ${prefs.rxProvince} ${prefs.rxPostal}`),
    phone: prefs.rxPhone,
    fax: prefs.faxnumber,
  };
  const names = Object.keys(prefs).map(q).join(',');
  const prefRows = () => sql.rows(`SELECT id, name, value, value IS NULL FROM property WHERE provider_no=${q(letterheadNo)}
    AND name IN (${names}) ORDER BY id`);
  const snapshot = prefRows();
  let requestId;
  let serviceId;
  s.cleanup(() => {
    const requests = sql.rows(`SELECT requestId FROM consultationRequests WHERE demographicNo=${patient}`)
      .map(([id]) => id).filter(id => /^[1-9]\d*$/.test(id));
    for (const id of requests) {
      sql.execute(`DELETE FROM consultationRequestExtArchive WHERE requestId=${id};
        DELETE FROM consultationRequestsArchive WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${id};
        DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`);
    }
    sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
    if (serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${q(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${q(specialist)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(specialist)})`) === '0',
    'Owned referral, service or specialist rows remain');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM property WHERE provider_no=${q(letterheadNo)} AND name IN (${names})`);
    for (const [id, name, value, isNull] of snapshot) {
      sql.execute(`INSERT INTO property (id,name,value,provider_no) VALUES (${id},${q(name)},${isNull === '1' ? 'NULL' : q(value)},${q(letterheadNo)})`);
    }
    h.assert(JSON.stringify(prefRows()) === JSON.stringify(snapshot), 'The letterhead provider\'s contact preferences were not restored');
  });
  sql.execute(`DELETE FROM property WHERE provider_no=${q(letterheadNo)} AND name IN (${names});
    INSERT INTO property (name,value,provider_no) VALUES ${Object.entries(prefs).map(([name, value]) => `(${q(name)},${q(value)},${q(letterheadNo)})`).join(',')}`);
  serviceId = h.insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(serviceName)},'1')`, 'service');
  const specId = h.insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${q(specialist)},'555-0160','555-0161','2 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);
  requestId = h.insertId(sql, `INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,
     allergies,concurrentProblems,statusText,sendTo,patientWillBook,letterheadName,letterheadAddress,letterheadPhone,letterheadFax,lastUpdateDate)
    VALUES(CURDATE(),${serviceId},${specId},${q(provider)},${patient},'1','2',${q(reason)},
     'Synthetic clinical information','','','','','',0,${q(letterheadNo)},${q(letterhead.address)},${q(letterhead.phone)},${q(letterhead.fax)},NOW())`,
  'referral');
  const [clinicAddress, clinicPhone, clinicFax] = sql.rows('SELECT clinic_address, clinic_phone, clinic_fax FROM clinic LIMIT 1')[0] || [];
  h.assert(clinicAddress && clinicPhone, 'The clinic has no address or phone to compare the letterhead with');

  /** The letterhead as the form shows it, and the hidden fields it will post. */
  async function shown(form) {
    const read = async id => squash(await form.locator(`#${id}`).evaluate(el => ('value' in el ? el.value : el.textContent) || ''));
    return {
      selected: await form.locator('#letterheadName').inputValue(),
      address: await read('letterheadAddressSpan'), phone: await read('letterheadPhoneSpan'), fax: await read('letterheadFaxSpan'),
      posted: { address: await read('letterheadAddress'), phone: await read('letterheadPhone'), fax: await read('letterheadFax') },
    };
  }
  /** The letterhead section starts collapsed; open it the way the clinician does. */
  async function openLetterhead(form) {
    const section = form.locator('#collapseLetterhead');
    if (!(await section.isVisible())) {
      await form.locator('a[href="#collapseLetterhead"]').click();
      await section.waitFor({ state: 'visible' });
    }
  }
  const showsProvider = l => l.address === letterhead.address && l.phone === letterhead.phone && l.fax === letterhead.fax
    && l.posted.address === letterhead.address && l.posted.phone === letterhead.phone && l.posted.fax === letterhead.fax;

  await s.step('choosing a provider letterhead shows its details, and the clinic again shows the clinic\'s', async () => {
    const chart = await s.chart();
    const form = await s.popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first(), 'consult-new');
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    await openLetterhead(form);
    const select = form.locator('#letterheadName');
    h.assert(await select.locator(`option[value="${letterheadNo}"]`).count() === 1, 'The Letterhead list does not offer the provider');
    const clinicOption = await select.locator('option').first().getAttribute('value');
    await select.selectOption({ value: letterheadNo });
    const provided = await shown(form);
    h.assert(showsProvider(provided), `Choosing the provider letterhead shows ${JSON.stringify(provided)}`);
    await select.selectOption({ value: clinicOption });
    const clinic = await shown(form);
    h.assert(clinic.address.includes(squash(clinicAddress)) && clinic.phone === squash(clinicPhone)
      && clinic.fax === squash(clinicFax || '') && clinic.posted.phone === squash(clinicPhone),
    `Choosing the clinic letterhead again shows ${JSON.stringify(clinic)}`);
    h.assert(!clinic.address.includes(letterhead.address), 'The clinic letterhead kept the provider\'s address');
    await form.close();
  });

  let form;
  await s.step('a saved referral opens with its provider letterhead', async () => {
    const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
      s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
      { context: s.context, recorder: s.recorder, label: 'consultations' });
    const row = list.locator(`tr[onclick*="requestId=${requestId}'"]`);
    await row.waitFor({ state: 'visible' });
    form = await s.popup(list, row, 'consult-saved');
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason, 'The list opened another referral');
    await openLetterhead(form);
    const saved = await shown(form);
    h.assert(saved.selected === letterheadNo && showsProvider(saved), `The saved referral opens with the letterhead ${JSON.stringify(saved)}`);
  });

  await s.step('Update And Print Preview prints the provider letterhead', async () => {
    const download = form.waitForEvent('download', { timeout: 30000 });
    await clickAndAwaitReload(form, form.locator('input[name="updateAndPrint"]'));
    const text = squash(pdfTextBuffer(fs.readFileSync(await (await download).path())));
    h.assert(text.includes(reason), 'The PDF is not the owned referral');
    const header = text.slice(0, Math.max(0, text.indexOf(reason)));
    h.assert(header.includes(letterhead.surname) && header.includes(letterhead.address) && header.includes(letterhead.phone),
      `The PDF's letterhead does not carry the provider's name, address and phone: ${JSON.stringify(header.slice(0, 300))}`);
    await form.close();
  });
}

if (require.main === module) {
  runWorkflow('consultation-letterhead', workflow, { openPatient: true, preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow };
