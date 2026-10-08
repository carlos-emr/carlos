#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Is the OAuth data API (/ws/services) protected against anonymous floods (issue #4429)?
 *
 * WHY A DEPLOYED CHECK. The fix has two halves that no unit test can join: the packaged
 * nginx front door rate-limits /ws/services and /ws/oauth/authorize, and OAuthInterceptor
 * bounds the synchronous OAUTH_LOGIN_FAILURE audit rows an anonymous caller can force. Only
 * a running deployment shows that the nginx zones are declared and matched, and that the
 * audit table really stops growing.
 *
 * WHAT IT DRIVES
 *   1. Application bound (always). A run of sequential anonymous calls to
 *      /ws/services/oauth/info is each refused 401 (or 429 at the front door) and writes at
 *      most the per-address budget of OAUTH_LOGIN_FAILURE rows plus one
 *      OAUTH_LOGIN_FAILURE_SUPPRESSED marker, not one row per call. It needs MYSQL_*.
 *   2. Front door (EXPECT_FRONT_DOOR=true only). A parallel burst over the nginx ceiling is
 *      answered 429 for /ws/services and for /ws/oauth/authorize, some of the burst still
 *      reaches the app (so the route is throttled, not blocked), and the human login route
 *      (a separate zone) is not collateral damage.
 *
 * The check creates no fixture and sends no credentials. The audit rows it provokes are
 * kept: they are the audit trail. Run it against a disposable server: it deliberately trips
 * the rate limit for the calling address for a few seconds.
 *
 *   npm run test:oauth-ws-rate-limit-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   EXPECT_FRONT_DOOR=true   also assert the nginx 429s (requires the packaged nginx).
 *   WS_FLOOD_REQUESTS=600    size of the front-door burst. WS_FLOOD_CONCURRENCY=60.
 */
const {
  SkipCheck, assert, createSqlRunner, launchBrowser, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');

// Mirrors OAuthFailureAuditService.MAX_ROWS_PER_ADDRESS (+1 for the suppression marker).
const MAX_ROWS_FOR_ONE_ADDRESS = 11;
const SEQUENTIAL_CALLS = 40;

async function statuses(requests) {
  const counts = {};
  for (const status of requests) counts[status] = (counts[status] || 0) + 1;
  return counts;
}

/** Runs `total` GETs against `url` with at most `concurrency` in flight; resolves status codes. */
async function burst(client, url, total, concurrency) {
  const codes = [];
  let next = 0;
  async function worker() {
    while (next < total) {
      next += 1;
      const r = await client.get(url, { failOnStatusCode: false });
      codes.push(r.status());
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker));
  return codes;
}

async function cleanup(state) {
  try {
    if (state.browser) await state.browser.close();
  } finally {
    if (state.sql) state.sql.dispose();
  }
}

async function main(state = {}) {
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  state.sql = sql;
  state.browser = await launchBrowser(config);
  const anon = (await newContext(state.browser, config)).request;
  const app = (p) => `${config.baseUrl}${p}`.replace(/([^:])\/\/+/g, '$1/');
  const infoUrl = app('/ws/services/oauth/info');
  const auditRows = () => Number(sql.value(
    "SELECT COUNT(*) FROM log WHERE action IN ('OAUTH_LOGIN_FAILURE','OAUTH_LOGIN_FAILURE_SUPPRESSED')"));

  // 1. Application bound: many anonymous rejections, few synchronous audit rows.
  const before = auditRows();
  const seen = [];
  for (let i = 0; i < SEQUENTIAL_CALLS; i += 1) {
    const r = await anon.get(infoUrl, { failOnStatusCode: false });
    seen.push(r.status());
  }
  const refused = seen.filter((s) => s === 401).length;
  assert(seen.every((s) => s === 401 || s === 429),
    `anonymous /ws/services calls must be refused (401) or throttled (429); got ${JSON.stringify(await statuses(seen))}`);
  assert(refused > MAX_ROWS_FOR_ONE_ADDRESS,
    `only ${refused} calls reached the application; the check cannot show a bound (is the address already throttled?)`);
  await new Promise((resolve) => setTimeout(resolve, 1500)); // audit rows are written synchronously; allow commit visibility
  const written = auditRows() - before;
  assert(written <= MAX_ROWS_FOR_ONE_ADDRESS,
    `${refused} anonymous rejections wrote ${written} OAUTH_LOGIN_FAILURE* rows; the per-address bound is `
    + `${MAX_ROWS_FOR_ONE_ADDRESS} (issue #4429: one synchronous row per rejected call is unbounded)`);

  // 2. Front door.
  if (!config.expectFrontDoor) {
    console.log('note: EXPECT_FRONT_DOOR is not true; skipped the nginx 429 assertions (bare Tomcat has no limit_req)');
    return { applicationBound: true, frontDoor: 'skipped', rows: written };
  }
  const total = Number(process.env.WS_FLOOD_REQUESTS || 600);
  const concurrency = Number(process.env.WS_FLOOD_CONCURRENCY || 60);
  const services = await burst(anon, infoUrl, total, concurrency);
  const counts = await statuses(services);
  assert(counts[429] > 0, `a ${total}-request burst at /ws/services was never throttled: ${JSON.stringify(counts)}`);
  assert(counts[401] > 0, `the throttled /ws/services route let nothing through: ${JSON.stringify(counts)}`);

  const authorize = await burst(anon, app('/ws/oauth/authorize'), 120, 40);
  const authorizeCounts = await statuses(authorize);
  assert(authorizeCounts[429] > 0,
    `a 120-request burst at /ws/oauth/authorize was never throttled: ${JSON.stringify(authorizeCounts)}`);

  // The human login route has its own zone: a /ws/services flood must not lock a clinician out.
  const login = await anon.get(app('/login'), { failOnStatusCode: false, maxRedirects: 0 });
  assert(login.status() !== 429, 'a /ws/services flood throttled the human login route (shared zone)');
  return { applicationBound: true, frontDoor: counts, authorize: authorizeCounts, rows: written };
}

if (require.main === module) {
  const state = {};
  runCheck({ name: 'oauth-ws-rate-limit', run: () => main(state), cleanup: () => cleanup(state) });
}
module.exports = { main, cleanup, burst, statuses };
