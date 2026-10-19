/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const h = require('./playwright-harness');

/** A syntactically valid Ontario health number (10 digits, mod-10 check digit) that no patient holds. */
function freshOntarioHin(sql) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const base = [9, 8, 7, 6, ...Array.from({ length: 5 }, () => Math.floor(Math.random() * 10))];
    let sum = 0;
    base.forEach((digit, index) => {
      if (index % 2 === 0) { const doubled = digit * 2; sum += doubled > 9 ? doubled - 9 : doubled; } else sum += digit;
    });
    const hin = base.join('') + String((10 - (sum % 10)) % 10);
    if (hin.length === 10 && sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${h.sqlString(hin)}`) === '0') return hin;
  }
  throw new Error('No unused synthetic health number found');
}

/**
 * Remove every patient row a UI add created for this run's marker surname and prove nothing is left.
 * Audit-log rows (`log`) are append-only evidence and are not deleted.
 * Tables found holding rows for the removed ids that this list does not know are reported by name.
 */
function removeMarkedPatients(sql, marker) {
  const ids = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(marker)}`).map(row => row[0]);
  ids.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Marked patient id is not numeric'));
  if (!ids.length) return;
  const list = ids.join(',');
  const archives = sql.rows(`SELECT id FROM demographicArchive WHERE demographic_no IN (${list})`).map(row => row[0]);
  if (archives.length) sql.execute(`DELETE FROM demographicExtArchive WHERE archiveId IN (${archives.join(',')})`);
  for (const table of ['demographicExtArchive', 'demographicExt', 'demographiccust', 'demographicArchive']) {
    sql.execute(`DELETE FROM ${table} WHERE demographic_no IN (${list})`);
  }
  sql.execute(`DELETE FROM admission WHERE client_id IN (${list})`);
  sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${list}) AND last_name=${h.sqlString(marker)}`);
  const holders = sql.rows(`SELECT table_name FROM information_schema.columns WHERE table_schema=DATABASE()
    AND column_name='demographic_no' AND table_name NOT IN ('log','demographic')`).map(row => row[0]);
  const leftovers = holders.filter(table => sql.value(`SELECT COUNT(*) FROM \`${table}\` WHERE demographic_no IN (${list})`) !== '0');
  h.assert(leftovers.length === 0, `Marked patient rows remain in: ${leftovers.join(', ')}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${h.sqlString(marker)}`) === '0', 'Marked patients were not removed');
}

module.exports = { freshOntarioHin, removeMarkedPatients };
