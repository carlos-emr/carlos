#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * Real-database check for rx-fax-pharmacy-fax-fixture.js (issue #3607).
 *
 * The unit tests drive the fixture against an in-memory table; this proves the parts only MariaDB
 * can: that the hex snapshot, the BINARY compare-and-swap, ROW_COUNT() through the mysql client and
 * the shared advisory lock behave as the fixture assumes, and that addDate (ON UPDATE
 * current_timestamp()) survives. It creates its own marked pharmacies (NULL, '', a trailing-space
 * value and a punctuated one; a pharmacy marked deleted but still linked, which the Rx page lists
 * and the fixture must cover; and an inactive link it must leave alone), links them to an unused
 * synthetic patient number, and removes every row it created in a finally. No existing pharmacy
 * or patient record is read or written.
 *
 * Env: MYSQL_HOST (loopback unless ALLOW_NON_LOCAL_MYSQL_HOST=true), MYSQL_USER, MYSQL_PASSWORD,
 * MYSQL_DATABASE.
 *
 * Run: MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=x MYSQL_DATABASE=carlos \
 *        node scripts/rx-fax-pharmacy-fax-fixture-integration-check.js
 */

const assert = require('node:assert/strict');
const { randomInt } = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createSqlRunner, sqlString } = require('./lib/playwright-harness');
const { createPharmacyFaxFixture, fixtureErrorTag } = require('./rx-fax-pharmacy-fax-fixture');

const config = {
  host: process.env.MYSQL_HOST || 'localhost',
  user: process.env.MYSQL_USER || 'root',
  password: process.env.MYSQL_PASSWORD,
  database: process.env.MYSQL_DATABASE || 'carlos',
};
const suffix = String(randomInt(1000000, 10000000));
const marker = `PW-RXFAX-FIXTURE-${suffix}`;
// int(10) holds it, and no real chart reaches two billion.
const patient = String(2000000000 + Number(suffix));
const ORIGINALS = [null, '', '416 400 0305 ', '(905) 555-0100 x.2+1'];

(async () => {
  const db = createSqlRunner(config);
  const text = (query) => db.rows(query).map((row) => row.join('\t')).join('\n');
  const journalDir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-fax-fixture-check-'));
  const created = [];
  const fixtures = [];
  const fixture = (stagedFax, sql = text) => {
    const made = createPharmacyFaxFixture({ sql, demographicNo: patient, stagedFax, mysql: config, journalDir });
    fixtures.push(made);
    return made;
  };
  const addDates = () => db.rows(`SELECT recordID, addDate FROM pharmacyInfo WHERE name = ${sqlString(marker)} ORDER BY recordID`);
  const state = () => db.rows(`SELECT recordID, ISNULL(fax), HEX(IFNULL(fax, '')) FROM pharmacyInfo
    WHERE name = ${sqlString(marker)} ORDER BY recordID`).map(([id, isNull, hex]) => [id, isNull === '1' ? null : Buffer.from(hex, 'hex').toString('utf8')]);
  try {
    assert.equal(db.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no = ${patient}`), '0',
      'the synthetic patient number is already linked; rerun for a new one');
    const addPharmacy = (fax, status, linkStatus) => {
      const id = db.value(`INSERT INTO pharmacyInfo (name, fax, status, uid, addDate) VALUES (${sqlString(marker)}, ${fax === null ? 'NULL' : sqlString(fax)}, ${sqlString(status)}, 0, '2024-01-31 19:41:06'); SELECT LAST_INSERT_ID();`);
      assert.match(id, /^[1-9][0-9]*$/);
      created.push(id);
      db.execute(`INSERT INTO demographicPharmacy (pharmacyID, demographic_no, status, preferredOrder) VALUES (${id}, ${patient}, ${sqlString(linkStatus)}, 0)`);
      return id;
    };
    const active = ORIGINALS.map((fax) => addPharmacy(fax, '1', '1'));
    const deleted = addPharmacy('9055550100', '0', '1');
    const unlinked = addPharmacy('9055550111', '1', '0');
    const listed = [...active, deleted];
    const before = state();
    const datesBefore = addDates();

    // 1. Every listed destination, the existing numbers included, holds the run's number.
    const run = fixture(`555${suffix}`);
    await run.lock();
    assert.deepEqual(run.seed(), { active: listed.length, seeded: listed.length });
    for (const [id, fax] of state()) {
      const expected = listed.includes(id) ? `555${suffix}` : before.find(([b]) => b === id)[1];
      assert.equal(fax, expected, `pharmacy ${id} after seeding`);
    }
    assert.deepEqual(addDates(), datesBefore, 'seeding must not restamp addDate');
    console.log('PASS: every listed destination (a deleted-but-linked one included) holds the run\'s 555 number; the unlinked one is untouched; addDate kept');

    // 2. The lock is shared: a second run on this database stops before writing anything.
    const second = fixture(`555${String(Number(suffix) + 1).slice(-7)}`);
    const refusal = await second.lock().catch((error) => error);
    assert.equal(fixtureErrorTag(refusal), ' (RX_FAX_FIXTURE_LOCKED)');
    console.log('PASS: a concurrent run is refused the shared lock');

    // 3. An edit made during the run survives the restore; everything else is restored exactly.
    db.execute(`UPDATE pharmacyInfo SET fax = '9055550199' WHERE recordID = ${active[1]}`);
    assert.deepEqual(run.restore(), { restored: listed.length - 1, untouched: 1 });
    await run.unlock();
    const restored = Object.fromEntries(state());
    assert.equal(restored[active[0]], null, 'NULL restored as NULL');
    assert.equal(restored[active[1]], '9055550199', 'an edit made during the run is kept');
    assert.equal(restored[active[2]], '416 400 0305 ', 'trailing space restored byte for byte');
    assert.equal(restored[active[3]], '(905) 555-0100 x.2+1');
    assert.equal(restored[deleted], '9055550100');
    assert.equal(restored[unlinked], '9055550111');
    assert.deepEqual(fs.readdirSync(journalDir), []);
    assert.deepEqual(addDates().filter(([id]) => id !== active[1]), datesBefore.filter(([id]) => id !== active[1]),
      'restoring must not restamp addDate');
    console.log('PASS: NULL, trailing-space and punctuated originals restored exactly with addDate kept; a concurrent edit kept; journal retired');
    db.execute(`UPDATE pharmacyInfo SET fax = '', addDate = '2024-01-31 19:41:06' WHERE recordID = ${active[1]}`);

    // 4. A killed run: lock released by the dropped connection, originals restored by the next run.
    const killed = fixture(`555${String(Number(suffix) + 2).slice(-7)}`);
    await killed.lock();
    killed.seed();
    await killed.unlock(); // the connection a SIGKILL would drop; no restore runs
    assert.equal(fs.readdirSync(journalDir).length, 1);
    const next = fixture(`555${String(Number(suffix) + 3).slice(-7)}`);
    assert.deepEqual(await next.lock(), { journals: 1, restored: listed.length, untouched: 0, kept: 0 });
    assert.deepEqual(state(), before);
    assert.deepEqual(addDates(), datesBefore, 'recovery must not restamp addDate');
    next.seed();
    next.restore();
    await next.unlock();
    assert.deepEqual(state(), before, 'the next run snapshots the true originals, not the killed run\'s number');
    console.log('PASS: the next run restores a killed run from its journal before taking its own snapshot');

    // 5. A value changed between snapshot and rewrite stops the run without overwriting it.
    let raced = false;
    const racing = fixture(`555${String(Number(suffix) + 4).slice(-7)}`, (query) => {
      if (!raced && query.startsWith('UPDATE pharmacyInfo SET fax')) {
        raced = true;
        db.execute(`UPDATE pharmacyInfo SET fax = '9055550188' WHERE recordID = ${active[0]}`);
      }
      return text(query);
    });
    await racing.lock();
    const changed = (() => { try { racing.seed(); return null; } catch (error) { return error; } })();
    assert.equal(fixtureErrorTag(changed), ' (RX_FAX_FIXTURE_CHANGED)');
    racing.restore();
    await racing.unlock();
    assert.equal(Object.fromEntries(state())[active[0]], '9055550188');
    console.log('PASS: a value changed during staging is kept and the run stops');
  } finally {
    // A failed assertion can leave a fixture holding the lock; its connection would keep Node alive.
    for (const made of fixtures) await made.unlock().catch(() => {});
    if (created.length) {
      db.execute(`DELETE FROM demographicPharmacy WHERE demographic_no = ${patient} AND pharmacyID IN (${created.join(',')});
        DELETE FROM pharmacyInfo WHERE recordID IN (${created.join(',')}) AND name = ${sqlString(marker)}`);
    }
    db.dispose();
    fs.rmSync(journalDir, { recursive: true, force: true });
  }
})().catch((error) => {
  console.error(`FAIL rx-fax-pharmacy-fax-fixture-integration-check: ${error.message}${fixtureErrorTag(error)}`);
  process.exitCode = 1;
});
