#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Provider-wide task list (tickler/ViewTicklerMain) status lifecycle, issue #4993:
 *   1. The footer follows the status filter in place. Active and Completed offer Complete and
 *      Delete; Deleted offers Erase Completely instead. The filter only reloads the DataTable,
 *      so the page cannot choose the buttons at render time.
 *   2. Reactivating a Completed task from its row pencil (tickler/ViewTicklerEdit popup) refreshes
 *      this list on its own: the popup broadcasts on the shared 'carlos_tickler_refresh_all'
 *      channel and the list reloads (a ListTicklers request) without Create Report.
 *   3. Erase Completely removes only the ticked task from the Deleted queue (the row is gone, the
 *      status-change history in tickler_update is kept) and leaves an unticked deleted task alone.
 * Fixtures: the synthetic patient from lib/workflow-session.js and four ticklers carrying the run
 * marker; every tickler row, comment, link, attachment and update row is removed and verified gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const FORM = 'form[name="ticklerform"]';
// The buttons are told apart by what they post, not by their (translated) labels.
const BUTTON = {
  complete: `${FORM} input[type="button"][onclick*="submit_form.value='Complete'"]`,
  delete: `${FORM} input[type="button"][onclick*="submit_form.value='Delete'"]`,
  erase: `${FORM} input[type="button"][onclick*="submit_form.value='Erase Completely'"]`,
};

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const owned = `SELECT tickler_no FROM tickler WHERE demographic_no=${patient}`;
  const ids = [];
  s.cleanup(() => {
    // tickler_update rows outlive an erased tickler by design, so they are found by id, not by join.
    const idList = ids.length ? ids.join(',') : '0';
    sql.execute([
      `DELETE FROM tickler_update WHERE tickler_no IN (${idList}) OR tickler_no IN (${owned})`,
      `DELETE FROM tickler_comments WHERE tickler_no IN (${idList}) OR tickler_no IN (${owned})`,
      `DELETE FROM tickler_link WHERE tickler_no IN (${idList}) OR tickler_no IN (${owned})`,
      `DELETE FROM ticklerdocs WHERE tickler_id IN (${idList}) OR tickler_id IN (${owned})`,
      `DELETE FROM tickler WHERE demographic_no=${patient}`,
    ].join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM tickler_update WHERE tickler_no IN (${idList}))
      + (SELECT COUNT(*) FROM tickler_comments WHERE tickler_no IN (${idList}))`) === '0',
    'Owned tickler rows were not removed');
  });

  const tickler = {};
  for (const label of ['first', 'second', 'third', 'fourth']) {
    tickler[label] = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${patient},${h.sqlString(`${marker} ${label} task`)},'A',NOW(),CURDATE(),${h.sqlString(provider)},'Normal',${h.sqlString(provider)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(tickler[label]), `The ${label} tickler fixture was not created`);
    ids.push(tickler[label]);
  }
  const statuses = () => ['first', 'second', 'third', 'fourth']
    .map(label => sql.value(`SELECT COALESCE((SELECT status FROM tickler WHERE tickler_no=${tickler[label]}),'-')`)).join('');
  const updateRows = label => sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${tickler[label]}`);

  const list = await s.context.newPage();

  /** Waits for the DataTable's current draw to finish. */
  async function settled() {
    await list.waitForFunction(() => {
      const table = window.jQuery && window.jQuery('#ticklerResults').DataTable();
      if (!table) return false;
      const settings = table.settings()[0];
      return !settings.bDrawing && (!settings.jqXHR || settings.jqXHR.readyState === 4);
    }, null, { timeout: TIMEOUT });
  }
  /** Narrows the list to this run's tasks through the DataTable search box. */
  async function showOnlyOwned() {
    const draw = await list.evaluate(() => window.jQuery('#ticklerResults').DataTable().settings()[0].iDraw);
    await list.locator('#ticklerResults_filter input[type="search"]').fill(marker);
    await list.evaluate(needle => window.jQuery('#ticklerResults').DataTable().search(needle).draw(), marker);
    await list.waitForFunction(previous => window.jQuery('#ticklerResults').DataTable().settings()[0].iDraw > previous, draw, { timeout: TIMEOUT });
    await settled();
  }
  async function openList() {
    await list.goto(h.appUrl(s.config.baseUrl, '/tickler/ViewTicklerMain'), { waitUntil: 'domcontentloaded' });
    await h.assertNotErrorPage(list, 'the task list');
    await settled();
    await showOnlyOwned();
  }
  async function filter(status) {
    const [response] = await Promise.all([
      list.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname.endsWith('/tickler/ListTicklers')
        && new URL(r.url()).searchParams.get('status') === status, { timeout: TIMEOUT }),
      list.locator('#ticklerview').selectOption(status),
    ]);
    h.assert(response.status() < 400, `The ${status} list reload returned HTTP ${response.status()}`);
    await settled();
  }
  const box = label => list.locator(`${FORM} input[name="checkbox"][value="${tickler[label]}"]`);
  async function lists(expected) {
    const found = {};
    for (const label of Object.keys(tickler)) found[label] = await box(label).count() === 1;
    h.assert(JSON.stringify(found) === JSON.stringify(expected),
      `The list shows ${JSON.stringify(found)}; expected ${JSON.stringify(expected)}`);
  }
  async function offers(expected) {
    const found = {};
    for (const button of Object.keys(BUTTON)) found[button] = await list.locator(BUTTON[button]).isVisible();
    h.assert(JSON.stringify(found) === JSON.stringify(expected),
      `The footer offers ${JSON.stringify(found)}; expected ${JSON.stringify(expected)}`);
  }
  async function press(button) {
    await Promise.all([
      list.waitForURL(/\/tickler\/ViewTicklerMain/, { timeout: TIMEOUT }),
      list.locator(BUTTON[button]).click(),
    ]);
    await list.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(list, `the task list after ${button}`);
    h.assert(!new URL(list.url()).searchParams.has('failCount'), `${button} reported tasks it could not change`);
    await settled();
    await showOnlyOwned();
  }

  await s.step('the Active view lists the four owned tasks and offers Complete and Delete', async () => {
    await openList();
    await lists({ first: true, second: true, third: true, fourth: true });
    await offers({ complete: true, delete: true, erase: false });
  });

  await s.step('Complete with two tasks ticked completes only those', async () => {
    await box('first').check();
    await box('second').check();
    await press('complete');
    h.assert(statuses() === 'CCAA', `Complete left first..fourth as ${statuses()}`);
  });

  await s.step('the Completed view lists them and a popup reactivation refreshes this list on its own', async () => {
    await filter('C');
    await lists({ first: true, second: true, third: false, fourth: false });
    await offers({ complete: true, delete: true, erase: false });

    const edit = await s.popup(list, list.locator(`#ticklerResults tbody tr:has(input[value="${tickler.first}"]) a[onclick*="openTicklerEdit"]`), 'tickler-edit');
    await edit.waitForLoadState('domcontentloaded');
    await edit.locator('#status').selectOption('A');
    // The refresh must come from the popup's broadcast; Create Report is never pressed.
    const reload = list.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname.endsWith('/tickler/ListTicklers')
      && new URL(r.url()).searchParams.get('status') === 'C', { timeout: TIMEOUT });
    await edit.locator('input[name="updateTickler"]').click();
    const response = await reload;
    h.assert(response.status() < 400, `The opener refresh returned HTTP ${response.status()}`);
    await settled();
    await expectValue(sql, `SELECT status FROM tickler WHERE tickler_no=${tickler.first}`, 'A', 'The popup edit did not reactivate the task');
    await list.locator(`${FORM} input[name="checkbox"][value="${tickler.first}"]`).waitFor({ state: 'detached', timeout: TIMEOUT });
    await lists({ first: false, second: true, third: false, fourth: false });
  });

  await s.step('Delete marks the ticked tasks deleted', async () => {
    await filter('A');
    await lists({ first: true, second: false, third: true, fourth: true });
    await box('third').check();
    await box('fourth').check();
    await press('delete');
    h.assert(statuses() === 'ACDD', `Delete left first..fourth as ${statuses()}`);
  });

  await s.step('the footer follows the filter in place: Deleted offers Erase Completely only, Active gets Complete and Delete back', async () => {
    await filter('D');
    await lists({ first: false, second: false, third: true, fourth: true });
    await offers({ complete: false, delete: false, erase: true });
    await filter('A');
    await offers({ complete: true, delete: true, erase: false });
    await filter('D');
    await offers({ complete: false, delete: false, erase: true });
  });

  await s.step('Erase Completely removes only the ticked task from Deleted and keeps the unticked one', async () => {
    const history = updateRows('third');
    await box('third').check();
    await press('erase');
    h.assert(statuses() === 'AC-D', `Erase left first..fourth as ${statuses()} (a - is an erased task)`);
    h.assert(updateRows('third') === history && history !== '0', 'Erase did not keep the status history of the erased task');
    await filter('D');
    await lists({ first: false, second: false, third: false, fourth: true });
  });
}

if (require.main === module) runWorkflow('tickler-main-list-lifecycle', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
