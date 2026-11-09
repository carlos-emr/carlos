#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * CAISI Issue Editor: the add form's Save and Cancel carry a CSRF token (#6078).
 *
 * User path: Schedule ▸ Administration ▸ CAISI ▸ "Issue Editor" (issueAdmin?method=list in the
 * #dynamic-content iframe) ▸ Add Issue ▸ code + description + role ▸ Save; then Add Issue ▸ Cancel.
 *
 * Why its own check: the add form (caisicore/issueAdminForm.jsp) was rendered without </head> or
 * </body>, so CsrfGuardScriptInjectionFilter treated it as a fragment and never added the CSRFGuard
 * client. The form then posted no CSRF-TOKEN and CSRFGuard refused every Save with 403. The broader
 * admin-issue-editor check covers the same Save, but stops at a later, unrelated chart step; this
 * one isolates the editor itself so a token regression is reported on its own.
 *
 * Asserted: the add form loads the CSRFGuard client and holds a filled CSRF-TOKEN field; Save posts
 * that token, is answered 200 (not 403), stores exactly one `issue` row with the code, description
 * and role entered, and lists it; Cancel also posts the token, returns to the list and stores
 * nothing; a direct POST of a save that carries no token is still refused with 403 and stores
 * nothing (the fix restores the token, it does not weaken CSRF enforcement).
 *
 * Skips where Administration offers no Issue Editor link (the packaged default, caisi=off).
 *
 * Fixtures and cleanup: none created by hand. The run owns up to three marker-coded issue codes
 * (PS<hex>, PS<hex>C, PS<hex>X); cleanup deletes the rows carrying those codes and the marker in
 * their description, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {openIssueEditor} = require('./admin-issue-editor-playwright-checks');

const TOKEN = 'CSRF-TOKEN';

function isIssueAdminPost(response) {
  return response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/issueAdmin');
}

async function workflow(s) {
  const {sql, marker} = s;
  const q = h.sqlString;
  const code = 'PS' + marker.slice(-14);
  const cancelCode = code + 'C';
  const forgedCode = code + 'X';
  const description = `${marker} issue saved with a token`;
  const codes = `(${[code, cancelCode, forgedCode].map(q).join(',')})`;
  const issueCount = value => sql.value(`SELECT COUNT(*) FROM issue WHERE code=${q(value)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM issue WHERE code IN ${codes} AND description LIKE ${q(marker + '%')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM issue WHERE code IN ${codes}`) === '0', 'Owned issue rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM issue WHERE code IN ${codes}`) === '0', 'An issue with an owned code already exists');
  const role = sql.value('SELECT role_name FROM secRole ORDER BY role_no LIMIT 1');
  if (!role) throw new h.SkipCheck('secRole holds no role, so the Issue Editor has no role to offer');

  const {frame, navigate} = await openIssueEditor(s);
  const form = () => frame.locator('form[action$="/issueAdmin"]');

  async function openAddForm() {
    await navigate(() => frame.getByRole('button', {name: 'Add Issue'}).click());
    await form().waitFor();
    // The client fills every POST form on DOMContentLoaded; wait for it rather than racing it.
    await frame.waitForFunction(name => {
      const field = document.querySelector(`form[action$="/issueAdmin"] input[name="${name}"]`);
      return Boolean(field && field.value);
    }, TOKEN, {timeout: 20000}).catch(() => {});
  }

  async function submit(button) {
    let response;
    await navigate(async () => {
      [response] = await Promise.all([
        frame.page().waitForResponse(isIssueAdminPost, {timeout: 30000}),
        button.click(),
      ]);
    });
    const posted = new URLSearchParams(response.request().postData() || '');
    return {response, posted};
  }

  await s.step('the add form loads the CSRFGuard client and holds a filled CSRF token', async () => {
    await openAddForm();
    h.assert(await frame.locator('script[src$="/csrfguard"]').count() >= 1,
      'The Issue Editor form page did not load the CSRFGuard client (it was served as a fragment)');
    const field = form().locator(`input[name="${TOKEN}"]`);
    h.assert(await field.count() === 1, `The Issue Editor form holds ${await field.count()} ${TOKEN} fields, not one`);
    h.assert((await field.inputValue()).length > 0, `The Issue Editor form's ${TOKEN} field is empty`);
  });

  await s.step('Save posts the token, is accepted, and stores and lists the issue', async () => {
    await form().locator('input[name="issueAdmin.code"]').fill(code);
    await form().locator('input[name="issueAdmin.description"]').fill(description);
    await form().locator('select[name="issueAdmin.role"]').selectOption(role);
    const {response, posted} = await submit(form().locator('input[type="submit"][value="Save"]'));
    h.assert(posted.get('method') === 'save', `Save posted method=${posted.get('method')}`);
    h.assert((posted.get(TOKEN) || '').length > 0, `Save posted no ${TOKEN}`);
    h.assert(response.status() === 200, `Save was answered HTTP ${response.status()} (403 means CSRFGuard refused it)`);
    h.assert(sql.value(`SELECT COUNT(*) FROM issue WHERE code=${q(code)} AND description=${q(description)}
      AND role=${q(role)}`) === '1', 'Save did not store exactly one issue row with the code, description and role entered');
    const row = frame.locator('table tr').filter({has: frame.locator('td', {hasText: code})});
    h.assert(await row.count() === 1, 'The saved issue is not listed exactly once');
    const cells = (await row.first().locator('td').allInnerTexts()).map(cell => cell.trim());
    h.assert(cells.join('|') === [code, description, role].join('|'), `The listed issue shows ${cells.join(' | ')}`);
  });

  await s.step('Cancel posts the token, returns to the list and stores nothing', async () => {
    await openAddForm();
    await form().locator('input[name="issueAdmin.code"]').fill(cancelCode);
    await form().locator('input[name="issueAdmin.description"]').fill(`${marker} cancelled`);
    const {response, posted} = await submit(form().locator('input[type="submit"][value="Cancel"]'));
    h.assert(posted.get('method') === 'cancel', `Cancel posted method=${posted.get('method')}`);
    h.assert((posted.get(TOKEN) || '').length > 0, `Cancel posted no ${TOKEN}`);
    h.assert(response.status() === 200, `Cancel was answered HTTP ${response.status()}`);
    await frame.getByRole('button', {name: 'Add Issue'}).waitFor();
    h.assert(issueCount(cancelCode) === '0', 'Cancel stored an issue');
  });

  await s.step('a save posted without a CSRF token is still refused and stores nothing', async () => {
    const response = await s.context.request.post(h.appUrl(s.config.baseUrl, '/issueAdmin'), {
      form: {method: 'save', 'issueAdmin.code': forgedCode, 'issueAdmin.description': `${marker} without a token`,
        'issueAdmin.role': role},
      maxRedirects: 0,
    });
    h.assert(response.status() === 403, `A token-less save was answered HTTP ${response.status()}, not 403`);
    h.assert(issueCount(forgedCode) === '0', 'A token-less save stored an issue');
  });
}

if (require.main === module) runWorkflow('admin-issue-editor-save', workflow, {openPatient: false});
module.exports = {workflow};
