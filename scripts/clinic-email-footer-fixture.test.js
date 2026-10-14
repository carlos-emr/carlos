/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { ensureClinicEmailFooter } = require('./lib/clinic-email-footer-fixture');

// A fake property table: only the rows this fixture's SQL can name.
function fakeSession(existing) {
  const rows = existing.map(value => ({ name: 'email_footer_clinic_default', value }));
  const cleanups = [];
  const valueOf = sql => (/value='([^']*)'/.exec(sql) || [])[1];
  const sql = {
    value(query) {
      assert.match(query, /^SELECT COUNT\(\*\) FROM property WHERE name='email_footer_clinic_default'/);
      const wanted = valueOf(query);
      return String(rows.filter(row => wanted === undefined || row.value === wanted).length);
    },
    execute(query) {
      if (query.startsWith('INSERT INTO property')) {
        rows.push({ name: 'email_footer_clinic_default', value: /, '([^']*)', NULL\)$/.exec(query)[1] });
      } else if (query.startsWith('DELETE FROM property')) {
        const wanted = valueOf(query);
        assert.ok(wanted, 'cleanup must delete by its own value, never every clinic footer');
        for (let i = rows.length - 1; i >= 0; i--) if (rows[i].value === wanted) rows.splice(i, 1);
      } else {
        assert.fail(`Unexpected SQL: ${query}`);
      }
    },
  };
  return { rows, cleanups, session: { sql, marker: 'PWTEST1234', cleanup: fn => cleanups.push(fn) } };
}

test('saves an owned FAKE clinic footer when none is saved, and removes exactly it on cleanup', () => {
  const { rows, cleanups, session } = fakeSession([]);
  assert.equal(ensureClinicEmailFooter(session), true);
  assert.deepEqual(rows.map(row => row.value), ['<b>FAKE Clinic</b><br>PWTEST1234 clinic footer']);
  assert.equal(cleanups.length, 1);
  cleanups[0]();
  assert.deepEqual(rows, []);
});

test('keeps a saved clinic footer as it is and registers no cleanup', () => {
  const { rows, cleanups, session } = fakeSession(['<b>Real clinic</b>']);
  assert.equal(ensureClinicEmailFooter(session), false);
  assert.deepEqual(rows.map(row => row.value), ['<b>Real clinic</b>']);
  assert.equal(cleanups.length, 0);
});

test('registers cleanup before saving, so a failed save is still cleaned up', () => {
  const { cleanups, session } = fakeSession([]);
  const execute = session.sql.execute;
  session.sql.execute = query => { if (query.startsWith('INSERT')) { execute(query); throw new Error('lost reply'); } execute(query); };
  assert.throws(() => ensureClinicEmailFooter(session), /lost reply/);
  assert.equal(cleanups.length, 1);
});
