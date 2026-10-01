#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * eForm group management, from the administrator's panel to the clinician's chart filter.
 *
 * User path: Schedule > Administration > Forms > eForm Groups (panel in #dynamic-content):
 * the owned group's contents, then E-Chart > eForms "+" (Add eForm list) and E-Chart > eForms
 * heading (patient eForm list), each filtered by the group in "View Group", and finally back in
 * the panel: Add Group, Add eForm (modal), remove from group and delete group for a second group.
 * Asserts eform_groups rows after every mutation (eform/addGroup, eform/addToGroup,
 * eforms/removeFromGroup, eforms/delGroup) and that both chart lists (eform/efmformslistadd,
 * eform/efmpatientformlist) show the grouped eForm and hide the ungrouped one.
 * Fixtures: two owned eForm templates named with the run marker, one saved instance of each for
 * the owned synthetic patient, an owned group PW<marker hex> seeded with one eForm, and a second
 * group PV<marker hex> the panel creates (eform_groups.group_name is varchar(20)). Cleanup deletes
 * only those group rows, instances and templates and asserts each gone.
 * Implements coverage plan section 4.1 eForms, eform-groups-independent (the groups half).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickInjectsPanel} = require('./lib/playwright-ui');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TEMPLATE_HTML = '<html><head><title>eForm group fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton">'
  + '</form></body></html>';

// The View Group links are href="#" anchors whose onclick submits a GET form, so the fragment
// navigation comes first: wait for the submitted list itself, not the first navigation.
async function viewGroup(page, link, groupView) {
  await Promise.all([
    page.waitForURL(url => url.searchParams.get('group_view') === groupView, {waitUntil: 'load'}),
    link.click(),
  ]);
  await page.locator('#efmTable').waitFor();
}

async function workflow(s) {
  const group = `PW${s.marker.slice(-16)}`;
  const addedGroup = `PV${s.marker.slice(-16)}`;
  const grouped = `${s.marker} Grouped`;
  const loose = `${s.marker} Loose`;
  const fids = [];
  const fdids = [];
  const groupRows = (name, fid = null) => s.sql.value(`SELECT COUNT(*) FROM eform_groups
    WHERE group_name=${h.sqlString(name)}${fid === null ? '' : ` AND fid=${fid}`}`);
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM eform_groups WHERE group_name IN (${h.sqlString(group)},${h.sqlString(addedGroup)})`);
    h.assert(groupRows(group) === '0' && groupRows(addedGroup) === '0', 'Owned eForm group rows were not removed');
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
      VALUES(${h.sqlString(name)},'','eForm group fixture',CURDATE(),CURTIME(),${h.sqlString(s.provider)},
      1,${h.sqlString(TEMPLATE_HTML)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fid), 'eForm template fixture was not created');
    fids.push(fid);
    const fdid = s.sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,
      form_time,form_provider,form_data,showLatestFormOnly,patient_independent,roleType)
      VALUES(${fid},${h.sqlString(name)},'eForm group fixture',${s.patient},1,CURDATE(),CURTIME(),
      ${h.sqlString(s.provider)},${h.sqlString(TEMPLATE_HTML)},0,0,''); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fdid), 'eForm instance fixture was not created');
    fdids.push(fdid);
  }
  const [groupedFid, looseFid] = fids;
  // The group itself is a fixture: fid 0 is the group's own marker row, as AddGroup2Action writes it.
  s.sql.execute(`INSERT INTO eform_groups(fid,group_name) VALUES(0,${h.sqlString(group)}),(${groupedFid},${h.sqlString(group)})`);
  h.assert(groupRows(group) === '2', 'eForm group fixture was not created');

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'eform-groups-admin', timeout: 20000});
  const panel = admin.locator('#dynamic-content');
  const contents = panel.locator('.card').nth(1);

  await s.step('Administration > Forms > eForm Groups lists the owned group and its one eForm', async () => {
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    await clickInjectsPanel(admin, admin.locator('a.defaultFormsGroups').first(), {marker: '#dynamic-content #groupListTbl'});
    const row = panel.locator('#groupListTbl tbody tr').filter({has: admin.locator(`td[title="${group}"]`)});
    h.assert(await row.count() === 1 && (await row.locator('td').nth(2).innerText()).trim() === '1',
      'The Groups panel does not list the owned group with one eForm');
    await clickInjectsPanel(admin, row.locator('a.contentLink'), {marker: '#dynamic-content #groupListTbl tr.table-success'});
    h.assert(await panel.locator('#groupListTbl tr.table-success td[title]').getAttribute('title') === group,
      'Clicking the group did not select it');
    h.assert(await contents.getByText(grouped, {exact: true}).count() === 1
      && await contents.getByText(loose, {exact: true}).count() === 0, 'The group contents do not list exactly the grouped eForm');
  });

  await s.step('a duplicate group name is refused by the form before any POST', async () => {
    const name = panel.locator('#addGroupForm input[name="groupName"]');
    await name.pressSequentially(group);
    await panel.locator('.textExists').waitFor();
    h.assert(await panel.locator('#addGroupForm input.groupAdd').isDisabled(), 'Add Group was enabled for a duplicate name');
    await name.fill('');
    await name.press('Backspace');
    h.assert(groupRows(group) === '2', 'Typing a duplicate group name wrote a row');
  });

  const chart = await s.chart();
  await s.step('E-Chart Add eForm list filtered by the group offers only the grouped eForm', async () => {
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    h.assert(await list.locator('#efmTable').getByText(loose, {exact: true}).count() === 1,
      'The unfiltered Add eForm list does not offer the ungrouped eForm');
    const groupLink = list.locator('.grouplist li a').filter({hasText: group});
    h.assert(/\(\s*1\s*\)/.test(await groupLink.innerText()), 'The Add eForm group list does not count one eForm in the group');
    await viewGroup(list, groupLink, group);
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
    await viewGroup(list, groupLink, group);
    await table.waitFor();
    h.assert(await table.getByText(grouped, {exact: true}).count() === 1 && await table.getByText(loose, {exact: true}).count() === 0,
      'The group-filtered patient eForm list does not show exactly the grouped instance');
    await viewGroup(list, list.locator('.grouplist li a').first(), '');
    await table.waitFor();
    h.assert(await table.getByText(loose, {exact: true}).count() === 1, 'Show All did not clear the group filter');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid IN (${fdids.join(',')}) AND status=1`) === '2',
      'Viewing the patient eForm list changed the saved instances');
    await list.close();
  });

  // The mutators come last: on 2026.08 every one of them fails in the panel (see the report).
  async function submitInPanel(control, path) {
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(path)),
      control.click(),
    ]);
    h.assert(response.status() === 200, `${path} answered HTTP ${response.status()} to the panel's own submit`);
    await admin.waitForLoadState('networkidle').catch(() => {});
    await h.assertNotErrorPage(admin, path);
    await admin.locator('#groupListTbl').waitFor();
  }

  await s.step('Add Group stores the group and the panel selects it', async () => {
    await panel.locator('#addGroupForm input[name="groupName"]').pressSequentially(addedGroup);
    await submitInPanel(panel.locator('#addGroupForm input.groupAdd'), '/eform/addGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(addedGroup)} AND fid=0`, '1',
      'Add Group did not store the group');
    h.assert(await panel.locator('#groupListTbl tr.table-success td[title]').getAttribute('title') === addedGroup,
      'The new group is not the selected group after Add Group');
  });

  await s.step('Add eForm to Group stores exactly the chosen eForm', async () => {
    await panel.locator('#addEform-btn').click();
    await admin.locator('#eformSelect').selectOption(looseFid);
    await submitInPanel(admin.locator('#eformToGroup-btn'), '/eform/addToGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(addedGroup)} AND fid=${looseFid}`,
      '1', 'Add eForm to Group did not store the eForm');
    h.assert(groupRows(addedGroup) === '2', 'Add eForm to Group stored an eForm that was not chosen');
    h.assert(await contents.getByText(loose, {exact: true}).count() === 1, 'The group contents do not list the added eForm');
  });

  async function confirmDelete(row, path) {
    await row.locator('a[data-confirm]').click();
    await admin.locator('#confirmModal #dataConfirmed').waitFor();
    await submitInPanel(admin.locator('#confirmModal #dataConfirmed'), path);
  }

  await s.step('remove from group deletes only the eForm membership row', async () => {
    await confirmDelete(admin.locator('.card').nth(1).locator('tr', {hasText: loose}), '/eforms/removeFromGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(addedGroup)} AND fid=${looseFid}`,
      '0', 'Remove from group left the membership row');
    h.assert(groupRows(addedGroup) === '1', 'Remove from group also removed the group itself');
  });

  await s.step('delete group removes the group from the list', async () => {
    await confirmDelete(admin.locator('#groupListTbl tbody tr').filter({has: admin.locator(`td[title="${addedGroup}"]`)}),
      '/eforms/delGroup');
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_groups WHERE group_name=${h.sqlString(addedGroup)}`, '0',
      'Delete group left rows behind');
    h.assert(await admin.locator(`#groupListTbl td[title="${addedGroup}"]`).count() === 0, 'The deleted group is still listed');
    h.assert(groupRows(group) === '2', 'Deleting one group changed another');
  });
}

if (require.main === module) runWorkflow('eform-groups', workflow, {openPatient: true});
module.exports = {workflow};
