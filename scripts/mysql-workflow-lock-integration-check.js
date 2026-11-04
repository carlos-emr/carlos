#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const {acquireMysqlWorkflowLock} = require('./lib/mysql-workflow-lock');

(async () => {
  const config = {host: process.env.MYSQL_HOST, user: process.env.MYSQL_USER,
    password: process.env.MYSQL_PASSWORD, database: process.env.MYSQL_DATABASE};
  const name = 'report-fixture-lock-test';
  const release = await acquireMysqlWorkflowLock(config, name);
  try {
    await assert.rejects(acquireMysqlWorkflowLock(config, name), /Another report workflow/);
    console.log('PASS: a second connection cannot enter the same database workflow');
  } finally {
    await release();
  }
  const again = await acquireMysqlWorkflowLock(config, name);
  await again();
  await again();
  console.log('PASS: cleanup releases the lock and repeated cleanup is harmless');
})().catch(error => {console.error(error.message); process.exitCode = 1;});
