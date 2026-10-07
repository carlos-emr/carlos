/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {execFileSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const input = path.join(root, '.devcontainer/db/scripts/development.sql');

// Read literal INSERT tuples without treating quoted commas, parentheses or escapes as syntax.
function literalRows(sql, table, headerCount, columnCount, key, positionalColumns) {
  const patterns = {
    demographic: /^INSERT(?: IGNORE)? INTO `demographic` \(([^)]+)\) VALUES\s*/gm,
    emailLog: /^INSERT(?: IGNORE)? INTO `emailLog` \(([^)]+)\) VALUES\s*/gm,
    pharmacyInfo: /^INSERT(?: IGNORE)? INTO `pharmacyInfo` \(([^)]+)\) VALUES\s*/gm,
    prescription: /^INSERT(?: IGNORE)? INTO `prescription` VALUES\s*/gm,
    drugs: /^INSERT(?: IGNORE)? INTO `drugs` VALUES\s*/gm,
  };
  const headers = [...sql.matchAll(patterns[table])];
  assert.equal(headers.length, headerCount, 'source layouts must declare their own columns');
  const rows = new Map();
  for (const header of headers) {
    const columns = positionalColumns || header[1].split(',').map(value => value.trim().replaceAll('`', ''));
    assert.equal(new Set(columns).size, columnCount);
    let quote = false, escaped = false, depth = 0, start = 0, fields = [];
    for (let i = header.index + header[0].length; i < sql.length; i++) {
      const ch = sql[i];
      if (quote) {
        if (escaped) escaped = false;
        else if (ch === '\\') escaped = true;
        else if (ch === "'") quote = false;
        continue;
      }
      if (ch === "'") quote = true;
      else if (ch === '(') { depth++; start = i + 1; fields = []; }
      else if (ch === ',' && depth === 1) { fields.push(sql.slice(start, i).trim()); start = i + 1; }
      else if (ch === ')') {
        assert.equal(depth--, 1);
        fields.push(sql.slice(start, i).trim());
        assert.equal(fields.length, columns.length);
        const row = Object.fromEntries(columns.map((column, index) => [column, fields[index]]));
        assert.ok(!rows.has(row[key]));
        rows.set(row[key], row);
      } else if (ch === ';') { assert.equal(depth, 0); break; }
    }
  }
  return rows;
}

function verifyPrescriptions(sql) {
  const schema = fs.readFileSync(path.join(root, 'database/mysql/migration/common/V1__baseline_schema.sql'), 'utf8');
  const tables = {};
  for (const [table, key, count] of [['prescription', 'script_no', 10], ['drugs', 'drugid', 61]]) {
    const start = schema.indexOf(`CREATE TABLE \`${table}\` (`);
    assert.ok(start >= 0);
    const end = schema.indexOf('\n) ENGINE=', start);
    assert.ok(end > start);
    const columns = [...schema.slice(start, end).matchAll(/^  `([^`]+)`/gm)].map(match => match[1]);
    tables[table] = literalRows(sql, table, 1, count, key, columns);
  }
  const {prescription, drugs} = tables;
  assert.equal(drugs.size, 63, 'keep all original demo medication rows');
  assert.deepEqual([...drugs.keys()].map(Number).sort((a, b) => a - b),
    Array.from({length: 63}, (_, index) => index + 1), 'keep the original demo medication IDs');
  for (const row of prescription.values()) {
    assert.ok([...drugs.values()].some(drug => drug.script_no === row.script_no
      && drug.demographic_no === row.demographic_no),
    `demo prescription ${row.script_no} must have drug rows for its own patient`);
  }
  assert.equal(prescription.size, 27, 'keep every usable demo prescription');
  const latest = Math.max(...[...prescription.values()]
    .filter(row => row.demographic_no === '1').map(row => Number(row.script_no)));
  assert.equal(latest, 45, 'a plain MAX(script_no) must select a usable reprint fixture');
  assert.deepEqual([...prescription.keys()].map(Number).sort((a, b) => a - b),
    [17, 19, 20, 21, 22, 23, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34,
      35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45],
    'keep every usable demo prescription ID');
}

test('every demo prescription has medication rows for its own patient', () => {
  verifyPrescriptions(fs.readFileSync(input, 'utf8'));
});

function verify(sql) {
  const rows = literalRows(sql, 'demographic', 2, 58, 'demographic_no');
  assert.equal(rows.size, 3000);
  for (const row of rows.values()) {
    assert.ok(row.country_of_origin === 'NULL' || row.country_of_origin.length <= 6,
      'country codes must fit the schema without truncation');
    for (const column of ['genderId', 'pronounId', 'consentToUseEmailForCare']) {
      assert.match(row[column], /^(?:NULL|-?\d+|'-?\d+')$/, `${column} must not silently coerce text`);
    }
  }
  assert.equal(rows.get('1').genderId, 'NULL');
  assert.equal(rows.get('1').pref_name, "''");
  assert.equal(rows.get('21').pref_name, "'Oscar'");
  assert.equal(rows.get('21').genderId, "'0'");
  assert.equal(rows.get('21').pronoun, 'NULL');
  assert.equal(rows.get('21').pronounId, "'0'");
  assert.equal(rows.get('21').gender, "'M'");
  assert.equal(rows.get('2020').country_of_origin, "'CA'");
}

test('demo patient rows preserve preferred names and gender fields without integer coercion', () => {
  verify(fs.readFileSync(input, 'utf8'));
});

test('both Debian demo artifacts preserve patient layouts and usable prescriptions', () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-demo-layout-'));
  try {
    for (const province of ['on', 'bc']) {
      const output = path.join(temporary, `${province}.sql`);
      execFileSync('bash', ['scripts/build-demo-additive.sh', input, province, output], {cwd: root, stdio: 'pipe'});
      execFileSync('bash', ['scripts/check-demo-additive.sh', output, province], {cwd: root, stdio: 'pipe'});
      const sql = fs.readFileSync(output, 'utf8');
      verify(sql);
      verifyPrescriptions(sql);
    }
  } finally {
    fs.rmSync(temporary, {recursive: true, force: true});
  }
});

// The imported snapshot keeps provider attribution after additionalParams; the baseline does not.
test('demo email-log attribution is not confused with additional parameters', () => {
  const rows = literalRows(fs.readFileSync(input, 'utf8'), 'emailLog', 1, 20, 'id');
  assert.equal(rows.size, 184);
  for (const row of rows.values()) assert.match(row.providerNo, /^(?:NULL|-?\d+|'-?\d+')$/);
  assert.ok([...rows.values()].some(row => row.providerNo === '999998'));
  assert.ok([...rows.values()].some(row => row.additionalParams.length > 8));
});

test('demo pharmacies keep names, fax numbers, dates and stable UIDs in their own columns', () => {
  const rows = literalRows(fs.readFileSync(input, 'utf8'), 'pharmacyInfo', 1, 15, 'recordID');
  assert.ok(rows.size > 0);
  for (const row of rows.values()) {
    assert.match(row.uid, /^\d+$/);
    assert.match(row.addDate, /^'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'$/);
  }
  assert.equal(rows.get('1').uid, '1');
  assert.equal(rows.get('1').name, "'NAME'");
  assert.equal(rows.get('1').fax, "'5555555555'");
  assert.equal(rows.get('1').addDate, "'2024-04-25 21:03:16'");
});

test('legacy Rich Text Letter seed explicitly supplies its required display flag', () => {
  const seed = fs.readFileSync(path.join(root, 'database/mysql/updates/update-2012-07-12.sql'), 'utf8');
  assert.match(seed, /^INSERT INTO `eform` \(`showLatestFormOnly`,/);
  assert.match(seed, /VALUES\s*\(0,'Rich Text Letter'/);
});
