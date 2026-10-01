#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario billing shortcut (hospital billing) page 1 / page 2 save check.
 *
 * User path: NONE in this release. billingShortcutPg1View has no opener in
 * the webapp (no JSP, JS or action links to it; the coverage plan's "billing
 * form ▸ shortcut page" step does not exist, and the month view's "billing
 * shortcut" is the B key to the Billing Report Center). The check therefore
 * SKIPs unless BILLING_SHORTCUT_DIRECT_ENTRY=true, in which case it opens the
 * page by address with the parameters the page's own billing-form chooser
 * links carry, and then drives page 1 ▸ Next ▸ page 2 ▸ Save (confirm) through
 * real clicks.
 *
 * Asserts, against MariaDB: the save writes one billing_on_cheader1 row for
 * the owned patient, provider and seeded appointment (status O, pay program
 * HCP, total = the billingservice fee, today's date, the chosen facility,
 * visit type and SLI location) and one billing_on_item row (code, fee, one
 * unit, dx 250); the page-2 calculation shows the same total; a second save
 * for the same appointment is reported (refused or duplicated) without
 * touching the first bill; GET against BillingShortcutPg2Save answers 405 with
 * Allow: POST. The appointment's status is read back and reported: the
 * hospital-billing path does not flip it to billed.
 *
 * Fixtures: one owned appointment for today (name = run marker) for the owned
 * FAKE- patient. Cleanup deletes the bills written for that appointment and
 * patient (transaction/ext/item/repo/header) and the appointment, and asserts
 * they are gone.
 *
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.7
 * billing-shortcut.
 *
 * Environment: the common contract in lib/playwright-harness.js readConfig().
 * BILLING_SHORTCUT_DIRECT_ENTRY=true enables the address entry described
 * above. Optional BILLING_SHORTCUT_CODE (default A007A).
 */

const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const PAGE1_FORM = 'form[name="titlesearch"][action$="/billing/CA/ON/BillingShortcutPg2Save"]';

function serviceCode() {
  const code = (process.env.BILLING_SHORTCUT_CODE || 'A007A').toUpperCase();
  h.assert(/^[A-Z]\d{3}[A-Z]$/.test(code), 'BILLING_SHORTCUT_CODE must be an Ontario service code like A007A');
  return code;
}

/** The value of the first option that carries a non-empty value, after selecting it. */
async function selectFirstValued(select) {
  const values = await select.locator('option').evaluateAll(options => options.map(option => option.value));
  const value = values.find(candidate => candidate.trim() !== '');
  h.assert(value !== undefined, 'The select offers no usable option');
  await select.selectOption(value);
  return value;
}

/** Pick today in the page's multiple-date flatpickr and close it so onClose fills billDate. */
async function pickTodayAsBillDate(page, today) {
  // After a save the page-1 redirect echoes billDate; a click would toggle today OFF.
  const already = (await page.locator('textarea[name="billDate"]').inputValue()).split('\n').filter(Boolean);
  if (already.length === 1 && already[0] === today) return;
  await page.locator('#trigger').click();
  const calendar = page.locator('.flatpickr-calendar.open');
  await calendar.waitFor({ state: 'visible', timeout: 10000 });
  await calendar.locator('.flatpickr-day.today').first().click();
  await page.locator('body').click({ position: { x: 4, y: 4 } });
  await calendar.waitFor({ state: 'hidden', timeout: 10000 });
  const dates = (await page.locator('textarea[name="billDate"]').inputValue()).split('\n').filter(Boolean);
  h.assert(dates.length === 1 && dates[0] === today, 'The service-date calendar did not record exactly today');
}

/** Drive page 1 (codes, dx, provider) ▸ Next ▸ page 2 ▸ Save for one appointment. */
async function driveShortcutSave(s, page, { code, provider, today, fee }) {
  await page.locator(PAGE1_FORM).waitFor({ state: 'visible', timeout: 20000 });
  const providers = page.locator(`${PAGE1_FORM} select[name="xml_provider"]`);
  const offered = await providers.locator('option').evaluateAll(options => options.map(option => option.value));
  if (!offered.includes(provider)) throw new h.SkipCheck('the shortcut page does not offer the fixture provider (doctor with an OHIP number)');
  await providers.selectOption(provider);
  await pickTodayAsBillDate(page, today);
  await page.locator('input[name="serviceDate0"]').fill(code);
  await page.locator('input[name="serviceUnit0"]').fill('1');
  await page.locator('input[name="dxCode"]').fill('250');
  const billType = page.locator('select[name="xml_billtype"]');
  const billTypes = await billType.locator('option').evaluateAll(options => options.map(option => option.value));
  const ohip = billTypes.find(value => value.startsWith('ODP'));
  h.assert(ohip, 'The shortcut page does not offer the Bill OHIP type');
  await billType.selectOption(ohip);
  const visitType = await selectFirstValued(page.locator('select[name="xml_visittype"]'));
  const location = await selectFirstValued(page.locator('select[name="xml_location"]'));
  const sli = await selectFirstValued(page.locator('select[name="xml_slicode"]'));
  await Promise.all([
    page.waitForURL(/\/billing\/CA\/ON\/BillingShortcutPg2Save/, { timeout: 30000, waitUntil: 'domcontentloaded' }),
    page.locator(`${PAGE1_FORM} input[type="submit"][name="submit"]`).click(),
  ]);
  await h.assertNotErrorPage(page, 'billing shortcut page 2');
  const confirm = page.locator('form[name="titlesearch"] input[name="addition"][value="Confirm"]');
  await confirm.waitFor({ state: 'attached', timeout: 20000 });
  const body = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
  h.assert(/Calculation/.test(body) && body.includes(code) && body.includes(`Total: ${Number(fee).toFixed(2)}`),
    'Page 2 did not show the calculation for the chosen code at the schedule fee');
  let response;
  const dialogs = await h.withExpectedDialogs(page, async () => {
    [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingShortcutPg2Save')
        && /(^|&)addition=Confirm(&|$)/.test(r.request().postData() || ''), { timeout: 30000 }),
      page.locator('input[name="button"][value="Save"]').click(),
    ]);
  });
  h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && /Are you sure you want to Save\?/.test(dialogs[0].text),
    'Save must ask its confirmation question exactly once');
  h.assert(response.status() === 200, `The shortcut save answered HTTP ${response.status()}`);
  // The page then either closes itself (Save) or returns to page 1 (the echoed
  // submit=Next wins); accept both and let the database decide what happened.
  await page.waitForURL(/\/billing\/CA\/ON\/billingShortcutPg1View/, { timeout: 20000, waitUntil: 'domcontentloaded' })
    .catch(() => {});
  return { visitType: visitType.slice(0, 2), facility: location.split('|')[0].slice(0, 4), sli: sli.trim() };
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const code = serviceCode();
  let appointmentNo = '';

  s.cleanup(() => {
    const appointments = sql.rows(`SELECT appointment_no FROM appointment WHERE demographic_no=${patient}
      AND name=${h.sqlString(marker)}`).map(row => row[0]);
    if (appointmentNo) appointments.push(appointmentNo);
    for (const appt of new Set(appointments)) {
      h.assert(/^[1-9]\d*$/.test(appt), 'Owned appointment id is invalid');
      const headers = sql.rows(`SELECT id FROM billing_on_cheader1 WHERE appointment_no=${appt}
        AND demographic_no=${patient}`).map(row => row[0]);
      for (const id of headers) {
        h.assert(/^[1-9]\d*$/.test(id), 'Owned billing header id is invalid');
        const items = sql.rows(`SELECT id FROM billing_on_item WHERE ch1_id=${id}`).map(Number);
        sql.execute(`DELETE FROM billing_on_repo WHERE (category='billing_on_item' AND h_id IN (${items.length ? items.join(',') : '0'}))
            OR (category='billing_on_cheader1' AND h_id=${id});
          DELETE FROM billing_on_transaction WHERE ch1_id=${id};
          DELETE FROM billing_on_ext WHERE billing_no=${id};
          DELETE FROM billing_on_item WHERE ch1_id=${id};
          DELETE FROM billing_on_cheader1 WHERE id=${id} AND demographic_no=${patient}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE id=${id})
          + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id=${id})`) === '0', 'Owned shortcut bill rows were not removed');
      }
      sql.execute(`DELETE FROM appointment WHERE appointment_no=${appt} AND demographic_no=${patient}
        AND name=${h.sqlString(marker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appt}`) === '0',
        'The owned appointment was not removed');
    }
  });

  if (process.env.BILLING_SHORTCUT_DIRECT_ENTRY !== 'true') {
    throw new h.SkipCheck('billingShortcutPg1View has no UI opener in this release; set BILLING_SHORTCUT_DIRECT_ENTRY=true to drive it by address');
  }

  // Page 1 lists doctors with an OHIP number; bill under one, preferring the login.
  const billingProvider = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND ohip_no<>''
    AND provider_type='doctor' ORDER BY (provider_no=${h.sqlString(provider)}) DESC, provider_no LIMIT 1`);
  if (!/^-?\d+$/.test(billingProvider)) throw new h.SkipCheck('no active doctor with an OHIP number to bill under');
  const today = sql.value('SELECT CURDATE()');
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(today), 'The database did not report today');
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    AND billingservice_date<=${h.sqlString(today)} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) {
    throw new h.SkipCheck(`service code ${code} has no positive schedule fee today; set BILLING_SHORTCUT_CODE`);
  }
  appointmentNo = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,
      updatedatetime, creator, remarks, urgency)
    VALUES (${h.sqlString(provider)}, CURDATE(), '10:00:00', '10:15:00', ${h.sqlString(marker)}, ${patient}, 0, '', '',
      '', '', '', '', '', 't', NOW(), NOW(), ${h.sqlString(provider)}, '', ''); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointmentNo), 'The appointment fixture was not created');

  const headers = () => sql.rows(`SELECT id FROM billing_on_cheader1 WHERE appointment_no=${appointmentNo}
    AND demographic_no=${patient} ORDER BY id`).map(row => row[0]);
  const headerSnapshot = id => sql.value(`SELECT CONCAT_WS('|', status, pay_program, total, provider_no, billing_date,
    facilty_num, visittype, location, demographic_name) FROM billing_on_cheader1 WHERE id=${id}`);
  const itemSnapshot = id => sql.value(`SELECT GROUP_CONCAT(CONCAT_WS('|', service_code, fee, ser_num, dx, status,
    service_date) ORDER BY id) FROM billing_on_item WHERE ch1_id=${id}`);

  // The parameters the page's own billing-form chooser links carry.
  const entry = new URLSearchParams({
    billForm: '', hotclick: '', appointment_no: appointmentNo, demographic_name: `${marker},Workflow`,
    demographic_no: patient, user_no: provider, apptProvider_no: provider, providerview: billingProvider,
    appointment_date: today, status: 't', start_time: '10:00:00', bNewForm: '1',
  });
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, `/billing/CA/ON/billingShortcutPg1View?${entry.toString()}`);
  await h.assertNotErrorPage(page, 'billing shortcut page 1');
  let first;
  let chosen;

  await s.step('page 1 ▸ Next ▸ page 2 ▸ Save writes the header and item for the appointment', async () => {
    chosen = await driveShortcutSave(s, page, { code, provider: billingProvider, today, fee });
    await expectValue(sql, `SELECT COUNT(*) FROM billing_on_cheader1 WHERE appointment_no=${appointmentNo}
      AND demographic_no=${patient}`, '1', 'The shortcut save did not write exactly one billing header');
    [first] = headers();
    h.assert(headerSnapshot(first) === ['O', 'HCP', Number(fee).toFixed(2), billingProvider, today, chosen.facility,
      chosen.visitType, chosen.sli, `${marker},Workflow`].join('|'),
    'The billing header does not carry the chosen provider, date, fee, facility, visit type and SLI location');
    h.assert(itemSnapshot(first) === `${code}|${Number(fee).toFixed(2)}|1|250|O|${today}`,
      'The billing item does not carry the code, schedule fee, one unit, dx and today');
    const appointment = sql.value(`SELECT CONCAT_WS('|', status, billing) FROM appointment WHERE appointment_no=${appointmentNo}`);
    console.log(`  NOTE billing-shortcut: appointment status|billing after the save = ${appointment} (not flipped to B by this path)`);
  });

  await s.step('a second save for the same appointment leaves the first bill untouched (count reported)', async () => {
    const before = { header: headerSnapshot(first), items: itemSnapshot(first) };
    // Save either closed the window or returned to page 1; reopen by address when closed.
    const again = page.isClosed() ? await s.context.newPage() : page;
    if (!/\/billing\/CA\/ON\/billingShortcutPg1View/.test(again.url())) {
      await h.gotoApp(again, s.config.baseUrl, `/billing/CA/ON/billingShortcutPg1View?${entry.toString()}`);
      await h.assertNotErrorPage(again, 'billing shortcut page 1 (second save)');
    }
    await driveShortcutSave(s, again, { code, provider: billingProvider, today, fee });
    await new Promise(resolve => setTimeout(resolve, 1500));
    const after = headers();
    h.assert(after.length === 1 || after.length === 2, `A second save left ${after.length} headers for one appointment`);
    h.assert(headerSnapshot(first) === before.header && itemSnapshot(first) === before.items,
      'A second save altered the first bill');
    if (after.length === 2) h.assert(itemSnapshot(after[1]) === before.items, 'The duplicate bill differs from the first');
    console.log(`  NOTE billing-shortcut: second save for the same appointment was ${after.length === 1 ? 'refused' : 'accepted as a duplicate bill'}`);
  });

  await s.step('GET against BillingShortcutPg2Save is refused and writes nothing', async () => {
    const count = headers().length;
    const probe = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/BillingShortcutPg2Save'), {
      params: { submit: 'Next', addition: 'Confirm', demographic_no: patient, appointment_no: appointmentNo,
        xml_provider: billingProvider, billDate: today, serviceDate0: code, serviceUnit0: '1', dxCode: '250' },
      maxRedirects: 0,
    });
    h.assert(probe.status() === 405 && probe.headers().allow === 'POST', 'BillingShortcutPg2Save must reject GET with Allow: POST');
    h.assert(headers().length === count, 'A rejected GET wrote a billing header');
  });
}

if (require.main === module) runWorkflow('billing-shortcut', workflow, { openPatient: true });
module.exports = { workflow };
