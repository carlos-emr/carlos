/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Shared setup for the gap-provider-appointment-* checks that book appointments as a THROWAWAY login.
 *
 * lib/throwaway-login-fixture.js reserves a provider number against provider, security, log and the
 * provider-linked tables, but NOT against appointment / appointmentArchive (neither table has a provider
 * foreign key). A stale booking left under a number the fixture then picked would be swept up by a cleanup
 * that deletes by provider_no. createUnbookedThrowaway() therefore refuses any number that already owns an
 * appointment or archive row (it discards that fixture and draws another), so the by-provider-number
 * cleanup registered by registerAppointmentCleanup() can only ever remove rows this run created.
 */
const { assert, sqlString } = require('./playwright-harness');
const { throwawayLoginFixture } = require('./throwaway-login-fixture');

/**
 * Create a throwaway login whose provider number owns no appointment or appointmentArchive row.
 * The fixture's own cleanup is registered with the workflow session before its first write.
 *
 * @param s the workflow session
 * @return the created throwaway login fixture
 */
function createUnbookedThrowaway(s) {
  const { sql, marker, config } = s;
  for (let attempt = 0; attempt < 5; attempt++) {
    const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
    let active = true;
    // A discarded fixture is cleaned immediately; the flag stops the registered callback from deleting its
    // provider-number rows a second time, after another run could have drawn the same number.
    s.cleanup(() => { if (active) fixture.cleanup(); });
    fixture.create();
    const owner = sqlString(fixture.providerNo);
    const booked = sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no=${owner})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${owner})`);
    if (booked === '0') return fixture;
    active = false;
    fixture.cleanup();
  }
  throw new Error('No throwaway provider number free of appointment rows was found');
}

/**
 * Register the cleanup of every appointment and archive row under the throwaway's provider number, and
 * prove they are gone. Only call it for a fixture from createUnbookedThrowaway(), whose number owned no
 * appointment row when the run began.
 *
 * @param s the workflow session
 * @param fixture the throwaway login
 */
function registerAppointmentCleanup(s, fixture) {
  const { sql } = s;
  const owner = sqlString(fixture.providerNo);
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE provider_no=${owner}; DELETE FROM appointment WHERE provider_no=${owner}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no=${owner})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${owner})`) === '0', 'Owned appointment rows were not removed');
  });
}

module.exports = { createUnbookedThrowaway, registerAppointmentCleanup };
