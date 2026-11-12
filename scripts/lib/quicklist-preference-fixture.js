/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./playwright-harness');
const q = h.sqlString;

function storedQuickListNames(sql) {
  const names = sql.rows('SELECT quickListName FROM quickList GROUP BY quickListName ORDER BY quickListName').map(row => row[0]);
  h.assert(names.length && names.every(name => typeof name === 'string' && name.length),
    'Registry controls need stored quicklist names');
  return names;
}

/** Restore only observed last-used changes for the explicitly supplied provider/name keys. */
function trackQuickListPreferences(s, names) {
  const { sql } = s;
  const provider = String(s.provider);
  h.assert(/^[1-9]\d*$/.test(provider) && Array.isArray(names) && names.length
    && names.every(name => typeof name === 'string' && name.length), 'Invalid quicklist preference fixture identity');
  // Match dxQuickListItemsHandler's ten-Java-character storage key.
  const keys = [...new Set(names.map(name => name.slice(0, 10)))];
  const scope = `providerNo=${q(provider)} AND quickListName IN (${keys.map(q).join(',')})`;
  const rows = () => sql.rows(`SELECT id,providerNo,quickListName,
    COALESCE(DATE_FORMAT(lastUsed,'%Y-%m-%d %H:%i:%s.%f'),''),lastUsed IS NULL
    FROM quickListUser WHERE ${scope} ORDER BY id`);
  const maximum = sql.value('SELECT COALESCE(MAX(id),0) FROM quickListUser');
  h.assert(/^[0-9]+$/.test(maximum), 'Invalid quicklist preference identity boundary');
  const before = rows();
  let observed = before;
  const valid = values => values.every(row => /^[1-9]\d*$/.test(row[0]) && row[1] === provider
    && keys.includes(row[2]) && ['0', '1'].includes(row[4]))
    && new Set(values.map(row => row[0])).size === values.length;
  h.assert(valid(before), 'Quicklist snapshot contains foreign identities or keys');
  s.cleanup(() => {
    const current = rows();
    // Validate the complete scope before executing even one mutation.
    h.assert(JSON.stringify(current) === JSON.stringify(observed) && valid(observed),
      'Quicklist preferences drifted or contain foreign identities; retaining them');
    h.assert(before.every(old => observed.some(row => row[0] === old[0] && row[2] === old[2])),
      'A preexisting quicklist preference disappeared or changed keys; retaining it');
    for (const key of keys) {
      const prior = before.filter(row => row[2] === key);
      const after = observed.filter(row => row[2] === key);
      h.assert(prior.length ? after.length === prior.length : after.length <= 1,
        'Unexpected quicklist preference row count; retaining it');
    }
    h.assert(observed.every(row => before.some(old => old[0] === row[0]) || BigInt(row[0]) > BigInt(maximum)),
      'A foreign preexisting preference ID entered the fixture scope; retaining it');
    const stamp = row => row[4] === '1' ? 'NULL' : q(row[3]);
    const added = [];
    for (const row of observed) {
      const original = before.find(old => old[0] === row[0]);
      if (original && original[3] === row[3] && original[4] === row[4]) continue;
      const exact = `id=${row[0]} AND providerNo=${q(provider)} AND quickListName=${q(row[2])}
        AND lastUsed <=> ${stamp(row)}`;
      if (original) sql.execute(`UPDATE quickListUser SET lastUsed=${stamp(original)} WHERE ${exact}`);
      else { sql.execute(`DELETE FROM quickListUser WHERE ${exact}`); added.push(row[0]); }
    }
    h.assert(JSON.stringify(rows()) === JSON.stringify(before)
      && (!added.length || sql.value(`SELECT COUNT(*) FROM quickListUser WHERE id IN (${added.join(',')})`) === '0'),
    'Registry controls did not restore their exact quicklist preference snapshot');
  });
  return { observe() { observed = rows(); } };
}

module.exports = { storedQuickListNames, trackQuickListPreferences };
