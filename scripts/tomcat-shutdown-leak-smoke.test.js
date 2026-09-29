/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { shutdownLeakFindings } = require('./tomcat-shutdown-leak-smoke');

test('reports the Java 25 scheduler leak for the target webapp separately from DrugRef JDBC leaks', () => {
  const owned = 'WARNING clearReferencesThreads The web application [carlos] started [ForkJoinPool.commonPool-delayScheduler]';
  const other = 'WARNING clearReferencesJdbc The web application [drugref2] registered the JDBC driver [com.mysql.cj.jdbc.Driver]';
  const result = shutdownLeakFindings(`${owned}\n${other}`, 'http://127.0.0.1:18080/carlos/');
  assert.deepEqual(result, { owned: [owned], otherApplications: [other] });
});

test('does not confuse similarly named contexts or hide target JDBC cleanup warnings', () => {
  const owned = 'WARNING clearReferencesThreads The web application [clinic] started [mysql-cj-abandoned-connection-cleanup]';
  const other = 'WARNING clearReferencesThreads The web application [clinic-old] started [mysql-cj-abandoned-connection-cleanup]';
  assert.deepEqual(shutdownLeakFindings(`${owned}\n${other}`, 'http://127.0.0.1/clinic'),
    { owned: [owned], otherApplications: [other] });
});

test('an unattributed matching warning remains a failure', () => {
  const warning = 'WARNING clearReferencesThreads started mysql-cj-abandoned-connection-cleanup';
  assert.deepEqual(shutdownLeakFindings(warning, 'http://127.0.0.1/carlos'),
    { owned: [warning], otherApplications: [] });
});
