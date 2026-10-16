/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { EPISODE_MODULE_GATES, navbarGateGrants, moduleGateGrants, episodeModuleGranted, grantEpisodeModule } = require('./lib/episode-module-access');

/*
 * The Episode-module grant used by episode-lifecycle and record-access (#4566). The module passes
 * two gates (_episode, then _newCasemgmt.episode) and the seeds deny doctor both, so the grant must
 * cover both. It writes to a shared security table, so the restore paths matter as much as the
 * grant: the fake below keeps a secObjPrivilege/secUserRole state and answers exactly the
 * statements the helper issues.
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

const EPISODE = '_episode';
const MODULE = '_newCasemgmt.episode';
const seeded = (objectName, privilege = 'o', roleUserGroup = 'doctor') => ({ roleUserGroup, objectName, privilege, priority: '0', provider_no: '999998' });
const seedRows = () => [seeded(EPISODE), seeded(MODULE)];
const carlosdocRoles = [{ provider: '999998', name: 'doctor' }, { provider: '999998', name: 'admin' }];
const providerRow = (sql, objectName) => sql.table.find(row => row.roleUserGroup === '999998' && row.objectName === objectName);

test('shouldGateTheModuleOnBothObjects_inDisplayOrder', () => {
  assert.deepEqual(EPISODE_MODULE_GATES.map(gate => gate.objectName), [EPISODE, MODULE]);
});

test('shouldReadUnderNavbarGate_likeSecurityManagerHasReadAccess', () => {
  assert.equal(navbarGateGrants([]), true, 'no row for the login reads');
  for (const privileges of [['x'], ['r'], ['o', 'x'], ['or']]) assert.equal(navbarGateGrants(privileges), true, privileges.join());
  for (const privileges of [['o'], ['o', 'o'], ['w'], ['u'], ['d']]) assert.equal(navbarGateGrants(privileges), false, privileges.join());
});

test('shouldReadUnderModuleGate_likeCheckPrivilege', () => {
  for (const privilege of ['x', 'w', 'u', 'r', 'X', 'or', '|or|']) assert.equal(moduleGateGrants([privilege]), true, privilege);
  for (const privilege of ['o', '', null, undefined, 'd', 'od', '|o|']) assert.equal(moduleGateGrants([privilege]), false, String(privilege));
  assert.equal(moduleGateGrants([]), false, 'no row for the login is no rights here, unlike the navbar gate');
});

test('shouldReportNoGrant_whenTheSeededRolesDenyTheModule', () => {
  assert.equal(episodeModuleGranted(fakeSql({ privileges: seedRows(), roles: carlosdocRoles }), '999998'), false);
  // The pre-#4566 devcontainer gave doctor x on both: the roles alone then pass both gates.
  const devcontainer = fakeSql({ privileges: [seeded(EPISODE, 'x'), seeded(MODULE, 'x')], roles: carlosdocRoles });
  assert.equal(episodeModuleGranted(devcontainer, '999998'), true);
  // One gate is not enough: x on _newCasemgmt.episode alone still leaves _episode refusing.
  const moduleOnly = fakeSql({ privileges: [seeded(EPISODE), seeded(MODULE, 'x')], roles: carlosdocRoles });
  assert.equal(episodeModuleGranted(moduleOnly, '999998'), false);
  // Another login's role does not count.
  const other = fakeSql({ privileges: [...seedRows(), seeded(EPISODE, 'x', 'nurse'), seeded(MODULE, 'x', 'nurse')], roles: carlosdocRoles });
  assert.equal(episodeModuleGranted(other, '999998'), false);
});

test('shouldInsertAndRemoveBothProviderRows_whenTheLoginHasNone', () => {
  const sql = fakeSql({ privileges: seedRows(), roles: carlosdocRoles });
  const s = session(sql);
  assert.deepEqual(grantEpisodeModule(s), { [EPISODE]: 'inserted', [MODULE]: 'inserted' });
  for (const objectName of [EPISODE, MODULE]) {
    assert.deepEqual(providerRow(sql, objectName), { roleUserGroup: '999998', objectName, privilege: 'x', priority: '0', provider_no: '999998' });
  }
  assert.equal(episodeModuleGranted(sql, '999998'), true, 'the grant passes both gates');
  assert.equal(s.cleanups.length, 2);
  s.runCleanups();
  assert.deepEqual(sql.table, seedRows(), 'cleanup leaves exactly the seeded rows');
});

test('shouldRestoreProviderDenial_whenTheLoginHadOne', () => {
  const sql = fakeSql({ privileges: [...seedRows(), seeded(EPISODE, 'o', '999998')], roles: carlosdocRoles });
  const s = session(sql);
  assert.deepEqual(grantEpisodeModule(s), { [EPISODE]: 'updated', [MODULE]: 'inserted' });
  assert.equal(providerRow(sql, EPISODE).privilege, 'x');
  s.runCleanups();
  assert.equal(providerRow(sql, EPISODE).privilege, 'o', 'the operator denial is restored');
  assert.equal(providerRow(sql, MODULE), undefined, 'the inserted row is removed');
});

test('shouldChangeNothing_whenTheProviderRowsAlreadyGrant', () => {
  const sql = fakeSql({ privileges: [...seedRows(), seeded(EPISODE, 'r', '999998'), seeded(MODULE, 'r', '999998')], roles: carlosdocRoles });
  const s = session(sql);
  assert.deepEqual(grantEpisodeModule(s), { [EPISODE]: 'held', [MODULE]: 'held' });
  assert.equal(s.cleanups.length, 0);
  assert.ok(!sql.statements.some(query => /^(INSERT|UPDATE|DELETE)/.test(query)), 'no write when the rows already grant');
});

test('shouldUpgradeProviderRow_whenItPassesOnlyTheModuleGate', () => {
  // w reads under checkPrivilege but not under SecurityManager.hasReadAccess, so _episode needs the grant.
  const sql = fakeSql({ privileges: [...seedRows(), seeded(EPISODE, 'w', '999998'), seeded(MODULE, 'w', '999998')], roles: carlosdocRoles });
  const s = session(sql);
  assert.deepEqual(grantEpisodeModule(s), { [EPISODE]: 'updated', [MODULE]: 'held' });
  s.runCleanups();
  assert.equal(providerRow(sql, EPISODE).privilege, 'w');
});

test('shouldFailCleanup_whenTheGrantCannotBeRemoved', () => {
  const sql = fakeSql({ privileges: seedRows(), roles: carlosdocRoles });
  const s = session(sql);
  grantEpisodeModule(s);
  // Something else changed the row during the run: the guarded DELETE leaves it, and cleanup says so.
  providerRow(sql, MODULE).privilege = 'w';
  assert.throws(() => s.runCleanups(), /_newCasemgmt\.episode grant for the test login was not removed/);
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
