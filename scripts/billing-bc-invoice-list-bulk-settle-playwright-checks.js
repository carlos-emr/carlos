#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Invoice List: the ticked-bill Settle and Reprocess And Resubmit buttons.
 *
 * Bill Status (billing/CA/BC/reprocessBill, billStatus.jsp) lists a patient's bills with a box on each row; Settle and
 * Reprocess And Resubmit post the ticked rows (billCheck) to the same route, which reprocesses each from its stored
 * values (BillingReProcessBill2Action's mass edit). billing-bc-bill-status-filters checks only the list's filters,
 * and billing-bc-reprocess-bill settles one bill through the adjust bill page; no check presses these buttons.
 *
 * User path: Master record ▸ Billing ▸ Invoice List ▸ tick bills ▸ Settle; then tick a rejected bill ▸ Reprocess
 * And Resubmit. Asserted, on the three owned bills only:
 *   - the Invoice List lists them, and both buttons stay disabled until a bill is ticked;
 *   - Settle settles exactly the two ticked bills, changing nothing else on them, and leaves the unticked bill
 *     unchanged in every column;
 *   - LAST: Reprocess And Resubmit puts exactly the ticked rejected bill back to Not Submitted (queued for the next
 *     Teleplan file), changing nothing else on it, leaves the two settled bills unchanged, and sends nothing.
 * "Sends nothing": resubmitting only re-queues a bill; the Teleplan file is made and sent from Administration ▸
 * Billing (ManageTeleplan2Action, the only caller of TeleplanAPI). The step asserts that no batch, Teleplan log line
 * or submission link was written and the Teleplan sequence did not move, that HOME_DIR (the Teleplan output
 * directory, which must exist when set; unset, the step says it did not look) gained no file, and that the browser
 * opened no Teleplan page and wrote to no other host.
 *
 * Fixtures: three MSP bills (billing + billingmaster) for the workflow's own FAKE patient, made a BC patient: two
 * paid with explanation (E) and one rejected (R), all dated today, holding the creation date, name check and
 * provider billing numbers a bill-form save stores (a reprocess writes those again). Cleanup deletes every BC billing
 * row of that patient, with the history and notes the saves wrote, and asserts none is left. BC installs only
 * (billregion=BC).
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const bc = require('./lib/bc-billing-fixture');

const TIMEOUT = 60000;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  list: 'the Invoice List lists the owned bills, with Settle and Reprocess And Resubmit disabled until one is ticked',
  settle: 'Settle settles exactly the two ticked bills and leaves the unticked bill unchanged',
  resubmit: 'Reprocess And Resubmit puts exactly the ticked rejected bill back to Not Submitted and sends nothing',
};

/** Every column of a table's row, by name, so a changed column can be named. */
function rowOf(sql, table, key, id) {
  const columns = sql.rows(`SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME=${h.sqlString(table)} ORDER BY ORDINAL_POSITION`).map(([name]) => name);
  h.assert(columns.length && columns.every(name => /^[A-Za-z_][A-Za-z0-9_]*$/.test(name)), `Cannot read the ${table} columns`);
  const [values] = sql.rows(`SELECT ${columns.map(name => `IFNULL(\`${name}\`,'<NULL>')`).join(',')} FROM ${table} WHERE ${key}=${id}`);
  h.assert(values, `No ${table} row ${id}`);
  return Object.fromEntries(columns.map((name, index) => [name, values[index]]));
}

const changed = (before, after) => Object.keys(before).filter(name => before[name] !== after[name]).sort();

async function workflow(s) {
  const { sql } = s;
  const q = h.sqlString;
  bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const bills = {
    first: bc.seedOwnedBcBill(s, { billingtype: 'MSP', status: 'E', amount: '23.00' }),
    second: bc.seedOwnedBcBill(s, { billingtype: 'MSP', status: 'E', amount: '31.62' }),
    rejected: bc.seedOwnedBcBill(s, { billingtype: 'MSP', status: 'R', amount: '45.00' }),
  };
  // As a bill saved through the bill form holds them (BillingSaveBilling2Action), since a reprocess writes them again
  // from the patient and provider: the creation date without a time, the name check, and the provider's billing and
  // practitioner numbers.
  const masters = Object.values(bills).map(({ billingmasterNo }) => billingmasterNo).join(',');
  sql.execute(`UPDATE billingmaster bm JOIN demographic d ON d.demographic_no=bm.demographic_no
      JOIN billing b ON b.billing_no=bm.billing_no JOIN provider p ON p.provider_no=b.provider_no
    SET bm.createdate=CURDATE(), bm.name_verify=CONCAT(LEFT(d.first_name,1),' ',LEFT(d.last_name,2)),
      bm.payee_no=p.billing_no, bm.practitioner_no=p.ohip_no, b.provider_ohip_no=p.ohip_no
    WHERE bm.billingmaster_no IN (${masters}) AND d.demographic_no=${s.patient}`);
  const snapshot = () => Object.fromEntries(Object.entries(bills).map(([name, { billingNo, billingmasterNo }]) => [name, {
    master: rowOf(sql, 'billingmaster', 'billingmaster_no', billingmasterNo),
    bill: rowOf(sql, 'billing', 'billing_no', billingNo),
  }]));
  // A bill's whole stored state, or only the named columns changed; anything else is a reprocess that rewrote it.
  const expectOnly = (before, after, name, allowed) => {
    const unexpected = ['master', 'bill'].flatMap(part => changed(before[name][part], after[name][part])
      .filter(column => !(allowed[part] || []).includes(column))
      .map(column => `${part === 'master' ? 'billingmaster' : 'billing'}.${column} ${before[name][part][column]} -> ${after[name][part][column]}`));
    h.assert(unexpected.length === 0, `The ${name} bill also changed: ${unexpected.join('; ')}`);
  };
  const boxOf = name => `input[name="billCheck"][value="${bills[name].billingNo}_${bills[name].billingmasterNo}"]`;

  let invoices;
  const requests = [];
  const base = new URL(s.config.baseUrl);
  const record = request => requests.push({ url: request.url(), method: request.method() });
  s.context.on('request', record);
  async function press(button) {
    await Promise.all([
      invoices.waitForURL(url => url.pathname.endsWith('/billing/CA/BC/reprocessBill'), { timeout: TIMEOUT, waitUntil: 'domcontentloaded' }),
      invoices.locator(button).click(),
    ]);
    await invoices.waitForLoadState('networkidle');
    await h.assertNotErrorPage(invoices, 'BC Bill Status after the ticked-bill save');
  }
  async function tick(names) {
    for (const name of names) await invoices.locator(boxOf(name)).check();
  }

  await s.step(STEP.list, async () => {
    invoices = await bc.openBillStatus(s, ui);
    for (const name of Object.keys(bills)) {
      h.assert(await invoices.locator(boxOf(name)).count() === 1, `The Invoice List has no box for the ${name} bill`);
    }
    h.assert(await invoices.locator('#settleButton').isDisabled() && await invoices.locator('#resubmitButton').isDisabled(),
      'Settle or Reprocess And Resubmit is enabled with no bill ticked');
    await invoices.locator(boxOf('first')).check();
    h.assert(await invoices.locator('#settleButton').isEnabled() && await invoices.locator('#resubmitButton').isEnabled(),
      'Ticking a bill did not enable Settle and Reprocess And Resubmit');
    await invoices.locator(boxOf('first')).uncheck();
  });

  await s.step(STEP.settle, async () => {
    const before = snapshot();
    await tick(['first', 'second']);
    await press('#settleButton');
    const after = snapshot();
    for (const name of ['first', 'second']) {
      h.assert(after[name].master.billingstatus === 'S', `The ticked ${name} bill is ${after[name].master.billingstatus}, not settled`);
      expectOnly(before, after, name, { master: ['billingstatus'], bill: ['status'] });
    }
    expectOnly(before, after, 'rejected', {});
  });

  await s.step(STEP.resubmit, async () => {
    const max = (table, column) => sql.value(`SELECT IFNULL(MAX(${column}),0) FROM ${table}`);
    const teleplan = () => JSON.stringify({
      batches: max('billactivity', 'id'), log: max('log_teleplantx', 'log_no'), links: max('teleplan_submission_link', 'id'),
      sequence: sql.rows(`SELECT id, value FROM property WHERE name=${q('teleplan_sequence')} ORDER BY id`),
    });
    // HOME_DIR is where the Teleplan file is written (billing-bc-teleplan-file). The proof needs the folder itself, so
    // a set but missing folder fails here, and an unset one is said plainly rather than passed over.
    const homeDir = process.env.HOME_DIR ? path.resolve(process.env.HOME_DIR) : '';
    if (homeDir) {
      h.assert(fs.existsSync(homeDir) && fs.statSync(homeDir).isDirectory(),
        `HOME_DIR (${homeDir}) is not a folder, so the Teleplan output cannot be checked`);
    } else {
      console.log('  NOTE billing-bc-invoice-list-bulk-settle: HOME_DIR is not set, so the Teleplan output folder was not '
        + 'checked; the batch, log line, link and sequence assertions still run');
    }
    const homeFiles = () => (homeDir ? fs.readdirSync(homeDir).sort() : []);
    const teleplanBefore = teleplan();
    const homeBefore = homeFiles();
    const before = snapshot();
    const firstRequest = requests.length;
    await tick(['rejected']);
    await press('#resubmitButton');
    const after = snapshot();
    h.assert(after.rejected.master.billingstatus === 'O',
      `The ticked rejected bill is ${after.rejected.master.billingstatus}, not back to Not Submitted`);
    expectOnly(before, after, 'rejected', { master: ['billingstatus'], bill: ['status'] });
    for (const name of ['first', 'second']) expectOnly(before, after, name, {});
    h.assert(teleplan() === teleplanBefore, 'Reprocess And Resubmit wrote a Teleplan batch, log line or link, or moved the sequence');
    h.assert(JSON.stringify(homeFiles()) === JSON.stringify(homeBefore), 'Reprocess And Resubmit wrote a file into HOME_DIR');
    // A page may fetch its static assets from elsewhere; a send would be a Teleplan page or a write to another host.
    const outside = requests.slice(firstRequest).filter(({ url, method }) => {
      const target = new URL(url);
      return ['http:', 'https:'].includes(target.protocol)
        && (/teleplan/i.test(target.pathname) || (target.host !== base.host && method !== 'GET'));
    });
    h.assert(outside.length === 0, `Reprocess And Resubmit made the browser send ${JSON.stringify(outside.slice(0, 3))}`);
  });
  s.context.off('request', record);
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

if (require.main === module) runWorkflow('billing-bc-invoice-list-bulk-settle', workflow, { openPatient: true, preflight });
module.exports = { workflow, STEP };
