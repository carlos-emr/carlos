/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const h = require('./playwright-harness');

function physician(value) {
  const match = /^(-?\d+)\|(\d+)$/.exec(value);
  return match ? { providerNo: match[1], ohipNo: match[2], value } : null;
}
async function offeredOptions(select) {
  return select.locator('option').evaluateAll(options => options.map(option => ({
    value: option.value, disabled: option.disabled || Boolean(option.closest('optgroup')?.disabled),
  })));
}
function eligiblePhysicians(options, preferred) {
  const offered = options.filter(option => !option.disabled).map(option => physician(option.value)).filter(Boolean);
  h.assert(new Set(offered.map(option => option.value)).size === offered.length,
    'The native invoice form offers duplicate physician values');
  return offered.find(option => option.providerNo === preferred) || offered[0];
}

/** Select only native offered controls. Never insert options or change site/provider rows. */
async function selectBillingSiteAndProvider(page, preferredProvider) {
  h.assert(/^-?\d+$/.test(preferredProvider), 'The logged-in billing provider is invalid');
  const providers = page.locator('select[name="xml_provider"]');
  await providers.waitFor({ state: 'visible', timeout: 20000 });
  h.assert(await providers.count() === 1, 'The native invoice form must offer exactly one physician control');
  const sites = page.locator('select[name="site"]');
  const count = await sites.count();
  h.assert(count === 0 || count === 1, 'The native invoice form offers ambiguous clinic controls');
  let clinic = null;
  let selected;
  if (count) {
    const offered = (await offeredOptions(sites)).filter(option => !option.disabled && option.value && option.value !== 'none');
    h.assert(offered.length > 0, 'The native invoice form offers no eligible clinic');
    h.assert(new Set(offered.map(option => option.value)).size === offered.length,
      'The native invoice form offers duplicate clinic values');
    const current = await sites.inputValue();
    const ordered = [...offered.filter(option => option.value === current), ...offered.filter(option => option.value !== current)];
    for (const option of ordered) {
      // The native change event populates only that clinic's eligible physicians.
      await sites.selectOption(option.value);
      h.assert(await sites.inputValue() === option.value, 'The native clinic selection did not persist');
      selected = eligiblePhysicians(await offeredOptions(providers), preferredProvider);
      if (selected) { clinic = option.value; break; }
    }
  } else selected = eligiblePhysicians(await offeredOptions(providers), preferredProvider);
  h.assert(selected, 'The selected native billing clinic offers no billable physician');
  await providers.selectOption(selected.value);
  h.assert(await providers.inputValue() === selected.value, 'The native physician selection did not persist');
  return Object.freeze({ multisites: count === 1, clinic, providerNo: selected.providerNo, ohipNo: selected.ohipNo });
}

/** Revalidate the offered context with SELECTs before inserting only owned bill rows. */
function validateBillingContext(sql, operator, context) {
  h.assert(context && typeof context.multisites === 'boolean' && /^-?\d+$/.test(context.providerNo)
    && /^\d+$/.test(context.ohipNo) && /^-?\d+$/.test(operator), 'The offered billing context is invalid');
  h.assert(context.multisites ? typeof context.clinic === 'string' && context.clinic.length > 0 && context.clinic !== 'none'
    : context.clinic === null, 'The offered billing clinic binding is invalid');
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(context.providerNo)}
    AND status='1' AND ohip_no=${h.sqlString(context.ohipNo)} AND TRIM(ohip_no)<>''`) === '1',
  'The offered billing physician is no longer active with the same OHIP number');
  if (context.multisites) {
    h.assert(sql.value(`SELECT COUNT(DISTINCT site.site_id) FROM site
      JOIN providersite operator_site ON operator_site.site_id=site.site_id
      JOIN providersite billing_site ON billing_site.site_id=site.site_id
      WHERE site.status<>0 AND site.name=${h.sqlString(context.clinic)}
      AND operator_site.provider_no=${h.sqlString(operator)}
      AND billing_site.provider_no=${h.sqlString(context.providerNo)}`) === '1',
    'The offered billing clinic/provider is not an unambiguous active site shared with the operator');
  }
  return context;
}
module.exports = { selectBillingSiteAndProvider, validateBillingContext };
