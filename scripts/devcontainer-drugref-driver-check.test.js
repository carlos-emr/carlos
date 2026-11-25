/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * #6282 added .devcontainer/drugref/check-driver-config.sh, the entry point that qualifies the mounted dev
 * DrugRef settings against the image's own WAR: it unpacks /usr/local/tomcat/webapps/drugref2.war into a fresh
 * temporary directory, runs DriverConfigurationProbe there as a single-file source program with the WAR's
 * class path and the settings mounted at /drugref2.properties, and removes the directory however the probe ends.
 *
 * These tests run a copy of the real wrapper whose three absolute container paths are moved under a fixture
 * root, with fake `jar` and `java` first on PATH that record their arguments and working directory (the
 * relocation pattern of debian-build.test.js). The probe itself runs in a real JVM against a stub WAR in
 * src/test/java/io/github/carlos_emr/carlos/devcontainer/DriverConfigurationProbeUnitTest.java (#6336).
 */

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const WRAPPER = path.join(root, '.devcontainer/drugref/check-driver-config.sh');
const IMAGE_WAR = '/usr/local/tomcat/webapps/drugref2.war';
const PROBE = 'io/github/carlos_emr/carlos/devcontainer/DriverConfigurationProbe.java';
const SETTINGS = '/drugref2.properties';

// One script serves as both fakes; it tells them apart by the name it was run as.
const FAKE_TOOL = `#!${process.execPath}
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const tool = path.basename(process.argv[1]);
const record = {tool, cwd: process.cwd(), argv: process.argv.slice(2)};
if (tool === 'jar') {
  record.entriesBefore = fs.readdirSync('.');
  if (process.env.FAKE_JAR_STATUS === '0') {
    fs.mkdirSync('WEB-INF/classes', {recursive: true});
    fs.mkdirSync('WEB-INF/lib', {recursive: true});
    fs.writeFileSync('WEB-INF/classes/spring_config.xml', '<beans/>\\n');
    fs.writeFileSync('WEB-INF/lib/FAKE-connector.jar', '');
  }
} else {
  record.extracted = fs.existsSync('WEB-INF/classes/spring_config.xml');
}
fs.appendFileSync(process.env.FAKE_CALLS, JSON.stringify(record) + '\\n');
process.exitCode = Number(process.env['FAKE_' + tool.toUpperCase() + '_STATUS']);
`;

/** The real wrapper with only its image WAR, probe mount and settings mount moved under `container`. */
function relocatedWrapper(container, destination) {
  let moved = 0;
  const relocated = fs.readFileSync(WRAPPER, 'utf8').replace(
    /\/usr\/local\/tomcat\/webapps\/drugref2\.war|\/driver-check\/|(?<=\s)\/drugref2\.properties(?=\s)/g,
    (containerPath) => {
      moved += 1;
      return container + containerPath;
    });
  assert.equal(moved, 3, 'the wrapper names the image WAR, the probe mount and the settings mount once each');
  fs.writeFileSync(destination, relocated);
  return destination;
}

function fixture(t) {
  const base = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-drugref-driver-check-')));
  t.after(() => fs.rmSync(base, { recursive: true, force: true }));
  const fx = {
    container: path.join(base, 'container'),
    tmp: path.join(base, 'tmp'),
    bin: path.join(base, 'bin'),
    calls: path.join(base, 'calls.jsonl'),
    caller: path.join(base, 'caller'),
  };
  for (const directory of [fx.tmp, fx.bin, fx.caller, path.join(fx.container, path.dirname(IMAGE_WAR))]) {
    fs.mkdirSync(directory, { recursive: true });
  }
  fs.writeFileSync(path.join(fx.container, IMAGE_WAR), 'FAKE drugref2.war');
  for (const tool of ['jar', 'java']) {
    fs.writeFileSync(path.join(fx.bin, tool), FAKE_TOOL);
    fs.chmodSync(path.join(fx.bin, tool), 0o755);
  }
  fx.wrapper = relocatedWrapper(fx.container, path.join(base, 'check-driver-config.sh'));
  return fx;
}

function runWrapper(fx, statuses = {}) {
  return spawnSync('bash', [fx.wrapper], {
    cwd: fx.caller,
    env: {
      PATH: `${fx.bin}${path.delimiter}${process.env.PATH}`,
      TMPDIR: fx.tmp,
      FAKE_CALLS: fx.calls,
      FAKE_JAR_STATUS: '0',
      FAKE_JAVA_STATUS: '0',
      ...statuses,
    },
    encoding: 'utf8',
    timeout: 30_000,
  });
}

function calls(fx) {
  if (!fs.existsSync(fx.calls)) return [];
  return fs.readFileSync(fx.calls, 'utf8').trim().split('\n').map((line) => JSON.parse(line));
}

test('the wrapper unpacks the image WAR into a fresh directory and probes the mounted settings there', (t) => {
  const fx = fixture(t);

  const result = runWrapper(fx);

  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stderr);
  const [jar, java, ...others] = calls(fx);
  assert.deepEqual(others, []);
  assert.equal(jar.tool, 'jar');
  assert.deepEqual(jar.argv, ['xf', fx.container + IMAGE_WAR]);
  assert.equal(path.dirname(jar.cwd), fx.tmp, 'mktemp -d creates the directory under TMPDIR');
  assert.deepEqual(jar.entriesBefore, [], 'the WAR is unpacked into an empty directory');
  assert.equal(java.tool, 'java');
  assert.equal(java.cwd, jar.cwd, 'the probe runs where the WAR was unpacked');
  assert.equal(java.extracted, true);
  // The class path stays relative to the unpacked WAR and its lib/* wildcard is left for the JVM to expand.
  assert.deepEqual(java.argv, [
    '--class-path', 'WEB-INF/classes:WEB-INF/lib/*',
    `${fx.container}/driver-check/${PROBE}`,
    fx.container + SETTINGS,
    'WEB-INF/classes/spring_config.xml',
  ]);
  assert.equal(fs.existsSync(jar.cwd), false, 'the unpacked WAR is removed on success');
  assert.deepEqual(fs.readdirSync(fx.tmp), []);
});

test('a refused configuration fails the wrapper with the probe status and still removes the unpacked WAR', (t) => {
  const fx = fixture(t);

  const result = runWrapper(fx, { FAKE_JAVA_STATUS: '3' });

  assert.ifError(result.error);
  assert.equal(result.status, 3);
  const [jar, java] = calls(fx);
  assert.equal(java.tool, 'java');
  assert.equal(fs.existsSync(jar.cwd), false, 'the unpacked WAR is removed on failure');
  assert.deepEqual(fs.readdirSync(fx.tmp), []);
});

test('a WAR that cannot be unpacked stops the wrapper before the probe runs', (t) => {
  const fx = fixture(t);

  const result = runWrapper(fx, { FAKE_JAR_STATUS: '2' });

  assert.ifError(result.error);
  assert.equal(result.status, 2);
  assert.deepEqual(calls(fx).map((call) => call.tool), ['jar']);
  assert.deepEqual(fs.readdirSync(fx.tmp), []);
});

test('the wrapper container paths are the image WAR and the mounted probe source and settings', () => {
  // The image installs DrugRef's WAR where the wrapper unpacks it from.
  const dockerfile = fs.readFileSync(path.join(root, '.devcontainer/drugref/Dockerfile'), 'utf8');
  assert.match(dockerfile, /^COPY --from=builder \S+\/drugref2\.war \/usr\/local\/tomcat\/webapps\/drugref2\.war$/m);
  // The probe is the source file beside the wrapper, and its only caller mounts that directory at /driver-check
  // and the dev settings at /drugref2.properties.
  assert.ok(fs.statSync(path.join(path.dirname(WRAPPER), PROBE)).isFile());
  const workflow = fs.readFileSync(path.join(root, '.github/workflows/container-images.yml'), 'utf8');
  assert.match(workflow, /\$PWD\/\.devcontainer\/drugref:\/driver-check:ro\b/);
  assert.match(workflow,
    /\$PWD\/\.devcontainer\/development\/config\/shared\/volumes\/drugref2\.properties:\/drugref2\.properties:ro\b/);
  assert.match(workflow, /carlos-drugref-dev:test \/driver-check\/check-driver-config\.sh\b/);
});
