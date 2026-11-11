/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/** Select an Add Tickler assignee, choosing their clinic first on multisite installs. */
async function selectTicklerAssignee(page, providerNo) {
  const assignee = page.locator('select[name="task_assigned_to"]');
  const clinic = page.locator('select#site');
  if (await clinic.count()) {
    const sites = await clinic.locator('option').evaluateAll(options =>
      options.map(option => option.value).filter(value => value && value !== 'none'));
    let found = false;
    for (const site of sites) {
      await clinic.selectOption(site);
      found = await assignee.locator('option').evaluateAll((options, provider) =>
        options.some(option => option.value === provider), String(providerNo));
      if (found) break;
    }
    if (!found) throw new Error(`No available Tickler clinic contains provider ${providerNo}`);
  }
  await assignee.selectOption(String(providerNo));
}

module.exports = { selectTicklerAssignee };
