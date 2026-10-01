#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * eForm group management, from the administrator's panel to the clinician's chart filter.
 *
 * User path: Schedule > Administration > Forms > eForm Groups (panel in #dynamic-content):
 * Add Group, Add eForm (modal), then E-Chart > eForms "+" (Add eForm list) and E-Chart > eForms
 * heading (patient eForm list), each filtered by the group in "View Group", and finally back in
 * the panel: remove the eForm from the group and delete the group.
 * Asserts eform_groups rows after every mutation (eform/addGroup, eform/addToGroup,
 * eforms/removeFromGroup, eforms/delGroup) and that both chart lists (eform/efmformslistadd,
 * eform/efmpatientformlist) show the grouped eForm and hide the ungrouped one.
 * Fixtures: two owned eForm templates named with the run marker, one saved instance of each for
 * the owned synthetic patient, and an owned group named PW<marker hex> (eform_groups.group_name is
 * varchar(20)). Cleanup deletes only those group rows, instances and templates and asserts each gone.
 * Implements coverage plan section 2.6 eform-groups.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickInjectsPanel, clickAndAwaitReload} = require('./lib/playwright-ui');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TEMPLATE_HTML = '<html><head><title>eForm group fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton">'
  + '</form></body></html>';

async function workflow(s) {
  const group = `PW${s.marker.slice(-16)}`;
  const grouped = `${s.marker} Grouped`;
  const loose = `${s.marker} Loose`;
  const fids = [];
  const fdids = [];
  const groupRows = (fid = null) => s.sql.value(`SELECT COUNT(*) FROM eform_groups
    WHERE group_name=${h.sqlString(group)}${fid === null ? '' : ` AND fid=${fid}`}`);
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM eform_groups WHERE group_name=${h.sqlString(group)}`);
    h.assert(groupRows() === '0', 'Owned eForm group rows were not removed');
    for (const fdid of fdids) {
      s.sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid};
        DELETE FROM eform_data WHERE fdid=${fdid} AND demographic_no=${s.patient}`);
    }
    for (const fid of fids) {
      s.sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name LIKE ${h.sqlString(s.marker + '%')}`);
    }
    const left = [...fids, 0].join(',');
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE fid IN (${left}) AND fid<>0)
      + (SELECT COUNT(*) FROM eform_data WHERE fid IN (${left}) AND fid<>0)`) === '0',
    'Owned eForm templates or instances were not removed');
  });
  for (const name of [grouped, loose]) {
    const fid = s.sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
      status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${h.sqlString(name)},'',${h.sqlString(name)},CURDATE(),CURTIME(),${h.sqlString(s.provider)},
      1,${h.sqlString(TEMPLATE_HTML)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fid), 'eForm template fixture was not created');
    fids.push(fid);
    const fdid = s.sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,
      form_time,form_provider,form_data,showLatestFormOnly,patient_independent,roleType)
      VALUES(${fid},${h.sqlString(name)},${h.sqlString(name)},${s.patient},1,CURDATE(),CURTIME(),
      ${h.sqlString(s.provider)},${h.sqlString(TEMPLATE_HTML)},0,0,''); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fdid), 'eForm instance fixture was not created');
    fdids.push(fdid);
  }
  const [groupedFid, looseFid] = fids;

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'eform-groups-admin', timeout: 20000});
  const panel = admin.locator('#dynamic-content');
  async function postPanel(control, path) {
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(path)),
      control.click(),
    ]);
    h.assert(response.status() === 200, `${path} answered HTTP ${response.status()}`);
    await panel.locator('#groupListTbl').waitFor();
  }

  await s.step('the Groups panel opens from Administration > Forms', async () => {
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    await clickInjectsPanel(admin, admin.locator('a.defaultFormsGroups').first(), {marker: '#dynamic-content #groupListTbl'});
    h.assert(await panel.locator('#groupListTbl td[title]').filter({hasText: group}).count() === 0,
      'The owned group existed before it was added');
  });

  await s.step('Add Group stores the group marker row and selects the new group', async () => {
    const name = panel.locator('#addGroupForm input[name="groupName"]');
    await name.pressSequentially(group);
    const add = panel.locator('#addGroupForm input.groupAdd');
    h.assert(await add.isEnabled(), 'Add Group stayed disabled for a new group name');
    await postPanel(add, '/eform/addGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(group)} AND fid=0`, '1',
      'Add Group did not store the group');
    h.assert(await panel.locator('#groupListTbl tr.table-success td[title]').getAttribute('title') === group,
      'The new group is not the selected group after Add Group');
  });

  await s.step('a duplicate group name is refused by the form before any POST', async () => {
    const name = panel.locator('#addGroupForm input[name="groupName"]');
    await name.pressSequentially(group);
    await panel.locator('.textExists').waitFor();
    h.assert(await panel.locator('#addGroupForm input.groupAdd').isDisabled(), 'Add Group was enabled for a duplicate name');
    await name.fill('');
    await name.press('Backspace');
    h.assert(groupRows() === '1', 'Typing a duplicate group name wrote a row');
  });

  await s.step('Add eForm to Group stores exactly the chosen eForm in the group', async () => {
    await panel.locator('#addEform-btn').click();
    await admin.locator('#myModal').waitFor();
    await admin.locator('#eformSelect').selectOption(groupedFid);
    await postPanel(admin.locator('#eformToGroup-btn'), '/eform/addToGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(group)} AND fid=${groupedFid}`,
      '1', 'Add eForm to Group did not store the eForm');
    h.assert(groupRows(looseFid) === '0' && groupRows() === '2', 'Add eForm to Group stored an eForm that was not chosen');
    const contents = panel.locator('.card').nth(1);
    h.assert(await contents.getByText(grouped, {exact: true}).count() === 1
      && await contents.getByText(loose, {exact: true}).count() === 0, 'The group contents panel does not list exactly the added eForm');
  });

  const chart = await s.chart();
  await s.step('E-Chart Add eForm list filtered by the group offers only the grouped eForm', async () => {
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    h.assert(await list.locator('#efmTable').getByText(loose, {exact: true}).count() === 1,
      'The unfiltered Add eForm list does not offer the ungrouped eForm');
    const groupLink = list.locator('.grouplist li a').filter({hasText: group});
    h.assert(/\(\s*1\s*\)/.test(await groupLink.innerText()), 'The Add eForm group list does not count one eForm in the group');
    await clickAndAwaitReload(list, groupLink, {label: 'Add eForm group filter'});
    h.assert(new URL(list.url()).searchParams.get('group_view') === group, 'The group filter did not submit the group');
    await list.locator('#efmTable').waitFor();
    h.assert(await list.locator('#efmTable').getByText(grouped, {exact: true}).count() === 1
      && await list.locator('#efmTable').getByText(loose, {exact: true}).count() === 0,
    'The group-filtered Add eForm list does not offer exactly the grouped eForm');
    await list.close();
  });

  await s.step('E-Chart patient eForm list filtered by the group shows only the grouped instance', async () => {
    const list = await s.popup(chart, chart.locator('a[onclick*="/eform/efmpatientformlist?"]').first(), 'eform-patient-list');
    const table = list.locator('#efmTable');
    await table.waitFor();
    h.assert(await table.getByText(grouped, {exact: true}).count() === 1 && await table.getByText(loose, {exact: true}).count() === 1,
      'The unfiltered patient eForm list does not show both saved instances');
    const groupLink = list.locator('.grouplist li a').filter({hasText: group});
    h.assert(/\(\s*1\s*\)/.test(await groupLink.innerText()), 'The patient group list does not count one saved instance in the group');
    await clickAndAwaitReload(list, groupLink, {label: 'patient eForm group filter'});
    await table.waitFor();
    h.assert(await table.getByText(grouped, {exact: true}).count() === 1 && await table.getByText(loose, {exact: true}).count() === 0,
      'The group-filtered patient eForm list does not show exactly the grouped instance');
    await clickAndAwaitReload(list, list.locator('.grouplist li a').first(), {label: 'patient eForm Show All'});
    await table.waitFor();
    h.assert(await table.getByText(loose, {exact: true}).count() === 1, 'Show All did not clear the group filter');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid IN (${fdids.join(',')}) AND status=1`) === '2',
      'Viewing the patient eForm list changed the saved instances');
    await list.close();
  });

  async function confirmPanelDelete(row, path) {
    await row.locator('a[data-confirm]').click();
    const confirm = admin.locator('#confirmModal #dataConfirmed');
    await confirm.waitFor();
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(path)),
      confirm.click(),
    ]);
    h.assert(response.status() === 200, `${path} answered HTTP ${response.status()}`);
    await admin.waitForLoadState('networkidle').catch(() => {});
    await h.assertNotErrorPage(admin, path);
  }

  await s.step('remove from group deletes only the eForm membership row', async () => {
    const row = panel.locator('.card').nth(1).locator('tr', {hasText: grouped});
    await confirmPanelDelete(row, '/eforms/removeFromGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(group)} AND fid=${groupedFid}`,
      '0', 'Remove from group left the membership row');
    h.assert(groupRows() === '1', 'Remove from group also removed the group itself');
    h.assert(await admin.getByText(grouped, {exact: true}).count() === 0, 'The page still lists the removed eForm');
  });

  await s.step('delete group removes the group and the lists stop offering it', async () => {
    await admin.locator('#groupListTbl').waitFor();
    const row = admin.locator('#groupListTbl tbody tr').filter({has: admin.locator(`td[title="${group}"]`)});
    await confirmPanelDelete(row, '/eforms/delGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(group)}`, '0',
      'Delete group left rows behind');
    h.assert(await admin.locator(`#groupListTbl td[title="${group}"]`).count() === 0, 'The deleted group is still listed');
  });
}

if (require.main === module) runWorkflow('eform-groups', workflow, {openPatient: true});
module.exports = {workflow};
