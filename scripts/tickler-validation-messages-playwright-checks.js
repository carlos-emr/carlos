#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for issue #3957: tickler add/edit validation
 * messages must REPLACE the previous attempt's messages, not pile up.
 *
 * ticklerAdd.jsp and ticklerEdit.jsp used to append every validation message
 * to the #error alert with insertAdjacentText("beforeend") and never cleared
 * it, so each failed Save added the same message again and a message stayed
 * on screen after the operator had fixed the field. The fix (ported from
 * openo-beta/Open-O PR #2410 by Liam Stanziani) empties the alert at the start
 * of every validation pass and renders each current message on its own line.
 *
 * The check is entered the way a clinician gets there: Schedule > Search >
 * Master Record > Tickler (the patient's tickler list) > New Tickler. It then
 * proves, on the add popup and again on the edit popup:
 *   1. a Save with the service date cleared shows exactly ONE message line,
 *      and that line is the bundle's "missing service date" text;
 *   2. a second Save with the field still empty shows exactly ONE line -- the
 *      regression: the old code showed two;
 *   3. restoring the date and saving succeeds, the alert is hidden and empty
 *      (no stale message), and the row that reached MariaDB carries the date.
 *
 * It owns everything it writes: the synthetic patient comes from
 * lib/workflow-session.js, and the one tickler it saves is deleted on exit,
 * together with the edit comment the edit save records.
 *
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md section 3.4
 * (Tickler); see the "Checks implementing this plan" table there.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:tickler-validation-messages-playwright
 *
 * Environment (the common contract in lib/playwright-harness.js readConfig()):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE, EXPECT_FRONT_DOOR.
 */

const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

/** Bundle key each message line is compared against, and its English value. */
const MISSING_DATE_KEY = 'tickler.ticklerAdd.msgMissingDate';
const MISSING_DATE_FALLBACK = 'Missing service date, please verify!';

/** Selectors shared by ticklerAdd.jsp and ticklerEdit.jsp. */
const SELECTORS = {
  alert: '#error',
  // One element per message: the fix renders each line separately so the
  // count of lines IS the count of messages, with no string splitting.
  messageLine: '#error .tickler-validation-message',
  date: 'input[name="xml_appointment_date"]',
  addSave: 'input.btn-primary[name="Button"]',
  editSave: 'input[name="updateTickler"]',
  addSavedMarker: 'ticklerSubmitFrame',
  editSavedMarker: 'ticklerEditFrame',
};

/**
 * The English text of a bundle key, read from the source tree the check runs
 * in, so a reworded message moves the assertion with it. Falls back to the
 * known value when the script runs from a copy without src/ (a packaged VM).
 */
function bundleMessage(key, fallback) {
  const bundle = path.join(__dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties');
  try {
    const line = fs.readFileSync(bundle, 'utf8').split('\n').find((candidate) => candidate.startsWith(`${key}=`));
    return line ? line.slice(key.length + 1).trim() : fallback;
  } catch (error) {
    return fallback;
  }
}

/**
 * Exactly `expectedTexts` message lines, in order, and the alert visible.
 * Assertions run against the rendered DOM, never the alert's raw innerHTML.
 */
async function assertMessages(page, expectedTexts, label) {
  const alert = page.locator(SELECTORS.alert);
  await alert.waitFor({ state: 'visible', timeout: 10000 });
  // Behaviour first, shape second. The alert's whole text is what the operator
  // reads, so count the copies of each message in it: the #3957 defect is a
  // second copy after a second submit, and this is the assertion that names it.
  // On the pre-fix pages the count is right after ONE submit and wrong after two.
  const whole = (await alert.innerText()).replace(/\s+/g, ' ').trim();
  for (const expected of new Set(expectedTexts)) {
    const wanted = expectedTexts.filter((text) => text === expected).length;
    const copies = whole.split(expected).length - 1;
    h.assert(copies === wanted,
      `${label}: expected ${wanted} copy of the validation message in the alert, found ${copies}`
      + (copies > wanted ? ' (messages piled up from an earlier submit)' : ''));
  }
  h.assert(whole === expectedTexts.join(' ').replace(/\s+/g, ' ').trim(),
    `${label}: the alert carries text other than the expected message(s)`);
  // Then the shape the fix renders: one element per message, so the line count
  // is the message count without string splitting, and text never becomes markup.
  const texts = (await page.locator(SELECTORS.messageLine).allTextContents()).map((text) => text.trim());
  h.assert(texts.length === expectedTexts.length,
    `${label}: the message text is present but rendered as ${texts.length} message line(s), not ${expectedTexts.length}`
    + (texts.length === 0 ? ' (bare text in the alert is the pre-fix append path)' : ''));
  expectedTexts.forEach((expected, index) => {
    h.assert(texts[index] === expected, `${label}: message line ${index + 1} did not match the bundle text`);
  });
}

/** The alert is hidden and holds no message -- the state after a fixed field. */
async function assertNoMessages(page, label) {
  const alert = page.locator(SELECTORS.alert);
  h.assert(!(await alert.isVisible()), `${label}: the validation alert is still visible after a valid save`);
  h.assert((await page.locator(SELECTORS.messageLine).count()) === 0,
    `${label}: stale validation message lines survived a valid save`);
  h.assert((await alert.textContent()).trim() === '', `${label}: the validation alert still carries text`);
}

/** The hidden submit iframe rendered the server's success sentinel. */
async function waitForSaveSentinel(page, frameId, sentinelId) {
  await page.waitForFunction(({ frame, sentinel }) => {
    const iframe = document.getElementById(frame);
    return Boolean(iframe && iframe.contentDocument && iframe.contentDocument.getElementById(sentinel));
  }, { frame: frameId, sentinel: sentinelId }, { timeout: 30000 });
}

/**
 * Drive one popup (add or edit) through the three-step contract:
 * empty date -> one message; save again -> still one; restore -> saved, clean.
 */
async function exerciseValidation(popup, { saveSelector, frameId, sentinelId, restoreDate, label, missingDate }) {
  const date = popup.locator(SELECTORS.date);
  await date.waitFor({ state: 'visible', timeout: 20000 });
  // Nothing shown before the first submit: the alert starts hidden.
  h.assert(!(await popup.locator(SELECTORS.alert).isVisible()), `${label}: the alert was visible before any submit`);

  await date.fill('');
  await popup.locator(saveSelector).first().click();
  await assertMessages(popup, [missingDate], `${label} first failed save`);

  // The regression itself: the same failure again must not add a second copy.
  await popup.locator(saveSelector).first().click();
  await assertMessages(popup, [missingDate], `${label} second failed save`);

  // Fixing the field and saving must clear the message, not leave it behind.
  await date.fill(restoreDate);
  await popup.locator(saveSelector).first().click();
  await waitForSaveSentinel(popup, frameId, sentinelId);
  await assertNoMessages(popup, `${label} valid save`);
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const missingDate = bundleMessage(MISSING_DATE_KEY, MISSING_DATE_FALLBACK);
  const ticklerMessage = `${marker} tickler validation check`;
  const addDate = '2026-03-04';
  const editDate = '2026-03-05';
  let ticklerNo;

  s.cleanup(() => {
    // Own rows only: the message carries this run's marker, and the row must
    // still belong to the owned patient before anything is deleted.
    const rows = sql.rows(`SELECT tickler_no FROM tickler WHERE demographic_no=${patient}
      AND message=${h.sqlString(ticklerMessage)}`);
    for (const [id] of rows) {
      sql.execute(`DELETE FROM tickler_comments WHERE tickler_no=${Number(id)}`);
      sql.execute(`DELETE FROM tickler_update WHERE tickler_no=${Number(id)}`);
    }
    sql.execute(`DELETE FROM tickler WHERE demographic_no=${patient} AND message=${h.sqlString(ticklerMessage)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient}`) === '0',
      'Tickler fixture cleanup left rows behind');
  });

  // Master Record > Tickler opens the patient's tickler list as a popup.
  const ticklerList = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(),
    'tickler-list');
  await ticklerList.waitForLoadState('domcontentloaded', { timeout: 20000 });
  await h.assertNotErrorPage(ticklerList, 'the patient tickler list');

  await s.step('add popup: repeated failed saves show one message line, a valid save clears it', async () => {
    // Tickler list > New Tickler opens ticklerAdd.jsp with this patient attached.
    const addPopup = await s.popup(ticklerList,
      ticklerList.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
    await addPopup.waitForLoadState('domcontentloaded', { timeout: 20000 });
    await h.assertNotErrorPage(addPopup, 'the add tickler popup');
    await addPopup.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await addPopup.locator('form[name="serviceform"] input[name="demographic_no"]').last().inputValue() === patient,
      'the add popup did not open for the owned patient');
    await addPopup.locator('textarea[name="ticklerMessage"]').fill(ticklerMessage);
    await addPopup.locator('select[name="task_assigned_to"]').first().selectOption(provider);
    await exerciseValidation(addPopup, {
      saveSelector: SELECTORS.addSave,
      frameId: SELECTORS.addSavedMarker,
      sentinelId: 'tickler-save-ok',
      restoreDate: addDate,
      label: 'add popup',
      missingDate,
    });
    // The row the valid save wrote is the one the edit half works on.
    await expectValue(sql, `SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient}
      AND message=${h.sqlString(ticklerMessage)}`, '1', 'the valid add save did not reach the database');
    ticklerNo = sql.value(`SELECT tickler_no FROM tickler WHERE demographic_no=${patient}
      AND message=${h.sqlString(ticklerMessage)}`);
    h.assert(/^[1-9]\d*$/.test(ticklerNo), 'the saved tickler has no id');
    h.assert(sql.value(`SELECT DATE(service_date) FROM tickler WHERE tickler_no=${ticklerNo}`) === addDate,
      'the saved tickler did not carry the restored service date');
    if (!addPopup.isClosed()) await addPopup.close();
  });

  await s.step('edit popup: repeated failed saves show one message line, a valid save clears it', async () => {
    // The list refreshes itself on the add popup's broadcast; reload so the row
    // is on the page regardless of DataTables timing, then click its pencil.
    await ticklerList.reload({ waitUntil: 'domcontentloaded', timeout: 20000 });
    const row = ticklerList.locator('#ticklerResults tbody tr').filter({ hasText: ticklerMessage }).first();
    await row.waitFor({ state: 'visible', timeout: 20000 });
    const editPopup = await s.popup(ticklerList, row.locator('a[onclick*="openTicklerEdit"]'), 'tickler-edit');
    await editPopup.waitForLoadState('domcontentloaded', { timeout: 20000 });
    await h.assertNotErrorPage(editPopup, 'the edit tickler popup');
    await editPopup.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(new URL(editPopup.url()).searchParams.get('tickler_no') === ticklerNo,
      'the edit popup opened a tickler other than the one this check saved');
    await exerciseValidation(editPopup, {
      saveSelector: SELECTORS.editSave,
      frameId: SELECTORS.editSavedMarker,
      sentinelId: 'tickler-edit-ok',
      restoreDate: editDate,
      label: 'edit popup',
      missingDate,
    });
    await expectValue(sql, `SELECT DATE(service_date) FROM tickler WHERE tickler_no=${ticklerNo}`, editDate,
      'the valid edit save did not reach the database');
    if (!editPopup.isClosed()) await editPopup.close();
  });
}

if (require.main === module) runWorkflow('tickler-validation-messages', workflow);
module.exports = {
  MISSING_DATE_KEY, SELECTORS, assertMessages, assertNoMessages, bundleMessage, exerciseValidation, workflow,
};
