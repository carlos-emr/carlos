/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Modifications by CARLOS Contributors, 2026.
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
'use strict';
const {randomBytes} = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./playwright-harness');

function requireDisposableEnvironment(env = process.env) {
  h.assert(env.EXCLUSIVE === '1' && env.CARLOS_DISPOSABLE_VM === 'true',
    'Owned Inbox fixtures require EXCLUSIVE=1 and CARLOS_DISPOSABLE_VM=true');
}
function assertOwnedRows(actual, expected, label) {
  const missing = expected.filter(identity => !actual.includes(identity));
  h.assert(!missing.length, `${label}: missing owned Inbox identities ${missing.join(', ')}`);
}

/** Native Create Lab writes only fresh unique accessions for the workflow-owned patient. */
async function prepareInboxDataset(s, mode, dependencies = {}) {
  requireDisposableEnvironment();
  h.assert(['filters', 'acknowledgement'].includes(mode), 'Unknown owned Inbox fixture mode');
  h.assert(process.env.LAB_UPLOAD_DOCUMENT_STORE && fs.statSync(fs.realpathSync(process.env.LAB_UPLOAD_DOCUMENT_STORE)).isDirectory(),
    'Owned Inbox fixtures require the installed LAB_UPLOAD_DOCUMENT_STORE');
  if (mode === 'filters') h.assert((process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR)
    && fs.statSync(fs.realpathSync(process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR)).isDirectory(),
  'Owned Inbox filter fixtures require the installed DOCUMENT_DIR');
  h.assert(/^FAKE-PW[a-f0-9]{16}$/.test(s.marker) && /^[1-9]\d*$/.test(s.patient)
    && /^[1-9]\d*$/.test(s.provider), 'Invalid owned Inbox patient/provider identity');
  // Lazy imports avoid the existing lab helper -> Inbox settle() dependency cycle.
  const submit = dependencies.submit || require('../lab-manual-entry-cumulative-playwright-checks').submitCreateLab;
  const remove = dependencies.remove || require('../lab-forwarding-rules-playwright-checks').removeOwnedHl7Labs;
  const {sql, patient, provider, marker} = s, q = h.sqlString;
  const stem = `IB${randomBytes(7).toString('hex').toUpperCase()}`;
  const accessions = ['0','1','2'].map(suffix => stem + suffix);
  const scope = `accessionNum IN (${accessions.map(q).join(',')})`;
  const messageScope = `FROM_BASE64(message) LIKE ${q('%' + stem + '%')}`;
  const ownPatient = `demographic_no=${patient} AND last_name=${q(marker)} AND first_name='Workflow' AND provider_no=${q(provider)}`;
  function verifyPatient() {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE ${ownPatient}`) === '1', 'Owned Inbox patient changed');
  }
  verifyPatient();
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM hl7TextInfo WHERE ${scope})
    + (SELECT COUNT(*) FROM hl7TextMessage WHERE ${messageScope})`) === '0', 'Owned Inbox accessions already exist');
  const labs = [];
  const journal = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'inbox-owned-')), 'journal.json');
  function checkpoint(phase) {
    const temporary = journal + '.new';
    fs.writeFileSync(temporary, JSON.stringify({marker, patient, provider, mode, accessions, labs, phase}, null, 2) + '\n', {flag: 'wx', mode: 0o600});
    const descriptor = fs.openSync(temporary, 'r');
    try {fs.fsyncSync(descriptor);} finally {fs.closeSync(descriptor);}
    fs.renameSync(temporary, journal);
    const directory = fs.openSync(path.dirname(journal), 'r');
    try {fs.fsyncSync(directory);} finally {fs.closeSync(directory);}
  }
  checkpoint('planned-before-native-create');
  console.log(`  owned Inbox fixture journal: ${journal}`);
  const expected = {all: [], new: [], statuses: {N: [], A: [], F: []}};
  // Intent is registered before the first native submission. Querying exact absent-before
  // accessions recovers committed inserts even when the browser acknowledgement is lost.
  s.cleanup(() => {
    verifyPatient();
    const found = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE ${scope}
      UNION SELECT lab_id FROM hl7TextMessage WHERE ${messageScope}`).map(([id]) => id);
    h.assert(found.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned Inbox lab identity');
    const toRemove = [...new Set([...labs, ...found])];
    for (const id of toRemove) h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting
      WHERE lab_type='HL7' AND lab_no=${id} AND demographic_no<>${patient}`) === '0',
    'Owned Inbox lab acquired another patient; retain it for recovery');
    checkpoint('cleanup-started');
    remove(sql, toRemove);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM hl7TextInfo WHERE ${scope})
      + (SELECT COUNT(*) FROM hl7TextMessage WHERE ${messageScope})`) === '0', 'Owned Inbox labs remain');
    checkpoint('cleaned');
  });
  const statuses = mode === 'filters' ? ['N', 'A', 'F'] : ['N', 'N', 'N'];
  for (let index = 0; index < accessions.length; index++) {
    const accession = accessions[index], status = statuses[index];
    const created = await submit(s, {accession, label: `owned-inbox-${index}`, tests: [{
      code: ['2010', '2013', '3210'][index], name: ['HEMOGLOBIN', 'HEMATOCRIT', 'SODIUM'][index],
      value: ['137', '0.40', '140'][index], unit: ['g/L', 'L/L', 'mmol/L'][index], low: '0', high: '200', flag: 'N',
    }]});
    await created.form.close();
    if (created.inbox !== s.schedule) await created.inbox.close();
    const rows = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(accession)}`);
    h.assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]), 'Native Create Lab did not persist one owned result');
    const id = rows[0][0]; labs.push(id); checkpoint('native-lab-created');
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${id}
      AND demographic_no=${patient}`) === '1', 'Native Inbox lab is not routed to the owned patient');
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${id}
      AND provider_no=${q(provider)} AND status='N'`) === '1', 'Native Inbox lab is not New for this login');
    // Only the current login's newly created route is changed. Existing foreign routes are untouched.
    sql.execute(`UPDATE providerLabRouting SET status=${q(status)} WHERE lab_type='HL7' AND lab_no=${id}
      AND provider_no=${q(provider)}`);
    h.assert(sql.value(`SELECT status FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${id}
      AND provider_no=${q(provider)}`) === status, 'Owned Inbox review status was not prepared');
    const identity = `HL7:${id}`; expected.all.push(identity); expected.statuses[status].push(identity);
    if (status === 'N') expected.new.push(identity);
  }
  if (mode === 'filters') {
    const prepareProgram = dependencies.prepareProgram || require('./incoming-filing-program-fixture').prepareIncomingFilingProgram;
    const createDocument = dependencies.createDocument || require('./stored-document-mutation-fixture').createStoredDocumentFixture;
    let document, documentCleanupComplete = false;
    const {program} = prepareProgram({...s, cleanup(callback) {
      s.cleanup(() => {
        h.assert(documentCleanupComplete, 'Retain Inbox admission while owned document cleanup is incomplete');
        return callback();
      });
    }});
    document = createDocument({...s, cleanup(callback) {
      s.cleanup(async () => { await callback(); documentCleanupComplete = true; });
    }}, program);
    // workflow-session closes the browser before unwinding callbacks in reverse order.
    s.cleanup(() => document.closeAfterDrain());
    const identity = `DOC:${document.sourceId}`;
    expected.all.push(identity); expected.new.push(identity); expected.statuses.N.push(identity);
  }
  checkpoint('ready');
  return {journal, marker, labs: labs.map(id => `HL7:${id}`), ...expected};
}

/** Keep the actual UI audit on the new patient; never widen into or acknowledge a foreign chart. */
async function narrowToOwnedPatient(page, dataset, timeout, settle) {
  if (!await page.locator('#inbox-sidebar').isVisible()) await page.locator('#inbox-sidebar-toggle').click({timeout});
  h.assert(await page.locator('#specificProvider').isChecked(), 'Owned Inbox audit requires the current login provider');
  await page.locator('#specificPatients').check({timeout});
  await page.locator('#inputLastName').fill(dataset.marker);
  await page.locator('#inputHIN').fill('');
  await page.locator('#statusNew').check({timeout});
  await require('./playwright-ui').clickAndAwaitReload(page, page.locator('#inboxhubFormSearchBtn'),
    {timeout, label: 'the owned patient Inbox search'});
  await settle(page, timeout);
}

async function assertOwnedAcknowledgementChain(view, identity, ownedLabs) {
  h.assert(ownedLabs.includes(identity), 'Acknowledgement target is not an owned Inbox lab');
  const chain = await view.evaluate(() => document.querySelector(
    'form[id^="acknowledgeForm_"] input[name="multiID"], input[name="multiID"]')?.value || '');
  const ids = String(chain).split(',').map(id => id.trim()).filter(Boolean);
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id) && ownedLabs.includes(`HL7:${id}`)),
    'Acknowledgement version chain contains a foreign Inbox lab');
}

function assertOwnedReviewStatus(rows, dataset, status) {
  assertOwnedRows(rows, dataset.statuses[status], `Owned ${status} status`);
  const wrong = dataset.all.filter(identity => rows.includes(identity) && !dataset.statuses[status].includes(identity));
  h.assert(!wrong.length, `Owned Inbox results returned under the wrong ${status} status: ${wrong.join(', ')}`);
}

/** Legacy workflow callbacks are retained if close() fails and Chromium stays connected. */
function withConfirmedBrowserDrain(session) {
  const browser = session.context.browser();
  h.assert(browser && typeof browser.isConnected === 'function', 'Owned Inbox browser lifecycle is unavailable');
  return {...session, cleanup(callback) {
    session.cleanup(async () => {
      h.assert(!browser.isConnected(), 'Owned Inbox children retained: browser drain is unconfirmed');
      return callback();
    });
  }};
}

function runOwnedInboxCheck(name, mode, main) {
  const {runWorkflow} = require('./workflow-session');
  return runWorkflow(name, async original => {
    const s = withConfirmedBrowserDrain(original);
    const dataset = await prepareInboxDataset(s, mode);
    await s.step('owned Inbox results exercise the complete UI assertions', () => main({session: s, dataset}));
  }, {openMaster: false, preflight: () => requireDisposableEnvironment()});
}
module.exports = {withConfirmedBrowserDrain, assertOwnedAcknowledgementChain, assertOwnedReviewStatus, requireDisposableEnvironment, assertOwnedRows, prepareInboxDataset, narrowToOwnedPatient, runOwnedInboxCheck};
