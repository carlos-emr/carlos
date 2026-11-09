#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue changes and deletions reach the patient issue the clinician clicked (#6090).
 *
 * The note editor rebuilds its issue checklist from storage on every editor refresh (a CPP save,
 * a note save, a new note), ordered by `resolved`, so resolving an issue moves it to the end of the
 * rebuilt list. A click on the old rendering must still change or delete the issue it showed.
 * The check resolves one issue, holds the editor refresh that reorders the list (the server has
 * already rebuilt it; only the browser's copy of the response waits), and clicks another issue's
 * Acute radio during that window. Then it deletes two issues, the second behind the first's held
 * response. It asserts every issue's stored flags and existence by identity after each step.
 *
 * The same script runs against a build without the fix: there the click during the held refresh
 * and the second delete reach the server with a list position and change or delete the wrong
 * issue, which the identity assertions report. ISSUE_IDENTITY_SCREENSHOT_DIR, when set, receives
 * screenshots of the checklist for the PR (before/after evidence).
 *
 * Fixtures: four casemgmt_issue rows of the run's own FAKE patient, created with SQL from catalog
 * issues of the role the test provider sees in the chart (the role of CTCMM1000 "Safety"), not linked
 * to any note so each row offers Delete. Cleanup deletes the patient's issues, notes, links, drafts,
 * locks and eChart row by the patient key and asserts them gone.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const TIMEOUT = 30000;
const ROLE_ISSUE_CODE = 'CTCMM1000';
const SHOT_DIR = (process.env.ISSUE_IDENTITY_SCREENSHOT_DIR || '').trim();
const ENTRY = /\/CaseManagementEntry(\?|$)/;
const ACUTE = 'input[type="radio"][name$=".issue.acute"][value="true"]';
const RESOLVED = 'input[type="radio"][name$=".issue.resolved"][value="true"]';

const STEP = {
  reorder: 'a status click during the editor refresh that reorders the checklist changes only the clicked issue',
  change: 'after the refresh, the clicked issue\'s Acute radio sets acute on that issue alone',
  remove: 'two Delete clicks from one rendering delete exactly the two clicked issues',
};

const flags = (acute, resolved) => ({ acute: acute ? '1' : '0', certain: '0', major: '0', resolved: resolved ? '1' : '0' });
const saveRequest = method => request => request.method() === 'POST' && ENTRY.test(request.url())
  && new URLSearchParams(request.postData() || '').get('method') === method;

/** Delete every chart row of the run's patient (it owns them all), and prove they are gone. */
function removeOwnedChartRows(sql, patient) {
  const ids = rows => (rows.length ? rows.map(([id]) => { h.assert(/^\d+$/.test(id), 'An owned id is not a number'); return id; }).join(',') : '0');
  const notes = ids(sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`));
  const issues = ids(sql.rows(`SELECT id FROM casemgmt_issue WHERE demographic_no=${patient}`));
  sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes}) OR id IN (${issues});
    DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
    DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
    DELETE FROM casemgmt_note WHERE demographic_no=${patient};
    DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
    DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
    DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
    DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
    DELETE FROM eChart WHERE demographicNo=${patient}`);
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${notes}) OR id IN (${issues}))
    + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})`) === '0',
  'Owned chart issues, notes, links, drafts, locks or summary rows were not removed');
}

/** Seed issues A, B, C and D (unresolved, chronic, uncertain, not major) from catalog issues of one role. */
function seedIssues(sql, patient) {
  const q = h.sqlString;
  const role = sql.rows(`SELECT role FROM issue WHERE code=${q(ROLE_ISSUE_CODE)}`);
  if (role.length !== 1) throw new h.SkipCheck(`The issue catalog has no single row with code ${ROLE_ISSUE_CODE}`);
  // Distinct descriptions, so each checklist row can be found by the text it shows.
  const catalog = sql.rows(`SELECT MIN(issue_id), description FROM issue WHERE role=${q(role[0][0])}
    AND description IS NOT NULL AND description <> '' GROUP BY description ORDER BY MIN(issue_id) LIMIT 4`);
  if (catalog.length !== 4) throw new h.SkipCheck(`The issue catalog has fewer than four ${role[0][0]} issues`);
  const program = sql.value("SELECT id FROM program WHERE name='OSCAR' LIMIT 1") || '10034';
  h.assert(/^\d+$/.test(program), 'The chart program is not a number');
  const issues = {};
  for (const [index, [issueId, description]] of catalog.entries()) {
    const id = sql.value(`INSERT INTO casemgmt_issue (demographic_no, issue_id, acute, certain, major, resolved, program_id, type, update_date)
      VALUES (${patient}, ${issueId}, 0, 0, 0, 0, ${program}, ${q(role[0][0])}, NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A casemgmt_issue fixture was not created');
    issues['ABCD'[index]] = { id, description };
  }
  return issues;
}

/** The check's view of the database and the chart, shared by the steps. */
function checkContext(s, chart, issues) {
  const { sql, patient } = s;
  const label = id => Object.entries(issues).find(([, issue]) => issue.id === id)?.[0] || `#${id}`;
  const t = { s, chart, issues, label, mutations: [] };
  /** Every owned issue's stored flags by name, so an assertion can say which issue changed. */
  t.stored = () => {
    const rows = sql.rows(`SELECT id, acute, certain, major, resolved FROM casemgmt_issue WHERE demographic_no=${patient} ORDER BY id`);
    return Object.fromEntries(rows.map(([id, acute, certain, major, resolved]) => [label(id), { acute, certain, major, resolved }]));
  };
  t.expectStored = (expected, message) => {
    const actual = t.stored();
    console.log(`  stored issues: ${JSON.stringify(actual)}`);
    h.assert(JSON.stringify(actual) === JSON.stringify(expected), `${message}: stored ${JSON.stringify(actual)}, expected ${JSON.stringify(expected)}`);
  };
  chart.on('request', request => {
    for (const method of ['issueChange', 'issueDelete']) {
      if (!saveRequest(method)(request)) continue;
      const body = new URLSearchParams(request.postData() || '');
      t.mutations.push({ method, caseIssueId: body.get('caseIssueId'), lineId: body.get('lineId'), deleteId: body.get('deleteId') });
    }
  });
  return t;
}

async function settled(chart) {
  await chart.waitForFunction(() => window.issueUpdatesPending === 0, null, { timeout: TIMEOUT });
}

/** Show both checklist sections and open the issue's status panel, as a clinician's toggle does. */
async function openRow(t, issue) {
  const { chart } = t;
  await chart.evaluate(() => {
    window.showIssue = true;
    for (const id of ['noteIssues', 'noteIssues-resolved', 'noteIssues-unresolved']) {
      const section = document.getElementById(id);
      if (section) section.style.display = 'block';
    }
  });
  // The checklist cell that names the issue holds its checkbox, links and status radios. Mark it
  // by exact text in the page (the fragment is replaced after every update, so mark it each time).
  const marked = await chart.evaluate(({ description, key }) => {
    const cells = [...document.querySelectorAll('#noteIssues td')].filter(cell => {
      const link = cell.querySelector('a[onclick^="return displayIssue"]');
      return link && link.textContent.trim() === description;
    });
    cells.forEach(cell => cell.setAttribute('data-pw-issue', key));
    return cells.length;
  }, { description: issue.description, key: t.label(issue.id) });
  h.assert(marked === 1, `The checklist shows ${marked} rows for issue ${t.label(issue.id)}, expected one`);
  const row = chart.locator(`#noteIssues td[data-pw-issue="${t.label(issue.id)}"]`);
  const radio = row.locator(ACUTE);
  if (!(await radio.isVisible())) await row.locator('a[onclick^="return displayIssue"]').click({ timeout: TIMEOUT });
  await radio.waitFor({ state: 'visible', timeout: TIMEOUT });
  return row;
}

async function shot(chart, name) {
  if (!SHOT_DIR) return;
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  const target = path.join(SHOT_DIR, `${name}.png`);
  await chart.locator('#noteIssues').screenshot({ path: target }).catch(() => chart.screenshot({ path: target, fullPage: true }));
  console.log(`  screenshot ${target}`);
}

/** Choose a radio and wait for the issueChange it sends; returns that response's status. */
async function chooseAndWait(chart, radio) {
  const [response] = await Promise.all([
    chart.waitForResponse(candidate => saveRequest('issueChange')(candidate.request()), { timeout: TIMEOUT }),
    radio.check({ timeout: TIMEOUT }),
  ]);
  return response.status();
}

/**
 * Let `method` requests reach the server, then hold their responses until released: the server
 * has finished the work, and only the browser has not yet seen the answer.
 */
async function holdResponses(chart, method) {
  const held = [];
  const handler = async route => {
    if (!saveRequest(method)(route.request())) return route.fallback();
    const response = await route.fetch();
    held.push(() => route.fulfill({ response }));
    return undefined;
  };
  await chart.route(ENTRY, handler);
  return {
    async arrived(count = 1) {
      const deadline = Date.now() + TIMEOUT;
      while (held.length < count && Date.now() < deadline) await chart.waitForTimeout(100);
      h.assert(held.length >= count, `No ${method} response arrived to hold`);
    },
    async release() {
      await chart.unroute(ENTRY, handler);
      held.splice(0).forEach(fulfil => fulfil());
    },
  };
}

async function resolveFirstIssue(t) {
  const row = await openRow(t, t.issues.A);
  const status = await chooseAndWait(t.chart, row.locator(RESOLVED));
  h.assert(status === 200, `Resolving issue A answered HTTP ${status}`);
  await settled(t.chart);
  t.expectStored({ A: flags(false, true), B: flags(false, false), C: flags(false, false), D: flags(false, false) }, 'Resolving A changed another issue');
}

async function clickDuringReorderingRefresh(t) {
  const { chart } = t;
  // The CPP editor's save starts this refresh with the same call; the server rebuilds the list
  // from storage (unresolved first), so A moves from the first position to the last.
  const hold = await holdResponses(chart, 'edit');
  await chart.evaluate(() => ajaxUpdateIssues('edit', document.getElementById('noteIssues').parentNode.id, true));
  await hold.arrived();
  const radio = (await openRow(t, t.issues.B)).locator(ACUTE);
  const enabled = await radio.isEnabled();
  const before = t.mutations.length;
  // Without the fix the radio is live and its request names the row's old position. With it, a
  // click on the disabled radio does nothing, as a clinician's click does nothing.
  const outcome = enabled ? `enabled, answered HTTP ${await chooseAndWait(chart, radio)}` : 'disabled';
  if (!enabled) {
    await chart.evaluate(el => el.click(), await radio.elementHandle());
    await chart.waitForTimeout(500);
  }
  await shot(chart, '2-click-during-refresh');
  console.log(`  B's Acute radio during the held refresh: ${outcome}; requests ${JSON.stringify(t.mutations.slice(before))}`);
  await hold.release();
  await settled(chart);
  const actual = t.stored();
  const others = ['A', 'C', 'D'].filter(name => actual[name].acute !== '0');
  h.assert(others.length === 0, `Clicking issue B's Acute during the refresh set acute on issue ${others.join(', ')} instead`);
  h.assert(enabled || t.mutations.length === before, 'A disabled radio still sent an issue change');
}

async function changeAfterRefresh(t) {
  const radio = (await openRow(t, t.issues.B)).locator(ACUTE);
  if (!(await radio.isChecked())) {
    const status = await chooseAndWait(t.chart, radio);
    h.assert(status === 200, `Choosing Acute for B answered HTTP ${status}`);
    await settled(t.chart);
  }
  t.expectStored({ A: flags(false, true), B: flags(true, false), C: flags(false, false), D: flags(false, false) },
    'Choosing Acute for issue B did not set acute on B alone');
  h.assert(await (await openRow(t, t.issues.B)).locator(ACUTE).isChecked(), 'The refreshed checklist does not show B\'s stored Acute state');
  await shot(t.chart, '3-after-status-change');
}

async function deleteTwoFromOneRendering(t) {
  const { chart, s } = t;
  const deleteLink = async issue => (await openRow(t, issue)).locator('a', { hasText: /^Delete$/ });
  const hold = await holdResponses(chart, 'issueDelete');
  const before = t.mutations.length;
  await (await deleteLink(t.issues.C)).click({ timeout: TIMEOUT });
  await hold.arrived();
  // The second click comes from the same rendering, while the first deletion's answer is held.
  await (await deleteLink(t.issues.D)).click({ timeout: TIMEOUT });
  await chart.waitForTimeout(500);
  const sent = t.mutations.slice(before);
  console.log(`  delete requests behind the held response: ${JSON.stringify(sent)}`);
  if (sent.length > 1) await hold.arrived(2);
  await hold.release();
  await settled(chart);
  if (sent.length === 1) {
    // The refused second click is repeated from the refreshed checklist.
    await (await deleteLink(t.issues.D)).click({ timeout: TIMEOUT });
    await expectValue(s.sql, `SELECT COUNT(*) FROM casemgmt_issue WHERE id=${t.issues.D.id}`, '0', 'Deleting issue D did not remove it');
    await settled(chart);
  }
  const remaining = Object.keys(t.stored()).sort();
  h.assert(JSON.stringify(remaining) === '["A","B"]', `Deleting C then D left issues [${remaining.join(', ')}], expected [A, B]`);
  t.expectStored({ A: flags(false, true), B: flags(true, false) }, 'A deletion changed a remaining issue');
  await shot(chart, '4-after-deletions');
}

async function workflow(s) {
  s.cleanup(() => removeOwnedChartRows(s.sql, s.patient));
  const issues = seedIssues(s.sql, s.patient);
  const chart = await s.chart();
  await chart.locator('textarea[name="caseNote_note"]').first().waitFor({ state: 'visible', timeout: TIMEOUT });
  const t = checkContext(s, chart, issues);

  await s.step('(fixture) the editor checklist shows the four owned issues, unresolved and chronic', async () => {
    for (const issue of Object.values(issues)) await openRow(t, issue);
    t.expectStored({ A: flags(false, false), B: flags(false, false), C: flags(false, false), D: flags(false, false) }, 'The fixture is not as seeded');
    await shot(chart, '1-initial-checklist');
  });
  await s.step('(control) resolving issue A through its radio sets resolved on A only', () => resolveFirstIssue(t));
  await s.step(STEP.reorder, () => clickDuringReorderingRefresh(t));
  await s.step(STEP.change, () => changeAfterRefresh(t));
  await s.step(STEP.remove, () => deleteTwoFromOneRendering(t));
  await releaseChartLocks(s.context, s.config.baseUrl, [chart]);
}

module.exports = { workflow, STEP };
if (require.main === module) runWorkflow('echart-issue-mutation-identity', workflow, { openPatient: true });
