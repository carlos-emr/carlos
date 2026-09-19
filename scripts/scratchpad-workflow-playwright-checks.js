#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Owned, synthetic scratchpad versions; requires an isolated test login.
// Positive paths enter through the schedule icon and use the actual editor.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

function isSave(response) {
  return new URL(response.url()).pathname.endsWith('/Scratch')
    && response.request().method() === 'POST'
    && !new URLSearchParams(response.request().postData() || '').has('method');
}

// Consume only the specific HTTP failure deliberately asserted by a negative
// step. Other endpoints, statuses, JavaScript errors and dialogs stay strict.
function consumeExpectedFailure(recorder, url, status, since) {
  const errors = recorder.badResponses.slice(since.responses);
  h.assert(errors.length === 1 && errors[0].url === url && errors[0].status === status
    && errors[0].method === 'POST', 'Negative save probe did not produce exactly its expected HTTP failure');
  recorder.badResponses.splice(since.responses, 1);
  for (let i = recorder.consoleIssues.length - 1; i >= since.console; i--) {
    const entry = recorder.consoleIssues[i];
    if (entry.location.url === url && entry.text.startsWith('Failed to load resource:')
        && entry.text.includes(`status of ${status} (`)) recorder.consoleIssues.splice(i, 1);
  }
}

async function workflow(s) {
  const provider = h.sqlString(s.provider);
  const owned = new Map();
  const baseline = s.sql.rows(`SELECT id,SHA2(scratch_text,256),status FROM scratch_pad WHERE provider_no=${provider} ORDER BY id`);
  const baselineMax = baseline.length ? Math.max(...baseline.map(row => Number(row[0]))) : 0;
  s.cleanup(() => {
    // Recover marker-bearing rows even if an assertion failed before recording
    // the successful response. Empty clear versions require an acknowledged ID.
    for (const [id, text] of s.sql.rows(`SELECT id,scratch_text FROM scratch_pad
      WHERE provider_no=${provider} AND id>${baselineMax} AND scratch_text LIKE ${h.sqlString(s.marker + '%')}`)) owned.set(id, text);
    for (const [id, text] of owned) {
      h.assert(/^[1-9]\d*$/.test(id) && Number(id) > baselineMax, 'Scratch fixture ID is outside this run');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM scratch_pad WHERE id=${id} AND provider_no=${provider}
        AND BINARY scratch_text=BINARY ${h.sqlString(text)}`) === '1', 'Scratch fixture ownership or contents changed');
      s.sql.execute(`DELETE FROM scratch_pad WHERE id=${id} AND provider_no=${provider}`);
    }
    h.assert(JSON.stringify(s.sql.rows(`SELECT id,SHA2(scratch_text,256),status FROM scratch_pad WHERE provider_no=${provider} ORDER BY id`))
      === JSON.stringify(baseline), 'Scratch cleanup did not restore the original provider history');
  });
  const open = schedule => s.popup(schedule, schedule.getByTitle(/Scratch\s*Pad/i).first(), 'scratch-editor');
  let editor = await open(s.schedule);
  async function acknowledged(page, response, text) {
    h.assert(response.status() === 200, `Scratch save returned HTTP ${response.status()}`);
    const result = await response.json();
    h.assert(result.success === true && /^[1-9]\d*$/.test(String(result.id)) && result.text === text,
      'Scratchpad save did not acknowledge the exact literal text');
    owned.set(String(result.id), text);
    await expectValue(s.sql, `SELECT scratch_text FROM scratch_pad WHERE id=${Number(result.id)} AND provider_no=${provider}`,
      text, 'Scratchpad text was not persisted exactly');
    await page.locator('#lastSavedTimestamp').filter({ hasText: /^Last saved:/ }).waitFor();
    h.assert(await page.locator('#thetext').inputValue() === text, 'Saving changed the editor text');
    return String(result.id);
  }
  async function save(page, text, button = '#savebutton') {
    await page.locator('#thetext').fill(text);
    const [response] = await Promise.all([page.waitForResponse(isSave), page.locator(button).click()]);
    return acknowledged(page, response, text);
  }
  let id;
  let secondContext;
  let current;
  await s.step('save literal characters and reopen the exact note', async () => {
    const text = `${s.marker} A+B %20 &amp; <note>\n  indented line  `;
    id = await save(editor, text);
    await editor.close(); editor = await open(s.schedule);
    h.assert(await editor.locator('#thetext').inputValue() === text, 'Reopened scratchpad changed literal text');
  });
  await s.step('autosave a literal plus edit and deliberately clear the note', async () => {
    await save(editor, `${s.marker} alpha beta`);
    // Wait for the real 30-second interval. Installing a fake clock after the
    // UI opener loaded this page would leave the original interval uncontrolled.
    const text = `${s.marker} alpha+beta`;
    await editor.locator('#thetext').fill(text);
    const response = await editor.waitForResponse(isSave, {timeout: 35000});
    await acknowledged(editor, response, text);
    await save(editor, '');
    await editor.close(); editor = await open(s.schedule);
    h.assert(await editor.locator('#thetext').inputValue() === '', 'Deliberate clear was not saved');
    id = await save(editor, `${s.marker} history baseline`);
  });
  await s.step('opening and closing history preserves unsaved editor text', async () => {
    const text = `${s.marker} unsaved while viewing history`;
    await editor.locator('#thetext').fill(text);
    const popup = editor.waitForEvent('popup');
    await editor.locator('#scratchVersions').selectOption(id);
    const history = await popup;
    await history.locator('#scratchpad-version').filter({ hasText: `${s.marker} history baseline` }).waitFor();
    await history.close();
    h.assert(await editor.locator('#thetext').inputValue() === text, 'History window discarded unsaved text');
    await save(editor, text);
  });
  await s.step('a failed save preserves text and Retry saves it', async () => {
    const text = `${s.marker} retry after transport failure`;
    await editor.locator('#thetext').fill(text);
    const since = { responses: s.recorder.badResponses.length, console: s.recorder.consoleIssues.length };
    const handler = route => route.request().method() === 'POST'
      ? route.fulfill({ status: 503, contentType: 'application/json', body: '{"success":false}' }) : route.continue();
    await editor.route('**/Scratch', handler);
    let response;
    try {
      [response] = await Promise.all([editor.waitForResponse(isSave), editor.locator('#savebutton').click()]);
      h.assert(response.status() === 503, 'Failure injection was not exercised');
      await editor.locator('#saveError').filter({ hasText: 'Save failed' }).waitFor();
      h.assert(await editor.locator('#thetext').inputValue() === text, 'Failed save discarded unsaved text');
      consumeExpectedFailure(s.recorder, response.url(), 503, since);
    } finally { await editor.unroute('**/Scratch', handler); }
    await save(editor, text, '#retryScratch');
  });
  await s.step('Retry acknowledges a committed save whose response was lost', async () => {
    const text = `${s.marker} committed before response loss`;
    let committedId;
    const since = { responses: s.recorder.badResponses.length, console: s.recorder.consoleIssues.length };
    const handler = async route => {
      if (route.request().method() !== 'POST') return route.continue();
      const upstream = await route.fetch();
      h.assert(upstream.status() === 200, 'Lost-response probe did not commit successfully upstream');
      const result = await upstream.json();
      h.assert(result.success === true && /^[1-9]\d*$/.test(String(result.id)) && result.text === text,
        'Lost-response probe received an invalid upstream save');
      committedId = String(result.id);
      owned.set(committedId, text);
      await route.fulfill({status: 503, contentType: 'application/json', body: '{"success":false}'});
    };
    await editor.route('**/Scratch', handler);
    try {
      await editor.locator('#thetext').fill(text);
      const [response] = await Promise.all([editor.waitForResponse(isSave), editor.locator('#savebutton').click()]);
      h.assert(response.status() === 503 && committedId, 'Lost-response probe was not exercised');
      await editor.locator('#saveError').filter({hasText: 'Save failed'}).waitFor();
      consumeExpectedFailure(s.recorder, response.url(), 503, since);
    } finally { await editor.unroute('**/Scratch', handler); }
    const retryId = await save(editor, text, '#retryScratch');
    h.assert(retryId === committedId, 'Retry created duplicate history for an already committed save');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM scratch_pad WHERE provider_no=${provider} AND id>${Number(committedId)}`) === '0',
      'Retry inserted an unexpected later revision');
  });
  await s.step('a stale second browser cannot overwrite the newer note', async () => {
    secondContext = await h.newContext(s.context.browser(), s.config);
    secondContext.on('page', page => h.wireStrictPage(page, 'scratch-second-browser', s.recorder));
    const secondSchedule = await h.login(secondContext, s.config, s.recorder);
    const stale = await open(secondSchedule);
    const staleId = await stale.locator('#curr_id').inputValue();
    const serverText = `${s.marker} first browser wins`;
    const latestId = await save(editor, serverText);
    const localText = `${s.marker} second browser must keep this unsaved`;
    await stale.locator('#thetext').fill(localText);
    const since = { responses: s.recorder.badResponses.length, console: s.recorder.consoleIssues.length };
    const [response] = await Promise.all([stale.waitForResponse(isSave), stale.locator('#savebutton').click()]);
    h.assert(response.status() === 409, 'Stale editor did not receive a conflict');
    await stale.locator('#saveError').filter({ hasText: 'Another window changed' }).waitFor();
    h.assert(await stale.locator('#thetext').inputValue() === localText, 'Conflict discarded the local note');
    h.assert(await stale.locator('#curr_id').inputValue() === staleId, 'Conflict incorrectly advanced the stale revision');
    consumeExpectedFailure(s.recorder, response.url(), 409, since);
    // Observe a complete real autosave interval before asserting no write.
    await stale.waitForTimeout(31000);
    h.assert(s.sql.value(`SELECT id FROM scratch_pad WHERE provider_no=${provider} AND status=1 ORDER BY id DESC LIMIT 1`) === latestId,
      'Conflict retry/autosave created a new version');
    current = await s.popup(stale, stale.locator('#openCurrentScratch'), 'scratch-conflict-current');
    h.assert(await current.locator('#thetext').inputValue() === serverText, 'Conflict recovery opened the wrong current note');
    await save(current, `${serverText}\n${localText}`);
  });
  await s.step('simultaneous saves commit one version and visibly reject the other', async () => {
    await editor.reload();
    const revision = await editor.locator('#curr_id').inputValue();
    h.assert(await current.locator('#curr_id').inputValue() === revision, 'Concurrent editors did not start at the same revision');
    const texts = [`${s.marker} concurrent first`, `${s.marker} concurrent second`];
    const pages = [editor, current];
    for (let i = 0; i < pages.length; i++) await pages[i].locator('#thetext').fill(texts[i]);
    let release;
    let arrivals = 0;
    const gate = new Promise(resolve => { release = resolve; });
    const handler = async route => {
      if (route.request().method() === 'POST') {
        arrivals++;
        if (arrivals === 2) release();
        await gate;
      }
      await route.continue();
    };
    const since = { responses: s.recorder.badResponses.length, console: s.recorder.consoleIssues.length };
    for (const page of pages) await page.route('**/Scratch', handler);
    try {
      const [responses] = await Promise.all([
        Promise.all(pages.map(page => page.waitForResponse(isSave))),
        Promise.all(pages.map(page => page.locator('#savebutton').click())),
      ]);
      h.assert(arrivals === 2 && responses.map(response => response.status()).sort().join(',') === '200,409',
        'Simultaneous revision checks did not accept exactly one save and reject the other');
      const winner = responses.findIndex(response => response.status() === 200);
      const loser = 1 - winner;
      await acknowledged(pages[winner], responses[winner], texts[winner]);
      await pages[loser].locator('#saveError').filter({hasText: 'Another window changed'}).waitFor();
      h.assert(await pages[loser].locator('#thetext').inputValue() === texts[loser], 'Concurrent conflict discarded its draft');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM scratch_pad WHERE provider_no=${provider} AND id>${Number(revision)}`) === '1',
        'Simultaneous saves created more than one history version');
      consumeExpectedFailure(s.recorder, responses[loser].url(), 409, since);
    } finally {
      release();
      for (const page of pages) await page.unroute('**/Scratch', handler);
    }
    // Context teardown bypasses beforeunload; the stale note is an owned fixture.
    await secondContext.close();
  });
}
if (require.main === module) runWorkflow('scratchpad-workflow', workflow, { openPatient: false });
module.exports = { workflow, consumeExpectedFailure, isSave };
