/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Unit tests for scripts/deb-server-log-audit.sh, driven through --input so no
// journal is needed, and the contract of its shipped baseline.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const ROOT = path.join(__dirname, '..');
const SCRIPT = path.join(__dirname, 'deb-server-log-audit.sh');
const BASELINE = path.join(__dirname, 'lib', 'server-log-baseline.tsv');
const FINDINGS = path.join(ROOT, 'docs', 'ui-tests', 'app-findings-log.md');
const SIGNATURES = path.join(__dirname, 'fixtures', 'server-log-signatures-2026.08.txt');

function tmp(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-log-audit-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  return dir;
}

// A baseline entry's first column is a regular expression by contract: the audit script greps with it. These tests
// compile the checked-in baseline to prove what it explains. The pattern comes from the repository's own
// scripts/lib/server-log-baseline.tsv, never from a request or from a log line, so nobody can steer it into a
// catastrophic pattern; every other construction in this file goes through here so the exception is stated once.
function baselinePattern(source) {
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
  return new RegExp(source);
}

function audit(t, lines, { baseline = '# none\n', args = [] } = {}) {
  const dir = tmp(t);
  const input = path.join(dir, 'app.log');
  const base = path.join(dir, 'baseline.tsv');
  fs.writeFileSync(input, lines.join('\n') + '\n');
  fs.writeFileSync(base, baseline);
  const result = spawnSync('bash', [SCRIPT, '--input', input, '--baseline', base, ...args], { encoding: 'utf8' });
  return { status: result.status, out: result.stdout, err: result.stderr };
}

// Fictitious content shaped like the packaged log4j layout.
const NPE_EVENT = [
  '2026-10-08 04:40:11,612 ERROR utility.DbConnectionFilter (DbConnectionFilter.java:96) - Unexpected error for FAKE-Doe, FAKE-Jane HIN 9876543210',
  'java.lang.NullPointerException: Cannot invoke "String.split(String, int)" because "s" is null',
  '\tat io.github.carlos_emr.carlos.utility.SomeFilter.doFilter(SomeFilter.java:10)',
  '\tat io.github.carlos_emr.carlos.form.pdfservlet.FrmCustomedPDFServlet.parseSCAddress(FrmCustomedPDFServlet.java:981)',
  '\tat io.github.carlos_emr.carlos.form.pdfservlet.FrmCustomedPDFServlet.service(FrmCustomedPDFServlet.java:254)',
];

test('should reduce an ERROR event to logger, location, first non-filter CARLOS frame and exception class', (t) => {
  const r = audit(t, NPE_EVENT);
  assert.equal(r.status, 1, r.out + r.err);
  assert.match(r.out, /UNKNOWN +1 +ERROR utility\.DbConnectionFilter \(DbConnectionFilter\.java:96\) @FrmCustomedPDFServlet\.parseSCAddress :: java\.lang\.NullPointerException\n/);
  assert.match(r.out, /^FAIL server log audit: 1 unexplained signature\(s\), 0 known, 1 event\(s\)/m);
});

test('should keep message text out of the report by default', (t) => {
  const r = audit(t, NPE_EVENT);
  assert.doesNotMatch(r.out, /FAKE-Doe|9876543210|Unexpected error/);
  const shown = audit(t, NPE_EVENT, { args: ['--show-messages'] });
  assert.match(shown.out, /e\.g\. Unexpected error for FAKE-Doe/);
});

test('should name the request path and status without the query string', (t) => {
  const r = audit(t, [
    '2026-10-08 04:43:27,461 ERROR utility.ErrorPageLogger (ErrorPageLogger.java:135) - errorpage.jsp captured exception (method=GET, uri=/carlos/demographic/DemographicEdit?demographic_no=4242, status=500)',
    'jakarta.servlet.ServletException: JSP file [x.jsp] not found',
  ]);
  assert.match(r.out, /uri=\/carlos\/demographic\/DemographicEdit status=500 :: jakarta\.servlet\.ServletException/);
  assert.doesNotMatch(r.out, /4242|demographic_no/);
});

test('should key a CSRF rejection on its method and reason, never on the user or address', (t) => {
  const r = audit(t, [
    '2026-10-08 04:41:40,319 ERROR action.Log (Log.java:73) - CSRF violation: (user:<anonymous>, ip:127.0.0.1, method:POST, error:Required Token is missing from the Request)',
    '2026-10-08 04:41:41,319 ERROR action.Log (Log.java:73) - CSRF violation: (user:clinician1, ip:10.0.0.7, method:POST, error:Required Token is missing from the Request)',
    '2026-10-08 04:41:42,319 ERROR action.Log (Log.java:73) - CSRF violation: (user:<anonymous>, ip:127.0.0.1, method:POST, error:Request Token does not match the Master Token)',
  ]);
  // CARLOS's session login is invisible to CSRFGuard (every packaged-install
  // violation reads user:<anonymous>), so the user cannot split probes from
  // a page that lost its token; the two "missing" events are one signature.
  assert.match(r.out, /^UNKNOWN +2 +ERROR action\.Log \(Log\.java:73\) csrf-method:POST csrf-error:Required Token is missing from the Request$/m);
  assert.match(r.out, /^UNKNOWN +1 +ERROR action\.Log \(Log\.java:73\) csrf-method:POST csrf-error:Request Token does not match the Master Token$/m);
  assert.doesNotMatch(r.out, /clinician1|10\.0\.0\.7|anonymous/);
});

test('should ignore WARN and INFO lines and pass an empty window', (t) => {
  const r = audit(t, [
    '2026-10-08 04:40:15,286 WARN  app.HttpMethodGuardFilter (HttpMethodGuardFilter.java:580) - Blocked HEAD request',
    '2026-10-08 04:40:15,287 INFO  app.Something (Something.java:1) - started',
  ]);
  assert.equal(r.status, 0, r.out);
  assert.match(r.out, /^PASS server log audit: no unexplained signature \(0 known, 0 event\(s\)/m);
});

test('should report a baselined signature as KNOWN with its reason and pass', (t) => {
  const r = audit(t, NPE_EVENT, {
    baseline: '^ERROR utility\\.DbConnectionFilter .*@FrmCustomedPDFServlet\\.parseSCAddress \tfixture reason #1\n',
  });
  assert.equal(r.status, 0, r.out);
  assert.match(r.out, /KNOWN +1 +ERROR utility\.DbConnectionFilter[^\n]*\n +\(fixture reason #1\)/);
});

test('should refuse an unknown argument or a --since that is not a date', (t) => {
  assert.equal(spawnSync('bash', [SCRIPT, '--bogus'], { encoding: 'utf8' }).status, 2);
  const r = spawnSync('bash', [SCRIPT, '--since', 'not a date', '--input', '/dev/null'], { encoding: 'utf8' });
  assert.equal(r.status, 2);
  assert.match(r.stderr, /--since is not a date/);
});

test('should explain every shipped baseline entry with an issue, a recorded finding or an existing file', () => {
  const findings = fs.readFileSync(FINDINGS, 'utf8');
  const recorded = new Set([...findings.matchAll(/^\| (\d+) \|/gm)].map((m) => Number(m[1])));
  const entries = fs.readFileSync(BASELINE, 'utf8').split('\n').filter((line) => line.trim() && !line.startsWith('#'));
  assert.ok(entries.length > 0, 'the shipped baseline is empty');
  for (const entry of entries) {
    const fields = entry.split('\t');
    assert.equal(fields.length, 2, `baseline entry must be "regex<TAB>reason": ${entry}`);
    const [regex, reason] = fields;
    assert.ok(regex.startsWith('^'), `baseline regex must be anchored: ${regex}`);
    assert.doesNotThrow(() => baselinePattern(regex), `baseline regex does not compile: ${regex}`);
    const finding = reason.match(/app-findings-log\.md finding (\d+)/);
    const file = reason.match(/\b((?:scripts|docs)\/[\w./-]+\.(?:js|sh|md))\b/);
    assert.ok(/#\d{3,}/.test(reason) || finding || file, `baseline reason cites nothing: ${reason}`);
    if (finding) assert.ok(recorded.has(Number(finding[1])), `reason cites finding ${finding[1]}, which is not in the findings log`);
    if (file) assert.ok(fs.existsSync(path.join(ROOT, file[1])), `reason cites ${file[1]}, which does not exist`);
  }
});

test('should match each shipped baseline entry against the event it was written for', (t) => {
  // PHI-free reconstructions of the events each entry explains, so a regex
  // that silently stopped matching (a typo, an escaped space) fails here.
  const lines = [
    '2026-10-08 04:40:11,662 ERROR utility.ResponseSanitizationFilter (ResponseSanitizationFilter.java:341) - Uncaught exception escaped filter chain [uri=/carlos/form/createcustomedpdf correlationId=x]',
    'jakarta.servlet.ServletException: java.lang.NullPointerException: x',
    '\tat io.github.carlos_emr.carlos.form.pdfservlet.FrmCustomedPDFServlet.parseSCAddress(FrmCustomedPDFServlet.java:981)',
    'Caused by: java.lang.NullPointerException: x',
    ...NPE_EVENT,
    '2026-10-08 04:40:11,913 ERROR utility.ResponseSanitizationFilter (ResponseSanitizationFilter.java:393) - Sanitizing output-stream error response body [status=500 uri=/carlos/ws/rs/schedule/fetchProvidersApptsCount/not-a-date/not-a-date]',
    '2026-10-08 04:40:11,881 ERROR rest.ScheduleService (ScheduleService.java:657) - ScheduleService.listProviderAppointmentCounts error',
    'jakarta.ws.rs.WebApplicationException: HTTP 400 Bad Request',
    '\tat io.github.carlos_emr.carlos.managers.ScheduleManagerImpl.listProviderAppointmentCounts(ScheduleManagerImpl.java:351)',
    '2026-10-08 04:41:40,319 ERROR action.Log (Log.java:73) - CSRF violation: (user:<anonymous>, ip:127.0.0.1, method:POST, error:Required Token is missing from the Request)',
    '2026-10-08 04:37:51,825 ERROR pageUtil.PrintAppointmentReceipt2Action (PrintAppointmentReceipt2Action.java:173) - user home: /var/lib/carlos-emr',
    '2026-10-08 04:43:26,812 ERROR dao.DemographicPharmacyDaoImpl (DemographicPharmacyDaoImpl.java:149) - UNKNOWN PHARMACY TO UNLINK',
  ];
  const r = audit(t, lines, { baseline: fs.readFileSync(BASELINE, 'utf8') });
  assert.equal(r.status, 0, r.out);
  assert.doesNotMatch(r.out, /^UNKNOWN /m);
  assert.equal((r.out.match(/^KNOWN /gm) || []).length, 7, 'each reconstructed event should be its own KNOWN signature');
});

test('should match every shipped baseline entry against a signature a packaged install produced', () => {
  // The signatures the audit printed for the 2026.08.0~alpha19 promotion run
  // (no message text). An entry that matches none of them explains nothing.
  const signatures = fs.readFileSync(SIGNATURES, 'utf8').split('\n').filter((line) => line.trim() && !line.startsWith('#'));
  const entries = fs.readFileSync(BASELINE, 'utf8').split('\n').filter((line) => line.trim() && !line.startsWith('#'));
  for (const entry of entries) {
    const regex = baselinePattern(entry.split('\t')[0]);
    assert.ok(signatures.some((signature) => regex.test(signature)), `baseline entry matches no recorded signature: ${entry.split('\t')[0]}`);
  }
  // Still unexplained on that run, recorded or not (the flowsheet one is finding
  // 176): the baseline must not grow to swallow them (report, don't encode).
  for (const open of [
    'ERROR app.CarlosExceptionMappingInterceptor (CarlosExceptionMappingInterceptor.java:197) :: org.hibernate.exception.ConstraintViolationException java.sql.SQLIntegrityConstraintViolationException',
    'ERROR oscarMeasurements.HealthTrackerPage_jspf (HealthTrackerPage_jspf.java:652) @MeasurementFlowSheet.getMessages :: java.lang.IllegalStateException',
    'ERROR utility.ErrorPageLogger (ErrorPageLogger.java:135) uri=/carlos/eform/addEForm status=500 @CarlosExceptionMappingInterceptor.intercept :: org.apache.jasper.JasperException java.lang.NullPointerException',
  ]) {
    assert.ok(signatures.includes(open), `fixture lost an unexplained signature: ${open}`);
    assert.ok(!entries.some((entry) => baselinePattern(entry.split('\t')[0]).test(open)), `baseline swallows an unexplained defect: ${open}`);
  }
});

test('should mask every all-digit path segment so no record number reaches the output', (t) => {
  const r = audit(t, [
    '2026-10-08 04:54:01,000 ERROR utility.ResponseSanitizationFilter (ResponseSanitizationFilter.java:393) - Sanitizing output-stream error response body [status=401 uri=/carlos/ws/services/demographics/48213]',
    '2026-10-08 04:54:02,000 ERROR utility.ResponseSanitizationFilter (ResponseSanitizationFilter.java:393) - Sanitizing output-stream error response body [status=401 uri=/carlos/ws/services/demographics/7]',
    '2026-10-08 04:54:03,000 ERROR utility.ResponseSanitizationFilter (ResponseSanitizationFilter.java:393) - x [uri=/carlos/ws/rs/notes/12/history/34?demographicNo=48213]',
  ]);
  assert.match(r.out, /^UNKNOWN +2 +.*uri=\/carlos\/ws\/services\/demographics\/\{n\}$/m);
  assert.match(r.out, /uri=\/carlos\/ws\/rs\/notes\/\{n\}\/history\/\{n\}$/m);
  assert.doesNotMatch(r.out, /48213|demographics\/7\b/);
});

test('should exit 2, not 1, when an option is missing its value', (t) => {
  for (const option of ['--since', '--baseline', '--unit', '--catalina-dir', '--input']) {
    const r = spawnSync('bash', [SCRIPT, option], { encoding: 'utf8' });
    assert.equal(r.status, 2, `${option} without a value: ${r.stderr}`);
  }
});

test('should pin every shipped baseline entry that names no frame and no exception to one source line', () => {
  const entries = fs.readFileSync(BASELINE, 'utf8').split('\n').filter((line) => line.trim() && !line.startsWith('#'));
  for (const entry of entries) {
    const regex = entry.split('\t')[0];
    if (regex.includes(' @') || regex.includes('::') || regex.includes('uri=') || regex.includes('csrf-')) continue;
    // A class-only entry with any line number would also hide every other
    // failure that class logs by name alone.
    assert.doesNotMatch(regex, /\.java:\[0-9\]\+/, `class-only entry is not pinned to a line: ${regex}`);
  }
  // A JSP include failure the page swallows reaches only catalina's log.
  const catalinaInclude = 'SEVERE org.apache.catalina.core.ApplicationDispatcher.invoke';
  assert.ok(!entries.some((entry) => baselinePattern(entry.split('\t')[0]).test(catalinaInclude)),
    'the baseline must not explain catalina include failures wholesale');
});

// The journal side of the default window is `journalctl -b`; catalina's daily logs outlive a boot, so the
// catalina side needs the same lower bound or an earlier boot's SEVERE lines are reported as this boot's.
function auditWithStubJournal(t, { bootEpoch }) {
  const dir = tmp(t);
  const bin = path.join(dir, 'bin');
  const catalina = path.join(dir, 'catalina');
  fs.mkdirSync(bin);
  fs.mkdirSync(catalina);
  fs.writeFileSync(path.join(catalina, 'catalina.2026-10-07.log'),
    '07-Oct-2026 23:00:00.000 SEVERE [main] com.example.EarlierBootFailure.run an earlier boot failed\n');
  fs.writeFileSync(path.join(catalina, 'catalina.2026-10-08.log'),
    '08-Oct-2026 13:00:00.000 SEVERE [main] com.example.ThisBootFailure.run this boot failed\n');
  const first = bootEpoch === null ? '' : `${bootEpoch}.123456 host kernel: first entry of the boot`;
  fs.writeFileSync(path.join(bin, 'journalctl'), [
    '#!/bin/sh',
    'case "$*" in',
    `  *short-unix*) printf '%s\\n' '${first}' ;;`,
    // The unit's own journal: one line, so the audit has something to read (an empty journal is refused).
    "  *) printf '%s\\n' 'INFO started' ;;",
    'esac',
    '',
  ].join('\n'), { mode: 0o755 });
  const base = path.join(dir, 'baseline.tsv');
  fs.writeFileSync(base, '# none\n');
  const result = spawnSync('bash', [SCRIPT, '--baseline', base, '--catalina-dir', catalina],
    { encoding: 'utf8', env: { ...process.env, TZ: 'UTC', PATH: `${bin}:${process.env.PATH}`, CARLOS_LOG_AUDIT_SINCE: '' } });
  return { status: result.status, out: result.stdout, err: result.stderr };
}

test('should bound catalina SEVERE lines to the current boot when no window is given', (t) => {
  const bootEpoch = Date.UTC(2026, 9, 8, 12, 0, 0) / 1000;
  const r = auditWithStubJournal(t, { bootEpoch });
  assert.equal(r.status, 1, r.out + r.err);
  assert.match(r.out, /SEVERE com\.example\.ThisBootFailure\.run/);
  assert.doesNotMatch(r.out, /EarlierBootFailure/);
  assert.equal(r.err, '', 'a boot start the journal can state needs no warning');
});

test('should say so, and audit every catalina log, when the journal cannot name the start of the boot', (t) => {
  const r = auditWithStubJournal(t, { bootEpoch: null });
  assert.equal(r.status, 1, r.out + r.err);
  assert.match(r.err, /could not find the start of the current boot; catalina logs are not bounded/);
  assert.match(r.out, /EarlierBootFailure/);
  assert.match(r.out, /ThisBootFailure/);
});

// A journal that cannot be read is not a finding: status 2, kept apart from status 1 (an unexplained signature).
function auditWithJournalScript(t, journalctlBody) {
  const dir = tmp(t);
  const bin = path.join(dir, 'bin');
  fs.mkdirSync(bin);
  fs.writeFileSync(path.join(bin, 'journalctl'), `#!/bin/sh\n${journalctlBody}\n`, { mode: 0o755 });
  const base = path.join(dir, 'baseline.tsv');
  fs.writeFileSync(base, '# none\n');
  const result = spawnSync('bash', [SCRIPT, '--baseline', base, '--catalina-dir', path.join(dir, 'no-catalina')],
    { encoding: 'utf8', env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, CARLOS_LOG_AUDIT_SINCE: '' } });
  return { status: result.status, out: result.stdout, err: result.stderr };
}

test('should exit 2, not 1, when journalctl fails', (t) => {
  const r = auditWithJournalScript(t, 'echo "Failed to open journal" >&2\nexit 1');
  assert.equal(r.status, 2, r.out + r.err);
  assert.match(r.err, /journalctl failed \(exit 1\): Failed to open journal/);
  assert.doesNotMatch(r.out, /PASS/);
});

test('should exit 2, not report PASS, when the journal holds no line for the unit', (t) => {
  const r = auditWithJournalScript(t, 'exit 0');
  assert.equal(r.status, 2, r.out + r.err);
  assert.match(r.err, /read no journal line for unit/);
  assert.doesNotMatch(r.out, /PASS/);
});
