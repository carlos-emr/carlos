#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Follow Chart -> Calculators -> About / Licence through actual popup links.
// Check the deployed build identity and close controls, not merely HTTP 200.
// EXPECTED_BUILD_VERSION optionally pins the exact WAR version under test.
const { assert } = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

async function closeThroughUi(page) {
  await Promise.all([
    page.waitForEvent('close'),
    page.locator('a[href="javascript:window.close()"], a[href="javascript:window.close();"]').first().click(),
  ]);
}

async function workflow(s) {
  const chart = await s.chart();
  const menu = await s.popup(chart, chart.locator('a[onclick*="ViewCalculators"]').first(), 'about-calculators');
  const index = await clickOpensPopup(menu, menu.locator('a[onclick*="ViewGeneralCalculators"]'),
    { context: s.context, recorder: s.recorder, label: 'about-conversions', closesOpener: true });
  await s.step('general conversions calculate and calibrate without silent errors', async () => {
    const forms = index.locator('form');
    const convert = async (formIndex, input, value, output, expected, tolerance) => {
      const form = forms.nth(formIndex);
      await form.locator(`[name="${input}"]`).fill(value);
      await form.locator('input[onclick="convertform(this.form)"]').click();
      const rendered = await form.locator(`[name="${output}"]`).inputValue();
      const actual = Number(rendered);
      assert(rendered.trim() !== '' && Number.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
        `Conversion ${formIndex}/${input} produced ${actual}; expected ${expected}`);
    };
    // Independent physical identities, including both previously incorrect factors.
    await convert(0, 'val6', '1', 'val1', 1852, 0.001);
    await convert(0, 'val2', '12', 'val3', 1, 0.000001);
    await convert(1, 'val7', '1', 'val1', 1016.0469088, 0.01);
    await convert(1, 'val3', '1', 'val1', 0.45359237, 0.000001);
    await convert(2, 'val4', '1', 'val1', 3.785411784, 0.00001);
    await convert(2, 'val2', '1', 'val1', 0.0295735295625, 0.0000001);
    await convert(2, 'val3', '1', 'val1', 0.946352946, 0.000001);
    await convert(2, 'val5', '1', 'val1', 4.54609, 0.00001);
    await convert(0, 'val1', '0', 'val2', 0, 0);
    await convert(0, 'val1', '1e-12', 'val2', 3.937007874e-11, 1e-17);
    const distance = forms.nth(0);
    const previous = await distance.locator('[name="val2"]').inputValue();
    await distance.locator('[name="val2"]').focus();
    assert(await distance.locator('[name="val2"]').inputValue() === previous,
      'Focusing a conversion result erased it');
    await distance.locator('[name="val1"]').fill('invalid');
    await distance.locator('input[onclick="convertform(this.form)"]').click();
    assert(!await distance.locator('[name="val1"]').evaluate(input => input.validity.valid),
      'Invalid input was silently accepted');
    assert(await distance.locator('[name="val2"]').inputValue() === '', 'Invalid input produced a result');
    assert(await distance.locator('[name="val1"]').inputValue() === 'invalid',
      'Validation erased the value the user needs to correct');
    await convert(0, 'val1', '1', 'val2', 1 / 0.0254, 0.00001);
    for (let i = 0; i < 3; i++) {
      const form = forms.nth(i);
      await form.locator('input[onclick="resetform(this.form)" i]').click();
      assert(await form.locator('[name="val1"]').inputValue() === '1', 'Calibrate did not reset the base unit');
      assert(Number(await form.locator('[name="val2"]').inputValue()) > 0, 'Calibrate did not calculate other units');
    }
    await forms.nth(3).locator('[name="F"]').fill('212');
    await forms.nth(3).locator('[name="F"]').press('Tab');
    assert(Number(await forms.nth(3).locator('[name="C"]').inputValue()) === 100, 'Fahrenheit conversion failed');
    await forms.nth(3).locator('[name="C"]').fill('-40');
    await forms.nth(3).locator('[name="C"]').press('Tab');
    assert(Number(await forms.nth(3).locator('[name="F"]').inputValue()) === -40, 'Celsius conversion failed');
    await forms.nth(3).locator('[name="C"]').fill('invalid');
    await forms.nth(3).locator('[name="C"]').press('Tab');
    assert(await forms.nth(3).locator('[name="F"]').inputValue() === '', 'Invalid temperature produced a result');
    assert(!await forms.nth(3).locator('[name="C"]').evaluate(input => input.validity.valid),
      'Invalid temperature was silently accepted');
    await forms.nth(3).locator('[name="F"]').fill('212');
    await forms.nth(3).locator('[name="F"]').press('Tab');
    assert(await forms.nth(3).locator('[name="C"]').inputValue() === '100',
      'Correcting the opposite temperature field did not recalculate');
    assert(await forms.nth(3).locator('[name="C"]').evaluate(input => input.validity.valid),
      'A recalculated temperature retained its old validation error');
    await forms.nth(3).locator('[name="C"]').fill('invalid');
    await forms.nth(3).locator('[name="C"]').press('Tab');
    await forms.nth(3).locator('[name="C"]').fill('');
    await forms.nth(3).locator('[name="C"]').press('Tab');
    assert(await forms.nth(3).locator('[name="C"]').evaluate(input => input.validity.valid),
      'Clearing the temperature left a stale validation error');
    assert(await forms.nth(3).locator('[name="F"]').inputValue() === '',
      'An empty temperature produced a numeric result');
  });
  await s.step('About popup identifies CARLOS and the deployed WAR', async () => {
    const page = await s.popup(index, index.locator('a[href*="ViewAbout"]').first(), 'about-release');
    await page.locator('.build_info').waitFor({ state: 'visible' });
    const build = await page.locator('.build_info').innerText();
    assert(/build date:\s*\S+/i.test(build) && /build tag:\s*\S+/i.test(build), 'About omitted build identity');
    assert(!/\$\{|@[A-Za-z][^\s]*@|unknown|unavailable/i.test(build), 'About contains unresolved build metadata');
    if (process.env.EXPECTED_BUILD_VERSION) {
      assert(build.includes(process.env.EXPECTED_BUILD_VERSION), 'About reports a different WAR version than the package under test');
    }
    assert((await page.title()).includes('CARLOS EMR'), 'About title does not identify CARLOS');
    assert(await page.locator('a[href="https://github.com/carlos-emr/carlos"]').count() > 0,
      'About omitted the project link');
    assert((await page.locator('body').innerText()).includes('WITHOUT ANY WARRANTY'), 'About omitted the warranty notice');
    await closeThroughUi(page);
    assert(!index.isClosed(), 'Closing About also closed its opener');
  });
  await s.step('Licence popup preserves CARLOS terms and upstream attribution', async () => {
    const page = await s.popup(index, index.locator('a[href*="ViewLicense"]').first(), 'licence-release');
    const body = await page.locator('body').innerText();
    assert(body.includes('CARLOS EMR') && body.includes('GNU General Public Licence'), 'Licence omitted CARLOS licensing terms');
    assert(body.includes('version 2 or, at your option, any later version'), 'Licence changed the stated licence version');
    const upstream = await page.locator('pre').innerText();
    assert(upstream.includes('McMaster University') && upstream.includes('GNU General Public License'),
      'Licence omitted the upstream notice');
    await closeThroughUi(page);
    assert(!index.isClosed(), 'Closing Licence also closed its opener');
  });
  await s.step('legacy calculator bookmark reaches the maintained calculator', async () => {
    const legacy = await s.context.newPage();
    for (const fragment of ['', '#weight', '#temps']) {
      await legacy.goto(`${s.config.baseUrl}/encounter/calculators/GeneralCalculators.htm${fragment}`);
      await legacy.waitForURL(url => url.pathname.endsWith('/encounter/calculators/ViewGeneralCalculators')
        && url.hash === fragment);
      assert(await legacy.locator('form').count() === 4, 'Legacy bookmark did not render the calculator');
      if (fragment) {
        assert(await legacy.locator(`a[name="${fragment.slice(1)}"]`).count() === 1,
          'Legacy bookmark names a missing conversion section');
      }
    }
    await legacy.close();
  });
}

if (require.main === module) runWorkflow('about-licence', workflow);
module.exports = { workflow };
