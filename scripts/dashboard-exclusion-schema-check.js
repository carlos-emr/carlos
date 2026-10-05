#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Uses an owned temporary database: the configured account needs CREATE/DROP DATABASE.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {randomBytes} = require('node:crypto');
const h = require('./lib/playwright-harness');
const root = path.join(__dirname, '..');
const config = {host: process.env.MYSQL_HOST, user: process.env.MYSQL_USER,
  password: process.env.MYSQL_PASSWORD, database: process.env.MYSQL_DATABASE};
const admin = h.createSqlRunner(config);
const name = `carlos_test_ext_${randomBytes(8).toString('hex')}`;
const baseline = fs.readFileSync(path.join(root, 'database/mysql/migration/common/V1__baseline_schema.sql'), 'utf8');
const schema = baseline.match(/CREATE TABLE `demographicExt` \([\s\S]*?ENGINE=[^;]+;/)[0];
const migration = fs.readFileSync(path.join(root, 'database/mysql/migration/common/V1.0.54__allow_dashboard_exclusion_history.sql'), 'utf8');
let sql;
try {
  admin.execute(`CREATE DATABASE ${name}`);
  sql = h.createSqlRunner({...config, database: name});
  for (const variant of ['baseline', 'renamed', 'legacy']) {
    sql.execute(schema);
    if (variant === 'renamed') sql.execute('ALTER TABLE demographicExt DROP INDEX uk_demo_ext, ADD UNIQUE INDEX site_extension_identity (key_val,demographic_no)');
    if (variant === 'legacy') sql.execute('ALTER TABLE demographicExt DROP INDEX uk_demo_ext');
    sql.execute("INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time) VALUES (1,'111','excludeIndicator','first','1990-01-01'),(1,'111','demo_cell','original','2000-01-01')");
    if (variant === 'legacy') sql.execute("INSERT INTO demographicExt (demographic_no,key_val,value) VALUES (1,'demo_cell','legacy duplicate')");
    const before = sql.rows('SELECT id,demographic_no,provider_no,key_val,value,date_time,hidden FROM demographicExt ORDER BY id');
    sql.execute(migration);
    assert.deepEqual(sql.rows('SELECT id,demographic_no,provider_no,key_val,value,date_time,hidden FROM demographicExt ORDER BY id'), before);
    sql.execute("INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time) VALUES (1,'111','excludeIndicator','first',NOW()),(1,'222','excludeIndicator','first',NOW()),(1,'111','excludeIndicator','second',NOW())");
    assert.equal(sql.value("SELECT COUNT(*) FROM demographicExt WHERE key_val='excludeIndicator'"), '4');
    if (variant !== 'legacy') {
      assert.throws(() => sql.execute("INSERT INTO demographicExt (demographic_no,key_val,value) VALUES (1,'demo_cell','duplicate')"));
      assert.throws(() => sql.execute("INSERT INTO demographicExt (demographic_no,key_val,value) VALUES (1,'DEMO_CELL','case duplicate')"));
    }
    sql.execute(migration); // A repeat must not remove the replacement uniqueness or rewrite rows.
    assert.equal(sql.value("SELECT COUNT(*) FROM demographicExt WHERE key_val='excludeIndicator'"), '4');
    console.log(`PASS: ${variant} preserves existing rows, exclusion history and other-key constraints`);
    sql.execute('DROP TABLE demographicExt');
  }
} finally {
  if (sql) sql.dispose();
  admin.execute(`DROP DATABASE IF EXISTS ${name}`);
  admin.dispose();
}
