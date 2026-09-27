/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { sqlString } = require('./playwright-harness');

function ids(output) {
  if (!output) return [];
  const values = output.trim().split(/\s+/);
  if (values.some(value => !/^[1-9]\d*$/.test(value))) throw new Error('Invalid owned fixture ID');
  return values;
}

/** Delete only ticklers and marked notes created by this test for its selected patient. */
function cleanupTicklerFixture({ sql, patient, stamp, noteTexts = [] }) {
  if (!/^[1-9]\d*$/.test(String(patient)) || !/^PW_TICKLER_(CRUD|NOTE)_\d+$/.test(stamp)) {
    throw new Error('Invalid tickler fixture identity');
  }
  const owned = `demographic_no=${patient} AND LOCATE(${sqlString(stamp)}, message)>0`;
  const ticklers = ids(sql(`SELECT tickler_no FROM tickler WHERE ${owned}`));
  if (!ticklers.length) return;
  const ticklerIds = ticklers.join(',');
  // Match both patient and exact test note text. An inherited orphan link must
  // never authorize deletion of an unrelated clinical note.
  const notes = noteTexts.length ? ids(sql(`SELECT DISTINCT n.note_id FROM casemgmt_note n
    JOIN casemgmt_note_link l ON l.note_id=n.note_id
    WHERE l.table_name=10 AND l.table_id IN (${ticklerIds})
    AND n.demographic_no=${patient} AND n.note IN (${noteTexts.map(sqlString).join(',')})`)) : [];
  // Remove every link owned by the ticklers being deleted, retaining excluded notes.
  sql(`DELETE FROM casemgmt_note_link WHERE table_name=10 AND table_id IN (${ticklerIds})`);
  if (notes.length) {
    const noteIds = notes.join(',');
    sql(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${noteIds})`);
    sql(`DELETE FROM casemgmt_note WHERE note_id IN (${noteIds})`);
  }
  sql(`DELETE FROM tickler_comments WHERE tickler_no IN (${ticklerIds})`);
  sql(`DELETE FROM tickler_update WHERE tickler_no IN (${ticklerIds})`);
  sql(`DELETE FROM tickler WHERE ${owned} AND tickler_no IN (${ticklerIds})`);
}

module.exports = { cleanupTicklerFixture };
