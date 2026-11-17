/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 */
'use strict';
const h = require('./playwright-harness');
async function offeredOptions(select) {
  return select.locator('option').evaluateAll(options => options.map(option => ({
    value: option.value, disabled: option.disabled || Boolean(option.closest('optgroup')?.disabled),
  })));
}

/** Reuse the owned invoice's exact native clinic/physician on Invoice Reports. */
async function selectBillingReportContext(frame, context) {
  h.assert(context && typeof context.multisites === 'boolean' && /^-?\d+$/.test(context.providerNo),
    'The owned report billing context is invalid');
  const providers = frame.locator('select[name="providerview"]');
  await providers.waitFor({ state: 'visible', timeout: 20000 });
  h.assert(await providers.count() === 1, 'The native report must offer exactly one physician control');
  const sites = frame.locator('select[name="site"]');
  h.assert(await sites.count() === (context.multisites ? 1 : 0), 'The native report clinic mode differs from the owned invoice');
  // In multisite mode the native physician change submits immediately when both
  // dates are populated. Clear only these native controls first; the caller
  // restores its exact owned date window before the explicit Create Report.
  for (const selector of ['#xml_vdate', '#xml_appointment_date']) {
    await frame.locator(selector).fill('');
    h.assert(await frame.locator(selector).inputValue() === '', 'The report date did not clear before physician selection');
  }
  if (context.multisites) {
    h.assert(typeof context.clinic === 'string' && context.clinic && context.clinic !== 'none', 'The owned report clinic is invalid');
    const matches = (await offeredOptions(sites)).filter(option => option.value === context.clinic);
    h.assert(matches.length === 1 && !matches[0].disabled, 'The native report does not offer the exact owned clinic');
    await sites.selectOption(context.clinic);
    h.assert(await sites.inputValue() === context.clinic, 'The owned report clinic selection did not persist');
  } else h.assert(context.clinic === null, 'A nonmultisite report must preserve a NULL clinic');
  const matches = (await offeredOptions(providers)).filter(option => option.value === context.providerNo);
  h.assert(matches.length === 1 && !matches[0].disabled, 'The native report does not offer the exact owned physician');
  await providers.selectOption(context.providerNo);
  h.assert(await providers.inputValue() === context.providerNo, 'The owned report physician selection did not persist');
  for (const selector of ['#xml_vdate', '#xml_appointment_date']) {
    h.assert(await frame.locator(selector).inputValue() === '', 'The report navigated before its complete owned filters were set');
  }
}
module.exports = { selectBillingReportContext };
