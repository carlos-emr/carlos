#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Run exclusively on a disposable deployment: EXCLUSIVE=1 npm run test:admin-role-management-playwright
 * Role and privilege administration: coverage plan §2.2 admin-misc / role-privilege-matrix.
 * User path: Schedule ▸ Administration ▸ System Management ▸ Add A Role, Assign Role/Rights to Object;
 * User Management ▸ Assign Role to Provider (fixture step; assign-role covers it); Schedule Management
 * ▸ Access Control; Data Management ▸ Fix notes with invalid role (its submit is an
 * unscoped bulk UPDATE, exercised only with an isolated owned invalid-role note). Asserted: the role is created once (secRole + audit); the editor grants it
 * _admin.userAdmin read (secObjPrivilege + audit); a throwaway doctor given the role opens Add A Role
 * from its own menu while the write-only editor stays hidden and 403; deleting the grant (recyclebin
 * copy) refuses Add A Role again; unassigning removes secUserRole; Access Control hides an owned group
 * from the admin's day-sheet list and back; Fix notes lists the role; GET saves are 405. Defects met on
 * the way are asserted in the last step. Fixtures: role, throwaway login and mygroup named by the marker;
 * no role delete exists in the UI, so cleanup deletes every marker row and asserts they are gone.
 */
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const TIMEOUT = 20000;
const OBJECT = '_admin.userAdmin';

async function openAdmin(schedule, context, recorder, label) {
  const { page } = await clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label, timeout: TIMEOUT });
  await page.locator('#adminNav').waitFor({ state: 'attached', timeout: TIMEOUT });
  return page;
}

// Open an Administration item by its route and hand back the iframe the shell loads it into.
async function openItem(admin, route, ready) {
  const link = admin.locator(`#adminNav a[rel$="/admin/${route}"]`).first();
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `The ${route} iframe did not load`);
  // route comes only from the fixed admin route literals in this file.
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
  await frame.waitForURL(new RegExp(`/admin/${route}`), { timeout: TIMEOUT, waitUntil: 'load' });
  await frame.locator(ready).first().waitFor({ state: 'attached', timeout: TIMEOUT });
  return frame;
}

// Wait for the frame's own navigation; `action` is a control to click or a function that submits.
async function submitIn(admin, frame, action) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
  navigated.catch(() => {});
  await (typeof action === 'function' ? action() : action.click());
  await navigated;
  await frame.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
}

async function workflow(s) {
  const { sql, config, context, recorder, marker } = s;
  h.assert(process.env.EXCLUSIVE === '1', 'Role repair validation requires an exclusive disposable deployment');
  const role = marker;
  const group = 'PW' + marker.slice(-8);
  const R = h.sqlString(role);
  const G = h.sqlString(group);
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const owned = `%${role}%`;
  // The GET ProviderAddRole probe names this role; a wrongly accepted GET would create it.
  const roles = `${R},${h.sqlString(`${role}-GET`)}`;
  s.cleanup(() => {
    sql.execute([
      `DELETE FROM MyGroupAccessRestriction WHERE myGroupNo=${G}`,
      `DELETE FROM mygroup WHERE mygroup_no=${G} AND last_name=${R}`,
      `DELETE FROM secObjPrivilege WHERE roleUserGroup=${R}`,
      `DELETE FROM secUserRole WHERE role_name=${R}`,
      `DELETE FROM recyclebin WHERE keyword LIKE ${h.sqlString(owned)}`,
      `DELETE FROM log WHERE content IN ('role','privilege') AND contentId LIKE ${h.sqlString(owned)}`,
      `DELETE FROM secRole WHERE role_name IN (${roles})`,
    ].join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM MyGroupAccessRestriction WHERE myGroupNo=${G})
      + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${G}) + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${R})
      + (SELECT COUNT(*) FROM secUserRole WHERE role_name=${R}) + (SELECT COUNT(*) FROM secRole WHERE role_name IN (${roles}))
      + (SELECT COUNT(*) FROM recyclebin WHERE keyword LIKE ${h.sqlString(owned)})
      + (SELECT COUNT(*) FROM log WHERE content IN ('role','privilege') AND contentId LIKE ${h.sqlString(owned)})`) === '0',
    'Owned role, grant, group or audit rows were not removed');
  });
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${R}) + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${G})`) === '0',
    'The run marker already names a role or schedule group');
  fixture.create();
  const throwaway = h.sqlString(fixture.providerNo);
  // A plain doctor: the copied admin grant would make every gate below pass vacuously.
  sql.execute(`DELETE FROM secUserRole WHERE provider_no=${throwaway} AND role_name<>'doctor'`);
  h.assert(sql.value(`SELECT GROUP_CONCAT(role_name) FROM secUserRole WHERE provider_no=${throwaway}`) === 'doctor',
    'The throwaway login does not hold exactly the doctor role');
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name,vieworder) VALUES (${G},${throwaway},${R},'Throwaway','1')`);
  const grant = () => sql.rows(`SELECT objectName,privilege,priority,provider_no FROM secObjPrivilege WHERE roleUserGroup=${R}`);
  const auditRows = (action, content, contentId) =>
    `SELECT COUNT(*) FROM log WHERE action='${action}' AND content='${content}' AND contentId=${h.sqlString(contentId)}`;

  // A fresh throwaway session each time: the menu's oscarSec tags read roles captured at login.
  async function throwawaySession(label, openPanel = true) {
    const ctx = await h.newContext(context.browser(), config);
    ctx.on('page', page => h.wireStrictPage(page, label, recorder));
    const schedule = await h.login(ctx, { ...config, testUser: fixture.username }, recorder, { label });
    const admin = openPanel ? await openAdmin(schedule, ctx, recorder, `${label}-administration`) : null;
    return { ctx, schedule, admin };
  }
  async function refused(ctx, route) {
    const response = await ctx.request.get(h.appUrl(config.baseUrl, `/admin/${route}`), { maxRedirects: 0 });
    return response.status();
  }

  // Application defects found on the way are asserted in the LAST step, so every step that can be
  // proven is proven first; the check still fails while any of them stands.
  const defects = [];
  // The evidence taken out of the strict recorder is kept here, so a run that fails before the
  // last step still prints the deferred defects (sanitised like the recorder's own details).
  const deferred = h.createRecorder();
  let lastStepReached = false;
  s.cleanup(() => {
    if (lastStepReached || !defects.length) return;
    console.error(JSON.stringify({ deferredDefects: defects, evidence: h.buildFailureDetails(deferred) }, null, 2));
  });
  function deferPageErrors(pattern, description) {
    const matched = recorder.pageErrors.filter(entry => entry.label === 'role-administration' && pattern.test(entry.text));
    if (!matched.length) return;
    recorder.pageErrors.splice(0, recorder.pageErrors.length, ...recorder.pageErrors.filter(entry => !matched.includes(entry)));
    deferred.pageErrors.push(...matched);
    defects.push(description);
  }
  // Take one HTTP failure on any of `labels` out of the strict recorder as a deferred defect; false if none.
  function deferFailure(labels, urlPattern, status, description) {
    const index = recorder.badResponses.findIndex(entry => labels.includes(entry.label) && entry.status === status && urlPattern.test(entry.url));
    if (index < 0) return false;
    deferred.badResponses.push(...recorder.badResponses.splice(index, 1));
    for (let i = recorder.consoleIssues.length - 1; i >= 0; i--) {
      const entry = recorder.consoleIssues[i];
      if (labels.includes(entry.label) && urlPattern.test(entry.location.url || '') && entry.text.includes(`status of ${status} (`)) {
        deferred.consoleIssues.push(...recorder.consoleIssues.splice(i, 1));
      }
    }
    defects.push(description);
    return true;
  }

  const admin = await openAdmin(s.schedule, context, recorder, 'role-administration');
  let frame;
  await s.step('an administrator can open the flowsheet editor from its menu', async () => {
    const flowsheets = await openItem(admin, 'ManageFlowsheets', '#flowsheetActionForm');
    await h.assertNotErrorPage(flowsheets, 'administrator flowsheet editor');
  });

  await s.step('Add A Role refuses a one-letter name in the page before any request is sent', async () => {
    frame = await openItem(admin, 'ProviderAddRole', 'input#role_name');
    const before = frame.url();
    await frame.locator('#role_name').fill('Z');
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      await frame.locator('input[name="submit"][value="Search"]').click();
    });
    h.assert(dialogs.length === 1 && /must type/i.test(dialogs[0].text), 'The short role name raised no validation alert');
    h.assert(frame.url() === before, 'The refused search still submitted');
  });
  let roleNo;
  await s.step('searching the new name offers it as NEW and the confirmed Save creates exactly one role', async () => {
    await frame.locator('#role_name').fill(role);
    await submitIn(admin, frame, frame.locator('input[name="submit"][value="Search"]'));
    h.assert(/It is a NEW role/.test(await frame.locator('span.alert').innerText()), 'The search did not offer the name as a new role');
    const dialogs = await h.withExpectedDialogs(admin, () =>
      submitIn(admin, frame, frame.locator('input[name="submit"][value="Save"]')));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Save did not ask for confirmation exactly once');
    h.assert((await frame.locator('span.alert').innerText()).includes(`${role} is added.`), 'Save did not confirm the new role');
    h.assert(sql.value(`SELECT CONCAT(COUNT(*),'|',MAX(description)) FROM secRole WHERE role_name=${R}`) === `1|${role}`,
      'The role was not stored exactly once with its name as description');
    roleNo = sql.value(`SELECT role_no FROM secRole WHERE role_name=${R}`);
    await expectValue(sql, auditRows('add', 'role', role), '1', 'The role creation was not audited');
  });
  await s.step('searching the saved name again offers it for editing, not as a duplicate', async () => {
    await frame.locator('#role_name').fill(role);
    await submitIn(admin, frame, frame.locator('input[name="submit"][value="Search"]'));
    h.assert(/You can edit the role/.test(await frame.locator('span.alert').innerText())
      && await frame.locator('input[name="action"]').inputValue() === `edit${role}`, 'The existing role was not offered for editing');
    h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name LIKE ${h.sqlString(owned)}`) === '1', 'Searching changed the role table');
  });
  await s.step(`Assign Role/Rights to Object grants the owned role ${OBJECT} read and lists it`, async () => {
    frame = await openItem(admin, 'ProviderPrivilege', 'select[name="roleUserGroup"]');
    await frame.locator('select[name="roleUserGroup"]').selectOption(role);
    h.assert(await frame.locator('select[name="roleUserGroup1"]').evaluate(el => el.style.backgroundColor) === 'silver',
      'Choosing a role did not update the provider selector');
    await frame.locator('select[name="roleUserGroup"]').selectOption('');
    h.assert(await frame.locator('select[name="roleUserGroup1"]').evaluate(el => el.style.backgroundColor) === 'white',
      'Clearing the role did not restore the provider selector');
    await frame.locator('select[name="roleUserGroup"]').selectOption(role);
    await frame.locator('#addtbl_filter input').fill(OBJECT);
    const row = frame.locator('#addtbl tbody tr').filter({ has: frame.locator(`input[name="object$${OBJECT}"]`) });
    await row.locator(`input[name="object$${OBJECT}"]`).check();
    await row.locator(`input[name="privilege$${OBJECT}$r"]`).check();
    await submitIn(admin, frame, row.locator('input[name="submit"][value="Add"]'));
    deferPageErrors(/onChangeSelect/, 'choosing a role in "Add Role Privilege For" raised an uncaught TypeError in onChangeSelect()');
    h.assert((await frame.locator('div.alert').first().innerText()).includes(`${role}/${OBJECT}/r is added.`), 'The grant was not confirmed');
    h.assert(JSON.stringify(grant()) === JSON.stringify([[OBJECT, 'r', '0', s.provider]]), 'The grant was not stored exactly as submitted');
    await expectValue(sql, auditRows('add', 'privilege', `${role}|${OBJECT}|r`), '1', 'The grant was not audited');
    await frame.locator('input[name="keyword"]').first().fill(role);
    await submitIn(admin, frame, frame.locator('input[name="search"]'));
    const listed = frame.locator('#tblpp tbody tr').filter({ hasText: OBJECT });
    h.assert(await frame.locator('#tblpp tbody tr').count() === 1 && await listed.locator('input[name="privileger"]').isChecked(),
      'The role filter does not list exactly the owned grant with read checked');
  });
  await s.step('before the role is assigned the throwaway doctor has no Add A Role and is refused it', async () => {
    const { ctx, schedule } = await throwawaySession('role-throwaway-before', false);
    try {
      h.assert(await refused(ctx, 'ProviderAddRole') === 403, 'A plain doctor was not refused Add A Role');
      h.assert(await refused(ctx, 'ManageFlowsheets') === 403,
        'Flowsheet read permission granted the write-only flowsheet editor');
      // The fixture's doctor role holds flowsheet read permission and must reach the shell.
      h.assert(await schedule.locator('#admin-panel').count() === 1,
        'A flowsheet-read doctor was not offered the Administration shell');
      if (await schedule.locator('#admin-panel').count()) {
        const label = 'role-throwaway-before-administration';
        const { page: own } = await clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel'),
          { context: ctx, recorder, label, timeout: TIMEOUT });
        await own.waitForLoadState('load');
        // A popup is relabelled to `label` in the same 'page' dispatch that wires it, before its
        // response arrives; an in-place navigation keeps the schedule's label until it commits.
        const forbidden = deferFailure(['role-throwaway-before', label], /\/administration(\?|$)/, 403, 'Schedule ▸ Administration is offered to a plain doctor '
          + '(day-sheet gate includes _admin.flowsheet) but /administration answers 403 (ViewAdministrationIndex2Action omits it)');
        if (!forbidden) {
          await own.locator('#adminNav').waitFor({ state: 'attached', timeout: TIMEOUT });
          h.assert(await own.locator('#adminNav a[rel$="/admin/ProviderAddRole"]').count() === 0, 'A plain doctor was offered Add A Role');
          h.assert(await own.locator('#adminNav a[rel$="/admin/ProviderPrivilege"]').count() === 0,
            'Flowsheet access exposed the role-rights editor');
          h.assert(await refused(ctx, 'ProviderPrivilege') === 403, 'Flowsheet access granted role-rights editing');
          h.assert(await own.locator('#adminNav a[rel$="/admin/ManageFlowsheets"]').count() === 0,
            'The menu advertised the write-only flowsheet editor to a read-only doctor');
        }
      }
    } finally { await ctx.close(); }
  });
  await s.step('Assign Role to Provider adds the owned role to the throwaway alongside doctor', async () => {
    frame = await openItem(admin, 'ProviderRole', 'input[name="keyword"]');
    await frame.locator('input[name="keyword"]').first().fill(marker);
    await submitIn(admin, frame, frame.locator('input[name="search"]').first());
    const row = frame.locator('tr').filter({ hasText: fixture.providerNo }).first();
    await row.locator('select[name="roleNew"]').selectOption(role);
    await submitIn(admin, frame, row.locator('input[name="submit"][value="Add"]'));
    h.assert(sql.value(`SELECT GROUP_CONCAT(role_name ORDER BY role_name) FROM secUserRole WHERE provider_no=${throwaway}`) === `doctor,${role}`,
      'The throwaway does not hold exactly doctor and the owned role');
  });
  await s.step('the granted throwaway opens Add A Role from its own menu; Rights to Object stays hidden and refused', async () => {
    const { ctx, admin: own } = await throwawaySession('role-throwaway-granted');
    try {
      const page = await openItem(own, 'ProviderAddRole', 'input#role_name');
      h.assert(await page.locator('input[name="submit"][value="Search"]').isVisible(), 'Add A Role did not render its form');
      h.assert(await own.locator('#adminNav a[rel$="/admin/ProviderPrivilege"]').count() === 0, 'Read access exposed Assign Role/Rights to Object');
      h.assert(await refused(ctx, 'ProviderPrivilege') === 403, 'Read access was not refused the write-only privilege editor');
    } finally { await ctx.close(); }
  });
  await s.step('deleting the grant removes it, keeps a recyclebin copy and closes Add A Role for a fresh session', async () => {
    frame = await openItem(admin, 'ProviderPrivilege', 'select[name="roleUserGroup"]');
    await frame.locator('input[name="keyword"]').first().fill(role);
    await submitIn(admin, frame, frame.locator('input[name="search"]'));
    const listed = frame.locator('#tblpp tbody tr').filter({ hasText: OBJECT });
    await submitIn(admin, frame, listed.locator('input[name="submit"][value="Delete"]'));
    h.assert((await frame.locator('div.alert').first().innerText()).includes(`${role}/${OBJECT}/r is deleted.`), 'The delete was not confirmed');
    h.assert(grant().length === 0, 'The grant is still stored');
    h.assert(sql.value(`SELECT COUNT(*) FROM recyclebin WHERE table_name='secObjPrivilege' AND keyword=${h.sqlString(`${role}|${OBJECT}`)}
      AND table_content LIKE '%<privilege>r</privilege>%'`) === '1', 'The deleted grant was not copied to the recycle bin');
    // The plain doctor's Administration panel itself answers 403 (deferred above), so the refusal is probed directly.
    const { ctx } = await throwawaySession('role-throwaway-revoked', false);
    try {
      h.assert(await refused(ctx, 'ProviderAddRole') === 403, 'The revoked grant still opens Add A Role');
    } finally { await ctx.close(); }
  });
  await s.step('Delete on the owned role row unassigns it from the throwaway', async () => {
    frame = await openItem(admin, 'ProviderRole', 'input[name="keyword"]');
    await frame.locator('input[name="keyword"]').first().fill(marker);
    await submitIn(admin, frame, frame.locator('input[name="search"]').first());
    const row = frame.locator('tr').filter({ has: frame.locator(`input[name="roleOld"][value="${role}"]`) });
    h.assert(await row.count() === 1, 'The owned role assignment is not listed once');
    await submitIn(admin, frame, row.locator('input[name="submit"][value="Delete"]'));
    h.assert(sql.value(`SELECT GROUP_CONCAT(role_name) FROM secUserRole WHERE provider_no=${throwaway}`) === 'doctor',
      'The owned role was not unassigned (or doctor went with it)');
  });
  await s.step('a miscellaneous administrator reaches the flowsheet editor without another admin grant', async () => {
    const miscRole = `${role}-misc`;
    const M = h.sqlString(miscRole);
    const miscFixture = throwawayLoginFixture({ sql, marker: `FAKE-PW${randomBytes(8).toString('hex')}`, provider: s.provider, testUser: config.testUser });
    h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${M}`) === '0', 'The miscellaneous role already exists');
    s.cleanup(() => {
      miscFixture.cleanup();
      sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${M}; DELETE FROM secRole WHERE role_name=${M}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${M})
        + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${M})`) === '0', 'Owned miscellaneous role was not removed');
    });
    // Preserve ordinary doctor access needed to sign in, with exactly one admin object.
    // Inheriting doctor itself would add flowsheet read and hide a broken parent menu gate.
    sql.execute(`INSERT INTO secRole (role_name,description) VALUES (${M},${M});
      INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
        SELECT ${M},objectName,privilege,priority,provider_no FROM secObjPrivilege
        WHERE roleUserGroup='doctor' AND LEFT(objectName,6)<>'_admin';
      INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
        VALUES (${M},'_admin.misc','rw',0,${h.sqlString(s.provider)})`);
    miscFixture.create({ roleNames: [miscRole] });
    h.assert(sql.value(`SELECT GROUP_CONCAT(objectName) FROM secObjPrivilege
      WHERE roleUserGroup=${M} AND LEFT(objectName,6)='_admin'`) === '_admin.misc', 'The fixture has an extra admin grant');
    const ctx = await h.newContext(context.browser(), config);
    ctx.on('page', page => h.wireStrictPage(page, 'role-misc-only', recorder));
    try {
      const schedule = await h.login(ctx, { ...config, testUser: miscFixture.username }, recorder, { label: 'role-misc-only' });
      const own = await openAdmin(schedule, ctx, recorder, 'role-misc-only-administration');
      const flowsheets = await openItem(own, 'ManageFlowsheets', '#flowsheetActionForm');
      await h.assertNotErrorPage(flowsheets, 'miscellaneous administrator flowsheet editor');
      h.assert(await own.locator('#adminNav a[rel$="/admin/ProviderPrivilege"]').count() === 0,
        'Miscellaneous access exposed the role-rights editor');
      h.assert(await refused(ctx, 'ProviderPrivilege') === 403, 'Miscellaneous access opened the role-rights editor');
    } finally { await ctx.close(); }
  });
  // The Administration link replaced the original day sheet, so a second admin session keeps one open.
  const dayContext = await h.newContext(context.browser(), config);
  dayContext.on('page', page => h.wireStrictPage(page, 'role-day-sheet', recorder));
  s.cleanup(() => dayContext.close());
  const daySheet = await h.login(dayContext, config, recorder, { label: 'role-day-sheet' });
  async function refreshDaySheet() {
    await daySheet.reload();
    await daySheet.locator('#mygroup_no').waitFor({ state: 'attached' });
  }
  const groupOption = () => daySheet.locator(`#mygroup_no option[value="_grp_${group}"]`).count();
  const restrictions = () => sql.rows(`SELECT providerNo FROM MyGroupAccessRestriction WHERE myGroupNo=${G} ORDER BY providerNo`).map(r => r[0]);
  async function accessControl(restrict) {
    frame = await openItem(admin, 'GroupNoAcl', 'select#chosen_group');
    // Choosing a group re-renders the page for it (onChange submits method=setGroupNo).
    await submitIn(admin, frame, () => frame.locator('select#chosen_group').selectOption(group));
    const box = frame.locator(`input[name="data"][value="${s.provider}"]`);
    h.assert(await box.isChecked() === !restrict, 'The group\'s current restriction is not reflected');
    await box.setChecked(restrict);
    await submitIn(admin, frame, frame.locator('input[name="Submit"]'));
    h.assert(await frame.locator(`input[name="data"][value="${s.provider}"]`).isChecked() === restrict, 'The saved restriction is not shown');
    await refreshDaySheet();
  }
  await s.step('Access Control restricts the owned group from the admin, hiding it from the day-sheet group list', async () => {
    await refreshDaySheet();
    h.assert(await groupOption() === 1, 'The owned schedule group is not offered on the day sheet');
    await accessControl(true);
    h.assert(JSON.stringify(restrictions()) === JSON.stringify([s.provider]), 'The restriction was not stored for exactly the admin');
    h.assert(await groupOption() === 0, 'The restricted group is still offered on the admin\'s day sheet');
  });
  await s.step('clearing the restriction deletes it and the group returns to the day sheet', async () => {
    await accessControl(false);
    h.assert(restrictions().length === 0, 'The restriction row was not removed');
    h.assert(await groupOption() === 1, 'The un-restricted group did not return to the day sheet');
  });
  await s.step('Fix notes with invalid role lists the owned role as a target (opened, never submitted)', async () => {
    frame = await openItem(admin, 'FixRolesOnNotes', 'select[name="role_to"]');
    const option = frame.locator(`select[name="role_to"] option[value="${roleNo}"]`);
    h.assert(await option.count() === 1 && (await option.innerText()).trim() === role, 'The owned role is not offered as a target');
    h.assert(await frame.locator('input[name="action"]').inputValue() === 'run', 'The page did not render its unsubmitted form');
  });
  await s.step('Fix notes refuses GET/HEAD and missing CSRF, then a protected POST repairs only the owned note', async () => {
    // This utility updates every zero-role note. Refuse to run if any pre-existing row could be affected.
    h.assert(sql.value("SELECT COUNT(*) FROM casemgmt_note WHERE reporter_caisi_role='0'") === '0',
      'Pre-existing invalid-role notes prevent an isolated repair test');
    const q = h.sqlString;
    const uuids = [marker, `${marker}-empty`, `${marker}-text`].map(q).join(',');
    const ownedNotes = `demographic_no=${s.patient} AND uuid IN (${uuids})`;
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE ${ownedNotes}`) === '0', 'The note marker is already in use');
    const others = () => JSON.stringify(sql.rows(`SELECT note_id,reporter_caisi_role FROM casemgmt_note
      WHERE NOT (${ownedNotes}) OR demographic_no IS NULL OR uuid IS NULL ORDER BY note_id`));
    const before = others();
    s.cleanup(() => {
      sql.execute(`DELETE FROM casemgmt_note WHERE ${ownedNotes}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE ${ownedNotes}`) === '0',
        'The owned repair note remains');
      h.assert(others() === before, 'The repair changed another note role');
    });
    const noteId = sql.value(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,
      note,history,uuid,locked,archived,reporter_caisi_role,appointmentNo)
      VALUES (NOW(),NOW(),${s.patient},${q(s.provider)},${q(marker)},${q(marker)},${q(marker)},0,0,'0',0);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(noteId), 'No owned note ID was returned');
    for (const [suffix, value] of [['empty', ''], ['text', 'invalid-role']]) {
      sql.execute(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,
        note,history,uuid,locked,archived,reporter_caisi_role,appointmentNo)
        VALUES (NOW(),NOW(),${s.patient},${q(s.provider)},${q(marker)},${q(marker)},${q(`${marker}-${suffix}`)},0,0,${q(value)},0)`);
    }
    const untouchedRoles = () => sql.rows(`SELECT uuid,reporter_caisi_role FROM casemgmt_note
      WHERE demographic_no=${s.patient} AND uuid IN (${q(`${marker}-empty`)},${q(`${marker}-text`)}) ORDER BY uuid`);
    const untouchedBefore = JSON.stringify(untouchedRoles());
    const currentRole = () => sql.value(`SELECT reporter_caisi_role FROM casemgmt_note WHERE note_id=${noteId}`);
    h.assert(sql.value("SELECT COUNT(*) FROM casemgmt_note WHERE reporter_caisi_role='0'") === '1'
      && currentRole() === '0', 'The repair would target anything other than the owned note');
    const route = h.appUrl(config.baseUrl, '/admin/FixRolesOnNotes');
    for (const method of ['GET', 'HEAD']) {
      const response = await context.request.fetch(route, {method, params: {action: 'run', role_to: roleNo}, maxRedirects: 0});
      h.assert(response.status() === 405 && response.headers().allow === 'POST', `${method} did not deliberately refuse repair`);
      await response.dispose();
      h.assert(currentRole() === '0' && others() === before, `${method} changed a note role`);
    }
    const refused = await context.request.post(route, {form: {action: 'run', role_to: roleNo}, maxRedirects: 0});
    h.assert(refused.status() === 403, 'Repair without CSRF was not refused');
    await refused.dispose();
    h.assert(currentRole() === '0' && others() === before, 'A request without CSRF changed a note role');
    h.assert((await frame.locator('input[name="CSRF-TOKEN"]').inputValue()).length > 0, 'The repair form has no CSRF token');
    await frame.locator('select[name="role_to"]').selectOption(roleNo);
    await submitIn(admin, frame, frame.locator('input[type="submit"]'));
    await h.assertNotErrorPage(frame, 'protected note role repair');
    h.assert(currentRole() === roleNo && others() === before, 'The protected repair did not change exactly the owned note');
    h.assert(JSON.stringify(untouchedRoles()) === untouchedBefore, 'The repair overwrote an empty or nonnumeric role');
  });

  await s.step('ProviderAddRole and ProviderPrivilege refuse a GET save without writing', async () => {
    const extra = `${role}-GET`;
    const add = await context.request.get(h.appUrl(config.baseUrl, '/admin/ProviderAddRole'), {
      params: { submit: 'Save', action: `add${extra}`, role_name: extra }, maxRedirects: 0 });
    const privilege = await context.request.get(h.appUrl(config.baseUrl, '/admin/ProviderPrivilege'), {
      params: { submit: 'Add', roleUserGroup: role, [`object$${OBJECT}`]: 'on', [`privilege$${OBJECT}$x`]: 'on', [`priority$${OBJECT}`]: '0' },
      maxRedirects: 0 });
    h.assert(add.status() === 405 && privilege.status() === 405,
      `GET saves answered HTTP ${add.status()} / ${privilege.status()}, expected 405`);
    h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${h.sqlString(extra)}`) === '0' && grant().length === 0,
      'A refused GET wrote a role or grant');
  });
  await s.step('Fix notes refuses a GET carrying its run action, and no deferred defect remains', async () => {
    lastStepReached = true;
    // role_to is deliberately not a number: were the GET to reach the UPDATE, parseInt fails first.
    const response = await context.request.get(h.appUrl(config.baseUrl, '/admin/FixRolesOnNotes'), {
      params: { action: 'run', role_to: 'not-a-role' }, maxRedirects: 0 });
    if (response.status() !== 405) defects.push(`GET admin/FixRolesOnNotes?action=run answered HTTP ${response.status()}, expected 405`);
    h.assert(defects.length === 0, `Application defects: ${defects.join('; ')}`);
  });
}
if (require.main === module) runWorkflow('admin-role-management', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
