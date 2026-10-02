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
const { Seeder, payload, TIERS } = require('./lib/xss-poison-helpers');

test.after(() => fs.rmSync(LEDGER_DIR, { recursive: true, force: true }));

/** A stand-in for the harness SQL runner: keys in `rows` exist until a DELETE names them. */
function fakeSql(rows = []) {
  const live = new Set(rows);
  const log = [];
  let next = 500;
  return {
    log,
    live,
    value(query) {
      log.push(query);
      if (/LAST_INSERT_ID\(\)/.test(query)) { live.add(`id=${++next}`); return String(next); }
      if (/^SELECT COUNT\(\*\)/.test(query)) return [...live].some(key => query.includes(key)) ? '1' : '0';
      return '';
    },
    rows(query) { log.push(query); return []; },
    execute(query) {
      log.push(query);
      if (/^DELETE/.test(query)) for (const key of [...live]) if (query.includes(key)) live.delete(key);
    },
  };
}

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
