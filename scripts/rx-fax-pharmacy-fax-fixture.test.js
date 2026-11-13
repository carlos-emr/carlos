/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const { createPharmacyFaxFixture, LOCK_NAME } = require('./rx-fax-pharmacy-fax-fixture');

const PATIENT = '424242';

/**
 * An in-memory pharmacyInfo/demographicPharmacy pair that executes exactly the statements the
 * fixture issues, so the tests assert database state rather than SQL strings.
 */
function fakeDatabase({ pharmacies, links, identity = 'db-host\t3306\tcarlos' }) {
  const db = {
    pharmacies: new Map(pharmacies.map((p) => [String(p.id), { status: '1', addDate: '2024-01-31 19:41:06', ...p }])),
    links,
    statements: [],
    failUpdatesTo: null,
    commitThenFail: null,
    afterSnapshot: null,
  };
  const literal = (text) => (text === 'NULL' ? null : text.replace(/^'|'$/g, ''));
  db.sql = (query) => {
    db.statements.push(query);
    if (query.startsWith('SELECT @@hostname')) return identity;
    if (query.startsWith('SELECT p.recordId')) {
      const patient = query.match(/dp\.demographic_no = (\d+)/)[1];
      const rows = db.links
        .filter((l) => String(l.patient) === patient && l.status === '1')
        .map((l) => db.pharmacies.get(String(l.pharmacy)))
        .filter(Boolean)
        .sort((a, b) => a.id - b.id)
        .map((p) => `${p.id}\t${p.fax === null ? 1 : 0}\t${p.fax === null ? '' : Buffer.from(p.fax, 'utf8').toString('hex').toUpperCase()}`);
      const output = rows.join('\n');
      if (db.afterSnapshot) { db.afterSnapshot(db); db.afterSnapshot = null; }
      return output;
    }
    // Every statement must carry addDate = addDate: the column is ON UPDATE current_timestamp(), and
    // an UPDATE without it is rejected here exactly as the real column would silently be restamped.
    const update = query.match(/^UPDATE pharmacyInfo SET fax = (NULL|'[^']*'), addDate = addDate WHERE recordId = (\d+) AND (fax IS NULL|fax = BINARY '[^']*'|fax = '[^']*'); SELECT ROW_COUNT\(\);$/);
    if (update) {
      const [, value, id, condition] = update;
      if (db.failUpdatesTo !== null && literal(value) === db.failUpdatesTo) throw new Error('database query failed');
      const row = db.pharmacies.get(id);
      const expected = condition === 'fax IS NULL' ? null : literal(condition.replace(/^fax = (BINARY )?/, ''));
      if (!row || row.fax !== expected) return '0';
      row.fax = literal(value);
      if (db.commitThenFail === id) throw new Error('database query timed out');
      return '1';
    }
    throw new Error(`unexpected statement: ${query}`);
  };
  return db;
}

function demoDatabase() {
  return fakeDatabase({
    pharmacies: [
      { id: 3, fax: '4164000305' },        // the demo dataset's real-looking Toronto number
      { id: 6, fax: '416 400 0305 ' },     // trailing space a trim() would lose
      { id: 7, fax: null },
      { id: 8, fax: '' },
      { id: 9, fax: '9055550100', status: '0' }, // marked deleted but still linked: the Rx page lists it
      { id: 10, fax: '9055550111' },       // linked, but the link is inactive
      { id: 11, fax: '9055550122' },       // another patient's pharmacy
    ],
    links: [
      { patient: PATIENT, pharmacy: 3, status: '1' },
      { patient: PATIENT, pharmacy: 6, status: '1' },
      { patient: PATIENT, pharmacy: 6, status: '1' }, // linked twice: still one record
      { patient: PATIENT, pharmacy: 7, status: '1' },
      { patient: PATIENT, pharmacy: 8, status: '1' },
      { patient: PATIENT, pharmacy: 9, status: '1' },
      { patient: PATIENT, pharmacy: 10, status: '0' },
      { patient: '1', pharmacy: 11, status: '1' },
    ],
  });
}

function faxes(db) {
  return Object.fromEntries([...db.pharmacies].map(([id, p]) => [id, p.fax]));
}

function journalDir(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-fax-journal-test-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  return dir;
}

function fixture(db, dir, stagedFax = '5551234567', lockLog = []) {
  return createPharmacyFaxFixture({
    sql: db.sql,
    demographicNo: PATIENT,
    stagedFax,
    mysql: { host: 'localhost', user: 'root', password: '', database: 'carlos' },
    journalDir: dir,
    acquireLock: async (config, name) => {
      lockLog.push(`lock:${name}`);
      return async () => { lockLog.push('unlock'); };
    },
  });
}

test('replaces every listed destination, including existing numbers, and restores NULL, empty and exact values', async (t) => {
  const db = demoDatabase();
  const before = faxes(db);
  const lockLog = [];
  const run = fixture(db, journalDir(t), '5551234567', lockLog);
  await run.lock();
  assert.deepEqual(run.seed(), { active: 5, seeded: 5 });
  assert.deepEqual(faxes(db), { ...before, 3: '5551234567', 6: '5551234567', 7: '5551234567', 8: '5551234567', 9: '5551234567' });
  assert.deepEqual(run.restore(), { restored: 5, untouched: 0 });
  assert.deepEqual(faxes(db), before);
  assert.deepEqual([...db.pharmacies.values()].map((p) => p.addDate), [...db.pharmacies.values()].map(() => '2024-01-31 19:41:06'));
  await run.unlock();
  assert.deepEqual(lockLog, [`lock:${LOCK_NAME}`, 'unlock']);
});

test('covers a deleted pharmacy that is still linked, because the Rx page lists it, but not an inactive link', async (t) => {
  const db = demoDatabase();
  const run = fixture(db, journalDir(t));
  await run.lock();
  run.seed();
  assert.equal(db.pharmacies.get('9').fax, '5551234567');
  assert.equal(db.pharmacies.get('10').fax, '9055550111');
  assert.equal(db.pharmacies.get('11').fax, '9055550122');
  run.restore();
  assert.equal(db.pharmacies.get('9').fax, '9055550100');
});

test('refuses to seed before taking the shared lock', (t) => {
  const db = demoDatabase();
  assert.throws(() => fixture(db, journalDir(t)).seed(), /lock before seeding/);
  assert.equal(db.statements.length, 0);
});

for (const value of ["416'4000305", '416\\4000305', 'fax é', 'x'.repeat(33)]) {
  test(`refuses an unrestorable original (${JSON.stringify(value).slice(0, 14)}) before any write or journal`, async (t) => {
    const db = fakeDatabase({ pharmacies: [{ id: 3, fax: '4164000305' }, { id: 4, fax: value }],
      links: [{ patient: PATIENT, pharmacy: 3, status: '1' }, { patient: PATIENT, pharmacy: 4, status: '1' }] });
    const dir = journalDir(t);
    const run = fixture(db, dir);
    await run.lock();
    assert.throws(() => run.seed(), /unexpected shape/);
    assert.equal(db.statements.some((q) => q.startsWith('UPDATE')), false);
    assert.deepEqual(fs.readdirSync(dir), []);
  });
}

test('a second seed is refused until restore() has settled the first', async (t) => {
  const { fixtureErrorTag } = require('./rx-fax-pharmacy-fax-fixture');
  const db = demoDatabase();
  const before = faxes(db);
  const dir = journalDir(t);
  const run = fixture(db, dir);
  await run.lock();
  db.afterSnapshot = (state) => { state.pharmacies.get('7').fax = '9055550199'; };
  assert.throws(() => run.seed(), /changed while the fixture was being staged/);
  const journal = fs.readFileSync(path.join(dir, fs.readdirSync(dir)[0]), 'utf8');
  assert.throws(() => run.seed(), (e) => fixtureErrorTag(e) === ' (RX_FAX_FIXTURE_SEEDED)');
  assert.equal(fs.readFileSync(path.join(dir, fs.readdirSync(dir)[0]), 'utf8'), journal, 'the first snapshot is kept');
  run.restore();
  db.pharmacies.get('7').fax = null;
  assert.deepEqual(faxes(db), before);
  assert.deepEqual(run.seed(), { active: 5, seeded: 5 });
  run.restore();
  assert.deepEqual(faxes(db), before);
});

test('a value changed between snapshot and rewrite stops the run without overwriting the edit', async (t) => {
  const db = demoDatabase();
  db.afterSnapshot = (state) => { state.pharmacies.get('7').fax = '9055550199'; };
  const run = fixture(db, journalDir(t));
  await run.lock();
  assert.throws(() => run.seed(), /changed while the fixture was being staged/);
  assert.equal(db.pharmacies.get('7').fax, '9055550199');
  run.restore();
  assert.equal(db.pharmacies.get('3').fax, '4164000305');
  assert.equal(db.pharmacies.get('6').fax, '416 400 0305 ');
});

test('restore leaves a destination someone else changed during the run', async (t) => {
  const db = demoDatabase();
  const run = fixture(db, journalDir(t));
  await run.lock();
  run.seed();
  db.pharmacies.get('3').fax = '9055550144';
  assert.deepEqual(run.restore(), { restored: 4, untouched: 1 });
  assert.equal(db.pharmacies.get('3').fax, '9055550144');
});

test('every compare-and-swap is byte-exact, not collation-equal', async (t) => {
  const db = demoDatabase();
  const dir = journalDir(t);
  const crashed = fixture(db, dir, '5551111111');
  await crashed.lock();
  crashed.seed();
  await fixture(db, dir, '5552222222').lock();
  const run = fixture(db, dir, '5553333333');
  await run.lock();
  run.seed();
  run.restore();
  const swaps = db.statements.filter((q) => q.startsWith('UPDATE pharmacyInfo'));
  assert.ok(swaps.length > 0);
  for (const q of swaps) assert.match(q, /AND (fax IS NULL|fax = BINARY ')/, q);
});

test('restore is idempotent for the finally-plus-signal-handler path', async (t) => {
  const db = demoDatabase();
  const run = fixture(db, journalDir(t));
  await run.lock();
  run.seed();
  run.restore();
  const count = db.statements.length;
  assert.deepEqual(run.restore(), { restored: 0, untouched: 0 });
  assert.equal(db.statements.length, count);
});

test('the journal records pharmacy ids and numbers only, never the patient', async (t) => {
  const db = demoDatabase();
  const dir = journalDir(t);
  const run = fixture(db, dir);
  await run.lock();
  run.seed();
  const [name] = fs.readdirSync(dir);
  assert.match(name, /^[0-9a-f]{16}-5551234567\.json$/);
  const text = fs.readFileSync(path.join(dir, name), 'utf8');
  assert.equal(text.includes(PATIENT), false);
  assert.equal(fs.statSync(path.join(dir, name)).mode & 0o777, 0o600);
  run.restore();
  assert.deepEqual(fs.readdirSync(dir), []);
});

test('the next run restores an interrupted run exactly before taking its own snapshot', async (t) => {
  const db = demoDatabase();
  const before = faxes(db);
  const dir = journalDir(t);
  const crashed = fixture(db, dir, '5551111111');
  await crashed.lock();
  crashed.seed(); // SIGKILL here: no restore, no unlock

  const next = fixture(db, dir, '5552222222');
  assert.deepEqual(await next.lock(), { journals: 1, restored: 5, untouched: 0, kept: 0 });
  assert.deepEqual(faxes(db), before);
  assert.deepEqual(fs.readdirSync(dir), []);
  next.seed();
  next.restore();
  assert.deepEqual(faxes(db), before, 'the crashed run\'s 555 number must not become the next snapshot');
});

test('recovery leaves a value an operator set after the crash and still retires the journal', async (t) => {
  const db = demoDatabase();
  const dir = journalDir(t);
  const crashed = fixture(db, dir, '5551111111');
  await crashed.lock();
  crashed.seed();
  db.pharmacies.get('3').fax = '9055550177';
  assert.deepEqual(await fixture(db, dir, '5552222222').lock(), { journals: 1, restored: 4, untouched: 1, kept: 0 });
  assert.equal(db.pharmacies.get('3').fax, '9055550177');
  assert.deepEqual(fs.readdirSync(dir), []);
});

test('a failed restore keeps the row queued and the journal until a retry succeeds', async (t) => {
  const db = demoDatabase();
  const before = faxes(db);
  const dir = journalDir(t);
  const run = fixture(db, dir, '5551111111');
  await run.lock();
  run.seed();
  db.failUpdatesTo = '4164000305';
  assert.throws(() => run.restore(), /could not restore 1 pharmacy fax/);
  assert.equal(db.pharmacies.get('3').fax, '5551111111');
  assert.equal(db.pharmacies.get('7').fax, null, 'the other rows are still attempted');
  // The finally and the outer catch (or a signal handler) can both call restore(): a second
  // failing call must not mistake the emptied queue for success and delete the journal.
  assert.throws(() => run.restore(), /could not restore 1 pharmacy fax/);
  assert.equal(fs.readdirSync(dir).length, 1);
  db.failUpdatesTo = null;
  assert.deepEqual(run.restore(), { restored: 1, untouched: 0 });
  assert.deepEqual(faxes(db), before);
  assert.deepEqual(fs.readdirSync(dir), []);
});

test('a restore that never succeeds leaves the journal for the next run', async (t) => {
  const db = demoDatabase();
  const before = faxes(db);
  const dir = journalDir(t);
  const run = fixture(db, dir, '5551111111');
  await run.lock();
  run.seed();
  db.failUpdatesTo = '4164000305';
  assert.throws(() => run.restore());
  assert.throws(() => run.restore());
  db.failUpdatesTo = null;
  assert.deepEqual(await fixture(db, dir, '5552222222').lock(), { journals: 1, restored: 1, untouched: 4, kept: 0 });
  assert.deepEqual(faxes(db), before);
  assert.deepEqual(fs.readdirSync(dir), []);
});

test('an UPDATE whose outcome is unknown is still restored', async (t) => {
  const db = demoDatabase();
  const before = faxes(db);
  const dir = journalDir(t);
  const run = fixture(db, dir);
  await run.lock();
  db.commitThenFail = '6'; // the row is written, then the client reports a failure
  assert.throws(() => run.seed(), /timed out/);
  assert.equal(db.pharmacies.get('6').fax, '5551234567');
  db.commitThenFail = null;
  assert.deepEqual(run.restore(), { restored: 2, untouched: 0 });
  assert.deepEqual(faxes(db), before);
  assert.deepEqual(fs.readdirSync(dir), []);
});

test('a journal that restores nothing is kept, not deleted', async (t) => {
  const db = demoDatabase();
  const before = faxes(db);
  const dir = journalDir(t);
  const crashed = fixture(db, dir, '5551111111');
  await crashed.lock();
  crashed.seed();
  for (const [id, fax] of Object.entries(before)) db.pharmacies.get(id).fax = fax; // repaired by hand
  assert.deepEqual(await fixture(db, dir, '5552222222').lock(), { journals: 0, restored: 0, untouched: 5, kept: 1 });
  assert.equal(fs.readdirSync(dir).length, 1);
});

test('journals for another database are not replayed here', async (t) => {
  const db = demoDatabase();
  const dir = journalDir(t);
  const other = fakeDatabase({ pharmacies: [{ id: 3, fax: '9055550100' }],
    links: [{ patient: PATIENT, pharmacy: 3, status: '1' }], identity: 'other-host\t3306\tcarlos' });
  const elsewhere = fixture(other, dir, '5553333333');
  await elsewhere.lock();
  elsewhere.seed(); // interrupted on the other database
  assert.deepEqual(await fixture(db, dir).lock(), { journals: 0, restored: 0, untouched: 0, kept: 0 });
  assert.equal(other.pharmacies.get('3').fax, '5553333333');
  assert.equal(fs.readdirSync(dir).length, 1);
});

test('a malformed journal stops the run and is kept for inspection', async (t) => {
  const db = demoDatabase();
  const dir = journalDir(t);
  const probe = fixture(db, dir, '5551111111');
  await probe.lock();
  probe.seed();
  const [name] = fs.readdirSync(dir);
  probe.restore();
  for (const body of ['not json', '{"format":1,"stagedFax":"4165550100","entries":[]}',
    '{"format":1,"stagedFax":"5551111111","entries":[{"recordId":"3 OR 1=1","wasNull":true}]}',
    '{"format":1,"stagedFax":"5551111111","entries":[{"recordId":"3","wasNull":false,"originalFax":"x\'y"}]}']) {
    fs.writeFileSync(path.join(dir, name), body);
    await assert.rejects(fixture(db, dir, '5552222222').lock(), /journal|unexpected shape/);
    assert.equal(fs.existsSync(path.join(dir, name)), true);
  }
});

test('a shared or world-writable journal directory is refused', async (t) => {
  const dir = journalDir(t);
  fs.chmodSync(dir, 0o777);
  await assert.rejects(fixture(demoDatabase(), dir).lock(), /private directory/);
});

test('a lock held by another run is reported as such and nothing is touched', async (t) => {
  const db = demoDatabase();
  const run = createPharmacyFaxFixture({ sql: db.sql, demographicNo: PATIENT, stagedFax: '5551234567', mysql: {},
    journalDir: journalDir(t), acquireLock: async () => { throw new Error('Another report workflow owns this database'); } });
  await assert.rejects(run.lock(), /another Rx fax check is running/);
  assert.equal(db.statements.length, 0);
  await run.unlock();
});

test('rejects a routable staged number and a non-numeric patient up front', () => {
  const sql = () => { throw new Error('no SQL expected'); };
  assert.throws(() => createPharmacyFaxFixture({ sql, demographicNo: PATIENT, stagedFax: '4165550100' }), /NPA-555/);
  assert.throws(() => createPharmacyFaxFixture({ sql, demographicNo: '1 OR 1=1', stagedFax: '5551234567' }), /numeric/);
});

test('failures carry a fixed code the checks may print, and nothing else is tagged', async (t) => {
  const { fixtureErrorTag } = require('./rx-fax-pharmacy-fax-fixture');
  const db = demoDatabase();
  const locked = createPharmacyFaxFixture({ sql: db.sql, demographicNo: PATIENT, stagedFax: '5551234567', mysql: {},
    journalDir: journalDir(t), acquireLock: async () => { throw new Error('Another report workflow owns this database'); } });
  const error = await locked.lock().catch((e) => e);
  assert.equal(fixtureErrorTag(error), ' (RX_FAX_FIXTURE_LOCKED)');
  assert.throws(() => fixture(db, journalDir(t)).seed(), (e) => fixtureErrorTag(e) === ' (RX_FAX_FIXTURE_NOT_LOCKED)');
  for (const foreign of [new Error('x'), Object.assign(new Error('x'), { code: 'ECONNREFUSED' }),
    Object.assign(new Error('x'), { code: 'RX_FAX_FIXTURE_<script>' }), null, undefined]) {
    assert.equal(fixtureErrorTag(foreign), '');
  }
});
