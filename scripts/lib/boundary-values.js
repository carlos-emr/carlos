/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Shared boundary-value helpers for the `boundary-*` checks: the special-character set, code-point
 * length helpers, byte-exact comparison of a stored column against what a user typed (HEX() so the
 * mysql client's own charset can never mask a utf8mb4/latin1 mojibake), and the over-length verdict.
 *
 * Every value built here is synthetic (marker-tagged); messages may therefore quote them.
 */
const h = require('./playwright-harness');

/** Characters that commonly break an encoder, a charset hop or a query builder. */
const TOKENS = {
  apostrophe: "O'Brien-Smith",
  latin: 'René Zoë Ålesund',
  cjk: '山田太郎',
  emoji: '😀',
  entity: '&amp;',
  quotes: '"dq" \'sq\'',
  backslash: 'a\\b',
  percent: '%41',
  plus: 'a+b',
  semicolon: 'a;b',
};

/** Length in Unicode code points: what a MariaDB utf8mb4 VARCHAR(n) counts. */
function cpLength(value) { return Array.from(String(value)).length; }

/** Upper-case hex of the UTF-8 bytes of a value. */
function hex(value) { return Buffer.from(String(value), 'utf8').toString('hex').toUpperCase(); }

/** A string of exactly `length` code points that starts with `prefix` and is padded with `pad` (one code point). */
function exactly(length, prefix, pad = 'x') {
  const head = Array.from(prefix);
  const padding = Array.from(String(pad));
  if (padding.length !== 1) throw new Error('pad must be exactly one code point');
  if (head.length > length) throw new Error('prefix longer than the requested length');
  return head.concat(Array(length - head.length).fill(padding[0])).join('');
}

/** The declared character length of a column (information_schema), as a number. */
function columnLength(sql, table, column) {
  const n = sql.value(`SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name=${h.sqlString(table)} AND column_name=${h.sqlString(column)}`);
  h.assert(/^\d+$/.test(n), `Column ${table}.${column} has no character length`);
  return Number(n);
}

/** What the database holds for one column, as {hex, chars, text}. `where` is a trusted fragment. */
function readStored(sql, table, column, where) {
  const rows = sql.rows(`SELECT HEX(\`${column}\`), CHAR_LENGTH(\`${column}\`), \`${column}\` FROM \`${table}\` WHERE ${where}`);
  h.assert(rows.length === 1, `Expected exactly one ${table} row for the fixture, found ${rows.length}`);
  return { hex: rows[0][0] || '', chars: Number(rows[0][1] || 0), text: rows[0][2] };
}

/** Explain WHY a stored value differs from what was typed, in terms a finding can quote. */
function explainMismatch(expected, stored) {
  const want = Array.from(expected);
  const bits = [];
  if (stored.chars < want.length) bits.push(`truncated from ${want.length} to ${stored.chars} characters`);
  else if (stored.chars > want.length) bits.push(`grew from ${want.length} to ${stored.chars} characters (double encoding?)`);
  if (/3F/.test(stored.hex) && !/3F/.test(hex(expected))) bits.push('contains "?" substitution');
  if (/C383|C382|C2A\d|C3A2/.test(stored.hex) && !/C383|C382/.test(hex(expected))) bits.push('looks like mojibake (UTF-8 read as latin1)');
  const got = Buffer.from(stored.hex, 'hex').toString('utf8');
  const have = Array.from(got);
  let index = 0;
  while (index < want.length && index < have.length && want[index] === have[index]) index += 1;
  bits.push(`first difference at character ${index}`);
  return `${bits.join('; ')}; typed="${expected}" stored="${got}"`;
}

/** Assert a stored column equals what was typed, byte for byte. */
function assertStored(sql, table, column, where, expected, label) {
  const stored = readStored(sql, table, column, where);
  h.assert(stored.hex === hex(expected), `${label}: stored ${table}.${column} differs from what was typed: ${explainMismatch(expected, stored)}`);
}

/**
 * Over-length verdict: with a value one code point past the column maximum, the application must
 * either keep the row out of the database (a refusal) or store exactly what was typed. A stored
 * value shorter than the typed one is a silent truncation.
 */
function assertNotSilentlyTruncated(sql, table, column, where, typed, label) {
  const rows = sql.rows(`SELECT HEX(\`${column}\`), CHAR_LENGTH(\`${column}\`) FROM \`${table}\` WHERE ${where}`);
  if (rows.length === 0) return 'refused';
  const stored = { hex: rows[0][0] || '', chars: Number(rows[0][1] || 0) };
  h.assert(stored.hex === hex(typed),
    `${label}: ${cpLength(typed)} characters were typed into a ${table}.${column} of limited length and the save was accepted, `
    + `but ${stored.chars} were stored (${explainMismatch(typed, stored)}) - silent truncation, no refusal and no visible limit`);
  return 'stored';
}

/** Compare a page's displayed value with what is stored, tolerant only of case where the page upper-cases. */
function sameText(shown, expected) {
  return String(shown) === String(expected) || String(shown) === String(expected).toUpperCase();
}

/** Remove every demographic whose last name starts with the tag, by relabelling to `marker` first. */
function removeTaggedPatients(sql, tag, marker, removeMarkedPatients) {
  sql.execute(`UPDATE demographic SET last_name=${h.sqlString(marker)} WHERE last_name LIKE ${h.sqlString(tag + '%')}`);
  removeMarkedPatients(sql, marker);
}

module.exports = {
  TOKENS, cpLength, hex, exactly, columnLength, readStored, explainMismatch, assertStored,
  assertNotSilentlyTruncated, sameText, removeTaggedPatients,
};
