#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Provider Linking Rules, end to end (issue #3971).
 *
 * The clinic-wide switch under Administration > Labs/Inbox sends a lab or HRM report that is
 * matched to a patient to the patient's Most Responsible Provider (MRP) as well as to the
 * ordering / delivered-to provider. This check drives every surface that decision touches,
 * the way an operator reaches it:
 *
 *   1. Administration > Labs/Inbox > Provider Linking Rules opens in the admin shell's frame
 *      and shows the stored state; the switch is turned OFF there and saved.
 *   2. Administration > Labs/Inbox > HL7 Lab Upload, a synthetic CML lab for a demo patient
 *      ordered by another provider: it reaches the ordering provider and NOT the MRP.
 *   3. The switch is turned ON and saved: the global property row reads true and the change is
 *      in the audit log.
 *   4. The same upload again (new accession): it reaches the ordering provider AND the MRP,
 *      and the automatic routing is audited.
 *   5. An upload for an unknown patient stays unmatched; matching it to the demo patient from
 *      the lab's Patient Search popup routes it to the MRP and drops the unassigned row.
 *   6. An HRM report is given an unclaimed (-1) row and unlinked with the viewer's (remove)
 *      link; assigning it to the patient in the HRM viewer routes it to the MRP, removes the
 *      unclaimed row, and the viewer reloads to show the MRP under Assigned Providers.
 *   7. The save route refuses GET (405) and refuses a POST without a CSRF token, and neither
 *      changes the stored switch.
 *
 * FIXTURES. Everything comes from the demo dataset: a patient with a unique 10-digit HIN whose
 * MRP is an active provider, a second active provider with a unique 6-digit OHIP number who does
 * not forward to that MRP, and an HRM document (demo-hrm-report.sql). A dataset without them
 * reports SKIP (exit 2), naming what is missing.
 *
 * CLEANUP. The switch's property rows are put back byte for byte (including "no row"); every
 * uploaded lab is deleted with its routing, measurement, message and upload-check rows; the HRM
 * document's patient and provider rows are restored from the snapshot taken before step 6.
 * Audit rows are left alone on purpose: the audit log is append-only. The uploaded .hl7 files
 * stay in the server's upload directory (harmless synthetic content on a disposable install).
 * Nothing this check prints carries a patient's name, HIN or date of birth.
 *
 * Environment: the common contract in lib/playwright-harness.js readConfig() (BASE_URL,
 * TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH, MYSQL_*). The account must hold _admin
 * write, _lab write and _hrm write. Run it against a disposable database only.
 */

'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {
  SkipCheck,
  assert,
  assertNotErrorPage,
  assertStrictPage,
  createRecorder,
  createSqlRunner,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  runCheck,
  sqlString,
  wireStrictPage,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');

const PROPERTY = 'provider_linking_rules';
const NULL_MARKER = '<NULL>';
const POLL_MS = 500;

/**
 * A CML ORU message the CMLHandler accepts: PID-4 carries the HIN, PID-7 the date of birth,
 * ORC-2 the accession number and OBR-16 the ordering provider's OHIP number. Segments are
 * separated by carriage returns as HL7 requires.
 */
function buildCmlMessage({ controlId, accession, hin, lastName, firstName, dob, sex, ohipNo }) {
  for (const [name, value] of Object.entries({ controlId, accession, hin, dob, sex, ohipNo })) {
    assert(/^[A-Za-z0-9-]+$/.test(String(value)), `${name} must be alphanumeric for an HL7 field`);
  }
  const clean = (value) => String(value).replace(/[|^~\\&\r\n]/g, ' ').trim();
  return [
    // MSH-9 must carry the trigger event: HAPI cannot pick a structure from a bare ORU.
    `MSH|^~\\&|Reports|CML|||202601020800||ORU^R01||${controlId}|2.3`,
    `PID|1|||${hin}^^ON|${clean(lastName)}^${clean(firstName)}||${dob}|${sex}`,
    `ORC|NW|${accession}|||F|||||||${ohipNo}^DR. PLAYWRIGHT CHECK|||20260101`,
    `OBR|1|${accession}||ML70^PROVIDER LINKING CHECK||20260101|20260102|||||||||${ohipNo}^DR. PLAYWRIGHT CHECK|||||||||F`,
    'OBX|1|ST|7010^PROVIDER LINKING CHECK|^^CHEMISTRY|SYNTHETIC|||N|||F',
    'FTS|1',
    '',
  ].join('\r');
}

/** Statements that put the switch's property rows back exactly as they were. */
function restorePropertyStatements(snapshot) {
  const statements = [`DELETE FROM property WHERE name=${sqlString(PROPERTY)}`];
  for (const [id, value, providerNo] of snapshot) {
    assert(/^\d+$/.test(id), 'property snapshot id must be numeric');
    const provider = providerNo === NULL_MARKER ? 'NULL' : sqlString(providerNo);
    const stored = value === null ? 'NULL' : sqlString(value);
    statements.push(`INSERT INTO property (id, name, value, provider_no) VALUES (${id}, ${sqlString(PROPERTY)}, ${stored}, ${provider})`);
  }
  return statements;
}

/** Statements that delete every row an uploaded lab left behind. */
function deleteLabStatements(labNos) {
  const ids = labNos.filter((id) => /^\d+$/.test(String(id)));
  if (!ids.length) {
    return [];
  }
  const list = ids.join(',');
  const labKeys = ids.map((id) => sqlString(id)).join(',');
  // Measurements first (found through their lab_no extension row), then every extension row of
  // those measurements. The derived table lets MariaDB read the table it is deleting from.
  return [
    `DELETE m FROM measurements m JOIN measurementsExt e ON e.measurement_id = m.id WHERE e.keyval='lab_no' AND e.val IN (${labKeys})`,
    `DELETE FROM measurementsExt WHERE measurement_id IN (SELECT id FROM (SELECT measurement_id AS id FROM measurementsExt WHERE keyval='lab_no' AND val IN (${labKeys})) x)`,
    `DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list})`,
    `DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list})`,
    `DELETE FROM providerLabRoutingLock WHERE lab_no IN (${list})`,
    `DELETE FROM fileUploadCheck WHERE id IN (SELECT id FROM (SELECT fileUploadCheck_id AS id FROM hl7TextMessage WHERE lab_id IN (${list})) f)`,
    `DELETE FROM hl7TextInfo WHERE lab_no IN (${list})`,
    `DELETE FROM hl7TextMessage WHERE lab_id IN (${list})`,
  ];
}

/** Statements that restore one HRM document's patient and provider rows from a snapshot. */
function restoreHrmStatements(hrmId, demographicRows, providerRows) {
  assert(/^\d+$/.test(String(hrmId)), 'HRM document id must be numeric');
  const value = (v) => (v === null ? 'NULL' : sqlString(v));
  const statements = [
    `DELETE FROM HRMDocumentToDemographic WHERE hrmDocumentId=${sqlString(hrmId)}`,
    `DELETE FROM HRMDocumentToProvider WHERE hrmDocumentId=${sqlString(hrmId)}`,
  ];
  for (const [id, demographicNo, timeAssigned] of demographicRows) {
    statements.push('INSERT INTO HRMDocumentToDemographic (id, demographicNo, hrmDocumentId, timeAssigned) VALUES '
      + `(${value(id)}, ${value(demographicNo)}, ${sqlString(hrmId)}, ${value(timeAssigned)})`);
  }
  for (const [id, providerNo, signedOff, signedOffTimestamp, viewed, filed] of providerRows) {
    statements.push('INSERT INTO HRMDocumentToProvider (id, providerNo, hrmDocumentId, signedOff, signedOffTimestamp, viewed, filed) VALUES '
      + `(${value(id)}, ${value(providerNo)}, ${sqlString(hrmId)}, ${value(signedOff)}, ${value(signedOffTimestamp)}, ${value(viewed)}, ${value(filed)})`);
  }
  return statements;
}

function uniqueToken() {
  return crypto.randomBytes(4).toString('hex').toUpperCase();
}

async function waitFor(description, probe, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = probe();
    if (value) {
      return value;
    }
    assert(Date.now() < deadline, `timed out waiting for ${description}`);
    await new Promise((resolve) => setTimeout(resolve, POLL_MS));
  }
}

function findFixtures(sql) {
  const patient = sql.rows(`SELECT d.demographic_no, d.hin, d.year_of_birth, d.month_of_birth, d.date_of_birth,
      d.sex, d.last_name, d.first_name, d.provider_no
    FROM demographic d JOIN provider p ON p.provider_no = d.provider_no AND p.status = '1'
    WHERE d.hin REGEXP '^[0-9]{10}$' AND d.sex IN ('M','F') AND d.patient_status = 'AC'
      AND d.year_of_birth REGEXP '^[0-9]{4}$' AND d.month_of_birth REGEXP '^[0-9]{2}$'
      AND d.date_of_birth REGEXP '^[0-9]{2}$'
      AND (SELECT COUNT(*) FROM demographic e WHERE e.hin = d.hin) = 1
      AND NOT EXISTS (SELECT 1 FROM demographic_merged m WHERE m.deleted = 0
                      AND (m.demographic_no = d.demographic_no OR m.merged_to = d.demographic_no))
    ORDER BY d.demographic_no LIMIT 1`)[0];
  if (!patient) {
    throw new SkipCheck('the database has no active patient with a unique 10-digit HIN and an active MRP');
  }
  const [demographicNo, hin, year, month, day, sex, lastName, firstName, mrp] = patient;
  const orderer = sql.rows(`SELECT p.provider_no, p.ohip_no FROM provider p
    WHERE p.status = '1' AND p.ohip_no REGEXP '^[0-9]{6}$' AND p.provider_no <> ${sqlString(mrp)}
      AND (SELECT COUNT(*) FROM provider q WHERE q.ohip_no = p.ohip_no) = 1
      AND NOT EXISTS (SELECT 1 FROM incomingLabRules r WHERE r.provider_no = p.provider_no
                      AND r.frwdProvider_no = ${sqlString(mrp)} AND r.archive = '0')
    ORDER BY p.provider_no LIMIT 1`)[0];
  if (!orderer) {
    throw new SkipCheck('the database has no second active provider with a unique 6-digit OHIP number');
  }
  const hrmId = sql.value('SELECT MIN(id) FROM HRMDocument');
  if (!hrmId) {
    throw new SkipCheck('the database has no HRM document (load the demo dataset)');
  }
  return {
    patient: { demographicNo, hin, dob: `${year}${month}${day}`, sex, lastName, firstName, mrp },
    orderer: { providerNo: orderer[0], ohipNo: orderer[1] },
    hrmId,
  };
}

async function openAdminFrame(page, config, linkPath) {
  await gotoApp(page, config.baseUrl, '/administration');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'Administration');
  const section = page.locator('button[data-bs-target="#collapseThree"]').first();
  await section.waitFor({ state: 'visible', timeout: 20000 });
  if ((await section.getAttribute('aria-expanded')) !== 'true') {
    await section.click();
  }
  const link = page.locator(`#collapseThree a.xlink[rel$="${linkPath}"]`).first();
  await link.waitFor({ state: 'visible', timeout: 20000 });
  await link.click();
  await page.locator('#dynamic-content iframe#myFrame').waitFor({ state: 'attached', timeout: 20000 });
  return page.frameLocator('#myFrame');
}

async function setSwitch(page, config, sql, enabled) {
  const frame = await openAdminFrame(page, config, '/admin/providerLinkingRules');
  const toggle = frame.locator('#providerLinkingRulesEnabled');
  await toggle.waitFor({ state: 'visible', timeout: 30000 });
  if ((await toggle.isChecked()) !== enabled) {
    await toggle.click();
  }
  await frame.locator('#saveProviderLinkingRules').click();
  await frame.locator('#providerLinkingRulesSaved').waitFor({ state: 'visible', timeout: 30000 });
  assert((await frame.locator('#providerLinkingRulesEnabled').isChecked()) === enabled,
    `the page did not show the switch ${enabled ? 'on' : 'off'} after saving`);
  const stored = sql.rows(`SELECT value FROM property WHERE name=${sqlString(PROPERTY)}
    AND (provider_no IS NULL OR provider_no = '')`).map((row) => row[0]);
  assert(stored.length > 0 && stored.every((value) => value === String(enabled)),
    `the global ${PROPERTY} row does not read ${enabled} after saving`);
}

async function uploadLab(page, config, sql, message, labUploads) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'provider-linking-'));
  try {
    // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- both segments are local constants
    const file = path.join(dir, `provider-linking-${uniqueToken()}.hl7`);
    fs.writeFileSync(file, message.body);
    const frame = await openAdminFrame(page, config, '/lab/CA/ALL/ViewInsideLabUpload');
    await frame.locator('#importFiles').waitFor({ state: 'attached', timeout: 30000 });
    await frame.locator('#importFiles').setInputFiles(file);
    await frame.locator('#type').selectOption('CML');
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && /\/lab\/CA\/ALL\/insideLabUpload/.test(r.url()),
        { timeout: 90000 }),
      frame.locator('#uploadForm button[type="submit"]').click(),
    ]);
    assert(response.status() < 400, `the lab upload answered HTTP ${response.status()}`);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
  const labNo = await waitFor('the uploaded lab to be stored', () => sql.value(
    `SELECT lab_no FROM hl7TextInfo WHERE accessionNum LIKE ${sqlString(`${message.accession}%`)} ORDER BY lab_no DESC LIMIT 1`));
  labUploads.push(labNo);
  return labNo;
}

function routedProviders(sql, labNo) {
  return sql.rows(`SELECT provider_no FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${Number(labNo)}`)
    .map((row) => row[0]);
}

async function main({ throwIfCancelled } = {}) {
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  const recorder = createRecorder();
  const labUploads = [];
  const propertySnapshot = sql.rows(`SELECT id, value, IFNULL(provider_no, ${sqlString(NULL_MARKER)})
    FROM property WHERE name=${sqlString(PROPERTY)} ORDER BY id`);
  let hrmSnapshot = null;
  let browser;

  const cleanup = () => {
    const failures = [];
    const run = (statements) => {
      for (const statement of statements) {
        try {
          sql.execute(statement);
        } catch (error) {
          failures.push(error.message);
        }
      }
    };
    run(deleteLabStatements(labUploads));
    if (hrmSnapshot) {
      run(restoreHrmStatements(hrmSnapshot.hrmId, hrmSnapshot.demographics, hrmSnapshot.providers));
    }
    run(restorePropertyStatements(propertySnapshot));
    sql.dispose();
    assert(failures.length === 0, `cleanup could not restore ${failures.length} statement(s): ${failures[0]}`);
  };

  try {
    const { patient, orderer, hrmId } = findFixtures(sql);
    const makeMessage = (hin) => {
      const token = uniqueToken();
      const accession = `PLR${token}`;
      return {
        accession,
        body: buildCmlMessage({
          controlId: token, accession, hin, lastName: patient.lastName, firstName: patient.firstName,
          dob: patient.dob, sex: patient.sex, ohipNo: orderer.ohipNo,
        }),
      };
    };

    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const page = await login(context, config, recorder);

    // 1. The page shows what is stored, then the switch is turned off through it.
    const frame = await openAdminFrame(page, config, '/admin/providerLinkingRules');
    const storedOn = propertySnapshot.some(([, value, provider]) => (provider === NULL_MARKER || provider === '')
      && value === 'true');
    await frame.locator('#providerLinkingRulesEnabled').waitFor({ state: 'visible', timeout: 30000 });
    assert((await frame.locator('#providerLinkingRulesEnabled').isChecked()) === storedOn,
      'the switch does not show the stored setting');
    await setSwitch(page, config, sql, false);
    throwIfCancelled && throwIfCancelled();

    // 2. Off: an upload reaches the ordering provider only.
    const offLab = await uploadLab(page, config, sql, makeMessage(patient.hin), labUploads);
    const offRouted = await waitFor('the lab to be routed', () => {
      const rows = routedProviders(sql, offLab);
      return rows.length ? rows : null;
    });
    assert(offRouted.includes(orderer.providerNo), 'with the rules off the lab did not reach the ordering provider');
    assert(!offRouted.includes(patient.mrp), 'with the rules off the lab reached the MRP anyway');
    assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${Number(offLab)}`)
      === patient.demographicNo, 'the synthetic lab did not match the demo patient by HIN');

    // 3. On, through the page, and audited.
    await setSwitch(page, config, sql, true);
    await waitFor('the switch change to be audited', () => sql.value(`SELECT COUNT(*) FROM log
      WHERE action='update' AND content='providerLinkingRules' AND data='enabled=true'
        AND dateTime >= NOW() - INTERVAL 10 MINUTE`) !== '0');

    // 4. On: the same kind of upload reaches the ordering provider AND the MRP.
    const onLab = await uploadLab(page, config, sql, makeMessage(patient.hin), labUploads);
    const onRouted = await waitFor('the lab to reach the MRP', () => {
      const rows = routedProviders(sql, onLab);
      return rows.includes(patient.mrp) ? rows : null;
    });
    assert(onRouted.includes(orderer.providerNo), 'with the rules on the lab no longer reached the ordering provider');
    assert(onRouted.filter((p) => p === patient.mrp).length === 1, 'the MRP got more than one routing row');
    await waitFor('the MRP routing to be audited', () => sql.value(`SELECT COUNT(*) FROM log
      WHERE action='route to MRP' AND contentId=${sqlString(`HL7:${onLab}`)}`) !== '0');
    throwIfCancelled && throwIfCancelled();

    // 5. Patient Match: an unmatched lab is matched from the lab's Patient Search popup.
    const unknownHin = `9${String(Date.now()).slice(-9)}`;
    assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${sqlString(unknownHin)}`) === '0',
      'the generated unknown HIN collides with a patient');
    const unmatchedLab = await uploadLab(page, config, sql, makeMessage(unknownHin), labUploads);
    await waitFor('the unmatched lab to be routed', () => routedProviders(sql, unmatchedLab).length > 0);
    assert(!routedProviders(sql, unmatchedLab).includes(patient.mrp), 'an unmatched lab reached the MRP');
    const search = await context.newPage();
    wireStrictPage(search, 'patient-search', recorder);
    // The popup closes itself after a match; keep it open so the result can be read.
    await search.addInitScript(() => { window.close = () => {}; });
    await gotoApp(search, config.baseUrl, `/oscarMDS/ViewPatientSearch?labNo=${Number(unmatchedLab)}&labType=HL7`);
    await search.locator('#search_hin').check();
    await search.locator('#keyword').fill(patient.hin);
    await clickAndAwaitReload(search, search.locator('#titlesearch button[type="submit"]'),
      { label: 'Patient Search' });
    const row = search.locator(`tr[onclick*="selectPatient('${patient.demographicNo}')"]`).first();
    await row.waitFor({ state: 'visible', timeout: 30000 });
    const [matchResponse] = await Promise.all([
      search.waitForResponse((r) => r.request().method() === 'POST' && /\/oscarMDS\/PatientMatch/.test(r.url()),
        { timeout: 30000 }),
      row.click(),
    ]);
    assert(matchResponse.status() < 400, `Patient Match answered HTTP ${matchResponse.status()}`);
    await waitFor('the matched lab to reach the MRP', () => routedProviders(sql, unmatchedLab).includes(patient.mrp));
    assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${Number(unmatchedLab)}`)
      === patient.demographicNo, 'Patient Match did not link the lab to the patient');
    assert(!routedProviders(sql, unmatchedLab).includes('0'), 'the unassigned routing row survived the MRP routing');
    await search.close();

    // 6. HRM: assign an unlinked report to the patient in the HRM viewer.
    hrmSnapshot = {
      hrmId,
      demographics: sql.rows(`SELECT id, demographicNo, timeAssigned FROM HRMDocumentToDemographic
        WHERE hrmDocumentId=${sqlString(hrmId)} ORDER BY id`),
      providers: sql.rows(`SELECT id, providerNo, signedOff, signedOffTimestamp, viewed, filed FROM HRMDocumentToProvider
        WHERE hrmDocumentId=${sqlString(hrmId)} ORDER BY id`),
    };
    // Fixture: the report is unclaimed (a -1 row, as an unmatched HRM delivery leaves it) and the
    // MRP does not hold it yet, so both the MRP row and the -1 cleanup are observable.
    sql.execute(`DELETE FROM HRMDocumentToProvider WHERE hrmDocumentId=${sqlString(hrmId)}
      AND providerNo IN (${sqlString(patient.mrp)}, '-1')`);
    sql.execute(`INSERT INTO HRMDocumentToProvider (providerNo, hrmDocumentId, signedOff, viewed)
      VALUES ('-1', ${sqlString(hrmId)}, 0, 0)`);
    const hrm = await context.newPage();
    wireStrictPage(hrm, 'hrm-viewer', recorder);
    await gotoApp(hrm, config.baseUrl, `/hospitalReportManager/Display?id=${Number(hrmId)}`);
    await assertNotErrorPage(hrm, 'HRM viewer');
    if (hrmSnapshot.demographics.length) {
      // The demo report is linked: unlink it the way a clinician does. This is the unlink that
      // failed at flush before the bulk delete, so it is asserted, not assumed.
      const [unlink] = await Promise.all([
        hrm.waitForResponse((r) => r.request().method() === 'POST' && /hospitalReportManager\/Modify/.test(r.url()),
          { timeout: 30000 }),
        hrm.locator(`#demostatus${Number(hrmId)} a`, { hasText: '(remove)' }).first().click(),
      ]);
      assert((await unlink.json()).success === true, 'unlinking the HRM report from its patient failed');
      assert(sql.value(`SELECT COUNT(*) FROM HRMDocumentToDemographic WHERE hrmDocumentId=${sqlString(hrmId)}`) === '0',
        'the HRM report is still linked to a patient after (remove)');
    }
    const input = hrm.locator(`#autocompletedemo${Number(hrmId)}hrm`);
    await input.waitFor({ state: 'visible', timeout: 30000 });
    await input.pressSequentially(`${patient.lastName}, ${patient.firstName}`.slice(0, 40), { delay: 30 });
    const choice = hrm.locator('ul.ui-autocomplete li').filter({
      hasText: `${patient.dob.slice(0, 4)}-${patient.dob.slice(4, 6)}-${patient.dob.slice(6, 8)}`,
    }).first();
    await choice.waitFor({ state: 'visible', timeout: 30000 });
    // Picking the patient posts the match; a report open on its own page then reloads to show
    // the MRP the rules added (hrmActions.js showMrpRouted).
    await clickAndAwaitReload(hrm, choice, { label: 'the HRM patient pick' });
    await waitFor('the HRM report to be linked to the patient', () => sql.value(`SELECT demographicNo FROM HRMDocumentToDemographic
      WHERE hrmDocumentId=${sqlString(hrmId)}`) === patient.demographicNo);
    assert(sql.value(`SELECT COUNT(*) FROM HRMDocumentToProvider WHERE hrmDocumentId=${sqlString(hrmId)}
      AND providerNo=${sqlString(patient.mrp)} AND signedOff=0`) === '1', 'the HRM report did not reach the MRP exactly once');
    assert(sql.value(`SELECT COUNT(*) FROM HRMDocumentToProvider WHERE hrmDocumentId=${sqlString(hrmId)}
      AND providerNo='-1'`) === '0', 'the unclaimed HRM row survived the MRP routing');
    // ProviderDao.getProviderName renders "First Last".
    const mrpName = sql.value(`SELECT CONCAT(first_name, ' ', last_name) FROM provider WHERE provider_no=${sqlString(patient.mrp)}`);
    await hrm.locator(`#provstatus${Number(hrmId)}`).waitFor({ state: 'attached', timeout: 30000 });
    assert((await hrm.locator('body').innerText()).includes(mrpName),
      'the reloaded HRM viewer does not list the MRP under Assigned Providers');
    await hrm.close();

    // 7. The save route refuses GET and a POST without a token, and neither changes anything.
    const getResponse = await page.request.get(`${config.baseUrl.href.replace(/\/$/, '')}/admin/saveProviderLinkingRules`,
      { failOnStatusCode: false, maxRedirects: 0 });
    assert(getResponse.status() === 405, `GET on the save route answered ${getResponse.status()}, expected 405`);
    const forged = await page.request.post(`${config.baseUrl.href.replace(/\/$/, '')}/admin/saveProviderLinkingRules`,
      { form: { enabled: 'false' }, failOnStatusCode: false, maxRedirects: 0 });
    assert(forged.status() !== 302 || !/providerLinkingRules\?saved=true/.test(forged.headers().location || ''),
      'a POST without a CSRF token was accepted');
    assert(sql.value(`SELECT value FROM property WHERE name=${sqlString(PROPERTY)}
      AND (provider_no IS NULL OR provider_no='') LIMIT 1`) === 'true', 'a token-less POST changed the switch');

    assertStrictPage(recorder);
    return { hl7: 3, patientMatch: 1, hrm: 1 };
  } finally {
    try {
      if (browser) {
        await browser.close();
      }
    } finally {
      cleanup();
    }
  }
}

if (require.main === module) {
  runCheck({ name: 'provider-linking-rules', run: main });
}

module.exports = {
  buildCmlMessage,
  deleteLabStatements,
  restoreHrmStatements,
  restorePropertyStatements,
};
