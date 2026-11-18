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
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { privateStore, sha } = require('./owned-billing-physician');
const assert = (ok, message) => { if (!ok) throw Error(message); };
const COLUMNS = ['id', 'providerNo', 'quickListName', 'lastUsed'];
const PROJECTION = 'JSON_ARRAY(`id`,`providerNo`,`quickListName`,`lastUsed`)';
const stamp = value => typeof value === 'string' && /^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/.test(value);
function schema(sql) {
  assert(JSON.stringify(sql.rows('SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION', ['quickListUser']).map(row => row[0])) === JSON.stringify(COLUMNS),
    'Unsupported quick-list preference schema');
  assert(sql.value("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='quickListUser'") === '0', 'Quick-list preference triggers require separate review');
  assert(sql.value("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND (TABLE_NAME='quickListUser' OR REFERENCED_TABLE_NAME='quickListUser')") === '0', 'Quick-list preference foreign-key effects require separate review');
}
function snapshot(sql) {
  const rows = sql.rows(`SELECT ${PROJECTION} FROM quickListUser ORDER BY id`).map(row => {
    assert(row.length === 1 && typeof row[0] === 'string', 'Invalid preference snapshot');
    const fields = JSON.parse(row[0]);
    assert(fields.length === 4 && Number.isSafeInteger(fields[0]) && fields[0] > 0
      && typeof fields[1] === 'string' && typeof fields[2] === 'string'
      && (fields[3] === null || stamp(fields[3])), 'Invalid preference row');
    return {raw: row[0], fields};
  });
  assert(new Set(rows.map(row => row.fields[0])).size === rows.length, 'Ambiguous preference identities');
  return rows;
}
/** Snapshot before UI; register after physician cleanup and before list cleanup. */
function createQuickListPreferenceFixture(session, ownedNames, { ledgerDirectory, nativeOnly = false } = {}) {
  const { sql, provider, marker } = session;
  assert(/^\d{1,6}$/.test(String(provider)) && /^FAKE-PW[a-f0-9]{16}$/.test(marker)
    && Array.isArray(ownedNames) && ((nativeOnly === false && ownedNames.length === 2)
      || (nativeOnly === true && ownedNames.length === 0)) && new Set(ownedNames).size === ownedNames.length
    && ownedNames.every(name => typeof name === 'string' && name.length > 0 && name.length <= 10),
  'Invalid quick-list preference fixture owner');
  schema(sql);
  // Match the database's native name collation. Do not adopt orphan preferences
  // or an existing global list that happens to collide with a generated name.
  for (const table of ownedNames.length ? ['quickList', 'quickListUser'] : []) {
    assert(sql.value(`SELECT COUNT(*) FROM ${table} WHERE quickListName IN (?,?)`, ownedNames) === '0',
      'Generated quick-list name already exists; preserving prior rows');
  }
  const before = snapshot(sql);
  const startedAt = sql.value('SELECT CURRENT_TIMESTAMP');
  assert(stamp(startedAt), 'Preference write-window clock is invalid');
  const knownNames = sql.rows('SELECT DISTINCT quickListName FROM quickList ORDER BY quickListName').map(row => {
    assert(row.length === 1 && typeof row[0] === 'string', 'Invalid native quick-list inventory');
    return row[0].slice(0, 10); // dxQuickListItemsHandler's exact storage contract.
  });
  const allowed = new Set([...knownNames, ...ownedNames]);
  if (!knownNames.length) allowed.add(''); // Native null fallback normalizes to empty.
  const browser = session.context.browser();
  assert(browser && typeof browser.isConnected === 'function', 'Workflow browser identity is required');
  const parent = ledgerDirectory || (process.env.RESULT_JSON ? path.dirname(path.resolve(process.env.RESULT_JSON)) : os.tmpdir());
  const directory = fs.mkdtempSync(path.join(parent, 'quick-list-preference-')); fs.chmodSync(directory, 0o700);
  const ledgerPath = path.join(directory, 'ownership.json'); const save = privateStore(ledgerPath, true);
  const state = {schemaVersion: 1, status: 'SNAPSHOT_BEFORE_UI', provider: String(provider), marker,
    startedAt, ownedNames, allowedNativeNames: [...allowed], before};
  session.cleanup(() => {
    assert(browser.isConnected() === false, 'Preference restore retained: workflow browser close is unconfirmed');
    schema(sql);
    const endedAt = sql.value('SELECT CURRENT_TIMESTAMP');
    assert(stamp(endedAt) && endedAt >= startedAt, 'Preference write-window clock changed');
    const current = snapshot(sql); const original = new Map(before.map(row => [row.fields[0], row]));
    const now = new Map(current.map(row => [row.fields[0], row]));
    const additions = [], updates = []; const seenNames = new Set();
    const maxBefore = Math.max(0, ...original.keys());
    const nativeStamp = value => stamp(value) && value >= startedAt && value <= endedAt;
    for (const row of before) {
      const changed = now.get(row.fields[0]);
      assert(changed, 'An original preference was removed; preserving evidence');
      if (row.raw === changed.raw) continue;
      assert(row.fields[1] === state.provider && changed.fields[1] === state.provider
        && changed.fields[2] === row.fields[2] && allowed.has(row.fields[2])
        && nativeStamp(changed.fields[3]), 'Unrelated or renamed preference changed; preserving evidence');
      updates.push({current: changed, original: row});
    }
    for (const row of current) {
      if (original.has(row.fields[0])) continue;
      assert(row.fields[0] > maxBefore && row.fields[1] === state.provider && allowed.has(row.fields[2])
        && nativeStamp(row.fields[3]) && !seenNames.has(row.fields[2])
        && !before.some(old => old.fields[1] === state.provider && old.fields[2] === row.fields[2]),
      'Unknown preference row appeared; preserving all rows');
      seenNames.add(row.fields[2]); additions.push(row);
    }
    // Validate every row before the first DELETE/UPDATE. Full tuples remain in
    // the private ledger; errors never print provider names or clinical values.
    state.status = 'EXACT_RESTORE_PREPARED'; state.endedAt = endedAt; state.additions = additions; state.updates = updates; save(state);
    for (const row of additions) {
      sql.execute(`DELETE FROM quickListUser WHERE id=? AND SHA2(${PROJECTION},256)=?`, [row.fields[0], sha(row.raw)]);
      assert(sql.value('SELECT COUNT(*) FROM quickListUser WHERE id=?', [row.fields[0]]) === '0', 'Exact preference removal failed');
    }
    for (const item of updates) {
      sql.execute(`UPDATE quickListUser SET lastUsed=? WHERE id=? AND SHA2(${PROJECTION},256)=?`,
        [item.original.fields[3], item.current.fields[0], sha(item.current.raw)]);
    }
    assert(JSON.stringify(snapshot(sql)) === JSON.stringify(before), 'Exact preference snapshot was not restored');
    state.status = 'RESTORED'; save(state);
  });
  save(state); // Durable exact baseline exists before the first native UI action.
  return {ledgerPath};
}
/** Native registry views select existing lists but create no owned list names. */
function createNativeQuickListPreferenceFixture(session, options = {}) {
  return createQuickListPreferenceFixture(session, [], {...options, nativeOnly: true});
}
module.exports = {createQuickListPreferenceFixture, createNativeQuickListPreferenceFixture};
