/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Plumbing for waf-clinical-text-corpus: the clinical phrase corpus fitted to a field, and the
 * ModSecurity audit log read back to name the rule behind a front-door 403.
 *
 * FITTING THE CORPUS. The brief is one request per route carrying every phrase, joined with
 * newlines. Two things bend that. A column has a length (appointment.reason is 80 characters, a
 * custom drug name 60), so a field with a limit gets the phrases packed greedily into as many
 * values as it takes, each within the limit, and a phrase that is longer than the limit on its own
 * is clipped to it (and reported). A single-line <input> cannot hold a newline, so its phrases are
 * joined with a space. The phrase marked `leading` always comes first, because CRS 931100 is
 * anchored on the START of the argument and a link buried mid-text would not exercise it.
 *
 * READING THE AUDIT LOG. nginx answers a blocked request with a bare 403 page that names nothing.
 * The packaged ModSecurity writes one JSON line per relevant transaction to
 * /var/log/carlos-emr/modsec/modsec_audit.log, with each rule that matched. The check records the
 * log's size before a request and, after a WAF page, reads what was appended and picks out the
 * transaction for that route. Only rule ids, CRS messages and the matched variable's NAME are
 * reported, never the matched data. The log is root-readable on the install, so a run from
 * outside the container simply cannot read it and says so.
 */
const fs = require('node:fs');
const { PROSE_CORPUS } = require('./clinical-prose-corpus');

const AUDIT_LOG = '/var/log/carlos-emr/modsec/modsec_audit.log';
/** The CRS rules that only total the score of the others; they name no signature. */
const EVALUATION_RULES = new Set(['949110', '949111', '959100', '980130']);

const codePoints = (text) => Array.from(String(text));

/**
 * The corpus as the values to send for one field.
 *
 * @param {object} [options]
 * @param {number} [options.limit] the field's length in characters; default unlimited
 * @param {string} [options.joiner] what separates phrases inside one value; default a newline
 * @param {ReadonlyArray<{text: string, label: string, leading?: boolean}>} [options.phrases]
 * @returns {{texts: string[], clipped: Array<{label: string, from: number, to: number}>}}
 */
function corpusTexts({ limit = Infinity, joiner = '\n', phrases = PROSE_CORPUS } = {}) {
  if (!(limit >= 1)) throw new Error('corpusTexts: limit must be a positive number of characters');
  const ordered = [...phrases.filter((phrase) => phrase.leading), ...phrases.filter((phrase) => !phrase.leading)];
  const clipped = [];
  const pieces = ordered.map((phrase) => {
    const chars = codePoints(phrase.text);
    if (chars.length <= limit) return phrase.text;
    clipped.push({ label: phrase.label, from: chars.length, to: limit });
    // trimEnd: a value cut after a space would be stored without it, and that is not a loss to report.
    return chars.slice(0, limit).join('').trimEnd();
  });
  const texts = [];
  let current = '';
  for (const piece of pieces) {
    if (current === '') current = piece;
    else if (codePoints(current).length + codePoints(joiner).length + codePoints(piece).length <= limit) current += joiner + piece;
    else { texts.push(current); current = piece; }
  }
  if (current !== '') texts.push(current);
  return { texts, clipped };
}

/**
 * One value per request for a set of fields: field i of request n is that field's n-th value,
 * wrapping when a field has fewer values than the busiest one. Every field therefore carries every
 * one of its own values at least once.
 *
 * @param {Array<{name: string, limit?: number, joiner?: string}>} fields
 * @returns {{requests: Array<Record<string, string>>, clipped: Array<{field: string, label: string, from: number, to: number}>}}
 */
function corpusRequests(fields, phrases = PROSE_CORPUS) {
  const per = fields.map((field) => ({ field, ...corpusTexts({ limit: field.limit, joiner: field.joiner, phrases }) }));
  const count = Math.max(...per.map((entry) => entry.texts.length));
  const requests = [];
  for (let n = 0; n < count; n += 1) {
    requests.push(Object.fromEntries(per.map((entry) => [entry.field.name, entry.texts[n % entry.texts.length]])));
  }
  const clipped = per.flatMap((entry) => entry.clipped.map((clip) => ({ field: entry.field.name, ...clip })));
  return { requests, clipped };
}

/** The matched variable's NAME (ARGS:ticklerMessage), from a CRS "Matched Data: ... found within ARGS:x: value". */
function matchedVariable(data) {
  const match = /within\s+((?:ARGS|ARGS_NAMES|REQUEST_[A-Z_]+|RESPONSE_[A-Z_]+|FILES|XML|JSON)(?::[^:\s]+)?)/.exec(String(data || ''));
  return match ? match[1] : '';
}

/**
 * The rules ModSecurity logged for a refused POST to `routePath`, from the audit log text.
 *
 * @param {string} text the audit log lines appended since the request
 * @param {string} routePath the request path without the query string (/carlos/tickler/DbTicklerAdd)
 * @param {string} [method]
 * @returns {Array<{status: number, rules: Array<{id: string, message: string, variable: string}>}>}
 *   one entry per matching transaction, newest last; a malformed line is skipped
 */
function auditTransactions(text, routePath, method = 'POST') {
  const out = [];
  for (const line of String(text).split('\n')) {
    if (!line.trim().startsWith('{')) continue;
    let transaction;
    try { transaction = JSON.parse(line).transaction; } catch { continue; }
    const request = transaction && transaction.request;
    if (!request || request.method !== method || String(request.uri || '').split('?')[0] !== routePath) continue;
    const rules = (transaction.messages || [])
      .map((entry) => ({
        id: String(entry.details && entry.details.ruleId || ''),
        message: String(entry.message || ''),
        variable: matchedVariable(entry.details && entry.details.data),
      }))
      .filter((rule) => rule.id !== '');
    out.push({ status: Number(transaction.response && transaction.response.http_code), rules });
  }
  return out;
}

/** The signature rules (not the score totals) of the refused transactions, as "932100 name on ARGS:x". */
function describeRules(transactions) {
  const seen = new Map();
  for (const transaction of transactions) {
    if (transaction.status !== 403) continue;
    for (const rule of transaction.rules) {
      if (EVALUATION_RULES.has(rule.id)) continue;
      const key = `${rule.id}|${rule.variable}`;
      if (!seen.has(key)) seen.set(key, `${rule.id} "${rule.message}"${rule.variable ? ` on ${rule.variable}` : ''}`);
    }
  }
  return [...seen.values()];
}

/**
 * A handle on the audit log: mark() before a request, rulesSince(mark, path) after a WAF page.
 * `available` is false (with a reason) where the log cannot be read, and rulesSince then says so.
 */
function auditLog(file = process.env.WAF_AUDIT_LOG || AUDIT_LOG) {
  let unreadable = '';
  try { fs.accessSync(file, fs.constants.R_OK); } catch (error) { unreadable = error.code || error.message; }
  return {
    file,
    available: unreadable === '',
    unreadable,
    mark() {
      if (unreadable) return 0;
      try { return fs.statSync(file).size; } catch { return 0; }
    },
    /** Waits briefly for the transaction's line (it is written after the response), then reads it. `method` is the refused request's. */
    async rulesSince(mark, routePath, { waitMs = 3000, method = 'POST' } = {}) {
      if (unreadable) return { rules: [], note: `the ModSecurity audit log ${file} cannot be read here (${unreadable})` };
      const deadline = Date.now() + waitMs;
      for (;;) {
        const size = fs.statSync(file).size;
        let text = '';
        if (size > mark) {
          const fd = fs.openSync(file, 'r');
          try {
            const buffer = Buffer.alloc(size - mark);
            fs.readSync(fd, buffer, 0, buffer.length, mark);
            text = buffer.toString('utf8');
          } finally { fs.closeSync(fd); }
        }
        const rules = describeRules(auditTransactions(text, routePath, method));
        if (rules.length || Date.now() >= deadline) {
          return { rules, note: rules.length ? '' : `the audit log ${file} holds no signature rule for ${routePath} after the request` };
        }
        await new Promise((resolve) => setTimeout(resolve, 150));
      }
    },
  };
}

module.exports = { AUDIT_LOG, EVALUATION_RULES, auditLog, auditTransactions, corpusRequests, corpusTexts, describeRules, matchedVariable };
