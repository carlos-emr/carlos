/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { assert, sqlString } = require('./playwright-harness');

/*
 * Access to the chart's Episode module for the checks that drive it.
 *
 * EctDisplayEpisode2Action draws the module only for a login holding _newCasemgmt.episode (read).
 * The Flyway seeds give the doctor role `o` (no rights) on it and the admin role no row, so the
 * seeded test login (doctor + admin) does not see the module on a packaged install, nor in the
 * devcontainer since #4566 aligned its privileges with the seeds. A check that opens the module
 * therefore grants the object for its own run, to the test login's PROVIDER NUMBER: the session's
 * role list is read at login, but the provider number is always one of its entries, so the grant
 * applies without signing in again and reaches no other login. The suite runs one check at a
 * time, so two checks never share the row.
 */

const EPISODE_OBJECT = '_newCasemgmt.episode';

/**
 * Whether a stored privilege satisfies the module's read check (OscarRoleObjectPrivilege.checkRights):
 * x, w, u or r, alone or with the "only" prefix (`or`); a bare `o` is no rights. A value may hold
 * several `|`-separated tokens.
 */
function grantsRead(privilege) {
  return String(privilege || '').split('|').some(token => /^o?[rwux]$/i.test(token.trim()));
}

/**
 * Whether any of the login's roles, or its provider number, already holds the module.
 * Counts inactive role rows too, so a "not granted" answer is never optimistic.
 */
function episodeModuleGranted(sql, provider) {
  const P = sqlString(provider);
  const privileges = sql.rows(`SELECT privilege FROM secObjPrivilege WHERE objectName=${sqlString(EPISODE_OBJECT)}
    AND (roleUserGroup=${P} OR roleUserGroup IN (SELECT role_name FROM secUserRole WHERE provider_no=${P}))`);
  return privileges.some(([privilege]) => grantsRead(privilege));
}

/**
 * Grants the Episode module to the test login's provider number until the run's cleanup, and
 * returns what it did: 'inserted' (no row before; cleanup deletes it), 'updated' (a non-granting
 * row before; cleanup restores its privilege) or 'held' (the provider row already grants; nothing
 * changes and nothing is registered).
 */
function grantEpisodeModule({ sql, provider, cleanup }) {
  const P = sqlString(provider);
  const row = `roleUserGroup=${P} AND objectName=${sqlString(EPISODE_OBJECT)}`;
  const previous = sql.value(`SELECT privilege FROM secObjPrivilege WHERE ${row}`);
  if (grantsRead(previous)) return 'held';
  // Registered before the write, so a failure between the two still restores the row.
  cleanup(() => {
    if (previous) sql.execute(`UPDATE secObjPrivilege SET privilege=${sqlString(previous)} WHERE ${row} AND privilege='x'`);
    else sql.execute(`DELETE FROM secObjPrivilege WHERE ${row} AND privilege='x' AND provider_no=${P}`);
    assert(sql.value(`SELECT COALESCE(MAX(privilege), '') FROM secObjPrivilege WHERE ${row}`) === previous,
      `The temporary ${EPISODE_OBJECT} grant for the test login was not ${previous ? 'restored' : 'removed'}`);
  });
  if (previous) sql.execute(`UPDATE secObjPrivilege SET privilege='x' WHERE ${row}`);
  else {
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${P},${sqlString(EPISODE_OBJECT)},'x',0,${P})`);
  }
  assert(sql.value(`SELECT privilege FROM secObjPrivilege WHERE ${row}`) === 'x',
    `The test login was not granted ${EPISODE_OBJECT}`);
  return previous ? 'updated' : 'inserted';
}

module.exports = { EPISODE_OBJECT, grantsRead, episodeModuleGranted, grantEpisodeModule };
