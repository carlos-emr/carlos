/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Unit tests for scripts/deb-server-log-phi-scan.sh, driven through --input (the journal) and
// --catalina-dir (Tomcat's own logs) so no journal is needed. Every fixture line is fictitious.
//
// The scan looks for patient data in the server log at EVERY level and prints only counts and
// logger names, so most of these tests assert two things: what is reported and what is NOT
// printed (the needle, the line, the message).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const SCRIPT = path.join(__dirname, 'deb-server-log-phi-scan.sh');

// A run marker and a synthetic 10-digit health number, both fictitious.
const MARKER = 'FAKE-PW0123456789abcdef';
const LOGIN = 'FAKEPW0123456789abcdefa';
const HIN = '0123456789';

function tmp(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-phi-scan-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  return dir;
}

/**
 * Run the scan on fixture lines. `catalina` maps a file name to its lines; the catalina directory is
 * passed only when it is given, so a test never reads the host's own /var/log.
 */
function scan(t, journal, { catalina = null, args = [], env = {} } = {}) {
  const dir = tmp(t);
  const input = path.join(dir, 'app.log');
  fs.writeFileSync(input, journal.join('\n') + (journal.length ? '\n' : ''));
  const catalinaArgs = [];
  if (catalina) {
    const catalinaDir = path.join(dir, 'tomcat');
    fs.mkdirSync(catalinaDir);
    for (const [name, lines] of Object.entries(catalina)) fs.writeFileSync(path.join(catalinaDir, name), lines.join('\n') + '\n');
    catalinaArgs.push('--catalina-dir', catalinaDir);
  }
  const result = spawnSync('bash', [SCRIPT, '--input', input, ...catalinaArgs, ...args], {
    encoding: 'utf8',
    env: { ...process.env, CARLOS_LOG_AUDIT_SINCE: '', CARLOS_LOG_PHI_SCAN_HINS: '', CARLOS_LOG_PHI_SCAN_MARKERS: '', ...env },
  });
  return { status: result.status, out: result.stdout, err: result.stderr, all: result.stdout + result.stderr };
}

const QUIET = [
  '2026-10-08 04:40:15,287 INFO  app.Something (Something.java:1) - started',
  '2026-10-08 04:40:16,001 WARN  app.HttpMethodGuardFilter (HttpMethodGuardFilter.java:580) - Blocked HEAD request',
];

test('should pass and say how many lines it read when nothing in the window matches', (t) => {
  const r = scan(t, QUIET);
  assert.equal(r.status, 0, r.all);
  assert.match(r.out, /^PASS log PHI scan: no match \(journal 2 line\(s\)/m);
  assert.doesNotMatch(r.out, /^HIT /m);
});

test('should report a run marker logged at INFO by logger and count, never by text', (t) => {
  const r = scan(t, [
    ...QUIET,
    `2026-10-08 04:41:00,100 INFO  note.NoteSaver (NoteSaver.java:42) - saved note for ${MARKER} with text about the patient`,
  ]);
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +INFO +note\.NoteSaver +marker +events=1 +lines=1$/m);
  assert.match(r.out, /^FAIL log PHI scan: 1 logger\(s\) matched/m);
  assert.doesNotMatch(r.all, new RegExp(`${MARKER}|with text about the patient|saved note`));
});

test('should find a marker at WARN and DEBUG too, not only at ERROR', (t) => {
  const r = scan(t, [
    `2026-10-08 04:41:00,100 WARN  warn.Logger (Logger.java:1) - ${MARKER} first`,
    `2026-10-08 04:41:00,200 DEBUG debug.Logger (Logger.java:2) - ${MARKER} second`,
    `2026-10-08 04:41:00,300 TRACE trace.Logger (Logger.java:3) - ${MARKER} third`,
  ]);
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +WARN +warn\.Logger /m);
  assert.match(r.out, /^HIT +journal +DEBUG +debug\.Logger /m);
  assert.match(r.out, /^HIT +journal +TRACE +trace\.Logger /m);
  assert.match(r.out, /^FAIL log PHI scan: 3 logger\(s\) matched/m);
});

test('should attribute a stack-trace line that carries the text to the logger that logged the event', (t) => {
  // The shape of finding 141: the exception message holds the text, on a line with no logger of its own.
  const r = scan(t, [
    '2026-10-08 04:42:00,100 ERROR action.LogAction (LogAction.java:235) - failed to parse the demographic number',
    `java.lang.NumberFormatException: For input string: "${MARKER}-note and issues"`,
    '\tat java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)',
    `Caused by: java.lang.IllegalArgumentException: ${MARKER} again`,
    '2026-10-08 04:42:01,100 INFO  app.Something (Something.java:1) - unrelated',
  ]);
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +ERROR +action\.LogAction +marker +events=1 +lines=2$/m);
  assert.doesNotMatch(r.out, /app\.Something/);
  assert.doesNotMatch(r.all, new RegExp(`${MARKER}|NumberFormatException|input string`));
});

test('should count an event once per needle class however many of its lines match', (t) => {
  const r = scan(t, [
    `2026-10-08 04:42:00,100 ERROR a.B (B.java:1) - ${MARKER}`,
    `x ${MARKER}`,
    `y ${MARKER}`,
    `2026-10-08 04:42:01,100 ERROR a.B (B.java:1) - ${MARKER}`,
  ], { args: ['--hin', HIN] });
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +ERROR +a\.B +marker +events=2 +lines=4$/m);
});

test('should find a health number given by --hin, also written with spaces or dashes, and report only the class', (t) => {
  const r = scan(t, [
    `2026-10-08 04:43:00,100 WARN  demographic.Search (Search.java:9) - looked up ${HIN}`,
    '2026-10-08 04:43:01,100 WARN  demographic.Spaced (Spaced.java:9) - card 0123 456 789 was read',
    '2026-10-08 04:43:02,100 WARN  demographic.Dashed (Dashed.java:9) - card 0123-456-789 was read',
    '2026-10-08 04:43:03,100 WARN  demographic.Other (Other.java:9) - card 1123456789 was read',
  ], { args: ['--hin', HIN] });
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +WARN +demographic\.Search +hin +events=1 +lines=1$/m);
  assert.match(r.out, /^HIT +journal +WARN +demographic\.Spaced +hin /m);
  assert.match(r.out, /^HIT +journal +WARN +demographic\.Dashed +hin /m);
  assert.doesNotMatch(r.out, /demographic\.Other/);
  assert.doesNotMatch(r.all, /0123456789|0123 456 789|0123-456-789/);
});

test('should take health numbers and extra markers from the environment, separated by commas or spaces', (t) => {
  const r = scan(t, [
    '2026-10-08 04:44:00,100 INFO  a.One (One.java:1) - fixture-number 9999000011 seen',
    '2026-10-08 04:44:01,100 INFO  a.Two (Two.java:1) - fixture-number 9999000022 seen',
    '2026-10-08 04:44:02,100 INFO  a.Three (Three.java:1) - EXTRA-MARKER-ONE seen',
  ], { env: { CARLOS_LOG_PHI_SCAN_HINS: '9999000011, 9999000022', CARLOS_LOG_PHI_SCAN_MARKERS: 'EXTRA-MARKER-ONE' } });
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +INFO +a\.One +hin /m);
  assert.match(r.out, /^HIT +journal +INFO +a\.Two +hin /m);
  assert.match(r.out, /^HIT +journal +INFO +a\.Three +marker /m);
  assert.doesNotMatch(r.all, /9999000011|9999000022|EXTRA-MARKER-ONE/);
});

test('should recognise the harness marker and its throwaway login names without being told them', (t) => {
  const r = scan(t, [
    `2026-10-08 04:45:00,100 INFO  p.Patient (Patient.java:1) - created ${MARKER}`,
    `2026-10-08 04:45:01,100 INFO  s.Login (Login.java:1) - signed in ${LOGIN}`,
  ]);
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +INFO +p\.Patient +marker /m);
  assert.match(r.out, /^HIT +journal +INFO +s\.Login +marker /m);
});

test('should read the Tomcat log format in the journal and in the catalina files, naming the class without its method', (t) => {
  const r = scan(t, [
    `08-Oct-2026 04:46:00.123 WARNING [http-nio-8080-exec-3] org.apache.catalina.core.StandardWrapperValve.invoke request for ${MARKER}`,
    'java.lang.Exception: no match here',
  ], {
    catalina: {
      'catalina.2026-10-08.log': [
        `08-Oct-2026 04:47:00.001 SEVERE [Catalina-utility-1] org.apache.tomcat.util.Something.run failed on ${MARKER}`,
        `\tat org.apache.tomcat.util.Something.run(Something.java:1) ${MARKER}`,
        '08-Oct-2026 04:47:01.001 INFO [main] org.apache.catalina.startup.Other.start started',
      ],
    },
  });
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +WARNING +org\.apache\.catalina\.core\.StandardWrapperValve +marker +events=1 +lines=1$/m);
  assert.match(r.out, /^HIT +catalina +SEVERE +org\.apache\.tomcat\.util\.Something +marker +events=1 +lines=2$/m);
  assert.doesNotMatch(r.out, /org\.apache\.catalina\.startup\.Other/);
  assert.match(r.out, /catalina 3 line\(s\)/);
});

test('should leave out catalina events before --since and keep the continuation lines of the ones after it', (t) => {
  const r = scan(t, [...QUIET], {
    args: ['--since', '2026-10-08 12:00:00'],
    catalina: {
      'catalina.2026-10-08.log': [
        `08-Oct-2026 11:59:59.000 SEVERE [t] old.Logger.m ${MARKER}`,
        `\tat old.Logger.m ${MARKER}`,
        `08-Oct-2026 12:00:00.000 SEVERE [t] new.Logger.m ${MARKER}`,
        `\tat new.Logger.m ${MARKER}`,
      ],
    },
  });
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +catalina +SEVERE +new\.Logger +marker +events=1 +lines=2$/m);
  assert.doesNotMatch(r.out, /old\.Logger/);
});

test('should name a match that precedes any event header rather than drop it', (t) => {
  const r = scan(t, [`a continuation with ${MARKER} and no header above it`, ...QUIET]);
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +- +\(no-event-header\) +marker +events=1 +lines=1$/m);
});

test('should search only the needles it was given when told to, so a check can scan just its own fixture', (t) => {
  // Another run's marker, and the harness prefix itself, are not this run's data.
  const r = scan(t, [
    '2026-10-08 04:44:00,100 INFO  a.Other (Other.java:1) - saved FAKE-PWffffffffffffffff and FAKEPWeeeeeeeeeeeeeeeea',
    '2026-10-08 04:44:01,100 INFO  a.Mine (Mine.java:1) - saved MINE-FIXTURE-NAME',
    `2026-10-08 04:44:02,100 INFO  a.Number (Number.java:1) - looked up ${HIN}`,
  ], { args: ['--only-given', '--marker', 'MINE-FIXTURE-NAME', '--hin', HIN] });
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +INFO +a\.Mine +marker /m);
  assert.match(r.out, /^HIT +journal +INFO +a\.Number +hin /m);
  assert.doesNotMatch(r.out, /a\.Other/);
  assert.match(r.out, /^FAIL log PHI scan: 2 logger\(s\) matched/m);
});

test('should accept a short unique token as a marker, because finding 144 logs a prescription instruction only while it is shorter than six characters', (t) => {
  const r = scan(t, [
    '2026-10-08 04:44:00,100 ERROR pageUtil.RxWriteScriptForm (RxWriteScriptForm.java:267) - drug special is either null or empty : ZQ7K9',
    'java.lang.IllegalArgumentException: special is null or empty',
    '2026-10-08 04:44:01,100 ERROR pageUtil.RxWriteScriptForm (RxWriteScriptForm.java:267) - drug special is either null or empty : null',
  ], { args: ['--only-given', '--marker', 'ZQ7K9'] });
  assert.equal(r.status, 1, r.all);
  assert.match(r.out, /^HIT +journal +ERROR +pageUtil\.RxWriteScriptForm +marker +events=1 +lines=1$/m);
  assert.doesNotMatch(r.all, /ZQ7K9/);
  // Three characters would match almost any line; that is still refused.
  assert.equal(scan(t, QUIET, { args: ['--marker', 'abc'] }).status, 2);
});

test('should refuse --only-given when no needle was given, rather than pass having searched for nothing', (t) => {
  const r = scan(t, QUIET, { args: ['--only-given'] });
  assert.equal(r.status, 2, r.all);
  assert.match(r.err, /--only-given needs at least one/);
});

test('should not print the needles it was given when nothing matches', (t) => {
  const r = scan(t, QUIET, { args: ['--hin', HIN, '--marker', 'A-FIXTURE-MARKER'] });
  assert.equal(r.status, 0, r.all);
  assert.doesNotMatch(r.all, new RegExp(`${HIN}|A-FIXTURE-MARKER`));
});

test('should exit 2 rather than pass when no log line was read', (t) => {
  const r = scan(t, []);
  assert.equal(r.status, 2, r.all);
  assert.match(r.err, /read no log line/);
});

test('should refuse an unknown argument, a missing value, a needle too short to mean anything and a --since that is not a date', (t) => {
  assert.equal(spawnSync('bash', [SCRIPT, '--bogus'], { encoding: 'utf8' }).status, 2);
  for (const option of ['--since', '--hin', '--marker', '--unit', '--catalina-dir', '--input']) {
    const r = spawnSync('bash', [SCRIPT, option], { encoding: 'utf8' });
    assert.equal(r.status, 2, `${option} without a value: ${r.stderr}`);
  }
  const short = scan(t, QUIET, { args: ['--hin', '123'] });
  assert.equal(short.status, 2, short.all);
  assert.match(short.err, /too short|not a valid/);
  const control = scan(t, QUIET, { args: ['--marker', 'two\nlines'] });
  assert.equal(control.status, 2, control.all);
  const date = spawnSync('bash', [SCRIPT, '--since', 'not a date', '--input', '/dev/null'], { encoding: 'utf8' });
  assert.equal(date.status, 2);
  assert.match(date.stderr, /--since is not a date/);
});

test('should not echo a bad needle in its usage error', (t) => {
  const r = scan(t, QUIET, { args: ['--hin', 'ab'] });
  assert.equal(r.status, 2, r.all);
  assert.doesNotMatch(r.all, /--hin ab|'ab'/);
});

test('should be executable and carry the project header', () => {
  const mode = fs.statSync(SCRIPT).mode;
  assert.ok(mode & 0o111, 'the scan must be executable (the live run calls it by path)');
  assert.match(fs.readFileSync(SCRIPT, 'utf8'), /Copyright \(c\) 2026 CARLOS Contributors/);
});
