#!/usr/bin/env node
/*
 * Starts Tomcat, checks the CARLOS app responds, stops Tomcat, and fails when
 * Tomcat reports CARLOS-owned shutdown leak warnings.
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CATALINA_HOME=/path/to/tomcat
 *   CATALINA_SH=/path/to/catalina.sh
 *   STARTUP_TIMEOUT_MS=120000
 *   SHUTDOWN_TIMEOUT_MS=60000
 */

const { spawn } = require('child_process');

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8080/carlos';
const catalina = process.env.CATALINA_SH
  || (process.env.CATALINA_HOME ? `${process.env.CATALINA_HOME.replace(/\/$/, '')}/bin/catalina.sh` : 'catalina.sh');
const startupTimeoutMs = Number(process.env.STARTUP_TIMEOUT_MS || 120000);
const shutdownTimeoutMs = Number(process.env.SHUTDOWN_TIMEOUT_MS || 60000);

const leakPatterns = [
  /LogAction\.executorService/i,
  /QueueCache/i,
  /mysql-cj-abandoned-connection-cleanup/i,
  /AbandonedConnectionCleanupThread/i,
  /registered the JDBC driver/i,
  /ForkJoinPool-\d+-worker/i,
  /drools-worker/i,
  /com\.github\.javaparser\.ParserConfiguration/i,
  /StaticJavaParser/i,
];

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function waitForStartup(child, output) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Timed out waiting for Tomcat startup')), startupTimeoutMs);
    const interval = setInterval(() => {
      if (/Server startup in \[\d+\] milliseconds/i.test(output.text)) {
        clearTimeout(timeout);
        clearInterval(interval);
        resolve();
      }
      if (child.exitCode !== null) {
        clearTimeout(timeout);
        clearInterval(interval);
        reject(new Error(`Tomcat exited before startup with code ${child.exitCode}`));
      }
    }, 250);
  });
}

async function checkHealth() {
  const url = new URL(baseUrl);
  const response = await fetch(url, { redirect: 'manual' });
  if (response.status < 200 || response.status >= 400) {
    throw new Error(`Unexpected health status ${response.status} from ${url}`);
  }
}

function stopTomcat(child, output) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      child.kill('SIGKILL');
      reject(new Error('Timed out waiting for Tomcat shutdown'));
    }, shutdownTimeoutMs);
    child.once('exit', () => {
      clearTimeout(timeout);
      resolve();
    });
    child.kill('SIGINT');
  });
}

async function main() {
  const output = { text: '' };
  const child = spawn(catalina, ['run'], { env: process.env, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.on('data', (chunk) => { output.text += chunk.toString(); process.stdout.write(chunk); });
  child.stderr.on('data', (chunk) => { output.text += chunk.toString(); process.stderr.write(chunk); });

  try {
    await waitForStartup(child, output);
    await wait(1000);
    await checkHealth();
  } finally {
    if (child.exitCode === null) {
      await stopTomcat(child, output);
    }
  }

  const leaks = leakPatterns.filter((pattern) => pattern.test(output.text));
  if (leaks.length > 0) {
    throw new Error(`Tomcat shutdown leak smoke failed; matched ${leaks.map(String).join(', ')}`);
  }
  console.log('Tomcat shutdown leak smoke passed');
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
