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
 * Last (pinned to app-findings-log.md finding 168): a login that may save eForms but holds no `_edoc` x
 * presses Add to Documents. The eForm is stored, the eDoc step is then refused, and the answer must not be
 * the replay refusal ("This form can no longer be submitted from this page"): that text tells the
 * clinician nothing failed and that the page was submitted twice.
 * Fixtures: one owned eForm template (marker name) and the owned FAKE- patient; for finding 168 one owned
 * role holding `_eform` w, `_demographic` r and `_eChart` r (no `_edoc`) and a login holding it; cleanup
 * deletes the instances (eform_values + eform_data) for the owned patient/form and the template, removes the
 * role and login, and asserts they are gone.
 * Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const { bundleMessage } = require('./lib/throwaway-login-fixture');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep, recorderMark, forgiveAbortedSecondRequest } = require('./lib/double-submit-helpers');

const q = h.sqlString;

/**
 * Put the page in the state a clinician's is in when a browser shows its "Leave site?" prompt: the user
 * has clicked in it, and a beforeunload handler asks to stay.
 *
 * TWO BROWSER RULES, BOTH ARTEFACTS OF THE TEST BROWSER AND NOT OF THE APPLICATION. Chromium suppresses
 * the prompt, and logs "Blocked attempt to show a 'beforeunload' confirmation panel for a frame that never
 * had a user gesture since its load", in these cases (reproduced on the packaged Chromium 154):
 *   1. the frame never had a user gesture. locator.fill() inserts text and is not a gesture, so the page
 *      is clicked first: a real, trusted click on the fixture's own heading, a neutral element with no
 *      handler, so it cannot change the form, press a toolbar button or start the submission measured;
 *   2. the handler removes ITSELF while it runs, which is what `addEventListener(..., { once: true })` does.
 *      The message blames the gesture, but a gesture does not help: the same page and click show the prompt
 *      for a handler that stays registered. So the handler disarms itself from a timer, after the browser
 *      has taken the prompt, which keeps the one-shot behaviour the later steps rely on (the corrected
 *      form must save without a second prompt).
 */
async function armUnsavedChangesPrompt(form) {
  const heading = form.locator('h2').first();
  await heading.waitFor({ state: 'visible' });
  await heading.click();
  await form.evaluate(() => {
    const prompt = event => {
      setTimeout(() => window.removeEventListener('beforeunload', prompt), 0);
      event.preventDefault(); event.returnValue = '';
    };
    window.addEventListener('beforeunload', prompt);
  });
}

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
  // The demo database ships EFormDocs rows (attached documents, labs, forms, HRM reports and eForms) for ids
  // 247-1063 that no eform_data row owns any more, because the eForm instances were pruned and the id counter
  // restarted below them. An instance saved at one of those ids inherits the stale attachments, and a login that
  // cannot read those object types is then refused with 403 ("no permission to use one or more selected
  // attachments") BEFORE the eForm is stored: a fixture artefact that production never meets (ids are not reused),
  // and not the failure the Add to Documents step below pins. Move the counter past them with an owned row
  // inserted at an explicit id and deleted again, which is all an AUTO_INCREMENT column needs.
  const staleAttachmentHigh = Number(sql.value('SELECT COALESCE(MAX(fdid),0) FROM EFormDocs'));
  const nextInstanceId = Number(sql.value(`SELECT AUTO_INCREMENT FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='eform_data'`));
  if (nextInstanceId <= staleAttachmentHigh) {
    const seed = staleAttachmentHigh + 1;
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${seed}`) === '0', 'The eForm instance id used to move the id counter is occupied');
    sql.execute(`INSERT INTO eform_data(fdid,fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,form_data,
      showLatestFormOnly,patient_independent,roleType)
      VALUES(${seed},${fid},${q(formName)},${q(`${marker}-IDSEED`)},${patient},1,CURDATE(),CURTIME(),${q(provider)},'',0,0,'')`);
    sql.execute(`DELETE FROM eform_data WHERE fdid=${seed} AND form_name=${q(formName)} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${seed}`) === '0', 'The eForm instance used to move the id counter was not removed');
  }
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
        await armUnsavedChangesPrompt(form);
        const dialogs = await h.withExpectedDialogs(form, activate);
        h.assert(dialogs.length === 1 && dialogs[0].type === 'beforeunload', 'Expected an accepted unsaved-form navigation prompt');
      } else await activate();
      // The saved result page closes itself only after its 5 s success alert, so the replay reload (2.5 s after the
      // click) should land on the still-open result page. rapid() swallows a "Target page closed" error, so prove the
      // reload really ran: the window is still open, or the reload re-sent the POST.
      if (mode.key === 'replay') {
        h.assert(!form.isClosed() || posts.seen.length >= 2,
          'The eForm window closed before the replay reload ran, so the POST replay was never exercised');
      }
      const count = await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
        AND form_name=${q(formName)} AND subject=${q(subject)}`, { min: 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      await sleep(300);
      forgiveAbortedSecondRequest(s.recorder, since, /\/eform\//);
      if (mode.key === 'replay') {
        // Statuses and the owned-row count ride in the messages (no body text: it is the form's own answer): a
        // replay that fails here is either a lost response (the reload cancelled the first POST in flight, a
        // timing artefact) or a second save, and the two read differently only by these numbers.
        h.assert(responses.some(response => response.status() === 409),
          `Reload did not reject the consumed submission (AddEForm responses seen: [${responses.map(response => response.status()).join(', ')}]; owned rows: ${count})`);
        const conflict = responses.find(response => response.status() === 409);
        const conflictText = await conflict.text().catch(error => `<body unavailable: ${String(error.message).split('\n')[0]}>`);
        h.assert(conflictText.includes('Check the patient'),
          `Replay did not explain how to check the saved eForm (HTTP 409 body of ${conflictText.length} characters)`);
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
        `Server accepted a repeated eForm POST or omitted recovery guidance (HTTP ${repeated.status()})`);
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid
        WHERE d.demographic_no=${patient} AND d.form_name=${q(formName)} AND d.subject=${q(subject)}
        AND v.var_name='note' AND v.var_value='double submit'`) === '1', 'Clinical field was lost or duplicated');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid
        WHERE d.demographic_no=${patient} AND d.form_name=${q(formName)}
        AND v.var_name='carlosEformSubmission'`) === '0', 'Submission identity was stored as clinical form data');
      const afterReplay = sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
        AND form_name=${q(formName)} AND subject=${q(subject)}`);
      h.assert(afterReplay === '1', `Direct replay created a duplicate (owned rows: ${afterReplay})`);
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
    await armUnsavedChangesPrompt(form);
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

  // ---- Finding 168: a failure after the eForm is stored is reported as a replay -------------------
  // AddEForm2Action answers every RuntimeException after the eForm is committed with the replay 409
  // (rejectSubmission(false): eform.submitUnavailable). Add to Documents moves the PDF into the document
  // store, which DocumentManagerImpl.moveDocument refuses without `_edoc` x.
  const replayText = bundleMessage('eform.submitUnavailable', "This form can no longer be submitted from this page.")
    .split('. ')[0].replace(/\.$/, '');
  let noEdoc;
  await s.step('Add to Documents by a login without _edoc x stores the eForm once and files no document', async () => {
    const fixture = authzReadFixture({ sql, marker, provider, testUser: s.config.testUser });
    s.cleanup(() => fixture.cleanup());
    const role = fixture.addRole({ _eform: 'w', _demographic: 'r', _eChart: 'r' });
    const login = fixture.addLogin(role);
    const subject = `${marker}-NOEDOC`;
    const restricted = await signIn(s, login);
    try {
      const page = await restricted.context.newPage();
      await h.gotoApp(page, s.config.baseUrl, `/eform/efmformadd_data?fid=${fid}&demographic_no=${patient}`);
      await page.locator('#remoteSaveEdocumentButton').waitFor({ state: 'visible' });
      await page.locator('#remote_eform_subject').fill(subject);
      await page.locator('#note').fill('no document rights');
      const [response] = await Promise.all([
        page.waitForResponse(r => r.request().method() === 'POST' && /\/eform\/addEForm$/i.test(new URL(r.url()).pathname)),
        page.locator('#remoteSaveEdocumentButton').click(),
      ]);
      noEdoc = { status: response.status(), text: await response.text() };
      await page.close();
    } finally {
      await restricted.context.close();
    }
    const stored = await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
      AND form_name=${q(formName)} AND subject=${q(subject)}`, { min: 1, quietMs: 1500 });
    h.assert(stored === 1, `The eForm was stored ${stored} time(s), expected exactly once (HTTP ${noEdoc.status})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc=${q(subject)}`) === '0',
      'A document was filed for a login that may not add documents');
  });

  // Pinned: holds only the assertion finding 168 breaks.
  await s.step('a failed Add to Documents is not answered with the replay refusal', async () => {
    h.assert(!noEdoc.text.includes(replayText),
      `The answer to a failed Add to Documents (HTTP ${noEdoc.status}) is the replay refusal "${replayText}.", which is what a `
      + 'replayed submission gets and hides that the eForm was saved and only the document step failed');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-eform', workflow, { openPatient: true });
