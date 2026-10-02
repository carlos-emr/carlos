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
 * Fixtures: two owned eForm templates; the broken one's deliberate page error is the only one consumed
 * from the strict recorder. Cleanup deletes both templates and asserts them gone.
 * Implements gap-encounter "script errors in an eForm" (eform/logEformError had no check).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  const scriptError = `FIXTURE-${marker.slice(-8)}`;
  const names = { broken: `${marker} Broken`, clean: `${marker} Clean` };
  const ids = {};
  const stable = key => sql.value(`SELECT stable FROM eform WHERE fid=${ids[key]}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM eform WHERE form_name IN (${q(names.broken)},${q(names.clean)})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name IN (${q(names.broken)},${q(names.clean)})`) === '0',
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
  template('broken', `<script>window.addEventListener('load', function () { setTimeout(function () { throw new Error('${scriptError}'); }, 300); });</script>`);
  template('clean', '');
  const consumeScriptErrors = () => {
    const expected = s.recorder.pageErrors.filter(entry => entry.text.includes(scriptError));
    for (const entry of expected) s.recorder.pageErrors.splice(s.recorder.pageErrors.indexOf(entry), 1);
    return expected.length;
  };
  const chart = await s.chart();
  let list;

  await s.step('both templates start stable and the Add eForm list offers them', async () => {
    h.assert(stable('broken') === '1' && stable('clean') === '1', 'The fixtures did not start stable');
    list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
  });

  await s.step('opening the eForm whose script throws reports the error and flags the template unstable', async () => {
    const reported = list.context().waitForEvent('request', { predicate: r => /\/eform\/logEformError/.test(r.url()), timeout: 30000 });
    reported.catch(() => {});
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: names.broken }).first(), 'eform-broken');
    const request = await reported;
    h.assert(request.method() === 'POST', 'The script error was reported with a method other than POST');
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
