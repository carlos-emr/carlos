#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5050 (BC installs only): BC-AR 2007 and BC-AR 2012 show the Edinburgh Postnatal Depression Scale in its
 * published wording: the original English (Cox, Holden & Sagovsky 1987) and, for a French-speaking clinician, the
 * validated French version (Guedeney & Fermanian 1998). The EPDS is a validated questionnaire, so the check compares
 * every question and every answer with the published text word for word, in the order the scale prints them, and
 * proves the page still scores it the published way.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Forms menu ▸ BC-AR 2007 / BC-AR 2012 ▸ the AR2 (pg.1)
 * link (page 2, where the EPDS is), once logged in with an English browser and once with a French one
 * (Accept-Language fr-CA; that login enters the same patient's chart through the E-Chart link's route). Step 4 saves one record
 * per form for the owned patient, and reopens it from the chart's saved forms.
 * Asserted, per language and form:
 *   1. the form opens from the Forms menu and its page 2 link opens page 2;
 *   2. each of the ten items shows the published question and its four answers in the published order;
 *   3. ticking one answer per item makes the page's EPDS score the published total (items 1, 2 and 4 score the answers
 *      0..3 from the first, the others 3..0);
 *   4. (English, after both languages) the ticked answers are saved and come back ticked when the chart reopens the
 *      record: BC-AR 2012 defined its EPDS restore but never called it (#5090), so a reopened record showed no answer
 *      and its next Save stored the answers as empty.
 * BC-AR 2012 printed its own English copy until #5050 (with item 6 damaged to "coping as card card-body
 * bg-body-tertiary as usual"), so it showed no French at all; scripts/bcar-epds-wording.test.js holds the bundles.
 *
 * Fixtures: the owned FAKE- patient and one marker-named Forms-menu registration per form (clinic-wide, so EXCLUSIVE=1),
 * removed and asserted in cleanup with the owned patient's BC-AR 2007 and 2012 records; the French login is the same
 * test login in a second browser context.
 *
 *   npm run test:form-bc-epds-wording-playwright
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { ENGLISH, FRENCH, FORWARD_SCORED } = require('./lib/epds-wording');

const NAME = 'form-bc-epds-wording';
const FORMS = [
  { code: 'AR07', title: 'BC-AR 2007', view: 'formbcar2007', page2: 'formbcar2007pg2', table: 'formBCAR2007' },
  { code: 'AR12', title: 'BC-AR 2012', view: 'formbcar2012', page2: 'formbcar2012pg2', table: 'formBCAR2012' },
];
const LANGUAGES = [{ tag: 'en-CA', name: 'English', scale: ENGLISH }, { tag: 'fr-CA', name: 'French', scale: FRENCH }];
// The answer ticked on each item (its position, 0..3), chosen so every position is used.
const PICK = [1, 3, 0, 2, 2, 1, 3, 0, 1, 2];

/** The EPDS as the page shows it: per item, the question line and each answer's text and score. */
async function shownScale(page) {
  return page.evaluate(() => {
    const items = {};
    for (let item = 1; item <= 10; item++) {
      const radios = [...document.querySelectorAll(`input[type="radio"][name="ar2_EPDS${item}"]`)];
      if (!radios.length) continue;
      const cell = radios[0].closest('td');
      // The question is the text before the cell's first line break.
      let question = '';
      for (const node of cell.childNodes) {
        if (node.nodeName === 'BR' || node.nodeName === 'SPAN') break;
        question += node.textContent;
      }
      items[item] = {
        question: question.replace(/\s+/g, ' ').trim(),
        answers: radios.map(radio => ({ text: radio.parentElement.textContent.replace(/\s+/g, ' ').trim(), score: Number(radio.value) })),
      };
    }
    return items;
  });
}

async function workflow(s) {
  const { sql, patient, marker, config } = s;
  const q = h.sqlString;
  const present = sql.value(`SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME IN (${FORMS.map(form => q(form.table)).join(',')})`);
  if (present !== String(FORMS.length)) throw new h.SkipCheck('This BC check needs the BC-AR 2007 and 2012 tables');
  const entries = FORMS.map(form => ({ form, name: `${marker.slice(-12)} ${form.code}` }));
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterForm WHERE form_value LIKE ${q(`%fixture=${marker}&%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${q(`%fixture=${marker}&%`)}`) === '0',
      'The owned Forms-menu registrations were not removed');
    sql.execute(FORMS.map(form => `DELETE FROM ${form.table} WHERE demographic_no=${patient}`).join(';'));
    h.assert(sql.value(`SELECT ${FORMS.map(form => `(SELECT COUNT(*) FROM ${form.table} WHERE demographic_no=${patient})`).join('+')}`) === '0',
      'The owned patient\'s BC-AR records were not removed');
  });
  for (const { form, name } of entries) {
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${q(`../form/${form.view}.jsp?fixture=${marker}&demographic_no=`)},${q(name)},${q(form.table)},COALESCE(MAX(hidden),0)+1
      FROM encounterForm`);
  }

  // A chart holds its patient's note lock for its session and releases it on pagehide. The two logins are the same
  // user in two sessions, so each leaves the chart before the other enters it, as echart-lock-lifecycle does; entering
  // while the other holds it asks "You have started to edit this note in another window".
  const lockCount = `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(s.provider)}`;
  const leaveChart = async (page) => {
    if (!page || page.isClosed()) return;
    const closed = page.waitForEvent('close', { timeout: 20000 });
    await page.close({ runBeforeUnload: true });
    await closed;
    const deadline = Date.now() + 15000;
    while (sql.value(lockCount) !== '0') {
      h.assert(Date.now() < deadline, 'closing the chart did not release its note lock');
      await new Promise(resolve => setTimeout(resolve, 250));
    }
  };

  for (const language of LANGUAGES) {
    let chart;
    await s.step(`${language.name}: a ${language.tag} login reaches the patient's chart`, async () => {
      if (language.tag === 'en-CA') {
        chart = await s.chart();
        return;
      }
      const context = await h.newContext(s.context.browser(), config, { locale: language.tag,
        extraHTTPHeaders: { 'Accept-Language': language.tag } });
      context.setDefaultTimeout(20000);
      context.on('page', page => h.wireStrictPage(page, NAME, s.recorder));
      s.cleanup(() => context.close().catch(() => {}));
      await h.login(context, config, s.recorder);
      // The Master Record's links are translated, and the shared helper finds them by their English names, so the
      // French login enters the chart through the E-Chart link's own route. Loading the address the English chart ended
      // on instead skips that entry, and the chart page then answers parts of itself with 500 (a session without the
      // encounter's program: CaseManagementManagerImpl.hasAccessRight).
      await leaveChart(await s.chart());
      chart = await context.newPage();
      await h.gotoApp(chart, config.baseUrl,
        `/encounter/IncomingEncounter?demographicNo=${encodeURIComponent(patient)}&providerNo=${encodeURIComponent(s.provider)}`);
      await waitForNavbars(chart, 20000);
      h.assert(new URL(chart.url()).searchParams.get('demographicNo') === patient, 'the French login opened another chart');
      h.assert(/^fr\b/i.test(await chart.evaluate(() => navigator.language)), 'the French context does not report a French browser');
    });

    for (const { form, name } of entries) {
      await s.step(`${language.name}, ${form.title}: page 2 shows the published EPDS wording and scores it the published way`, async () => {
        await ui.openFormsMenu(chart);
        const link = chart.getByRole('link', { name, exact: true });
        await link.waitFor({ state: 'visible', timeout: 20000 });
        const page = await ui.clickOpensPopup(chart, link,
          { context: chart.context(), recorder: s.recorder, label: `epds-${form.code}-${language.tag}`, timeout: 20000 });
        const toPage2 = page.locator(`a[href*="${form.page2}?"]:not([href^="javascript"])`).first();
        await Promise.all([page.waitForURL(url => url.pathname.endsWith(`/form/${form.page2}`), { waitUntil: 'load' }), toPage2.click()]);
        const shown = await shownScale(page);
        const wrong = [];
        for (let item = 1; item <= 10; item++) {
          const [question, answers] = language.scale[item];
          const here = shown[item];
          if (!here) { wrong.push(`item ${item} is missing`); continue; }
          if (here.question !== question) wrong.push(`item ${item} asks ${JSON.stringify(here.question)}, not ${JSON.stringify(question)}`);
          const expectedScores = FORWARD_SCORED.has(item) ? [0, 1, 2, 3] : [3, 2, 1, 0];
          here.answers.forEach((answer, position) => {
            if (answer.text !== answers[position]) {
              wrong.push(`item ${item} answer ${position + 1} reads ${JSON.stringify(answer.text)}, not ${JSON.stringify(answers[position])}`);
            }
            if (answer.score !== expectedScores[position]) wrong.push(`item ${item} answer ${position + 1} scores ${answer.score}`);
          });
          if (here.answers.length !== 4) wrong.push(`item ${item} offers ${here.answers.length} answers`);
        }
        h.assert(!wrong.length, `${form.title} page 2 (${language.tag}) does not show the published EPDS: ${wrong.join('; ')}`);

        let total = 0;
        for (let item = 1; item <= 10; item++) {
          const position = PICK[item - 1];
          total += FORWARD_SCORED.has(item) ? position : 3 - position;
          await page.locator(`input[type="radio"][name="ar2_EPDS${item}"]`).nth(position).check();
        }
        const score = await page.locator('input[name="ar2_EPDSscore"]').inputValue();
        h.assert(score === String(total), `ticking one answer per item made the EPDS score ${score}, not ${total}`);
        await page.close();
      });
    }
    // The English login's step 4 below reopens the chart.
    if (language.tag !== 'en-CA') await leaveChart(chart);
  }

  // Step 4: the answers ticked on page 2 are stored and come back ticked when the record is reopened.
  const scores = PICK.map((position, index) => (FORWARD_SCORED.has(index + 1) ? position : 3 - position));
  const columns = [...PICK.keys()].map(index => `ar2_EPDS${index + 1}`);
  for (const { form, name } of entries) {
    await s.step(`${form.title}: the ticked EPDS answers are saved and come back ticked when the record is reopened`, async () => {
      let chart = await s.chart();
      await ui.openFormsMenu(chart);
      const link = chart.getByRole('link', { name, exact: true });
      await link.waitFor({ state: 'visible', timeout: 20000 });
      let page = await ui.clickOpensPopup(chart, link,
        { context: s.context, recorder: s.recorder, label: `epds-${form.code}-save`, timeout: 20000 });
      const toPage2 = page.locator(`a[href*="${form.page2}?"]:not([href^="javascript"])`).first();
      await Promise.all([page.waitForURL(url => url.pathname.endsWith(`/form/${form.page2}`), { waitUntil: 'load' }), toPage2.click()]);
      for (const [index, position] of PICK.entries()) {
        await page.locator(`input[type="radio"][name="ar2_EPDS${index + 1}"]`).nth(position).check();
      }
      await h.withExpectedDialogs(page, () => Promise.all([
        page.waitForNavigation({ waitUntil: 'load', timeout: 30000 }),
        page.getByRole('button', { name: 'Save', exact: true }).first().click(),
      ]));
      await h.assertNotErrorPage(page, `the saved ${form.title}`);
      const row = sql.rows(`SELECT ID, ${columns.join(',')}, ar2_EPDSscore FROM ${form.table}
        WHERE demographic_no=${patient} ORDER BY ID DESC LIMIT 1`)[0];
      h.assert(row, `Save stored no ${form.table} row`);
      const formId = row[0];
      h.assert(JSON.stringify(row.slice(1, 11)) === JSON.stringify(scores.map(String)),
        `${form.table} stored the EPDS answers ${JSON.stringify(row.slice(1, 11))}, not ${JSON.stringify(scores.map(String))}`);
      h.assert(row[11] === String(scores.reduce((sum, value) => sum + value, 0)), `${form.table} stored the EPDS score ${row[11]}`);
      await page.close();

      // A fresh chart lists the saved record; it reopens on page 2, the page it was saved from.
      const refreshed = await s.context.newPage();
      await refreshed.goto(chart.url(), { waitUntil: 'domcontentloaded' });
      await waitForNavbars(refreshed, 20000);
      chart = refreshed;
      const opener = `a[onclick*="/form/forwardshortcutname?formname=${name}&"]`;
      const saved = chart.locator('#leftNavBar li, #rightNavBar li').filter({ has: chart.locator(opener) });
      await saved.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
      h.assert(await saved.count() === 1, `the chart should list the saved ${form.title} once (listed ${await saved.count()} times)`);
      page = await ui.clickOpensPopup(chart, saved.first().locator(opener).first(),
        { context: s.context, recorder: s.recorder, label: `epds-${form.code}-reopen`, timeout: 20000, position: { x: 8, y: 9 } });
      h.assert(await page.locator('[name="formId"]').first().inputValue() === formId, 'the chart opened another record');
      if (!new URL(page.url()).pathname.endsWith(`/form/${form.page2}`)
          && (await page.locator('[name="form_link"]').first().inputValue().catch(() => '')) !== `${form.page2}.jsp`) {
        const link2 = page.locator(`a[href*="${form.page2}?"]:not([href^="javascript"])`).first();
        await Promise.all([page.waitForURL(url => url.pathname.endsWith(`/form/${form.page2}`), { waitUntil: 'load' }), link2.click()]);
      }
      const ticked = await page.evaluate(() => [...Array(10).keys()].map(index =>
        [...document.querySelectorAll(`input[type="radio"][name="ar2_EPDS${index + 1}"]`)].findIndex(radio => radio.checked)));
      h.assert(JSON.stringify(ticked) === JSON.stringify(PICK),
        `the reopened ${form.title} shows the EPDS answers at positions ${JSON.stringify(ticked)}, not ${JSON.stringify(PICK)} `
        + '(-1: no answer ticked)');
      await page.close();
      await refreshed.close();
    });
  }
}

if (require.main === module) runWorkflow(NAME, workflow);
module.exports = { workflow };
