/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * The BC billing writes that get-reject-bc-billing (a GET or HEAD must be refused) and authz-bc-billing (a login
 * without billing rights must be refused) send, the rows each could change, and the owned rows they are aimed at
 * (issue #4439, task 3).
 *
 * Each write names the fields its own page posts (read from the page's form: the bill view's delete, the day sheet's
 * unbill, Receive Payment, the WCB form, the WCB correction, Manage Billing Codes, Manage Private Bill, the
 * association editor and list, Manage Referral Doctors, BC Billing Preferences and Quick Billing), aimed at the
 * check's own rows. A refusal is judged by the application's own answer (405 or its 403 / security error page) AND
 * by unchanged rows; a missing field can therefore never make a route that would write look refused, it can only make
 * the write fail and the check report the route as answering.
 *
 * Two writes are left to the checks that own their clinic-wide side effects: Generate Teleplan File's POST
 * (billing-bc-teleplan-file records and restores the batch number, the Teleplan sequence and HOME_DIR, and sends the
 * GET naming a physician) and Settle Paid Claims (billing-bc-settle-paid-claims owns the only BG claim it may settle,
 * and sends the GET and HEAD). authz-bc-billing still opens both pages (a GET) as the login without billing rights.
 *
 * Fixtures (all removed and asserted gone by the cleanup each registers): an MSP bill on the owned FAKE patient, made
 * a BC patient, linked to an owned appointment that is billed (B); one MSP fee code and two diagnostic codes
 * described by the run marker, and the fee code's association with the first diagnosis; any referral doctor named by
 * the marker and any WCB form of the owned patient a write leaves behind.
 */
const h = require('./playwright-harness');
const bc = require('./bc-billing-fixture');
const { seedAppointment } = require('./gap-billing-support');

/** The provider-scoped property rows BC Billing Preferences saves (SaveBillingPreferences2Action). */
const PREFERENCE_PROPERTIES = ['default_billing_form', 'default_billing_provider', 'bc_default_service_location',
  'invoice_payee_info', 'invoice_payee_display_clinic', 'auto_populate_refer'];

/**
 * Seed the owned rows the writes aim at. Returns ids, codes and the snapshot readers.
 *
 * @param {Object} s the workflow session (owned FAKE patient)
 */
function seedBcBillingTargets(s) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  bc.requireBcBillingSchema(sql);
  bc.makeBcPatient(s);
  const appointment = seedAppointment(s, { time: '15:45:00', tag: 'BC billing refusal' });
  bc.registerOwnedBcBillCleanup(s);
  const bill = bc.seedOwnedBcBill(s, { code: '00100', amount: '23.00' });
  sql.execute(`UPDATE billing SET appointment_no=${appointment} WHERE billing_no=${bill.billingNo};
    UPDATE billingmaster SET appointment_no=${appointment} WHERE billingmaster_no=${bill.billingmasterNo};
    UPDATE appointment SET status='B' WHERE appointment_no=${appointment} AND demographic_no=${patient}`);

  const codes = { dx: [] };
  const assocMark = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM ctl_servicecodes_dxcodes'));
  const referralMark = Number(sql.value('SELECT COALESCE(MAX(billingreferral_no),0) FROM billingreferral'));
  s.cleanup(() => {
    const dxList = codes.dx.length ? codes.dx.map(q).join(',') : "''";
    if (codes.fee) {
      sql.execute(`DELETE FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee)} OR (id>${assocMark} AND dxcode IN (${dxList}))`);
      sql.execute(`DELETE FROM billingservice WHERE service_code IN (${q(codes.fee)},${q(codes.added)}) AND description LIKE ${q(`${marker}%`)}`);
    }
    if (codes.dx.length) sql.execute(`DELETE FROM diagnosticcode WHERE diagnostic_code IN (${dxList}) AND description=${q(`${marker} diagnosis`)}`);
    sql.execute(`DELETE FROM billingreferral WHERE billingreferral_no>${referralMark} AND last_name=${q(marker)}`);
    sql.execute(`DELETE FROM wcb WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee || '')} OR dxcode IN (${dxList}))
      + (SELECT COUNT(*) FROM billingservice WHERE description LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM diagnosticcode WHERE description LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM billingreferral WHERE last_name=${q(marker)})
      + (SELECT COUNT(*) FROM wcb WHERE demographic_no=${patient})`) === '0',
    'The owned BC billing codes, associations, referral doctors or WCB forms were not removed');
  });
  const taken = c => sql.value(`SELECT (SELECT COUNT(*) FROM billingservice WHERE service_code LIKE ${q(`${c}%`)})
    + (SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE service_code=${q(c)})`) !== '0';
  codes.fee = bc.unusedNumber('9', 5, taken);
  // A second free code, only ever named by the Add Code write (which must not create it).
  codes.added = bc.unusedNumber('9', 5, c => c === codes.fee || taken(c));
  sql.execute(`INSERT INTO billingservice (service_code,description,value,percentage,billingservice_date,termination_date,specialty,region,anaesthesia)
    VALUES (${q(codes.fee)},${q(`${marker} fee`)},'12.34','',DATE_SUB(CURDATE(),INTERVAL 1 YEAR),'9999-12-31','','BC','00')`);
  codes.feeId = sql.value(`SELECT billingservice_no FROM billingservice WHERE service_code=${q(codes.fee)} AND description=${q(`${marker} fee`)}`);
  h.assert(/^[1-9]\d*$/.test(codes.feeId), 'The owned fee code was not created');
  while (codes.dx.length < 2) {
    const dx = bc.unusedNumber('Z', 5, c => codes.dx.includes(c)
      || sql.value(`SELECT (SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code=${q(c)})
        + (SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE dxcode=${q(c)})`) !== '0');
    codes.dx.push(dx);
    sql.execute(`INSERT INTO diagnosticcode (diagnostic_code,description,status,region) VALUES (${q(dx)},${q(`${marker} diagnosis`)},'A','BC')`);
  }
  sql.execute(`INSERT INTO ctl_servicecodes_dxcodes (service_code,dxcode) VALUES (${q(codes.fee)},${q(codes.dx[0])})`);
  const referralNo = bc.unusedNumber('9', 5, c => sql.value(`SELECT COUNT(*) FROM billingreferral WHERE referral_no=${q(c)}`) !== '0');

  return { appointment, bill, codes, referralNo };
}

/**
 * The writes, each with the rows it could change. `providerNo` is the login that sends them: BC Billing Preferences
 * always saves the signed-in provider's own preferences.
 *
 * @returns {{label: string, route: string, params: URLSearchParams, snapshot: function(): string}[]}
 */
function bcBillingWrites(s, targets, providerNo) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const { appointment, bill, codes, referralNo } = targets;
  const owned = () => sql.value(`SELECT CONCAT_WS('|',
      (SELECT GROUP_CONCAT(CONCAT(billing_no,':',status) ORDER BY billing_no) FROM billing WHERE demographic_no=${patient}),
      (SELECT GROUP_CONCAT(CONCAT(billingmaster_no,':',billingstatus) ORDER BY billingmaster_no) FROM billingmaster WHERE demographic_no=${patient}),
      (SELECT status FROM appointment WHERE appointment_no=${appointment}),
      (SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${bill.billingmasterNo}))`);
  const wcb = () => sql.value(`SELECT CONCAT_WS('|', COUNT(*), IFNULL(MAX(ID),0)) FROM wcb WHERE demographic_no=${patient}`);
  const fee = () => sql.value(`SELECT CONCAT_WS('|', COUNT(*), GROUP_CONCAT(CONCAT_WS(':',billingservice_no,value,termination_date) ORDER BY billingservice_no))
    FROM billingservice WHERE service_code IN (${q(codes.fee)},${q(codes.added)})`);
  const associations = () => sql.value(`SELECT CONCAT_WS('|', COUNT(*), GROUP_CONCAT(CONCAT(IFNULL(service_code,''),':',dxcode) ORDER BY id))
    FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee)} OR dxcode IN (${codes.dx.map(q).join(',')})`);
  const referrals = () => sql.value(`SELECT COUNT(*) FROM billingreferral WHERE last_name=${q(marker)} OR referral_no=${q(referralNo)}`);
  const preferences = () => sql.value(`SELECT CONCAT_WS('|',
      (SELECT GROUP_CONCAT(CONCAT_WS(':',id,referral,defaultPayeeNo) ORDER BY id) FROM billing_preferences WHERE providerNo=${q(providerNo)}),
      (SELECT GROUP_CONCAT(CONCAT_WS(':',name,IFNULL(value,'')) ORDER BY name) FROM property WHERE provider_no=${q(providerNo)}
        AND name IN (${PREFERENCE_PROPERTIES.map(q).join(',')})))`);
  const params = fields => new URLSearchParams(Object.entries(fields).map(([k, v]) => [k, String(v)]));

  return [
    { label: 'Quick Billing Save (saveQuickBillingBC)', route: 'saveQuickBillingBC',
      params: params({ submit: 'Save' }), snapshot: owned },
    { label: 'the day sheet unbill (BillingDeleteWithoutNo)', route: 'billing/CA/BC/BillingDeleteWithoutNo',
      params: params({ appointment_no: appointment, status: 'B' }), snapshot: owned },
    { label: 'the bill delete (BillingDeleteNoAppt)', route: 'billing/CA/BC/BillingDeleteNoAppt',
      params: params({ billing_no: bill.billingNo, billCode: 'O' }), snapshot: owned },
    { label: 'Receive Payment (receivePaymentAction)', route: 'billing/CA/BC/receivePaymentAction',
      params: params({ billNo: bill.billingNo, billingmasterNo: bill.billingmasterNo, amountReceived: '1.00', paymentMethod: '1', isRefund: 'false' }),
      snapshot: owned },
    { label: 'the WCB form Save and Close (formwcb)', route: 'billing/CA/BC/formwcb',
      params: params({ demographic_no: patient, wcbFormId: '', bNewForm: '1', w_lname: marker, w_fname: 'Workflow', saveAndClose: 'Save and Close' }),
      snapshot: wcb },
    { label: 'the WCB correction Submit (billingTeleplanCorrectionWCB)', route: 'billing/CA/BC/billingTeleplanCorrectionWCB',
      params: params({ billingNo: bill.billingNo, billing_no: bill.billingNo, id: '', demographicNumber: patient, w_lname: marker, submit: 'Submit' }),
      snapshot: () => `${owned()}#${wcb()}` },
    { label: 'Manage Billing Codes Add Code (billingAddCode)', route: 'billing/CA/BC/billingAddCode',
      params: params({ code: codes.added, desc: `${marker} added`, value: '1.00', whereTo: '' }), snapshot: fee },
    { label: "Manage Billing Codes' fee save (billingEditCode)", route: 'billing/CA/BC/billingEditCode',
      params: params({ method: 'ajaxCodeUpdate', id: codes.feeId, val: '99.99', codeId: codes.feeId }), snapshot: fee },
    { label: 'Manage Private Bill Delete (DeletePrivateCode)', route: 'billing/CA/BC/DeletePrivateCode',
      params: params({ code: codes.feeId }), snapshot: fee },
    { label: 'the association editor Save (saveAssocAction)', route: 'billing/CA/BC/saveAssocAction',
      params: params({ xml_other1: codes.fee, xml_diagnostic_detail1: codes.dx[1], mode: 'edit' }), snapshot: associations },
    { label: "the association list's Remove (deleteServiceCodeAssoc)", route: 'billing/CA/BC/deleteServiceCodeAssoc',
      params: params({ svcCode: codes.fee }), snapshot: associations },
    { label: 'Manage Referral Doctors Add (AddReferralDoc)', route: 'billing/CA/BC/AddReferralDoc',
      params: params({ referral_no: referralNo, last_name: marker, first_name: 'Workflow', specialty: '00', city: 'Victoria', province: 'BC' }),
      snapshot: referrals },
    { label: 'BC Billing Preferences Save (saveBillingPreferencesAction)', route: 'billing/CA/BC/saveBillingPreferencesAction',
      params: params({ referral: '1', payeeProviderNo: providerNo, defaultBillingForm: 'GP', defaultServiceLocation: 'A', autoPopulateRefer: 'true', submit: 'Save' }),
      snapshot: preferences },
  ];
}

/**
 * Remove BC Billing Preferences rows a write may have left for `providerNo`; for a throwaway login only (the
 * throwaway fixture removes its property rows, not billing_preferences).
 */
function registerPreferencesCleanup(s, providerNo) {
  const q = h.sqlString;
  h.assert(/^8\d{5}$/.test(providerNo), 'registerPreferencesCleanup is for a throwaway login (provider 800000-899999)');
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM billing_preferences WHERE providerNo=${q(providerNo)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM billing_preferences WHERE providerNo=${q(providerNo)}`) === '0',
      'The throwaway login\'s BC billing preferences were not removed');
  });
}

/** An application path as an absolute pathname (context root included), for get-reject-probe's ledger. */
function appPath(s, route) {
  return new URL(route, `${s.config.baseUrl.href.replace(/\/$/, '')}/`).pathname;
}

module.exports = { seedBcBillingTargets, bcBillingWrites, registerPreferencesCleanup, appPath, PREFERENCE_PROPERTIES };
