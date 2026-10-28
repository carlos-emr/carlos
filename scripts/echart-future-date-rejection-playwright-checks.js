#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Real toolbar submissions, rewritten in flight to exercise server validation even when the
// browser calendar would prevent a future day. Each request must preserve the draft and database.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

async function workflow(s) {
  const endpoint = h.appUrl(s.config.baseUrl, '/CaseManagementEntry');
  const notes = () => s.sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${s.patient}`);
  for (const [kind, button, operation] of [
    ['Save', '#saveImg', 'save'], ['Sign & Save', '#signSaveImg', 'saveAndExit'], ['Verify & Sign', '#signVerifyImg', 'saveAndExit'],
  ]) {
    await s.step(`${kind} refuses a future date before persistence and retains the draft`, async () => {
      const chart = await s.chart();
      const field = chart.locator('textarea[name="caseNote_note"]').first();
      await field.waitFor({ state: 'visible' });
      const draft = `${s.marker} ${kind} future date draft`;
      await field.fill(draft);
      const before = notes();
      const cleanup = await chart.evaluate(() => ({
        method: 'releaseNoteLock', demographicNo: String(globalThis.demographicNo),
        noteId: document.forms.caseManagementEntryForm.elements.noteId.value,
        'CSRF-TOKEN': globalThis.CarlosAjax.getCsrfToken(),
      }));
      await chart.route('**/CaseManagementEntry*', async route => {
        const params = new URLSearchParams(route.request().postData() || '');
        if (route.request().method() === 'POST' && params.get('method') === operation) {
          params.set('observation_date', '01-Jan-2099 10:00');
          await route.continue({ postData: params.toString() });
        } else await route.continue();
      });
      const mark = failureMark(s.recorder);
      let refused;
      const dialogs = await h.withExpectedDialogs(chart, async () => {
        [refused] = await Promise.all([
          chart.waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
            && new URLSearchParams(response.request().postData() || '').get('method') === operation),
          chart.locator(button).first().click(),
        ]);
        if (operation === 'save') await chart.waitForFunction(() => globalThis.noteSwitchSavePending === false);
        else await chart.locator('#unsaved-note-text').waitFor({ state: 'visible' });
      });
      h.assert(refused.status() === 400, `${kind} answered HTTP ${refused.status()}, expected 400`);
      h.assert(Object.hasOwn(refused.headers(), 'x-permitted-cross-domain-policies'), 'The application must refuse the date');
      h.assert(notes() === before, `${kind} persisted the refused draft`);
      await chart.unroute('**/CaseManagementEntry*');
      if (operation === 'save') {
        h.assert(await field.inputValue() === draft, 'Save lost the draft or replaced its editor');
        h.assert(dialogs.some(dialog => /encounter date.*future|future.*encounter date/i.test(dialog.text)),
          'Save did not show the encounter-date validation message');
      } else {
        h.assert(await chart.locator('#unsaved-note-text').inputValue() === draft, `${kind} lost the recovery draft`);
        h.assert(/date.*future/i.test(await chart.locator('body').innerText()), `${kind} did not explain the refusal`);
      }
      consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/CaseManagementEntry$/ });
      if (operation === 'save') {
        // The original date in the editor is valid: the rejected future value was only in flight.
        const [saved] = await Promise.all([
          chart.waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
            && new URLSearchParams(response.request().postData() || '').get('method') === 'save'),
          chart.locator(button).first().click(),
        ]);
        h.assert(saved.status() === 200, 'Correcting the date did not permit a retry');
        h.assert(Number(notes()) === Number(before) + 1, 'The retry did not save exactly one note');
        await chart.waitForFunction(() => /^[1-9][0-9]*$/.test(document.forms.caseManagementEntryForm.elements.noteId.value));
        await releaseChartLocks(s.context, s.config.baseUrl, [chart]);
      } else {
        const released = await s.context.request.post(endpoint, { form: cleanup, maxRedirects: 0 });
        h.assert(released.status() === 200, 'Could not release the owned refused draft lock');
      }
      await chart.close();
    });
  }
}
if (require.main === module) runWorkflow('echart-future-date-rejection', workflow);
module.exports = { workflow };
