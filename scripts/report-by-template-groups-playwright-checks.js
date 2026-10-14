#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// report-by-template-groups: coverage plan §3.6 `report-by-template` (upload and template groups).
// User path: Schedule ▸ Administration ▸ Reports ▸ Report by Template (the admin #myFrame iframe)
//   ▸ Add Template (Upload & Add) ▸ Template Groups ▸ Add Group ▸ group ▸ Select Templates (modal)
//   ▸ Template Library group filter ▸ remove from group ▸ Back ▸ delete group.
// Asserts: the empty-file alert blocks the upload and a template missing its title is refused
// without a row; a valid upload stores the exact reportTemplates row; Add Group writes its
// rbt_groups marker row (tid 0) and the duplicate-name guard disables a second add; the modal adds
// the owned template (rbt_groups row) and the library's group filter shows only it; the
// confirm()-gated removal and group delete remove exactly those rows; a GET to addGroup must not
// create a group.
// Fixtures: one template and one group named with the per-run marker; cleanup deletes only rows
// carrying the marker and asserts they are gone. Running reports and exports live in
// report-by-template-playwright-checks.js.
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const ERROR_PAGE = /CARLOS has encountered an unexpected error|HTTP Status \d{3}|Exception Report/i;

async function workflow(s) {
  const title = `${s.marker} RBT group member`;
  const group = `${s.marker} Clinic`;
  const like = h.sqlString(`${s.marker}%`);
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM rbt_groups WHERE group_name LIKE ${like}`);
    s.sql.execute(`DELETE FROM reportTemplates WHERE templatetitle LIKE ${like} OR templatedescription LIKE ${like}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM rbt_groups WHERE group_name LIKE ${like})
      + (SELECT COUNT(*) FROM reportTemplates WHERE templatetitle LIKE ${like} OR templatedescription LIKE ${like})`) === '0',
    'Owned report templates or groups were not removed');
  });
  const query = 'SELECT demographic_no, last_name FROM demographic WHERE last_name = \'{who}\' ORDER BY demographic_no';
  const xml = (reportTitle, description) => `<report${reportTitle === null ? '' : ` title="${reportTitle}"`}`
    + ` description="${description}" active="1"><query>${query}</query>`
    + '<param id="who" type="text" description="Surname"></param></report>';
  const groupRows = () => s.sql.rows(`SELECT tid FROM rbt_groups WHERE group_name=${h.sqlString(group)} ORDER BY tid`)
    .map(row => row[0]);
  let templateId;

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'rbt-groups-administration', timeout: 20000});
  let frame;
  async function settle(label) {
    await frame.waitForLoadState('domcontentloaded');
    await frame.waitForLoadState('networkidle').catch(() => {});
    const text = await frame.locator('body').innerText().catch(() => '');
    h.assert(text.trim() && !ERROR_PAGE.test(text), `${label} rendered an error or blank page`);
  }
  async function frameClick(locator, label) {
    const navigated = admin.waitForEvent('framenavigated', {predicate: f => f === frame, timeout: 20000});
    navigated.catch(() => {});
    await locator.click();
    await navigated;
    await settle(label);
  }
  async function upload(content) {
    await frame.locator('#uploadReportXml').setInputFiles({name: 'rbt-group-template.xml', mimeType: 'text/xml',
      buffer: Buffer.from(content)});
    await frameClick(frame.locator('input[type="submit"][value^="Upload"]'), 'Upload & Add');
  }
  const groupLink = () => frame.locator('#groupListTbl td[title]').filter({hasText: group}).getByRole('link');

  await s.step('Administration ▸ Reports ▸ Report by Template opens the Template Library', async () => {
    const link = admin.getByRole('link', {name: 'Report by Template', exact: true, includeHidden: true});
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Report by Template frame did not load');
    await frame.waitForURL(/\/oscarReport\/reportByTemplate\/ViewHomePage/);
    await settle('Template Library');
    await frame.locator('h3', {hasText: 'Template Library'}).waitFor();
  });

  await s.step('Upload & Add without a file is stopped by its alert and a title-less template is refused', async () => {
    await frameClick(frame.getByRole('link', {name: 'Add Template', exact: true}), 'Add Template');
    const dialogs = await h.withExpectedDialogs(admin, () => frame.locator('input[type="submit"][value^="Upload"]').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert'
      && dialogs[0].text === 'Please upload a file before submitting the form.', 'Empty upload did not raise its alert');
    h.assert(/\/ViewAddEditTemplate/.test(frame.url()), 'Empty upload left the Add Template page');
    await upload(xml(null, `${s.marker} missing title`));
    await frame.locator('.alert-danger', {hasText: 'Attribute \'title\' missing'}).waitFor();
    h.assert(s.sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templatedescription LIKE ${like}`) === '0',
      'A refused upload stored a template');
  });

  await s.step('Upload & Add stores the owned template exactly as the XML describes it', async () => {
    await upload(xml(title, `${s.marker} grouped roster`));
    await frame.locator('.alert-success', {hasText: 'Saved Successfully'}).waitFor();
    const rows = s.sql.rows(`SELECT templateid,templatedescription,templatesql,active,type,uuid,templatexml
      FROM reportTemplates WHERE templatetitle=${h.sqlString(title)}`);
    h.assert(rows.length === 1, 'Upload did not create exactly one owned template');
    const [id, description, sql, active, type, uuid, storedXml] = rows[0];
    h.assert(description === `${s.marker} grouped roster` && sql === query && active === '1' && type === ''
      && /^[0-9a-f-]{36}$/.test(uuid) && storedXml.includes('<param id="who"'), 'Stored template differs from the upload');
    templateId = id;
  });

  await s.step('Template Groups ▸ Add Group writes the group marker row and lists the group', async () => {
    await frameClick(frame.getByRole('link', {name: 'Template Groups', exact: true}), 'Template Groups');
    const add = frame.locator('input.groupAdd');
    h.assert(await add.isDisabled(), 'Add Group is enabled before a name is typed');
    await frame.locator('input[name="groupName"].check').pressSequentially(group);
    h.assert(await add.isEnabled(), 'Add Group stayed disabled for a new name');
    await frameClick(add, 'Add Group');
    await groupLink().waitFor();
    h.assert(JSON.stringify(groupRows()) === '["0"]', 'Add Group did not write exactly its marker row');
  });

  await s.step('the duplicate-name guard disables Add Group for an existing name in any case', async () => {
    await frame.locator('input[name="groupName"].check').pressSequentially(group.toLowerCase());
    await frame.locator('.textExists').waitFor();
    h.assert(await frame.locator('input.groupAdd').isDisabled(), 'Add Group is enabled for a duplicate name');
    h.assert(JSON.stringify(groupRows()) === '["0"]', 'Typing a duplicate name changed the group rows');
  });

  await s.step('Select Templates adds the owned template to the group', async () => {
    await frameClick(groupLink(), 'Templates in group');
    await frame.locator('h4', {hasText: `Templates in Group: ${group}`}).waitFor();
    await frame.locator('#groupData', {hasText: 'No templates in this group'}).waitFor();
    await frame.locator('#selectRbtTemplatesBtn').click();
    await frame.locator('#selectTemplatesModal.show').waitFor();
    await frame.locator('#templateSelect').selectOption(templateId);
    await frameClick(frame.locator('#templateToGroup-btn'), 'Add Selected Template(s)');
    await frame.locator('#groupData').getByRole('link', {name: title, exact: true}).waitFor();
    h.assert(JSON.stringify(groupRows()) === JSON.stringify(['0', templateId]), 'The template was not added to the group');
    h.assert(await frame.locator('#templateSelect option').evaluateAll((options, id) =>
      options.every(option => option.value !== id), templateId), 'A grouped template is still offered for the group');
  });

  await s.step('the Template Library group filter shows only the grouped template', async () => {
    await frameClick(frame.getByRole('link', {name: 'Template Library', exact: true}), 'Template Library');
    await frame.locator('#viewSelect').selectOption({label: group});
    const visible = frame.locator('#tableData tr:visible');
    h.assert(await visible.count() === 1 && await visible.getByRole('link', {name: title, exact: true}).count() === 1,
      'The group filter did not narrow the library to the grouped template');
  });

  await s.step('removing the template from the group asks first and deletes only its row', async () => {
    await frameClick(frame.getByRole('link', {name: 'Template Groups', exact: true}), 'Template Groups');
    await frameClick(groupLink(), 'Templates in group');
    const dialogs = await h.withExpectedDialogs(admin, () => frameClick(
      frame.locator('#groupData a[title="delete template from group"]'), 'Remove from group'));
    h.assert(dialogs.length === 1 && dialogs[0].text === 'Remove template from group?', 'Removal did not ask once');
    await frame.locator('#groupData', {hasText: 'No templates in this group'}).waitFor();
    h.assert(JSON.stringify(groupRows()) === '["0"]', 'Removal did not delete exactly the membership row');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templateid=${templateId}`) === '1',
      'Removing a template from a group deleted the template');
  });

  await s.step('Back ▸ delete group asks first and removes every row of the group', async () => {
    await frameClick(frame.locator('#back-btn'), 'Back to groups');
    const dialogs = await h.withExpectedDialogs(admin, () => frameClick(
      frame.locator('#groupListTbl tr', {has: frame.locator(`td[title]`, {hasText: group})})
        .locator('a[title="delete group"]'), 'Delete group'));
    h.assert(dialogs.length === 1 && dialogs[0].text === 'Are you sure you want to delete this group?',
      'Group delete did not ask once');
    h.assert(await groupLink().count() === 0, 'Deleted group is still listed');
    h.assert(groupRows().length === 0, 'Group delete left rbt_groups rows');
  });

  // Negative probe after the UI path: the mutator must not act on a GET (CLAUDE.md GET/HEAD contract).
  await s.step('a GET to actions/addGroup does not create a group', async () => {
    const probe = `${s.marker} GET probe`;
    const url = new URL(`${s.config.baseUrl.href.replace(/\/$/, '')}/oscarReport/reportByTemplate/actions/addGroup`);
    url.searchParams.set('groupName', probe);
    const response = await admin.request.get(url.href, {maxRedirects: 0, failOnStatusCode: false});
    await response.dispose();
    h.assert([403, 405].includes(response.status()), `GET actions/addGroup answered HTTP ${response.status()}, not a refusal`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM rbt_groups WHERE group_name=${h.sqlString(probe)}`) === '0',
      `GET actions/addGroup (HTTP ${response.status()}) created a template group; the action must reject GET`);
  });
}

if (require.main === module) runWorkflow('report-by-template-groups', workflow, {openPatient: false});
module.exports = {workflow};
