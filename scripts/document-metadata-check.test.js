/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {validateTransition, validateRequest, confirmedResponse, createMetadataTransport,
  preflightMetadataSchema} = require('./lib/document-metadata-check');
const hash = digit => digit.repeat(64);
const clone = value => JSON.parse(JSON.stringify(value));
function intent(kind = 'metadata') {
  const fields = kind === 'metadata' ? {method: 'documentUpdateAjax', documentId: '42', demog: '77', documentDescription: 'Owned revised', docType: 'Consult'}
    : kind === 'queue' ? {method: 'updateDocStatusInQueue', docid: '42'}
      : {method: 'removeLinkFromDocument', docId: '42', providerNo: '999998', docType: 'DOC'};
  fields['CSRF-TOKEN'] = 'owned-token';
  return {kind, document: '42', patient: '77', provider: '999998', description: 'Owned revised', classification: 'Consult',
    endpoint: 'https://owned.invalid/' + (kind === 'queue' ? 'inboxManage' : 'ManageDocument'), token: 'owned-token', body: new URLSearchParams(fields).toString()};
}
function request(value) {return {method: () => 'POST', url: () => value.endpoint, postData: () => value.body, headers: () => ({'csrf-token': value.token})};}
function state() {
  return {file: {dev: 1, ino: 2, mode: 0o100640, uid: 7, gid: 8, sha: hash('a')}, rows: {
    document: [['42', hash('a'), hash('b')]], ctl_document: [['77', 'demographic', '42', hash('c'), hash('d')]],
    patientLabRouting: [['3', hash('e'), hash('f')]], providerLabRouting: [['4', hash('a'), hash('b')], ['5', hash('c'), hash('d')]],
    queue_document_link: [['6', hash('e'), hash('f')], ['7', hash('e'), hash('f')], ['8', hash('e'), hash('f')]]},
  values: {document: [['Owned old', 'Lab']], providers: [['4', '999998', 'N'], ['5', '999997', 'N']], queues: [['6', '1', 'A'], ['7', '2', null], ['8', '3', 'I']]}};
}
function after(before, kind) {
  const result = clone(before);
  if (kind === 'metadata') {result.rows.document[0][1] = hash('9'); result.values.document = [['Owned revised', 'Consult']];}
  if (kind === 'queue') {result.rows.queue_document_link[0][1] = hash('9'); result.values.queues[0][2] = 'I';}
  if (kind === 'unlink') {result.rows.providerLabRouting[0][1] = hash('9'); result.values.providers[0][2] = 'X';}
  return result;
}
for (const kind of ['metadata', 'queue', 'unlink']) {
  test(kind + ' permits only its exact committed transition', () => {
    const before = state(); assert.deepEqual(validateTransition(kind, before, after(before, kind), intent(kind)), after(before, kind));
  });
  test(kind + ' rejects any file or immutable classification change', () => {
    const before = state(), changed = after(before, kind); changed.file.ino++;
    assert.throws(() => validateTransition(kind, before, changed, intent(kind)));
    changed.file = before.file; changed.rows.document[0][2] = hash('8');
    assert.throws(() => validateTransition(kind, before, changed, intent(kind)));
  });
  test(kind + ' rejects a new patient route even if it points at the same patient', () => {
    const before = state(), changed = after(before, kind); changed.rows.patientLabRouting.push(['99', hash('e'), hash('f')]);
    assert.throws(() => validateTransition(kind, before, changed, intent(kind)));
  });
  test(kind + ' validates exact frozen CSRF and identity', () => {
    const value = intent(kind); validateRequest(request(value), value);
    assert.throws(() => validateRequest({...request(value), headers: () => ({'csrf-token': 'other-session'})}, value));
    assert.throws(() => validateRequest({...request(value), method: () => 'GET'}, value));
    assert.throws(() => validateRequest({...request(value), postData: () => value.body + '&docid=99'}, value));
  });
}
test('unlink never permits another provider timestamp to change', () => {
  const before = state(), changed = after(before, 'unlink'); changed.rows.providerLabRouting[1][1] = hash('7');
  assert.throws(() => validateTransition('unlink', before, changed, intent('unlink')), /another provider timestamp/);
});
test('queue preserves NULL and already inactive links', () => {
  const before = state(), changed = after(before, 'queue'); changed.values.queues[1][2] = 'I';
  assert.throws(() => validateTransition('queue', before, changed, intent('queue')));
});
test('metadata permits header-only CSRF when form uses the existing page token', () => {
  const value = intent(), fields = new URLSearchParams(value.body); fields.delete('CSRF-TOKEN'); value.body = fields.toString();
  validateRequest(request(value), value);
  fields.set('doc_no', '99'); value.body = fields.toString(); assert.throws(() => validateRequest(request(value), value));
});
test('extra fields and duplicate identity cannot widen queue or unlink ownership', () => {
  for (const kind of ['queue', 'unlink']) {
    const value = intent(kind); value.body += '&documentId=99'; assert.throws(() => validateRequest(request(value), value));
  }
});
function response(data = {success: true, accepted: true, document: 42, patientId: '77'}, status = 200, type = 'application/json') {
  return {status: () => status, headers: () => ({'content-type': type}), body: async () => Buffer.from(JSON.stringify(data)), dispose: async () => {}};
}
test('acceptance rejects redirects, HTML, wrong document and contradictory flags', () => {
  for (const [res, body] of [[response({}, 302), {}], [response({}, 200, 'text/html'), {}],
    [response(), {success: true, accepted: true, document: 99, patientId: '77'}],
    [response(), {success: true, accepted: false, document: 42, patientId: '77'}]]) {
    assert.throws(() => confirmedResponse(res, body, intent()));
  }
});
function transportFixture(mode = 'real', overrides = {}) {
  const events = [], value = intent(), before = state(), changed = after(before, 'metadata'); let captures = 0;
  const route = {request: () => request(value), fetch: async options => {events.push(['fetch', options]); return response();},
    fulfill: async () => events.push(['fulfill']), abort: async () => events.push(['abort'])};
  const fixture = {assertUnchanged() {events.push(['unchanged']);}, begin() {events.push(['begin']);}, transportFailed() {events.push(['uncertain']);}};
  const transport = createMetadataTransport({intent: value, fixture, capture: () => captures++ ? changed : before,
    accept: async () => events.push(['durable']), mode, ...overrides});
  return {events, route, transport};
}
for (const mode of ['rejected500', 'html200', 'lostBeforeForward']) test(mode + ' is proven pre-forward and never marks accepted or uncertain', async () => {
  const {events, route, transport} = transportFixture(mode); await transport.route(route); await transport.drain();
  assert.deepEqual(transport.counts(), {calls: 1, forwarded: 0, settled: true});
  assert(!events.some(([name]) => ['fetch', 'begin', 'durable', 'uncertain'].includes(name)));
});
test('real request begins ownership before forwarding and persists receipt before exposing success', async () => {
  const {events, route, transport} = transportFixture(); await transport.route(route); await transport.drain();
  assert.deepEqual(events.map(([name]) => name), ['unchanged', 'begin', 'fetch', 'durable', 'fulfill']);
  assert.deepEqual(events.find(([name]) => name === 'fetch')[1], {maxRedirects: 0, maxRetries: 0, timeout: 90000});
});
test('transport loss after forwarding stays uncertain and never invokes the acceptance adapter', async () => {
  const {events, route, transport} = transportFixture(); route.fetch = async () => {throw new Error('lost reply');};
  await assert.rejects(transport.route(route)); await assert.rejects(transport.drain());
  assert.equal(transport.counts().forwarded, 1); assert(events.some(([name]) => name === 'uncertain')); assert(!events.some(([name]) => name === 'durable'));
});
test('failure to durably accept a real write remains uncertain', async () => {
  const {events, route, transport} = transportFixture('real', {accept: async () => {throw new Error('fsync failed');}});
  await assert.rejects(transport.route(route)); await assert.rejects(transport.drain());
  assert(events.some(([name]) => name === 'uncertain')); assert(!events.some(([name]) => name === 'fulfill'));
});
test('duplicate dispatch is sticky even after the original request drains', async () => {
  const {route, transport} = transportFixture(); let release;
  route.fetch = () => new Promise(resolve => {release = () => resolve(response());});
  const first = transport.route(route); await assert.rejects(transport.route(route), /Duplicate/);
  release(); await first; await assert.rejects(transport.drain(), /Duplicate/);
  assert.equal(transport.counts().forwarded, 1);
});
test('invalid local request never begins ownership or forwards', async () => {
  const {route, transport, events} = transportFixture(); route.request = () => ({...request(intent()), method: () => 'GET'});
  await assert.rejects(transport.route(route)); await assert.rejects(transport.drain());
  assert(!events.some(([name]) => ['begin', 'fetch', 'uncertain'].includes(name)));
});
test('missing durable acceptance adapter fails before any request', () => {
  assert.throws(() => createMetadataTransport({intent: intent(), fixture: {}, capture() {}}), /adapter/);
});
const columns = {document: ['document_no', 'docdesc', 'doctype', 'updatedatetime', 'observationdate'], ctl_document: ['module_id', 'module', 'document_no'],
  patientLabRouting: ['id'], providerLabRouting: ['id', 'provider_no', 'status', 'timestamp'], queue_document_link: ['id', 'queue_id', 'status'],
  demographic: ['demographic_no'], casemgmt_note_link: ['table_id'], formVTForm: ['24UAValue'], external: ['document_id']};
function schemaSql(engine = 'InnoDB') {return {rows(query) {
  return query.includes('KEY_COLUMN_USAGE') ? [] : Object.entries(columns).flatMap(([table, fields]) => fields.map(column => [table, column, table === 'external' ? engine : 'InnoDB']));
}};}
test('metadata preflight permits safely quoted legacy digit-leading columns', () => {
  assert.deepEqual(preflightMetadataSchema(schemaSql()).schemas.get('formVTForm').columns, ['24UAValue']);
});
test('nontransactional external reference tables block fixture preparation', () => {
  assert.throws(() => preflightMetadataSchema(schemaSql('MyISAM')), /transactional/);
});
test('unsafe discovered schema identifiers fail before they can enter SQL', () => {
  assert.throws(() => preflightMetadataSchema({rows: () => [['unsafe`table', 'id', 'InnoDB']]}), /Unsafe/);
});

test('failure to deliver an already durable acceptance retains transport uncertainty', async () => {
  const {events, route, transport} = transportFixture(); route.fulfill = async () => {throw new Error('browser disconnected');};
  await assert.rejects(transport.route(route)); await assert.rejects(transport.drain());
  assert.equal(transport.durablyAccepted(), true);
  assert(events.some(([name]) => name === 'durable')); assert(events.some(([name]) => name === 'uncertain'));
});
test('response disposal failure after delivery still fails closed', async () => {
  const {events, route, transport} = transportFixture();
  route.fetch = async () => ({...response(), dispose: async () => {throw new Error('response disposal failed');}});
  await assert.rejects(transport.route(route)); await assert.rejects(transport.drain());
  assert(events.some(([name]) => name === 'fulfill')); assert(events.some(([name]) => name === 'uncertain'));
});
test('durable acceptance is visible before delivery completes so a legitimate queue phase can follow', async () => {
  const {route, transport} = transportFixture(); let release;
  route.fulfill = () => new Promise(resolve => {assert.equal(transport.durablyAccepted(), true); release = resolve;});
  const pending = transport.route(route);
  while (!release) await new Promise(resolve => setImmediate(resolve));
  assert.equal(transport.counts().settled, false); release(); await pending; await transport.drain();
});

test('only a genuine forwarded Request can make a later browser failure uncertain', async () => {
  for (const mode of ['real', 'rejected500']) {
    const {route, transport} = transportFixture(mode); const ownedRequest = route.request(); route.request = () => ownedRequest;
    await transport.route(route); await transport.drain();
    assert.equal(transport.ownsForwarded(ownedRequest), mode === 'real');
    assert.equal(transport.ownsForwarded({}), false);
  }
});

test('an Aria demographic reference blocks before the workflow can create its patient', () => {
  const sql = schemaSql();
  assert.throws(() => preflightMetadataSchema({rows(query) {
    const rows = sql.rows(query);
    return query.includes('KEY_COLUMN_USAGE') ? rows : [...rows, ['formRourke2009', 'demographic_no', 'Aria']];
  }}), /transactional/);
});

test('real DOM freeze normalizes legacy identity and saved=false exactly as production submission', async () => {
  const vm = require('node:vm');
  const {freezeMetadataIntent} = require('./lib/document-metadata-check');
  const fields = [['method', 'documentUpdate'], ['doc_no', '42'], ['demog', '77'],
    ['documentDescription', 'Owned revised'], ['docType', 'Consult'], ['saved', 'true']];
  const form = {querySelector: () => null};
  const page = {evaluate: async (fn, id) => vm.runInNewContext('(' + fn.toString() + ')(id)', {
    id, URLSearchParams, window: {document: {getElementById: () => form, querySelector: () => ({value: 'owned-token'})}},
    FormData: class {constructor() {this.fields = fields;} [Symbol.iterator]() {return this.fields[Symbol.iterator]();}},
  })};
  const frozen = await freezeMetadataIntent(page, {document: '42', patient: '77', provider: '999998'}, 'https://owned.invalid/ManageDocument');
  const body = new URLSearchParams(frozen.body);
  assert.equal(body.get('method'), 'documentUpdateAjax'); assert.equal(body.get('documentId'), '42');
  assert.equal(body.get('saved'), 'false'); assert.equal(frozen.provider, '999998'); assert(Object.isFrozen(frozen));
  fields.find(([name]) => name === 'doc_no')[1] = '43';
  await assert.rejects(freezeMetadataIntent(page, {document: '42', patient: '77', provider: '999998'}, frozen.endpoint), /identity differs/);
});
