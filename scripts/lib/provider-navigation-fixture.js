/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const {assert, sqlString: q} = require('./playwright-harness');

// Native Inboxhub GETs initialize the queue; the schedule provider picker posts
// the consolidated preference form, including these absent-checkbox defaults.
const DEFAULTS = Object.freeze({
  incoming_document_default_queue: '1',
  'schedule.week_view_weekends': 'false',
  rx_show_patient_dob: 'no', cpp_single_line: 'no', lab_ack_comment: 'no',
  edoc_browser_in_document_report: 'no', edoc_browser_in_master_file: 'no',
  encounterWindowMaximize: 'no', dashboard_share: 'no',
  prevention_sso_warning: 'false', prevention_ispa_warning: 'false', prevention_non_ispa_warning: 'false',
});
const AFFECTED = new Set(['application-health', 'document-upload', 'schedule-links']);
const quote = name => {
  assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(name), 'Invalid navigation fixture column');
  return `\`${name}\``;
};
const encode = value => value === null ? 'NULL' : `UNHEX('${value}')`;
const hexText = value => value === null ? null : Buffer.from(value, 'hex').toString('utf8');
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

function validateProperties(before, after, columns, provider, defaults) {
  const index = name => columns.indexOf(name);
  for (const name of ['id', 'provider_no', 'name', 'value']) assert(index(name) >= 0, 'Incomplete property snapshot');
  const baseline = new Map(before.map(row => [row[index('id')], row]));
  const current = new Map(after.map(row => [row[index('id')], row]));
  assert(current.size === after.length && baseline.size === before.length, 'Duplicate navigation property identity');
  for (const [id] of baseline) assert(current.has(id), 'A preexisting navigation property disappeared; refusing cleanup');
  for (const row of after) {
    const original = baseline.get(row[index('id')]);
    if (original && same(original, row)) continue;
    const key = hexText(row[index('name')]);
    assert(hexText(row[index('provider_no')]) === provider && Object.hasOwn(defaults, key)
      && hexText(row[index('value')]) === defaults[key], 'Unexpected navigation property change; refusing cleanup');
    if (original) for (const name of columns.filter(name => name !== 'value')) {
      assert(row[index(name)] === original[index(name)], 'Navigation property ownership changed; refusing cleanup');
    }
  }
}

function validatePreference(snapshot) {
  const {table, before, after, columns} = snapshot;
  if (table !== 'ProviderPreference') {
    assert(same(before, after) || after.length === 0, 'Unexpected provider form collection change; refusing cleanup');
    return;
  }
  assert(before.length === 1 && after.length === 1, 'Provider preference identity changed; refusing cleanup');
  const defaults = {colourTemplate: 'deepblue', printQrCodeOnPrescriptions: '0', defaultCaisiPmm: 'disabled', defaultDoNotDeleteBilling: '0'};
  for (let i = 0; i < columns.length; i++) {
    if (before[0][i] === after[0][i]) continue;
    const name = columns[i], value = hexText(after[0][i]);
    const expected = name === 'myGroupNo' ? /^\d+$/.test(value || '')
      : name === 'lastUpdated' ? /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?$/.test(value || '')
        : Object.hasOwn(defaults, name) && value === defaults[name];
    assert(expected, 'Unexpected provider preference change; refusing cleanup');
  }
}

/**
 * Used only by the already database-backed residue-audited suite, on its exclusive
 * disposable install. Standalone browser checks retain their original requirements.
 * A snapshot belongs to one check and one authenticated provider, never a key-only
 * global DELETE. The final SQL locks and verifies all captured rows before restoring.
 */
function providerNavigationFixture(sql, testUser, checkName) {
  if (!AFFECTED.has(checkName)) return null;
  const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${q(testUser)}`);
  assert(/^\d+$/.test(provider), 'Navigation cleanup needs one authenticated provider');
  const defaults = checkName === 'schedule-links' ? DEFAULTS : {incoming_document_default_queue: '1'};
  const specifications = [{table: 'property', owner: 'provider_no',
    suffix: ` AND name IN (${Object.keys(defaults).map(q).join(',')})`}];
  if (checkName === 'schedule-links') {
    // ProviderPreferencesUIBean changes myGroupNo/other scalars and clears the
    // two submitted form collections; the quick-link collection is untouched.
    for (const table of ['ProviderPreference', 'ProviderPreferenceAppointmentScreenForm', 'ProviderPreferenceAppointmentScreenEForm']) {
      specifications.push({table, owner: 'providerNo', suffix: ''});
    }
  }
  const snapshots = specifications.map(spec => {
    const columns = sql.rows(`SHOW COLUMNS FROM ${quote(spec.table)}`).map(row => row[0]);
    assert(columns.length && columns.includes(spec.owner), 'Incomplete navigation preference schema');
    const expressions = columns.map(name => `HEX(CAST(${quote(name)} AS BINARY))`);
    const expression = `JSON_ARRAY(${expressions.join(',')})`;
    const predicate = `BINARY ${quote(spec.owner)}=BINARY ${q(provider)}${spec.suffix}`;
    const read = () => sql.rows(`SELECT ${expression} FROM ${quote(spec.table)} WHERE ${predicate}`)
      .map(([json]) => {
        const row = JSON.parse(json);
        assert(Array.isArray(row) && row.length === columns.length
          && row.every(value => value === null || typeof value === 'string' && /^[0-9A-F]*$/i.test(value)),
        'Invalid navigation preference snapshot');
        return row;
      }).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
    return {...spec, columns, expressions, predicate, read, before: read()};
  });
  for (const snapshot of snapshots.filter(snapshot => snapshot.table === 'ProviderPreference')) {
    assert(snapshot.before.length === 1, 'Navigation cleanup needs an existing provider preference');
  }
  let restored = false;
  return {
    cleanup() {
      if (restored) return;
      const changes = snapshots.map(snapshot => ({...snapshot, after: snapshot.read()}));
      validateProperties(changes[0].before, changes[0].after, changes[0].columns, provider, defaults);
      for (const snapshot of changes.slice(1)) validatePreference(snapshot);
      const changed = changes.filter(snapshot => !same(snapshot.before, snapshot.after));
      if (changed.length) {
        const statements = ['START TRANSACTION',
          'CREATE TEMPORARY TABLE carlos_navigation_restore_guard (ok INT NOT NULL CHECK(ok=1))'];
        // All guards precede all mutations. A failed CHECK stops mysql and its
        // connection rollback preserves the rows; no partially restored state commits.
        for (const snapshot of changed) {
          const {table, columns, predicate, expressions, after} = snapshot;
          statements.push(`UPDATE ${quote(table)} SET ${quote(columns[0])}=${quote(columns[0])} WHERE ${predicate}`);
          const count = `(SELECT COUNT(*) FROM ${quote(table)} WHERE ${predicate})=${after.length}`;
          const rowsMatch = after.map(row => `(SELECT COUNT(*) FROM ${quote(table)} WHERE ${predicate}
            AND ${row.map((value, i) => `${expressions[i]}<=>${value === null ? 'NULL' : q(value)}`).join(' AND ')})=${after.filter(other => same(other, row)).length}`);
          statements.push(`INSERT INTO carlos_navigation_restore_guard VALUES(IF(${[count, ...rowsMatch].join(' AND ')},1,0))`);
        }
        for (const snapshot of [...changed].sort((a, b) => Number(a.table === 'ProviderPreference') - Number(b.table === 'ProviderPreference'))) {
          const {table, columns, predicate, before, after} = snapshot;
          if (table === 'ProviderPreference' && before.length) {
            assert(before.length === 1, 'Duplicate provider preference identity');
            statements.push(`UPDATE ${quote(table)} SET ${columns.map((name, i) => `${quote(name)}=${encode(before[0][i])}`).join(',')} WHERE ${predicate}`);
          } else if (table === 'property') {
            const identity = columns.indexOf('id');
            const valueIndex = columns.indexOf('value');
            for (const row of after) {
              const original = before.find(other => other[identity] === row[identity]);
              if (!original) statements.push(`DELETE FROM property WHERE ${predicate} AND id=${encode(row[identity])}`);
              else if (!same(original, row)) statements.push(`UPDATE property SET value=${encode(original[valueIndex])} WHERE ${predicate} AND id=${encode(row[identity])}`);
            }
          } else {
            statements.push(`DELETE FROM ${quote(table)} WHERE ${predicate}`);
            for (const row of before) statements.push(`INSERT INTO ${quote(table)} (${columns.map(quote).join(',')}) VALUES (${row.map(encode).join(',')})`);
          }
        }
        statements.push('COMMIT');
        sql.execute(statements.join(';'));
      }
      for (const snapshot of snapshots) assert(same(snapshot.before, snapshot.read()), 'Navigation preferences were not restored');
      restored = true;
    },
  };
}

module.exports = {providerNavigationFixture, validateProperties, validatePreference, DEFAULTS};
