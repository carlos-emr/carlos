/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {providerNavigationFixture, validateProperties, validatePreference, DEFAULTS} = require('./lib/provider-navigation-fixture');
const hex = text => text === null ? null : Buffer.from(String(text)).toString('hex').toUpperCase();
const columns = ['id', 'provider_no', 'name', 'value'];
const row = (id, key, value, provider = '999998') => [id, provider, key, value].map(hex);
const validate = (before, after) => validateProperties(before, after, columns, '999998', DEFAULTS);

test('preserves preexisting duplicate-key rows and NULL values while recognizing native checkbox writes', () => {
  const before = [row(3, 'cpp_single_line', null), row(4, 'cpp_single_line', 'yes')];
  assert.doesNotThrow(() => validate(before, [row(3, 'cpp_single_line', 'no'), before[1], row(5, 'incoming_document_default_queue', '1')]));
});

for (const [label, before, after] of [
  ['preexisting deletion', [row(3, 'cpp_single_line', 'yes')], []],
  ['changed provider', [row(3, 'cpp_single_line', 'yes')], [row(3, 'cpp_single_line', 'no', '77')]],
  ['changed key', [row(3, 'cpp_single_line', 'yes')], [row(3, 'lab_ack_comment', 'no')]],
  ['unexpected value', [], [row(3, 'cpp_single_line', 'yes')]],
  ['unrecognized key', [], [row(3, 'unrelated', 'no')]],
  ['duplicate identity', [], [row(3, 'cpp_single_line', 'no'), row(3, 'cpp_single_line', 'no')]],
]) test(`refuses all cleanup after ${label}`, () => assert.throws(() => validate(before, after), /refusing cleanup|Duplicate/));

function fixtureSql(name = 'schedule-links') {
  const schemas = {property: columns, ProviderPreference: ['providerNo', 'myGroupNo', 'lastUpdated'],
    ProviderPreferenceAppointmentScreenForm: ['providerNo', 'appointmentScreenForm'],
    ProviderPreferenceAppointmentScreenEForm: ['providerNo', 'appointmentScreenEForm', 'eFormName']};
  const baseline = {property: [row(3, 'cpp_single_line', null)],
    ProviderPreference: [['999998', 'original', '2026-01-02 03:04:05'].map(hex)],
    ProviderPreferenceAppointmentScreenForm: [['999998', 'existing form'].map(hex)],
    ProviderPreferenceAppointmentScreenEForm: [['999998', 17, "O'Neil"].map(hex)]};
  let current = structuredClone(baseline);
  const writes = [], reads = [];
  const sql = {
    value(query) { assert.match(query, /FROM security WHERE user_name='fixture'/); return '999998'; },
    rows(query) {
      reads.push(query);
      const table = /(?:FROM) `([A-Za-z]+)`/.exec(query)?.[1];
      if (query.startsWith('SHOW COLUMNS')) return schemas[table].map(column => [column]);
      assert.match(query, /WHERE BINARY `provider(?:_no|No)`=BINARY '999998'/);
      return current[table].map(row => [JSON.stringify(row)]);
    },
    execute(query) { writes.push(query); current = structuredClone(baseline); },
  };
  const fixture = providerNavigationFixture(sql, 'fixture', name);
  return {fixture, writes, reads, tamper() { current.ProviderPreference[0][0] = hex('77'); },
    addForm() { current.ProviderPreferenceAppointmentScreenForm.push(['999998', 'unexpected form'].map(hex)); }, change() {
    current.property = [row(3, 'cpp_single_line', 'no'), row(6, 'incoming_document_default_queue', '1')];
    current.ProviderPreference[0][1] = hex('123456');
    current.ProviderPreferenceAppointmentScreenForm = [];
    current.ProviderPreferenceAppointmentScreenEForm = [];
  }};
}

test('restores scalar preferences and cleared form collections with all ownership guards before mutation', () => {
  const f = fixtureSql();
  f.change(); f.fixture.cleanup();
  assert.equal(f.writes.length, 1);
  const statement = f.writes[0];
  assert.match(statement, /^START TRANSACTION;/);
  assert.match(statement, /CHECK\(ok=1\)/);
  assert.match(statement, /HEX\(CAST\(`id` AS BINARY\)\)/, 'numeric identities must round-trip as text bytes');
  assert.ok(statement.lastIndexOf('INSERT INTO carlos_navigation_restore_guard') < statement.indexOf('UPDATE property SET value='));
  assert.match(statement, /UPDATE property SET value=NULL/);
  assert.doesNotMatch(statement, /DELETE FROM property[^;]*id=UNHEX\('33'\)/, 'preexisting property IDs are updated, never deleted');
  assert.match(statement, /DELETE FROM property[^;]*id=UNHEX\('36'\)/);
  assert.match(statement, /INSERT INTO `ProviderPreferenceAppointmentScreenForm`/);
  assert.match(statement, /INSERT INTO `ProviderPreferenceAppointmentScreenEForm`/);
  assert.doesNotMatch(statement, /QuickLink/);
  assert.ok(statement.endsWith(';COMMIT'));
  f.fixture.cleanup();
  assert.equal(f.writes.length, 1, 'cleanup must be idempotent');
});

test('does not access the database for unrelated suite entries', () => {
  assert.equal(providerNavigationFixture({}, 'fixture', 'drug-search'), null);
});

test('does not mutate a matching baseline', () => {
  const f = fixtureSql(); f.fixture.cleanup(); assert.equal(f.writes.length, 0);
});

test('Inboxhub-only checks preserve only the queue property and do not inspect preferences', () => {
  const f = fixtureSql('application-health'); f.fixture.cleanup();
  assert.ok(f.reads.every(query => !query.includes('ProviderPreference')));
  assert.match(f.reads.find(query => query.startsWith('SELECT JSON_ARRAY')), /name IN \('incoming_document_default_queue'\)/);
});

test('refuses unrelated preference changes and preference identity replacement', () => {
  for (const [name, value] of [['defaultDxCode', 'changed'], ['providerNo', '77'], ['startHour', '12'], ['printQrCodeOnPrescriptions', '1']]) {
    assert.throws(() => validatePreference({table: 'ProviderPreference', columns: [name], before: [[hex('original')]], after: [[hex(value)]]}), /refusing cleanup/);
  }
});

test('refuses collection additions and replacements; native provider selection can only clear them', () => {
  for (const after of [[[hex('999998'), hex('unexpected')]], [[hex('999998'), hex('original')], [hex('999998'), hex('added')]]]) {
    assert.throws(() => validatePreference({table: 'ProviderPreferenceAppointmentScreenForm', before: [[hex('999998'), hex('original')]], after}), /refusing cleanup/);
  }
});

for (const method of ['tamper', 'addForm']) test(`performs no database mutation after ${method}`, () => {
  const f = fixtureSql(); f[method]();
  assert.throws(() => f.fixture.cleanup(), /refusing cleanup/);
  assert.equal(f.writes.length, 0);
});
