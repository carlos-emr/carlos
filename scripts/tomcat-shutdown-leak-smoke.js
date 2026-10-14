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
  /clearReferencesThreads[^\n]+LogAction\.executorService/,
  /clearReferencesThreads[^\n]+\bQueueCache\b/,
  /clearReferencesThreads[^\n]+mysql-cj-abandoned-connection-cleanup/,
  /clearReferencesThreads[^\n]+AbandonedConnectionCleanupThread/,
  /clearReferencesThreads[^\n]+HikariPool-/,
  /clearReferencesJdbc[^\n]+registered the JDBC driver/,
  /clearReferencesThreads[^\n]+ForkJoinPool-\d+-worker/,
  /clearReferencesThreads[^\n]+ForkJoinPool\.commonPool-delayScheduler/,
  /clearReferencesThreads[^\n]+drools-worker-\d+/,
  /clearReferencesThreadLocals[^\n]+com\.github\.javaparser\.ParserConfiguration/,
  /clearReferencesThreadLocals[^\n]+StaticJavaParser/,
];

function shutdownLeakFindings(output, targetUrl) {
  const context = new URL(targetUrl).pathname.replace(/^\/|\/$/g, '');
  const owned = [];
  const otherApplications = [];
  for (const line of output.split('\n')) {
    if (!leakPatterns.some(pattern => pattern.test(line))) continue;
    const application = line.match(/The web application \[([^\]]*)\]/);
    // An unfamiliar warning format must fail conservatively, not disappear as
    // an assumed warning from another deployment.
    if (!application || application[1] === context) owned.push(line);
    else otherApplications.push(line);
  }
  return { owned, otherApplications };
}

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

  const leaks = shutdownLeakFindings(output.text, baseUrl);
  if (leaks.otherApplications.length) {
    console.warn(`Other deployed applications reported ${leaks.otherApplications.length} shutdown warnings; see the captured Tomcat log`);
  }
  if (leaks.owned.length > 0) {
    throw new Error(`Tomcat shutdown leak smoke failed: ${leaks.owned.length} target-webapp warning(s)\n${leaks.owned.join('\n')}`);
  }
  console.log('Tomcat shutdown leak smoke passed');
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.stack || error.message);
    process.exitCode = 1;
  });
}

module.exports = { shutdownLeakFindings };
