#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

/*
 * Issue #4130: forms built in script, and forms inside HTML the Administration
 * panel inserts after load, posted WITHOUT a CSRF token. CarlosCsrfGuardFilter
 * answered 403, nothing was written, and the operator saw a button that did
 * nothing. The fix is the CARLOS-patched CSRFGuard client
 * (src/main/resources/csrfguard/carlos-csrfguard.js).
 *
 * This check drives every workflow the issue lists, by clicking the controls an
 * operator clicks, and for each one asserts all three halves of the round trip:
 *   1. every same-origin POST the browser sent carried CSRF-TOKEN (body or header);
 *   2. no response was a 403/405 or an error page (strict page wiring);
 *   3. the database change the control promises actually landed.
 *
 * Workflows: Report by Template ▸ Delete; patient eForm delete + restore;
 * patient-independent eForm delete + restore (Administration panel); eForm
 * Groups remove-from-group + delete-group (Administration panel); Manage
 * Billing Form add + change bill type + delete; Ontario Billing History ▸
 * Unbill; Billing Reconciliation ▸ Summary + Settle; dx code search ▸ Update
 * (numerically named controls); Messenger ▸ View Message ▸ Link to Patient.
 *
 * FIXTURES: every row is owned by this run (marked with the run's FAKE-PW
 * marker or a per-run code) and removed in cleanup; the patient is the
 * synthetic one runWorkflow creates. The dx update re-submits the code's own
 * description, so the shared reference row is rewritten with its current value.
 * Ontario only (Billing History, Manage Billing Form, RA and dx search are ON).
 */

const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NUMERIC_ID = /^[1-9]\d*$/;

/** Every same-origin POST must carry the token; record the ones that did not. */
function watchTokens(context, baseUrl) {
  const missing = [];
  const appPrefix = baseUrl.origin + baseUrl.pathname.replace(/\/$/, '');
  context.on('request', request => {
    if (request.method() !== 'POST' || !request.url().startsWith(appPrefix)) return;
    const headers = request.headers();
    const body = request.postData() || '';
    const inBody = /(^|&)CSRF-TOKEN=[^&]+/.test(body) || /name="CSRF-TOKEN"\r?\n\r?\n[^\r\n]+/.test(body);
    if (!headers['csrf-token'] && !inBody) missing.push(h.pathOnly(request.url()));
  });
  return {
    assertNone(label) {
      const seen = missing.splice(0);
      h.assert(!seen.length, `${label}: POST sent without a CSRF token: ${seen.join(', ')}`);
    },
  };
}

async function open(s, path) {
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, path);
  await page.waitForLoadState('networkidle').catch(() => {});
  await h.assertNotErrorPage(page, path);
  return page;
}

/** Click and accept exactly one confirm(), then let the resulting navigation settle. */
async function clickConfirmed(page, locator, expectText) {
  const dialogs = await h.withExpectedDialogs(page, async () => {
    await Promise.all([
      page.waitForEvent('framenavigated', { timeout: 20000 }).catch(() => {}),
      locator.click(),
    ]);
    await page.waitForLoadState('networkidle').catch(() => {});
  });
  h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && expectText.test(dialogs[0].text),
    `expected one confirm() matching ${expectText}, saw ${JSON.stringify(dialogs.map(d => d.text))}`);
}

/**
 * Click a control that posts into a popup, and wait for that popup to finish.
 * The wait and the click are started together: a wait created first and left
 * pending when the click throws would reject unhandled and kill the run before
 * its cleanup.
 */
async function clickPostsToPopup(page, locator, { openerReloads = false } = {}) {
  const [popup, reloaded] = await Promise.all([
    page.context().waitForEvent('page', { timeout: 20000 }),
    // Some result pages resubmit the opener's form before closing; wait for that load
    // so the next navigation does not race it.
    openerReloads ? page.waitForEvent('load', { timeout: 20000 }) : Promise.resolve(null),
    locator.click(),
  ]);
  await popup.waitForEvent('close', { timeout: 20000 }).catch(() => {});
  if (reloaded) await page.waitForLoadState('networkidle').catch(() => {});
  return popup;
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const tokens = watchTokens(s.context, s.config.baseUrl);
  const step = (label, body) => s.step(label, async () => {
    await body();
    tokens.assertNone(label);
  });

  await step('Report by Template: Delete Template posts with a token and removes the template', async () => {
    const id = sql.value(`INSERT INTO reportTemplates(templatetitle,templatedescription,templatesql,templatexml,active)
      VALUES(${h.sqlString(marker)},'csrf check','SELECT 1',
      ${h.sqlString(`<report title="${marker}" description="csrf check" active="1"><query>SELECT 1</query></report>`)},1);
      SELECT LAST_INSERT_ID()`);
    h.assert(NUMERIC_ID.test(id), 'report template fixture was not created');
    s.cleanup(() => sql.execute(`DELETE FROM reportTemplates WHERE templateid=${id} AND templatetitle=${h.sqlString(marker)}`));
    const home = await open(s, '/oscarReport/reportByTemplate/ViewHomePage');
    await Promise.all([home.waitForURL(/ViewReportConfiguration/), home.getByRole('link', { name: marker }).first().click()]);
    await clickConfirmed(home, home.locator('#optionsDiv a', { hasText: 'Delete Template' }), /delete this report template/);
    await expectValue(sql, `SELECT COUNT(*) FROM reportTemplates WHERE templateid=${id}`, '0', 'the template was not deleted');
    await home.close();
  });

  await step('Patient eForm: delete and restore both post with a token', async () => {
    const fdid = sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,form_time,
        form_provider,form_data,showLatestFormOnly,patient_independent,roleType)
      SELECT fid,form_name,${h.sqlString(marker)},${patient},1,CURDATE(),CURTIME(),${h.sqlString(provider)},
        '<html></html>',0,0,NULL FROM eform WHERE status=1 ORDER BY fid LIMIT 1; SELECT LAST_INSERT_ID()`);
    h.assert(NUMERIC_ID.test(fdid), 'patient eForm fixture was not created');
    s.cleanup(() => sql.execute(`DELETE FROM eform_data WHERE fdid=${fdid} AND subject=${h.sqlString(marker)}`));
    const list = await open(s, `/eform/efmpatientformlist?demographic_no=${patient}`);
    const row = list.locator('#efmTable tr', { hasText: marker });
    await clickConfirmed(list, row.getByRole('link', { name: 'Delete' }), /delete this eform/);
    await expectValue(sql, `SELECT status FROM eform_data WHERE fdid=${fdid}`, '0', 'the patient eForm was not deleted');
    await Promise.all([list.waitForURL(/efmpatientformlistdeleted/), list.getByRole('link', { name: /Deleted eForms/i }).first().click()]);
    await clickConfirmed(list, list.locator('#efmTable tr', { hasText: marker }).getByRole('link', { name: 'Restore' }),
      /restore this eform/);
    await expectValue(sql, `SELECT status FROM eform_data WHERE fdid=${fdid}`, '1', 'the patient eForm was not restored');
    await list.close();
  });

  const admin = await open(s, '/administration');
  const openFormsSection = async () => {
    const toggle = admin.locator('[data-bs-target="#collapseForms"]');
    if (await admin.locator('#collapseForms').isHidden()) await toggle.click();
  };

  await step('Independent eForm (Administration panel): delete and restore both post with a token', async () => {
    const fdid = sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,form_time,
        form_provider,form_data,showLatestFormOnly,patient_independent)
      SELECT fid,form_name,${h.sqlString(marker)},0,1,CURDATE(),CURTIME(),${h.sqlString(provider)},
        '<html></html>',0,1 FROM eform WHERE status=1 ORDER BY fid LIMIT 1; SELECT LAST_INSERT_ID()`);
    h.assert(NUMERIC_ID.test(fdid), 'independent eForm fixture was not created');
    s.cleanup(() => sql.execute(`DELETE FROM eform_data WHERE fdid=${fdid} AND subject=${h.sqlString(marker)}`));
    await openFormsSection();
    await admin.locator('a.contentLink', { hasText: 'Patient-independent eForm' }).first().click();
    const row = admin.locator('#dynamic-content tr', { hasText: marker });
    await row.waitFor();
    await clickConfirmed(admin, row.getByRole('link', { name: 'Delete' }), /delete this eform/);
    await expectValue(sql, `SELECT status FROM eform_data WHERE fdid=${fdid}`, '0', 'the independent eForm was not deleted');
    // The delete redirect lands on the standalone list; reach the deleted list the way it offers.
    await Promise.all([admin.waitForURL(/efmmanageindependentdeleted/).catch(() => {}),
      admin.getByRole('link', { name: /Deleted eforms/i }).first().click()]);
    const deletedRow = admin.locator('tr', { hasText: marker });
    await deletedRow.waitFor();
    await clickConfirmed(admin, deletedRow.getByRole('link', { name: 'Restore' }), /restore this eform/);
    await expectValue(sql, `SELECT status FROM eform_data WHERE fdid=${fdid}`, '1', 'the independent eForm was not restored');
  });

  await step('eForm Groups (Administration panel): remove from group and delete group post with a token', async () => {
    const group = `PW${marker.slice(-12)}`;
    const fid = sql.value('SELECT fid FROM eform WHERE status=1 ORDER BY fid LIMIT 1');
    h.assert(NUMERIC_ID.test(fid), 'no active eForm to put in the fixture group');
    sql.execute(`INSERT INTO eform_groups(fid,group_name) VALUES (0,${h.sqlString(group)}),(${fid},${h.sqlString(group)})`);
    s.cleanup(() => sql.execute(`DELETE FROM eform_groups WHERE group_name=${h.sqlString(group)}`));
    await h.gotoApp(admin, s.config.baseUrl, '/administration?show=FormsGroups');
    await admin.waitForLoadState('networkidle').catch(() => {});
    await admin.locator('#groupListTbl a.contentLink', { hasText: group }).click();
    const member = admin.locator('#dynamic-content a[title="remove from group"]').first();
    await member.waitFor();
    await member.click();
    await Promise.all([admin.waitForEvent('framenavigated').catch(() => {}), admin.locator('#dataConfirmed').click()]);
    await admin.waitForLoadState('networkidle').catch(() => {});
    await expectValue(sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(group)} AND fid=${fid}`, '0',
      'the eForm was not removed from the group');
    await h.assertNotErrorPage(admin, 'after remove from group');
    await h.gotoApp(admin, s.config.baseUrl, '/administration?show=FormsGroups');
    await admin.waitForLoadState('networkidle').catch(() => {});
    await admin.locator('#groupListTbl tr', { hasText: group }).locator('a[title="delete this group"]').click();
    await Promise.all([admin.waitForEvent('framenavigated').catch(() => {}), admin.locator('#dataConfirmed').click()]);
    await admin.waitForLoadState('networkidle').catch(() => {});
    await expectValue(sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(group)}`, '0',
      'the group was not deleted');
    await h.assertNotErrorPage(admin, 'after delete group');
  });
  await admin.close();

  await step('Manage Billing Form: add, change bill type and delete all post with a token', async () => {
    let code;
    for (let i = 0; i < 20 && !code; i += 1) {
      const candidate = `Z${Math.random().toString(36).slice(2, 4).toUpperCase()}`;
      if (/^Z[A-Z0-9]{2}$/.test(candidate)
        && sql.value(`SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(candidate)}`) === '0') code = candidate;
    }
    h.assert(code, 'no free three-character billing form code');
    s.cleanup(() => sql.execute(['ctl_billingservice', 'ctl_diagcode', 'ctl_billingtype']
      .map(table => `DELETE FROM ${table} WHERE servicetype=${h.sqlString(code)}`).join(';')));
    const page = await open(s, '/billing/CA/ON/ManageBillingform?billingform=000');
    const form = page.locator('form[name="servicetypeform"]');
    await form.locator('[name="typeid"]').fill(code);
    await form.locator('[name="type"]').fill(marker.slice(0, 20));
    for (const g of ['group1', 'group2', 'group3']) await form.locator(`[name="${g}"]`).fill(`${g} ${code}`);
    await Promise.all([page.waitForURL(/ManageBillingform/), form.locator('[name="addForm"]').click()]);
    await expectValue(sql, `SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(code)}`, '3',
      'the billing form was not added');
    await h.gotoApp(page, s.config.baseUrl, '/billing/CA/ON/ManageBillingform?billingform=000');
    await page.locator('a[title="Manage Billing Form"]', { hasText: code }).first().click();
    await page.locator('#manage_type select[name="billtype_new"]').waitFor();
    await page.locator('#manage_type select[name="billtype_new"]').selectOption('PAT');
    await clickPostsToPopup(page, page.locator('#manage_type input[value="Change"]'), { openerReloads: true });
    await expectValue(sql, `SELECT billtype FROM ctl_billingtype WHERE servicetype=${h.sqlString(code)}`, 'PAT',
      'the bill type was not changed');
    await h.gotoApp(page, s.config.baseUrl, '/billing/CA/ON/ManageBillingform?billingform=000');
    await page.locator('a[title="Manage Billing Form"]', { hasText: code }).first().click();
    const del = page.locator('#manage_type input[value="Delete Billing Form"]');
    await del.waitFor();
    const deleteDialogs = await h.withExpectedDialogs(page, () => clickPostsToPopup(page, del, { openerReloads: true }));
    h.assert(deleteDialogs.length === 1 && /delete the billing form/.test(deleteDialogs[0].text),
      'deleting the billing form did not ask for confirmation once');
    await expectValue(sql, `SELECT COUNT(*) FROM ctl_billingservice WHERE servicetype=${h.sqlString(code)}`, '0',
      'the billing form was not deleted');
    await page.close();
  });

  await step('Ontario Billing History: Unbill posts with a token and deletes the bill', async () => {
    const billNo = sql.value(`INSERT INTO billing_on_cheader1(header_id,demographic_no,provider_no,appointment_no,
        billing_date,billing_time,total,paid,status,pay_program,comment1)
      VALUES(0,${patient},${h.sqlString(provider)},0,CURDATE(),CURTIME(),'33.70','0.00','O','HCP',${h.sqlString(marker)});
      SELECT LAST_INSERT_ID()`);
    h.assert(NUMERIC_ID.test(billNo), 'bill fixture was not created');
    s.cleanup(() => sql.execute(`DELETE FROM billing_on_proc WHERE object=${h.sqlString(billNo)}
      AND action IN ('updateBillingStatus','updateBillingStatus-items');
      DELETE FROM billing_on_item WHERE ch1_id=${billNo};
      DELETE FROM billing_on_cheader1 WHERE id=${billNo} AND demographic_no=${patient}`));
    const page = await open(s, `/billing/CA/ON/ViewBillingONHistory?demographic_no=${patient}`);
    const unbill = page.locator(`#billingHistoryTable a[onclick*="onUnbilled('${billNo}'"]`);
    const unbillDialogs = await h.withExpectedDialogs(page, () => clickPostsToPopup(page, unbill));
    h.assert(unbillDialogs.length === 1 && /delete the previous billing/.test(unbillDialogs[0].text),
      'Unbill did not ask for confirmation once');
    await expectValue(sql, `SELECT status FROM billing_on_cheader1 WHERE id=${billNo}`, 'D', 'the bill was not deleted');
    await page.close();
  });

  await step('Billing Reconciliation: Summary and Settle post with a token', async () => {
    const filename = `PW${marker.slice(-20)}`;
    // Under _site_access_privacy (granted to the demo administrator) the RA list shows a
    // remittance only through an radetail row billed by a provider sharing one of the
    // user's sites (RaHeaderDaoImpl.findByStatusAndProviderMagic). Settle then needs that
    // row's bill to exist, so the remittance pays one fixture bill.
    const ohip = sql.value(`SELECT p.ohip_no FROM provider p JOIN providersite s ON s.provider_no=p.provider_no
      WHERE s.site_id IN (SELECT site_id FROM providersite WHERE provider_no=${h.sqlString(provider)})
      ORDER BY p.ohip_no='' , p.provider_no=${h.sqlString(provider)} DESC LIMIT 1`)
      || sql.value(`SELECT ohip_no FROM provider WHERE provider_no=${h.sqlString(provider)}`);
    const billNo = sql.value(`INSERT INTO billing_on_cheader1(header_id,demographic_no,provider_no,appointment_no,
        billing_date,billing_time,total,paid,status,pay_program,comment1)
      VALUES(0,${patient},${h.sqlString(provider)},0,CURDATE(),CURTIME(),'33.70','0.00','O','HCP',${h.sqlString(marker)});
      SELECT LAST_INSERT_ID()`);
    h.assert(NUMERIC_ID.test(billNo), 'RA bill fixture was not created');
    const raNo = sql.value(`INSERT INTO raheader(filename,paymentdate,payable,totalamount,records,claims,status,readdate,content)
      VALUES(${h.sqlString(filename)},DATE_FORMAT(CURDATE(),'%Y%m%d'),'CSRF CHECK','33.70','1','1','N',CURDATE(),'');
      SELECT LAST_INSERT_ID()`);
    h.assert(NUMERIC_ID.test(raNo), 'RA fixture was not created');
    s.cleanup(() => sql.execute(`DELETE FROM radetail WHERE raheader_no=${raNo};
      DELETE FROM raheader WHERE raheader_no=${raNo} AND filename=${h.sqlString(filename)};
      DELETE FROM billing_on_proc WHERE object=${h.sqlString(billNo)};
      DELETE FROM billing_on_cheader1 WHERE id=${billNo} AND comment1=${h.sqlString(marker)}`));
    sql.execute(`INSERT INTO radetail(raheader_no,providerohip_no,billing_no,service_code,service_count,hin,amountclaim,
        amountpay,service_date,error_code,billtype,claim_no)
      VALUES(${raNo},${h.sqlString(ohip || '')},${billNo},'A007A','1','','3370','3370',DATE_FORMAT(CURDATE(),'%Y%m%d'),'','HCP','')`);
    // Opened the way the Administration panel opens it: no opener. The settle page
    // used to throw on self.opener.refresh() there; it must return to the RA list.
    const ra = await open(s, '/billing/CA/ON/ViewGenRA');
    const row = () => ra.locator('tr', { has: ra.locator(`a[onclick*="'${raNo}'"]`) });
    const [summaryPage] = await Promise.all([ra.context().waitForEvent('page', { timeout: 20000 }),
      row().getByRole('link', { name: 'Summary' }).click()]);
    await summaryPage.waitForLoadState('networkidle').catch(() => {});
    await h.assertNotErrorPage(summaryPage, 'RA summary');
    await summaryPage.close();
    await clickConfirmed(ra, row().getByRole('link', { name: 'Settle', exact: true }), /reconcile the file/);
    await expectValue(sql, `SELECT status FROM raheader WHERE raheader_no=${raNo}`, 'S', 'the remittance was not settled');
    await ra.waitForURL(/\/billing\/CA\/ON\/(ViewGenRA|ViewOnGenRA)(\?|$)/, { timeout: 20000 });
    await row().getByRole('link', { name: 'S35', exact: true }).waitFor();
    h.assert(await row().getByRole('link', { name: 'Settle', exact: true }).count() === 0,
      'the RA list still offers Settle for a settled remittance');
    await ra.close();
  });

  await step('dx code search: numerically named controls get a token and Update posts with it', async () => {
    const page = await open(s, '/billing/CA/ON/ViewBillingDigSearch?codedesc=25');
    const token = await page.locator('#diagcode input[name="CSRF-TOKEN"]').inputValue().catch(() => '');
    h.assert(token, 'the dx update form has no CSRF token (the numeric control names broke injection)');
    // A code with exactly one row, so re-submitting its own description changes nothing.
    const codes = await page.locator('#diagcode input[type="text"]').evaluateAll(inputs => inputs.map(i => i.name));
    let code = '';
    for (const candidate of codes) {
      if (/^[0-9A-Z]{3,5}$/.test(candidate)
        && sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code=${h.sqlString(candidate)}`) === '1') {
        code = candidate;
        break;
      }
    }
    h.assert(code, 'no single-row dx code in the search results');
    const before = sql.value(`SELECT description FROM diagnosticcode WHERE diagnostic_code=${h.sqlString(code)}`);
    // The update trims the seeded column padding; put the exact bytes back afterwards.
    s.cleanup(() => sql.execute(`UPDATE diagnosticcode SET description=${h.sqlString(before)}
      WHERE diagnostic_code=${h.sqlString(code)}`));
    const update = page.waitForResponse(r => r.url().includes('/BillingDigUpdate') && r.request().method() === 'POST');
    await page.locator(`#diagcode input[name="update"][value$=" ${code}"]`).click();
    h.assert((await update).status() === 200, 'the dx update was not accepted');
    await page.waitForLoadState('networkidle').catch(() => {});
    h.assert(/Successful/i.test(await page.locator('body').innerText()), 'the dx update did not report success');
    h.assert(sql.value(`SELECT description FROM diagnosticcode WHERE diagnostic_code=${h.sqlString(code)}`).trim() === before.trim(),
      'the dx description changed although it was re-submitted unchanged');
    await page.close();
  });

  await step('Messenger: Link to Patient posts with a token and links the message', async () => {
    const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1') || '0';
    const messageId = sql.value(`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,
        sentByLocation,type)
      VALUES(CURDATE(),CURTIME(),'csrf check',${h.sqlString(marker)},'UI test','UI test',${h.sqlString(provider)},
        ${location},0); SELECT LAST_INSERT_ID()`);
    h.assert(NUMERIC_ID.test(messageId), 'message fixture was not created');
    sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${messageId},${h.sqlString(provider)},'new',${location},0,0)`);
    s.cleanup(() => sql.execute(`DELETE FROM msgDemoMap WHERE messageID=${messageId};
      DELETE FROM messagelisttbl WHERE message=${messageId};
      DELETE FROM messagetbl WHERE messageid=${messageId} AND thesubject=${h.sqlString(marker)}`));
    const page = await open(s, `/messenger/ViewMessage?messageID=${messageId}&demographic_no=${patient}`);
    const linked = page.waitForResponse(r => r.request().method() === 'POST' && /\/messenger\/ViewMessage(\?|$)/.test(r.url()));
    await page.locator('input[name="linkDemo"]').click();
    h.assert((await linked).status() === 200, 'the link-to-patient POST was not accepted');
    await expectValue(sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${messageId} AND demographic_no=${patient}`, '1',
      'the message was not linked to the patient');
    await page.close();
  });
}

if (require.main === module) runWorkflow('csrf-runtime-forms', workflow, { openMaster: false });
module.exports = { workflow };
