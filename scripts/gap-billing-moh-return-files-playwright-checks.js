#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario MOH return files other than the RA and the claims error report: the Batch Eligibility
 * (OBEC) output specification (R...) and the claims Batch Acknowledgement report (B...).
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Upload MOH files (BillingONUpload in the
 * #dynamic-content iframe) ▸ choose a synthetic R or B file ▸ Create Report
 * (oscarBilling/DocumentErrorReportUpload). billing-on-ra-import covers the P and E files.
 *
 * Asserts (one step, because every page these uploads render links a missing /billing.css and the
 * strict page check would otherwise stop the run after the first): the R report lists each row with
 * its health number, version, response code and the MOH
 * reason, resolves the owned patient behind the health number and reports one applied update and
 * the skipped rows (response code 55 needs no update; a health number no patient holds); applying
 * flips the patient's health-card version to "##" and appends the reason to the patient's alert
 * without a stray "null" line; the same file uploaded again changes nothing (the version no longer
 * matches). The B report lists its batch row with batch, provider, group and process date (the
 * LAST step: it fails while the page reads a property the parser does not have).
 *
 * Fixtures: createBillingFixture (owned provider, synthetic HIN and version ZZ on the owned
 * patient), an owned demographiccust row, the uploaded files (DOCUMENT_DIR and the MOH inbox,
 * removed in cleanup). Nothing is sent to MOH/MCEDT.
 * Implements the gap-billing "MOH return files" workflow. Optional RA_DOCUMENT_DIR / RA_EDT_INBOX.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { settleOperations } = require('./graceful-signal-cancellation');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');

const DEFAULT_DOCUMENT_DIR = '/var/lib/carlos-emr/CarlosDocument/carlos/document';
const DEFAULT_EDT_INBOX = '/var/lib/carlos-emr/CarlosDocument/carlos/onEDTDocs/inbox';
const q = h.sqlString;

function directory(env, fallback) {
  const dir = process.env[env] || fallback;
  h.assert(path.isAbsolute(dir) && fs.statSync(dir).isDirectory(), `${env} must name an existing directory`);
  return fs.realpathSync(dir);
}

/** One OBEC output-specification record: HIN, version, response code, expiry (27) and second name (85). */
function obecLine(hin, version, response) {
  return `${hin}${version}${response}`.padEnd(27) + '20991231'.padEnd(58) + 'FAKE'.padEnd(20) + ' '.repeat(10);
}

/** One batch-acknowledgement record laid out as BillingClaimBatchAcknowledgementReportParser reads it. */
function batchAckLine({ batch, operator, created, sequence, group, provider, claims, records, processed, reason }) {
  const line = ['HB1'.padEnd(6), batch.padEnd(5), operator.padEnd(6), created.padEnd(8), sequence.padEnd(4), ''.padEnd(11),
    ''.padEnd(5), ''.padEnd(7), group.padEnd(4), provider.padEnd(6), claims.padEnd(5), records.padEnd(6), processed.padEnd(8),
    reason.padEnd(40)].join('');
  h.assert(line[2] === '1' && line.slice(6, 11) === batch.padEnd(5) && line.slice(56, 62) === provider.padEnd(6),
    'The synthetic batch acknowledgement record does not match the parser layout');
  return line;
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const documentDir = directory('RA_DOCUMENT_DIR', DEFAULT_DOCUMENT_DIR);
  const edtInbox = directory('RA_EDT_INBOX', DEFAULT_EDT_INBOX);
  const hex = marker.slice('FAKE-PW'.length);
  const obecName = `RPW${hex}.001`;
  const ackName = `BPW${hex}.001`;
  const owned = createBillingFixture(s);
  s.cleanup(() => {
    sql.execute(`DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    for (const dir of [documentDir, edtInbox]) {
      for (const name of [obecName, ackName]) {
        const file = path.join(dir, name);
        if (fs.existsSync(file)) {
          h.assert(fs.lstatSync(file).isFile(), 'Refusing to remove a non-regular uploaded file');
          fs.unlinkSync(file);
        }
        h.assert(!fs.existsSync(file), 'An uploaded MOH file was not removed');
      }
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient}`) === '0', 'The owned demographiccust row was not removed');
  });
  sql.execute(`INSERT INTO demographiccust (demographic_no, cust1, cust2, cust3, cust4, content) VALUES (${patient}, '', '', NULL, '', '')`);
  const strangerHin = `8${String(Math.floor(Math.random() * 1e9)).padStart(9, '0')}`;
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${q(strangerHin)}`) === '0', 'The unmatched health number is in use');
  const patientState = () => sql.value(`SELECT CONCAT_WS('|', ver, IFNULL((SELECT cust3 FROM demographiccust WHERE demographic_no=${patient}), 'none'))
    FROM demographic WHERE demographic_no=${patient}`);
  const before = patientState();
  h.assert(before === `ZZ|none` || before.startsWith('ZZ|'), 'The owned patient does not start with health-card version ZZ');

  const admin = await openAdministration(s);
  async function upload(name, text) {
    const frame = await openAdminFrame(admin, '/billing/CA/ON/BillingONUpload', 'form#form1');
    await frame.locator('input[name="file1"]').setInputFiles({ name, mimeType: 'text/plain', buffer: Buffer.from(text, 'latin1') });
    const [response] = await settleOperations([
      admin.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/oscarBilling/DocumentErrorReportUpload'), { timeout: 60000 }),
      frame.locator('input[type="submit"][value="Create Report"]').click(),
    ]);
    await frame.waitForLoadState('domcontentloaded');
    return { frame, status: response.status() };
  }
  const text = frame => frame.locator('body').innerText().then(t => t.replace(/\s+/g, ' '));
  /** The data rows of a report table: the trimmed texts of each row's td.dataTable cells. */
  const dataRows = frame => frame.locator('tr').filter({ has: frame.locator('td.dataTable') }).evaluateAll(rows =>
    rows.map(row => Array.from(row.querySelectorAll('td.dataTable')).map(cell => cell.innerText.trim())));

  const obec = [obecLine(owned.hin, 'ZZ', '60'), obecLine(owned.hin, 'ZZ', '55'), obecLine(strangerHin, 'ZZ', '60')].join('\r\n') + '\r\n';

  // One step: every page these uploads render links /billing.css, which does not exist (the page
  // answers HTML), and the strict page check runs at the end of a step, so the steps below would
  // never be reached if each one ended on it.
  await s.step('the R report, its apply and a repeat upload behave, and the B report lists its batch row', async () => {
    const first = await upload(obecName, obec);
    h.assert(first.status === 200, `The OBEC upload answered HTTP ${first.status}`);
    const body = await text(first.frame);
    h.assert(body.includes(owned.hin) && body.includes(strangerHin), 'The R report does not list the health numbers of the file');
    h.assert(body.includes('Expired card'), 'The R report does not show the MOH response of code 60');
    h.assert(body.includes(marker), 'The R report does not resolve the owned patient behind the health number');
    h.assert(/1 demographic record\(s\) were updated/.test(body) && /skipped 2 row/i.test(body),
      'The R report does not state one applied update and two skipped rows');
    h.assert(body.includes('does not require an update') && body.includes('no demographic match'), 'The R report does not give the skip reasons');
    // Each row shows its own health number, version and response code (Health #, Ver, Response Code columns).
    const rRows = (await dataRows(first.frame)).map(cells => cells.slice(0, 3).join(' '));
    const expectedRows = [`${owned.hin} ZZ 60`, `${owned.hin} ZZ 55`, `${strangerHin} ZZ 60`];
    h.assert(rRows.length === 3 && rRows.slice().sort().join('|') === expectedRows.slice().sort().join('|'),
      'The R report rows do not show the expected health number, version and response code of each of the three file rows');
    h.assert(fs.existsSync(path.join(documentDir, obecName)), 'The R file was not stored in DOCUMENT_DIR');

    const [ver, ...alertParts] = patientState().split('|');
    const alert = alertParts.join('|');
    h.assert(ver === '##', 'Applying the R file did not flag the patient\'s health-card version ##');
    h.assert(alert.includes('Invalid old version code: ZZ') && alert.includes('Expired card') && alert.includes('Response Code: 60'),
      'The patient alert does not carry the old version, the MOH reason and the response code');

    const stateBefore = patientState();
    const again = await upload(obecName, obec);
    h.assert(/version mismatch/.test(await text(again.frame)), 'The second R upload does not report the version mismatch');
    h.assert(patientState() === stateBefore, 'Uploading the same R file again changed the patient');

    // The two defects below are judged last so everything above is proven first.
    const problems = [];
    if (/^null/.test(alert)) problems.push('the patient alert starts with a literal "null" line (the alert is concatenated while empty)');
    const ack = batchAckLine({ batch: '00001', operator: 'FAKE01', created: '20260101', sequence: '0001', group: owned.groupNo,
      provider: owned.ohipNo, claims: '00001', records: '000001', processed: '20260102', reason: 'SYNTHETIC BATCH REJECT REASON' });
    const acked = await upload(ackName, `${ack}\r\n`);
    const ackBody = acked.status === 200 ? await text(acked.frame) : '';
    // Batch #, Oper.#, Provider #, Group#, Create Date, Seq#, Rec Start/End/Type, Claims, Records, Process Date, Reason.
    const ackRows = acked.status === 200 ? await dataRows(acked.frame) : [];
    const ackRow = ackRows.find(cells => cells[2] === owned.ohipNo) || [];
    if (acked.status !== 200 || !ackBody.includes(owned.ohipNo) || !ackBody.includes('SYNTHETIC BATCH REJECT REASON')
      || ackRow[0] !== '00001' || ackRow[3] !== owned.groupNo || ackRow[11] !== '20260102' || ackRow[12] !== 'SYNTHETIC BATCH REJECT REASON') {
      problems.push(`the Batch Acknowledgement report page answered HTTP ${acked.status} instead of listing its batch row`);
    }
    h.assert(!problems.length, problems.join('; '));
  });
}

if (require.main === module) runWorkflow('gap-billing-moh-return-files', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
