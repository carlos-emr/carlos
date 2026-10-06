#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: two sessions amend the same allergy.
 *
 * User path (both sessions of the shared test login): Schedule > Search > Master Record > E-Chart >
 * Allergies > the allergy's modify (pencil) link > change the severity > Add Allergy. An amendment is
 * "add a replacement, archive the original" (RxAddAllergy2Action, allergyToArchive). Session A and
 * session B both open the allergy list while the original is active. A amends it (severity 1); B,
 * whose list still shows the original as active, amends it too (severity 3).
 *
 * Asserted: A's amendment lands and archives the original (control); after B's amendment the chart
 * carries exactly ONE active version of that allergy (a refusal or a merge are both fine). The action
 * only checks that the archive target belongs to the patient, not that it is still active, so B's
 * stale form adds a second active allergy with the same description and a different severity and the
 * archive of an already-archived row is a silent no-op. The check fails at that last step.
 *
 * Fixtures: one owned allergy seeded by SQL (marker description) for the owned FAKE- patient; cleanup
 * deletes every allergies row of the owned patient and asserts none remain. Session B takes the chart
 * lock over with the explicit confirm (the same-login takeover), which is accepted and counted.
 * Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { openSecondSession, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const TAKEOVER = /^You have started to edit this note in another window at [^\n]+\.\nDo you wish to continue\?$/;

async function allergyPopup(s, context, chart, label) {
  const popup = await clickOpensPopup(chart, chart.locator('a[onclick*="showAllergy"]').first(),
    { context, recorder: s.recorder, label, timeout: 20000 });
  await popup.locator('#searchString').waitFor({ state: 'visible', timeout: 20000 });
  return popup;
}

async function amend(page, id, severity) {
  const form = page.locator('#RxAddAllergyForm');
  await page.locator(`#allergy_${id} a.modifyAllergyLink`).click();
  await form.waitFor({ state: 'visible' });
  h.assert(await form.locator('#allergyToArchive').inputValue() === id, 'The amendment form lost the original allergy');
  await form.locator('[name="severityOfReaction"]').selectOption(severity);
  await form.locator('input[type="submit"]').click();
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Owned allergy rows were not removed');
  });
  const original = sql.value(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,start_date,age_of_onset,
      severity_of_reaction,onset_of_reaction,life_stage,position,lastUpdateDate,providerNo,nonDrug)
    VALUES (${patient},CURDATE(),${h.sqlString(marker)},0,${h.sqlString(marker)},0,NULL,0,'2','1','A',0,NOW(),${h.sqlString(provider)},1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(original), 'The allergy fixture was not created');
  const active = `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND archived=0`;
  let takeovers = 0;
  const b = await openSecondSession(s, {
    label: 'second-session',
    async dialogHandler(dialog, entry) {
      if (dialog.type() === 'confirm' && TAKEOVER.test(dialog.message())) { takeovers++; await dialog.accept(); return; }
      s.recorder.unexpectedDialogs.push(entry);
      await dialog.dismiss();
    },
  });
  const aChart = await s.chart();
  const bChart = await openChart(b.context, b.master, s.recorder, 20000);
  await waitForNavbars(bChart, 20000);
  const aPopup = await allergyPopup(s, s.context, aChart, 'allergy-a');
  const bPopup = await allergyPopup(s, b.context, bChart, 'allergy-b');
  h.assert(takeovers <= 1, 'More than one chart lock takeover was confirmed');
  for (const page of [aPopup, bPopup]) await page.locator(`#allergy_${original}`).waitFor({ state: 'visible' });

  await s.step('session A amends the allergy: a replacement is added and the original archived', async () => {
    await amend(aPopup, original, '1');
    await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${original}`, '1', 'Session A\'s amendment did not archive the original');
    await expectValue(sql, active, '1', 'Session A\'s amendment did not leave exactly one active allergy');
  });
  await s.step('session B amends the same allergy from its stale list', async () => {
    const mark = failureMark(s.recorder);
    const [response] = await Promise.all([
      bPopup.waitForResponse(r => r.request().method() === 'POST' && /\/rx\/addAllergy2?$/.test(new URL(r.url()).pathname), { timeout: 20000 }),
      amend(bPopup, original, '3'),
    ]);
    h.assert(response.status() === 409, `Session B's stale amendment answered HTTP ${response.status()} instead of a conflict`);
    await bPopup.waitForTimeout(500);
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/rx\/addAllergy2?$/ });
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND severity_of_reaction='3'`) === '0',
      'The rejected amendment still created its replacement allergy');
  });
  await s.step('the chart carries one active version of the allergy', async () => {
    const count = sql.value(active);
    h.assert(sql.value(`SELECT severity_of_reaction FROM allergies WHERE demographic_no=${patient} AND archived=0`) === '1',
      'The stale amendment changed the winning allergy severity');
    h.assert(count === '1',
      `After two sessions amended the same allergy the chart carries ${count} active allergies with the same description and different severities. `
      + 'RxAddAllergy2Action only checks that allergyToArchive belongs to the patient, not that it is still active, so the stale amendment adds a '
      + 'second active row and archiving the already-archived original is a silent no-op.');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-allergy-amend', workflow, { openPatient: true });
