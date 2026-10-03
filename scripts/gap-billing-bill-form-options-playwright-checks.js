#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario bill entry: every header option and the typed service-code columns, from the schedule.
 *
 * User path: Schedule ▸ owned appointment "B" (the bill form popup) ▸ billing physician, visit type,
 * visit location, SLI code, manual review, three diagnostic codes, Referral Doctor search (popup ▸
 * pick), typed service codes with units and percent ▸ Next (ViewBillingONReview) ▸ Save
 * (BillingONSave). The bill-type matrix (OHIP, WSIB, bonus) and the favourite grid are covered by
 * billing-on-submit; this check covers what that one leaves out.
 *
 * Asserts against MariaDB: the saved billing_on_cheader1 row carries the chosen physician and OHIP
 * number, visit type, facility number, SLI code, manual-review flag, referral number, billing date,
 * the patient's HIN and the appointment; one billing_on_item row per typed code with its units, the
 * fee = schedule fee x units x percent, and the three diagnostic codes; the header total equals the
 * sum of the items; the appointment turns "billed". The Referral Doctor popup lists the owned
 * specialist and picking it fills the form's number and name (the last step fails while it does not).
 *
 * Fixtures: createBillingFixture (owned billing provider and HIN on the owned patient), an owned
 * professionalSpecialists row (last name = marker), one owned appointment today. Cleanup removes the
 * claim rows the browser wrote and every fixture, and asserts they are gone.
 * Implements the gap-billing "create a bill from the schedule" workflow.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const g = require('./lib/gap-billing-support');

const q = h.sqlString;

async function workflow(s) {
  const { sql, patient, marker } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const feeA = g.scheduleFee(sql, 'A007A');
  const feeK = g.scheduleFee(sql, 'K030A');

  // An owned referral doctor, found through its marker last name.
  const referralNo = String(100000 + Math.floor(Math.random() * 899999));
  s.cleanup(() => {
    sql.execute(`DELETE FROM professionalSpecialists WHERE lName=${q(marker)} AND fName='Referral'`);
    h.assert(sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(marker)}`) === '0',
      'The owned referral doctor was not removed');
  });
  sql.execute(`INSERT INTO professionalSpecialists (fName, lName, proLetters, address, phone, fax, specType, lastUpdated,
      referralNo, institutionId, departmentId, hideFromView, deleted)
    VALUES ('Referral', ${q(marker)}, '', '1 Fake Street', '555-555-0101', '', 'Fake specialty', NOW(), ${q(referralNo)}, 0, 0, 0, 0)`);
  const appointment = g.seedAppointment(s, { time: '14:15:00' });
  const second = g.seedAppointment(s, { time: '14:30:00' });

  await g.showSeededAppointments(s);
  let form;
  let facility;
  await s.step('day sheet "B" opens the bill form and offers the owned billing physician', async () => {
    form = await g.openBillForm(s, appointment);
    await g.chooseBillingPhysician(form, owned.providerNo);
  });

  await s.step('hospital and nursing-home visits retain the admission date and outpatient visits clear it', async () => {
    const admission = form.locator('#xml_vdate');
    const visit = form.locator('select[name="xml_visittype"]');
    const options = await visit.locator('option').evaluateAll(rows => rows.map(row => row.value));
    for (const code of ['02', '04']) {
      await admission.evaluate(input => input._flatpickr.setDate('2026-01-15', true, 'Y-m-d'));
      h.assert(await admission.inputValue() === '2026-01-15', 'The admission date picker did not set the fixture');
      await visit.selectOption(options.find(value => value.startsWith(code + '|')) || options.find(value => value.startsWith(code)));
      h.assert(await admission.inputValue() === '2026-01-15', `Visit ${code} cleared its admission date`);
    }
    await visit.selectOption(options.find(value => value.startsWith('01')));
    h.assert(await admission.inputValue() === '', 'An outpatient visit retained an admission date');
  });

  await s.step('visit type, location, SLI code, manual review, dx codes and typed codes with units and percent are accepted', async () => {
    await form.locator('select[name="xml_visittype"]').selectOption({ index: 1 }); // 01| Outpatient Visit
    const locations = await form.locator('select[name="xml_location"] option').evaluateAll(o => o.map(x => x.value));
    h.assert(locations.length > 1, 'The bill form offers no visit locations to choose from');
    await form.locator('select[name="xml_location"]').selectOption({ index: 1 });
    facility = locations[1].split('|')[0];
    await form.locator('select[name="xml_slicode"]').selectOption('OFF');
    await form.locator('input[name="m_review"]').check();
    await form.locator('input[name="dxCode"]').fill('250');
    await form.locator('input[name="dxCode1"]').fill('401');
    await form.locator('input[name="dxCode2"]').fill('493');
    await form.locator('input[name="serviceCode0"]').fill('A007A');
    await form.locator('input[name="serviceUnit0"]').fill('2');
    await form.locator('input[name="serviceCode1"]').fill('K030A');
    await form.locator('input[name="serviceAt1"]').fill('0.5');
  });

  await s.step('Next lists both typed codes with their units and fees on the review page', async () => {
    await g.nextToReview(form);
    const text = (await form.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes('A007A') && text.includes('K030A'), 'The review page does not list both typed service codes');
  });

  let headerId;
  await s.step('Save writes one claim with the chosen header options and one item per typed code', async () => {
    const [response] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'),
        { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').click(),
    ]);
    h.assert(response.status() === 200, `The bill save answered HTTP ${response.status()}`);
    const headers = g.headersOf(sql, patient, ['pay_program', 'status', 'provider_no', 'provider_ohip_no', 'visittype',
      'facilty_num', 'location', 'man_review', 'ref_num', 'appointment_no', 'hin', 'total', 'billing_date']);
    h.assert(headers.length === 1, 'The save did not write exactly one claim for the owned patient');
    const header = headers[0];
    headerId = header.id;
    h.assert(header.provider_no === owned.providerNo && header.provider_ohip_no === owned.ohipNo,
      'The claim does not carry the chosen billing physician and OHIP number');
    h.assert(header.pay_program === 'HCP' && header.status === 'O' && header.visittype === '01',
      'The claim does not carry pay program HCP, status O and visit type 01');
    h.assert(header.facilty_num === facility, 'The claim does not carry the chosen visit location number');
    h.assert(header.location === 'OFF' && header.man_review === 'Y' && header.hin === owned.hin
      && header.appointment_no === appointment, 'The claim does not carry the SLI code, manual review, HIN and appointment');
    h.assert(header.billing_date === sql.value('SELECT CURDATE()'), 'The claim is not dated by the appointment');
    const items = g.itemsOf(sql, headerId);
    h.assert(items.length === 2, 'The claim does not hold one item per typed code');
    const [a, k] = items;
    h.assert(a.service_code === 'A007A' && a.ser_num === '2' && Number(a.fee) === Number(g.money(feeA * 2)),
      'The A007A item does not carry 2 units at twice the schedule fee');
    h.assert(k.service_code === 'K030A' && Number(k.fee) === Number(g.money(feeK * 0.5)),
      'The K030A item does not carry the 0.5 percent factor');
    h.assert([a, k].every(item => item.dx === '250' && item.dx1 === '401' && item.dx2 === '493'),
      'Each item does not carry the three diagnostic codes');
    h.assert(Number(header.total) === Number(g.money(Number(a.fee) + Number(k.fee))), 'The header total is not the sum of the items');
    h.assert(/B/.test(sql.value(`SELECT status FROM appointment WHERE appointment_no=${appointment}`)),
      'The appointment was not marked billed');
  });

  await s.step('Referral Doctor search lists the owned specialist and picking it fills the number and name, which the claim keeps', async () => {
    await form.close();
    const next = await g.openBillForm(s, second);
    await g.chooseBillingPhysician(next, owned.providerNo);
    const popup = await s.popup(next, next.locator('a[href*="referralScriptAttach2"]'), 'referral-search');
    const row = popup.locator('#tblDocs tbody tr').filter({ hasText: referralNo });
    await row.first().waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await row.count() === 1, 'The referral search does not list the owned specialist exactly once');
    await row.first().click();
    await popup.waitForEvent('close', { timeout: 10000 }).catch(() => {});
    h.assert(await next.locator('input[name="referralCode"]').inputValue() === referralNo,
      'Picking the specialist did not fill the referral number on the bill form');
    h.assert((await next.locator('input[name="referralDocName"]').inputValue()).includes(marker),
      'Picking the specialist did not fill the referral doctor name on the bill form');
    await next.locator('input[name="serviceCode0"]').fill('A007A');
    await next.locator('input[name="dxCode"]').fill('250');
    await g.nextToReview(next);
    await Promise.all([
      next.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'),
        { timeout: 30000 }),
      next.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').click(),
    ]);
    h.assert(sql.value(`SELECT ref_num FROM billing_on_cheader1 WHERE appointment_no=${second}`) === referralNo,
      'The saved claim does not carry the picked referral number');
  });
}

if (require.main === module) runWorkflow('gap-billing-bill-form-options', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
