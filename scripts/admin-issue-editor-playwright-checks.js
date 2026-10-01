#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * CAISI Issue Editor and where its issues are used (coverage plan §3.7 admin-issue-editor).
 *
 * User path: Master Record ▸ E-Chart ▸ Save a plain note (binds the session's case
 * program, whose provider role governs which issues the chart may search), then
 * Schedule ▸ Administration ▸ CAISI ▸ "Issue Editor" (issueAdmin?method=list in the
 * #dynamic-content iframe) ▸ Add Issue ▸ code + description + role ▸ Save, then back
 * in the E-Chart: Social History ▸ Add Item ▸ "Assign Issues" (#issueAutocompleteCPP,
 * the chart's issue search backed by CaseManagementEntry?method=issueList) ▸ Sign & Save.
 *
 * Asserted: the `issue` row (code, description, role) and the list row; a second Add
 * with the same code is refused ("Issue code already exists.") and leaves one row; the
 * chart's issue search offers exactly the new issue; selecting it attaches it to the
 * CPP item (issue_id checkbox) and Sign & Save writes casemgmt_issue for the patient
 * joined to the saved note through casemgmt_issue_notes; a GET against method=save
 * creates nothing.
 *
 * Not covered: the Issue Editor offers no edit path (list links and form binding are
 * commented out) and casemgmt/ViewIssueSearch has no UI opener; both reported.
 *
 * Fixtures and cleanup: one marker-coded issue (PW<hex>) and the owned patient's note,
 * casemgmt_issue, CPP and eChart rows; all removed in cleanup and asserted gone.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const NOTE_EDITOR = '#encMainDiv textarea[name="caseNote_note"]';
const CPP_BOX = '#divR1I1';
const DIALOG = '#showEditNote';

function isEntryPost(response, method) {
  return response.request().method() === 'POST' && /\/CaseManagementEntry/.test(response.url())
    && new URLSearchParams(response.request().postData() || '').get('method') === method;
}

/** Admin ▸ CAISI ▸ Issue Editor, inside the administration iframe. */
async function openIssueEditor(s) {
  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'issue-administration', timeout: 20000});
  const link = admin.getByRole('link', {name: 'Issue Editor', exact: true, includeHidden: true}).first();
  h.assert(await link.count() === 1, 'Administration offers no Issue Editor link');
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The issue editor iframe did not load');
  await frame.getByRole('button', {name: 'Add Issue'}).waitFor();
  async function navigate(action) {
    const navigated = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: 20000});
    navigated.catch(() => {});
    await action();
    await navigated;
    await frame.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
  }
  return {admin, frame, navigate};
}

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const code = 'PW' + marker.slice(-14);
  const probeCode = code + 'G';
  const description = `${marker} issue: O'Neil & co`;
  const firstNote = `${marker} plain note before issues`;
  const cppText = `${marker} CPP item carrying the new issue`;
  const codes = `(${h.sqlString(code)},${h.sqlString(probeCode)})`;
  const issueCount = value => sql.value(`SELECT COUNT(*) FROM issue WHERE code=${h.sqlString(value)}`);
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_issue_notes WHERE id IN (SELECT id FROM casemgmt_issue WHERE demographic_no=${patient});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${h.sqlString(String(patient))};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})`) === '0', 'Owned chart rows were not removed');
    sql.execute(`DELETE FROM issue WHERE code IN ${codes} AND description LIKE ${h.sqlString(marker + '%')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM issue WHERE code IN ${codes}`) === '0', 'Owned issue rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM issue WHERE code IN ${codes}`) === '0', 'An issue with the owned code already exists');

  const chart = await s.chart();
  const editor = chart.locator(NOTE_EDITOR).first();
  let role;
  await s.step('a plain note saves and binds the chart to a program the provider holds a role in', async () => {
    await editor.waitFor({state: 'visible'});
    await editor.click();
    await editor.fill(firstNote);
    const [response] = await Promise.all([
      chart.waitForResponse(r => isEntryPost(r, 'save'), {timeout: 30000}),
      chart.locator('#saveImg').first().click(),
    ]);
    h.assert(response.status() < 400, `Save returned HTTP ${response.status()}`);
    const noteQuery = `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND provider_no=${h.sqlString(provider)}
      AND LOCATE(BINARY ${h.sqlString(firstNote)}, note)>0`;
    await expectValue(sql, noteQuery, '1', 'The plain note was not stored');
    const program = sql.value(`SELECT program_no FROM casemgmt_note WHERE demographic_no=${patient}
      AND LOCATE(BINARY ${h.sqlString(firstNote)}, note)>0 ORDER BY note_id DESC LIMIT 1`);
    h.assert(/^[1-9]\d*$/.test(program), 'The saved note carries no case program, so the chart cannot search issues');
    // The chart searches only issues whose role the provider holds in that program.
    role = sql.value(`SELECT r.role_name FROM program_provider pp JOIN secRole r ON r.role_no=pp.role_id
      WHERE pp.provider_no=${h.sqlString(provider)} AND pp.program_id=${program} ORDER BY pp.id LIMIT 1`);
    if (!role) throw new h.SkipCheck('The test provider holds no role in the chart program, so no issue could be offered to it');
  });

  const {frame, navigate} = await openIssueEditor(s);
  const form = () => frame.locator('form[action$="/issueAdmin"]');
  let issueId;
  await s.step('Issue Editor adds a coded issue and lists it', async () => {
    await navigate(() => frame.getByRole('button', {name: 'Add Issue'}).click());
    await form().locator('input[name="issueAdmin.code"]').fill(code);
    await form().locator('input[name="issueAdmin.description"]').fill(description);
    await form().locator('select[name="issueAdmin.role"]').selectOption(role);
    await navigate(() => form().locator('input[type="submit"][value="Save"]').click());
    const row = frame.locator('table tr').filter({has: frame.locator('td', {hasText: code})});
    h.assert(await row.count() === 1, 'The saved issue is not listed exactly once');
    const cells = await row.first().locator('td').allInnerTexts();
    h.assert(cells.map(cell => cell.trim()).join('|') === [code, description, role].join('|'), 'The listed issue does not show its code, description and role');
    issueId = sql.value(`SELECT issue_id FROM issue WHERE code=${h.sqlString(code)} AND description=${h.sqlString(description)}
      AND role=${h.sqlString(role)}`);
    h.assert(/^[1-9]\d*$/.test(issueId), 'The issue row was not stored with its code, description and role');
  });

  await s.step('the chart issue search offers exactly the new issue', async () => {
    const box = chart.locator(CPP_BOX);
    await box.waitFor({state: 'visible'});
    await box.locator('a[title="Add Item"]').first().click();
    await chart.locator(DIALOG).waitFor({state: 'visible'});
    const search = chart.locator('#issueAutocompleteCPP');
    await search.click();
    await search.pressSequentially(code, {delay: 40});
    const suggestions = chart.locator('.ui-autocomplete:visible li');
    await suggestions.first().waitFor({state: 'visible', timeout: 20000});
    h.assert(await suggestions.count() === 1 && (await suggestions.first().innerText()).trim() === `${description} (${code})`,
      'The issue search did not offer exactly the new issue');
    await suggestions.first().click();
    h.assert(await chart.locator('#newIssueId').inputValue() === issueId, 'Selecting the issue did not record its id');
  });

  await s.step('the selected issue attaches to the CPP item and Sign & Save links it to the note', async () => {
    const attached = chart.locator(`${DIALOG} input[name="issue_id"][value="${issueId}"]`);
    h.assert(await attached.count() === 1 && await attached.isChecked(),
      'Selecting the issue did not attach it to the item being edited');
    await chart.locator('#noteEditTxt').fill(cppText);
    const [response] = await Promise.all([
      chart.waitForResponse(r => r.request().method() === 'POST' && /method=issueNoteSave/.test(r.url()), {timeout: 30000}),
      chart.locator(`${DIALOG} input[type="image"][title="Sign & Save"]`).click(),
    ]);
    h.assert(response.status() < 400, `Sign & Save returned HTTP ${response.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_issue ci JOIN casemgmt_issue_notes cin ON cin.id=ci.id
      JOIN casemgmt_note n ON n.note_id=cin.note_id WHERE ci.demographic_no=${patient} AND ci.issue_id=${issueId}
      AND n.demographic_no=${patient} AND LOCATE(BINARY ${h.sqlString(cppText)}, n.note)=1`,
    '1', 'The saved item is not linked to the new issue through casemgmt_issue and casemgmt_issue_notes');
  });

  await s.step('a second issue with the same code is refused and leaves one row', async () => {
    await navigate(() => frame.getByRole('button', {name: 'Add Issue'}).click());
    await form().locator('input[name="issueAdmin.code"]').fill(code);
    await form().locator('input[name="issueAdmin.description"]').fill(`${description} duplicate`);
    await navigate(() => form().locator('input[type="submit"][value="Save"]').click());
    h.assert(await form().locator('input[name="issueAdmin.code"]').count() === 1, 'The refused save did not return to the form');
    h.assert(issueCount(code) === '1', 'The duplicate code was stored');
  });

  await s.step('a GET against the issue save creates nothing', async () => {
    const url = new URL(h.appUrl(s.config.baseUrl, '/issueAdmin'));
    url.search = new URLSearchParams({method: 'save', 'issueAdmin.code': probeCode,
      'issueAdmin.description': `${description} via GET`, 'issueAdmin.role': role}).toString();
    const response = await s.context.request.get(url.href);
    h.assert(response.status() === 405, `Issue saves must reject GET; got HTTP ${response.status()}`);
    h.assert(issueCount(probeCode) === '0', 'A GET request created an issue');
  });

  await s.step('the refused duplicate shows "Issue code already exists." on the form', async () => {
    h.assert(await frame.locator('td.error', {hasText: 'Issue code already exists.'}).count() === 1,
      'The duplicate-code refusal is silent: the form shows no error message');
  });
}
if (require.main === module) runWorkflow('admin-issue-editor', workflow, {openPatient: true});
module.exports = {workflow, openIssueEditor};
