#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, writes named by a parameter other than `method` (issue #3394).
 *
 * WHY. HttpMethodGuardFilter refuses a GET when the route name starts with a mutator verb or
 * the `method` value names one. These four routes do neither: each picks its write from a
 * parameter of its own, so until #3394 a GET (which CSRFGuard never checks) wrote with the
 * clinician's session from a link or an <img>:
 *   - admin/ProviderPrivilege   buttonUpdate rewrote a role's privilege bits (#2862's exploit);
 *   - admin/ProviderRole        submit (a LOCALIZED Add/Delete), buttonUpdate and
 *                               buttonSetPrimaryRole changed a provider's role;
 *   - messenger/HandleMessages  delete deleted the message;
 *   - report/DxresearchReport   method=editDesc rewrote a shared code description (the value
 *                               names no verb the filter knew).
 * Each action now refuses such a request itself and the filter lists the parameters per route.
 *
 * User path, per route: the real page performs the write by POST (captured), and the captured
 * request is replayed as GET then HEAD against a second row the run owns. Probes are asserted
 * together in the LAST step, so one open route does not hide the others:
 *   1. Administration's "Assign Role/Rights to Object" (admin/ProviderPrivilege): filter by the
 *      owned object prefix, tick Write on row A and press its Update. Replays: that Update for
 *      row B with other bits, and the row's Delete for row B.
 *   2. "Assign Role to Provider" (admin/ProviderRole): filter by the throwaway provider, add a
 *      second role, then change the first with Update. Replays: Add, Update and Delete, and the
 *      Primary EMR Role form's buttonSetPrimaryRole.
 *   3. Messenger (from the schedule) ▸ owned message A ▸ Delete deletes it. Replay: the Delete for
 *      owned message B (the button posts an EMPTY `delete`, so the replay names it). The inbox
 *      must be opened first: the page Delete lands on redirects to the login page without the
 *      messenger session bean the inbox creates.
 *   4. The Dx registry's code-description editor (oscarReport/ViewEditCodeDesc) saves an owned
 *      ICD-9 row's description. The page has no link that fills its hidden code fields (it reads
 *      them from a session nothing sets), so the check sets them on the page before Modify.
 *      Replay: the same save with another description.
 *
 * Fixtures, all named from the run marker and removed by cleanup (which asserts it): two
 * secObjPrivilege rows of a marker role; a throwaway provider and login holding `doctor`
 * (lib/throwaway-login-fixture.js) and the recyclebin copies the role page writes for it; two
 * messages to the test provider; one ICD-9 row with a marker code. No clinic-wide setting changes.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const NAME = 'get-reject-dispatch-params';
const TIMEOUT = 20000;
const STEP = {
  privilege: 'Assign Role/Rights to Object ▸ a row\'s Update posts its new privilege bits (buttonUpdate)',
  role: 'Assign Role to Provider ▸ Add and Update change the throwaway provider\'s roles by POST',
  message: 'a message\'s Delete deletes the owned message by POST',
  dx: 'the Dx code-description editor saves the owned code\'s description by POST',
  refused: 'every write named by a parameter other than method refused GET/HEAD and left the owned rows unchanged',
};

/** The real page's POST is the CSRF-protected path, so it must have carried a token. */
function assertTokenPosted(capture, what) {
  h.assert((capture.params.get('CSRF-TOKEN') || capture.headers['csrf-token'] || '').length > 0,
    `${what} was posted without a CSRF token`);
}

/** Open an application page in its own tab and wait for the given control. */
async function openPage(s, appPath, ready) {
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, appPath);
  await page.locator(ready).first().waitFor({ state: 'attached', timeout: TIMEOUT });
  await h.assertNotErrorPage(page, appPath);
  return page;
}

async function workflow(s) {
  const { sql, provider, marker, config } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const hex = marker.replace(/^FAKE-PW/, '');

  // ── fixtures ──────────────────────────────────────────────────────────────────────────────
  const role = `PV${hex}`; // secObjPrivilege.roleUserGroup is varchar(30)
  const objectPrefix = `_pw${hex.slice(-12)}`;
  const objectA = `${objectPrefix}a`;
  const objectB = `${objectPrefix}b`;
  const privilegeRow = (object) => sql.value(`SELECT CONCAT(privilege,'|',IFNULL(priority,''),'|',IFNULL(provider_no,''))
    FROM secObjPrivilege WHERE roleUserGroup=${q(role)} AND objectName=${q(object)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)};
      DELETE FROM recyclebin WHERE table_name='secObjPrivilege' AND keyword LIKE ${q(`${role}|%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM recyclebin WHERE table_name='secObjPrivilege' AND keyword LIKE ${q(`${role}|%`)}`) === '0',
    'The owned privilege rows were not removed');
  });
  sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no) VALUES
    (${q(role)},${q(objectA)},'r',1,${q(provider)}),(${q(role)},${q(objectB)},'r',1,${q(provider)})`);
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '2',
    'The owned privilege rows were not created');

  const throwaway = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const roleRecycleKeyword = () => q(`${throwaway.providerNo}|%`);
  s.cleanup(() => {
    if (throwaway.providerNo) {
      sql.execute(`DELETE FROM recyclebin WHERE table_name='secUserRole' AND keyword LIKE ${roleRecycleKeyword()}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM recyclebin WHERE table_name='secUserRole' AND keyword LIKE ${roleRecycleKeyword()}`) === '0',
        'The recyclebin copies of the throwaway\'s roles were not removed');
    }
    throwaway.cleanup();
  });
  throwaway.create({ roleNames: ['doctor'] });
  const throwawayNo = throwaway.providerNo;
  const rolesOf = () => sql.rows(`SELECT id, role_name FROM secUserRole WHERE provider_no=${q(throwawayNo)} ORDER BY id`)
    .map(([id, name]) => `${id}:${name}`).join(',');
  const programRolesOf = () => sql.value(`SELECT COUNT(*) FROM program_provider WHERE provider_no=${q(throwawayNo)}`);

  const ownedMessages = () => sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${q(`%${marker}%`)}`).map(([id]) => id);
  s.cleanup(() => {
    const ids = ownedMessages();
    if (!ids.length) return;
    sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${ids.join(',')});
      DELETE FROM messagelisttbl WHERE message IN (${ids.join(',')});
      DELETE FROM messagetbl WHERE messageid IN (${ids.join(',')}) AND thesubject LIKE ${q(`%${marker}%`)}`);
    h.assert(ownedMessages().length === 0, 'The owned messages were not removed');
  });
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  if (!/^\d+$/.test(location || '')) throw new h.SkipCheck('No current messenger location is configured');
  const messages = {};
  for (const key of ['A', 'B']) {
    // Written the way MsgMessageData.sendMessage2 writes a local message to one recipient.
    messages[key] = sql.value(`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
      VALUES(CURDATE(),CURTIME(),${q(`${marker} body ${key}`)},${q(`${marker} message ${key}`)},'FAKE-PW sender','FAKE-PW recipient',
        ${q(provider)},${location},0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(messages[key]), `Message fixture ${key} was not created`);
    sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${messages[key]},${q(provider)},'new',${location},0,0)`);
  }
  const messageStatus = (key) => sql.value(`SELECT IFNULL(GROUP_CONCAT(status),'') FROM messagelisttbl
    WHERE message=${messages[key]} AND provider_no=${q(provider)}`);

  const code = `Z${hex.slice(-6)}`; // icd9.icd9 is varchar(7)
  const codeDescription = () => sql.value(`SELECT IFNULL(GROUP_CONCAT(description),'') FROM icd9 WHERE icd9=${q(code)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM icd9 WHERE icd9=${q(code)} AND description LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM icd9 WHERE icd9=${q(code)}`) === '0', 'The owned ICD-9 row was not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM icd9 WHERE icd9=${q(code)}`) === '0', 'The marker ICD-9 code already exists');
  sql.execute(`INSERT INTO icd9 (icd9, description) VALUES (${q(code)}, ${q(`${marker} original`)})`);

  // ── 1. Provider Privilege ─────────────────────────────────────────────────────────────────
  let privilegeUpdate;
  await s.step(STEP.privilege, async () => {
    const page = await openPage(s, '/admin/ProviderPrivilege', 'form[name="myform"] input[name="keyword"]');
    await page.locator('form[name="myform"] input[name="keyword"]').fill(objectPrefix);
    await clickAndAwaitReload(page, page.locator('form[name="myform"] input[name="search"]'), { label: 'privilege filter' });
    // Each row's <form> sits directly in <tbody>, so the parser leaves it empty: find the row, not the form.
    const row = page.locator('tr', { has: page.locator(`input[name="objectName"][value="${objectA}"]`) });
    h.assert(await row.count() === 1, 'The privilege filter does not list owned row A once');
    h.assert(await page.locator(`input[name="objectName"][value="${objectB}"]`).count() === 1,
      'The privilege filter does not list owned row B');
    await row.locator('input[name="privilegew"]').check();
    privilegeUpdate = await captureRequest(page, (url) => url.pathname.endsWith('/admin/ProviderPrivilege'),
      () => clickAndAwaitReload(page, row.locator('input[name="buttonUpdate"]'), { label: 'privilege Update' }));
    h.assert(privilegeUpdate.status === 200, `The privilege Update answered HTTP ${privilegeUpdate.status}`);
    h.assert(privilegeUpdate.params.has('buttonUpdate') && privilegeUpdate.params.get('objectName') === objectA,
      'The Update did not post buttonUpdate for row A');
    assertTokenPosted(privilegeUpdate, 'The privilege Update');
    await h.assertNotErrorPage(page, 'privilege list after Update');
    const [bits] = privilegeRow(objectA).split('|');
    h.assert([...bits].sort().join('') === 'rw', `Row A holds privilege "${bits}" after the Update, not read and write`);
    h.assert(privilegeRow(objectB) === `r|1|${provider}`, 'Updating row A changed row B');
    await page.close();

    const forB = (overrides) => replayParams(privilegeUpdate.params, { objectName: objectB, ...overrides });
    await ledger.probe(s, { label: 'admin/ProviderPrivilege buttonUpdate (row B, write and delete bits)',
      path: privilegeUpdate.path, params: forB({ privilegew: null, privileger: null, privilegex: 'on' }),
      snapshot: () => privilegeRow(objectB) });
    await ledger.probe(s, { label: 'admin/ProviderPrivilege submit=Delete (row B)',
      path: privilegeUpdate.path, params: forB({ buttonUpdate: null, submit: 'Delete' }),
      snapshot: () => privilegeRow(objectB) });
  });

  // ── 2. Provider Role ──────────────────────────────────────────────────────────────────────
  let roleAdd;
  let roleUpdate;
  await s.step(STEP.role, async () => {
    const page = await openPage(s, '/admin/ProviderRole', 'input[name="keyword"]');
    const filter = async () => {
      await page.locator('input[name="keyword"]').first().fill(marker);
      await clickAndAwaitReload(page, page.locator('input[name="search"]').first(), { label: 'role filter' });
    };
    await filter();
    const rows = () => page.locator('tr', { has: page.locator(`input[name="providerId"][value="${throwawayNo}"]`) });
    h.assert(await rows().count() === 1, 'The role filter does not list the throwaway provider\'s one role');
    const choices = (await rows().first().locator('select[name="roleNew"] option').evaluateAll((options) => options.map((o) => o.value)))
      .filter((value) => value !== '-' && value !== 'doctor' && value !== 'admin');
    if (choices.length < 2) throw new h.SkipCheck('Fewer than two assignable roles besides doctor and admin');
    const [added, changed] = choices;

    const before = rolesOf();
    await rows().first().locator('select[name="roleNew"]').selectOption(added);
    roleAdd = await captureRequest(page, (url) => url.pathname.endsWith('/admin/ProviderRole'),
      () => clickAndAwaitReload(page, rows().first().locator('input[name="submit"]').first(), { label: 'role Add' }));
    h.assert(roleAdd.status === 200 && roleAdd.params.get('roleNew') === added && roleAdd.params.get('providerId') === throwawayNo,
      `The role Add did not post the chosen role for the throwaway (HTTP ${roleAdd.status})`);
    assertTokenPosted(roleAdd, 'The role Add');
    await h.assertNotErrorPage(page, 'role list after Add');
    await expectValue(sql, `SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(throwawayNo)} AND role_name=${q(added)}`, '1',
      'Add did not give the throwaway the chosen role');
    h.assert(rolesOf().startsWith(before), 'Add changed the throwaway\'s existing role');

    await filter();
    const doctorRow = page.locator('tr', { has: page.locator(`input[name="providerId"][value="${throwawayNo}"]`) })
      .filter({ has: page.locator('input[name="roleOld"][value="doctor"]') });
    h.assert(await doctorRow.count() === 1, 'The role list does not show the throwaway\'s doctor row');
    await doctorRow.locator('select[name="roleNew"]').selectOption(changed);
    roleUpdate = await captureRequest(page, (url) => url.pathname.endsWith('/admin/ProviderRole'),
      () => clickAndAwaitReload(page, doctorRow.locator('input[name="buttonUpdate"]'), { label: 'role Update' }));
    h.assert(roleUpdate.status === 200 && roleUpdate.params.has('buttonUpdate') && roleUpdate.params.get('roleOld') === 'doctor',
      `The role Update did not post buttonUpdate for the doctor row (HTTP ${roleUpdate.status})`);
    assertTokenPosted(roleUpdate, 'The role Update');
    await h.assertNotErrorPage(page, 'role list after Update');
    await expectValue(sql, `SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(throwawayNo)} AND role_name=${q(changed)}`, '1',
      'Update did not change the throwaway\'s doctor role');
    await page.close();

    const roleId = roleUpdate.params.get('roleId');
    h.assert(/^[1-9]\d*$/.test(roleId || ''), 'The Update posted no role id');
    // `doctor` is gone now: a GET that wrote would bring it back, add it, or remove the row.
    await ledger.probe(s, { label: 'admin/ProviderRole submit=Add',
      path: roleAdd.path, params: replayParams(roleAdd.params, { roleNew: 'doctor' }), snapshot: rolesOf });
    await ledger.probe(s, { label: 'admin/ProviderRole submit=Ajouter (the label a French browser posts)',
      path: roleAdd.path, params: replayParams(roleAdd.params, { roleNew: 'doctor', submit: 'Ajouter' }), snapshot: rolesOf });
    await ledger.probe(s, { label: 'admin/ProviderRole buttonUpdate',
      path: roleUpdate.path, params: replayParams(roleUpdate.params, { roleOld: changed, roleNew: 'doctor' }), snapshot: rolesOf });
    await ledger.probe(s, { label: 'admin/ProviderRole submit=Delete',
      path: roleUpdate.path,
      params: replayParams(roleUpdate.params, { buttonUpdate: null, roleOld: changed, roleNew: null, submit: 'Delete' }),
      snapshot: rolesOf });
    await ledger.probe(s, { label: 'admin/ProviderRole buttonSetPrimaryRole',
      path: roleUpdate.path,
      params: new URLSearchParams({ primaryRoleProvider: throwawayNo, primaryRoleRole: changed,
        buttonSetPrimaryRole: 'Update Primary EMR Role' }),
      snapshot: () => `${rolesOf()}#${programRolesOf()}` });
  });

  // ── 3. Message Delete ─────────────────────────────────────────────────────────────────────
  let messageDelete;
  await s.step(STEP.message, async () => {
    const { page } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT });
    await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
    await h.assertNotErrorPage(page, 'messenger inbox');
    await clickAndAwaitReload(page, page.locator(`a[href*="/messenger/ViewMessage?messageID=${messages.A}&"]`).first(),
      { label: 'open message A' });
    await page.locator('button[name="delete"]').waitFor({ timeout: TIMEOUT });
    messageDelete = await captureRequest(page, (url) => url.pathname.endsWith('/messenger/HandleMessages'),
      () => clickAndAwaitReload(page, page.locator('button[name="delete"]'), { label: 'message Delete' }));
    h.assert(messageDelete.status === 200 && messageDelete.params.has('delete')
      && messageDelete.params.get('messageNo') === messages.A, `Delete did not post message A (HTTP ${messageDelete.status})`);
    assertTokenPosted(messageDelete, 'The message Delete');
    await h.assertNotErrorPage(page, 'message list after Delete');
    h.assert(new URL(page.url()).pathname.endsWith('/messenger/HandleMessages'),
      `Delete did not land on the message list (left on ${new URL(page.url()).pathname})`);
    await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(status),'') FROM messagelisttbl WHERE message=${messages.A}
      AND provider_no=${q(provider)}`, 'del', 'Delete did not delete message A');
    h.assert(messageStatus('B') === 'new', 'Deleting message A changed message B');
    await page.close();

    // The button posts an empty value, which replayParams drops: name it, as any link could.
    await ledger.probe(s, { label: 'messenger/HandleMessages delete (message B)',
      path: messageDelete.path, params: replayParams(messageDelete.params, { messageNo: messages.B, delete: 'Delete' }),
      snapshot: () => messageStatus('B') });
  });

  // ── 4. Dx code description ────────────────────────────────────────────────────────────────
  let descriptionSave;
  await s.step(STEP.dx, async () => {
    const page = await openPage(s, '/oscarReport/ViewEditCodeDesc', 'input[name="editingCodeDesc"]');
    // Nothing opens this page with its code filled in; set the hidden fields the session would hold.
    await page.locator('input[name="editingCodeType"]').evaluate((input) => { input.value = 'icd9'; });
    await page.locator('input[name="editingCodeCode"]').evaluate((input, value) => { input.value = value; }, code);
    await page.locator('input[name="editingCodeDesc"]').fill(`${marker} edited`);
    descriptionSave = await captureRequest(page, (url) => url.pathname.endsWith('/report/DxresearchReport'),
      () => clickAndAwaitReload(page, page.locator('input[type="submit"][name="submit"]'), { label: 'description Modify' }));
    h.assert(descriptionSave.status === 200 && descriptionSave.params.get('method') === 'editDesc',
      `Modify did not post method=editDesc (HTTP ${descriptionSave.status})`);
    assertTokenPosted(descriptionSave, 'The description Modify');
    await h.assertNotErrorPage(page, 'Dx registry report after Modify');
    await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(description),'') FROM icd9 WHERE icd9=${q(code)}`,
      `${marker} edited`, 'Modify did not save the owned code\'s description');
    await page.close();

    await ledger.probe(s, { label: 'report/DxresearchReport method=editDesc',
      path: descriptionSave.path,
      params: replayParams(descriptionSave.params, { submit: null, editingCodeDesc: `${marker} GET` }),
      snapshot: codeDescription });
  });

  await s.step(STEP.refused, async () => {
    ledger.assertAllRefused();
    h.assert(privilegeRow(objectB) === `r|1|${provider}`, 'A replay changed owned privilege row B');
    h.assert(messageStatus('B') === 'new', 'A replay deleted owned message B');
    h.assert(codeDescription() === `${marker} edited`, 'A replay changed the owned code description');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow, STEP };
