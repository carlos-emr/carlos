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

/** "action/content" label that is safe to print. */
function label(row) {
  return `${row.action}${row.content ? `/${row.content}` : ''}`;
}

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

  function rows(extra = '1=1') {
    return sql.rows(`SELECT ${selectList()} FROM log WHERE ${aboutPredicate()} AND (${extra}) ORDER BY id`).map(toRow);
  }

  async function sleep(ms) { await new Promise(resolve => setTimeout(resolve, ms)); }

  return {
    /** Register a row the check created whose audit rows carry it as contentId. */
    own(content, id) { owned.push({ content, id }); },
    rows,
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
    /** Delete exactly the rows this probe scopes to, and prove they are gone. */
    cleanup() {
      sql.execute(`DELETE FROM log WHERE ${aboutPredicate()}`);
      assert(sql.value(`SELECT COUNT(*) FROM log WHERE ${aboutPredicate()}`) === '0', 'Owned audit rows were not removed');
    },
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
      if (cell && needles.some(needle => needle && cell.includes(needle))) leaks.push(`${label(row)}:${column}`);
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

module.exports = { auditProbe, phiLeaks, incomplete, label, NIL };
