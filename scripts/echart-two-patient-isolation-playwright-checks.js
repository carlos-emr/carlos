#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Two patients' charts open in one login must stay isolated from each other.
 *
 * A clinician who opens patient A's E-Chart and then patient B's in a second window of the same
 * login expects each window to keep showing, and writing to, its own patient. The chart keeps ONE
 * session-wide EctSessionBean, rebuilt whenever a chart is opened, and its navbar modules, the
 * measurement history, the Forms module and the form setup all read the bean rather than the
 * patient their own window was opened for. Opening B therefore switches A's window to B the next
 * time it reloads a module or opens a history or a form (finding 182, a patient-safety defect).
 * This is the chart-side twin of rx-stash-patient-isolation, which pins the same fault in the
 * prescription module (fixed per tab in #3908).
 *
 * User path: Schedule > Search > Master Record > E-Chart for patient A; a second browser tab ->
 * Schedule > Search > Master Record > E-Chart for patient B; then back to A's window.
 *
 * Reads, asserted first: with only A open, A's Measurements module, its WT history, the plot, the
 * Forms module's Add Form links and a form opened from them are A's. With B open, the same in B's
 * window. Back in A's window the modules are reloaded the way the app reloads them (close a popup
 * the module opened), and then the module, the history, the plot, the Forms module and a form
 * opened from it must still be A's and must not have become B's. Writes, asserted after: a
 * measurement, a Dx code, a CPP item (Risk Factors), a prevention and a history Delete, made in
 * both directions, land on the selected patient and leave the other patient's full rows unchanged.
 *
 * Fixtures: the workflow's FAKE patient (A, first name PatientAlpha), a second FAKE patient (B,
 * PatientBravo, last name the run marker) and one WT measurement each (61.2 kg for A, 87.4 kg for
 * B; the two plots differ, which is what lets the plot say whose data it shows). Cleanup removes both patients' measurements, archived
 * measurements, Dx, prevention, note, CPP and lock rows by key and asserts each is gone.
 * The existing VT encounterForm registration is temporarily given a positive display order if
 * hidden. This requires exclusive fixture use; cleanup restores and compares the complete registry.
 *
 * Vascular Tracker setup, Update and reopen are driven for both patients. Save & Exit closes
 * the popup, writes A's measurements and appends only to A's editor; a second exit after the
 * opener navigates to B must leave B's editor and complete clinical rows unchanged. Invalid patient
 * parameters and cross-patient form IDs are rejected without exposing names or changing either
 * patient's rows. CPP signature hashes and VT forms are included in owned fixture cleanup.
 *
 * Issue #4673 adds the bean's other readers, each driven in both directions right after the other
 * chart was entered: the chart print's MRP line (B's MRP is a second named doctor); the legacy
 * encounter save, its Sign, Save and Exit of the chart's own appointment and its refusals; the
 * legacy editor and history prints; the legacy immunization schedule (template add, Save, set
 * delete, the dose window, and a Save, template add or set restore without one valid patient,
 * refused); the author of a template made after a chart was entered from another provider's day
 * sheet; the legacy case-management navigation and its list of the patient's own messages; and the
 * notes list's edit rights and the notes panel's entry provider once a chart was entered from
 * another provider's day sheet. One owned appointment per patient, its archive rows and the
 * immunization schedules are in the snapshot and the cleanup, as are the owned template and one
 * message linked to each patient; the provider's encounterWindow row is restored.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, csrfTokenPresent } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const print = require('./lib/gap-clinical-print');
const { squash } = require('./lib/export-content-helpers');

const TIMEOUT = 20000;
const A_WEIGHT = '61.2';
const B_WEIGHT = '87.4';
const OBSERVED = '2026-02-03';
const A_FIRST_NAME = 'PatientAlpha';
const B_FIRST_NAME = 'PatientBravo';
const RETAINED_VT_FIELDS = ['iExValue', 'iExDate', 'iExComments', 'WHRValue', 'WHRDate', 'WHRComments'];
// Large Forms menus alternate these columns; a configured form can occupy either one.
const FORM_MENU_ITEMS = '#forms #menu1 a:is(.menuItemleft, .menuItemright)';
// Unique tokens (nothing else on a page contains them), so a plain substring says whose name a page prints.
const namesA = text => text.includes(A_FIRST_NAME);
const namesB = text => text.includes(B_FIRST_NAME);

const norm = text => text.replace(/\s+/g, ' ').trim();
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex').slice(0, 16);

function patientKey(value) {
  h.assert(/^[1-9]\d*$/.test(String(value)), 'An owned patient key is invalid');
  return String(value);
}

/** Clinical rows, including child data and signatures; no session locks or access-audit timestamps. */
function patientStateQueries(patient) {
  const id = patientKey(patient);
  const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${id}`;
  const measurements = `SELECT id FROM measurements WHERE demographicNo=${id}
    UNION SELECT originalId FROM measurementsDeleted WHERE demographicNo=${id}`;
  return {
    demographic: `SELECT * FROM demographic WHERE demographic_no=${id}`,
    demographicExtensions: `SELECT * FROM demographicExt WHERE demographic_no=${id} ORDER BY id`,
    measurements: `SELECT * FROM measurements WHERE demographicNo=${id} ORDER BY id`,
    deletedMeasurements: `SELECT * FROM measurementsDeleted WHERE demographicNo=${id} ORDER BY id`,
    // The id set is a derived table so it is materialized once: MariaDB 11.8 runs a bare IN (... UNION ...)
    // as a dependent subquery for every extension row, which takes minutes on the demonstration dataset.
    measurementExtensions: `SELECT * FROM measurementsExt WHERE measurement_id IN
      (SELECT id FROM (${measurements}) owned) ORDER BY id`,
    diagnoses: `SELECT * FROM dxresearch WHERE demographic_no=${id} ORDER BY dxresearch_no`,
    notes: `SELECT * FROM casemgmt_note WHERE demographic_no=${id} ORDER BY note_id`,
    noteExtensions: `SELECT * FROM casemgmt_note_ext WHERE note_id IN (${notes}) ORDER BY id`,
    noteLinks: `SELECT * FROM casemgmt_note_link WHERE note_id IN (${notes}) ORDER BY id`,
    noteIssues: `SELECT * FROM casemgmt_issue_notes WHERE note_id IN (${notes}) ORDER BY note_id,id`,
    issues: `SELECT * FROM casemgmt_issue WHERE demographic_no=${id} ORDER BY id`,
    signatures: `SELECT * FROM hash_audit WHERE type='enc' AND id IN (${notes}) ORDER BY pkid`,
    cpp: `SELECT * FROM casemgmt_cpp WHERE demographic_no=${id} ORDER BY id`,
    echart: `SELECT * FROM eChart WHERE demographicNo=${id} ORDER BY eChartId`,
    drafts: `SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${id} ORDER BY id`,
    preventions: `SELECT * FROM preventions WHERE demographic_no=${id} ORDER BY id`,
    preventionExtensions: `SELECT * FROM preventionsExt WHERE prevention_id IN
      (SELECT id FROM preventions WHERE demographic_no=${id}) ORDER BY id`,
    forms: `SELECT * FROM formVTForm WHERE demographic_no=${id} ORDER BY ID`,
    appointments: `SELECT * FROM appointment WHERE demographic_no=${id} ORDER BY appointment_no`,
    archivedAppointments: `SELECT * FROM appointmentArchive WHERE demographic_no=${id} ORDER BY id`,
    immunizationSchedules: `SELECT * FROM immunizations WHERE demographic_no=${id} ORDER BY ID`,
  };
}

function readPatientState(sql, patient) {
  return Object.fromEntries(Object.entries(patientStateQueries(patient)).map(([name, query]) => [name, sql.rows(query)]));
}

function assertPatientStateUnchanged(before, after, operation) {
  h.assert(JSON.stringify(Object.keys(before).sort()) === JSON.stringify(Object.keys(after).sort()),
    `${operation}: the patient snapshot is incomplete`);
  for (const name of Object.keys(before)) {
    h.assert(JSON.stringify(before[name]) === JSON.stringify(after[name]),
      `${operation}: the other patient's ${name} rows changed`);
  }
}

function invalidPatientParameters(patient, other) {
  return [
    ['missing', []],
    ['malformed', [['demographicNo', 'not-a-patient']]],
    ['zero', [['demographic_no', '0']]],
    ['conflicting aliases', [['demographicNo', patientKey(patient)], ['demographic_no', patientKey(other)]]],
    ['conflicting repeated parameter', [['demographicNo', patientKey(patient)], ['demographicNo', patientKey(other)]]],
  ];
}

function assertRequestPatient(request, expected) {
  const query = new URL(request.url()).searchParams;
  const body = new URLSearchParams(request.postData() || '');
  const owners = [query, body].flatMap(parameters => [
    ...parameters.getAll('demographicNo'), ...parameters.getAll('demographic_no'),
  ]);
  h.assert(owners.length > 0 && owners.every(owner => owner === expected),
    'The request did not explicitly identify only its own patient');
}

function assertVitalsMoreHistory({url, ids, text}, expected) {
  const address = new URL(url);
  h.assert(address.pathname.endsWith('/encounter/oscarMeasurements/SetupDisplayHistory')
    && address.searchParams.get('demographicNo') === expected.patient && address.searchParams.get('type') === 'BP',
  'Vitals More did not request its own patient and measurement type');
  h.assert(ids.length === 1 && ids[0] === expected.id, 'Vitals More displayed another patient\'s measurement rows');
  h.assert(expected.ownName(text) && !expected.otherName(text)
    && text.includes(expected.value) && !text.includes(expected.otherValue),
  'Vitals More displayed another patient\'s name or BP');
}

function assertVtControlLabel(actual, expected) {
  h.assert(actual.name === expected.name && actual.role === expected.role,
    `VT control ${expected.id} lost its clinical name or native role`);
  h.assert(JSON.stringify(actual.referenceIds) === JSON.stringify(expected.references || []),
    `VT control ${expected.id} lost its contextual label references`);
  h.assert(actual.labelTexts.length > 0 && actual.labelTexts.every(text => norm(text).length > 0),
    `VT control ${expected.id} has an empty or missing label`);
  if (expected.nativeLabel) {
    h.assert(actual.nativeLabels.length === 1 && actual.nativeLabels[0].target === expected.id
      && norm(actual.nativeLabels[0].text).length > 0,
    `VT control ${expected.id} lost its native label association`);
  }
  return norm(actual.labelTexts.join(' '));
}

/** Inspect native accessibility without editing answers or submitting another clinical write. */
async function assertVascularTrackerAccessibility(page, form) {
  const controls = [];
  for (const type of ['DpSc', 'StSc', 'LcCt', 'MedG', 'MedN', 'MedR', 'MedA', 'WHRB', 'FTEx']) {
    for (const answer of ['yes', 'no']) controls.push({id: `vt-${type}-${answer}`,
      name: `value(${type}Value)`, role: 'radio', nativeLabel: true,
      references: [`vt-${type}-label`, `vt-${type}-${answer}-label`]});
    if (type !== 'FTEx') controls.push({id: `vt-${type}-comments`, name: `value(${type}Comments)`,
      role: 'textbox', references: [`vt-${type}-label`, 'vt-comments-label']});
  }
  for (const type of ['BP', 'WC', 'HC', 'HR', 'HT', 'WT']) {
    controls.push({id: `vt-${type}-value`, name: `value(${type}Value)`, role: 'textbox',
      nativeLabel: true, references: [`vt-${type}-label`, 'vt-new-data-label']});
    controls.push({id: `vt-${type}-comments`, name: `value(${type}Comments)`, role: 'textbox',
      references: [`vt-${type}-label`, 'vt-comments-label']});
  }
  for (const [id, name] of [['vt-objective', 'value(objective)'], ['FTDate', 'FTDate'], ['vt-FTComments', 'FTComments'],
    ['vt-iComments', 'iComments']]) {
    controls.push({id, name, role: 'textbox', nativeLabel: true});
  }
  const ids = await page.locator('[id]').evaluateAll(nodes => nodes.map(node => node.id).filter(Boolean));
  h.assert(new Set(ids).size === ids.length, 'VT contains duplicate DOM IDs');
  const state = () => form.evaluate(node => ({
    controls: Array.from(node.elements, field => ({name: field.name, type: field.type,
      value: field.value, checked: field.checked, disabled: field.disabled})),
    submitted: Array.from(new FormData(node).entries()),
    examination: ['examinationFoot', 'examinationEye'].map(id => node.ownerDocument.getElementById(id).style.display),
  }));
  const before = await state();
  const headers = async expanded => {
    for (const id of ['vt-medication-adherence', 'vital', 'detailVital']) {
      const table = page.locator(`#${id}`);
      h.assert(!['none', 'presentation'].includes(await table.getAttribute('role')),
        `VT data matrix ${id} lost its table semantics`);
      const columns = table.locator(':scope > tbody > tr:first-child > th');
      h.assert(await columns.count() === 4, `VT data matrix ${id} lost its column headers`);
      for (let index = 0; index < 4; index++) {
        const column = columns.nth(index);
        h.assert(await column.getAttribute('scope') === 'col', `VT matrix ${id} has an unscoped header`);
        // Detailed vitals is deliberately absent from the accessibility tree until Show Details.
        const named = column.and(page.getByRole('columnheader', {name: /\S/}));
        h.assert(await named.count() === (id === 'detailVital' && !expanded ? 0 : 1),
          `VT matrix ${id} has an empty or incorrectly exposed column-header name`);
      }
    }
  };
  h.assert(await page.locator('#showDetailTitle a').isVisible(), 'VT must initially show its compact view');
  await headers(false);
  await page.locator('#showDetailTitle a').click();
  let examinationToggled = false;
  try {
    await page.locator('#hideDetailTitle a').waitFor({state: 'visible'});
    await headers(true);
    // initialize()/DMCheck hides examinations for a fresh form. The existing disclosure
    // changes visibility only; leave the diagnosis checkboxes and disabled inputs untouched.
    if (!await page.locator('#examinationFoot').isVisible()) {
      await page.locator('#examLabel a').click();
      examinationToggled = true;
      await page.locator('#examinationFoot').waitFor({state: 'visible'});
    }
    for (const expected of controls) {
      const control = form.locator(`#${expected.id}`);
      h.assert(await control.count() === 1, `VT control ${expected.id} is missing`);
      const actual = await control.evaluate(node => {
        const referenceIds = (node.getAttribute('aria-labelledby') || '').trim().split(/\s+/).filter(Boolean);
        const nativeLabels = Array.from(node.labels || [], label => ({
          target: label.control?.id, text: label.textContent,
        }));
        return {name: node.name, role: node.type === 'radio' ? 'radio' : 'textbox', referenceIds, nativeLabels,
          labelTexts: referenceIds.length ? referenceIds.map(id => document.getElementById(id)?.textContent || '')
            : nativeLabels.map(label => label.text)};
      });
      const accessibleName = assertVtControlLabel(actual, expected);
      h.assert(await control.and(page.getByRole(expected.role, {name: accessibleName, exact: true})).count() === 1,
        `VT control ${expected.id} does not expose its contextual accessible name`);
    }
    await page.locator('label[for="vt-WT-value"]').click();
    h.assert(await page.evaluate(() => document.activeElement?.id) === 'vt-WT-value',
      'The native weight label did not focus its textbox');
    await form.locator('#vt-MedR-comments').focus();
    await page.keyboard.press('Tab');
    h.assert(await page.evaluate(() => document.activeElement?.id) === 'vt-MedA-yes',
      'Native Tab from MedR comments did not enter the MedA radio group');
    await page.keyboard.press('Tab');
    h.assert(await page.evaluate(() => document.activeElement?.getAttribute('href')) === 'javascript:clearAll(MedAId, MedAId-1)',
      'Native Tab from the MedA radio group did not focus its clear link');
    await page.keyboard.press('Tab');
    h.assert(await page.evaluate(() => document.activeElement?.id) === 'vt-MedA-comments',
      'Native Tab from the MedA clear link did not focus its comments');
    h.assert(await form.locator('#vt-MedA-comments').evaluate(node => node.tabIndex === 0 && !node.hasAttribute('abindex')),
      'MedA comments no longer follows native tab order or retained the legacy abindex typo');
  } finally {
    if (examinationToggled) {
      await page.locator('#examLabel a').click();
      await page.locator('#examinationFoot').waitFor({state: 'hidden'});
    }
    await page.locator('#hideDetailTitle a').click();
    await page.locator('#showDetailTitle a').waitFor({state: 'visible'});
  }
  await headers(false);
  h.assert(JSON.stringify(await state()) === JSON.stringify(before),
    'VT accessibility checks changed clinical controls or the submitted payload');
}

/** Finish a native note save before another patient's operation protects this patient's full rows. */
async function saveCurrentChartNote(chart, sql, patient, noteText) {
  const owner = patientKey(patient);
  const editor = chart.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
  h.assert(await editor.isEditable(), 'The current chart note is not editable');
  const previousId = await chart.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
  await chart.waitForFunction(() => !window.autoSaveXhr, undefined, {timeout: TIMEOUT});
  const [saved] = await Promise.all([
    chart.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
      && new URLSearchParams(response.request().postData() || '').get('method') === 'save', {timeout: TIMEOUT}),
    chart.locator('#saveImg').first().click(),
  ]);
  await saved.finished();
  h.assert(saved.status() === 200, 'The current chart note did not save');
  assertRequestPatient(saved.request(), owner);
  const submitted = new URLSearchParams(saved.request().postData() || '');
  h.assert(submitted.get('noteId') === previousId && submitted.get('caseNote_note') === noteText,
    'The native Save changed the current revision or raw note payload');
  const savedId = await chart.waitForFunction(({previousId, owner}) => {
    const form = document.forms.caseManagementEntryForm;
    const editor = document.getElementById(window.caseNote);
    const observation = document.getElementById('observationDate');
    const id = form?.elements.noteId?.value;
    return /^[1-9]\d*$/.test(id) && id !== previousId
      && form.elements.demographicNo.value === owner && editor && observation
      && window.origCaseNote === editor.value
      && window.origObservationDate === observation.value
      && !window.autoSaveXhr && id;
  }, {previousId, owner}, {timeout: TIMEOUT});
  const savedNoteId = patientKey(await savedId.jsonValue());
  await savedId.dispose();
  // noteSave trims only outer whitespace. Bind the check to the UI's returned revision ID,
  // since an older measurement note can legitimately have identical text.
  await expectValue(sql, `SELECT note FROM casemgmt_note WHERE note_id=${savedNoteId}
    AND demographic_no=${owner}`, noteText.trim(), 'The returned note revision changed its text or patient');
  await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${owner}`, '0',
    'The saved current note retained a background draft');
  h.assert(await editor.isEditable(), 'The saved current note is no longer editable');
}

function isOwnedLockRelease(request, patient, noteId, provider) {
  const data = new URLSearchParams(request.postData() || '');
  return request.method() === 'POST' && request.resourceType() === 'ping'
    && new URL(request.url()).pathname.endsWith('/CaseManagementEntry')
    && [['method', 'releaseNoteLock'], ['demographicNo', patient], ['noteId', noteId], ['providerNo', provider]]
      .every(([name, value]) => data.getAll(name).length === 1 && data.get(name) === String(value));
}

// Called only after SQL proves the departed chart's lock was released. Each candidate was
// captured by that page's requestfailed listener, bound to its exact owned release POST.
function consumeDeliveredLockReleases(recorder, candidates) {
  for (const {request, entry} of candidates) {
    if (entry && entry.url === request.url() && entry.resourceType === 'ping'
        && entry.errorText === request.failure()?.errorText && /ERR_ABORTED/.test(entry.errorText)
        && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) {
      const index = recorder.requestFailures.indexOf(entry);
      if (index >= 0) recorder.requestFailures.splice(index, 1);
    }
  }
}

function prepareVascularTrackerFixture(s) {
  const {sql} = s;
  const q = h.sqlString;
  const registryQuery = 'SELECT * FROM encounterForm ORDER BY form_value';
  const registryBefore = sql.rows(registryQuery);
  const columns = 'form_name,form_value,form_table,hidden';
  const candidates = sql.rows(`SELECT ${columns} FROM encounterForm
    WHERE form_name='Vascular Tracker' AND form_table='formVTForm'`);
  h.assert(candidates.length === 1, 'Exactly one existing Vascular Tracker registration is required');
  const original = candidates[0];
  h.assert(original.length === 4 && /^\d+$/.test(original[3]), 'VT display order is invalid');
  // Despite its SQL name, hidden=0 means unavailable; positive display orders make the form visible.
  // A unique positive value also distinguishes this run's activation from another registry writer.
  const active = [...original];
  if (original[3] === '0') active[3] = String(crypto.randomInt(100000000, 2000000000));
  const rowQuery = `SELECT ${columns} FROM encounterForm WHERE BINARY form_value=${q(original[1])}`;
  const same = (left, right) => JSON.stringify(left) === JSON.stringify(right);
  const matches = row => ['form_name', 'form_value', 'form_table']
    .map((column, index) => `BINARY ${column}=${q(row[index])}`)
    .concat(`hidden=${row[3]}`).join(' AND ');

  // Register before attempting the update: cleanup also runs if SQL succeeds but its result is lost.
  s.cleanup(() => {
    const current = sql.rows(rowQuery);
    if (!same(current, [original])) {
      h.assert(same(current, [active]), 'VT registration ownership changed; refusing to overwrite it');
      h.assert(sql.value(`UPDATE encounterForm SET hidden=${original[3]} WHERE ${matches(active)};
        SELECT ROW_COUNT()`) === '1', 'VT registration restoration lost ownership');
    }
    h.assert(same(sql.rows(registryQuery), registryBefore), 'The complete encounterForm registry was not restored');
  });
  if (!same(active, original)) {
    h.assert(sql.value(`UPDATE encounterForm SET hidden=${active[3]} WHERE ${matches(original)};
      SELECT ROW_COUNT()`) === '1', 'VT registration activation lost ownership');
  }
  h.assert(same(sql.rows(rowQuery), [active]), 'The VT registration fixture is not available');
}

async function run(s) {
  const { sql, patient: A, provider, marker, context, recorder } = s;
  const q = h.sqlString;

  // ---- Fixtures --------------------------------------------------------------------------------
  prepareVascularTrackerFixture(s);
  // Two first names no page text or script contains, so a page that prints one names its patient.
  sql.execute(`UPDATE demographic SET first_name=${q(A_FIRST_NAME)}, provider_no=${q(provider)}
    WHERE demographic_no=${A} AND last_name=${q(marker)}`);
  // B's most responsible provider is another named provider, so a chart print says whose MRP it names.
  const mrpB = sql.value(`SELECT provider_no FROM provider WHERE provider_no<>${q(provider)} AND status='1'
    AND provider_type='doctor' AND provider_no REGEXP '^[1-9][0-9]{0,5}$'
    AND TRIM(first_name)<>'' AND TRIM(last_name)<>'' AND CONCAT(TRIM(first_name),' ',TRIM(last_name))<>
      (SELECT CONCAT(TRIM(first_name),' ',TRIM(last_name)) FROM provider WHERE provider_no=${q(provider)})
    ORDER BY provider_no LIMIT 1`);
  h.assert(/^[1-9]\d{0,5}$/.test(mrpB), 'A second named doctor is required as patient B\'s MRP');
  const B = sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
    date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(mrpB)}, 'ON', 'ON', 'NR', NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(B), 'Patient B fixture was not created');
  const patients = `${A},${B}`;

  // Registered first, so it runs last: after every child row below is gone. The harness removes A.
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}
      AND last_name=${q(marker)}`) === '1', 'Patient B fixture ownership changed');
    // The chart's own children (notes, locks, drafts, Dx and the rest) are deleted by the cleanup
    // registered after this one, which runs first; what is left is the patient's own support rows.
    sql.execute(['demographicExt', 'demographicArchive']
      .map(table => `DELETE FROM ${table} WHERE demographic_no=${B}`).join(';'));
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}`) === '0',
      'Patient B was not removed');
  });
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}
      AND last_name=${q(marker)}`) === '1', 'Patient B ownership changed before child cleanup');
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${patients})`;
    const ownedNotes = sql.rows(notes).map(row => patientKey(row[0])).join(',') || '0';
    const ownedPreventions = sql.rows(`SELECT id FROM preventions WHERE demographic_no IN (${patients})`)
      .map(row => patientKey(row[0])).join(',') || '0';
    const ownedMeasurements = sql.rows(`SELECT id FROM measurements WHERE demographicNo IN (${patients})
      UNION SELECT originalId FROM measurementsDeleted WHERE demographicNo IN (${patients})`)
      .map(row => patientKey(row[0])).join(',') || '0';
    sql.execute(`DELETE FROM hash_audit WHERE type='enc' AND id IN (${ownedNotes});
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_issue WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_cpp WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${patients});
      DELETE FROM eChart WHERE demographicNo IN (${patients});
      DELETE FROM dxresearch WHERE demographic_no IN (${patients});
      DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no IN (${patients});
      DELETE FROM preventions WHERE demographic_no IN (${patients});
      DELETE FROM measurementsExt WHERE measurement_id IN (${ownedMeasurements});
      DELETE FROM measurements WHERE demographicNo IN (${patients});
      DELETE FROM measurementsDeleted WHERE demographicNo IN (${patients});
      DELETE FROM formVTForm WHERE demographic_no IN (${patients});
      DELETE FROM appointmentArchive WHERE demographic_no IN (${patients});
      DELETE FROM appointment WHERE demographic_no IN (${patients});
      DELETE FROM immunizations WHERE demographic_no IN (${patients})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${ownedNotes}))
      + (SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id IN (${ownedNotes}))
      + (SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN (${ownedNotes}))
      + (SELECT COUNT(*) FROM hash_audit WHERE type='enc' AND id IN (${ownedNotes}))
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${patients}))
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM preventionsExt WHERE prevention_id IN (${ownedPreventions}))
      + (SELECT COUNT(*) FROM measurementsExt WHERE measurement_id IN (${ownedMeasurements}))
      + (SELECT COUNT(*) FROM formVTForm WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM appointment WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM immunizations WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo IN (${patients}))
      + (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo IN (${patients}))`) === '0',
    'Owned chart rows were not removed');
  });

  const seedWeight = (demo, value) => sql.value(`INSERT INTO measurements
    (type, demographicNo, providerNo, dataField, measuringInstruction, comments, dateObserved, dateEntered)
    VALUES ('WT', ${demo}, ${q(provider)}, ${q(value)}, 'in kg', ${q(marker)}, ${q(OBSERVED)}, NOW());
    SELECT LAST_INSERT_ID()`);
  const weight = { a: seedWeight(A, A_WEIGHT), b: seedWeight(B, B_WEIGHT) };
  h.assert(/^[1-9]\d*$/.test(weight.a) && /^[1-9]\d*$/.test(weight.b), 'The WT measurement fixtures were not created');

  const snapshot = patient => readPatientState(sql, patient);

  // ---- Page helpers ----------------------------------------------------------------------------
  const moduleText = async (chart, id) => norm(await chart.locator(`#${id}`).innerText());
  const weightLink = chart => chart.locator('#measurements a[onclick*="SetupDisplayHistory?type=WT"]').first();
  const ownerLabel = owner => (owner === String(A) ? 'A' : owner === String(B) ? 'B' : 'another patient');
  const idsLabel = ids => ids.map(id => (id === weight.a ? 'A' : id === weight.b ? 'B' : 'another patient')).join('+');

  /** The WT history opened from a chart window: whose rows it lists and whose name it prints. */
  async function openWeightHistory(chart, label) {
    await weightLink(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
    const popup = await s.popup(chart, weightLink(chart), label);
    await popup.locator('tr.data').first().waitFor({ state: 'visible', timeout: TIMEOUT });
    const ids = await popup.locator('input[name="deleteCheckbox"]').evaluateAll(els => els.map(e => e.value));
    const text = norm(await popup.locator('body').innerText());
    return { popup, ids, text };
  }

  /** The digest of the plot the history's Plot control opens (the plot is deterministic per data set). */
  async function plotOf(history, expectedPatient) {
    const rendered = context.waitForEvent('response', {
      predicate: r => new URL(r.url()).pathname.endsWith('/GraphMeasurements') && r.request().isNavigationRequest(),
      timeout: TIMEOUT,
    });
    rendered.catch(() => {});
    await history.locator('input[onclick*="GraphMeasurements"]').click();
    const response = await rendered;
    h.assert(new URL(response.url()).searchParams.get('demographic_no') === expectedPatient,
      'The Plot control requested another patient');
    h.assert(response.status() === 200 && (response.headers()['content-type'] || '').startsWith('image/png'),
      'The Plot control did not answer with a PNG');
    const bytes = await response.body();
    h.assert(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])),
      'The Plot control returned something other than PNG bytes');
    return sha(bytes);
  }

  /** Close the plot popup the Plot control opened, if it is still open (it reuses a named window). */
  async function closePlot() {
    for (const page of context.pages()) {
      if (!page.isClosed() && /\/encounter\/GraphMeasurements/.test(page.url())) await page.close();
    }
  }

  /**
   * Close a popup that a module item opened and wait for the module to repaint from the reload the
   * app issues when such a popup closes (reloadWindows polling in newCaseManagementView.js). The
   * module's first child is marked first: the repaint replaces every child, so the mark going away
   * is the repaint having landed, whether or not the content changed.
   */
  async function closeAndAwaitModuleReload(chart, popup, id) {
    await chart.locator(`#${id} > *`).first().evaluate(el => { el.setAttribute('data-stale-before-reload', '1'); });
    const route = {measurements: 'displayMeasurements', forms: 'displayForms', preventions: 'displayPrevention'}[id];
    h.assert(route, 'The reload check needs a known navbar route');
    const reloaded = chart.waitForResponse(response => new URL(response.url()).pathname.endsWith(`/encounter/${route}`),
      {timeout: TIMEOUT});
    reloaded.catch(() => {});
    await popup.close();
    const response = await reloaded;
    await response.finished();
    const expected = new URL(chart.url()).searchParams.get('demographicNo');
    h.assert(response.status() === 200, `The ${id} reload did not succeed`);
    assertRequestPatient(response.request(), expected);
    await chart.locator(`#${id} [data-stale-before-reload]`).waitFor({ state: 'detached', timeout: TIMEOUT })
      .catch(() => { throw new Error(`The ${id} module did not reload after the popup it opened closed`); });
  }

  /** The Add Form links of the Forms module: the demographic number each carries. */
  async function formLinkOwners(chart) {
    const handlers = await chart.locator(FORM_MENU_ITEMS).evaluateAll(els => els.map(e => e.getAttribute('onclick') || ''));
    return handlers.map(onclick => (onclick.match(/demographic(?:_no|No)=(\d+)/) || [])[1]);
  }

  /**
   * Shows the Forms module's Add Form menu. Its "+" is hovered by firing its mouseover, as
   * bpmh-form does: the menu opens over the "+" itself whenever its list is wide enough to reach
   * it, so Playwright's hover, which waits for the "+" to receive the pointer, never settles.
   */
  async function showFormMenu(chart) {
    await chart.locator('#menuTitle1 a').dispatchEvent('mouseover');
  }

  /** Open the first Add Form item of a chart window's Forms module. */
  async function openFirstForm(chart, label) {
    await showFormMenu(chart);
    const item = chart.locator(FORM_MENU_ITEMS).first();
    await item.waitFor({ state: 'visible', timeout: TIMEOUT });
    return s.popup(chart, item, label);
  }

  /**
   * Whose form a popup is: the demographic number in its address and the name it prints. The
   * name is read from the page source, because a form prints it in an input's value.
   */
  async function formOwner(form) {
    const text = norm(await form.content());
    return {
      demographicNo: new URL(form.url()).searchParams.get('demographic_no'),
      namesA: namesA(text),
      namesB: namesB(text),
    };
  }

  let chartA;
  let chartB;
  let plotA;
  let plotB;
  const entered = {A: {}, B: {}};

  /** Whose identity banner a chart window shows (the header's name, DOB and HIN block). */
  async function bannerOwner(chart) {
    const text = norm(await chart.locator('#header-top-row').innerText({ timeout: TIMEOUT }));
    return { namesA: namesA(text), namesB: namesB(text) };
  }

  /**
   * Open the other patient's chart afresh, in a window of its own, through the clinician's chart
   * entry (IncomingEncounter), so the other patient is the login's most recently opened chart
   * whatever the previous step did. That entry is what still rewrites the login-wide
   * EctSessionBean; reloading a chart page or its navbar modules no longer does, so loading the
   * chart URL alone would leave the session on whichever patient was entered last, and a step
   * could pass only because an earlier step had pointed the session at its own patient. A new
   * window rather than chartB.reload(): leaving a chart sends its note-lock release beacon, which
   * the browser reports as an aborted ping and the strict recorder counts as a failure.
   * entryProvider is the entry's providerNo: the provider whose day sheet the chart is opened from.
   */
  async function activateOther(chart, entryProvider = provider) {
    const patient = new URL(chart.url()).searchParams.get('demographicNo');
    h.assert(patient === String(A) || patient === String(B), 'The chart to select belongs to neither owned patient');
    const fresh = await context.newPage();
    await h.gotoApp(fresh, s.config.baseUrl,
      `/encounter/IncomingEncounter?demographicNo=${encodeURIComponent(patient)}&providerNo=${encodeURIComponent(entryProvider)}`);
    await waitForNavbars(fresh, TIMEOUT);
    h.assert(new URL(fresh.url()).searchParams.get('demographicNo') === patient,
      'Entering the other chart opened a different patient');
    const banner = await bannerOwner(fresh);
    h.assert(patient === String(A) ? banner.namesA && !banner.namesB : banner.namesB && !banner.namesA,
      'The freshly entered chart names another patient in its identity banner');
  }

  /** Enter a patient's chart for an entry provider as a window that is closed again would. */
  async function enterChartElsewhere(patient, entryProvider) {
    const entered = await context.request.get(h.appUrl(s.config.baseUrl,
      `/encounter/IncomingEncounter?demographicNo=${encodeURIComponent(patient)}&providerNo=${encodeURIComponent(entryProvider)}`),
    {failOnStatusCode: false});
    try {
      h.assert(entered.status() === 200, `Entering a chart answered HTTP ${entered.status()}`);
    } finally { await entered.dispose(); }
  }

  /**
   * Reload a chart window's own address in a fresh window, as a refresh would, and read whose
   * banner it shows and which patient its notes panel's Browse Notes button opens.
   */
  async function reloadedChart(chart) {
    const fresh = await context.newPage();
    await fresh.goto(chart.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(fresh, TIMEOUT);
    const browse = fresh.locator('#note-control-panel button[onclick*="ViewNoteBrowser"]');
    await browse.waitFor({ state: 'attached', timeout: TIMEOUT });
    const onclick = await browse.getAttribute('onclick');
    return { ...await bannerOwner(fresh), noteBrowser: (onclick.match(/demographic_no=(\d+)/) || [])[1] };
  }

  // ---- Controls: with a single chart open nothing can cross, so a failure here is a broken
  // fixture or selector, not finding 182. --------------------------------------------------------
  await s.step('A\'s chart, opened first, shows A\'s measurement history, plot, Forms links and form', async () => {
    chartA = await s.chart();
    const text = await moduleText(chartA, 'measurements');
    h.assert(text.includes(A_WEIGHT) && !text.includes(B_WEIGHT), 'A\'s Measurements module does not list A\'s weight alone');
    const history = await openWeightHistory(chartA, 'a-history-alone');
    h.assert(history.ids.length === 1 && history.ids[0] === weight.a,
      'A\'s history, opened alone, does not list exactly A\'s measurement');
    h.assert(namesA(history.text) && !namesB(history.text), 'A\'s history is not headed with A\'s name');
    plotA = await plotOf(history.popup, A);
    await closePlot();
    await history.popup.close();
    const owners = await formLinkOwners(chartA);
    h.assert(owners.length > 0 && owners.every(owner => owner === String(A)),
      'A\'s Forms module, opened alone, does not carry A\'s demographic number on every Add Form link');
    const form = await openFirstForm(chartA, 'a-form-alone');
    const owner = await formOwner(form);
    h.assert(owner.demographicNo === String(A), `A form opened from A's chart, alone, is for ${ownerLabel(owner.demographicNo)}`);
    h.assert(owner.namesA && !owner.namesB, 'A form opened from A\'s chart, alone, does not print A\'s name alone');
    await closeAndAwaitModuleReload(chartA, form, 'forms');
  });

  await s.step('B\'s chart, opened in a second window of the same login, shows B\'s own rows', async () => {
    // A second browser tab, as a clinician opens one: its popups do not re-target A's windows.
    const second = await context.newPage();
    await second.goto(s.schedule.url(), { waitUntil: 'domcontentloaded' });
    const { masterPage } = await openMasterRecord(context, second, recorder, {
      searchTerm: marker, preferredDemographicNo: B, timeout: TIMEOUT,
    });
    h.assert(new URL(masterPage.url()).searchParams.get('demographic_no') === B, 'The search opened a patient other than B');
    chartB = await openChart(context, masterPage, recorder, TIMEOUT);
    await waitForNavbars(chartB, TIMEOUT);
    h.assert(!chartA.isClosed() && new URL(chartA.url()).searchParams.get('demographicNo') === A,
      'Opening B\'s chart closed or navigated A\'s window');
    h.assert(new URL(chartB.url()).searchParams.get('demographicNo') === B, 'B\'s chart window is not B\'s chart');
    const text = await moduleText(chartB, 'measurements');
    h.assert(text.includes(B_WEIGHT) && !text.includes(A_WEIGHT), 'B\'s Measurements module does not list B\'s weight alone');
    const history = await openWeightHistory(chartB, 'b-history');
    h.assert(history.ids.length === 1 && history.ids[0] === weight.b, 'B\'s history does not list exactly B\'s measurement');
    h.assert(namesB(history.text) && !namesA(history.text), 'B\'s history is not headed with B\'s name');
    plotB = await plotOf(history.popup, B);
    h.assert(plotB !== plotA,
      'The two patients\' plots are identical, so a plot cannot say whose data it shows');
    await closePlot();
    await history.popup.close();
    const owners = await formLinkOwners(chartB);
    h.assert(owners.length > 0 && owners.every(owner => owner === String(B)),
      'B\'s Forms module does not carry B\'s demographic number on every Add Form link');
  });

  await s.step('each chart refreshed after the other was opened keeps its own identity banner and note browser', async () => {
    // B was entered last, so the login-wide encounter bean is B's while A's page reloads.
    const a = await reloadedChart(chartA);
    h.assert(a.namesA && !a.namesB, 'A\'s refreshed chart names another patient in its identity banner');
    h.assert(a.noteBrowser === String(A), `A's refreshed chart browses the notes of ${ownerLabel(a.noteBrowser)}`);
    // Entering A again makes the bean A's while B's page reloads; then B is entered again so the
    // steps below start from the same order as before (B entered last).
    await activateOther(chartA);
    const b = await reloadedChart(chartB);
    h.assert(b.namesB && !b.namesA, 'B\'s refreshed chart names another patient in its identity banner');
    h.assert(b.noteBrowser === String(B), `B's refreshed chart browses the notes of ${ownerLabel(b.noteBrowser)}`);
    await activateOther(chartB);
  });

  const directions = [
    {label: 'A', patient: A, chart: chartA, other: B, otherChart: chartB, weightId: weight.a,
      ownWeight: A_WEIGHT, otherWeight: B_WEIGHT, plot: plotA, ownName: namesA, otherName: namesB},
    {label: 'B', patient: B, chart: chartB, other: A, otherChart: chartA, weightId: weight.b,
      ownWeight: B_WEIGHT, otherWeight: A_WEIGHT, plot: plotB, ownName: namesB, otherName: namesA},
  ];
  for (const d of directions) {
    await s.step(`${d.label}'s reloaded navbar, measurement history and plot show only ${d.label}'s rows`, async () => {
      await activateOther(d.otherChart);
      const before = snapshot(d.other);
      const problems = [];
      const first = await openWeightHistory(d.chart, `${d.label}-history-with-other-open`);
      if (first.ids.join() !== d.weightId) problems.push(`history lists the measurement(s) of ${idsLabel(first.ids)}`);
      if (d.otherName(first.text) || !d.ownName(first.text)) problems.push('history is headed with another patient\'s name');
      if (await plotOf(first.popup, d.patient) !== d.plot) problems.push('plot is not the plot of this patient\'s data');
      await closePlot();
      // Re-select the opposite chart before the close-triggered reload too.
      await activateOther(d.otherChart);
      await closeAndAwaitModuleReload(d.chart, first.popup, 'measurements');
      const text = await moduleText(d.chart, 'measurements');
      if (!text.includes(d.ownWeight)) problems.push('reloaded Measurements module lost the patient\'s weight');
      if (text.includes(d.otherWeight)) problems.push('reloaded Measurements module lists the other patient\'s weight');
      await activateOther(d.otherChart);
      const again = await openWeightHistory(d.chart, `${d.label}-history-after-reload`);
      if (again.ids.join() !== d.weightId || d.otherName(again.text) || !d.ownName(again.text)) {
        problems.push('history from the reloaded module is for another patient');
      }
      if (await plotOf(again.popup, d.patient) !== d.plot) problems.push('plot from the reloaded history changed patients');
      await closePlot();
      await again.popup.close();
      h.assert(problems.length === 0, `${d.label}'s window crossed patients: ${problems.join('; ')}`);
      assertPatientStateUnchanged(before, snapshot(d.other), `${d.label}'s read/reload`);
    });

    await s.step(`a form opened from ${d.label}'s chart shows ${d.label} before and after reload`, async () => {
      await activateOther(d.otherChart);
      const otherBefore = snapshot(d.other);
      const problems = [];
      const before = await openFirstForm(d.chart, `${d.label}-form-before-reload`);
      const initialOwner = await formOwner(before);
      h.assert(initialOwner.demographicNo === d.patient && initialOwner[d.label === 'A' ? 'namesA' : 'namesB']
        && !initialOwner[d.label === 'A' ? 'namesB' : 'namesA'], 'The initial form belongs to the wrong patient');
      await activateOther(d.otherChart);
      await closeAndAwaitModuleReload(d.chart, before, 'forms');
      const owners = await formLinkOwners(d.chart);
      if (!owners.length || owners.some(owner => owner !== d.patient)) {
        problems.push('reloaded Forms module lost its explicit patient on an Add Form link');
      }
      await activateOther(d.otherChart);
      const form = await openFirstForm(d.chart, `${d.label}-form-after-reload`);
      const owner = await formOwner(form);
      if (owner.demographicNo !== d.patient) problems.push(`the form is for ${ownerLabel(owner.demographicNo)}`);
      if (!owner[d.label === 'A' ? 'namesA' : 'namesB'] || owner[d.label === 'A' ? 'namesB' : 'namesA']) {
        problems.push('the form is headed with another patient\'s name');
      }
      await form.close();
      h.assert(problems.length === 0, `${d.label}'s Forms module crossed patients: ${problems.join('; ')}`);
      assertPatientStateUnchanged(otherBefore, snapshot(d.other), `${d.label}'s forms read/reload`);
    });

  }

  // ---- Writes in both directions, with the opposite chart selected immediately before each save.
  async function isolatedWrite(d, description, body) {
    await s.step(`${description} in ${d.label}'s window leaves the other patient's full state unchanged`, async () => {
      await activateOther(d.otherChart);
      const before = snapshot(d.other);
      await body();
      assertPatientStateUnchanged(before, snapshot(d.other), `${d.label}'s ${description}`);
    });
  }

  for (const d of directions) {
    const bpValue = d.label === 'A' ? '128/82' : '142/86';
    const otherBpValue = d.label === 'A' ? '142/86' : '128/82';
    await isolatedWrite(d, 'measurement entry', async () => {
      const comment = `${marker}-${d.label}-bp`;
      const value = bpValue;
      await d.chart.locator('#menuTitle3 a').hover();
      const vitals = d.chart.locator('#menu3 a:is(.menuItemleft, .menuItemright)').filter({hasText: /^\s*Vitals\s*$/});
      const group = await s.popup(d.chart, vitals, `${d.label}-vitals`);
      const row = group.locator('#row-BP');
      await row.waitFor({state: 'visible', timeout: TIMEOUT});
      h.assert(new URL(group.url()).searchParams.get('demographicNo') === d.patient, 'Vitals opened for another patient');
      await row.locator('input[name^="inputValue-"]').fill(value);
      await row.locator('input[name^="date-"]').fill('2026-03-04');
      await row.locator('input[name^="comments-"]').fill(comment);
      await activateOther(d.otherChart);
      const closed = group.waitForEvent('close', {timeout: TIMEOUT});
      await group.getByRole('button', {name: 'Submit', exact: true}).click();
      await closed;
      const where = `type='BP' AND comments=${q(comment)} AND demographicNo IN (${patients})`;
      await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE ${where}`, '1', 'The measurement was not saved exactly once');
      entered[d.label].bp = patientKey(sql.value(`SELECT id FROM measurements WHERE ${where}`));
      h.assert(sql.value(`SELECT demographicNo FROM measurements WHERE ${where}`) === d.patient
        && sql.value(`SELECT dataField FROM measurements WHERE ${where}`) === value, 'The measurement crossed patients or changed value');

      // Measurement entry also appends a note in its own chart. Finish that native workflow before
      // taking later snapshots of this patient: an unsaved note otherwise autosaves every five
      // seconds and replaces its draft row while the other patient's independent step runs.
      const editor = d.chart.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
      const noteText = await editor.inputValue();
      h.assert(noteText.includes(comment) && noteText.includes(value), 'Measurement entry lost its own chart note');
      await saveCurrentChartNote(d.chart, sql, d.patient, noteText);
    });

    // The configured Vitals group contains BP, not necessarily WT. Its native save above supplies
    // the prior value that makes the More control appear, without changing group configuration.
    await s.step(`${d.label}'s Vitals More history retains its patient after the other chart is selected`, async () => {
      await activateOther(d.otherChart);
      const before = snapshot(d.other);
      await d.chart.locator('#menuTitle3 a').hover();
      const vitals = d.chart.locator('#menu3 a:is(.menuItemleft, .menuItemright)').filter({hasText: /^\s*Vitals\s*$/});
      const group = await s.popup(d.chart, vitals, `${d.label}-vitals-history`);
      const patientField = group.locator('input[name="demographicNo"]');
      await patientField.waitFor({state: 'attached'});
      h.assert(await patientField.inputValue() === d.patient, 'Vitals More opened from another patient\'s entry form');
      const more = group.locator('[onclick*="SetupDisplayHistory?type=BP"]');
      await more.waitFor({state: 'visible', timeout: TIMEOUT});
      h.assert(await more.count() === 1, 'Vitals must offer exactly one BP history control');
      await activateOther(d.otherChart);
      const history = await s.popup(group, more, `${d.label}-vitals-more-history`);
      await history.locator('tr.data').first().waitFor({state: 'visible', timeout: TIMEOUT});
      const ids = await history.locator('input[name="deleteCheckbox"]').evaluateAll(els => els.map(el => el.value));
      const text = norm(await history.locator('body').innerText());
      assertVitalsMoreHistory({url: history.url(), ids, text}, {
        patient: d.patient, id: entered[d.label].bp, value: bpValue, otherValue: otherBpValue,
        ownName: d.ownName, otherName: d.otherName,
      });
      await history.close();
      await group.close();
      assertPatientStateUnchanged(before, snapshot(d.other), `${d.label}'s Vitals More history`);
    });

    await isolatedWrite(d, 'Dx Add', async () => {
      const code = d.label === 'A' ? '250' : '401';
      const plus = d.chart.locator('#Dx a[onclick*="setupDxResearch"]').filter({hasText: '+'}).first();
      const registry = await s.popup(d.chart, plus, `${d.label}-dx`);
      const form = registry.locator('form[action*="/oscarResearch/oscarDxResearch/dxResearch"]');
      await form.locator('input[name="demographicNo"]').waitFor({state: 'attached'});
      h.assert(await form.locator('input[name="demographicNo"]').inputValue() === d.patient, 'Dx opened for another patient');
      await form.locator('select[name="selectedCodingSystem"]').selectOption('icd9');
      await form.locator('input[name="xml_research1"]').fill(code);
      await activateOther(d.otherChart);
      await clickAndAwaitReload(registry, form.locator('input[name="codeAdd"]'), {label: 'Dx Add'});
      const owned = `dxresearch_code=${q(code)} AND coding_system='icd9' AND demographic_no IN (${patients})`;
      await expectValue(sql, `SELECT COUNT(*) FROM dxresearch WHERE ${owned}`, '1', 'The Dx code was not saved exactly once');
      h.assert(sql.value(`SELECT demographic_no FROM dxresearch WHERE ${owned}`) === d.patient, 'Dx crossed patients');
      await registry.close();
    });

    await isolatedWrite(d, 'CPP Sign & Save', async () => {
      const note = `${marker} ${d.label} risk factor`;
      await d.chart.locator('#rightNavBar a[title="Add Item"][onclick*="Risk Factors"]').first().click();
      const dialog = d.chart.locator('#showEditNote');
      await dialog.waitFor({state: 'visible'});
      await d.chart.locator('#noteEditTxt').fill(note);
      const action = await d.chart.locator('#frmIssueNotes').evaluate(form => form.action);
      h.assert(new URL(action).searchParams.get('demographicNo') === d.patient, 'CPP editor targets another patient');
      await activateOther(d.otherChart);
      const [response] = await Promise.all([
        d.chart.waitForResponse(r => r.request().method() === 'POST'
          && r.url() === action, {timeout: TIMEOUT}),
        dialog.locator('input[type="image"][title="Sign & Save"]').click(),
      ]);
      await response.finished();
      h.assert(response.status() === 302, 'The CPP save did not return its listing redirect');
      await dialog.waitFor({state: 'hidden'});
      const owned = `note=${q(note)} AND demographic_no IN (${patients})`;
      await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE ${owned}`, '1', 'The CPP item was not saved exactly once');
      const id = patientKey(sql.value(`SELECT note_id FROM casemgmt_note WHERE ${owned}`));
      h.assert(sql.value(`SELECT demographic_no FROM casemgmt_note WHERE note_id=${id}`) === d.patient
        && sql.value(`SELECT signed FROM casemgmt_note WHERE note_id=${id}`) === '1', 'The signed CPP item crossed patients');
      h.assert(sql.value(`SELECT COUNT(*) FROM hash_audit WHERE type='enc' AND id=${id}`) === '1',
        'The signed CPP item did not create exactly one owned signature');
      await d.chart.locator(`#rightNavBar a[id="listNote${id}"]`).waitFor({state: 'visible'});
    });

    await isolatedWrite(d, 'prevention entry', async () => {
      const date = d.label === 'A' ? '2026-01-05' : '2026-01-06';
      const plus = () => d.chart.locator('#preventions a[onclick*="ViewPreventionIndex"]').filter({hasText: '+'}).first();
      const initial = await s.popup(d.chart, plus(), `${d.label}-prevention-reload`);
      await activateOther(d.otherChart);
      await closeAndAwaitModuleReload(d.chart, initial, 'preventions');
      await activateOther(d.otherChart);
      const list = await s.popup(d.chart, plus(), `${d.label}-prevention`);
      h.assert(new URL(list.url()).searchParams.get('demographic_no') === d.patient, 'Preventions opened for another patient');
      await list.locator('#immunization').fill('Fluzone');
      const editor = await s.popup(list,
        list.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first(),
        `${d.label}-prevention-editor`);
      await editor.locator('[name="given"][value="given"]').check();
      await editor.locator('#prevDate').fill(date);
      await editor.locator('[name="comments"]').fill(`${marker}-${d.label}-prevention`);
      await activateOther(d.otherChart);
      await editor.locator('input[type="submit"][name="action"]').first().click();
      const owned = `prevention_type='Inf' AND deleted=0 AND DATE(prevention_date)=${q(date)} AND demographic_no IN (${patients})`;
      await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE ${owned}`, '1', 'The prevention was not saved exactly once');
      h.assert(sql.value(`SELECT demographic_no FROM preventions WHERE ${owned}`) === d.patient, 'Prevention crossed patients');
      for (const page of [editor, list]) if (!page.isClosed()) await page.close();
    });

    await isolatedWrite(d, 'Vascular Tracker setup and Update', async () => {
      await showFormMenu(d.chart);
      const item = d.chart.locator(FORM_MENU_ITEMS).filter({hasText: /^\s*Vascular Tracker\s*$/});
      await item.waitFor({state: 'visible', timeout: TIMEOUT});
      h.assert(await item.count() === 1, 'The Forms menu must offer exactly one Vascular Tracker');
      const formPage = await s.popup(d.chart, item, `${d.label}-vascular-tracker`);
      const setupAddress = new URL(formPage.url());
      h.assert(setupAddress.pathname.endsWith('/form/SetupForm')
        && setupAddress.searchParams.get('formName') === 'VTForm'
        && setupAddress.searchParams.get('demographic_no') === d.patient,
      'The Vascular Tracker menu item did not open its own canonical form setup');
      const form = formPage.locator('form[action$="/form/SubmitForm"]');
      const patientField = form.locator('input[name="demographic_no"]');
      await patientField.waitFor({state: 'attached'});
      h.assert(await patientField.inputValue() === d.patient, 'Vascular Tracker setup chose another patient');
      for (const field of RETAINED_VT_FIELDS) {
        const carrier = form.locator(`input[name="value(${field})"]`);
        h.assert(await carrier.getAttribute('type') === 'hidden' && await carrier.inputValue() === '',
          'A new Vascular Tracker fabricated retained clinical data');
      }
      const text = norm(await formPage.content());
      h.assert(d.ownName(text) && !d.otherName(text), 'Vascular Tracker displays another patient');
      await assertVascularTrackerAccessibility(formPage, form);
      const value = d.label === 'A' ? '62.3' : '88.5';
      await form.locator('[name="value(WTValue)"]').fill(value);
      await form.locator('[name="value(visitCod)"]').fill('2026-03-05');
      await form.locator('[name="value(WTComments)"]').fill(`${marker}-${d.label}-vt`);
      await activateOther(d.otherChart);
      const [saved] = await Promise.all([
        formPage.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/SubmitForm')),
        form.locator('input[type="submit"][onclick*="onSave();"]').first().click(),
      ]);
      await saved.finished();
      const sent = new URLSearchParams(saved.request().postData() || '');
      h.assert(saved.status() === 302 && sent.get('demographic_no') === d.patient && sent.get('submit') === 'save',
        'Vascular Tracker Update did not submit its explicit patient and return its form redirect');
      const where = `demographic_no IN (${patients}) AND WTComments=${q(`${marker}-${d.label}-vt`)}`;
      await expectValue(sql, `SELECT COUNT(*) FROM formVTForm WHERE ${where}`, '1', 'Vascular Tracker was not saved exactly once');
      const id = patientKey(sql.value(`SELECT ID FROM formVTForm WHERE ${where}`));
      entered[d.label].form = id;
      h.assert(sql.value(`SELECT demographic_no FROM formVTForm WHERE ID=${id}`) === d.patient
        && sql.value(`SELECT WTValue FROM formVTForm WHERE ID=${id}`) === value, 'Vascular Tracker crossed patients or changed value');
      h.assert(JSON.stringify(sql.rows(`SELECT ${RETAINED_VT_FIELDS.join(',')} FROM formVTForm WHERE ID=${id}`))
        === JSON.stringify([RETAINED_VT_FIELDS.map(() => null)]),
      'A new Vascular Tracker invented retained values or metadata');
      await formPage.waitForURL(url => url.pathname.endsWith('/form/SetupForm')
        && url.searchParams.get('demographic_no') === d.patient && url.searchParams.get('formId') === id);
      await form.locator('[name="value(formId)"]').waitFor({state: 'attached'});
      h.assert(await patientField.inputValue() === d.patient
        && await form.locator('[name="value(formId)"]').inputValue() === id
        && await form.locator('[name="value(WTValue)"]').inputValue() === value, 'The saved Vascular Tracker did not reopen for its patient');
      entered[d.label].formData = await form.evaluate(element => [...new FormData(element).entries()]);
      entered[d.label].token = await csrfTokenPresent(formPage);
      await formPage.close();
    });
  }

  for (const d of directions) {
    await isolatedWrite(d, 'measurement history Delete', async () => {
      const history = await openWeightHistory(d.chart, `${d.label}-history-delete`);
      const checkbox = history.popup.locator(`input[name="deleteCheckbox"][value="${d.weightId}"]`);
      h.assert(await checkbox.count() === 1 && history.ids.join() === d.weightId, 'Delete history contains another patient\'s rows');
      await checkbox.check();
      await activateOther(d.otherChart);
      await clickAndAwaitReload(history.popup, history.popup.locator('input[onclick="submit();"]'), {label: 'history Delete'});
      await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${d.weightId}`, '0', 'The selected reading was not deleted');
      h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${d.weightId} AND demographicNo=${d.patient}
        AND dataField=${q(d.ownWeight)}`) === '1', 'Delete did not archive the selected patient\'s reading intact');
      h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE id=${entered[d.label].bp} AND demographicNo=${d.patient}`) === '1',
        'Delete removed an unselected reading');
      const body = norm(await history.popup.locator('body').innerText());
      h.assert(d.ownName(body) && !d.otherName(body), 'Delete returned to the other patient\'s history');
      await history.popup.close();
    });
  }

  await s.step('missing, malformed and conflicting patient IDs are rejected without reading or changing either chart', async () => {
    const before = {A: snapshot(A), B: snapshot(B)};
    const routes = [
      ['/encounter/displayMeasurements', []],
      ['/encounter/displayForms', []],
      ['/encounter/oscarMeasurements/SetupDisplayHistory', [['type', 'WT']]],
      ['/encounter/GraphMeasurements', [['type', 'WT']]],
      ['/form/SetupForm', [['formName', 'VTForm'], ['formId', '0']]],
    ];
    for (const [route, defaults] of routes) {
      for (const [label, parameters] of invalidPatientParameters(A, B)) {
        const query = new URLSearchParams([...defaults, ...parameters]);
        const response = await context.request.get(h.appUrl(s.config.baseUrl, `${route}?${query}`),
          {maxRedirects: 0, failOnStatusCode: false});
        try {
          h.assert(response.status() === 400, `${route}: ${label} patient request did not return HTTP 400`);
          const body = await response.text();
          h.assert(!namesA(body) && !namesB(body), 'A rejected patient request exposed an owned patient name');
        } finally { await response.dispose(); }
      }
    }
    for (const d of directions) {
      const otherLabel = d.label === 'A' ? 'B' : 'A';
      const token = await csrfTokenPresent(d.chart);
      // The first selection is owned by this chart; a foreign second selection must reject
      // the entire delete, rather than deleting the first row before noticing the mismatch.
      const deletion = new URLSearchParams({type: 'BP', demographicNo: d.patient, 'CSRF-TOKEN': token});
      deletion.append('deleteCheckbox', entered[d.label].bp);
      deletion.append('deleteCheckbox', entered[otherLabel].bp);
      const refusedDelete = await context.request.post(h.appUrl(s.config.baseUrl, '/encounter/oscarMeasurements/DeleteData2'), {
        data: deletion.toString(), headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token},
        maxRedirects: 0, failOnStatusCode: false,
      });
      try {
        h.assert(refusedDelete.status() === 400, 'A mixed-patient measurement delete was not rejected atomically');
      } finally { await refusedDelete.dispose(); }
      const cases = invalidPatientParameters(d.patient, d.other);
      cases.push(['foreign form', [['demographic_no', d.patient], ['value(formId)', entered[otherLabel].form]]]);
      for (const [label, parameters] of cases) {
        const form = new URLSearchParams(entered[d.label].formData);
        form.delete('demographicNo'); form.delete('demographic_no');
        form.set('submit', 'save');
        if (label === 'foreign form') form.delete('value(formId)');
        for (const [name, value] of parameters) form.append(name, value);
        const response = await context.request.post(h.appUrl(s.config.baseUrl, '/form/SubmitForm'), {
          data: form.toString(), headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': entered[d.label].token},
          maxRedirects: 0, failOnStatusCode: false,
        });
        try {
          h.assert(response.status() === 400, `Vascular Tracker ${label} save did not return HTTP 400`);
          const body = await response.text();
          h.assert(!namesA(body) && !namesB(body), 'A rejected form save exposed an owned patient name');
        } finally { await response.dispose(); }
      }
      const query = new URLSearchParams({formName: 'VTForm', demographic_no: d.patient, formId: entered[otherLabel].form});
      const response = await context.request.get(h.appUrl(s.config.baseUrl, `/form/SetupForm?${query}`),
        {maxRedirects: 0, failOnStatusCode: false});
      try {
        h.assert(response.status() === 400, 'Vascular Tracker opened a form belonging to the other patient');
        const body = await response.text();
        h.assert(!namesA(body) && !namesB(body), 'A rejected foreign form exposed patient data');
      } finally { await response.dispose(); }
    }
    assertPatientStateUnchanged(before.A, snapshot(A), 'Rejected requests for A');
    assertPatientStateUnchanged(before.B, snapshot(B), 'Rejected requests for B');
  });

  // ---- Issue #4673: the other readers of the login-wide encounter bean. The chart print's MRP
  // line, the legacy encounter save and its prints, the legacy immunization schedule and the legacy
  // case-management navigation each used to read the bean IncomingEncounter rewrites for whichever
  // chart was entered last. Every request below names only its own patient and is sent right after
  // the other chart was entered. (chart-session-state-two-window-isolation drives the notes
  // service's new-note visit type.)
  // As the print names a provider (EctProviderData): "Dr. " before a first name that lacks it.
  const providerName = no => norm(sql.value(`SELECT CONCAT(IF(INSTR(BINARY first_name, 'Dr.')>0, '', 'Dr. '),
    first_name, ' ', last_name) FROM provider WHERE provider_no=${q(no)}`));
  const mrp = {A: providerName(provider), B: providerName(mrpB)};
  h.assert(mrp.A && mrp.B && squash(mrp.A) !== squash(mrp.B), 'The two patients\' MRP names must differ');
  const otherLabelOf = d => (d.label === 'A' ? 'B' : 'A');
  const read = async route => {
    const response = await context.request.get(h.appUrl(s.config.baseUrl, route), {maxRedirects: 0, failOnStatusCode: false});
    try {
      return {status: response.status(), body: norm(await response.text())};
    } finally { await response.dispose(); }
  };

  for (const d of directions) {
    await isolatedWrite(d, 'chart print', async () => {
      const scratch = print.scratchDir();
      try {
        await print.openPrintDialog(d.chart);
        await d.chart.locator('#printopAll').check();
        await print.setFlags(d.chart, []);
        await activateOther(d.otherChart);
        const {text} = await print.pressPrint(d.chart, scratch);
        // The header is two columns (patient, clinic), so text extraction may continue the MRP line.
        // The other patient's MRP may still appear elsewhere, as the signer of this patient's notes.
        const compact = squash(text);
        const at = compact.indexOf('MRP:');
        h.assert(at >= 0 && compact.startsWith(squash(mrp[d.label]), at + 'MRP:'.length),
          `${d.label}'s chart print does not name ${d.label}'s own MRP${compact.startsWith(squash(mrp[otherLabelOf(d)]),
            at + 'MRP:'.length) ? ' (it names the other patient\'s)' : ''}`);
      } finally {
        fs.rmSync(scratch, {recursive: true, force: true});
      }
    });
  }

  // The legacy encounter editor (Index2.jsp) renders only with the CAISI module; its save action is
  // reachable in every configuration, so it is driven here with the editor's own form fields.
  const appointmentOf = {A: undefined, B: undefined};
  for (const [label, patient] of [['A', A], ['B', B]]) {
    appointmentOf[label] = h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time,
      end_time, name, demographic_no, notes, reason, status, createdatetime, updatedatetime, creator, lastupdateuser)
      VALUES (${q(provider)}, ${q(OBSERVED)}, '23:00:00', '23:14:59', ${q(marker)}, ${patient}, ${q(marker)},
      ${q(marker)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'appointment');
  }
  // A legacy save stores the editor's row sizes for its provider; restore the provider's exact row.
  const layoutQuery = `SELECT provider_no,rowOneSize,rowTwoSize,presBoxSize,rowThreeSize FROM encounterWindow
    WHERE provider_no=${q(provider)}`;
  const layoutBefore = sql.rows(layoutQuery);
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterWindow WHERE provider_no=${q(provider)}`
      + layoutBefore.map(row => `; INSERT INTO encounterWindow (provider_no,rowOneSize,rowTwoSize,presBoxSize,rowThreeSize)
        VALUES (${q(row[0])},${row.slice(1).map(Number).join(',')})`).join(''));
    h.assert(JSON.stringify(sql.rows(layoutQuery)) === JSON.stringify(layoutBefore),
      'The provider\'s legacy encounter layout was not restored');
  });
  const revisionsOf = patient => sql.rows(`SELECT eChartId FROM eChart WHERE demographicNo=${patientKey(patient)}
    ORDER BY eChartId`).map(row => row[0]);
  const legacy = {A: {}, B: {}};
  const legacyForm = (d, text, overrides = []) => {
    const form = new URLSearchParams([['demographicNo', d.patient], ['btnPressed', 'Save'], ['enTextarea', text],
      ['shTextarea', `${marker}-${d.label}-history`], ['fhTextarea', ''], ['mhTextarea', ''], ['ocTextarea', ''],
      ['reTextarea', ''], ['subject', ''], ['appointment_no', '']]);
    for (const [name, value] of overrides) {
      if (value === undefined) form.delete(name); else form.set(name, value);
    }
    return form;
  };
  async function postLegacySave(token, form) {
    form.set('CSRF-TOKEN', token);
    return context.request.post(h.appUrl(s.config.baseUrl, '/encounter/SaveEncounter2'), {
      data: form.toString(), headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token},
      maxRedirects: 0, failOnStatusCode: false,
    });
  }

  for (const d of directions) {
    await isolatedWrite(d, 'legacy encounter save', async () => {
      const token = await csrfTokenPresent(d.chart);
      const base = revisionsOf(d.patient).at(-1) || '';
      const text = `${marker}-${d.label}-legacy-encounter`;
      const response = await postLegacySave(token, legacyForm(d, text, [['eChartId', base]]));
      try {
        h.assert(response.status() === 302, `The legacy save answered HTTP ${response.status()}`);
        const location = new URL(response.headers().location, h.appUrl(s.config.baseUrl, '/'));
        h.assert(location.pathname.endsWith('/casemgmt/ViewForward') && location.searchParams.get('demographicNo') === d.patient,
          'The saved legacy editor reopened another patient\'s chart');
      } finally { await response.dispose(); }
      const saved = revisionsOf(d.patient).at(-1);
      h.assert(saved && saved !== base && sql.value(`SELECT encounter FROM eChart WHERE eChartId=${saved}`) === text
        && sql.value(`SELECT providerNo FROM eChart WHERE eChartId=${saved}`) === provider,
      'The legacy save did not write a revision of its own patient');
      legacy[d.label] = {base, revision: saved};
    });
  }

  for (const d of directions) {
    await isolatedWrite(d, 'legacy Sign, Save and Exit of its own appointment', async () => {
      const token = await csrfTokenPresent(d.chart);
      const appointment = appointmentOf[d.label];
      const text = `${marker}-${d.label}-signed-encounter`;
      const response = await postLegacySave(token, legacyForm(d, text, [['eChartId', legacy[d.label].revision],
        ['appointment_no', appointment], ['btnPressed', 'Sign,Save and Exit']]));
      try {
        h.assert(response.status() === 200, `The legacy sign answered HTTP ${response.status()}`);
        h.assert((await response.text()).includes('opener.refresh()'),
          'The legacy sign did not refresh the schedule that opened the chart');
      } finally { await response.dispose(); }
      h.assert(JSON.stringify(sql.rows(`SELECT demographic_no,status,lastupdateuser FROM appointment
        WHERE appointment_no=${appointment}`)) === JSON.stringify([[d.patient, 'tS', provider]]),
      'The legacy sign did not sign the chart\'s own appointment');
      h.assert(JSON.stringify(sql.rows(`SELECT demographic_no,status FROM appointmentArchive
        WHERE appointment_no=${appointment}`)) === JSON.stringify([[d.patient, 't']]),
      'The legacy sign did not archive the appointment\'s previous status');
      const saved = revisionsOf(d.patient).at(-1);
      h.assert(saved !== legacy[d.label].revision
        && sql.value(`SELECT encounter FROM eChart WHERE eChartId=${saved}`).startsWith(`${text}\n[`),
      'The legacy sign did not write its own patient\'s signed revision');
      legacy[d.label].revision = saved;
    });
  }

  for (const d of directions) {
    await s.step(`the legacy encounter save refuses ${d.label}'s stale, foreign or ambiguous identities without writing`, async () => {
      const token = await csrfTokenPresent(d.chart);
      const other = otherLabelOf(d);
      await activateOther(d.otherChart);
      const before = {own: snapshot(d.patient), other: snapshot(d.other)};
      const text = `${marker}-${d.label}-refused-encounter`;
      const conflicts = [
        ['a stale revision', [['eChartId', legacy[d.label].base]]],
        ['no revision field', [['eChartId', undefined]]],
        ['another patient\'s appointment', [['eChartId', legacy[d.label].revision], ['appointment_no', appointmentOf[other]],
          ['btnPressed', 'Sign,Save and Exit']]],
      ];
      for (const [label, overrides] of conflicts) {
        const response = await postLegacySave(token, legacyForm(d, text, overrides));
        try {
          h.assert(response.status() === 409, `A legacy save with ${label} answered HTTP ${response.status()}`);
          const body = norm(await response.text());
          h.assert(body.includes(text) && !body.includes(`${marker}-${other}-`) && !namesA(body) && !namesB(body),
            `The refused legacy save with ${label} did not return only its own unsaved text`);
        } finally { await response.dispose(); }
      }
      const rejected = [
        ['another patient\'s revision', [['eChartId', legacy[other].revision]]],
        ['a malformed revision', [['eChartId', 'latest']]],
        ['a malformed appointment', [['eChartId', legacy[d.label].revision], ['appointment_no', '7e2']]],
      ];
      for (const [label, parameters] of invalidPatientParameters(d.patient, d.other)) {
        const overrides = [['demographicNo', undefined], ['eChartId', legacy[d.label].revision]];
        rejected.push([`a ${label} patient`, overrides, parameters]);
      }
      for (const [label, overrides, extra = []] of rejected) {
        const form = legacyForm(d, text, overrides);
        for (const [name, value] of extra) form.append(name, value);
        const response = await postLegacySave(token, form);
        try {
          h.assert(response.status() === 400, `A legacy save with ${label} answered HTTP ${response.status()}`);
          const body = await response.text();
          h.assert(!namesA(body) && !namesB(body), 'A rejected legacy save exposed an owned patient name');
        } finally { await response.dispose(); }
      }
      const query = new URLSearchParams(legacyForm(d, text, [['eChartId', legacy[d.label].revision]]));
      const get = await context.request.get(h.appUrl(s.config.baseUrl, `/encounter/SaveEncounter2?${query}`),
        {maxRedirects: 0, failOnStatusCode: false});
      try {
        h.assert(get.status() === 405, `A legacy save by GET answered HTTP ${get.status()}`);
      } finally { await get.dispose(); }
      assertPatientStateUnchanged(before.own, snapshot(d.patient), `${d.label}'s refused legacy saves (own rows)`);
      assertPatientStateUnchanged(before.other, snapshot(d.other), `${d.label}'s refused legacy saves`);
    });
  }

  for (const d of directions) {
    await s.step(`the legacy encounter prints for ${d.label} print only ${d.label}'s revisions`, async () => {
      const other = otherLabelOf(d);
      await activateOther(d.otherChart);
      const before = snapshot(d.other);
      const own = text => d.ownName(text) && !d.otherName(text) && text.includes(`${marker}-${d.label}-signed-encounter`)
        && !text.includes(`${marker}-${other}-`);
      const editor = await read(`/encounter/ViewEncounterPrint?demographicNo=${d.patient}&echartid=${legacy[d.label].revision}`);
      h.assert(editor.status === 200 && own(editor.body), `${d.label}'s legacy editor print is not ${d.label}'s chart alone`);
      const history = await read(`/encounter/ViewEcharthistoryprint?echartid=${legacy[d.label].revision}&demographic_no=${d.patient}`);
      h.assert(history.status === 200 && own(history.body), `${d.label}'s chart history print is not ${d.label}'s revision alone`);
      const foreign = await read(`/encounter/ViewEcharthistoryprint?echartid=${legacy[other].revision}&demographic_no=${d.patient}`);
      h.assert(foreign.status === 200 && d.ownName(foreign.body) && !d.otherName(foreign.body)
        && !foreign.body.includes(`${marker}-${other}-`), `${d.label}'s history print showed the other patient's revision`);
      for (const route of [`/encounter/ViewEncounterPrint?echartid=${legacy[d.label].revision}`,
        `/encounter/ViewEcharthistoryprint?echartid=${legacy[d.label].revision}&demographic_no=0`]) {
        const refused = await read(route);
        h.assert(refused.status === 400 && !namesA(refused.body) && !namesB(refused.body),
          `A legacy print without a valid patient answered HTTP ${refused.status}`);
      }
      assertPatientStateUnchanged(before, snapshot(d.other), `${d.label}'s legacy prints`);
    });
  }

  const [template] = sql.rows(`SELECT setId,setName FROM config_Immunization WHERE archived=0 ORDER BY setId LIMIT 1`);
  h.assert(template && /^[1-9]\d*$/.test(template[0]), 'An immunization schedule template is required');
  for (const d of directions) {
    await isolatedWrite(d, 'legacy immunization schedule template and delete', async () => {
      const page = await context.newPage();
      const schedule = `SELECT ID,provider_no,immunizations FROM immunizations WHERE demographic_no=${d.patient} ORDER BY ID`;
      h.assert(sql.rows(schedule).length === 0, `${d.label} already has an immunization schedule`);
      await h.gotoApp(page, s.config.baseUrl, `/encounter/immunization/initSchedule?demographic_no=${d.patient}`);
      const chooser = page.locator('form[action$="/encounter/immunization/saveConfig"]');
      await chooser.waitFor({state: 'visible', timeout: TIMEOUT});
      const chooserText = norm(await page.locator('body').innerText());
      h.assert(d.ownName(chooserText) && !d.otherName(chooserText)
        && await chooser.locator('input[name="demographic_no"]').inputValue() === d.patient,
      `${d.label}'s schedule template chooser is for another patient`);
      await chooser.locator(`input[type="checkbox"][value="${template[0]}"]`).check();
      await activateOther(d.otherChart);
      await clickAndAwaitReload(page, chooser.locator('input[type="submit"]'), {label: `${d.label} add immunization template`});
      const scheduleText = norm(await page.locator('body').innerText());
      h.assert(d.ownName(scheduleText) && !d.otherName(scheduleText) && squash(scheduleText).includes(squash(template[1])),
        `The schedule redisplayed after ${d.label}'s template was added is not ${d.label}'s`);
      const added = sql.rows(schedule);
      h.assert(added.length === 1 && added[0][1] === provider && added[0][2].includes(`name="${template[1].replace(/&/g, '&amp;')}"`),
        `${d.label}'s template was not added to ${d.label}'s schedule by the logged-in provider`);
      // Save, made after the other chart was entered, writes this patient's next schedule version,
      // by the logged-in provider, and shows this patient's schedule again.
      await activateOther(d.otherChart);
      await clickAndAwaitReload(page, page.locator('input[type="button"][value="Save"]').first(),
        {label: `${d.label} save immunization schedule`});
      const savedText = norm(await page.locator('body').innerText());
      h.assert(d.ownName(savedText) && !d.otherName(savedText),
        `The schedule redisplayed after ${d.label}'s Save is not ${d.label}'s`);
      const resaved = sql.rows(schedule);
      h.assert(resaved.length === 2 && resaved[1][1] === provider,
        `${d.label}'s Save did not write a new version of ${d.label}'s schedule by the logged-in provider`);
      // Record a dose from the schedule (a set item's edit link): the dose window names this
      // patient, and choosing Given offers the logged-in provider as the giver. Both came from the
      // login-wide encounter bean, here the other chart's, entered from another doctor's day sheet.
      await activateOther(d.otherChart, mrpB);
      // Sets render collapsed: ticking the set's box shows its doses.
      await page.locator('#chkSet0').check();
      const doseLink = page.locator('#tblSet0 a[href^="javascript:edit("]').first();
      h.assert(await doseLink.count() === 1, `${d.label}'s schedule offers no dose to record`);
      const [dose] = await Promise.all([context.waitForEvent('page', {timeout: TIMEOUT}), doseLink.click()]);
      await dose.waitForLoadState('domcontentloaded');
      await h.assertNotErrorPage(dose, `${d.label}'s dose window`);
      const doseText = norm(await dose.locator('body').innerText());
      h.assert(d.ownName(doseText) && !d.otherName(doseText),
        `${d.label}'s dose window names ${d.otherName(doseText) ? 'the other patient' : 'no patient'}`);
      await dose.locator('input[name="chkStatus"][onclick*="changeStatus(1)"]').check();
      h.assert(await dose.locator('#provider').inputValue() === provider,
        `${d.label}'s dose window does not offer the logged-in provider as the giver`);
      await dose.close();
      await activateOther(d.otherChart);
      const deletion = page.locator('a[onclick*="submitScheduleMutation(\'delete\', 0)"]');
      h.assert(await deletion.count() === 1, `${d.label}'s schedule does not offer exactly one set to delete`);
      const dialogs = await h.withExpectedDialogs(page, () => clickAndAwaitReload(page, deletion,
        {label: `${d.label} delete immunization set`}));
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Deleting the set did not ask for confirmation once');
      const afterDelete = norm(await page.locator('body').innerText());
      h.assert(d.ownName(afterDelete) && !d.otherName(afterDelete),
        `The schedule redisplayed after ${d.label}'s set was deleted is another patient's`);
      const versions = sql.rows(schedule);
      h.assert(versions.length === 3 && versions[2][1] === provider && versions[2][2].includes('status="deleted"'),
        `Deleting ${d.label}'s set did not write a new version of ${d.label}'s schedule`);
      await page.close();
      // A schedule Save, template add or set restore that names no patient, a malformed one or two
      // is refused before it writes, so neither schedule changes.
      const token = await csrfTokenPresent(d.chart);
      const version = sql.value(`SELECT ID FROM immunizations WHERE demographic_no=${d.patient} AND archived=0`);
      const schedules = `SELECT ID,demographic_no,archived FROM immunizations WHERE demographic_no IN (${patients}) ORDER BY ID`;
      const schedulesBefore = JSON.stringify(sql.rows(schedules));
      for (const [route, fields] of [
        ['/encounter/immunization/saveSchedule', [['hdnAction', 'Save'], ['scheduleVersion', version]]],
        ['/encounter/immunization/saveConfig', [['chkSet', template[0]]]],
        ['/encounter/immunization/deleteSchedule', [['method', 'restore'], ['tblSet', '0'], ['scheduleVersion', version]]],
      ]) {
        for (const [label, parameters] of invalidPatientParameters(d.patient, d.other)) {
          const form = new URLSearchParams([...fields, ...parameters, ['CSRF-TOKEN', token]]);
          const response = await context.request.post(h.appUrl(s.config.baseUrl, route), {
            data: form.toString(), headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token},
            maxRedirects: 0, failOnStatusCode: false,
          });
          try {
            h.assert(response.status() === 400, `${route} with a ${label} patient answered HTTP ${response.status()}, not 400`);
          } finally { await response.dispose(); }
        }
      }
      h.assert(JSON.stringify(sql.rows(schedules)) === schedulesBefore, 'A refused immunization schedule request wrote a schedule');
      for (const route of ['/encounter/immunization/ViewSchedule', '/encounter/immunization/ViewScheduleConfig',
        `/encounter/immunization/ViewScheduleConfig?demographic_no=${d.patient}&demographicNo=${d.other}`]) {
        const refused = await read(route);
        h.assert(refused.status === 400 && !namesA(refused.body) && !namesB(refused.body),
          `An immunization schedule page without one valid patient answered HTTP ${refused.status}`);
      }
    });
  }

  // A template made in the template administration is the logged-in provider's. Its author was the
  // provider of the login-wide bean, here B's chart entered from another doctor's day sheet.
  const templateName = `${marker} template author`;
  const ownedTemplate = `FROM config_Immunization WHERE setName=${q(templateName)} AND providerNo IN (${q(provider)},${q(mrpB)})`;
  s.cleanup(() => {
    sql.execute(`DELETE ${ownedTemplate}`);
    h.assert(sql.value(`SELECT COUNT(*) ${ownedTemplate}`) === '0', 'The owned immunization template was not removed');
  });
  await s.step('an immunization template made after a chart was entered from another provider\'s day sheet is the logged-in provider\'s', async () => {
    await activateOther(chartB, mrpB);
    const admin = await context.newPage();
    await h.gotoApp(admin, s.config.baseUrl, '/encounter/immunization/config/initConfig');
    await clickAndAwaitReload(admin, admin.locator('input[value="Add New"]'), {label: 'add a new immunization template'});
    await admin.locator('#setName').fill(templateName);
    await admin.locator('#numRows').fill('1');
    await admin.locator('#numCols').fill('1');
    await clickAndAwaitReload(admin, admin.locator('input[type="submit"][value="Next Step"]'), {label: 'the template grid'});
    await admin.locator('textarea[name="heading0D1"]').fill('2 mo');
    await admin.locator('textarea[name="immunization1D0"]').fill(`${marker}-T1`);
    await admin.locator('input[name="yearAge1D1"]').check();
    await clickAndAwaitReload(admin, admin.locator('input[type="submit"][value="Render Immunization Set"]'),
      {label: 'render the template'});
    const authors = sql.rows(`SELECT providerNo ${ownedTemplate}`).map(row => row[0]);
    h.assert(authors.length === 1, `Render stored ${authors.length} templates instead of one`);
    h.assert(authors[0] === provider, 'The template was stored as made by the provider of the chart entered last');
    await admin.close();
    // Later steps expect the other chart's entry to be the logged-in provider's again.
    await activateOther(chartB);
  });

  await s.step('retired direct-render chart routes reject requests without changing either chart', async () => {
    const beforeA = snapshot(A);
    const beforeB = snapshot(B);
    for (const route of ['/casemgmt/ViewNavigation', '/casemgmt/ViewSearch', '/encounter/ViewIndex2']) {
      const response = await read(`${route}?demographicNo=${A}`);
      h.assert(response.status === 404, `${route} bypasses its owning chart action (HTTP ${response.status})`);
    }
    assertPatientStateUnchanged(beforeA, snapshot(A), 'retired chart routes (A)');
    assertPatientStateUnchanged(beforeB, snapshot(B), 'retired chart routes (B)');
  });

  // The notes list (ChartNotesAjax.jsp) compares each unsigned note's author with its chart entry's
  // provider. It used to take that provider from the bean, so once the other chart was entered from
  // another provider's day sheet, this chart's list treated the logged-in author's own unsigned
  // notes as another provider's: Edit first asked to confirm editing a note "not been signed by the
  // owner".
  for (const d of directions) {
    await s.step(`${d.label}'s notes list keeps the logged-in provider's unsigned notes editable after the other chart was entered from another provider's day sheet`, async () => {
      const own = sql.rows(`SELECT note_id FROM casemgmt_note n WHERE demographic_no=${d.patient}
        AND provider_no=${q(provider)} AND signed=0
        AND note_id=(SELECT MAX(note_id) FROM casemgmt_note WHERE uuid=n.uuid) ORDER BY note_id`).map(row => row[0]);
      h.assert(own.length > 0, `${d.label} has no unsigned note by the logged-in provider`);
      await activateOther(d.otherChart, mrpB);
      const before = snapshot(d.other);
      const token = await csrfTokenPresent(d.chart);
      // The chart's own notes loader (newCaseManagementView.js.jsp notesLoader) sends this request.
      const response = await context.request.post(h.appUrl(s.config.baseUrl, '/CaseManagementView'), {
        form: {method: 'viewNotesOpt', offset: '0', numToReturn: '50', demographicNo: d.patient, 'CSRF-TOKEN': token},
        headers: {'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest'},
        maxRedirects: 0, failOnStatusCode: false,
      });
      let body;
      try {
        h.assert(response.status() === 200, `${d.label}'s notes list answered HTTP ${response.status()}`);
        body = await response.text();
      } finally { await response.dispose(); }
      const warnings = own.map(id => (body.match(new RegExp(`id="editWarn${id}" value="(true|false)"`)) || [])[1])
        .filter(Boolean);
      h.assert(warnings.length > 0, `${d.label}'s notes list does not show its own unsigned notes`);
      h.assert(warnings.every(value => value === 'false'),
        `${d.label}'s notes list treats the logged-in provider's own unsigned notes as another provider's`);
      assertPatientStateUnchanged(before, snapshot(d.other), `${d.label}'s notes list`);
      // Later steps expect the other chart's entry to be the logged-in provider's again.
      await activateOther(d.otherChart);
    });
  }

  // The notes panel (ChartNotes.jsp, rendered again after every note save) hands its chart's entry
  // provider to the page, in its view form and its script. It took the bean's provider only when the
  // bean was this chart's and the logged-in provider otherwise, so once the other chart was entered
  // last, a chart opened from another provider's day sheet named the logged-in provider.
  for (const d of directions) {
    await s.step(`${d.label}'s notes panel, saved after the other chart was entered, keeps ${d.label}'s own entry provider`, async () => {
      // This patient's chart entered from another doctor's day sheet in a window since closed (a
      // second open chart of the same patient would hold its note), then the other chart.
      await enterChartElsewhere(d.patient, mrpB);
      await activateOther(d.otherChart);
      const before = snapshot(d.other);
      const editor = d.chart.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
      await editor.waitFor({state: 'visible', timeout: TIMEOUT});
      await editor.click();
      await editor.press('Control+End');
      await editor.pressSequentially(` ${marker}-${d.label}-entry-provider`, {delay: 5});
      await saveCurrentChartNote(d.chart, sql, d.patient, await editor.inputValue());
      const field = await d.chart.locator('form[name="caseManagementViewForm"] input[name="providerNo"]').inputValue();
      const script = await d.chart.evaluate(() => window.providerNo);
      h.assert(field === mrpB && script === mrpB,
        `${d.label}'s notes panel names ${field === provider ? 'the logged-in provider' : 'another provider'} as its entry provider, not the one ${d.label} was entered for`);
      assertPatientStateUnchanged(before, snapshot(d.other), `${d.label}'s notes panel`);
      // Later steps expect both entries, and this page's provider, to be the logged-in provider's
      // again: once the patient's entry is, the next note save renders the panel with it.
      await enterChartElsewhere(d.patient, provider);
      await editor.click();
      await editor.press('Control+End');
      await editor.pressSequentially(` ${marker}-${d.label}-entry-restored`, {delay: 5});
      await saveCurrentChartNote(d.chart, sql, d.patient, await editor.inputValue());
      h.assert(await d.chart.evaluate(() => window.providerNo) === provider,
        `${d.label}'s notes panel did not return to the logged-in provider's entry`);
      await activateOther(d.otherChart);
    });
  }

  async function reopenVascularTracker(chart, label, expectedId) {
    const item = chart.locator('#forms a[onclick*="forwardshortcutname"]').filter({hasText: 'Vascular Tracker'}).first();
    await item.waitFor({state: 'visible', timeout: TIMEOUT});
    const popup = await s.popup(chart, item, label);
    const form = popup.locator('form[action$="/form/SubmitForm"]');
    await form.locator('[name="value(formId)"]').waitFor({state: 'attached'});
    h.assert(await form.locator('[name="demographic_no"]').inputValue() === A
      && await form.locator('[name="value(formId)"]').inputValue() === expectedId,
    'The saved Vascular Tracker link did not reopen A\'s latest form');
    return {popup, form};
  }

  async function saveAndExitVascularTracker(popup, form, expectedId) {
    // Both listeners precede the native submit: the completion document closes its own window.
    const posted = popup.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/form/SubmitForm'), {timeout: TIMEOUT});
    const closed = popup.waitForEvent('close', {timeout: TIMEOUT});
    posted.catch(() => {}); closed.catch(() => {});
    const dialogs = await h.withExpectedDialogs(popup, async () => {
      await form.locator('input[type="submit"][onclick*="onSaveExit();"]').first().click()
        .catch(error => { if (!popup.isClosed()) throw error; });
    });
    const response = await posted;
    await response.finished();
    h.assert(response.status() === 200, 'Vascular Tracker Save & Exit did not return its completion page');
    const data = new URLSearchParams(response.request().postData() || '');
    assertRequestPatient(response.request(), A);
    h.assert(data.get('submit') === 'exit' && data.get('value(formId)') === expectedId,
      'Save & Exit did not submit the reopened A form through the native control');
    await closed;
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Save & Exit did not ask for confirmation exactly once');
    return data;
  }

  await isolatedWrite(directions[0], 'Vascular Tracker Save & Exit', async () => {
    // Only this run's saved A form receives historical data. The non-editable carriers must
    // preserve its original database values, including NULL, even if the browser submits changes.
    const historical = ['yes', null, `${marker}-historical-eye`, '0.88', '2026-01-07', `${marker}-historical-ratio`];
    const ownedForm = `ID=${entered.A.form} AND demographic_no=${A} AND WTComments=${q(`${marker}-A-vt`)}`;
    const editedQuery = `SELECT formEdited FROM formVTForm WHERE ${ownedForm}`;
    const editedBefore = sql.rows(editedQuery);
    h.assert(editedBefore.length === 1, 'The historical VT fixture does not own exactly one form');
    // Preserve the saved revision's timestamp: SQL's automatic timestamp may use a different
    // timezone from the application and otherwise make this older row appear newer after Save & Exit.
    h.assert(sql.value(`UPDATE formVTForm SET ${RETAINED_VT_FIELDS.map((field, index) =>
      `${field}=${historical[index] === null ? 'NULL' : q(historical[index])}`).join(',')},formEdited=formEdited
      WHERE ${ownedForm} AND ${RETAINED_VT_FIELDS.map(field => `${field} IS NULL`).join(' AND ')};
      SELECT ROW_COUNT()`) === '1', 'The historical VT fixture no longer owns an untouched A form');
    h.assert(JSON.stringify(sql.rows(editedQuery)) === JSON.stringify(editedBefore),
      'Seeding historical VT fields changed the saved revision timestamp');
    const retainedQuery = `SELECT ${RETAINED_VT_FIELDS.join(',')} FROM formVTForm WHERE ${ownedForm}`;
    h.assert(JSON.stringify(sql.rows(retainedQuery)) === JSON.stringify([historical]),
      'The owned historical VT fixture was not stored exactly');
    const retainedMeasurements = `SELECT * FROM measurements WHERE demographicNo IN (${patients})
      AND type IN ('iEx','WHR') ORDER BY id`;
    h.assert(sql.rows(retainedMeasurements).length === 0, 'The retention fixture already has unrelated retained-type measurements');
    const {popup, form} = await reopenVascularTracker(chartA, 'A-vascular-tracker-exit', entered.A.form);
    const tampered = ['no', '2031-09-23', `${marker}-tampered-eye`, '1.11', '2031-09-24', `${marker}-tampered-ratio`];
    for (let index = 0; index < RETAINED_VT_FIELDS.length; index++) {
      const carrier = form.locator(`input[name="value(${RETAINED_VT_FIELDS[index]})"]`);
      h.assert(await carrier.getAttribute('type') === 'hidden'
        && await carrier.inputValue() === (historical[index] ?? ''),
      'The reopened VT did not restore its historical hidden value or metadata');
      await carrier.evaluate((element, value) => { element.value = value; }, tampered[index]);
    }
    const editor = chartA.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
    const draft = await editor.inputValue();
    const previousForm = sql.rows(`SELECT * FROM formVTForm WHERE ID=${entered.A.form}`);
    const refreshes = [];
    for (const [id, route] of [['forms', 'displayForms'], ['measurements', 'displayMeasurements']]) {
      await chartA.locator(`#${id} > *`).first().evaluate(el => { el.setAttribute('data-before-form-exit', '1'); });
      const refreshed = chartA.waitForResponse(response => new URL(response.url()).pathname.endsWith(`/encounter/${route}`),
        {timeout: TIMEOUT});
      refreshed.catch(() => {});
      refreshes.push([id, refreshed]);
    }
    // Form setup must not make the save pass merely by restoring the session's patient to A.
    await activateOther(chartB);
    const submitted = await saveAndExitVascularTracker(popup, form, entered.A.form);
    for (let index = 0; index < RETAINED_VT_FIELDS.length; index++) {
      h.assert(submitted.get(`value(${RETAINED_VT_FIELDS[index]})`) === tampered[index],
        'The native Save & Exit did not submit the tampered hidden fields to the retention guard');
    }
    h.assert(await editor.inputValue() === `${draft}\nSee Vascular Tracker Template`,
      'Save & Exit did not append literal completion text to A\'s current note');
    for (const [id, pending] of refreshes) {
      const response = await pending;
      await response.finished();
      h.assert(response.status() === 200, 'Save & Exit did not refresh an own-chart module');
      assertRequestPatient(response.request(), A);
      await chartA.locator(`#${id} [data-before-form-exit]`).waitFor({state: 'detached', timeout: TIMEOUT});
    }
    const measurements = `type='WT' AND comments=${q(`${marker}-A-vt`)} AND demographicNo IN (${patients})`;
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE ${measurements}`, '1', 'Save & Exit did not write one owned WT reading');
    h.assert(sql.value(`SELECT demographicNo FROM measurements WHERE ${measurements}`) === A
      && sql.value(`SELECT dataField FROM measurements WHERE ${measurements}`) === '62.3',
    'Save & Exit wrote the WT reading to the wrong patient or changed its value');
    h.assert((await moduleText(chartA, 'measurements')).includes('62.3'), 'The refreshed Measurements module omitted the saved WT reading');
    await expectValue(sql, `SELECT COUNT(*) FROM formVTForm WHERE demographic_no=${A}`, '2', 'Save & Exit did not create a second A form revision');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM formVTForm WHERE ID=${entered.A.form}`)) === JSON.stringify(previousForm),
      'Save & Exit changed the previous saved form');
    entered.A.form = patientKey(sql.value(`SELECT MAX(ID) FROM formVTForm WHERE demographic_no=${A}`));
    h.assert(JSON.stringify(sql.rows(`SELECT ${RETAINED_VT_FIELDS.join(',')} FROM formVTForm
      WHERE ID=${entered.A.form} AND demographic_no=${A}`)) === JSON.stringify([historical]),
    'Save & Exit accepted tampered retained fields or changed the historical NULL date');
    h.assert(sql.rows(retainedMeasurements).length === 0,
      'Save & Exit fabricated a measurement from a retained iEx or WHR value');
  });

  await s.step('A form saved after its opener navigates to B closes without changing B\'s current note or clinical rows', async () => {
    const {popup, form} = await reopenVascularTracker(chartA, 'A-vascular-tracker-foreign-opener', entered.A.form);
    await form.locator('[name="value(WTValue)"]').fill('63.4');
    await form.locator('[name="value(WTComments)"]').fill(`${marker}-A-vt-after-navigation`);
    const noteId = await chartA.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
    const lockQuery = `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${A} AND provider_no=${q(provider)} AND note_id=${Number(noteId)}`;
    h.assert(sql.value(lockQuery) === '1', 'The departing A chart did not hold its own note lock');
    const otherLockQuery = `SELECT note_id,demographic_no,provider_no FROM casemgmt_note_lock
      WHERE demographic_no=${B} ORDER BY note_id,provider_no`;
    const otherLocks = sql.rows(otherLockQuery);
    h.assert(otherLocks.length > 0, 'B did not hold its own note lock before A navigated');
    const releases = [];
    const candidates = [];
    const onFailure = request => {
      if (releases.includes(request)) candidates.push({request, entry: recorder.requestFailures.at(-1)});
    };
    // Chromium omits an unintercepted Blob beacon's body from request events. A temporary
    // pass-through exposes its real bytes without rewriting the request or mocking its response.
    const releaseEndpoint = new URL(h.appUrl(s.config.baseUrl, '/CaseManagementEntry'));
    const releaseUrl = url => url.origin === releaseEndpoint.origin && url.pathname === releaseEndpoint.pathname;
    const passReleaseThrough = async route => {
      const request = route.request();
      if (isOwnedLockRelease(request, A, noteId, provider) && request.frame().page() === chartA) releases.push(request);
      await route.continue();
    };
    await context.route(releaseUrl, passReleaseThrough);
    chartA.on('requestfailed', onFailure);
    try {
      // Real navigation preserves the popup's WindowProxy opener while replacing its patient/document.
      await chartA.goto(chartB.url(), {waitUntil: 'domcontentloaded'});
      await waitForNavbars(chartA, TIMEOUT);
      h.assert(new URL(chartA.url()).searchParams.get('demographicNo') === B
        && await chartA.locator('#caseManagementEntryForm [name="demographicNo"]').inputValue() === B,
      'The form opener did not actually navigate to B');
      h.assert(releases.length > 0, 'Navigation did not send A\'s explicit note-lock release');
      await expectValue(sql, lockQuery, '0', 'Navigation left A\'s note lock behind');
      h.assert(JSON.stringify(sql.rows(otherLockQuery)) === JSON.stringify(otherLocks),
        'Releasing A\'s note lock changed B\'s note locks');
      // The browser can report the delivered beacon as aborted only after A's document is gone:
      // wait until each release has settled, so that its report is in before it is excused.
      await Promise.all(releases.map(request => request.response().catch(() => null)));
      consumeDeliveredLockReleases(recorder, candidates);
    } finally {
      chartA.off('requestfailed', onFailure);
      await context.unroute(releaseUrl, passReleaseThrough);
    }
    const editor = chartA.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
    // Settle B's own draft through its native Save before protecting every B row. Otherwise a
    // legitimate periodic autosave could replace B's draft during A's independent form save.
    await saveCurrentChartNote(chartA, sql, B, await editor.inputValue());
    const currentNote = await editor.inputValue();
    const before = snapshot(B);
    const previousForm = sql.rows(`SELECT * FROM formVTForm WHERE ID=${entered.A.form}`);
    h.assert(await popup.evaluate(() => window.opener.demographicNo) === B, 'The A form lost its actual B opener');
    await saveAndExitVascularTracker(popup, form, entered.A.form);
    h.assert(await editor.inputValue() === currentNote, 'A\'s completion text contaminated B\'s current note');
    const measurements = `type='WT' AND comments=${q(`${marker}-A-vt-after-navigation`)} AND demographicNo IN (${patients})`;
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE ${measurements}`, '1', 'The foreign-opener save lost A\'s measurement');
    h.assert(sql.value(`SELECT demographicNo FROM measurements WHERE ${measurements}`) === A
      && sql.value(`SELECT dataField FROM measurements WHERE ${measurements}`) === '63.4',
    'The foreign-opener save wrote A\'s measurement into B');
    await expectValue(sql, `SELECT COUNT(*) FROM formVTForm WHERE demographic_no=${A}`, '3', 'The foreign-opener save lost A\'s form revision');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM formVTForm WHERE ID=${entered.A.form}`)) === JSON.stringify(previousForm),
      'The foreign-opener save changed the prior A form revision');
    assertPatientStateUnchanged(before, snapshot(B), 'A\'s form completion in a B opener');
  });
}

async function workflow(s) {
  try {
    await run(s);
  } finally {
    // The charts take note locks. Release them the way closing the windows would, whatever a
    // step did, so a failed run does not leave locks for the cleanup to find; the cleanup still
    // deletes any that remain.
    await releaseChartLocks(s.context, s.config.baseUrl).catch(() => {});
  }
}

if (require.main === module) runWorkflow('echart-two-patient-isolation', workflow, { openPatient: true });
module.exports = { workflow, patientStateQueries, readPatientState, assertPatientStateUnchanged,
  invalidPatientParameters, assertRequestPatient, isOwnedLockRelease, consumeDeliveredLockReleases,
  prepareVascularTrackerFixture, assertVitalsMoreHistory, saveCurrentChartNote,
  assertVtControlLabel, assertVascularTrackerAccessibility };
