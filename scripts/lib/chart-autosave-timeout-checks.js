/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const h = require('./playwright-harness');
const { expectValue } = require('./workflow-session');

async function checkAutosaveTimeout(s, { chart, patient, otherPatient, isAutosave, drafts, autosave, pause }) {
  await s.step('a stalled autosave times out visibly and releases the guard for a real retry', async () => {
    await pause(chart);
    const baseline = drafts(s.sql, [patient, otherPatient]);
    const text = `${s.marker}-native-timeout-retry`;
    await chart.locator('textarea[name="caseNote_note"]').fill(text);
    const before = { failures: new Set(s.recorder.requestFailures), console: new Set(s.recorder.consoleIssues) };
    const match = url => url.pathname.endsWith('/CaseManagementEntry');
    const ownedAutosave = request => {
      if (!isAutosave(request)) return false;
      const fields = new URLSearchParams(request.postData() || '');
      return fields.get('demographicNo') === patient && fields.get('note') === text;
    };
    let release;
    const held = new Promise(resolve => { release = resolve; });
    let finish;
    const finished = new Promise(resolve => { finish = resolve; });
    let intercepted = 0;
    const stall = async route => {
      if (!isAutosave(route.request())) return route.continue();
      h.assert(ownedAutosave(route.request()), 'The stalled POST was not the exact owned patient and retained text');
      intercepted++;
      try {
        await held;
        // Never forward the timed-out POST: a late write would invalidate this test.
        await route.abort('aborted');
      } finally { finish(); }
    };
    await chart.route(match, stall);
    let nativeTimeout;
    try {
      const failed = chart.waitForEvent('requestfailed', { predicate: ownedAutosave, timeout: 65000 });
      failed.catch(() => {}); // The original promise is still awaited below.
      const started = Date.now();
      await chart.evaluate(() => autoSave());
      nativeTimeout = await chart.evaluateHandle(() => {
        const xhr = autoSaveXhr;
        const evidence = { timeout: xhr?.timeout, event: null, events: 0 };
        xhr?.addEventListener('timeout', event => {
          evidence.events++;
          evidence.event = { trusted: event.isTrusted, readyState: xhr.readyState, status: xhr.status };
        });
        return evidence;
      });
      h.assert(await nativeTimeout.evaluate(evidence => evidence.timeout) === 45000,
        'The real autosave XHR has no bounded 45-second timeout');
      await chart.locator('#autosaveTime[data-autosave-state="pending"]').waitFor();
      await chart.locator('#autosaveTime[data-autosave-state="failed"]').waitFor({ timeout: 65000 });
      h.assert(Date.now() - started >= 43000, 'The request failed before the native timeout elapsed');
      await chart.waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation);
      const evidence = await nativeTimeout.jsonValue();
      h.assert(evidence.events === 1 && evidence.event?.trusted === true
        && evidence.event.readyState === 4 && evidence.event.status === 0,
      'The browser did not deliver a trusted native XHR timeout before test cleanup');
      release();
      await finished;
      const request = await failed;
      const error = request.failure()?.errorText;
      h.assert(intercepted === 1 && /^net::ERR_(?:ABORTED|TIMED_OUT)$/.test(error || ''),
        `The native timeout did not cancel exactly the held autosave: ${error}`);
      h.assert(await chart.locator('textarea[name="caseNote_note"]').inputValue() === text,
        'The timed-out request discarded the editor text');
      h.assert(drafts(s.sql, [patient, otherPatient]) === baseline, 'The undelivered timed-out request changed a draft');
      const failures = s.recorder.requestFailures.filter(entry => !before.failures.has(entry)
        && entry.url === request.url() && entry.errorText === error);
      h.assert(failures.length === 1, 'The recorder did not capture exactly the timed-out autosave');
      const diagnostic = s.recorder.consoleIssues.filter(entry => !before.console.has(entry)
        && /\/js\/newCaseManagementView\.js\.jsp(?:\?|$)/.test(entry.location?.url || '')
        && entry.text.trim() === 'CaseManagement autoSave failed 0 Request timed out');
      h.assert(diagnostic.length === 1, 'The timeout did not report its exact visible-failure diagnostic');
      const resource = s.recorder.consoleIssues.filter(entry => !before.console.has(entry)
        && entry.location?.url === request.url()
        && /^Failed to load resource:.*net::ERR_(?:ABORTED|TIMED_OUT)/.test(entry.text));
      h.assert(resource.length <= 1, 'The held request produced duplicate resource errors');
      s.recorder.requestFailures = s.recorder.requestFailures.filter(entry => !failures.includes(entry));
      s.recorder.consoleIssues = s.recorder.consoleIssues.filter(entry => !diagnostic.includes(entry) && !resource.includes(entry));
    } finally {
      release();
      if (intercepted) await finished;
      await chart.unroute(match, stall);
      if (nativeTimeout) await nativeTimeout.dispose();
      await chart.evaluate(() => clearAutoSaveTimer());
    }
    await autosave(chart, text);
    await chart.locator('#autosaveTime[data-autosave-state="saved"]').waitFor();
    await expectValue(s.sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(s.provider)} ORDER BY id DESC LIMIT 1`, text,
    'A successful retry after the native timeout did not persist the retained text');
  });
}

module.exports = { checkAutosaveTimeout };
