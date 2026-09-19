#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');

// Run against a migrated disposable MariaDB database with MYSQL_* configured.
// All fixture inserts occur in one transaction and are rolled back, including
// on SQL errors (connection close). No stored credentials are read or printed.
function check(sql) {
  assert.equal(sql.value("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='emailConfig' AND COLUMN_NAME='configDetails'"), 'text',
    'Migrated emailConfig.configDetails must be TEXT, not the legacy VARCHAR(1000)');
  assert.equal(sql.value("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.0.26' AND success=1"), '1',
    'The widening migration must be recorded as successful');
  assert.equal(sql.value("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='emailConfig'"), 'InnoDB',
    'Rollback safety requires InnoDB');
  const sender = `coverage-${randomBytes(12).toString('hex')}@example.invalid`;
  const small = JSON.stringify({ password: "synthetic 'quoted' value" });
  const large = JSON.stringify({ password: 'synthetic-é'.repeat(1500) });
  const values = [small, large, null];
  const tuples = values.map(value => `('SMTP','LOCAL',0,${h.sqlString(sender)},${value === null ? 'NULL' : h.sqlString(value)})`);
  const rows = sql.rows(`START TRANSACTION;
    INSERT INTO emailConfig (emailType,emailProvider,active,senderEmail,configDetails) VALUES ${tuples.join(',')};
    SELECT IF(configDetails IS NULL,'NULL',HEX(configDetails)) FROM emailConfig WHERE senderEmail=${h.sqlString(sender)} ORDER BY id;
    ROLLBACK;`);
  assert.ok(JSON.stringify(rows.map(row => row[0])) === JSON.stringify(values.map(value => value === null ? null : Buffer.from(value).toString('hex').toUpperCase())),
    'Short, long UTF-8 JSON and NULL must round-trip exactly');
  assert.equal(sql.value(`SELECT COUNT(*) FROM emailConfig WHERE senderEmail=${h.sqlString(sender)}`), '0',
    'Schema check must leave no fixture rows');
}
if (require.main === module) {
  let sql;
  try {
    sql = h.createSqlRunner(h.readConfig().mysql);
    check(sql);
    console.log('PASS email-config-schema: migrated MariaDB TEXT, long JSON, NULL and rollback');
  } catch (error) {
    console.error(`FAIL email-config-schema: ${error.message}`);
    process.exitCode = 1;
  } finally {
    if (sql) sql.dispose();
  }
}
module.exports = { check };
