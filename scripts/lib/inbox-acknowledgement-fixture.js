/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {assert, createSqlRunner, sqlString, SkipCheck} = require('./playwright-harness');
const {createLabRoutingFixture} = require('./lab-routing-fixture');

/** Capture every version the UI can file, before submitting an acknowledgement. */
function createInboxAcknowledgementFixture(config, sqlFactory = createSqlRunner) {
  let sql;
  let routing;
  let snapshot;
  let snapshotQuery;
  return {
    async prepare(view, identity) {
      assert(/^HL7:[1-9]\d*$/.test(identity), 'Acknowledgement fixture requires an HL7 identity');
      const current = identity.split(':')[1];
      const chain = await view.evaluate(() => document.querySelector(
        'form[id^="acknowledgeForm_"] input[name="multiID"], input[name="multiID"]')?.value || '');
      const ids = String(chain).split(',').map(id => id.trim()).filter(Boolean);
      assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid acknowledgement version chain');
      const at = ids.indexOf(current);
      const filed = at < 0 ? [current] : ids.slice(0, at + 1);
      sql = sqlFactory(config.mysql);
      const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)}`);
      // Acknowledgement deletes and archives provider-zero routes. Do not mutate those
      // shared unassigned fixtures; use results already routed to a real provider.
      if (Number(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7'
          AND lab_no IN (${filed.join(',')}) AND provider_no='0'`)) !== 0) {
        throw new SkipCheck('Acknowledgement source has unassigned routing; provide labs routed to the test provider');
      }
      assert(/^[1-9]\d*$/.test(provider), 'Login provider is not a valid routing owner');
      snapshotQuery = `SELECT id, lab_no, IFNULL(HEX(status),'NULL'), IFNULL(HEX(comment),'NULL'),
        IFNULL(HEX(timestamp),'NULL') FROM providerLabRouting WHERE provider_no='${provider}'
        AND lab_type='HL7' AND lab_no IN (${filed.join(',')}) ORDER BY id`;
      snapshot = sql.rows(snapshotQuery);
      routing = createLabRoutingFixture(query => sql.execute(query), query => sql.rows(query), provider, filed);
      routing.prepare(false);
    },
    cleanup() {
      try {
        routing?.cleanup();
        if (snapshotQuery) assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(snapshot),
          'Acknowledgement cleanup did not restore the exact routing snapshot');
      }
      finally { sql?.dispose(); }
    },
  };
}
module.exports = {createInboxAcknowledgementFixture};
