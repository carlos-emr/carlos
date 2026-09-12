#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the Rx previous-instructions ("*") modal — the
 * red asterisk beside the Instructions field on a staged prescription, which
 * opens a small "<drug> Rx Examples" window listing the instructions that drug
 * was last written with so the prescriber can click one instead of retyping it.
 *
 * The reported defect (#955) was that the asterisk did "nada": the window
 * either never opened or opened empty. All three shapes are pinned here,
 * because they are different failures wearing the same symptom:
 *
 *   1. The POST behind the asterisk (WriteScript?parameterValue=
 *      listPreviousInstructions) must answer 2xx. The modal is opened from the
 *      request's onSuccess callback ONLY (SearchDrug3.jsp), so any 4xx/5xx
 *      means no window appears at all and the click looks inert. This is what
 *      an unresolvable stash item used to do: getPreviousInstructions(null)
 *      threw an NPE straight out of the action.
 *   2. The iframe the modal loads (rx/ViewDisplayMedHistory) must RENDER
 *      something. displayMedHistory.jsp wraps its whole body in a
 *      try/catch that logs and swallows, so a null prescription used to
 *      produce a 200 with an empty body — a blank white box, indistinguishable
 *      to the user from nothing happening.
 *   3. A drug with no prescribing history must SAY so. The table rendered its
 *      header and then stopped, leaving two column titles over nothing, which
 *      reads as a broken control rather than an empty result.
 *
 * Reaching the modal the way a prescriber does matters as much as asserting on
 * it. The asterisk only exists on a STAGED prescription, so this check stages
 * one from the patient's own drug profile (tick ReRx, "Stage medication") and
 * then clicks the rendered anchor. A check that navigated straight to
 * rx/ViewDisplayMedHistory would exercise neither the action nor the callback
 * that decides whether the window is shown. The custom drug this check also
 * stages is for case 3 ONLY, and must not be the drug case 1 asserts against:
 * a freshly-invented name has no history by construction, so an empty result
 * would satisfy the main path while real lookups stayed broken.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN
 * Optional: RX_MED_HISTORY_DEMOGRAPHIC_NO (default PRESCRIPTION_DEMOGRAPHIC_NO,
 *   then 1), CHROME_PATH, RX_MED_HISTORY_SCREENSHOT_DIR (default /tmp),
 *   RESET_PASSWORD (forced-reset completion; see the runbook).
 */

const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  resetPassword: process.env.RESET_PASSWORD || '',
  screenshotDir: process.env.RX_MED_HISTORY_SCREENSHOT_DIR || '/tmp',
};
const demographicNo = process.env.RX_MED_HISTORY_DEMOGRAPHIC_NO
  || process.env.PRESCRIPTION_DEMOGRAPHIC_NO
  || '1';
assert(/^\d+$/.test(demographicNo), `RX_MED_HISTORY_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);

const LIST_PREVIOUS_INSTRUCTIONS = 'parameterValue=listPreviousInstructions';

/** The modal iframe's document, once the window has been opened. */
function modalFrame(page) {
  return page.frameLocator('#xmaskframe');
}

/**
 * The text a prescriber can actually READ in the modal opened for `randomId`.
 *
 * Two traps, both of which make a blank modal look fine:
 *
 *   - Not `body.innerText`: the response-rewriting filters inject their own
 *     <script> into every HTML response, and its source comes back in the body
 *     text, so a window that rendered nothing still measures as several hundred
 *     non-empty characters. Strip script and style before measuring.
 *   - The page REUSES one #xmaskframe for every modal. After the first one has
 *     rendered, a read taken before the next navigation commits returns the
 *     PREVIOUS drug's document — so a later assertion could pass, or fail, on
 *     text that has nothing to do with what it just asked for. Every read is
 *     therefore pinned to the document whose URL carries the requested id;
 *     anything else reads as empty, which is what the assertions report.
 */
async function modalVisibleText(page, randomId) {
  const text = await page.evaluate((id) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- the id is passed as an argument, never interpolated into the page script
    const frame = document.getElementById('xmaskframe');
    const doc = frame && frame.contentDocument;
    if (!doc || !(doc.URL || '').includes(`randomId=${id}`) || !doc.body) return '';
    const clone = doc.body.cloneNode(true);
    clone.querySelectorAll('script, style').forEach((node) => node.remove());
    return clone.textContent || '';
  }, String(randomId));
  return text.replace(/\s+/g, ' ').trim();
}

/**
 * Wait for the modal to finish loading the document for `randomId` and put
 * something readable in it.
 *
 * Tolerant on purpose: a modal that stays blank, or never navigates, is a
 * finding for the assertion that follows to report in full, not a bare timeout
 * here.
 */
async function waitForModalDocument(page, randomId) {
  await page.waitForFunction(
    // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- the id is passed as an argument, never interpolated into the page script
    (id) => {
      const frame = document.getElementById('xmaskframe');
      const doc = frame && frame.contentDocument;
      if (!doc || doc.readyState !== 'complete') return false;
      if (!(doc.URL || '').includes(`randomId=${id}`) || !doc.body) return false;
      const clone = doc.body.cloneNode(true);
      clone.querySelectorAll('script, style').forEach((node) => node.remove());
      return (clone.textContent || '').trim().length > 0;
    },
    String(randomId),
    { timeout: 20000 },
  ).catch(() => {});
}

/**
 * Stage one of the patient's EXISTING prescriptions, the operator way: tick its
 * ReRx box in the drug profile and confirm with "Stage medication".
 *
 * Returns the staged prescription's randomId, which is what every id on the
 * staged pane — and the asterisk's onclick — is keyed by.
 */
async function stageExistingPrescription(page) {
  const checkbox = page.locator("[id^='reRxCheckBox_']").first();
  assert(
    await checkbox.count(),
    `Patient ${demographicNo} lists no re-prescribable drug, so no prescription can be staged. `
      + 'The demo dataset seeds this patient with prescriptions; check the drug profile rendered '
      + '(docs/ui-tests/deb-install-validation.md §4).',
  );
  await checkbox.check();

  const stageButton = page.locator('#reRxConfirmBox input[name="stage"]');
  await stageButton.waitFor({ state: 'visible', timeout: 20000 });
  await stageButton.click();
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const instructions = page.locator("[id^='instructions_']").first();
  await instructions.waitFor({ state: 'visible', timeout: 30000 });
  const instructionsId = await instructions.getAttribute('id');
  const randomId = (instructionsId || '').split('_')[1];
  assert(/^\d+$/.test(randomId), `Staged prescription exposed no usable randomId (id="${instructionsId}")`);
  return randomId;
}

/** The randomIds of everything currently staged on the prescription pane. */
async function stagedRandomIds(page) {
  return page.evaluate(() => Array.from(document.querySelectorAll("[id^='instructions_']")) // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed function over the page's own DOM, nothing interpolated
    .map((el) => el.id.split('_')[1])
    .filter((id) => /^\d+$/.test(id)));
}

/**
 * Stage a CUSTOM drug under a name nothing has ever been prescribed as, so its
 * history lookup is legitimately empty — the one remaining shape of "the modal
 * shows nothing", where the prescription resolves fine but has no instructions
 * to offer.
 *
 * Driven through the CustomDrug button rather than the endpoint behind it, so
 * the check keeps measuring the path a prescriber takes. That button confirms
 * first, and wirePage's recorder DISMISSES every dialog — which would silently
 * cancel the staging and leave this section asserting against the previous
 * prescription. Swap the handler for the duration.
 */
async function stageUnprescribedCustomDrug(page, recorder, drugName) {
  const before = new Set(await stagedRandomIds(page));

  page.removeAllListeners('dialog');
  page.on('dialog', async (dialog) => {
    recorder.dialogs.push({ label: 'rx-med-history', type: dialog.type(), text: dialog.message() });
    await dialog.accept().catch(() => {});
  });

  await page.locator('#searchString').fill(drugName);
  await page.locator('#customDrug').click();
  await page.waitForFunction(
    // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- the count is passed as an argument, never interpolated into the page script
    (previousCount) => document.querySelectorAll("[id^='instructions_']").length > previousCount,
    before.size,
    { timeout: 30000 },
  );

  const added = (await stagedRandomIds(page)).filter((id) => !before.has(id));
  assert(added.length === 1, `Staging a custom drug added ${added.length} prescriptions, expected 1`);
  return added[0];
}

/**
 * Click the asterisk for a staged prescription and wait for BOTH halves of the
 * interaction: the action POST and the iframe navigation it triggers.
 */
async function openPreviousInstructions(page, randomId) {
  const star = page.locator(`a[onclick*="displayMedHistory('${randomId}')"]`).first();
  await star.waitFor({ state: 'visible', timeout: 20000 });

  const [postResponse] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes(LIST_PREVIOUS_INSTRUCTIONS) && r.request().method() === 'POST',
      { timeout: 30000 },
    ),
    star.click(),
  ]);
  return postResponse;
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    if (config.baseUrl.protocol !== 'https:') {
      console.log(
        '[warn] BASE_URL is not HTTPS, so this run does NOT go through nginx and the WAF; the '
        + 'modal POST is not being measured against the front door an operator uses.',
      );
    }

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const landing = await login(context, config, recorder);
    await landing.close();

    const rxPage = await context.newPage();
    wirePage(rxPage, 'rx-med-history', recorder);
    await gotoApp(rxPage, config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
    await rxPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(rxPage, 'rx module');

    const randomId = await stageExistingPrescription(rxPage);

    // --- 1. the real lookup -------------------------------------------------
    const postResponse = await openPreviousInstructions(rxPage, randomId);
    const postStatus = postResponse.status();
    assert(
      postStatus < 400,
      `The previous-instructions POST returned HTTP ${postStatus}. SearchDrug3.jsp opens the modal `
        + 'from onSuccess only, so on this status the asterisk does nothing at all — the original '
        + `"nada" report. Check catalina.out for a stack trace out of listPreviousInstructions.`,
    );

    const iframe = rxPage.locator('#xmaskframe');
    await iframe.waitFor({ state: 'visible', timeout: 20000 });
    await modalFrame(rxPage).locator('body').waitFor({ state: 'attached', timeout: 20000 });
    await waitForModalDocument(rxPage, randomId);

    // The whole defect is "the window is empty", so measure the rendered text,
    // not the presence of the frame.
    const modalText = await modalVisibleText(rxPage, randomId);
    assert(
      modalText.length > 0,
      'The previous-instructions modal opened with a completely empty body. displayMedHistory.jsp '
        + 'swallows its own exceptions, so an empty body means the page threw while rendering '
        + '(check catalina.out) — this is the blank box users reported.',
    );
    assert(
      /Rx\s*Examples/i.test(modalText),
      `The modal rendered "${modalText.slice(0, 200)}" instead of the "<drug> Rx Examples" table. `
        + 'A staged prescription that IS in the session stash must render its history table, not '
        + 'the "Medication history is unavailable" fallback — that fallback appearing here means '
        + 'the stash lookup failed for a prescription the page itself just staged.',
    );

    // A table with a header and no rows is still "nothing" to a prescriber: the
    // whole point of the control is to offer instructions to click.
    const instructionRows = modalFrame(rxPage).locator("a[id^='mhInst_'], a[id^='mhSpecInst_']");
    const rowCount = await instructionRows.count();
    assert(
      rowCount > 0,
      'The modal rendered its header but listed no previous instruction to choose. The staged drug '
        + 'was re-prescribed from this patient\'s own record, so at least its own prior '
        + 'instruction must come back from RxUtil.getPreviousInstructions '
        + '(DrugDaoImpl.findByParameter binds the value — an unbound query used to fail outright '
        + 'on any drug name containing an apostrophe).',
    );

    await screenshot(rxPage, config.screenshotDir, 'rx-med-history-modal');

    // Choosing an entry must write it back into the prescription, which is the
    // only reason the window exists.
    //
    // Two things this has to get right, and neither is obvious:
    //
    //   - The two column links go to DIFFERENT fields. mhInst_* calls
    //     addInstruction (-> #instructions_<id>) and mhSpecInst_* calls
    //     addSpecialInstruction (-> #siInput_<id>), so clicking whichever link
    //     happens to be first and then asserting on the Instructions field
    //     fails on a history entry that only carries a special instruction,
    //     even though the handoff worked. Pick the link that matches the field
    //     being asserted.
    //   - A ReRx-staged prescription arrives with its existing text ALREADY in
    //     the field (prescribe.jsp seeds it from rx.getSpecial()), so "the
    //     field is non-empty afterwards" is true before the click and measures
    //     nothing. Clear it first.
    //
    // The assertion is "non-empty after clearing" rather than an equality
    // against the link text on purpose: addInstruction runs the value through
    // parseIntr, which rewrites it (it lifts quantity and repeats out of the
    // sig), so an exact match would fail on a working handoff.
    const plainRows = modalFrame(rxPage).locator("a[id^='mhInst_']");
    const specialRows = modalFrame(rxPage).locator("a[id^='mhSpecInst_']");
    const usePlainRow = (await plainRows.count()) > 0;
    const chosenRow = usePlainRow ? plainRows.first() : specialRows.first();
    const targetSelector = usePlainRow ? `#instructions_${randomId}` : `#siInput_${randomId}`;
    const targetField = usePlainRow ? 'Instructions' : 'Special Instructions';

    const chosen = (await chosenRow.innerText()).trim();
    const appliedInput = rxPage.locator(targetSelector);
    await appliedInput.waitFor({ state: 'attached', timeout: 10000 });
    await appliedInput.fill('');
    assert(
      (await appliedInput.inputValue()).trim() === '',
      `Could not clear the ${targetField} field before testing the modal's handoff, so the `
        + 'assertion that follows would not measure the click.',
    );

    await chosenRow.click();
    await rxPage.waitForFunction(
      // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- the selector is passed as an argument, never interpolated into the page script
      (selector) => {
        const el = document.querySelector(selector);
        return !!el && el.value.trim().length > 0;
      },
      targetSelector,
      { timeout: 10000 },
    ).catch(() => {});
    const applied = (await appliedInput.inputValue()).trim();
    assert(
      applied.length > 0,
      `Clicking "${chosen}" in the modal left the ${targetField} field empty (it was cleared `
        + 'first, so this is the click being measured, not leftover text). The modal renders but '
        + 'cannot hand its selection back to the prescription.',
    );

    // --- 2. a prescription with no history ---------------------------------
    // The lookup succeeds and the prescription resolves; there is simply
    // nothing to offer. The table used to render its header and then stop, so
    // the prescriber got a box with two column titles and no content — the
    // same "nothing happened" as a blank window.
    const customDrugName = `ZZ Check Drug ${Date.now()}`;
    const customRandomId = await stageUnprescribedCustomDrug(rxPage, recorder, customDrugName);
    const customResponse = await openPreviousInstructions(rxPage, customRandomId);
    assert(
      customResponse.status() < 400,
      `The previous-instructions POST for a history-less drug returned HTTP ${customResponse.status()}.`,
    );

    await modalFrame(rxPage).locator('body').waitFor({ state: 'attached', timeout: 20000 });
    await waitForModalDocument(rxPage, customRandomId);
    const customText = await modalVisibleText(rxPage, customRandomId);
    assert(
      /Rx\s*Examples/i.test(customText),
      `A history-less drug rendered "${customText.slice(0, 200)}" instead of its Rx Examples table. `
        + 'The prescription is in the stash, so the table — not the unavailable fallback — belongs here.',
    );
    assert(
      /No previous instructions recorded/i.test(customText),
      `A drug with no prescribing history rendered "${customText.slice(0, 200)}" — a header with no `
        + 'row under it and no explanation. It must say so in words, or the prescriber cannot tell '
        + 'an empty history from a broken control.',
    );
    assert(
      (await modalFrame(rxPage).locator("a[id^='mhInst_'], a[id^='mhSpecInst_']").count()) === 0,
      'A drug with no prescribing history offered instructions to click; the empty-state row is '
        + 'being rendered alongside real content, which means the row counter is wrong.',
    );

    await screenshot(rxPage, config.screenshotDir, 'rx-med-history-no-history');

    // --- 3. the hardened unresolvable path ---------------------------------
    // A randomId the session stash does not know (a stale modal, a second tab
    // whose prescription was removed, a hand-edited request) used to throw an
    // NPE out of the action — 500, no modal, "nada". It must now answer 2xx and
    // the iframe must SAY the history is unavailable rather than render blank.
    const strayId = String(Number(randomId) + 7654321);
    const [strayResponse] = await Promise.all([
      rxPage.waitForResponse(
        (r) => r.url().includes(LIST_PREVIOUS_INSTRUCTIONS) && r.request().method() === 'POST',
        { timeout: 30000 },
      ),
      // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- the id is passed as an argument, never interpolated into the page script, and is derived from a numeric id this script generated
      rxPage.evaluate((id) => window.displayMedHistory(id), strayId),
    ]);
    const strayStatus = strayResponse.status();
    assert(
      strayStatus < 400,
      `An unresolvable randomId returned HTTP ${strayStatus} from listPreviousInstructions. It must `
        + 'answer 2xx with an empty history: the modal is opened from onSuccess, so an error status '
        + 'leaves the user clicking an asterisk that never responds.',
    );

    await modalFrame(rxPage).locator('body').waitFor({ state: 'attached', timeout: 20000 });
    await waitForModalDocument(rxPage, strayId);
    const strayText = await modalVisibleText(rxPage, strayId);
    assert(
      /Medication history is unavailable/i.test(strayText),
      `An unresolvable randomId rendered "${strayText.slice(0, 200)}" instead of the explicit `
        + '"Medication history is unavailable" empty state. A blank body here is the exact defect '
        + 'this check exists for: the user sees a white box and cannot tell it from a dead button.',
    );

    await screenshot(rxPage, config.screenshotDir, 'rx-med-history-unavailable');
    await rxPage.close();

    assertNoPageErrors(recorder);
    await context.close();

    console.log(
      `PASS rx med history: staged prescription ${randomId} for demographic ${demographicNo}, `
      + `asterisk answered HTTP ${postStatus} and listed ${rowCount} previous instruction(s), `
      + 'selection applied to the prescription; a drug with no history said so in words; '
      + `unresolvable id answered HTTP ${strayStatus} with the explicit empty state`,
    );
  } catch (error) {
    console.error('FAIL rx med history Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
