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
 * button has a visible keyboard focus ring, opening the hidden panel moves focus
 * to its title and Esc closes it and returns focus to the button, and the single
 * #cs-announcer status region carries a message once the card settles.
 *
 * Injected responses (page.route on the allergies JSON action, own page and
 * recorder): at 1180 an HTTP 500, a load error that ends in ERROR with text
 * rather than LOADING; at 390 a NO_ACCESS status (visible card, not hidden) and
 * an OK with a 300-character unbroken name plus markup (no horizontal scroll,
 * markup shown as text). The shell's .catch path is covered by the fake-DOM
 * test scripts/chartspace-shell.test.js, not here.
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

// The page-level status region: a non-empty message after the card settles, kept
// outside the hidden panel, and the only aria-live element on the page.
async function assertAnnouncer(page, label) {
  const announced = await page.waitForFunction(() => {
    const node = document.getElementById('cs-announcer');
    return node && node.textContent.trim().length > 0;
  }, null, { timeout: 5000 }).then(() => true, () => false);
  const info = await page.evaluate(() => {
    const node = document.getElementById('cs-announcer');
    return {
      present: !!node,
      inPanel: !!(node && node.closest('#cs-hidden-panel')),
      liveCount: document.querySelectorAll('[aria-live]').length,
    };
  });
  assert(info.present, `${label}: #cs-announcer is missing`);
  assert(announced, `${label}: #cs-announcer stayed empty after the card settled`);
  assert(!info.inPanel, `${label}: #cs-announcer sits inside #cs-hidden-panel`);
  assert(info.liveCount === 1, `${label}: expected exactly one aria-live element, found ${info.liveCount}`);
}

async function checkWidth(page, baseUrl, demographicNo, viewport) {
  const label = `width ${viewport.width}`;
  await page.setViewportSize(viewport);
  await gotoApp(page, baseUrl, pagePath(demographicNo));
  await assertNotErrorPage(page, label);
  const state = await waitForSettledCard(page);
  assert(state !== 'LOADING', `${label}: allergies card stayed in LOADING`);
  await assertAnnouncer(page, label);

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
  // The panel title takes focus programmatically only (tabindex="-1"): never a Tab stop.
  assert(!focusables.some((f) => f.name === 'cs-hidden-title'),
    `${label}: #cs-hidden-title (tabindex=-1) was counted as keyboard-focusable`);
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
    // OK with nothing hidden marks focus and Esc n/a; CI (clean DB, patient 2 in EMPTY) exercises them.
    return state;
  }
  assert(outline, `${label}: keyboard focus did not land on any element (${focusDiag})`);
  if (!disabled) {
    assert(outline.id === 'cs-hidden-toggle', `${label}: Shift+Tab did not return focus to the hidden button`);
  }
  assert(outline.style !== 'none' && outline.width >= 2,
    `${label}: focused element has no visible outline (${outline.style}, ${outline.width}px)`);

  // Opening moves focus to the panel title; Esc closes the layer and hands focus
  // back to the button.
  if (!disabled) {
    await toggle.click();
    assert(await toggle.getAttribute('aria-expanded') === 'true', `${label}: aria-expanded not true after click`);
    assert(await page.locator('#cs-hidden-panel').isVisible(), `${label}: hidden panel not visible after click`);
    assert(await page.evaluate(() => document.activeElement && document.activeElement.id) === 'cs-hidden-title',
      `${label}: opening the hidden panel did not move focus to #cs-hidden-title`);
    await page.keyboard.press('Escape');
    assert(await toggle.getAttribute('aria-expanded') === 'false', `${label}: aria-expanded not false after Esc`);
    assert(!(await page.locator('#cs-hidden-panel').isVisible()), `${label}: hidden panel still visible after Esc`);
    assert(await page.evaluate(() => document.activeElement && document.activeElement.id) === 'cs-hidden-toggle',
      `${label}: focus did not return to the hidden button after Esc`);
  }
  return state;
}

// One stubbed allergies response on its own page and recorder: the injected
// responses (an expected HTTP 500 among them) must not trip the strict assertions
// of the real-endpoint widths. Every case must settle in expectedState with a
// visible, non-empty card and an announcement; verify() adds case-specific checks.
async function checkInjected(context, config, demographicNo, injected) {
  const { label, viewport, status, body, expectedState, verify } = injected;
  const recorder = createRecorder();
  const page = await context.newPage();
  wireStrictPage(page, `chartspace-${label}`, recorder);
  await page.setViewportSize(viewport);
  await page.route(ALLERGIES_ROUTE, (route) => route.fulfill({
    status,
    contentType: 'application/json',
    body,
  }));
  try {
    await gotoApp(page, config.baseUrl, pagePath(demographicNo));
    await assertNotErrorPage(page, `${label} load`);
    const state = await waitForSettledCard(page);
    assert(state === expectedState, `${label}: ended in data-state=${state}, expected ${expectedState}`);
    const text = (await page.locator(CARD).evaluate((node) => node.innerText || '')).trim();
    assert(text.length > 0 && await page.locator(CARD).isVisible(), `${label}: ${state} allergies card has no visible text`);
    await assertAnnouncer(page, label);
    if (verify) {
      await verify(page, label);
    }
    assert(recorder.pageErrors.length === 0, `${label}: the injected response raised an uncaught JavaScript error`);
  } finally {
    await page.unroute(ALLERGIES_ROUTE).catch(() => {});
    await page.close().catch(() => {});
  }
}

// A load error: HTTP 500 is absorbed by the block's load() (response not ok ->
// ERROR), so the card must end in data-state="ERROR" with text, never LOADING.
// The shell's .catch path is covered by scripts/chartspace-shell.test.js (fake DOM).
function loadErrorCase() {
  return {
    label: 'injected-http-500',
    viewport: WIDTHS[0],
    status: 500,
    body: '{"error":"injected by chartspace check"}',
    expectedState: 'ERROR',
  };
}

// NO_ACCESS is never auto-hidden: the card stays in #cs-right and the hidden
// button does not signal data for it.
function noAccessCase() {
  return {
    label: 'injected-no-access',
    viewport: { width: 390, height: 844 },
    status: 200,
    body: JSON.stringify({ status: 'NO_ACCESS', items: [] }),
    expectedState: 'NO_ACCESS',
    verify: async (page, label) => {
      const placement = await page.locator(CARD).evaluate((node) => ({
        inPanel: !!node.closest('#cs-hidden-panel'),
        inRight: !!node.closest('#cs-right'),
        blocks: document.querySelectorAll('.cs-block').length,
      }));
      assert(!placement.inPanel, `${label}: NO_ACCESS card sits inside #cs-hidden-panel`);
      assert(placement.inRight, `${label}: NO_ACCESS card is not in #cs-right`);
      const toggle = page.locator('#cs-hidden-toggle');
      const hasData = await toggle.getAttribute('data-has-data');
      assert(hasData === 'false', `${label}: hidden button has data-has-data=${hasData}, expected false`);
      if (placement.blocks === 1) {
        assert(await toggle.isDisabled(), `${label}: hidden button enabled with a single, visible block`);
      }
    },
  };
}

// A 300-character name with no break points and markup in the text fields: no
// horizontal scroll, the long name shown whole, and markup shown as text only.
function longTextCase() {
  const longName = 'A'.repeat(300);
  return {
    label: 'injected-long-text',
    viewport: { width: 390, height: 844 },
    status: 200,
    body: JSON.stringify({
      status: 'OK',
      items: [
        { description: longName, severityCode: '3', reaction: '', startDate: '' },
        { description: '<b>x</b>', severityCode: '1', reaction: '<i>y</i>', startDate: '' },
      ],
    }),
    expectedState: 'OK',
    verify: async (page, label) => {
      const overflow = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }));
      assert(overflow.scrollWidth <= overflow.clientWidth,
        `${label}: horizontal scroll (${overflow.scrollWidth} > ${overflow.clientWidth})`);
      const rendered = await page.locator(CARD).evaluate((node) => ({
        names: Array.from(node.querySelectorAll('.cs-allergy-name')).map((n) => n.textContent),
        reactions: Array.from(node.querySelectorAll('.cs-allergy-reaction')).map((n) => n.textContent),
        markupElements: node.querySelector('b, i') !== null,
      }));
      assert(rendered.names.some((name) => name.length === longName.length),
        `${label}: the ${longName.length}-character name was not shown whole`);
      assert(rendered.names.includes('<b>x</b>'), `${label}: "<b>x</b>" was not shown literally as text`);
      assert(rendered.reactions.includes('<i>y</i>'), `${label}: "<i>y</i>" was not shown literally as text`);
      assert(!rendered.markupElements, `${label}: server text was parsed into b/i elements inside the card`);
    },
  };
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
    await checkInjected(context, config, demographicNo, loadErrorCase());
    console.log('ok 1180px injected HTTP 500 -> ERROR');
    await checkInjected(context, config, demographicNo, noAccessCase());
    console.log('ok 390px injected NO_ACCESS -> visible card, not hidden');
    await checkInjected(context, config, demographicNo, longTextCase());
    console.log('ok 390px injected long text and markup -> OK, no scroll, text only');
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
