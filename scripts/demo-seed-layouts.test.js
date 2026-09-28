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
function literalRows(sql, table, headerCount, columnCount, key) {
  const patterns = {
    demographic: /^INSERT(?: IGNORE)? INTO `demographic` \(([^)]+)\) VALUES\s*/gm,
    emailLog: /^INSERT(?: IGNORE)? INTO `emailLog` \(([^)]+)\) VALUES\s*/gm,
    pharmacyInfo: /^INSERT(?: IGNORE)? INTO `pharmacyInfo` \(([^)]+)\) VALUES\s*/gm,
  };
  const headers = [...sql.matchAll(patterns[table])];
  assert.equal(headers.length, headerCount, 'source layouts must declare their own columns');
  const rows = new Map();
  for (const header of headers) {
    const columns = header[1].split(',').map(value => value.trim().replaceAll('`', ''));
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

test('both Debian demo artifacts preserve the explicit demographic layouts', () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-demo-layout-'));
  try {
    for (const province of ['on', 'bc']) {
      const output = path.join(temporary, `${province}.sql`);
      execFileSync('bash', ['scripts/build-demo-additive.sh', input, province, output], {cwd: root, stdio: 'pipe'});
      execFileSync('bash', ['scripts/check-demo-additive.sh', output, province], {cwd: root, stdio: 'pipe'});
      verify(fs.readFileSync(output, 'utf8'));
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
