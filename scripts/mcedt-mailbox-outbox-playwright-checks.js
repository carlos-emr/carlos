#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * MCEDT mailbox, the LOCAL half (Ontario): the OHIP claim file staged into the EDT outbox, the
 * Upload tab's Add and Delete, and Change Password. Nothing here talks to the Ministry.
 *
 * USER PATH. Administration ▸ Billing ▸ MCEDT Mailbox (the packaged default, mcedt.mailbox.enabled=true,
 * leftNav.jspf: the framed page /mcedt/kaimcedt) ▸ Upload tab ▸ Add ▸ Create / Delete Selected, and
 * Menu ▸ Change Password. The OHIP claim file is the one Administration ▸ Billing ▸ Generate OHIP diskette
 * writes, produced the way billing-on-group-disk-zero-total produces it (its fixture and cleanup are reused).
 *
 * NEVER REACHED. Upload & Submit (the tab's Submit button), "Upload new files" and "Download new files" on the
 * Menu tab, the Sent and Download tabs, Re-Submit and Check Connection are never clicked, and the older
 * Administration ▸ MCEDT Interface page (/mcedt/mcedt, which lists the Ministry's mailbox on load) is never
 * opened. The browser's own requests are recorded and a step asserts none went to an MCEDT service route and
 * none left the application's origin; every probe sent by script is one of the three local mutators below.
 * mcedt.service.url is empty on the packaged install, so even a stray call could not leave the machine.
 *
 * WHAT IS ASSERTED, in step order (the labels below are the manifest's expectedFailure vocabulary):
 *   - the Ontario install, the paths and the clinic-wide state: the MCEDT password property is PARKED (renamed
 *     for the run, never read or printed; lib/mcedt-local-state.js) and the outbox listing and its two timestamp
 *     files are snapshotted, both restored byte for byte however the run ends;
 *   - Menu ▸ Upload: the mailbox opens with its four tabs, the Upload tab lists the outbox and offers Add,
 *     Delete Selected and Submit (Submit is only inspected, disabled until a file is ticked);
 *   - the OHIP diskette is generated for an owned provider (the group-disk fixture) and its claim file is on the
 *     HOME_DIR disk;
 *   - Add (Upload ▸ Add ▸ Create) writes the fixture claim file to ONEDT_OUTBOX byte for byte and lists it once;
 *   - Add refuses a name that is not an OHIP or OBEC claim file (notes.txt, a month letter beyond L) and a name
 *     that carries a path (../, a leading /, a sub-directory; ..\ and C: are blocked by the front door and
 *     counted as "application not reached"), with the application's own message and nothing written anywhere;
 *   - Delete Selected removes only the ticked owned file: the OBEC-shaped bystander and a marker beside the outbox
 *     survive byte for byte; a list whose second name carries a path is refused and removes nothing;
 *   - Change Password writes the property and no page or response echoes it; a second change updates the row
 *     (still one row), it does not add another;
 *   - for Delete, Add and Change Password: a request with a valid token works (the control), then GET and the
 *     same POST WITHOUT its token are each refused with h.assertRefused (the application's own 403/405, never
 *     the front door's page) and change neither the outbox, the parent directory nor the property;
 *   - a doctor (holds _billing, not _admin.billing) is refused by every route, and a login holding
 *     _admin.billing for reading only is served the pages but refused all three mutators with a valid token;
 *   - no step touched the MCEDT service routes;
 *   - the mailbox is reopened three times from Administration and what each open left in the outbox is recorded
 *     (this step only drives and records, so a failure to open reads as an ordinary failure);
 *   - LAST, pinned to finding 216: the recorded opens show the generated claim file copied into ONEDT_OUTBOX byte
 *     for byte, listed once, with no duplicate on reopening. ActionUtils reads <outbox>/.timestamp but writes
 *     <parent>/outbox.timestamp, so the copy window is empty and the file is never staged. It is the last step
 *     because a script stops at its first failing step; everything above runs first.
 *
 * TWO MORE FINDINGS, each pinned by its own manifest entry running this script with MCEDT_PIN (a script stops at
 * its first failing step, so one run cannot pin three defects; the precedent is allergy-add-penicillin-shortcut-id):
 *   - MCEDT_PIN=short-name (mcedt-mailbox-outbox-short-name), finding 217: ActionUtils.isOHIPFile reads
 *     filename.substring(0, 2), so Add of a ONE-character name ("x") answers HTTP 500 instead of the refusal every
 *     other non-claim name gets. Runs the menu, a two-character control ("ab" is refused with the message), then the pin
 *     (the status is not a server error) and, once that passes, the same refusal message the control gets.
 *   - MCEDT_PIN=plaintext (mcedt-mailbox-outbox-plaintext), finding 189: Change Password stores the typed text as
 *     given. Runs the menu and the Change Password step (which does NOT judge the stored value, so it keeps passing
 *     when the credential is encrypted at rest), then the pin: the stored value is not the typed one.
 * Both variants run only the steps their finding needs; the default run is the full flow above.
 *
 * Environment (suite-env.sh): OHIP_DISK_DIR (the application's HOME_DIR), GROUP_DISK_SERVICE_DATE,
 * GROUP_DISK_PAID_CODE and GROUP_DISK_TEMPLATE_PROVIDER (the group-disk fixture), MYSQL_*.
 * ONEDT_OUTBOX and the billing region come from the environment or CARLOS_PROPERTIES_FILE (default
 * /etc/carlos-emr/carlos.properties), because the application reads its properties once at start and the check
 * must use what is configured. Ontario only: any other billregion SKIPs with the reason.
 *
 * Fixtures: the group-disk billing rows (three providers, two claims, one disk), two throwaway logins and a
 * read-only role, marker files beside and in the outbox. All are removed and asserted gone; the check changes
 * clinic-wide state (the MCEDT password, the outbox) and must run alone (live wrapper: EXCLUSIVE=1).
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const { captureRequest } = require('./lib/get-reject-probe');
const { buildReplay, sendReplay, sessionToken } = require('./lib/mutation-replay');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const D = require('./lib/download-contract');
const L = require('./lib/mcedt-local-state');
const { createFixture, removeFixture, checkedDiskDirectory } = require('./billing-on-group-disk-zero-total-playwright-checks');

const NAME = 'mcedt-mailbox-outbox';
const PROPERTIES_FILE = process.env.CARLOS_PROPERTIES_FILE || '/etc/carlos-emr/carlos.properties';

// Literal step labels, so the manifest's expectedFailure can name the one it fails at.
const STEP = {
  setup: 'the install is Ontario, the MCEDT paths are usable, and the password property and the outbox are snapshotted',
  menu: 'Administration ▸ Billing ▸ MCEDT Mailbox ▸ Upload opens the outbox listing with Add, Delete Selected and Submit',
  diskette: 'the OHIP diskette is generated for an owned provider and its claim file is on the HOME_DIR disk',
  add: 'Upload ▸ Add ▸ Create writes the fixture claim file to ONEDT_OUTBOX byte for byte and lists it once',
  addRefused: 'Add refuses a name that is not an OHIP or OBEC claim file and a name that carries a path, and writes nothing',
  remove: 'Upload ▸ Delete Selected removes only the ticked owned file',
  removeRefused: 'Delete Selected refuses a list with a path in it and removes nothing outside the outbox',
  password: 'Menu ▸ Change Password writes the mcedt_account_password property and no page echoes it',
  mutators: 'Delete, Add and Change Password work with a token, and GET and a tokenless POST of each are refused and change nothing',
  privileges: 'a login without _admin.billing is refused by every MCEDT route, and one holding it for reading only cannot change anything',
  shortNameControl: 'Add refuses a two-character name that is not a claim file with the application\'s message, and the one-character request is sent',
  noService: 'the run never posted to an MCEDT service route and the browser reached only the application',
  shortName: 'Add of a one-character name is not answered with a server error',
  shortNameMessage: 'Add refuses a one-character name with the application\'s message',
  plaintext: 'the stored MCEDT password is not the text that was typed',
  reopen: 'Administration ▸ MCEDT Mailbox ▸ Upload is opened three times in a row and what each open left in ONEDT_OUTBOX is recorded',
  staged: 'the generated OHIP claim file is copied to ONEDT_OUTBOX byte for byte and listed once, and reopening does not duplicate it',
};

// MCEDT_PIN selects which finding the run pins, because a script stops at its first failing step and so cannot pin
// three defects in one run. Unset: the full flow, pinned to finding 216 (the claim file is never staged). `short-name`
// (entry mcedt-mailbox-outbox-short-name): the menu, then finding 217. `plaintext` (entry mcedt-mailbox-outbox-plaintext):
// the menu and Change Password, then finding 189. The variants run only the steps their finding needs.
const PIN = (process.env.MCEDT_PIN || '').trim();
/** MCEDT_PIN must be unset, short-name or plaintext. Judged when the check runs (workflow), never when the module is required. */
function validatePin(value = PIN) {
  if (!['', 'short-name', 'plaintext'].includes(value)) throw new Error(`MCEDT_PIN must be unset, short-name or plaintext, not ${value}`);
}

// Routes whose handlers call the Ministry (Upload & Submit, Upload new files, Download, Sent, Re-Submit, the old interface).
const SERVICE_PATHS = /\/mcedt\/(autoUpload|kaiautodl|download|resourceInfo|reSubmit|mcedt|uploads|update|info)$/;
const SERVICE_METHODS = /(^|&)method=(uploadToMcedt|submitToMcedt|uploadSubmitToMcedt|loadDownloadList|loadSentList)(&|$)/;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function until(check, message, timeout = 20000) {
  const end = Date.now() + timeout;
  for (;;) {
    const value = await check();
    if (value) return value;
    h.assert(Date.now() < end, message);
    await sleep(150);
  }
}

async function workflow(s) {
  validatePin();
  const { sql, marker, provider, config, context } = s;
  const full = PIN === '';
  const q = h.sqlString;
  const origin = config.baseUrl.origin;
  const hex = marker.slice('FAKE-PW'.length);
  const n = BigInt(`0x${hex}`);
  const digits = (divisor, width = 6) => String((n / divisor) % (10n ** BigInt(width))).padStart(width, '0');

  // ---- configuration the application reads once at start ------------------------------------------------
  const propertiesText = (() => { try { return fs.readFileSync(PROPERTIES_FILE, 'utf8'); } catch (error) { return null; } })();
  if (propertiesText === null) throw new h.SkipCheck(`the properties file ${PROPERTIES_FILE} is unreadable, so the billing region and ONEDT_OUTBOX are unknown`);
  const billRegion = D.propertyValue(propertiesText, 'billregion');
  if (billRegion !== 'ON') throw new h.SkipCheck(`billregion=${billRegion === undefined ? '(not set)' : billRegion}: the OHIP diskette and the MCEDT mailbox are Ontario`);
  const outboxSetting = process.env.ONEDT_OUTBOX || D.propertyValue(propertiesText, 'ONEDT_OUTBOX');
  const outboxUsable = D.usableDirectory(outboxSetting, 'ONEDT_OUTBOX');
  if (!outboxUsable.ok) throw new h.SkipCheck(outboxUsable.reason);
  const outbox = fs.realpathSync(outboxUsable.dir);
  const parentUsable = D.usableDirectory(path.dirname(outbox), 'the directory holding ONEDT_OUTBOX');
  if (!parentUsable.ok) throw new h.SkipCheck(parentUsable.reason);
  const diskDir = checkedDiskDirectory(process.env.OHIP_DISK_DIR || '');
  const serviceDate = process.env.GROUP_DISK_SERVICE_DATE || '2003-02-03';
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(serviceDate), 'GROUP_DISK_SERVICE_DATE must be YYYY-MM-DD');
  const paidCode = (process.env.GROUP_DISK_PAID_CODE || 'A007A').toUpperCase();
  h.assert(/^[A-Z]\d{3}[A-Z]$/.test(paidCode), 'GROUP_DISK_PAID_CODE must look like A007A');
  const templateProvider = process.env.GROUP_DISK_TEMPLATE_PROVIDER || '999998';
  h.assert(/^\d{1,6}$/.test(templateProvider), 'GROUP_DISK_TEMPLATE_PROVIDER must be a provider number');

  // ---- names every file of the run carries (OHIP and OBEC shapes the application accepts) ----------------
  const names = {
    add: `HK${digits(1n)}.998`,
    sacrificial: `HJ${digits(7n)}.997`,
    control: `HI${digits(13n)}.996`,
    missing: `HL${digits(29n)}.995`,
    badMonth: `HM${digits(31n)}.998`,
    notes: `notes${hex.slice(0, 10)}.txt`,
    bystander: D.realShapeNames(marker).obec,
    parentMarker: `${marker}-parent.txt`,
  };
  const passwords = { first: `${marker}-pw1`, second: `${marker}-pw2`, control: `${marker}-pw3`, probe: `${marker}-probe` };
  const filler = crypto.createHash('sha256').update(marker).digest();
  // Not valid UTF-8 (0xE9, 0xFF, 0xFE) and CRLF line ends: a servlet that re-encodes or trims the file changes them.
  const claimBytes = (label) => Buffer.concat([Buffer.from(`${marker} ${label} fixture claim file\r\n`, 'latin1'),
    Buffer.from([0xe9, 0xff, 0xfe, 0x0d, 0x0a]), filler, Buffer.from('\r\n')]);

  let generated = null; // { name, bytes }: the claim file the OHIP diskette wrote
  let admin = null; // the Administration shell page; the mailbox is its #myFrame iframe
  let ledger = null;
  let seeded = null;
  let deleteCaptured = null;
  let passwordCaptured = null;
  let addCaptured = null;

  // ---- clinic-wide state, registered BEFORE anything can change it (cleanup runs newest first) ------------
  const parking = L.createPropertyParking({ sql, name: L.PASSWORD_PROPERTY, marker });
  s.cleanup(() => parking.restore());
  s.cleanup(() => { if (ledger) ledger.restore(); });
  const markers = D.markerFiles(marker);
  s.cleanup(() => markers.remove());
  const fixtureState = { providers: {}, headerIds: [], groupNo: '', marker };
  s.cleanup(() => {
    removeFixture(sql, fixtureState, diskDir);
    if (generated) h.assert(!fs.existsSync(path.join(diskDir, generated.name)), 'The generated claim file is still on the HOME_DIR disk');
  });
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());

  const browserRequests = [];
  context.on('request', (request) => {
    const url = new URL(request.url());
    browserRequests.push({
      method: request.method(), host: url.host, path: url.pathname, type: request.resourceType(),
      post: request.method() === 'POST' ? (request.postData() || '').slice(0, 400) : '',
    });
  });

  // ---- small helpers -------------------------------------------------------------------------------------
  const url = (route) => h.appUrl(config.baseUrl, route);
  const outboxFile = (name) => path.join(outbox, name);
  const ownedRows = () => String(sql.value(`SELECT COUNT(*) FROM provider WHERE first_name=${q(marker)} OR last_name=${q(marker)}`));
  const propertyWhere = `name=${q(L.PASSWORD_PROPERTY)}`;
  const propertyRows = () => String(sql.value(`SELECT COUNT(*) FROM property WHERE ${propertyWhere}`));
  const storedDigest = () => sql.value(`SELECT IFNULL(SHA2(\`value\`,256),'') FROM property WHERE ${propertyWhere} ORDER BY id LIMIT 1`);
  const propertyHolds = (value) => sql.value(`SELECT COUNT(*) FROM property WHERE ${propertyWhere} AND value=${q(value)}`);
  const parentNames = () => fs.readdirSync(ledger.parent).sort();
  const visibleState = () => JSON.stringify([ledger.visible(), parentNames()]);
  const mailbox = () => admin.frameLocator('#myFrame');
  const frameUrl = () => { const frame = admin.frame({ name: 'myFrame' }); return frame ? frame.url() : ''; };
  // The names the Upload tab lists. #formUpload is in the document that carries the list, so waiting for it keeps a
  // read from landing in the gap while the frame swaps documents (an empty list there is not "the file is gone").
  const listed = async () => {
    await mailbox().locator('#formUpload').waitFor({ state: 'attached', timeout: 20000 });
    return mailbox().locator('#upload input.fileNames').evaluateAll((boxes) => boxes.map((box) => box.value));
  };
  const asText = (buffer) => buffer.toString('latin1');

  async function openMailboxFromAdministration() {
    if (!admin || admin.isClosed()) {
      ({ page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
        { context, recorder: s.recorder, label: 'administration', timeout: 20000 }));
    }
    const link = admin.locator('a.xlink[rel$="/mcedt/kaimcedt"]').first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    await until(() => /\/mcedt\/kaimcedt$/.test(frameUrl()), 'MCEDT Mailbox did not load in the Administration frame');
    await mailbox().locator('ul.tabNavigation').waitFor({ state: 'visible', timeout: 20000 });
  }

  async function openUploadTab() {
    await mailbox().locator('ul.tabNavigation a', { hasText: 'Upload' }).click();
    await until(() => /\/mcedt\/upload$/.test(frameUrl()), 'The Upload tab did not open /mcedt/upload');
    await mailbox().locator('#formUpload').waitFor({ state: 'attached', timeout: 20000 });
    await mailbox().locator('#upload').waitFor({ state: 'visible', timeout: 20000 });
  }

  async function post(ctx, route, { form, multipart, token = null, referer }) {
    const headers = { origin, referer: url(referer) };
    if (token) headers['CSRF-TOKEN'] = token;
    const options = { method: 'POST', headers, maxRedirects: 0, failOnStatusCode: false, timeout: 60000 };
    if (multipart) options.multipart = { ...multipart, ...(token ? { 'CSRF-TOKEN': token } : {}) };
    else options.form = { ...form, ...(token ? { 'CSRF-TOKEN': token } : {}) };
    return ctx.request.fetch(url(route), options);
  }
  const get = (ctx, route) => ctx.request.get(url(route), { failOnStatusCode: false, maxRedirects: 0, timeout: 60000 });
  const addForm = (name, bytes) => ({ method: 'addUpload', addUploadFile: { name, mimeType: 'application/octet-stream', buffer: bytes } });
  const deleteForm = (fileName) => ({ method: 'deleteUpload', fileName, description: '', resourceType: '' });
  const alertText = (html) => [...String(html).matchAll(/<div class="alert alert-danger"[^>]*>\s*<p>([^<]*)<\/p>/g)].map((match) => match[1].trim()).join(' | ');

  /**
   * Send the request `send()` and require the application's own refusal (403/405 carrying its header, or the
   * securityError page) and an unchanged owned count. The count is taken BEFORE the request is sent.
   * `kind` is 'property' (the MCEDT password rows) or 'file' (the owned provider rows: the outbox has no table,
   * so the files are compared separately by unchanged()).
   */
  async function refused(label, send, kind = 'file') {
    const spec = kind === 'property'
      ? { table: 'property', where: propertyWhere, count: propertyRows }
      : { table: 'provider', where: `first_name=${q(marker)} OR last_name=${q(marker)}`, count: ownedRows };
    const before = spec.count();
    const response = await send();
    return h.assertRefused(s, { response, table: spec.table, where: spec.where, before, label });
  }

  /** Run `probe` and require that it changed neither the outbox, the directory beside it nor the property. */
  async function unchanged(label, probe) {
    const files = visibleState();
    const digest = parking.digest();
    const result = await probe();
    h.assert(visibleState() === files, `${label}: the outbox or the directory beside it changed`);
    h.assert(parking.digest() === digest, `${label}: the ${L.PASSWORD_PROPERTY} property changed`);
    return result;
  }

  // =========================================================================================================
  await s.step(STEP.setup, async () => {
    ledger = L.createOutboxLedger({ outbox });
    h.assert(ledger.found.every((entry) => entry.kind === 'file' || entry.kind === 'dir'), 'The outbox holds an entry that is neither a file nor a directory');
    console.log(`  outbox before: ${ledger.found.length} entr${ledger.found.length === 1 ? 'y' : 'ies'}; password property rows before: ${propertyRows()}`);
    parking.park();
    h.assert(propertyRows() === '0', 'The MCEDT password rows were not parked');
    // The bystander the Delete step must leave alone, and a file beside the outbox a traversal would reach.
    seeded = {
      bystander: markers.seed(outbox, names.bystander, 'bystander'),
      parent: markers.seed(path.dirname(outbox), names.parentMarker, 'parent'),
    };
    // The role this check assumes: the doctor holds _billing (a billing login) but not _admin.billing (the MCEDT gate).
    const doctor = fixture.rolePrivileges('doctor').map((entry) => entry.split(':')[0]);
    h.assert(doctor.includes('_billing') && !doctor.includes('_admin.billing') && !doctor.includes('_admin'),
      'The doctor role no longer holds _billing and lacks _admin.billing; this check assumes it does');
  });

  // =========================================================================================================
  await s.step(STEP.menu, async () => {
    await openMailboxFromAdministration();
    const tabs = await mailbox().locator('ul.tabNavigation a').allInnerTexts();
    h.assert(JSON.stringify(tabs.map((text) => text.trim())) === JSON.stringify(['Menu', 'Upload', 'Sent', 'Download']),
      `The mailbox tabs are ${JSON.stringify(tabs)}, not Menu, Upload, Sent, Download`);
    await mailbox().getByRole('heading', { name: 'Welcome To MCEDT' }).waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await mailbox().getByRole('button', { name: 'Change Password' }).isVisible(), 'The Menu tab has no Change Password button');
    await openUploadTab();
    for (const button of ['Add', 'Delete Selected', 'Submit']) {
      h.assert(await mailbox().locator('#upload').getByRole('button', { name: button, exact: true }).isVisible(), `The Upload tab has no ${button} button`);
    }
    // Submit and Delete Selected stay disabled until a file is ticked. Submit is never clicked by this check.
    h.assert(await mailbox().locator('#submitUpload').isDisabled() && await mailbox().locator('#deleteUpload').isDisabled(),
      'Submit and Delete Selected are enabled with no file ticked');
    h.assert((await mailbox().locator('#upload table.whiteBox tr').first().innerText()).includes('File Name'), 'The Upload tab has no file table');
    const names0 = await listed();
    h.assert(names0.includes(names.bystander), 'The Upload tab does not list the OBEC-shaped marker file the run put in the outbox');
  });

  // =========================================================================================================
  if (PIN === 'short-name') {
    // The control: a name the application judges by its two-character prefix is refused with its own message. The same
    // step sends the one-character request and records what came back, so the pinned step below judges the answer and
    // nothing else (a failed token or send reads failed-elsewhere, not known-fail).
    let oneCharacter = null;
    await s.step(STEP.shortNameControl, async () => {
      const token = await sessionToken(admin, config.baseUrl);
      await unchanged('Add of a two-character name', async () => {
        const response = await post(context, '/mcedt/addUpload', { multipart: addForm('ab', claimBytes('short')), token, referer: '/mcedt/openAddUploadMailbox' });
        const body = await response.text();
        h.assert(response.status() === 200 && Object.prototype.hasOwnProperty.call(response.headers(), h.APPLICATION_HEADER),
          `Add of a two-character name answered HTTP ${response.status()}, not the application's page`);
        h.assert(alertText(body).includes('not a supported file Name'), 'Add of a two-character name was not refused with the application\'s message');
      });
      await unchanged('Add of a one-character name', async () => {
        const response = await post(context, '/mcedt/addUpload', { multipart: addForm('x', claimBytes('short')), token, referer: '/mcedt/openAddUploadMailbox' });
        oneCharacter = { status: response.status(), message: alertText(await response.text()) };
      });
    });
    // Finding 217: ActionUtils.isOHIPFile reads filename.substring(0, 2), which throws for a one-character name.
    // Pinned: holds only the assertion the defect breaks (a server error where a refusal is due).
    await s.step(STEP.shortName, async () => {
      h.assert(oneCharacter.status < 500, `Add of a one-character name answered HTTP ${oneCharacter.status}, a server error, instead of refusing it`);
    });
    // Runs only once finding 217 is fixed (a script stops at its first failing step): the refusal must then be the
    // same one every other non-claim name gets, the page with the application's message.
    await s.step(STEP.shortNameMessage, async () => {
      h.assert(oneCharacter.status === 200 && oneCharacter.message.includes('not a supported file Name'),
        'Add of a one-character name was not refused with the application\'s message');
    });
  }

  // =========================================================================================================
  if (full) await s.step(STEP.diskette, async () => {
    const window = { serviceDate, start: serviceDate, end: serviceDate };
    createFixture(sql, { templateProvider, window, paidCode, demographicNo: process.env.GROUP_DISK_DEMOGRAPHIC_NO || '', testUser: config.testUser }, fixtureState);
    const paid = fixtureState.providers.PAID;
    const page = await context.newPage();
    await h.gotoApp(page, config.baseUrl, '/billing/CA/ON/ViewBillingONMRI');
    await h.assertNotErrorPage(page, 'Generate OHIP diskette page');
    const form = page.locator('form[name="form1"]');
    await form.waitFor({ state: 'visible', timeout: 30000 });
    await form.locator('select[name="providers"]').selectOption(paid.providerNo);
    // Both date inputs carry flatpickr: typing sets the value and the open calendar covers the submit button
    // until the user clicks away, so click the heading as an operator would (as billing-on-group-disk-zero-total).
    for (const [selector, value] of [['#xml_vdate', window.start], ['#xml_appointment_date', window.end]]) {
      await form.locator(selector).fill(value);
      await page.locator('h3').first().click();
      await page.locator('.flatpickr-calendar.open').waitFor({ state: 'detached', timeout: 10000 })
        .catch(() => page.locator('.flatpickr-calendar.open').first().waitFor({ state: 'hidden', timeout: 10000 }));
      h.assert(await form.locator(selector).inputValue() === value, `The ${selector} date did not keep ${value}`);
    }
    const useProviderMoh = form.locator('#useProviderMOH');
    if (await useProviderMoh.isChecked()) await useProviderMoh.uncheck();
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewOngenreport'), { timeout: 120000 }),
      form.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    h.assert(response.status() === 200, `Create Report answered HTTP ${response.status()}`);
    await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
    await h.assertNotErrorPage(page, 'OHIP diskette page after Create Report');
    h.assert(await page.locator('form[name="form1"]').count() === 1, 'Create Report did not return to the diskette page; disk generation failed');
    await page.close();
    const disks = sql.rows('SELECT d.id, d.ohipfilename FROM billing_on_diskname d JOIN billing_on_filename f ON f.disk_id=d.id'
      + ` WHERE d.groupno=${q(fixtureState.groupNo)} AND f.providerno=${q(paid.providerNo)}`);
    h.assert(disks.length === 1, 'Expected exactly one disk for the owned provider');
    const name = disks[0][1];
    // The shape the MCEDT mailbox itself stages (ActionUtils.isOHIPFile): H, a month letter A-L, ..., a 3-digit batch suffix.
    h.assert(/^H[A-L][A-Za-z0-9]*\.\d{3}$/.test(name), 'The generated claim file name is not in the shape the MCEDT mailbox stages');
    const onDisk = path.join(diskDir, name);
    h.assert(fs.existsSync(onDisk), 'The diskette produced no claim file on the HOME_DIR disk');
    const bytes = fs.readFileSync(onDisk);
    h.assert(bytes.length > 0 && asText(bytes.subarray(0, 3)) === 'HEB', 'The generated claim file does not start with a batch header (HEB)');
    generated = { name, bytes };
    h.assert(!fs.existsSync(outboxFile(name)), 'The claim file was already in ONEDT_OUTBOX before the mailbox was opened for it');
    // If the mailbox does stage it, cleanup removes the copy: only while it still holds the generated bytes.
    ledger.expect(name, bytes);
    console.log('  generated one claim file on the HOME_DIR disk; not in the outbox yet');
  });

  // =========================================================================================================
  if (full) await s.step(STEP.add, async () => {
    const bytes = claimBytes('add');
    ledger.expect(names.add, bytes);
    await openMailboxFromAdministration();
    await openUploadTab();
    h.assert(!(await listed()).includes(names.add), 'The Add fixture name is already listed');
    await mailbox().locator('#upload').getByRole('button', { name: 'Add', exact: true }).click();
    await until(() => /\/mcedt\/openAddUploadMailbox$/.test(frameUrl()), 'Add did not open the Add Upload page');
    const file = mailbox().locator('#addUploadFile');
    await file.waitFor({ state: 'visible', timeout: 20000 });
    await file.setInputFiles({ name: names.add, mimeType: 'application/octet-stream', buffer: bytes });
    const created = mailbox().getByRole('button', { name: 'Create' });
    addCaptured = await captureRequest(admin, (u) => u.pathname.endsWith('/mcedt/addUpload'), () => created.click());
    h.assert(addCaptured.status === 200, `Create answered HTTP ${addCaptured.status}`);
    await mailbox().locator('#upload').waitFor({ state: 'visible', timeout: 20000 });
    const message = await mailbox().locator('.alert-info').first().innerText();
    h.assert(message.includes(names.add) && /added to the uploads list/.test(message), 'Create did not report the file as added to the uploads list');
    h.assert(fs.existsSync(outboxFile(names.add)), 'Create wrote no file to ONEDT_OUTBOX');
    h.assert(fs.readFileSync(outboxFile(names.add)).equals(bytes), 'The file in ONEDT_OUTBOX is not byte for byte what was added');
    const shown = (await listed()).filter((name) => name === names.add);
    h.assert(shown.length === 1, `The Upload tab lists the added file ${shown.length} time(s), not once`);
    h.assert(ledger.visible().filter((entry) => entry.name === names.add).length === 1, 'ONEDT_OUTBOX holds the added file other than once');
    const row = mailbox().locator('#upload tr', { has: mailbox().locator(`input.fileNames[value="${names.add}"]`) });
    h.assert(await row.locator('select.fileTypes').inputValue() === 'CL', 'An H-file is not offered as Claims by default');
    console.log('  Add wrote the fixture claim file byte for byte and listed it once');
  });

  // =========================================================================================================
  if (full) await s.step(STEP.addRefused, async () => {
    const token = await sessionToken(admin, config.baseUrl);
    const bytes = claimBytes('refused');
    let appRefused = 0;
    let frontDoor = 0;
    const attempts = [
      ['a name that is not a claim file', names.notes, 'not a supported file Name'],
      ['a month letter beyond L', names.badMonth, 'not a supported file Name'],
      ['a parent-directory name', `../${names.add}`, 'Invalid filename'],
      ['a leading slash', `/${names.add}`, 'Invalid filename'],
      ['a sub-directory', `sub/${names.add}`, 'Invalid filename'],
      ['a hidden name', `.${names.add}`, 'Invalid filename'],
      ['a backslash path', `..\\${names.add}`, null],
      ['a drive prefix', `C:${names.add}`, null],
    ];
    for (const [what, name, expected] of attempts) {
      await unchanged(`Add of ${what}`, async () => {
        const response = await post(context, '/mcedt/addUpload', { multipart: addForm(name, bytes), token, referer: '/mcedt/openAddUploadMailbox' });
        const body = await response.text();
        if (h.isWafPage(response.status(), body)) {
          // The front door's rule, not the application's: reported, never counted as the application's refusal.
          h.assert(expected === null, `Add of ${what} was blocked by the front door, which this shape does not expect`);
          frontDoor += 1;
          return;
        }
        h.assert(response.status() === 200 && Object.prototype.hasOwnProperty.call(response.headers(), h.APPLICATION_HEADER),
          `Add of ${what} answered HTTP ${response.status()} instead of the application's refusal page`);
        const shown = alertText(body);
        h.assert(shown.length > 0 && (expected === null || shown.includes(expected)),
          `Add of ${what} was not refused with the application's message (${shown ? 'another message' : 'no message'})`);
        appRefused += 1;
      });
    }
    h.assert(appRefused >= 6, `Only ${appRefused} of the refusals came from the application`);
    console.log(`  ${appRefused} Add refusals by the application, ${frontDoor} blocked by the front door (application not reached); outbox and parent unchanged`);
  });

  // =========================================================================================================
  if (full) await s.step(STEP.remove, async () => {
    await openMailboxFromAdministration();
    await openUploadTab();
    const before = await listed();
    h.assert(before.includes(names.add) && before.includes(names.bystander), 'The Upload tab does not list the owned file and the bystander');
    const bystanderBytes = fs.readFileSync(outboxFile(names.bystander));
    const parentBytes = fs.readFileSync(seeded.parent.file);
    await mailbox().locator(`#upload input.fileNames[value="${names.add}"]`).check();
    h.assert(await mailbox().locator('#deleteUpload').isEnabled(), 'Ticking a file did not enable Delete Selected');
    h.assert(await mailbox().locator(`#upload input.fileNames[value="${names.bystander}"]`).isChecked() === false, 'The bystander is ticked');
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      deleteCaptured = await captureRequest(admin, (u) => u.pathname.endsWith('/mcedt/upload'), () => mailbox().locator('#deleteUpload').click());
      await until(async () => {
        const now = await listed();
        return !now.includes(names.add) && now.includes(names.bystander);
      }, 'The Upload tab still lists the deleted file, or no longer lists the bystander');
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete Selected must ask for confirmation exactly once');
    h.assert(deleteCaptured.status === 200 && deleteCaptured.params.get('method') === 'deleteUpload', 'Delete Selected did not post method=deleteUpload');
    h.assert(deleteCaptured.params.get('fileName') === names.add, 'Delete Selected named another file than the ticked one');
    h.assert(!fs.existsSync(outboxFile(names.add)), 'Delete Selected left the file in ONEDT_OUTBOX');
    h.assert(fs.readFileSync(outboxFile(names.bystander)).equals(bystanderBytes), 'Delete Selected changed the bystander file');
    h.assert(fs.readFileSync(seeded.parent.file).equals(parentBytes), 'Delete Selected changed the file beside the outbox');
    h.assert((await listed()).includes(names.bystander), 'Delete Selected removed the bystander from the listing');
    console.log('  Delete Selected removed the one ticked file; bystander and the file beside the outbox intact');
  });

  // =========================================================================================================
  if (full) await s.step(STEP.removeRefused, async () => {
    const token = await sessionToken(admin, config.baseUrl);
    const bystanderBytes = fs.readFileSync(outboxFile(names.bystander));
    const parentBytes = fs.readFileSync(seeded.parent.file);
    // The first name is valid and absent, so nothing is removed before the second name is rejected.
    for (const [what, list, message] of [
      ['a parent-directory name', `${names.missing},../${names.parentMarker}`, 'must not include a path'],
      ['a sub-directory name', `${names.missing},sub/${names.bystander}`, 'must not include a path'],
      ['an empty name', '', 'Invalid filename'],
    ]) {
      await unchanged(`Delete of ${what}`, async () => {
        const response = await post(context, '/mcedt/upload', { form: deleteForm(list), token, referer: '/mcedt/upload' });
        const body = await response.text();
        h.assert(response.status() === 200 && Object.prototype.hasOwnProperty.call(response.headers(), h.APPLICATION_HEADER),
          `Delete of ${what} answered HTTP ${response.status()} instead of the application's page`);
        h.assert(alertText(body).includes(message), `Delete of ${what} was not refused with the application's message`);
      });
    }
    h.assert(fs.readFileSync(seeded.parent.file).equals(parentBytes), 'A path in the Delete list removed or changed the file beside the outbox');
    h.assert(fs.readFileSync(outboxFile(names.bystander)).equals(bystanderBytes), 'A path in the Delete list removed or changed the bystander');
  });

  // =========================================================================================================
  if (full || PIN === 'plaintext') await s.step(STEP.password, async () => {
    const pageBodies = [];
    let digestAfterFirst = '';
    const watch = (response) => { if (new URL(response.url()).origin === origin) pageBodies.push(response); };
    admin.on('response', watch);
    try {
      await openMailboxFromAdministration();
      for (const [round, value] of [[1, passwords.first], [2, passwords.second]]) {
        await mailbox().getByRole('button', { name: 'Change Password' }).click();
        await until(() => /\/mcedt\/kaichpass$/.test(frameUrl()), 'Change Password did not open /mcedt/kaichpass');
        await mailbox().locator('#password').waitFor({ state: 'visible', timeout: 20000 });
        h.assert(await mailbox().getByRole('heading', { name: 'Update MCEDT Password' }).isVisible(), 'The Change Password page has no heading');
        h.assert(await mailbox().locator('#password').inputValue() === '', 'The new-password field arrives pre-filled');
        await mailbox().locator('#password').fill(value);
        await mailbox().locator('#conPassword').fill(value);
        const dialogs = await h.withExpectedDialogs(admin, async () => {
          const submit = () => mailbox().getByRole('button', { name: 'Update Password' }).click();
          const captured = await captureRequest(admin, (u) => u.pathname.endsWith('/mcedt/kaichpass'), submit);
          if (round === 1) passwordCaptured = captured;
          h.assert(captured.status === 200 && captured.params.get('method') === 'changePassword', 'Update Password did not post method=changePassword');
          // The success page raises an alert and returns to the Menu tab by itself (the cancel result forwards to
          // kaimcedt, so the frame keeps the /mcedt/kaichpass address: the Menu heading is the proof, not the URL).
          await mailbox().getByRole('heading', { name: 'Welcome To MCEDT' }).waitFor({ state: 'visible', timeout: 20000 });
        });
        h.assert(dialogs.some((dialog) => /changed successfully/.test(dialog.text || '')), `Round ${round}: no success alert after Update Password`);
        h.assert(propertyRows() === '1', `Round ${round}: the ${L.PASSWORD_PROPERTY} property has ${propertyRows()} rows, not one`);
        // What is stored is not judged here (finding 189 pins it in its own step): a value was written, and the
        // second change replaced it in the same row instead of adding one.
        const stored = storedDigest();
        h.assert(stored !== '', `Round ${round}: the property row holds no value after the change`);
        if (round === 2) h.assert(stored !== digestAfterFirst, 'The second change did not replace the stored value');
        else digestAfterFirst = stored;
      }
      // The page the user is back on, and every response of this step, must never carry the value.
      const html = await mailbox().locator('html').innerHTML();
      h.assert(!html.includes(passwords.first) && !html.includes(passwords.second), 'The mailbox page echoes a password');
      let scanned = 0;
      for (const response of pageBodies) {
        const type = response.headers()['content-type'] || '';
        if (!/text|json|html/.test(type)) continue;
        const body = await response.text().catch(() => '');
        if (!body) continue;
        scanned += 1;
        h.assert(!body.includes(passwords.first) && !body.includes(passwords.second), `A response (${new URL(response.url()).pathname}) echoes a password`);
      }
      h.assert(scanned >= 2, 'The step scanned no responses for an echoed password');
      console.log(`  Change Password wrote one row twice (create, then update); ${scanned} responses and the page carry no password`);
    } finally {
      admin.off('response', watch);
    }
  });

  // =========================================================================================================
  // Finding 189 (variant `plaintext`): the pinned step holds only the stored value; Change Password itself ran above.
  if (PIN === 'plaintext') {
    await s.step(STEP.plaintext, async () => {
      h.assert(propertyHolds(passwords.second) === '0', 'The MCEDT password is stored exactly as typed, unencrypted');
    });
  }

  // =========================================================================================================
  if (full) await s.step(STEP.mutators, async () => {
    const token = await sessionToken(admin, config.baseUrl);
    // --- controls: the same requests WITH a valid token do their work, so the refusals below are about the token or the verb.
    const sacrificial = markers.seed(outbox, names.sacrificial, 'sacrificial');
    const control = await sendReplay(context, buildReplay(deleteCaptured, { origin, token, overrides: { fileName: names.sacrificial } }));
    h.assert(control.status() === 200 && !fs.existsSync(sacrificial.file), 'Control: Delete with a token did not remove the sacrificial file');
    const controlBytes = claimBytes('control');
    ledger.expect(names.control, controlBytes);
    const added = await post(context, '/mcedt/addUpload', { multipart: addForm(names.control, controlBytes), token, referer: '/mcedt/openAddUploadMailbox' });
    h.assert(added.status() === 200 && fs.existsSync(outboxFile(names.control)) && fs.readFileSync(outboxFile(names.control)).equals(controlBytes),
      'Control: Add with a token did not write the file byte for byte');
    const valueBeforeControl = storedDigest();
    const changed = await sendReplay(context, buildReplay(passwordCaptured, { origin, token, overrides: { password: passwords.control, conPassword: passwords.control } }));
    h.assert(changed.status() === 200 && propertyRows() === '1' && storedDigest() !== valueBeforeControl,
      'Control: Change Password with a token did not replace the stored password');

    // --- GET and a tokenless POST of each mutator.
    const targets = {
      delete: {
        kind: 'file',
        get: `/mcedt/upload?method=deleteUpload&fileName=${encodeURIComponent(names.bystander)}`,
        tokenless: () => sendReplay(context, buildReplay(deleteCaptured, { origin, token: null, overrides: { fileName: names.bystander } })),
      },
      add: {
        kind: 'file',
        get: `/mcedt/addUpload?method=addUpload&fileName=${encodeURIComponent(names.control)}`,
        tokenless: () => post(context, '/mcedt/addUpload', { multipart: addForm(`HK${digits(41n)}.994`, claimBytes('tokenless')), referer: '/mcedt/openAddUploadMailbox' }),
      },
      password: {
        kind: 'property',
        get: `/mcedt/kaichpass?method=changePassword&password=${encodeURIComponent(passwords.probe)}`,
        tokenless: () => sendReplay(context, buildReplay(passwordCaptured, { origin, token: null, overrides: { password: passwords.probe, conPassword: passwords.probe } })),
      },
    };
    let refusals = 0;
    for (const [key, target] of Object.entries(targets)) {
      await unchanged(`${key}: GET`, async () => {
        await refused(`${key}: GET`, () => get(context, target.get), target.kind);
        refusals += 1;
      });
      await unchanged(`${key}: tokenless POST`, async () => {
        await refused(`${key}: tokenless POST`, target.tokenless, target.kind);
        refusals += 1;
      });
    }
    h.assert(propertyHolds(passwords.probe) === '0', 'A refused request stored its password');
    h.assert(fs.existsSync(outboxFile(names.bystander)), 'A refused request removed the bystander');
    console.log(`  ${refusals} refusals (GET and tokenless POST of Delete, Add and Change Password) by the application; nothing changed`);
  });

  // =========================================================================================================
  if (full) await s.step(STEP.privileges, async () => {
    const doctor = await signIn(s, fixture.addLogin('doctor'));
    const routes = ['/mcedt/kaimcedt', '/mcedt/upload', '/mcedt/openAddUploadMailbox', '/mcedt/kaichpass'];
    for (const route of routes) {
      await unchanged(`doctor ${route}`, () => refused(`doctor GET ${route}`, () => get(doctor.context, route)));
    }
    const doctorToken = await sessionToken(doctor.page, config.baseUrl);
    const mutate = {
      delete: { kind: 'file', send: (ctx, tok) => post(ctx, '/mcedt/upload', { form: deleteForm(names.bystander), token: tok, referer: '/mcedt/upload' }) },
      add: { kind: 'file', send: (ctx, tok) => post(ctx, '/mcedt/addUpload', { multipart: addForm(`HK${digits(37n)}.993`, claimBytes('privilege')), token: tok, referer: '/mcedt/openAddUploadMailbox' }) },
      password: { kind: 'property', send: (ctx, tok) => post(ctx, '/mcedt/kaichpass', { form: { method: 'changePassword', password: passwords.probe }, token: tok, referer: '/mcedt/kaichpass' }) },
    };
    for (const [key, m] of Object.entries(mutate)) {
      await unchanged(`doctor ${key}`, () => refused(`doctor ${key} with a valid token`, () => m.send(doctor.context, doctorToken), m.kind));
    }
    await doctor.context.close();
    // A login that may read MCEDT (_admin.billing r) but not write: pages are served, every mutator is refused.
    // The role is made last and removed at once: its rows reorder the privilege lookups other logins use (finding 205).
    const reader = await signIn(s, fixture.addLogin(fixture.addRole({ '_admin.billing': 'r' })));
    for (const route of routes) {
      const response = await get(reader.context, route);
      const body = await response.text();
      h.assert(response.status() === 200 && Object.prototype.hasOwnProperty.call(response.headers(), h.APPLICATION_HEADER) && body.length > 1000,
        `The read-only login was not served ${route} (HTTP ${response.status()})`);
    }
    const readerToken = await sessionToken(reader.page, config.baseUrl);
    for (const [key, m] of Object.entries(mutate)) {
      await unchanged(`read-only ${key}`, () => refused(`read-only login ${key} with a valid token`, () => m.send(reader.context, readerToken), m.kind));
    }
    await reader.context.close();
    fixture.cleanup();
    console.log('  doctor refused on 4 routes and 3 mutators; read-only login served the pages and refused 3 mutators');
  });

  // =========================================================================================================
  if (full) await s.step(STEP.noService, async () => {
    const outside = browserRequests.filter((request) => request.host !== config.baseUrl.host
      && ['document', 'xhr', 'fetch', 'websocket', 'eventsource', 'other'].includes(request.type));
    h.assert(outside.length === 0, `${outside.length} browser request(s) left the application's origin`);
    const service = browserRequests.filter((request) => SERVICE_PATHS.test(request.path) || (request.method === 'POST' && SERVICE_METHODS.test(request.post)));
    h.assert(service.length === 0, `${service.length} browser request(s) went to an MCEDT service route`);
    h.assert(browserRequests.some((request) => request.path.endsWith('/mcedt/kaimcedt')) && browserRequests.some((request) => request.path.endsWith('/mcedt/upload')),
      'The recorded browser traffic does not include the mailbox pages this step is about');
    console.log(`  ${browserRequests.length} browser requests recorded; none to the MCEDT service routes or beyond the application`);
  });

  // =========================================================================================================
  // The reopen step does the driving and records what each open left behind; the pinned step below only judges the
  // record, so a failure to open the mailbox reads failed-elsewhere and only the staging question reads known-fail.
  const opens = [];
  if (full) await s.step(STEP.reopen, async () => {
    for (const round of [1, 2, 3]) {
      await openMailboxFromAdministration();
      await openUploadTab();
      const staged = ledger.visible().filter((entry) => entry.name === generated.name);
      opens.push({
        round,
        copies: staged.length,
        sameBytes: staged.length > 0 && fs.readFileSync(outboxFile(generated.name)).equals(generated.bytes),
        listedTimes: (await listed()).filter((name) => name === generated.name).length,
      });
    }
    h.assert(opens.length === 3, 'The mailbox was not reopened three times');
  });

  // LAST: the pinned step holds only the defect's assertion (finding 216); every control sits above.
  if (full) await s.step(STEP.staged, async () => {
    for (const open of opens) {
      h.assert(open.copies === 1, `Open ${open.round}: ${open.copies} copies of the generated claim file are in ONEDT_OUTBOX, expected one`);
      h.assert(open.sameBytes, `Open ${open.round}: the staged claim file is not byte for byte the generated one`);
      h.assert(open.listedTimes === 1, `Open ${open.round}: the Upload tab lists the generated claim file ${open.listedTimes} time(s), expected once`);
    }
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow, STEP, validatePin };
