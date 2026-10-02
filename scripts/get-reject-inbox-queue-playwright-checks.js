#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, document queues. User path: Schedule ▸ Administration ▸ Add New
 * Queue (admin/ViewAddQueue in #dynamic-content) ▸ type a name ▸ Add
 * (POST documentManager/inboxManage, method=addNewQueue).
 *
 * DmsInboxManage2Action guards only method=updateDocStatusInQueue with a POST check;
 * addNewQueue inserts a `queue` row AND a `secObjectName` row (_queue.<id>) after checking
 * only _edoc READ, on any verb. "inboxManage" has no mutator prefix and "addNewQueue" is not
 * in HttpMethodGuardFilter's exact method list; the class is outside the GET-rejection
 * contract. The check adds one marker-named queue through the page, asserts both rows, then
 * replays the request as GET/HEAD with a second marker name (a name no row can carry) and
 * asserts 405 and no row in the LAST step.
 *
 * Fixtures: queue / secObjectName rows whose name or description carries the run marker;
 * cleanup deletes only those and asserts they are gone.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');

const NAME = 'get-reject-inbox-queue';

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const uiName = `Q${marker.slice(-16)}A`;
  const probeName = `Q${marker.slice(-16)}G`;
  const names = `${q(uiName)},${q(probeName)}`;
  const rows = name => `SELECT CONCAT((SELECT COUNT(*) FROM queue WHERE name=${q(name)}),'|',
    (SELECT COUNT(*) FROM secObjectName WHERE description=${q(name)} AND objectName LIKE '\\_queue.%'))`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM secObjectName WHERE description IN (${names}) AND objectName LIKE '\\_queue.%';
      DELETE FROM queue WHERE name IN (${names})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM queue WHERE name IN (${names}))
      + (SELECT COUNT(*) FROM secObjectName WHERE description IN (${names}))`) === '0', 'Owned queue rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM queue WHERE name IN (${names})`) === '0', 'The marker queue names already exist');
  let added;

  await s.step('Administration ▸ Add New Queue ▸ Add creates the queue and its security object (POST method=addNewQueue)', async () => {
    const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });
    const link = admin.locator('a.contentLink[href$="/admin/ViewAddQueue"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const input = admin.locator('#dynamic-content #newQueueName');
    await input.waitFor({ state: 'visible', timeout: 20000 });
    await input.fill(uiName);
    added = await captureRequest(admin, url => url.pathname.endsWith('/documentManager/inboxManage'),
      () => admin.locator('#dynamic-content #add-btn').click());
    h.assert(added.params.get('method') === 'addNewQueue', 'Add did not post method=addNewQueue');
    await expectValue(sql, rows(uiName), '1|1', 'Add did not create the queue and its _queue security object');
  });

  await s.step('the addNewQueue replayed as GET/HEAD with a new marker name is recorded', async () => {
    await ledger.probe(s, { label: 'documentManager/inboxManage?method=addNewQueue', path: added.path,
      params: replayParams(added.params, { newQueueName: probeName }), snapshot: () => sql.value(rows(probeName)) });
  });

  await s.step('queue creation refused GET/HEAD and no queue carries the replayed name', async () => {
    ledger.assertAllRefused();
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
