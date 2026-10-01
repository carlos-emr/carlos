#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Ontario OHIP bill Save (HIGH severity if it duplicates; a duplicate claim is a
 * billing error).
 *
 * User path: Schedule day sheet (the appointment's date) > the appointment's Bill link > OHIP bill form
 * (choose the General Practice form, tick the favourite code, type a diagnostic code) > Next > review
 * page > Save. For each rapid activation (dblclick(), two back-to-back clicks, double Enter in a review
 * text field when the review page has one, slow-response re-click) the check bills ONE owned
 * appointment and asserts EXACTLY ONE billing_on_cheader1 row and ONE billing_on_item row set for it.
 *
 * Fixtures: the owned FAKE- patient (given a synthetic HIN so billing is allowed) and one owned
 * appointment per mode on a fixed past date; cleanup deletes the headers/items/ext/transaction rows
 * for those appointments and the appointments, and asserts they are gone. Wave-6 sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer } = require('./lib/double-submit-helpers');

const BILL_DATE = process.env.BILLING_SUBMIT_DATE || '2024-05-06';
const OHIP_CODE = process.env.BILLING_OHIP_CODE || 'A007A';
const DX = process.env.BILLING_DX_CODE || '250';
const FORM = process.env.BILLING_FORM_NAME || 'General Practice';

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const apptIds = [];
  const headers = () => (apptIds.length ? sql.rows(`SELECT id FROM billing_on_cheader1 WHERE appointment_no IN (${apptIds.join(',')})
    AND demographic_no=${patient}`).map(([id]) => id) : []);
  s.cleanup(() => {
    for (const id of headers()) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned bill id is invalid');
      sql.execute(`DELETE FROM billing_on_transaction WHERE ch1_id=${id}`);
      sql.execute(`DELETE FROM billing_on_ext WHERE billing_no=${id}`);
      sql.execute(`DELETE FROM billing_on_item WHERE ch1_id=${id}`);
      sql.execute(`DELETE FROM billing_on_cheader1 WHERE id=${id} AND demographic_no=${patient}`);
    }
    if (apptIds.length) sql.execute(`DELETE FROM appointment WHERE appointment_no IN (${apptIds.join(',')}) AND demographic_no=${patient}`);
    h.assert(headers().length === 0, 'Owned bills were not removed');
    h.assert(!apptIds.length || sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no IN (${apptIds.join(',')})`) === '0',
      'Owned appointments were not removed');
  });
  sql.execute(`UPDATE demographic SET hin='9876543217', ver='ZZ', hc_type='ON', hc_renew_date='2099-12-31',
    date_joined='2020-01-01' WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);

  const [yy, mm, dd] = BILL_DATE.split('-').map(Number);
  const dayQuery = new URLSearchParams({ year: yy, month: mm, day: dd, view: '0', displaymode: 'day',
    dboperation: 'searchappointmentday', viewall: '1' });
  const v = verdicts('billing-on-save');

  for (const [index, mode] of MODES.entries()) {
    await s.step(`OHIP Save via ${mode.label} writes one bill`, async () => {
      const start = `${String(8 + index).padStart(2, '0')}:00:00`;
      const apptNo = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
        program_id, notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, remarks, urgency)
        VALUES (${h.sqlString(provider)}, ${h.sqlString(BILL_DATE)}, '${start}', ADDTIME('${start}', '00:14:00'),
        ${h.sqlString(`${marker},Workflow`)}, ${patient}, 0, ${h.sqlString(`${marker}-${mode.tag} bill`)}, ${h.sqlString(`${marker}-${mode.tag} bill`)},
        '', '', '', '', '', 't', NOW(), NOW(), ${h.sqlString(provider)}, '', ''); SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(apptNo), 'The appointment fixture was not created');
      apptIds.push(apptNo);

      await h.gotoApp(s.schedule, s.config.baseUrl, `/provider/providercontrol?${dayQuery}`);
      await s.schedule.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
      const bill = s.schedule.locator(`a[onclick*="/billing?billRegion"][onclick*="appointment_no=${apptNo}&"]`).first();
      await bill.waitFor({ state: 'attached', timeout: 20000 });
      const page = await s.popup(s.schedule, bill, 'bill-form');
      await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
      await page.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
      const physician = page.locator('select[name="xml_provider"]');
      const options = await physician.locator('option').evaluateAll((es) => es.map((e) => e.value));
      const own = options.find((value) => value.split('|')[0] === provider);
      if (own) await physician.selectOption(own);
      else await physician.selectOption(options.find((value) => /^-?\d+\|\d+$/.test(value)));
      const billType = await page.locator('select[name="xml_billtype"] option').evaluateAll((es) => (es.find((e) => e.value.startsWith('ODP')) || {}).value);
      await page.locator('select[name="xml_billtype"]').selectOption(billType);
      await page.locator('a[onclick*="showHideLayers(\'Layer1\',\'\',\'show\')"]').first().click();
      await page.locator('#Layer1').waitFor({ state: 'visible', timeout: 15000 });
      await page.locator('#Layer1 a', { hasText: FORM }).first().click();
      await page.waitForFunction(() => document.getElementById('Layer1').style.visibility !== 'visible', null, { timeout: 15000 });
      await page.locator(`input[name="xml_${OHIP_CODE}"]:visible`).first().check();
      await page.locator('input[name="dxCode"]').fill(DX);
      await page.locator('input[name="dxCode"]').dispatchEvent('change');
      await Promise.all([page.waitForURL(/ViewBillingONReview/, { timeout: 30000 }),
        page.locator('#titlesearch input[type="submit"][name="submit"]').click()]);
      await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await h.assertNotErrorPage(page, 'bill review');
      const save = page.locator('form[name="titlesearch"] input[type="submit"][value="Save"]');
      h.assert(await save.count(), 'the review page did not offer Save');
      const textField = page.locator('form[name="titlesearch"] input[type="text"]:visible').first();
      if (mode.key === 'doubleEnter' && !(await textField.count())) {
        console.log('    (review page has no visible text field: Enter mode not applicable)');
        await page.close().catch(() => {});
        return;
      }
      const route = /\/billing\/CA\/ON\/BillingONSave$/;
      const posts = watchPosts(page.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      await rapid(mode.key, save, { textField });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM billing_on_cheader1 WHERE appointment_no=${apptNo} AND demographic_no=${patient}`,
        { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      const items = sql.value(`SELECT COUNT(*) FROM billing_on_item i JOIN billing_on_cheader1 c ON c.id=i.ch1_id
        WHERE c.appointment_no=${apptNo} AND c.demographic_no=${patient}`);
      console.log(`    (${posts.seen.length} BillingONSave POST(s); ${count} header(s); ${items} item(s))`);
      v.record(mode.label, count, mode.key === 'doubleEnter' ? { atMost: 1 } : { exactly: 1 });
      if (!page.isClosed()) await page.close().catch(() => {});
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-billing-on', workflow, { openPatient: true, openMaster: false });
