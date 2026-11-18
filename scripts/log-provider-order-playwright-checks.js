#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

function preflightConfig() {
  if (process.env.CARLOS_DISPOSABLE_HOST !== 'true' || process.env.EXCLUSIVE !== '1') {
    throw new h.SkipCheck('Requires a disposable exclusive application host');
  }
}

async function workflow(s) {
  const {sql, marker, provider} = s;
  const names = [['alice', 'Smith'], ['Alice', 'Jones'], ['Bob', 'Martin'], ['bob', 'Taylor']];
  const own = names.map(([first, last], i) => ({id: `L${marker.slice(-4)}${i}`, first, last: marker + last}));
  for (const row of own) {
    h.assert(sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=?', [row.id]) === '0', 'Provider fixture ID already exists');
  }
  s.cleanup(() => {
    for (const row of own) {
      const exists = sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=?', [row.id]);
      if (exists !== '0') {
        h.assert(sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=? AND first_name=? AND last_name=?',
          [row.id, row.first, row.last]) === '1', 'Owned provider identity changed');
        sql.execute('DELETE FROM providersite WHERE provider_no=?', [row.id]);
        sql.execute('DELETE FROM provider WHERE provider_no=? AND first_name=? AND last_name=?', [row.id, row.first, row.last]);
      }
      h.assert(sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=?', [row.id]) === '0', 'Owned provider remains');
      h.assert(sql.value('SELECT COUNT(*) FROM providersite WHERE provider_no=?', [row.id]) === '0', 'Owned provider-site association remains');
    }
  });
  for (const row of own) {
    sql.execute('INSERT INTO provider '
      + '(provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate) '
      + "SELECT ?,?,?,provider_type,specialty,sex,'1',?,NOW() FROM provider WHERE provider_no=?",
    [row.id, row.last, row.first, provider, provider]);
    h.assert(sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=? AND last_name=?', [row.id, row.last]) === '1',
      'Owned provider was not created');
    // Match the authenticated provider's existing sites without changing its grants or memberships.
    sql.execute('INSERT INTO providersite(provider_no,site_id) SELECT ?,site_id FROM providersite WHERE provider_no=?',
      [row.id, provider]);
  }
  await s.step('Audit Log offers mixed-case provider names in case-insensitive first/last-name order', async () => {
    await h.gotoApp(s.schedule, s.config.baseUrl, '/admin/LogReport');
    const select = s.schedule.locator('select[name="providerNo"]');
    await select.waitFor();
    const values = await select.locator('option').evaluateAll(options => options.map(option => option.value));
    const ids = own.map(row => row.id);
    const actual = values.filter(value => ids.includes(value));
    h.assert(actual.length === ids.length && ids.every(id => actual.includes(id)), 'Owned provider choices are not visible in Audit Log');
    h.assert(JSON.stringify(actual) === JSON.stringify([ids[1], ids[0], ids[2], ids[3]]),
      'Audit Log provider choices are still sorted by case');
  });
}

if (require.main === module) runWorkflow('log-provider-order', workflow, {openPatient: false, preflightConfig});
module.exports = {preflightConfig, workflow};
