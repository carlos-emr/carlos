/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Replay a mutation the UI really sent, from any session, with or without a CSRF token.
 *
 * WHY REPLAY THE UI'S OWN REQUEST. A hand-written POST is only as good as the writer's guess of
 * the form: a missing field makes the action fail for a reason that has nothing to do with the
 * question asked, and the probe then "passes" on a request the application would never have
 * accepted from anyone. So the request is first produced by the real page (the full-privilege
 * login clicking the real control, captured with captureRequest from get-reject-probe.js), and
 * the probe sends exactly that request again with one thing changed:
 *   - authz-write-role-matrix: the cookies and the token of a login whose role lacks the write
 *     right (a VALID token, so CSRFGuard lets it through and the action's own privilege check is
 *     what answers);
 *   - csrf-negative-matrix: no token at all, from the same session that just made the request.
 * A marker the caller chooses goes into the request (overrides), so any row the replay wrote is
 * found by that marker and not confused with the row the UI wrote.
 *
 * WHERE THE TOKEN GOES. CSRFGuard (Ajax=true) reads the CSRF-TOKEN request header when the
 * request is marked X-Requested-With, and the CSRF-TOKEN parameter otherwise
 * (docs/csrf-protection-architecture.md). The replay carries a token exactly where the page did:
 * the header if the page sent one, the body parameter if the page's form had one, the query
 * string likewise; with no carrier at all on a form body it goes in the body. A tokenless replay
 * removes every carrier and changes nothing else: the X-Requested-With marker, the Origin and the
 * Referer stay as the page sent them, so only the token can explain a refusal.
 *
 * The body is resent in the encoding the page used (urlencoded, multipart text fields, JSON);
 * empty fields are kept, because a legacy action treats an absent field differently from an
 * empty one. A multipart request with a file part is refused rather than replayed without it.
 */
const h = require('./playwright-harness');
const { captureRequest, replayParams, multipartFields } = require('./get-reject-probe');

const TOKEN = 'CSRF-TOKEN';
const TOKEN_HEADER = 'csrf-token';
// Request headers resent as the page sent them; everything else (cookies, length, host) is the
// replaying context's own.
const KEPT_HEADERS = ['x-requested-with', 'accept', 'origin', 'referer'];

/** Where the captured request carried its CSRF token. */
function tokenCarriers(captured) {
  return {
    header: Object.prototype.hasOwnProperty.call(captured.headers || {}, TOKEN_HEADER),
    body: captured.body.has(TOKEN),
    query: captured.query.has(TOKEN),
  };
}

function encoding(captured) {
  const type = captured.contentType || '';
  if (/multipart\/form-data/i.test(type)) return 'multipart';
  if (/application\/x-www-form-urlencoded/i.test(type)) return 'form';
  if (/json/i.test(type)) return 'json';
  return captured.postData ? 'raw' : 'form';
}

/** True when a multipart body carries a file part (it cannot be replayed from text fields). */
function hasFilePart(captured) {
  return encoding(captured) === 'multipart'
    && /filename="[^"]+"/i.test(String(captured.postData || ''));
}

/**
 * The replay of a captured POST, as plain data (unit-tested in mutation-replay.test.js):
 * { url, headers, data } for a urlencoded, JSON or raw body, { url, headers, multipart } for a
 * multipart one (an array of [name, value]).
 *
 * @param captured what captureRequest returned
 * @param origin the application's origin (config.baseUrl.origin)
 * @param token the replaying session's CSRF token, or null for the tokenless replay
 * @param overrides body fields to replace (replayParams grammar: null deletes, an array repeats);
 *   for a JSON body, top-level properties to replace
 * @param query query parameters to replace, same grammar
 */
function buildReplay(captured, { origin, token = null, overrides = {}, query = {} } = {}) {
  h.assert(captured && captured.query && captured.body && captured.path, 'buildReplay needs a request from captureRequest');
  h.assert(token === null || (typeof token === 'string' && /^[A-Za-z0-9-]{8,128}$/.test(token)),
    'buildReplay: the token must be the session\'s CSRF token, or null for the tokenless replay');
  h.assert(!hasFilePart(captured), `${captured.path}: a multipart request with a file part cannot be replayed from its text fields`);
  const carriers = tokenCarriers(captured);
  const kind = encoding(captured);
  const urlQuery = replayParams(captured.query, {
    ...query, ...(token && carriers.query ? { [TOKEN]: token } : {}),
  }, { keepEmpty: true });
  const url = new URL(`${captured.path}${urlQuery.toString() ? `?${urlQuery}` : ''}`, origin).toString();
  const headers = {};
  for (const name of KEPT_HEADERS) {
    if (captured.headers && captured.headers[name] !== undefined) headers[name] = captured.headers[name];
  }
  // The body parameter is the carrier for a form the page submitted with one, and the fallback
  // for a form body that had none; a header-only page keeps its token in the header alone.
  const bodyCarrier = carriers.body || (!carriers.header && !carriers.query && kind !== 'json' && kind !== 'raw');
  if (token && carriers.header) headers[TOKEN_HEADER] = token;
  if (token && !carriers.header && !bodyCarrier && !carriers.query) headers[TOKEN_HEADER] = token;
  if (kind === 'json' || kind === 'raw') {
    headers['content-type'] = captured.contentType;
    let data = captured.postData;
    if (kind === 'json' && Object.keys(overrides).length) {
      const parsed = JSON.parse(captured.postData || '{}');
      for (const [key, value] of Object.entries(overrides)) {
        if (value === null) delete parsed[key]; else parsed[key] = value;
      }
      data = JSON.stringify(parsed);
    }
    return { url, headers, data, kind };
  }
  const fields = replayParams(captured.body, { ...overrides, ...(token && bodyCarrier ? { [TOKEN]: token } : {}) }, { keepEmpty: true });
  if (kind === 'multipart') return { url, headers, multipart: [...fields], kind };
  headers['content-type'] = 'application/x-www-form-urlencoded';
  return { url, headers, data: fields.toString(), kind };
}

/** Send a buildReplay() result through `context` (its cookies are the session that asks). */
async function sendReplay(context, replay) {
  const options = { method: 'POST', headers: replay.headers, maxRedirects: 0, failOnStatusCode: false, timeout: 60000 };
  if (replay.multipart) {
    const form = new FormData();
    for (const [name, value] of replay.multipart) form.append(name, value);
    options.multipart = form;
  } else {
    options.data = replay.data;
  }
  return context.request.fetch(replay.url, options);
}

/**
 * This session's CSRF master token, read the way the page's own script reads it (GET /csrfguard).
 * An empty token fails: without one CSRFGuard answers 403 itself, and an authorization probe
 * would then pass on CSRFGuard's refusal instead of the action's privilege check.
 */
async function sessionToken(page, baseUrl) {
  const response = await page.context().request.get(h.appUrl(baseUrl, '/csrfguard'), {
    headers: { referer: page.url() }, failOnStatusCode: false,
  });
  const match = (await response.text()).match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
  h.assert(response.status() === 200 && match, 'No CSRF token could be read for this session');
  return match[1];
}

/** COUNT(*) of `table` WHERE `where`, as a number. */
function count(sql, table, where) {
  h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(table), 'count: table must be a plain table name');
  return Number(sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${where}`));
}

/** Poll until COUNT(*) reaches `expected` (an asynchronous save), then assert it. */
async function expectCount(sql, table, where, expected, message, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  let seen = count(sql, table, where);
  while (seen !== expected && Date.now() < deadline) {
    await new Promise(resolve => setTimeout(resolve, 200));
    seen = count(sql, table, where);
  }
  h.assert(seen === expected, `${message} (${table}: expected ${expected} row(s), found ${seen})`);
  return seen;
}

/** The owned COUNT(*) a write moves `before` to: +1 for a create or update family, -1 for a delete. */
function afterWrite(family, before) {
  return family.kind === 'delete' ? before - 1 : before + 1;
}

/**
 * The full-privilege login performs the family's write through its UI, tagged `tag`. Asserts the
 * page really posted (the captured request) and the owned row really moved, so every replay
 * below starts from a request the application accepted. Returns the captured request.
 */
async function uiWrite(s, family, tag) {
  const before = count(s.sql, family.table, family.where(tag));
  const captured = await family.perform(tag);
  h.assert(captured && captured.method === 'POST', `${family.label}: the UI action sent no POST`);
  h.assert(captured.status > 0 && captured.status < 400, `${family.label}: the UI's own request answered HTTP ${captured.status}`);
  await expectCount(s.sql, family.table, family.where(tag), afterWrite(family, before),
    `${family.label}: the UI's own write did not land`);
  return captured;
}

/**
 * Replay `captured` from `context` (cookies) with `token` (null: none), aimed at `tag`. `write`
 * tells the family the replay is the positive control that must write, so it may set up the fresh
 * server state a write needs. Returns { response, before } with the owned count before sending.
 */
async function replayAimed(s, family, captured, { tag, context, token, write }) {
  const aim = await family.aim(captured, tag, { write });
  const replay = buildReplay(captured, {
    origin: s.config.baseUrl.origin, token, overrides: aim.overrides || {}, query: aim.query || {},
  });
  const before = count(s.sql, family.table, family.where(tag));
  const response = await sendReplay(context, replay);
  return { response, before };
}

/**
 * The positive control every refusal needs: the replay that only differs in WHO sends it (or
 * whether it carries the token) writes its owned row, so the request was well formed and the
 * refusal was about the difference. Returns the response status.
 */
async function proveReplayWrites(s, family, captured, { tag, context, token, who }) {
  const { response, before } = await replayAimed(s, family, captured, { tag, context, token, write: true });
  const status = response.status();
  h.assert(!h.isWafPage(status, await response.text().catch(() => '')),
    `${family.label}: the ${who} replay was refused by the WAF front door, so it proves nothing about the request`);
  await expectCount(s.sql, family.table, family.where(tag), afterWrite(family, before),
    `${family.label}: the same request sent by ${who} (HTTP ${status}) did not write, so the replay is not a request the application accepts`);
  return status;
}

module.exports = {
  TOKEN, afterWrite, buildReplay, captureRequest, count, encoding, expectCount, hasFilePart, multipartFields,
  proveReplayWrites, replayAimed, sendReplay, sessionToken, tokenCarriers, uiWrite,
};
