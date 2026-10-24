#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario Remittance Advice follow-up: the practitioner premium and the S35 settlement.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Upload MOH files (a synthetic RA with a PREMIUM
 * PAYMENTS message and three claims) ▸ Administration ▸ Payment Received (premium report) ▸ Billing
 * Reconciliation ▸ Report (ViewGenRADesc popup ▸ premium table ▸ provider ▸ tick ▸ Apply =
 * ApplyPractitionerPremium) ▸ Billing Reconciliation ▸ S35 (confirm ▸ ViewOnGenRAsettle35).
 * billing-on-ra-import covers the import, Report, Summary, Error and the plain Settle; this check
 * covers the premium and the I2/35 + Q-code settlement it does not touch.
 *
 * Asserts against MariaDB: the upload writes the RA (three radetail rows) and leaves the claims
 * billed; Payment Received lists the applied premium of a seeded second RA for the owned provider and
 * not its unapplied premium; a GET against the premium apply is refused and changes nothing (fails
 * today: it applies the unticked state on GET). Then, behind the Report / S35 links (which answer 403
 * today because Billing Reconciliation posts a runtime form without a CSRF token): the first Report
 * parses the RA message into one premium row (owned OHIP number, amount, pay date, not applied);
 * choosing the owned provider and ticking Apply turns it on for that provider; reopening shows it
 * ticked and unticking turns it off and clears the provider; S35 settles the claim without an RA
 * error and the claim whose only RA line is a Q-code rejection (code 30), keeps the claim with a
 * real error billed and marks the RA header F.
 *
 * Fixtures: createBillingFixture (owned billing provider, HIN) with three submitted (B) claims, the
 * RA file uploaded through the page (raheader/radetail/premium rows and the stored file, removed in
 * cleanup), and a seeded second raheader with two premium rows. Nothing is sent to MOH/MCEDT.
 * Implements the gap-billing "premium and S35" workflow. Optional RA_DOCUMENT_DIR / RA_EDT_INBOX.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { settleOperations } = require('./graceful-signal-cancellation');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { buildRemittance } = require('./billing-on-ra-import-playwright-checks');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');

const DEFAULT_DOCUMENT_DIR = '/var/lib/carlos-emr/CarlosDocument/carlos/document';
const DEFAULT_EDT_INBOX = '/var/lib/carlos-emr/CarlosDocument/carlos/onEDTDocs/inbox';
const RECONCILE_CONFIRM = 'You are about to reconcile the file, are you sure?';
const PREMIUM_DATE = '2004-06-15';
const PREMIUM_AMOUNT = '1234.56';

function directory(env, fallback) {
  const dir = process.env[env] || fallback;
  h.assert(path.isAbsolute(dir) && fs.statSync(dir).isDirectory(), `${env} must name an existing directory`);
  return fs.realpathSync(dir);
}

/** An 79-character HR8 message record. */
function message(text) {
  h.assert(text.length <= 70, 'An RA message line is longer than 70 characters');
  return `HR8${text.padEnd(70)}`.padEnd(79);
}

/** The MOH premium block BillingONPremiumDaoImpl.parseAndSaveRAPremiums reads from the HR8 lines. */
function premiumBlock(ohipNo) {
  return [
    message('*'.repeat(70)),
    message('PREMIUM PAYMENTS'),
    message(`FOR PAYMENT: ${PREMIUM_DATE}`),
    message(`PROVIDER NUMBER: ${ohipNo}`),
    message('TOTAL MONTHLY PREMIUM PAYMENT'.padEnd(29) + `  $${Number(PREMIUM_AMOUNT).toLocaleString('en-US', { minimumFractionDigits: 2 })}`),
  ].join('\r\n');
}

async function workflow(s) {
  const { sql, marker, context } = s;
  const documentDir = directory('RA_DOCUMENT_DIR', DEFAULT_DOCUMENT_DIR);
  const edtInbox = directory('RA_EDT_INBOX', DEFAULT_EDT_INBOX);
  const raName = `PPW${marker.slice('FAKE-PW'.length)}.001`;
  const owned = createBillingFixture(s);
  const seededName = `SPW${marker.slice('FAKE-PW'.length)}.001`;
  const raIds = () => sql.rows(`SELECT raheader_no FROM raheader WHERE filename IN (${h.sqlString(raName)}, ${h.sqlString(seededName)})`)
    .map(row => Number(row[0]));

  // Runs before the claim/provider cleanups the fixture registered.
  s.cleanup(() => {
    for (const id of raIds()) {
      sql.execute(`DELETE FROM billing_on_premium WHERE raheader_no=${id};
        DELETE FROM radetail WHERE raheader_no=${id};
        DELETE FROM raheader WHERE raheader_no=${id} AND filename IN (${h.sqlString(raName)}, ${h.sqlString(seededName)})`);
    }
    h.assert(raIds().length === 0, 'Owned RA rows were not removed');
    for (const dir of [documentDir, edtInbox]) {
      const file = path.join(dir, raName);
      if (fs.existsSync(file)) {
        h.assert(fs.lstatSync(file).isFile(), 'Refusing to remove a non-regular uploaded file');
        fs.unlinkSync(file);
      }
      h.assert(!fs.existsSync(file), 'An uploaded MOH file was not removed');
    }
  });

  const fee = code => {
    const value = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
      ORDER BY billingservice_date DESC LIMIT 1`);
    if (!/^\d+(\.\d+)?$/.test(value)) throw new h.SkipCheck(`service code ${code} has no fee`);
    return Number(value).toFixed(2);
  };
  const date = '2004-05-12';
  const paid = owned.addClaim({ tag: 'PAID', date, status: 'B', items: [{ code: 'A007A', fee: fee('A007A') }] });
  const rejected = owned.addClaim({ tag: 'REJECTED', date, status: 'B', items: [{ code: 'A001A', fee: fee('A001A') }] });
  const qcode = owned.addClaim({ tag: 'QCODE', date, status: 'B', items: [{ code: 'Q011A', fee: fee('Q011A') }] });
  const statuses = () => [paid, rejected, qcode]
    .map(claim => sql.value(`SELECT status FROM billing_on_cheader1 WHERE id=${claim.id}`)).join('|');
  const remittance = buildRemittance({ owned, marker, claims: [
    { id: paid.id, code: 'A007A', fee: fee('A007A'), paid: fee('A007A'), error: '' },
    { id: rejected.id, code: 'A001A', fee: fee('A001A'), paid: '0.00', error: 'AD' },
    { id: qcode.id, code: 'Q011A', fee: fee('Q011A'), paid: '0.00', error: '30' },
  ] });
  const text = `${remittance.text}${premiumBlock(owned.ohipNo)}\r\n`;

  const admin = await openAdministration(s);
  let raNo;
  // By the uploaded RA's own number (its Report link posts it): the seeded payable text also carries the marker.
  const raRow = frame => frame.locator('tbody tr').filter({
    has: frame.locator(`a[onclick*="ViewGenRADesc"][onclick*="'${raNo}'"]`),
  });
  const premiums = () => sql.rows(`SELECT premium_id, providerohip_no, amount_pay, pay_date, status, IFNULL(provider_no, '')
    FROM billing_on_premium WHERE raheader_no=${raNo}`);

  await s.step('Upload MOH files imports the RA with its three claims', async () => {
    const frame = await openAdminFrame(admin, '/billing/CA/ON/BillingONUpload', 'form#form1');
    await frame.locator('input[name="file1"]').setInputFiles({ name: raName, mimeType: 'text/plain', buffer: Buffer.from(text, 'latin1') });
    const [response] = await settleOperations([
      admin.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/servlet/io.github.carlos_emr.DocumentUploadServlet'), { timeout: 60000 }),
      frame.locator('input[type="submit"][value="Create Report"]').click(),
    ]);
    h.assert(response.status() === 200, `The RA upload answered HTTP ${response.status()}`);
    await frame.waitForLoadState('domcontentloaded');
    const ids = raIds();
    h.assert(ids.length === 1, 'The upload did not create exactly one raheader for the owned file');
    raNo = ids[0];
    h.assert(sql.value(`SELECT COUNT(*) FROM radetail WHERE raheader_no=${raNo}`) === '3', 'The import did not write one radetail per claim');
    h.assert(statuses() === 'B|B|B', 'Importing the RA changed the claim statuses');
  });

  // A second, seeded RA header with one applied and one unapplied premium: Payment Received and the
  // apply endpoint do not need the uploaded file, so these steps are provable on their own.
  const seededRa = sql.value(`INSERT INTO raheader (filename, paymentdate, payable, totalamount, records, claims, status, readdate)
    VALUES (${h.sqlString(seededName)}, '20040615', ${h.sqlString(`${marker} seeded`)}, '0.00', '0', '0', 'N', '20040615');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(seededRa), 'The seeded RA header was not created');
  const premium = (amount, status, provider) => sql.value(`INSERT INTO billing_on_premium (raheader_no, provider_no, providerohip_no,
      pay_date, amount_pay, status, creator) VALUES (${seededRa}, ${provider ? h.sqlString(provider) : 'NULL'},
      ${h.sqlString(owned.ohipNo)}, ${h.sqlString(PREMIUM_DATE)}, ${h.sqlString(amount)}, ${status}, ${h.sqlString(s.provider)});
    SELECT LAST_INSERT_ID()`);
  const appliedId = premium('777.77', 1, owned.providerNo);
  const unappliedId = premium('555.55', 0, null);
  const premiumState = id => sql.value(`SELECT CONCAT_WS('|', status, IFNULL(provider_no, 'none')) FROM billing_on_premium WHERE premium_id=${id}`);

  await s.step('Payment Received lists the applied premium for the owned provider and not the unapplied one', async () => {
    const paymentFrame = await openAdminFrame(admin, '/billing/CA/ON/BillingONPayment', 'form[name="billingPaymentForm"]');
    await paymentFrame.locator('select[name="providerList"]').selectOption(owned.providerNo);
    for (const [selector, value] of [['#startDateText', '2004-06-01'], ['#endDateText', '2004-06-30']]) {
      await paymentFrame.locator(selector).fill(value);
      await paymentFrame.locator('body').click({ position: { x: 4, y: 4 } });
    }
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === paymentFrame, timeout: 30000 });
    navigated.catch(() => {});
    await paymentFrame.locator('form[name="billingPaymentForm"] input[type="submit"]').click();
    await navigated;
    await paymentFrame.waitForLoadState('networkidle', { timeout: 30000 });
    const text = (await paymentFrame.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes('777.77'), 'Payment Received does not list the applied premium amount');
    h.assert(!text.includes('555.55'), 'Payment Received lists a premium that was never applied');
  });

  // The apply endpoint changes the premium when it is simply requested: no GET/HEAD rejection.
  await s.step('GET against the premium apply is refused and changes nothing', async () => {
    const before = premiumState(appliedId);
    const params = { rano: String(seededRa), [`providerNo${appliedId}`]: owned.providerNo };
    const probe = await context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/ApplyPractitionerPremium'),
      { params, maxRedirects: 0 });
    h.assert(premiumState(appliedId) === before && premiumState(unappliedId) === '0|none',
      'A GET changed a premium (ApplyPractitionerPremium applies the unticked state on GET)');
    h.assert(probe.status() === 405, `ApplyPractitionerPremium must reject GET with 405 (answered ${probe.status()})`);
  });

  let report;
  let frame;
  const openReport = async () => {
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewGenRA', 'table');
    const popup = await s.popup(frame.page(), raRow(frame).locator('a', { hasText: 'Report' }), 'ra-description');
    await popup.locator('body').waitFor();
    await h.assertNotErrorPage(popup, 'RA description');
    return popup;
  };
  const apply = async popup => {
    const [response] = await Promise.all([
      context.waitForEvent('response', { timeout: 30000,
        predicate: r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ApplyPractitionerPremium') }),
      popup.locator('form[action$="/billing/CA/ON/ApplyPractitionerPremium"] input[type="submit"]').click(),
    ]);
    h.assert(response.status() === 200, `Applying the premium answered HTTP ${response.status()}`);
    await popup.waitForLoadState('load');
  };

  // The steps below sit behind Billing Reconciliation's Report / S35 links, which post a runtime form
  // without a CSRF token (HTTP 403) today.
  await s.step('Report parses the RA premium message into one premium row that is not applied yet', async () => {
    report = await openReport();
    const rows = premiums();
    h.assert(rows.length === 1, 'The Report did not record exactly one premium for the RA');
    const [, ohip, amount, payDate, status, providerNo] = rows[0];
    h.assert(ohip === owned.ohipNo && Number(amount) === Number(PREMIUM_AMOUNT) && payDate === PREMIUM_DATE
      && status === '0' && providerNo === '', 'The premium row does not carry the RA OHIP number, amount, pay date and no provider');
    const table = report.locator('form[action$="/billing/CA/ON/ApplyPractitionerPremium"]');
    h.assert(await table.count() === 1, 'The Report does not offer the practitioner premium table');
    const options = await table.locator('select[name^="providerNo"] option').evaluateAll(o => o.map(x => x.value));
    h.assert(options.includes(owned.providerNo), 'The premium table does not offer the owned provider for its OHIP number');
  });

  await s.step('ticking Apply with the owned provider turns the premium on for that provider', async () => {
    const form = report.locator('form[action$="/billing/CA/ON/ApplyPractitionerPremium"]');
    await form.locator('select[name^="providerNo"]').selectOption(owned.providerNo);
    await form.locator('input[name^="choosePremium"]').check();
    await apply(report);
    await expectValue(sql, `SELECT CONCAT_WS('|', status, provider_no) FROM billing_on_premium WHERE raheader_no=${raNo}`,
      `1|${owned.providerNo}`, 'Applying did not turn the premium on for the owned provider');
    h.assert(premiums().length === 1, 'Applying created or removed premium rows');
  });

  await s.step('reopening the Report shows the premium applied, and unticking turns it off again', async () => {
    await report.close();
    report = await openReport();
    const form = report.locator('form[action$="/billing/CA/ON/ApplyPractitionerPremium"]');
    h.assert(await form.locator('input[name^="choosePremium"]').isChecked(), 'The reopened Report does not show the premium ticked');
    h.assert(await form.locator('select[name^="providerNo"]').inputValue() === owned.providerNo, 'The reopened Report lost the chosen provider');
    await form.locator('input[name^="choosePremium"]').uncheck();
    await apply(report);
    await expectValue(sql, `SELECT CONCAT_WS('|', status, IFNULL(provider_no, 'none')) FROM billing_on_premium WHERE raheader_no=${raNo}`,
      '0|none', 'Unticking did not turn the premium off and clear the provider');
    await report.close();
  });

  await s.step('S35 settles the claim without error and the Q-code claim, keeps the rejected claim billed and marks the RA F', async () => {
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewGenRA', 'table');
    const row = raRow(frame);
    h.assert(await row.locator('a', { hasText: 'S35' }).count() === 1, 'A new RA does not offer S35');
    let response;
    const dialogs = await h.withExpectedDialogs(frame.page(), async () => {
      [response] = await settleOperations([
        admin.waitForResponse(r => r.request().method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewOnGenRAsettle35'), { timeout: 30000 }),
        row.locator('a', { hasText: 'S35' }).click(),
      ]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === RECONCILE_CONFIRM,
      'S35 must ask the reconcile confirmation exactly once');
    h.assert(response.status() === 200, `S35 answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT status FROM raheader WHERE raheader_no=${raNo}`, 'F', 'S35 did not mark the RA header F');
    h.assert(statuses() === 'S|B|S', 'S35 did not settle exactly the claim without error and the Q-code claim');
  });
}

if (require.main === module) runWorkflow('gap-billing-ra-premium-settle35', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
