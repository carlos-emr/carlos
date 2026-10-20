#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Default encounter issues: the CAISI administration page that sets them, and what they do to a
 * brand-new patient's first encounter.
 *
 * User path: Schedule ▸ Administration ▸ CAISI ▸ "Default Encounter Issue" (DefaultEncounterIssue,
 * in the #dynamic-content iframe) ▸ the list ▸ "Assign default issues" ▸ issue codes, comma
 * separated ▸ Save; Save again with nothing entered; "Remove default issues" ▸ a code ▸ Remove.
 * Then Master Record ▸ E-Chart for the owned patient, who has no notes yet.
 *
 * Asserted: the list shows the saved defaults (by issue description) and who saved them; Save
 * stores exactly the issues of the codes given, in place of the earlier list, as the test login,
 * with a fresh update time and the first assignment time kept; Save with nothing entered is
 * refused in the page ("Issues can't be empty!") and stores nothing; Remove takes off only the
 * code given. The first encounter: with wl_default_issue off (the shipped default) it opens and
 * the patient carries none of the defaults; with it on (run with WL_DEFAULT_ISSUE=true when the
 * server was started so) it should open as well. Last, a code assigns that issue only: ICSW100
 * must not also assign ICSW1000.
 *
 * Known failures, held for the findings log: with wl_default_issue on, a new patient's first
 * encounter fails with HTTP 500 unless the session holds a program the login is assigned to, and
 * the schedule's CAISI program list offers none to a login without a bed program
 * (EctIncomingEncounter2Action.getIssueIdSet; release/2026.08 opens the encounter since #4843);
 * and codes match as substrings of any issue's code
 * or description (IssueDAO.findIssueBySearch), so ICSW100 also assigns ICSW1000, and Remove
 * takes off both. Not covered: an encounter that does assign the defaults, which needs a CAISI
 * bed program for the login.
 *
 * Needs caisi=on: the CAISI group, and this link in it, are rendered only with the caisi module.
 * Without it the check SKIPs, as admin-issue-editor does.
 *
 * Fixtures: the runWorkflow FAKE patient (its chart rows go with it). default_issue holds one
 * clinic-wide row; it is recorded first and restored exactly afterwards (re-inserted with its id
 * when Remove deleted it). A row the page creates during the run (after Remove deletes the last
 * one) is captured by id after each change, and only those ids are deleted. The restored table
 * is then compared with the recorded one. default_issue is clinic-wide, so the check needs an
 * exclusive run.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
/** Three codes no other issue's code or description contains (see the last step for one that is). */
const ASSIGNED = ['OMeds', 'CTCMM5000', 'ICSW210'];
const REMOVED = 'CTCMM5000';
/** ICSW100 is also the start of ICSW1000. */
const PREFIX_CODE = 'ICSW100';
const PREFIX_OF = 'ICSW1000';
const WL_ON = process.env.WL_DEFAULT_ISSUE === 'true';

const sorted = values => [...values].map(String).sort((a, b) => Number(a) - Number(b));

/** Administration ▸ CAISI ▸ Default Encounter Issue, inside the administration iframe. */
async function openDefaultIssues(s) {
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'default-issue-administration', timeout: TIMEOUT });
  const link = admin.getByRole('link', { name: 'Default Encounter Issue', exact: true, includeHidden: true }).first();
  // leftNav.jspf renders the CAISI group only inside <caisi:isModuleLoad moduleName="caisi">.
  if (await link.count() === 0) {
    throw new h.SkipCheck('Administration offers no Default Encounter Issue link to this login; '
      + 'it needs the CAISI module (caisi=on in carlos.properties) and _admin.caisi');
  }
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The default issue page did not load in the administration iframe');
  await frame.getByRole('button', { name: 'Assign default issues' }).waitFor();
  /** Runs a click that loads the next page in the iframe, and waits for it. */
  async function navigate(action) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: candidate => candidate === frame, timeout: TIMEOUT });
    navigated.catch(() => {});
    await action();
    await navigated;
    await frame.waitForLoadState('load', { timeout: TIMEOUT });
  }
  return { admin, frame, navigate };
}

async function workflow(s) {
  const { sql, patient, provider } = s;

  const issues = new Map(sql.rows(`SELECT code, issue_id, description FROM issue
    WHERE code IN (${[...ASSIGNED, PREFIX_CODE, PREFIX_OF].map(h.sqlString).join(',')})`)
    .map(([code, id, description]) => [code, { id, description }]));
  for (const code of [...ASSIGNED, PREFIX_CODE, PREFIX_OF]) {
    if (!issues.has(code)) throw new h.SkipCheck(`The CAISI issue ${code} is not in this database's issue list`);
  }
  const ids = codes => sorted(codes.map(code => issues.get(code).id));

  const read = () => sql.rows(`SELECT id, COALESCE(issue_ids,''), provider_no,
      DATE_FORMAT(assigned_time,'%Y-%m-%d %H:%i:%s'), DATE_FORMAT(update_time,'%Y-%m-%d %H:%i:%s'),
      update_time >= NOW() - INTERVAL 5 MINUTE
    FROM default_issue ORDER BY assigned_time DESC, id DESC`)
    .map(([id, issueIds, savedBy, assigned, updated, fresh]) => ({ id, issueIds, savedBy, assigned, updated, fresh }));
  const recorded = read();
  const recordedIds = new Set(recorded.map(row => row.id));
  // Ids of rows this run's own changes created: taken after each change, so cleanup deletes
  // exactly those and never a row it did not create.
  const created = new Set();
  const track = () => read().forEach(row => { if (!recordedIds.has(row.id)) created.add(row.id); });
  /** Runs a page action that can change default_issue, then captures any row it created. */
  async function change(action) {
    try { await action(); } finally { track(); }
  }
  s.cleanup(() => {
    // Rows this run created first, then the recorded rows exactly as they were.
    for (const id of created) sql.execute(`DELETE FROM default_issue WHERE id=${Number(id)}`);
    for (const row of recorded) {
      const values = `${h.sqlString(row.issueIds)},${h.sqlString(row.savedBy)},${h.sqlString(row.assigned)},${h.sqlString(row.updated)}`;
      if (sql.value(`SELECT COUNT(*) FROM default_issue WHERE id=${row.id}`) === '1') {
        sql.execute(`UPDATE default_issue SET issue_ids=${h.sqlString(row.issueIds)}, provider_no=${h.sqlString(row.savedBy)},
          assigned_time=${h.sqlString(row.assigned)}, update_time=${h.sqlString(row.updated)} WHERE id=${row.id}`);
      } else {
        sql.execute(`INSERT INTO default_issue(id,issue_ids,provider_no,assigned_time,update_time) VALUES(${row.id},${values})`);
      }
    }
    const restored = read().map(({ fresh, ...row }) => row);
    h.assert(JSON.stringify(restored) === JSON.stringify(recorded.map(({ fresh, ...row }) => row)),
      'default_issue was not restored to the rows recorded before the run');
  });
  const latest = () => read()[0];
  const listed = async frame => (await frame.locator('table', { hasText: 'Assigned Date' }).innerText()).replace(/\s+/g, ' ');

  const { admin, frame, navigate } = await openDefaultIssues(s);

  await s.step('the list shows the saved defaults and who saved them', async () => {
    const text = await listed(frame);
    const current = recorded[0];
    if (!current) {
      h.assert(/There's no default issues!/.test(text), 'With no defaults saved, the list did not say so');
      return;
    }
    for (const id of current.issueIds.split(',').filter(Boolean)) {
      const description = sql.value(`SELECT description FROM issue WHERE issue_id=${Number(id)}`);
      if (description) h.assert(text.includes(description.trim()), `The list does not show the saved default "${description}"`);
    }
    const [lastName, firstName] = sql.rows(`SELECT last_name, first_name FROM provider WHERE provider_no=${h.sqlString(current.savedBy)}`)[0] || [];
    h.assert(lastName && text.includes(lastName) && text.includes(firstName), 'The list does not name who saved the defaults');
    h.assert(text.includes(current.assigned) && text.includes(current.updated), 'The list does not show when the defaults were assigned and updated');
  });

  await s.step(`Save stores exactly the issues of ${ASSIGNED.join(', ')}, in place of the earlier list, as the test login`, async () => {
    await navigate(() => frame.getByRole('button', { name: 'Assign default issues' }).click());
    await frame.locator('#issuesNames').fill(ASSIGNED.join(', '));
    await change(() => navigate(() => frame.getByRole('button', { name: 'Save' }).click()));
    const row = latest();
    h.assert(row && JSON.stringify(sorted(row.issueIds.split(','))) === JSON.stringify(ids(ASSIGNED)),
      `Save did not store exactly the issues of ${ASSIGNED.join(', ')} (stored ${row && row.issueIds})`);
    h.assert(row.savedBy === provider && row.fresh === '1', 'Save did not record the test login and a fresh update time');
    if (recorded[0]) {
      h.assert(row.id === recorded[0].id && row.assigned === recorded[0].assigned,
        'Save did not update the existing defaults in place, keeping when they were first assigned');
    }
    h.assert(read().length === Math.max(recorded.length, 1), 'Save added a second set of defaults');
    const text = await listed(frame);
    for (const code of ASSIGNED) h.assert(text.includes(issues.get(code).description.trim()), `The list does not show ${code}`);
  });

  await s.step('Save with nothing entered is refused in the page and stores nothing', async () => {
    const before = latest();
    await navigate(() => frame.getByRole('button', { name: 'Assign default issues' }).click());
    await frame.locator('#issuesNames').fill('   ');
    const dialogs = await h.withExpectedDialogs(admin, () => change(async () => {
      await frame.getByRole('button', { name: 'Save' }).click();
      await frame.locator('#issuesNames').waitFor();
    }));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === "Issues can't be empty!",
      `The empty Save was not refused with "Issues can't be empty!" (${JSON.stringify(dialogs.map(d => d.text))})`);
    h.assert(JSON.stringify(latest()) === JSON.stringify(before), 'The refused Save changed the defaults');
    await navigate(() => frame.getByRole('button', { name: 'Cancel' }).click());
  });

  await s.step(`Remove takes off ${REMOVED} only`, async () => {
    await navigate(() => frame.getByRole('button', { name: 'Remove default issues' }).click());
    await frame.locator('input[name="issueNames"]').fill(REMOVED);
    await change(() => navigate(() => frame.getByRole('button', { name: 'Remove' }).click()));
    const kept = ASSIGNED.filter(code => code !== REMOVED);
    const row = latest();
    h.assert(row && JSON.stringify(sorted(row.issueIds.split(','))) === JSON.stringify(ids(kept)),
      `Remove did not leave exactly ${kept.join(', ')} (left ${row && row.issueIds})`);
    const text = await listed(frame);
    h.assert(!text.includes(issues.get(REMOVED).description.trim()), `The list still shows ${REMOVED}`);
  });

  const defaults = ids(ASSIGNED.filter(code => code !== REMOVED));
  const carried = () => sql.rows(`SELECT issue_id FROM casemgmt_issue WHERE demographic_no=${Number(patient)}
    AND issue_id IN (${defaults.join(',')})`).map(([id]) => id);
  h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${Number(patient)}`) === '0',
    'The owned patient already has notes, so the first-encounter defaults cannot apply');
  if (WL_ON) {
    await s.step('with wl_default_issue on, a new patient\'s first encounter opens', async () => {
      // A program is chosen on the schedule only through the CAISI program list, which offers none to a
      // login without a bed program; getIssueIdSet then fails on the missing or unassigned program.
      await s.chart();
    });
  } else {
    await s.step('with wl_default_issue off, a new patient\'s first encounter opens and carries none of the defaults', async () => {
      await s.chart();
      h.assert(carried().length === 0, `The first encounter assigned default issues (${carried().join(',')}) although wl_default_issue is off`);
    });
  }

  await s.step(`a code assigns that issue only: ${PREFIX_CODE} does not also assign ${PREFIX_OF}`, async () => {
    await navigate(() => frame.getByRole('button', { name: 'Assign default issues' }).click());
    await frame.locator('#issuesNames').fill(PREFIX_CODE);
    await change(() => navigate(() => frame.getByRole('button', { name: 'Save' }).click()));
    const row = latest();
    h.assert(row && JSON.stringify(sorted(row.issueIds.split(','))) === JSON.stringify(ids([PREFIX_CODE])),
      `Saving ${PREFIX_CODE} stored ${row && row.issueIds}, not the one issue ${issues.get(PREFIX_CODE).id}`);
  });
}

if (require.main === module) runWorkflow('admin-default-encounter-issues', workflow);
module.exports = { workflow };
