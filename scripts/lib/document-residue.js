/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The rows the application writes for a DOCUMENT a check owns, beyond the document and ctl_document rows the check
 * inserts or reads back itself. The residue audit (lib/residue-audit.js) counts every table, and found these:
 *
 *   casemgmt_note + casemgmt_note_link   Uploading a document through the UI files a chart note for it ("Document <description>
 *                                        created at <time> by doctor <name>.", provider -1) and a link row (table_name 5 = document,
 *                                        table_id = the document number).
 *   queue_document_link                  The Inbox document queue link of a new document.
 *   providerLabRoutingLock               The lock row of routing, filing, acknowledging or forwarding. It is keyed by the NUMBER
 *                                        alone, in the number space of the HL7 labs, and a new document's number is one that the
 *                                        demo dataset's labs (1 to 172) also use.
 *
 * None of them carries the check's marker, so each is found by the owned document number AND by having been written after a mark
 * taken before the check wrote anything: the demo dataset has link rows for document numbers that no longer exist (finding 143),
 * and a row that was there before the run is not the run's to delete.
 *
 * `sql` is anything with value(query), rows(query) and execute(query) (the harness client, or a thin adapter over an older
 * check's own mysql helper).
 */

const POSITIVE = /^[1-9]\d*$/;

/**
 * Take the mark before the check writes its first row: the highest id of each table the application adds to, and the
 * database's own clock (the lock row's timestamp is the database's).
 *
 * @returns {{ noteLinkFloor: number, queueLinkFloor: number, startedAt: string }}
 */
function markDocumentResidue(sql) {
  const floor = (table) => Number(sql.value(`SELECT IFNULL(MAX(id), 0) FROM ${table}`));
  const startedAt = sql.value('SELECT DATE_FORMAT(NOW(), \'%Y-%m-%d %H:%i:%s\')');
  if (!/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/.test(startedAt)) throw new Error('the database clock was not readable for the document residue mark');
  return { noteLinkFloor: floor('casemgmt_note_link'), queueLinkFloor: floor('queue_document_link'), startedAt };
}

/**
 * Remove, for the given owned document numbers, the chart note and link, the queue links and the routing lock written after
 * `mark`, then assert none is left. Call it after the document rows themselves are gone (or before; the keys do not depend on them).
 */
function removeDocumentResidue(sql, mark, documentNumbers) {
  const ids = [...new Set(documentNumbers.map(String))].filter((id) => POSITIVE.test(id));
  if (!ids.length) return;
  const list = ids.join(',');
  const links = `table_name=5 AND table_id IN (${list}) AND id > ${Number(mark.noteLinkFloor)}`;
  const notes = sql.rows(`SELECT note_id FROM casemgmt_note_link WHERE ${links}`).map(([id]) => id).filter((id) => POSITIVE.test(id));
  if (notes.length) {
    // Only the application's own "document created" note (provider -1), never a note a clinician wrote. The extension
    // rows carry the same guard through the note they hang off (they have no provider or text of their own), and go
    // first, while the note that says whose they are still exists.
    const appNotes = `SELECT note_id FROM casemgmt_note WHERE note_id IN (${notes.join(',')})
      AND provider_no='-1' AND note LIKE 'Document % created at %'`;
    sql.execute(`DELETE FROM casemgmt_note_ext WHERE note_id IN (${appNotes});
      DELETE FROM casemgmt_note WHERE note_id IN (${notes.join(',')}) AND provider_no='-1' AND note LIKE 'Document % created at %'`);
  }
  sql.execute(`DELETE FROM casemgmt_note_link WHERE ${links};
    DELETE FROM queue_document_link WHERE document_id IN (${list}) AND id > ${Number(mark.queueLinkFloor)};
    DELETE FROM providerLabRoutingLock WHERE lab_no IN (${list}) AND lastUpdateDate >= '${mark.startedAt}'`);
  const left = sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note_link WHERE ${links})
    + ${notes.length ? `(SELECT COUNT(*) FROM casemgmt_note WHERE note_id IN (${notes.join(',')}))` : '0'}
    + (SELECT COUNT(*) FROM queue_document_link WHERE document_id IN (${list}) AND id > ${Number(mark.queueLinkFloor)})
    + (SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no IN (${list}) AND lastUpdateDate >= '${mark.startedAt}')`);
  if (left !== '0') throw new Error('The document note, queue link or routing lock rows this run created were not removed');
}

module.exports = { markDocumentResidue, removeDocumentResidue };
