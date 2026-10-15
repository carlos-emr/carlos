#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Direct-response contract of the download servlets and the hidden-but-live admin download pages
 * (coverage-plan area: direct responses outside the Struts route measurement).
 *
 * WHY. /servlet/BackupDownload, /servlet/OscarDownload, admin/ViewAdminBackupDownload and
 * admin/ViewOscarLogging stream files from server directories (database and document backups, the OHIP
 * claim disk, the OBEC eligibility batch, server logs). Nothing proved them end to end: BackupDownloadUnitTest
 * and OscarDownloadUnitTest drive the servlets with mocks, so a wrong directory, a missing Content-Disposition,
 * a refusal that is really the front door's page, or a role the privilege check lets through are invisible.
 * The check seeds FAKE marker files where the application serves from and asserts what a recipient receives.
 *
 * THE TWO GATES (read from the source, pinned here). BackupDownload checks the privilege itself (_admin r or
 * _admin.backup r) on every request. OscarDownload checks NO privilege: it serves the directory a session
 * attribute names (obecdownload, ohipdownload, homepath), and the attribute exists only after the session
 * opened the page that sets it -- oscarReport/obec (_report r) sets obecdownload to DOCUMENT_DIR,
 * billing/CA/ON/ViewBillingONMRI (_billing r) sets ohipdownload to HOME_DIR. BackupDownload's directory is the
 * session's backupfilepath: admin/ViewAdminBackupDownload sets it to backup_path, billing/CA/ON/moveMOHFiles
 * sets it to an EDT folder. A role is therefore served by OscarDownload exactly when its role may open the page.
 *
 * Asserted:
 *   - the roles (doctor, nurse, receptionist, and two roles the check builds: `_report` r alone and
 *     `_admin.reporting` r alone) hold the objects this check assumes and sign in through the login form;
 *   - the administrator is listed and served the EXACT bytes of the seeded marker file (bytes that are not
 *     valid UTF-8) with `Content-Disposition: attachment;filename=...`, nosniff and a matching Content-Length,
 *     for BackupDownload (through the MOH files folder), the OBEC key and the OHIP claim-disk key; the backup
 *     page and the log viewer are asserted the same way where the installed configuration lets the service
 *     read the directory;
 *   - doctor, nurse and receptionist are refused by every route with the application's own 403 (h.assertRefused:
 *     its response header or error page, never the front door's page) and nothing is served; OscarDownload
 *     refuses a session that has not opened its page, serves a role that opened a page its role may open, and
 *     refuses one whose role may not (the page itself refuses: nurse and receptionist on the OBEC page, nurse on
 *     the OHIP page, which answers HTTP 500 rather than 403, the family of finding 199);
 *   - a request without a session gets the application's 401 from both servlets and nothing is served;
 *   - a traversal filename or homepath gets the application's 400 and no bytes. The front door blocks the
 *     literal `../x` forms before the application sees them: each is classified (isWafPage) and reported as
 *     "application not reached", never counted as the application's refusal, and the path-component shapes the
 *     front door lets through (a nested marker file reached by `/`, `%2f` and `%5c`, a drive prefix, an absolute
 *     prefix, a hidden or script file name, an unknown homepath key) must reach the application and be refused
 *     there with a nested marker file in place to prove nothing is read below the served directory;
 *   - the log viewer's gate is the intended OR: a login holding `_admin.reporting` r alone is served the viewer
 *     (HTTP 200, #logForm) and neither backup route admits it, while a login holding neither `_admin` nor
 *     `_admin.reporting` is refused. The gate action (ViewOscarLogging2Action) ignores SERVERLOGGING; only the
 *     Administration menu entry (admin.jsp:505) checks it, so the property does not hide the page;
 *   - every download body is the file and never an HTML page, and no error is sent as an attachment;
 *   - OscarDownload's OBEC key refuses a stored-document file to a login that holds `_report` r and nothing
 *     else, in particular no `_edoc` (finding 202). That login can open oscarReport/obec and nothing more; it
 *     cannot grant itself rights, which is why it, and not the seeded admin role (`_admin` x, which can grant
 *     itself `_edoc` through admin/ProviderPrivilege), pins the finding.
 *
 * CONFIGURATION. The application reads its properties once, at start, so this check cannot change them (and
 * must not restart the service). It seeds into the directories the installed configuration already uses:
 * DOCUMENT_DIR, OHIP_DISK_DIR and ONEDT_INBOX (suite-env.sh) and, from CARLOS_PROPERTIES_FILE (default
 * /etc/carlos-emr/carlos.properties), backup_path, LOGGING_PATH and billregion. A row whose directory the
 * installation does not give the service is recorded as SKIP with the reason and its refusals still run: the
 * packaged install ships backup_path=/home/mysql/ (hidden from the service by ProtectHome=yes) and no
 * LOGGING_PATH. A properties file this run cannot read is reported as unreadable (not as "not set"), and the
 * billing region is then unknown, so the Ontario rows skip rather than assume Ontario.
 *
 * The OBEC and OHIP marker files are named in the shape the application writes (OBECE + 13 digits + .TXT;
 * H + month letter + 6 digits + .999), so the "served exactly" controls stay valid when finding 202 is fixed by
 * limiting the OBEC key to the files the application generates. The stored-document marker (`<marker>-doc.pdf`)
 * is deliberately not in that shape: it is what the fixed servlet must refuse. If the fix is of another kind
 * (a per-file token, a privilege check), those controls have to change with it.
 *
 * Fixtures: five throwaway logins (own provider rows, lib/authz-read-fixture.js; two of them hold a custom
 * role the check creates) and FAKE marker files in those directories. Both are removed and asserted gone. A crash
 * between the seed and the cleanup leaves marker files behind, which is why the manifest asks for an exclusive run.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn, urlFor } = require('./lib/authz-read-probe');
const D = require('./lib/download-contract');

const NAME = 'direct-response-contract';
const PROPERTIES_FILE = process.env.CARLOS_PROPERTIES_FILE || '/etc/carlos-emr/carlos.properties';
const LOG_DATE = '1999-12-31';

// Literal step labels, so the manifest's expectedFailure can name the step it fails at.
const STEP = {
  setup: 'the roles hold the objects this check assumes and their logins sign in',
  seed: 'the marker files are seeded where the application serves them, and the rows that cannot be seeded are recorded as skipped',
  backupBytes: 'BackupDownload: the administrator is served the exact bytes of the marker file with its Content-Disposition',
  backupPage: 'admin/ViewAdminBackupDownload: the administrator\'s page lists the marker file and its link serves the exact bytes (skipped when the service cannot see backup_path)',
  logPage: 'admin/ViewOscarLogging: the administrator reads the marker log HTML-encoded in the viewer (skipped when LOGGING_PATH is not set)',
  oscarBytes: 'OscarDownload: the administrator is served the exact bytes of the OBEC and OHIP marker files after opening their pages',
  refuseBackup: 'BackupDownload refuses doctor, nurse and receptionist and serves nothing',
  refuseBackupPage: 'admin/ViewAdminBackupDownload refuses doctor, nurse and receptionist and lists nothing',
  refuseLog: 'admin/ViewOscarLogging refuses doctor, nurse and receptionist and shows nothing',
  leastPrivilege: 'two least-privilege logins, holding exactly _report r and exactly _admin.reporting r, sign in',
  logGate: 'admin/ViewOscarLogging admits a login holding only _admin.reporting (the intended OR gate) and refuses one holding neither _admin nor _admin.reporting',
  reportingOnlyBackup: 'a login holding only _admin.reporting is refused by BackupDownload and the backup page',
  oscarArming: 'OscarDownload serves a login only after it opened the page that arms the key: refused before, served after a page its role may open, refused after one it may not',
  anonymous: 'a request without a session gets the application\'s 401 from both servlets and nothing is served from either page',
  traversal: 'a traversal filename or homepath gets the application\'s 400 (or the front door\'s block, reported as application not reached) and no file bytes',
  neverHtml: 'every download body is the file and never an HTML page, and no error is sent as an attachment',
  frontDoor: 'the run went through the front door when one is expected',
  documentControls: 'OscarDownload (OBEC key): a login holding only _report r opens the OBEC page, is served OBEC output, and the document manager refuses it a stored document',
  documentScope: 'OscarDownload (OBEC key) refuses a stored-document file to a login that holds no _edoc',
};

// What each seeded role is assumed to hold (secObjPrivilege, the roles the install seeds). `o` is NORIGHTS.
const ROLE_EXPECTATIONS = {
  doctor: { holds: ['_report', '_billing', '_edoc'], lacks: ['_admin', '_admin.backup', '_admin.reporting'] },
  nurse: { holds: [], lacks: ['_admin', '_admin.backup', '_admin.reporting', '_report', '_billing', '_edoc'] },
  receptionist: { holds: ['_billing'], lacks: ['_admin', '_admin.backup', '_admin.reporting', '_report', '_edoc'] },
};
const ROLES = ['doctor', 'nurse', 'receptionist'];

// The pages that arm OscarDownload's keys and who may open them (Obec2Action: _report r; ViewBillingOnMri2Action: _billing r).
const KEYS = {
  obecdownload: {
    page: 'oscarReport/obec', pageHas: /id="obecForm"/, seed: 'obec', label: 'OBEC',
    mayOpen: { doctor: true, nurse: false, receptionist: false },
  },
  ohipdownload: {
    page: 'billing/CA/ON/ViewBillingONMRI', pageHas: /id="billcenter"/, seed: 'ohip', label: 'OHIP claim disk',
    mayOpen: { doctor: true, nurse: false, receptionist: true },
  },
};

// Shapes the front door's rules block before the application sees them (path traversal, restricted files, NUL).
const FILENAME_LITERAL = ['../x', '..%2fx', '%2e%2e%2fx', '..%5cx', '/etc/passwd', 'x%00.txt'];
const KEY_LITERAL = ['../etc', 'homepath/../x', '..', '%2e%2e', 'obecdownload%00'];
// Shapes that carry no traversal text the front door matches: they must reach the application and be refused there.
const FILENAME_PLAIN = ['C:x.txt', '/x.txt', 'x.txt/', '....//x', '~root', '.hidden', 'x.jsp', 'x.war'];
const KEY_PLAIN = ['user', 'userrole', 'OBECDOWNLOAD', 'obecdownload/', 'obecdownload%20', '', 'obecdownload,homepath'];

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const files = D.markerFiles(marker);
  s.cleanup(() => files.remove());

  const propertiesText = (() => { try { return fs.readFileSync(PROPERTIES_FILE, 'utf8'); } catch (error) { return null; } })();
  const unreadable = propertiesText === null;
  const unreadableReason = `the properties file ${PROPERTIES_FILE} is unreadable`;
  const property = key => (unreadable ? undefined : D.propertyValue(propertiesText, key));
  const env = process.env;
  const billRegion = property('billregion');
  // The application's own test is exactly "ON" (CarlosProperties.isOntarioBillingRegion). An unreadable file leaves the region unknown, never Ontario.
  const ontario = !unreadable && billRegion === 'ON';
  const notOntario = () => (unreadable ? `${unreadableReason}, so the billing region is unknown`
    : `billregion=${billRegion === undefined ? '(not set)' : billRegion}: the OHIP report and MOH files pages are the Ontario ones`);
  /** A directory from the environment, else from the installed properties; a reason when neither gives a usable one. */
  const directory = (envValue, key, what) => {
    if (envValue) return D.usableDirectory(envValue, what);
    if (unreadable) return { ok: false, reason: `${what} is not given by the environment and ${unreadableReason}` };
    return D.usableDirectory(property(key), what);
  };
  const names = D.realShapeNames(marker);

  const contexts = { admin: s.context };
  const logins = {};
  const seeds = {};
  const skipped = [];
  const seen = [];
  let nginx = false;
  let backupVia = null;

  const skip = (row, reason) => { skipped.push(`${row}: ${reason}`); console.log(`  SKIP ${NAME}: ${row} -- ${reason}`); };
  const R = {
    backup: name => `servlet/BackupDownload?filename=${name}`,
    oscar: (key, name) => `servlet/OscarDownload?homepath=${key}&filename=${name}`,
    log: (date, type) => `admin/ViewOscarLogging?reportDate=${date}&reportType=${type}`,
    moh: 'billing/CA/ON/moveMOHFiles?folder=INBOX',
    backupPage: 'admin/ViewAdminBackupDownload',
  };
  const ownedRows = () => String(sql.value(`SELECT COUNT(*) FROM provider WHERE last_name=${h.sqlString(marker)}`));

  async function get(who, route, label) {
    const res = await D.fetchRaw(contexts[who], urlFor(config, route));
    if (res.nginx) nginx = true;
    seen.push({ label: `${who} ${label || route.split('?')[0]}`, res });
    return res;
  }
  const allPayloads = () => Object.values(seeds).filter(Boolean).flatMap(seed => [seed.bytes, seed.nested && seed.nested.bytes].filter(Boolean));

  /** The application's own refusal (h.assertRefused: 403/405/securityError with its header, an unchanged owned-row count) and nothing served. */
  async function expectRefused(who, route, label) {
    const before = ownedRows();
    const res = await get(who, route, label);
    await h.assertRefused(s, {
      response: { status: res.status, headers: res.headers, body: res.text },
      table: 'provider', where: `last_name=${h.sqlString(marker)}`, before, label: `${who}: ${label}`,
    });
    D.assertNothingServed(res, { label: `${who}: ${label}`, forbidden: allPayloads() });
    return res;
  }

  /** Open a page that arms a session attribute; returns the response and whether the page itself rendered. */
  async function open(who, key) {
    const res = await get(who, KEYS[key].page, `${KEYS[key].label} page`);
    return { res, opened: res.status === 200 && KEYS[key].pageHas.test(res.text) };
  }

  async function armBackupViaMoh() {
    const res = await get('admin', R.moh, 'MOH files folder');
    h.assert(res.status === 200 && /id="reportFolder"/.test(res.text), `The MOH files folder page did not render (HTTP ${res.status})`);
    h.assert(res.text.includes(`servlet/BackupDownload?filename=${seeds.moh.name}`),
      'The MOH files listing does not show the marker file with its BackupDownload link');
  }

  await s.step(STEP.setup, async () => {
    for (const [role, expected] of Object.entries(ROLE_EXPECTATIONS)) {
      const held = fixture.rolePrivileges(role);
      const holds = object => held.some(entry => entry.startsWith(`${object}:`) && !entry.endsWith(':o'));
      for (const object of expected.holds) h.assert(holds(object), `The ${role} role no longer holds ${object}; this check assumes it does`);
      for (const object of expected.lacks) h.assert(!holds(object), `The ${role} role now holds ${object}; this check assumes it does not`);
    }
    for (const role of ROLES) {
      logins[role] = await signIn(s, fixture.addLogin(role));
      contexts[role] = logins[role].context;
    }
    contexts.anon = await h.newContext(s.context.browser(), config);
  });

  await s.step(STEP.seed, async () => {
    const documents = directory(env.DOCUMENT_DIR, 'DOCUMENT_DIR', 'DOCUMENT_DIR');
    if (!documents.ok) throw new h.SkipCheck(`${documents.reason}; the OBEC row and the document-scope row need it`);
    seeds.obec = files.seed(documents.dir, names.obec, 'obec');
    seeds.obec.nested = files.seedNested(documents.dir, `${marker}-sub-obec`, `${marker}-nested.txt`, 'obec nested');
    seeds.doc = files.seed(documents.dir, `${marker}-doc.pdf`, 'stored document');

    const ohip = directory(env.OHIP_DISK_DIR, 'HOME_DIR', 'OHIP_DISK_DIR / HOME_DIR');
    if (!ontario) skip('OscarDownload OHIP claim disk', notOntario());
    else if (!ohip.ok) skip('OscarDownload OHIP claim disk', ohip.reason);
    else {
      seeds.ohip = files.seed(ohip.dir, names.ohip, 'ohip');
      seeds.ohip.nested = files.seedNested(ohip.dir, `${marker}-sub-ohip`, `${marker}-nested.txt`, 'ohip nested');
    }

    const inbox = directory(env.ONEDT_INBOX, 'ONEDT_INBOX', 'ONEDT_INBOX');
    if (!ontario) skip('BackupDownload via the MOH files folder', notOntario());
    else if (!inbox.ok) skip('BackupDownload via the MOH files folder', inbox.reason);
    else {
      seeds.moh = files.seed(inbox.dir, `${marker}-moh.txt`, 'moh');
      seeds.moh.nested = files.seedNested(inbox.dir, `${marker}-sub-moh`, `${marker}-nested.txt`, 'moh nested');
    }

    const backup = unreadable ? { ok: false, reason: unreadableReason } : D.usableDirectory(property('backup_path'), 'backup_path');
    if (!backup.ok) skip('admin/ViewAdminBackupDownload listing and bytes', backup.reason);
    else seeds.backup = files.seed(backup.dir, `${marker}-backup.txt`, 'backup');

    const logging = unreadable ? { ok: false, reason: unreadableReason } : D.usableDirectory(property('LOGGING_PATH'), 'LOGGING_PATH');
    if (!logging.ok) skip('admin/ViewOscarLogging content', /is not set$/.test(logging.reason) ? `${logging.reason}; the application reads its properties at start, so setting it here would need a restart` : logging.reason);
    else {
      const body = Buffer.from(`${D.LOG_SCRIPT}${marker} log\n`, 'utf8');
      seeds.logGeneral = files.seed(logging.dir, `report${LOG_DATE.replace(/-/g, '')}.html`, 'log', body);
      seeds.logMysql = files.seed(logging.dir, `reportmysql${LOG_DATE.replace(/-/g, '')}.html`, 'log', body);
    }
  });

  await s.step(STEP.backupBytes, async () => {
    if (!seeds.moh) return;
    await armBackupViaMoh();
    const res = await get('admin', R.backup(seeds.moh.name), 'BackupDownload');
    D.assertServedExactly(res, { bytes: seeds.moh.bytes, name: seeds.moh.name, label: 'BackupDownload (MOH files folder)' });
    backupVia = { name: 'the MOH files folder', arm: armBackupViaMoh, seed: seeds.moh };
  });

  await s.step(STEP.backupPage, async () => {
    const page = await get('admin', R.backupPage, 'backup page');
    h.assert(page.status === 200 && page.fromApp, `The backup page did not render for the administrator (HTTP ${page.status})`);
    const listing = /class="table table-striped/.test(page.text);
    const noDirectory = /class="alert alert-danger/.test(page.text);
    h.assert(listing !== noDirectory, 'The backup page is neither the file listing nor the "no backup directory" alert');
    if (!listing) {
      if (seeds.backup) skip('admin/ViewAdminBackupDownload listing and bytes', 'the page says the service cannot see backup_path, although it exists on this host');
      return;
    }
    if (!seeds.backup) {
      skip('admin/ViewAdminBackupDownload listing and bytes', 'the service lists backup_path but this run cannot write there');
      return;
    }
    h.assert(page.text.includes(`servlet/BackupDownload?filename=${seeds.backup.name}`), 'The backup page does not list the marker file with its link');
    const res = await get('admin', R.backup(seeds.backup.name), 'BackupDownload');
    D.assertServedExactly(res, { bytes: seeds.backup.bytes, name: seeds.backup.name, label: 'BackupDownload (backup_path)' });
    backupVia = backupVia || { name: 'the backup page', arm: async () => { await get('admin', R.backupPage, 'backup page'); }, seed: seeds.backup };
  });

  await s.step(STEP.logPage, async () => {
    const form = await get('admin', R.log(LOG_DATE, 'general'), 'log viewer');
    h.assert(form.status === 200 && /id="logForm"/.test(form.text), `The log viewer did not render for the administrator (HTTP ${form.status})`);
    const bad = await get('admin', R.log('nope', 'general'), 'log viewer, invalid date');
    h.assert(bad.status === 400 && bad.fromApp, `An invalid report date was not refused with the application's 400 (HTTP ${bad.status})`);
    D.assertNothingServed(bad, { label: 'log viewer, invalid date', forbidden: allPayloads() });
    if (!seeds.logGeneral) {
      // The viewer itself says whether it has a directory: when the installed properties set none, it must say so.
      const configured = (property('LOGGING_PATH') || '').trim() !== '';
      h.assert(configured || unreadable || /Logging path is not configured/.test(form.text),
        'The log viewer reports a logging path although the installed properties set none');
      return;
    }
    for (const [type, seed] of [['general', seeds.logGeneral], ['mysql', seeds.logMysql]]) {
      const page = await get('admin', R.log(LOG_DATE, type), `log viewer, ${type}`);
      h.assert(page.status === 200, `The ${type} log report answered HTTP ${page.status}`);
      const problems = D.logViewerProblems(page.text, marker);
      h.assert(!problems.length, `The ${type} log report is wrong: ${problems.join('; ')}`);
      h.assert(seed.bytes.length > 0, 'The seeded log is empty');
    }
    const invalid = await get('admin', R.log(LOG_DATE, 'bogus'), 'log viewer, invalid type');
    h.assert(/Invalid report type/.test(invalid.text) && !invalid.text.includes(marker), 'An invalid report type was not refused, or showed log content');
  });

  await s.step(STEP.oscarBytes, async () => {
    for (const [key, spec] of Object.entries(KEYS)) {
      const seed = seeds[spec.seed];
      if (!seed) continue;
      const { res: page, opened } = await open('admin', key);
      h.assert(opened, `The ${spec.label} page did not open for the administrator (HTTP ${page.status})`);
      const res = await get('admin', R.oscar(key, seed.name), `OscarDownload ${key}`);
      D.assertServedExactly(res, { bytes: seed.bytes, name: seed.name, label: `OscarDownload ${key}` });
    }
  });

  await s.step(STEP.refuseBackup, async () => {
    const name = seeds.moh ? seeds.moh.name : `${marker}-moh.txt`;
    for (const role of ROLES) await expectRefused(role, R.backup(name), 'BackupDownload');
  });

  await s.step(STEP.refuseBackupPage, async () => {
    for (const role of ROLES) {
      const res = await expectRefused(role, R.backupPage, 'backup page');
      h.assert(!/class="table table-striped/.test(res.text), `${role}: the refusal page lists backup files`);
    }
  });

  await s.step(STEP.refuseLog, async () => {
    for (const role of ROLES) {
      const res = await expectRefused(role, R.log(LOG_DATE, 'general'), 'log viewer');
      h.assert(!/id="log-results"/.test(res.text) && !/id="logForm"/.test(res.text), `${role}: the refusal page shows the log viewer`);
    }
  });

  await s.step(STEP.oscarArming, async () => {
    // Cold: a session that never opened the page is refused, whatever its role.
    for (const [key, spec] of Object.entries(KEYS)) {
      const seed = seeds[spec.seed];
      if (!seed) continue;
      for (const role of ROLES) await expectRefused(role, R.oscar(key, seed.name), `OscarDownload ${key}, page not opened`);
    }
    // After the page: the role that may open it is served, the one that may not stays refused.
    for (const [key, spec] of Object.entries(KEYS)) {
      const seed = seeds[spec.seed];
      if (!seed) continue;
      for (const role of ROLES) {
        const { res: page, opened } = await open(role, key);
        h.assert(opened === spec.mayOpen[role],
          `${role} ${opened ? 'opened' : `could not open (HTTP ${page.status}${page.headers.location ? ` to ${h.pathOnly(page.headers.location)}` : ''})`} the ${spec.label} page; its role ${spec.mayOpen[role] ? 'holds' : 'lacks'} the page's object`);
        if (opened) {
          D.assertServedExactly(await get(role, R.oscar(key, seed.name), `OscarDownload ${key}`),
            { bytes: seed.bytes, name: seed.name, label: `${role}: OscarDownload ${key}` });
        } else {
          await expectRefused(role, R.oscar(key, seed.name), `OscarDownload ${key}, page refused`);
        }
      }
    }
    // An allowed key whose page this session never opened stays refused for the administrator too.
    const unarmed = await get('admin', R.oscar('homepath', seeds.obec.name), 'OscarDownload homepath');
    h.assert(unarmed.status === 403 && unarmed.fromApp, `The homepath key answered HTTP ${unarmed.status}, not the application's 403, for a session that never armed it`);
    D.assertNothingServed(unarmed, { label: 'OscarDownload homepath', forbidden: allPayloads() });
  });

  await s.step(STEP.anonymous, async () => {
    for (const route of [R.backup(`${marker}-moh.txt`), R.oscar('obecdownload', seeds.obec.name)]) {
      const res = await get('anon', route, 'servlet without a session');
      h.assert(res.status === 401 && res.fromApp && res.type === 'text/plain',
        `A request without a session answered HTTP ${res.status} (${res.type || 'no type'}), not the application's 401 text/plain`);
      D.assertNothingServed(res, { label: 'servlet without a session', forbidden: allPayloads() });
    }
    for (const route of [R.backupPage, R.log(LOG_DATE, 'general')]) {
      const res = await get('anon', route, 'page without a session');
      // The unauthenticated-page contract (UnauthenticatedRejectionResolver): the application redirects a browser page to /logoutPage.
      h.assert(res.fromApp && !res.waf && res.status >= 300 && res.status < 400 && /\/logoutPage$/.test(res.headers.location || ''),
        `A page without a session answered HTTP ${res.status}${res.fromApp ? '' : ' from outside the application'}, not the application's redirect to /logoutPage`);
      h.assert(!/id="logForm"|class="table table-striped/.test(res.text), 'A page without a session rendered its content');
      D.assertNothingServed(res, { label: 'page without a session', forbidden: allPayloads() });
    }
  });

  await s.step(STEP.traversal, async () => {
    const targets = [];
    if (backupVia) {
      targets.push({ name: `BackupDownload (${backupVia.name})`, arm: backupVia.arm, seed: backupVia.seed, url: name => R.backup(name), bare: 'servlet/BackupDownload' });
    }
    else skip('BackupDownload traversal', 'no readable BackupDownload directory on this install');
    for (const [key, spec] of Object.entries(KEYS)) {
      const seed = seeds[spec.seed];
      if (!seed) continue;
      targets.push({
        name: `OscarDownload ${key}`, arm: async () => { await open('admin', key); }, seed, url: name => R.oscar(key, name), key,
        bare: `servlet/OscarDownload?homepath=${key}`,
      });
    }
    const outcomes = { 'app-400': 0, waf: 0 };
    const notReached = [];
    // The application owes a rejected name or key its 400 and nothing else: a 403/405 from it would be a different gate answering.
    const probe = async (target, route, label, { mustReach }) => {
      const res = await get('admin', route, label);
      const outcome = D.judgeBlocked(res, { label: `${target.name} ${label}`, forbidden: allPayloads(), statuses: [400] });
      outcomes[outcome] += 1;
      target.sent += mustReach ? 1 : 0;
      if (outcome === 'waf') {
        notReached.push(`${target.name} ${label}`);
        h.assert(!mustReach, `${target.name}: ${label} was blocked by the front door; it was meant to reach the application`);
      } else if (mustReach) target.reached += 1;
    };
    for (const target of targets) {
      target.sent = 0;
      target.reached = 0;
      await target.arm();
      // The control: the same armed session is served the real file, so a refusal below is about the request.
      D.assertServedExactly(await get('admin', target.url(target.seed.name), 'traversal control'),
        { bytes: target.seed.bytes, name: target.seed.name, label: `${target.name} control` });
      const nested = target.seed.nested;
      const relative = nested ? nested.relative : null;
      for (const filename of FILENAME_LITERAL) await probe(target, target.url(filename), `filename=${filename}`, { mustReach: false });
      const plain = [...FILENAME_PLAIN];
      if (relative) plain.unshift(relative, relative.replaceAll('/', '%2f'), relative.replaceAll('/', '%5c'));
      for (const filename of plain) await probe(target, target.url(filename), `filename=${filename}`, { mustReach: true });
      await probe(target, target.bare, 'no filename', { mustReach: true });
      if (target.key) {
        for (const key of KEY_LITERAL) await probe(target, R.oscar(key, target.seed.name), `homepath=${key}`, { mustReach: false });
        for (const key of KEY_PLAIN) await probe(target, R.oscar(key, target.seed.name), `homepath=${key || '(empty)'}`, { mustReach: true });
        await probe(target, `servlet/OscarDownload?filename=${target.seed.name}`, 'no homepath', { mustReach: true });
      }
      // Every probe meant to reach the application drew the application's 400, none of them the front door's page.
      h.assert(target.sent > 0 && target.reached === target.sent,
        `${target.name}: ${target.reached} of ${target.sent} probes meant to reach the application drew its 400`);
    }
    console.log(`  NOTE ${NAME}: traversal probes -- application 400: ${outcomes['app-400']}, front door blocked: ${outcomes.waf}`);
    if (notReached.length) {
      console.log(`  NOTE ${NAME}: application not reached for ${notReached.length} literal traversal shape(s) -- the front door's rules blocked them first, `
        + 'so the application\'s own answer to them was not observed (no direct Tomcat port exists in this install to repeat them without the front door)');
    }
  });

  await s.step(STEP.neverHtml, async () => {
    const downloads = seen.filter(entry => /^attachment\s*;/i.test(entry.res.disposition));
    h.assert(downloads.length >= 1, 'No download was seen, so "never an HTML page" was not exercised');
    for (const { label, res } of downloads) {
      h.assert(res.status === 200 && res.type === 'application/octet-stream' && !D.startsLikeHtml(res.body),
        `${label}: an attachment response is HTTP ${res.status} ${res.type} or starts like an HTML page`);
    }
    for (const { label, res } of seen) {
      if (res.status >= 300) {
        h.assert(!/^attachment\s*;/i.test(res.disposition) && res.type !== 'application/octet-stream',
          `${label}: HTTP ${res.status} was sent as an attachment`);
      }
    }
    console.log(`  NOTE ${NAME}: ${seen.length} responses inspected, ${downloads.length} downloads`);
  });

  await s.step(STEP.frontDoor, async () => {
    h.assert(!config.expectFrontDoor || nginx,
      'EXPECT_FRONT_DOOR is set but no response carried an nginx Server header; the run did not go through the front door');
  });

  // Created only now, after every step that uses the doctor or the test login on a multi-object page. A role row
  // for _report or _admin.reporting changes the order in which the database returns the rows
  // OscarRoleObjectPrivilege.getPrivilegeProp sorts only by an all-zero priority, and that method keeps the LAST row
  // per role across the listed objects, so with two such rows present the doctor (_report x, _admin.reporting o) is
  // refused the OBEC page it holds the right to (finding 205). Rows are removed again by fixture.cleanup().
  await s.step(STEP.leastPrivilege, async () => {
    // addRole asserts each role holds exactly the one right asked for.
    logins.reportOnly = await signIn(s, fixture.addLogin(fixture.addRole({ _report: 'r' })));
    contexts.reportOnly = logins.reportOnly.context;
    logins.reportingOnly = await signIn(s, fixture.addLogin(fixture.addRole({ '_admin.reporting': 'r' })));
    contexts.reportingOnly = logins.reportingOnly.context;
  });

  await s.step(STEP.logGate, async () => {
    // The gate is _admin OR _admin.reporting (ViewOscarLogging2Action; admin.jsp lists Server Log under both): intended, so it is asserted, not logged.
    const admitted = await get('reportingOnly', R.log(LOG_DATE, 'general'), 'log viewer');
    h.assert(admitted.status === 200 && admitted.fromApp && /id="logForm"/.test(admitted.text),
      `A login holding only _admin.reporting r got HTTP ${admitted.status}, not the log viewer`);
    // A login that holds neither object (only _report r) is refused.
    const refused = await expectRefused('reportOnly', R.log(LOG_DATE, 'general'), 'log viewer');
    h.assert(!/id="logForm"/.test(refused.text), 'The refusal page shows the log viewer');
  });

  await s.step(STEP.reportingOnlyBackup, async () => {
    // _admin.reporting opens the viewer only; the backup routes need _admin or _admin.backup.
    const name = seeds.moh ? seeds.moh.name : `${marker}-moh.txt`;
    await expectRefused('reportingOnly', R.backup(name), 'BackupDownload');
    const page = await expectRefused('reportingOnly', R.backupPage, 'backup page');
    h.assert(!/class="table table-striped/.test(page.text), 'The refusal page lists backup files');
  });

  await s.step(STEP.documentControls, async () => {
    // The login holds `_report` r and nothing else (addRole asserted it), so it cannot grant itself anything.
    h.assert(fixture.rolePrivileges(logins.reportOnly.login.role).join() === '_report:r', 'The _report-only role holds more than _report r');
    const { res: page, opened } = await open('reportOnly', 'obecdownload');
    h.assert(opened, `The OBEC page did not open for the _report-only login (HTTP ${page.status})`);
    // OBEC output, in the shape the application writes, is served: the OBEC session is armed and DOCUMENT_DIR is readable.
    D.assertServedExactly(await get('reportOnly', R.oscar('obecdownload', seeds.obec.name), 'OscarDownload obecdownload'),
      { bytes: seeds.obec.bytes, name: seeds.obec.name, label: '_report-only login: OBEC output' });
    // And the document manager refuses the login: it holds no _edoc (ManageDocument.display checks _edoc r first).
    await expectRefused('reportOnly', 'documentManager/ManageDocument?method=display&doc_no=1', 'document manager display');
  });

  // The ONLY assertion the finding breaks: this step carries no control or precondition (they are above).
  await s.step(STEP.documentScope, async () => {
    const res = await get('reportOnly', R.oscar('obecdownload', seeds.doc.name), 'OscarDownload obecdownload, stored document');
    const served = res.status === 200 && res.body.equals(seeds.doc.bytes);
    h.assert(!served, `OscarDownload served a ${seeds.doc.bytes.length}-byte stored-document file from DOCUMENT_DIR to a login that holds _report r and no _edoc`);
    h.assert(res.fromApp && res.status >= 400 && res.status < 500,
      `OscarDownload answered HTTP ${res.status} to a stored-document request from a login without _edoc, not the application's 4xx refusal`);
    D.assertNothingServed(res, { label: 'stored-document request', forbidden: [seeds.doc.bytes] });
  });

  if (skipped.length) console.log(`  NOTE ${NAME}: ${skipped.length} row(s) skipped -- ${skipped.join(' | ')}`);
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false, openMaster: false });
module.exports = { workflow, STEP };
