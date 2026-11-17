#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Invoice List: a claim's sequence-number link, which opens that claim's MSP remittance lines (teleplanS00, the
 * S00 records of the MSP remittance) in the RecValues window. billing-bc-remittance-upload opens the remittance by RA
 * number from Reconciliation Reports (ViewGenTAS00); no check opens it by claim.
 *
 * User path: Master record ▸ Billing ▸ Invoice List (billing/CA/BC/reprocessBill, billStatus.jsp) ▸ the claim's
 * sequence number ▸ billing/CA/BC/ViewGenTAS00ByOfficeNo?officeNo=<the claim's billingmaster number> (a popup).
 * Asserted, on two owned claims and their own remittance lines only:
 *   - the Invoice List shows each claim's latest MSP sequence number as its link, naming the claim;
 *   - the first claim's link opens exactly its two lines (office number linked to the claim, fee code, billed and paid
 *     amounts in dollars, explanatory code, record type and what the line code means) and counts 2 records;
 *   - LAST: the second claim's link opens only its own line.
 * Fixtures: the workflow's own FAKE patient made a BC patient, with two MSP claims (billing + billingmaster, paid with
 * explanation, E) and three teleplanS00 lines whose filename is the run marker and whose office number is the owned
 * claim's (7 digits, zero-padded). Cleanup deletes the lines by marker and every BC billing row of the patient, and
 * asserts none is left. BC installs only (billregion=BC).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const bc = require('./lib/bc-billing-fixture');

const TIMEOUT = 60000;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  list: 'the Invoice List links each owned claim\'s latest MSP sequence number to that claim\'s remittance lines',
  first: 'the first claim\'s link opens exactly its two remittance lines with their codes and amounts',
  second: 'the second claim\'s link opens only its own remittance line',
};

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  s.cleanup(() => {
    sql.execute(`DELETE FROM teleplanS00 WHERE filename=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM teleplanS00 WHERE filename=${q(marker)}`) === '0', 'The owned remittance lines were not removed');
  });
  const claims = {
    first: bc.seedOwnedBcBill(s, { billingtype: 'MSP', status: 'E', amount: '33.70' }),
    second: bc.seedOwnedBcBill(s, { billingtype: 'MSP', status: 'E', amount: '45.00' }),
  };
  const office = claim => claim.billingmasterNo.padStart(7, '0');
  h.assert(Object.values(claims).every(claim => office(claim).length === 7), 'An owned claim number is longer than an MSP office number');
  h.assert(sql.value(`SELECT COUNT(*) FROM teleplanS00 WHERE t_officeno IN (${Object.values(claims).map(c => q(office(c))).join(',')})`) === '0',
    'Another remittance line already names an owned claim');
  const today = sql.value("SELECT DATE_FORMAT(CURDATE(), '%Y%m%d')");
  // As MSP sends them (see billing-bc-settle-paid-claims): amounts are 7 digits in cents, the office number is the claim's.
  const lines = [
    { claim: 'first', seq: '0000101', type: 'S01', code: 'P', meaning: 'Paid as billed', fee: '00100', billed: '0003370', paid: '0003370', exp: 'AA' },
    { claim: 'first', seq: '0000102', type: 'S00', code: 'R', meaning: 'Refusal', fee: '00110', billed: '0001500', paid: '0000000', exp: 'BG' },
    { claim: 'second', seq: '0000201', type: 'S01', code: 'P', meaning: 'Paid as billed', fee: '00100', billed: '0004500', paid: '0004500', exp: 'AB' },
  ];
  for (const line of lines) {
    sql.execute(`INSERT INTO teleplanS00 (s21_id, filename, t_s00type, t_datacenter, t_dataseq, t_payment, t_linecode,
        t_payeeno, t_mspctlno, t_practitionerno, t_msprcddate, t_initial, t_surname, t_phn, t_phndepno, t_servicedate, t_today,
        t_billnoservices, t_billclafcode, t_billfeeschedule, t_billamt, t_paidnoservices, t_paidclafcode, t_paidfeeschedule,
        t_paidamt, t_officeno, t_exp1, t_exp2, t_exp3, t_exp4, t_exp5, t_exp6, t_exp7, t_ajc1, t_aja1, t_ajc2, t_aja2, t_ajc3,
        t_aja3, t_ajc4, t_aja4, t_ajc5, t_aja5, t_ajc6, t_aja6, t_ajc7, t_aja7, t_paidrate, t_planrefno, t_claimsource,
        t_previouspaiddate, t_insurercode, t_icbcwcb, t_filler)
      VALUES (0, ${q(marker)}, ${q(line.type)}, '00000', ${q(line.seq)}, ${q(today)}, ${q(line.code)}, '00000', '000000', '00000',
        ${q(today)}, 'W', ${q(marker.slice(0, 18))}, '9000000000', '00', ${q(today)}, '00', '001', '00', ${q(line.fee)},
        ${q(line.billed)}, '001', '00', ${q(line.fee)}, ${q(line.paid)}, ${q(office(claims[line.claim]))}, ${q(line.exp)}, '', '', '',
        '', '', '', '', '0000000', '', '0000000', '', '0000000', '', '0000000', '', '0000000', '', '0000000', '', '0000000', '', '',
        '', '', '', '', '')`);
  }
  const dollars = cents => (Number(cents) / 100).toFixed(2);
  // The window lists a claim's lines in the order the database returns them; the set is what is asserted.
  const sorted = list => list.map(row => JSON.stringify(row)).sort();
  const expected = name => lines.filter(line => line.claim === name).map(line => ({
    office: office(claims[name]), fee: line.fee, billed: dollars(line.billed), paid: dollars(line.paid), exp: line.exp,
    type: line.type, meaning: line.meaning, linksClaim: true,
  }));

  let invoices;
  const seqLink = name => invoices.locator('tr', { has: invoices.locator(`input[name="billCheck"][value="${claims[name].billingNo}_${claims[name].billingmasterNo}"]`) })
    .locator(`a[onclick*="/billing/CA/BC/ViewGenTAS00ByOfficeNo?officeNo=${claims[name].billingmasterNo}'"]`);
  /** The remittance lines the RecValues window lists, and its record count line. */
  async function openLines(name) {
    const popup = await s.popup(invoices, seqLink(name), `bc-claim-remittance-${name}`);
    await popup.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(popup, 'the claim remittance lines');
    const rows = await popup.locator('tr').evaluateAll((trs, pad) => trs.map(tr => [...tr.querySelectorAll('td')])
      .filter(cells => cells.length >= 24 && cells[0].textContent.trim() !== 'Office No')
      .map(cells => {
        const text = index => cells[index].textContent.replace(/ /g, ' ').trim();
        const link = cells[0].querySelector('a');
        return { office: text(0), fee: text(2), billed: text(4), paid: text(5), exp: text(6), type: text(22), meaning: text(23),
          linksClaim: !!link && (link.getAttribute('href') || '').includes(`/billing/CA/BC/reprocessBill?billingmaster_no=${pad}`) };
      }), office(claims[name]));
    const counted = (await popup.locator('td[colspan="23"]').last().innerText()).trim();
    await popup.close();
    return { rows, counted };
  }

  await s.step(STEP.list, async () => {
    invoices = await bc.openBillStatus(s, ui);
    for (const name of Object.keys(claims)) {
      const link = seqLink(name);
      h.assert(await link.count() === 1, `The Invoice List row of the ${name} claim has no sequence-number link to its remittance`);
      const latest = lines.filter(line => line.claim === name).map(line => line.seq).sort().pop();
      h.assert((await link.innerText()).trim() === latest,
        `The ${name} claim's link reads "${(await link.innerText()).trim()}", not its latest MSP sequence ${latest}`);
    }
  });

  await s.step(STEP.first, async () => {
    const { rows, counted } = await openLines('first');
    h.assert(JSON.stringify(sorted(rows)) === JSON.stringify(sorted(expected('first'))),
      `The first claim's remittance window lists ${JSON.stringify(rows)}`);
    h.assert(counted === '2 Records found.', `The first claim's remittance window counts "${counted}"`);
  });

  await s.step(STEP.second, async () => {
    const { rows, counted } = await openLines('second');
    h.assert(JSON.stringify(sorted(rows)) === JSON.stringify(sorted(expected('second'))),
      `The second claim's remittance window lists ${JSON.stringify(rows)}`);
    h.assert(counted === '1 Record found.', `The second claim's remittance window counts "${counted}"`);
  });
}

if (require.main === module) runWorkflow('billing-bc-claim-remittance-lines', workflow);
module.exports = { workflow, STEP };
