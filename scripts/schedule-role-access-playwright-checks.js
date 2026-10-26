#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * schedule-role-access -- issue #2689: what the schedule landing shows each kind of account.
 *
 * User path: log in, land on the schedule (provider/providercontrol), let it finish loading.
 * Asserted, each with a fresh login of one owned throwaway account whose roles are changed
 * between steps:
 *   - receptionist: the schedule renders with no HTTP error, no script error and no dialog; the
 *     tab-count poll asks only for the tabs the role's menu shows (a receptionist has none of
 *     Inbox, Tickler, Msg, Consultation), and asking directly for a hidden tab's count is
 *     refused with 403 (it answered 500 for Msg before), an unknown tab with 400;
 *   - no role at all: the landing is refused with 403 and the page says the account has no role
 *     and what to ask an administrator for, instead of the generic "insufficient privileges";
 *     nothing of the schedule renders or is polled; Log Out is offered;
 *   - a role without schedule access: the same 403 refusal, saying the role lacks the schedule.
 * The configured test login (a doctor) is the baseline: its own landing refreshed every tab it
 * renders with HTTP 200, and its Msg count still answers.
 * Fixtures: one throwaway login (owned provider, security and role rows) and one owned role with
 * its privilege rows; all removed in cleanup and checked.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { bundleMessage, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const TAB_IDS = ['oscar_new_lab', 'oscar_new_msg', 'oscar_new_tickler', 'oscar_aged_consults', 'oscar_scratch'];
const TAB_REFRESH_PATH = '/provider/ViewTabAlertsRefresh';
const LANDING_PATH = '/provider/providercontrol';

/** The tab ids a recorder's pages polled tab counts for, with each answer's status. */
function tabRefreshes(recorder) {
  return recorder.requestLog
    .filter(entry => new URL(entry.url).pathname.endsWith(TAB_REFRESH_PATH))
    .map(entry => ({ id: new URL(entry.url).searchParams.get('id'), status: entry.status }));
}

/** Log the throwaway in through the real form in its own context and recorder. */
async function signIn(s, fixture, label) {
  const recorder = h.createRecorder();
  const context = await h.newContext(s.context.browser(), s.config);
  context.setDefaultTimeout(20000);
  s.cleanup(() => context.close().catch(() => {}));
  const page = await h.login(context, { ...s.config, testUser: fixture.username }, recorder, { label });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return { context, recorder, page };
}

/** A refused landing: exactly one 403, on the landing itself, and a clean page otherwise. */
async function assertRefusedLanding({ recorder, page }, label, reasonKey, reasonFallback) {
  const refusals = recorder.badResponses.filter(entry => entry.label === label);
  h.assert(refusals.length === 1 && refusals[0].status === 403
    && new URL(refusals[0].url).pathname.endsWith(LANDING_PATH),
  `${label}: the landing must be refused with exactly one 403 on ${LANDING_PATH}, saw ${JSON.stringify(refusals.map(r => [r.status, h.pathOnly(r.url)]))}`);
  recorder.badResponses = recorder.badResponses.filter(entry => entry !== refusals[0]);
  // Chromium reports the refused document load itself on the console; that is the 403 above.
  const refusalNotices = recorder.consoleIssues.filter(entry => entry.label === label
    && /Failed to load resource: the server responded with a status of 403/.test(entry.text));
  h.assert(refusalNotices.length <= 1, `${label}: more than one resource was refused`);
  recorder.consoleIssues = recorder.consoleIssues.filter(entry => !refusalNotices.includes(entry));
  h.assertStrictPage(recorder);

  const reason = (await page.locator('#noScheduleAccessReason').innerText()).replace(/\s+/g, ' ').trim();
  h.assert(reason === bundleMessage(reasonKey, reasonFallback),
    `${label}: the refusal did not explain itself (saw "${reason}")`);
  h.assert(!/insufficient privileges/i.test(await page.locator('body').innerText()),
    `${label}: the generic security-exception page was shown instead of the explanation`);
  h.assert(await page.locator('a.adhour, #oscar_new_msg, #oscar_new_lab').count() === 0,
    `${label}: schedule content rendered for an account without schedule access`);
  h.assert(tabRefreshes(recorder).length === 0, `${label}: a refused landing still polled tab counts`);
  const logout = page.locator('#noScheduleAccessLogout');
  h.assert(await logout.count() === 1 && new URL(await logout.evaluate(a => a.href)).pathname.endsWith('/logoutPage'),
    `${label}: the refusal offers no Log Out`);
}

function schedulelessRole(marker) {
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker), 'The owned role requires the complete run marker');
  // secObjPrivilege.roleUserGroup is varchar(30); keep every marker bit within that key.
  return `${marker}-nosch`;
}

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  const q = h.sqlString;
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const scheduleless = schedulelessRole(marker);
  let roleOwned = false;
  s.cleanup(() => {
    if (!roleOwned) return;
    sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(scheduleless)};
      DELETE FROM secRole WHERE role_name=${q(scheduleless)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(scheduleless)}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(scheduleless)}`) === '0',
    'The owned role without schedule access was not removed');
  });
  fixture.create({ roleNames: ['receptionist'] });
  const roles = () => sql.value(`SELECT COALESCE(GROUP_CONCAT(role_name ORDER BY role_name), '')
    FROM secUserRole WHERE provider_no=${q(fixture.providerNo)}`);
  h.assert(roles() === 'receptionist', `The throwaway must start with only receptionist, has "${roles()}"`);
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup='receptionist'
    AND objectName IN ('_msg','_tickler','_con','_appointment.doctorLink')`) === '0',
  'This install grants receptionist a tab privilege; the hidden-tab assertions below assume the seeded role');

  await s.step('the doctor baseline refreshes every tab its schedule renders, each with HTTP 200', async () => {
    const refreshed = tabRefreshes(s.recorder);
    h.assert(refreshed.length > 0, 'The doctor landing did not poll any tab count');
    for (const { id, status } of refreshed) {
      h.assert(TAB_IDS.includes(id) && status === 200, `The doctor landing's ${id} count answered HTTP ${status}`);
      h.assert(await s.schedule.locator(`#${id}`).count() === 1, `The doctor landing polled ${id}, which it does not render`);
    }
    h.assert(refreshed.some(({ id }) => id === 'oscar_new_msg'), 'The doctor landing did not refresh its Msg count');
    const msg = await s.context.request.get(`${config.baseUrl}${TAB_REFRESH_PATH}?id=oscar_new_msg`, { maxRedirects: 0 });
    try {
      h.assert(msg.status() === 200, `The doctor's Msg count answered HTTP ${msg.status()}`);
    } finally { await msg.dispose(); }
  });

  await s.step('a receptionist lands on the schedule with no error and no poll for a hidden tab', async () => {
    const session = await signIn(s, fixture, 'receptionist');
    const { page, recorder, context } = session;
    h.assert(h.pathOnly(page.url()).endsWith(LANDING_PATH), `The receptionist landed on ${h.pathOnly(page.url())}`);
    await h.assertNotErrorPage(page, 'receptionist schedule');
    await page.locator('a.adhour').first().waitFor({ state: 'visible', timeout: 30000 });
    h.assertStrictPage(recorder);
    for (const { id, status } of tabRefreshes(recorder)) {
      h.assert(await page.locator(`#${id}`).count() === 1,
        `The receptionist schedule polled ${id} (HTTP ${status}), a tab its menu does not show`);
    }
    h.assert(await page.locator('#oscar_new_msg').count() === 0, 'The receptionist menu shows the Msg tab');

    for (const [query, expected] of [['?id=oscar_new_msg', 403], ['?id=oscar_new_lab', 403], ['?id=oscar_unknown', 400], ['', 400]]) {
      const answer = await context.request.get(`${config.baseUrl}${TAB_REFRESH_PATH}${query}`, { maxRedirects: 0 });
      try {
        h.assert(answer.status() === expected,
          `A receptionist asking for ${query || 'no tab'} got HTTP ${answer.status()}, expected ${expected}`);
      } finally { await answer.dispose(); }
    }
  });

  await s.step('an account with no role is refused at the landing with an explanation, not a security exception', async () => {
    sql.execute(`DELETE FROM secUserRole WHERE provider_no=${q(fixture.providerNo)}`);
    h.assert(roles() === '', 'The throwaway still holds a role');
    const session = await signIn(s, fixture, 'no role');
    await assertRefusedLanding(session, 'no role', 'provider.noScheduleAccess.noRole',
      'Your account has no role assigned, so it cannot open the schedule or any other part of CARLOS. '
      + 'Ask a CARLOS administrator to assign your role (Administration, User Management, Assign Role to Provider), '
      + 'then log in again.');
  });

  await s.step('an account whose only role lacks the schedule is refused with that reason', async () => {
    h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(scheduleless)}`) === '0', 'Owned role already exists');
    roleOwned = true;
    sql.execute(`INSERT INTO secRole(role_name,description) VALUES(${q(scheduleless)},'Owned role without schedule access');
      INSERT INTO secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES(${q(scheduleless)},'_msg','r',0,${q(provider)});
      INSERT INTO secUserRole(provider_no,role_name,activeyn,lastUpdateDate)
      VALUES(${q(fixture.providerNo)},${q(scheduleless)},1,NOW())`);
    h.assert(roles() === scheduleless, `The throwaway must hold only the owned role, has "${roles()}"`);
    const session = await signIn(s, fixture, 'role without schedule');
    await assertRefusedLanding(session, 'role without schedule', 'provider.noScheduleAccess.noSchedulePrivilege',
      'Your role does not include access to the schedule. If your work needs the schedule, ask a CARLOS '
      + 'administrator to review your role assignments, then log in again.');
  });
}

if (require.main === module) runWorkflow('schedule-role-access', workflow, { openPatient: false });
module.exports = { workflow, schedulelessRole };
