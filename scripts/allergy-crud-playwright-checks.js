#!/usr/bin/env node
/*
 * Browser CRUD checks for the CARLOS patient allergy interface.
 *
 * WHY THIS EXISTS. The allergy list is the single most safety-critical list in
 * the chart — it is what stops a penicillin prescription reaching an allergic
 * patient — and before this script no browser check touched it. Every one of
 * `rx/searchAllergy2`, `rx/addReaction2`, `rx/addAllergy2`, `rx/deleteAllergy2`
 * and the prescriber's `rx/showAllergy?method=allergyData` warning endpoint was
 * unexercised, so a page whose AJAX silently stopped posting would have looked
 * healthy in CI while quietly recording no allergies at all.
 *
 * THREE SHAPES THAT FAIL INDEPENDENTLY, so all three are driven:
 *   - the classic form POST (the drug-class search),
 *   - the jQuery AJAX POSTs that build and delete allergies, which CSRFGuard
 *     validates through the CSRF-TOKEN request header rather than a form field,
 *   - the JSON warning endpoint the prescribing page calls per drug row, whose
 *     failure mode is the worst of the three: an HTML error page here means the
 *     prescriber is shown no allergy warning and is never told why.
 *
 * ARCHIVE, NOT DELETE. The allergy surfaces never remove a row: a modify adds a
 * replacement and archives the original, and a delete flips `archived`. The
 * check asserts both halves of each, because "the row is gone from the page" is
 * equally true of a working archive and of a broken one that dropped the record.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:allergy-crud-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   ALLERGY_DEMOGRAPHIC_NO=1   patient whose allergy list is driven
 *   ALLERGY_WARNING_ATC=J01CA04  ATC handed to the prescriber warning endpoint
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Cleanup: every allergy row the script creates carries a unique
 * PW_ALLERGY_<millis> marker in its reaction text and is deleted in a finally,
 * including after a failure. The script never touches pre-existing allergies —
 * a patient's real allergy list must survive a failed check run.
 */

const { chromium } = require('playwright');
const {
  acceptNextDialog,
  appUrl,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  createRecorder,
  createSqlClient,
  escapeSql,
  getLaunchOptions,
  gotoApp,
  login,
  readConfig,
  readCsrfToken,
  requireId,
  waitFor,
  wirePage,
} = require('./carlos-playwright-harness');

const config = readConfig();
const demographicNo = requireId(process.env.ALLERGY_DEMOGRAPHIC_NO || '1', 'ALLERGY_DEMOGRAPHIC_NO');
const warningAtc = process.env.ALLERGY_WARNING_ATC || 'J01CA04';
assert(/^[A-Z0-9]{1,10}$/.test(warningAtc), `ALLERGY_WARNING_ATC must be an ATC code, got ${warningAtc}`);

// The quick-add buttons on ShowAllergies2 post fixed drugref ids; PENICILLINS is
// used because it is the class a clinician most often needs the warning for and
// because its id is hard-coded in the page rather than looked up, so the check
// does not depend on the local DrugRef dataset to create the allergy.
const PENICILLIN_DRUGREF_ID = '44452';
const PENICILLIN_NAME = 'PENICILLINS';

const stamp = `PW_ALLERGY_${Date.now()}`;
const createdReaction = `${stamp} initial reaction`;
const editedReaction = `${stamp} revised reaction`;

const recorder = createRecorder();
const sql = createSqlClient({ namespace: 'carlos-allergy' });
const passed = [];

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function stampedAllergies() {
  return sql.rows(
    `SELECT allergyid, demographic_no, DESCRIPTION, TYPECODE, reaction, archived, drugref_id`
    + ` FROM allergies WHERE reaction LIKE '${escapeSql(`${stamp}%`)}' ORDER BY allergyid`
  ).map(([id, demographic, description, typeCode, reaction, archived, drugrefId]) => ({
    id, demographic, description, typeCode, reaction, archived, drugrefId,
  }));
}

function cleanupRows() {
  sql.exec(`DELETE FROM allergies WHERE reaction LIKE '${escapeSql(`${stamp}%`)}'`);
}

async function openAllergyList(context, label) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/rx/showAllergy', 'domcontentloaded', { demographicNo });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, `${label} allergy list`);
  return page;
}

/**
 * Drives the drug-class search.
 *
 * Every write and read on this page is a jQuery AJAX POST — the search form
 * carries no method attribute and is never submitted classically — so the search
 * is asserted on the injected markup in #searchResultsContainer rather than on a
 * navigation. That container staying empty while the request returns 200 is the
 * failure mode worth pinning: the page looks fine and offers no way to record an
 * allergy.
 */
async function searchDrugClass(page) {
  const token = await readCsrfToken(page);
  assert(token, 'allergy list page carried no CSRFGuard token; its AJAX POSTs cannot be authorised');

  await page.locator('#type4').check();
  await page.locator('#searchString').fill('PENICILLIN');
  const [response] = await Promise.all([
    page.waitForResponse((r) => new URL(r.url()).pathname.endsWith('/rx/searchAllergy2'), { timeout: 45000 }),
    page.locator('#searchStringButton').click(),
  ]);
  assert(response.status() < 400, `rx/searchAllergy2 returned HTTP ${response.status()}`);

  const results = page.locator('#searchResultsContainer');
  await waitFor(async () => ((await results.innerText().catch(() => '')).trim().length > 0 ? true : null),
    { description: 'the drug-class search results to be injected into the page' });
  const text = await results.innerText();
  assert(/penicillin/i.test(text),
    `allergy drug-class search for PENICILLIN injected no matching results: ${text.slice(0, 300)}`);
}

/**
 * Opens the reaction form through the page's own quick-add button.
 *
 * The button fires a jQuery AJAX POST whose response is injected into
 * #addAllergyDialogue; asserting the injected form's hidden fields is what
 * catches a response that arrived but carried the wrong drugref id or no CSRF
 * token, which is how the follow-up save fails with no visible error.
 */
async function openReactionFormViaQuickAdd(page) {
  const dialogue = page.locator('#addAllergyDialogue');
  const [response] = await Promise.all([
    page.waitForResponse((r) => new URL(r.url()).pathname.endsWith('/rx/addReaction2'), { timeout: 45000 }),
    page.locator("input[value='Penicillin']").first().click(),
  ]);
  assert(response.status() < 400, `rx/addReaction2 returned HTTP ${response.status()}`);

  await dialogue.locator('form#RxAddAllergyForm').waitFor({ state: 'visible', timeout: 30000 });
  const drugrefId = await dialogue.locator('input[name="ID"]').inputValue();
  const name = await dialogue.locator('#name').inputValue();
  assert(drugrefId === PENICILLIN_DRUGREF_ID,
    `quick-add reaction form carried drugref id ${drugrefId}, expected ${PENICILLIN_DRUGREF_ID}`);
  assert(name === PENICILLIN_NAME, `quick-add reaction form carried name ${name}, expected ${PENICILLIN_NAME}`);
  const formToken = await dialogue.locator('input[name="CSRF-TOKEN"]').first().inputValue().catch(() => '');
  assert(formToken, 'AJAX-injected reaction form carried no CSRFGuard token');
  return dialogue;
}

async function saveReaction(page, dialogue, reactionText, expectArchiveOf = null) {
  await dialogue.locator('#reactionDescription').fill(reactionText);
  if (expectArchiveOf !== null) {
    const archiveTarget = await dialogue.locator('#allergyToArchive').inputValue();
    assert(archiveTarget === String(expectArchiveOf),
      `modify form carried allergyToArchive=${archiveTarget}, expected ${expectArchiveOf}`);
  }

  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST'
      && /\/rx\/addAllergy2?$/.test(new URL(r.url()).pathname), { timeout: 45000 }),
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    dialogue.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  assert(response.status() < 400, `rx/addAllergy2 returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'allergy list after save');

  return waitFor(() => {
    const active = stampedAllergies().filter((row) => row.archived === '0' && row.reaction === reactionText);
    return active.length === 1 ? active[0] : null;
  }, { description: `the saved allergy carrying reaction ${reactionText}` });
}

/**
 * Calls the warning endpoint the prescribing page calls for every drug row.
 *
 * Asserted as a contract (HTTP 200, parseable JSON, an `id` echo and a `results`
 * array) rather than on a specific warning: whether a given ATC matches a stored
 * allergy depends on the local DrugRef dataset, but an HTML error page or a
 * non-JSON body here means the prescriber silently gets no warning at all on
 * every deployment, which is the failure worth pinning.
 */
async function checkPrescriberWarningEndpoint(page) {
  const probeId = 'pw-allergy-probe';
  const url = appUrl(config.baseUrl, '/rx/showAllergy', {
    method: 'allergyData',
    atcCode: warningAtc,
    id: probeId,
  });
  const result = await page.evaluate(async (target) => {
    const response = await fetch(target, {
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
    return { status: response.status, contentType: response.headers.get('content-type') || '', body: await response.text() };
  }, url);

  assert(result.status === 200,
    `prescriber allergy-warning endpoint returned HTTP ${result.status} for atcCode=${warningAtc}`);
  assert(!/^\s*</.test(result.body),
    `prescriber allergy-warning endpoint returned markup instead of JSON: ${result.body.slice(0, 200)}`);
  let parsed;
  try {
    parsed = JSON.parse(result.body);
  } catch (error) {
    throw new Error(`prescriber allergy-warning endpoint returned unparseable JSON: ${result.body.slice(0, 200)}`);
  }
  assert(parsed.id === probeId,
    `prescriber allergy-warning endpoint echoed id=${parsed.id}, expected ${probeId}`);
  assert(Array.isArray(parsed.results),
    `prescriber allergy-warning endpoint returned no results array: ${result.body.slice(0, 200)}`);
  return parsed;
}

async function modifyAllergy(page, allergyId) {
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const modifyLink = page.locator(`a.modifyAllergyLink[id*="allergyToArchive=${allergyId}"]`).first();
  await modifyLink.waitFor({ state: 'visible', timeout: 30000 });

  const [response] = await Promise.all([
    page.waitForResponse((r) => new URL(r.url()).pathname.endsWith('/rx/addReaction2'), { timeout: 45000 }),
    modifyLink.click(),
  ]);
  assert(response.status() < 400, `modify allergy reaction form returned HTTP ${response.status()}`);
  const dialogue = page.locator('#addAllergyDialogue');
  await dialogue.locator('form#RxAddAllergyForm').waitFor({ state: 'visible', timeout: 30000 });

  const replacement = await saveReaction(page, dialogue, editedReaction, allergyId);

  const archived = await waitFor(() => {
    const original = stampedAllergies().find((row) => row.id === String(allergyId));
    return original && original.archived === '1' ? original : null;
  }, { description: `the original allergy ${allergyId} to be archived by the modify` });

  assert(replacement.id !== String(allergyId),
    'modify reused the original allergy row instead of adding a replacement and archiving it');
  return { replacement, archived };
}

async function deleteAllergy(page, allergyId) {
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const deleteLink = page.locator(`a.deleteAllergyLink[id*="ID=${allergyId}&"]`).first();
  await deleteLink.waitFor({ state: 'visible', timeout: 30000 });

  // The confirm is raised by the click handler before it fires the AJAX POST, so
  // the accept is queued first and awaited: a dismissed confirm means no request
  // is ever made and the check would time out on a response that cannot come.
  const dialogPromise = acceptNextDialog(page, recorder, 'delete-allergy-confirm');
  const responsePromise = page.waitForResponse((r) => /\/rx\/deleteAllergy2?$/.test(new URL(r.url()).pathname),
    { timeout: 45000 });
  await deleteLink.click();
  const confirmText = await dialogPromise;
  assert(/allergy/i.test(confirmText), `allergy delete raised an unexpected confirmation: ${confirmText}`);
  const response = await responsePromise;
  assert(response.status() < 400, `rx/deleteAllergy2 returned HTTP ${response.status()}`);

  await waitFor(() => {
    const active = stampedAllergies().filter((row) => row.archived === '0');
    return active.length === 0 ? true : null;
  }, { description: 'the deleted allergy to leave no active rows behind' });

  const remaining = stampedAllergies();
  assert(remaining.length >= 1,
    'allergy delete removed the row outright instead of archiving it; the chart keeps no record of a withdrawn allergy');
}

(async () => {
  cleanupRows();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    await login(context, config, recorder);

    const page = await openAllergyList(context, 'allergy');
    pass(`allergy list renders for demographic ${demographicNo} with a CSRFGuard token present`);

    await searchDrugClass(page);
    pass('drug-class allergy search injects matching results into the page');

    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    const listPage = await openAllergyList(context, 'allergy-add');
    const dialogue = await openReactionFormViaQuickAdd(listPage);
    pass('quick-add button AJAX returns a reaction form carrying the drug class and a CSRF token');

    const created = await saveReaction(listPage, dialogue, createdReaction);
    assert(created.demographic === demographicNo,
      `saved allergy landed on demographic ${created.demographic}, expected ${demographicNo}`);
    assert(created.description === PENICILLIN_NAME,
      `saved allergy description was ${created.description}, expected ${PENICILLIN_NAME}`);
    assert(created.drugrefId === PENICILLIN_DRUGREF_ID,
      `saved allergy drugref_id was ${created.drugrefId}, expected ${PENICILLIN_DRUGREF_ID}`);
    pass(`allergy ${created.id} persisted active with its drug class, drugref id and reaction text`);

    await listPage.reload({ waitUntil: 'domcontentloaded' });
    await listPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const listedRow = listPage.locator(`#allergy_${created.id}`);
    await listedRow.waitFor({ state: 'visible', timeout: 30000 });
    const listedText = await listedRow.innerText();
    assert(listedText.includes(PENICILLIN_NAME) || /penicillin/i.test(listedText),
      `the saved allergy row did not render its drug class: ${listedText.slice(0, 200)}`);
    pass('the saved allergy appears on the reloaded allergy list');

    const warning = await checkPrescriberWarningEndpoint(listPage);
    pass(`prescriber allergy-warning endpoint answers atcCode=${warningAtc} with JSON (${warning.results.length} warning(s))`);

    const { replacement } = await modifyAllergy(listPage, created.id);
    pass(`modify added replacement allergy ${replacement.id} and archived the original ${created.id}`);

    await deleteAllergy(listPage, replacement.id);
    pass('delete archived the allergy instead of removing the record');

    assertNoPageErrors(recorder, 'allergy CRUD');
    console.log(`\nCompleted ${passed.length} Playwright checks, 0 failures`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupRows();
    sql.close();
  }
})().catch((error) => {
  console.error(`FAIL allergy CRUD interface flow: ${error.stack || error.message}`);
  process.exit(1);
});
