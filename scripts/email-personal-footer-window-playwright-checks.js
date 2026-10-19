#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The personal email footer, edited in the footer window from Preferences (issue #3981, Ben's demo of
 * 15 Oct 2026: "the modal should be sufficient, the separate tab seems redundant, just make sure the
 * modal button actually saves it").
 *
 * User path: Schedule ▸ Preferences (popup) ▸ Account ▸ Personal email footer (window, same page) ▸
 * Save footer; then a direct visit to /email/myEmailFooter, which stays as the fallback.
 *
 * Asserted:
 *  - The link opens the window on the Preferences page itself: no new tab. The window shows the saved
 *    footer, read as JSON, under "My personal footer", and its one button is Save footer.
 *  - Save footer saves to the server as one AJAX POST with the CSRF token. The window closes, the page
 *    says it was saved, the footer is stored, and the save is audited.
 *  - A footer changed in another window meanwhile is refused: the window stays open with the typed
 *    text, says why, and the newer footer is kept.
 *  - My Email Footer still opens on a direct visit, with the same one-button window.
 *
 * Fixtures: the test user's own footer rows (property email_footer) are snapshotted and restored
 * exactly (same ids and values); the run's audit rows are removed. FAKE text only.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 30000;

async function workflow(s) {
  const {sql, marker, provider, context, config} = s;
  const q = h.sqlString;
  const own = `name='email_footer' AND provider_no=${q(provider)}`;
  const rows = sql.rows(`SELECT id, value FROM property WHERE ${own} ORDER BY id`).map(([id, value]) => ({id, value}));
  const firstLog = sql.value('SELECT COALESCE(MAX(id), 0) FROM log');
  s.cleanup(() => {
    sql.execute(`DELETE FROM property WHERE ${own}`);
    for (const row of rows) {
      sql.execute(`INSERT INTO property (id, name, value, provider_no) VALUES (${row.id}, 'email_footer', ${q(row.value)}, ${q(provider)})`);
    }
    sql.execute(`DELETE FROM log WHERE id > ${firstLog} AND provider_no=${q(provider)} AND content='emailFooterOwn'`);
    const now = sql.rows(`SELECT id, value FROM property WHERE ${own} ORDER BY id`).map(([id, value]) => ({id, value}));
    h.assert(JSON.stringify(now) === JSON.stringify(rows), 'The personal footer was not restored');
  });
  const before = `${marker} footer before`;
  sql.execute(`DELETE FROM property WHERE ${own};
    INSERT INTO property (name, value, provider_no) VALUES ('email_footer', ${q(before)}, ${q(provider)})`);
  const savedFooter = () => sql.value(`SELECT value FROM property WHERE ${own} ORDER BY id LIMIT 1`);

  const preferences = await s.popup(s.schedule,
    s.schedule.locator('a[onclick*="/provider/ViewProviderPreference"]').first(), 'preferences');
  await preferences.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(preferences, 'Preferences');
  const link = preferences.locator('#myEmailFooterLink');
  const modal = preferences.locator('#footerEditorModal');
  const editor = preferences.locator('#footerEditorText');

  async function openWindow() {
    if (!await link.isVisible()) {
      await preferences.locator('button[data-bs-target="#secAccount"]').click();
      await link.waitFor({state: 'visible', timeout: TIMEOUT});
    }
    const loaded = preferences.waitForResponse(response => new URL(response.url()).pathname.endsWith('/email/myEmailFooter')
      && response.request().method() === 'GET', {timeout: TIMEOUT});
    await link.click();
    await modal.waitFor({state: 'visible', timeout: TIMEOUT});
    const response = await loaded;
    h.assert(response.status() === 200 && (response.headers()['content-type'] || '').startsWith('application/json'),
      `The window read the footer as ${response.status()} ${response.headers()['content-type']}`);
    await preferences.waitForFunction(() => !document.getElementById('footerEditorApply').disabled, null, {timeout: TIMEOUT});
  }
  async function saveAndAwait() {
    const posted = preferences.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/email/saveMyEmailFooter'), {timeout: TIMEOUT});
    const answered = preferences.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/email/saveMyEmailFooter'), {timeout: TIMEOUT});
    await preferences.locator('#footerEditorApply').click();
    return {request: await posted, response: await answered};
  }

  await s.step('Personal email footer opens the window on the Preferences page, on the saved footer, with one Save button', async () => {
    const tabs = context.pages().length;
    await openWindow();
    h.assert(context.pages().length === tabs, 'Personal email footer opened a new tab');
    h.assert((await preferences.locator('#footerEditorScope').textContent()).trim() === 'My personal footer', 'The window is not for the personal footer');
    h.assert((await preferences.locator('#footerEditorApply').textContent()).trim() === 'Save footer', 'The window\'s button is not Save footer');
    h.assert((await editor.innerText()).includes(before), 'The window does not show the saved footer');
  });

  const typed = `${marker} footer saved from Preferences`;
  await s.step('Save footer saves it to the server with the CSRF token, closes, says so, and is audited', async () => {
    await editor.fill(typed);
    const {request, response} = await saveAndAwait();
    const headers = request.headers();
    h.assert(headers['csrf-token'], 'The save carried no CSRF token');
    h.assert((headers['x-requested-with'] || '').includes('XMLHttpRequest'), 'The save was not sent as AJAX');
    h.assert(response.status() === 200, `The save answered ${response.status()}`);
    await modal.waitFor({state: 'hidden', timeout: TIMEOUT});
    const saved = preferences.locator('#myEmailFooterSaved');
    await saved.waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert((await saved.innerText()).includes('saved'), 'The page does not say the footer was saved');
    h.assert(savedFooter().includes(typed), 'The footer was not stored');
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE id > ${firstLog} AND provider_no=${q(provider)}
      AND content='emailFooterOwn'`, '1', 'The save was not audited');
  });

  await s.step('a footer changed in another window meanwhile is refused, and the window keeps the typed text', async () => {
    await openWindow();
    const newer = `${marker} footer saved elsewhere`;
    sql.execute(`UPDATE property SET value=${q(newer)} WHERE ${own}`);
    const stale = `${marker} footer typed over an older one`;
    await editor.fill(stale);
    const since = {responses: s.recorder.badResponses.length, console: s.recorder.consoleIssues.length};
    // The browser also logs the refused POST to the console, at times after the response event;
    // wait for that line (the recorder, listening since the page opened, has it by then).
    const logged = preferences.waitForEvent('console', {predicate: m => /status of 409 \(/.test(m.text()), timeout: 5000})
      .catch(() => null);
    const {response} = await saveAndAwait();
    h.assert(response.status() === 409, `A stale save answered ${response.status()}`);
    await logged;
    // Consume exactly the 409 this save drew (and the browser's console line for it); any other
    // signal stays strict.
    for (let i = s.recorder.badResponses.length - 1; i >= since.responses; i--) {
      const entry = s.recorder.badResponses[i];
      if (entry.status === 409 && /\/email\/saveMyEmailFooter$/.test(new URL(entry.url).pathname)) s.recorder.badResponses.splice(i, 1);
    }
    for (let i = s.recorder.consoleIssues.length - 1; i >= since.console; i--) {
      if (/status of 409 \(/.test(s.recorder.consoleIssues[i].text)) s.recorder.consoleIssues.splice(i, 1);
    }
    const message = preferences.locator('#footerEditorMessage');
    await message.waitFor({state: 'visible', timeout: TIMEOUT});
    h.assert((await message.innerText()).includes('another window'), 'The window does not say the footer changed elsewhere');
    h.assert(await modal.isVisible(), 'The window closed on a refused save');
    h.assert((await editor.innerText()).includes(stale), 'The typed text was lost');
    h.assert(savedFooter() === newer, 'The stale save overwrote the newer footer');
    await preferences.locator('#footerEditorModal button[data-bs-dismiss="modal"]').first().click();
    await modal.waitFor({state: 'hidden', timeout: TIMEOUT});
  });

  await s.step('My Email Footer still opens on a direct visit, with the same one-button window', async () => {
    const page = await context.newPage();
    try {
      await page.goto(h.appUrl(config.baseUrl, '/email/myEmailFooter'));
      await h.assertNotErrorPage(page, 'My Email Footer');
      h.assert(await page.locator('#myFooterForm button[name="footerAction"][value="save"]').count() === 0,
        'My Email Footer still has a separate Save button');
      h.assert(await page.locator('[data-footer-editor-save="myFooterForm"]').count() === 1, 'My Email Footer has no window that saves');
    } finally {
      await page.close();
    }
  });
}

if (require.main === module) runWorkflow('email-personal-footer-window', workflow, {openPatient: false});
module.exports = {workflow};
