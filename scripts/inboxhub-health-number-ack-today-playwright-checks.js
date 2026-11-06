#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Two Inbox criteria no check exercises: the search panel's Health Number, and the list toolbar's Acknowledged toggle.
 * inboxhub-filters covers the type badges and the review-status partition; inbox-document-dates the date search.
 * User path: Schedule ▸ Inbox (the Inboxhub, list mode) ▸ the search panel's Specific Patients ▸ Last Name and Health
 * Number ▸ Search; the toolbar's Acknowledged toggle; the search panel's Acknowledged status ▸ Search; last, the search
 * panel's Any provider.
 * What the page means (read from LabDataController and Hl7TextInfoDaoImpl): the Health Number criterion keeps a
 * patient's lab when the patient's own health number contains it; the toggle shows the acknowledged results whose
 * acknowledgement (the routing row's time) is today; the search panel's Acknowledged status shows every acknowledged
 * result, older ones included; Any provider lists every provider's results, each lab once.
 * Asserted, on the two owned patients only (both carry the run marker as last name, so the marker narrows every list
 * to this run's rows); every search but the last is the test login's own results (Specific provider, the default):
 *   - Inbox ▸ Create Lab files one lab for each owned patient, routed to the test login;
 *   - with the marker as last name, each patient's Health Number lists that patient's lab and not the other's;
 *   - with both labs acknowledged, the Acknowledged toggle lists the one acknowledged today and not the one
 *     acknowledged three days ago;
 *   - the search panel's Acknowledged status lists both;
 *   - LAST: with the first lab also routed to, and acknowledged by, a second provider (as a lab forwarding rule routes
 *     it), Any provider with the Acknowledged status lists each lab once.
 * Expected to FAIL on release at the last step until #5016 is fixed (finding 443): the patient search groups the
 * results by lab only for the receivedCreated date type, so Any provider lists the first lab twice.
 * Fixtures: the owned FAKE patient and a second owned FAKE patient, each given a FAKE health number that fails the
 * Ontario check digit, so no real card can carry it; one Create Lab lab per patient (the second is moved to its
 * patient by its patient routing row); both acknowledged for the test login by the routing rows (one today, one
 * three days ago), as set-up, since an acknowledgement in the past cannot be made through the page; a FAKE provider
 * with no login, and its acknowledged routing row for the first lab. The labs sit in the test login's inbox while the
 * check runs, where other checks read it, so it must not run concurrently with them (live wrapper: EXCLUSIVE=1).
 * Cleanup removes both labs with all their routing and messages, their upload archives (LAB_UPLOAD_DOCUMENT_STORE),
 * the FAKE provider and the second patient, and asserts each is gone.
 */
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createOwnedPatient, removeOwnedPatient } = require('./lib/owned-patient');
const { settle, shownRows } = require('./inboxhub-filters-playwright-checks');
const { submitCreateLab } = require('./lab-manual-entry-cumulative-playwright-checks');
const { removeOwnedHl7Labs } = require('./lab-forwarding-rules-playwright-checks');

const TIMEOUT = 60000;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  labs: 'Inbox ▸ Create Lab files one lab for each owned patient, routed to the test login',
  hin: 'searching by Health Number lists that patient\'s lab and not the other patient\'s',
  today: 'the Acknowledged toggle lists the lab acknowledged today and not one acknowledged three days ago',
  history: 'the search panel\'s Acknowledged status lists both acknowledged labs',
  anyProvider: 'with a lab also routed to a second provider, Any provider lists each lab once',
};

/** A 10-digit health number no patient holds whose Ontario mod-10 check digit is deliberately wrong. */
function fakeHealthNumber(sql, taken = []) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const base = [9, 8, 7, 6, ...Array.from({ length: 5 }, () => crypto.randomInt(10))];
    let sum = 0;
    base.forEach((digit, index) => {
      if (index % 2 === 0) { const doubled = digit * 2; sum += doubled > 9 ? doubled - 9 : doubled; } else sum += digit;
    });
    const wrongCheck = ((10 - (sum % 10)) % 10 + 5) % 10;
    const hin = base.join('') + String(wrongCheck);
    if (!taken.includes(hin) && sql.value(`SELECT COUNT(*) FROM demographic WHERE hin LIKE ${h.sqlString(`%${hin}%`)}`) === '0') {
      return hin;
    }
  }
  throw new Error('No unused FAKE health number found');
}

/** A provider number no provider, login, routing row or log entry uses. */
function unusedProviderNo(sql) {
  for (let attempt = 0; attempt < 30; attempt++) {
    const candidate = h.sqlString(String(crypto.randomInt(800000, 900000)));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${candidate})
      + (SELECT COUNT(*) FROM security WHERE provider_no=${candidate})
      + (SELECT COUNT(*) FROM providerLabRouting WHERE provider_no=${candidate})
      + (SELECT COUNT(*) FROM log WHERE provider_no=${candidate})`) === '0') {
      return candidate.slice(1, -1);
    }
  }
  throw new Error('No unused provider number found');
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const stamp = crypto.randomBytes(3).toString('hex').toUpperCase();
  const accession = { a: `IHA${stamp}`, b: `IHB${stamp}` };
  for (const value of Object.values(accession)) {
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${q(value)}`) === '0', 'A run accession is already in use');
  }
  // Registered first, so they run last: the second patient and the FAKE provider go after the labs and their routing.
  let second = null;
  s.cleanup(() => {
    if (second) removeOwnedPatient(sql, second, marker);
    h.assert(!second || sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${second}`) === '0',
      'The second owned patient was not removed');
  });
  let other = null;
  s.cleanup(() => {
    if (!other) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE provider_no=${q(other)}`) === '0',
      'The FAKE provider still has routing rows after the owned labs were removed');
    sql.execute(`DELETE FROM provider WHERE provider_no=${q(other)} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(other)}`) === '0', 'The FAKE provider was not removed');
  });
  s.cleanup(() => {
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum IN (${q(accession.a)},${q(accession.b)})`).map(([id]) => id);
    removeOwnedHl7Labs(sql, labs);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum IN (${q(accession.a)},${q(accession.b)})`) === '0',
      'The owned labs were not removed');
  });
  second = createOwnedPatient(sql, { marker, provider, firstName: 'Second' });
  const hin = { a: fakeHealthNumber(sql) };
  hin.b = fakeHealthNumber(sql, [hin.a]);
  sql.execute(`UPDATE demographic SET hin=${q(hin.a)}, ver='' WHERE demographic_no=${patient} AND last_name=${q(marker)};
    UPDATE demographic SET hin=${q(hin.b)}, ver='' WHERE demographic_no=${second} AND last_name=${q(marker)}`);

  const lab = {};
  let inbox;
  await s.step(STEP.labs, async () => {
    for (const [key, test] of [['a', { code: '2010', name: 'HEMOGLOBIN', value: '137', unit: 'g/L', low: '120', high: '160', flag: 'N' }],
      ['b', { code: '2013', name: 'HEMATOCRIT', value: '0.40', unit: 'L/L', low: '0.36', high: '0.46', flag: 'N' }]]) {
      const created = await submitCreateLab(s, { accession: accession[key], tests: [test], label: `hin-lab-${key}` });
      await created.form.close();
      if (created.inbox !== s.schedule) await created.inbox.close();
      lab[key] = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(accession[key])}`);
      h.assert(/^[1-9]\d*$/.test(lab[key]), `Create Lab filed no lab under ${accession[key]}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${lab[key]}
        AND provider_no=${q(provider)} AND status='N'`) === '1', `The ${key} lab is not routed New to the test login`);
    }
    // Create Lab matches both to the run patient by name; the second belongs to the second owned patient.
    sql.execute(`UPDATE patientLabRouting SET demographic_no=${second} WHERE lab_type='HL7' AND lab_no=${lab.b}
      AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${lab.a}`) === patient
      && sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${lab.b}`) === second,
    'The labs are not each matched to their own patient');
  });

  // shownRows() answers each row as "<lab type>:<segment id>" (and fails on an unidentified or duplicated row).
  const owned = rows => rows.filter(row => row === `HL7:${lab.a}` || row === `HL7:${lab.b}`)
    .map(row => row.slice('HL7:'.length)).sort();
  const expectRows = (rows, expected, label) => {
    const got = owned(rows);
    h.assert(JSON.stringify(got) === JSON.stringify([...expected].sort()),
      `${label}: the list holds owned labs ${JSON.stringify(got)}, not ${JSON.stringify([...expected].sort())}`);
  };
  // A search submits the form and reloads the Inbox, whose sidebar starts collapsed on every load. Every search is
  // the test login's own results (Specific provider, the page default) unless it asks for Any provider: a lab
  // forwarding rule (the Ontario demo data has one) routes each new lab to a second provider as well.
  const search = async ({ status, healthNumber = '', providers = 'own' }) => {
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    if (providers === 'any') await inbox.locator('#anyProvider').check();
    else h.assert(await inbox.locator('#specificProvider').isChecked(), 'The search panel is not on the test login\'s own results');
    // Clicked, as a clinician does: the radio's onclick is what clears the toolbar's Acknowledged toggle, and check()
    // sends no click to a radio that is already selected (the toggle selects Acknowledged itself).
    await inbox.locator(status).click();
    h.assert(await inbox.locator(status).isChecked(), `The search panel did not select ${status}`);
    await inbox.locator('#specificPatients').check();
    await inbox.locator('#inputLastName').fill(marker);
    await inbox.locator('#inputHIN').fill(healthNumber);
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, TIMEOUT);
    if (providers !== 'any') {
      h.assert(await inbox.locator('#findProvider').inputValue() === provider, 'The search did not run on the test login\'s own results');
    }
    return shownRows(inbox);
  };

  await s.step(STEP.hin, async () => {
    ({ page: inbox } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink'), {
      context: s.context, label: 'hin-inbox', recorder: s.recorder, timeout: TIMEOUT,
    }));
    await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: TIMEOUT });
    if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
    await settle(inbox, TIMEOUT);
    h.assert(await inbox.locator('#specificProvider').isChecked(), 'The Inbox does not default to the test login\'s own results');
    expectRows(await search({ status: '#statusNew' }), [lab.a, lab.b], 'The marker alone');
    expectRows(await search({ status: '#statusNew', healthNumber: hin.a }), [lab.a], 'The first patient\'s Health Number');
    expectRows(await search({ status: '#statusNew', healthNumber: hin.b }), [lab.b], 'The second patient\'s Health Number');
  });

  await s.step(STEP.today, async () => {
    sql.execute(`UPDATE providerLabRouting SET status='A', timestamp=NOW() WHERE lab_type='HL7' AND lab_no=${lab.a}
      AND provider_no=${q(provider)};
      UPDATE providerLabRouting SET status='A', timestamp=DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE lab_type='HL7'
      AND lab_no=${lab.b} AND provider_no=${q(provider)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${lab.a},${lab.b})
      AND provider_no=${q(provider)} AND status='A'`) === '2', 'The two labs were not set acknowledged');
    // Back to the marker-only list, then the toolbar toggle, which re-runs it as today's acknowledgements.
    await search({ status: '#statusNew' });
    const toggle = inbox.locator('#ackToggle');
    h.assert(await toggle.count() === 1 && !await toggle.isChecked(), 'The list toolbar has no unticked Acknowledged toggle');
    await toggle.check();
    await settle(inbox, TIMEOUT);
    expectRows(await shownRows(inbox), [lab.a], 'The Acknowledged toggle');
  });

  await s.step(STEP.history, async () => {
    expectRows(await search({ status: '#statusAcknowledged' }), [lab.a, lab.b], 'The search panel\'s Acknowledged status');
    h.assert(!await inbox.locator('#ackToggle').isChecked(), 'The search panel\'s Acknowledged status left the toolbar toggle on');
  });

  await s.step(STEP.anyProvider, async () => {
    // As set-up, what a lab forwarding rule and the second provider's own review leave: a second routing row for the
    // first lab, acknowledged, for a FAKE provider with no login.
    other = unusedProviderNo(sql);
    sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
      SELECT ${q(other)},${q(marker)},'Forwarded',provider_type,specialty,sex,'1',${q(provider)},NOW()
      FROM provider WHERE provider_no=${q(provider)};
      INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status,timestamp) VALUES (${q(other)},${lab.a},'HL7','A',NOW())`);
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${lab.a} AND status='A'
      AND provider_no IN (${q(provider)},${q(other)})`) === '2', 'The first lab is not acknowledged by both providers');
    // shownRows() fails first if the list shows a lab twice (#5016).
    expectRows(await search({ status: '#statusAcknowledged', providers: 'any' }), [lab.a, lab.b], 'Any provider');
  });
}

if (require.main === module) runWorkflow('inboxhub-health-number-ack-today', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, STEP };
