/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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

const assert = require('node:assert/strict');
const fs = require('node:fs');
const { createHash, randomBytes } = require('node:crypto');

const sha256 = value => createHash('sha256').update(value).digest('hex');
const POSITIVE = /^[1-9]\d*$/;
const MARKER = /^FAKE-PW[0-9a-f]{16}$/;
const HEADER = 'X-CARLOS-AP-Probe';

function validateSubmission(submission, { baseUrl, fid, patient, provider, expected }) {
  for (const id of [fid, patient, provider]) assert(POSITIVE.test(String(id)), 'Invalid AP probe scope');
  const base = new URL(baseUrl);
  const target = new URL(submission.action);
  assert(target.origin === base.origin && target.pathname === `${base.pathname.replace(/\/$/, '')}/eform/addEForm`
    && !target.username && !target.password && !target.hash, 'AP probe must use the actual local addEForm route');
  const query = target.searchParams;
  for (const [name, id] of [['efmfid', fid], ['efmdemographic_no', patient], ['efmprovider_no', provider]]) {
    assert.deepEqual(query.getAll(name), [String(id)], 'AP probe route has a changed or duplicate identity');
  }
  assert.equal([...query].length, 3, 'AP probe action must not include another workflow route');
  assert.equal(submission.method, 'post', 'AP probe form must POST');
  assert(Array.isArray(submission.entries) && submission.entries.every(pair => Array.isArray(pair)
    && pair.length === 2 && pair.every(value => typeof value === 'string')), 'AP probe requires string-only successful controls');
  const body = new URLSearchParams(submission.entries);
  for (const [name, id] of [['efmfid', fid], ['efmdemographic_no', patient], ['efmprovider_no', provider]]) {
    assert(body.getAll(name).every(value => value === String(id)), 'AP probe body has a conflicting identity');
  }
  for (const key of ['CSRF-TOKEN', 'carlosEformSubmission']) {
    assert(body.getAll(key).length === 1 && body.get(key), 'The actual form must provide a unique request token');
  }
  assert.deepEqual(body.getAll('newForm'), ['true'], 'The actual new form must supply its newForm default');
  assert.equal(body.getAll('playwright_latest_lab_values').length, 1, 'The AP probe field must be unique');
  const values = JSON.parse(body.get('playwright_latest_lab_values'));
  assert(Array.isArray(values) && values.length === expected.length, 'The serialized AP probe must contain all DOM-proven values');
  const actual = new Map(values.map(row => [row.loincCode, row.measurementValue]));
  assert(actual.size === expected.length && expected.every(row => actual.get(row.identifier) === row.value),
    'The serialized AP probe changed a DOM-proven clinical value');
  for (const key of ['faxEForm', 'emailEForm', 'recipientFaxNumber', 'recipient', 'saveToDoc', 'addToDocuments']) {
    assert(body.getAll(key).every(value => value === '' || value === 'false'), 'AP probe must not carry a transport or document workflow intent');
  }
  return { target: target.href, uri: `${target.pathname}${target.search}`, body: body.toString() };
}

function logCheckpoint(filename) {
  assert(typeof filename === 'string' && filename.startsWith('/'), 'WAF probe log paths must be explicit and absolute');
  const stat = fs.lstatSync(filename);
  assert(stat.isFile() && !stat.isSymbolicLink(), 'WAF probe log must be a regular file');
  return { filename, dev: stat.dev, ino: stat.ino, offset: stat.size };
}

function readCheckpoint(checkpoint) {
  const descriptor = fs.openSync(checkpoint.filename, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const stat = fs.fstatSync(descriptor);
    assert(stat.isFile() && stat.dev === checkpoint.dev && stat.ino === checkpoint.ino
      && stat.size >= checkpoint.offset, 'WAF probe log rotated or truncated during the request');
    const size = stat.size - checkpoint.offset;
    assert(size <= 8 * 1024 * 1024, 'WAF probe log delta exceeded the bounded evidence budget');
    const bytes = Buffer.alloc(size);
    assert.equal(fs.readSync(descriptor, bytes, 0, size, checkpoint.offset), size, 'Incomplete WAF probe log read');
    return bytes.toString('utf8');
  } finally { fs.closeSync(descriptor); }
}

function correlatedDenial(audit, errors, { token, uri }) {
  const matches = [];
  // Serial JSON records may begin with the tail of an older transaction. Never
  // borrow that tail, another transaction, or an incomplete current record.
  for (const line of audit.split(/\r?\n/)) {
    let document;
    try { document = JSON.parse(line); } catch { continue; }
    const tx = document && typeof document === 'object' && document.transaction;
    if (!tx || typeof tx !== 'object') continue;
    const headers = tx.request && tx.request.headers;
    if (!headers || typeof headers !== 'object' || Array.isArray(headers)) continue;
    const proofHeaders = Object.entries(headers).filter(([name]) => name.toLowerCase() === HEADER.toLowerCase());
    if (proofHeaders.length !== 1 || proofHeaders[0][1] !== token) continue;
    matches.push(tx);
  }
  assert.equal(matches.length, 1, 'A unique complete WAF transaction must match this exact probe header');
  const tx = matches[0];
  assert(tx.request.method === 'POST' && tx.request.uri === uri && tx.response?.http_code === 403,
    'The correlated WAF transaction must reject the exact POST route with403');
  assert(typeof tx.unique_id === 'string' && /^[A-Za-z0-9_.@-]+$/.test(tx.unique_id), 'Invalid WAF transaction identity');
  const messages = Array.isArray(tx.messages) ? tx.messages : [];
  const rules = new Set(messages.map(message => String(message?.details?.ruleId || '')));
  assert(rules.has('949110') && rules.has('932130')
    && ['941100', '941110', '941120', '941160'].some(rule => rules.has(rule)),
  'The same WAF transaction must contain marker/XSS signatures and inbound anomaly evaluation');
  assert(messages.some(message => /\bARGS:playwright_latest_lab_values\b/.test(
    `${message?.details?.match || ''} ${message?.details?.data || ''}`)), 'The WAF denial must identify the actual AP argument');
  const phase = errors.split(/\r?\n/).filter(line => line.includes(`[unique_id "${tx.unique_id}"]`)
    && /Access denied with code 403 \(phase 2\)/.test(line) && line.includes('[id "949110"]'));
  assert(phase.length >= 1, 'The same WAF transaction must have an explicit phase2 denial');
  return { transactionId: tx.unique_id, rules: [...rules].filter(rule => /^\d+$/.test(rule)).sort(),
    auditTransactionSha256: sha256(JSON.stringify(tx)), phase2Sha256: sha256(phase.join('\n')) };
}

function ownedState(sql, { patient, marker, provider, expected }) {
  assert(POSITIVE.test(String(patient)) && POSITIVE.test(String(provider)) && MARKER.test(marker), 'Invalid owned clinical fixture scope');
  assert(expected.length === 9 && new Set(expected.map(row => row.measurement)).size === 9
    && expected.every(row => POSITIVE.test(String(row.measurement))), 'The probe must own exactly nine unique measurements');
  const rows = sql.rows(`SELECT m.id,m.demographicNo,m.providerNo,m.type,m.dataField,m.comments,x.keyval,x.val
    FROM measurements m JOIN measurementsExt x ON x.measurement_id=m.id
    WHERE m.demographicNo=? ORDER BY m.id,x.id`, [String(patient)]);
  const wanted = expected.map(row => [String(row.measurement), String(patient), String(provider), 'PWLB', row.value, marker, 'identifier', row.identifier])
    .sort((a, b) => Number(a[0]) - Number(b[0]));
  assert.deepEqual(rows, wanted, 'An owned lab measurement, value, extension, or identity changed');
  assert.equal(sql.value('SELECT COUNT(*) FROM demographic WHERE demographic_no=? AND BINARY last_name=BINARY ? AND provider_no=?',
    [String(patient), marker, String(provider)]), '1', 'The owned clinical patient identity changed');
  const formRows = sql.rows('SELECT fdid,fid FROM eform_data WHERE demographic_no=? ORDER BY fdid', [String(patient)]);
  const values = sql.rows('SELECT v.* FROM eform_values v WHERE v.demographic_no=? OR v.fdid IN (SELECT fdid FROM eform_data WHERE demographic_no=?) ORDER BY v.id', [String(patient), String(patient)]);
  return { formRows, values };
}

async function proveWafNegative({ context, page, sql, scope, auditLog, errorLog, pause = ms => new Promise(resolve => setTimeout(resolve, ms)) }) {
  const before = ownedState(sql, scope);
  assert.deepEqual(before, { formRows: [], values: [] }, 'The negative probe must precede any saved form');
  await page.evaluate(() => {
    const form = window.getEForm();
    if (!form) throw new Error('The actual generated save form is absent');
    if (window.__carlosApProbeSubmission) throw new Error('An AP negative capture already exists');
    form.addEventListener('submit', event => {
      if (event.defaultPrevented) throw new Error('The actual eForm validation canceled the negative probe');
      event.preventDefault();
      window.__carlosApProbeSubmission = { action: form.action, method: form.method,
        entries: [...new FormData(form, event.submitter).entries()] };
    }, { once: true });
  });
  // Run the real toolbar/template validation, image inputs and subject update.
  // Only native navigation is canceled. The resulting successful controls are
  // sent once below with the same context's cookies and genuine form tokens.
  await page.locator('#remoteSubmitButton').click();
  await page.waitForFunction(() => window.__carlosApProbeSubmission, null, { timeout: 10000 });
  const submission = await page.evaluate(() => window.__carlosApProbeSubmission);
  const validated = validateSubmission(submission, scope);
  const referer = new URL(page.url());
  const base = new URL(scope.baseUrl);
  assert(referer.origin === base.origin && referer.pathname === `${base.pathname.replace(/\/$/, '')}/eform/efmformadd_data`
    && referer.searchParams.get('fid') === String(scope.fid)
    && referer.searchParams.get('demographic_no') === String(scope.patient), 'AP probe must originate from its owned new form');
  const token = `ap-probe-${randomBytes(16).toString('hex')}`;
  const auditStart = logCheckpoint(auditLog);
  const errorStart = logCheckpoint(errorLog);
  const response = await context.request.post(validated.target, {
    data: validated.body, headers: { 'Content-Type': 'application/x-www-form-urlencoded', [HEADER]: token,
      Origin: base.origin, Referer: referer.href },
    maxRedirects: 0, timeout: 30000, failOnStatusCode: false,
  });
  const body = await response.body();
  assert.equal(response.status(), 403, 'The intentional hostile AP POST must be denied');
  assert.equal(response.url(), validated.target, 'The intentional hostile AP POST must not redirect');
  let proof;
  let lastError;
  for (let attempt = 0; attempt < 30; attempt += 1) {
    try { proof = correlatedDenial(readCheckpoint(auditStart), readCheckpoint(errorStart), { token, uri: validated.uri }); break; }
    catch (error) { lastError = error; }
    await pause(100);
  }
  assert(proof, lastError?.message || 'No complete correlated WAF denial was observed');
  assert.deepEqual(ownedState(sql, scope), before, 'The rejected AP POST wrote or changed an owned form or its clinical fixture');
  return { outcome: 'PASS', mode: 'WAF_NEGATIVE_NO_SAVE', ...proof, status: response.status(),
    requestBodySha256: sha256(validated.body), responseBodySha256: sha256(body), responseBytes: body.length,
    beforeSavedForms: 0, afterSavedForms: 0, tokensAndBodyWithheld: true };
}

function transitionToClinicalValues(sql, scope) {
  assert.deepEqual(ownedState(sql, scope), { formRows: [], values: [] }, 'Clinical transition must precede any saved form');
  const expected = scope.expected.map(row => ({ ...row }));
  const replacements = { 7: '3–5 per field', 8: '>1000 / mL' };
  // Each exact parent/provider/marker/value/identifier predicate is required.
  // A partial fixture failure can only alter this run's rows and cannot pass.
  for (const index of [7, 8]) {
    const old = scope.expected[index];
    sql.execute(`UPDATE measurements m JOIN demographic d ON d.demographic_no=m.demographicNo
      JOIN measurementsExt x ON x.measurement_id=m.id
      SET m.dataField=? WHERE m.id=? AND m.demographicNo=? AND m.providerNo=? AND m.type='PWLB'
      AND BINARY m.dataField=BINARY ? AND BINARY m.comments=BINARY ? AND BINARY d.last_name=BINARY ?
      AND d.provider_no=? AND x.keyval='identifier' AND BINARY x.val=BINARY ?`,
    [replacements[index], String(old.measurement), String(scope.patient), String(scope.provider),
      old.value, scope.marker, scope.marker, String(scope.provider), old.identifier]);
    expected[index].value = replacements[index];
    assert.deepEqual(ownedState(sql, { ...scope, expected }), { formRows: [], values: [] }, 'The exact owned AP fixture transition failed');
  }
  assert.deepEqual(ownedState(sql, { ...scope, expected }), { formRows: [], values: [] }, 'Clinical transition changed a saved form');
  return expected;
}

function assertPersistedLabValues(sql, scope, fdid) {
  assert(POSITIVE.test(String(fdid)) && POSITIVE.test(String(scope.fid))
    && POSITIVE.test(String(scope.patient)), 'Invalid saved AP fixture scope');
  const rows = sql.rows(`SELECT v.var_value FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid
    WHERE d.fdid=? AND d.fid=? AND d.demographic_no=? AND v.fid=d.fid
    AND v.demographic_no=d.demographic_no AND v.var_name='playwright_latest_lab_values'`,
  [String(fdid), String(scope.fid), String(scope.patient)]);
  assert.equal(rows.length, 1, 'The saved eForm must persist exactly one owned lab JSON value');
  const values = JSON.parse(rows[0][0]);
  assert(Array.isArray(values) && values.length === scope.expected.length, 'Saved lab JSON omitted or duplicated clinical values');
  const actual = new Map(values.map(row => [row.loincCode, row.measurementValue]));
  assert(actual.size === scope.expected.length && scope.expected.every(row => actual.get(row.identifier) === row.value),
    'The database did not persist the exact benign clinical lab values');
}

module.exports = { correlatedDenial, validateSubmission, logCheckpoint, readCheckpoint, ownedState, proveWafNegative, transitionToClinicalValues, assertPersistedLabValues };
