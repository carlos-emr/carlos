#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4992, the Rh immune globulin (RH) workflow around the RH form:
//   284  the WorkFlow queue's RH link left the application (../form/... against a context-root base) and named a
//        Forms-menu shortcut no shipped install registers;
//   285  the RH form's Add Injection button opened addRhInjection without form/ (the error page);
//   287  the injection editor's Save handler ended with an undefined name, a script error on every Save;
//   288  the RH form's injection panel stayed empty: the lookup passed the prevention entity's toString() as its id.
// The panel prints what was recorded for each injection, so once it shows again its values must be encoded and only
// the chart's own patient's injections listed. Finding 290 (the entity written to the error log) is covered by
// PreventionDataExtLookupUnitTest.
//
// User path: a saved RH form opened for the owned patient (form/formRhImmuneGlobulin?demographic_no=&formId=), its
// panel read, Add Injection pressed and the editor saved, the panel's Delete used on one injection, and the queue
// (oscarWorkflow/WorkFlowList) row's link followed.
//
// Fixtures: the owned synthetic patient; one active RH workflow, one saved RH form bound to it and one RH prevention
// linked to both, whose lot carries inert markup; the injection the editor saves. Cleanup deletes the patient's
// preventions and their extensions, RH forms and workflows, and asserts them gone.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const NAME = 'rh-workflow-form-links';

function rows(sql, query) {
  return Number(sql.value(query));
}

async function workflow(s) {
  const { sql, patient, provider, context, marker } = s;
  h.assert(/^[1-9]\d*$/.test(patient), 'The owned synthetic patient was not created');
  const extensions = `SELECT id FROM preventions WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM preventionsExt WHERE prevention_id IN (SELECT id FROM (${extensions}) owned);
      DELETE FROM preventions WHERE demographic_no=${patient};
      DELETE FROM formRhImmuneGlobulin WHERE demographic_no=${patient};
      DELETE FROM workflow WHERE demographic_no=${patient}`);
    h.assert(rows(sql, `SELECT (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM formRhImmuneGlobulin WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM workflow WHERE demographic_no=${patient})`) === 0, "The owned patient's RH rows were not removed");
  });

  const seededLot = `${marker}<b data-rh-xp>lot</b>`;
  const savedLot = `${marker}-saved`;
  let workflowId;
  let formId;
  let seededInjection;
  await s.step('RH: seed an active workflow, its saved RH form and one linked injection', async () => {
    workflowId = sql.value(`INSERT INTO workflow (workflow_type,provider_no,demographic_no,completion_date,current_state,create_date_time)
      VALUES ('RH',${h.sqlString(provider)},${patient},DATE_ADD(CURDATE(), INTERVAL 60 DAY),'1',NOW()); SELECT LAST_INSERT_ID()`);
    formId = sql.value(`INSERT INTO formRhImmuneGlobulin (demographic_no,provider_no,formCreated,workflowId,edd,motherSurname,motherFirstname)
      VALUES (${patient},${h.sqlString(provider)},CURDATE(),${workflowId},DATE_ADD(CURDATE(), INTERVAL 60 DAY),${h.sqlString(marker)},'Workflow');
      SELECT LAST_INSERT_ID()`);
    seededInjection = sql.value(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,deleted,refused,never,creator,lastUpdateDate)
      VALUES (${patient},NOW(),NOW(),${h.sqlString(provider)},'RH','0','0','0',${h.sqlString(provider)},NOW()); SELECT LAST_INSERT_ID()`);
    const ext = { workflowId, formId, lot: seededLot, product: 'FAKE WinRho', dosage: '300', location: 'FAKE clinic', reason: 'Antepartum' };
    sql.execute(Object.entries(ext).map(([key, value]) =>
      `INSERT INTO preventionsExt (prevention_id,keyval,val) VALUES (${seededInjection},${h.sqlString(key)},${h.sqlString(String(value))})`).join(';'));
    h.assert([workflowId, formId, seededInjection].every(id => /^[1-9]\d*$/.test(id)), 'The RH fixtures were not stored');
  });

  const page = await context.newPage();
  try {
    await s.step('RH: the saved form lists its injection with the recorded values, as text', async () => {
      const response = await h.gotoApp(page, s.config.baseUrl, `/form/formRhImmuneGlobulin?demographic_no=${patient}&formId=${formId}`);
      h.assert(response && response.status() === 200, `The saved RH form answered HTTP ${response && response.status()}`);
      await h.assertNotErrorPage(page, 'RH form');
      const panel = page.locator('#injectionInfo');
      await panel.locator('fieldset').first().waitFor({ state: 'visible', timeout: 20000 });
      const text = await panel.innerText();
      for (const value of [seededLot, 'FAKE WinRho', 'FAKE clinic', 'Antepartum']) {
        h.assert(text.includes(value), `The injection panel does not show ${value === seededLot ? 'the recorded lot' : value}`);
      }
      h.assert(await page.locator('[data-rh-xp]').count() === 0, 'The recorded lot became markup in the injection panel');
    });

    let editor;
    await s.step('RH: Add Injection opens the editor for the same patient, workflow and form', async () => {
      editor = await s.popup(page, page.locator('input[value="Add Injection"]'), 'rh-injection-editor');
      await editor.waitForLoadState('load');
      const opened = new URL(editor.url());
      h.assert(opened.pathname.endsWith('/form/addRhInjection'), `Add Injection opened ${opened.pathname}`);
      h.assert(opened.searchParams.get('demographic_no') === patient && opened.searchParams.get('workflowId') === workflowId
        && opened.searchParams.get('formId') === formId, 'The editor did not open for the same patient, workflow and form');
      await h.assertNotErrorPage(editor, 'RH injection editor');
    });

    await s.step('RH: Save Injection stores one linked injection without a script error and refreshes the panel', async () => {
      await editor.locator('input[name="lot"]').fill(savedLot);
      await editor.locator('input[name="product"]').fill('FAKE Rhophylac');
      await editor.locator('input[name="dosage"]').fill('300');
      const stored = editor.waitForResponse(answer => /\/prevention\/AddPrevention$/.test(new URL(answer.url()).pathname));
      await editor.getByRole('button', { name: 'Save Injection', exact: true }).click({ noWaitAfter: true });
      const answer = await stored;
      h.assert(answer.status() === 200, `Save Injection answered HTTP ${answer.status()}`);
      const linked = `SELECT COUNT(*) FROM preventions p JOIN preventionsExt w ON w.prevention_id=p.id AND w.keyval='workflowId'
        JOIN preventionsExt l ON l.prevention_id=p.id AND l.keyval='lot'
        WHERE p.demographic_no=${patient} AND p.prevention_type='RH' AND p.deleted='0' AND w.val=${h.sqlString(workflowId)}
          AND l.val=${h.sqlString(savedLot)}`;
      h.assert(rows(sql, linked) === 1, 'Save Injection did not store exactly one RH injection linked to the workflow');
      await page.locator('#injectionInfo').getByText(savedLot).waitFor({ state: 'visible', timeout: 20000 });
      h.assert(await page.locator('#injectionInfo fieldset').count() === 2, 'The refreshed panel does not list both injections');
    });

    await s.step('RH: the panel\'s Delete removes only the chosen injection', async () => {
      const seeded = page.locator('#injectionInfo fieldset').filter({ hasText: marker + '<b' });
      const removed = page.waitForResponse(answer => /\/prevention\/AddPrevention$/.test(new URL(answer.url()).pathname));
      await seeded.getByText('Delete', { exact: true }).first().click();
      h.assert((await removed).ok(), 'The Delete request was refused');
      await page.locator('#injectionInfo').getByText(seededLot).waitFor({ state: 'detached', timeout: 20000 });
      h.assert(sql.value(`SELECT deleted FROM preventions WHERE id=${seededInjection}`) === '1', 'The chosen injection was not deleted');
      h.assert(rows(sql, `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND prevention_type='RH' AND deleted='0'`) === 1,
        'Delete affected an injection other than the chosen one');
    });

    await s.step('RH: the WorkFlow queue row opens that pregnancy\'s saved RH form inside the application', async () => {
      const queue = await context.newPage();
      try {
        const response = await h.gotoApp(queue, s.config.baseUrl, '/oscarWorkflow/WorkFlowList');
        h.assert(response && response.status() === 200, `The WorkFlow queue answered HTTP ${response && response.status()}`);
        const link = queue.locator(`a[href*="formRhImmuneGlobulin?demographic_no=${patient}&"]`);
        h.assert(await link.count() === 1, 'The queue has no RH link for the owned patient\'s workflow');
        const target = new URL(await link.evaluate(anchor => anchor.href));
        const base = new URL(s.config.baseUrl);
        h.assert(target.origin === base.origin && target.pathname === `${base.pathname.replace(/\/$/, '')}/form/formRhImmuneGlobulin`,
          `The queue link resolves to ${target.pathname}, outside the application's form route`);
        h.assert(target.searchParams.get('formId') === formId, 'The queue link does not open the form saved for that workflow');
        const opened = await context.newPage();
        try {
          const answer = await opened.goto(target.href, { waitUntil: 'load' });
          h.assert(answer && answer.status() === 200, `The queue link answered HTTP ${answer && answer.status()}`);
          await h.assertNotErrorPage(opened, 'RH form from the queue');
        } finally {
          await opened.close();
        }
      } finally {
        await queue.close();
      }
    });
  } finally {
    await page.close().catch(() => {});
  }
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow };
