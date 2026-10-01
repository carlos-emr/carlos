/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Shared helpers of the gap-billing-* Ontario billing workflow checks: an owned appointment that
 * puts the "B" link on the day sheet, the bill form opened through that link, the review page and
 * its Save buttons, and the cleanup of every claim the browser created for the owned patient.
 */
const h = require('./playwright-harness');

/**
 * An appointment today for the operator, owned by the run, so the day sheet offers its "B" link.
 * Cleanup is registered before the INSERT and asserts the row is gone. Returns the appointment id.
 */
function seedAppointment(s, { time = '14:15:00', tag = 'Billing' } = {}) {
  const { sql, patient, marker, provider } = s;
  const name = `${marker},Workflow`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointment WHERE demographic_no=${patient} AND name=${h.sqlString(name)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient}`) === '0',
      'The owned appointment was not removed');
  });
  const end = sql.value(`SELECT ADDTIME(${h.sqlString(time)}, '00:14:00')`);
  const id = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,
      updatedatetime, creator, remarks, urgency)
    VALUES (${h.sqlString(provider)}, CURDATE(), ${h.sqlString(time)}, ${h.sqlString(end)}, ${h.sqlString(name)}, ${patient},
      0, '', ${h.sqlString(`${tag} workflow`)}, '', '', '', '', '', 't', NOW(), NOW(), ${h.sqlString(provider)}, '', '');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'The appointment fixture was not created');
  return id;
}

/**
 * Remove every billing row the browser wrote for the owned patient or the owned billing provider (header, items, ext, payments,
 * transactions, audit snapshots), by id, and assert nothing is left. Register it AFTER the billing
 * fixture so it runs BEFORE the fixture removes the owned provider.
 */
function registerOwnedBillCleanup(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => {
    const ids = new Set(sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${patient}`).map(row => row[0]));
    // Bonus claims are saved without the patient: they are found through the run's owned billing provider,
    // which carries the run marker as its last name (the fixture's cleanup only knows claims with a marked note).
    for (const row of sql.rows(`SELECT c.id FROM billing_on_cheader1 c JOIN provider p ON p.provider_no=c.provider_no
        WHERE p.last_name=${h.sqlString(marker)}`)) ids.add(row[0]);
    for (const id of ids) removeBill(sql, id);
  });
}

function removeBill(sql, id) {
  h.assert(/^[1-9]\d*$/.test(id), 'Owned billing header id is invalid');
  const items = sql.rows(`SELECT id FROM billing_on_item WHERE ch1_id=${id}`).map(row => Number(row[0]));
  const itemIds = items.length ? items.join(',') : '0';
  sql.execute(`DELETE FROM billing_on_repo WHERE (category='billing_on_item' AND h_id IN (${itemIds}))
      OR (category='billing_on_cheader1' AND h_id=${id});
    DELETE FROM billing_on_proc WHERE object=${h.sqlString(id)};
    DELETE FROM billing_on_eareport WHERE billing_no=${id};
    DELETE FROM billing_on_transaction WHERE ch1_id=${id};
    DELETE FROM billing_on_item_payment WHERE ch1_id=${id};
    DELETE FROM billing_on_payment WHERE billing_no=${id};
    DELETE FROM billing_on_ext WHERE billing_no=${id};
    DELETE FROM billing_on_item WHERE ch1_id=${id};
    DELETE FROM billing_on_cheader1 WHERE id=${id}`);
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE id=${id})
    + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id=${id})
    + (SELECT COUNT(*) FROM billing_on_ext WHERE billing_no=${id})
    + (SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${id})
    + (SELECT COUNT(*) FROM billing_on_item_payment WHERE ch1_id=${id})
    + (SELECT COUNT(*) FROM billing_on_transaction WHERE ch1_id=${id})
    + (SELECT COUNT(*) FROM billing_on_repo WHERE category='billing_on_cheader1' AND h_id=${id})`) === '0',
  'Owned billing rows were not removed');
}

/**
 * Reload the day sheet once so it shows the appointments seeded after login. Seed every appointment a
 * workflow needs first and call this once: a reload that interrupts the schedule's own polling requests
 * is reported as an aborted request by the strict page recorder.
 */
async function showSeededAppointments(s) {
  await s.schedule.waitForLoadState('load').catch(() => {});
  await s.schedule.reload({ waitUntil: 'load' });
}

/** Day sheet "B" of the owned appointment: the Ontario bill form popup, ready for input. */
async function openBillForm(s, appointment) {
  const link = s.schedule.locator(`a[onclick*="appointment_no=${appointment}&"][onclick*="/billing?"]`).first();
  // A save popup that just closed makes the schedule refresh itself (BroadcastChannel): wait it out.
  await s.schedule.waitForLoadState('load').catch(() => {});
  await link.waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
  h.assert(await link.count() === 1, 'The day sheet does not offer the B link for the owned appointment');
  // The refresh that follows a save popup can reload the day sheet under the click: retry the click.
  let form;
  for (let attempt = 0; !form; attempt++) {
    try {
      form = await s.popup(s.schedule, link, 'bill-form');
    } catch (error) {
      if (attempt >= 2 || !/opened no popup|detached|Target closed|not attached/.test(error.message)) throw error;
      await s.schedule.waitForLoadState('load').catch(() => {});
      await link.waitFor({ state: 'attached', timeout: 20000 });
    }
  }
  await form.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
  h.assert(new URL(form.url()).searchParams.get('appointment_no') === appointment, 'The bill form opened for another appointment');
  return form;
}

/** Pick the owned billing physician ("provider_no|ohip_no" option value) in the bill form. */
async function chooseBillingPhysician(form, providerNo) {
  const select = form.locator('select[name="xml_provider"]');
  const values = await select.locator('option').evaluateAll(options => options.map(option => option.value));
  const wanted = values.find(value => value.split('|')[0] === providerNo);
  h.assert(wanted, 'The bill form does not offer the owned billing physician');
  await select.selectOption(wanted);
  return wanted;
}

/** Bill form ▸ Next: the review page, answered by ViewBillingONReview. */
async function nextToReview(form) {
  await Promise.all([
    form.waitForURL(/ViewBillingONReview/, { timeout: 30000 }),
    form.locator('#titlesearch input[type="submit"][name="submit"]').click(),
  ]);
  await form.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(form, 'bill review page');
}

/** The claim headers saved for the owned patient: [{id, ...columns}] ordered by id. */
function headersOf(sql, patient, columns) {
  const rows = sql.rows(`SELECT id, ${columns.join(', ')} FROM billing_on_cheader1
    WHERE demographic_no=${patient} ORDER BY id`);
  return rows.map(row => Object.fromEntries(['id', ...columns].map((name, i) => [name, row[i]])));
}

/** The items of one claim: [{service_code, ser_num, fee, dx, dx1, dx2, status}] ordered by id. */
function itemsOf(sql, headerId) {
  return sql.rows(`SELECT service_code, ser_num, fee, dx, dx1, dx2, status FROM billing_on_item
    WHERE ch1_id=${headerId} ORDER BY id`).map(row => ({
    service_code: row[0], ser_num: row[1], fee: row[2], dx: row[3], dx1: row[4], dx2: row[5], status: row[6],
  }));
}

/**
 * The latest schedule fee on or before a date (default today, in the database clock) for a code. Pass the
 * service date when the bill is dated otherwise: the form prices a code as of the service date.
 */
function scheduleFee(sql, code, date = null) {
  const bound = date ? h.sqlString(date) : 'CURDATE()';
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    AND billingservice_date<=${bound} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) throw new h.SkipCheck(`service code ${code} has no positive fee`);
  return fee;
}

const money = value => (Math.round(Number(value) * 100) / 100).toFixed(2);

module.exports = {
  seedAppointment, registerOwnedBillCleanup, removeBill, showSeededAppointments, openBillForm, chooseBillingPhysician,
  nextToReview, headersOf, itemsOf, scheduleFee, money,
};
