#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const name = `${s.marker.slice(0, 16)} & 'p'`;
  const edited = `${s.marker.slice(0, 16)} & 'e'`;
  let id;
  s.cleanup(() => {
    if (!id) id = s.sql.value(`SELECT id FROM billing_payment_type WHERE payment_type IN (${h.sqlString(name)},${h.sqlString(edited)})`);
    if (id) {
      h.assert(/^[1-9]\d*$/.test(id), 'Payment fixture identifier is invalid');
      s.sql.execute(`DELETE FROM billing_payment_type WHERE id=${id} AND payment_type IN (${h.sqlString(name)},${h.sqlString(edited)})`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM billing_payment_type WHERE id=${id}`) === '0', 'Payment fixture cleanup failed');
    }
  });
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'payment-admin' });
  const link = admin.locator('a[rel$="/billing/CA/ON/managePaymentType"], a[href$="/billing/CA/ON/managePaymentType"]');
  await link.first().waitFor({ state: 'attached' });
  const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
  if (await panel.count() && !await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
  await link.first().click();
  const page = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
  h.assert(page, 'Payment management did not open its iframe');
  const row = value => page.locator('#tblBillType tbody tr').filter({ hasText: value });
  await s.step('empty payment type is rejected without a database write', async () => {
    await page.getByRole('link', { name: 'Create a new payment type' }).click();
    await page.locator('#paymentType').fill('');
    const dialogs = await h.withExpectedDialogs(admin, () => page.locator('[name="create"]').click());
    h.assert(dialogs.length === 1 && /can not be empty/.test(dialogs[0].text), 'Empty payment type did not show its validation alert');
  });
  await s.step('create a literal payment type and verify it in the list and database', async () => {
    await page.locator('#paymentType').fill(name);
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      await page.locator('[name="create"]').click();
      await expectValue(s.sql, `SELECT COUNT(*) FROM billing_payment_type WHERE payment_type=${h.sqlString(name)}`, '1', 'Payment type creation did not persist');
      await row(name).waitFor();
    });
    h.assert(dialogs.length === 1 && /Success/.test(dialogs[0].text), 'Payment create did not report success');
    id = s.sql.value(`SELECT id FROM billing_payment_type WHERE payment_type=${h.sqlString(name)}`);
  });
  await s.step('edit and reopen the exact owned payment type', async () => {
    await row(name).getByRole('link', { name: 'Edit', exact: true }).click();
    h.assert(await page.locator('#paymentType').inputValue() === name, 'Payment editor did not preserve literal input');
    await page.locator('#paymentType').fill(edited);
    await h.withExpectedDialogs(admin, async () => {
      await page.locator('[name="save"]').click();
      await expectValue(s.sql, `SELECT payment_type FROM billing_payment_type WHERE id=${id}`, edited, 'Payment edit did not persist');
      await row(edited).waitFor();
    });
    await row(edited).getByRole('link', { name: 'Edit', exact: true }).click();
    h.assert(await page.locator('#paymentType').inputValue() === edited, 'Reopened payment type lost its edit');
    await page.locator('[name="back"]').click();
  });
  await s.step('delete the owned payment type through its list action', async () => {
    await h.withExpectedDialogs(admin, async () => {
      await row(edited).getByRole('link', { name: 'Delete', exact: true }).click();
      await expectValue(s.sql, `SELECT COUNT(*) FROM billing_payment_type WHERE id=${id}`, '0', 'Payment delete did not persist');
      await row(edited).waitFor({ state: 'hidden' });
    });
  });
}
if (require.main === module) runWorkflow('billing-payment-types', workflow, { openPatient: false });
module.exports = { workflow };
