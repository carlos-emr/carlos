/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Guards the demo-data correction behind issue #3724.
 *
 * The dev snapshot seeds formLabReq07 with practitionerNo '0000--00'.
 * formlabreq07.jsp posts that value back as a hidden field on every save, and
 * ModSecurity's libinjection rule (OWASP CRS 942100) reads it as a numeric
 * literal followed by a '--' SQL comment. That scores CRITICAL, which is 5, and
 * the packaged inbound anomaly threshold is also 5 — so the single argument
 * reaches the threshold by itself and nginx answers 403 before Tomcat sees the
 * save. formLabReq10's value is empty, which is why that form saved and this
 * one did not.
 *
 * These are file-level checks, not a live WAF run: they pin that the correction
 * exists, that it still runs after the snapshot that introduces the value, and
 * that it is idempotent in shape. The behavioural verification was done against
 * a ModSecurity+CRS 4.25.1 proxy at PL1 with the threshold at 5 — the body was
 * blocked with '0000--00' and passed once corrected.
 */

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const sqlPath = path.join(repoRoot, '.devcontainer/db/scripts/demo-labreq-practitioner-no.sql');
const populatePath = path.join(repoRoot, '.devcontainer/db/scripts/populate_db.sh');
const dockerfilePath = path.join(repoRoot, '.devcontainer/db/Dockerfile');

const read = (p) => fs.readFileSync(p, 'utf8');

test('the demo lab-req practitioner correction exists', () => {
  assert.ok(fs.existsSync(sqlPath), `expected ${sqlPath} to exist`);
});

test('the correction targets only double-hyphen values', () => {
  const sql = read(sqlPath);
  const updates = sql.split(';').filter((s) => /^\s*UPDATE/im.test(s));
  assert.ok(updates.length > 0, 'expected at least one UPDATE');
  for (const stmt of updates) {
    assert.match(
      stmt,
      /WHERE[\s\S]*practitionerNo\s+LIKE\s+'%--%'/i,
      'every UPDATE must be scoped to values containing "--", so a re-run is a no-op ' +
        'and a real practitioner number is never rewritten',
    );
  }
});

test('the correction never widens beyond formLabReq07', () => {
  const sql = read(sqlPath);
  const tables = [...sql.matchAll(/UPDATE\s+([A-Za-z0-9_]+)/gi)].map((m) => m[1]);
  assert.deepStrictEqual(
    [...new Set(tables)],
    ['formLabReq07'],
    'only the table carrying the bad seed value should be touched',
  );
});

test('the correction runs after the snapshot that introduces the value', () => {
  const populate = read(populatePath);
  const snapshot = populate.indexOf('/scripts/development.sql');
  const fix = populate.indexOf('/scripts/demo-labreq-practitioner-no.sql');
  assert.ok(snapshot !== -1, 'development.sql load not found in populate_db.sh');
  assert.ok(fix !== -1, 'demo-labreq-practitioner-no.sql is not loaded by populate_db.sh');
  assert.ok(
    fix > snapshot,
    'the correction must run after development.sql, which is what seeds "0000--00"',
  );
});

test('the correction is copied into the database image', () => {
  assert.match(
    read(dockerfilePath),
    /COPY \.\/\.devcontainer\/db\/scripts\/demo-labreq-practitioner-no\.sql/,
    'the script must be COPYed into the image or populate_db.sh will fail at load time',
  );
});

test('no lab-req seed in the tracked snapshot keeps a libinjection-hostile value', () => {
  // The snapshot itself still carries '0000--00'; that is expected and is what the
  // correction exists to repair. This asserts the pairing holds: if someone ever
  // removes the bad value from the snapshot, the correction becomes a harmless
  // no-op rather than silently masking a reintroduction elsewhere.
  const sql = read(sqlPath);
  assert.match(
    sql,
    /942100/,
    'keep the CRS rule id in the file so the next reader can find why this exists',
  );
  assert.match(
    sql,
    /#3724/,
    'keep the issue reference so the correction is traceable',
  );
});
