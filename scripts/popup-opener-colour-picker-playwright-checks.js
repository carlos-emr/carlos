#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Tigra colour picker popup -> window.opener (risk sweep "lost popup openers";
 * coverage plan §2.3 administration, opener contracts).
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage Code Styles
 * (admin/manageCSSStyles, administration iframe) ▸ Text Colour picker icon
 * (share/javascript/picker.js TCP.popup) ▸ admin/picker.html ▸ click a swatch.
 * Asserts the picker renders its palette (it reads window.opener.TCP on load) and
 * that clicking a swatch writes "#RRGGBB" into the opener's Text Colour field and
 * closes the picker. picker.html is a static file, so it is served with no
 * Cross-Origin-Opener-Policy while the administration page carries
 * `same-origin`: Chromium puts the picker in a new browsing-context group and
 * window.opener is null. The same picker serves Schedule Template Code Setting,
 * UserPreferences.jsp and providerColourPicker.jsp (see the risk report).
 * Read-only: nothing is saved, so there are no fixtures and no cleanup.
 */
const h = require('./lib/playwright-harness');
const { openAdmin, adminFrame } = require('./billing-on-admin-config-playwright-checks');
const { runWorkflow } = require('./lib/workflow-session');
const { documentChain, pickInPopup, lostOpenerMessage } = require('./lib/popup-opener-helpers');

const STYLE_ROUTE = '/admin/manageCSSStyles';

async function workflow(s) {
  const chain = documentChain(s.context);
  let frame;

  await s.step('Manage Code Styles loads in Administration with an empty Text Colour field and its picker icon', async () => {
    const admin = await openAdmin(s);
    frame = await adminFrame(admin, STYLE_ROUTE, '#color');
    h.assert(await frame.locator('#color').inputValue() === '', 'The Text Colour field is not empty on a fresh page');
    h.assert(await frame.locator('a[href*="TCP.popup(document.forms[0].elements[\'color\'])"]').count() === 1,
      'The Text Colour picker icon is missing');
  });

  // Last: the picker callback (Cross-Origin-Opener-Policy severs it on this install).
  await s.step('the colour picker renders its palette and a picked swatch fills Text Colour and closes the picker', async () => {
    const popup = await s.popup(frame, frame.locator('a[href*="TCP.popup(document.forms[0].elements[\'color\'])"]'),
      'colour-picker');
    const swatch = popup.locator('a[href^="javascript:P.S("]');
    await swatch.first().waitFor({ state: 'attached', timeout: 5000 }).catch(() => {});
    h.assert(await swatch.count() > 0,
      await lostOpenerMessage('Colour picker (admin/picker.html)', popup, chain,
        'the picker rendered no palette because it could not read window.opener.TCP'));
    const href = await swatch.nth(10).getAttribute('href');
    const picked = `#${/P\.S\('([0-9A-Fa-f]{6})'\)/.exec(href)[1].toUpperCase()}`;
    const closed = await pickInPopup(popup, swatch.nth(10));
    h.assert(await frame.locator('#color').inputValue() === picked,
      closed ? 'The picker closed but Text Colour does not hold the picked colour'
        : await lostOpenerMessage('Colour picker', popup, chain));
    h.assert(closed, 'The picked colour was written but the picker did not close');
  });
}

if (require.main === module) runWorkflow('popup-opener-colour-picker', workflow, { openPatient: false });
module.exports = { workflow };
