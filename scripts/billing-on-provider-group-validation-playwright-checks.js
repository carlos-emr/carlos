#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Issue #4277: a provider's stored billing group number is normalized rather
 * than gated. A short all-digit value (123) is read as the group 0123 and
 * generates a disk whose file name, stored group and HEB batch header all
 * carry 0123; a value that cannot be normalized (12345) is rejected before any
 * disk is allocated, naming the provider, for a single selection and for All
 * Providers alike; an unrelated invalid group does not block a valid provider.
 * Uses the standard browser/SQL harness on a disposable Ontario deployment.
 * Run as the only billing writer, with a small fixture database/output directory:
 * whole-table/file snapshots prove All Providers cannot mutate unrelated records.
 * Requires OHIP_DISK_DIR (the application's local HOME_DIR). Optional fixture
 * settings: GROUP_DISK_TEMPLATE_PROVIDER, GROUP_DISK_DEMOGRAPHIC_NO,
 * GROUP_DISK_SERVICE_DATE, GROUP_DISK_PAID_CODE (same as the zero-total check).
 * All Providers is submitted only after single-provider rejection proves the
 * new validation is installed. Baseline failure therefore touches owned rows only.
 */

const fs = require('node:fs');
const path = require('node:path');
const { randomBytes, createHash } = require('node:crypto');
const {
  assert, assertStrictPage, createRecorder, createSqlRunner, launchBrowser,
  login, newContext, readConfig, runCheck, sqlString,
} = require('./lib/playwright-harness');
const {
  isoDate, createFixture, checkedDiskDirectory, cleanupResources, generateProviderDisk, unusedNumber,
} = require('./billing-on-group-disk-zero-total-playwright-checks');

// HEB record layout: "HEB" + version(3) + MOH office(1) + batch id(12) + 6 spaces,
// then the 4-character group number at offset 25 and the provider number at 29.
const HEB_GROUP_OFFSET = 25;

function billingSnapshot(db, diskDir) {
  const rows = ['billing_on_diskname', 'billing_on_filename', 'billing_on_header',
    'billing_on_cheader1', 'billing_on_item', 'billing_on_repo']
    .map(table => db.rows(`SELECT * FROM ${table} ORDER BY id`));
  const files = fs.readdirSync(diskDir).sort().filter(name => name !== '.carlos-ohip-disk.lock')
    .map(name => {
      const file = path.join(diskDir, name);
      const stat = fs.lstatSync(file);
      return [name, stat.isFile() && !stat.isSymbolicLink()
        ? createHash('sha256').update(fs.readFileSync(file)).digest('hex') : 'non-regular'];
    });
  return JSON.stringify({ rows, files });
}

/** Generates for one provider and returns the disk row, downloaded bytes and billed state. */
async function generateAndVerify(context, config, recorder, window, db, diskDir, member, groupNo) {
  const page = await generateProviderDisk(context, config, recorder, window, member.providerNo);
  assert(await page.locator('#ohip-provider-validation').count() === 0, `Provider ${member.providerNo} was rejected`);
  const disks = db.rows('SELECT d.id, d.ohipfilename, d.groupno FROM billing_on_diskname d'
    + ' JOIN billing_on_filename f ON f.disk_id=d.id'
    + ` WHERE f.providerno=${sqlString(member.providerNo)} ORDER BY d.id DESC LIMIT 1`);
  assert(disks.length === 1, 'Expected one generated disk for the selected provider');
  const [diskId, filename, storedGroup] = disks[0];
  assert(storedGroup === groupNo, `Disk stored group ${storedGroup}, expected ${groupNo}`);
  assert(/^H[A-L]/.test(filename) && filename.slice(2, 6) === groupNo && /\.\d{3}$/.test(filename),
    `Disk file name ${filename} does not carry the group ${groupNo}`);
  const link = page.locator(`a[href*="filename=${encodeURIComponent(filename)}"]`).first();
  assert(await link.count() === 1, 'Generated disk download is missing');
  const response = await context.request.get(new URL(await link.getAttribute('href'), page.url()).toString());
  assert(response.status() === 200, 'Generated OHIP file did not download');
  const data = await response.body();
  await response.dispose();
  assert(data.equals(fs.readFileSync(path.join(diskDir, filename))), 'Download differs from generated file');
  const records = data.toString('latin1').split(/[\r\n]+/);
  const headers = records.filter(line => line.startsWith('HEB'));
  assert(headers.length === 1 && headers[0].includes(member.ohipNo), 'Wrong provider in generated batch');
  assert(headers[0].slice(HEB_GROUP_OFFSET, HEB_GROUP_OFFSET + 4) === groupNo,
    `HEB header carries group "${headers[0].slice(HEB_GROUP_OFFSET, HEB_GROUP_OFFSET + 4)}", expected ${groupNo}`);
  assert(records.filter(line => line.startsWith('HET')).length === member.itemCount, 'Wrong claim item count');
  const [status, headerId] = db.rows(`SELECT status, header_id FROM billing_on_cheader1 WHERE id=${Number(member.headerId)}`)[0];
  assert(status === 'B', 'Generated claim was not marked billed');
  assert(db.value(`SELECT COUNT(*) FROM billing_on_header WHERE id=${Number(headerId)} AND disk_id=${Number(diskId)}`) === '1',
    'Claim does not belong to its generated disk');
  await page.close();
  return filename;
}

async function main() {
  const config = readConfig();
  const serviceDate = isoDate('GROUP_DISK_SERVICE_DATE', process.env.GROUP_DISK_SERVICE_DATE, '2003-02-03');
  const window = { serviceDate, start: serviceDate, end: serviceDate };
  const diskDir = checkedDiskDirectory(process.env.OHIP_DISK_DIR || '');
  const state = { providers: {}, headerIds: [], groupNo: '', marker: `PW4277-${randomBytes(8).toString('hex')}` };
  const recorder = createRecorder();
  const db = createSqlRunner(config.mysql);
  let browser;

  return {
    run: async () => {
      // A legacy exporter may choose H<month>.001. Refuse to overwrite orphaned
      // output when exercising that baseline, even if no database row owns it.
      assert(!fs.readdirSync(diskDir).some(name => /^H[A-L]\.\d+$/.test(name)),
        'Existing malformed-group output requires a fresh disposable fixture database/directory');
      createFixture(db, {
        templateProvider: process.env.GROUP_DISK_TEMPLATE_PROVIDER || '999998',
        demographicNo: process.env.GROUP_DISK_DEMOGRAPHIC_NO || '',
        paidCode: process.env.GROUP_DISK_PAID_CODE || 'A007A', window, testUser: config.testUser,
      }, state);
      // The short group whose zero-padded form must be unused by any provider or disk.
      const shortGroup = unusedNumber(db, 3, '', candidate => candidate.startsWith('0') || db.value(
        `SELECT (SELECT COUNT(*) FROM provider WHERE comments LIKE ${sqlString(`%<xml_p_billinggroup_no>0${candidate}<%`)})`
        + ` + (SELECT COUNT(*) FROM billing_on_diskname WHERE groupno=${sqlString(`0${candidate}`)})`) !== '0');
      const paddedGroup = `0${shortGroup}`;
      const unnormalizable = `${state.groupNo}5`;
      state.cleanupGroupNumbers = [state.groupNo, paddedGroup, ''];
      const { ZERO, PAID } = state.providers;
      const setGroup = group => db.execute(`UPDATE provider SET comments=${sqlString(
        `<xml_p_billinggroup_no>${group}</xml_p_billinggroup_no><xml_p_specialty_code>00</xml_p_specialty_code>`)}
        WHERE provider_no=${sqlString(ZERO.providerNo)} AND first_name=${sqlString(state.marker)}`);
      browser = await launchBrowser(config);
      const context = await newContext(browser, config);
      await login(context, config, recorder);

      // 1. A short all-digit group is normalized, not rejected: 123 bills as 0123 everywhere.
      setGroup(shortGroup);
      const filenames = [];
      filenames.push(await generateAndVerify(context, config, recorder, window, db, diskDir, ZERO, paddedGroup));
      assert(db.value(`SELECT COUNT(*) FROM billing_on_diskname WHERE groupno=''`) === '0',
        'A normalized group must never be stored as the legacy empty group');
      console.log(`  Short group ${shortGroup}: generated as ${paddedGroup} (disk name, stored group, HEB header)`);

      // 2. A value that cannot be normalized is reported per provider before any write,
      //    for the selected provider and for All Providers (PAID still has an unbilled claim).
      setGroup(unnormalizable);
      const before = billingSnapshot(db, diskDir);
      for (const selection of [ZERO.providerNo, 'all']) {
        const page = await generateProviderDisk(context, config, recorder, window, selection);
        const alert = page.locator('#ohip-provider-validation');
        assert(await alert.count() === 1, `Group ${unnormalizable} did not show provider-specific correction guidance`);
        const guidance = await alert.innerText();
        assert(guidance.includes(ZERO.providerNo) && guidance.includes('four letters or digits')
          && guidance.includes('0000') && guidance.includes('No files were generated'),
        'Validation guidance must identify the provider, expected format, and unchanged billing state');
        assert(billingSnapshot(db, diskDir) === before, 'Rejected generation changed billing records or output files');
        await page.close();
      }
      console.log(`  Group ${unnormalizable}: selected provider and All Providers rejected; no billing or file mutations`);

      // 3. A different valid provider is still billable while ZERO is invalid.
      filenames.push(await generateAndVerify(context, config, recorder, window, db, diskDir, PAID, state.groupNo));
      assert(new Set(filenames).size === 2, 'Group exports reused a filename');
      assertStrictPage(recorder);
      console.log('  Valid provider unaffected by unrelated invalid group; distinct downloads and billed claims verified');
    },
    cleanup: () => cleanupResources(browser, db, state, diskDir),
  };
}

if (require.main === module) {
  let handles;
  runCheck({
    name: 'billing-on-provider-group-validation',
    run: async () => { handles = await main(); return handles.run(); },
    cleanup: async () => handles && handles.cleanup(),
  });
}
