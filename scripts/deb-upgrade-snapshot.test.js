/* Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const fake = `#!/usr/bin/env python3
import json, os, sys
name=os.path.basename(sys.argv[0]); args=sys.argv[1:]
with open(os.environ['COMMAND_LOG'],'a') as log: log.write(json.dumps([name,args])+'\\n')
if name=='mariadb':
    sql=next(a.split('=',1)[1] for a in args if a.startswith('--execute='))
    if os.environ.get('FAIL_QUERY') and os.environ['FAIL_QUERY'] in sql:
        if os.environ.get('SIMULATE_DEFAULTS_FORCE') and '--no-defaults' not in args: sys.exit(0)
        print('simulated database query failure',file=sys.stderr); sys.exit(1)
    if 'COUNT(*) FROM security' in sql: print(os.environ.get('ADMIN_COUNT','1'))
    elif 'SHA2' in sql: print(os.environ.get('PASSWORD_DIGEST','a'*64) if 'password:' in sql else 'b'*64)
    elif 'forcePasswordReset' in sql: print('0')
    elif 'SELECT version' in sql: print('1.0.39')
    elif 'SELECT HEX' in sql:
        body=os.environ.get('INLINE_BODY','')
        eligible=bool(body.strip(''.join(chr(n) for n in range(33))))
        print('example file.pdf'.encode().hex()+'\\t'+str(int(eligible)))
    elif 'WHERE success=0' in sql: print('0')
    elif 'SELECT CONCAT' in sql: print('NULL:1')
    elif 'COUNT(*)' in sql: print('1')
    else: sys.exit('unexpected test query')
elif name=='dpkg-query': print('1.0')
elif name=='dpkg': print('carlos-ctl: /usr/sbin/carlos-ctl')
elif name=='systemctl': print('active' if args[0]=='is-active' else '0')
elif name=='curl': print('200',end='')
elif name=='carlos-ctl':
    print('1 check(s) failed' if os.environ.get('HEALTH_FAIL') else 'All checks passed')
    sys.exit(1 if os.environ.get('HEALTH_FAIL') else 0)
`;

function setup(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-upgrade with spaces-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const env = { ...process.env, DB_NAME: 'renamed_fixture', ADMIN_USER: 'operator',
    MYSQL_SOCKET: '/tmp/fixture socket', CARLOS_ETC_DIR: path.join(root, 'etc'),
    CARLOS_STATE_DIR: path.join(root, 'state'), CARLOS_SHARE_DIR: path.join(root, 'share'),
    PRE: path.join(root, 'before snapshot.txt'), POST: path.join(root, 'after snapshot.txt'),
    COMMAND_LOG: path.join(root, 'commands.jsonl'), UPGRADE_LOG: path.join(root, 'absent.log'),
    EXPECT_FLYWAY: '1', EXPECT_NEW: '', EXPECT_TAG: 'build.version=fixture', EXPECT_SPLIT: '0' };
  const files = {
    'etc/carlos-emr.env': 'CARLOS_PROVINCE="ON"\nCARLOS_TZ="UTC"\nCARLOS_DB_NAME="renamed_fixture"\n',
    'etc/carlos.properties': 'consultation_signature_enabled=true\nrx_fax_enabled=true\n',
    'etc/backup.env': 'fixture\n', 'etc/tls/mode': 'selfsigned\n', 'etc/tls/fullchain.pem': 'certificate\n',
    'share/webapp/carlos/WEB-INF/classes/carlos-build.properties': 'build.version=fixture\nbuild.job=fixture\nbuild.number=1\n',
    'state/CarlosDocument/carlos/document/example file.pdf': 'fixture document',
  };
  for (const [name, contents] of Object.entries(files)) {
    const file = path.join(root, name);
    fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, contents);
  }
  fs.mkdirSync(path.join(root, 'bin'));
  for (const name of ['mariadb', 'dpkg-query', 'dpkg', 'systemctl', 'curl', 'carlos-ctl']) {
    fs.writeFileSync(path.join(root, 'bin', name), fake, { mode: 0o755 });
  }
  env.PATH = `${path.join(root, 'bin')}:${process.env.PATH}`;
  const run = (script, overrides = {}) => spawnSync('bash', [path.join(__dirname, script)],
    { env: { ...env, ...overrides }, encoding: 'utf8', timeout: 30000 });
  const baseline = () => {
    const result = run('deb-upgrade-baseline.sh');
    assert.ifError(result.error); assert.equal(result.status, 0, result.stderr);
    fs.writeFileSync(env.PRE, result.stdout); return result.stdout;
  };
  return { root, env, run, baseline };
}

test('upgrade baseline handles renamed DB/socket and encodes arbitrary UTF-8 administrator names', t => {
  const fixture = setup(t);
  const name = "Renée'\\_%; DROP TABLE security; --";
  const result = fixture.run('deb-upgrade-baseline.sh', { ADMIN_USER: name });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /^baseline.format=2$/m);
  assert.match(result.stdout, /^admin.hash=a{64}$/m);
  assert.match(result.stdout, /^admin.pin=b{64}$/m);
  const calls = fs.readFileSync(fixture.env.COMMAND_LOG, 'utf8').trim().split('\n').map(JSON.parse)
    .filter(([command]) => command === 'mariadb');
  assert.ok(calls.length > 15);
  for (const [, args] of calls) {
    assert.equal(args[0], '--no-defaults');
    assert.ok(args.includes('--protocol=socket'));
    assert.ok(args.includes('--database=renamed_fixture'));
    assert.ok(args.includes('--socket=/tmp/fixture socket'));
    assert.ok(!args.some(arg => arg.includes(name)));
  }
  assert.ok(calls[0][1].some(arg => arg.includes(`CONVERT(X'${Buffer.from(name).toString('hex')}' USING utf8mb4)`)));
});

test('upgrade baseline rejects a database identifier before invoking MariaDB', t => {
  const fixture = setup(t);
  const result = fixture.run('deb-upgrade-baseline.sh', { DB_NAME: 'clinic;DROP' });
  assert.equal(result.status, 2); assert.match(result.stderr, /DB_NAME/);
  assert.equal(fs.existsSync(fixture.env.COMMAND_LOG), false);
});

for (const [name, overrides, error] of [
  ['missing administrator', { ADMIN_COUNT: '0' }, /exactly one/],
  ['ambiguous administrator', { ADMIN_COUNT: '2' }, /exactly one/],
  ['failed clinical query', { FAIL_QUERY: 'COUNT(*) FROM appointment' }, /query failure/],
]) {
  test(`upgrade baseline fails closed for ${name}`, t => {
    const result = setup(t).run('deb-upgrade-baseline.sh', overrides);
    assert.equal(result.status, 1); assert.match(result.stderr, error);
  });
}

test('upgrade verifier accepts an unchanged install with snapshot paths containing spaces', t => {
  const fixture = setup(t); fixture.baseline();
  fs.appendFileSync(fixture.env.PRE, 'adminXhash=unrelated-key\n');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /0 failed/);
  assert.match(result.stdout, /PIN digest unchanged/);
});

test('upgrade verifier accepts the one-time OSCAR feature-defaults migration', t => {
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  fs.writeFileSync(props, 'rx_fax_enabled=false\nonare_labreqver=07\nALLOW_UPDATE_DOCUMENT_CONTENT= false\nother=kept\n');
  fixture.baseline();
  fs.writeFileSync(props, 'rx_fax_enabled=true\nonare_labreqver=10\nALLOW_UPDATE_DOCUMENT_CONTENT= true\nother=kept\n');
  fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, '.oscar-feature-defaults-migrated'), '');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /changed only by the one-time stock-default migrations/);
  assert.match(result.stdout, /old stock onare_labreqver=07 was migrated to onare_labreqver=10/);
  assert.match(result.stdout, /OSCAR feature-defaults migration sentinel written/);
});

test('upgrade verifier rejects one line that joins two definitions of a migrated key', t => {
  // Two lines "k=false" + "k=true" must not snapshot the same as the single line
  // "k=false;k=true", which changes the effective value.
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  fs.writeFileSync(props, 'rx_fax_enabled=false\nrx_fax_enabled=true\nonare_labreqver=07\nother=kept\n');
  fixture.baseline();
  fs.writeFileSync(props, 'rx_fax_enabled=false;rx_fax_enabled=true\nonare_labreqver=10\nother=kept\n');
  fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, '.oscar-feature-defaults-migrated'), '');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /FAIL cfg.carlos.properties.sha changed/);
});

for (const [name, after, sentinel, error] of [
  ['an unsanctioned change to a migrated key', 'rx_fax_enabled=off\nonare_labreqver=07\nother=kept\n', false,
    /FAIL cfg.carlos.properties.sha changed/],
  ['a migration that skipped its sentinel', 'rx_fax_enabled=true\nonare_labreqver=10\nother=kept\n', false,
    /FAIL OSCAR feature-defaults migration sentinel missing/],
  ['a rewrite after the migration already ran', 'rx_fax_enabled=true\nonare_labreqver=10\nother=kept\n', 'pre',
    /FAIL cfg.carlos.properties.sha changed/],
]) {
  test(`upgrade verifier rejects ${name}`, t => {
    const fixture = setup(t);
    const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
    const marker = path.join(fixture.env.CARLOS_STATE_DIR, '.oscar-feature-defaults-migrated');
    fs.writeFileSync(props, 'rx_fax_enabled=false\nonare_labreqver=07\nother=kept\n');
    if (sentinel === 'pre') fs.writeFileSync(marker, '');
    fixture.baseline();
    fs.writeFileSync(props, after);
    const result = fixture.run('deb-upgrade-verify.sh');
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stdout, error);
  });
}

// The one-time HL7_LAB_MEASUREMENT_FILTER rewrite (#4613). The old stock line is the postinst's LAB_FILTER_OLD; the new
// one is the shipped carlos.properties line, which the package's skeleton carries.
const LAB_FILTER_OLD = /^\s*LAB_FILTER_OLD='([^']*)'$/m.exec(
  fs.readFileSync(path.join(__dirname, '..', 'debian', 'carlos-emr.postinst'), 'utf8'))[1];
const LAB_FILTER_NEW = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'carlos.properties'), 'utf8')
  .split('\n').find(line => line.startsWith('HL7_LAB_MEASUREMENT_FILTER='));
const LAB_FILTER_SENTINEL = '.lab-measurement-filter-migrated';

function labFilterFixture(t, { sentinel = false, skelLine = LAB_FILTER_NEW } = {}) {
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  const skel = path.join(fixture.env.CARLOS_SHARE_DIR, 'skel', 'carlos.properties');
  fs.mkdirSync(path.dirname(skel), { recursive: true });
  fs.writeFileSync(skel, `rx_fax_enabled=true\n${skelLine === null ? '' : `${skelLine}\n`}`);
  fs.writeFileSync(props, `rx_fax_enabled=true\n${LAB_FILTER_OLD}\nother=kept\n`);
  if (sentinel) fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, LAB_FILTER_SENTINEL), '');
  fixture.baseline();
  return { ...fixture, props };
}

test('upgrade verifier digest of the old lab filter is the postinst old stock line', () => {
  const verify = fs.readFileSync(path.join(__dirname, 'deb-upgrade-verify.sh'), 'utf8');
  const sha = /^LAB_FILTER_OLD_SHA=([0-9a-f]{16})$/m.exec(verify);
  assert.ok(sha, 'deb-upgrade-verify.sh states LAB_FILTER_OLD_SHA');
  assert.equal(crypto.createHash('sha256').update(`${LAB_FILTER_OLD}\n`).digest('hex').slice(0, 16), sha[1]);
  // The old line is the one with the four-character range alternative; the shipped one no longer has it.
  assert.ok(LAB_FILTER_OLD.includes('|^([+-\\\\?]{1,4})|'), LAB_FILTER_OLD);
  assert.ok(LAB_FILTER_NEW, 'carlos.properties ships HL7_LAB_MEASUREMENT_FILTER');
  assert.ok(!LAB_FILTER_NEW.includes('{1,4}'), LAB_FILTER_NEW);
});

test('upgrade verifier accepts the one-time lab-filter migration', t => {
  const fixture = labFilterFixture(t);
  fs.writeFileSync(fixture.props, `rx_fax_enabled=true\n${LAB_FILTER_NEW}\nother=kept\n`);
  fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, LAB_FILTER_SENTINEL), '');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /changed only by the one-time stock-default migrations .*lab filter: yes/);
  assert.match(result.stdout, /PASS the old stock HL7_LAB_MEASUREMENT_FILTER was migrated to the skeleton's/);
  assert.match(result.stdout, /PASS lab-filter migration sentinel written/);
});

for (const [name, after, sentinel, error] of [
  ['an unsanctioned change to the lab filter', 'HL7_LAB_MEASUREMENT_FILTER=.*', false, /FAIL cfg.carlos.properties.sha changed/],
  ['a lab-filter migration that skipped its sentinel', LAB_FILTER_NEW, false, /FAIL lab-filter migration sentinel missing/],
  ['a lab-filter rewrite after the migration already ran', LAB_FILTER_NEW, 'pre', /FAIL cfg.carlos.properties.sha changed/],
  ['an upgrade that left the old lab filter in place', LAB_FILTER_OLD, 'post', /FAIL HL7_LAB_MEASUREMENT_FILTER was not migrated/],
]) {
  test(`upgrade verifier rejects ${name}`, t => {
    const fixture = labFilterFixture(t, { sentinel: sentinel === 'pre' });
    fs.writeFileSync(fixture.props, `rx_fax_enabled=true\n${after}\nother=kept\n`);
    if (sentinel === 'post') fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, LAB_FILTER_SENTINEL), '');
    const result = fixture.run('deb-upgrade-verify.sh');
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stdout, error);
  });
}

test('upgrade verifier rejects an unmigrated lab filter when the skeleton itself still has the old line', t => {
  // The skeleton's digest equals the old one, so "post equals skeleton" proves nothing.
  const fixture = labFilterFixture(t, { skelLine: LAB_FILTER_OLD });
  fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, LAB_FILTER_SENTINEL), '');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /FAIL HL7_LAB_MEASUREMENT_FILTER was not migrated/);
  assert.doesNotMatch(result.stdout, /PASS the old stock HL7_LAB_MEASUREMENT_FILTER was migrated/);
});

test('upgrade verifier rejects a changed lab filter override written with an indent', t => {
  // java.util.Properties reads the indented line as a definition, so its change is a changed setting.
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  fs.writeFileSync(props, `rx_fax_enabled=true\n${LAB_FILTER_OLD}\nother=kept\n  HL7_LAB_MEASUREMENT_FILTER=^\\\\d+\n`);
  fixture.baseline();
  fs.writeFileSync(props, `rx_fax_enabled=true\n${LAB_FILTER_OLD}\nother=kept\n  HL7_LAB_MEASUREMENT_FILTER=.*\n`);
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /FAIL cfg.carlos.properties.sha changed/);
});

test('upgrade verifier rejects a deleted lab filter when the skeleton lacks the key', t => {
  // No definition on either side digests alike; a deletion is not the sanctioned rewrite.
  const fixture = labFilterFixture(t, { skelLine: null });
  fs.writeFileSync(fixture.props, 'rx_fax_enabled=true\nother=kept\n');
  fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, LAB_FILTER_SENTINEL), '');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /FAIL cfg.carlos.properties.sha changed/);
});

// The one-time Help-link rewrite: the old stock resource_base_url (a path that now returns 404) becomes the line the
// package's skeleton carries. The old line is the postinst's HELP_URL_OLD; the new one is the shipped carlos.properties.
const HELP_URL_OLD = /^\s*HELP_URL_OLD='([^']*)'$/m.exec(
  fs.readFileSync(path.join(__dirname, '..', 'debian', 'carlos-emr.postinst'), 'utf8'))[1];
const HELP_URL_NEW = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'carlos.properties'), 'utf8')
  .split('\n').find(line => line.startsWith('resource_base_url='));
const HELP_URL_SENTINEL = '.help-url-default-migrated';

function helpUrlFixture(t, { sentinel = false } = {}) {
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  fs.writeFileSync(props, `rx_fax_enabled=true\n${HELP_URL_OLD}\nother=kept\n`);
  if (sentinel) fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, HELP_URL_SENTINEL), '');
  fixture.baseline();
  return { ...fixture, props };
}

test('upgrade verifier states the postinst old Help link and the shipped new one', () => {
  const verify = fs.readFileSync(path.join(__dirname, 'deb-upgrade-verify.sh'), 'utf8');
  assert.ok(verify.includes(`HELP_URL_OLD='${HELP_URL_OLD}'`), 'verifier old Help link');
  assert.ok(verify.includes(`HELP_URL_NEW='${HELP_URL_NEW}'`), 'verifier new Help link');
  assert.equal(HELP_URL_OLD, 'resource_base_url=http://oscarmanual.org/14/');
  assert.notEqual(HELP_URL_NEW, HELP_URL_OLD);
});

test('upgrade verifier accepts the one-time Help-link migration', t => {
  const fixture = helpUrlFixture(t);
  fs.writeFileSync(fixture.props, `rx_fax_enabled=true\n${HELP_URL_NEW}\nother=kept\n`);
  fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, HELP_URL_SENTINEL), '');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /changed only by the one-time stock-default migrations .*Help link: yes/);
  assert.match(result.stdout, /PASS the old stock Help link was migrated/);
  assert.match(result.stdout, /PASS Help-link migration sentinel written/);
});

for (const [name, after, sentinel, error] of [
  ['an unsanctioned change to the Help link', 'resource_base_url=https://help.example.org/', false, /FAIL cfg.carlos.properties.sha changed/],
  ['a Help-link migration that skipped its sentinel', HELP_URL_NEW, false, /FAIL Help-link migration sentinel missing/],
  ['a Help-link rewrite after the migration already ran', HELP_URL_NEW, 'pre', /FAIL cfg.carlos.properties.sha changed/],
  ['an upgrade that left the old Help link in place', HELP_URL_OLD, 'post', /FAIL the Help link was not migrated/],
]) {
  test(`upgrade verifier rejects ${name}`, t => {
    const fixture = helpUrlFixture(t, { sentinel: sentinel === 'pre' });
    fs.writeFileSync(fixture.props, `rx_fax_enabled=true\n${after}\nother=kept\n`);
    if (sentinel === 'post') fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, HELP_URL_SENTINEL), '');
    const result = fixture.run('deb-upgrade-verify.sh');
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stdout, error);
  });
}

test('upgrade verifier accepts an operator Help link that did not change', t => {
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  fs.writeFileSync(props, 'rx_fax_enabled=true\nresource_base_url=https://help.example.org/\nother=kept\n');
  fixture.baseline();
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /PASS cfg.carlos.properties.sha preserved/);
});

for (const [name, overrides, error, status] of [
  ['failed post snapshot', { FAIL_QUERY: 'COUNT(*) FROM appointment' }, /post-upgrade baseline failed/, 2],
  ['failed document inventory', { FAIL_QUERY: 'SELECT HEX' }, /document inventory query failed/, 2],
  ['failed health check', { HEALTH_FAIL: '1' }, /FAIL carlos-ctl health check failed/, 1],
]) {
  test(`upgrade verifier rejects ${name}`, t => {
    const fixture = setup(t); fixture.baseline();
    const result = fixture.run('deb-upgrade-verify.sh', overrides);
    assert.equal(result.status, status, result.stdout + result.stderr);
    assert.match(result.stdout + result.stderr, error);
  });
}

test('upgrade verifier rejects missing required values instead of comparing empty strings', t => {
  const fixture = setup(t);
  fs.writeFileSync(fixture.env.PRE, fixture.baseline().replace(/^admin.hash=.*$/m, 'admin.hash='));
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 2); assert.match(result.stderr, /empty required snapshot field/);
});

test('upgrade verifier refuses legacy raw-credential snapshots before printing a diff', t => {
  const fixture = setup(t);
  fs.writeFileSync(fixture.env.PRE, 'admin.hash=legacy-authentication-hash\n');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 2); assert.match(result.stderr, /fresh format-2 PRE snapshot/);
  assert.ok(!result.stdout.includes('legacy-authentication-hash'));
});

test('upgrade verifier detects credential changes without exposing credential digests in the diff', t => {
  const fixture = setup(t); fixture.baseline();
  const result = fixture.run('deb-upgrade-verify.sh', { PASSWORD_DIGEST: 'c'.repeat(64) });
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /password CHANGED/);
  assert.ok(!result.stdout.includes('a'.repeat(64)) && !result.stdout.includes('c'.repeat(64)));
});

test('upgrade verifier rejects a lost stored document', t => {
  const fixture = setup(t); fixture.baseline();
  fs.unlinkSync(path.join(fixture.env.CARLOS_STATE_DIR, 'CarlosDocument/carlos/document/example file.pdf'));
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /1 document file\(s\) missing/);
});

test('upgrade verifier accepts a properties key commented out and re-appended with the same value', t => {
  // What an alpha13 renderer's postrm plus the new init-config do to the renderer keys.
  const fixture = setup(t); fixture.baseline();
  fs.writeFileSync(path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties'),
    '#consultation_signature_enabled=true\nrx_fax_enabled=true\nconsultation_signature_enabled = true\n');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /layout changed but every active setting is preserved/);
});

test('upgrade verifier accepts a layout change combined with the sanctioned OSCAR-defaults migration', t => {
  // An alpha13 upgrade does both: init-config moves the renderer keys and the
  // postinst runs the one-time stock-default migration.
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  fs.writeFileSync(props, 'rx_fax_enabled=false\nrenderer_url=http://127.0.0.1:9515/t\nother=kept\n');
  fixture.baseline();
  fs.writeFileSync(props, 'rx_fax_enabled=true\n#renderer_url=http://127.0.0.1:9515/t\nother=kept\nrenderer_url = http://127.0.0.1:9515/t\n');
  fs.writeFileSync(path.join(fixture.env.CARLOS_STATE_DIR, '.oscar-feature-defaults-migrated'), '');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /layout changed but every active setting is preserved/);
});

test('upgrade verifier rejects a layout change that hides an unsanctioned migrated-key change', t => {
  // The migrated key moves the wrong way while another key is re-laid out.
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  fs.writeFileSync(props, 'rx_fax_enabled=true\nrenderer_url=http://127.0.0.1:9515/t\n');
  fixture.baseline();
  fs.writeFileSync(props, 'rx_fax_enabled=false\n#renderer_url=http://127.0.0.1:9515/t\nrenderer_url = http://127.0.0.1:9515/t\n');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /FAIL cfg.carlos.properties.sha changed/);
});

test('upgrade verifier rejects a changed properties value even when the key moved', t => {
  const fixture = setup(t); fixture.baseline();
  fs.writeFileSync(path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties'),
    '#consultation_signature_enabled=true\nrx_fax_enabled=true\nconsultation_signature_enabled = false\n');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /FAIL cfg.carlos.properties.sha changed/);
});

// The Query by Example reporting account (#3372): carlos-ctl db-users appends its two
// keys on the first upgrade whose package ships the grant list.
function reportingFixture(t, { before = '', after = '', shipsGrantList = true } = {}) {
  const fixture = setup(t);
  const props = path.join(fixture.env.CARLOS_ETC_DIR, 'carlos.properties');
  const grantList = path.join(fixture.env.CARLOS_SHARE_DIR, 'webapp/carlos/WEB-INF/classes/db/reporting/carlos_report_grants.sql');
  fs.writeFileSync(props, `consultation_signature_enabled=true\n${before}`);
  const snapshot = fixture.baseline();
  fs.writeFileSync(props, `consultation_signature_enabled=true\n${after}`);
  if (shipsGrantList) {
    fs.mkdirSync(path.dirname(grantList), { recursive: true }); fs.writeFileSync(grantList, '-- fixture\n');
  }
  return { fixture, snapshot };
}

test('upgrade verifier accepts the reporting account recorded by the upgrade that ships the grant list', t => {
  const secret = crypto.randomBytes(16).toString('hex');
  const { fixture, snapshot } = reportingFixture(t,
    { after: `db_reporting_username = carlos_report\ndb_reporting_password = ${secret}\n` });
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /reporting account: added/);
  assert.match(result.stdout, /records the Query by Example reporting account/);
  assert.ok(!snapshot.includes(secret));
  assert.ok(!fs.readFileSync(fixture.env.POST, 'utf8').includes(secret));
});

test('upgrade verifier keeps an already recorded reporting account as unchanged', t => {
  const keys = `db_reporting_username=carlos_report\ndb_reporting_password=${crypto.randomBytes(16).toString('hex')}\n`;
  const { fixture } = reportingFixture(t, { before: keys, after: keys });
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /cfg\.carlos\.properties\.sha preserved/);
});

// Generated per run: the fixtures carry no credential-looking literal.
const [firstPw, secondPw] = [crypto.randomBytes(16).toString('hex'), crypto.randomBytes(16).toString('hex')];
for (const [name, before, after] of [
  ['a reporting password replaced across the upgrade',
    `db_reporting_username=carlos_report\ndb_reporting_password=${firstPw}\n`,
    `db_reporting_username=carlos_report\ndb_reporting_password=${secondPw}\n`],
  ['a reporting account renamed across the upgrade',
    `db_reporting_username=carlos_report\ndb_reporting_password=${firstPw}\n`,
    `db_reporting_username=someone_else\ndb_reporting_password=${firstPw}\n`],
  ['a reporting account added under another name', '',
    `db_reporting_username=carlos\ndb_reporting_password=${firstPw}\n`],
]) {
  test(`upgrade verifier rejects ${name}`, t => {
    const { fixture } = reportingFixture(t, { before, after });
    const result = fixture.run('deb-upgrade-verify.sh');
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stdout, /FAIL cfg\.carlos\.properties\.sha changed: .*reporting account: .*sanctioned=0/);
  });
}

test('upgrade verifier fails when the package ships the grant list but no reporting account is recorded', t => {
  const { fixture } = reportingFixture(t);
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /ships the Query by Example grant list but carlos\.properties does not record carlos_report/);
});

test('upgrade verifier asks nothing of a package without the grant list', t => {
  const { fixture } = reportingFixture(t, { shipsGrantList: false });
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.doesNotMatch(result.stdout, /reporting account/);
});

test('upgrade verifier refuses a POST hard link to PRE without overwriting the baseline', t => {
  const fixture = setup(t); const original = fixture.baseline();
  fs.linkSync(fixture.env.PRE, fixture.env.POST);
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 2); assert.match(result.stderr, /different files/);
  assert.equal(fs.readFileSync(fixture.env.PRE, 'utf8'), original);
});

test('upgrade verifier rejects a failed upgrade even when the unchanged application is healthy', t => {
  const fixture = setup(t); fixture.baseline();
  fs.writeFileSync(fixture.env.UPGRADE_LOG, 'UPGRADE_RC=100\n');
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /FAIL upgrade command failed/);
});

test('upgrade baseline ignores client defaults that would suppress SQL failures with force', t => {
  const fixture = setup(t);
  const result = fixture.run('deb-upgrade-baseline.sh', {
    FAIL_QUERY: 'COUNT(*) FROM appointment', SIMULATE_DEFAULTS_FORCE: '1',
  });
  assert.equal(result.status, 1); assert.match(result.stderr, /query failure/);
});

test('upgrade verifier checks the configured alternate document directory', t => {
  const fixture = setup(t);
  fixture.env.DOC_DIR = path.join(fixture.root, 'custom documents');
  fs.renameSync(path.join(fixture.env.CARLOS_STATE_DIR, 'CarlosDocument/carlos/document'), fixture.env.DOC_DIR);
  fixture.baseline();
  const result = fixture.run('deb-upgrade-verify.sh');
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /every stored document has a file or inline HTML/);
});

for (const [name, body, status] of [
  ['inline HTML', '<p>Stored HTML</p>', 0],
  ['empty inline HTML', '', 1],
  ['Java-trim whitespace inline HTML', ' \t\r\n\x1f', 1],
  ['nonbreaking-space inline HTML', '\u00a0', 0],
]) {
  test(`upgrade verifier handles a missing file with ${name}`, t => {
    const fixture = setup(t); fixture.baseline();
    fs.unlinkSync(path.join(fixture.env.CARLOS_STATE_DIR, 'CarlosDocument/carlos/document/example file.pdf'));
    const result = fixture.run('deb-upgrade-verify.sh', { INLINE_BODY: body });
    assert.equal(result.status, status, result.stdout + result.stderr);
    assert.match(result.stdout, status === 0 ? /every stored document has a file or inline HTML/
      : /missing without an inline HTML fallback/);
  });
}

test('upgrade baseline rejects a relative document directory before querying the database', t => {
  const fixture = setup(t);
  const result = fixture.run('deb-upgrade-baseline.sh', { DOC_DIR: '.' });
  assert.equal(result.status, 2); assert.match(result.stderr, /absolute document directory/);
  assert.equal(fs.existsSync(fixture.env.COMMAND_LOG), false);
});

for (const [phase, baselineExit, expected] of [
  ['baseline', 2, /FAIL pre-split baseline failed/],
  ['verification', 0, /FAIL split upgrade verification failed/],
]) {
  test(`split matrix stops when ${phase} exits nonzero without printing FAIL assertions`, t => {
    const fixture = setup(t);
    const matrix = path.join(fixture.root, 'deb-split-matrix.sh');
    fs.copyFileSync(path.join(__dirname, 'deb-split-matrix.sh'), matrix);
    for (const [name, status] of [['deb-upgrade-baseline.sh', baselineExit], ['deb-upgrade-verify.sh', 2]]) {
      fs.writeFileSync(path.join(fixture.root, name), `#!/bin/bash\nexit ${status}\n`, { mode: 0o755 });
    }
    // Intercept every package/service mutation; only the matrix control flow is real.
    const stub = `#!/bin/bash
case "$0" in
  */curl) printf 200 ;;
  */mariadb) printf 1 ;;
  */dpkg-deb) printf '1.0\\n' ;;
  */dpkg-query) [[ "$*" == *Status-Status* ]] && printf installed || printf '1.0' ;;
  */dpkg) printf 'carlos-emr: /usr/sbin/carlos-ctl\\n' ;;
  */apt-get) printf '%s\\0' "$@" >> "$COMMAND_LOG" ;;
  */debconf-set-selections) cat >/dev/null ;;
esac
exit 0
`;
    for (const name of ['curl', 'mariadb', 'dpkg-deb', 'dpkg-query', 'dpkg', 'apt-get',
      'debconf-set-selections', 'python3', 'carlos-ctl']) {
      fs.writeFileSync(path.join(fixture.root, 'bin', name), stub, { mode: 0o755 });
    }
    const result = spawnSync('bash', [matrix], { encoding: 'utf8', timeout: 10000,
      env: { ...fixture.env, WORK: path.join(fixture.root, 'work'), PRESPLIT_EMR: 'old.deb',
        SPLIT_EMR: 'new.deb', CTL_A: 'ctl-a.deb', CTL_B: 'ctl-b.deb',
        PRESPLIT_DRUGREF: 'old DrugRef.deb', SPLIT_DRUGREF: 'new DrugRef.deb' } });
    assert.ifError(result.error);
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stdout, expected);
    const transactions = fs.readFileSync(fixture.env.COMMAND_LOG, 'utf8');
    const argumentsSeen = transactions.split('\0');
    assert.ok(argumentsSeen.includes('old DrugRef.deb'));
    if (phase === 'verification') assert.ok(argumentsSeen.includes('new DrugRef.deb'));
    assert.ok(!transactions.includes('ctl-b.deb') && !transactions.includes('purge'));
    if (phase === 'baseline') assert.ok(!transactions.includes('new.deb'));
  });
}
