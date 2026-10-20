#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Teleplan claim file: simulate it, then generate it (NEW_BC_TELEPLAN=yes, the packaged default).
 * billing-bc-simulation-encoding drives only the legacy ViewBillingSim by typed URL; no check drove
 * these two pages or the batch Generate writes. Nothing is sent to MSP: the Activity List's Send link
 * is never pressed.
 * User path: Schedule ▸ Administration ▸ Billing ▸ the Teleplan File2 items (in the #dynamic-content
 * iframe). SimulateTeleplanFile: pick the owned billing physician ▸ Create Report (the claim report,
 * a dry run). GenerateTeleplanFile: pick the physician ▸ Create Report (writes the MSP file and its
 * HTML report into HOME_DIR, a billactivity row, the claims' billed status, their Teleplan log lines,
 * the submission links and the Teleplan sequence) ▸ the Activity List.
 * Asserted: the simulation lists exactly the owned claims and writes nothing; Generate writes one
 * batch of 2 claims whose MSP file on disk is the batch's stored file, a VS1 header and one C02
 * record per owned claim carrying its PHN and fee code; the claims are marked billed (B), each has
 * one Teleplan log line and one submission link to the batch, and the sequence moved; the Activity
 * List lists the batch and its files; a second simulation lists none of the billed claims.
 * Known failures, recorded in the manifest notes (all three are in #4439): opening the generation
 * page from the menu (a GET) writes a batch of its own (D3); the Activity List's file links answer
 * 404 (D5); the two File2 menu labels are swapped (D4). They are the last three steps, in that order.
 * EXCLUSIVE: the batch number, the Teleplan sequence (property teleplan_sequence) and the
 * billactivity, log and link tables are clinic-wide. The check records them first and restores
 * them at the end: it deletes the billactivity, log and link rows written after the record and the
 * HOME_DIR files those batches name (only names in the batch file pattern, only inside HOME_DIR),
 * puts the sequence back (or removes it when it did not exist), and asserts all of it.
 * Needs HOME_DIR: the server's Teleplan output directory, readable and writable by this runner
 * (SKIP without it).
 * Fixtures: one billing physician (bc.createBillingPhysician; its practitioner number is unique, so
 * Generate for it takes the owned claims only) and two MSP claims on the workflow's own FAKE
 * patient, made a BC patient. Cleanup deletes every BC billing row of the patient, then the
 * physician, and asserts each is gone. BC installs only (billregion=BC).
 */
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 60000;
const SIM_ROUTE = '/billing/CA/BC/SimulateTeleplanFile';
const GEN_ROUTE = '/billing/CA/BC/GenerateTeleplanFile';
const CLAIMS = [{ code: '00100', amount: '23.00', dx: '780' }, { code: '00110', amount: '31.50', dx: '250' }];
const TOTAL = '54.50';
const SEQUENCE = 'teleplan_sequence';
// H, the month code, yyMMdd_HHmmss and the 3-digit batch number (GenerateTeleplanFile2Action), plus .html for the report.
const BATCH_FILE = /^H[0-9A-Z]\d{6}_\d{6}_\d{3}(\.html)?$/;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  openSim: 'Administration ▸ Billing ▸ the Teleplan simulation item opens the simulation form and lists the owned billing physician',
  simulate: 'the simulation for the owned physician lists exactly the owned claims and writes nothing',
  openGen: 'Administration ▸ Billing ▸ the Teleplan file item opens the generation form and lists the owned billing physician',
  generate: 'Create Report for the owned physician writes one batch whose MSP file holds exactly the owned claims, now billed and logged',
  activity: 'the Activity List lists the new batch with its MSP and HTML files',
  simAgain: 'a second simulation lists none of the billed claims',
  menuGet: 'opening the Teleplan file item from the menu writes no batch of its own',
  download: "the Activity List's MSP file link returns the file",
  labels: 'each Teleplan File2 menu item is labelled for what it does',
};

/**
 * The fields of a C02 claim record this check compares, by position (TeleplanFileWriter.getClaimDetailRecord:
 * C02, data centre 5, sequence 7, payee 5, practitioner 5, PHN 10, ... fee item 5, amount 7 in cents, ...,
 * office number 7 = the billingmaster number).
 */
function c02(line) {
  return { payee: line.slice(15, 20), practitioner: line.slice(20, 25), phn: line.slice(25, 35), code: line.slice(51, 56),
    amount: line.slice(56, 63), office: line.slice(139, 146) };
}

function homeDir() {
  return process.env.HOME_DIR ? path.resolve(process.env.HOME_DIR) : '';
}

/** Remove one batch file this run's batches named: only a batch file name, only inside HOME_DIR. */
function removeBatchFile(dir, name) {
  if (!name) return;
  h.assert(BATCH_FILE.test(name), `A batch written during the run names a file outside the batch pattern: ${JSON.stringify(name)}`);
  const file = path.join(dir, name);
  h.assert(path.dirname(file) === dir, 'A batch file resolves outside HOME_DIR');
  if (fs.existsSync(file)) fs.unlinkSync(file);
  h.assert(!fs.existsSync(file), `The batch file ${name} was not removed`);
}

/**
 * Record the clinic-wide Teleplan state and register its restore: the billactivity, log and link rows
 * written after the record and the HOME_DIR files of those batches are removed, and the sequence is put
 * back. Registered before the first write, so a failed step still restores. Each row is first proven to
 * be this run's (its batch written during the run, its claim one of `ownedMasters`).
 */
function recordTeleplanState(s, dir, ownedMasters) {
  const { sql } = s;
  const q = h.sqlString;
  const max = (table, column) => sql.value(`SELECT IFNULL(MAX(${column}),0) FROM ${table}`);
  const sequenceRows = () => JSON.stringify(sql.rows(`SELECT id, value FROM property WHERE name=${q(SEQUENCE)} ORDER BY id`));
  const before = {
    start: sql.value('SELECT NOW()'),
    activity: max('billactivity', 'id'),
    log: max('log_teleplantx', 'log_no'),
    link: max('teleplan_submission_link', 'id'),
    property: max('property', 'id'),
    sequence: sql.rows(`SELECT id, value FROM property WHERE name=${q(SEQUENCE)} ORDER BY id`),
  };
  before.sequenceJson = sequenceRows();
  s.cleanup(() => {
    const batches = sql.rows(`SELECT id, ohipfilename, htmlfilename, updatedatetime FROM billactivity WHERE id > ${before.activity}`);
    for (const [id, , , written] of batches) {
      h.assert(written >= before.start, `Batch ${id} was not written during this run; it is not the run's to remove`);
    }
    // The log and link rows are removed by id after the record, so first prove each is this run's: a log
    // row is an owned claim's line or a batch's VS1 header (logged without a claim), one header per
    // batch; a link joins one of the run's batches to an owned claim. A foreign row fails here instead.
    const batchIds = batches.map(([id]) => id).join(',') || '0';
    const foreignLogs = sql.value(`SELECT COUNT(*) FROM log_teleplantx WHERE log_no > ${before.log}
      AND NOT (billingmaster_no IN (${ownedMasters}) OR (billingmaster_no=0 AND claim LIKE 'VS1%'))`);
    h.assert(foreignLogs === '0', `${foreignLogs} Teleplan log row(s) written after the record are neither an owned claim's nor a batch header`);
    const headers = sql.value(`SELECT COUNT(*) FROM log_teleplantx WHERE log_no > ${before.log} AND billingmaster_no=0`);
    h.assert(Number(headers) === batches.length, `${headers} batch header log row(s) for ${batches.length} batch(es) written during the run`);
    const foreignLinks = sql.value(`SELECT COUNT(*) FROM teleplan_submission_link WHERE id > ${before.link}
      AND NOT (bill_activity_id IN (${batchIds}) AND billingmaster_no IN (${ownedMasters}))`);
    h.assert(foreignLinks === '0', `${foreignLinks} submission link(s) written after the record do not join the run's batches to owned claims`);
    for (const [, msp, html] of batches) {
      removeBatchFile(dir, msp);
      removeBatchFile(dir, html);
    }
    sql.execute([
      `DELETE FROM teleplan_submission_link WHERE id > ${before.link}`,
      `DELETE FROM log_teleplantx WHERE log_no > ${before.log}`,
      `DELETE FROM billactivity WHERE id > ${before.activity}`,
      `DELETE FROM property WHERE name=${q(SEQUENCE)} AND id > ${before.property}`,
      ...before.sequence.map(([id, value]) => `UPDATE property SET value=${q(value)} WHERE id=${id} AND name=${q(SEQUENCE)}`),
    ].join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billactivity WHERE id > ${before.activity})
      + (SELECT COUNT(*) FROM log_teleplantx WHERE log_no > ${before.log})
      + (SELECT COUNT(*) FROM teleplan_submission_link WHERE id > ${before.link})`) === '0', 'The Teleplan batch rows were not removed');
    h.assert(sequenceRows() === before.sequenceJson, 'The Teleplan sequence was not restored');
  });
  return before;
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
  const dir = homeDir();
  if (!dir || !fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    throw new h.SkipCheck('HOME_DIR (the server\'s Teleplan output directory, reachable from this runner) is required');
  }
}

async function workflow(s) {
  const { sql, patient } = s;
  const dir = homeDir();
  const physician = bc.createBillingPhysician(s);
  const phn = bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const claims = CLAIMS.map(claim => ({ ...claim, ...bc.seedOwnedBcBill({ ...s, provider: physician.providerNo }, claim) }));
  const masters = claims.map(claim => claim.billingmasterNo).join(',');
  const before = recordTeleplanState(s, dir, masters);
  const counts = () => sql.value(`SELECT CONCAT_WS('|',
      (SELECT COUNT(*) FROM billactivity WHERE id > ${before.activity}),
      (SELECT COUNT(*) FROM log_teleplantx WHERE log_no > ${before.log}),
      (SELECT COUNT(*) FROM teleplan_submission_link WHERE id > ${before.link}),
      (SELECT GROUP_CONCAT(billingstatus ORDER BY billingmaster_no) FROM billingmaster WHERE billingmaster_no IN (${masters})),
      (SELECT GROUP_CONCAT(status ORDER BY billing_no) FROM billing WHERE demographic_no=${patient}))`);
  const untouched = '0|0|0|O,O|O,O';
  h.assert(counts() === untouched, `The Teleplan fixtures did not start untouched: ${counts()}`);

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-teleplan-file-admin' });
  const menuLink = route => admin.locator(`a.xlink[rel$="${route}"]`).first();
  const contentFrame = async () => {
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({ timeout: TIMEOUT });
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'The Teleplan page did not load into the Administration panel');
    return frame;
  };
  /** Open a menu item into the Administration panel; true when the item exists. */
  const openMenu = async (route, label) => {
    // The BC billing menu is there once Accounting Reports (shown on every BC install) is; only then
    // does a missing Teleplan item mean the install hides it.
    await menuLink('/billing/CA/BC/ViewBillingAccountReports').waitFor({ state: 'attached', timeout: TIMEOUT });
    const link = menuLink(route);
    if (await link.count() === 0) return null;
    await revealAuditLink(admin, link, 20000);
    const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(route), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `${label} answered HTTP ${response.status()}`);
    const frame = await contentFrame();
    await frame.locator('select[name="providers"]').waitFor({ timeout: TIMEOUT });
    await h.assertNotErrorPage(frame, label);
    return frame;
  };
  /**
   * Pick the owned physician and press Create Report; waits for the iframe's own navigation. The
   * simulation form submits by GET (it has no method), the generation form by POST.
   */
  const createReport = async (frame, route, method, label) => {
    await frame.locator('select[name="providers"]').selectOption(physician.ohipNo);
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
    navigated.catch(() => {});
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === method && new URL(r.url()).pathname.endsWith(route)
        && (method === 'POST' || new URL(r.url()).searchParams.get('providers') === physician.ohipNo), { timeout: TIMEOUT }),
      frame.locator('input[type="submit"][value="Create Report"]').click(),
    ]);
    h.assert(response.status() === 200, `${label} answered HTTP ${response.status()}`);
    await navigated;
    await frame.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(frame, label);
  };
  // A claim's row links its invoice to the adjust page; the red rows under it are the report's validation
  // messages (the synthetic PHNs fail MSP's check digit), which link there too and are not claims.
  const claimLinks = frame => frame.locator('tr:not([bgcolor="red"]) > td > a[onclick*="reprocessBill?billingmaster_no="]');
  const claimLink = (frame, master) => frame.locator(
    `tr:not([bgcolor="red"]) > td > a[onclick*="billingmaster_no=${master.padStart(7, '0')}'"]`);

  await s.step(STEP.openSim, async () => {
    const frame = await openMenu(SIM_ROUTE, 'The Teleplan simulation');
    if (!frame) throw new h.SkipCheck('The Teleplan File2 items are shown only with NEW_BC_TELEPLAN=yes');
    h.assert(await frame.locator(`select[name="providers"] option[value="${physician.ohipNo}"]`).count() === 1,
      'The simulation form does not list the owned billing physician');
  });

  await s.step(STEP.simulate, async () => {
    const frame = await contentFrame();
    await createReport(frame, SIM_ROUTE, 'GET', 'The simulation');
    for (const claim of claims) {
      h.assert(await claimLink(frame, claim.billingmasterNo).count() === 1, `The simulation does not list claim ${claim.billingmasterNo} once`);
    }
    h.assert(await claimLinks(frame).count() === claims.length,
      `The simulation for the owned physician lists ${await claimLinks(frame).count()} claims, not ${claims.length}`);
    h.assert(counts() === untouched, `The simulation wrote something: ${counts()}`);
  });

  let afterMenu;
  await s.step(STEP.openGen, async () => {
    const frame = await openMenu(GEN_ROUTE, 'The Teleplan file page');
    h.assert(frame, 'Administration ▸ Billing has no Teleplan file item');
    afterMenu = sql.value('SELECT IFNULL(MAX(id),0) FROM billactivity');
    h.assert(await frame.locator(`select[name="providers"] option[value="${physician.ohipNo}"]`).count() === 1,
      'The generation form does not list the owned billing physician');
  });

  let batch;
  await s.step(STEP.generate, async () => {
    const frame = await contentFrame();
    await createReport(frame, GEN_ROUTE, 'POST', 'Create Report (generate)');
    const rows = sql.rows(`SELECT id, ohipfilename, htmlfilename, claimrecord, total, status FROM billactivity WHERE id > ${afterMenu}`);
    h.assert(rows.length === 1, `Create Report wrote ${rows.length} batches, not 1`);
    const [id, msp, html, records, total, status] = rows[0];
    batch = { id, msp, html };
    h.assert(records === String(claims.length) && total === TOTAL && status === 'A',
      `The batch is ${records} claims, ${total}, status ${status}; not ${claims.length}, ${TOTAL}, A`);
    h.assert(BATCH_FILE.test(msp) && html === `${msp}.html`, `The batch names unexpected files: ${msp}, ${html}`);
    const file = path.join(dir, msp);
    h.assert(fs.existsSync(file) && fs.existsSync(path.join(dir, html)), 'The batch\'s MSP or HTML file is not in HOME_DIR');
    // The file is the stored text plus println's newline. Compared by digest: the SQL client's batch
    // output escapes the record separators.
    const bytes = fs.readFileSync(file);
    const body = bytes.length && bytes[bytes.length - 1] === 0x0a ? bytes.subarray(0, bytes.length - 1) : bytes;
    h.assert(createHash('sha256').update(body).digest('hex') === sql.value(`SELECT SHA2(ohipcontext, 256) FROM billactivity WHERE id=${id}`),
      'The MSP file on disk is not the batch\'s stored file');
    const onDisk = bytes.toString('latin1');
    const lines = onDisk.split(/\r?\n|\r/).map(line => line.trim()).filter(Boolean);
    h.assert(lines[0].startsWith('VS1'), 'The MSP file does not start with its VS1 header');
    const records2 = lines.filter(line => line.startsWith('C02'));
    h.assert(records2.length === claims.length, `The MSP file has ${records2.length} C02 records, not ${claims.length}`);
    for (const claim of claims) {
      const record = records2.filter(line => c02(line).office === claim.billingmasterNo.padStart(7, '0'));
      h.assert(record.length === 1, `The MSP file has ${record.length} C02 records for claim ${claim.billingmasterNo}, not 1`);
      const fields = c02(record[0]);
      const expected = { payee: physician.ohipNo, practitioner: physician.ohipNo, phn, code: claim.code,
        amount: claim.amount.replace('.', '').padStart(7, '0') };
      for (const [field, value] of Object.entries(expected)) {
        h.assert(fields[field] === value, `Claim ${claim.billingmasterNo}'s C02 ${field} is ${JSON.stringify(fields[field])}, not ${value}`);
      }
    }
    h.assert(sql.value(`SELECT CONCAT_WS('|',
        (SELECT GROUP_CONCAT(billingstatus ORDER BY billingmaster_no) FROM billingmaster WHERE billingmaster_no IN (${masters})),
        (SELECT GROUP_CONCAT(status ORDER BY billing_no) FROM billing WHERE demographic_no=${patient}),
        (SELECT COUNT(*) FROM log_teleplantx WHERE billingmaster_no IN (${masters}) AND log_no > ${before.log}),
        (SELECT COUNT(*) FROM teleplan_submission_link WHERE bill_activity_id=${id} AND billingmaster_no IN (${masters})))`)
      === 'B,B|B,B|2|2', 'The claims are not each marked billed with one log line and one link to the batch');
    h.assert(JSON.stringify(sql.rows(`SELECT id, value FROM property WHERE name=${h.sqlString(SEQUENCE)} ORDER BY id`))
      !== before.sequenceJson, 'Generate did not move the Teleplan sequence');
  });

  await s.step(STEP.activity, async () => {
    const frame = await contentFrame();
    const row = frame.locator('tbody tr').filter({ hasText: batch.msp });
    h.assert(await row.count() === 1, 'The Activity List does not list the new batch once');
    const records = (await row.locator('td').nth(3).innerText()).trim();
    h.assert(records === String(claims.length), `The Activity List row shows ${JSON.stringify(records)} claims, not ${claims.length}`);
    h.assert((await row.innerText()).includes(batch.html), 'The Activity List row does not name the batch\'s HTML file');
  });

  await s.step(STEP.simAgain, async () => {
    const frame = await openMenu(SIM_ROUTE, 'The Teleplan simulation');
    await createReport(frame, SIM_ROUTE, 'GET', 'The second simulation');
    h.assert(await claimLinks(frame).count() === 0,
      `The second simulation still lists ${await claimLinks(frame).count()} claims for the owned physician`);
  });

  await s.step(STEP.menuGet, async () => {
    const written = sql.value(`SELECT COUNT(*) FROM billactivity WHERE id > ${before.activity} AND id <= ${afterMenu}`);
    h.assert(written === '0', `Opening the Teleplan file item from the menu wrote ${written} batch(es)`);
  });

  await s.step(STEP.download, async () => {
    const frame = await openMenu(GEN_ROUTE, 'The Teleplan file page');
    // Exact text: the HTML file's name is the MSP file's name plus .html.
    const link = frame.locator('tbody tr').filter({ hasText: batch.msp }).locator('a')
      .filter({ hasText: new RegExp(`^\\s*${batch.msp}\\s*$`) });
    h.assert(await link.count() === 1, `The Activity List has ${await link.count()} links for the batch's MSP file, not 1`);
    const href = await link.getAttribute('href');
    const response = await s.context.request.get(new URL(href, frame.url()).toString(), { maxRedirects: 0 });
    h.assert(response.status() === 200, `The MSP file link answered HTTP ${response.status()}`);
    h.assert((await response.body()).toString('latin1').trim() === fs.readFileSync(path.join(dir, batch.msp), 'latin1').trim(),
      'The MSP file link did not return the file');
  });

  await s.step(STEP.labels, async () => {
    const generate = (await menuLink(GEN_ROUTE).innerText()).trim();
    const simulate = (await menuLink(SIM_ROUTE).innerText()).trim();
    h.assert(/generate/i.test(generate) && !/simulat/i.test(generate) && /simulat/i.test(simulate) && !/generate/i.test(simulate),
      `The item that generates is labelled "${generate}" and the one that simulates "${simulate}"`);
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-teleplan-file', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
