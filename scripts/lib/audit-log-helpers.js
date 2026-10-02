/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Helpers for the audit-log sweep ("audit-log-*" checks): read the `log` table rows that one
 * browser workflow wrote about ONE owned synthetic patient, and judge them without ever printing
 * a row.
 *
 * Scoping. Several checks run at once against the same install and the same test login, so a
 * provider-wide or time-wide read of `log` is polluted by other checks. Every read here is scoped
 * to the owned demographic_no (a number no other check can own) plus the ids of rows the check
 * itself created (note id, appointment id, ...). A row "is about" the patient when its
 * demographic_no column names them, its contentId is the demographic_no, or its free-text `data`
 * names them (the manager layer writes "demographicNo=N", "Demographic: N" or a bare "N").
 *
 * Timing. LogAction.addLog hands the write to a background executor, so a row is not committed
 * the moment the response is back: waitForRows() polls, settle() waits a beat before a COUNT is
 * asserted so a late duplicate is counted too.
 *
 * Messages. Nothing here puts a row value in a thrown message: failures name the action, content
 * type and column only (a log row can carry a patient identifier or note text).
 */
const { assert, sqlString } = require('./playwright-harness');

const NIL = '~NULL~';
const COLUMNS = ['id', 'dateTime', 'provider_no', 'action', 'content', 'contentId', 'ip', 'demographic_no', 'data'];

function selectList() {
  return COLUMNS.map(column => `COALESCE(CAST(\`${column}\` AS CHAR),'${NIL}')`).join(',');
}

function toRow(cells) {
  const value = index => (cells[index] === NIL ? null : cells[index]);
  return {
    id: Number(cells[0]), at: value(1), provider: value(2), action: value(3), content: value(4),
    contentId: value(5), ip: value(6), demographic: value(7), data: value(8),
  };
}

/**
 * "action/content" label for a row. A `content` value is printed as stored, so a caller that is reporting
 * that `content` itself carries patient text passes {maskContent: true} and gets a fixed placeholder.
 */
function label(row, { maskContent = false } = {}) {
  if (!row.content) return `${row.action}`;
  return `${row.action}/${maskContent ? '<content>' : row.content}`;
}

/** Content types whose audit rows legitimately carry the patient's demographic_no as their contentId. */
const PATIENT_KEYED_CONTENT = ['demographic', 'eChart'];

/**
 * @param sql        the session's createSqlRunner result
 * @param patient    owned demographic_no (string of digits), or null for checks about no patient
 * @param ownedRows  mutable list of {content, id} the check adds to as it creates rows (note id, appointment id, ...)
 */
function auditProbe({ sql, patient = null }) {
  const owned = [];
  if (patient !== null) assert(/^[1-9]\d*$/.test(String(patient)), 'The audit probe needs the owned demographic_no');

  function aboutPredicate() {
    const parts = [];
    if (patient !== null) {
      const p = String(patient);
      parts.push(`demographic_no=${p}`, `contentId=${sqlString(p)}`, `data=${sqlString(p)}`,
        `data REGEXP '(emographic(No|_no| id| no)?[ =:]+|patientId=|patient=)${p}([^0-9]|$)'`);
    }
    for (const { content, id } of owned) {
      parts.push(`(content=${sqlString(content)} AND contentId=${sqlString(String(id))})`);
    }
    assert(parts.length, 'The audit probe has nothing to scope to');
    return `(${parts.join(' OR ')})`;
  }

  /**
   * The rows cleanup() may delete. Narrower than aboutPredicate(): a bare `contentId=<demographic_no>` also matches
   * an UNRELATED row whose contentId is another entity's id that happens to equal the number (a document id, a note
   * id), so the contentId match applies only to the content types that key by demographic_no, and a bare numeric
   * `data` value (which cannot say whose number it is) is left out. Rows about the patient that only that wider
   * match would have found are left behind rather than risk deleting someone else's audit history.
   */
  function ownedPredicate() {
    const parts = [];
    if (patient !== null) {
      const p = String(patient);
      parts.push(`demographic_no=${p}`,
        `(content IN (${PATIENT_KEYED_CONTENT.map(sqlString).join(',')}) AND contentId=${sqlString(p)})`,
        `data REGEXP '(emographic(No|_no| id| no)?[ =:]+|patientId=|patient=)${p}([^0-9]|$)'`);
    }
    for (const { content, id } of owned) {
      parts.push(`(content=${sqlString(content)} AND contentId=${sqlString(String(id))})`);
    }
    // Rows only the wider aboutPredicate() matches (a bare numeric data value, contentId under another content type) are
    // ambiguous: every check signs in as the same test login, so neither a watermark nor the provider proves they are this
    // run's rather than a concurrent check's row about a colliding id. They are left in place, never deleted.
    assert(parts.length, 'The audit probe has nothing to scope to');
    return `(${parts.join(' OR ')})`;
  }

  function rows(extra = '1=1') {
    return sql.rows(`SELECT ${selectList()} FROM log WHERE ${aboutPredicate()} AND (${extra}) ORDER BY id`).map(toRow);
  }

  async function sleep(ms) { await new Promise(resolve => setTimeout(resolve, ms)); }

  return {
    /** Register a row the check created whose audit rows carry it as contentId. */
    own(content, id) { owned.push({ content, id }); },
    rows,
    /** Rows of one provider written after `after`, whether or not they name the patient (a report or export row need not). */
    byProvider(providerNo, after, extra = '1=1') {
      return sql.rows(`SELECT ${selectList()} FROM log WHERE id>${Number(after)} AND provider_no=${sqlString(providerNo)} AND (${extra}) ORDER BY id`).map(toRow);
    },
    /** Rows whose id is above `after` (a value returned by mark()). */
    since(after, extra = '1=1') { return rows(`id>${Number(after)} AND (${extra})`); },
    /** High-water mark of the whole table; rows written afterwards have a larger id. */
    mark() { return Number(sql.value('SELECT COALESCE(MAX(id),0) FROM log')); },
    /** Poll until `accept(rows)` is true, then give late duplicates a moment to land and return the rows. */
    async waitFor(accept, description, { after = 0, timeout = 15000, settle = 1500 } = {}) {
      const deadline = Date.now() + timeout;
      let current = rows(`id>${Number(after)}`);
      while (!accept(current) && Date.now() < deadline) {
        await sleep(250);
        current = rows(`id>${Number(after)}`);
      }
      assert(accept(current), `No audit row appeared for: ${description}`);
      await sleep(settle);
      return rows(`id>${Number(after)}`);
    },
    async settle(ms = 1500) { await sleep(ms); },
    /** Delete the rows this probe can prove are its own (ownedPredicate), and prove they are gone. */
    cleanup() {
      sql.execute(`DELETE FROM log WHERE ${ownedPredicate()}`);
      assert(sql.value(`SELECT COUNT(*) FROM log WHERE ${ownedPredicate()}`) === '0', 'Owned audit rows were not removed');
    },
    /** The SQL cleanup() deletes by; exposed for the regression test that an unrelated row is outside it. */
    ownedPredicate,
  };
}

/**
 * Columns of the rows that carry any needle. Never returns or prints the needle or the cell.
 * @return {string[]} "action/content:column" for each leak
 */
function phiLeaks(rows, needles) {
  const leaks = [];
  for (const row of rows) {
    for (const column of ['content', 'contentId', 'data']) {
      const cell = row[column];
      // A leak found IN `content` must not print that content: it is the patient text being reported.
      if (cell && needles.some(needle => needle && cell.includes(needle))) leaks.push(`${label(row, { maskContent: column === 'content' })}:${column}`);
    }
  }
  return [...new Set(leaks)];
}

/** Rows that name an action but not the provider, address or patient they should carry. */
function incomplete(rows, { provider, patient, needIp = true }) {
  const problems = [];
  for (const row of rows) {
    if (row.provider !== provider) problems.push(`${label(row)}: provider`);
    if (needIp && !row.ip) problems.push(`${label(row)}: ip`);
    if (patient && row.demographic !== String(patient)) problems.push(`${label(row)}: demographic_no`);
  }
  return [...new Set(problems)];
}

module.exports = { auditProbe, phiLeaks, incomplete, label, NIL, PATIENT_KEYED_CONTENT };
