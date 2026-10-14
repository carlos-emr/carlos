#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* A malformed configured fee must block every private-bill save/print action. */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const g = require('./lib/gap-billing-support');

async function workflow(s) {
  const { sql, patient, marker } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const code = `_P${require('node:crypto').randomBytes(4).toString('hex').toUpperCase()}`;
  h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(code)}`) === '0',
    'The owned fee code collided with an existing code');
  s.cleanup(() => {
    sql.execute(`DELETE FROM billingservice WHERE service_code=${h.sqlString(code)} AND description=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${h.sqlString(code)}`) === '0',
      'The owned malformed fee was not removed');
  });
  sql.execute(`INSERT INTO billingservice (service_compositecode,service_code,description,value,percentage,
    billingservice_date,specialty,region,anaesthesia,termination_date,sliFlag,gstFlag)
    VALUES ('',${h.sqlString(code)},${h.sqlString(marker)},'bad-fee','','2020-01-01','','ON','00','9999-12-31',0,0)`);
  const appointment = g.seedAppointment(s, { time: '17:00:00' });
  await g.showSeededAppointments(s);
  await s.step('a failed private fee calculation blocks every save/print action but permits Back to Edit', async () => {
    const form = await g.openBillForm(s, appointment);
    const select = form.locator('select[name="xml_billtype"]');
    const value = await select.locator('option').evaluateAll(options => options.find(option => option.value.startsWith('PAT')).value);
    await Promise.all([form.waitForNavigation({ waitUntil: 'domcontentloaded' }), select.selectOption(value)]);
    await g.chooseBillingPhysician(form, owned.providerNo);
    await form.locator('input[name="serviceCode0"]').fill(code);
    await form.locator('input[name="dxCode"]').fill('250');
    await g.nextToReview(form);
    h.assert((await form.locator('body').innerText()).includes('Invalid service fee, units or percent'),
      'The failed calculation did not display its warning');
    const saveActions = await form.locator('input[type="submit"], input[type="button"]').evaluateAll(inputs =>
      inputs.filter(input => /^(Save|Settle)/.test(input.value)).map(input => input.value));
    h.assert(saveActions.length === 0, `A failed calculation still offers ${saveActions.join(', ')}`);
    h.assert(await form.locator('#settlePrintBtn').count() === 0, 'A failed calculation still offers Settle & Print');
    await Promise.all([
      form.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/billing/CA/ON/BillingONSave')),
      form.locator('input[type="submit"][value="Back to Edit"]').click(),
    ]);
    await form.locator('input[name="serviceCode0"]').waitFor({ state: 'visible' });
    h.assert(await form.locator('input[name="serviceCode0"]').inputValue() === code, 'Back to Edit lost the failed fee code');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`) === '0',
      'A failed calculation wrote a claim');
  });
}
if (require.main === module) runWorkflow('billing-review-failed-calculation', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
