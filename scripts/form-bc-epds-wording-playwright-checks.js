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
 * (Accept-Language fr-CA; that login opens the chart address the English login reached). Nothing is saved.
 * Asserted, per language and form:
 *   1. the form opens from the Forms menu and its page 2 link opens page 2;
 *   2. each of the ten items shows the published question and its four answers in the published order;
 *   3. ticking one answer per item makes the page's EPDS score the published total (items 1, 2 and 4 score the answers
 *      0..3 from the first, the others 3..0).
 * BC-AR 2012 printed its own English copy until #5050 (with item 6 damaged to "coping as card card-body
 * bg-body-tertiary as usual"), so it showed no French at all; scripts/bcar-epds-wording.test.js holds the bundles.
 *
 * Fixtures: the owned FAKE- patient and one marker-named Forms-menu registration per form (clinic-wide, so EXCLUSIVE=1),
 * removed and asserted in cleanup; the French login is the same test login in a second browser context.
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
  });
  for (const { form, name } of entries) {
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${q(`../form/${form.view}.jsp?fixture=${marker}&demographic_no=`)},${q(name)},${q(form.table)},COALESCE(MAX(hidden),0)+1
      FROM encounterForm`);
  }

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
      // French login opens the chart address the English login's E-Chart link opened.
      chart = await context.newPage();
      await chart.goto((await s.chart()).url(), { waitUntil: 'domcontentloaded' });
      await waitForNavbars(chart, 20000);
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
  }
}

if (require.main === module) runWorkflow(NAME, workflow);
module.exports = { workflow };
