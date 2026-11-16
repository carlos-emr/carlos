/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Keeps the reminder-eligibility table of prevention-reminder-eligibility-matrix honest. Its expectations come from
 * prevention.drl's documentation, so every quoted sentence must still be in the rule file, and every message must
 * still be raised by the rule it is attributed to: a rule renamed, reworded or redocumented fails here, in CI, rather
 * than as a confusing browser failure on a packaged install.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const {
  AGE_RULES, RULE_FILE, birthDateSql, boundaryAges, describeAge, documentedToFire,
} = require('./lib/prevention-eligibility-rules');

const drl = fs.readFileSync(path.join(__dirname, '..', RULE_FILE), 'utf8');

/** The text of one rule, from its `rule "name"` line to its `end`. */
function ruleBody(name) {
  const start = drl.indexOf(`rule "${name}"\n`);
  assert.notEqual(start, -1, `prevention.drl has no rule "${name}"`);
  const end = drl.indexOf('\nend', start);
  assert.notEqual(end, -1, `rule "${name}" has no end`);
  return drl.slice(start, end);
}

for (const row of AGE_RULES) {
  test(`the documentation quoted for ${row.rule} is still in prevention.drl`, () => {
    for (const sentence of row.documented) assert.ok(drl.includes(sentence), `prevention.drl no longer says "${sentence}"`);
  });

  test(`${row.rule} still raises "${row.message}"`, () => {
    const body = ruleBody(row.rule);
    const raised = [...body.matchAll(/prev\.add(Warning|Reminder)\((?:"[^"]*",\s*)?"([^"]*)"\)/g)]
      .map(match => ({ kind: match[1].toLowerCase(), message: match[2].trim() }));
    const match = raised.find(entry => entry.message === row.message);
    assert.ok(match, `rule "${row.rule}" raises ${JSON.stringify(raised)}, not "${row.message}"`);
    if (row.kind !== null) assert.equal(match.kind, row.kind, `rule "${row.rule}" raises its message as a ${match.kind}`);
  });

  test(`${row.rule} has a well-formed documented range`, () => {
    assert.ok([null, 'F', 'M'].includes(row.sex));
    assert.ok([null, 'warning', 'reminder'].includes(row.kind));
    assert.ok(Number.isInteger(row.minAge) && row.minAge >= 0);
    assert.ok(row.maxAge === null || (Number.isInteger(row.maxAge) && row.maxAge >= row.minAge));
  });
}

test('the boundary ages straddle every documented lower and upper limit', () => {
  const points = boundaryAges();
  const labels = points.map(point => `${point.years}${point.lastDay ? '-last' : '-first'}`);
  for (const row of AGE_RULES) {
    assert.ok(labels.includes(`${row.minAge - 1}-last`) && labels.includes(`${row.minAge}-first`), `no points around ${row.minAge}`);
    if (row.maxAge !== null) {
      assert.ok(labels.includes(`${row.maxAge}-last`) && labels.includes(`${row.maxAge + 1}-first`), `no points around ${row.maxAge}`);
    }
  }
  assert.equal(new Set(labels).size, labels.length, 'a boundary point is listed twice');
});

test('a documented range includes its limits and excludes the ages beyond them', () => {
  const mam = AGE_RULES.find(row => row.rule === 'MAM 3');
  assert.equal(documentedToFire(mam, 'F', 39), false);
  assert.equal(documentedToFire(mam, 'F', 40), true);
  assert.equal(documentedToFire(mam, 'F', 74), true);
  assert.equal(documentedToFire(mam, 'F', 75), false);
  assert.equal(documentedToFire(mam, 'M', 60), false);
  const bmd = AGE_RULES.find(row => row.rule === 'Bone Mineral Density');
  assert.equal(documentedToFire(bmd, 'M', 120), true);
});

test('birth dates are computed from the application\'s date, either side of the birthday', () => {
  assert.equal(birthDateSql('2026-10-29', { years: 40, lastDay: false }), "DATE_SUB('2026-10-29', INTERVAL 40 YEAR)");
  assert.equal(birthDateSql('2026-10-29', { years: 39, lastDay: true }),
    "DATE_ADD(DATE_SUB('2026-10-29', INTERVAL 40 YEAR), INTERVAL 1 DAY)");
  assert.equal(describeAge('F', { years: 39, lastDay: true }), 'F 39 (turns 40 tomorrow)');
  assert.throws(() => birthDateSql("2026-10-29'; DROP TABLE x; --", { years: 1, lastDay: false }), /yyyy-mm-dd/);
  assert.throws(() => birthDateSql('2026-10-29', { years: -1, lastDay: false }), /whole number/);
});
