/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const h = require('./playwright-harness');

/** Observe bill-entry journeys, which have a patient ID and never need patient-name search. */
function observeBillingNameQueries(context) {
  const leaks = [];
  const names = new Set(['demographicname', 'lastname', 'firstname']);
  const listener = request => {
    const url = new URL(request.url());
    if (!/\/billing(?:\/CA\/(?:BC|ON)\/|$)/.test(url.pathname)) return;
    const keys = [...url.searchParams.keys()].filter(key => names.has(key.replaceAll('_', '').toLowerCase()));
    if (keys.length) leaks.push({ route: url.pathname, keys });
  };
  context.on('request', listener);
  return {
    assertNoNames: () => h.assert(leaks.length === 0, `Bill-entry navigation leaked patient-name query keys: ${JSON.stringify(leaks)}`),
    dispose: () => context.off('request', listener),
  };
}

module.exports = { observeBillingNameQueries };
