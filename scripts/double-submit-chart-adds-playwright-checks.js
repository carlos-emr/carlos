#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: E-Chart clinical adds -- Allergy (Add Allergy), Prevention (Save) and Measurement
 * group (Submit).
 *
 * User path: Schedule > Search > Master Record > E-Chart > Allergies / Preventions / Measurements "+" >
 * the add popup > Save. For each rapid activation (dblclick(), two back-to-back clicks, double Enter in a text field or
 * on the focused Add/Submit button where the form has no implicit-submit path, slow-response re-click) the check adds ONE record with its own marker (allergy reaction text,
 * prevention date, measurement comment) and asserts EXACTLY ONE owned row in allergies, preventions,
 * measurements. Neither form disables its button.
 *
 * Fixtures: the owned FAKE- patient; cleanup deletes this patient's allergies, preventions (+ext),
 * measurements (+deleted) and any chart notes/locks the popups left, and asserts they are gone.
 * Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES, MODES_REPLAY, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep, recorderMark, forgiveAbortedSecondRequest } = require('./lib/double-submit-helpers');

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient};
      DELETE FROM measurementsDeleted WHERE demographicNo=${patient};
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})`) === '0', 'Owned clinical rows were not removed');
  });
  const chart = await s.chart();
  const allergies = verdicts('allergy-add');
  const preventions = verdicts('prevention-add');
  const measurements = verdicts('measurement-add');

  // The allergy popup navigates in place after Add Allergy, so it also gets the POST-replay mode.
  for (const mode of MODES_REPLAY) {
    await s.step(`Add Allergy via ${mode.label} records exactly one allergy`, async () => {
      const page = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergy-list');
      const form = page.locator('#RxAddAllergyForm');
      await page.locator('#searchString').fill(marker.slice(-16).toUpperCase());
      await h.withExpectedDialogs(page, () => page.locator('input[value="Custom Allergy"]').click());
      await form.waitFor({ state: 'visible' });
      const reaction = `${marker}-${mode.tag}`;
      await form.locator('#reactionDescription').fill(reaction);
      await form.locator('[name="nonDrug"]').selectOption('on');
      await form.locator('[name="severityOfReaction"]').selectOption('3');
      await form.locator('[name="onSetOfReaction"]').selectOption('1');
      await form.locator('[name="lifeStage"]').selectOption('A');
      await form.locator('#startDate').fill('2026-01-02');
      await form.locator('#startDate').press('Escape');
      await form.locator('#startDate').press('Tab');
      const route = /\/rx\/addAllergy|\/rx\/AddAllergy|RxAddAllergy/i;
      const posts = watchPosts(page.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      await rapid(mode.key, form.locator('input[type="submit"]').first(), { textField: form.locator('input[type="submit"]').first() });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}
        AND reaction=${h.sqlString(reaction)}`, { min: 1 });
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${posts.seen.length} allergy POST(s))`);
      allergies.record(mode.label, count, { exactly: 1 });
      if (!page.isClosed()) await page.close().catch(() => {});
    });
  }

  for (const [index, mode] of MODES.entries()) {
    await s.step(`Prevention Save via ${mode.label} records exactly one prevention`, async () => {
      const list = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
      await list.locator('#immunization').fill('Fluzone');
      const editor = await s.popup(list,
        list.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first(), 'prevention-editor');
      const date = `2026-01-0${index + 2}`;
      await editor.locator('[name="given"][value="given"]').check();
      await editor.locator('#prevDate').fill(date);
      await editor.locator('[name="comments"]').fill(`${marker}-${mode.tag}`);
      const route = /\/prevention\/.*AddPrevention|AddPreventionData/i;
      const posts = watchPosts(editor.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      await rapid(mode.key, editor.locator('input[type="submit"][name="action"]').first(), { textField: editor.locator('#prevDate') });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}
        AND prevention_type='Inf' AND deleted=0 AND DATE(prevention_date)=${h.sqlString(date)}`,
      { min: 1 });
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${posts.seen.length} prevention POST(s))`);
      preventions.record(mode.label, count, { exactly: 1 });
      for (const p of [editor, list]) if (!p.isClosed()) await p.close().catch(() => {});
    });
  }

  for (const mode of MODES) {
    await s.step(`Measurement group Submit via ${mode.label} stores exactly one reading`, async () => {
      await chart.locator('#menuTitle3 a').hover();
      const item = chart.locator('#menu3 a.menuItemleft').filter({ hasText: 'Vitals' });
      await item.waitFor({ state: 'visible' });
      const group = await s.popup(chart, item, 'vitals-entry');
      await group.locator('#row-BP').waitFor({ state: 'visible' });
      const row = group.locator('#row-BP');
      await row.locator('input[name^="inputValue-"]').fill('128/82');
      await row.locator('input[name^="date-"]').fill('2026-03-04');
      await row.locator('input[name^="comments-"]').fill(`${marker}-${mode.tag}`);
      const since = recorderMark(s.recorder);
      const posts = watchPosts(group.context(), /Measurement/i);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, /Measurement/i) : null;
      await rapid(mode.key, group.getByRole('button', { name: 'Submit', exact: true }), { textField: group.getByRole('button', { name: 'Submit', exact: true }) });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}
        AND type='BP' AND comments=${h.sqlString(`${marker}-${mode.tag}`)}`, { min: 1 });
      if (disarm) await disarm();
      posts.stop();
      await sleep(500);
      forgiveAbortedSecondRequest(s.recorder, since, /\/encounter\/Measurements/);
      console.log(`    (${posts.seen.length} measurement POST(s))`);
      measurements.record(mode.label, count, { exactly: 1 });
      if (!group.isClosed()) await group.close().catch(() => {});
    });
  }
  allergies.finish();
  preventions.finish();
  measurements.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-chart-adds', workflow, { openPatient: true });
