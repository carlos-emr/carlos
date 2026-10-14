/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { EPISODE_OBJECT, grantsRead, episodeModuleGranted, grantEpisodeModule } = require('./lib/episode-module-access');

/*
 * The Episode-module grant used by episode-lifecycle and record-access (#4566). It writes to a
 * shared security table, so the restore paths matter as much as the grant: the fake below keeps a
 * secObjPrivilege/secUserRole state and answers exactly the statements the helper issues.
 */

function fakeSql({ privileges = [], roles = [] } = {}) {
  const table = privileges.map(row => ({ ...row }));
  const statements = [];
  // Every `column='literal'` pair of a statement, by column (the helper only filters by equality).
  const literal = (text, name) => {
    for (const [, column, value] of text.matchAll(/(\w+)='((?:[^']|'')*)'/g)) if (column === name) return value.replace(/''/g, "'");
    return null;
  };
  const find = (text) => table.find(row => row.roleUserGroup === literal(text, 'roleUserGroup') && row.objectName === literal(text, 'objectName'));
  return {
    table,
    statements,
    value(query) {
      statements.push(query);
      if (/COALESCE\(MAX\(privilege\)/.test(query)) return find(query)?.privilege || '';
      if (/^SELECT privilege FROM secObjPrivilege WHERE roleUserGroup=/.test(query)) return find(query)?.privilege || '';
      throw new Error(`unexpected value query: ${query}`);
    },
    rows(query) {
      statements.push(query);
      const provider = /roleUserGroup='([^']*)' OR/.exec(query)[1];
      const holders = new Set([provider, ...roles.filter(role => role.provider === provider).map(role => role.name)]);
      return table.filter(row => row.objectName === literal(query, 'objectName') && holders.has(row.roleUserGroup)).map(row => [row.privilege]);
    },
    execute(query) {
      statements.push(query);
      if (/^INSERT INTO secObjPrivilege/.test(query)) {
        const [roleUserGroup, objectName, privilege, priority, providerNo] = /VALUES \(([^)]*)\)/.exec(query)[1].split(',').map(part => part.trim().replace(/^'|'$/g, ''));
        assert.ok(!table.some(row => row.roleUserGroup === roleUserGroup && row.objectName === objectName), 'duplicate key');
        table.push({ roleUserGroup, objectName, privilege, priority, provider_no: providerNo });
      } else if (/^UPDATE secObjPrivilege SET privilege='((?:[^']|'')*)'/.test(query)) {
        const row = find(query);
        const guard = /AND privilege='([^']*)'/.exec(query);
        if (row && (!guard || row.privilege === guard[1])) row.privilege = /SET privilege='((?:[^']|'')*)'/.exec(query)[1];
      } else if (/^DELETE FROM secObjPrivilege/.test(query)) {
        const row = find(query);
        if (row && row.privilege === literal(query, 'privilege') && row.provider_no === literal(query, 'provider_no')) table.splice(table.indexOf(row), 1);
      } else throw new Error(`unexpected statement: ${query}`);
    },
  };
}

function session(sql, provider = '999998') {
  const cleanups = [];
  return { sql, provider, cleanups, cleanup(fn) { cleanups.push(fn); }, runCleanups() { for (const fn of [...cleanups].reverse()) fn(); } };
}

const doctorDenied = { roleUserGroup: 'doctor', objectName: EPISODE_OBJECT, privilege: 'o', priority: '0', provider_no: '999998' };
const carlosdocRoles = [{ provider: '999998', name: 'doctor' }, { provider: '999998', name: 'admin' }];

test('shouldReadGrant_forEachPrivilegeTheModuleAccepts', () => {
  for (const privilege of ['x', 'w', 'u', 'r', 'X', 'or', '|or|']) assert.equal(grantsRead(privilege), true, privilege);
  for (const privilege of ['o', '', null, undefined, 'd', 'od', '|o|']) assert.equal(grantsRead(privilege), false, String(privilege));
});

test('shouldReportNoGrant_whenTheSeededRolesDenyTheModule', () => {
  const sql = fakeSql({ privileges: [doctorDenied], roles: carlosdocRoles });
  assert.equal(episodeModuleGranted(sql, '999998'), false);
  // The pre-#4566 devcontainer gave doctor x: the roles alone then grant it.
  const devcontainer = fakeSql({ privileges: [{ ...doctorDenied, privilege: 'x' }], roles: carlosdocRoles });
  assert.equal(episodeModuleGranted(devcontainer, '999998'), true);
  // Another login's role does not count.
  const other = fakeSql({ privileges: [{ ...doctorDenied, roleUserGroup: 'nurse', privilege: 'x' }], roles: carlosdocRoles });
  assert.equal(episodeModuleGranted(other, '999998'), false);
});

test('shouldInsertAndRemoveProviderRow_whenTheLoginHasNone', () => {
  const sql = fakeSql({ privileges: [doctorDenied], roles: carlosdocRoles });
  const s = session(sql);
  assert.equal(grantEpisodeModule(s), 'inserted');
  assert.deepEqual(sql.table.find(row => row.roleUserGroup === '999998'),
    { roleUserGroup: '999998', objectName: EPISODE_OBJECT, privilege: 'x', priority: '0', provider_no: '999998' });
  assert.equal(episodeModuleGranted(sql, '999998'), true);
  assert.equal(s.cleanups.length, 1);
  s.runCleanups();
  assert.deepEqual(sql.table, [doctorDenied], 'cleanup leaves exactly the seeded rows');
});

test('shouldRestoreProviderDenial_whenTheLoginHadOne', () => {
  const denial = { ...doctorDenied, roleUserGroup: '999998', privilege: 'o' };
  const sql = fakeSql({ privileges: [doctorDenied, denial], roles: carlosdocRoles });
  const s = session(sql);
  assert.equal(grantEpisodeModule(s), 'updated');
  assert.equal(sql.table.find(row => row.roleUserGroup === '999998').privilege, 'x');
  s.runCleanups();
  assert.equal(sql.table.find(row => row.roleUserGroup === '999998').privilege, 'o', 'the operator denial is restored');
});

test('shouldChangeNothing_whenTheProviderRowAlreadyGrants', () => {
  const held = { ...doctorDenied, roleUserGroup: '999998', privilege: 'r' };
  const sql = fakeSql({ privileges: [doctorDenied, held], roles: carlosdocRoles });
  const s = session(sql);
  assert.equal(grantEpisodeModule(s), 'held');
  assert.equal(s.cleanups.length, 0);
  assert.ok(!sql.statements.some(query => /^(INSERT|UPDATE|DELETE)/.test(query)), 'no write when the row already grants');
});

test('shouldFailCleanup_whenTheGrantCannotBeRemoved', () => {
  const sql = fakeSql({ privileges: [doctorDenied], roles: carlosdocRoles });
  const s = session(sql);
  grantEpisodeModule(s);
  // Something else changed the row during the run: the guarded DELETE leaves it, and cleanup says so.
  sql.table.find(row => row.roleUserGroup === '999998').privilege = 'w';
  assert.throws(() => s.runCleanups(), /was not removed/);
});

test('shouldGrantBeforeOpeningChart_inEveryCheckThatDrivesTheModule', () => {
  // A check that clicks the module's heading must grant it first, or it fails on every seeded build.
  const scripts = fs.readdirSync(__dirname).filter(name => name.endsWith('-playwright-checks.js'));
  const drivers = scripts.filter(name => fs.readFileSync(path.join(__dirname, name), 'utf8').includes('#menuTitleepisode'));
  assert.deepEqual(drivers.sort(), ['episode-lifecycle-playwright-checks.js', 'record-access-playwright-checks.js']);
  for (const name of drivers) {
    const source = fs.readFileSync(path.join(__dirname, name), 'utf8');
    const grant = source.indexOf('grantEpisodeModule(s)');
    assert.ok(grant > 0, `${name} drives the Episode module without grantEpisodeModule`);
    assert.ok(grant < source.lastIndexOf('await s.chart()'), `${name} must grant the module before the chart it clicks is opened`);
  }
});
