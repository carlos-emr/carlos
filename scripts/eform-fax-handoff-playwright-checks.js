#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * eForm Save-and-Fax handoff check (fax preview preparation is POST-only; PR #2474, issue #2466).
 *
 * User path: the owned eForm opened for the owned patient (eform/efmformadd_data, the page the eChart's
 * eForm list opens) > type a subject and a field > open the toolbar's fax options and enter a recipient >
 * toolbar Fax (#remoteFaxButton). The eForm is opened directly rather than through the master record and
 * eChart: the toolbar Fax and everything after it is what this check pins, and the route to the form is
 * covered by the eForm and eChart checks.
 *
 * The toolbar saves the eForm (POST /eform/addEForm). The save answers with EFormFaxPreparation.jsp, whose
 * own form then POSTs /fax/faxAction?method=prepareFax naming the saved eForm, and preparation renders the
 * fax cover page. The check asserts that chain end to end, through whatever front door BASE_URL names:
 *   1. the save answers 200 itself (no redirect) with Cache-Control: no-store, and stores ONE eform_data row;
 *   2. exactly one POST reaches prepareFax, naming that saved fdid, the owned patient and the typed
 *      recipient, with a CSRF token as its only body field (no clinical field is replayed to the fax route);
 *      no GET ever reaches prepareFax;
 *   3. the cover page carries the same references and renders the staged preview;
 *   4. a bare GET of the same preparation URL is refused 405 with Allow: POST;
 *   5. replaying the save POST is refused 409, and replaying the preparation POST (what a reload of the
 *      cover page sends) renders a cover page again: neither creates a second eform_data row, so a reload
 *      cannot repeat the clinical save (the review finding on the earlier dispatcher-forward design);
 *   6. Cancel on the cover page returns to the saved eForm, and nothing was queued to the owned sender line.
 *
 * Fixtures: the owned synthetic patient (runWorkflow), one owned eForm template, and one owned active SRFax
 * sender with a random number (rx-fax-account-fixture); like double-submit-eform it may move the eform_data
 * id counter past the demo database's stale EFormDocs ids. The sender only enables the toolbar's Fax button and
 * lets preparation reach the cover page; nothing is sent. Both staged previews are cancelled through the
 * application (the cover page's Cancel, and a cancel POST for the replayed one). Cleanup deletes the instances
 * (eform_values + eform_data), the template and the sender, and asserts them gone.
 */
'use strict';
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { settledCount } = require('./lib/double-submit-helpers');
const { stageRxFaxAccount, cleanupRxFaxAccount } = require('./rx-fax-account-fixture');

const q = h.sqlString;
const SAVE_PATH = /\/eform\/addEForm$/i;
const FAX_PATH = /\/fax\/faxAction$/;
const PREPARE_TIMEOUT_MS = 180000;

function isPrepare(url) {
  const parsed = new URL(url);
  return FAX_PATH.test(parsed.pathname) && parsed.searchParams.get('method') === 'prepareFax';
}

// A fixed pattern over every name/value pair, compared by name: no RegExp is built from an argument.
function hiddenValue(html, name) {
  for (const [, field, value] of html.matchAll(/name="([A-Za-z]+)" value="([^"]*)"/g)) {
    if (field === name) return value.replace(/&#x2f;|&#47;/gi, '/').replace(/&amp;/g, '&');
  }
  return '';
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const formName = `${marker} fax form`;
  const recipientName = `${marker} Recipient`;
  const recipientFax = `555${String(randomInt(0, 10_000_000)).padStart(7, '0')}`;
  const html = '<html><head><title>fax handoff fixture</title></head><body>'
    + `<form method="post" action="" name="FormName" id="FormName"><h2>${marker.replace(/-/g, '')}</h2>`
    + '<input type="text" name="subject" id="subject"><input type="text" name="note" id="note">'
    + '<input type="submit" value="Submit" id="SubmitButton"></form></body></html>';
  let fid;
  let sender;

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
  s.cleanup(() => {
    if (!sender) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM faxes WHERE faxline=${q(sender.faxNumber)}`) === '0',
      'A fax job was queued on the owned sender line although the check never pressed Send');
    cleanupRxFaxAccount(sql, sender);
  });

  sender = stageRxFaxAccount(sql, `9${String(randomInt(0, 1_000_000_000)).padStart(9, '0')}`);
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'','fax handoff fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The eForm template fixture was not created');
  // The demo database ships EFormDocs rows for ids that no eform_data row owns any more (see
  // double-submit-eform-playwright-checks.js). An instance saved at one of those ids inherits the stale
  // attachments, and its fax packet would render them too. Move the id counter past them with an owned row
  // inserted at an explicit id and deleted again.
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

  const subject = `${marker}-FAX`;
  const rowCount = () => sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
    AND form_name=${q(formName)} AND subject=${q(subject)}`);
  const requests = [];
  const recordRequest = (request) => requests.push({ method: request.method(), url: request.url() });
  s.context.on('request', recordRequest);

  let form;
  let save;
  let prepare;
  let fdid;
  await s.step('Fax from the eForm toolbar saves once and hands the saved eForm to preparation by POST', async () => {
    form = await s.context.newPage();
    await h.gotoApp(form, s.config.baseUrl, `/eform/efmformadd_data?fid=${fid}&demographic_no=${patient}`);
    const faxButton = form.locator('#remoteFaxButton');
    await faxButton.waitFor({ state: 'visible' });
    h.assert(await faxButton.isEnabled(), 'The toolbar Fax button is disabled although an active fax sender exists');
    await form.locator('#remote_eform_subject').fill(subject);
    await form.locator('#note').fill('fax handoff clinical text');
    await form.locator('#remoteFaxOptions summary').click();
    await form.locator('#remoteFaxNumber').fill(recipientFax);
    // Typing a name runs the recipient directory search. Let it answer before Fax navigates away, or the
    // in-flight lookup is aborted and reported as a failed request.
    await Promise.all([
      form.waitForResponse((r) => /\/fax\/SearchFaxRecipient$/.test(new URL(r.url()).pathname), { timeout: 30000 }),
      form.locator('#remoteFaxRecipient').fill(recipientName),
    ]);
    await form.locator('#remoteFaxRecipient').press('Escape');

    const saved = form.waitForResponse((r) => r.request().method() === 'POST' && SAVE_PATH.test(new URL(r.url()).pathname),
      { timeout: 60000 });
    const prepared = form.waitForResponse((r) => isPrepare(r.url()), { timeout: PREPARE_TIMEOUT_MS });
    await faxButton.click();
    save = await saved;
    prepare = await prepared;

    h.assert(save.status() === 200, `The eForm save answered HTTP ${save.status()}, not the preparation page`);
    h.assert(!save.headers().location, 'The eForm save redirected instead of answering with the preparation page');
    h.assert(/no-store/i.test(save.headers()['cache-control'] || ''),
      'The preparation page may be cached, so Back could show a stale handoff');
    h.assert(await settledCount(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}
      AND form_name=${q(formName)} AND subject=${q(subject)}`, { min: 1, quietMs: 1500 }) === 1,
    'Save and fax did not store exactly one eForm instance');
    fdid = sql.value(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)} AND subject=${q(subject)}`);

    const request = prepare.request();
    h.assert(request.method() === 'POST', `Preparation was requested by ${request.method()}`);
    const params = new URL(request.url()).searchParams;
    h.assert(params.get('transactionType') === 'EFORM', 'Preparation does not name an eForm transaction');
    h.assert(params.get('transactionId') === fdid, 'Preparation names a different eForm than the one just saved');
    h.assert(params.get('demographicNo') === patient, 'Preparation names a different patient than the eForm');
    h.assert(params.get('recipientFaxNumber') === recipientFax, 'The typed recipient fax number was not carried to preparation');
    h.assert(params.get('recipient') === recipientName, 'The typed recipient name was not carried to preparation');
    const body = new URLSearchParams(request.postData() || '');
    const fields = [...body.keys()];
    h.assert(fields.length === 1 && fields[0] === 'CSRF-TOKEN' && body.get('CSRF-TOKEN'),
      `The preparation POST must carry only its CSRF token (fields: ${fields.join(', ') || 'none'})`);
    h.assert(!(request.postData() || '').includes('fax handoff clinical text'),
      'Clinical form text was replayed to the fax route');
    h.assert(prepare.status() === 200, `Preparation answered HTTP ${prepare.status()}`);
    const preparePosts = requests.filter((r) => r.method === 'POST' && isPrepare(r.url));
    h.assert(preparePosts.length === 1, `Expected one preparation POST, saw ${preparePosts.length}`);
    h.assert(!requests.some((r) => r.method !== 'POST' && isPrepare(r.url)), 'A non-POST request reached prepareFax');
  });

  await s.step('the cover page carries the saved references and renders the staged preview', async () => {
    const cover = form.locator('#coverPageForm');
    await cover.waitFor({ state: 'visible', timeout: 30000 });
    h.assert(await cover.locator('input[name="transactionId"]').inputValue() === fdid, 'Cover page names another eForm');
    h.assert(await cover.locator('input[name="transactionType"]').inputValue() === 'EFORM', 'Cover page is not an eForm fax');
    h.assert(await cover.locator('input[name="demographicNo"]').inputValue() === patient, 'Cover page names another patient');
    h.assert(await form.locator('#searchProfessionalSpecialist_fax').inputValue() === recipientFax,
      'Cover page lost the recipient fax number');
    h.assert(await form.locator('#searchProfessionalSpecialist_name').inputValue() === recipientName,
      'Cover page lost the recipient name');
    h.assert(await cover.locator('input[name="faxFilePath"]').inputValue(), 'Cover page has no staged fax file');
    await form.locator('#previewImages img').first().waitFor({ state: 'visible', timeout: 90000 });
    await form.waitForFunction(() => {
      const img = document.querySelector('#previewImages img');
      return img && img.complete && img.naturalWidth > 0;
    }, null, { timeout: 60000 }).catch(() => { throw new Error('The staged fax preview image did not load'); });
  });

  await s.step('a bare GET of the preparation URL is refused 405 with Allow: POST', async () => {
    const probe = await s.context.request.get(prepare.url(), { maxRedirects: 0 });
    h.assert(probe.status() === 405, `GET prepareFax answered HTTP ${probe.status()}, expected 405`);
    h.assert(probe.headers().allow === 'POST', `GET prepareFax answered Allow: ${probe.headers().allow || '(none)'}`);
    h.assert(rowCount() === '1', 'The refused GET changed the saved eForm rows');
  });

  await s.step('replaying the save is refused and replaying preparation cannot repeat the clinical save', async () => {
    const original = save.request();
    const replayedSave = await s.context.request.post(original.url(), {
      data: original.postData(), headers: { 'Content-Type': original.headers()['content-type'] }, maxRedirects: 0,
    });
    h.assert(replayedSave.status() === 409, `A replayed eForm save answered HTTP ${replayedSave.status()}, expected 409`);

    // A reload of the cover page re-sends the preparation POST, never the save.
    const replayedPrepare = await s.context.request.post(prepare.request().url(), {
      data: prepare.request().postData(),
      headers: { 'Content-Type': prepare.request().headers()['content-type'] }, maxRedirects: 0,
    });
    h.assert(replayedPrepare.status() === 200, `A replayed preparation answered HTTP ${replayedPrepare.status()}`);
    const page = await replayedPrepare.text();
    const faxFilePath = hiddenValue(page, 'faxFilePath');
    h.assert(faxFilePath, 'The replayed preparation did not render a cover page with a staged preview');
    h.assert(hiddenValue(page, 'transactionId') === fdid, 'The replayed preparation names another eForm');
    h.assert(rowCount() === '1', 'Replaying the handoff created a second eForm instance');
    // The replay staged a second preview for this session; cancel it here, while the session that owns it is
    // signed in (workflow cleanups run after the browser has closed).
    const cancelled = await s.context.request.post(h.appUrl(s.config.baseUrl, '/fax/faxAction'), {
      form: {
        method: 'cancel', transactionType: 'EFORM', transactionId: fdid, demographicNo: patient, faxFilePath,
        'CSRF-TOKEN': new URLSearchParams(prepare.request().postData()).get('CSRF-TOKEN'),
      },
      maxRedirects: 0,
    });
    h.assert(cancelled.status() === 302, `The replayed fax preview could not be cancelled (HTTP ${cancelled.status()})`);
  });

  await s.step('Cancel on the cover page returns to the saved eForm without queueing a fax', async () => {
    const back = form.waitForURL((url) => url.pathname.endsWith('/eform/efmshowform_data'), { timeout: 30000 });
    await form.locator('#btnCancel').click();
    await back;
    h.assert(new URL(form.url()).searchParams.get('fdid') === fdid, 'Cancel returned to another eForm');
    await h.assertNotErrorPage(form, 'saved eForm after cancelling the fax');
    h.assert(sql.value(`SELECT COUNT(*) FROM faxes WHERE faxline=${q(sender.faxNumber)}`) === '0',
      'Cancelling the cover page queued a fax');
    h.assert(rowCount() === '1', 'Cancelling the fax changed the saved eForm rows');
  });
  s.context.off('request', recordRequest);
}

module.exports = { workflow };
if (require.main === module) runWorkflow('eform-fax-handoff', workflow, { openPatient: true, openMaster: false });
