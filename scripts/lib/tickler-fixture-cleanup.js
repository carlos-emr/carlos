/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { sqlString } = require('./playwright-harness');

const TICKLER_LINK_TABLE = 10; // CaseManagementNoteLink.TICKLER

function ids(output) {
  if (!output) return [];
  const values = output.trim().split(/\s+/);
  if (values.some(value => !/^[1-9]\d*$/.test(value))) throw new Error('Invalid owned fixture ID');
  return values;
}

function linkFloor(value) {
  if (!/^(0|[1-9]\d*)$/.test(String(value))) throw new Error('Invalid note link floor');
  return String(value);
}

/**
 * Highest casemgmt_note_link id before this run creates anything. Every link the run
 * makes gets a higher id, so the floor separates the run's links from inherited ones,
 * such as demo-data links whose tickler_no a fresh tickler happens to reuse (#4409).
 */
function readNoteLinkFloor(sql) {
  return linkFloor(sql('SELECT COALESCE(MAX(id), 0) FROM casemgmt_note_link'));
}

/** Count of tickler note links on ticklerNo that existed before the run (id <= floor). */
function inheritedTicklerNoteLinkCount(sql, ticklerNo, linkIdFloor) {
  if (!/^[1-9]\d*$/.test(String(ticklerNo))) throw new Error('Invalid owned fixture ID');
  const count = sql(`SELECT COUNT(*) FROM casemgmt_note_link WHERE table_name=${TICKLER_LINK_TABLE}
    AND table_id=${ticklerNo} AND id<=${linkFloor(linkIdFloor)}`);
  if (!/^\d+$/.test(count)) throw new Error('Invalid inherited note link count');
  return Number(count);
}

/**
 * Create fixture ticklers until one has no note link from before the run, so its note
 * dialog must open blank (#4409). `create(attempt)` makes one tickler and resolves to
 * its `{ id, message }`; a skipped tickler is still the caller's to clean up. Each
 * attempt must use its own message so a skipped tickler never matches the one returned.
 */
async function createTicklerWithoutInheritedNoteLink({ sql, linkIdFloor, create, maxAttempts = 10, log = () => {} }) {
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const created = await create(attempt);
    const inherited = inheritedTicklerNoteLinkCount(sql, created.id, linkIdFloor);
    if (inherited === 0) {
      return created;
    }
    log(`skipped tickler ${created.id}: ${inherited} note link(s) predate this run`);
  }
  throw new Error(`no tickler without an inherited note link after ${maxAttempts} attempts`);
}

/**
 * Delete only ticklers, note links and marked notes created by this test for its selected
 * patient. Note links at or below linkIdFloor predate the run and are never deleted, even
 * when their table_id matches a reused tickler_no.
 */
function cleanupTicklerFixture({ sql, patient, stamp, noteTexts = [], linkIdFloor }) {
  if (!/^[1-9]\d*$/.test(String(patient)) || !/^PW_TICKLER_(CRUD|NOTE)_\d+$/.test(stamp)) {
    throw new Error('Invalid tickler fixture identity');
  }
  const floor = linkFloor(linkIdFloor);
  const owned = `demographic_no=${patient} AND LOCATE(${sqlString(stamp)}, message)>0`;
  const ticklers = ids(sql(`SELECT tickler_no FROM tickler WHERE ${owned}`));
  if (!ticklers.length) return;
  const ticklerIds = ticklers.join(',');
  const ownedLink = alias => `${alias}table_name=${TICKLER_LINK_TABLE} AND ${alias}table_id IN (${ticklerIds}) AND ${alias}id>${floor}`;
  // Match both patient and exact test note text. An inherited orphan link must
  // never authorize deletion of an unrelated clinical note.
  const notes = noteTexts.length ? ids(sql(`SELECT DISTINCT n.note_id FROM casemgmt_note n
    JOIN casemgmt_note_link l ON l.note_id=n.note_id
    WHERE ${ownedLink('l.')}
    AND n.demographic_no=${patient} AND n.note IN (${noteTexts.map(sqlString).join(',')})
    AND NOT EXISTS (SELECT 1 FROM casemgmt_note_link remaining
      WHERE remaining.note_id=n.note_id
      AND NOT (${ownedLink('remaining.')}))`)) : [];
  // Remove the links this run made on its ticklers; inherited links stay as found.
  sql(`DELETE FROM casemgmt_note_link WHERE ${ownedLink('')}`);
  if (notes.length) {
    const noteIds = notes.join(',');
    sql(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${noteIds})`);
    sql(`DELETE FROM casemgmt_note WHERE note_id IN (${noteIds})`);
  }
  sql(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ticklerIds})`);
  sql(`DELETE FROM tickler_comments WHERE tickler_no IN (${ticklerIds})`);
  sql(`DELETE FROM tickler_update WHERE tickler_no IN (${ticklerIds})`);
  sql(`DELETE FROM tickler WHERE ${owned} AND tickler_no IN (${ticklerIds})`);
}

module.exports = {
  cleanupTicklerFixture,
  createTicklerWithoutInheritedNoteLink,
  inheritedTicklerNoteLinkCount,
  readNoteLinkFloor,
};
