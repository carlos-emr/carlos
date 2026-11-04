#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule > Search > Master Record > E-Chart > RH Form > Save;
// Schedule > WorkFlow > the patient's RH link > Change State > Closed > Save.
// Requires WORKFLOW=yes and an exclusive disposable install (optional form registration).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareRhWorkflow } = require('./lib/rh-ui-workflow');

async function workflow(s) {
  const mode = process.env.RH_QUEUE_ONLY || 'all';
  h.assert(['all', 'link', 'lifecycle'].includes(mode), 'RH_QUEUE_ONLY must be all, link or lifecycle');
  const entry = s.schedule.getByRole('link', { name: /^\s*WorkFlow\s*$/i });
  if (!await entry.count()) throw new h.SkipCheck('Enable WORKFLOW=yes and restart CARLOS to test the WorkFlow queue');
  let rh;
  let queue;
  const row = () => queue.locator('#results_table tbody tr').filter({ hasText: s.marker });
  await s.step('a saved pregnancy appears once in the WorkFlow queue with its patient, due date and state', async () => {
    rh = await prepareRhWorkflow(s);
    queue = await s.popup(s.schedule, entry, 'rh-workflow-queue');
    await row().waitFor();
    h.assert(await row().count() === 1, 'The new pregnancy is missing or duplicated in the queue');
    h.assert((await row().innerText()).includes(rh.edd) && (await row().innerText()).includes('No Appt made'),
      'The queue lost the stored due date or initial state');
  });
  if (mode !== 'lifecycle') await s.step('the queue RH link reopens the saved form for that pregnancy', async () => {
    const reopened = await s.popup(queue, row().getByRole('link', { name: 'RH', exact: true }), 'rh-from-queue');
    h.assert(new URL(reopened.url()).pathname.startsWith(`${s.config.baseUrl.pathname}/form/`),
      `The RH queue link navigated outside its form route: ${h.pathOnly(reopened.url())}`);
    await reopened.locator('[name="formId"]').waitFor({ state: 'attached' });
    // The injection panel's delete form, later on the page, carries its own demographic_no. The RH
    // form's fields cannot be scoped by the form element: its table markup ends it early in the DOM.
    h.assert(await reopened.locator('[name="demographic_no"]').first().inputValue() === s.patient
      && await reopened.locator('[name="workflowId"]').inputValue() === rh.workflowId,
    'The queue link opened a different patient or pregnancy');
    h.assert(await reopened.locator('[name="comments"]').inputValue() === `${s.marker} first pregnancy`,
      'The queue link did not restore the saved form');
    await rh.page.close(); rh.page = reopened;
  });
  if (mode === 'link') return;
  await s.step('closing a pregnancy removes it from the active queue without deleting its saved form', async () => {
    await rh.page.locator('[name="state"]').selectOption('C');
    await rh.save(rh.page);
    h.assert(s.sql.value(`SELECT current_state FROM workflow WHERE ID=${rh.workflowId}`) === 'C', 'Closed state was not stored');
    await queue.reload({ waitUntil: 'domcontentloaded' });
    h.assert(await row().count() === 0, 'A closed pregnancy remains in the active queue');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient}
      AND workflowId=${rh.workflowId} AND comments=${h.sqlString(`${s.marker} first pregnancy`)}`) !== '0',
    'Closing the pregnancy erased its saved form');
  });
  await s.step('a later pregnancy creates a separate active workflow and preserves the closed one', async () => {
    await rh.page.close();
    const page = await rh.openNew();
    await page.locator('[name="edd"]').fill(s.sql.value('SELECT DATE_ADD(CURDATE(),INTERVAL 365 DAY)'));
    await page.locator('[name="comments"]').fill(`${s.marker} later pregnancy`);
    await rh.save(page);
    const later = rh.latest();
    h.assert(later[1] !== rh.workflowId && /^[1-9]\d*$/.test(later[1]), 'The later pregnancy reused the closed workflow');
    h.assert(s.sql.value(`SELECT current_state FROM workflow WHERE ID=${rh.workflowId}`) === 'C', 'The closed pregnancy was reopened');
    await queue.reload({ waitUntil: 'domcontentloaded' });
    await row().waitFor();
    h.assert(await row().count() === 1, 'The active queue does not contain exactly the later pregnancy');
  });
}
if (require.main === module) runWorkflow(process.env.RH_QUEUE_ONLY === 'lifecycle' ? 'rh-workflow-queue-lifecycle' : 'rh-workflow-queue', workflow);
module.exports = { workflow };
