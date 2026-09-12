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
 * either never opened or opened empty. Both halves are pinned here, because
 * they are two different failures with the same symptom:
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
 *
 * Reaching the modal the way a prescriber does matters as much as asserting on
 * it. The asterisk only exists on a STAGED prescription, so this check stages
 * one from the patient's own drug profile (tick ReRx, "Stage medication") and
 * then clicks the rendered anchor. A check that navigated straight to
 * rx/ViewDisplayMedHistory would exercise neither the action nor the callback
 * that decides whether the window is shown, and a check that staged a fresh
 * CUSTOM drug would assert against a drug with no history by construction —
 * the empty state would pass while real lookups stayed broken.
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

    // The whole defect is "the window is empty", so measure the rendered text,
    // not the presence of the frame.
    const modalText = (await modalFrame(rxPage).locator('body').innerText()).replace(/\s+/g, ' ').trim();
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
    const chosen = (await instructionRows.first().innerText()).trim();
    await instructionRows.first().click();
    const instructionsInput = rxPage.locator(`#instructions_${randomId}`);
    await instructionsInput.waitFor({ state: 'visible', timeout: 10000 });
    const applied = (await instructionsInput.inputValue()).trim();
    assert(
      applied.length > 0,
      `Clicking "${chosen}" in the modal left the Instructions field empty; the modal renders but `
        + 'cannot hand its selection back to the prescription.',
    );

    // --- 2. the hardened unresolvable path ---------------------------------
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
    await rxPage.waitForFunction(
      // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed predicate over the page's own DOM, no interpolation
      () => {
        const frame = document.getElementById('xmaskframe');
        const body = frame && frame.contentDocument && frame.contentDocument.body;
        return !!body && body.innerText.trim().length > 0;
      },
      null,
      { timeout: 20000 },
    ).catch(() => {});
    const strayText = (await modalFrame(rxPage).locator('body').innerText()).replace(/\s+/g, ' ').trim();
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
      + `selection applied to the prescription, unresolvable id answered HTTP ${strayStatus} `
      + 'with the explicit empty state',
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
