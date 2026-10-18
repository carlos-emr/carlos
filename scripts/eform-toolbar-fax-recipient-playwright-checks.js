#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * eForm toolbar ▸ Fax to a recipient typed in its options: the eForm is saved once and reaches the fax cover page addressed to
 * that recipient, and Cancel discards it. Send is never pressed, so nothing can leave the host.
 *
 * WHY THIS CHECK EXISTS. The eForm toolbar's Fax (remoteFax) saves the eForm with the chosen recipient
 * (eform/addEForm faxEForm=true, recipient, recipientFaxNumber), which hands off through EFormFaxPreparation's auto-submit to
 * fax/faxAction method=prepareFax and the cover page. The toolbar's options let the clinician type a recipient name and fax
 * number in place of the eForm's own. eform-saved-render drives the toolbar's Fax only behind EFORM_FAX_PREVIEW_CHECK, to
 * test preview ownership on cancellation; nothing chose a recipient and looked at where the cover page sends. A fax addressed
 * to the eForm's old number instead of the one typed, or a second saved eForm, sends patient information to the wrong place.
 *
 * User path (as the shared test login): Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ eForms "+" (the Add eForm list) ▸ the
 * owned eForm ▸ subject ▸ the toolbar's fax options (▾): recipient name and fax number ▸ Fax ▸ the cover page ▸ Cancel.
 * Asserted:
 *   - With an active fax account: Fax posts the save with faxEForm=true and exactly the typed recipient and number, saves one
 *     instance of the owned eForm for the patient, and opens the cover page for that instance (transactionType EFORM, its id
 *     and the patient) with the typed recipient and number filled in; the staged PDF carries the eForm's own text; no fax job
 *     exists after the save, after the preparation page's auto-submit or on the cover page. Cancel answers, the staged copy
 *     is no longer served, and still no fax job exists.
 *   - With no active fax account: the toolbar's Fax is disabled ("No active fax senders"), and nothing is saved or queued.
 *   - Where the eForm cannot be rendered to PDF (no eForm render browser service), the check SKIPs after the save.
 * Fixtures: the owned FAKE- patient and one owned eForm template (eform row named with the run marker) in the clinic-wide eForm
 * library while the run lasts, so the check must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup
 * removes the template, its instances and their eform_values, and asserts them gone. Needs pdftotext. The recipient number
 * is 555-0177, which is never assigned.
 */
const { spawnSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const NAME = 'eform-toolbar-fax-recipient';
const TIMEOUT = 90000;
const q = h.sqlString;
const RECIPIENT = { name: 'FAKE-PW Fax Recipient', fax: '555-0177' };

function pdfText(bytes) {
  const text = spawnSync('pdftotext', ['-', '-'], { input: bytes });
  h.assert(text.status === 0, 'The staged fax PDF is not readable');
  return text.stdout.toString().replace(/\s+/g, ' ');
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  if (spawnSync('pdftotext', ['-v']).error) throw new h.SkipCheck('Install Poppler pdftotext to read the staged fax PDF');
  const formName = `${marker} fax form`;
  const heading = marker.replace(/-/g, '');
  const html = '<html><head><title>fax fixture</title></head><body>'
    + `<form method="post" action="" name="FormName" id="FormName"><h2>${heading}</h2>`
    + '<input type="text" name="note" id="note"></form></body></html>';
  const faxJobs = () => sql.value(`SELECT COUNT(*) FROM faxes WHERE demographicNo=${patient}`);
  const instances = () => sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}
    ORDER BY fdid`).map(([fdid]) => fdid);
  let fid;
  s.cleanup(() => {
    for (const fdid of instances()) {
      h.assert(/^[1-9]\d*$/.test(fdid), 'Owned eForm instance id is invalid');
      sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid}; DELETE FROM eform_data WHERE fdid=${fdid} AND demographic_no=${patient}`);
    }
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})`) === '0', 'Owned eForm rows were not removed');
  });
  h.assert(faxJobs() === '0', 'The owned patient already has fax jobs');
  fid = h.insertId(sql, `INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'','fax fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1)`, 'eForm template');
  const accounts = sql.value('SELECT COUNT(*) FROM fax_config WHERE active=1');

  const chart = await s.chart();
  const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
  await list.locator('#efmTable').waitFor();
  const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-fax');
  const faxButton = form.locator('#remoteFaxButton');
  await faxButton.waitFor({ state: 'visible', timeout: TIMEOUT });

  if (accounts === '0') {
    await s.step('with no active fax account, the toolbar\'s Fax is disabled and nothing is saved or queued', async () => {
      h.assert(await faxButton.isDisabled() && /No active fax senders/.test(await faxButton.getAttribute('title') || ''),
        'The toolbar\'s Fax is offered with no active fax account');
      h.assert(instances().length === 0 && faxJobs() === '0', 'An eForm was saved or a fax queued');
    });
    return;
  }

  let fax;
  let fdid;
  await s.step('Fax saves the eForm once and opens the cover page for the typed recipient, queueing nothing', async () => {
    h.assert(await faxButton.isEnabled(), 'The toolbar\'s Fax is disabled although a fax account is active');
    await form.locator('#remote_eform_subject').fill(`${marker} fax`);
    await form.locator('#note').fill('faxed by the check');
    await form.locator('#remoteFaxOptions summary').click();
    await form.locator('#remoteFaxRecipient').fill(RECIPIENT.name);
    await form.locator('#remoteFaxNumber').fill(RECIPIENT.fax);
    const saved = form.waitForResponse((r) => r.request().method() === 'POST' && /\/eform\/addEForm$/i.test(new URL(r.url()).pathname),
      { timeout: TIMEOUT });
    const prepared = form.waitForResponse((r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/fax/faxAction')
      && new URL(r.url()).searchParams.get('method') === 'prepareFax', { timeout: TIMEOUT });
    prepared.catch(() => {});
    await faxButton.click();
    const save = await saved;
    const posted = new URLSearchParams(save.request().postData() || '');
    h.assert(posted.get('faxEForm') === 'true' && posted.get('recipient') === RECIPIENT.name
      && posted.get('recipientFaxNumber') === RECIPIENT.fax,
    `Fax posted recipient "${posted.get('recipient')}" / "${posted.get('recipientFaxNumber')}", not the typed one`);
    h.assert(instances().length === 1, `Fax saved ${instances().length} instances of the eForm, not one`);
    [fdid] = instances();
    h.assert(faxJobs() === '0', 'Saving the eForm for fax queued a fax job');
    const preparation = await prepared.catch(() => null);
    if (!preparation || preparation.status() >= 400) {
      throw new h.SkipCheck('This install did not prepare the eForm fax (no eForm render browser service?); nothing was queued');
    }
    fax = form;
    const cover = fax.locator('#coverPageForm');
    await cover.waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(faxJobs() === '0', 'Preparing the fax queued a fax job');
    const value = (name) => cover.locator(`input[name="${name}"]`).first().inputValue();
    h.assert(await value('transactionType') === 'EFORM' && await value('transactionId') === fdid && await value('demographicNo') === patient,
      'The cover page is not for the saved eForm instance and its patient');
    h.assert(await fax.locator('#searchProfessionalSpecialist_name').inputValue() === RECIPIENT.name,
      'The cover page is not addressed to the typed recipient name');
    const number = (await fax.locator('#searchProfessionalSpecialist_fax').inputValue()).replace(/\D/g, '');
    h.assert(number === RECIPIENT.fax.replace(/\D/g, ''), `The cover page fax number is "${number}", not the typed one`);
    const preview = await s.context.request.get(await fax.locator('#previewPdfLink').evaluate((a) => a.href));
    const bytes = await preview.body();
    h.assert(preview.status() === 200 && bytes.subarray(0, 4).toString('latin1') === '%PDF', 'The cover page\'s staged PDF is not a PDF');
    h.assert(pdfText(bytes).includes(heading), 'The staged PDF is not the saved eForm');
    h.assert(faxJobs() === '0', 'Loading the cover page queued a fax job');
  });

  await s.step('Cancel discards the staged copy and queues nothing', async () => {
    const previewHref = await fax.locator('#previewPdfLink').evaluate((a) => a.href);
    const [cancel] = await Promise.all([
      fax.waitForResponse((r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/fax/faxAction')
        && new URLSearchParams(r.request().postData() || '').get('method') === 'cancel', { timeout: TIMEOUT }),
      fax.locator('#btnCancel').click(),
    ]);
    h.assert(cancel.status() < 400, `Cancel answered HTTP ${cancel.status()}`);
    h.assert(faxJobs() === '0', 'Cancel queued a fax job');
    const after = await s.context.request.get(previewHref);
    h.assert(!(after.status() === 200 && (await after.body()).subarray(0, 4).toString('latin1') === '%PDF'),
      'The staged copy is still served after Cancel');
    h.assert(instances().length === 1, 'Cancel changed the number of saved eForm instances');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
