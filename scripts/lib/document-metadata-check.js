/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Installed-only building blocks. No fixture insertion, credential changes, or cleanup here.
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const h = require('./playwright-harness');
const {externalDocumentReferences, fileProof} = require('./stored-document-mutation-fixture');
const KEYS = {document: ['document_no'], ctl_document: ['module_id', 'module', 'document_no'],
  patientLabRouting: ['id'], providerLabRouting: ['id'], queue_document_link: ['id']};
const MUTABLE = {metadata: {document: ['docdesc', 'doctype', 'updatedatetime']},
  queue: {queue_document_link: ['status']}, unlink: {providerLabRouting: ['status', 'timestamp']}};
const METHODS = {metadata: 'documentUpdateAjax', queue: 'updateDocStatusInQueue', unlink: 'removeLinkFromDocument'};
function identifier(value) {assert(typeof value === 'string' && value.length && !/[^A-Za-z0-9_]/.test(value), 'Unsafe metadata fixture schema'); return '`' + value + '`';}
function positive(value) {assert(/^[1-9][0-9]*$/.test(String(value)) && Number(value) <= 2147483647, 'Invalid owned metadata identity'); return String(value);}
function condition(table, id) {
  return table === 'document' || table === 'ctl_document' ? 'document_no=' + id
    : table === 'queue_document_link' ? 'document_id=' + id : "lab_no=" + id + " AND lab_type='DOC'";
}

/** Call BEFORE creating any patient/document fixture. A blocked engine is not a skipped pass. */
function preflightMetadataSchema(sql, {patientCleanup} = {}) {
  const catalog = sql.rows(`SELECT c.TABLE_NAME,c.COLUMN_NAME,t.ENGINE FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
    WHERE c.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE' ORDER BY c.TABLE_NAME,c.ORDINAL_POSITION`);
  const schemas = new Map();
  for (const [table, column, engine] of catalog) {
    identifier(table); identifier(column);
    if (!schemas.has(table)) schemas.set(table, {engine, columns: []});
    assert.equal(schemas.get(table).engine, engine, 'Inconsistent metadata schema engine');
    schemas.get(table).columns.push(column);
  }
  const refs = catalog.filter(([, column]) => ['documentno', 'documentid', 'docno', 'docid'].includes(column.replaceAll('_', '').toLowerCase()))
    .map(([table, column]) => [table, column, '0']);
  refs.push(...sql.rows(`SELECT TABLE_NAME,COLUMN_NAME,1 FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='document' AND REFERENCED_COLUMN_NAME='document_no'`));
  // The workflow also creates a patient. Parent cleanup must never race a
  // nontransactional clinical reference, even when the document tables are safe.
  const patientRefs = catalog.filter(([, column]) => ['demographicno', 'demographicid', 'patientno', 'patientid', 'clientid']
    .includes(column.replaceAll('_', '').toLowerCase())).map(([table, column]) => [table, column, '0']);
  patientRefs.push(...sql.rows(`SELECT TABLE_NAME,COLUMN_NAME,1 FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='demographic' AND REFERENCED_COLUMN_NAME='demographic_no'`));
  const required = new Set([...Object.keys(KEYS), 'demographic', 'casemgmt_note_link',
    ...refs.map(([table]) => table), ...patientRefs.map(([table]) => table)]);
  const documentTables = new Set([...Object.keys(KEYS), 'demographic', 'casemgmt_note_link', ...refs.map(([table]) => table)]);
  for (const table of required) {
    const reviewedAriaParent = table === 'formRourke2009' && schemas.get(table)?.engine === 'Aria'
      && !documentTables.has(table) && patientCleanup?.tables.includes(table)
      && patientCleanup.schema.some(row => row[0] === table && row[3] === 'Aria' && row[9] === 'BASE TABLE');
    assert(schemas.get(table)?.engine === 'InnoDB' || reviewedAriaParent,
      'Metadata fixture requires transactional references or the exact reviewed parent-lock protocol');
  }
  for (const [table, columns] of Object.entries(KEYS)) for (const column of columns) assert(schemas.get(table).columns.includes(column), 'Required metadata fixture key missing');
  for (const [table, columns] of Object.entries({document: ['docdesc', 'doctype', 'updatedatetime', 'observationdate'], providerLabRouting: ['provider_no', 'status', 'timestamp'], queue_document_link: ['queue_id', 'status']})) {
    for (const column of columns) assert(schemas.get(table).columns.includes(column), 'Required metadata fixture column missing');
  }
  return {schemas, refs, patientRefs};
}

/** Full-row hashes plus operation-specific stable hashes; values never go to public diagnostics. */
function captureMetadataState(sql, identity, schema, kind) {
  const id = positive(identity.document), patient = positive(identity.patient);
  assert(MUTABLE[kind], 'Unknown metadata operation');
  assert.equal(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
    AND last_name=${h.sqlString(identity.marker)} AND first_name='Workflow' AND provider_no=${h.sqlString(identity.provider)}`), '1', 'Metadata patient ownership changed');
  const rows = {};
  for (const [table, keys] of Object.entries(KEYS)) {
    const fields = schema.schemas.get(table).columns;
    const full = `SHA2(JSON_ARRAY(${fields.map(identifier).join(',')}),256)`;
    const stable = `SHA2(JSON_ARRAY(${fields.filter(column => !(MUTABLE[kind][table] || []).includes(column)).map(identifier).join(',')}),256)`;
    rows[table] = sql.rows(`SELECT ${keys.map(identifier).join(',')},${full},${stable} FROM ${identifier(table)}
      WHERE ${condition(table, id)} ORDER BY ${keys.map(identifier).join(',')}`);
    assert(rows[table].every(row => row.length === keys.length + 2 && row.slice(-2).every(value => /^[a-f0-9]{64}$/.test(value))), 'Metadata row proof incomplete');
  }
  assert.equal(rows.document.length, 1, 'Owned metadata document missing');
  const references = externalDocumentReferences(schema.refs, id, table => schema.schemas.get(table).columns, new Set(Object.keys(KEYS)));
  for (const ref of references) assert.equal(sql.value(`SELECT COUNT(*) FROM ${identifier(ref.table)} WHERE ${ref.condition}`), '0', 'Metadata document gained an external reference');
  assert.equal(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link WHERE table_name=5 AND table_id=${id}`), '0', 'Metadata fixture gained a clinical note');
  const values = {
    document: sql.rows(`SELECT docdesc,doctype FROM document WHERE document_no=${id}`),
    queues: sql.rows(`SELECT id,queue_id,status FROM queue_document_link WHERE document_id=${id} ORDER BY id`),
    providers: sql.rows(`SELECT id,provider_no,status FROM providerLabRouting WHERE lab_no=${id} AND lab_type='DOC' ORDER BY id`),
  };
  return {rows, values, file: fileProof(identity.file, identity.store)};
}

function validateTransition(kind, before, after, intent) {
  assert(MUTABLE[kind], 'Unknown metadata transition');
  assert.deepEqual(after.file, before.file, 'Metadata operation changed clinical PDF bytes or ownership');
  for (const table of Object.keys(KEYS)) {
    const stable = rows => rows.map(row => [...row.slice(0, -2), row.at(-1)]);
    assert.deepEqual(stable(after.rows[table]), stable(before.rows[table]), 'Metadata operation changed rows outside its exact allowed fields');
    if (!MUTABLE[kind][table]) assert.deepEqual(after.rows[table], before.rows[table], 'Metadata operation changed unrelated rows');
  }
  if (kind === 'metadata') {
    assert.deepEqual(after.values.document, [[intent.description, intent.classification]], 'Saved metadata differs from frozen form');
    assert.deepEqual(after.values.queues, before.values.queues);
    assert.deepEqual(after.values.providers, before.values.providers);
  } else if (kind === 'queue') {
    assert.deepEqual(after.values.queues, before.values.queues.map(([key, queue, status]) => [key, queue, status === null || status === 'I' ? status : 'I']), 'Queue completion changed unexpected links');
    assert.deepEqual(after.values.document, before.values.document); assert.deepEqual(after.values.providers, before.values.providers);
  } else {
    assert(before.values.providers.some(([, provider]) => provider === intent.provider), 'Unlink target is not an owned provider route');
    assert.deepEqual(after.values.providers, before.values.providers.map(([key, provider, status]) => [key, provider, provider === intent.provider ? 'X' : status]), 'Provider unlink changed another route');
    const targetKeys = new Set(before.values.providers.filter(([, provider]) => provider === intent.provider).map(([key]) => key));
    assert.deepEqual(after.rows.providerLabRouting.filter(([key]) => !targetKeys.has(key)),
      before.rows.providerLabRouting.filter(([key]) => !targetKeys.has(key)), 'Unlink changed another provider timestamp');
    assert.deepEqual(after.values.document, before.values.document); assert.deepEqual(after.values.queues, before.values.queues);
  }
  return after;
}

function validateRequest(request, intent) {
  assert.equal(request.method(), 'POST'); assert.equal(request.url(), intent.endpoint);
  const fields = new URLSearchParams(request.postData() || '');
  const id = positive(intent.document), key = intent.kind === 'metadata' ? 'documentId' : intent.kind === 'queue' ? 'docid' : 'docId';
  assert.deepEqual(fields.getAll('method'), [METHODS[intent.kind]]);
  assert.deepEqual(fields.getAll(key), [id]);
  const token = fields.getAll('CSRF-TOKEN');
  assert(typeof intent.token === 'string' && intent.token, 'Frozen session token required');
  assert.equal(request.headers()['csrf-token'], intent.token, 'Metadata session token changed');
  assert(token.length <= 1 && (token.length === 0 ? intent.kind === 'metadata' : token[0] === intent.token), 'Metadata form token changed');
  assert.equal(request.postData(), intent.body, 'Metadata operation changed its frozen body');
  if (intent.kind === 'metadata') {
    assert(fields.getAll('doc_no').length <= 1 && (!fields.has('doc_no') || fields.get('doc_no') === id), 'Metadata legacy identity differs');
    assert.deepEqual(fields.getAll('demog'), [positive(intent.patient)]);
    assert.deepEqual(fields.getAll('documentDescription'), [intent.description]);
    assert.deepEqual(fields.getAll('docType'), [intent.classification]);
    assert.equal(fields.getAll('flagproviders').length, 0, 'Metadata check must not add provider routes');
  } else {
    const allowed = new Set(['method', key, 'CSRF-TOKEN', ...(intent.kind === 'unlink' ? ['docType', 'providerNo'] : [])]);
    assert([...fields.keys()].every(name => allowed.has(name)), 'Unexpected metadata action field');
    if (intent.kind === 'unlink') {
      assert.deepEqual(fields.getAll('docType'), ['DOC']); assert.deepEqual(fields.getAll('providerNo'), [intent.provider]);
    }
  }
  return fields;
}
function confirmedResponse(response, data, intent) {
  assert.equal(response.status(), 200);
  assert(/^application\/json(?:\s*;|$)/i.test(response.headers()['content-type'] || ''), 'Metadata response was not JSON');
  assert(data && data.success === true && data.accepted === true && Number.isSafeInteger(data.document)
    && String(data.document) === positive(intent.document), 'Metadata acceptance identity is unconfirmed');
  if (intent.kind === 'metadata') assert.equal(data.patientId, positive(intent.patient));
  if (intent.kind === 'unlink') assert(Array.isArray(data.linkedProviders)
    && data.linkedProviders.every(link => link && typeof link.providerNo === 'string' && link.providerNo !== intent.provider), 'Unlink response still contains removed provider');
}

/** Injected faults NEVER forward. Real dispatch is single-shot and durably owned before transmission. */
function createMetadataTransport({intent, fixture, capture, accept, mode = 'real'}) {
  assert(['real', 'rejected500', 'html200', 'lostBeforeForward'].includes(mode));
  assert.equal(typeof accept, 'function', 'Exact durable fixture transition adapter is required');
  let calls = 0, forwarded = 0, settled = false, accepted = false, failure;
  const pending = new Set(), requests = new Set(), forwardedRequests = new WeakSet();
  const handle = async route => {
    let response;
    try {
      requests.add(route.request());
      assert.equal(++calls, 1, 'Duplicate metadata request');
      validateRequest(route.request(), intent); fixture.assertUnchanged();
      if (mode !== 'real') {
        if (mode === 'lostBeforeForward') await route.abort('failed');
        else if (mode === 'html200') await route.fulfill({status: 200, contentType: 'text/html', body: '<html>Unconfirmed response</html>'});
        else await route.fulfill({status: 500, contentType: 'application/json', body: JSON.stringify({success: false, accepted: false, retryable: false, document: Number(intent.document)})});
        fixture.assertUnchanged(); settled = true; return;
      }
      const before = capture();
      fixture.begin(); forwarded++; forwardedRequests.add(route.request());
      response = await route.fetch({maxRedirects: 0, maxRetries: 0, timeout: 90000});
      const bytes = await response.body(); const data = JSON.parse(bytes.toString('utf8'));
      confirmedResponse(response, data, intent);
      const after = validateTransition(intent.kind, before, capture(), intent);
      // Must fsync the owned fixture's new exact snapshot before exposing acceptance.
      await accept({kind: intent.kind, intent, before, after, response: data,
        requestBodySha256: createHash('sha256').update(intent.body).digest('hex')});
      accepted = true;
      await route.fulfill({response, body: bytes});
      await response.dispose(); response = null; settled = true;
    } catch (error) {
      failure ||= error;
      if (forwarded) fixture.transportFailed();
      await route.abort('failed').catch(() => {});
      throw error;
    } finally {
      if (response) {
        try {await response.dispose();}
        catch (error) {failure ||= error; if (forwarded) fixture.transportFailed();}
      }
    }
  };
  return {route(value) {
    const promise = handle(value); pending.add(promise);
    promise.then(() => pending.delete(promise), () => pending.delete(promise));
    return promise;
  }, ownsForwarded: request => forwardedRequests.has(request), durablyAccepted: () => accepted, requests: () => new Set(requests), counts: () => ({calls, forwarded, settled}),
  async drain() {while (pending.size) await Promise.allSettled([...pending]); if (failure) throw failure;}};
}

/** Freeze the real successful form controls and the session token before installing a route. */
async function freezeMetadataIntent(page, identity, endpoint) {
  const document = positive(identity.document), patient = positive(identity.patient);
  const frozen = await page.evaluate(id => {
    const form = window.document.getElementById('forms_' + id);
    if (!form) throw new Error('Owned metadata form missing');
    const fields = new URLSearchParams(new FormData(form));
    if (fields.getAll('method').length !== 1 || !['documentUpdate', 'documentUpdateAjax'].includes(fields.get('method'))) throw new Error('Unexpected metadata form method');
    const ids = fields.getAll('documentId'), legacy = fields.getAll('doc_no');
    if (ids.length > 1 || legacy.length > 1 || (ids.length ? ids[0] : legacy[0]) !== id || (legacy.length && legacy[0] !== id)) throw new Error('Owned metadata form identity differs');
    fields.set('method', 'documentUpdateAjax');
    fields.set('documentId', id);
    if (fields.has('saved')) fields.set('saved', 'false'); // Production clears it before FormData.
    const token = (form.querySelector('input[name="CSRF-TOKEN"]') || window.document.querySelector('input[name="CSRF-TOKEN"]'))?.value;
    return {body: fields.toString(), token, description: fields.get('documentDescription'), classification: fields.get('docType')};
  }, document);
  const intent = {kind: 'metadata', document, patient, provider: identity.provider, endpoint, ...frozen};
  validateRequest({method: () => 'POST', url: () => endpoint, postData: () => intent.body,
    headers: () => ({'csrf-token': intent.token})}, intent);
  return Object.freeze(intent);
}

/** Uses real form submit, not a replacement Save implementation. Fault pages must be freshly opened. */
async function checkMetadataUiAttempt({page, intent, transport, mode = 'real'}) {
  const id = positive(intent.document), form = page.locator('#forms_' + id), endpoint = intent.endpoint;
  const events = [];
  const observed = request => {if (request.url() === endpoint && request.method() === 'POST') events.push(request);};
  const handler = route => transport.route(route).catch(() => {});
  page.on('request', observed); await page.route(endpoint, handler);
  const beforeUrl = page.url();
  try {
    assert.equal(await form.locator('[name="documentDescription"]').inputValue(), intent.description);
    await page.locator('#save' + id).click();
    const status = page.locator('#document-metadata-status-' + id);
    await status.waitFor({state: 'visible'});
    await page.waitForFunction(({id, mode}) => {
      const status = document.getElementById('document-metadata-status-' + id);
      const messages = window.CarlosDocumentMutationMessages || {};
      const expected = mode === 'real' ? messages.saved : mode === 'rejected500' ? messages.rejected : messages.uncertain;
      return status && expected && status.textContent === expected;
    }, {id, mode});
    await transport.drain();
    assert.equal(page.url(), beforeUrl, 'Metadata Save unexpectedly navigated');
    assert.equal(await form.locator('[name="documentDescription"]').inputValue(), intent.description);
    assert.equal(await form.locator('[name="docType"]').inputValue(), intent.classification);
    const saved = await page.locator('#saved' + id).inputValue();
    assert.equal(saved, mode === 'real' ? 'true' : 'false', 'Saved marker disagrees with confirmed acceptance');
    assert.equal(await page.locator('#saveSucessMsg_' + id).isVisible(), mode === 'real', 'Visible Saved label disagrees with confirmed acceptance');
    if (mode === 'html200' || mode === 'lostBeforeForward') {
      assert.equal(await page.locator('#save' + id).isDisabled(), true);
      await page.evaluate(id => window.CarlosDocumentMetadata.save('forms_' + id), id);
    }
    await page.waitForTimeout(1200);
    assert.equal(events.length, 1, 'Failed or completed metadata operation was replayed');
    assert.equal(transport.counts().forwarded, mode === 'real' ? 1 : 0);
  } finally {
    await page.evaluate(() => window.dispatchEvent(new Event('pagehide'))).catch(() => {});
    try {await transport.drain();} finally {await page.unroute(endpoint, handler); page.off('request', observed);}
  }
}

module.exports = {preflightMetadataSchema, captureMetadataState, validateTransition, validateRequest, confirmedResponse,
  createMetadataTransport, freezeMetadataIntent, checkMetadataUiAttempt};
