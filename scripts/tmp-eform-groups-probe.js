#!/usr/bin/env node
/* temporary probe -- deleted after research */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickInjectsPanel} = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');
async function workflow(s) {
  const group = `PW${s.marker.slice(-16)}`;
  let fid;
  s.cleanup(() => { s.sql.execute(`DELETE FROM eform_groups WHERE group_name=${h.sqlString(group)}`);
    if (fid) s.sql.execute(`DELETE FROM eform WHERE fid=${fid}`); });
  fid = s.sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable) VALUES(${h.sqlString(s.marker)},'','x',CURDATE(),CURTIME(),'x',1,'<html></html>',0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  const clear = () => { s.recorder.badResponses.length = 0; s.recorder.consoleIssues.length = 0; s.recorder.pageErrors.length = 0; };
  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(), {context: s.context, recorder: s.recorder, label: 'adm', timeout: 20000});
  admin.on('response', r => { if (r.request().method() === 'POST') console.log('POST', r.status(), new URL(r.url()).pathname, r.headers()['location'] || ''); });
  admin.on('pageerror', e => console.log('PAGEERROR', e.message));
  await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
  const open = async () => { await clickInjectsPanel(admin, admin.locator('a.defaultFormsGroups').first(), {marker: '#dynamic-content #groupListTbl'}); };
  await open();
  await admin.locator('#addGroupForm input[name="groupName"]').pressSequentially(group);
  await admin.locator('#addGroupForm input.groupAdd').click();
  await admin.waitForTimeout(2500);
  console.log('after addGroup rows', s.sql.rows(`SELECT fid FROM eform_groups WHERE group_name=${h.sqlString(group)}`));
  console.log('panel text', (await admin.locator('#dynamic-content').innerText()).slice(0, 200).replace(/\s+/g, ' '));
  clear();
  // reload panel with group selected
  await admin.goto(admin.url()); // research only
  await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
  await open();
  await admin.locator(`#groupListTbl a.contentLink`, {hasText: group}).click();
  await admin.waitForTimeout(2000);
  await admin.locator('#addEform-btn').click();
  await admin.locator('#eformSelect').selectOption(fid);
  await admin.locator('#eformToGroup-btn').click();
  await admin.waitForTimeout(2500);
  console.log('after addToGroup rows', s.sql.rows(`SELECT fid FROM eform_groups WHERE group_name=${h.sqlString(group)}`));
  clear();
  await admin.goto(admin.url());
  await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
  await open();
  await admin.locator(`#groupListTbl a.contentLink`, {hasText: group}).click();
  await admin.waitForTimeout(2000);
  const row = admin.locator('#dynamic-content .card').nth(1).locator('tr', {hasText: s.marker});
  await row.locator('a[data-confirm]').click();
  await admin.locator('#dataConfirmed').click();
  await admin.waitForTimeout(3000);
  console.log('after removeFromGroup rows', s.sql.rows(`SELECT fid FROM eform_groups WHERE group_name=${h.sqlString(group)}`), admin.url());
  console.log('page text', (await admin.locator('body').innerText()).slice(0, 300).replace(/\s+/g, ' '));
  clear();
  await admin.goto(admin.url().replace(/\/eforms\/removeFromGroup.*/, '/administration'));
  await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
  await open();
  const grow = admin.locator('#groupListTbl tbody tr').filter({has: admin.locator(`td[title="${group}"]`)});
  await grow.locator('a[data-confirm]').click();
  await admin.locator('#dataConfirmed').click();
  await admin.waitForTimeout(3000);
  console.log('after delGroup rows', s.sql.rows(`SELECT fid FROM eform_groups WHERE group_name=${h.sqlString(group)}`), admin.url());
  console.log('page text', (await admin.locator('body').innerText()).slice(0, 300).replace(/\s+/g, ' '));
  clear();
}
runWorkflow('tmp-probe', workflow, {openPatient: false});
