#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #3355: "Cannot add Allergies -- CSRF token never bootstrapped into AJAX-injected add-allergy
 * form (403)", driven the way it was reported, plus the acceptance list the issue thread attached to
 * it and the failed-save recovery of #3488.
 *
 * User path: Rx module (/rx/choosePatient) > "+" beside Active Allergies > a quick-add button or a
 * search result > the reaction form loads into the page over AJAX > Add Allergy.
 *
 *   1. NKDA, the reported reproduction: the form is injected twice and still carries exactly one
 *      CSRF-TOKEN field holding this session's current master token; it is submitted from the
 *      keyboard and saves exactly one row for the owned patient, with today's entry date and an
 *      ADD audit row naming the test provider.
 *   2. Sulfa, a coded allergen: saves with its drugref id and type code and archives the NKDA row
 *      after the page's own "Remove NKDA" confirmation.
 *   3. A DrugRef search result: saves the picked allergen.
 *   4. The dialogue request refused by CSRFGuard (its token header stripped, a real server 403):
 *      the page says so in its role="alert" region instead of spinning, the dialogue container
 *      survives, and the next click loads the form.
 *   5. The save refused by CSRFGuard (the token stripped from the body, a real server 403): the
 *      dialogue says "NOT saved", keeps every entered value, writes nothing, and the retry saves
 *      exactly one row (#3488).
 *   6. A double click on Add Allergy records the allergy once.
 *   7. With a second owned patient open in another tab, patient A's form pointed at patient B is
 *      refused and writes nothing to either; the untouched form still writes to A only.
 *   8. Direct posts without a token or with a forged one are refused (403) and write nothing.
 *   9. A form left open after the session ends (a server-side logout) fails closed: the dialogue
 *      reports it, keeps the entries, and nothing is written.
 *
 * Fixtures: two owned FAKE-PW patients (the workflow's own, and a second inserted here); cleanup
 * deletes every allergies row of both and both patients. The last step ends the test session. Local disposable database only.
 *
 * Environment: BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH, MYSQL_HOST/USER/PASSWORD/
 * DATABASE (docs/ui-tests/deb-install-validation.md section 6). Optional: ALLERGY_SEARCH_TERM
 * (default amoxicillin), the DrugRef term step 3 searches for.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

const NAME = 'allergy-injected-form-csrf';
const NKDA = 'No Known Drug Allergies';
const searchTerm = process.env.ALLERGY_SEARCH_TERM || 'amoxicillin';
h.assert(/^[A-Za-z][A-Za-z -]{2,40}$/.test(searchTerm), 'ALLERGY_SEARCH_TERM must be a plain allergen name');

const isPath = (route) => (response) => new URL(response.url()).pathname.endsWith(route);
// rx-patient-context.js adds ?demographicNo= to the page's AJAX URLs, so match routes by path.
const routePath = (route) => (url) => url.pathname.endsWith(route);
const isPost = (route) => (response) => response.request().method() === 'POST' && isPath(route)(response);

async function openAllergyPageFromRx(s, page, patient) {
  await h.gotoApp(page, s.config.baseUrl, `/rx/choosePatient?demographicNo=${patient}`);
  const plus = page.locator('a[name="cmdAllergies"]');
  await plus.waitFor({ state: 'visible' });
  await Promise.all([page.waitForURL(/\/rx\/showAllergy\?/), plus.click()]);
  await page.locator('#searchString').waitFor({ state: 'visible' });
  await h.assertNotErrorPage(page, 'allergy page');
}

/** Clicks a control that loads the reaction form over AJAX and waits for that form. */
async function loadReactionForm(page, click) {
  const [answer] = await Promise.all([page.waitForResponse(isPost('/rx/addReaction2')), click()]);
  h.assert(answer.status() === 200, `addReaction2 answered HTTP ${answer.status()}`);
  const form = page.locator('#RxAddAllergyForm');
  await form.waitFor({ state: 'visible' });
  return form;
}

/** Submits the reaction form and waits for the confirmed save to reload the allergy list. */
async function saveAndReturnToList(page, submit) {
  const [answer] = await Promise.all([
    page.waitForResponse(isPost('/rx/addAllergy2')),
    page.waitForEvent('framenavigated', { predicate: frame => frame === page.mainFrame() }),
    submit(),
  ]);
  h.assert([302, 303].includes(answer.status()), `addAllergy2 answered HTTP ${answer.status()} instead of the success redirect`);
  await page.locator('#searchString').waitFor({ state: 'visible' });
  await h.assertNotErrorPage(page, 'allergy page after a save');
}

async function masterToken(page) {
  return page.evaluate(async () => {
    const script = await (await fetch(new URL('csrfguard', document.baseURI.replace(/\/rx\/.*$/, '/')), { credentials: 'same-origin' })).text();
    const match = script.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
    return match ? match[1] : '';
  });
}

async function workflow(s) {
  const { sql, patient, provider, marker, context, recorder } = s;
  const rows = (where = '') => `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}${where}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient}`);
    h.assert(sql.value(rows()) === '0', 'Owned allergies were not removed');
  });

  const page = await context.newPage();
  h.relabelStrictPage(page, 'allergies-A');
  await openAllergyPageFromRx(s, page, patient);

  await s.step('NKDA injected twice carries one current token and saves once from the keyboard (#3355)', async () => {
    await loadReactionForm(page, () => page.locator('input[value="NKDA"]').click());
    const form = await loadReactionForm(page, () => page.locator('input[value="NKDA"]').click());
    h.assert(await page.locator('#RxAddAllergyForm').count() === 1, 'Re-injecting the dialogue left two allergy forms');
    const tokens = form.locator('input[name="CSRF-TOKEN"]');
    h.assert(await tokens.count() === 1, `The injected form carries ${await tokens.count()} CSRF-TOKEN fields, not one`);
    const token = await tokens.inputValue();
    h.assert(token.length >= 16 && token === await masterToken(page),
      'The injected form does not carry this session\'s current CSRF token');
    h.assert(await form.locator('[name="formDemographicNo"]').inputValue() === patient
      && await form.locator('[name="demographicNo"]').inputValue() === patient, 'The injected form is not bound to the patient');
    await form.locator('#reactionDescription').fill(`${marker} nkda`);
    const submit = form.locator('input[type="submit"][value="Add Allergy"]');
    await submit.focus();
    await saveAndReturnToList(page, () => page.keyboard.press('Enter'));
    await expectValue(sql, rows(` AND DESCRIPTION=${h.sqlString(NKDA)} AND archived=0`), '1', 'NKDA was not saved once');
    h.assert(sql.value(rows()) === '1', 'The NKDA save wrote more than one row');
    const id = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT CONCAT(entry_date=CURDATE(),'|',reaction) FROM allergies WHERE allergyid=${id}`) === `1|${marker} nkda`,
      'The NKDA row lost its entry date or reaction');
    // LogAction writes the audit row on a background thread, so poll for it.
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE action='add' AND content='allergy' AND contentId=${h.sqlString(id)}
      AND provider_no=${h.sqlString(provider)} AND demographic_no=${h.sqlString(patient)}`, '1',
    'The NKDA save left no ADD audit row naming the provider and patient');
    await page.locator(`#allergy_${id}`).waitFor({ state: 'visible' });
  });

  await s.step('Sulfa saves the coded allergen and archives NKDA after confirmation', async () => {
    const nkda = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient} AND DESCRIPTION=${h.sqlString(NKDA)}`);
    const form = await loadReactionForm(page, () => page.locator('input[value="Sulfa"]').click());
    h.assert(await form.locator('#allergyToArchive').inputValue() === nkda, 'The Sulfa form does not offer to archive NKDA');
    await form.locator('#reactionDescription').fill(`${marker} sulfa`);
    await form.locator('select[name="severityOfReaction"]').selectOption('2');
    const dialogs = await h.withExpectedDialogs(page, () => saveAndReturnToList(page,
      () => form.locator('input[type="submit"][value="Add Allergy"]').click()));
    h.assert(dialogs.length === 1 && dialogs[0].text === `Remove "${NKDA}" from list?`, 'The NKDA removal was not confirmed');
    await expectValue(sql, rows(` AND DESCRIPTION='SULFONAMIDES' AND TYPECODE=10 AND drugref_id='44159' AND severity_of_reaction='2' AND archived=0`),
      '1', 'Sulfonamides was not saved with its drugref identity');
    h.assert(sql.value(`SELECT archived FROM allergies WHERE allergyid=${nkda}`) === '1', 'NKDA stayed active beside a drug allergy');
  });

  await s.step(`a DrugRef search result (${searchTerm}) saves the picked allergen`, async () => {
    await page.locator('#searchString').fill(searchTerm);
    const [answer] = await Promise.all([page.waitForResponse(isPost('/rx/searchAllergy2')), page.locator('#searchStringButton').click()]);
    h.assert(answer.status() === 200, `searchAllergy2 answered HTTP ${answer.status()}`);
    const result = page.locator('#searchResultsContainer a[data-id]').first();
    await result.waitFor({ state: 'visible' });
    const picked = await result.getAttribute('data-desc');
    const form = await loadReactionForm(page, () => result.click());
    h.assert(await form.locator('input[name="name"]').inputValue() === picked, 'The form is not for the picked result');
    await form.locator('#reactionDescription').fill(`${marker} search`);
    await saveAndReturnToList(page, () => form.locator('input[type="submit"][value="Add Allergy"]').click());
    await expectValue(sql, rows(` AND DESCRIPTION=${h.sqlString(picked)} AND reaction=${h.sqlString(`${marker} search`)} AND archived=0`),
      '1', 'The picked search result was not saved');
  });

  await s.step('a dialogue request refused by CSRFGuard is announced and the container survives', async () => {
    const stripHeaderToken = route => {
      const headers = { ...route.request().headers() };
      delete headers['csrf-token'];
      return route.continue({ headers });
    };
    await page.route(routePath('/rx/addReaction2'), stripHeaderToken, { times: 1 });
    const mark = failureMark(recorder);
    const [refused] = await Promise.all([page.waitForResponse(isPost('/rx/addReaction2')), page.locator('input[value="Penicillin"]').click()]);
    h.assert(refused.status() === 403, `A tokenless dialogue request answered HTTP ${refused.status()}, not 403`);
    const alert = page.locator('#allergyRequestStatus[role="alert"]');
    await alert.waitFor({ state: 'visible' });
    h.assert(/could not be opened: the server refused the request \(HTTP 403\).*Nothing was saved\./.test(await alert.innerText()),
      'The refused dialogue request was not explained');
    h.assert(await page.locator('.ajax-loader').count() === 0, 'The spinner kept turning after the refusal');
    h.assert(await page.locator('#addAllergyDialogue').count() === 1, 'The dialogue container was lost');
    consumeExpectedFailure(recorder, mark, { status: 403, path: /\/rx\/addReaction2$/ });
    await loadReactionForm(page, () => page.locator('input[value="Penicillin"]').click());
    h.assert(!await alert.isVisible(), 'The earlier failure stayed on screen after the form loaded');
  });

  await s.step('a save refused by CSRFGuard keeps the entries and the retry saves exactly once (#3488)', async () => {
    const form = page.locator('#RxAddAllergyForm');
    const reaction = `${marker} penicillin`;
    await form.locator('#reactionDescription').fill(reaction);
    await form.locator('input[name="ageOfOnset"]').fill('42');
    await form.locator('select[name="severityOfReaction"]').selectOption('3');
    await form.locator('select[name="lifeStage"]').selectOption('A');
    const stripBodyToken = route => {
      const body = new URLSearchParams(route.request().postData() || '');
      h.assert(body.get('CSRF-TOKEN'), 'The save carried no CSRF token to strip');
      body.delete('CSRF-TOKEN');
      return route.continue({ postData: body.toString() });
    };
    await page.route(routePath('/rx/addAllergy2'), stripBodyToken, { times: 1 });
    const mark = failureMark(recorder);
    const [refused] = await Promise.all([page.waitForResponse(isPost('/rx/addAllergy2')),
      form.locator('input[type="submit"][value="Add Allergy"]').click()]);
    h.assert(refused.status() === 403, `A tokenless save answered HTTP ${refused.status()}, not 403`);
    const status = form.locator('.allergySaveStatus[role="alert"]');
    await status.waitFor({ state: 'visible' });
    h.assert(/^Allergy NOT saved: the server refused the request \(HTTP 403\)/.test(await status.innerText()),
      'The refused save was not reported as not saved');
    h.assert(await form.locator('#reactionDescription').inputValue() === reaction
      && await form.locator('input[name="ageOfOnset"]').inputValue() === '42'
      && await form.locator('select[name="severityOfReaction"]').inputValue() === '3'
      && await form.locator('select[name="lifeStage"]').inputValue() === 'A', 'The refused save lost the entered values');
    h.assert(await form.locator('input[type="submit"][value="Add Allergy"]').isEnabled(), 'Add Allergy stayed disabled after the refusal');
    h.assert(sql.value(rows(` AND DESCRIPTION='PENICILLINS'`)) === '0', 'The refused save wrote a row');
    consumeExpectedFailure(recorder, mark, { status: 403, path: /\/rx\/addAllergy2$/ });
    await saveAndReturnToList(page, () => form.locator('input[type="submit"][value="Add Allergy"]').click());
    await expectValue(sql, rows(` AND DESCRIPTION='PENICILLINS' AND reaction=${h.sqlString(reaction)} AND age_of_onset='42' AND archived=0`),
      '1', 'The retry did not save the preserved entries');
    h.assert(sql.value(rows(` AND DESCRIPTION='PENICILLINS'`)) === '1', 'The retry recorded the allergy twice');
  });

  await s.step('a double click on Add Allergy records the allergy once', async () => {
    const name = `DBL ${marker.slice(-8)}`.toUpperCase();
    await page.locator('#searchString').fill(name);
    const form = await h.withExpectedDialogs(page, () => loadReactionForm(page, () => page.locator('input[value="Custom Allergy"]').click()))
      .then(() => page.locator('#RxAddAllergyForm'));
    await form.locator('#reactionDescription').fill(`${marker} double`);
    await form.locator('[name="nonDrug"]').selectOption('on');
    await saveAndReturnToList(page, () => form.locator('input[type="submit"][value="Add Allergy"]').dblclick());
    await expectValue(sql, rows(` AND DESCRIPTION=${h.sqlString(name)}`), '1', 'The custom allergy was not saved');
    await new Promise(resolve => setTimeout(resolve, 1500));
    h.assert(sql.value(rows(` AND DESCRIPTION=${h.sqlString(name)}`)) === '1', 'A double click recorded the allergy twice');
  });

  let other;
  await s.step('with a second patient open, patient A\'s form cannot write to patient B', async () => {
    other = sql.value(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(marker)},'WorkflowB','1981','02','03','M','AC',${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(other) && other !== patient, 'The second patient fixture was not created');
    s.cleanup(() => {
      sql.execute(`DELETE FROM allergies WHERE demographic_no=${other};
        DELETE FROM demographic WHERE demographic_no=${other} AND last_name=${h.sqlString(marker)} AND first_name='WorkflowB'`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${other}`) === '0', 'The second patient was not removed');
    });
    const tabB = await context.newPage();
    h.relabelStrictPage(tabB, 'allergies-B');
    await openAllergyPageFromRx(s, tabB, other);

    await page.bringToFront();
    await page.locator('#searchString').fill(`XPT ${marker.slice(-8)}`.toUpperCase());
    await h.withExpectedDialogs(page, () => loadReactionForm(page, () => page.locator('input[value="Custom Allergy"]').click()));
    const form = page.locator('#RxAddAllergyForm');
    await form.locator('#reactionDescription').fill(`${marker} cross`);
    await form.locator('[name="nonDrug"]').selectOption('off');
    await form.locator('[name="demographicNo"]').evaluate((input, value) => { input.value = value; }, other);
    const mark = failureMark(recorder);
    const [refused] = await Promise.all([page.waitForResponse(isPost('/rx/addAllergy2')),
      form.locator('input[type="submit"][value="Add Allergy"]').click()]);
    h.assert(refused.status() === 403, `A form for patient A aimed at patient B answered HTTP ${refused.status()}, not 403`);
    await form.locator('.allergySaveStatus').waitFor({ state: 'visible' });
    consumeExpectedFailure(recorder, mark, { status: 403, path: /\/rx\/addAllergy2$/ });
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${other}`) === '0', 'Patient A\'s form wrote to patient B');
    h.assert(sql.value(rows(` AND reaction=${h.sqlString(`${marker} cross`)}`)) === '0', 'The refused cross-patient save wrote to patient A');

    await form.locator('[name="demographicNo"]').evaluate((input, value) => { input.value = value; }, patient);
    await saveAndReturnToList(page, () => form.locator('input[type="submit"][value="Add Allergy"]').click());
    await expectValue(sql, rows(` AND reaction=${h.sqlString(`${marker} cross`)}`), '1', 'Patient A\'s own form did not save to A');
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${other}`) === '0', 'Patient B gained an allergy');
    await tabB.close();
  });

  await s.step('direct posts without a token or with a forged one are refused and write nothing', async () => {
    const fields = {
      demographicNo: patient, formDemographicNo: patient, ID: '0', type: '0', name: `FORGED ${marker.slice(-6)}`,
      reactionDescription: `${marker} forged`, startDate: '', ageOfOnset: '', severityOfReaction: '4',
      onSetOfReaction: '4', lifeStage: '', nonDrug: 'on', allergyToArchive: '',
    };
    for (const [label, token] of [['no token', null], ['a forged token', 'FORGED0123456789ABCDEFFORGED0123']]) {
      const form = token === null ? fields : { ...fields, 'CSRF-TOKEN': token };
      const answer = await context.request.post(h.appUrl(s.config.baseUrl, '/rx/addAllergy2'), { form, maxRedirects: 0 });
      h.assert(answer.status() === 403, `A direct post with ${label} answered HTTP ${answer.status()}, not 403`);
    }
    h.assert(sql.value(rows(` AND reaction=${h.sqlString(`${marker} forged`)}`)) === '0', 'A forged post wrote an allergy');
  });

  await s.step('a form left open after the session ends fails closed and writes nothing', async () => {
    // The session ends on the server while the form is open, the way a timeout or a logout from
    // elsewhere ends it. The heartbeat is held at "valid" for the rest of the run: this step is about
    // what the server does with the stale form, and LogoutBroadcastFilter's heartbeat or a logout
    // page's broadcast would otherwise replace the page before the form could be submitted.
    await context.route(routePath('/status/SessionHeartbeat'),
      route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"valid":true}' }));
    await page.locator('#searchString').fill(`OUT ${marker.slice(-8)}`.toUpperCase());
    await h.withExpectedDialogs(page, () => loadReactionForm(page, () => page.locator('input[value="Custom Allergy"]').click()));
    const form = page.locator('#RxAddAllergyForm');
    await form.locator('#reactionDescription').fill(`${marker} stale`);
    await form.locator('[name="nonDrug"]').selectOption('on');
    // Logout2Action is POST-only; logout.jsp posts to it the same way.
    const logout = await context.request.post(h.appUrl(s.config.baseUrl, '/logout'), { maxRedirects: 0 });
    h.assert([302, 303].includes(logout.status()), `Logging out answered HTTP ${logout.status()}`);
    const probe = await context.request.get(h.appUrl(s.config.baseUrl, `/rx/showAllergy?demographicNo=${patient}`), { maxRedirects: 0 });
    h.assert(probe.status() !== 200, 'The session survived the logout');
    const mark = failureMark(recorder);
    const [answer] = await Promise.all([page.waitForResponse(isPost('/rx/addAllergy2')),
      form.locator('input[type="submit"][value="Add Allergy"]').click()]);
    const status = form.locator('.allergySaveStatus[role="alert"]');
    await status.waitFor({ state: 'visible' });
    h.assert(/^(Allergy NOT saved|The allergy save could not be confirmed)/.test(await status.innerText()),
      'The stale form was not reported as unsaved');
    h.assert(await form.locator('#reactionDescription').inputValue() === `${marker} stale`, 'The stale form lost its entries');
    if (answer.status() >= 400) consumeExpectedFailure(recorder, mark, { status: answer.status(), path: /\/rx\/addAllergy2$/ });
    h.assert(sql.value(rows(` AND reaction=${h.sqlString(`${marker} stale`)}`)) === '0', 'A form submitted after the session ended wrote an allergy');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow };
