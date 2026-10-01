/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Shared plumbing for the authz-read-* checks: sign a restricted-role login in through the real
 * login form, issue GET (and HEAD) requests through that login's browser context, and classify
 * what came back.
 *
 * Classification (the application's own refusal vocabulary):
 *   refused  405, a 401/403 that carries the application's own response header (`fromApp`: the WAF's
 *            block page does not), a redirect to securityError / noRights, or the "Security Exception"
 *            page (securityError.jsp is what every SecurityException ends in, delivered as HTTP 403)
 *   error    5xx, the generic "Error Page" (CARLOS error page), an unmarked 401/403 (WAF/proxy) or any
 *            other redirect (a login or session-timeout bounce): nothing was served, but the refusal is not deliberate, so a
 *            check that pins a refusal accepts only `refused`
 *   served   2xx whose body is real content (not an error page). A caller that wants proof of WHICH
 *            data was served also passes `needles` (strings only that data contains).
 *
 * Probes only ever GET/HEAD routes the caller has verified cannot write on GET, and requests
 * run through context.request (a negative probe, never the positive path of a workflow).
 */
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const h = require('./playwright-harness');

const APPLICATION_HEADER = 'x-permitted-cross-domain-policies';

/** One request through a login's browser context; never follows redirects. */
async function probe(context, url, { method = 'GET', needles = [] } = {}) {
  try {
    const response = await context.request.fetch(url, { method, maxRedirects: 0, timeout: 40000, failOnStatusCode: false });
    const body = method === 'HEAD' ? Buffer.alloc(0) : await response.body();
    const headers = response.headers();
    const type = (headers['content-type'] || '').split(';')[0];
    let text = body.toString('utf8');
    if (/pdf/i.test(type) && body.length > 0) {
      try {
        text = execFileSync('pdftotext', ['-', '-'], { input: body, encoding: 'utf8', timeout: 20000, maxBuffer: 8 << 20 });
      } catch (error) { text = ''; }
    }
    const title = (/<title[^>]*>([\s\S]*?)<\/title>/i.exec(text) || [])[1] || '';
    const plain = text.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>|<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    return {
      status: response.status(), type, length: body.length, location: headers.location || '',
      pdf: body.subarray(0, 5).toString() === '%PDF-',
      // Positive evidence that CARLOS (not the nginx/ModSecurity front door) wrote this answer: its own
      // filters add this header to every response and the WAF's error page lacks it (see
      // scripts/lib/get-reject-probe.js). Works for HEAD, where there is no body to recognise a WAF page by.
      fromApp: Object.prototype.hasOwnProperty.call(headers, APPLICATION_HEADER),
      lead: (title || plain).replace(/\s+/g, ' ').trim().slice(0, 60),
      found: needles.filter(needle => text.includes(needle)),
      hash: crypto.createHash('sha1').update(body).digest('hex').slice(0, 10),
    };
  } catch (error) {
    return { status: 0, error: String(error.message).slice(0, 80), found: [], lead: '' };
  }
}

/** 'refused' | 'error' | 'served' | 'empty' (a 2xx with no body, e.g. a HEAD or an unconfigured fragment). */
function classify(result) {
  const { status } = result;
  const lead = result.lead || '';
  // A 401/403 is the application's refusal only with proof the application answered; an unmarked one
  // (a WAF block, a proxy page) decided nothing about the role, so it reads as an error.
  if (status === 405) return 'refused';
  if ([401, 403].includes(status)) return result.fromApp ? 'refused' : 'error';
  // Only the application's own refusal destinations count; a login or session-timeout redirect is
  // an error (the session was lost, nothing was decided about the role).
  if (status >= 300 && status < 400) return /securityError|noRights/i.test(result.location || '') ? 'refused' : 'error';
  if (/^Security Exception/i.test(lead)) return 'refused';
  if (status === 0 || status >= 400 || /^Error Page/i.test(lead)) return 'error';
  if (status >= 200 && status < 300) return result.length > 0 || result.pdf ? 'served' : 'empty';
  return 'error';
}

/** HTTP 403 that CARLOS itself wrote (application header present); a WAF/proxy 403 is not an authorization decision. */
function forbiddenByApp(result) {
  return result.status === 403 && result.fromApp === true;
}

/**
 * The application's deliberate refusal: HTTP 403 ("Security Exception") carrying the application's
 * own response header, or the redirect a <security:oscarSec> block in a view sends to
 * /securityError?type=_object or /noRights.html. A bare 3xx (a login redirect, a session timeout)
 * and an unmarked 403 (WAF block) are NOT refusals here.
 */
function refusedByApp(result) {
  if (forbiddenByApp(result)) return true;
  return result.status >= 300 && result.status < 400 && /securityError|noRights/i.test(result.location || '');
}

/** The refusal expected for HEAD: the application's refusal, or 405 where the route is GET-only (a container runs doGet for HEAD). */
function refusedHead(result) {
  return refusedByApp(result) || result.status === 405;
}

/** Absolute URL for an application path (`route?query`), validated by the harness. */
function urlFor(config, route) {
  return h.appUrl(config.baseUrl, `/${route.replace(/^\//, '')}`);
}

/**
 * Sign a throwaway login (from authzReadFixture.addLogin) in through the login form and return
 * its context. Pages of this context are not wired to the workflow recorder: the restricted
 * logins deliberately land on pages their role cannot fully load.
 */
async function signIn(session, login) {
  const context = await h.newContext(session.context.browser(), session.config);
  context.setDefaultTimeout(20000);
  const page = await h.login(context, { ...session.config, testUser: login.username }, h.createRecorder());
  return { context, page, login };
}

/**
 * Accumulate findings and assert them together in the LAST step, so one open route does not
 * hide the others. entries: {pair, detail}.
 */
function ledger() {
  const open = [];
  return {
    add(pair, detail) { open.push(`${pair}${detail ? ` (${detail})` : ''}`); },
    get open() { return open.slice(); },
    assertEmpty(message) { h.assert(open.length === 0, `${message}: ${open.join('; ')}`); },
  };
}

module.exports = { probe, classify, forbiddenByApp, refusedByApp, refusedHead, urlFor, signIn, ledger };
