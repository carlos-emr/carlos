/* SPDX-License-Identifier: GPL-2.0-or-later */
//
// An install can provision nothing and still report success: every database
// step in carlos-emr.postinst is non-fatal on purpose, so dpkg is never left
// half-configured over a database problem. These tests pin the other half of
// that bargain — that such an install is RECORDED, ANNOUNCED through debconf and stderr, and FINISHED without reinstalling.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const { spawnSync } = require('node:child_process');

const repo = path.join(__dirname, '..');
const read = (...p) => fs.readFileSync(path.join(repo, ...p), 'utf8');
const postinst = read('debian', 'carlos-emr.postinst');
const MARKER = '/var/lib/carlos-emr/.install-incomplete';

test('the postinst records an unfinished install, with what the install asked for', () => {
  const fn = postinst.match(/mark_incomplete\(\) \{[\s\S]*?\n\}/)[0];
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-incomplete-'));
  try {
    const script = `STATE="${root}/state"\nINCOMPLETE_MARKER="\${STATE}/.install-incomplete"\n`
      + `RESET_ADMIN=true\nDEMO_DATA=true\n${fn}\nmark_incomplete "MariaDB was not reachable"`;
    const result = spawnSync('sh', ['-c', script], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    const marker = path.join(root, 'state', '.install-incomplete');
    const body = fs.readFileSync(marker, 'utf8');
    // KEY=value, one line each: carlos_ctl.util.env_get reads this file, and a
    // reason that wrapped would be read back as a stray key.
    assert.match(body, /^reason=MariaDB was not reachable$/m);
    assert.match(body, /^reset_admin=true$/m);
    assert.match(body, /^demo_data=true$/m);
    assert.equal(fs.statSync(marker).mode & 0o777, 0o644);
    // The operator is told the one command that fixes it, not only that
    // something went wrong.
    assert.match(result.stderr, /sudo carlos-ctl finish-install/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('every path that leaves the database unprovisioned records it; a good one clears it', () => {
  // The three ways provisioning can be skipped or fail, each inside the branch
  // that sets MIGRATION_OK=0.
  for (const reason of [
    'the configuration could not be applied',
    'the database settings could not be applied',
    'the seeded administrator credential could not be replaced',
    'the requested demonstration dataset could not be loaded',
    'the database accounts could not be provisioned',
    'the database schema migration failed',
    'MariaDB was not reachable while the package was configured',
  ]) {
    assert.ok(postinst.includes(`mark_incomplete "${reason}"`), reason);
  }

});

test('an unfinished install is announced through debconf, not only on stderr', () => {
  // critical priority: a preseeded or low-priority run must still record it.
  assert.match(postinst, /db_input critical carlos-emr\/install-incomplete/);
  // A note is shown once and then skipped; a SECOND failed attempt is exactly
  // the one an operator is watching for, so the seen flag is cleared first.
  assert.match(postinst,
    /db_fset carlos-emr\/install-incomplete seen false[\s\S]{0,200}db_input critical carlos-emr\/install-incomplete/);
  // Outside the `[ -d /run/systemd/system ]` guard the other trailing blocks
  // sit in: a chroot or image build that provisioned nothing needs it too.
  const notice = postinst.slice(postinst.indexOf('db_input critical carlos-emr/install-incomplete'));
  assert.ok(!notice.includes('/run/systemd/system'));
  assert.ok(postinst.includes('[ "${INSTALL_INCOMPLETE}" = 1 ] || [ -e "${INCOMPLETE_MARKER}" ]'));
  const templates = read('debian', 'carlos-emr.templates');
  // error, not note: this reports a failed install, and note is for
  // informational text (the type debconf documents for error conditions is the
  // one a front end may not let an operator page past unread).
  assert.match(templates, /Template: carlos-emr\/install-incomplete\nType: error\n/);
  assert.match(templates, /sudo carlos-ctl finish-install/);
});

test('the boot-time completion watches the same marker and runs before the EMR', () => {
  const unit = read('debian', 'carlos-emr.carlos-emr-provision.service');
  assert.ok(unit.includes(`ConditionPathExists=${MARKER}`),
    'the unit must be skipped, not merely quick, on a healthy boot');
  assert.match(unit, /^ExecStart=\/usr\/lib\/carlos-emr\/carlos-ctl finish-install --boot$/m);
  assert.match(unit, /^After=mariadb\.service/m);
  assert.match(unit, /^Before=carlos-emr\.service$/m);
  assert.match(unit, /^WantedBy=multi-user\.target$/m);
  // Long enough to create the schema: the default 90s would kill a migration
  // part-way through.
  assert.match(unit, /^TimeoutStartSec=(\d+)$/m);
  assert.ok(Number(unit.match(/^TimeoutStartSec=(\d+)$/m)[1]) >= 900);

  const emr = read('debian', 'carlos-emr.carlos-emr.service');
  assert.match(emr, /^Wants=carlos-emr-provision\.service$/m);
  assert.match(emr, /^After=carlos-emr-provision\.service$/m);
  assert.ok(emr.includes('ConditionPathExists=!/var/lib/carlos-emr/.seed-credential-live'));
  // Wants, never Requires: a completion that cannot finish must not stop an
  // EMR whose schema an operator has since fixed by hand.
  assert.ok(!/Requires=carlos-emr-provision/.test(emr));

  assert.match(read('debian', 'rules'),
    /dh_installsystemd --no-start --name=carlos-emr-provision/);
});

test('finish-install reads the marker the postinst wrote, and defaults safely without one', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-provision-'));
  try {
    const marker = path.join(root, '.install-incomplete');
    const probe = `
import sys
sys.path.insert(0, ${JSON.stringify(path.join(repo, 'debian', 'assets'))})
from carlos_ctl import provision
provision.MARKER = ${JSON.stringify(marker)}
print("absent", provision.pending(), provision._answer("reset_admin", True),
      provision._answer("demo_data", False))
open(provision.MARKER, "w").write(
    "reason=MariaDB was not reachable\\nreset_admin=false\\ndemo_data=true\\n")
print("present", provision.pending(), provision._answer("reset_admin", True),
      provision._answer("demo_data", False), "|", provision.reason())
provision.clear()
print("cleared", provision.pending())
provision.clear()
`;
    // Absolute interpreter with an empty PATH: the no-marker case must fall back
    // to its own defaults when debconf-show cannot be reached either.
    const python = spawnSync('python3', ['-c', 'import sys; print(sys.executable)'], { encoding: 'utf8' }).stdout.trim();
    const result = spawnSync(python, ['-c', probe],
      { encoding: 'utf8', env: { ...process.env, PATH: root } });
    assert.equal(result.status, 0, result.stderr);
    const [absent, present, cleared] = result.stdout.trim().split('\n');
    // No marker (and, with PATH emptied, no debconf-show either): replacing the
    // published administrator credential is the answer we assume, never
    // loading demonstration data.
    assert.equal(absent, 'absent False True False');
    // The marker is authoritative — it is what the postinst recorded at the
    // moment it gave up, so a resumed run makes the same decisions.
    assert.equal(present, 'present True False True | MariaDB was not reachable');
    assert.equal(cleared, 'cleared False');  // and clearing twice is not an error
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('carlos-ctl exposes finish-install, and check points at it', () => {
  const probe = `
import sys
sys.path.insert(0, ${JSON.stringify(path.join(repo, 'debian', 'assets'))})
from carlos_ctl import cli, provision
assert cli._VERBS["finish-install"] is provision.cmd_finish_install
assert "finish-install" in cli._USAGE
print("ok")
`;
  const result = spawnSync('python3', ['-c', probe], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), 'ok');

  // The same unreachable database skips the drugref package's seed load, which
  // lives in ITS maintainer script: point at that rather than reimplement it.
  const provision = read('debian', 'assets', 'carlos_ctl', 'provision.py');
  assert.match(provision, /dpkg-reconfigure carlos-emr-drugref/);
  assert.match(provision, /table_schema='drugref2'/);

  // One repair at a time: the boot provisioner, a manual run and the postinst
  // all provision with the same verbs, and each would generate its own
  // administrator credential. THE SAME lock file in all three.
  assert.match(provision, /fcntl\.LOCK_EX \| fcntl\.LOCK_NB/);
  assert.match(provision, /LOCK = os\.path\.join\(STATE, "\.finish-install\.lock"\)/);
  assert.match(postinst, /PROVISION_LOCK="\$\{STATE\}\/\.finish-install\.lock"/);
  assert.match(postinst, /flock -w 300 9/);
  // Taken before the oscar -> carlos rename, which MOVES the clinical tables —
  // not merely before the provisioning verbs. Every mutating step is inside it.
  assert.ok(postinst.indexOf('acquire_provision_lock ||') <
    postinst.indexOf('DB_NAME_SENTINEL="${STATE}/.db-name-default-migrated"'));
  assert.ok(postinst.indexOf('acquire_provision_lock ||') <
    postinst.indexOf('carlos-ctl init-config'));
  assert.match(postinst,
    /if \[ "\$\{PROVISION_LOCK_HELD\}" = 1 \] && \[ ! -e "\$\{DB_NAME_SENTINEL\}" \]; then/);
  // init-config rewrites carlos.properties and reloads nginx: shared state.
  assert.match(postinst,
    /if \[ "\$\{PROVISION_LOCK_HELD\}" = 1 \]; then\n\s+carlos-ctl init-config/);
  assert.match(postinst, /if \[ "\$\{PROVISION_LOCK_HELD\}" = 0 \]; then/);
  // Only a MISSING flock(1) falls through to the pre-lock behavior; a lock file
  // that cannot be opened defers provisioning instead of running unserialized.
  assert.match(postinst, /command -v flock[^\n]*\|\| return 0/);
  assert.match(postinst, /! mkdir -p "\$\{STATE\}"[^\n]*\|\| ! exec 9>/);
  // The sentinel is the per-start guard; when it cannot be written the mask is
  // the only containment left, and recovery has to lift it again.
  assert.match(provision, /"systemctl", "mask", "carlos-emr\.service"/);
  assert.match(provision, /"systemctl", "unmask", "carlos-emr\.service"/);
  // The postinst falls back to the same mask, so the two paths cannot leave a
  // host in different states.
  assert.match(postinst, /deb-systemd-helper mask carlos-emr\.service/);
  assert.match(postinst, /deb-systemd-helper unmask carlos-emr\.service/);
  // And a chroot or image build, with no systemd to ask, still sees that mask:
  // it is a symlink to /dev/null, readable either way.
  assert.match(postinst,
    /readlink \/etc\/systemd\/system\/carlos-emr\.service[^\n]*= \/dev\/null/);

  // db-users provisions BOTH packages' accounts and rewrites both properties
  // files, so the DrugRef package's configure takes the same lock.
  const drugref = read('debian', 'carlos-emr-drugref.postinst');
  assert.match(drugref, /PROVISION_LOCK="\$\{STATE\}\/\.finish-install\.lock"/);
  assert.match(drugref, /flock -w 300 9/);
  assert.ok(drugref.indexOf('acquire_provision_lock') <
    drugref.indexOf('carlos-ctl db-users'));

  // One predicate for "an OSCAR 19 import is in progress": the shipped guard
  // that carlos-emr.service also runs as its ExecCondition. finish-install
  // does far more than start the EMR, so it must consult it too.
  assert.match(provision, /carlos-emr-o19-guard/);
  assert.ok(provision.indexOf('_o19_import_running()') <
    provision.indexOf('_wait_for_db(120 if boot else 0)'));
  // A configure that deliberately provisioned nothing (the import gate) must
  // not delete the marker a previous failed configure left behind.
  assert.match(postinst, /INSTALL_INCOMPLETE\}" = 0 \] && \[ "\$\{MIGRATION_OK:-1\}" = 1/);

  const validate = read('debian', 'assets', 'carlos_ctl', 'validate.py');
  // check reports the unfinished install FIRST: it is the one cause behind the
  // dozen unrelated-looking failures the rest of the run then reports.
  assert.ok(validate.indexOf('provision.pending()') < validate.indexOf('print("services")'));
  assert.match(validate, /carlos-ctl finish-install/);
  // A COUNT that failed and a COUNT that returned zero must not read the same.
  assert.match(validate, /could not count the tables in/);
  assert.match(validate, /has NO tables: the schema was never created/);
  // DrugRef shares the EMR's Tomcat; a stopped EMR is not a DrugRef fault.
  assert.match(validate, /because carlos-emr is NOT\s+"\s*"running: DrugRef shares that Tomcat/);
});

// Behavioral tests mock only the external database/systemd boundary. They run
// the complete repair command and assert its exit status and persistent state.
test('repair failure and recovery behavior', () => {
  const result = spawnSync('python3', [path.join(__dirname, 'deb-install-completion-tests.py')],
    { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stdout + result.stderr);
});

for (const fault of ['mkdir', 'mktemp', 'write', 'chmod', 'mv', 'rm']) {
  test(`marker ${fault} failure remains visible without breaking dpkg configure`, () => {
    const functions = postinst.slice(postinst.indexOf('mark_incomplete() {'),
      postinst.indexOf('# deb-systemd-invoke'));
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-marker-fault-'));
    try {
      const marker = path.join(root, '.install-incomplete');
      fs.writeFileSync(marker, 'reason=previous failure\n');
      const inject = fault === 'write' ? `mktemp() { echo "${root}"; }`
        : `${fault}() { return 1; }`;
      const result = spawnSync('sh', ['-c', `set -e
STATE="${root}"
INCOMPLETE_MARKER="${marker}"
INSTALL_INCOMPLETE=0
${functions}
${inject}
${fault === 'rm' ? 'clear_incomplete' : 'mark_incomplete "injected failure"'}
test "$INSTALL_INCOMPLETE" = 1
echo survived
`], { encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
      assert.match(result.stdout, /survived/);
      assert.match(result.stderr, /sudo carlos-ctl finish-install/);
      assert.ok(fs.existsSync(marker), 'a previous marker must survive failed persistence');
      if (fault !== 'rm') {
        assert.equal(fs.readFileSync(marker, 'utf8'), 'reason=previous failure\n');
      }
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
}

test('configure retains failed requested work and clears a completed retry', () => {
  const functions = postinst.slice(postinst.indexOf('mark_incomplete() {'),
    postinst.indexOf('# deb-systemd-invoke'));
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-marker-retry-'));
  try {
    const result = spawnSync('sh', ['-c', `set -e
STATE="${root}"
INCOMPLETE_MARKER="$STATE/.install-incomplete"
INSTALL_INCOMPLETE=0
${functions}
mark_incomplete "demo-data failed"
MIGRATION_OK=1
clear_incomplete
test -f "$INCOMPLETE_MARKER"
INSTALL_INCOMPLETE=0
clear_incomplete
test ! -e "$INCOMPLETE_MARKER"
`], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('a configure that provisioned nothing keeps the marker it did not earn', () => {
  // The OSCAR 19 import gate sets MIGRATION_OK=0 and deliberately skips every
  // provisioning step without marking anything itself. Deleting the marker an
  // earlier failed configure left would throw away the only recovery trigger
  // while still reporting the install as finished.
  const functions = postinst.slice(postinst.indexOf('mark_incomplete() {'),
    postinst.indexOf('# deb-systemd-invoke'));
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-marker-gated-'));
  try {
    const marker = path.join(root, '.install-incomplete');
    fs.writeFileSync(marker, 'reason=MariaDB was not reachable\n');
    const result = spawnSync('sh', ['-c', `set -e
STATE="${root}"
INCOMPLETE_MARKER="${marker}"
INSTALL_INCOMPLETE=0
${functions}
MIGRATION_OK=0
clear_incomplete
test -f "$INCOMPLETE_MARKER"
grep -q 'MariaDB was not reachable' "$INCOMPLETE_MARKER"
`], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stdout + result.stderr);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

for (const [active, enabled, success] of [
  ['inactive', 'disabled', true], ['active', 'disabled', false],
  ['inactive', 'enabled', false], ['unknown', 'unknown', false],
]) {
  test(`postinst verifies credential containment (${active}, ${enabled})`, () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-containment-'));
    try {
      const start = postinst.indexOf('if [ "$1" = configure ] && [ "${SEED_CREDENTIAL_LIVE:-0}" = 1 ]; then');
      fs.writeFileSync(path.join(root, 'deb-systemd-helper'), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
      const block = postinst.slice(start, postinst.indexOf('# Everything below runs AFTER', start))
        .replaceAll('/run/systemd/system', root);
      const result = spawnSync('sh', ['-c', `set -e
set -- configure
STATE="${root}"
SEED_SENTINEL="$STATE/seed"
SEED_CREDENTIAL_LIVE=1
PATH="${root}:$PATH"
sd_invoke() { return 0; }
systemctl() {
  case "$1" in
    is-active) echo ${active} ;;
    is-enabled) echo ${enabled} ;;
    *) return 1 ;;
  esac
}
${block}
`], { encoding: 'utf8' });
      assert.equal(result.status, success ? 0 : 1, result.stderr);
      assert.match(result.stderr, success ? /guard is in place/ : /could not verify/);
      assert.equal(fs.statSync(path.join(root, 'seed')).mode & 0o777, 0o600);
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
}

// Execute the shipped nginx decision block with isolated service commands.
// A syntax or service failure must survive the earlier clear_incomplete call.
test('postinst records terminal nginx failures and accepts a successful rebind', () => {
  const start = postinst.indexOf('        if nginx -t >/dev/null 2>&1; then');
  assert.ok(start >= 0);
  const end = postinst.indexOf('\n        ;;', start);
  assert.ok(end > start);
  const block = postinst.slice(start, end).replaceAll('/run/systemd/system', '/tmp');
  for (const [syntax, reload, restart, bound, incomplete] of [
    [1, 0, 0, 0, true], [0, 1, 0, 0, true],
    [0, 0, 1, 1, true], [0, 0, 0, 1, true],
    [0, 0, 0, 0, false], [0, 0, 0, 2, false],
  ]) {
    const script = `set -eu
nginx() { return ${syntax}; }
sd_invoke() { echo "service:$1"; if [ "$1" = reload ]; then return ${reload}; else return ${restart}; fi; }
calls=0
front_door_listening() { calls=$((calls + 1)); if [ ${bound} = 2 ]; then [ "$calls" -gt 1 ]; else return ${bound}; fi; }
mark_incomplete() { echo "incomplete:$1"; }
${block}
`;
    const result = spawnSync('sh', ['-c', script], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stdout.includes('incomplete:'), incomplete, JSON.stringify({ syntax, reload, restart, bound, result }));
    if (syntax || reload) assert.ok(!result.stdout.includes('service:restart'));
    if (bound === 2) assert.ok(result.stdout.includes('service:restart'));
  }
});

test('postinst listener proof requires nginx ownership, both ports and the last configured address', () => {
  const fn = postinst.match(/front_door_listening\(\) \{[\s\S]*?\n\}/)[0];
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-nginx-listeners-'));
  try {
    const envFile = path.join(root, 'env');
    for (const [ip, addresses, owner, expected] of [
      ['127.0.0.1', ['127.0.0.1:80', '127.0.0.1:443'], 'nginx', 0],
      ['127.0.0.1', ['127.0.0.1:80', '127.0.0.1:443'], 'python3', 1],
      ['127.0.0.1', ['127.0.0.1:80', '127.0.0.1:443'], 'nginx-other', 1],
      ['127.0.0.1', ['127.0.0.1:443'], 'nginx', 1],
      ['127.0.0.1', ['0.0.0.0:80', '127.0.0.1:443'], 'nginx', 1],
      ['::1', ['[::1]:80', '[::1]:443'], 'nginx', 0],
      [' [::1] ', ['[::1]:80', '[::1]:443'], 'nginx', 0],
      [' 127.0.0.1 ', ['127.0.0.1:80', '127.0.0.1:443'], 'nginx', 0],
      ['0.0.0.0', ['0.0.0.0:80', '0.0.0.0:443'], 'nginx', 0],
      ['127.0.0.1', ['127.0.0.1:80', '127.0.0.1:443'], '', 1],
    ]) {
      fs.writeFileSync(envFile, `CARLOS_BIND_IP=192.0.2.1\nCARLOS_BIND_IP="${ip}"\n`);
      const sockets = addresses.map(a => `LISTEN 0 511 ${a} 0.0.0.0:* ${owner ? `users:(("${owner}",pid=123,fd=6))` : ''}`).join('\n');
      const result = spawnSync('sh', ['-c', `
ENV_FILE='${envFile}'
ss() { [ "$1" = '-ltnpH' ] || return 2; cat <<'SOCKETS'
${sockets}
SOCKETS
}
sleep() { :; }
${fn}
front_door_listening
`], { encoding: 'utf8' });
      assert.equal(result.status, expected, JSON.stringify({ ip, addresses, owner, result }));
    }
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test('postinst HTTP probe uses the last overrides and the matching wildcard address family', () => {
  const start = postinst.indexOf('        PROBE_NAME="$(sed');
  const end = postinst.indexOf('        i=0', start);
  assert.ok(start >= 0 && end > start);
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-nginx-probe-'));
  try {
    const envFile = path.join(root, 'env');
    for (const [ip, expected] of [['0.0.0.0', '127.0.0.1'], ['::', '::1'], ['::1', '::1'], [' [::] ', '::1'], [' [::1] ', '::1'], [' 192.0.2.8 ', '192.0.2.8']]) {
      fs.writeFileSync(envFile, `CARLOS_SERVER_NAME=old.invalid\nCARLOS_SERVER_NAME="clinic.test"\nCARLOS_BIND_IP=192.0.2.1\nCARLOS_BIND_IP="${ip}"\n`);
      const result = spawnSync('sh', ['-c', `ENV_FILE='${envFile}'\n${postinst.slice(start, end)}\nprintf '%s %s' "$PROBE_NAME" "$PROBE_IP"`], { encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
      assert.equal(result.stdout, `clinic.test ${expected}`);
    }
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test('reconfigure preserves the last deployed overrides, including a reduced heap and bind address', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-debconf-overrides-'));
  try {
    fs.writeFileSync(path.join(root, 'carlos-emr.env'), [
      'CARLOS_SERVER_NAME=old.invalid', 'CARLOS_BIND_IP=0.0.0.0',
      'CARLOS_PROVINCE=on', 'CARLOS_JAVA_XMX=8g',
      'CARLOS_SERVER_NAME="clinic.test"', 'CARLOS_BIND_IP="127.0.0.1"',
      'CARLOS_PROVINCE="bc"', 'CARLOS_JAVA_XMX="2g"', '',
    ].join('\n'));
    const stubs = `db_set() { printf '%s=%s\\n' "$1" "$2"; }
db_fget() { RET=true; }
db_input() { :; }
db_go() { :; }
db_get() { RET=selfsigned; }`;
    const script = read('debian', 'carlos-emr.config')
      .replace('. /usr/share/debconf/confmodule', stubs)
      .replace('CONF_DIR=/etc/carlos-emr', `CONF_DIR='${root}'`);
    const result = spawnSync('sh', ['-c', script], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(result.stdout.trim().split('\n'), [
      'carlos-emr/server-name=clinic.test', 'carlos-emr/bind-ip=127.0.0.1',
      'carlos-emr/province=bc', 'carlos-emr/java-heap=2g',
    ]);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
