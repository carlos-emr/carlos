#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Legacy calculator bookmarks reach the maintained, guarded calculators
 * (issue #3665, findings-log 130).
 *
 * encounter/calculators/OsteoporoticFracture.htm and
 * CoronaryArteryDiseaseRiskPrediction.html were public static copies of the
 * WEB-INF calculator pages. Nothing linked to them, but a bookmark still
 * reached them, and they still ran the unguarded age ladder findings 8 and 9
 * fixed in the maintained pages: a blank age answered for the youngest band, a
 * typo for the oldest. Each copy is now a redirect, so this check follows a
 * bookmark the way a browser would -- by its address, which is the only way in
 * -- and proves it lands on the maintained route with the legacy sex/age
 * prefill intact, and that the page it lands on refuses a blank age.
 *
 * The Framingham/UKPDS calculator is static by design; its direct address is
 * checked here for the chart prefill (the in-chart path is covered by
 * clinical-calculators-playwright-checks.js).
 *
 * READ-ONLY: calculators persist nothing and "paste to the chart" is never used.
 */
const { assert } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { REFUSED_AGE_TEXT } = require('./clinical-calculators-playwright-checks');

const LEGACY_BOOKMARKS = [
  { file: 'OsteoporoticFracture.htm', route: 'ViewOsteoporoticFracture', ageForm: true },
  { file: 'CoronaryArteryDiseaseRiskPrediction.html', route: 'ViewCoronaryArteryDiseaseRiskPrediction', ageForm: true },
  { file: 'SimpleCalculator.htm', route: 'ViewSimpleCalculator', ageForm: false },
];
const QUERY = '?sex=F&age=67';

async function followBookmark(s, bookmark) {
  const page = await s.context.newPage();
  try {
    await page.goto(`${s.config.baseUrl}/encounter/calculators/${bookmark.file}${QUERY}`);
    await page.waitForURL((url) => url.pathname.endsWith(`/encounter/calculators/${bookmark.route}`));
    await page.waitForLoadState('domcontentloaded');
    assert(new URL(page.url()).search === QUERY,
      `${bookmark.file} redirected without the legacy prefill: ${page.url()}`);
    if (!bookmark.ageForm) {
      assert(await page.locator('form[name="rcform"] input[name="display"]').count() === 1,
        `${bookmark.file} did not render the maintained calculator`);
      return;
    }
    const form = page.locator('form[name="calCorArDi"]');
    assert(await form.locator('input[name="age"]').inputValue() === '67',
      `${bookmark.file}: the bookmark's age did not reach the maintained page`);
    assert(await form.locator('input[name="sex"][value="F"]').isChecked(),
      `${bookmark.file}: the bookmark's sex did not reach the maintained page`);
    // The defect the static copy carried: a blank age must be refused, not
    // answered for the youngest band.
    await form.locator('input[name="age"]').fill('');
    await form.locator('input[type="button"]').first().click();
    const text = await page.locator('textarea[name="prediction"]').inputValue();
    assert(REFUSED_AGE_TEXT.test(text) && !/Probability:\s*[\d.]+\s*%|Point Count:\s*-?\d/.test(text),
      `${bookmark.file}: the page a bookmark reaches answered a blank age: ${JSON.stringify(text.trim())}`);
  } finally {
    await page.close();
  }
}

async function workflow(s) {
  for (const bookmark of LEGACY_BOOKMARKS) {
    await s.step(`${bookmark.file} bookmark reaches the maintained ${bookmark.route}`, () => followBookmark(s, bookmark));
  }
  await s.step('Framingham/UKPDS takes the patient from its address and refuses an age it cannot use', async () => {
    const page = await s.context.newPage();
    try {
      await page.goto(`${s.config.baseUrl}/encounter/calculators/riskcalc/index.html?sex=F&age=62`);
      assert(await page.locator('#cAge').inputValue() === '62', 'the age in the address did not prefill the form');
      assert(await page.locator('#cFemale').isChecked(), 'the sex in the address did not prefill the form');
      assert(await page.locator('#riskInputRefused').count() === 0, 'a usable age was refused on load');
      assert(await page.locator('#bp6c3').innerText() !== '', 'the table was not computed on load');
      await page.goto(`${s.config.baseUrl}/encounter/calculators/riskcalc/index.html?sex=M&age=82`);
      const refused = await page.locator('#riskInputRefused').innerText();
      assert(/age as a whole number from 30 to 75/.test(refused),
        `an 82-year-old must be refused by name on load, not answered for another age: ${JSON.stringify(refused)}`);
      assert(await page.locator('#cAge').inputValue() === '82', 'the refused age was rewritten');
      assert(await page.locator('#bp6c3').innerText() === '', 'a figure was left on screen under the refusal');
    } finally {
      await page.close();
    }
  });
}

if (require.main === module) runWorkflow('legacy-calculator-bookmarks', workflow, { openPatient: false });
module.exports = { LEGACY_BOOKMARKS, QUERY, workflow };
