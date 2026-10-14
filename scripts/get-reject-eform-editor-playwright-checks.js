#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, eForm editor. User path: Schedule ▸ Administration ▸ Forms/eForms ▸
 * Manage eForms (library in #dynamic-content) ▸ the owned form's pencil ▸ edit the HTML ▸
 * Save (multipart POST eform/editForm).
 *
 * HtmlEdit2Action (eform/editForm) binds fid/formName/formSubject/formHtml through Struts
 * setters, which the params interceptor fills from a query string as readily as from a
 * multipart body, and has no POST check; "editForm" has no mutator prefix, the action is
 * outside the GET-rejection contract, and finding L99 lists other eForm mutators but not
 * this one. A GET therefore replaces any eForm template's HTML (or, with an empty fid,
 * creates a new template) -- markup every clinician then renders. The check saves through
 * the page, asserts eform.form_html, then replays the captured fields as GET/HEAD with a
 * different (tag-free, so the WAF's XSS rules do not mask the application) text, and
 * asserts 405 and an unchanged template in the LAST step.
 *
 * Fixtures: one eform row named from the run marker, seeded by SQL; cleanup deletes it by
 * fid and name and asserts it is gone.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');

const NAME = 'get-reject-eform-editor';

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const formName = `${marker} eform`;
  const html = `<html><body><p>${marker} original</p></body></html>`;
  let fid;
  s.cleanup(() => {
    sql.execute(`DELETE FROM eform WHERE form_name=${q(formName)}${fid ? ` OR (fid=${fid} AND form_name=${q(formName)})` : ''}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)}`) === '0', 'The owned eForm was not removed');
  });
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
      showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'',${q(`${marker} subject`)},CURDATE(),CURTIME(),'get-reject',1,${q(html)},0,0,'',0,0);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The owned eForm was not created');
  const savedHtml = () => sql.value(`SELECT form_html FROM eform WHERE fid=${fid}`);
  const editedMarker = `${marker} edited through the page`;
  let saved;

  await s.step('Administration ▸ Manage eForms ▸ pencil ▸ Save stores the edited HTML (multipart POST eform/editForm)', async () => {
    const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    await admin.locator('a.defaultForms').first().click();
    const row = admin.locator('#dynamic-content #eformTbl tbody tr', { hasText: formName }).first();
    await row.waitFor({ state: 'visible', timeout: 30000 });
    await row.locator('a.contentLink[href*="efmformmanageredit?fid="]').first().click();
    const textarea = admin.locator('#dynamic-content textarea[name="formHtml"]');
    await textarea.waitFor({ state: 'visible', timeout: 30000 });
    h.assert((await textarea.inputValue()).includes(`${marker} original`), 'The editor did not load the owned eForm');
    await textarea.fill(`<html><body><p>${editedMarker}</p></body></html>`);
    saved = await captureRequest(admin, url => url.pathname.endsWith('/eform/editForm'),
      () => admin.locator('#dynamic-content #savebtn').click(), { timeout: 60000 });
    h.assert(saved.status > 0 && saved.status < 400, `eform/editForm answered ${saved.status}`);
    h.assert(saved.params.get('fid') === fid, 'The editor posted a different fid');
    await expectValue(sql, `SELECT LOCATE(${q(editedMarker)}, form_html) > 0 FROM eform WHERE fid=${fid}`, '1',
      'The edited HTML was not stored');
  });

  await s.step('the editor save replayed as GET/HEAD with a different template text is recorded', async () => {
    await ledger.probe(s, { label: 'eform/editForm (HtmlEdit2Action)', path: saved.path,
      params: replayParams(saved.params, { formHtml: `${marker} template text replaced by GET` }), snapshot: savedHtml });
  });

  await s.step('the eForm editor refused GET/HEAD and the owned template is unchanged', async () => {
    ledger.assertAllRefused();
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
