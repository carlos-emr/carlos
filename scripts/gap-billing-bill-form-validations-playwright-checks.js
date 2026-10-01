#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario bill entry validations and the review page's fee edit.
 *
 * User path: Schedule ▸ owned appointment "B" (bill form) ▸ Next with an empty form / no
 * diagnostic code (the form's alert and confirm) ▸ a valid code, Next, edit the line fee on the
 * review page ▸ Save (BillingONSave) ▸ an unknown and a duplicated service code (the review page's
 * warnings) ▸ a malformed percent.
 *
 * Asserts: Next with nothing selected alerts "You haven't selected any billing item yet!" and stays
 * on the form; an empty Dx asks "Continue?" and Cancel stays on the form with the Dx box focused
 * while OK goes on to the review page, whose Back to Edit writes nothing; a fee edited on the review
 * page is saved as the item fee and the claim total (fee override), the schedule fee being left
 * alone. The LAST step fails today on two defects: the review page of an unknown or duplicated
 * service code marks it and offers no Save but throws a script error on load and on Back to Edit
 * (it reads a total box rendered only for valid codes), and a malformed percent (abc) is not stopped
 * by the form's "enter a decimal number" alert because that check selects inputs by an id the
 * percent boxes do not have.
 *
 * Fixtures: createBillingFixture (owned billing provider and HIN on the owned patient), owned
 * appointments today. Cleanup removes the claim rows the browser wrote and every fixture, and
 * asserts they are gone.
 * Implements the gap-billing "bill form validations" workflow.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const g = require('./lib/gap-billing-support');

async function workflow(s) {
  const { sql, patient } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const fee = g.scheduleFee(sql, 'A007A');
  const appointment = g.seedAppointment(s, { time: '16:00:00' });
  const second = g.seedAppointment(s, { time: '16:15:00' });
  const third = g.seedAppointment(s, { time: '16:30:00' });
  await g.showSeededAppointments(s);
  const claims = () => sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`);
  const onForm = form => !/ViewBillingONReview|BillingONSave/.test(form.url());
  let form;

  await s.step('Next with nothing selected and an empty Dx are stopped by the form', async () => {
    form = await g.openBillForm(s, appointment);
    await g.chooseBillingPhysician(form, owned.providerNo);
    const next = form.locator('#titlesearch input[type="submit"][name="submit"]');

    let dialogs = await h.withExpectedDialogs(form, () => next.click());
    h.assert(dialogs.length === 1 && /haven't selected any billing item/.test(dialogs[0].text),
      'Next with no service code did not alert that no billing item is selected');
    h.assert(onForm(form), 'Next with no service code left the bill form');

    await form.locator('input[name="serviceCode0"]').fill('A007A');

    // Empty Dx: Cancel stays on the form.
    dialogs = await h.withExpectedDialogs(form, () => next.click(), { accept: false });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && /diagnostic code/.test(dialogs[0].text),
      'An empty Dx did not ask for confirmation');
    h.assert(onForm(form) && await form.locator('input[name="dxCode"]').evaluate(el => el === document.activeElement),
      'Cancelling the empty-Dx confirmation did not stay on the form with the Dx box focused');
    h.assert(claims() === '0', 'A stopped Next wrote a claim');
  });

  await s.step('an empty Dx accepted goes on to the review page, which Back to Edit leaves without a claim', async () => {
    const next = form.locator('#titlesearch input[type="submit"][name="submit"]');
    await h.withExpectedDialogs(form, async () => {
      await Promise.all([form.waitForURL(/ViewBillingONReview/, { timeout: 30000 }), next.click()]);
    });
    await h.assertNotErrorPage(form, 'review page');
    h.assert((await form.locator('body').innerText()).includes('A007A'), 'The review page does not list the typed code');
    await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'), { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[value="Back to Edit"]').click(),
    ]);
    await form.locator('input[name="serviceCode0"]').waitFor({ state: 'visible', timeout: 30000 });
    h.assert(claims() === '0', 'Back to Edit wrote a claim');
    await form.close();
  });

  // Last: the review page of an invalid or duplicated code throws a script error on load, and the
  // percent box check is dead code; both are judged together so each is reported.
  await s.step('the review page marks an unknown and a duplicated code, and a malformed percent is stopped', async () => {
    const problems = [];
    form = await g.openBillForm(s, second);
    await g.chooseBillingPhysician(form, owned.providerNo);
    await form.locator('input[name="serviceCode0"]').fill('ZZZ99');
    await form.locator('input[name="dxCode"]').fill('250');
    await g.nextToReview(form);
    let body = (await form.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(body.includes('ZZZ99'), 'The review page does not mention the unknown code');
    h.assert(await form.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').count() === 0,
      'The review page offers Save for an unknown service code');
    await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'), { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[value="Back to Edit"]').click(),
    ]);
    await form.locator('input[name="serviceCode0"]').waitFor({ state: 'visible', timeout: 30000 });
    await form.locator('input[name="serviceCode0"]').fill('A007A');
    await form.locator('input[name="serviceCode1"]').fill('A007A');
    await g.nextToReview(form);
    body = (await form.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(/Duplicate service codes/.test(body), 'The review page does not warn about the duplicated service code');
    h.assert(await form.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').count() === 0,
      'The review page offers Save for a duplicated service code');
    h.assert(claims() === '0', 'A rejected review wrote a claim');
    await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'), { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[value="Back to Edit"]').click(),
    ]);
    await form.locator('input[name="serviceCode0"]').waitFor({ state: 'visible', timeout: 30000 });
    // A malformed percent: the form should stop it with its decimal-number alert.
    await form.locator('input[name="serviceCode0"]').fill('A007A');
    await form.locator('input[name="serviceCode1"]').fill('');
    await form.locator('input[name="serviceAt0"]').fill('abc');
    await form.locator('input[name="dxCode"]').fill('250');
    let dialogs = [];
    dialogs = await h.withExpectedDialogs(form, async () => {
      await form.locator('#titlesearch input[type="submit"][name="submit"]').click();
      await form.waitForLoadState('domcontentloaded');
    });
    if (!(dialogs.length === 1 && /decimal number in the service code percent/.test(dialogs[0].text) && onForm(form))) {
      problems.push('a malformed percent (abc) was not stopped by the decimal-number alert');
    }
    h.assert(claims() === '0', 'A rejected review or a malformed percent wrote a claim');
    await form.close();

    // A fee edited on the review page is saved as the item fee and the claim total.
    form = await g.openBillForm(s, third);
    await g.chooseBillingPhysician(form, owned.providerNo);
    await form.locator('input[name="serviceCode0"]').fill('A007A');
    await form.locator('input[name="dxCode"]').fill('250');
    await g.nextToReview(form);
    const line = form.locator('input[id^="percCodeSubtotal_"]').first();
    h.assert(Number(await line.inputValue()) === Number(fee), 'The review line does not start at the schedule fee');
    await line.fill('12.34');
    await line.dispatchEvent('change');
    await line.blur();
    await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'), { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').click(),
    ]);
    const headers = g.headersOf(sql, patient, ['total', 'status']);
    h.assert(headers.length === 1, 'The save did not write exactly one claim');
    const items = g.itemsOf(sql, headers[0].id);
    h.assert(items.length === 1 && Number(items[0].fee) === 12.34 && Number(headers[0].total) === 12.34,
      'The edited fee was not saved as the item fee and the claim total');
    h.assert(sql.value("SELECT value FROM billingservice WHERE service_code='A007A' ORDER BY billingservice_date DESC LIMIT 1") === fee,
      'Editing a line fee changed the schedule of benefits');
    h.assert(!problems.length, problems.join('; '));
  });
}

if (require.main === module) runWorkflow('gap-billing-bill-form-validations', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
