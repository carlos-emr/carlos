#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: eForm Submit/Save from the floating toolbar.
 *
 * User path: Schedule > Search > Master Record > E-Chart > eForms "+" > the owned eForm > type a subject and a
 * field > toolbar Submit (#remoteSubmitButton). For each rapid activation (dblclick(), two back-to-back
 * clicks, double Enter on the focused toolbar Save button (the fixture's own submit is not the eForm save path), slow-response re-click) the check saves ONE instance with
 * its own marker subject and asserts EXACTLY ONE eform_data row for the owned patient and that subject.
 *
 * Fixtures: one owned eForm template (marker name) and the owned FAKE- patient; cleanup deletes the instances
 * (eform_values + eform_data) for the owned patient/form and the template, and asserts they are gone.
 * Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep, recorderMark, forgiveAbortedSecondRequest } = require('./lib/double-submit-helpers');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const formName = `${marker} dbl form`;
  const html = '<html><head><title>dbl fixture</title></head><body>'
    + `<form method="post" action="" name="FormName" id="FormName"><h2>${marker.replace(/-/g, '')}</h2>`
    + '<input type="text" name="subject" id="subject"><input type="text" name="note" id="note">'
    + '<input type="submit" value="Submit" id="SubmitButton"></form></body></html>';
  let fid;
  s.cleanup(() => {
    const fdids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`).map((r) => r[0]);
    for (const fdid of fdids) {
      h.assert(/^[1-9]\d*$/.test(fdid), 'Owned eForm instance id is invalid');
      sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid}; DELETE FROM eform_data WHERE fdid=${fdid} AND demographic_no=${patient}`);
    }
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})`) === '0', 'Owned eForm rows were not removed');
  });
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'','dbl fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The eForm template fixture was not created');
  const chart = await s.chart();
  const v = verdicts('eform-submit');
  // The Add eForm list stays open all run: unloading it throws on a null window.opener (known defect,
  // covered by eform-groups), so it is never closed from here.
  const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
  await list.locator('#efmTable').waitFor();

  for (const mode of MODES) {
    await s.step(`eForm Submit via ${mode.label} saves exactly one instance`, async () => {
      const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-fill');
      await form.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
      const subject = `${marker}-${mode.tag}`;
      await form.locator('#remote_eform_subject').fill(subject);
      await form.locator('#note').fill('double submit');
      const route = /\/eform\/AddEForm$|\/eform\/.*AddEForm/i;
      const posts = watchPosts(form.context(), route);
      const since = recorderMark(s.recorder);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      await rapid(mode.key, form.locator('#remoteSubmitButton'), { textField: form.locator('#remoteSubmitButton') });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
        AND form_name=${q(formName)} AND subject=${q(subject)}`, { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      await sleep(300);
      forgiveAbortedSecondRequest(s.recorder, since, /\/eform\//);
      console.log(`    (${posts.seen.length} AddEForm POST(s))`);
      v.record(mode.label, count, { exactly: 1 });
      if (!form.isClosed()) await form.close().catch(() => {});
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-eform', workflow, { openPatient: true });
