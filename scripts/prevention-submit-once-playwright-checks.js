#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #4410 item 2: one logical prevention submission creates one record, without suppressing a
 * later intentional add.
 *
 * User path: Schedule > Search > Master Record > E-Chart > Preventions "+" > the prevention list >
 * an immunization > the add form (AddPreventionData.jsp) > Save.
 *
 * Each rendering of the form carries its own one-time submission token (carlosPreventionSubmission),
 * and AddPrevention2Action answers a repeat of the same rendering from the first save's outcome.
 * Asserted:
 *   1. The form renders a token. A double click on Save stores exactly one prevention, and the popup
 *      ends on the close page as a single click does.
 *   2. A later, intentional add of the same immunization (the form opened again) gets a fresh token
 *      and is stored: the guard does not suppress it.
 *   3. Re-posting the first form's exact body (its token, its CSRF token) after it saved answers the
 *      close page again and stores nothing.
 *   4. A token used for a different record than the form it was issued for is refused with 409 as
 *      plain text, and the record it names is left untouched.
 * double-submit-chart-adds drives the remaining rapid-activation modes (two clicks, double Enter, a
 * slow-response re-click).
 *
 * Fixtures: the owned FAKE- patient; cleanup deletes its preventions (+ext) and any chart notes the
 * popups left, and asserts none remain.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { settledCount, sleep } = require('./lib/double-submit-helpers');

const TOKEN = 'carlosPreventionSubmission';
const SAVE_PATH = /\/prevention\/AddPrevention$/;

async function openEditor(s, chart) {
  const list = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  await list.locator('#immunization').fill('Fluzone');
  const editor = await s.popup(list,
    list.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first(), 'prevention-editor');
  return { list, editor };
}

async function fillEditor(editor, date, comment) {
  await editor.locator('[name="given"][value="given"]').check();
  await editor.locator('#prevDate').fill(date);
  await editor.locator('[name="comments"]').fill(comment);
}

/** The form's url-encoded body exactly as a classic submit of the Save button would send it. */
async function formBody(editor) {
  return editor.evaluate((name) => {
    const form = document.querySelector(`form input[name="${name}"]`).form;
    const save = form.querySelector('input[type="submit"][name="action"]');
    const data = new FormData(form, save);
    return { action: form.action, body: new URLSearchParams(data).toString() };
  }, TOKEN);
}

async function closeAll(pages) {
  for (const page of pages) if (page && !page.isClosed()) await page.close().catch(() => {});
}

async function workflow(s) {
  const { sql, patient, marker, context, config } = s;
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0', 'Owned prevention rows were not removed');
  });
  const chart = await s.chart();
  const owned = `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND deleted=0`;
  const onDate = (date) => `${owned} AND DATE(prevention_date)=${h.sqlString(date)}`;

  let firstToken;
  let firstBody;
  await s.step('a double click on Save stores one prevention and the popup closes as for a single click', async () => {
    const { list, editor } = await openEditor(s, chart);
    firstToken = await editor.locator(`input[name="${TOKEN}"]`).inputValue();
    h.assert(/^[0-9a-f-]{36}$/.test(firstToken), 'The prevention form rendered no submission token');
    await fillEditor(editor, '2026-02-03', `${marker}-double`);
    firstBody = await formBody(editor);
    const saves = [];
    editor.on('response', r => {
      if (r.request().method() === 'POST' && SAVE_PATH.test(new URL(r.url()).pathname)) saves.push(r.status());
    });
    const closed = editor.waitForEvent('close', { timeout: 30000 });
    try {
      await editor.locator('input[type="submit"][name="action"]').first().dblclick({ noWaitAfter: true, timeout: 8000 });
    } catch (error) {
      // Only the popup closing or navigating under the second click is expected; anything else fails.
      if (!/closed|detached|destroyed|navigat|Target page/i.test(error.message)) throw error;
    }
    await closed;
    const count = await settledCount(sql, onDate('2026-02-03'), { min: 1 });
    h.assert(count === 1, `A double click on Save stored ${count} preventions`);
    h.assert(saves.length >= 1 && saves.every(status => status === 200),
      `Save answered ${saves.length ? saves.join(', ') : 'nothing the editor received'} instead of the close page`);
    console.log(`    (${saves.length} Save response(s) reached the editor, which closed itself)`);
    await closeAll([list]);
  });

  await s.step('a later, intentional add of the same immunization gets a new token and is stored', async () => {
    const { list, editor } = await openEditor(s, chart);
    const token = await editor.locator(`input[name="${TOKEN}"]`).inputValue();
    h.assert(token && token !== firstToken, 'The reopened form reused the earlier submission token');
    await fillEditor(editor, '2026-02-04', `${marker}-again`);
    await editor.locator('input[type="submit"][name="action"]').first().click({ noWaitAfter: true });
    const count = await settledCount(sql, onDate('2026-02-04'), { min: 1 });
    h.assert(count === 1, `The intentional second add stored ${count} preventions`);
    h.assert(Number(sql.value(owned)) === 2, 'The chart does not carry both intended preventions');
    await closeAll([editor, list]);
  });

  const post = (body) => context.request.post(firstBody.action, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Origin: new URL(config.baseUrl).origin,
      Referer: firstBody.action,
    },
    data: body,
    failOnStatusCode: false,
    maxRedirects: 0,
  });

  await s.step('re-posting the first form after it saved answers the close page and stores nothing', async () => {
    const response = await post(firstBody.body);
    h.assert(response.status() === 200, `The replay answered HTTP ${response.status()}`);
    h.assert(/closeWin\(\)/.test(await response.text()), 'The replay did not answer the close page');
    await sleep(1500);
    h.assert(sql.value(onDate('2026-02-03')) === '1', 'The replayed form stored a second prevention');
    h.assert(Number(sql.value(owned)) === 2, 'The replay changed the chart');
  });

  await s.step('a token used for another record than its form is refused with 409 and changes nothing', async () => {
    const target = sql.value(`SELECT id FROM preventions WHERE demographic_no=${patient} AND deleted=0
      AND DATE(prevention_date)='2026-02-04' LIMIT 1`);
    h.assert(/^[1-9]\d*$/.test(target), 'The intentional add is not in the chart');
    const body = new URLSearchParams(firstBody.body);
    body.set('id', target);
    body.set('prevDate', '2026-02-05');
    const response = await post(body.toString());
    h.assert(response.status() === 409, `A token for another record answered HTTP ${response.status()}, not 409`);
    h.assert(/^text\/plain/.test(response.headers()['content-type'] || ''), 'The refusal is not plain text');
    await sleep(1000);
    h.assert(sql.value(`SELECT deleted FROM preventions WHERE id=${target}`) === '0', 'The refused submission replaced the record');
    h.assert(sql.value(onDate('2026-02-05')) === '0', 'The refused submission stored a prevention');
    h.assert(Number(sql.value(owned)) === 2, 'The refused submission changed the chart');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('prevention-submit-once', workflow, { openPatient: true });
