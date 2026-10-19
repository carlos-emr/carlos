#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * An eForm whose own script throws is reported to CARLOS and flagged unstable, and a clean one is not.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ eForms "+" ▸ an eForm whose page script throws (the
 * floating toolbar's window.onerror posts eform/logEformError) ▸ a second, clean eForm.
 * Asserts against MariaDB that the broken template's eform.stable becomes 0 after the page error and
 * the clean template stays 1; that the report is a POST answered 200; and, as the last step, that a
 * plain GET of eform/logEformError for the clean template (a link or image tag anywhere would do it)
 * is refused and leaves the template stable. That step fails today: EformLogError2Action accepts GET
 * and flips the flag, so any clinician-visible page can mark any eForm unstable.
 * Fixtures: three owned eForm templates; the broken one's deliberate page error is the only one consumed
 * from the strict recorder. Cleanup deletes all three templates and asserts them gone.
 * Implements gap-encounter "script errors in an eForm" (eform/logEformError had no check).
 *
 * The broken form throws the way real Galaxy forms do: it calls a jQuery plugin that is not loaded, so
 * the browser's message reads "$(...).<name> is not a function". Through the packaged front door CRS
 * 932130 read that "$(...)" as shell command substitution and 403'd the report until WAF exclusion
 * 1075; a plain thrown Error never exercised that path. The third form loads the CARLOS lab
 * decision-support stub (LabEngine_2023.js) and calls a stubbed function, whose own report used a
 * token-less navigator.sendBeacon() that CSRFGuard refused with 403; it must arrive as a CSRF-tokened
 * POST answered 200.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  // Becomes the missing plugin's method name, so the browser's message carries it after "$(...).".
  const scriptError = `fixture${marker.slice(-8).replace(/[^A-Za-z0-9]/g, '')}`;
  const names = { broken: `${marker} Broken`, clean: `${marker} Clean`, stub: `${marker} Stub` };
  const ownedNames = Object.values(names).map(q).join(',');
  const ids = {};
  const stable = key => sql.value(`SELECT stable FROM eform WHERE fid=${ids[key]}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM eform WHERE form_name IN (${ownedNames})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name IN (${ownedNames})`) === '0',
      'Owned eForm templates were not removed');
  });
  const template = (key, script) => {
    const html = `<html><head><title>${key}</title></head><body><form method="post" action="" name="FormName" id="FormName">`
      + `<input type="text" name="subject" id="subject">${script}<input type="submit" value="Submit" id="SubmitButton"></form></body></html>`;
    ids[key] = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
      showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${q(names[key])},'','error log fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(ids[key]), `The ${key} template was not created`);
  };
  // Thrown after load: the toolbar installs window.onerror when its own script runs, after the eForm markup.
  template('broken', `<script>window.addEventListener('load', function () { setTimeout(function () { $(document.body).${scriptError}(); }, 300); });</script>`);
  template('clean', '');
  // ${oscar_image_path} is the eForm image directory, where EFormAssetDeployer seeds the stub.
  template('stub', '<script src="${oscar_image_path}LabEngine_2023.js"></script>'
    + `<script>window.addEventListener('load', function () { setTimeout(function () { autoLabReqPop(); }, 300); });</script>`);
  const consumeScriptErrors = () => {
    const expected = s.recorder.pageErrors.filter(entry => entry.text.includes(scriptError));
    for (const entry of expected) s.recorder.pageErrors.splice(s.recorder.pageErrors.indexOf(entry), 1);
    return expected.length;
  };
  const chart = await s.chart();
  let list;

  await s.step('the templates start stable and the Add eForm list offers them', async () => {
    h.assert(stable('broken') === '1' && stable('clean') === '1' && stable('stub') === '1', 'The fixtures did not start stable');
    list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
  });

  await s.step('opening the eForm whose script throws reports the error and flags the template unstable', async () => {
    const reported = list.context().waitForEvent('request', { predicate: r => /\/eform\/logEformError/.test(r.url()), timeout: 30000 });
    reported.catch(() => {});
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: names.broken }).first(), 'eform-broken');
    const request = await reported;
    h.assert(request.method() === 'POST', 'The script error was reported with a method other than POST');
    // The browser's own wording is what the WAF scores; prove this run sent the shape that 403'd.
    const message = new URLSearchParams(request.postData() || '').get('error') || '';
    h.assert(message.includes(`$(...).${scriptError}`), 'The report did not carry the browser\'s "$(...)" message');
    const response = await request.response();
    h.assert(response && response.status() === 200, `The script error report was answered HTTP ${response ? response.status() : 'none'}, not 200`);
    await expectValue(sql, `SELECT stable FROM eform WHERE fid=${ids.broken}`, '0', 'The failing eForm was not flagged unstable');
    h.assert(consumeScriptErrors() >= 1, 'The fixture script error was not raised by the eForm page');
    h.assert(stable('clean') === '1', 'Another eForm was flagged by the report');
    await form.close();
  });

  await s.step('opening the clean eForm reports nothing and leaves it stable', async () => {
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: names.clean }).first(), 'eform-clean');
    await form.locator('#remote_eform_subject').waitFor();
    h.assert(stable('clean') === '1', 'The clean eForm was flagged unstable');
    h.assert(consumeScriptErrors() === 0, 'The clean eForm raised the fixture error');
    await form.close();
  });

  await s.step('a stubbed lab decision-support call is reported with a CSRF token and flags its template', async () => {
    const reported = list.context().waitForEvent('request', { predicate: r => /\/eform\/logEformError/.test(r.url()), timeout: 30000 });
    reported.catch(() => {});
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: names.stub }).first(), 'eform-stub');
    const request = await reported;
    h.assert(await form.locator('#carlos-lab-ds-stubbed').count() === 1,
      'LabEngine_2023.js on this install is not the CARLOS stub, so its report cannot be checked');
    h.assert(request.method() === 'POST', 'The stub reported with a method other than POST');
    h.assert(new URLSearchParams(request.postData() || '').get('formId') === ids.stub, 'The stub reported another form');
    h.assert(Boolean((await request.allHeaders())['csrf-token']), 'The stub report carried no CSRF-TOKEN header');
    const response = await request.response();
    h.assert(response && response.status() === 200, `The stub report was answered HTTP ${response ? response.status() : 'none'}, not 200`);
    await expectValue(sql, `SELECT stable FROM eform WHERE fid=${ids.stub}`, '0', 'The stubbed eForm was not flagged unstable');
    h.assert(stable('clean') === '1', 'The clean eForm was flagged by the stub report');
    await form.close();
  });

  await s.step('a GET of the error-report address is refused and leaves the clean template stable', async () => {
    const response = await s.context.request.get(`${String(config.baseUrl).replace(/\/$/, '')}/eform/logEformError?formId=${ids.clean}&error=probe`);
    const status = response.status();
    await response.dispose();
    h.assert(stable('clean') === '1', 'A GET of eform/logEformError flagged the clean eForm unstable');
    h.assert(status === 405, `A GET of eform/logEformError answered HTTP ${status}, not 405`);
  });
}

if (require.main === module) runWorkflow('gap-encounter-eform-error-log', workflow, { openPatient: true });
module.exports = { workflow };
