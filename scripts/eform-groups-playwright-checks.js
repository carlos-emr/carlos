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
 * Also a failed Add Group request (#5842): the panel's POST is held by page.route, the name is
 * changed to the existing group's while it is out, and the routed HTTP 500 must put the shell's
 * notice above the form while the page checks the name again (Add Group stays disabled).
 * Fixtures: two owned eForm templates named with the run marker, one saved instance of each for
 * the owned synthetic patient, an owned group PW<marker hex> seeded with one eForm, and a second
 * group PV<marker hex> the panel creates (eform_groups.group_name is varchar(20)). The failed
 * request's name PF<marker hex> never reaches the server. Cleanup deletes only those group rows,
 * instances and templates and asserts each gone.
 * Implements coverage plan section 4.1 eForms, eform-groups-independent (the groups half).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickInjectsPanel} = require('./lib/playwright-ui');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {failureMark, consumeExpectedFailure} = require('./lib/concurrency-support');

const TEMPLATE_HTML = '<html><head><title>eForm group fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note"><input type="submit" value="Submit" id="SubmitButton">'
  + '</form></body></html>';
// The body the routed HTTP 500 answers the held Add Group POST with; the panel must never show it.
const FAILED_PAGE_TEXT = 'FAKE eForm group error page';
const FAILED_PAGE = `<!DOCTYPE html><html><body><h1>HTTP Status 500</h1><p>${FAILED_PAGE_TEXT}</p></body></html>`;

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
  // The name the failed Add Group request carries. The routed 500 keeps it off the server; it is
  // still cleaned up in case a route ever lets the POST through.
  const failedGroup = `PF${s.marker.slice(-16)}`;
  const grouped = `${s.marker} Grouped`;
  const loose = `${s.marker} Loose`;
  const fids = [];
  const fdids = [];
  const groupRows = (name, fid = null) => s.sql.value(`SELECT COUNT(*) FROM eform_groups
    WHERE group_name=${h.sqlString(name)}${fid === null ? '' : ` AND fid=${fid}`}`);
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM eform_groups WHERE group_name IN (${h.sqlString(group)},${h.sqlString(addedGroup)},${h.sqlString(failedGroup)})`);
    h.assert(groupRows(group) === '0' && groupRows(addedGroup) === '0' && groupRows(failedGroup) === '0',
      'Owned eForm group rows were not removed');
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

  // #5842. The shell's registerFormSubmit sends Add Group as an XHR; on failure showAdminRequestFailure
  // puts a notice above the form, makes usable again the submit controls that were usable when it was
  // sent, and triggers carlos:admin-request-failed on the form. The groups page answers that event by
  // running validate() on its name box again, because the name may have changed while the request was
  // out. So the POST is held until the name has become the existing group's, and only then answered 500.
  await s.step('a failed Add Group request is reported above the form and the name is checked again (#5842)', async () => {
    const form = panel.locator('#addGroupForm');
    const name = form.locator('input[name="groupName"]');
    const addButton = form.locator('input.groupAdd');
    const exists = panel.locator('.textExists');
    const notice = panel.locator('.carlos-admin-request-failed');
    // The shell's own bundle text (admin.admin.panelRequestFailed), so the expected notice holds in any locale.
    const message = await admin.evaluate(() => window.carlosAdminRequestFailedMessage);
    h.assert(typeof message === 'string' && message.length > 0, 'The Administration shell defines no request-failure message');
    await name.pressSequentially(failedGroup);
    h.assert(await addButton.isEnabled() && !(await exists.isVisible()), 'Add Group was not enabled for a new group name');

    const isAddGroup = url => url.pathname.endsWith('/eform/addGroup');
    const isAddGroupPost = request => request.method() === 'POST' && isAddGroup(new URL(request.url()));
    const posted = [];
    let release = () => {};
    const released = new Promise(resolve => { release = resolve; });
    // Nothing reaches the server: the POST waits here until released, then gets the error page.
    await admin.route(isAddGroup, async route => {
      if (!isAddGroupPost(route.request())) return route.fallback();
      posted.push(route.request().postData() || '');
      await released;
      return route.fulfill({status: 500, contentType: 'text/html', body: FAILED_PAGE});
    });
    try {
      const mark = failureMark(s.recorder);
      await Promise.all([admin.waitForRequest(isAddGroupPost), addButton.click()]);
      // While the request is out the name becomes the existing group's, and the keyup check refuses it.
      await name.fill('');
      await name.pressSequentially(group);
      await exists.waitFor();
      h.assert(await addButton.isDisabled(), 'Add Group stayed enabled for a duplicate name typed while the request was out');
      // Record what the failure does to the button's disabled attribute (oldValue of each change).
      await addButton.evaluate(button => {
        const changes = [];
        const observer = new MutationObserver(records => records.forEach(record => changes.push(record.oldValue)));
        observer.observe(button, {attributes: true, attributeFilter: ['disabled'], attributeOldValue: true});
        button.carlosCheckDisabledChanges = {changes, observer};
      });
      const answered = admin.waitForResponse(response => isAddGroupPost(response.request()));
      release();
      h.assert((await answered).status() === 500, 'The held Add Group request was not answered by the routed HTTP 500');
      await notice.waitFor();

      h.assert(posted.length === 1 && new URLSearchParams(posted[0]).get('groupName') === failedGroup,
        `Add Group sent ${posted.length} request(s), expected one carrying the name typed before the click`);
      h.assert(await notice.count() === 1 && await notice.getAttribute('role') === 'alert'
        && await notice.textContent() === `HTTP 500: ${message}`,
      'The failed request is not reported as "HTTP 500: " and the shell\'s message in one role="alert" notice');
      h.assert(await form.evaluate(element => Boolean(element.previousElementSibling
        && element.previousElementSibling.classList.contains('carlos-admin-request-failed'))),
      'The failure notice is not directly above the Add Group form');
      h.assert(!(await panel.innerText()).includes(FAILED_PAGE_TEXT), 'The failed response body was painted into the panel');
      h.assert(await name.inputValue() === group
        && await panel.locator(`#groupListTbl td[title="${group}"]`).count() === 1,
      'The failure did not keep the panel and the name typed while the request was out');
      // showAdminRequestFailure re-enabled the button (it was usable when sent): only the page's
      // carlos:admin-request-failed handler re-running validate() disables it again for the duplicate.
      const changes = await addButton.evaluate(button => {
        const {changes: seen, observer} = button.carlosCheckDisabledChanges;
        seen.push(...observer.takeRecords().map(record => record.oldValue));
        observer.disconnect();
        delete button.carlosCheckDisabledChanges;
        return seen;
      });
      h.assert(JSON.stringify(changes) === JSON.stringify(['disabled', null]),
        'After the failure Add Group should be re-enabled by the shell and then disabled again by the page\'s name check '
        + `(disabled attribute old values ["disabled",null]); they were ${JSON.stringify(changes)}`);
      h.assert(await addButton.isDisabled() && await exists.isVisible(),
        'After the failed request Add Group is enabled for the duplicate name, or the duplicate warning is hidden');
      h.assert(groupRows(failedGroup) === '0' && groupRows(group) === '2', 'The failed Add Group request changed eform_groups');
      consumeExpectedFailure(s.recorder, mark, {status: 500, path: /\/eform\/addGroup$/});
    } finally {
      release();
      await admin.unroute(isAddGroup);
    }

    // The form still follows the name: a unique one enables Add Group again.
    await name.fill('');
    await name.pressSequentially(failedGroup);
    h.assert(await addButton.isEnabled() && !(await exists.isVisible()),
      'Add Group was not enabled again for a unique name after the failed request');
    await name.fill('');
    await name.press('Backspace');
    h.assert(await addButton.isDisabled(), 'Add Group was left enabled for an empty name');
  });

  const chart = await s.chart();
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

  await s.step('E-Chart Add eForm list filtered by the group offers only the grouped eForm', async () => {
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    // Opened without group_view, the list applies the provider's saved favourite group.
    await viewGroup(list, list.locator('.grouplist li a').first(), '');
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

  // The mutators come last. Since #4130 they answer POST/redirect/GET: a forward kept the POST, and
  // the GET-only groups page refused it with 405 (findings 69, 74). So a redirect is the success
  // path, and what is checked is where it leads: every hop stays on this origin, the hops after
  // the POST are GETs, and the page the chain ends on answers 200.
  const REDIRECT_STATUSES = new Set([301, 302, 303, 307, 308]);
  async function submitInPanel(control, path) {
    const isPanelPost = req => req.method() === 'POST' && new URL(req.url()).pathname.endsWith(path);
    const chainOf = req => {
      const chain = [];
      for (let hop = req; hop; hop = hop.redirectedFrom()) chain.unshift(hop);
      return chain;
    };
    // Wait for the end of the chain the panel's POST starts, not the POST's own response: the
    // redirected GET can answer before a waiter registered after the 302 would be listening.
    const [landed] = await Promise.all([
      admin.waitForResponse(r => isPanelPost(chainOf(r.request())[0]) && !REDIRECT_STATUSES.has(r.status())),
      control.click(),
    ]);
    const chain = chainOf(landed.request());
    const origin = new URL(chain[0].url()).origin;
    const posted = await chain[0].response();
    h.assert(posted && (posted.status() === 200 || REDIRECT_STATUSES.has(posted.status())),
      `${path} answered HTTP ${posted ? posted.status() : 'nothing'} to the panel's own submit`);
    h.assert(chain.length <= 4, `${path} redirected ${chain.length - 1} times`);
    for (const hop of chain.slice(1)) {
      h.assert(new URL(hop.url()).origin === origin, `${path} redirected off-origin to ${new URL(hop.url()).origin}`);
      h.assert(hop.method() === 'GET', `${path} redirected to a ${hop.method()} instead of a GET`);
    }
    h.assert(landed.status() === 200,
      `${path} ended on HTTP ${landed.status()} at ${new URL(landed.url()).pathname} after ${chain.length - 1} redirect(s)`);
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
    // The notice the failed request left above the form (#5842) must not outlive a successful Add Group.
    h.assert(await panel.locator('.carlos-admin-request-failed').count() === 0,
      'The earlier failure notice is still shown after a successful Add Group');
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
