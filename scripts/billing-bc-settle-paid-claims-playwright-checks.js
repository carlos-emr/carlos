#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * BC Administration ▸ Billing ▸ Settle Paid Claims: one Submit that settles every MSP claim paid an amount different
 * from what was billed (explanation code BG). For each such claim still waiting on its balance (bill line status E)
 * the page writes an internal adjustment for the amount still owing and, once it balances, marks the line settled.
 * Nothing else drives this page.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Settle Paid Claims (billing/CA/BC/ViewSettleBG, in the
 * #dynamic-content iframe) ▸ Submit (POST settled=false to the same route) ▸ "All claims with an explanation of type
 * 'BG' have been adjusted and settled".
 * Asserted: the menu item opens the settle form; the same request as a GET or HEAD is refused with 405 and settles
 * nothing; Submit says the claims were settled, writes one internal adjustment
 * (payment type 10) for exactly the FAKE claim's underpayment and marks its bill line settled (S); a second Submit
 * from the menu writes nothing more.
 *
 * The action is clinic-wide, so the check runs only where it can own every claim it touches: the preflight SKIPs when
 * any BG payment line already exists, and right before each Submit the check asserts that the FAKE claim is the only
 * bill line in status E with a BG payment.
 *
 * Fixtures: the owned FAKE- patient (lib/workflow-session.js) and one MSP bill on it: a billing row, its bill line
 * (status E, $33.70) and a teleplanS00 payment line for it (BG, paid $20.00, filename = the run marker). Cleanup deletes
 * those rows by id and marker, plus the billing-history rows the page wrote for that FAKE bill line, and asserts they
 * are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 60000;
const MENU_ROUTE = '/billing/CA/BC/ViewSettleBG';
const BG = "'BG' IN (s.t_exp1, s.t_exp2, s.t_exp3, s.t_exp4, s.t_exp5, s.t_exp6, s.t_exp7)";
// $33.70 billed, $20.00 paid: the page must adjust the $13.70 difference.
const BILLED = '33.70';
const OWING = '13.70';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ Settle Paid Claims opens the settle form',
  getRefused: 'a GET or HEAD asking to settle (settled=false) is refused with 405 and settles nothing',
  settle: "Submit adjusts the FAKE claim's BG underpayment and settles its bill line",
  again: 'a second Submit adjusts nothing more',
};

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='teleplanS00'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required');
  }
  // Submit settles every BG claim in the clinic; run only where the check's own claim will be the only one.
  const existing = sql.value(`SELECT COUNT(*) FROM teleplanS00 s WHERE ${BG}`);
  if (existing !== '0') {
    throw new h.SkipCheck(`Settle Paid Claims acts on every BG claim, and this database already has ${existing} BG payment line(s)`);
  }
}

/** The patient's MSP bill: a billing row, its bill line waiting on its balance, and an underpaid BG payment line. */
function seedClaim(s) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const rows = { billing: null, master: null, s00: null };
  s.cleanup(() => {
    if (rows.master) sql.execute(`DELETE FROM billing_history WHERE billingmaster_no=${rows.master}`);
    if (rows.s00) sql.execute(`DELETE FROM teleplanS00 WHERE s00_id=${rows.s00} AND filename=${q(marker)}`);
    if (rows.master) sql.execute(`DELETE FROM billingmaster WHERE billingmaster_no=${rows.master} AND demographic_no=${patient}`);
    if (rows.billing) sql.execute(`DELETE FROM billing WHERE billing_no=${rows.billing} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM teleplanS00 WHERE filename=${q(marker)})
      + ${rows.master ? `(SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${rows.master})` : '0'}`) === '0',
    'Owned Settle Paid Claims fixtures were not removed');
  });
  const today = sql.value("SELECT DATE_FORMAT(CURDATE(), '%Y%m%d')");
  // clinic_no and appointment_no are primitive ints on the Billing entity; NULL there fails the page's load of the bill.
  rows.billing = h.insertId(sql, `INSERT INTO billing (clinic_no, demographic_no, provider_no, appointment_no, apptProvider_no,
      billingtype, demographic_name, dob, update_date, billing_date, status, creator)
    VALUES (0, ${patient}, ${q(s.provider)}, 0, ${q(s.provider)}, 'MSP', ${q(`${marker},Workflow`)}, '19800102', CURDATE(), CURDATE(),
      'O', ${q(s.provider)})`, 'bill');
  // Status E: the claim was paid and is waiting on its balance, the state the page settles.
  rows.master = h.insertId(sql, `INSERT INTO billingmaster (billing_no, createdate, billingstatus, demographic_no, appointment_no,
      claimcode, datacenter, payee_no, practitioner_no, phn, name_verify, dependent_num, billing_unit, clarification_code,
      anatomical_area, after_hour, new_program, billing_code, bill_amount, payment_mode, service_date, service_to_day,
      submission_code, extended_submission_code, dx_code1, dx_code2, dx_code3, dx_expansion, service_location, referral_flag1,
      referral_no1, referral_flag2, referral_no2, time_call, service_start_time, service_end_time, birth_date, office_number,
      correspondence_code, claim_comment, mva_claim_code, icbc_claim_no, original_claim, facility_no, facility_sub_no,
      oin_insurer_code, oin_registration_no, oin_birthdate, oin_first_name, oin_second_name, oin_surname, oin_sex_code,
      oin_address, oin_address2, oin_address3, oin_address4, oin_postalcode, paymentMethod, wcb_id)
    VALUES (${rows.billing}, NOW(), 'E', ${patient}, 0, 'C02', '00000', '00000', '00000', '9000000000', 'SET', '00', '1', '00', '00',
      '0', '00', '00100', ${q(BILLED)}, '0', ${q(today)}, '00', '0', '', '780', '', '', '', 'A', '0', '', '0', '', '', '', '',
      '19800102', '', '0', '', 'N', '', '', '', '', '', '', '', '', '', '', 'F', '', '', '', '', '', '1', 0)`, 'bill line');
  // The payment line names its bill line by the 7-digit, zero-padded billingmaster number (MSPReconcile.forwardZero), and
  // holds its amounts as MSP sends them: 7 digits in cents (t_billamt and t_paidamt are varchar(7)).
  h.assert(sql.value("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='teleplanS00' AND COLUMN_NAME='t_paidamt'") === '7',
    'teleplanS00.t_paidamt is not the 7-character MSP amount this fixture writes');
  rows.s00 = h.insertId(sql, `INSERT INTO teleplanS00 (s21_id, filename, t_s00type, t_datacenter, t_dataseq, t_payment, t_linecode,
      t_payeeno, t_mspctlno, t_practitionerno, t_msprcddate, t_initial, t_surname, t_phn, t_phndepno, t_servicedate, t_today,
      t_billnoservices, t_billclafcode, t_billfeeschedule, t_billamt, t_paidnoservices, t_paidclafcode, t_paidfeeschedule,
      t_paidamt, t_officeno, t_exp1, t_exp2, t_exp3, t_exp4, t_exp5, t_exp6, t_exp7, t_ajc1, t_aja1, t_ajc2, t_aja2, t_ajc3,
      t_aja3, t_ajc4, t_aja4, t_ajc5, t_aja5, t_ajc6, t_aja6, t_ajc7, t_aja7, t_paidrate, t_planrefno, t_claimsource,
      t_previouspaiddate, t_insurercode, t_icbcwcb, t_filler)
    VALUES (0, ${q(marker)}, 'S01', '00000', '0000001', ${q(today)}, '1', '00000', '000000', '00000', ${q(today)}, 'W',
      ${q(marker.slice(0, 18))}, '9000000000', '00', ${q(today)}, '00', '001', '00', '00100', '0003370', '001', '00', '00100',
      '0002000', ${q(rows.master.padStart(7, '0'))}, 'BG', '', '', '', '', '', '', '', '0000000', '', '0000000', '', '0000000',
      '', '0000000', '', '0000000', '', '0000000', '', '0000000', '', '', '', '', '', '', '')`, 'payment line');
  return rows;
}

async function workflow(s) {
  const { sql } = s;
  const rows = seedClaim(s);
  const adjustments = () => sql.rows(`SELECT CAST(amount_received AS DECIMAL(10,2)), payment_type_id FROM billing_history
    WHERE billingmaster_no=${rows.master}`);
  const lineStatus = () => sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${rows.master}`);
  const assertOnlyOwnClaim = () => {
    const others = sql.value(`SELECT COUNT(*) FROM teleplanS00 s JOIN billingmaster bm
      ON bm.billingmaster_no = CAST(s.t_officeno AS UNSIGNED)
      WHERE ${BG} AND bm.billingstatus='E' AND bm.billingmaster_no<>${rows.master}`);
    h.assert(others === '0', `${others} other bill line(s) in status E have a BG payment; Submit would settle them too`);
  };

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-settle-paid-claims-admin' });
  const openSettle = async () => {
    const link = admin.locator(`a.xlink[rel$="${MENU_ROUTE}"]`).first();
    await link.waitFor({ state: 'attached', timeout: TIMEOUT });
    await revealAuditLink(admin, link, 20000);
    const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(MENU_ROUTE), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `Settle Paid Claims answered HTTP ${response.status()}`);
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({ timeout: TIMEOUT });
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Settle Paid Claims did not load into the Administration panel');
    await frame.locator('form input[type="submit"][name="Continue"]').waitFor({ timeout: TIMEOUT });
    return frame;
  };
  const submit = async (frame) => {
    assertOnlyOwnClaim();
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(MENU_ROUTE), { timeout: TIMEOUT }),
      frame.locator('form input[type="submit"][name="Continue"]').click(),
    ]);
    h.assert(response.status() === 200, `Submit answered HTTP ${response.status()}`);
    await frame.locator('h4', { hasText: "have been adjusted and settled" }).waitFor({ timeout: TIMEOUT });
  };

  let frame;
  await s.step(STEP.open, async () => {
    frame = await openSettle();
    h.assert(await frame.locator('h4', { hasText: 'Automatically settle claims' }).count() === 1,
      'The settle form does not say what Submit does');
  });

  // Only Submit's POST settles; the same request as a link (a GET) or a HEAD must not (#4439 task 7).
  await s.step(STEP.getRefused, async () => {
    const url = new URL(`${MENU_ROUTE.slice(1)}?settled=false`, `${s.config.baseUrl.href.replace(/\/$/, '')}/`).toString();
    for (const method of ['GET', 'HEAD']) {
      assertOnlyOwnClaim();
      const before = sql.value(`SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${rows.master}`);
      const response = await s.context.request.fetch(url, { method, maxRedirects: 0, failOnStatusCode: false });
      h.assert(response.status() === 405, `A ${method} with settled=false answered HTTP ${response.status()}, not 405`);
      await h.assertRefused(s, { response, table: 'billing_history', where: `billingmaster_no=${rows.master}`, before,
        label: `a ${method} with settled=false` });
      h.assert(lineStatus() === 'E', `A ${method} with settled=false moved the FAKE claim's bill line to ${lineStatus()}`);
    }
  });

  await s.step(STEP.settle, async () => {
    h.assert(adjustments().length === 0 && lineStatus() === 'E', 'The FAKE claim was not waiting on its balance before Submit');
    await submit(frame);
    const written = adjustments();
    h.assert(written.length === 1, `Submit wrote ${written.length} adjustment(s) for the FAKE claim, not 1`);
    h.assert(written[0][0] === OWING && written[0][1] === '10',
      `Submit wrote ${JSON.stringify(written[0])}, not an internal adjustment (10) of ${OWING}`);
    h.assert(lineStatus() === 'S', `The FAKE claim's bill line is in status ${lineStatus()}, not settled (S)`);
  });

  await s.step(STEP.again, async () => {
    await submit(await openSettle());
    h.assert(adjustments().length === 1, `A second Submit left ${adjustments().length} adjustments for the FAKE claim`);
    h.assert(lineStatus() === 'S', `A second Submit changed the FAKE claim's bill line to status ${lineStatus()}`);
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-settle-paid-claims', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
