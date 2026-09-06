#!/usr/bin/env node
/*
 * Browser smoke checks for a CARLOS instance running on a database produced by
 * `carlos-ctl import-o19` (the OSCAR 19 -> CARLOS clinic import).
 *
 * Everything the importer proves today it proves in SQL: row parity, content
 * digests, referential spot checks, billing totals. None of that opens the
 * application. A migration can pass verification with every value intact and
 * still leave a clinic that cannot log in on Monday. This script is the
 * "the clinic can work tomorrow" signal: it logs in as the accounts the import
 * created, and asks the application to render the records the import carried.
 *
 * It discovers its own fixtures. Unlike every other script in scripts/, nothing
 * here is hardcoded to the CARLOS dev seed (carlosdoc / demographic 1 /
 * "FAKE-" names) -- a migrated database has none of that, because the import's
 * pristine gate refuses a target that holds extra logins and then deletes the
 * seeded clinician. Before opening a browser this script asks the migrated
 * schema for the break-glass administrator, the patient with the most clinical
 * notes, that patient's newest note and its signing provider, and their most
 * recent appointment, prescription and lab. A clinic with no labs (or no
 * prescriptions) skips that check and says so, instead of failing.
 *
 * WHAT IT WRITES. This script drives the forced password reset the import sets
 * on every migrated account, so it necessarily changes passwords. It snapshots
 * `password`, `forcePasswordReset` and `passwordUpdateDate` for the two
 * accounts it touches and restores them in a finally block, exactly as
 * scripts/login-playwright-checks.js does. Point it at a rehearsal or staging
 * copy of a migration, never at the clinic's live post-go-live database.
 *
 * The migrated clinician's own password is not knowable -- the operator does
 * not have it either -- so for check 2 the script copies the break-glass
 * account's freshly reset BCrypt hash onto the clinician's row and re-arms the
 * forced reset. BCrypt carries its salt inside the hash, so the same hash
 * verifies the same plaintext for any user. That check therefore proves the
 * clinician's *account* is usable end to end (provider row, role assignment,
 * privileges, expiry, forced reset, schedule render); it does not claim
 * anything about how their old OSCAR 19 password hash was carried.
 *
 * Usage (after an import; app deployed against the migrated schema):
 *   npm run test:o19-migrated-smoke
 * or driven end to end, including building and starting Tomcat, by
 *   scripts/migration/o19/rehearsal/ui-smoke.sh
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   O19_STATE_DIR=/var/lib/carlos-emr/o19-import
 *   O19_ADMIN_CREDENTIALS=$O19_STATE_DIR/admin-credentials.txt
 *   O19_SMOKE_SCREENSHOT_DIR=  (unset: no screenshots)
 *   MYSQL_DATABASE=oscar  MYSQL_USER=root  MYSQL_HOST=  MYSQL_SOCKET=
 *   O19_ARCHIVE_SCHEMA=o19_archive
 *   MYSQL_PASSWORD=       (omit for socket/root auth)
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const stateDir = process.env.O19_STATE_DIR || '/var/lib/carlos-emr/o19-import';
const credentialsPath = process.env.O19_ADMIN_CREDENTIALS || path.join(stateDir, 'admin-credentials.txt');
const screenshotDir = process.env.O19_SMOKE_SCREENSHOT_DIR || '';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlHost = process.env.MYSQL_HOST || '';
const mysqlSocket = process.env.MYSQL_SOCKET || '';
const mysqlPassword = process.env.MYSQL_PASSWORD || '';
const archiveSchema = process.env.O19_ARCHIVE_SCHEMA || 'o19_archive';
// A scratch Tomcat on localhost serves a self-signed certificate and
// there is nothing to validate against; anywhere else, skipping
// validation would mean typing migrated passwords into whatever answered.
const allowSelfSignedCert = isLoopbackHost(baseUrl.hostname.toLowerCase());

// The reset password the script drives the forced-reset form with. It has to
// satisfy the CARLOS password policy (length, mixed case, digit, symbol); the
// original row is restored before the script exits either way.
const smokePassword = ['Carlos', 'O19', 'Smoke', '2026!'].join('-');
// The clinician's reset needs a DIFFERENT new password: its old one is the
// administrator's freshly reset hash (see the header), and CARLOS refuses a
// reset that sets the password back to the one being replaced -- the form
// re-renders and the login never reaches the schedule.
const smokePasswordSecond = ['Carlos', 'O19', 'Smoke', '2026!!'].join('-');

const results = [];
const failures = [];
const skipped = [];
const badResponses = [];
const errorPages = [];
const restorePoints = [];
let mysqlDefaults = null;

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function record(name, fn) {
  const start = Date.now();
  try {
    await fn();
    results.push({ name, ms: Date.now() - start });
    console.log(`PASS ${name}`);
  } catch (error) {
    failures.push({ name, error });
    console.log(`FAIL ${name}: ${error.message}`);
  }
}

function skip(name, reason) {
  skipped.push({ name, reason });
  console.log(`SKIP ${name}: ${reason}`);
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  // Playwright sends a URL's userinfo as Basic Auth and repeats the whole
  // URL in navigation diagnostics, so a credential embedded here would be
  // both transmitted and logged.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not carry embedded credentials');
  }

  const host = parsed.hostname.toLowerCase();
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!isLoopbackHost(host) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  // Cleartext only to loopback. This script types the MIGRATED passwords
  // of a real clinic's administrator and clinician into a login form; on
  // anything but the local host that is a credential on the wire, and
  // opting into a non-local target is not opting into that.
  if (parsed.protocol === 'http:' && !isLoopbackHost(host)) {
    throw new Error(`BASE_URL ${host} is not loopback, so it must use https: this smoke sends migrated account passwords`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

// Loopback in the strict sense -- the only place a password may travel in
// cleartext, and the only place a self-signed certificate is acceptable.
// `host.docker.internal` and a container alias are NOT loopback: they
// resolve to another host on a shared network.
function isLoopbackHost(host) {
  return ['localhost', '127.0.0.1', '::1', '[::1]'].includes(host)
    || /^127\./.test(host);
}

function appUrl(appPath, query = null) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${appPath}`.replace(/\/{2,}/g, '/');
  url.search = '';
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

async function gotoApp(page, appPath, query = null, options = { waitUntil: 'domcontentloaded' }) {
  const target = appUrl(appPath, query);
  // nosemgrep -- appUrl rejects non-root-relative paths and validateBaseUrl rejects non-local hosts unless explicitly allowed.
  return page.goto(target, options); // nosemgrep
}

// --- database access -------------------------------------------------------

// A MariaDB option file is not a raw key=value format: '\' starts an escape
// sequence in a value and an unquoted '#' truncates the line. Quote and escape,
// as scripts/login-playwright-checks.js does for the same reason.
function encodeOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function createMysqlDefaultsFile() {
  if (!mysqlPassword) {
    return null;
  }
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-o19-smoke-mysql-'));
  const file = path.join(dir, 'my.cnf');
  try {
    fs.writeFileSync(file, `[client]\npassword=${encodeOptionFileValue(mysqlPassword)}\n`, { mode: 0o600 });
  } catch (error) {
    // Run-level cleanup is not installed until this returns, so a partial
    // password-bearing file would otherwise be left in the temp directory.
    fs.rmSync(dir, { recursive: true, force: true });
    throw error;
  }
  return { dir, file };
}

function cleanupMysqlDefaultsFile() {
  if (!mysqlDefaults) {
    return;
  }
  try {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
  } catch (error) {
    console.error(`Failed to remove temporary MariaDB option file: ${error.message}`);
  }
  mysqlDefaults = null;
}

// The HTTP target is guarded in `validateBaseUrl`; this is the same
// question asked of the DATABASE connection, which is where the damage
// would actually be done. This script REWRITES the password and
// forced-reset flag of two real accounts, so pointing MYSQL_HOST at a
// shared or production server rewrites credentials there. A socket or an
// unset host is local by construction; a named host is not, and opting
// into one has to be deliberate. `assertMigratedTarget` then asks the
// second question -- whether this schema is a migration at all.
function assertLocalDatabaseTarget() {
  if (!mysqlHost || mysqlSocket) {
    return;
  }
  const host = mysqlHost.toLowerCase().replace(/^\[|\]$/g, '');
  if (isLoopbackHost(host)) {
    return;
  }
  if (process.env.ALLOW_NON_LOCAL_MYSQL_HOST === 'true') {
    console.log(`WARNING: rewriting security rows on non-local database host ${host} (ALLOW_NON_LOCAL_MYSQL_HOST=true)`);
    return;
  }
  throw new Error(`Refusing to rewrite security rows on non-local database host ${host}; `
    + 'set ALLOW_NON_LOCAL_MYSQL_HOST=true only for a rehearsal copy you own');
}

function mysqlArgs() {
  const args = [];
  if (mysqlDefaults) {
    args.push(`--defaults-file=${mysqlDefaults.file}`);
  }
  if (mysqlSocket) {
    args.push('--protocol=socket', `--socket=${mysqlSocket}`);
  } else if (mysqlHost) {
    args.push('-h', mysqlHost);
  }
  args.push('-u', mysqlUser, '--default-character-set=utf8mb4', '-N', '-B', mysqlDatabase);
  return args;
}

// Rows come back tab separated, with NULL rendered as the literal NULL, so any
// column that can hold a newline or a tab is selected through HEX() by the
// callers below and decoded here by hex(). HEX() and not TO_BASE64(): MariaDB
// wraps base64 output at 76 characters with an embedded newline, which the
// batch client then re-escapes, so a value longer than 57 bytes -- a clinical
// note, or a 68-character password hash -- comes back silently mangled.
function query(sql) {
  const out = execFileSync('mysql', [...mysqlArgs(), '-e', sql], { encoding: 'utf8' });
  return out
    .split('\n')
    .filter((line) => line.length > 0)
    .map((line) => line.split('\t'));
}

function queryOne(sql) {
  const rows = query(sql);
  return rows.length ? rows[0] : null;
}

function execute(sql) {
  execFileSync('mysql', [...mysqlArgs(), '-e', sql], { encoding: 'utf8' });
}

function hex(value) {
  if (value === 'NULL' || value === undefined || value === null || value === '') {
    return '';
  }
  return Buffer.from(value, 'hex').toString('utf8');
}

function sqlString(value) {
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

function sqlInt(value) {
  const text = String(value);
  if (!/^-?\d+$/.test(text)) {
    throw new Error(`Expected an integer identifier, got ${text}`);
  }
  return text;
}

// --- fixture discovery -----------------------------------------------------

function readAdminCredentials() {
  assert(fs.existsSync(credentialsPath),
    `No break-glass credentials at ${credentialsPath}: this script only runs against a database `
    + 'produced by carlos-ctl import-o19. Set O19_STATE_DIR or O19_ADMIN_CREDENTIALS.');
  const text = fs.readFileSync(credentialsPath, 'utf8');
  // A line scan rather than a built regex. The three field names are
  // literals in this file, so the RegExp was never attacker-shaped -- but
  // it read as one to the scanners, and scanning the lines is both
  // clearer and exactly as strict.
  const field = (name) => {
    const prefix = `${name}:`;
    for (const line of text.split(/\r?\n/)) {
      if (line.startsWith(prefix)) {
        return line.slice(prefix.length).trim();
      }
    }
    return '';
  };
  const credentials = { user: field('user'), password: field('password'), pin: field('pin') };
  assert(credentials.user && credentials.password && credentials.pin,
    `${credentialsPath} did not carry a user, password and pin`);
  return credentials;
}

// Refuse to run anywhere but a migrated database. Two independent signals: the
// import's preserved-column convention (requirement B: nothing is dropped
// silently) and the absence of the CARLOS dev seed clinician, which the
// import's P0 always deletes. Both wrong => this is somebody's dev or live
// database and the password rewrites below must not happen.
function assertMigratedTarget(adminUser) {
  const archived = queryOne(
    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
    + " AND column_name LIKE 'import\\_archived\\_%'");
  assert(archived && Number(archived[0]) > 0,
    `Schema ${mysqlDatabase} carries no import_archived_ columns; it does not look like an import-o19 target`);

  const admin = queryOne(`SELECT COUNT(*) FROM security WHERE user_name = ${sqlString(adminUser)}`);
  assert(admin && Number(admin[0]) === 1,
    `Break-glass account ${adminUser} is not present exactly once in ${mysqlDatabase}.security`);

  const seed = queryOne("SELECT COUNT(*) FROM security WHERE user_name = 'carlosdoc'");
  assert(seed && Number(seed[0]) === 0,
    `Schema ${mysqlDatabase} still holds the CARLOS dev seed login 'carlosdoc'; refusing to rewrite `
    + 'passwords on what looks like a development database rather than a migrated clinic');
}

function discoverFixtures(adminUser) {
  const patientRow = queryOne(
    'SELECT d.demographic_no, HEX(d.last_name), HEX(d.first_name), COUNT(*)'
    + ' FROM casemgmt_note n JOIN demographic d ON d.demographic_no = n.demographic_no'
    + ' WHERE n.archived = 0 GROUP BY d.demographic_no, d.last_name, d.first_name'
    + ' ORDER BY COUNT(*) DESC, d.demographic_no ASC LIMIT 1');
  assert(patientRow,
    'No demographic in the migrated database has a clinical note; there is nothing for a chart smoke to open');
  const patient = {
    demographicNo: patientRow[0],
    lastName: hex(patientRow[1]),
    firstName: hex(patientRow[2]),
    noteCount: Number(patientRow[3]),
  };

  const noteRow = queryOne(
    'SELECT n.note_id, HEX(n.note), n.provider_no'
    + ` FROM casemgmt_note n WHERE n.demographic_no = ${sqlInt(patient.demographicNo)} AND n.archived = 0`
    + ' ORDER BY n.update_date DESC, n.note_id DESC LIMIT 1');
  assert(noteRow, 'The discovered patient lost their notes between two queries');
  const note = { noteId: noteRow[0], text: hex(noteRow[1]), providerNo: noteRow[2] };

  const providerRow = queryOne(
    'SELECT HEX(last_name), HEX(first_name) FROM provider'
    + ` WHERE provider_no = ${sqlString(note.providerNo)}`);
  const provider = providerRow
    ? { providerNo: note.providerNo, lastName: hex(providerRow[0]), firstName: hex(providerRow[1]) }
    : null;

  const appointmentRow = queryOne(
    'SELECT appointment_no, appointment_date, start_time, provider_no,'
    + ' IFNULL(program_id, 0) FROM appointment'
    + ` WHERE demographic_no = ${sqlInt(patient.demographicNo)}`
    + ' ORDER BY appointment_date DESC, start_time DESC LIMIT 1');
  const appointment = appointmentRow
    ? {
      appointmentNo: appointmentRow[0],
      date: appointmentRow[1],
      startTime: appointmentRow[2],
      providerNo: appointmentRow[3],
      programId: appointmentRow[4],
    }
    : null;

  const drugRow = queryOne(
    'SELECT drugid, HEX(IFNULL(BN, \'\')), HEX(IFNULL(GN, \'\')) FROM drugs'
    + ` WHERE demographic_no = ${sqlInt(patient.demographicNo)} AND archived = 0`
    + ' ORDER BY drugid DESC LIMIT 1');
  const drug = drugRow ? { drugId: drugRow[0], brandName: hex(drugRow[1]), genericName: hex(drugRow[2]) } : null;

  const labRow = queryOne(
    'SELECT r.lab_no, HEX(IFNULL(i.accessionNum, \'\')), HEX(IFNULL(i.discipline, \'\'))'
    + ' FROM patientLabRouting r JOIN hl7TextInfo i ON i.lab_no = r.lab_no'
    + ` WHERE r.demographic_no = ${sqlInt(patient.demographicNo)} AND r.lab_type = 'HL7'`
    + ' ORDER BY r.lab_no DESC LIMIT 1');
  const routed = queryOne(
    'SELECT COUNT(*) FROM patientLabRouting WHERE demographic_no = '
    + `${sqlInt(patient.demographicNo)} AND lab_type = 'HL7'`);
  const lab = labRow
    ? {
      labNo: labRow[0], accession: hex(labRow[1]), discipline: hex(labRow[2]),
      routed: routed ? routed[0] : '0',
    }
    : null;

  // A migrated clinician with a usable login: prefer the provider who signed the
  // newest note, fall back to any other non-admin account. Accounts whose expiry
  // has passed are excluded on purpose -- CARLOS refuses those logins by design,
  // and the import reports them rather than silently extending them.
  const clinicianRow = queryOne(
    'SELECT s.security_no, s.user_name, s.pin, s.provider_no FROM security s'
    + ` WHERE s.user_name <> ${sqlString(adminUser)} AND s.provider_no IS NOT NULL`
    + " AND (s.b_ExpireSet <> '1' OR s.date_ExpireDate IS NULL OR s.date_ExpireDate > CURDATE())"
    + ' AND EXISTS (SELECT 1 FROM provider p WHERE p.provider_no = s.provider_no'
    + "            AND p.status = '1')"
    + ` ORDER BY (s.provider_no = ${sqlString(note.providerNo)}) DESC, s.security_no ASC LIMIT 1`);
  const clinician = clinicianRow
    ? {
      securityNo: clinicianRow[0], userName: clinicianRow[1], pin: clinicianRow[2], providerNo: clinicianRow[3],
    }
    : null;

  // Requirement B, the two populations the import preserves rather than drops
  // (docs/o19-import-deb.md, "what did not arrive"):
  //   * every live table that gained import_archived_<col> columns, and
  //   * the o19_archive.<table>__unknown_cols shadow of the vendor-fork
  //     columns the operator cleared with --accept unknown-as-archive.
  // The shadow holds the rows where at least one of those columns had a
  // value; the live twin holds every row. That is the invariant reconciled
  // below.
  const preservedColumns = new Map();
  for (const [table, column] of query(
    'SELECT table_name, column_name FROM information_schema.columns'
    + " WHERE table_schema = DATABASE() AND column_name LIKE 'import\\_archived\\_%'"
    + ' ORDER BY table_name, column_name')) {
    if (!preservedColumns.has(table)) {
      preservedColumns.set(table, []);
    }
    preservedColumns.get(table).push(column);
  }
  const archiveExists = queryOne(
    'SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '
    + sqlString(archiveSchema));
  const unknownShadows = Number(archiveExists[0]) === 0 ? [] : query(
    'SELECT table_name FROM information_schema.tables WHERE table_schema = '
    + sqlString(archiveSchema)
    + " AND table_name LIKE '%\\_\\_unknown\\_cols' ORDER BY table_name")
    .map(([shadow]) => ({ shadow, table: shadow.slice(0, -'__unknown_cols'.length) }));

  return {
    patient, note, provider, appointment, drug, lab, clinician,
    preservedColumns, unknownShadows,
  };
}

// --- security-row snapshot / restore ---------------------------------------

function securityRow(userName) {
  const row = queryOne(
    'SELECT HEX(password), forcePasswordReset,'
    + " IFNULL(DATE_FORMAT(passwordUpdateDate, '%Y-%m-%d %H:%i:%s'), 'NULL')"
    + ` FROM security WHERE user_name = ${sqlString(userName)}`);
  assert(row, `No security row for ${userName}`);
  return { userName, password: hex(row[0]), forcePasswordReset: row[1], passwordUpdateDate: row[2] };
}

function rememberForRestore(userName) {
  if (restorePoints.some((point) => point.userName === userName)) {
    return;
  }
  restorePoints.push(securityRow(userName));
}

// Ctrl-C and SIGTERM run neither `finally` nor the top-level catch, so
// without these the accounts stay on the smoke password and the clinician
// stays forced onto the copied hash -- on a database holding a copy of a
// clinic's records. `restoreSecurityRows` and `cleanupMysqlDefaultsFile`
// are both SYNCHRONOUS (execFileSync, fs.rmSync), so a handler can finish
// the work before exiting; both are idempotent, so the `finally` path
// running afterwards is harmless.
function installSignalHandlers() {
  for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
    process.on(signal, () => {
      console.log(`\nReceived ${signal}: restoring the security rows before exiting`);
      try {
        restoreSecurityRows();
      } catch (error) {
        console.error(`Restore failed: ${error.stack || error}`);
      } finally {
        cleanupMysqlDefaultsFile();
      }
      // 128 + signal number, the convention a shell reports for a
      // signalled child
      process.exit(signal === 'SIGINT' ? 130 : 143);
    });
  }
}

function restoreSecurityRows() {
  for (const point of restorePoints) {
    const passwordUpdateDate = point.passwordUpdateDate === 'NULL'
      ? 'NULL'
      : sqlString(point.passwordUpdateDate);
    execute(
      `UPDATE security SET password = ${sqlString(point.password)},`
      + ` forcePasswordReset = ${sqlInt(point.forcePasswordReset)},`
      + ` passwordUpdateDate = ${passwordUpdateDate}`
      + ` WHERE user_name = ${sqlString(point.userName)}`);
    console.log(`Restored the ${point.userName} security row`);
  }
}

// --- page wiring -----------------------------------------------------------

// The import's props phase carries signature/eform settings; a missing or
// mis-carried one surfaces in the page body rather than as an HTTP error, so
// the body text is scanned as well as the status code.
const BODY_FAILURE_PATTERNS = [
  /CARLOS Error/i,
  /\[Unknown Signature Type Requested\]/i,
  /org\.apache\.(jasper|struts2?)\..*Exception/,
  /jakarta\.servlet\.ServletException/,
  /java\.lang\.NullPointerException/,
];

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    await dialog.dismiss().catch(() => {});
  });
  page.on('response', (response) => {
    const status = response.status();
    if (status >= 400) {
      badResponses.push({ label, status, url: response.url() });
    }
  });
  page.on('pageerror', () => {
    // Page-level JavaScript errors are noisy on legacy CARLOS pages and are not
    // what this smoke is asserting; HTTP status and body content are.
  });
}

async function scanBody(page, label) {
  const body = await page.content();
  for (const pattern of BODY_FAILURE_PATTERNS) {
    const match = body.match(pattern);
    if (match) {
      errorPages.push({ label, url: page.url(), matched: match[0] });
    }
  }
  return body;
}

function normalise(text) {
  return text.replace(/\s+/g, ' ').trim();
}

async function bodyText(page) {
  return normalise(await page.locator('body').innerText({ timeout: 15000 }));
}

async function screenshot(page, name) {
  if (!screenshotDir) {
    return;
  }
  const file = path.join(screenshotDir, `${name.replace(/[^A-Za-z0-9._-]/g, '-')}.png`);
  // nosemgrep -- the basename is sanitized above and screenshotDir is operator supplied.
  await page.screenshot({ path: file, fullPage: true }).catch(() => {}); // nosemgrep
}

// --- login helpers ---------------------------------------------------------

async function submitLogin(page, user, password, pin) {
  await gotoApp(page, '/');
  await page.locator('#username').waitFor({ timeout: 20000 });
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(password);
  await page.locator('#pin').fill(pin);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
}

async function completeForcedReset(page, oldPassword, newPassword) {
  await page.waitForURL(/forcepasswordreset/, { timeout: 20000 });
  await scanBody(page, 'forced reset');
  const action = await page.locator('form').first().getAttribute('action');
  assert(action && action.endsWith('/forcepasswordresetSubmit'),
    `forced reset form posted to ${action}, not /forcepasswordresetSubmit`);
  await page.locator('input[name="oldPassword"]').fill(oldPassword);
  await page.locator('input[name="newPassword"]').fill(newPassword);
  await page.locator('input[name="confirmPassword"]').fill(newPassword);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"]').first().click(),
  ]);
}

async function expectSchedulePage(page, label) {
  // domcontentloaded, not the default 'load': the schedule pulls the
  // inbox and messenger counts and a slow one of those leaves the load
  // event pending long after the page is there and usable.
  await page.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/,
    { timeout: 30000, waitUntil: 'domcontentloaded' });
  const body = await scanBody(page, label);
  assert(body.includes('/csrfguard'), `${label} did not include the csrfguard script`);
  const text = await bodyText(page);
  assert(text.length > 0, `${label} rendered a blank body`);
  return text;
}

// Log in an account that is sitting on a forced reset, and land on the schedule.
async function loginThroughForcedReset(context, user, password, pin, label,
  newPassword = smokePassword) {
  const page = await context.newPage();
  wirePage(page, label);
  await submitLogin(page, user, password, pin);
  await completeForcedReset(page, password, newPassword);
  await expectSchedulePage(page, label);
  return page;
}

// --- checks ----------------------------------------------------------------

(async () => {
  assertLocalDatabaseTarget();
  mysqlDefaults = createMysqlDefaultsFile();
  const credentials = readAdminCredentials();
  assertMigratedTarget(credentials.user);
  const fixtures = discoverFixtures(credentials.user);

  // `demographic_no` is a PHI-CORRELATING operational identifier, not
  // clinical content: it is here because a failed smoke is unreproducible
  // without knowing which row it picked, and it is not paired with a
  // name, a diagnosis or a note.
  console.log(`Migrated schema ${mysqlDatabase}: break-glass ${credentials.user}, `
    + `patient ${fixtures.patient.demographicNo} with ${fixtures.patient.noteCount} note(s), `
    + `clinician ${fixtures.clinician ? fixtures.clinician.userName : 'none'}`);

  // Installed BEFORE the first row is remembered, so there is no window in
  // which a rewrite has happened and an interrupt would not undo it.
  installSignalHandlers();
  rememberForRestore(credentials.user);
  if (fixtures.clinician) {
    rememberForRestore(fixtures.clinician.userName);
  }

  const launchOptions = { headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }
  const browser = await chromium.launch(launchOptions);
  let adminPage = null;

  try {
    await record('the break-glass administrator can log in through the forced password reset', async () => {
      const context = await browser.newContext({ ignoreHTTPSErrors: allowSelfSignedCert, viewport: { width: 1440, height: 1100 } });
      adminPage = await loginThroughForcedReset(
        context, credentials.user, credentials.password, credentials.pin, 'admin schedule');
      const row = securityRow(credentials.user);
      assert(row.forcePasswordReset === '0',
        `the forced reset did not clear forcePasswordReset for ${credentials.user}`);
      await screenshot(adminPage, 'o19-smoke-admin-schedule');
    });

    if (!fixtures.clinician) {
      skip('a migrated clinician can log in through the forced password reset',
        'no migrated account has both a login and an unexpired, active provider');
    } else {
      await record('a migrated clinician can log in through the forced password reset', async () => {
        // See the header: BCrypt hashes carry their own salt, so re-using the
        // administrator's freshly reset hash gives the clinician a password this
        // script knows without needing the clinic's original one.
        const adminRow = securityRow(credentials.user);
        // CARLOS stores DelegatingPasswordEncoder hashes: '{bcrypt}' then the
        // BCrypt string (PasswordHashHelper).
        assert(/^\{bcrypt\}\$2/.test(adminRow.password),
          `expected a {bcrypt} hash for ${credentials.user} after the reset, `
          + `got ${adminRow.password.slice(0, 10)}`);
        execute(
          `UPDATE security SET password = ${sqlString(adminRow.password)}, forcePasswordReset = 1`
          + ` WHERE user_name = ${sqlString(fixtures.clinician.userName)}`);

        const context = await browser.newContext({ ignoreHTTPSErrors: allowSelfSignedCert });
        const page = await loginThroughForcedReset(
          context, fixtures.clinician.userName, smokePassword, fixtures.clinician.pin,
          'clinician schedule', smokePasswordSecond);
        await screenshot(page, 'o19-smoke-clinician-schedule');
        await context.close();
      });
    }

    await record('patient search finds the migrated patient by surname', async () => {
      assert(adminPage, 'the administrator session was not established');
      const page = await adminPage.context().newPage();
      wirePage(page, 'search');
      // The same GET the search form issues (zdemographicfulltitlesearch.jsp):
      // search_mode plus the form's hidden orderby/dboperation/limit/ptstatus.
      await gotoApp(page, '/demographic/DemographicSearch', {
        search_mode: 'search_name',
        keyword: fixtures.patient.lastName,
        orderby: 'last_name, first_name',
        dboperation: 'search_titlename',
        limit1: 0,
        limit2: 10,
        displaymode: 'Search',
        ptstatus: 'active',
      });
      const body = await scanBody(page, 'search');
      assert(!/CARLOS Error/i.test(body), 'demographic search returned an error page');
      const text = await bodyText(page);
      assert(text.toUpperCase().includes(fixtures.patient.lastName.toUpperCase()),
        'demographic search did not list the migrated patient (surname withheld: PHI)');
      await screenshot(page, 'o19-smoke-search');
      await page.close();
    });

    await record('the e-chart renders the newest migrated note and its signing provider', async () => {
      assert(adminPage, 'the administrator session was not established');
      const page = await adminPage.context().newPage();
      wirePage(page, 'echart');
      // The E-Chart link on the demographic search results (demographicsearchresults.jsp).
      await gotoApp(page, '/encounter/IncomingEncounter', {
        providerNo: fixtures.provider ? fixtures.provider.providerNo : '',
        appointmentNo: '',
        demographicNo: fixtures.patient.demographicNo,
        curProviderNo: '',
        reason: '',
        encType: '',
        appointmentDate: '',
        startTime: '',
        status: '',
      });
      await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
      // Clinical notes arrive by AJAX after the layout, so wait for the note
      // itself rather than asserting on whatever the first paint held.
      const excerpt = normalise(fixtures.note.text).slice(0, 40);
      assert(excerpt.length > 0, 'the newest migrated note is empty');
      await page.getByText(excerpt, { exact: false }).first()
        .waitFor({ state: 'attached', timeout: 30000 })
        .catch(() => {});
      await scanBody(page, 'echart');
      const text = await bodyText(page);
      assert(text.includes(excerpt),
        'the e-chart did not render the newest migrated note (text withheld: PHI)');
      if (fixtures.provider && fixtures.provider.lastName) {
        assert(text.toUpperCase().includes(fixtures.provider.lastName.toUpperCase()),
          "the e-chart did not name the note's signing provider (name withheld)");
      }
      await screenshot(page, 'o19-smoke-echart');
      await page.close();
    });

    if (!fixtures.appointment) {
      skip('the migrated appointment appears in the patient\'s appointment history',
        'the patient has no appointment');
    } else {
      await record('the migrated appointment appears in the patient\'s appointment history', async () => {
        // The appointment HISTORY, not the day schedule, and deliberately:
        // both CARLOS and OSCAR 19 hardcode the day view's programId to 0
        // (`appointmentprovideradminday.jsp`, "Disable schedule view
        // associated with the program"), so an appointment carrying any
        // other program id is invisible there -- in CARLOS exactly as it
        // was in OSCAR 19. The history lists every appointment whatever
        // its program, which is what "the appointment migrated" means.
        // The import's own report names any appointment that carries a
        // non-zero program id.
        const page = await adminPage.context().newPage();
        wirePage(page, 'appointment history');
        await gotoApp(page, '/demographic/DemographicApptHistory', {
          demographic_no: fixtures.patient.demographicNo,
          dboperation: 'appt_history',
          orderby: 'appointment_date',
          limit1: 0,
          limit2: 25,
        });
        await scanBody(page, 'appointment history');
        const text = await bodyText(page);
        assert(text.includes(fixtures.appointment.date),
          'the appointment history did not show the migrated appointment '
          + '(date withheld: PHI)');
        await screenshot(page, 'o19-smoke-appointment-history');
        await page.close();
      });

      if (fixtures.appointment.programId !== '0') {
        skip('the migrated appointment appears on its provider\'s day schedule',
          `it carries program id ${fixtures.appointment.programId}, and the day view of both `
          + 'CARLOS and OSCAR 19 shows program 0 only');
      } else {
        await record('the migrated appointment appears on its provider\'s day schedule', async () => {
          const [year, month, day] = fixtures.appointment.date.split('-');
          const page = await adminPage.context().newPage();
          wirePage(page, 'schedule day');
          await gotoApp(page, '/provider/providercontrol', {
            year, month, day, view: 0, displaymode: 'day',
            dboperation: 'searchappointmentday', viewall: 1,
          });
          await scanBody(page, 'schedule day');
          const text = await bodyText(page);
          assert(text.toUpperCase().includes(fixtures.patient.lastName.toUpperCase()),
            'the day schedule did not show the migrated appointment '
            + '(patient and date withheld: PHI)');
          await screenshot(page, 'o19-smoke-schedule-day');
          await page.close();
        });
      }
    }

    if (!fixtures.drug) {
      skip('the migrated prescription appears in the Rx module', 'the patient has no prescription');
    } else {
      await record('the migrated prescription appears in the Rx module', async () => {
        const page = await adminPage.context().newPage();
        wirePage(page, 'rx');
        await gotoApp(page, '/rx/choosePatient', {
          providerNo: fixtures.clinician ? fixtures.clinician.providerNo : fixtures.note.providerNo,
          demographicNo: fixtures.patient.demographicNo,
        });
        await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
        await scanBody(page, 'rx');
        const text = await bodyText(page);
        const drugName = (fixtures.drug.brandName || fixtures.drug.genericName).split(/[\s(]/)[0];
        assert(drugName.length > 0, 'the migrated prescription has neither a brand nor a generic name');
        assert(text.toUpperCase().includes(drugName.toUpperCase()),
          'the Rx module did not list the migrated prescription '
          + '(drug withheld: PHI)');
        await screenshot(page, 'o19-smoke-rx');
        await page.close();
      });
    }

    if (!fixtures.lab) {
      skip('the migrated lab result appears in the patient\'s lab list', 'the patient has no routed lab result');
    } else {
      await record('the migrated lab result appears in the patient\'s lab list', async () => {
        const page = await adminPage.context().newPage();
        wirePage(page, 'labs');
        await gotoApp(page, '/lab/ViewDemographicLab', { demographicNo: fixtures.patient.demographicNo });
        await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
        await scanBody(page, 'labs');
        const text = await bodyText(page);
        // the discipline, not the accession number: the lab list renders
        // date / label / requesting client / status / discipline, and a
        // check keyed on a column the page does not show would fail on a
        // faithful migration
        const marker = fixtures.lab.discipline || fixtures.lab.accession;
        assert(marker.length > 0, 'the migrated lab carries neither a discipline nor an accession number');
        assert(text.includes(marker),
          "the patient's lab list did not show the migrated lab "
          + '(discipline withheld: PHI)');
        assert(Number(fixtures.lab.routed) > 0, 'no lab is routed to this patient');
        console.log(`  ${fixtures.lab.routed} lab(s) routed to the patient, and the list shows the expected marker`);
        await screenshot(page, 'o19-smoke-labs');
        await page.close();
      });
    }

    await record('preserved vendor-fork data is still there, and its table still renders', async () => {
      // Requirement B from the application's side. Two claims:
      //   1. the columns the import added to live tables did not stop CARLOS
      //      reading those tables -- proved by rendering the patient's
      //      document list, whose `document` table carries one; and
      //   2. the values are all still accounted for -- the live
      //      import_archived_ twin agrees with the o19_archive shadow the
      //      clinic's CSV export is rendered from.
      assert(fixtures.preservedColumns.size > 0,
        'the migrated schema carries no import_archived_ column at all');

      const page = await adminPage.context().newPage();
      wirePage(page, 'documents');
      await gotoApp(page, '/documentManager/ViewDocumentReport', {
        function: 'demographic', functionid: fixtures.patient.demographicNo,
      });
      await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      const body = await scanBody(page, 'documents');
      assert(!/CARLOS Error/i.test(body), 'the patient document list returned an error page');
      const documentColumns = fixtures.preservedColumns.get('document') || [];
      assert(documentColumns.length > 0,
        'the `document` table carries no import_archived_ column, so this check proves nothing');
      const documents = queryOne(
        `SELECT COUNT(*) FROM document d JOIN ctl_document c ON c.document_no = d.document_no`
        + ` WHERE c.module = 'demographic' AND c.module_id = ${sqlInt(fixtures.patient.demographicNo)}`
        + ` AND d.status <> 'D'`);
      const text = await bodyText(page);
      assert(Number(documents[0]) === 0 || text.length > 0,
        'the patient has documents but the document list rendered nothing');
      await screenshot(page, 'o19-smoke-documents');
      await page.close();

      const mismatches = [];
      for (const { shadow, table } of fixtures.unknownShadows) {
        const columns = fixtures.preservedColumns.get(table) || [];
        if (!columns.length) {
          mismatches.push(`${table} has an ${shadow} shadow but no import_archived_ column`);
          continue;
        }
        const predicate = columns.map((column) => `\`${column}\` IS NOT NULL`).join(' OR ');
        const live = queryOne(`SELECT COUNT(*) FROM \`${table}\` WHERE ${predicate}`);
        const archived = queryOne(`SELECT COUNT(*) FROM \`${archiveSchema}\`.\`${shadow}\``);
        if (live[0] !== archived[0]) {
          mismatches.push(
            `${table}: ${live[0]} live row(s) carry a preserved value but `
            + `${archiveSchema}.${shadow} holds ${archived[0]}`);
        }
      }
      assert(mismatches.length === 0, mismatches.join('; '));
      console.log(`  reconciled ${fixtures.unknownShadows.length} vendor-fork shadow(s) against `
        + `${fixtures.preservedColumns.size} live table(s) carrying preserved columns`);
    });

    await record('no visited page returned an error status or rendered a stack trace', async () => {
      assert(errorPages.length === 0,
        `pages rendered failure content: ${JSON.stringify(errorPages.slice(0, 5))}`);
      assert(badResponses.length === 0,
        `requests failed: ${JSON.stringify(badResponses.slice(0, 10))}`);
    });
  } finally {
    await browser.close().catch(() => {});
    try {
      restoreSecurityRows();
    } finally {
      cleanupMysqlDefaultsFile();
    }
    console.log(`Completed ${results.length} checks, ${skipped.length} skipped, ${failures.length} failures`);
    for (const entry of skipped) {
      console.log(`SKIPPED ${entry.name}: ${entry.reason}`);
    }
    if (failures.length) {
      for (const failure of failures) {
        console.log(`FAILED ${failure.name}: ${failure.error.stack || failure.error.message}`);
      }
      process.exitCode = 1;
    }
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  try {
    restoreSecurityRows();
  } catch (restoreError) {
    console.error(`Restore failed: ${restoreError.stack || restoreError}`);
  } finally {
    cleanupMysqlDefaultsFile();
  }
  process.exit(1);
});
