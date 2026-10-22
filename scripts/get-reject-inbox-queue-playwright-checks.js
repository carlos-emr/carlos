#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, document queues. User path: Schedule ▸ Administration ▸ Add New
 * Queue (admin/ViewAddQueue in #dynamic-content) ▸ type a name ▸ Add
 * (POST documentManager/inboxManage, method=addNewQueue).
 *
 * addNewQueue inserts a `queue` row AND a `secObjectName` row (_queue.<id>). Since #4428 it
 * requires POST and `_edoc` write, and answers 400 (not an NPE) for a blank name. The check adds
 * one marker-named queue through the page and asserts both rows, posts a blank name and asserts
 * 400 with no new rows, then replays the request as GET/HEAD with a second marker name (a name no
 * row can carry) and asserts 405 and no row in the LAST step. The read-only-user denial is covered
 * by DmsInboxManage2ActionUnitTest; it needs a second, restricted login this harness does not seed.
 *
 * Fixtures: queue / secObjectName rows whose name or description carries the run marker;
 * cleanup deletes only those and asserts they are gone.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger, takeKnownNoise } = require('./lib/get-reject-probe');

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
  let adminPage;

  await s.step('Administration ▸ Add New Queue ▸ Add creates the queue and its security object (POST method=addNewQueue)', async () => {
    const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });
    const link = admin.locator('a.contentLink[href$="/admin/ViewAddQueue"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.click();
    adminPage = admin;
    const input = admin.locator('#dynamic-content #newQueueName');
    await input.waitFor({ state: 'visible', timeout: 20000 });
    await input.fill(uiName);
    added = await captureRequest(admin, url => url.pathname.endsWith('/documentManager/inboxManage'),
      () => admin.locator('#dynamic-content #add-btn').click());
    h.assert(added.params.get('method') === 'addNewQueue', 'Add did not post method=addNewQueue');
    await expectValue(sql, rows(uiName), '1|1', 'Add did not create the queue and its _queue security object');
  });

  await s.step('a POST with a blank newQueueName answers 400 and creates no queue (was an NPE, #4428)', async () => {
    const countBefore = sql.value('SELECT COUNT(*) FROM queue');
    const secBefore = sql.value("SELECT COUNT(*) FROM secObjectName WHERE objectName LIKE '\\_queue.%'");
    // jQuery's POST carries the CSRF token the way the Add button does; only the name is blank.
    const status = await adminPage.evaluate(url => new Promise(resolve => {
      window.jQuery.ajax({ url, method: 'POST', data: { method: 'addNewQueue', newQueueName: '   ' }, dataType: 'json' })
        .always((a, b, c) => resolve((a && a.status) || (c && c.status) || 0));
    }), added.path);
    // The 400 is the behaviour under test; move exactly that response (and the browser's console echo of
    // it) out of the strict recorder instead of letting the harness read it as a page failure.
    const expected = takeKnownNoise(s.recorder, '"status":400').length + takeKnownNoise(s.recorder, 'status of 400').length;
    h.assert(expected > 0, 'The blank-name POST 400 was not observed by the recorder');
    h.assert(status === 400, `A blank newQueueName answered ${status}, expected 400`);
    h.assert(sql.value('SELECT COUNT(*) FROM queue') === countBefore, 'A blank-name POST created a queue row');
    h.assert(sql.value("SELECT COUNT(*) FROM secObjectName WHERE objectName LIKE '\\_queue.%'") === secBefore,
      'A blank-name POST created a _queue security object');
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
