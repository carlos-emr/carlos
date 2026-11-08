/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { shutdownLeakFindings,waitForStartup } = require('./tomcat-shutdown-leak-smoke');

test('startup timeout retires its polling interval even while the child remains alive',async()=>{
  let reads=0;
  const child={get exitCode(){reads++;return null;},signalCode:null};
  await assert.rejects(waitForStartup(child,{text:''},{timeoutMs:10,pollMs:1}),/Timed out waiting for Tomcat startup/);
  const stopped=reads;await new Promise(resolve=>setTimeout(resolve,20));
  assert.equal(reads,stopped,'Startup polling continued after timeout');
});
test('startup keeps the original launch error and retires its timers',async()=>{
  const error=new Error('synthetic launch failure');
  await assert.rejects(waitForStartup({exitCode:null,signalCode:null},{text:'',launchError:error},{timeoutMs:100,pollMs:1}),value=>value===error);
});
test('CLI reports a signal-terminated startup child without waiting or retaining its interval',async t=>{
  const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
  const execFile=require('node:util').promisify(require('node:child_process').execFile);
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-startup-signal-'));
  t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
  const catalina=path.join(directory,'catalina');
  fs.writeFileSync(catalina,"#!/usr/bin/env node\nprocess.kill(process.pid,'SIGKILL');\n",{mode:0o755});
  await assert.rejects(execFile(process.execPath,[path.join(__dirname,'tomcat-shutdown-leak-smoke.js')],{
    env:{...process.env,CATALINA_SH:catalina,STARTUP_TIMEOUT_MS:'5000',SHUTDOWN_TIMEOUT_MS:'100'},timeout:2500,
  }),error=>{
    assert.equal(error.code,1);assert.equal(error.killed,false);
    assert.match(error.stderr,/Tomcat exited before startup with code null signal SIGKILL/);
    assert.doesNotMatch(error.stderr,/Timed out waiting for Tomcat startup/);return true;
  });
});

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
  const warning = process.env.SYNTHETIC_LEAK
    ? 'WARNING clearReferencesThreads The web application [carlos] failed to stop [MariaDb-timeout]\\n' : '';
  if (process.env.SYNTHETIC_LEAK === 'late') {
    require('node:child_process').spawn(process.execPath, ['-e', 'setTimeout(() => process.stderr.write(' + JSON.stringify(warning) + '), 350)'], {stdio:['ignore','inherit','inherit']}).unref();
    process.exit(0);
  }
  process.stderr.write(warning, () => process.exit(0));
});
`, {mode:0o755});
  const options = {env:{...process.env, CATALINA_SH:catalina,
    BASE_URL:`http://127.0.0.1:${server.address().port}/carlos`, STARTUP_TIMEOUT_MS:'3000', SHUTDOWN_TIMEOUT_MS:'3000'}, timeout:10000};
  const clean = await execFile(process.execPath, [path.join(__dirname, 'tomcat-shutdown-leak-smoke.js')], options);
  assert.match(clean.stdout, /shutdown leak smoke passed/);
  for (const synthetic of ['true', 'late']) await assert.rejects(execFile(process.execPath, [path.join(__dirname, 'tomcat-shutdown-leak-smoke.js')],
    {...options,env:{...options.env,SYNTHETIC_LEAK:synthetic}}), failure => {
    assert.equal(failure.code, 1);
    assert.match(failure.stderr, /target-webapp warning/);
    return true;
  });
});


test('CLI preserves a failed health check together with a shutdown timeout', async t => {
  const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),http=require('node:http');
  const execFile=require('node:util').promisify(require('node:child_process').execFile);
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-leak-double-failure-'));
  t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
  const server=http.createServer((_request,response)=>{response.statusCode=503;response.end('synthetic unhealthy response');});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
  const catalina=path.join(directory,'catalina');
  fs.writeFileSync(catalina,`#!/usr/bin/env node
console.log('Server startup in [1] milliseconds');
setInterval(()=>{},1000);
process.on('SIGINT',()=>{});
`,{mode:0o755});
  await assert.rejects(execFile(process.execPath,[path.join(__dirname,'tomcat-shutdown-leak-smoke.js')],{
    env:{...process.env,CATALINA_SH:catalina,BASE_URL:`http://127.0.0.1:${server.address().port}/carlos`,STARTUP_TIMEOUT_MS:'3000',SHUTDOWN_TIMEOUT_MS:'100'},timeout:10000,
  }),error=>{
    assert.equal(error.code,1);assert.match(error.stderr,/Unexpected health status 503/);assert.match(error.stderr,/Timed out waiting for Tomcat shutdown/);
    assert.doesNotMatch(error.stdout,/shutdown leak smoke passed/);return true;
  });
});
