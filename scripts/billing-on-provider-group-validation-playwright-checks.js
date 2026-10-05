#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Issue #4277: reject malformed provider group numbers before allocating disks,
 * identify the affected provider, and generate distinct files after correction.
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
  isoDate, createFixture, checkedDiskDirectory, cleanupResources, generateProviderDisk,
} = require('./billing-on-group-disk-zero-total-playwright-checks');

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
      state.cleanupGroupNumbers = [state.groupNo, ''];
      const { ZERO, PAID } = state.providers;
      const setGroup = group => db.execute(`UPDATE provider SET comments=${sqlString(
        `<xml_p_billinggroup_no>${group}</xml_p_billinggroup_no><xml_p_specialty_code>00</xml_p_specialty_code>`)}
        WHERE provider_no=${sqlString(ZERO.providerNo)} AND first_name=${sqlString(state.marker)}`);
      setGroup('123');
      const before = billingSnapshot(db, diskDir);
      browser = await launchBrowser(config);
      const context = await newContext(browser, config);
      await login(context, config, recorder);
      for (const selection of [ZERO.providerNo, 'all']) {
        const page = await generateProviderDisk(context, config, recorder, window, selection);
        const alert = page.locator('#ohip-provider-validation');
        assert(await alert.count() === 1, 'Malformed group did not show provider-specific correction guidance');
        const guidance = await alert.innerText();
        assert(guidance.includes(ZERO.providerNo) && guidance.includes('exactly four digits')
          && guidance.includes('0000') && guidance.includes('No files were generated'),
        'Validation guidance must identify the provider, expected format, and unchanged billing state');
        assert(billingSnapshot(db, diskDir) === before, 'Rejected generation changed billing records or output files');
        await page.close();
      }
      console.log('  Invalid selected provider and All Providers: actionable guidance; no billing or file mutations');

      // A different valid provider is still billable while ZERO is invalid.
      const filenames = [];
      for (const member of [PAID, ZERO]) {
        if (member === ZERO) setGroup(state.groupNo);
        const page = await generateProviderDisk(context, config, recorder, window, member.providerNo);
        assert(await page.locator('#ohip-provider-validation').count() === 0, 'Valid provider was rejected');
        const disks = db.rows('SELECT d.id, d.ohipfilename FROM billing_on_diskname d'
          + ' JOIN billing_on_filename f ON f.disk_id=d.id'
          + ` WHERE d.groupno=${sqlString(state.groupNo)} AND f.providerno=${sqlString(member.providerNo)}`);
        assert(disks.length === 1, 'Expected one generated disk for the selected provider');
        const [diskId, filename] = disks[0];
        filenames.push(filename);
        const link = page.locator(`a[href*="filename=${encodeURIComponent(filename)}"]`).first();
        assert(await link.count() === 1, 'Generated disk download is missing');
        const response = await context.request.get(new URL(await link.getAttribute('href'), page.url()).toString());
        assert(response.status() === 200, 'Generated OHIP file did not download');
        const data = await response.body();
        await response.dispose();
        assert(data.equals(fs.readFileSync(path.join(diskDir, filename))), 'Download differs from generated file');
        const records = data.toString('latin1').split(/[\r\n]+/);
        assert(records.filter(line => line.startsWith('HEB')).length === 1
          && records.some(line => line.startsWith('HEB') && line.includes(member.ohipNo)), 'Wrong provider in generated batch');
        assert(records.filter(line => line.startsWith('HET')).length === member.itemCount, 'Wrong claim item count');
        const [status, headerId] = db.rows(`SELECT status, header_id FROM billing_on_cheader1 WHERE id=${Number(member.headerId)}`)[0];
        assert(status === 'B', 'Generated claim was not marked billed');
        assert(db.value(`SELECT COUNT(*) FROM billing_on_header WHERE id=${Number(headerId)} AND disk_id=${Number(diskId)}`) === '1',
          'Claim does not belong to its generated disk');
        await page.close();
      }
      assert(new Set(filenames).size === 2, 'Successive group exports reused a filename');
      assertStrictPage(recorder);
      console.log('  Valid provider unaffected by unrelated invalid group; correction succeeds; distinct downloads and billed claims verified');
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
