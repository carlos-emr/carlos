/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
// Per-workflow synthetic physician: no edits to an existing provider, site or login.
// Use only with the loopback-guarded SQL session supplied by runWorkflow.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const assert = (ok, message) => { if (!ok) throw new Error(message); };
const sha = raw => crypto.createHash('sha256').update(raw).digest('hex');
const TABLES = ['provider', 'site', 'providersite'];
const REFERENCE_NAMES = ['provider_no', 'providerno', 'provider_id', 'providerid', 'apptprovider_no',
  'asstprovider_no', 'creator', 'lastupdateuser', 'user_no', 'assignedto'];
// Exact incoming single-column keys in the shipped/current V1 provider schema.
// Constraint names are captured from the actual database and pinned in the
// ledger, rather than assuming a Hibernate-generated name is portable.
const PROVIDER_FOREIGN_KEYS = [['ClientLink', 'linkProviderNo'], ['ClientLink', 'unlinkProviderNo'],
  ['DigitalSignature', 'providerNo'], ['document_review', 'provider_no'], ['HnrDataValidation', 'validatorProviderNo'],
  ['program_client_restriction', 'provider_no'], ['program_provider', 'provider_no'], ['provider_facility', 'provider_no']];
const ident = value => { assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(value), 'Unsupported database identifier'); return '`' + value + '`'; };

function authority(owner) {
  assert(owner && /^FAKE-PW[a-f0-9]{16}$/.test(owner.marker)
    && /^\d{1,6}$/.test(owner.operator), 'Invalid synthetic fixture owner');
  return owner;
}
function sameBinding(a, b) {
  assert(a.marker === b.marker && a.operator === b.operator, 'Fixture binding changed');
}
function columns(sql, table) {
  const names = sql.rows('SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION', [table]).map(row => row[0]);
  assert(names.length && new Set(names).size === names.length, 'Missing or ambiguous fixture schema');
  names.forEach(ident); return names;
}
function capture(sql, table, names, where = '', params = []) {
  const rows = sql.rows(`SELECT JSON_ARRAY(${names.map(ident).join(',')}) FROM ${ident(table)}${where ? ' WHERE ' + where : ''}`, params).map(row => {
    assert(row.length === 1 && typeof row[0] === 'string' && Array.isArray(JSON.parse(row[0])), 'Invalid private table snapshot');
    return row[0];
  }).sort();
  return { count: rows.length, sha256: sha(JSON.stringify(rows)), rows };
}
function catalog(sql, foreignKeys) {
  const rows = sql.rows(`SELECT c.TABLE_NAME,c.COLUMN_NAME FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
    WHERE c.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE'
    AND LOWER(c.COLUMN_NAME) IN (${REFERENCE_NAMES.map(() => '?').join(',')})
    ORDER BY c.TABLE_NAME,c.ORDINAL_POSITION`, REFERENCE_NAMES);
  assert(rows.length && rows.some(([t, c]) => t === 'provider' && c === 'provider_no')
    && rows.some(([t, c]) => t === 'providersite' && c === 'provider_no'), 'Missing provider reference inventory');
  rows.forEach(row => { assert(row.length === 2, 'Invalid reference inventory'); row.forEach(ident); });
  const unique = new Map(rows.map(([table, column]) => [table + '\0' + column, [table, column]]));
  for (const row of foreignKeys) unique.set(row[1] + '\0' + row[5], [row[1], row[5]]);
  return [...unique.values()].sort((a, b) => a[0].localeCompare(b[0], 'en') || a[1].localeCompare(b[1], 'en'));
}
function references(sql, refs, number, ownSite) {
  return refs.map(([table, column]) => {
    let suffix = ''; const args = [number];
    if (table === 'provider' && column === 'provider_no') suffix = ' AND provider_no<>?';
    if (suffix) args.push(number);
    if (table === 'providersite' && column === 'provider_no' && ownSite !== undefined) {
      suffix = ' AND site_id<>?'; args.push(ownSite);
    }
    const count = sql.value(`SELECT COUNT(*) FROM ${ident(table)} WHERE ${ident(column)}=?${suffix}`, args);
    assert(/^\d+$/.test(count), 'Invalid provider reference count');
    return [table, column, Number(count)];
  });
}
function noReferences(sql, refs, number, ownSite) {
  assert(references(sql, refs, number, ownSite).every(row => row[2] === 0),
    'Provider identity has external references; preserving all rows');
}
function unchangedSchema(sql, state) {
  assert(sql.value('SELECT DATABASE()') === state.database, 'Fixture database changed');
  for (const t of TABLES) assert(JSON.stringify(columns(sql, t)) === JSON.stringify(state.columns[t]), 'Fixture schema changed');
  const foreignKeys = reviewedForeignKeys(sql);
  assert(JSON.stringify(foreignKeys) === JSON.stringify(state.foreignKeys), 'Provider foreign-key schema changed');
  assert(JSON.stringify(catalog(sql, foreignKeys)) === JSON.stringify(state.references), 'Provider reference schema changed');
}
function reviewedForeignKeys(sql) {
  const database = sql.value('SELECT DATABASE()'); ident(database);
  assert(sql.value("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE IN ('provider','providersite')") === '0',
    'Provider or membership triggers require separate review');
  const rows = sql.rows(`SELECT r.CONSTRAINT_NAME,r.TABLE_NAME,r.REFERENCED_TABLE_NAME,r.UPDATE_RULE,r.DELETE_RULE,
      k.COLUMN_NAME,k.REFERENCED_COLUMN_NAME,k.ORDINAL_POSITION,k.REFERENCED_TABLE_SCHEMA
    FROM information_schema.REFERENTIAL_CONSTRAINTS r JOIN information_schema.KEY_COLUMN_USAGE k
      ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME AND k.TABLE_NAME=r.TABLE_NAME
    WHERE r.CONSTRAINT_SCHEMA=DATABASE() AND (r.TABLE_NAME IN ('provider','providersite')
      OR r.REFERENCED_TABLE_NAME IN ('provider','providersite'))
    ORDER BY r.TABLE_NAME,r.CONSTRAINT_NAME,k.ORDINAL_POSITION`);
  assert(rows.length === PROVIDER_FOREIGN_KEYS.length, 'Unreviewed provider foreign-key inventory');
  const allowed = new Set(PROVIDER_FOREIGN_KEYS.map(([t, c]) => t + '\0' + c));
  const seen = new Set();
  for (const row of rows) {
    assert(row.length === 9, 'Invalid provider foreign-key metadata');
    const [constraint, table, target, update, remove, column, targetColumn, ordinal, targetSchema] = row;
    [constraint, table, target, column, targetColumn, targetSchema].forEach(ident);
    const key = table + '\0' + column;
    assert(allowed.has(key) && !seen.has(key) && target === 'provider' && targetColumn === 'provider_no'
      && String(ordinal) === '1' && targetSchema === database
      && ['RESTRICT', 'NO ACTION'].includes(update) && ['RESTRICT', 'NO ACTION'].includes(remove),
    'Unreviewed provider foreign-key effect or column');
    seen.add(key);
  }
  return rows;
}
function preserveForeign(sql, state) {
  for (const table of TABLES) {
    const where = table === 'provider' ? 'provider_no<>?' : table === 'providersite' ? 'NOT(provider_no=? AND site_id=?)' : '';
    const params = table === 'provider' ? [state.providerNo] : table === 'providersite' ? [state.providerNo, state.siteId] : [];
    assert(capture(sql, table, state.columns[table], where, params).sha256 === state.before[table].sha256,
      'Pre-existing provider/site rows changed; preserving fixture for ownership review');
  }
}
function providerRows(sql, state) { return capture(sql, 'provider', state.columns.provider, 'provider_no=?', [state.providerNo]).rows; }
function ownedProvider(sql, state, allowAbsent = false) {
  const rows = providerRows(sql, state);
  assert((allowAbsent && rows.length === 0) || (rows.length === 1 && rows[0] === state.expectedProviderRow),
    'Owned billing provider is missing or changed; refusing mutation');
  return rows.length === 1;
}
function memberships(sql, state) {
  const rows = sql.rows('SELECT site_id FROM providersite WHERE provider_no=? ORDER BY site_id', [state.providerNo]);
  assert(rows.every(row => row.length === 1) && rows.length <= 1 && (!rows.length || String(rows[0][0]) === state.siteId),
    'Owned provider acquired an unknown membership; refusing mutation');
  return rows.length;
}
function prepare(sql, owner, save, { randomInt = crypto.randomInt } = {}) {
  authority(owner);
  const database = sql.value('SELECT DATABASE()'); ident(database);
  const foreignKeys = reviewedForeignKeys(sql);
  assert(sql.value("SELECT COUNT(*) FROM provider WHERE provider_no=? AND status='1'", [owner.operator]) === '1', 'Operator provider must be active and unique');
  const sites = sql.rows(`SELECT s.site_id,s.name FROM site s JOIN providersite p ON p.site_id=s.site_id
    WHERE p.provider_no=? AND s.status<>0 ORDER BY s.site_id`, [owner.operator]);
  assert(sites.length, 'No existing active operator-owned site; do not invent a clinic or alter operator membership');
  const [site, name] = sites[0];
  assert(/^[1-9]\d*$/.test(String(site)) && typeof name === 'string' && name.trim() && name !== 'none', 'Invalid native operator site');
  assert(sql.value('SELECT COUNT(*) FROM site WHERE name=? AND status<>0', [name]) === '1', 'Selected clinic name is ambiguous');
  const schema = Object.fromEntries(TABLES.map(t => [t, columns(sql, t)]));
  assert(JSON.stringify(schema.providersite) === JSON.stringify(['provider_no', 'site_id']),
    'Only the exact shipped two-column membership schema is supported');
  const refs = catalog(sql, foreignKeys); let number, ohip;
  for (let i = 0; i < 50; i++) {
    const candidate = String(randomInt(970000, 980000));
    if (sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=?', [candidate]) === '0'
      && references(sql, refs, candidate).every(row => row[2] === 0)) { number = candidate; break; }
  }
  assert(number, 'No wholly unused synthetic provider identity');
  for (let i = 0; i < 50; i++) {
    const candidate = String(randomInt(960000, 970000));
    if (sql.value('SELECT COUNT(*) FROM provider WHERE ohip_no=?', [candidate]) === '0') { ohip = candidate; break; }
  }
  assert(ohip, 'No unused synthetic OHIP number');
  const before = Object.fromEntries(TABLES.map(t => [t, capture(sql, t, schema[t])]));
  const authored = { provider_no: number, last_name: owner.marker, first_name: 'BillingPrerequisite',
    provider_type: 'doctor', specialty: 'GP', status: '1', ohip_no: ohip, lastUpdateUser: owner.operator,
    lastUpdateDate: new Date().toISOString().slice(0, 19).replace('T', ' ') };
  for (const c of Object.keys(authored)) assert(schema.provider.includes(c), 'Required provider column absent');
  const args = []; const expressions = schema.provider.map(c => {
    if (Object.hasOwn(authored, c)) { args.push(authored[c]); return '?'; }
    return ident(c);
  });
  const projection = expressions.join(','); args.push(owner.operator);
  const expected = sql.rows(`SELECT JSON_ARRAY(${projection}) FROM provider WHERE provider_no=?`, args);
  assert(expected.length === 1 && expected[0].length === 1, 'Cannot prepare exact complete provider row');
  const state = { schemaVersion: 1, status: 'PREPARED_BEFORE_INSERT', authority: owner, database, providerNo: number,
    ohipNo: ohip, siteId: String(site), siteName: name, columns: schema, references: refs, foreignKeys, before,
    expectedProviderRow: expected[0][0], expectedProviderSha256: sha(expected[0][0]), createdAt: new Date().toISOString() };
  save(state); // durable exclusive ownership ledger exists before the first INSERT.
  sql.execute(`INSERT INTO provider (${schema.provider.map(ident).join(',')}) SELECT ${projection} FROM provider WHERE provider_no=?`, args);
  ownedProvider(sql, state);
  state.status = 'PROVIDER_CREATED_BEFORE_MEMBERSHIP'; save(state);
  sql.execute('INSERT INTO providersite (provider_no,site_id) VALUES (?,?)', [number, String(site)]);
  state.status = 'ACTIVE'; save(state);
  verify(sql, owner, state); return state;
}
function verify(sql, owner, state) {
  authority(owner); sameBinding(owner, state.authority); unchangedSchema(sql, state);
  assert(state.status === 'ACTIVE', 'Fixture is not active');
  preserveForeign(sql, state); ownedProvider(sql, state);
  assert(memberships(sql, state) === 1, 'Owned membership absent');
  noReferences(sql, state.references, state.providerNo, state.siteId);
  assert(sql.value(`SELECT COUNT(*) FROM provider p JOIN providersite ps ON ps.provider_no=p.provider_no
    JOIN site s ON s.site_id=ps.site_id JOIN providersite op ON op.site_id=s.site_id
    WHERE p.provider_no=? AND p.status='1' AND p.ohip_no=? AND s.site_id=? AND s.status<>0 AND op.provider_no=?`,
  [state.providerNo, state.ohipNo, state.siteId, owner.operator]) === '1', 'Owned physician is no longer natively eligible');
  return { status: 'OWNED_PREREQUISITE_VERIFIED', providerNo: state.providerNo, siteId: state.siteId,
    allPreexistingProviderSiteRowsPreserved: true, actualBrowserExecuted: false };
}
function cleanup(sql, owner, state, save) {
  authority(owner); sameBinding(owner, state.authority); unchangedSchema(sql, state);
  assert(['PREPARED_BEFORE_INSERT', 'PROVIDER_CREATED_BEFORE_MEMBERSHIP', 'ACTIVE', 'CLEANUP_STARTED'].includes(state.status), 'Unexpected cleanup lifecycle');
  preserveForeign(sql, state); const exists = ownedProvider(sql, state, true);
  const count = memberships(sql, state);
  assert(exists || count === 0, 'Membership without its exact owned parent; preserving evidence');
  noReferences(sql, state.references, state.providerNo, state.siteId);
  state.status = 'CLEANUP_STARTED'; save(state);
  if (count) {
    sql.execute('DELETE FROM providersite WHERE provider_no=? AND site_id=?', [state.providerNo, state.siteId]);
    assert(memberships(sql, state) === 0, 'Exact owned membership removal failed');
  }
  if (exists) {
    ownedProvider(sql, state); // no parent deletion if child cleanup or full ownership verification failed.
    sql.execute(`DELETE FROM provider WHERE provider_no=? AND SHA2(JSON_ARRAY(${state.columns.provider.map(ident).join(',')}),256)=?`,
      [state.providerNo, state.expectedProviderSha256]);
    assert(providerRows(sql, state).length === 0, 'Exact owned provider removal failed');
  }
  for (const t of TABLES) assert(capture(sql, t, state.columns[t]).sha256 === state.before[t].sha256, 'Final pre-existing table preservation failed');
  noReferences(sql, state.references, state.providerNo);
  state.status = 'CLEANED'; state.cleanedAt = new Date().toISOString(); save(state);
  return { status: 'OWNED_PREREQUISITE_REMOVED', providerNo: state.providerNo, siteId: state.siteId,
    exactOriginalProviderSiteTablesRestored: true, actualBrowserExecuted: false };
}
function privateStore(filename, create) {
  const target = path.resolve(filename), parent = path.dirname(target);
  const info = fs.lstatSync(parent);
  assert(info.isDirectory() && !info.isSymbolicLink() && info.uid === process.getuid()
    && fs.realpathSync(parent) === parent && !(info.mode & 0o022), 'Private ownership ledger directory required');
  let first = create;
  return value => {
    if (!first) {
      const old = fs.lstatSync(target);
      assert(old.isFile() && !old.isSymbolicLink() && (old.mode & 0o777) === 0o600 && old.uid === process.getuid(), 'Invalid private ledger inode');
    }
    const temp = target + '.pending-' + crypto.randomBytes(6).toString('hex');
    const fd = fs.openSync(first ? target : temp, 'wx', 0o600);
    try { fs.writeFileSync(fd, JSON.stringify(value, null, 2) + '\n'); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
    if (!first) fs.renameSync(temp, target);
    const dir = fs.openSync(parent, 'r'); try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
    first = false;
  };
}

/**
 * Register the parent cleanup before any INSERT. Dependent claim callbacks run
 * first in workflow-session's LIFO finalizer. Its continue-on-error behavior must
 * never permit parent deletion after an unconfirmed dependent cleanup.
 */
function createOwnedBillingPhysician(session, { ledgerDirectory, randomInt } = {}) {
  const { sql, marker, provider: operator } = session;
  const owner = authority({ marker, operator: String(operator) });
  assert(sql && typeof session.cleanup === 'function' && session.context,
    'A guarded workflow SQL/browser session is required');
  const browser = session.context.browser();
  assert(browser && typeof browser.isConnected === 'function', 'Workflow browser identity is required');
  const parent = ledgerDirectory || (process.env.RESULT_JSON ? path.dirname(path.resolve(process.env.RESULT_JSON)) : os.tmpdir());
  // A fresh private child retains the durable intent even if an INSERT's reply
  // is lost. No pre-existing file or ownership namespace is adopted.
  const directory = fs.mkdtempSync(path.join(parent, 'billing-physician-'));
  fs.chmodSync(directory, 0o700);
  const ledgerPath = path.join(directory, 'ownership.json');
  const durable = privateStore(ledgerPath, true);
  let state;
  const dependents = { registered: 0, completed: 0, failed: false };
  const save = value => { durable(value); state = value; };
  const drained = () => assert(browser.isConnected() === false,
    'Owned billing fixture retained: workflow browser close is unconfirmed');
  session.cleanup(() => {
    if (!state || state.status === 'CLEANED') return;
    drained();
    assert(!dependents.failed && dependents.completed === dependents.registered,
      'Owned billing physician retained: dependent cleanup is unconfirmed');
    cleanup(sql, owner, state, save);
  });
  prepare(sql, owner, save, { randomInt });
  const dependentSession = Object.create(session);
  dependentSession.cleanup = callback => {
    assert(typeof callback === 'function', 'A dependent cleanup callback is required');
    dependents.registered++;
    session.cleanup(async () => {
      try { drained(); await callback(); dependents.completed++; }
      catch (error) { dependents.failed = true; throw error; }
    });
  };
  return {
    providerNo: state.providerNo, ohipNo: state.ohipNo, siteId: state.siteId, siteName: state.siteName,
    ledgerPath, dependentSession,
    async selectClinic(page) {
      const sites = page.locator('select[name="site"]');
      const count = await sites.count();
      assert(count <= 1, 'Native billing clinic control is ambiguous');
      if (!count) return;
      const offered = await sites.locator('option').evaluateAll(options => options.map(option => ({
        value: option.value, disabled: option.disabled || !!option.closest('optgroup:disabled'),
      })));
      assert(offered.filter(option => option.value === state.siteName && !option.disabled).length === 1,
        'Owned physician clinic is not offered by the native billing form');
      await sites.selectOption(state.siteName);
      assert(await sites.inputValue() === state.siteName, 'Native clinic selection did not persist');
    },
    assertContext(context) {
      assert(context && context.providerNo === state.providerNo && context.ohipNo === state.ohipNo
        && (!context.multisites || context.clinic === state.siteName),
      'Native billing context does not identify the owned physician and clinic');
    },
  };
}
module.exports = { createOwnedBillingPhysician, prepare, verify, cleanup, privateStore, capture, sha,
  constants: { TABLES, REFERENCE_NAMES, PROVIDER_FOREIGN_KEYS } };
