/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./playwright-harness');

// The clinic email footer is mandatory (#3981): until an administrator saves one, CARLOS
// refuses every staff email and keeps Send disabled. A check that presses Send calls this so it
// also runs on a fresh install or database. It never changes or removes a footer it did not save.
const CLINIC_FOOTER = 'email_footer_clinic_default';
const CLINIC_ROWS = `name=${h.sqlString(CLINIC_FOOTER)} AND (provider_no IS NULL OR provider_no='')`;

/**
 * Saves an owned FAKE clinic footer when none is saved, and registers its removal with the
 * workflow session, so it is removed after a failure too. A saved footer is used as it is.
 *
 * @param {{sql: object, marker: string, cleanup: Function}} session the runWorkflow session
 * @returns {boolean} true when this run saved (and will remove) the footer
 */
function ensureClinicEmailFooter(session) {
  const { sql, marker } = session;
  if (sql.value(`SELECT COUNT(*) FROM property WHERE ${CLINIC_ROWS}`) !== '0') return false;
  // The marker makes the row this run's own: cleanup removes exactly that row.
  const value = `<b>FAKE Clinic</b><br>${marker} clinic footer`;
  session.cleanup(() => {
    sql.execute(`DELETE FROM property WHERE ${CLINIC_ROWS} AND value=${h.sqlString(value)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${CLINIC_ROWS} AND value=${h.sqlString(value)}`) === '0',
      'The owned clinic email footer was not removed');
  });
  sql.execute(`INSERT INTO property (name, value, provider_no) VALUES (${h.sqlString(CLINIC_FOOTER)}, ${h.sqlString(value)}, NULL)`);
  h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${CLINIC_ROWS}`) === '1',
    'Another clinic email footer appeared while the owned one was saved');
  return true;
}

module.exports = { ensureClinicEmailFooter };
