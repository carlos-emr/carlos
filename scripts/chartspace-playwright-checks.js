#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for the ChartSpace page (#4349, PR1) at 1180, 1460 and 390 px.
 *
 * Read-only: it logs in through the shared harness (TEST_USER / TEST_PASSWORD /
 * TEST_PIN from the environment, devcontainer defaults otherwise), then opens
 * /encounter/chartspace directly for CHARTSPACE_DEMOGRAPHIC_NO (default 2, a
 * FAKE- demo patient). It never writes and does not validate the schedule.
 *
 * Per width: no horizontal scroll, the Allergies card leaves LOADING and shows
 * text, the hidden-sections button signals data with btn-danger plus text, the
 * button has a visible keyboard focus ring, and Esc closes the hidden panel and
 * returns focus to the button. At 1180 only, the allergies JSON action is
 * intercepted with HTTP 500 to prove the card ends in ERROR rather than LOADING.
 *
 * A failed login ends the check as SKIP and is never retried (login_lock).
 *
 *   node scripts/chartspace-playwright-checks.js
 *   CHARTSPACE_DEMOGRAPHIC_NO=<id> BASE_URL=... node scripts/chartspace-playwright-checks.js
 */

const {
  SkipCheck,
  assert,
  assertNotErrorPage,
  assertStrictPage,
  createRecorder,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  runCheck,
  wireStrictPage,
} = require('./lib/playwright-harness');

const WIDTHS = [
  { width: 1180, height: 900 },
  { width: 1460, height: 900 },
  { width: 390, height: 844 },
];
const CARD = '[data-block="allergies"]';
const ALLERGIES_ROUTE = '**/encounter/chartspace/allergies**';

function pagePath(demographicNo) {
  return `/encounter/chartspace?demographicNo=${encodeURIComponent(demographicNo)}`;
}

async function waitForSettledCard(page) {
  await page.waitForFunction(
    (selector) => {
      const card = document.querySelector(selector);
      return card && card.getAttribute('data-state') && card.getAttribute('data-state') !== 'LOADING';
    },
    CARD,
    { timeout: 20000 },
  );
  return page.locator(CARD).getAttribute('data-state');
}

async function checkWidth(page, baseUrl, demographicNo, viewport) {
  const label = `width ${viewport.width}`;
  await page.setViewportSize(viewport);
  await gotoApp(page, baseUrl, pagePath(demographicNo));
  await assertNotErrorPage(page, label);
  const state = await waitForSettledCard(page);
  assert(state !== 'LOADING', `${label}: allergies card stayed in LOADING`);

  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  assert(overflow.scrollWidth <= overflow.clientWidth,
    `${label}: horizontal scroll (${overflow.scrollWidth} > ${overflow.clientWidth})`);

  const toggle = page.locator('#cs-hidden-toggle');
  const hiddenLabel = await page.evaluate(() => {
    const t = document.getElementById('cs-hidden-toggle');
    return t ? t.textContent : '';
  });

  // A state with text, never a bare or empty card.
  const cardText = (await page.locator(CARD).evaluate((node) => node.innerText || node.textContent || '')).trim();
  assert(cardText.length > 0, `${label}: allergies card has no text in state ${state}`);
  if (state === 'EMPTY') {
    const inPanel = await page.locator(`#cs-hidden-panel ${CARD}`).count();
    assert(inPanel === 1, `${label}: EMPTY allergies card is not inside #cs-hidden-panel`);
    assert(hiddenLabel.includes('(1)'), `${label}: hidden button does not show (1): ${JSON.stringify(hiddenLabel)}`);
  } else {
    assert(await page.locator(`#cs-hidden-panel ${CARD}`).count() === 0,
      `${label}: ${state} allergies card unexpectedly sits in the hidden panel`);
  }

  // Hidden button signalling: colour AND text, never colour alone.
  const hasData = await toggle.getAttribute('data-has-data');
  const classes = (await toggle.getAttribute('class')) || '';
  if (hasData === 'true') {
    const flag = (await page.locator('#cs-hidden-toggle .cs-hidden-flag').innerText()).trim();
    assert(classes.split(/\s+/).includes('btn-danger'), `${label}: data-has-data=true without btn-danger`);
    assert(flag.length > 0 && hiddenLabel.includes(flag), `${label}: data-has-data=true without the msgHiddenHasData text`);
  } else {
    assert(!classes.split(/\s+/).includes('btn-danger'), `${label}: btn-danger present with data-has-data=${hasData}`);
  }

  // Visible keyboard focus (outline), reached and re-reached by keyboard.
  const disabled = await toggle.isDisabled();
  // Diagnostics for the keyboard checks: card state, toggle state and how many
  // elements the keyboard can reach at all.
  const focusables = await page.evaluate(() => Array.from(document.querySelectorAll(
    'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), '
    + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex^="-"])')).map((el) => ({
    tag: el.tagName.toLowerCase(),
    name: el.id || (el.textContent || '').trim().slice(0, 24),
    // [hidden] panels and display:none ancestors leave no client rects: Tab cannot reach them.
    visible: el.getClientRects().length > 0 && (typeof el.checkVisibility !== 'function' || el.checkVisibility()),
  })));
  const visibleFocusables = focusables.filter((f) => f.visible);
  const focusableCount = visibleFocusables.length;
  const focusDiag = `state=${state} toggleDisabled=${disabled} focusable=${focusableCount}`;
  console.log(`${label}: focus diagnostics: ${focusDiag}; candidates: `
    + (focusables.map((f) => `${f.tag}#${JSON.stringify(f.name)} visible=${f.visible}`).join(', ') || 'none'));
  if (!disabled) {
    await toggle.focus();
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');
  } else {
    await page.evaluate(() => document.body.focus());
    await page.keyboard.press('Tab');
  }
  const outline = await page.evaluate(() => {
    const node = document.activeElement;
    if (!node || node === document.body) {
      return null;
    }
    const style = window.getComputedStyle(node);
    return { id: node.id, style: style.outlineStyle, width: parseFloat(style.outlineWidth) };
  });
  if (!outline && state === 'OK' && disabled && focusableCount === 0) {
    // Nothing on the page is keyboard-reachable (the only button is disabled with no
    // hidden sections), so there is no element whose focus ring could be asserted.
    console.log(`${label}: focus: n/a (no focusable elements)`);
    return state;
  }
  assert(outline, `${label}: keyboard focus did not land on any element (${focusDiag})`);
  if (!disabled) {
    assert(outline.id === 'cs-hidden-toggle', `${label}: Shift+Tab did not return focus to the hidden button`);
  }
  assert(outline.style !== 'none' && outline.width >= 2,
    `${label}: focused element has no visible outline (${outline.style}, ${outline.width}px)`);

  // Esc closes the layer and hands focus back to the button.
  if (!disabled) {
    await toggle.click();
    assert(await toggle.getAttribute('aria-expanded') === 'true', `${label}: aria-expanded not true after click`);
    assert(await page.locator('#cs-hidden-panel').isVisible(), `${label}: hidden panel not visible after click`);
    await page.keyboard.press('Escape');
    assert(await toggle.getAttribute('aria-expanded') === 'false', `${label}: aria-expanded not false after Esc`);
    assert(!(await page.locator('#cs-hidden-panel').isVisible()), `${label}: hidden panel still visible after Esc`);
    assert(await page.evaluate(() => document.activeElement && document.activeElement.id) === 'cs-hidden-toggle',
      `${label}: focus did not return to the hidden button after Esc`);
  }
  return state;
}

// The shell's .catch path: a failing allergies endpoint must end in ERROR with text.
async function checkErrorPath(context, config, demographicNo) {
  // Own recorder: the injected HTTP 500 is expected here and must not trip the strict
  // assertions of the real-endpoint widths.
  const errorRecorder = createRecorder();
  const page = await context.newPage();
  wireStrictPage(page, 'chartspace-error', errorRecorder);
  await page.setViewportSize(WIDTHS[0]);
  await page.route(ALLERGIES_ROUTE, (route) => route.fulfill({
    status: 500,
    contentType: 'application/json',
    body: '{"error":"injected by chartspace check"}',
  }));
  try {
    await gotoApp(page, config.baseUrl, pagePath(demographicNo));
    await assertNotErrorPage(page, 'injected-error load');
    const state = await waitForSettledCard(page);
    assert(state === 'ERROR', `injected HTTP 500 ended in data-state=${state}, expected ERROR`);
    const text = (await page.locator(CARD).evaluate((node) => node.innerText || '')).trim();
    assert(text.length > 0 && await page.locator(CARD).isVisible(), 'ERROR allergies card has no visible text');
    assert(await page.locator(CARD).getAttribute('data-state') !== 'LOADING', 'ERROR card reverted to LOADING');
    assert(errorRecorder.pageErrors.length === 0, 'the injected failure raised an uncaught JavaScript error');
  } finally {
    await page.unroute(ALLERGIES_ROUTE).catch(() => {});
    await page.close().catch(() => {});
  }
}

async function main() {
  const config = readConfig();
  const demographicNo = process.env.CHARTSPACE_DEMOGRAPHIC_NO || '2';
  assert(/^[1-9]\d*$/.test(demographicNo), 'CHARTSPACE_DEMOGRAPHIC_NO must be a positive integer');
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    let page;
    try {
      // No retry: login_lock locks the account after repeated failures.
      page = await login(context, config, recorder);
    } catch (error) {
      throw new SkipCheck(`login did not succeed with the configured credentials: ${error.message}`);
    }
    wireStrictPage(page, 'chartspace', recorder);
    for (const viewport of WIDTHS) {
      const state = await checkWidth(page, config.baseUrl, demographicNo, viewport);
      console.log(`ok ${viewport.width}px allergies=${state}`);
    }
    assertStrictPage(recorder);
    await checkErrorPath(context, config, demographicNo);
    console.log('ok 1180px injected HTTP 500 -> ERROR');
    await context.close();
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'chartspace', run: main }).catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exitCode = 1;
  });
}
