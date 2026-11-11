/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { assert, sqlString } = require('./playwright-harness');

/*
 * Access to the chart's Episode module for the checks that drive it.
 *
 * The module (encounter/displayEpisodes) passes TWO privilege gates, each on its own object:
 *   - _episode: EctDisplayAction.hasReadAccessForDisplayCommand ("_" + cmd) through
 *     SecurityManager.hasReadAccess, which answers an empty 200 when it refuses;
 *   - _newCasemgmt.episode: EctDisplayEpisode2Action.getInfo through
 *     OscarRoleObjectPrivilege.checkPrivilege, which leaves the box empty when it refuses.
 * The Flyway seeds give the doctor role `o` (no rights) on both and the admin role no row, so the
 * seeded test login (doctor + admin) does not see the module on a packaged install, nor in the
 * devcontainer since #4566 aligned its privileges with the seeds. A check that opens the module
 * therefore grants both objects for its own run, to the test login's PROVIDER NUMBER: the
 * session's role list is read at login, but the provider number is always one of its entries, so
 * the grant applies without signing in again and reaches no other login. The suite runs one check
 * at a time, so two checks never share the rows.
 */

/** SecurityManager.hasReadAccess: no row for the login's roles reads; otherwise a row holding r or x. */
function navbarGateGrants(privileges) {
  return privileges.length === 0 || privileges.some(privilege => /[rx]/.test(String(privilege || '')));
}

/**
 * OscarRoleObjectPrivilege.checkPrivilege for read, in descending row priority: x, w, u or r
 * grant read. A legacy "only" token stops evaluation even when it denies read (for example,
 * `od|x` denies, while `x|od` grants). A bare `o` does not stop evaluation.
 */
function moduleGateGrants(privileges) {
  for (const privilege of privileges) {
    for (const token of String(privilege || '').split('|')) {
      let right = token.trim().toLowerCase();
      const only = right.length > 1 && right.startsWith('o');
      if (only) right = right.slice(1);
      if (['r', 'w', 'u', 'x'].includes(right)) return true;
      if (only) return false;
    }
  }
  return false;
}

const EPISODE_MODULE_GATES = [
  { objectName: '_episode', grants: navbarGateGrants },
  { objectName: '_newCasemgmt.episode', grants: moduleGateGrants },
];

/** The login's privilege values on one object: its roles' rows and its provider number's row. */
function loginPrivileges(sql, provider, objectName) {
  const P = sqlString(provider);
  return sql.rows(`SELECT privilege FROM secObjPrivilege WHERE objectName=${sqlString(objectName)}
    AND (roleUserGroup=${P} OR roleUserGroup IN (SELECT role_name FROM secUserRole WHERE provider_no=${P} AND activeyn=1))
    ORDER BY priority DESC`)
    .map(([privilege]) => privilege);
}

/**
 * Whether the login's active roles, or its provider number, already pass both gates.
 */
function episodeModuleGranted(sql, provider) {
  return EPISODE_MODULE_GATES.every(({ objectName, grants }) => grants(loginPrivileges(sql, provider, objectName)));
}

/**
 * Grants both Episode objects to the test login's provider number until the run's cleanup.
 * Returns, per object, what it did: 'inserted' (no row before; cleanup deletes it), 'updated' (a
 * row that did not pass the gate; cleanup restores its privilege) or 'held' (the provider row
 * already passes; nothing changes and nothing is registered).
 */
function grantEpisodeModule({ sql, provider, cleanup }) {
  const P = sqlString(provider);
  const outcome = {};
  for (const { objectName, grants } of EPISODE_MODULE_GATES) {
    const row = `roleUserGroup=${P} AND objectName=${sqlString(objectName)}`;
    const previous = sql.value(`SELECT privilege FROM secObjPrivilege WHERE ${row}`);
    if (previous && grants([previous])) {
      outcome[objectName] = 'held';
      continue;
    }
    // Registered before the write, so a failure between the two still restores the row.
    cleanup(() => {
      if (previous) sql.execute(`UPDATE secObjPrivilege SET privilege=${sqlString(previous)} WHERE ${row} AND privilege='x'`);
      else sql.execute(`DELETE FROM secObjPrivilege WHERE ${row} AND privilege='x' AND provider_no=${P}`);
      assert(sql.value(`SELECT COALESCE(MAX(privilege), '') FROM secObjPrivilege WHERE ${row}`) === previous,
        `The temporary ${objectName} grant for the test login was not ${previous ? 'restored' : 'removed'}`);
    });
    if (previous) sql.execute(`UPDATE secObjPrivilege SET privilege='x' WHERE ${row}`);
    else {
      sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
        VALUES (${P},${sqlString(objectName)},'x',0,${P})`);
    }
    assert(sql.value(`SELECT privilege FROM secObjPrivilege WHERE ${row}`) === 'x', `The test login was not granted ${objectName}`);
    outcome[objectName] = previous ? 'updated' : 'inserted';
  }
  return outcome;
}

module.exports = { EPISODE_MODULE_GATES, navbarGateGrants, moduleGateGrants, episodeModuleGranted, grantEpisodeModule };
