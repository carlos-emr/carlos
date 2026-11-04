#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The removed demographic/Contact dispatch targets stay removed (issues #3509 and #4402).
 * User path: Schedule ▸ Search ▸ Master Record ▸ Contacts "Manage" (demographic/Contact?method=manage).
 *
 * Contact2Action used to route eight `method` values that no page sends: setEmergencyContact,
 * setDNC, setMRP, addPharmacy, removePharmacy, addPharmacyInfo, editPharmacyInfo and
 * savePharmacyInfo. The writers among them changed a patient's emergency-contact, consent and
 * most-responsible-provider flags and preferred pharmacies with no privilege check, and setDNC
 * wrote a "do not contact" value into a consent field with opposite polarity on its two branches.
 * They were removed, so a request that still names one falls through to the read-only Manage
 * Contacts view.
 *
 * HOW. The check reads the Manage Contacts form the page itself renders (its fields and the CSRF
 * token CSRFGuard injected) and posts it with `method` set to each removed target, plus the
 * parameters that target used to read, aimed at rows the check seeded with the values each writer
 * would change: a personal association (ec ''), a professional one (consent 0, mrp 0) and a
 * preferred pharmacy (status 1, consent 1). The patient's associations, pharmacy list and the
 * marker pharmacy directory rows must stay byte-for-byte unchanged (their timestamps move on any
 * UPDATE). Each answer must also come from the application: today the Manage Contacts page, which
 * proves CSRFGuard accepted the POST and the action dispatched it; an application 4xx other than
 * 403 (CSRFGuard's refusal) is accepted too, so a later change that rejects unknown methods
 * outright still passes. A WAF page or a 5xx is never a pass. The positive control, sent LAST
 * through the same request, names a live writer (removeContact) and must soft-delete its
 * association: the request is one the application accepts, so a restored writer would be seen.
 *
 * Fixtures: the runWorkflow FAKE- patient; one marker-named Contact and pharmacyInfo row, two
 * DemographicContact rows and one demographicPharmacy row for that patient, all seeded by SQL.
 * Cleanup deletes them, and any marker pharmacyInfo row a regressed savePharmacyInfo wrote, and
 * asserts they are gone.
 */
const { assert, sqlString, isWafPage, APPLICATION_HEADER } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const NAME = 'contact-removed-targets';
const TYPE_PROVIDER = 0; // DemographicContact.TYPE_PROVIDER
const TYPE_CONTACT = 2; // DemographicContact.TYPE_CONTACT

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const contactName = `${marker}R`;
  const pharmacyName = `${marker}Rx`;
  const ownedPharmacies = `SELECT recordID FROM pharmacyInfo WHERE LOCATE(${sqlString(marker)}, name) = 1`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient};
      DELETE FROM demographicPharmacy WHERE demographic_no=${patient};
      DELETE FROM pharmacyInfo WHERE recordID IN (SELECT recordID FROM (${ownedPharmacies}) t);
      DELETE FROM Contact WHERE lastName=${sqlString(contactName)}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM pharmacyInfo WHERE LOCATE(${sqlString(marker)}, name) = 1)
      + (SELECT COUNT(*) FROM Contact WHERE lastName=${sqlString(contactName)})`) === '0',
    'The owned contact, pharmacy or association rows were not removed');
  });

  const contact = sql.value(`INSERT INTO Contact (type,lastName,firstName,deleted)
    VALUES ('Contact',${sqlString(contactName)},'Removed',0); SELECT LAST_INSERT_ID()`);
  const association = (type, contactId, category, consent) => sql.value(`INSERT INTO DemographicContact
    (facilityId,creator,created,updateDate,deleted,demographicNo,contactId,role,type,category,sdm,ec,note,consentToContact,active,mrp)
    VALUES (0,${sqlString(provider)},NOW(),NOW(),0,${patient},${sqlString(contactId)},'Friend',${type},${sqlString(category)},
      '','',${sqlString(marker)},${consent},1,0); SELECT LAST_INSERT_ID()`);
  const personal = association(TYPE_CONTACT, contact, 'personal', 1);
  const professional = association(TYPE_PROVIDER, provider, 'professional', 0);
  const pharmacy = sql.value(`INSERT INTO pharmacyInfo (name,city,province,status,uid)
    VALUES (${sqlString(pharmacyName)},'Toronto','ON','1',0); SELECT LAST_INSERT_ID()`);
  const preferred = sql.value(`INSERT INTO demographicPharmacy (pharmacyID,demographic_no,status,preferredOrder,consentToContact)
    VALUES (${pharmacy},${patient},'1',1,1); SELECT LAST_INSERT_ID()`);
  for (const id of [contact, personal, professional, pharmacy, preferred]) {
    assert(/^[1-9]\d*$/.test(id), 'A contact or pharmacy fixture was not created');
  }

  // Everything a removed writer could touch for this patient, including the ON UPDATE timestamps.
  const snapshot = () => JSON.stringify([
    sql.rows(`SELECT * FROM DemographicContact WHERE demographicNo=${patient} ORDER BY id`),
    sql.rows(`SELECT * FROM demographicPharmacy WHERE demographic_no=${patient} ORDER BY id`),
    sql.rows(`SELECT * FROM pharmacyInfo WHERE LOCATE(${sqlString(marker)}, name) = 1 ORDER BY recordID`),
  ]);

  // Each removed target with the parameters its old body read, aimed at the seeded row it changed.
  const probes = [
    ['setEmergencyContact', { contactId: personal, setting: 'true' }],
    ['setDNC', { contactId: professional, contactGroup: 'professional', dnc: 'true' }],
    ['setDNC', { contactId: preferred, contactGroup: 'pharmacy', dnc: 'true' }],
    ['setMRP', { contactId: professional }],
    ['addPharmacy', { contactId: pharmacy, preferredOrder: '2' }],
    ['removePharmacy', { contactId: preferred }],
    ['addPharmacyInfo', {}],
    ['editPharmacyInfo', { contactId: preferred }],
    ['savePharmacyInfo', { 'pharmacyInfo.name': `${pharmacyName}New`, 'pharmacyInfo.city': 'Toronto' }],
  ];
  let action;
  let fields;
  let token;
  const post = overrides => {
    const body = new URLSearchParams(fields);
    for (const [key, value] of Object.entries(overrides)) {
      if (value === null) body.delete(key); else body.set(key, String(value));
    }
    return s.context.request.post(action, {
      headers: { 'CSRF-TOKEN': token, 'Content-Type': 'application/x-www-form-urlencoded' },
      data: body.toString(), maxRedirects: 0, failOnStatusCode: false,
    });
  };

  await s.step('Manage Contacts renders the patient form the probes reuse, with its CSRF token', async () => {
    const editor = await s.popup(s.master, masterRecordLink(s.master, 'Contact?method=manage').first(), 'contacts');
    await editor.waitForFunction(() => document.querySelector('input[name="CSRF-TOKEN"]')?.value);
    token = await editor.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const form = await editor.locator('form[name="contactForm"]').evaluate(element => ({
      action: element.action, entries: Array.from(new FormData(element).entries()),
    }));
    action = form.action;
    fields = form.entries;
    const original = new URLSearchParams(fields);
    assert(original.get('method') === 'saveManage', 'The Manage Contacts form does not target saveManage');
    assert(original.get('demographic_no') === patient, 'The Manage Contacts form is not for the owned patient');
    assert(new URL(action).pathname.endsWith('/demographic/Contact'), `The Manage Contacts form posts to ${action}`);
    await editor.close();
  });

  const results = [];
  await s.step('each removed target posted with its old parameters is answered by the application', async () => {
    for (const [method, params] of probes) {
      const before = snapshot();
      const response = await post({ method, demographic_no: patient, ...params });
      const status = response.status();
      const text = await response.text().catch(() => '');
      const after = snapshot();
      const manageView = status === 200 && text.includes('name="contactForm"') && text.includes('value="saveManage"');
      // Only CARLOS's own filters add this header; the WAF page and a bare proxy answer lack it.
      const fromApplication = Object.prototype.hasOwnProperty.call(response.headers(), APPLICATION_HEADER);
      const refused = fromApplication && status >= 400 && status < 500 && status !== 403;
      const label = `${method}${params.contactGroup ? ` (${params.contactGroup})` : ''}`;
      results.push({ label, status, waf: isWafPage(status, text), answered: manageView || refused, changed: before !== after });
      console.log(`  probe ${NAME}: ${label} -> HTTP ${status}${manageView ? ', Manage Contacts view' : ''}`
        + `${refused ? ', application refusal' : ''}${before !== after ? ', CHANGED the owned rows' : ''}`);
    }
  });

  await s.step('the same request naming a live writer (removeContact) is accepted and deletes its association', async () => {
    const response = await post({
      method: 'removeContact', demographic_no: patient, contactId: personal, 'contact.delete': null, 'procontact.delete': null,
    });
    const status = response.status();
    assert(!isWafPage(status, await response.text().catch(() => '')),
      'The positive control was blocked by the WAF front door, so it proves nothing about the request');
    await expectValue(sql, `SELECT deleted FROM DemographicContact WHERE id=${personal}`, '1',
      `The positive control (HTTP ${status}) did not delete its association, so the probes did not reach a writer`);
  });

  await s.step('no removed target wrote a contact flag, a pharmacy link or a pharmacy record', async () => {
    const failed = results.filter(r => r.changed || r.waf || !r.answered);
    assert(results.length === probes.length, `Only ${results.length} of ${probes.length} probes were sent`);
    assert(!failed.length, `${failed.length} of ${results.length} removed target(s) failed: `
      + failed.map(r => `${r.label} HTTP ${r.status}${r.waf ? ' (WAF page)' : ''}`
        + `${r.answered ? '' : ' (neither the Manage Contacts view nor an application 4xx other than 403)'}`
        + `${r.changed ? ' and CHANGED the owned rows' : ''}`).join('; '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
