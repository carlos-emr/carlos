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
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
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
  // Keep the template list open while exercising independently rendered submission identities.
  const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
  await list.locator('#efmTable').waitFor();
  // The owned template may sort past the first 15-row page on a populated installation.
  await list.locator('#efmTable_filter input[type="search"]').fill(formName);
  await list.locator('#efmTable a').filter({ hasText: formName }).first().waitFor({ state: 'visible' });

  for (const mode of MODES) {
    await s.step(`eForm Submit via ${mode.label} saves exactly one instance`, async () => {
      const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-fill');
      await form.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
      const subject = `${marker}-${mode.tag}`;
      await form.locator('#remote_eform_subject').fill(subject);
      await form.locator('#note').fill('double submit');
      const route = /\/eform\/AddEForm$|\/eform\/.*AddEForm/i;
      const posts = watchPosts(form.context(), route);
      const submitted = [];
      const responses = [];
      const capture = request => {
        if (request.method() === 'POST' && route.test(request.url())) submitted.push(request);
      };
      const captureResponse = response => {
        if (response.request().method() === 'POST' && route.test(response.url())) responses.push(response);
      };
      form.on('request', capture);
      form.on('response', captureResponse);
      const since = recorderMark(s.recorder);
      const failures = failureMark(s.recorder);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      const activate = () => rapid(mode.key, form.locator('#remoteSubmitButton'),
        { textField: form.locator('#remoteSubmitButton') });
      if (mode.key === 'slowResubmit') {
        // fill() and this mode's synthetic clicks do not give the page sticky user activation.
        // A real editing gesture is required before Chrome permits a beforeunload prompt.
        await form.locator('#note').click();
        await form.evaluate(() => window.addEventListener('beforeunload', event => {
          event.preventDefault(); event.returnValue = '';
        }, { once: true }));
        const dialogs = await h.withExpectedDialogs(form, activate);
        h.assert(dialogs.length === 1 && dialogs[0].type === 'beforeunload', 'Expected an accepted unsaved-form navigation prompt');
      } else await activate();
      const count = await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
        AND form_name=${q(formName)} AND subject=${q(subject)}`, { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      await sleep(300);
      forgiveAbortedSecondRequest(s.recorder, since, /\/eform\//);
      if (mode.key === 'replay') {
        h.assert(responses.some(response => response.status() === 409), 'Reload did not reject the consumed submission');
        const conflict = responses.find(response => response.status() === 409);
        h.assert((await conflict.text()).includes('Check the patient'), 'Replay did not explain how to check the saved eForm');
        // This exact, asserted conflict is the expected result of the deliberately replayed POST.
        consumeExpectedFailure(s.recorder, failures, { status: 409, path: /\/eform\/addEForm$/ });
      }
      h.assert(submitted.length > 0, 'No eForm submission was observed');
      // Replay the actual captured request with JavaScript entirely bypassed. The UI guard alone
      // cannot pass this assertion; the server must reject it without saving or attaching again.
      const original = submitted[0];
      const repeated = await form.context().request.post(original.url(), {
        data: original.postData(), headers: { 'Content-Type': original.headers()['content-type'] },
        maxRedirects: 0,
      });
      h.assert(repeated.status() === 409 && (await repeated.text()).includes('Check the patient'),
        'Server accepted a repeated eForm POST or omitted recovery guidance');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid
        WHERE d.demographic_no=${patient} AND d.form_name=${q(formName)} AND d.subject=${q(subject)}
        AND v.var_name='note' AND v.var_value='double submit'`) === '1', 'Clinical field was lost or duplicated');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid
        WHERE d.demographic_no=${patient} AND d.form_name=${q(formName)}
        AND v.var_name='carlosEformSubmission'`) === '0', 'Submission identity was stored as clinical form data');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
        AND form_name=${q(formName)} AND subject=${q(subject)}`) === '1', 'Direct replay created a duplicate');
      if (mode.key === 'slowResubmit') h.assert(posts.seen.length === 1, 'Toolbar remained active while saving');
      form.off('request', capture);
      form.off('response', captureResponse);
      console.log(`    (${posts.seen.length} AddEForm POST(s); direct server replay rejected)`);
      v.record(mode.label, count, { exactly: 1 });
      if (!form.isClosed()) await form.close().catch(() => {});
    });
  }
  v.finish();

  await s.step('canceled and invalid submits remain editable and preserve the named submitter', async () => {
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-validation');
    await form.locator('#remoteSubmitButton').waitFor();
    const subject = `${marker}-VALIDATION`;
    const rows = () => sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
      AND form_name=${q(formName)} AND subject=${q(subject)}`);
    await form.locator('#remote_eform_subject').fill(subject);
    await form.evaluate(() => {
      document.getElementById('note').required = true;
      document.getElementById('SubmitButton').name = 'SubmitButton';
    });
    await form.locator('#remoteSubmitButton').click();
    await sleep(300);
    h.assert(rows() === '0' && await form.locator('#remoteSubmitButton').isEnabled(),
      'Invalid form saved or left the toolbar disabled');
    await form.locator('#note').fill('validated field');
    await form.evaluate(() => document.getElementById('SubmitButton').addEventListener('click',
      event => event.preventDefault(), { once: true }));
    await form.locator('#remoteSubmitButton').click();
    await sleep(300);
    h.assert(rows() === '0' && await form.locator('#remoteSubmitButton').isEnabled(),
      'Canceled template submission saved or left the toolbar disabled');
    // A real trusted click exposes the browser's microtask checkpoint between listeners;
    // a synthetic dispatch or a Node event stub alone cannot verify this ordering.
    await form.evaluate(() => window.addEventListener('submit', event => event.preventDefault(), { once: true }));
    await form.locator('#remoteSubmitButton').click();
    await sleep(300);
    h.assert(rows() === '0' && await form.locator('#remoteSubmitButton').isEnabled(),
      'Late window cancellation saved or trapped the next attempt');
    h.assert(await form.locator('#oscar-spinner-screen.active-oscar-spinner').count() === 0, 'Late cancellation left an overlay blocking edits');
    await form.evaluate(() => window.addEventListener('beforeunload', event => {
      event.preventDefault(); event.returnValue = '';
    }, { once: true }));
    const dialogs = await h.withExpectedDialogs(form, () => Promise.all([
      form.waitForEvent('dialog', { predicate: dialog => dialog.type() === 'beforeunload' }),
      form.locator('#remoteSubmitButton').click({ noWaitAfter: true }),
    ]), { accept: false });
    await sleep(300);
    h.assert(dialogs.length === 1 && dialogs[0].type === 'beforeunload', 'Expected a canceled unsaved-form navigation prompt');
    h.assert(rows() === '0' && await form.locator('#remoteSubmitButton').isEnabled(),
      'Canceled navigation saved or trapped the next attempt');
    h.assert(await form.locator('#oscar-spinner-screen.active-oscar-spinner').count() === 0, 'Canceled navigation left an overlay blocking edits');
    await form.locator('#remoteSubmitButton').click();
    h.assert(await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
      AND form_name=${q(formName)} AND subject=${q(subject)}`) === 1, 'Corrected form did not save exactly once');
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid
      WHERE d.demographic_no=${patient} AND d.form_name=${q(formName)} AND d.subject=${q(subject)}
      AND v.var_name='SubmitButton' AND v.var_value='Submit'`) === '1', 'Named submitter was disabled or lost');
    if (!form.isClosed()) await form.close();
  });

  await s.step('independent editing windows can each save the same content', async () => {
    const first = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-window-one');
    // The list deliberately reuses a named popup. A separate browser tab represents the
    // independent editing opportunity; another list click would simply replace the first page.
    const second = await s.context.newPage();
    await h.gotoApp(second, s.config.baseUrl, `/eform/efmformadd_data?fid=${fid}&demographic_no=${patient}`);
    const subject = `${marker}-WINDOWS`;
    const tokens = [];
    for (const form of [first, second]) {
      await form.locator('#remoteSubmitButton').waitFor();
      tokens.push(await form.locator('input[name=carlosEformSubmission]').inputValue());
      await form.locator('#remote_eform_subject').fill(subject);
      await form.locator('#note').fill('independent window');
      await form.locator('#remoteSubmitButton').click();
    }
    h.assert(tokens[0] !== tokens[1], 'Separate windows shared a submission identity');
    h.assert(await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
      AND form_name=${q(formName)} AND subject=${q(subject)}`, { min: 2 }) === 2, 'Legitimate separate form was discarded');
    const savedId = sql.value(`SELECT MIN(fdid) FROM eform_data WHERE demographic_no=${patient}
      AND form_name=${q(formName)} AND subject=${q(subject)}`);
    // Open the saved instance through its authorized view, then save a real new revision.
    const revision = await s.context.newPage();
    await h.gotoApp(revision, s.config.baseUrl, `/eform/efmshowform_data?fdid=${savedId}`);
    await revision.locator('#remoteSubmitButton').waitFor();
    h.assert(!tokens.includes(await revision.locator('input[name=carlosEformSubmission]').inputValue()),
      'Reopened saved form retained an already consumed identity');
    await revision.locator('#remote_eform_subject').fill(`${marker}-REVISION`);
    await revision.locator('#note').fill('edited saved form');
    await revision.locator('#remoteSubmitButton').click();
    h.assert(await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
      AND form_name=${q(formName)} AND subject=${q(`${marker}-REVISION`)}`) === 1, 'Saved-form revision was lost');
    for (const form of [first, second, revision]) if (!form.isClosed()) await form.close();
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-eform', workflow, { openPatient: true });
