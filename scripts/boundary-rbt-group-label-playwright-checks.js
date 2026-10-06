#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters in a Report by Template group name (wave 6, boundary values).
 * User path: Schedule > Administration > Reports > Report by Template > Template Groups > type a group name > Add Group.
 * Asserts: a group name with an apostrophe, an accent, CJK, a literal "&amp;" and a backslash is stored in rbt_groups
 * byte for byte (not run through Java string escaping) and the group list shows it as typed.
 * Fixtures: one group named with the run marker; cleanup deletes the rbt_groups rows carrying the marker and asserts
 * none remain.
 * Implements the wave-6 "boundary values" pattern, Part 1 (group labels).
 */
const h = require('./lib/playwright-harness');
const b = require('./lib/boundary-values');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const ERROR_PAGE = /CARLOS has encountered an unexpected error|HTTP Status \d{3}|Exception Report/i;

async function workflow(s) {
  const T = b.TOKENS;
  const group = `${s.marker} ${T.apostrophe} Zoë ${T.cjk} ${T.entity} ${T.backslash} "<b>literal</b> #?`;
  const like = h.sqlString(`${s.marker}%`);
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM rbt_groups WHERE group_name LIKE ${like}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM rbt_groups WHERE group_name LIKE ${like}`) === '0', 'Owned template groups were not removed');
  });
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'rbt-label-administration', timeout: 20000 });
  const link = admin.getByRole('link', { name: 'Report by Template', exact: true, includeHidden: true });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe#myFrame');
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'Report by Template frame did not load');
  await frame.waitForURL(/\/oscarReport\/reportByTemplate\/ViewHomePage/);
  async function frameClick(locator, label) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 20000 });
    navigated.catch(() => {});
    await locator.click();
    await navigated;
    await frame.waitForLoadState('domcontentloaded');
    await frame.waitForLoadState('networkidle').catch(() => {});
    h.assert(!ERROR_PAGE.test(await frame.locator('body').innerText().catch(() => '')), `${label} rendered an error page`);
  }

  await s.step('Template Groups > Add Group stores a group name with special characters byte for byte and lists it as typed', async () => {
    await frameClick(frame.getByRole('link', { name: 'Template Groups', exact: true }), 'Template Groups');
    await frame.locator('input[name="groupName"].check').pressSequentially(group);
    await frameClick(frame.locator('input.groupAdd'), 'Add Group');
    const rows = s.sql.rows(`SELECT HEX(group_name) FROM rbt_groups WHERE group_name LIKE ${like}`);
    h.assert(rows.length === 1, 'Add Group did not store exactly one group marker row');
    const stored = Buffer.from(rows[0][0], 'hex').toString('utf8');
    h.assert(rows[0][0] === b.hex(group), `The stored group name differs from what was typed: typed="${group}" stored="${stored}" `
      + '(RBTGroupManager.addTemplateToGroup runs the name through Encode.forJava before persisting it)');
    const cell = frame.locator('#groupListTbl td[title]').filter({ hasText: group });
    h.assert(await cell.count() === 1, 'The group list does not show the name as typed');
    h.assert(await cell.getAttribute('title') === group, 'Group title changed literal characters');
    h.assert(await cell.locator('b').count() === 0, 'Group markup was interpreted as HTML');
    const link = cell.locator('a');
    const target = new URL(await link.getAttribute('href'), frame.url());
    h.assert(target.searchParams.get('groupName') === group, 'Group link did not preserve its query parameter');
    const deleteForm = cell.locator('..').locator('form');
    h.assert(await deleteForm.locator('input[name="groupName"]').inputValue() === group,
      'The delete form changed the group name');
    await frameClick(link, 'Open literal group');
    h.assert((await frame.locator('body').innerText()).includes(group), 'Opened group lost its literal name');
  });
}

if (require.main === module) runWorkflow('boundary-rbt-group-label', workflow, { openPatient: false });
module.exports = { workflow };
