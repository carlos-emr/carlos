/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

/*
 * The xss-poison fixtures are global (providers, lookup items, document types) and other checks' forms echo
 * them back into POSTs the WAF refuses, so their cleanup has to hold even for a run that was killed. These
 * tests pin the three guarantees of the Seeder in scripts/lib/xss-poison-helpers.js without a database.
 */
const LEDGER_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'xss-poison-ledger-test-'));
process.env.XSS_POISON_LEDGER_DIR = LEDGER_DIR;
const { Seeder, payload, TIERS, classifyText, norm } = require('./lib/xss-poison-helpers');

test.after(() => fs.rmSync(LEDGER_DIR, { recursive: true, force: true }));

/*
 * A stand-in for the harness SQL runner: keys in `live` exist until a DELETE names them. Keys are recorded
 * the way Seeder.insert names them (`<key column>=<id>`, the column taken from KEY_COLUMNS by table), and a
 * query names a key only when the whole key appears, so `id=5` is not matched by `id=50` or `tickler_id=5`.
 */
const KEY_COLUMNS = { demographic: 'demographic_no', tickler: 'tickler_no', queue: 'id' };
const names = (query, key) => new RegExp(`(^|[^\\w])${key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![\\w])`).test(query);

function fakeSql(rows = []) {
  const live = new Set(rows);
  const log = [];
  let next = 500;
  return {
    log,
    live,
    value(query) {
      log.push(query);
      if (/LAST_INSERT_ID\(\)/.test(query)) {
        const table = /^INSERT INTO (\w+)/.exec(query)[1];
        assert.ok(KEY_COLUMNS[table], `the fake runner has no key column for ${table}`);
        live.add(`${KEY_COLUMNS[table]}=${++next}`);
        return String(next);
      }
      if (/^SELECT COUNT\(\*\)/.test(query)) return [...live].some(key => names(query, key)) ? '1' : '0';
      return '';
    },
    rows(query) { log.push(query); return []; },
    execute(query) {
      log.push(query);
      if (/^DELETE/.test(query)) for (const key of [...live]) if (names(query, key)) live.delete(key);
    },
  };
}

test('the fake runner matches whole column-qualified keys only', () => {
  const sql = fakeSql(['id=5', 'id=50']);
  sql.execute('DELETE FROM queue WHERE tickler_id=5');
  sql.execute('DELETE FROM queue WHERE id=50');
  assert.deepEqual([...sql.live], ['id=5']);
});

function newSeeder(sql, marker) {
  const cleanups = [];
  const seed = new Seeder(sql, fn => cleanups.push(fn), marker);
  return { seed, cleanup: () => cleanups.forEach(fn => fn()) };
}

test('every payload tier carries the sweep signature and fits the column it was cut for', () => {
  for (const tier of TIERS) assert.match(tier(7), /<i data-xp=/);
  assert.ok(payload(901, 20).length <= 20);
  assert.match(payload(901, 20), /<i data-xp=901>/);
});

test('a payload shown literally, or cut short by the page, is encoded; an altered one is mangled', () => {
  const shown = text => norm(text).toLowerCase();
  const full = payload(7);
  assert.equal(classifyText(shown(`Name: ${full} next`), 7), 'ok');
  assert.equal(classifyText(shown('nothing seeded here'), 7), null);
  // A list column cuts the value short: what is visible matches the literal as far as it goes.
  assert.equal(classifyText(shown(`${full.slice(0, 26)}...`), 7), 'ok');
  assert.equal(classifyText(shown(`${full.slice(0, 22)}`), 7), 'ok');
  // An entity decoded twice (&amp; shown as &), shown re-encoded, or a dropped backslash is a defect.
  assert.equal(classifyText(shown(full.replace('&amp;', '&')), 7), 'mangled');
  assert.equal(classifyText(shown(full.replace('&amp;', '&amp;amp;')), 7), 'mangled');
  assert.equal(classifyText(shown(full.replace('\\back', 'back')), 7), 'mangled');
  // The marker is there but its head is not the literal one (the opening bracket was stripped).
  assert.equal(classifyText(shown(full.replace('<i data-xp', 'i data-xp')), 7), 'mangled');
  // Field 7 is not field 70.
  assert.equal(classifyText(shown(payload(70)), 7), null);
});

test('a natural-key insert refuses a key that already exists, so cleanup can never delete it', () => {
  const sql = fakeSql(["provider_no='700001'"]);
  const { seed, cleanup } = newSeeder(sql, 'FAKE-PWnatural');
  assert.throws(() => seed.insert('provider', { provider_no: '700001' }, { where: "provider_no='700001'" }), /already taken/);
  cleanup();
  assert.ok(sql.live.has("provider_no='700001'"), 'the pre-existing row must survive the failed run');
  assert.ok(!sql.log.some(q => /^DELETE FROM provider/.test(q)));
});

test('cleanup deletes exactly the recorded keys, children first, asserts them gone and drops the ledger', () => {
  const sql = fakeSql();
  const { seed, cleanup } = newSeeder(sql, 'FAKE-PWcleanup');
  const parent = seed.insert('demographic', { last_name: payload(1, 30) }, { key: 'demographic_no' });
  seed.track('log', `demographic_no=${parent}`);
  seed.insert('tickler', { demographic_no: Number(parent) }, { key: 'tickler_no' });
  const ledger = path.join(LEDGER_DIR, 'FAKE-PWcleanup.json');
  assert.equal(JSON.parse(fs.readFileSync(ledger, 'utf8')).entries.length, 3);
  cleanup();
  const deletes = sql.log.filter(q => /^DELETE FROM (demographic|log|tickler) /.test(q));
  assert.deepEqual(deletes.map(q => q.split(' ')[2]), ['tickler', 'log', 'demographic']);
  assert.equal(sql.live.size, 0, `cleanup left ${[...sql.live].join(', ')}`);
  assert.equal(fs.existsSync(ledger), false);
});

test('the next run removes a killed run\'s rows by key before it seeds, and leaves a live run alone', async () => {
  const dead = path.join(LEDGER_DIR, 'FAKE-PWdead.json');
  fs.writeFileSync(dead, JSON.stringify({ pid: 2 ** 22 + 4321, entries: [{ table: 'queue', where: 'id=77' }] }));
  // A live xss-poison run: its ledger must be kept, and while it lives no payload sweep may run.
  const other = spawn(process.execPath, ['-e', 'setTimeout(() => {}, 30000)', 'xss-poison-live']);
  try {
    await new Promise(resolve => setTimeout(resolve, 200));
    const alive = path.join(LEDGER_DIR, 'FAKE-PWalive.json');
    fs.writeFileSync(alive, JSON.stringify({ pid: other.pid, entries: [{ table: 'queue', where: 'id=88' }] }));
    const sql = fakeSql(['id=77', 'id=88']);
    const { seed, cleanup } = newSeeder(sql, 'FAKE-PWnext');
    seed.insert('queue', { name: payload(2, 40) }, { key: 'id' });
    assert.ok(!sql.live.has('id=77'), 'the dead run\'s row was not recovered');
    assert.equal(fs.existsSync(dead), false);
    assert.ok(sql.live.has('id=88') && fs.existsSync(alive), 'a live run\'s fixture was touched');
    assert.ok(!sql.log.some(q => /information_schema/.test(q)), 'the payload sweep ran while another run was alive');
    cleanup();
    fs.rmSync(alive, { force: true });
  } finally { other.kill(); }
});

test('an aborted request is excused only when its document provably went away', () => {
  const { Findings } = require('./lib/xss-poison-helpers');
  const failure = (url, navigatedAway) => {
    const entry = { url, resourceType: 'font', errorText: 'net::ERR_ABORTED' };
    if (navigatedAway !== undefined) Object.defineProperty(entry, 'navigatedAway', { value: () => navigatedAway });
    return entry;
  };
  const recorder = { pageErrors: [], consoleIssues: [], badResponses: [], unexpectedDialogs: [], requestFailures: [] };
  const f = new Findings(recorder);
  const since = f.mark();
  recorder.requestFailures.push(
    failure('https://host/carlos/font.woff2', true),
    failure('https://host/carlos/api/poll', false),
    failure('https://host/carlos/js/app.js'),
  );
  f.drain('surface', since);
  assert.deepEqual(f.items.map(i => `${i.kind} ${i.detail}`), ['REQUEST-FAILED /carlos/api/poll', 'REQUEST-FAILED /carlos/js/app.js']);
  assert.deepEqual(f.observed.map(o => o.text), ['request abandoned by navigation: /carlos/font.woff2']);
});
