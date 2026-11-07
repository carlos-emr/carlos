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

for (const context of ['carlos', 'other']) {
  test(`detects a retained MariaDB scheduler in ${context} without confusing application logs`, () => {
    const warning = `WARNING clearReferencesThreads The web application [${context}] appears to have started a thread named [MariaDb-timeout] but has failed to stop it.`;
    const clean = 'INFO MariaDb-timeout scheduler stopped';
    assert.deepEqual(shutdownLeakFindings(`${clean}\n${warning}`, 'http://127.0.0.1/carlos'),
      context === 'carlos' ? { owned: [warning], otherApplications: [] }
        : { owned: [], otherApplications: [warning] });
    assert.deepEqual(shutdownLeakFindings(clean, 'http://127.0.0.1/carlos'),
      { owned: [], otherApplications: [] });
  });
}

test('CLI fails on a retained MariaDB scheduler after successful startup and HTTP health', async t => {
  const fs = require('node:fs');
  const path = require('node:path');
  const os = require('node:os');
  const http = require('node:http');
  const { promisify } = require('node:util');
  const execFile = promisify(require('node:child_process').execFile);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-leak-cli-'));
  t.after(() => fs.rmSync(directory, {recursive:true,force:true}));
  const server = http.createServer((_request, response) => response.end('healthy'));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const catalina = path.join(directory, 'catalina');
  fs.writeFileSync(catalina, `#!/usr/bin/env node
console.log('Server startup in [1] milliseconds');
setInterval(() => {}, 1000);
process.once('SIGINT', () => {
  const warning = process.env.SYNTHETIC_LEAK === 'true'
    ? 'WARNING clearReferencesThreads The web application [carlos] failed to stop [MariaDb-timeout]\\n' : '';
  process.stderr.write(warning, () => process.exit(0));
});
`, {mode:0o755});
  const options = {env:{...process.env, CATALINA_SH:catalina,
    BASE_URL:`http://127.0.0.1:${server.address().port}/carlos`, STARTUP_TIMEOUT_MS:'3000', SHUTDOWN_TIMEOUT_MS:'3000'}, timeout:10000};
  const clean = await execFile(process.execPath, [path.join(__dirname, 'tomcat-shutdown-leak-smoke.js')], options);
  assert.match(clean.stdout, /shutdown leak smoke passed/);
  await assert.rejects(execFile(process.execPath, [path.join(__dirname, 'tomcat-shutdown-leak-smoke.js')],
    {...options,env:{...options.env,SYNTHETIC_LEAK:'true'}}), failure => {
    assert.equal(failure.code, 1);
    assert.match(failure.stderr, /target-webapp warning/);
    return true;
  });
});
