#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// #4477: preparation failures must show a dedicated error page, preserve the saved letter and
// retry this window's draft. Reach Email through E-Chart > eForms > the owned form's toolbar.
// Remove an owned selected attachment just before compose to reproduce a preparation failure;
// restore that exact fixture before Retry. No email is sent and no shared clinical row is changed.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 60000;
const HTML = '<html><head><title>Email error fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton">'
  + '</form></body></html>';

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const name = `${marker} Email error`;
  const note = `${marker} Saved letter`;
  let fid;
  let attachment;
  let form;
  let fdid;
  let composeUrl;
  const requests = [];

  s.cleanup(() => {
    if (!fid) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${fid} AND form_name=${q(name)}`) === '1',
      'Owned eForm template changed');
    const owned = `SELECT fdid FROM eform_data WHERE fid=${fid} AND demographic_no=${patient}`;
    const ids = sql.rows(owned).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned eForm id');
    sql.execute(`DELETE FROM EFormDocs WHERE fdid IN (${owned});
      DELETE FROM eform_values WHERE fdid IN (${owned});
      DELETE FROM eform_data WHERE fid=${fid} AND demographic_no=${patient};
      DELETE FROM eform WHERE fid=${fid} AND form_name=${q(name)}`);
    if (ids.length) h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM EFormDocs WHERE fdid IN (${ids.join(',')}))
      + (SELECT COUNT(*) FROM eform_values WHERE fdid IN (${ids.join(',')}))`) === '0',
    'Owned attachment relations or values remain');
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE fid=${fid})
      + (SELECT COUNT(*) FROM eform_data WHERE fid=${fid})`) === '0', 'Owned eForm rows remain');
  });
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
    status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(name)},'',${q(name)},CURDATE(),CURTIME(),${q(provider)},1,${q(HTML)},0,0,'',0,1);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned template was not created');
  const seedAttachment = id => sql.value(`INSERT INTO eform_data(fdid,fid,form_name,subject,demographic_no,
    status,form_date,form_time,form_provider,form_data,showLatestFormOnly,patient_independent,roleType)
    VALUES(${id || 'NULL'},${fid},${q(name)},'Attachment',${patient},1,CURDATE(),CURTIME(),
      ${q(provider)},${q(HTML)},0,0,'');
    SELECT fdid FROM eform_data WHERE fid=${fid} AND demographic_no=${patient} AND subject='Attachment'`);
  attachment = seedAttachment();
  h.assert(/^[1-9]\d*$/.test(attachment), 'Owned attachment was not created');

  await s.step('Email saves the letter and shows a preparation error without rendering an empty eForm', async () => {
    const chart = await s.chart();
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'email-error-form-list');
    await list.locator('#efmTable').waitFor();
    form = await s.popup(list, list.locator('#efmTable').getByRole('link', { name, exact: true }), 'email-error-form');
    await form.locator('#note').fill(note);
    // Select only our own attachment. It becomes unavailable after save, before compose.
    await form.locator('#FormName').evaluate((element, id) => {
      const input = document.createElement('input');
      input.type = 'hidden'; input.name = 'eFormNo'; input.value = id;
      element.appendChild(input);
    }, attachment);
    form.on('request', request => requests.push(request.url()));
    await form.route('**/email/emailComposeAction?**', async route => {
      sql.execute(`DELETE FROM eform_data WHERE fdid=${attachment} AND fid=${fid} AND demographic_no=${patient}`);
      await route.continue();
    }, { times: 1 });
    await h.withExpectedDialogs(form, async () => {
      await Promise.all([form.waitForURL(/\/email\/emailComposeAction/, { timeout: TIMEOUT }),
        form.locator('#remoteEmailButton').click()]);
    }, { promptText: 'Yes' });
    await form.locator('#emailComposeErrorMessage').waitFor({ timeout: TIMEOUT });
    composeUrl = form.url();
    h.assert((await form.locator('#emailComposeErrorMessage').innerText()).includes('attachment could not be prepared'),
      'Preparation failure is not visible');
    h.assert((await form.locator('body').innerText()).includes('Your eForm has been saved. No email has been sent.'),
      'Saved-form recovery instructions are missing');
    h.assert(await form.locator('#FormName, #emailComposeForm').count() === 0, 'Error page rendered a form or composer');
    const css = form.locator('link[rel="stylesheet"]');
    const cssUrl = await css.evaluate(element => element.href);
    h.assert(new URL(cssUrl).pathname.startsWith(`${s.config.baseUrl.pathname}/library/bootstrap/`),
      'Stylesheet resolves relative to /email/');
    h.assert(await css.evaluate(element => !!element.sheet), 'Error-page stylesheet did not load');
    h.assert(!requests.some(url => /\/email\/(?:efmformrtl_templates|library\/)/.test(url)),
      'Error page requested an eForm resource under /email/');
    fdid = sql.value(`SELECT fdid FROM eform_data WHERE fid=${fid} AND demographic_no=${patient}`);
    h.assert(/^[1-9]\d*$/.test(fdid), 'The letter was not saved');
    h.assert(sql.value(`SELECT var_value FROM eform_values WHERE fdid=${fdid} AND var_name='note'`) === note,
      'The saved letter lost its typed value');
    h.assert(sql.value(`SELECT COUNT(*) FROM emailLog WHERE demographicNo=${patient}`) === '0', 'An email was sent');
    await h.screenshot(form, s.config.screenshotDir, 'eform-email-error');
  });

  await s.step('Retry keeps the same draft while the attachment remains unavailable', async () => {
    await Promise.all([form.waitForEvent('domcontentloaded'), form.locator('#retryEmailCompose').click()]);
    await form.locator('#emailComposeErrorMessage').waitFor({ timeout: TIMEOUT });
    h.assert(form.url() === composeUrl, 'Retry changed the compose URL or draft key');
    h.assert(await form.locator('#draftExpiredMessage').count() === 0, 'Failed preparation consumed the draft');
  });

  await s.step('Retry prepares the original saved letter once its attachment is available again', async () => {
    h.assert(seedAttachment(attachment) === attachment, 'Owned attachment was not restored');
    await Promise.all([form.waitForEvent('domcontentloaded', { timeout: TIMEOUT }),
      form.locator('#retryEmailCompose').click()]);
    await form.locator('#emailComposeForm').waitFor({ timeout: TIMEOUT });
    h.assert(form.url() === composeUrl, 'Recovery changed the draft key');
    h.assert(await form.locator('.attachmentName').count() === 2, 'Retry did not prepare both original attachments');
    h.assert(sql.value(`SELECT var_value FROM eform_values WHERE fdid=${fdid} AND var_name='note'`) === note,
      'Retry changed the saved letter');
    h.assert(sql.value(`SELECT COUNT(*) FROM emailLog WHERE demographicNo=${patient}`) === '0', 'Retry sent an email');
  });
}

if (require.main === module) runWorkflow('eform-email-error', workflow, { openPatient: true });
module.exports = { workflow };
